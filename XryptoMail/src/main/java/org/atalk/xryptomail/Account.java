package org.atalk.xryptomail;

import static org.atalk.xryptomail.Preferences.getEnumStringPref;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.atalk.xryptomail.activity.AccountConfig;
import org.atalk.xryptomail.activity.setup.CheckDirection;
import org.atalk.xryptomail.helper.EmailHelper;
import org.atalk.xryptomail.helper.Utility;
import org.atalk.xryptomail.mail.Address;
import org.atalk.xryptomail.mail.AuthType;
import org.atalk.xryptomail.mail.ConnectionSecurity;
import org.atalk.xryptomail.mail.Folder.FolderClass;
import org.atalk.xryptomail.mail.MessagingException;
import org.atalk.xryptomail.mail.NetworkType;
import org.atalk.xryptomail.mail.ServerSettings;
import org.atalk.xryptomail.mail.Store;
import org.atalk.xryptomail.mail.TransportUris;
import org.atalk.xryptomail.mail.filter.Base64;
import org.atalk.xryptomail.mail.ssl.LocalKeyStore;
import org.atalk.xryptomail.mail.store.RemoteStore;
import org.atalk.xryptomail.mailstore.LocalStore;
import org.atalk.xryptomail.mailstore.StorageManager;
import org.atalk.xryptomail.mailstore.StorageManager.StorageProvider;
import org.atalk.xryptomail.preferences.Storage;
import org.atalk.xryptomail.preferences.StorageEditor;
import org.atalk.xryptomail.provider.EmailProvider;
import org.atalk.xryptomail.provider.EmailProvider.StatsColumns;
import org.atalk.xryptomail.search.ConditionsTreeNode;
import org.atalk.xryptomail.search.LocalSearch;
import org.atalk.xryptomail.search.SearchSpecification.Attribute;
import org.atalk.xryptomail.search.SearchSpecification.SearchCondition;
import org.atalk.xryptomail.search.SearchSpecification.SearchField;
import org.atalk.xryptomail.search.SqlQueryBuilder;
import org.atalk.xryptomail.view.ColorChip;
import org.atalk.xryptomail.view.ColorPicker;

import timber.log.Timber;

/**
 * Account stores all of the settings for a single account defined by the user. It is able to save
 * and delete itself given a Preferences to work with. Each account is defined by a UUID.
 */
public class Account implements BaseAccount, AccountConfig {
    /**
     * Default value for the inbox folder (never changes for POP3 and IMAP)
     */
    public static final String INBOX = "INBOX";

    /**
     * This local folder is used to store messages to be sent.
     */
    public static final String OUTBOX = "XryptoMail_INTERNAL_OUTBOX";

    public enum Expunge {
        EXPUNGE_IMMEDIATELY,
        EXPUNGE_MANUALLY,
        EXPUNGE_ON_POLL
    }

    /**
     * <pre>
     * 0 - Never (DELETE_POLICY_NEVER)
     * 1 - After 7 days (DELETE_POLICY_7DAYS)
     * 2 - When I delete from inbox (DELETE_POLICY_ON_DELETE)
     * 3 - Mark as read (DELETE_POLICY_MARK_AS_READ)
     * </pre>
     */
    public enum DeletePolicy {
        NEVER(0),
        SEVEN_DAYS(1),
        ON_DELETE(2),
        MARK_AS_READ(3);

        public final int setting;

        DeletePolicy(int setting) {
            this.setting = setting;
        }

        public String preferenceString() {
            return Integer.toString(setting);
        }

        public static DeletePolicy fromInt(int initialSetting) {
            for (DeletePolicy policy : values()) {
                if (policy.setting == initialSetting) {
                    return policy;
                }
            }
            throw new IllegalArgumentException("DeletePolicy " + initialSetting + " unknown");
        }
    }

    public static final MessageFormat DEFAULT_MESSAGE_FORMAT = MessageFormat.HTML;
    public static final boolean DEFAULT_MESSAGE_FORMAT_AUTO = false;
    public static final boolean DEFAULT_MESSAGE_READ_RECEIPT = false;
    public static final QuoteStyle DEFAULT_QUOTE_STYLE = QuoteStyle.PREFIX;
    public static final String DEFAULT_QUOTE_PREFIX = ">";
    public static final boolean DEFAULT_QUOTED_TEXT_SHOWN = true;
    public static final boolean DEFAULT_REPLY_AFTER_QUOTE = false;
    public static final boolean DEFAULT_STRIP_SIGNATURE = true;
    public static final boolean XRYPTO_MODE = true;
    public static final int DEFAULT_REMOTE_SEARCH_NUM_RESULTS = 25;

    // Default mail polling interval = 30 minutes
    private static final int DEFAULT_POLL_INTERVAL = 30;

    public static final String ACCOUNT_DESCRIPTION_KEY = "description";
    public static final String STORE_URI_KEY = "storeUri";
    public static final String TRANSPORT_URI_KEY = "transportUri";

    public static final String IDENTITY_NAME_KEY = "name";
    public static final String IDENTITY_EMAIL_KEY = "email";
    public static final String IDENTITY_DESCRIPTION_KEY = "description";

    /*
     * http://developer.android.com/design/style/color.html
     * Note: Order does matter, it's the order in which they will be picked.
     */
    private static final Integer[] PREDEFINED_COLORS = new Integer[]{
            Color.parseColor("#0099CC"),    // blue
            Color.parseColor("#669900"),    // green
            Color.parseColor("#FF8800"),    // orange
            Color.parseColor("#CC0000"),    // red
            Color.parseColor("#9933CC")     // purple
    };

    public enum SortType {
        SORT_DATE(R.string.sort_earliest_first, R.string.sort_latest_first, false),
        SORT_ARRIVAL(R.string.sort_earliest_first, R.string.sort_latest_first, false),
        SORT_SUBJECT(R.string.sort_subject_alpha, R.string.sort_subject_re_alpha, true),
        SORT_SENDER(R.string.sort_sender_alpha, R.string.sort_sender_re_alpha, true),
        SORT_UNREAD(R.string.sort_unread_first, R.string.sort_unread_last, true),
        SORT_FLAGGED(R.string.sort_flagged_first, R.string.sort_flagged_last, true),
        SORT_ATTACHMENT(R.string.sort_attach_first, R.string.sort_unattached_first, true);

        private final int ascendingToast;
        private final int descendingToast;
        private final boolean defaultAscending;

        SortType(int ascending, int descending, boolean ndefaultAscending) {
            ascendingToast = ascending;
            descendingToast = descending;
            defaultAscending = ndefaultAscending;
        }

        public int getToast(boolean ascending) {
            return (ascending) ? ascendingToast : descendingToast;
        }

        public boolean isDefaultAscending() {
            return defaultAscending;
        }
    }

    public static final SortType DEFAULT_SORT_TYPE = SortType.SORT_DATE;
    public static final boolean DEFAULT_SORT_ASCENDING = false;
    public static final long NO_OPENPGP_KEY = 0;

    private DeletePolicy mDeletePolicy = DeletePolicy.NEVER;

    private final String mAccountUuid;
    private String mStoreUri;

    /**
     * Storage provider ID, used to locate and manage the underlying DB/file
     * storage
     */
    private String mLocalStorageProviderId;
    private String mTransportUri;
    private String mDescription;
    private String mAlwaysBcc;
    private int mAutomaticCheckIntervalMinutes;
    private int mDisplayCount;
    private int mChipColor;
    private long mLatestOldMessageSeenTime;
    private boolean mNotifyNewMail;
    private FolderMode mFolderNotifyNewMailMode;
    private boolean mNotifySelfNewMail;
    private boolean mNotifyContactsMailOnly;
    private String mInboxFolder;
    private String mDraftsFolder;
    private String mSentFolder;
    private String mTrashFolder;
    private String mArchiveFolder;
    private String mSpamFolder;
    private String mAutoExpandFolder;
    private FolderMode mFolderDisplayMode;
    private FolderMode mFolderSyncMode;
    private FolderMode mFolderPushMode;
    private FolderMode mFolderTargetMode;
    private int mAccountNumber;
    private boolean mPushPollOnConnect;
    private boolean mNotifySync;
    private SortType mSortType;
    private final Map<SortType, Boolean> mSortAscending = new HashMap<>();
    private ShowPictures mShowPictures;
    private boolean mIsSignatureBeforeQuotedText;
    private Expunge mExpungePolicy = Expunge.EXPUNGE_IMMEDIATELY;
    private int mMaxPushFolders;
    private int mIdleRefreshMinutes;
    private boolean goToUnreadMessageSearch;
    private final Map<NetworkType, Boolean> compressionMap = new ConcurrentHashMap<>();
    private Searchable searchableFolders;
    private boolean subscribedFoldersOnly;
    private int maximumPolledMessageAge;
    private int maximumAutoDownloadMessageSize;
    // Tracks if we have sent a notification for this account for current set of fetched messages
    private boolean mRingNotified;
    private MessageFormat mMessageFormat;
    private boolean mMessageFormatAuto;
    private boolean mMessageReadReceipt;
    private QuoteStyle mQuoteStyle;
    private String mQuotePrefix;
    private boolean mDefaultQuotedTextShown;
    private boolean mReplyAfterQuote;
    private boolean mStripSignature;
    private boolean mSyncRemoteDeletions;
    private boolean mStealthMode;
    private long mPgpCryptoKey;
    private boolean autocryptPreferEncryptMutual;
    private boolean mMarkMessageAsReadOnView;
    private boolean mAlwaysShowCcBcc;
    private boolean mAllowRemoteSearch;
    private boolean mRemoteSearchFullText;
    private int mRemoteSearchNumResults;

    private ColorChip mUnreadColorChip;
    private ColorChip mReadColorChip;

    private ColorChip mFlaggedUnreadColorChip;
    private ColorChip mFlaggedReadColorChip;

    /**
     * Indicates whether this account is enabled, i.e. ready for use, or not.
     *
     * <p>
     * Right now newly imported accounts are disabled if the settings file
     * didn't contain a password for the incoming and/or outgoing server.
     * </p>
     */
    private boolean mEnabled;

    /**
     * Name of the folder that was last selected for a copy or move operation.
     *
     * Note: For now this value isn't persisted. So it will be reset when K-9
     * Mail is restarted.
     */
    private String lastSelectedFolderName = null;
    private List<Identity> identities;
    private NotificationSetting mNotificationSetting = new NotificationSetting();

    public enum FolderMode {
        NONE, ALL, FIRST_CLASS, FIRST_AND_SECOND_CLASS, NOT_SECOND_CLASS
    }

    public enum ShowPictures {
        NEVER, ALWAYS, ONLY_FROM_CONTACTS
    }

    public enum Searchable {
        ALL, DISPLAYABLE, NONE
    }

    public enum QuoteStyle {
        PREFIX, HEADER
    }

    public enum MessageFormat {
        TEXT, HTML, AUTO
    }

    protected Account(Context context) {
        mAccountUuid = UUID.randomUUID().toString();
        mLocalStorageProviderId = StorageManager.getInstance(context).getDefaultProviderId();
        mAutomaticCheckIntervalMinutes = -1;
        mIdleRefreshMinutes = 24;
        mPushPollOnConnect = true;
        mDisplayCount = XryptoMail.DEFAULT_VISIBLE_LIMIT;
        mAccountNumber = -1;
        mNotifyNewMail = true;
        mFolderNotifyNewMailMode = FolderMode.ALL;
        mNotifySync = true;
        mNotifySelfNewMail = true;
        mNotifyContactsMailOnly = false;
        mFolderDisplayMode = FolderMode.NOT_SECOND_CLASS;
        mFolderSyncMode = FolderMode.FIRST_CLASS;
        mFolderPushMode = FolderMode.FIRST_CLASS;
        mFolderTargetMode = FolderMode.NOT_SECOND_CLASS;
        mSortType = DEFAULT_SORT_TYPE;
        mSortAscending.put(DEFAULT_SORT_TYPE, DEFAULT_SORT_ASCENDING);
        mShowPictures = ShowPictures.ALWAYS;
        mIsSignatureBeforeQuotedText = false;
        mExpungePolicy = Expunge.EXPUNGE_IMMEDIATELY;
        mAutoExpandFolder = INBOX;
        mInboxFolder = INBOX;
        mMaxPushFolders = 10;
        mChipColor = pickColor(context);
        goToUnreadMessageSearch = false;
        subscribedFoldersOnly = false;
        maximumPolledMessageAge = -1;
        maximumAutoDownloadMessageSize = 0;
        mMessageFormat = DEFAULT_MESSAGE_FORMAT;
        mMessageFormatAuto = DEFAULT_MESSAGE_FORMAT_AUTO;
        mMessageReadReceipt = DEFAULT_MESSAGE_READ_RECEIPT;
        mQuoteStyle = DEFAULT_QUOTE_STYLE;
        mQuotePrefix = DEFAULT_QUOTE_PREFIX;
        mDefaultQuotedTextShown = DEFAULT_QUOTED_TEXT_SHOWN;
        mReplyAfterQuote = DEFAULT_REPLY_AFTER_QUOTE;
        mStripSignature = DEFAULT_STRIP_SIGNATURE;
        mSyncRemoteDeletions = true;
        mStealthMode = false;
        mPgpCryptoKey = NO_OPENPGP_KEY;
        mAllowRemoteSearch = false;
        mRemoteSearchFullText = false;
        mRemoteSearchNumResults = DEFAULT_REMOTE_SEARCH_NUM_RESULTS;
        mEnabled = true;
        mMarkMessageAsReadOnView = true;
        mAlwaysShowCcBcc = false;

        searchableFolders = Searchable.ALL;
        identities = new ArrayList<>();

        Identity identity = new Identity();
        identity.setSignatureUse(true);
        identity.setSignature(context.getString(R.string.default_signature));
        identity.setDescription(context.getString(R.string.default_identity_description));
        identities.add(identity);

        mNotificationSetting = new NotificationSetting();
        mNotificationSetting.setVibrate(false);
        mNotificationSetting.setVibratePattern(0);
        mNotificationSetting.setVibrateTimes(5);
        mNotificationSetting.setRingEnabled(true);
        mNotificationSetting.setRingtone("content://settings/system/notification_sound");
        mNotificationSetting.setLedColor(mChipColor);
        cacheChips();
    }

    public void loadConfig(AccountConfig accountConfig) {
        setName(accountConfig.getName());
        setEmail(accountConfig.getEmail());
        setStoreUri(accountConfig.getStoreUri());
        setTransportUri(accountConfig.getTransportUri());
        setDescription(accountConfig.getDescription());
        setInboxFolder(accountConfig.getInboxFolder());
        setDraftsFolder(accountConfig.getDraftsFolderName());
        setDisplayCount(accountConfig.getDisplayCount());
        setAllowRemoteSearch(accountConfig.isAllowRemoteSearch());
        setAutomaticCheckIntervalMinutes(accountConfig.getAutomaticCheckIntervalMinutes());
        setDeletePolicy(accountConfig.getDeletePolicy());
        setCompression(NetworkType.WIFI, accountConfig.useCompression(NetworkType.WIFI));
        setCompression(NetworkType.MOBILE, accountConfig.useCompression(NetworkType.MOBILE));
        setCompression(NetworkType.OTHER, accountConfig.useCompression(NetworkType.OTHER));
        setFolderPushMode(accountConfig.getFolderPushMode());
        setNotifyNewMail(accountConfig.isNotifyNewMail());
        setShowOngoing(accountConfig.isShowOngoing());
    }

    /*
     * Pick a nice Android guidelines color if we haven't used them all yet.
     */
    private int pickColor(Context context) {
        List<Account> accounts = Preferences.getPreferences(context).getAccounts();

        List<Integer> availableColors = new ArrayList<>(PREDEFINED_COLORS.length);
        Collections.addAll(availableColors, PREDEFINED_COLORS);

        for (Account account : accounts) {
            Integer color = account.getChipColor();
            if (availableColors.contains(color)) {
                availableColors.remove(color);
                if (availableColors.isEmpty()) {
                    break;
                }
            }
        }
        return (availableColors.isEmpty()) ? ColorPicker.getRandomColor() : availableColors.get(0);
    }

    protected Account(Preferences preferences, String uuid) {
        this.mAccountUuid = uuid;
        loadAccount(preferences);
    }

    /**
     * Load stored settings for this account.
     */
    private synchronized void loadAccount(Preferences preferences) {
        Storage storage = preferences.getStorage();

        mStoreUri = Base64.decode(storage.getString(mAccountUuid + ".storeUri", null));
        mLocalStorageProviderId = storage.getString(mAccountUuid + ".localStorageProvider", StorageManager.getInstance(XryptoMail.mInstance).getDefaultProviderId());
        mTransportUri = Base64.decode(storage.getString(mAccountUuid + ".transportUri", null));
        mDescription = storage.getString(mAccountUuid + ".description", null);
        mAlwaysBcc = storage.getString(mAccountUuid + ".alwaysBcc", mAlwaysBcc);
        mAutomaticCheckIntervalMinutes = storage.getInt(mAccountUuid + ".automaticCheckIntervalMinutes", DEFAULT_POLL_INTERVAL);
        mIdleRefreshMinutes = storage.getInt(mAccountUuid + ".idleRefreshMinutes", 24);
        mPushPollOnConnect = storage.getBoolean(mAccountUuid + ".pushPollOnConnect", true);
        mDisplayCount = storage.getInt(mAccountUuid + ".displayCount", XryptoMail.DEFAULT_VISIBLE_LIMIT);
        if (mDisplayCount < 0) {
            mDisplayCount = XryptoMail.DEFAULT_VISIBLE_LIMIT;
        }
        mLatestOldMessageSeenTime = storage.getLong(mAccountUuid + ".latestOldMessageSeenTime", 0);
        mNotifyNewMail = storage.getBoolean(mAccountUuid + ".notifyNewMail", true);
        mFolderNotifyNewMailMode = getEnumStringPref(storage, mAccountUuid + ".folderNotifyNewMailMode", FolderMode.ALL);
        mNotifySelfNewMail = storage.getBoolean(mAccountUuid + ".notifySelfNewMail", true);
        mNotifyContactsMailOnly = storage.getBoolean(mAccountUuid + ".notifyContactsMailOnly", false);
        mNotifySync = storage.getBoolean(mAccountUuid + ".notifyMailCheck", false);
        mDeletePolicy = DeletePolicy.fromInt(storage.getInt(mAccountUuid + ".deletePolicy", DeletePolicy.NEVER.setting));
        mInboxFolder = storage.getString(mAccountUuid + ".inboxFolderName", INBOX);
        mDraftsFolder = storage.getString(mAccountUuid + ".draftsFolderName", "Drafts");
        mSentFolder = storage.getString(mAccountUuid + ".sentFolderName", "Sent");
        mTrashFolder = storage.getString(mAccountUuid + ".trashFolderName", "Trash");
        mArchiveFolder = storage.getString(mAccountUuid + ".archiveFolderName", "Archive");
        mSpamFolder = storage.getString(mAccountUuid + ".spamFolderName", "Spam");
        mExpungePolicy = getEnumStringPref(storage, mAccountUuid + ".expungePolicy", Expunge.EXPUNGE_IMMEDIATELY);
        mSyncRemoteDeletions = storage.getBoolean(mAccountUuid + ".syncRemoteDeletions", true);

        mMaxPushFolders = storage.getInt(mAccountUuid + ".maxPushFolders", 10);
        goToUnreadMessageSearch = storage.getBoolean(mAccountUuid + ".goToUnreadMessageSearch", false);
        subscribedFoldersOnly = storage.getBoolean(mAccountUuid + ".isSubscribedFoldersOnly", false);
        maximumPolledMessageAge = storage.getInt(mAccountUuid + ".maximumPolledMessageAge", -1);
        maximumAutoDownloadMessageSize = storage.getInt(mAccountUuid + ".maximumAutoDownloadMessageSize", 32768);
        mMessageFormat = getEnumStringPref(storage, mAccountUuid + ".messageFormat", DEFAULT_MESSAGE_FORMAT);
        mMessageFormatAuto = storage.getBoolean(mAccountUuid + ".messageFormatAuto", DEFAULT_MESSAGE_FORMAT_AUTO);
        if (mMessageFormatAuto && mMessageFormat == MessageFormat.TEXT) {
            mMessageFormat = MessageFormat.AUTO;
        }
        mMessageReadReceipt = storage.getBoolean(mAccountUuid + ".messageReadReceipt", DEFAULT_MESSAGE_READ_RECEIPT);
        mQuoteStyle = getEnumStringPref(storage, mAccountUuid + ".quoteStyle", DEFAULT_QUOTE_STYLE);
        mQuotePrefix = storage.getString(mAccountUuid + ".quotePrefix", DEFAULT_QUOTE_PREFIX);
        mDefaultQuotedTextShown = storage.getBoolean(mAccountUuid + ".defaultQuotedTextShown", DEFAULT_QUOTED_TEXT_SHOWN);
        mReplyAfterQuote = storage.getBoolean(mAccountUuid + ".replyAfterQuote", DEFAULT_REPLY_AFTER_QUOTE);
        mStripSignature = storage.getBoolean(mAccountUuid + ".stripSignature", DEFAULT_STRIP_SIGNATURE);
        for (NetworkType type : NetworkType.values()) {
            Boolean useCompression = storage.getBoolean(mAccountUuid + ".useCompression." + type, true);
            compressionMap.put(type, useCompression);
        }

        mAutoExpandFolder = storage.getString(mAccountUuid + ".autoExpandFolderName", INBOX);
        mAccountNumber = storage.getInt(mAccountUuid + ".accountNumber", 0);
        mChipColor = storage.getInt(mAccountUuid + ".chipColor", ColorPicker.getRandomColor());
        mSortType = getEnumStringPref(storage, mAccountUuid + ".sortTypeEnum", SortType.SORT_DATE);
        mSortAscending.put(mSortType, storage.getBoolean(mAccountUuid + ".sortAscending", false));
        mShowPictures = getEnumStringPref(storage, mAccountUuid + ".showPicturesEnum", ShowPictures.NEVER);

        mNotificationSetting.setVibrate(storage.getBoolean(mAccountUuid + ".vibrate", false));
        mNotificationSetting.setVibratePattern(storage.getInt(mAccountUuid + ".vibratePattern", 0));
        mNotificationSetting.setVibrateTimes(storage.getInt(mAccountUuid + ".vibrateTimes", 5));
        mNotificationSetting.setRingEnabled(storage.getBoolean(mAccountUuid + ".ring", true));
        mNotificationSetting.setRingtone(storage.getString(mAccountUuid + ".ringtone",
                "content://settings/system/notification_sound"));
        mNotificationSetting.setLed(storage.getBoolean(mAccountUuid + ".led", true));
        mNotificationSetting.setLedColor(storage.getInt(mAccountUuid + ".ledColor", mChipColor));

        mFolderDisplayMode = getEnumStringPref(storage, mAccountUuid + ".folderDisplayMode", FolderMode.NOT_SECOND_CLASS);
        mFolderSyncMode = getEnumStringPref(storage, mAccountUuid + ".folderSyncMode", FolderMode.FIRST_CLASS);
        mFolderPushMode = getEnumStringPref(storage, mAccountUuid + ".folderPushMode", FolderMode.FIRST_CLASS);
        mFolderTargetMode = getEnumStringPref(storage, mAccountUuid + ".folderTargetMode", FolderMode.NOT_SECOND_CLASS);
        searchableFolders = getEnumStringPref(storage, mAccountUuid + ".searchableFolders", Searchable.ALL);

        mIsSignatureBeforeQuotedText = storage.getBoolean(mAccountUuid + ".signatureBeforeQuotedText", false);
        identities = loadIdentities(storage);
        mStealthMode = storage.getBoolean(mAccountUuid + ".stealthMode", false);

        mPgpCryptoKey = storage.getLong(mAccountUuid + ".cryptoKey", NO_OPENPGP_KEY);
        mAllowRemoteSearch = storage.getBoolean(mAccountUuid + ".isAllowRemoteSearch", false);
        mRemoteSearchFullText = storage.getBoolean(mAccountUuid + ".remoteSearchFullText", false);
        mRemoteSearchNumResults = storage.getInt(mAccountUuid + ".remoteSearchNumResults", DEFAULT_REMOTE_SEARCH_NUM_RESULTS);
        mEnabled = storage.getBoolean(mAccountUuid + ".enabled", true);
        mMarkMessageAsReadOnView = storage.getBoolean(mAccountUuid + ".markMessageAsReadOnView", true);
        mAlwaysShowCcBcc = storage.getBoolean(mAccountUuid + ".alwaysShowCcBcc", false);
        cacheChips();
        // Use email address as account description if necessary
        if (mDescription == null) {
            mDescription = getEmail();
        }
    }

    protected synchronized void delete(Preferences preferences) {
        deleteCertificates();

        // Get the list of account UUIDs
        String[] uuids = preferences.getStorage().getString("accountUuids", "").split(",");

        // Create a list of all account UUIDs excluding this account
        List<String> newUuids = new ArrayList<>(uuids.length);
        for (String uuid : uuids) {
            if (!uuid.equals(mAccountUuid)) {
                newUuids.add(uuid);
            }
        }

        StorageEditor editor = preferences.getStorage().edit();
        // Only change the 'accountUuids' value if this account's UUID was listed before
        if (newUuids.size() < uuids.length) {
            String accountUuids = Utility.combine(newUuids.toArray(), ',');
            editor.putString("accountUuids", accountUuids);
        }

        editor.remove(mAccountUuid + ".storeUri");
        editor.remove(mAccountUuid + ".transportUri");
        editor.remove(mAccountUuid + ".description");
        editor.remove(mAccountUuid + ".name");
        editor.remove(mAccountUuid + ".email");
        editor.remove(mAccountUuid + ".alwaysBcc");
        editor.remove(mAccountUuid + ".automaticCheckIntervalMinutes");
        editor.remove(mAccountUuid + ".pushPollOnConnect");
        editor.remove(mAccountUuid + ".idleRefreshMinutes");
        editor.remove(mAccountUuid + ".lastAutomaticCheckTime");
        editor.remove(mAccountUuid + ".latestOldMessageSeenTime");
        editor.remove(mAccountUuid + ".notifyNewMail");
        editor.remove(mAccountUuid + ".notifySelfNewMail");
        editor.remove(mAccountUuid + ".deletePolicy");
        editor.remove(mAccountUuid + ".draftsFolderName");
        editor.remove(mAccountUuid + ".sentFolderName");
        editor.remove(mAccountUuid + ".trashFolderName");
        editor.remove(mAccountUuid + ".archiveFolderName");
        editor.remove(mAccountUuid + ".spamFolderName");
        editor.remove(mAccountUuid + ".autoExpandFolderName");
        editor.remove(mAccountUuid + ".accountNumber");
        editor.remove(mAccountUuid + ".vibrate");
        editor.remove(mAccountUuid + ".vibratePattern");
        editor.remove(mAccountUuid + ".vibrateTimes");
        editor.remove(mAccountUuid + ".ring");
        editor.remove(mAccountUuid + ".ringtone");
        editor.remove(mAccountUuid + ".folderDisplayMode");
        editor.remove(mAccountUuid + ".folderSyncMode");
        editor.remove(mAccountUuid + ".folderPushMode");
        editor.remove(mAccountUuid + ".folderTargetMode");
        editor.remove(mAccountUuid + ".signatureBeforeQuotedText");
        editor.remove(mAccountUuid + ".expungePolicy");
        editor.remove(mAccountUuid + ".syncRemoteDeletions");
        editor.remove(mAccountUuid + ".maxPushFolders");
        editor.remove(mAccountUuid + ".searchableFolders");
        editor.remove(mAccountUuid + ".chipColor");
        editor.remove(mAccountUuid + ".led");
        editor.remove(mAccountUuid + ".ledColor");
        editor.remove(mAccountUuid + ".goToUnreadMessageSearch");
        editor.remove(mAccountUuid + ".isSubscribedFoldersOnly");
        editor.remove(mAccountUuid + ".maximumPolledMessageAge");
        editor.remove(mAccountUuid + ".maximumAutoDownloadMessageSize");
        editor.remove(mAccountUuid + ".messageFormatAuto");
        editor.remove(mAccountUuid + ".quoteStyle");
        editor.remove(mAccountUuid + ".quotePrefix");
        editor.remove(mAccountUuid + ".sortTypeEnum");
        editor.remove(mAccountUuid + ".sortAscending");
        editor.remove(mAccountUuid + ".showPicturesEnum");
        editor.remove(mAccountUuid + ".replyAfterQuote");
        editor.remove(mAccountUuid + ".stripSignature");
        editor.remove(mAccountUuid + ".cryptoApp"); // this is no longer set, but cleans up legacy values
        editor.remove(mAccountUuid + ".cryptoAutoSignature");
        editor.remove(mAccountUuid + ".cryptoAutoEncrypt");
        editor.remove(mAccountUuid + ".cryptoApp");
        editor.remove(mAccountUuid + ".cryptoKey");
        editor.remove(mAccountUuid + ".cryptoSupportSignOnly");
        editor.remove(mAccountUuid + ".enabled");
        editor.remove(mAccountUuid + ".markMessageAsReadOnView");
        editor.remove(mAccountUuid + ".alwaysShowCcBcc");
        editor.remove(mAccountUuid + ".isAllowRemoteSearch");
        editor.remove(mAccountUuid + ".remoteSearchFullText");
        editor.remove(mAccountUuid + ".remoteSearchNumResults");
        editor.remove(mAccountUuid + ".defaultQuotedTextShown");
        editor.remove(mAccountUuid + ".displayCount");
        editor.remove(mAccountUuid + ".inboxFolderName");
        editor.remove(mAccountUuid + ".localStorageProvider");
        editor.remove(mAccountUuid + ".messageFormat");
        editor.remove(mAccountUuid + ".messageReadReceipt");
        editor.remove(mAccountUuid + ".notifyMailCheck");
        for (NetworkType type : NetworkType.values()) {
            editor.remove(mAccountUuid + ".useCompression." + type.name());
        }
        deleteIdentities(preferences.getStorage(), editor);
        // TODO: Remove preference settings that may exist for individual
        // folders in the account.
        editor.commit();

        Globals.getOAuth2TokenProvider().invalidateToken(getEmail());
        Globals.getOAuth2TokenProvider().disconnectEmailWithXMail(getEmail());
    }

    private static int findNewAccountNumber(List<Integer> accountNumbers) {
        int newAccountNumber = -1;
        Collections.sort(accountNumbers);
        for (int accountNumber : accountNumbers) {
            if (accountNumber > newAccountNumber + 1) {
                break;
            }
            newAccountNumber = accountNumber;
        }
        newAccountNumber++;
        return newAccountNumber;
    }

    private static List<Integer> getExistingAccountNumbers(Preferences preferences) {
        List<Account> accounts = preferences.getAccounts();
        List<Integer> accountNumbers = new ArrayList<>(accounts.size());
        for (Account a : accounts) {
            accountNumbers.add(a.getAccountNumber());
        }
        return accountNumbers;
    }

    public static int generateAccountNumber(Preferences preferences) {
        List<Integer> accountNumbers = getExistingAccountNumbers(preferences);
        return findNewAccountNumber(accountNumbers);
    }

    public void move(Preferences preferences, boolean moveUp) {
        String[] uuids = preferences.getStorage().getString("accountUuids", "").split(",");
        StorageEditor editor = preferences.getStorage().edit();
        String[] newUuids = new String[uuids.length];
        if (moveUp) {
            for (int i = 0; i < uuids.length; i++) {
                if (i > 0 && uuids[i].equals(mAccountUuid)) {
                    newUuids[i] = newUuids[i - 1];
                    newUuids[i - 1] = mAccountUuid;
                }
                else {
                    newUuids[i] = uuids[i];
                }
            }
        }
        else {
            for (int i = uuids.length - 1; i >= 0; i--) {
                if (i < uuids.length - 1 && uuids[i].equals(mAccountUuid)) {
                    newUuids[i] = newUuids[i + 1];
                    newUuids[i + 1] = mAccountUuid;
                }
                else {
                    newUuids[i] = uuids[i];
                }
            }
        }
        String accountUuids = Utility.combine(newUuids, ',');
        editor.putString("accountUuids", accountUuids);
        editor.commit();
        preferences.loadAccounts();
    }

    public synchronized void save(Preferences preferences) {
        StorageEditor editor = preferences.getStorage().edit();

        if (!preferences.getStorage().getString("accountUuids", "").contains(mAccountUuid)) {
            /*
             * When the account is first created we assign it a unique account number. The
             * account number will be unique to that account for the lifetime of the account.
             * So, we get all the existing account numbers, sort them ascending, loop through
             * the list and check if the number is greater than 1 + the previous number. If so
             * we use the previous number + 1 as the account number. This refills gaps.
             * accountNumber starts as -1 on a newly created account. It must be -1 for this
             * algorithm to work.
             *
             * I bet there is a much smarter way to do this. Anyone like to suggest it?
             */
            List<Account> accounts = preferences.getAccounts();
            int[] accountNumbers = new int[accounts.size()];
            for (int i = 0; i < accounts.size(); i++) {
                accountNumbers[i] = accounts.get(i).getAccountNumber();
            }
            Arrays.sort(accountNumbers);
            for (int accountNumber : accountNumbers) {
                if (accountNumber > mAccountNumber + 1) {
                    break;
                }
                mAccountNumber = accountNumber;
            }
            mAccountNumber++;

            String accountUuids = preferences.getStorage().getString("accountUuids", "");
            accountUuids += (accountUuids.length() != 0 ? "," : "") + mAccountUuid;
            editor.putString("accountUuids", accountUuids);
        }

        editor.putString(mAccountUuid + ".storeUri", Base64.encode(mStoreUri));
        editor.putString(mAccountUuid + ".localStorageProvider", mLocalStorageProviderId);
        editor.putString(mAccountUuid + ".transportUri", Base64.encode(mTransportUri));
        editor.putString(mAccountUuid + ".description", mDescription);
        editor.putString(mAccountUuid + ".alwaysBcc", mAlwaysBcc);
        editor.putInt(mAccountUuid + ".automaticCheckIntervalMinutes", mAutomaticCheckIntervalMinutes);
        editor.putInt(mAccountUuid + ".idleRefreshMinutes", mIdleRefreshMinutes);
        editor.putBoolean(mAccountUuid + ".pushPollOnConnect", mPushPollOnConnect);
        editor.putInt(mAccountUuid + ".displayCount", mDisplayCount);
        editor.putLong(mAccountUuid + ".latestOldMessageSeenTime", mLatestOldMessageSeenTime);
        editor.putBoolean(mAccountUuid + ".notifyNewMail", mNotifyNewMail);
        editor.putString(mAccountUuid + ".folderNotifyNewMailMode", mFolderNotifyNewMailMode.name());
        editor.putBoolean(mAccountUuid + ".notifySelfNewMail", mNotifySelfNewMail);
        editor.putBoolean(mAccountUuid + ".notifyContactsMailOnly", mNotifyContactsMailOnly);
        editor.putBoolean(mAccountUuid + ".notifyMailCheck", mNotifySync);
        editor.putInt(mAccountUuid + ".deletePolicy", mDeletePolicy.setting);
        editor.putString(mAccountUuid + ".inboxFolderName", mInboxFolder);
        editor.putString(mAccountUuid + ".draftsFolderName", mDraftsFolder);
        editor.putString(mAccountUuid + ".sentFolderName", mSentFolder);
        editor.putString(mAccountUuid + ".trashFolderName", mTrashFolder);
        editor.putString(mAccountUuid + ".archiveFolderName", mArchiveFolder);
        editor.putString(mAccountUuid + ".spamFolderName", mSpamFolder);
        editor.putString(mAccountUuid + ".autoExpandFolderName", mAutoExpandFolder);
        editor.putInt(mAccountUuid + ".accountNumber", mAccountNumber);
        editor.putString(mAccountUuid + ".sortTypeEnum", mSortType.name());
        editor.putBoolean(mAccountUuid + ".sortAscending", mSortAscending.get(mSortType));
        editor.putString(mAccountUuid + ".showPicturesEnum", mShowPictures.name());
        editor.putString(mAccountUuid + ".folderDisplayMode", mFolderDisplayMode.name());
        editor.putString(mAccountUuid + ".folderSyncMode", mFolderSyncMode.name());
        editor.putString(mAccountUuid + ".folderPushMode", mFolderPushMode.name());
        editor.putString(mAccountUuid + ".folderTargetMode", mFolderTargetMode.name());
        editor.putBoolean(mAccountUuid + ".signatureBeforeQuotedText", this.mIsSignatureBeforeQuotedText);
        editor.putString(mAccountUuid + ".expungePolicy", mExpungePolicy.name());
        editor.putBoolean(mAccountUuid + ".syncRemoteDeletions", mSyncRemoteDeletions);
        editor.putInt(mAccountUuid + ".maxPushFolders", mMaxPushFolders);
        editor.putString(mAccountUuid + ".searchableFolders", searchableFolders.name());
        editor.putInt(mAccountUuid + ".chipColor", mChipColor);
        editor.putBoolean(mAccountUuid + ".goToUnreadMessageSearch", goToUnreadMessageSearch);
        editor.putBoolean(mAccountUuid + ".isSubscribedFoldersOnly", subscribedFoldersOnly);
        editor.putInt(mAccountUuid + ".maximumPolledMessageAge", maximumPolledMessageAge);
        editor.putInt(mAccountUuid + ".maximumAutoDownloadMessageSize", maximumAutoDownloadMessageSize);
        if (MessageFormat.AUTO.equals(mMessageFormat)) {
            // saving MessageFormat.AUTO as is to the database will cause downgrades to crash on
            // startup, so we save as MessageFormat.TEXT instead with a separate flag for auto.
            editor.putString(mAccountUuid + ".messageFormat", Account.MessageFormat.TEXT.name());
            mMessageFormatAuto = true;
        }
        else {
            editor.putString(mAccountUuid + ".messageFormat", mMessageFormat.name());
            mMessageFormatAuto = false;
        }
        editor.putBoolean(mAccountUuid + ".messageFormatAuto", mMessageFormatAuto);
        editor.putBoolean(mAccountUuid + ".messageReadReceipt", mMessageReadReceipt);
        editor.putString(mAccountUuid + ".quoteStyle", mQuoteStyle.name());
        editor.putString(mAccountUuid + ".quotePrefix", mQuotePrefix);
        editor.putBoolean(mAccountUuid + ".defaultQuotedTextShown", mDefaultQuotedTextShown);
        editor.putBoolean(mAccountUuid + ".replyAfterQuote", mReplyAfterQuote);
        editor.putBoolean(mAccountUuid + ".stripSignature", mStripSignature);
        editor.putBoolean(mAccountUuid + ".stealthMode", mStealthMode);
        editor.putLong(mAccountUuid + ".cryptoKey", mPgpCryptoKey);
        editor.putBoolean(mAccountUuid + ".isAllowRemoteSearch", mAllowRemoteSearch);
        editor.putBoolean(mAccountUuid + ".remoteSearchFullText", mRemoteSearchFullText);
        editor.putInt(mAccountUuid + ".remoteSearchNumResults", mRemoteSearchNumResults);
        editor.putBoolean(mAccountUuid + ".enabled", mEnabled);
        editor.putBoolean(mAccountUuid + ".markMessageAsReadOnView", mMarkMessageAsReadOnView);
        editor.putBoolean(mAccountUuid + ".alwaysShowCcBcc", mAlwaysShowCcBcc);

        editor.putBoolean(mAccountUuid + ".vibrate", mNotificationSetting.isVibrateEnabled());
        editor.putInt(mAccountUuid + ".vibratePattern", mNotificationSetting.getVibratePattern());
        editor.putInt(mAccountUuid + ".vibrateTimes", mNotificationSetting.getVibrateTimes());
        editor.putBoolean(mAccountUuid + ".ring", mNotificationSetting.isRingEnabled());
        editor.putString(mAccountUuid + ".ringtone", mNotificationSetting.getRingtone());
        editor.putBoolean(mAccountUuid + ".led", mNotificationSetting.isLedEnabled());
        editor.putInt(mAccountUuid + ".ledColor", mNotificationSetting.getLedColor());

        for (NetworkType type : NetworkType.values()) {
            Boolean useCompression = compressionMap.get(type);
            if (useCompression != null) {
                editor.putBoolean(mAccountUuid + ".useCompression." + type, useCompression);
            }
        }
        saveIdentities(preferences.getStorage(), editor);
        editor.commit();
    }

    private void resetVisibleLimits() {
        try {
            getLocalStore().resetVisibleLimits(getDisplayCount());
        } catch (MessagingException e) {
            Timber.e(e, "Unable to reset visible limits");
        }
    }

    /**
     * @return <code>null</code> if not available
     *
     * @throws MessagingException
     * @see {@link #isAvailable(Context)}
     */
    public AccountStats getStats(Context context)
            throws MessagingException {
        if (!isAvailable(context)) {
            return null;
        }

        AccountStats stats = new AccountStats();
        ContentResolver cr = context.getContentResolver();

        Uri uri = Uri.withAppendedPath(EmailProvider.CONTENT_URI,
                "account/" + getUuid() + "/stats");

        String[] projection = {
                StatsColumns.UNREAD_COUNT,
                StatsColumns.FLAGGED_COUNT
        };

        // Create LocalSearch instance to exclude special folders (Trash, Drafts, Spam, Outbox,
        // Sent) and limit the search to displayable folders.
        LocalSearch search = new LocalSearch();
        excludeSpecialFolders(search);
        limitToDisplayableFolders(search);

        // Use the LocalSearch instance to create a WHERE clause to query the content provider
        StringBuilder query = new StringBuilder();
        List<String> queryArgs = new ArrayList<>();
        ConditionsTreeNode conditions = search.getConditions();
        SqlQueryBuilder.buildWhereClause(this, conditions, query, queryArgs);

        String selection = query.toString();
        String[] selectionArgs = queryArgs.toArray(new String[0]);

        Cursor cursor = cr.query(uri, projection, selection, selectionArgs, null);
        try {
            if (cursor != null && cursor.moveToFirst()) {
                stats.unreadMessageCount = cursor.getInt(0);
                stats.flaggedMessageCount = cursor.getInt(1);
            }
        } finally {
            Utility.closeQuietly(cursor);
        }
        LocalStore localStore = getLocalStore();
        if (XryptoMail.measureAccounts()) {
            stats.size = localStore.getSize();
        }
        return stats;
    }

    public int getFolderUnreadCount(Context context, String folderName)
            throws MessagingException {
        if (!isAvailable(context)) {
            return 0;
        }

        int unreadMessageCount = 0;

        Cursor cursor = loadUnreadCountForFolder(context, folderName);
        try {
            if (cursor != null && cursor.moveToFirst()) {
                unreadMessageCount = cursor.getInt(0);
            }
        } finally {
            Utility.closeQuietly(cursor);
        }

        return unreadMessageCount;
    }

    private Cursor loadUnreadCountForFolder(Context context, String folderName) {
        ContentResolver cr = context.getContentResolver();

        Uri uri = Uri.withAppendedPath(EmailProvider.CONTENT_URI,
                "account/" + getUuid() + "/stats");

        String[] projection = {
                StatsColumns.UNREAD_COUNT,
        };

        LocalSearch search = new LocalSearch();
        search.addAllowedFolder(folderName);

        // Use the LocalSearch instance to create a WHERE clause to query the content provider
        StringBuilder query = new StringBuilder();
        List<String> queryArgs = new ArrayList<>();
        ConditionsTreeNode conditions = search.getConditions();
        SqlQueryBuilder.buildWhereClause(this, conditions, query, queryArgs);

        String selection = query.toString();
        String[] selectionArgs = queryArgs.toArray(new String[0]);

        return cr.query(uri, projection, selection, selectionArgs, null);
    }

    public synchronized void setChipColor(int color) {
        mChipColor = color;
        cacheChips();
    }

    private synchronized void cacheChips() {
        mReadColorChip = new ColorChip(mChipColor, true, ColorChip.CIRCULAR);
        mUnreadColorChip = new ColorChip(mChipColor, false, ColorChip.CIRCULAR);
        mFlaggedReadColorChip = new ColorChip(mChipColor, true, ColorChip.STAR);
        mFlaggedUnreadColorChip = new ColorChip(mChipColor, false, ColorChip.STAR);
    }

    public synchronized int getChipColor() {
        return mChipColor;
    }

    public ColorChip generateColorChip(boolean messageRead, boolean messageFlagged) {
        ColorChip chip;

        if (messageRead) {
            if (messageFlagged) {
                chip = mFlaggedReadColorChip;
            }
            else {
                chip = mReadColorChip;
            }
        }
        else {
            if (messageFlagged) {
                chip = mFlaggedUnreadColorChip;
            }
            else {
                chip = mUnreadColorChip;
            }
        }
        return chip;
    }

    @Override
    public String getUuid() {
        return mAccountUuid;
    }

    public synchronized String getStoreUri() {
        return mStoreUri;
    }

    public synchronized void setStoreUri(String storeUri) {
        this.mStoreUri = storeUri;
    }

    public synchronized String getTransportUri() {
        return mTransportUri;
    }

    public synchronized void setTransportUri(String transportUri) {
        this.mTransportUri = transportUri;
    }

    @Override
    public synchronized String getDescription() {
        return mDescription;
    }

    @Override
    public synchronized void setDescription(String description) {
        this.mDescription = description;
    }

    public synchronized String getName() {
        return identities.get(0).getName();
    }

    public synchronized void setName(String name) {
        identities.get(0).setName(name);
    }

    public synchronized boolean getSignatureUse() {
        return identities.get(0).getSignatureUse();
    }

    public synchronized void setSignatureUse(boolean signatureUse) {
        identities.get(0).setSignatureUse(signatureUse);
    }

    public synchronized String getSignature() {
        return identities.get(0).getSignature();
    }

    public synchronized void setSignature(String signature) {
        identities.get(0).setSignature(signature);
    }

    @Override
    public synchronized String getEmail() {
        return identities.get(0).getEmail();
    }

    @Override
    public synchronized void setEmail(String email) {
        identities.get(0).setEmail(email);
    }

    public synchronized String getAlwaysBcc() {
        return mAlwaysBcc;
    }

    public synchronized void setAlwaysBcc(String alwaysBcc) {
        this.mAlwaysBcc = alwaysBcc;
    }

    /* Have we sent a new mail notification on this account */
    public boolean isRingNotified() {
        return mRingNotified;
    }

    public void setRingNotified(boolean ringNotified) {
        mRingNotified = ringNotified;
    }

    public String getLocalStorageProviderId() {
        return mLocalStorageProviderId;
    }

    public void setLocalStorageProviderId(String id) {

        if (!mLocalStorageProviderId.equals(id)) {

            boolean successful = false;
            try {
                switchLocalStorage(id);
                successful = true;
            } catch (MessagingException e) {
                Timber.e(e, "Switching local storage provider from %s to %s failed.", mLocalStorageProviderId, id);
            }

            // if migration to/from SD-card failed once, it will fail again.
            if (!successful) {
                return;
            }
            mLocalStorageProviderId = id;
        }
    }

    /**
     * Returns -1 for never.
     */
    public synchronized int getAutomaticCheckIntervalMinutes() {
        return mAutomaticCheckIntervalMinutes;
    }

    /**
     * @param automaticCheckIntervalMinutes or -1 for never.
     */
    public synchronized boolean setAutomaticCheckIntervalMinutes(int automaticCheckIntervalMinutes) {
        int oldInterval = this.mAutomaticCheckIntervalMinutes;
        this.mAutomaticCheckIntervalMinutes = automaticCheckIntervalMinutes;

        return (oldInterval != automaticCheckIntervalMinutes);
    }

    @Override
    public synchronized int getDisplayCount() {
        return mDisplayCount;
    }

    public synchronized void setDisplayCount(int displayCount) {
        if (displayCount != -1) {
            this.mDisplayCount = displayCount;
        }
        else {
            this.mDisplayCount = XryptoMail.DEFAULT_VISIBLE_LIMIT;
        }
        resetVisibleLimits();
    }

    public synchronized long getLatestOldMessageSeenTime() {
        return mLatestOldMessageSeenTime;
    }

    public synchronized void setLatestOldMessageSeenTime(long latestOldMessageSeenTime) {
        this.mLatestOldMessageSeenTime = latestOldMessageSeenTime;
    }

    @Override
    public ConnectionSecurity getIncomingSecurityType() {
        return RemoteStore.decodeStoreUri(getStoreUri()).connectionSecurity;
    }

    @Override
    public AuthType getIncomingAuthType() {
        return RemoteStore.decodeStoreUri(getStoreUri()).authenticationType;
    }

    @Override
    public String getIncomingPort() {
        return String.valueOf(RemoteStore.decodeStoreUri(getStoreUri()).port);
    }

    @Override
    public ConnectionSecurity getOutgoingSecurityType() {
        return TransportUris.decodeTransportUri(getTransportUri()).connectionSecurity;
    }

    @Override
    public AuthType getOutgoingAuthType() {
        return TransportUris.decodeTransportUri(getTransportUri()).authenticationType;
    }

    @Override
    public String getOutgoingPort() {
        return String.valueOf(TransportUris.decodeTransportUri(getTransportUri()).port);
    }

    @Override
    public synchronized boolean isNotifyNewMail() {
        return mNotifyNewMail;
    }

    public synchronized void setNotifyNewMail(boolean notifyNewMail) {
        this.mNotifyNewMail = notifyNewMail;
    }

    public synchronized FolderMode getFolderNotifyNewMailMode() {
        return mFolderNotifyNewMailMode;
    }

    public synchronized void setFolderNotifyNewMailMode(FolderMode folderNotifyNewMailMode) {
        this.mFolderNotifyNewMailMode = folderNotifyNewMailMode;
    }

    public synchronized DeletePolicy getDeletePolicy() {
        return mDeletePolicy;
    }

    public synchronized void setDeletePolicy(DeletePolicy deletePolicy) {
        this.mDeletePolicy = deletePolicy;
    }

    public boolean isSpecialFolder(String folderName) {
        return (folderName != null && (folderName.equalsIgnoreCase(getInboxFolder())
                || folderName.equals(getTrashFolder())
                || folderName.equals(getDraftsFolderName())
                || folderName.equals(getArchiveFolder())
                || folderName.equals(getSpamFolder())
                || folderName.equals(getOutboxFolderName())
                || folderName.equals(getSentFolder())));
    }

    public synchronized String getDraftsFolderName() {
        return mDraftsFolder;
    }

    public synchronized void setDraftsFolder(String name) {
        mDraftsFolder = name;
    }

    /**
     * Checks if this account has a drafts folder set.
     *
     * @return true if account has a drafts folder set.
     */
    public synchronized boolean hasDraftsFolder() {
        return !XryptoMail.FOLDER_NONE.equalsIgnoreCase(mDraftsFolder);
    }

    public synchronized String getSentFolder() {
        return mSentFolder;
    }

    public synchronized void setSentFolder(String name) {
        mSentFolder = name;
    }

    /**
     * Checks if this account has a sent folder set.
     *
     * @return true if account has a sent folder set.
     */
    public synchronized boolean hasSentFolder() {
        return !XryptoMail.FOLDER_NONE.equalsIgnoreCase(mSentFolder);
    }

    public synchronized String getTrashFolder() {
        return mTrashFolder;
    }

    public synchronized void setTrashFolder(String name) {
        mTrashFolder = name;
    }

    /**
     * Checks if this account has a trash folder set.
     *
     * @return true if account has a trash folder set.
     */
    public synchronized boolean hasTrashFolder() {
        return !XryptoMail.FOLDER_NONE.equalsIgnoreCase(mTrashFolder);
    }

    public synchronized String getArchiveFolder() {
        return mArchiveFolder;
    }

    public synchronized void setArchiveFolderName(String archiveFolderName) {
        mArchiveFolder = archiveFolderName;
    }

    /**
     * Checks if this account has an archive folder set.
     *
     * @return true if account has an archive folder set.
     */
    public synchronized boolean hasArchiveFolder() {
        return !XryptoMail.FOLDER_NONE.equalsIgnoreCase(mArchiveFolder);
    }

    public synchronized String getSpamFolder() {
        return mSpamFolder;
    }

    public synchronized void setSpamFolderName(String name) {
        mSpamFolder = name;
    }

    /**
     * Checks if this account has a spam folder set.
     *
     * @return true if account has a spam folder set.
     */
    public synchronized boolean hasSpamFolder() {
        return !XryptoMail.FOLDER_NONE.equalsIgnoreCase(mSpamFolder);
    }

    public synchronized String getOutboxFolderName() {
        return OUTBOX;
    }

    public synchronized String getAutoExpandFolder() {
        return mAutoExpandFolder;
    }

    public synchronized void setAutoExpandFolder(String name) {
        mAutoExpandFolder = name;
    }

    public synchronized int getAccountNumber() {
        return mAccountNumber;
    }

    public synchronized FolderMode getFolderDisplayMode() {
        return mFolderDisplayMode;
    }

    public synchronized boolean setFolderDisplayMode(FolderMode displayMode) {
        FolderMode oldDisplayMode = mFolderDisplayMode;
        mFolderDisplayMode = displayMode;
        return oldDisplayMode != displayMode;
    }

    public synchronized FolderMode getFolderSyncMode() {
        return mFolderSyncMode;
    }

    public synchronized boolean setFolderSyncMode(FolderMode syncMode) {
        FolderMode oldSyncMode = mFolderSyncMode;
        mFolderSyncMode = syncMode;

        if (syncMode == FolderMode.NONE && oldSyncMode != FolderMode.NONE) {
            return true;
        }
        return syncMode != FolderMode.NONE && oldSyncMode == FolderMode.NONE;
    }

    public synchronized FolderMode getFolderPushMode() {
        return mFolderPushMode;
    }

    public synchronized boolean setFolderPushMode(FolderMode pushMode) {
        FolderMode oldPushMode = mFolderPushMode;

        mFolderPushMode = pushMode;
        return pushMode != oldPushMode;
    }

    public synchronized boolean isShowOngoing() {
        return mNotifySync;
    }

    public synchronized void setShowOngoing(boolean showOngoing) {
        this.mNotifySync = showOngoing;
    }

    public synchronized SortType getSortType() {
        return mSortType;
    }

    public synchronized void setSortType(SortType sortType) {
        mSortType = sortType;
    }

    public synchronized boolean isSortAscending(SortType sortType) {
        if (mSortAscending.get(sortType) == null) {
            mSortAscending.put(sortType, sortType.isDefaultAscending());
        }
        return mSortAscending.get(sortType);
    }

    public synchronized void setSortAscending(SortType sortType, boolean sortAscending) {
        mSortAscending.put(sortType, sortAscending);
    }

    public synchronized ShowPictures getShowPictures() {
        return mShowPictures;
    }

    public synchronized void setShowPictures(ShowPictures showPictures) {
        mShowPictures = showPictures;
    }

    public synchronized FolderMode getFolderTargetMode() {
        return mFolderTargetMode;
    }

    public synchronized void setFolderTargetMode(FolderMode folderTargetMode) {
        mFolderTargetMode = folderTargetMode;
    }

    public synchronized boolean isSignatureBeforeQuotedText() {
        return mIsSignatureBeforeQuotedText;
    }

    public synchronized void setSignatureBeforeQuotedText(boolean mIsSignatureBeforeQuotedText) {
        this.mIsSignatureBeforeQuotedText = mIsSignatureBeforeQuotedText;
    }

    public synchronized boolean isNotifySelfNewMail() {
        return mNotifySelfNewMail;
    }

    public synchronized void setNotifySelfNewMail(boolean notifySelfNewMail) {
        mNotifySelfNewMail = notifySelfNewMail;
    }

    public synchronized boolean isNotifyContactsMailOnly() {
        return mNotifyContactsMailOnly;
    }

    public synchronized void setNotifyContactsMailOnly(boolean notifyContactsMailOnly) {
        mNotifyContactsMailOnly = notifyContactsMailOnly;
    }

    public synchronized Expunge getExpungePolicy() {
        return mExpungePolicy;
    }

    public synchronized void setExpungePolicy(Expunge expungePolicy) {
        mExpungePolicy = expungePolicy;
    }

    public synchronized int getMaxPushFolders() {
        return mMaxPushFolders;
    }

    public synchronized boolean setMaxPushFolders(int maxPushFolders) {
        int oldMaxPushFolders = mMaxPushFolders;
        mMaxPushFolders = maxPushFolders;
        return oldMaxPushFolders != maxPushFolders;
    }

    public LocalStore getLocalStore()
            throws MessagingException {
        return LocalStore.getInstance(this, XryptoMail.mInstance);
    }

    public Store getRemoteStore()
            throws MessagingException {
        return RemoteStore.getInstance(XryptoMail.mInstance, this, Globals.getOAuth2TokenProvider());
    }

    // It'd be great if this actually went into the store implementation to get
    // this, but that's expensive and not easily accessible during initialization
    public boolean isSearchByDateCapable() {
        return (getStoreUri().startsWith("imap"));
    }

    @NonNull
    @Override
    public synchronized String toString() {
        return mDescription;
    }

    public synchronized void setCompression(NetworkType networkType, boolean useCompression) {
        compressionMap.put(networkType, useCompression);
    }

    public synchronized boolean useCompression(NetworkType networkType) {
        Boolean useCompression = compressionMap.get(networkType);
        if (useCompression == null) {
            return true;
        }
        return useCompression;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Account) {
            return ((Account) o).mAccountUuid.equals(mAccountUuid);
        }
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return mAccountUuid.hashCode();
    }

    private synchronized List<Identity> loadIdentities(Storage storage) {
        List<Identity> newIdentities = new ArrayList<>();
        int ident = 0;
        boolean gotOne;
        do {
            gotOne = false;
            String name = storage.getString(mAccountUuid + "." + IDENTITY_NAME_KEY + "." + ident, null);
            String email = storage.getString(mAccountUuid + "." + IDENTITY_EMAIL_KEY + "." + ident, null);
            boolean signatureUse = storage.getBoolean(mAccountUuid + ".signatureUse." + ident, true);
            String signature = storage.getString(mAccountUuid + ".signature." + ident, null);
            String description = storage.getString(mAccountUuid + "." + IDENTITY_DESCRIPTION_KEY + "." + ident, null);
            final String replyTo = storage.getString(mAccountUuid + ".replyTo." + ident, null);
            if (email != null) {
                Identity identity = new Identity();
                identity.setName(name);
                identity.setEmail(email);
                identity.setSignatureUse(signatureUse);
                identity.setSignature(signature);
                identity.setDescription(description);
                identity.setReplyTo(replyTo);
                newIdentities.add(identity);
                gotOne = true;
            }
            ident++;
        } while (gotOne);

        if (newIdentities.isEmpty()) {
            String name = storage.getString(mAccountUuid + ".name", null);
            String email = storage.getString(mAccountUuid + ".email", null);
            boolean signatureUse = storage.getBoolean(mAccountUuid + ".signatureUse", true);
            String signature = storage.getString(mAccountUuid + ".signature", null);
            Identity identity = new Identity();
            identity.setName(name);
            identity.setEmail(email);
            identity.setSignatureUse(signatureUse);
            identity.setSignature(signature);
            identity.setDescription(email);
            newIdentities.add(identity);
        }
        return newIdentities;
    }

    private synchronized void deleteIdentities(Storage storage, StorageEditor editor) {
        int ident = 0;
        boolean gotOne;
        do {
            gotOne = false;
            String email = storage.getString(mAccountUuid + "." + IDENTITY_EMAIL_KEY + "." + ident, null);
            if (email != null) {
                editor.remove(mAccountUuid + "." + IDENTITY_NAME_KEY + "." + ident);
                editor.remove(mAccountUuid + "." + IDENTITY_EMAIL_KEY + "." + ident);
                editor.remove(mAccountUuid + ".signatureUse." + ident);
                editor.remove(mAccountUuid + ".signature." + ident);
                editor.remove(mAccountUuid + "." + IDENTITY_DESCRIPTION_KEY + "." + ident);
                editor.remove(mAccountUuid + ".replyTo." + ident);
                gotOne = true;
            }
            ident++;
        } while (gotOne);
    }

    private synchronized void saveIdentities(Storage storage, StorageEditor editor) {
        deleteIdentities(storage, editor);
        int ident = 0;

        for (Identity identity : identities) {
            editor.putString(mAccountUuid + "." + IDENTITY_NAME_KEY + "." + ident, identity.getName());
            editor.putString(mAccountUuid + "." + IDENTITY_EMAIL_KEY + "." + ident, identity.getEmail());
            editor.putBoolean(mAccountUuid + ".signatureUse." + ident, identity.getSignatureUse());
            editor.putString(mAccountUuid + ".signature." + ident, identity.getSignature());
            editor.putString(mAccountUuid + "." + IDENTITY_DESCRIPTION_KEY + "." + ident, identity.getDescription());
            editor.putString(mAccountUuid + ".replyTo." + ident, identity.getReplyTo());
            ident++;
        }
    }

    public synchronized List<Identity> getIdentities() {
        return identities;
    }

    public synchronized void setIdentities(List<Identity> newIdentities) {
        identities = new ArrayList<>(newIdentities);
    }

    public synchronized Identity getIdentity(int i) {
        if (i < identities.size()) {
            return identities.get(i);
        }
        throw new IllegalArgumentException("Identity with index " + i + " not found");
    }

    public boolean isAnIdentity(Address[] addrs) {
        if (addrs == null) {
            return false;
        }
        for (Address addr : addrs) {
            if (findIdentity(addr) != null) {
                return true;
            }
        }
        return false;
    }

    public boolean isAnIdentity(Address addr) {
        return findIdentity(addr) != null;
    }

    public synchronized Identity findIdentity(Address addr) {
        for (Identity identity : identities) {
            String email = identity.getEmail();
            if (email != null && email.equalsIgnoreCase(addr.getAddress())) {
                return identity;
            }
        }
        return null;
    }

    public synchronized Searchable getSearchableFolders() {
        return searchableFolders;
    }

    public synchronized void setSearchableFolders(Searchable searchableFolders) {
        this.searchableFolders = searchableFolders;
    }

    public synchronized int getIdleRefreshMinutes() {
        return mIdleRefreshMinutes;
    }

    @Override
    public boolean shouldHideHostname() {
        return XryptoMail.hideHostnameWhenConnecting();
    }

    public synchronized void setIdleRefreshMinutes(int idleRefreshMinutes) {
        mIdleRefreshMinutes = idleRefreshMinutes;
    }

    public synchronized boolean isPushPollOnConnect() {
        return mPushPollOnConnect;
    }

    public synchronized void setPushPollOnConnect(boolean pushPollOnConnect) {
        mPushPollOnConnect = pushPollOnConnect;
    }

    /**
     * Are we storing out localStore on the SD-card instead of the local device
     * memory?<br/>
     * Only to be called durin initial account-setup!<br/>
     * Side-effect: changes {@link #mLocalStorageProviderId}.
     *
     * @param newStorageProviderId Never <code>null</code>.
     *
     * @throws MessagingException
     */
    private void switchLocalStorage(final String newStorageProviderId)
            throws MessagingException {
        if (!mLocalStorageProviderId.equals(newStorageProviderId)) {
            getLocalStore().switchLocalStorage(newStorageProviderId);
        }
    }

    public synchronized boolean goToUnreadMessageSearch() {
        return goToUnreadMessageSearch;
    }

    public synchronized void setGoToUnreadMessageSearch(boolean goToUnreadMessageSearch) {
        this.goToUnreadMessageSearch = goToUnreadMessageSearch;
    }

    public synchronized boolean isSubscribedFoldersOnly() {
        return subscribedFoldersOnly;
    }

    public synchronized void setSubscribedFoldersOnly(boolean subscribedFoldersOnly) {
        this.subscribedFoldersOnly = subscribedFoldersOnly;
    }

    public synchronized int getMaximumPolledMessageAge() {
        return maximumPolledMessageAge;
    }

    public synchronized void setMaximumPolledMessageAge(int maximumPolledMessageAge) {
        this.maximumPolledMessageAge = maximumPolledMessageAge;
    }

    public synchronized int getMaximumAutoDownloadMessageSize() {
        return maximumAutoDownloadMessageSize;
    }

    public synchronized void setMaximumAutoDownloadMessageSize(int maximumAutoDownloadMessageSize) {
        this.maximumAutoDownloadMessageSize = maximumAutoDownloadMessageSize;
    }

    public Date getEarliestPollDate() {
        int age = getMaximumPolledMessageAge();
        if (age >= 0) {
            Calendar now = Calendar.getInstance();
            now.set(Calendar.HOUR_OF_DAY, 0);
            now.set(Calendar.MINUTE, 0);
            now.set(Calendar.SECOND, 0);
            now.set(Calendar.MILLISECOND, 0);
            if (age < 28) {
                now.add(Calendar.DATE, age * -1);
            }
            else
                switch (age) {
                    case 28:
                        now.add(Calendar.MONTH, -1);
                        break;
                    case 56:
                        now.add(Calendar.MONTH, -2);
                        break;
                    case 84:
                        now.add(Calendar.MONTH, -3);
                        break;
                    case 168:
                        now.add(Calendar.MONTH, -6);
                        break;
                    case 365:
                        now.add(Calendar.YEAR, -1);
                        break;
                }
            return now.getTime();
        }
        return null;
    }

    public MessageFormat getMessageFormat() {
        return mMessageFormat;
    }

    public void setMessageFormat(MessageFormat messageFormat) {
        this.mMessageFormat = messageFormat;
    }

    public synchronized boolean isMessageReadReceiptAlways() {
        return mMessageReadReceipt;
    }

    public synchronized void setMessageReadReceipt(boolean messageReadReceipt) {
        mMessageReadReceipt = messageReadReceipt;
    }

    public QuoteStyle getQuoteStyle() {
        return mQuoteStyle;
    }

    public void setQuoteStyle(QuoteStyle quoteStyle) {
        this.mQuoteStyle = quoteStyle;
    }

    public synchronized String getQuotePrefix() {
        return mQuotePrefix;
    }

    public synchronized void setQuotePrefix(String quotePrefix) {
        mQuotePrefix = quotePrefix;
    }

    public synchronized boolean isDefaultQuotedTextShown() {
        return mDefaultQuotedTextShown;
    }

    public synchronized void setDefaultQuotedTextShown(boolean shown) {
        mDefaultQuotedTextShown = shown;
    }

    public synchronized boolean isReplyAfterQuote() {
        return mReplyAfterQuote;
    }

    public synchronized void setReplyAfterQuote(boolean replyAfterQuote) {
        mReplyAfterQuote = replyAfterQuote;
    }

    public synchronized boolean isStripSignature() {
        return mStripSignature;
    }

    public synchronized void setStripSignature(boolean stripSignature) {
        mStripSignature = stripSignature;
    }

    public boolean isStealthModeEnable() {
        return mStealthMode;
    }

    public void enableStealthMode(boolean modeEn) {
        mStealthMode = modeEn;
    }

    public long getCryptoKey() {
        return mPgpCryptoKey;
    }

    public void setCryptoKey(long keyId) {
        mPgpCryptoKey = keyId;
    }

    public boolean getAutocryptPreferEncryptMutual() {
        return autocryptPreferEncryptMutual;
    }

    public void setAutocryptPreferEncryptMutual(boolean autocryptPreferEncryptMutual) {
        this.autocryptPreferEncryptMutual = autocryptPreferEncryptMutual;
    }

    public boolean isAllowRemoteSearch() {
        return mAllowRemoteSearch;
    }

    public void setAllowRemoteSearch(boolean val) {
        mAllowRemoteSearch = val;
    }

    public int getRemoteSearchNumResults() {
        return mRemoteSearchNumResults;
    }

    public void setRemoteSearchNumResults(int val) {
        mRemoteSearchNumResults = Math.max(val, 0);
    }

    public String getInboxFolder() {
        return mInboxFolder;
    }

    public void setInboxFolder(String name) {
        this.mInboxFolder = name;
    }

    public synchronized boolean syncRemoteDeletions() {
        return mSyncRemoteDeletions;
    }

    public synchronized void setSyncRemoteDeletions(boolean syncRemoteDeletions) {
        mSyncRemoteDeletions = syncRemoteDeletions;
    }

    public synchronized String getLastSelectedFolder() {
        return lastSelectedFolderName;
    }

    public synchronized void setLastSelectedFolder(String folderName) {
        lastSelectedFolderName = folderName;
    }

    public synchronized NotificationSetting getNotificationSetting() {
        return mNotificationSetting;
    }

    /**
     * @return <code>true</code> if our {@link StorageProvider} is ready. (e.g. card inserted)
     */
    public boolean isAvailable(Context context) {
        String localStorageProviderId = getLocalStorageProviderId();
        boolean storageProviderIsInternalMemory = localStorageProviderId == null;
        return storageProviderIsInternalMemory || StorageManager.getInstance(context).isReady(localStorageProviderId);
    }

    public synchronized boolean isEnabled() {
        return mEnabled;
    }

    public synchronized void setEnabled(boolean enabled) {
        mEnabled = enabled;
    }

    public synchronized boolean isMarkMessageAsReadOnView() {
        return mMarkMessageAsReadOnView;
    }

    public synchronized void setMarkMessageAsReadOnView(boolean value) {
        mMarkMessageAsReadOnView = value;
    }

    public synchronized boolean isAlwaysShowCcBcc() {
        return mAlwaysShowCcBcc;
    }

    public synchronized void setAlwaysShowCcBcc(boolean show) {
        mAlwaysShowCcBcc = show;
    }

    public boolean isRemoteSearchFullText() {
        return false;   // Temporarily disabled
        //return mRemoteSearchFullText;
    }

    public void setRemoteSearchFullText(boolean val) {
        mRemoteSearchFullText = val;
    }

    /**
     * Modify the supplied {@link LocalSearch} instance to limit the search to displayable folders.
     *
     * <p>
     * This method uses the current folder display mode to decide what folders to include/exclude.
     * </p>
     *
     * @param search The {@code LocalSearch} instance to modify.
     *
     * @see #getFolderDisplayMode()
     */
    public void limitToDisplayableFolders(LocalSearch search) {
        final Account.FolderMode displayMode = getFolderDisplayMode();

        switch (displayMode) {
            case FIRST_CLASS: {
                // Count messages in the INBOX and non-special first class folders
                search.and(SearchField.DISPLAY_CLASS, FolderClass.FIRST_CLASS.name(),
                        Attribute.EQUALS);
                break;
            }
            case FIRST_AND_SECOND_CLASS: {
                // Count messages in the INBOX and non-special first and second class folders
                search.and(SearchField.DISPLAY_CLASS, FolderClass.FIRST_CLASS.name(),
                        Attribute.EQUALS);

                // TODO: Create a proper interface for creating arbitrary condition trees
                SearchCondition searchCondition = new SearchCondition(SearchField.DISPLAY_CLASS,
                        Attribute.EQUALS, FolderClass.SECOND_CLASS.name());
                ConditionsTreeNode root = search.getConditions();
                if (root.mRight != null) {
                    root.mRight.or(searchCondition);
                }
                else {
                    search.or(searchCondition);
                }
                break;
            }
            case NOT_SECOND_CLASS: {
                // Count messages in the INBOX and non-special non-second-class folders
                search.and(SearchField.DISPLAY_CLASS, FolderClass.SECOND_CLASS.name(),
                        Attribute.NOT_EQUALS);
                break;
            }
            default:
            case ALL: {
                // Count messages in the INBOX and non-special folders
                break;
            }
        }
    }

    /**
     * Modify the supplied {@link LocalSearch} instance to exclude special folders.
     *
     * <p>
     * Currently the following folders are excluded:
     * <ul>
     * <li>Trash</li>
     * <li>Drafts</li>
     * <li>Spam</li>
     * <li>Outbox</li>
     * <li>Sent</li>
     * </ul>
     * The Inbox will always be included even if one of the special folders is configured to point
     * to the Inbox.
     * </p>
     *
     * @param search The {@code LocalSearch} instance to modify.
     */
    public void excludeSpecialFolders(LocalSearch search) {
        excludeSpecialFolder(search, getTrashFolder());
        excludeSpecialFolder(search, getDraftsFolderName());
        excludeSpecialFolder(search, getSpamFolder());
        excludeSpecialFolder(search, getOutboxFolderName());
        excludeSpecialFolder(search, getSentFolder());
        search.or(new SearchCondition(SearchField.FOLDER, Attribute.EQUALS, getInboxFolder()));
    }

    /**
     * Modify the supplied {@link LocalSearch} instance to exclude "unwanted" folders.
     *
     * <p>
     * Currently the following folders are excluded:
     * <ul>
     * <li>Trash</li>
     * <li>Spam</li>
     * <li>Outbox</li>
     * </ul>
     * The Inbox will always be included even if one of the special folders is configured to point
     * to the Inbox.
     * </p>
     *
     * @param search The {@code LocalSearch} instance to modify.
     */
    public void excludeUnwantedFolders(LocalSearch search) {
        excludeSpecialFolder(search, getTrashFolder());
        excludeSpecialFolder(search, getSpamFolder());
        excludeSpecialFolder(search, getOutboxFolderName());
        search.or(new SearchCondition(SearchField.FOLDER, Attribute.EQUALS, getInboxFolder()));
    }

    private void excludeSpecialFolder(LocalSearch search, String folderName) {
        if ((folderName != null) && !XryptoMail.FOLDER_NONE.equals(folderName)) {
            search.and(SearchField.FOLDER, folderName, Attribute.NOT_EQUALS);
        }
    }

    /**
     * Add a new certificate for the incoming or outgoing server to the local key store.
     */
    public void addCertificate(CheckDirection direction, X509Certificate certificate)
            throws CertificateException {
        Uri uri;
        if (direction == CheckDirection.INCOMING) {
            uri = Uri.parse(getStoreUri());
        }
        else {
            uri = Uri.parse(getTransportUri());
        }
        LocalKeyStore localKeyStore = LocalKeyStore.getInstance();
        localKeyStore.addCertificate(uri.getHost(), uri.getPort(), certificate);
    }

    /**
     * Examine the existing settings for an account.  If the old host/port is different from the
     * new host/port, then try and delete any (possibly non-existent) certificate stored for the
     * old host/port.
     */
    public void deleteCertificate(String newHost, int newPort, CheckDirection direction) {
        Uri uri;
        if (direction == CheckDirection.INCOMING) {
            uri = Uri.parse(getStoreUri());
        }
        else {
            uri = Uri.parse(getTransportUri());
        }
        String oldHost = uri.getHost();
        int oldPort = uri.getPort();
        if (oldPort == -1) {
            // This occurs when a new account is created
            return;
        }
        if (!newHost.equals(oldHost) || newPort != oldPort) {
            LocalKeyStore localKeyStore = LocalKeyStore.getInstance();
            localKeyStore.deleteCertificate(oldHost, oldPort);
        }
    }

    /**
     * Examine the settings for the account and attempt to delete (possibly non-existent)
     * certificates for the incoming and outgoing servers.
     */
    private void deleteCertificates() {
        LocalKeyStore localKeyStore = LocalKeyStore.getInstance();

        String storeUri = getStoreUri();
        if (storeUri != null) {
            Uri uri = Uri.parse(storeUri);
            localKeyStore.deleteCertificate(uri.getHost(), uri.getPort());
        }
        String transportUri = getTransportUri();
        if (transportUri != null) {
            Uri uri = Uri.parse(transportUri);
            localKeyStore.deleteCertificate(uri.getHost(), uri.getPort());
        }
    }

    public void init(String email, String password) {
        setName(getOwnerName());
        setEmail(email);

        String[] emailParts = EmailHelper.splitEmail(email);
        String user = emailParts[0];
        String domain = emailParts[1];

        // set default uris
        // NOTE: they will be changed again in AccountSetupAccountType!
        ServerSettings storeServer = new ServerSettings(ServerSettings.Type.IMAP, "mail." + domain, -1,
                ConnectionSecurity.SSL_TLS_REQUIRED, AuthType.PLAIN, user, password, null);
        ServerSettings transportServer = new ServerSettings(ServerSettings.Type.SMTP, "mail." + domain, -1,
                ConnectionSecurity.SSL_TLS_REQUIRED, AuthType.PLAIN, user, password, null);
        String storeUri = RemoteStore.createStoreUri(storeServer);
        String transportUri = TransportUris.createTransportUri(transportServer);
        setStoreUri(storeUri);
        setTransportUri(transportUri);

        setupFolderNames(domain);
    }

    private void setupFolderNames(String domain) {
        setDraftsFolder(XryptoMail.getResString(R.string.special_mailbox_name_drafts));
        setTrashFolder(XryptoMail.getResString(R.string.special_mailbox_name_trash));
        setSentFolder(XryptoMail.getResString(R.string.special_mailbox_name_sent));
        setArchiveFolderName(XryptoMail.getResString(R.string.special_mailbox_name_archive));

        // Yahoo! has a special folder for Spam, called "Bulk Mail".
        if (domain.endsWith(".yahoo.com")) {
            setSpamFolderName("Bulk Mail");
        }
        else {
            setSpamFolderName(XryptoMail.getResString(R.string.special_mailbox_name_spam));
        }
    }


    private String getOwnerName() {
        String name = null;
        try {
            name = getDefaultAccountName();
        } catch (Exception e) {
            Timber.e(e, "Could not get default account name");
        }

        if (name == null) {
            name = "";
        }
        return name;
    }

    private String getDefaultAccountName() {
        String name = null;
        Account account = Preferences.getPreferences(XryptoMail.mInstance).getDefaultAccount();
        if (account != null) {
            name = account.getName();
        }
        return name;
    }
}
