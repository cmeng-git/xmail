package org.atalk.xryptomail.remotecontrol;


import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;

import org.atalk.xryptomail.BuildConfig;

/**
 * Utillity definitions for Android applications to control the behavior of K-9 Mail.  All such applications must declare the following permission:
 * <uses-permission android:name="org.atalk.xryptomail.permission.REMOTE_CONTROL"/>
 * in their AndroidManifest.xml  In addition, all applications sending remote control messages to K-9 Mail must
 * <p>
 * An application that wishes to act on a particular Account in K-9 needs to fetch the list of configured Accounts by broadcasting an
 * {@link Intent} using CryptoMail_REQUEST_ACCOUNTS as the Action.  The broadcast must be made using the {@link ContextWrapper}
 * sendOrderedBroadcast(Intent intent, String receiverPermission, BroadcastReceiver resultReceiver,
 * Handler scheduler, int initialCode, String initialData, Bundle initialExtras).sendOrderedBroadcast}
 * method in order to receive the list of Account UUIDs and descriptions that K-9 will provide.
 *
 * @author Daniel I. Applebaum
 */
public class XryptoMailRemoteControl {
    /**
     * Permission that every application sending a broadcast to K-9 for Remote Control purposes should send on every broadcast.
     * Prevent other applications from intercepting the broadcasts.
     */
    public final static String CryptoMail_REMOTE_CONTROL_PERMISSION = BuildConfig.APPLICATION_ID + ".permission.REMOTE_CONTROL";
    /**
     * {@link Intent} Action to be sent to K-9 using {@link ContextWrapper.sendOrderedBroadcast} in order to fetch the list of configured Accounts.
     * The responseData will contain two String[] with keys CryptoMail_ACCOUNT_UUIDS and CryptoMail_ACCOUNT_DESCRIPTIONS
     */
    public final static String CryptoMail_REQUEST_ACCOUNTS = BuildConfig.APPLICATION_ID + ".XryptoMailRemoteControl.requestAccounts";
    public final static String CryptoMail_ACCOUNT_UUIDS = BuildConfig.APPLICATION_ID + ".XryptoMailRemoteControl.accountUuids";
    public final static String CryptoMail_ACCOUNT_DESCRIPTIONS = BuildConfig.APPLICATION_ID + ".XryptoMailRemoteControl.accountDescriptions";

    /**
     * The {@link {@link Intent}} Action to set in order to cause K-9 to check mail.  (Not yet implemented)
     */
    //public final static String CryptoMail_CHECK_MAIL = BuildConfig.APPLICATION_ID + ".XryptoMailRemoteControl.checkMail";

    /**
     * The {@link {@link Intent}} Action to set when remotely changing K-9 Mail settings
     */
    public final static String CryptoMail_SET = BuildConfig.APPLICATION_ID + ".XryptoMailRemoteControl.set";
    /**
     * The key of the {@link Intent} Extra to set to hold the UUID of a single Account's settings to change.  Used only if CryptoMail_ALL_ACCOUNTS
     * is absent or false.
     */
    public final static String CryptoMail_ACCOUNT_UUID = BuildConfig.APPLICATION_ID + ".XryptoMailRemoteControl.accountUuid";
    /**
     * The key of the {@link Intent} Extra to set to control if the settings will apply to all Accounts, or to the one
     * specified with CryptoMail_ACCOUNT_UUID
     */
    public final static String CryptoMail_ALL_ACCOUNTS = BuildConfig.APPLICATION_ID + ".XryptoMailRemoteControl.allAccounts";

    public final static String CryptoMail_ENABLED = "true";
    public final static String CryptoMail_DISABLED = "false";

    /*
     * Key for the {@link Intent} Extra for controlling whether notifications will be generated for new unread mail.
     * Acceptable values are CryptoMail_ENABLED and CryptoMail_DISABLED
     */
    public final static String CryptoMail_NOTIFICATION_ENABLED = BuildConfig.APPLICATION_ID + ".XryptoMailRemoteControl.notificationEnabled";
    /*
     * Key for the {@link Intent} Extra for controlling whether K-9 will sound the ringtone for new unread mail.
     * Acceptable values are CryptoMail_ENABLED and CryptoMail_DISABLED
     */
    public final static String CryptoMail_RING_ENABLED = BuildConfig.APPLICATION_ID + ".XryptoMailRemoteControl.ringEnabled";
    /*
     * Key for the {@link Intent} Extra for controlling whether K-9 will activate the vibrator for new unread mail.
     * Acceptable values are CryptoMail_ENABLED and CryptoMail_DISABLED
     */
    public final static String CryptoMail_VIBRATE_ENABLED = BuildConfig.APPLICATION_ID + ".XryptoMailRemoteControl.vibrateEnabled";

    public final static String CryptoMail_FOLDERS_NONE = "NONE";
    public final static String CryptoMail_FOLDERS_ALL = "ALL";
    public final static String CryptoMail_FOLDERS_FIRST_CLASS = "FIRST_CLASS";
    public final static String CryptoMail_FOLDERS_FIRST_AND_SECOND_CLASS = "FIRST_AND_SECOND_CLASS";
    public final static String CryptoMail_FOLDERS_NOT_SECOND_CLASS = "NOT_SECOND_CLASS";
    /**
     * Key for the {@link Intent} Extra to set for controlling which folders to be synchronized with Push.
     * Acceptable values are CryptoMail_FOLDERS_ALL, CryptoMail_FOLDERS_FIRST_CLASS, CryptoMail_FOLDERS_FIRST_AND_SECOND_CLASS,
     * CryptoMail_FOLDERS_NOT_SECOND_CLASS, CryptoMail_FOLDERS_NONE
     */
    public final static String CryptoMail_PUSH_CLASSES = BuildConfig.APPLICATION_ID + ".XryptoMailRemoteControl.pushClasses";
    /**
     * Key for the {@link Intent} Extra to set for controlling which folders to be synchronized with Poll.
     * Acceptable values are CryptoMail_FOLDERS_ALL, CryptoMail_FOLDERS_FIRST_CLASS, CryptoMail_FOLDERS_FIRST_AND_SECOND_CLASS,
     * CryptoMail_FOLDERS_NOT_SECOND_CLASS, CryptoMail_FOLDERS_NONE
     */
    public final static String CryptoMail_POLL_CLASSES = BuildConfig.APPLICATION_ID + ".XryptoMailRemoteControl.pollClasses";

    public final static String[] CryptoMail_POLL_FREQUENCIES = {"-1", "1", "5", "10", "15", "30", "60", "120", "180", "360", "720", "1440"};
    /**
     * Key for the {@link Intent} Extra to set with the desired poll frequency.  The value is a String representing a number of minutes.
     * Acceptable values are available in CryptoMail_POLL_FREQUENCIES
     */
    public final static String CryptoMail_POLL_FREQUENCY = BuildConfig.APPLICATION_ID + ".XryptoMailRemoteControl.pollFrequency";

    /**
     * Key for the {@link Intent} Extra to set for controlling K-9's global "Background sync" setting.
     * Acceptable values are CryptoMail_BACKGROUND_OPERATIONS_ALWAYS, CryptoMail_BACKGROUND_OPERATIONS_NEVER
     * CryptoMail_BACKGROUND_OPERATIONS_WHEN_CHECKED
     */
    public final static String CryptoMail_BACKGROUND_OPERATIONS = BuildConfig.APPLICATION_ID + ".XryptoMailRemoteControl.backgroundOperations";
    public final static String CryptoMail_BACKGROUND_OPERATIONS_WHEN_CHECKED = "WHEN_CHECKED";
    public final static String CryptoMail_BACKGROUND_OPERATIONS_ALWAYS = "ALWAYS";
    public final static String CryptoMail_BACKGROUND_OPERATIONS_NEVER = "NEVER";
    public final static String CryptoMail_BACKGROUND_OPERATIONS_WHEN_CHECKED_AUTO_SYNC = "WHEN_CHECKED_AUTO_SYNC";

    /**
     * Key for the {@link Intent} Extra to set for controlling which display theme K-9 will use.  Acceptable values are
     * CryptoMail_THEME_LIGHT, CryptoMail_THEME_DARK
     */
    public final static String CryptoMail_THEME = BuildConfig.APPLICATION_ID + ".XryptoMailRemoteControl.theme";
    public final static String CryptoMail_THEME_LIGHT = "LIGHT";
    public final static String CryptoMail_THEME_DARK = "DARK";

    protected static String LOG_TAG = "XryptoMailRemoteControl";

    public static void set(Context context, Intent broadcastIntent) {
        broadcastIntent.setAction(XryptoMailRemoteControl.CryptoMail_SET);
        broadcastIntent.setPackage(context.getPackageName());
        context.sendBroadcast(broadcastIntent, XryptoMailRemoteControl.CryptoMail_REMOTE_CONTROL_PERMISSION);
    }

    public static void fetchAccounts(Context context, XryptoMailAccountReceptor receptor) {
        Intent accountFetchIntent = new Intent();
        accountFetchIntent
                .setAction(XryptoMailRemoteControl.CryptoMail_REQUEST_ACCOUNTS)
                .setPackage(context.getPackageName());
        AccountReceiver receiver = new AccountReceiver(receptor);
        context.sendOrderedBroadcast(accountFetchIntent, XryptoMailRemoteControl.CryptoMail_REMOTE_CONTROL_PERMISSION, receiver, null, Activity.RESULT_OK, null, null);
    }
}
