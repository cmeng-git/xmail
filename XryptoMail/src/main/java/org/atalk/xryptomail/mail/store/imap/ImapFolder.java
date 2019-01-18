package org.atalk.xryptomail.mail.store.imap;

import android.text.TextUtils;

import org.atalk.xryptomail.XryptoMail;
import org.atalk.xryptomail.mail.Body;
import org.atalk.xryptomail.mail.BodyFactory;
import org.atalk.xryptomail.mail.FetchProfile;
import org.atalk.xryptomail.mail.Flag;
import org.atalk.xryptomail.mail.Folder;
import org.atalk.xryptomail.mail.Message;
import org.atalk.xryptomail.mail.MessageRetrievalListener;
import org.atalk.xryptomail.mail.MessagingException;
import org.atalk.xryptomail.mail.Part;
import org.atalk.xryptomail.mail.XryptoMailLib;
import org.atalk.xryptomail.mail.filter.EOLConvertingOutputStream;
import org.atalk.xryptomail.mail.internet.MimeBodyPart;
import org.atalk.xryptomail.mail.internet.MimeHeader;
import org.atalk.xryptomail.mail.internet.MimeMessageHelper;
import org.atalk.xryptomail.mail.internet.MimeMultipart;
import org.atalk.xryptomail.mail.internet.MimeUtility;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import timber.log.Timber;

class ImapFolder extends Folder<ImapMessage>
{
    private static final ThreadLocal<SimpleDateFormat> RFC3501_DATE = new ThreadLocal<SimpleDateFormat>()
    {
        @Override
        protected SimpleDateFormat initialValue()
        {
            return new SimpleDateFormat("dd-MMM-yyyy", Locale.US);
        }
    };
    private static final int MORE_MESSAGES_WINDOW_SIZE = 500;
    private static final int FETCH_WINDOW_SIZE = 100;

    protected volatile int mMessageCount = -1;
    protected volatile long uidNext = -1L;
    protected volatile ImapConnection mConnection;
    protected ImapStore mStore = null;
    protected Map<Long, String> msgSeqUidMap = new ConcurrentHashMap<>();
    private final FolderNameCodec folderNameCodec;
    private final String mName;
    private int mMode;
    private volatile boolean mExists;
    private boolean mInSearch = false;
    private boolean canCreateKeywords = false;

    public ImapFolder(ImapStore store, String name)
    {
        this(store, name, store.getFolderNameCodec());
    }

    ImapFolder(ImapStore store, String name, FolderNameCodec folderNameCodec)
    {
        super();
        mStore = store;
        mName = name;
        this.folderNameCodec = folderNameCodec;
    }

    private String getPrefixedName()
            throws MessagingException
    {
        String prefixedName = "";

        if (!mStore.getStoreConfig().getInboxFolderName().equalsIgnoreCase(mName)) {
            ImapConnection connection = null;
            synchronized (this) {
                if (mConnection == null) {
                    connection = mStore.getConnection();
                }
                else {
                    connection = mConnection;
                }
            }
            try {
                connection.open();
            } catch (IOException ioe) {
                throw new MessagingException("Unable to get IMAP prefix", ioe);
            } finally {
                if (mConnection == null) {
                    mStore.releaseConnection(connection);
                }
            }
            prefixedName = mStore.getCombinedPrefix();
        }
        prefixedName += mName;
        return prefixedName;
    }

    private List<ImapResponse> executeSimpleCommand(String command)
            throws MessagingException, IOException
    {
        return handleUntaggedResponses(mConnection.executeSimpleCommand(command));
    }

    @Override
    public void open(int mode)
            throws MessagingException
    {
        internalOpen(mode);

        if (mMessageCount == -1) {
            throw new MessagingException("Did not find message count during open");
        }
    }

    protected List<ImapResponse> internalOpen(int mode)
            throws MessagingException
    {
        if (isOpen() && (mMode == mode)) {
            // Make sure the connection is valid. If it's not we'll close it down and continue
            // on to get a new one.
            try {
                return executeSimpleCommand(Commands.NOOP);
            } catch (IOException ioe) {
                ioExceptionHandler(mConnection, ioe);
            }
        }
        mStore.releaseConnection(mConnection);
        synchronized (this) {
            mConnection = mStore.getConnection();
        }
        // * FLAGS (\Answered \Flagged \Deleted \Seen \Draft NonJunk $MDNSent)
        // * OK [PERMANENTFLAGS (\Answered \Flagged \Deleted \Seen \Draft NonJunk $MDNSent \*)] Flags permitted.
        // * 23 EXISTS
        // * 0  RECENT
        // * OK [UIDVALIDITY 1125022061] UIDs valid
        // * OK [UIDNEXT 57576] Predicted next UID
        // 2 OK [READ-WRITE] Select completed.
        try {
            msgSeqUidMap.clear();
            String openCommand = (mode == OPEN_MODE_RW) ? "SELECT" : "EXAMINE";
            String encodedFolderName = folderNameCodec.encode(getPrefixedName());
            String escapedFolderName = ImapUtility.encodeString(encodedFolderName);
            String command = String.format("%s %s", openCommand, escapedFolderName);
            List<ImapResponse> responses = executeSimpleCommand(command);

			/*
             * If the command succeeds we expect the folder has been opened
			 * read-write unless we are notified otherwise in the responses.
			 */
            mMode = mode;
            for (ImapResponse response : responses) {
                handlePermanentFlags(response);
            }
            handleSelectOrExamineOkResponse(ImapUtility.getLastResponse(responses));

            mExists = true;
            return responses;
        } catch (IOException ioe) {
            throw ioExceptionHandler(mConnection, ioe);
        } catch (MessagingException me) {
            Timber.e(me, "Unable to open connection for %s", getLogId());
            throw me;
        }
    }

    private void handlePermanentFlags(ImapResponse response)
    {
        PermanentFlagsResponse permanentFlagsResponse = PermanentFlagsResponse.parse(response);
        if (permanentFlagsResponse == null) {
            return;
        }

        Set<Flag> permanentFlags = mStore.getPermanentFlagsIndex();
        permanentFlags.addAll(permanentFlagsResponse.getFlags());
        canCreateKeywords = permanentFlagsResponse.canCreateKeywords();
    }

    private void handleSelectOrExamineOkResponse(ImapResponse response)
    {
        SelectOrExamineResponse selectOrExamineResponse = SelectOrExamineResponse.parse(response);
        if (selectOrExamineResponse == null) {
            // This shouldn't happen
            return;
        }

        if (selectOrExamineResponse.hasOpenMode()) {
            mMode = selectOrExamineResponse.getOpenMode();
        }
    }

    @Override
    public boolean isOpen()
    {
        return mConnection != null;
    }

    @Override
    public int getMode()
    {
        return mMode;
    }

    @Override
    public void close()
    {
        mMessageCount = -1;
        if (!isOpen()) {
            return;
        }

        synchronized (this) {
            // If we are mid-search and we get a close request, we gotta trash the connection.
            if (mInSearch && (mConnection != null)) {
                Timber.i("IMAP search was aborted, shutting down connection.");
                mConnection.close();
            }
            else {
                mStore.releaseConnection(mConnection);
            }
            mConnection = null;
        }
    }

    @Override
    public String getName()
    {
        return mName;
    }

    /**
     * Check if a given folder exists on the server.
     *
     * @param escapedFolderName The name of the folder encoded as quoted string.
     * @return {@code True}, if the folder exists. {@code False}, otherwise.
     */
    private boolean exists(String escapedFolderName)
            throws MessagingException
    {
        try {
            // Since we don't care about RECENT, we'll use that for the check, because we're checking
            // a folder other than ourself, and don't want any untagged responses to cause a change
            // in our own fields
            mConnection.executeSimpleCommand(String.format("STATUS %s (RECENT)", escapedFolderName));
            return true;
        } catch (IOException ioe) {
            throw ioExceptionHandler(mConnection, ioe);
        } catch (NegativeImapResponseException e) {
            return false;
        }
    }

    @Override
    public boolean exists()
            throws MessagingException
    {
        if (mExists) {
            return true;
        }
        /*
         * This method needs to operate in the unselected mode as well as the selected mode
         * so we must get the connection ourselves if it's not there. We are specifically
         * not calling checkOpen() since we don't care if the folder is open.
         */
        ImapConnection connection;
        synchronized (this) {
            if (mConnection == null) {
                connection = mStore.getConnection();
            }
            else {
                connection = mConnection;
            }
        }
        try {
            String encodedFolderName = folderNameCodec.encode(getPrefixedName());
            String escapedFolderName = ImapUtility.encodeString(encodedFolderName);
            connection.executeSimpleCommand(String.format("STATUS %s (UIDVALIDITY)", escapedFolderName));
            mExists = true;
            return true;
        } catch (NegativeImapResponseException ie) {
            // We got a response, but it was not "OK"
            return false;
        } catch (IOException ioe) {
            throw ioExceptionHandler(connection, ioe);
        } finally {
            if (mConnection == null) {
                mStore.releaseConnection(connection);
            }
        }
    }

    @Override
    public boolean create(FolderType type)
            throws MessagingException
    {
        /*
         * This method needs to operate in the unselected mode as well as the selected mode
         * so we must get the connection ourselves if it's not there. We are specifically
         * not calling checkOpen() since we don't care if the folder is open.
         */
        ImapConnection connection;
        synchronized (this) {
            if (mConnection == null) {
                connection = mStore.getConnection();
            }
            else {
                connection = mConnection;
            }
        }
        try {
            String encodedFolderName = folderNameCodec.encode(getPrefixedName());
            String escapedFolderName = ImapUtility.encodeString(encodedFolderName);
            connection.executeSimpleCommand(String.format("CREATE %s", escapedFolderName));
            return true;
        } catch (NegativeImapResponseException ie) {
            // We got a response, but it was not "OK"
            return false;
        } catch (IOException ioe) {
            throw ioExceptionHandler(mConnection, ioe);
        } finally {
            if (mConnection == null) {
                mStore.releaseConnection(connection);
            }
        }
    }

    /**
     * Copies the given messages to the specified folder.
     *
     * <p>
     * <strong>Note:</strong>
     * Only the UIDs of the given {@link Message} instances are used. It is assumed that all
     * UIDs represent valid messages in this folder.
     * </p>
     *
     * @param messages The messages to copy to the specified folder.
     * @param folder The name of the target folder.
     * @return The mapping of original message UIDs to the new server UIDs.
     */
    @Override
    public Map<String, String> copyMessages(List<? extends Message> messages, Folder folder)
            throws MessagingException
    {
        if (!(folder instanceof ImapFolder)) {
            throw new MessagingException("ImapFolder.copyMessages passed non-ImapFolder");
        }

        if (messages.isEmpty()) {
            return null;
        }

        ImapFolder imapFolder = (ImapFolder) folder;
        checkOpen(); //only need READ access

        Set<Long> uids = new HashSet<>(messages.size());
        for (int i = 0, count = messages.size(); i < count; i++) {
            uids.add(Long.parseLong(messages.get(i).getUid()));
        }

        String encodedDestinationFolderName = folderNameCodec.encode(imapFolder.getPrefixedName());
        String escapedDestinationFolderName = ImapUtility.encodeString(encodedDestinationFolderName);

        //TODO: Try to copy/move the messages first and only create the folder if the
        //      operation fails. This will save a roundtrip if the folder already exists.
        if (!exists(escapedDestinationFolderName)) {
            /*
             * If the remote folder doesn't exist we try to create it.
             */
            if (XryptoMailLib.isDebug()) {
                Timber.i("ImapFolder.copyMessages: attempting to create remote folder '%s' for %s",
                        escapedDestinationFolderName, getLogId());
            }
            imapFolder.create(FolderType.HOLDS_MESSAGES);
        }

        try {
            List<ImapResponse> imapResponses = mConnection.executeCommandWithIdSet(Commands.UID_COPY,
                    escapedDestinationFolderName, uids);

            UidCopyResponse response = UidCopyResponse.parse(imapResponses);
            return response == null ? null : response.getUidMapping();
        } catch (IOException ioe) {
            throw ioExceptionHandler(mConnection, ioe);
        }
    }

    @Override
    public Map<String, String> moveMessages(List<? extends Message> messages, Folder folder)
            throws MessagingException
    {
        if (messages.isEmpty()) {
            return null;
        }

        Map<String, String> uidMapping = copyMessages(messages, folder);
        setFlags(messages, Collections.singleton(Flag.DELETED), true);
        return uidMapping;
    }

    @Override
    public void delete(List<? extends Message> messages, String trashFolderName)
            throws MessagingException
    {
        if (messages.isEmpty()) {
            return;
        }

        if (trashFolderName == null || getName().equalsIgnoreCase(trashFolderName)) {
            setFlags(messages, Collections.singleton(Flag.DELETED), true);
        }
        else {
            ImapFolder remoteTrashFolder = getStore().getFolder(trashFolderName);
            String encodedTrashFolderName = folderNameCodec.encode(remoteTrashFolder.getPrefixedName());
            String escapedTrashFolderName = ImapUtility.encodeString(encodedTrashFolderName);

            if (!exists(escapedTrashFolderName)) {
                /*
				 * If the remote trash folder doesn't exist we try to create it.
				 */
                if (XryptoMailLib.isDebug())
                    Timber.i("IMAPMessage.delete: attempting to create remote '%s' folder for %s",
                            trashFolderName, getLogId());
                remoteTrashFolder.create(FolderType.HOLDS_MESSAGES);
            }

            if (exists(escapedTrashFolderName)) {
                if (XryptoMailLib.isDebug())
                    Timber.d("IMAPMessage.delete: copying remote %d messages to '%s' for %s",
                            messages.size(), trashFolderName, getLogId());

                moveMessages(messages, remoteTrashFolder);
            }
            else {
                throw new MessagingException("IMAPMessage.delete: remote Trash folder " + trashFolderName +
                        " does not exist and could not be created for " + getLogId(), true);
            }
        }
    }

    @Override
    public int getMessageCount()
    {
        return mMessageCount;
    }

    private int getRemoteMessageCount(String criteria)
            throws MessagingException
    {
        checkOpen();
        try {
            int count = 0;
            int start = 1;

            String command = String.format(Locale.US, "SEARCH %d:* %s", start, criteria);
            List<ImapResponse> responses = executeSimpleCommand(command);
            for (ImapResponse response : responses) {
                if (ImapResponseParser.equalsIgnoreCase(response.get(0), "SEARCH")) {
                    count += response.size() - 1;
                }
            }
            return count;
        } catch (IOException ioe) {
            throw ioExceptionHandler(mConnection, ioe);
        }
    }

    @Override
    public int getUnreadMessageCount()
            throws MessagingException
    {
        return getRemoteMessageCount("UNSEEN NOT DELETED");
    }

    @Override
    public int getFlaggedMessageCount()
            throws MessagingException
    {
        return getRemoteMessageCount("FLAGGED NOT DELETED");
    }

    protected long getHighestUid()
            throws MessagingException
    {
        try {
            List<ImapResponse> responses = executeSimpleCommand("UID SEARCH *:*");

            SearchResponse searchResponse = SearchResponse.parse(responses);

            return extractHighestUid(searchResponse);
        } catch (NegativeImapResponseException e) {
            return -1L;
        } catch (IOException ioe) {
            throw ioExceptionHandler(mConnection, ioe);
        }
    }

    private long extractHighestUid(SearchResponse searchResponse)
    {
        List<Long> uids = searchResponse.getNumbers();
        if (uids.isEmpty()) {
            return -1L;
        }

        if (uids.size() == 1) {
            return uids.get(0);
        }

        Collections.sort(uids, Collections.reverseOrder());
        return uids.get(0);
    }

    @Override
    public void delete(boolean recurse)
            throws MessagingException
    {
        throw new Error("ImapFolder.delete() not yet implemented");
    }

    @Override
    public ImapMessage getMessage(String uid)
            throws MessagingException
    {
        return new ImapMessage(uid, this);
    }

    @Override
    public List<ImapMessage> getMessages(int start, int end, Date earliestDate,
            MessageRetrievalListener<ImapMessage> listener)
            throws MessagingException
    {
        return getMessages(start, end, earliestDate, false, listener);
    }

    protected List<ImapMessage> getMessages(final int start, final int end, Date earliestDate,
            final boolean includeDeleted, final MessageRetrievalListener<ImapMessage> listener)
            throws MessagingException
    {

        if (start < 1 || end < 1 || end < start) {
            throw new MessagingException(String.format(Locale.US, "Invalid message set %d %d", start, end));
        }

        checkOpen();
        String dateSearchString = getDateSearchString(earliestDate);
        String command = String.format(Locale.US, "UID SEARCH %d:%d%s%s", start, end, dateSearchString,
                includeDeleted ? "" : " NOT DELETED");

        try {
            List<ImapResponse> imapResponses = mConnection.executeSimpleCommand(command);
            SearchResponse searchResponse = SearchResponse.parse(imapResponses);
            return getMessages(searchResponse, listener);
        } catch (IOException ioe) {
            throw ioExceptionHandler(mConnection, ioe);
        }
    }

    private String getDateSearchString(Date earliestDate)
    {
        if (earliestDate == null) {
            return "";
        }
        return " SINCE " + RFC3501_DATE.get().format(earliestDate);
    }

    @Override
    public boolean areMoreMessagesAvailable(int indexOfOldestMessage, Date earliestDate)
            throws IOException, MessagingException
    {

        checkOpen();

        if (indexOfOldestMessage == 1) {
            return false;
        }

        int endIndex = indexOfOldestMessage - 1;
        String dateSearchString = getDateSearchString(earliestDate);

        while (endIndex > 0) {
            int startIndex = Math.max(0, endIndex - MORE_MESSAGES_WINDOW_SIZE) + 1;

            if (existsNonDeletedMessageInRange(startIndex, endIndex, dateSearchString)) {
                return true;
            }

            endIndex = endIndex - MORE_MESSAGES_WINDOW_SIZE;
        }
        return false;
    }

    private boolean existsNonDeletedMessageInRange(int startIndex, int endIndex, String dateSearchString)
            throws MessagingException, IOException
    {

        String command = String.format(Locale.US, "SEARCH %d:%d%s NOT DELETED",
                startIndex, endIndex, dateSearchString);
        List<ImapResponse> imapResponses = executeSimpleCommand(command);

        SearchResponse response = SearchResponse.parse(imapResponses);
        return response.getNumbers().size() > 0;
    }

    protected List<ImapMessage> getMessages(final Set<Long> mesgSeqs, final boolean includeDeleted,
            final MessageRetrievalListener<ImapMessage> listener)
            throws MessagingException
    {

        checkOpen();

        try {
            String commandSuffix = includeDeleted ? "" : " NOT DELETED";
            List<ImapResponse> imapResponses = mConnection.executeCommandWithIdSet(Commands.UID_SEARCH,
                    commandSuffix, mesgSeqs);

            SearchResponse searchResponse = SearchResponse.parse(imapResponses);
            return getMessages(searchResponse, listener);
        } catch (IOException ioe) {
            throw ioExceptionHandler(mConnection, ioe);
        }
    }

    protected List<ImapMessage> getMessagesFromUids(final List<String> mesgUids)
            throws MessagingException
    {

        checkOpen();
        Set<Long> uidSet = new HashSet<>();
        for (String uid : mesgUids) {
            uidSet.add(Long.parseLong(uid));
        }

        try {
            List<ImapResponse> imapResponses = mConnection.executeCommandWithIdSet("UID SEARCH UID", "", uidSet);

            SearchResponse searchResponse = SearchResponse.parse(imapResponses);
            return getMessages(searchResponse, null);
        } catch (IOException ioe) {
            throw ioExceptionHandler(mConnection, ioe);
        }
    }

    private List<ImapMessage> getMessages(SearchResponse searchResponse, MessageRetrievalListener<ImapMessage> listener)
            throws MessagingException
    {

        List<ImapMessage> messages = new ArrayList<>();
        List<Long> uids = searchResponse.getNumbers();

        // Sort the uids in numerically decreasing order
        // By doing it in decreasing order, we ensure newest messages are dealt with first
        // This makes the most sense when a limit is imposed, and also prevents UI from going
        // crazy adding stuff at the top.
        Collections.sort(uids, Collections.reverseOrder());

        for (int i = 0, count = uids.size(); i < count; i++) {
            String uid = uids.get(i).toString();
            if (listener != null) {
                listener.messageStarted(uid, i, count);
            }
            ImapMessage message = new ImapMessage(uid, this);
            messages.add(message);
            if (listener != null) {
                listener.messageFinished(message, i, count);
            }
        }
        return messages;
    }

    @Override
    public void fetch(List<ImapMessage> messages, FetchProfile fetchProfile, MessageRetrievalListener<ImapMessage> listener)
            throws MessagingException
    {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        checkOpen();

        List<String> uids = new ArrayList<>(messages.size());
        HashMap<String, Message> messageMap = new HashMap<>();
        for (Message message : messages) {
            String uid = message.getUid();
            uids.add(uid);
            messageMap.put(uid, message);
        }

		/*
		 * Figure out what command we are going to run: Flags - UID FETCH
		 * (FLAGS) Envelope - UID FETCH ([FLAGS] INTERNALDATE UID
		 * RFC822.SIZE FLAGS BODY.PEEK[HEADER.FIELDS (date subject from
		 * content-type to cc)])
		 */
        Set<String> fetchFields = new LinkedHashSet<>();
        fetchFields.add("UID");
        if (fetchProfile.contains(FetchProfile.Item.FLAGS)) {
            fetchFields.add("FLAGS");
        }
        if (fetchProfile.contains(FetchProfile.Item.ENVELOPE)) {
            fetchFields.add("INTERNALDATE");
            fetchFields.add("RFC822.SIZE");
            fetchFields.add("BODY.PEEK[HEADER.FIELDS (date subject from content-type to cc "
                    + "reply-to message-id references in-reply-to " + XryptoMail.IDENTITY_HEADER + ")]");
        }
        if (fetchProfile.contains(FetchProfile.Item.STRUCTURE)) {
            fetchFields.add("BODYSTRUCTURE");
        }
        if (fetchProfile.contains(FetchProfile.Item.BODY_SANE)) {
            // If the user wants to download unlimited-size messages, don't go only for the truncated body
            int maximumAutoDownloadMessageSize = mStore.getStoreConfig().getMaximumAutoDownloadMessageSize();
            if (maximumAutoDownloadMessageSize > 0) {
                fetchFields.add(String.format(Locale.US, "BODY.PEEK[]<0.%d>", maximumAutoDownloadMessageSize));
            }
            else {
                fetchFields.add("BODY.PEEK[]");
            }
        }

        if (fetchProfile.contains(FetchProfile.Item.BODY)) {
            fetchFields.add("BODY.PEEK[]");
        }

        String spaceSeparatedFetchFields = ImapUtility.join(" ", fetchFields);

        for (int windowStart = 0; windowStart < messages.size(); windowStart += (FETCH_WINDOW_SIZE)) {
            int windowEnd = Math.min(windowStart + FETCH_WINDOW_SIZE, messages.size());
            List<String> uidWindow = uids.subList(windowStart, windowEnd);

            try {
                String commaSeparatedUids = ImapUtility.join(",", uidWindow);
                String command = String.format("UID FETCH %s (%s)", commaSeparatedUids, spaceSeparatedFetchFields);
                mConnection.sendCommand(command, false);

                ImapResponse response;
                int messageNumber = 0;

                ImapResponseCallback callback = null;
                if (fetchProfile.contains(FetchProfile.Item.BODY) ||
                        fetchProfile.contains(FetchProfile.Item.BODY_SANE)) {
                    callback = new FetchBodyCallback(messageMap);
                }

                do {
                    response = mConnection.readResponse(callback);
                    if (response.getTag() == null && ImapResponseParser.equalsIgnoreCase(response.get(1), "FETCH")) {
                        ImapList fetchList = (ImapList) response.getKeyedValue("FETCH");
                        String uid = fetchList.getKeyedString("UID");
                        long msgSeq = response.getLong(0);
                        if (uid != null) {
                            try {
                                msgSeqUidMap.put(msgSeq, uid);
                                if (XryptoMailLib.isDebug()) {
                                    Timber.v("Stored uid '%s' for msgSeq %d into map", uid, msgSeq);
                                }
                            } catch (Exception e) {
                                Timber.e("Unable to store uid '%s' for msgSeq %d", uid, msgSeq);
                            }
                        }

                        Message message = messageMap.get(uid);
                        if (message == null) {
                            if (XryptoMailLib.isDebug())
                                Timber.d("Do not have message in messageMap for UID %s for %s", uid, getLogId());

                            handleUntaggedResponse(response);
                            continue;
                        }
                        if (listener != null) {
                            listener.messageStarted(uid, messageNumber++, messageMap.size());
                        }

                        ImapMessage imapMessage = (ImapMessage) message;
                        Object literal = handleFetchResponse(imapMessage, fetchList);
                        if (literal != null) {
                            if (literal instanceof String) {
                                String bodyString = (String) literal;
                                InputStream bodyStream = new ByteArrayInputStream(bodyString.getBytes());
                                imapMessage.parse(bodyStream);
                            }
                            else if (literal instanceof Integer) {
                                // All the work was done in FetchBodyCallback.foundLiteral()
                            }
                            else {
                                // This shouldn't happen
                                throw new MessagingException("Got FETCH response with bogus parameters");
                            }
                        }

                        if (listener != null) {
                            listener.messageFinished(imapMessage, messageNumber, messageMap.size());
                        }
                    }
                    else {
                        handleUntaggedResponse(response);
                    }

                } while (response.getTag() == null);
            } catch (IOException ioe) {
                throw ioExceptionHandler(mConnection, ioe);
            }
        }
    }

    @Override
    public void fetchPart(Message message, Part part, MessageRetrievalListener<Message> listener, BodyFactory bodyFactory)
            throws MessagingException
    {
        checkOpen();  //only need READ access

        String partId = part.getServerExtra();

        String fetch;
        if ("TEXT".equalsIgnoreCase(partId)) {
            int maximumAutoDownloadMessageSize = mStore.getStoreConfig().getMaximumAutoDownloadMessageSize();
            fetch = String.format(Locale.US, "BODY.PEEK[TEXT]<0.%d>", maximumAutoDownloadMessageSize);
        }
        else {
            fetch = String.format("BODY.PEEK[%s]", partId);
        }

        try {
            String command = String.format("UID FETCH %s (UID %s)", message.getUid(), fetch);
            mConnection.sendCommand(command, false);

            ImapResponse response;
            int messageNumber = 0;

            ImapResponseCallback callback = new FetchPartCallback(part, bodyFactory);
            do {
                response = mConnection.readResponse(callback);

                if ((!response.isTagged() && ImapResponseParser.equalsIgnoreCase(response.get(1), "FETCH"))) {
                    ImapList fetchList = (ImapList) response.getKeyedValue("FETCH");
                    String uid = fetchList.getKeyedString("UID");

                    if (!message.getUid().equals(uid)) {
                        if (XryptoMailLib.isDebug())
                            Timber.d("Did not ask for UID %s for %s", uid, getLogId());

                        handleUntaggedResponse(response);
                        continue;
                    }
                    if (listener != null) {
                        listener.messageStarted(uid, messageNumber++, 1);
                    }

                    ImapMessage imapMessage = (ImapMessage) message;

                    Object literal = handleFetchResponse(imapMessage, fetchList);
                    if (literal != null) {
                        if (literal instanceof Body) {
                            // Most of the work was done in FetchAttchmentCallback.foundLiteral()
                            MimeMessageHelper.setBody(part, (Body) literal);
                        }
                        else if (literal instanceof String) {
                            String bodyString = (String) literal;
                            InputStream bodyStream = new ByteArrayInputStream(bodyString.getBytes());

                            String contentTransferEncoding =
                                    part.getHeader(MimeHeader.HEADER_CONTENT_TRANSFER_ENCODING)[0];
                            String contentType = part.getHeader(MimeHeader.HEADER_CONTENT_TYPE)[0];
                            Body body = bodyFactory.createBody(contentTransferEncoding, contentType, bodyStream);
                            MimeMessageHelper.setBody(part, body);
                        }
                        else {
                            // This shouldn't happen
                            throw new MessagingException("Got FETCH response with bogus parameters");
                        }
                    }

                    if (listener != null) {
                        listener.messageFinished(message, messageNumber, 1);
                    }
                }
                else {
                    handleUntaggedResponse(response);
                }

            } while (response.getTag() == null);
        } catch (IOException ioe) {
            throw ioExceptionHandler(mConnection, ioe);
        }
    }

    // Returns value of body field
    private Object handleFetchResponse(ImapMessage message, ImapList fetchList)
            throws MessagingException
    {
        Object result = null;
        if (fetchList.containsKey("FLAGS")) {
            ImapList flags = fetchList.getKeyedList("FLAGS");
            if (flags != null) {
                for (int i = 0, count = flags.size(); i < count; i++) {
                    String flag = flags.getString(i);
                    if (flag.equalsIgnoreCase("\\Deleted")) {
                        message.setFlagInternal(Flag.DELETED, true);
                    }
                    else if (flag.equalsIgnoreCase("\\Answered")) {
                        message.setFlagInternal(Flag.ANSWERED, true);
                    }
                    else if (flag.equalsIgnoreCase("\\Seen")) {
                        message.setFlagInternal(Flag.SEEN, true);
                    }
                    else if (flag.equalsIgnoreCase("\\Flagged")) {
                        message.setFlagInternal(Flag.FLAGGED, true);
                    }
                    else if (flag.equalsIgnoreCase("$Forwarded")) {
                        message.setFlagInternal(Flag.FORWARDED, true);
                        /* a message contains FORWARDED FLAG -> so we can also create them */
                        mStore.getPermanentFlagsIndex().add(Flag.FORWARDED);
                    }
                }
            }
        }

        if (fetchList.containsKey("INTERNALDATE")) {
            Date internalDate = fetchList.getKeyedDate("INTERNALDATE");
            message.setInternalDate(internalDate);
        }

        if (fetchList.containsKey("RFC822.SIZE")) {
            int size = fetchList.getKeyedNumber("RFC822.SIZE");
            message.setSize(size);
        }

        if (fetchList.containsKey("BODYSTRUCTURE")) {
            ImapList bs = fetchList.getKeyedList("BODYSTRUCTURE");
            if (bs != null) {
                try {
                    parseBodyStructure(bs, message, "TEXT");
                } catch (MessagingException e) {
                    if (XryptoMailLib.isDebug())
                        Timber.d(e, "Error handling message for %s", getLogId());
                    message.setBody(null);
                }
            }
        }

        if (fetchList.containsKey("BODY")) {
            int index = fetchList.getKeyIndex("BODY") + 2;
            int size = fetchList.size();
            if (index < size) {
                result = fetchList.getObject(index);

                // Check if there's an origin octet
                if (result instanceof String) {
                    String originOctet = (String) result;
                    if (originOctet.startsWith("<") && (index + 1) < size) {
                        result = fetchList.getObject(index + 1);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Handle any untagged responses that the caller doesn't care to handle themselves.
     *
     * @param responses
     */
    protected List<ImapResponse> handleUntaggedResponses(List<ImapResponse> responses)
    {
        for (ImapResponse response : responses) {
            handleUntaggedResponse(response);
        }
        return responses;
    }

    protected void handlePossibleUidNext(ImapResponse response)
    {
        if (ImapResponseParser.equalsIgnoreCase(response.get(0), "OK") && response.size() > 1) {
            Object bracketedObj = response.get(1);
            if (bracketedObj instanceof ImapList) {
                ImapList bracketed = (ImapList) bracketedObj;

                if (bracketed.size() > 1) {
                    Object keyObj = bracketed.get(0);
                    if (keyObj instanceof String) {
                        String key = (String) keyObj;
                        if ("UIDNEXT".equalsIgnoreCase(key)) {
                            uidNext = bracketed.getLong(1);
                            if (XryptoMailLib.isDebug())
                                Timber.d("Got UidNext = %s for %s", uidNext, getLogId());
                        }
                    }
                }

            }
        }
    }

    /**
     * Handle an untagged response that the caller doesn't care to handle themselves.
     *
     * @param response
     */
    protected void handleUntaggedResponse(ImapResponse response)
    {
        if (!response.isTagged() && response.size() > 1) {
            if (ImapResponseParser.equalsIgnoreCase(response.get(1), "EXISTS")) {
                mMessageCount = response.getNumber(0);
                if (XryptoMailLib.isDebug()) {
                    Timber.d("Got untagged EXISTS with value %d for %s", mMessageCount, getLogId());
                }
            }
            handlePossibleUidNext(response);

            if (ImapResponseParser.equalsIgnoreCase(response.get(1), "EXPUNGE") && mMessageCount > 0) {
                mMessageCount--;
                if (XryptoMailLib.isDebug()) {
                    Timber.d("Got untagged EXPUNGE with messageCount %d for %s", mMessageCount, getLogId());
                }
            }
        }
    }

    private void parseBodyStructure(ImapList bs, Part part, String id)
            throws MessagingException
    {
        if (bs.get(0) instanceof ImapList) {
			/*
			 * This is a multipart/*
			 */
            MimeMultipart mp = MimeMultipart.newInstance();
            for (int i = 0, count = bs.size(); i < count; i++) {
                if (bs.get(i) instanceof ImapList) {
					/*
					 * For each part in the message we're going to add a new
					 * BodyPart and parse into it.
					 */
                    MimeBodyPart bp = new MimeBodyPart();
                    if (id.equalsIgnoreCase("TEXT")) {
                        parseBodyStructure(bs.getList(i), bp, Integer.toString(i + 1));
                    }
                    else {
                        parseBodyStructure(bs.getList(i), bp, id + "." + (i + 1));
                    }
                    mp.addBodyPart(bp);
                }
                else {
					/*
					 * We've got to the end of the children of the part, so
					 * now we can find out what type it is and bail out.
					 */
                    String subType = bs.getString(i);
                    mp.setSubType(subType.toLowerCase(Locale.US));
                    break;
                }
            }
            MimeMessageHelper.setBody(part, mp);
        }
        else {
			/*
			 * This is a body. We need to add as much information as we can
			 * find out about it to the Part.
			 */

            /*
             *  0| 0  body type
             *  1| 1  body subtype
             *  2| 2  body parameter parenthesized list
             *  3| 3  body id (unused)
             *  4| 4  body description (unused)
             *  5| 5  body encoding
             *  6| 6  body size
             *  -| 7  text lines (only for type TEXT, unused)
             * Extensions (optional):
             *  7| 8  body MD5 (unused)
             *  8| 9  body disposition
             *  9|10  body language (unused)
             * 10|11  body location (unused)
             */

            String type = bs.getString(0);
            String subType = bs.getString(1);
            String mimeType = (type + "/" + subType).toLowerCase(Locale.US);

            ImapList bodyParams = null;
            if (bs.get(2) instanceof ImapList) {
                bodyParams = bs.getList(2);
            }
            String encoding = bs.getString(5);
            int size = bs.getNumber(6);

            if (MimeUtility.isMessage(mimeType)) {
//                  A body type of type MESSAGE and subtype RFC822
//                  contains, immediately after the basic fields, the
//                  envelope structure, body structure, and size in
//                  text lines of the encapsulated message.
//                    [MESSAGE, RFC822, [NAME, Fwd: [#HTR-517941]:  update plans at 1am Friday - Memory allocation - displayware.eml], NIL, NIL, 7BIT, 5974, NIL, [INLINE, [FILENAME*0, Fwd: [#HTR-517941]:  update plans at 1am Friday - Memory all, FILENAME*1, ocation - displayware.eml]], NIL]
                /*
				/*
				 * This will be caught by fetch and handled appropriately.
				 */
                throw new MessagingException("BODYSTRUCTURE message/rfc822 not yet supported.");
            }

			/*
			 * Set the content type with as much information as we know right now.
			 */
            StringBuilder contentType = new StringBuilder();
            contentType.append(mimeType);

            if (bodyParams != null) {
				/*
				 * If there are body params we might be able to get some
				 * more information out of them.
				 */
                for (int i = 0, count = bodyParams.size(); i < count; i += 2) {
                    String paramName = bodyParams.getString(i);
                    String paramValue = bodyParams.getString(i + 1);
                    contentType.append(String.format(";\r\n %s=\"%s\"", paramName, paramValue));
                }
            }

            part.setHeader(MimeHeader.HEADER_CONTENT_TYPE, contentType.toString());

            // Extension items
            ImapList bodyDisposition = null;
            if (("text".equalsIgnoreCase(type)) && (bs.size() > 9) && (bs.get(9) instanceof ImapList)) {
                bodyDisposition = bs.getList(9);
            }
            else if (!("text".equalsIgnoreCase(type)) && (bs.size() > 8) && (bs.get(8) instanceof ImapList)) {
                bodyDisposition = bs.getList(8);
            }

            StringBuilder contentDisposition = new StringBuilder();

            if (bodyDisposition != null && !bodyDisposition.isEmpty()) {
                if (!"NIL".equalsIgnoreCase(bodyDisposition.getString(0))) {
                    contentDisposition.append(bodyDisposition.getString(0).toLowerCase(Locale.US));
                }

                if ((bodyDisposition.size() > 1) && (bodyDisposition.get(1) instanceof ImapList)) {
                    ImapList bodyDispositionParams = bodyDisposition.getList(1);
					/*
					 * If there is body disposition information we can pull
					 * some more information about the attachment out.
					 */
                    for (int i = 0, count = bodyDispositionParams.size(); i < count; i += 2) {
                        String paramName = bodyDispositionParams.getString(i).toLowerCase(Locale.US);
                        String paramValue = bodyDispositionParams.getString(i + 1);
                        contentDisposition.append(String.format(";\r\n %s=\"%s\"", paramName, paramValue));
                    }
                }
            }

            if (MimeUtility.getHeaderParameter(contentDisposition.toString(), "size") == null) {
                contentDisposition.append(String.format(Locale.US, ";\r\n size=%d", size));
            }

			/*
			 * Set the content disposition containing at least the size.
			 * Attachment handling code will use this down the road.
			 */
            part.setHeader(MimeHeader.HEADER_CONTENT_DISPOSITION, contentDisposition.toString());

			/*
			 * Set the Content-Transfer-Encoding header. Attachment code
			 * will use this to parse the body.
			 */
            part.setHeader(MimeHeader.HEADER_CONTENT_TRANSFER_ENCODING, encoding);

            if (part instanceof ImapMessage) {
                ((ImapMessage) part).setSize(size);
            }
            part.setServerExtra(id);
        }
    }

    /**
     * Appends the given messages to the selected folder.
     *
     * <p>
     * This implementation also determines the new UIDs of the given messages on the IMAP
     * server and changes the messages' UIDs to the new server UIDs.
     * </p>
     *
     * @param messages The messages to append to the folder.
     * @return The mapping of original message UIDs to the new server UIDs.
     */
    @Override
    public Map<String, String> appendMessages(List<? extends Message> messages)
            throws MessagingException
    {
        open(OPEN_MODE_RW);
        checkOpen();

        try {
            Map<String, String> uidMap = new HashMap<>();
            for (Message message : messages) {
                long messageSize = message.calculateSize();

                String encodeFolderName = folderNameCodec.encode(getPrefixedName());
                String escapedFolderName = ImapUtility.encodeString(encodeFolderName);
                String combinedFlags = ImapUtility.combineFlags(message.getFlags(),
                        canCreateKeywords || mStore.getPermanentFlagsIndex().contains(Flag.FORWARDED));
                String command = String.format(Locale.US, "APPEND %s (%s) {%d}", escapedFolderName,
                        combinedFlags, messageSize);
                mConnection.sendCommand(command, false);

                ImapResponse response;
                do {
                    response = mConnection.readResponse();
                    handleUntaggedResponse(response);
                    if (response.isContinuationRequested()) {
                        EOLConvertingOutputStream eolOut = new EOLConvertingOutputStream(mConnection.getOutputStream());
                        message.writeTo(eolOut);
                        eolOut.write('\r');
                        eolOut.write('\n');
                        eolOut.flush();
                    }
                } while (!response.isTagged());

                if (response.size() > 1) {
					/*
					 * If the server supports UIDPLUS, then along with the APPEND response it 
					 * will return an APPENDUID response code, e.g.
					 * 
					 * 11 OK [APPENDUID 2 238268] APPEND completed
					 * 
					 * We can use the UID included in this response to update our records.
					 */
                    Object responseList = response.get(1);
                    if (responseList instanceof ImapList) {
                        ImapList appendList = (ImapList) responseList;
                        if ((appendList.size() >= 3) && appendList.getString(0).equals("APPENDUID")) {

                            String newUid = appendList.getString(2);

                            if (!TextUtils.isEmpty(newUid)) {
                                message.setUid(newUid);
                                uidMap.put(message.getUid(), newUid);
                                continue;
                            }
                        }
                    }
                }

				/*
				 * This part is executed in case the server does not support
				 * UIDPLUS or does not implement the APPENDUID response code.
				 */
                String newUid = getUidFromMessageId(message);
                if (XryptoMailLib.isDebug()) {
                    Timber.d("Got UID %s for message for %s", newUid, getLogId());
                }

                if (!TextUtils.isEmpty(newUid)) {
                    uidMap.put(message.getUid(), newUid);
                    message.setUid(newUid);
                }
            }

			/*
			 * We need uidMap to be null if new UIDs are not available to
			 * maintain consistency with the behavior of other similar
			 * methods (copyMessages, moveMessages) which return null.
			 */
            return (uidMap.isEmpty()) ? null : uidMap;
        } catch (IOException ioe) {
            throw ioExceptionHandler(mConnection, ioe);
        }
    }

    @Override
    public String getUidFromMessageId(Message message)
            throws MessagingException
    {
		/*
		 * Try to find the UID of the message we just appended using the
		 * Message-ID header.
		 */
        String[] messageIdHeader = message.getHeader("Message-ID");
        if ((messageIdHeader == null) || (messageIdHeader.length == 0)) {
            if (XryptoMailLib.isDebug())
                Timber.d("Did not get a message-id in order to search for UID  for %s", getLogId());
            return null;
        }
        String messageId = messageIdHeader[0];
        if (XryptoMailLib.isDebug())
            Timber.d("Looking for UID for message with message-id %s for %s", messageId, getLogId());

        String command = String.format("UID SEARCH HEADER MESSAGE-ID %s", ImapUtility.encodeString(messageId));

        List<ImapResponse> imapResponses;
        try {
            imapResponses = executeSimpleCommand(command);
        } catch (IOException ioe) {
            throw ioExceptionHandler(mConnection, ioe);
        }

        SearchResponse searchResponse = SearchResponse.parse(imapResponses);
        List<Long> uids = searchResponse.getNumbers();
        if (uids.size() > 0) {
            return Long.toString(uids.get(0));
        }
        return null;
    }

    @Override
    public void expunge()
            throws MessagingException
    {
        open(OPEN_MODE_RW);
        checkOpen();
        try {
            executeSimpleCommand("EXPUNGE");
        } catch (IOException ioe) {
            throw ioExceptionHandler(mConnection, ioe);
        }
    }

    @Override
    public void expungeUids(List<String> uids)
            throws MessagingException
    {
        if (uids == null || uids.isEmpty()) {
            throw new IllegalArgumentException("expungeUids() must be called with a non-empty set of UIDs");
        }

        open(OPEN_MODE_RW);
        checkOpen();

        try {
            if (mConnection.isUidPlusCapable()) {
                Set<Long> longUids = new HashSet<>(uids.size());
                for (String uid : uids) {
                    longUids.add(Long.parseLong(uid));
                }
                mConnection.executeCommandWithIdSet(Commands.UID_EXPUNGE, "", longUids);
            }
            else {
                executeSimpleCommand("EXPUNGE");
            }
        } catch (IOException ioe) {
            throw ioExceptionHandler(mConnection, ioe);
        }
    }

    @Override
    public void setFlags(Set<Flag> flags, boolean value)
            throws MessagingException
    {
        open(OPEN_MODE_RW);
        checkOpen();

        boolean canCreateForwardedFlag = canCreateKeywords ||
                mStore.getPermanentFlagsIndex().contains(Flag.FORWARDED);

        try {
            String combinedFlags = ImapUtility.combineFlags(flags, canCreateForwardedFlag);
            String command = String.format("%s 1:* %sFLAGS.SILENT (%s)",
                    Commands.UID_STORE, value ? "+" : "-", combinedFlags);
            executeSimpleCommand(command);
        } catch (IOException ioe) {
            throw ioExceptionHandler(mConnection, ioe);
        }
    }

    @Override
    public String getNewPushState(String oldSerializedPushState, Message message)
    {
        try {
            String uid = message.getUid();
            long messageUid = Long.parseLong(uid);

            ImapPushState oldPushState = ImapPushState.parse(oldSerializedPushState);
            if (messageUid >= oldPushState.uidNext) {
                long uidNext = messageUid + 1;
                ImapPushState newPushState = new ImapPushState(uidNext);
                return newPushState.toString();
            }
            else {
                return null;
            }
        } catch (Exception e) {
            Timber.e(e, "Exception while updated push state for %s", getLogId());
            return null;
        }
    }

    @Override
    public void setFlags(List<? extends Message> messages, final Set<Flag> flags, boolean value)
            throws MessagingException
    {
        open(OPEN_MODE_RW);
        checkOpen();

        Set<Long> uids = new HashSet<>(messages.size());
        for (Message message : messages) {
            uids.add(Long.parseLong(message.getUid()));
        }

        boolean canCreateForwardedFlag = canCreateKeywords ||
                mStore.getPermanentFlagsIndex().contains(Flag.FORWARDED);

        String combinedFlags = ImapUtility.combineFlags(flags, canCreateForwardedFlag);
        String commandSuffix = String.format("%sFLAGS.SILENT (%s)", value ? "+" : "-", combinedFlags);

        try {
            mConnection.executeCommandWithIdSet(Commands.UID_STORE, commandSuffix, uids);
        } catch (IOException ioe) {
            throw ioExceptionHandler(mConnection, ioe);
        }
    }

    private void checkOpen()
            throws MessagingException
    {
        if (!isOpen()) {
            throw new MessagingException("Folder " + getPrefixedName() + " is not open.");
        }
    }

    private MessagingException ioExceptionHandler(ImapConnection connection, IOException ioe)
    {
        Timber.e(ioe, "IOException for %s", getLogId());
        if (connection != null) {
            connection.close();
        }
        close();
        return new MessagingException("IO Error", ioe);
    }

    @Override
    public boolean equals(Object other)
    {
        if (other instanceof ImapFolder) {
            ImapFolder otherFolder = (ImapFolder) other;
            return otherFolder.getName().equalsIgnoreCase(getName());
        }
        return super.equals(other);
    }

    @Override
    public int hashCode()
    {
        return getName().hashCode();
    }

    private ImapStore getStore()
    {
        return mStore;
    }

    protected String getLogId()
    {
        String id = mStore.getStoreConfig().toString() + ":" + getName() + "/" + Thread.currentThread().getName();
        if (mConnection != null) {
            id += "/" + mConnection.getLogId();
        }
        return id;
    }

    /**
     * Search the remote ImapFolder.
     *
     * @param queryString String to query for.
     * @param requiredFlags Mandatory flags
     * @param forbiddenFlags Flags to exclude
     * @return List of messages found
     * @throws MessagingException On any error.
     */
    @Override
    public List<ImapMessage> search(final String queryString, final Set<Flag> requiredFlags,
            final Set<Flag> forbiddenFlags)
            throws MessagingException
    {

        if (!mStore.getStoreConfig().allowRemoteSearch()) {
            throw new MessagingException("Your settings do not allow remote searching of this account");
        }

        try {
            open(OPEN_MODE_RO);
            checkOpen();

            mInSearch = true;

            String searchCommand = new UidSearchCommandBuilder()
                    .queryString(queryString)
                    .performFullTextSearch(mStore.getStoreConfig().isRemoteSearchFullText())
                    .requiredFlags(requiredFlags)
                    .forbiddenFlags(forbiddenFlags)
                    .build();

            try {
                List<ImapResponse> imapResponses = executeSimpleCommand(searchCommand);
                SearchResponse searchResponse = SearchResponse.parse(imapResponses);
                return getMessages(searchResponse, null);
            } catch (IOException ioe) {
                throw ioExceptionHandler(mConnection, ioe);
            }
        } finally {
            mInSearch = false;
        }
    }
}
