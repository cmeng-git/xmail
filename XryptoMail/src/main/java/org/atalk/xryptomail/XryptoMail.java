package org.atalk.xryptomail;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.app.DownloadManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;

import org.atalk.xryptomail.Account.SortType;
import org.atalk.xryptomail.account.XMailOAuth2TokenProvider;
import org.atalk.xryptomail.activity.MessageCompose;
import org.atalk.xryptomail.activity.UpgradeDatabases;
import org.atalk.xryptomail.controller.MessagingController;
import org.atalk.xryptomail.controller.SimpleMessagingListener;
import org.atalk.xryptomail.helper.timberlog.TimberLogImpl;
import org.atalk.xryptomail.mail.Address;
import org.atalk.xryptomail.mail.Message;
import org.atalk.xryptomail.mail.XryptoMailLib;
import org.atalk.xryptomail.mail.internet.BinaryTempFileBody;
import org.atalk.xryptomail.mail.ssl.LocalKeyStore;
import org.atalk.xryptomail.mailstore.LocalStore;
import org.atalk.xryptomail.power.DeviceIdleManager;
import org.atalk.xryptomail.preferences.Storage;
import org.atalk.xryptomail.preferences.StorageEditor;
import org.atalk.xryptomail.provider.UnreadWidgetProvider;
import org.atalk.xryptomail.service.BootReceiver;
import org.atalk.xryptomail.service.MailService;
import org.atalk.xryptomail.service.ShutdownReceiver;
import org.atalk.xryptomail.service.StorageGoneReceiver;
import org.atalk.xryptomail.widget.list.MessageListWidgetProvider;

import timber.log.Timber;

public class XryptoMail extends Application {
    public static final int PRC_WRITE_EXTERNAL_STORAGE = 2003;

    public static String mVersion;

    /**
     * Components that are interested in knowing when the XryptoMail instance is
     * available and ready (Android invokes Application.onCreate() after other
     * components') should implement this interface and register using
     * {@link XryptoMail#registerApplicationAware(ApplicationAware)}.
     */
    public interface ApplicationAware {
        /**
         * Called when the Application instance is available and ready.
         *
         * @param application The application instance. Never {@code null}.
         */
        void initializeComponent(Application application);
    }

    public static final String EXTRA_STARTUP = "startup";
    public static Application mInstance = null;

    /**
     * Name of the {@link SharedPreferences} file used to store the last known version of the
     * accounts' databases.
     *
     * See {@link UpgradeDatabases} for a detailed explanation of the database upgrade process.
     */
    private static final String DATABASE_VERSION_CACHE = "database_version_cache";

    /**
     * Key used to store the last known database version of the accounts' databases.
     *
     * @see #DATABASE_VERSION_CACHE
     */
    private static final String KEY_LAST_ACCOUNT_DATABASE_VERSION = "last_account_database_version";

    /**
     * Components that are interested in knowing when the XryptoMail instance is
     * available and ready.
     *
     * @see ApplicationAware
     */
    private static final List<ApplicationAware> observers = new ArrayList<>();

    /**
     * This will be {@code true} once the initialization is complete and {@link #notifyObservers()} was called.
     * Afterwards calls to {@link #registerApplicationAware(XryptoMail.ApplicationAware)} will
     * immediately call {@link XryptoMail.ApplicationAware#initializeComponent(Application)} for the
     * supplied argument.
     */
    private static boolean sInitialized = false;

    public enum BACKGROUND_OPS {
        ALWAYS,
        NEVER,
        WHEN_CHECKED_AUTO_SYNC
    }

    private static String language = "";
    private static Theme mTheme = Theme.LIGHT;
    private static Theme messageViewTheme = Theme.USE_GLOBAL;
    private static Theme composerTheme = Theme.USE_GLOBAL;
    private static boolean useFixedMessageTheme = true;

    private static final FontSizes fontSizes = new FontSizes();

    private static BACKGROUND_OPS backgroundOps = BACKGROUND_OPS.WHEN_CHECKED_AUTO_SYNC;

    /**
     * If this is enabled, various development settings will be enabled
     * It should NEVER be on for Market builds Right now, it just governs strictMode
     **/
    public static boolean DEVELOPER_MODE = BuildConfig.DEVELOPER_MODE;

    /**
     * If this is enabled there will be additional logging information sent to
     * Log.d, including protocol dumps. Controlled by Preferences at run-time
     * Default to true for debug version apk
     */
    private static boolean DEBUG = BuildConfig.DEBUG;

    /**
     * If this is enabled than logging that normally hides sensitive information
     * like passwords will show that information.
     */
    public static boolean DEBUG_SENSITIVE = false;

    /**
     * A reference to the {@link SharedPreferences} used for caching the last known database
     * version.
     *
     * @see #checkCachedDatabaseVersion()
     * @see #setDatabasesUpToDate(boolean)
     */
    private static SharedPreferences sDatabaseVersionCache;
    private static boolean mAnimations = true;

    private static boolean mConfirmDelete = false;
    private static boolean mConfirmDiscardMessage = true;
    private static boolean mConfirmDeleteStarred = false;
    private static boolean mConfirmSpam = false;
    private static boolean mConfirmDeleteFromNotification = true;
    private static boolean mConfirmMarkAllRead = true;

    private static NotificationHideSubject sNotificationHideSubject = NotificationHideSubject.WHEN_LOCKED;

    /**
     * Controls when to hide the subject in the notification area.
     */
    public enum NotificationHideSubject {
        ALWAYS,
        WHEN_LOCKED,
        NEVER
    }

    private static NotificationQuickDelete sNotificationQuickDelete = NotificationQuickDelete.NEVER;

    /**
     * Possible values for the different theme settings.
     *
     * <p><strong>Important:</strong>
     * Do not change the order of the items! The ordinal value (position) is used when saving the
     * settings.</p>
     */
    public enum Theme {
        LIGHT,
        DARK,
        USE_GLOBAL
    }

    /**
     * Controls behaviour of delete button in notifications.
     */
    public enum NotificationQuickDelete {
        ALWAYS,
        FOR_SINGLE_MSG,
        NEVER
    }

    private static LockScreenNotificationVisibility sLockScreenNotificationVisibility =
            LockScreenNotificationVisibility.MESSAGE_COUNT;

    public enum LockScreenNotificationVisibility {
        EVERYTHING,
        SENDERS,
        MESSAGE_COUNT,
        APP_NAME,
        NOTHING
    }

    /**
     * Controls when to use the message list split view.
     */
    public enum SplitViewMode {
        ALWAYS,
        NEVER,
        WHEN_IN_LANDSCAPE
    }

    private static boolean mMessageListCheckboxes = true;
    private static boolean mMessageListStars = true;
    private static int mMessageListPreviewLines = 2;

    private static boolean mShowCorrespondentNames = true;
    private static boolean mMessageListSenderAboveSubject = false;
    private static boolean mShowContactName = false;
    private static boolean mChangeContactNameColor = false;
    private static int mContactNameColor = 0xff00008f;
    private static boolean sShowContactPicture = true;
    private static boolean mMessageViewFixedWidthFont = false;
    private static boolean mMessageViewReturnToList = false;
    private static boolean mMessageViewShowNext = true;

    private static boolean mGesturesEnabled = true;
    private static boolean mUseVolumeKeysForNavigation = false;
    private static boolean mUseVolumeKeysForListNavigation = false;
    private static boolean mStartIntegratedInbox = false;
    private static boolean mMeasureAccounts = true;
    private static boolean mCountSearchMessages = true;
    private static boolean mHideSpecialAccounts = false;
    private static boolean mAutofitWidth = true;
    private static boolean mQuietTimeEnabled = true;
    private static boolean mNotificationDuringQuietTimeEnabled = true;
    private static String mQuietTimeStarts = null;
    private static String mQuietTimeEnds = null;
    private static String mAttachmentDefaultPath = "";
    private static boolean mWrapFolderNames = false;
    private static boolean mHideUserAgent = false;
    private static boolean mHideTimeZone = false;
    private static boolean hideHostnameWhenConnecting = false;

    private static String sOpenPgpProvider = "";
    private static boolean sOpenPgpSupportSignOnly = false;

    private static SortType mSortType;
    private static final Map<SortType, Boolean> mSortAscending = new HashMap<>();

    private static boolean sUseBackgroundAsUnreadIndicator = true;
    private static boolean sThreadedViewEnabled = true;
    private static SplitViewMode sSplitViewMode = SplitViewMode.NEVER;
    private static boolean sColorizeMissingContactPictures = true;

    private static boolean sMessageViewArchiveActionVisible = false;
    private static boolean sMessageViewDeleteActionVisible = true;
    private static boolean sMessageViewMoveActionVisible = false;
    private static boolean sMessageViewCopyActionVisible = false;
    private static boolean sMessageViewSpamActionVisible = false;

    private static int sPgpInlineDialogCounter;
    private static int sPgpSignOnlyDialogCounter;

    /**
     * @see #areDatabasesUpToDate()
     */
    private static boolean sDatabasesUpToDate = false;

    /**
     * For use when displaying that no folder is selected
     */
    public static final String FOLDER_NONE = "-NONE-";
    public static final String LOCAL_UID_PREFIX = "XryptoMailLOCAL:";
    public static final String IDENTITY_HEADER = XryptoMailLib.IDENTITY_HEADER;

    /**
     * Specifies how many messages will be shown in a folder by default. This number is set
     * on each new folder and can be incremented with "Load more messages..." by the
     * VISIBLE_LIMIT_INCREMENT
     */
    public static final int DEFAULT_VISIBLE_LIMIT = 25;

    /**
     * The maximum size of an attachment we're willing to download (either View or Save)
     * Attachments that are base64 encoded (most) will be about 1.375x their actual size
     * so we should probably factor that in. A 5MB attachment will generally be around
     * 6.8MB downloaded but only 5MB saved.
     */
    public static final int MAX_ATTACHMENT_DOWNLOAD_SIZE = (128 * 1024 * 1024);

    /* How many times should K-9 try to deliver a message before giving up
     * until the app is killed and restarted
     */
    public static final int MAX_SEND_ATTEMPTS = 5;

    /*
     * strictmode-java-lang-throwable-untagged-socket-detected
     */
    public static final int THREAD_ID = 10000;

    /**
     * Max time (in millis) the wake lock will be held for when background sync is happening
     */
    public static final int WAKE_LOCK_TIMEOUT = 600000;
    public static final int MANUAL_WAKE_LOCK_TIMEOUT = 120000;
    public static final int PUSH_WAKE_LOCK_TIMEOUT = XryptoMailLib.PUSH_WAKE_LOCK_TIMEOUT;
    public static final int BOOT_RECEIVER_WAKE_LOCK_TIMEOUT = 60000;
    public static final String NO_OPENPGP_PROVIDER = "";

    public static class Intents {
        public static class EmailReceived {
            public static final String ACTION_EMAIL_RECEIVED = BuildConfig.APPLICATION_ID + ".intent.action.EMAIL_RECEIVED";
            public static final String ACTION_EMAIL_DELETED = BuildConfig.APPLICATION_ID + ".intent.action.EMAIL_DELETED";
            public static final String ACTION_REFRESH_OBSERVER = BuildConfig.APPLICATION_ID + ".intent.action.REFRESH_OBSERVER";
            public static final String EXTRA_ACCOUNT = BuildConfig.APPLICATION_ID + ".intent.extra.ACCOUNT";
            public static final String EXTRA_FOLDER = BuildConfig.APPLICATION_ID + ".intent.extra.FOLDER";
            public static final String EXTRA_SENT_DATE = BuildConfig.APPLICATION_ID + ".intent.extra.SENT_DATE";
            public static final String EXTRA_FROM = BuildConfig.APPLICATION_ID + ".intent.extra.FROM";
            public static final String EXTRA_TO = BuildConfig.APPLICATION_ID + ".intent.extra.TO";
            public static final String EXTRA_CC = BuildConfig.APPLICATION_ID + ".intent.extra.CC";
            public static final String EXTRA_BCC = BuildConfig.APPLICATION_ID + ".intent.extra.BCC";
            public static final String EXTRA_SUBJECT = BuildConfig.APPLICATION_ID + ".intent.extra.SUBJECT";
            public static final String EXTRA_FROM_SELF = BuildConfig.APPLICATION_ID + ".intent.extra.FROM_SELF";
        }

        public static class Share {
            /*
             * We don't want to use EmailReceived.EXTRA_FROM ("org.atalk.xryptomail.intent.extra.FROM") because
             * of different semantics (String array vs. string with comma separated email addresses)
             */
            public static final String EXTRA_FROM = BuildConfig.APPLICATION_ID + ".intent.extra.SENDER";
        }
    }

    /**
     * Called throughout the application when the number of accounts has changed. This method
     * enables or disables the Compose activity, the boot receiver and the service based on
     * whether any accounts are configured.
     */
    public static void setServicesEnabled(Context context) {
        Context appContext = context.getApplicationContext();
        int acctLength = Preferences.getPreferences(appContext).getAvailableAccounts().size();
        boolean enable = acctLength > 0;

        setServicesEnabled(appContext, enable, null);
        updateDeviceIdleReceiver(appContext, enable);
    }

    private static void updateDeviceIdleReceiver(Context context, boolean enable) {
        DeviceIdleManager deviceIdleManager = DeviceIdleManager.getInstance(context);
        if (enable) {
            deviceIdleManager.registerReceiver();
        }
        else {
            deviceIdleManager.unregisterReceiver();
        }
    }

    private static void setServicesEnabled(Context context, boolean enabled, Integer wakeLockId) {
        PackageManager pm = context.getPackageManager();
        if (!enabled && pm.getComponentEnabledSetting(new ComponentName(context, MailService.class)) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            /*
             * If no account now exist but the service is still enabled we're about to disable it
             * so we'll reschedule to kill off any existing alarms.
             */
            MailService.actionReset(context);
        }
        Class<?>[] classes = {MessageCompose.class, BootReceiver.class, MailService.class};

        for (Class<?> clazz : classes) {
            boolean alreadyEnabled = pm.getComponentEnabledSetting(new ComponentName(context, clazz)) ==
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED;

            if (enabled != alreadyEnabled) {
                pm.setComponentEnabledSetting(
                        new ComponentName(context, clazz),
                        enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
            }
        }

        if (enabled && pm.getComponentEnabledSetting(new ComponentName(context, MailService.class)) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            /*
             * And now if account do exist then we've just enabled the service and we want to
             * schedule alarms for the new accounts.
             */
            MailService.actionReset(context);
        }
    }

    /**
     * Register BroadcastReceivers programmatically because doing it from manifest
     * would make K-9 auto-start. We don't want auto-start because the initialization
     * sequence isn't safe while some events occur (SD card unmount).
     */
    protected void registerReceivers() {
        final StorageGoneReceiver receiver = new StorageGoneReceiver();
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_EJECT);
        filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        filter.addDataScheme("file");

        final BlockingQueue<Handler> queue = new SynchronousQueue<>();

        // starting a new thread to handle unmount events
        new Thread(() -> {
            Looper.prepare();
            try {
                queue.put(new Handler());
            } catch (InterruptedException e) {
                Timber.e(e);
            }
            Looper.loop();
        }, "Unmount-thread").start();

        try {
            final Handler storageGoneHandler = queue.take();
            registerReceiver(receiver, filter, null, storageGoneHandler);
            Timber.i("Registered: unmount receiver");
        } catch (InterruptedException e) {
            Timber.e(e, "Unable to register unmount receiver");
        }

        registerReceiver(new ShutdownReceiver(), new IntentFilter(Intent.ACTION_SHUTDOWN));
        Timber.i("Registered: shutdown receiver");
    }

    public static void save(StorageEditor editor) {
        editor.putBoolean("enableDebugLogging", XryptoMail.DEBUG);
        editor.putBoolean("enableSensitiveLogging", XryptoMail.DEBUG_SENSITIVE);
        editor.putString("backgroundOperations", XryptoMail.backgroundOps.name());
        editor.putBoolean("animations", mAnimations);
        editor.putBoolean("gesturesEnabled", mGesturesEnabled);
        editor.putBoolean("useVolumeKeysForNavigation", mUseVolumeKeysForNavigation);
        editor.putBoolean("useVolumeKeysForListNavigation", mUseVolumeKeysForListNavigation);
        editor.putBoolean("autofitWidth", mAutofitWidth);
        editor.putBoolean("quietTimeEnabled", mQuietTimeEnabled);
        editor.putBoolean("notificationDuringQuietTimeEnabled", mNotificationDuringQuietTimeEnabled);
        editor.putString("quietTimeStarts", mQuietTimeStarts);
        editor.putString("quietTimeEnds", mQuietTimeEnds);

        editor.putBoolean("startIntegratedInbox", mStartIntegratedInbox);
        editor.putBoolean("measureAccounts", mMeasureAccounts);
        editor.putBoolean("countSearchMessages", mCountSearchMessages);
        editor.putBoolean("messageListSenderAboveSubject", mMessageListSenderAboveSubject);
        editor.putBoolean("hideSpecialAccounts", mHideSpecialAccounts);
        editor.putBoolean("messageListStars", mMessageListStars);
        editor.putInt("messageListPreviewLines", mMessageListPreviewLines);
        editor.putBoolean("messageListCheckboxes", mMessageListCheckboxes);
        editor.putBoolean("showCorrespondentNames", mShowCorrespondentNames);
        editor.putBoolean("showContactName", mShowContactName);
        editor.putBoolean("showContactPicture", sShowContactPicture);
        editor.putBoolean("changeRegisteredNameColor", mChangeContactNameColor);
        editor.putInt("registeredNameColor", mContactNameColor);
        editor.putBoolean("messageViewFixedWidthFont", mMessageViewFixedWidthFont);
        editor.putBoolean("messageViewReturnToList", mMessageViewReturnToList);
        editor.putBoolean("messageViewShowNext", mMessageViewShowNext);

        editor.putBoolean("wrapFolderNames", mWrapFolderNames);
        editor.putBoolean("hideUserAgent", mHideUserAgent);
        editor.putBoolean("hideTimeZone", mHideTimeZone);
        editor.putBoolean("hideHostnameWhenConnecting", hideHostnameWhenConnecting);

        editor.putString("openPgpProvider", sOpenPgpProvider);
        editor.putBoolean("openPgpSupportSignOnly", sOpenPgpSupportSignOnly);

        editor.putString("language", language);
        editor.putInt("theme", mTheme.ordinal());
        editor.putInt("messageViewTheme", messageViewTheme.ordinal());
        editor.putInt("messageComposeTheme", composerTheme.ordinal());
        editor.putBoolean("fixedMessageViewTheme", useFixedMessageTheme);
        editor.putBoolean("confirmDelete", mConfirmDelete);
        editor.putBoolean("confirmDiscardMessage", mConfirmDiscardMessage);
        editor.putBoolean("confirmDeleteStarred", mConfirmDeleteStarred);
        editor.putBoolean("confirmSpam", mConfirmSpam);
        editor.putBoolean("confirmDeleteFromNotification", mConfirmDeleteFromNotification);
        editor.putBoolean("confirmMarkAllRead", mConfirmMarkAllRead);

        editor.putString("sortTypeEnum", mSortType.name());
        editor.putBoolean("sortAscending", Boolean.TRUE.equals(mSortAscending.get(mSortType)));

        editor.putString("notificationHideSubject", sNotificationHideSubject.toString());
        editor.putString("notificationQuickDelete", sNotificationQuickDelete.toString());
        editor.putString("lockScreenNotificationVisibility", sLockScreenNotificationVisibility.toString());

        editor.putString("attachmentdefaultpath", mAttachmentDefaultPath);
        editor.putBoolean("useBackgroundAsUnreadIndicator", sUseBackgroundAsUnreadIndicator);
        editor.putBoolean("threadedView", sThreadedViewEnabled);
        editor.putString("splitViewMode", sSplitViewMode.name());
        editor.putBoolean("colorizeMissingContactPictures", sColorizeMissingContactPictures);

        editor.putBoolean("messageViewArchiveActionVisible", sMessageViewArchiveActionVisible);
        editor.putBoolean("messageViewDeleteActionVisible", sMessageViewDeleteActionVisible);
        editor.putBoolean("messageViewMoveActionVisible", sMessageViewMoveActionVisible);
        editor.putBoolean("messageViewCopyActionVisible", sMessageViewCopyActionVisible);
        editor.putBoolean("messageViewSpamActionVisible", sMessageViewSpamActionVisible);
        editor.putInt("pgpInlineDialogCounter", sPgpInlineDialogCounter);
        editor.putInt("pgpSignOnlyDialogCounter", sPgpSignOnlyDialogCounter);
        fontSizes.save(editor);
    }

    private void getAppVersion() {
        PackageInfo pInfo;
        try {
            pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            mVersion = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            mVersion = "";
        }
    }

    private void setupStrictMode() {
        if (XryptoMail.DEVELOPER_MODE) {
            StrictMode.enableDefaults();
        }

        // #TODO - change all disk access to using thread
        // cmeng - disable android.os.StrictMode$StrictModeDisk Access Violation
        StrictMode.ThreadPolicy old = StrictMode.getThreadPolicy();
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder(old)
                .permitDiskReads()
                .permitDiskWrites()
                .build());
        //    StrictMode.setThreadPolicy(old);
    }

    @Override
    public void onCreate() {
        setupStrictMode();
        getAppVersion();

        super.onCreate();
        mInstance = this;
        Globals.setContext(this);
        Globals.setOAuth2TokenProvider(new XMailOAuth2TokenProvider(this));
        TimberLogImpl.init();

        XryptoMailLib.setDebugStatus(new XryptoMailLib.DebugStatus() {
            @Override
            public boolean enabled() {
                return DEBUG;
            }

            @Override
            public boolean debugSensitive() {
                return DEBUG_SENSITIVE;
            }
        });
        checkCachedDatabaseVersion();
        Preferences prefs = Preferences.getPreferences(this);
        loadPrefs(prefs);

        /*
         * We have to give MimeMessage a temp directory because
         * File.createTempFile(String, String) doesn't work in Android and
         * MimeMessage does not have access to a Context.
         */
        BinaryTempFileBody.setTempDirectory(getCacheDir());
        // LocalKeyStore.setKeyStoreLocation(getDir("KeyStore", MODE_PRIVATE).toString());
        LocalKeyStore.createInstance(this);

        /*
         * Enable background sync of messages - kill by android???
         */
        setServicesEnabled(this);
        registerReceivers();

        MessagingController.getInstance(this).addListener(new SimpleMessagingListener() {
            private void broadcastIntent(String action, Account account, String folder, Message message) {
                Uri uri = Uri.parse("email://messages/" + account.getAccountNumber() + "/" + Uri.encode(folder) + "/" + Uri.encode(message.getUid()));
                Intent intent = new Intent(action, uri);
                intent.putExtra(XryptoMail.Intents.EmailReceived.EXTRA_ACCOUNT, account.getDescription());
                intent.putExtra(XryptoMail.Intents.EmailReceived.EXTRA_FOLDER, folder);
                intent.putExtra(XryptoMail.Intents.EmailReceived.EXTRA_SENT_DATE, message.getSentDate());
                intent.putExtra(XryptoMail.Intents.EmailReceived.EXTRA_FROM, Address.toString(message.getFrom()));
                intent.putExtra(XryptoMail.Intents.EmailReceived.EXTRA_TO, Address.toString(message.getRecipients(Message.RecipientType.TO)));
                intent.putExtra(XryptoMail.Intents.EmailReceived.EXTRA_CC, Address.toString(message.getRecipients(Message.RecipientType.CC)));
                intent.putExtra(XryptoMail.Intents.EmailReceived.EXTRA_BCC, Address.toString(message.getRecipients(Message.RecipientType.BCC)));
                intent.putExtra(XryptoMail.Intents.EmailReceived.EXTRA_SUBJECT, message.getSubject());
                intent.putExtra(XryptoMail.Intents.EmailReceived.EXTRA_FROM_SELF, account.isAnIdentity(message.getFrom()));
                intent.setPackage(getPackageName());
                XryptoMail.this.sendBroadcast(intent);
                Timber.d("Broadcast: action=%s account=%s folder=%s message uid=%s",
                        action, account.getDescription(), folder, message.getUid());
            }

            private void updateUnreadWidget() {
                try {
                    UnreadWidgetProvider.updateUnreadCount(XryptoMail.this);
                } catch (Exception e) {
                    Timber.e(e, "Error while updating unread widget(s)");
                }
            }

            private void updateMailListWidget() {
                try {
                    MessageListWidgetProvider.triggerMessageListWidgetUpdate(XryptoMail.this);
                } catch (RuntimeException e) {
                    if (BuildConfig.DEBUG) {
                        throw e;
                    }
                    else {
                        Timber.e(e, "Error while updating message list widget");
                    }
                }
            }

            @Override
            public void synchronizeMailboxRemovedMessage(Account account, String folder, Message message) {
                broadcastIntent(XryptoMail.Intents.EmailReceived.ACTION_EMAIL_DELETED, account, folder, message);
                updateUnreadWidget();
                updateMailListWidget();
            }

            @Override
            public void messageDeleted(Account account, String folder, Message message) {
                broadcastIntent(XryptoMail.Intents.EmailReceived.ACTION_EMAIL_DELETED, account, folder, message);
                updateUnreadWidget();
                updateMailListWidget();
            }

            @Override
            public void synchronizeMailboxNewMessage(Account account, String folder, Message message) {
                broadcastIntent(XryptoMail.Intents.EmailReceived.ACTION_EMAIL_RECEIVED, account, folder, message);
                updateUnreadWidget();
                updateMailListWidget();
            }

            @Override
            public void folderStatusChanged(Account account, String folderName, int unreadMessageCount) {
                updateUnreadWidget();
                updateMailListWidget();

                // let observers know a change occurred
                Intent intent = new Intent(XryptoMail.Intents.EmailReceived.ACTION_REFRESH_OBSERVER, null);
                intent.putExtra(XryptoMail.Intents.EmailReceived.EXTRA_ACCOUNT, account.getDescription());
                intent.putExtra(XryptoMail.Intents.EmailReceived.EXTRA_FOLDER, folderName);
                intent.setPackage(getPackageName());
                XryptoMail.this.sendBroadcast(intent);
            }
        });
        notifyObservers();
    }

    /**
     * Loads the last known database version of the accounts' databases from a
     * {@code SharedPreference}.
     *
     * <p>
     * If the stored version matches {@link LocalStore#DB_VERSION} we know that the databases are
     * up to date.<br>
     * Using {@code SharedPreferences} should be a lot faster than opening all SQLite databases to
     * get the current database version.
     * </p><p>
     * See {@link UpgradeDatabases} for a detailed explanation of the database upgrade process.
     * </p>
     *
     * @see #areDatabasesUpToDate()
     */
    public void checkCachedDatabaseVersion() {
        sDatabaseVersionCache = getSharedPreferences(DATABASE_VERSION_CACHE, MODE_PRIVATE);
        int cachedVersion = sDatabaseVersionCache.getInt(KEY_LAST_ACCOUNT_DATABASE_VERSION, 0);
        if (cachedVersion >= LocalStore.DB_VERSION) {
            XryptoMail.setDatabasesUpToDate(false);
        }
    }

    /**
     * Load preferences into our statics.
     *
     * If you're adding a preference here, odds are you'll need to add it to
     * {@link .preferences.GlobalSettings}, too.
     *
     * @param prefs Preferences to load
     */
    public static void loadPrefs(Preferences prefs) {
        Storage storage = prefs.getStorage();
        setDebug(storage.getBoolean("enableDebugLogging", BuildConfig.DEVELOPER_MODE));
        DEBUG_SENSITIVE = storage.getBoolean("enableSensitiveLogging", false);
        mAnimations = storage.getBoolean("animations", true);
        mGesturesEnabled = storage.getBoolean("gesturesEnabled", false);
        mUseVolumeKeysForNavigation = storage.getBoolean("useVolumeKeysForNavigation", false);
        mUseVolumeKeysForListNavigation = storage.getBoolean("useVolumeKeysForListNavigation", false);
        mStartIntegratedInbox = storage.getBoolean("startIntegratedInbox", false);
        mMeasureAccounts = storage.getBoolean("measureAccounts", false);
        mCountSearchMessages = storage.getBoolean("countSearchMessages", false);
        mHideSpecialAccounts = storage.getBoolean("hideSpecialAccounts", false);
        mMessageListSenderAboveSubject = storage.getBoolean("messageListSenderAboveSubject", true);
        mMessageListCheckboxes = storage.getBoolean("messageListCheckboxes", false);
        mMessageListStars = storage.getBoolean("messageListStars", true);
        mMessageListPreviewLines = storage.getInt("messageListPreviewLines", 1);
        mAutofitWidth = storage.getBoolean("autofitWidth", true);
        mQuietTimeEnabled = storage.getBoolean("quietTimeEnabled", true);
        mNotificationDuringQuietTimeEnabled = storage.getBoolean("notificationDuringQuietTimeEnabled", true);
        mQuietTimeStarts = storage.getString("quietTimeStarts", "23:00");
        mQuietTimeEnds = storage.getString("quietTimeEnds", "7:00");

        mShowCorrespondentNames = storage.getBoolean("showCorrespondentNames", true);
        mShowContactName = storage.getBoolean("showContactName", false);
        sShowContactPicture = storage.getBoolean("showContactPicture", true);
        mChangeContactNameColor = storage.getBoolean("changeRegisteredNameColor", false);
        mContactNameColor = storage.getInt("registeredNameColor", 0xff00008f);
        mMessageViewFixedWidthFont = storage.getBoolean("messageViewFixedWidthFont", false);
        mMessageViewReturnToList = storage.getBoolean("messageViewReturnToList", false);
        mMessageViewShowNext = storage.getBoolean("messageViewShowNext", true);

        mWrapFolderNames = storage.getBoolean("wrapFolderNames", false);
        mHideUserAgent = storage.getBoolean("hideUserAgent", false);
        mHideTimeZone = storage.getBoolean("hideTimeZone", false);
        hideHostnameWhenConnecting = storage.getBoolean("hideHostnameWhenConnecting", false);

        sOpenPgpProvider = storage.getString("openPgpProvider", NO_OPENPGP_PROVIDER);
        sOpenPgpSupportSignOnly = storage.getBoolean("openPgpSupportSignOnly", false);

        mConfirmDelete = storage.getBoolean("confirmDelete", false);
        mConfirmDiscardMessage = storage.getBoolean("confirmDiscardMessage", true);
        mConfirmDeleteStarred = storage.getBoolean("confirmDeleteStarred", false);
        mConfirmSpam = storage.getBoolean("confirmSpam", false);
        mConfirmDeleteFromNotification = storage.getBoolean("confirmDeleteFromNotification", true);
        mConfirmMarkAllRead = storage.getBoolean("confirmMarkAllRead", true);

        try {
            String value = storage.getString("sortTypeEnum", Account.DEFAULT_SORT_TYPE.name());
            mSortType = SortType.valueOf(value);
        } catch (Exception e) {
            mSortType = Account.DEFAULT_SORT_TYPE;
        }

        boolean sortAscending = storage.getBoolean("sortAscending", Account.DEFAULT_SORT_ASCENDING);
        mSortAscending.put(mSortType, sortAscending);

        String notificationHideSubject = storage.getString("notificationHideSubject", null);
        if (notificationHideSubject == null) {
            // If the "notificationHideSubject" setting couldn't be found, the app was probably
            // updated. Look for the old "keyguardPrivacy" setting and map it to the new enum.
            sNotificationHideSubject = (storage.getBoolean("keyguardPrivacy", false)) ?
                    NotificationHideSubject.WHEN_LOCKED : NotificationHideSubject.NEVER;
        }
        else {
            sNotificationHideSubject = NotificationHideSubject.valueOf(notificationHideSubject);
        }

        String notificationQuickDelete = storage.getString("notificationQuickDelete", null);
        if (notificationQuickDelete != null) {
            sNotificationQuickDelete = NotificationQuickDelete.valueOf(notificationQuickDelete);
        }

        String lockScreenNotificationVisibility = storage.getString("lockScreenNotificationVisibility", null);
        if (lockScreenNotificationVisibility != null) {
            sLockScreenNotificationVisibility = LockScreenNotificationVisibility.valueOf(lockScreenNotificationVisibility);
        }

        String splitViewMode = storage.getString("splitViewMode", null);
        if (splitViewMode != null) {
            sSplitViewMode = SplitViewMode.valueOf(splitViewMode);
        }

        mAttachmentDefaultPath = storage.getString("attachmentdefaultpath",
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString());
        sUseBackgroundAsUnreadIndicator = storage.getBoolean("useBackgroundAsUnreadIndicator", true);
        sThreadedViewEnabled = storage.getBoolean("threadedView", true);
        fontSizes.load(storage);

        try {
            setBackgroundOps(BACKGROUND_OPS.valueOf(storage.getString(
                    "backgroundOperations", BACKGROUND_OPS.WHEN_CHECKED_AUTO_SYNC.name())));
        } catch (Exception e) {
            setBackgroundOps(BACKGROUND_OPS.WHEN_CHECKED_AUTO_SYNC);
        }

        sColorizeMissingContactPictures = storage.getBoolean("colorizeMissingContactPictures", true);

        sMessageViewArchiveActionVisible = storage.getBoolean("messageViewArchiveActionVisible", false);
        sMessageViewDeleteActionVisible = storage.getBoolean("messageViewDeleteActionVisible", true);
        sMessageViewMoveActionVisible = storage.getBoolean("messageViewMoveActionVisible", false);
        sMessageViewCopyActionVisible = storage.getBoolean("messageViewCopyActionVisible", false);
        sMessageViewSpamActionVisible = storage.getBoolean("messageViewSpamActionVisible", false);
        sPgpInlineDialogCounter = storage.getInt("pgpInlineDialogCounter", 0);
        sPgpSignOnlyDialogCounter = storage.getInt("pgpSignOnlyDialogCounter", 0);
        XryptoMail.setXMLanguage(storage.getString("language", ""));

        // We used to save the resource ID of the theme. So convert that to the new format if necessary.
        int themeValue = storage.getInt("theme", Theme.LIGHT.ordinal());
        if (themeValue == Theme.DARK.ordinal() || themeValue == android.R.style.Theme) {
            setXMTheme(Theme.DARK);
        }
        else {
            setXMTheme(Theme.LIGHT);
        }

        themeValue = storage.getInt("messageViewTheme", Theme.USE_GLOBAL.ordinal());
        setXMMessageViewThemeSetting(Theme.values()[themeValue]);
        themeValue = storage.getInt("messageComposeTheme", Theme.USE_GLOBAL.ordinal());
        setXMComposerThemeSetting(Theme.values()[themeValue]);
        setUseFixedMessageViewTheme(storage.getBoolean("fixedMessageViewTheme", true));
    }

    /**
     * since Android invokes Application.onCreate() only after invoking all
     * other components' onCreate(), here is a way to notify interested
     * component that the application is available and ready
     */
    protected void notifyObservers() {
        synchronized (observers) {
            for (final ApplicationAware aware : observers) {
                Timber.v("Initializing observer: %s", aware);

                try {
                    aware.initializeComponent(this);
                } catch (Exception e) {
                    Timber.w(e, "Failure when notifying %s", aware);
                }
            }
            sInitialized = true;
            observers.clear();
        }
    }

    /**
     * Register a component to be notified when the {@link XryptoMail} instance is ready.
     *
     * @param component Never <code>null</code>.
     */
    public static void registerApplicationAware(final ApplicationAware component) {
        synchronized (observers) {
            if (sInitialized) {
                component.initializeComponent(mInstance);
            }
            else if (!observers.contains(component)) {
                observers.add(component);
            }
        }
    }

    public static String getXMLanguage() {
        return language;
    }

    public static void setXMLanguage(String nlanguage) {
        language = nlanguage;
    }

    public static int getXMThemeResourceId(Theme theme) {
        return (theme == Theme.LIGHT) ? R.style.Theme_XMail_Light : R.style.Theme_XMail_Dark;
    }

    public static int getXMThemeResourceId() {
        return getXMThemeResourceId(mTheme);
    }

    public static Theme getXMMessageViewTheme() {
        return messageViewTheme == Theme.USE_GLOBAL ? mTheme : messageViewTheme;
    }

    public static Theme getXMMessageViewThemeSetting() {
        return messageViewTheme;
    }

    public static Theme getXMComposerTheme() {
        return composerTheme == Theme.USE_GLOBAL ? mTheme : composerTheme;
    }

    public static Theme getXMComposerThemeSetting() {
        return composerTheme;
    }

    public static Theme getXMTheme() {
        return mTheme;
    }

    public static void setXMTheme(Theme theme) {
        if (theme != Theme.USE_GLOBAL) {
            mTheme = theme;
        }
    }

    public static void setXMMessageViewThemeSetting(Theme nMessageViewTheme) {
        messageViewTheme = nMessageViewTheme;
    }

    public static boolean useFixedMessageViewTheme() {
        return useFixedMessageTheme;
    }

    public static void setXMComposerThemeSetting(Theme compTheme) {
        composerTheme = compTheme;
    }

    public static void setUseFixedMessageViewTheme(boolean useFixed) {
        useFixedMessageTheme = useFixed;
        if (!useFixedMessageTheme && messageViewTheme == Theme.USE_GLOBAL) {
            messageViewTheme = mTheme;
        }
    }

    public static BACKGROUND_OPS getBackgroundOps() {
        return backgroundOps;
    }

    public static boolean setBackgroundOps(BACKGROUND_OPS backgroundOps) {
        BACKGROUND_OPS oldBackgroundOps = XryptoMail.backgroundOps;
        XryptoMail.backgroundOps = backgroundOps;
        return backgroundOps != oldBackgroundOps;
    }

    public static boolean setBackgroundOps(String backgroundOps) {
        return setBackgroundOps(BACKGROUND_OPS.valueOf(backgroundOps));
    }

    public static boolean gesturesEnabled() {
        return mGesturesEnabled;
    }

    public static void setGesturesEnabled(boolean gestures) {
        mGesturesEnabled = gestures;
    }

    public static boolean useVolumeKeysForNavigationEnabled() {
        return mUseVolumeKeysForNavigation;
    }

    public static void setUseVolumeKeysForNavigation(boolean volume) {
        mUseVolumeKeysForNavigation = volume;
    }

    public static boolean useVolumeKeysForListNavigationEnabled() {
        return mUseVolumeKeysForListNavigation;
    }

    public static void setUseVolumeKeysForListNavigation(boolean enabled) {
        mUseVolumeKeysForListNavigation = enabled;
    }

    public static boolean autofitWidth() {
        return mAutofitWidth;
    }

    public static void setAutoFitWidth(boolean autoFitWidth) {
        mAutofitWidth = autoFitWidth;
    }

    public static boolean getQuietTimeEnabled() {
        return mQuietTimeEnabled;
    }

    public static void setQuietTimeEnabled(boolean quietTimeEnabled) {
        mQuietTimeEnabled = quietTimeEnabled;
    }

    public static boolean isNotificationDuringQuietTimeEnabled() {
        return mNotificationDuringQuietTimeEnabled;
    }

    public static void setNotificationDuringQuietTimeEnabled(boolean notificationDuringQuietTimeEnabled) {
        mNotificationDuringQuietTimeEnabled = notificationDuringQuietTimeEnabled;
    }

    public static String getQuietTimeStarts() {
        return mQuietTimeStarts;
    }

    public static void setQuietTimeStarts(String quietTimeStarts) {
        mQuietTimeStarts = quietTimeStarts;
    }

    public static String getQuietTimeEnds() {
        return mQuietTimeEnds;
    }

    public static void setQuietTimeEnds(String quietTimeEnds) {
        mQuietTimeEnds = quietTimeEnds;
    }

    public static boolean isQuietTime() {
        if (!mQuietTimeEnabled) {
            return false;
        }

        QuietTimeChecker quietTimeChecker = new QuietTimeChecker(Clock.INSTANCE, mQuietTimeStarts, mQuietTimeEnds);
        return quietTimeChecker.isQuietTime();
    }

    public static void setDebug(boolean debug) {
        XryptoMail.DEBUG = debug;
        updateLoggingStatus();
    }

    public static boolean isDebug() {
        return DEBUG;
    }

    public static boolean startIntegratedInbox() {
        return mStartIntegratedInbox;
    }

    public static void setStartIntegratedInbox(boolean startIntegratedInbox) {
        mStartIntegratedInbox = startIntegratedInbox;
    }

    public static boolean showAnimations() {
        return mAnimations;
    }

    public static void setAnimations(boolean animations) {
        mAnimations = animations;
    }

    public static int messageListPreviewLines() {
        return mMessageListPreviewLines;
    }

    public static void setMessageListPreviewLines(int lines) {
        mMessageListPreviewLines = lines;
    }

    public static boolean messageListCheckboxes() {
        return mMessageListCheckboxes;
    }

    public static void setMessageListCheckboxes(boolean checkboxes) {
        mMessageListCheckboxes = checkboxes;
    }

    public static boolean messageListStars() {
        return mMessageListStars;
    }

    public static void setMessageListStars(boolean stars) {
        mMessageListStars = stars;
    }

    public static boolean showCorrespondentNames() {
        return mShowCorrespondentNames;
    }

    public static boolean messageListSenderAboveSubject() {
        return mMessageListSenderAboveSubject;
    }

    public static void setMessageListSenderAboveSubject(boolean sender) {
        mMessageListSenderAboveSubject = sender;
    }

    public static void setShowCorrespondentNames(boolean showCorrespondentNames) {
        mShowCorrespondentNames = showCorrespondentNames;
    }

    public static boolean showContactName() {
        return mShowContactName;
    }

    public static void setShowContactName(boolean showContactName) {
        mShowContactName = showContactName;
    }

    public static boolean changeContactNameColor() {
        return mChangeContactNameColor;
    }

    public static void setChangeContactNameColor(boolean changeContactNameColor) {
        mChangeContactNameColor = changeContactNameColor;
    }

    public static int getContactNameColor() {
        return mContactNameColor;
    }

    public static void setContactNameColor(int contactNameColor) {
        mContactNameColor = contactNameColor;
    }

    public static boolean messageViewFixedWidthFont() {
        return mMessageViewFixedWidthFont;
    }

    public static void setMessageViewFixedWidthFont(boolean fixed) {
        mMessageViewFixedWidthFont = fixed;
    }

    public static boolean messageViewReturnToList() {
        return mMessageViewReturnToList;
    }

    public static void setMessageViewReturnToList(boolean messageViewReturnToList) {
        mMessageViewReturnToList = messageViewReturnToList;
    }

    public static boolean messageViewShowNext() {
        return mMessageViewShowNext;
    }

    public static void setMessageViewShowNext(boolean messageViewShowNext) {
        mMessageViewShowNext = messageViewShowNext;
    }

    public static FontSizes getFontSizes() {
        return fontSizes;
    }

    public static boolean measureAccounts() {
        return mMeasureAccounts;
    }

    public static void setMeasureAccounts(boolean measureAccounts) {
        mMeasureAccounts = measureAccounts;
    }

    public static boolean countSearchMessages() {
        return mCountSearchMessages;
    }

    public static void setCountSearchMessages(boolean countSearchMessages) {
        mCountSearchMessages = countSearchMessages;
    }

    public static boolean isHideSpecialAccounts() {
        return mHideSpecialAccounts;
    }

    public static void setHideSpecialAccounts(boolean hideSpecialAccounts) {
        mHideSpecialAccounts = hideSpecialAccounts;
    }

    public static boolean confirmDelete() {
        return mConfirmDelete;
    }

    public static void setConfirmDelete(final boolean confirm) {
        mConfirmDelete = confirm;
    }

    public static boolean confirmDeleteStarred() {
        return mConfirmDeleteStarred;
    }

    public static void setConfirmDeleteStarred(final boolean confirm) {
        mConfirmDeleteStarred = confirm;
    }

    public static boolean confirmSpam() {
        return mConfirmSpam;
    }

    public static boolean confirmDiscardMessage() {
        return mConfirmDiscardMessage;
    }

    public static void setConfirmSpam(final boolean confirm) {
        mConfirmSpam = confirm;
    }

    public static void setConfirmDiscardMessage(final boolean confirm) {
        mConfirmDiscardMessage = confirm;
    }

    public static boolean confirmDeleteFromNotification() {
        return mConfirmDeleteFromNotification;
    }

    public static void setConfirmDeleteFromNotification(final boolean confirm) {
        mConfirmDeleteFromNotification = confirm;
    }

    public static boolean confirmMarkAllRead() {
        return mConfirmMarkAllRead;
    }

    public static void setConfirmMarkAllRead(final boolean confirm) {
        mConfirmMarkAllRead = confirm;
    }

    public static NotificationHideSubject getNotificationHideSubject() {
        return sNotificationHideSubject;
    }

    public static void setNotificationHideSubject(final NotificationHideSubject mode) {
        sNotificationHideSubject = mode;
    }

    public static NotificationQuickDelete getNotificationQuickDeleteBehaviour() {
        return sNotificationQuickDelete;
    }

    public static void setNotificationQuickDeleteBehaviour(final NotificationQuickDelete mode) {
        sNotificationQuickDelete = mode;
    }

    public static LockScreenNotificationVisibility getLockScreenNotificationVisibility() {
        return sLockScreenNotificationVisibility;
    }

    public static void setLockScreenNotificationVisibility(final LockScreenNotificationVisibility visibility) {
        sLockScreenNotificationVisibility = visibility;
    }

    public static boolean wrapFolderNames() {
        return mWrapFolderNames;
    }

    public static void setWrapFolderNames(final boolean state) {
        mWrapFolderNames = state;
    }

    public static boolean hideUserAgent() {
        return mHideUserAgent;
    }

    public static void setHideUserAgent(final boolean state) {
        mHideUserAgent = state;
    }

    public static boolean hideTimeZone() {
        return mHideTimeZone;
    }

    public static void setHideTimeZone(final boolean state) {
        mHideTimeZone = state;
    }

    public static boolean hideHostnameWhenConnecting() {
        return hideHostnameWhenConnecting;
    }

    public static void setHideHostnameWhenConnecting(final boolean state) {
        hideHostnameWhenConnecting = state;
    }

    public static boolean isOpenPgpProviderConfigured() {
        return !NO_OPENPGP_PROVIDER.equals(sOpenPgpProvider);
    }

    public static String getOpenPgpProvider() {
        return sOpenPgpProvider;
    }

    public static void setOpenPgpProvider(String openPgpProvider) {
        sOpenPgpProvider = openPgpProvider;
    }

    public static boolean getOpenPgpSupportSignOnly() {
        return sOpenPgpSupportSignOnly;
    }

    public static void setOpenPgpSupportSignOnly(boolean supportSignOnly) {
        sOpenPgpSupportSignOnly = supportSignOnly;
    }

    public static String getAttachmentDefaultPath() {
        return mAttachmentDefaultPath;
    }

    public static void setAttachmentDefaultPath(String attachmentDefaultPath) {
        XryptoMail.mAttachmentDefaultPath = attachmentDefaultPath;
    }

    public static synchronized SortType getSortType() {
        return mSortType;
    }

    public static synchronized void setSortType(SortType sortType) {
        mSortType = sortType;
    }

    public static synchronized boolean isSortAscending(SortType sortType) {
        if (mSortAscending.get(sortType) == null) {
            mSortAscending.put(sortType, sortType.isDefaultAscending());
        }
        return Boolean.TRUE.equals(mSortAscending.get(sortType));
    }

    public static synchronized void setSortAscending(SortType sortType, boolean sortAscending) {
        mSortAscending.put(sortType, sortAscending);
    }

    public static synchronized boolean useBackgroundAsUnreadIndicator() {
        return sUseBackgroundAsUnreadIndicator;
    }

    public static synchronized void setUseBackgroundAsUnreadIndicator(boolean enabled) {
        sUseBackgroundAsUnreadIndicator = enabled;
    }

    public static synchronized boolean isThreadedViewEnabled() {
        return sThreadedViewEnabled;
    }

    public static synchronized void setThreadedViewEnabled(boolean enable) {
        sThreadedViewEnabled = enable;
    }

    public static synchronized SplitViewMode getSplitViewMode() {
        return sSplitViewMode;
    }

    public static synchronized void setSplitViewMode(SplitViewMode mode) {
        sSplitViewMode = mode;
    }

    public static boolean showContactPicture() {
        return sShowContactPicture;
    }

    public static void setShowContactPicture(boolean show) {
        sShowContactPicture = show;
    }

    public static boolean isColorizeMissingContactPictures() {
        return sColorizeMissingContactPictures;
    }

    public static void setColorizeMissingContactPictures(boolean enabled) {
        sColorizeMissingContactPictures = enabled;
    }

    public static boolean isMessageViewArchiveActionVisible() {
        return sMessageViewArchiveActionVisible;
    }

    public static void setMessageViewArchiveActionVisible(boolean visible) {
        sMessageViewArchiveActionVisible = visible;
    }

    public static boolean isMessageViewDeleteActionVisible() {
        return sMessageViewDeleteActionVisible;
    }

    public static void setMessageViewDeleteActionVisible(boolean visible) {
        sMessageViewDeleteActionVisible = visible;
    }

    public static boolean isMessageViewMoveActionVisible() {
        return sMessageViewMoveActionVisible;
    }

    public static void setMessageViewMoveActionVisible(boolean visible) {
        sMessageViewMoveActionVisible = visible;
    }

    public static boolean isMessageViewCopyActionVisible() {
        return sMessageViewCopyActionVisible;
    }

    public static void setMessageViewCopyActionVisible(boolean visible) {
        sMessageViewCopyActionVisible = visible;
    }

    public static boolean isMessageViewSpamActionVisible() {
        return sMessageViewSpamActionVisible;
    }

    public static void setMessageViewSpamActionVisible(boolean visible) {
        sMessageViewSpamActionVisible = visible;
    }

    public static int getPgpInlineDialogCounter() {
        return sPgpInlineDialogCounter;
    }

    public static void setPgpInlineDialogCounter(int pgpInlineDialogCounter) {
        XryptoMail.sPgpInlineDialogCounter = pgpInlineDialogCounter;
    }

    public static int getPgpSignOnlyDialogCounter() {
        return sPgpSignOnlyDialogCounter;
    }

    public static void setPgpSignOnlyDialogCounter(int pgpSignOnlyDialogCounter) {
        XryptoMail.sPgpSignOnlyDialogCounter = pgpSignOnlyDialogCounter;
    }

    /**
     * Check if we already know whether all databases are using the current database schema.
     *
     * <p>
     * This method is only used for optimizations. If it returns {@code true} we can be certain that
     * getting a {@link LocalStore} instance won't trigger a schema upgrade.
     * </p>
     *
     * @return {@code true}, if we know that all databases are using the current database schema.
     * {@code false}, otherwise.
     */
    public static synchronized boolean areDatabasesUpToDate() {
        return sDatabasesUpToDate;
    }

    /**
     * Remember that all account databases are using the most recent database schema.
     *
     * @param save Whether or not to write the current database version to the
     * {@code SharedPreferences} {@link #DATABASE_VERSION_CACHE}.
     *
     * @see #areDatabasesUpToDate()
     */
    public static synchronized void setDatabasesUpToDate(boolean save) {
        sDatabasesUpToDate = true;
        if (save) {
            Editor editor = sDatabaseVersionCache.edit();
            editor.putInt(KEY_LAST_ACCOUNT_DATABASE_VERSION, LocalStore.DB_VERSION);
            editor.apply();
        }
    }

    private static void updateLoggingStatus() {
        Timber.uprootAll();
        boolean enableDebugLogging = BuildConfig.DEBUG || DEBUG;
        if (enableDebugLogging) {
            TimberLogImpl.init();
        }
    }

    public static void saveSettingsAsync() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                Preferences prefs = Preferences.getPreferences(mInstance);
                StorageEditor editor = prefs.getStorage().edit();
                save(editor);
                editor.commit();

                return null;
            }
        }.execute();
    }

    /**
     * Returns global application context.
     *
     * @return Returns global application <tt>Context</tt>.
     */
    public static Context getGlobalContext() {
        return mInstance.getApplicationContext();
    }

    /**
     * Returns application <tt>Resources</tt> object.
     *
     * @return application <tt>Resources</tt> object.
     */
    public static Resources getAppResources() {
        return mInstance.getResources();
    }

    /**
     * Returns Android string resource for given <tt>id</tt>.
     *
     * @param id the string identifier.
     *
     * @return Android string resource for given <tt>id</tt>.
     */
    public static String getResString(int id) {
        return getAppResources().getString(id);
    }

    /**
     * Returns Android string resource for given <tt>id</tt> and format arguments that will be used for substitution.
     *
     * @param id the string identifier.
     * @param arg the format arguments that will be used for substitution.
     *
     * @return Android string resource for given <tt>id</tt> and format arguments.
     */
    public static String getResString(int id, Object... arg) {
        return getAppResources().getString(id, arg);
    }

    private static Toast toast = null;

    /**
     * Toast show message in UI thread
     *
     * @param message the string message to display.
     */
    public static void showToastMessage(final String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (toast != null)
                toast.cancel();
            toast = Toast.makeText(mInstance, message, Toast.LENGTH_LONG);
            toast.show();
        });
    }

    public static void showToastMessage(int id, Object... arg) {
        showToastMessage(getAppResources().getString(id, arg));
    }

    /**
     * Retrieves <tt>DownloadManager</tt> instance using application context.
     *
     * @return <tt>DownloadManager</tt> service instance.
     */
    public static DownloadManager getDownloadManager() {
        return (DownloadManager) getGlobalContext().getSystemService(Context.DOWNLOAD_SERVICE);
    }

    // =========== Runtime permission handlers ==========

    /**
     * Check the WRITE_EXTERNAL_STORAGE state; proceed to request for permission if requestPermission == true.
     * Require to support WRITE_EXTERNAL_STORAGE pending aTalk installed API version.
     *
     * @param callBack the requester activity to receive onRequestPermissionsResult()
     * @param requestPermission Proceed to request for the permission if was denied; check only if false
     *
     * @return the current WRITE_EXTERNAL_STORAGE permission state
     */
    public static boolean hasWriteStoragePermission(Context callBack, boolean requestPermission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true;
        }
        return hasPermission((Activity) callBack, requestPermission, PRC_WRITE_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    public static boolean hasPermission(Activity callBack, boolean requestPermission, int requestCode, String permission) {
        // Timber.d(new Exception(),"Callback: %s => %s (%s)", callBack, permission, requestPermission);
        // Do not use getInstance() as mInstances may be empty
        if (ActivityCompat.checkSelfPermission(XryptoMail.getGlobalContext(), permission) != PackageManager.PERMISSION_GRANTED) {
            if (requestPermission && (callBack != null)) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(callBack, permission)) {
                    ActivityCompat.requestPermissions(callBack, new String[]{permission}, requestCode);
                }
                else {
                    showToastMessage(R.string.storage_permission_denied_feedback);
                }
            }
            return false;
        }
        return true;
    }
}
