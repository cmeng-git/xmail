package org.atalk.xryptomail.service;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;
import android.widget.Toast;

import org.atalk.xryptomail.*;
import org.atalk.xryptomail.Account.FolderMode;
import org.atalk.xryptomail.XryptoMail.BACKGROUND_OPS;
import org.atalk.xryptomail.preferences.Storage;
import org.atalk.xryptomail.preferences.StorageEditor;
import org.atalk.xryptomail.remotecontrol.XryptoMailRemoteControl;

import java.util.List;

import timber.log.Timber;

import static org.atalk.xryptomail.remotecontrol.XryptoMailRemoteControl.CryptoMail_ACCOUNT_UUID;
import static org.atalk.xryptomail.remotecontrol.XryptoMailRemoteControl.CryptoMail_ALL_ACCOUNTS;
import static org.atalk.xryptomail.remotecontrol.XryptoMailRemoteControl.CryptoMail_BACKGROUND_OPERATIONS;
import static org.atalk.xryptomail.remotecontrol.XryptoMailRemoteControl.CryptoMail_NOTIFICATION_ENABLED;
import static org.atalk.xryptomail.remotecontrol.XryptoMailRemoteControl.CryptoMail_POLL_CLASSES;
import static org.atalk.xryptomail.remotecontrol.XryptoMailRemoteControl.CryptoMail_POLL_FREQUENCY;
import static org.atalk.xryptomail.remotecontrol.XryptoMailRemoteControl.CryptoMail_PUSH_CLASSES;
import static org.atalk.xryptomail.remotecontrol.XryptoMailRemoteControl.CryptoMail_RING_ENABLED;
import static org.atalk.xryptomail.remotecontrol.XryptoMailRemoteControl.CryptoMail_THEME;
import static org.atalk.xryptomail.remotecontrol.XryptoMailRemoteControl.CryptoMail_VIBRATE_ENABLED;

public class RemoteControlService extends JobIntentService
{
    private final static String RESCHEDULE_ACTION
            = "org.atalk.xryptomail.service.RemoteControlService.RESCHEDULE_ACTION";
    private final static String PUSH_RESTART_ACTION
            = "org.atalk.xryptomail.service.RemoteControlService.PUSH_RESTART_ACTION";
    private final static String SET_ACTION
            = "org.atalk.xryptomail.service.RemoteControlService.SET_ACTION";

    public static void set(Context context, Intent i)
    {
        //  Intent i = new Intent();
        i.setClass(context, RemoteControlService.class);
        i.setAction(RemoteControlService.SET_ACTION);
        enqueueWork(context, i);
    }

    /**
     * Unique job ID for this service.
     */
    static final int JOB_ID = 1400;

    public static void enqueueWork(Context context, Intent work)
    {
        enqueueWork(context, RemoteControlService.class, JOB_ID, work);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent)
    {
        Timber.i("RemoteControlService started");
        final Preferences preferences = Preferences.getPreferences(this);

        if (RESCHEDULE_ACTION.equals(intent.getAction())) {
            Timber.i("RemoteControlService requesting MailService poll reschedule");
            MailService.actionReschedulePoll(this);
        }
        if (PUSH_RESTART_ACTION.equals(intent.getAction())) {
            Timber.i("RemoteControlService requesting MailService push restart");
            MailService.actionRestartPushers(this);
        }
        else if (RemoteControlService.SET_ACTION.equals(intent.getAction())) {
            Timber.i("RemoteControlService got request to change settings");
            try {
                boolean needsReschedule = false;
                boolean needsPushRestart = false;
                String uuid = intent.getStringExtra(CryptoMail_ACCOUNT_UUID);
                boolean allAccounts = intent.getBooleanExtra(CryptoMail_ALL_ACCOUNTS, false);

                if (allAccounts) {
                    Timber.i("RemoteControlService changing settings for all accounts");
                }
                else {
                    Timber.i("RemoteControlService changing settings for account with UUID %s", uuid);
                }

                List<Account> accounts = preferences.getAccounts();
                for (Account account : accounts) {
                    //warning: account may not be isAvailable()
                    if (allAccounts || account.getUuid().equals(uuid)) {
                        Timber.i("RemoteControlService changing settings fo account %s", account.getDescription());

                        String notificationEnabled = intent.getStringExtra(CryptoMail_NOTIFICATION_ENABLED);
                        String ringEnabled = intent.getStringExtra(CryptoMail_RING_ENABLED);
                        String vibrateEnabled = intent.getStringExtra(CryptoMail_VIBRATE_ENABLED);
                        String pushClasses = intent.getStringExtra(CryptoMail_PUSH_CLASSES);
                        String pollClasses = intent.getStringExtra(CryptoMail_POLL_CLASSES);
                        String pollFrequency = intent.getStringExtra(CryptoMail_POLL_FREQUENCY);

                        if (notificationEnabled != null) {
                            account.setNotifyNewMail(Boolean.parseBoolean(notificationEnabled));
                        }
                        if (ringEnabled != null) {
                            account.getNotificationSetting().setRingEnabled(Boolean.parseBoolean(ringEnabled));
                        }
                        if (vibrateEnabled != null) {
                            account.getNotificationSetting().setVibrate(Boolean.parseBoolean(vibrateEnabled));
                        }
                        if (pushClasses != null) {
                            needsPushRestart |= account.setFolderPushMode(FolderMode.valueOf(pushClasses));
                        }
                        if (pollClasses != null) {
                            needsReschedule |= account.setFolderSyncMode(FolderMode.valueOf(pollClasses));
                        }
                        if (pollFrequency != null) {
                            String[] allowedFrequencies = getResources().getStringArray(
                                    R.array.account_settings_check_frequency_values);
                            for (String allowedFrequency : allowedFrequencies) {
                                if (allowedFrequency.equals(pollFrequency)) {
                                    int newInterval = Integer.parseInt(allowedFrequency);
                                    needsReschedule |= account.setAutomaticCheckIntervalMinutes(newInterval);
                                }
                            }
                        }
                        account.save(Preferences.getPreferences(RemoteControlService.this));
                    }
                }
                Timber.i("RemoteControlService changing global settings");

                String backgroundOps = intent.getStringExtra(CryptoMail_BACKGROUND_OPERATIONS);
                if (XryptoMailRemoteControl.CryptoMail_BACKGROUND_OPERATIONS_ALWAYS.equals(backgroundOps)
                        || XryptoMailRemoteControl.CryptoMail_BACKGROUND_OPERATIONS_NEVER.equals(backgroundOps)
                        || XryptoMailRemoteControl.CryptoMail_BACKGROUND_OPERATIONS_WHEN_CHECKED_AUTO_SYNC.equals(backgroundOps)) {
                    BACKGROUND_OPS newBackgroundOps = BACKGROUND_OPS.valueOf(backgroundOps);
                    boolean needsReset = XryptoMail.setBackgroundOps(newBackgroundOps);
                    needsPushRestart |= needsReset;
                    needsReschedule |= needsReset;
                }

                String theme = intent.getStringExtra(CryptoMail_THEME);
                if (theme != null) {
                    XryptoMail.setXMTheme(XryptoMailRemoteControl.CryptoMail_THEME_DARK.equals(theme)
                            ? XryptoMail.Theme.DARK : XryptoMail.Theme.LIGHT);
                }
                Storage storage = preferences.getStorage();
                StorageEditor editor = storage.edit();
                XryptoMail.save(editor);
                editor.commit();
                if (needsReschedule) {
                    Intent i = new Intent(RemoteControlService.this, RemoteControlService.class);
                    i.setAction(RESCHEDULE_ACTION);
                    long nextTime = System.currentTimeMillis() + 10000;
                    BootReceiver.scheduleIntent(RemoteControlService.this, nextTime, i);
                }
                if (needsPushRestart) {
                    Intent i = new Intent(RemoteControlService.this, RemoteControlService.class);
                    i.setAction(PUSH_RESTART_ACTION);
                    long nextTime = System.currentTimeMillis() + 10000;
                    BootReceiver.scheduleIntent(RemoteControlService.this, nextTime, i);
                }
            } catch (Exception e) {
                Timber.e(e, "Could not handle K9_SET");
                Toast toast = Toast.makeText(RemoteControlService.this, e.getMessage(), Toast.LENGTH_LONG);
                toast.show();
            }
        }
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        Timber.v("***** RemoteControlService *****: onCreate");
    }

    @Override
    public void onDestroy()
    {
        Timber.v("***** RemoteControlService *****: onDestroy()");
        super.onDestroy();
    }
}
