package org.atalk.xryptomail.mail.store.imap;

import android.net.ConnectivityManager;
import androidx.annotation.Nullable;

import org.atalk.xryptomail.mail.AuthType;
import org.atalk.xryptomail.mail.ConnectionSecurity;
import org.atalk.xryptomail.mail.Flag;
import org.atalk.xryptomail.mail.MessagingException;
import org.atalk.xryptomail.mail.NetworkType;
import org.atalk.xryptomail.mail.PushReceiver;
import org.atalk.xryptomail.mail.Pusher;
import org.atalk.xryptomail.mail.ServerSettings;
import org.atalk.xryptomail.mail.XryptoMailLib;
import org.atalk.xryptomail.mail.oauth.OAuth2TokenProvider;
import org.atalk.xryptomail.mail.ssl.TrustedSocketFactory;
import org.atalk.xryptomail.mail.store.RemoteStore;
import org.atalk.xryptomail.mail.store.StoreConfig;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import timber.log.Timber;

/**
 * <pre>
 * TODO Need to start keeping track of UIDVALIDITY
 * TODO Need a default response handler for things like folder updates
 * </pre>
 */
public class ImapStore extends RemoteStore
{
    private final Set<Flag> permanentFlagsIndex = EnumSet.noneOf(Flag.class);
    private final ConnectivityManager mConnectivityManager;
    private final OAuth2TokenProvider oauthTokenProvider;

    private final String mHost;
    private final int mPort;
    private final String mUsername;
    private final String mPassword;
    private final String clientCertificateAlias;
    private final ConnectionSecurity mConnectionSecurity;
    private final AuthType mAuthType;
    private String mPathPrefix;
    private String mCombinedPrefix = null;
    private String mPathDelimiter = null;

    private final Deque<ImapConnection> mConnections = new LinkedList<>();
    private final FolderNameCodec folderNameCodec;

    /**
     * Cache of ImapFolder objects. ImapFolders are attached to a given folder
     * on the server and as long as their associated connection remains open
     * they are reusable between requests. This cache lets us make sure we
     * always reuse, if possible, for a given folder name.
     */
    private final Map<String, ImapFolder> mFolderCache = new HashMap<>();

    public static ImapStoreSettings decodeUri(String uri)
    {
        return ImapStoreUriDecoder.decode(uri);
    }

    public static String createUri(ServerSettings server)
    {
        return ImapStoreUriCreator.create(server);
    }

    public ImapStore(StoreConfig storeConfig, TrustedSocketFactory trustedSocketFactory,
            ConnectivityManager connectivityManager, OAuth2TokenProvider oauthTokenProvider)
            throws MessagingException
    {
        super(storeConfig, trustedSocketFactory);

        ImapStoreSettings serverSettings;
        try {
            serverSettings = decodeUri(storeConfig.getStoreUri());
        } catch (IllegalArgumentException e) {
            throw new MessagingException("Error while decoding store URI", e);
        }

        mHost = serverSettings.host;
        mPort = serverSettings.port;
        mConnectionSecurity = serverSettings.connectionSecurity;
        mConnectivityManager = connectivityManager;
        this.oauthTokenProvider = oauthTokenProvider;

        mAuthType = serverSettings.authenticationType;
        mUsername = serverSettings.username;
        mPassword = serverSettings.password;
        clientCertificateAlias = serverSettings.clientCertificateAlias;

        // Make extra sure mPathPrefix is null if "auto-detect namespace" is configured
        mPathPrefix = (serverSettings.autoDetectNamespace) ? null : serverSettings.pathPrefix;
        folderNameCodec = FolderNameCodec.newInstance();
    }

    @Override
    public ImapFolder getFolder(String name)
    {
        ImapFolder folder;
        synchronized (mFolderCache) {
            folder = mFolderCache.get(name);
            if (folder == null) {
                folder = new ImapFolder(this, name);
                mFolderCache.put(name, folder);
            }
        }
        return folder;
    }

    String getCombinedPrefix()
    {
        if (mCombinedPrefix == null) {
            if (mPathPrefix != null) {
                String tmpPrefix = mPathPrefix.trim();
                String tmpDelim = (mPathDelimiter != null ? mPathDelimiter.trim() : "");
                if (tmpPrefix.endsWith(tmpDelim)) {
                    mCombinedPrefix = tmpPrefix;
                }
                else if (tmpPrefix.length() > 0) {
                    mCombinedPrefix = tmpPrefix + tmpDelim;
                }
                else {
                    mCombinedPrefix = "";
                }
            }
            else {
                mCombinedPrefix = "";
            }
        }
        return mCombinedPrefix;
    }

    @Override
    public List<ImapFolder> getPersonalNamespaces(boolean forceListAll)
            throws MessagingException
    {
        ImapConnection connection = getConnection();
        try {
            Set<String> folderNames = listFolders(connection, false);

            if (forceListAll || !mStoreConfig.isSubscribedFoldersOnly()) {
                return getFolders(folderNames);
            }

            Set<String> subscribedFolders = listFolders(connection, true);

            folderNames.retainAll(subscribedFolders);

            return getFolders(folderNames);
        } catch (IOException | MessagingException ioe) {
            connection.close();
            throw new MessagingException("Unable to get folder list.", ioe);
        } finally {
            releaseConnection(connection);
        }
    }

    private Set<String> listFolders(ImapConnection connection, boolean subscribedOnly)
            throws IOException, MessagingException
    {
        String commandResponse = subscribedOnly ? "LSUB" : "LIST";

        List<ImapResponse> responses
                = connection.executeSimpleCommand(String.format("%s \"\" %s", commandResponse,
                ImapUtility.encodeString(getCombinedPrefix() + "*")));

        List<ListResponse> listResponses = (subscribedOnly) ?
                ListResponse.parseLsub(responses) : ListResponse.parseList(responses);

        Set<String> folderNames = new HashSet<>(listResponses.size());

        for (ListResponse listResponse : listResponses) {
            String decodedFolderName;
            try {
                decodedFolderName = folderNameCodec.decode(listResponse.getName());
            } catch (CharacterCodingException e) {
                Timber.w(e, "Folder name not correctly encoded with the UTF-7 variant as defined by RFC 3501: %s",
                        listResponse.getName());

                // TODO: Use the raw name returned by the server for all commands that require
                // a folder name. Use the decoded name only for showing it to the user.

                // We currently just skip folders with malformed names.
                continue;
            }
            String folder = decodedFolderName;

            if (mPathDelimiter == null) {
                mPathDelimiter = listResponse.getHierarchyDelimiter();
                mCombinedPrefix = null;
            }

            if (folder.equalsIgnoreCase(mStoreConfig.getInboxFolder())) {
                continue;
            }
            else if (folder.equals(mStoreConfig.getOutboxFolderName())) {
                /*
                 * There is a folder on the server with the same name as our local
                 * outbox. Until we have a good plan to deal with this situation
                 * we simply ignore the folder on the server.
                 */
                continue;
            }
            else if (listResponse.hasAttribute("\\NoSelect")) {
                continue;
            }

            folder = removePrefixFromFolderName(folder);
            if (folder != null) {
                folderNames.add(folder);
            }
        }
        folderNames.add(mStoreConfig.getInboxFolder());
        return folderNames;
    }

    /**
     * Attempt to auto-configure folders by attributes if the server advertises that capability.
     *
     * The parsing here is essentially the same as
     * {@link #listFolders(org.atalk.xryptomail.mail.store.imap.ImapConnection, boolean)}
     * ; we should try to consolidate this at some point. :(
     *
     * @param connection IMAP Connection
     * @throws IOException uh oh!
     * @throws MessagingException uh oh!
     */
    void autoconfigureFolders(final ImapConnection connection)
            throws IOException, MessagingException
    {
        if (!connection.hasCapability(Capabilities.SPECIAL_USE)) {
            if (XryptoMailLib.isDebug()) {
                Timber.d("No detected folder auto-configuration methods.");
            }
            return;
        }

        if (XryptoMailLib.isDebug()) {
            Timber.d("Folder auto-configuration: Using RFC6154/SPECIAL-USE.");
        }

        String command = String.format("LIST (SPECIAL-USE) \"\" %s", ImapUtility.encodeString(getCombinedPrefix() + "*"));
        List<ImapResponse> responses = connection.executeSimpleCommand(command);
        List<ListResponse> listResponses = ListResponse.parseList(responses);
        for (ListResponse listResponse : listResponses) {

            String decodedFolderName;
            try {
                decodedFolderName = folderNameCodec.decode(listResponse.getName());
            } catch (CharacterCodingException e) {
                Timber.w(e, "Folder name not correctly encoded with the UTF-7 variant as defined by RFC 3501: %s",
                        listResponse.getName());
                // We currently just skip folders with malformed names.
                continue;
            }

            if (mPathDelimiter == null) {
                mPathDelimiter = listResponse.getHierarchyDelimiter();
                mCombinedPrefix = null;
            }

            decodedFolderName = removePrefixFromFolderName(decodedFolderName);
            if (decodedFolderName == null) {
                continue;
            }

            if (listResponse.hasAttribute("\\Archive") || listResponse.hasAttribute("\\All")) {
                mStoreConfig.setArchiveFolderName(decodedFolderName);
                if (XryptoMailLib.isDebug()) {
                    Timber.d("Folder auto-configuration detected Archive folder: %s", decodedFolderName);
                }
            }
            else if (listResponse.hasAttribute("\\Drafts")) {
                mStoreConfig.setDraftsFolder(decodedFolderName);
                if (XryptoMailLib.isDebug()) {
                    Timber.d("Folder auto-configuration detected Drafts folder: %s", decodedFolderName);
                }
            }
            else if (listResponse.hasAttribute("\\Sent")) {
                mStoreConfig.setSentFolder(decodedFolderName);
                if (XryptoMailLib.isDebug()) {
                    Timber.d("Folder auto-configuration detected Sent folder: %s", decodedFolderName);
                }
            }
            else if (listResponse.hasAttribute("\\Junk")) {
                mStoreConfig.setSpamFolderName(decodedFolderName);
                if (XryptoMailLib.isDebug()) {
                    Timber.d("Folder auto-configuration detected Spam folder: %s", decodedFolderName);
                }
            }
            else if (listResponse.hasAttribute("\\Trash")) {
                mStoreConfig.setTrashFolder(decodedFolderName);
                if (XryptoMailLib.isDebug()) {
                    Timber.d("Folder auto-configuration detected Trash folder: %s", decodedFolderName);
                }
            }
        }
    }

    @Nullable
    private String removePrefixFromFolderName(String folderName)
    {
        String prefix = getCombinedPrefix();
        int prefixLength = prefix.length();
        if (prefixLength == 0) {
            return folderName;
        }

        if (!folderName.startsWith(prefix)) {
            // Folder name doesn't start with our configured prefix. But right now when building commands we prefix all
            // folders except the INBOX with the prefix. So we won't be able to use this folder.
            return null;
        }

        return folderName.substring(prefixLength);
    }

    @Override
    public void checkSettings()
            throws MessagingException
    {
        try {
            ImapConnection connection = createImapConnection();
            connection.open();
            autoconfigureFolders(connection);
            connection.close();
        } catch (IOException ioe) {
            throw new MessagingException("Unable to connect", ioe);
        }
    }

    /**
     * Gets a connection if one is available for reuse, or creates a new one if not.
     *
     * @return ImapConnection
     */
    ImapConnection getConnection()
            throws MessagingException
    {
        ImapConnection connection;
        while ((connection = pollConnection()) != null) {
            try {
                connection.executeSimpleCommand(Commands.NOOP);
                break;
            } catch (IOException ioe) {
                connection.close();
            }
        }
        if (connection == null) {
            connection = createImapConnection();
        }
        return connection;
    }

    private ImapConnection pollConnection()
    {
        synchronized (mConnections) {
            return mConnections.poll();
        }
    }

    void releaseConnection(ImapConnection connection)
    {
        if (connection != null && connection.isConnected()) {
            synchronized (mConnections) {
                mConnections.offer(connection);
            }
        }
    }

    ImapConnection createImapConnection()
    {
        return new ImapConnection(
                new StoreImapSettings(),
                mTrustedSocketFactory,
                mConnectivityManager,
                oauthTokenProvider);
    }

    FolderNameCodec getFolderNameCodec()
    {
        return folderNameCodec;
    }

    private List<ImapFolder> getFolders(Collection<String> folderNames)
    {
        List<ImapFolder> folders = new ArrayList<>(folderNames.size());

        for (String folderName : folderNames) {
            ImapFolder imapFolder = getFolder(folderName);
            folders.add(imapFolder);
        }
        return folders;
    }

    @Override
    public boolean isMoveCapable()
    {
        return true;
    }

    @Override
    public boolean isCopyCapable()
    {
        return true;
    }

    @Override
    public boolean isPushCapable()
    {
        return true;
    }

    @Override
    public boolean isExpungeCapable()
    {
        return true;
    }

    StoreConfig getStoreConfig()
    {
        return mStoreConfig;
    }

    Set<Flag> getPermanentFlagsIndex()
    {
        return permanentFlagsIndex;
    }

    @Override
    public Pusher getPusher(PushReceiver receiver)
    {
        return new ImapPusher(this, receiver);
    }

    private class StoreImapSettings implements ImapSettings
    {
        @Override
        public String getHost()
        {
            return mHost;
        }

        @Override
        public int getPort()
        {
            return mPort;
        }

        @Override
        public ConnectionSecurity getConnectionSecurity()
        {
            return mConnectionSecurity;
        }

        @Override
        public AuthType getAuthType()
        {
            return mAuthType;
        }

        @Override
        public String getUsername()
        {
            return mUsername;
        }

        @Override
        public String getPassword()
        {
            return mPassword;
        }

        @Override
        public String getClientCertificateAlias()
        {
            return clientCertificateAlias;
        }

        @Override
        public boolean useCompression(final NetworkType type)
        {
            return mStoreConfig.useCompression(type);
        }

        @Override
        public String getPathPrefix()
        {
            return mPathPrefix;
        }

        @Override
        public void setPathPrefix(String prefix)
        {
            mPathPrefix = prefix;
        }

        @Override
        public String getPathDelimiter()
        {
            return mPathDelimiter;
        }

        @Override
        public void setPathDelimiter(String delimiter)
        {
            mPathDelimiter = delimiter;
        }

        @Override
        public String getCombinedPrefix()
        {
            return mCombinedPrefix;
        }

        @Override
        public void setCombinedPrefix(String prefix)
        {
            mCombinedPrefix = prefix;
        }
    }
}
