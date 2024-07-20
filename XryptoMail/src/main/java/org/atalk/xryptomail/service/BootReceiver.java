
package org.atalk.xryptomail.service;

import static org.atalk.xryptomail.notification.NotificationHelper.getPendingIntentFlag;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.text.TextUtils;

import java.util.Date;
import java.util.Objects;

import org.atalk.xryptomail.XryptoMail;
import org.atalk.xryptomail.helper.XryptoMailAlarmManager;

import timber.log.Timber;

public class BootReceiver extends CoreReceiver
{
    public static final String FIRE_INTENT = "org.atalk.xryptomail.service.BroadcastReceiver.fireIntent";
    public static final String SCHEDULE_INTENT = "org.atalk.xryptomail.service.BroadcastReceiver.scheduleIntent";
    public static final String CANCEL_INTENT = "org.atalk.xryptomail.service.BroadcastReceiver.cancelIntent";

    public static final String ALARMED_INTENT = "org.atalk.xryptomail.service.BroadcastReceiver.pendingIntent";
    public static final String AT_TIME = "org.atalk.xryptomail.service.BroadcastReceiver.atTime";

    public static final String SYNC_CONNECTION_SETTING_CHANGED = "com.android.sync.SYNC_CONN_STATUS_CHANGED";

    @Override
    public Integer receive(Context context, Intent intent, Integer tmpWakeLockId)
    {
        Timber.i("BootReceiver.onReceive %s", intent);
        XryptoMailAlarmManager alarmMgr = XryptoMailAlarmManager.getAlarmManager(context);
        Intent alarmedIntent = intent.getParcelableExtra(ALARMED_INTENT);
        PendingIntent pi;

        final String action = intent.getAction();
        switch (Objects.requireNonNull(action)) {
            case Intent.ACTION_BOOT_COMPLETED:
                //XryptoMail.setServicesEnabled(context, tmpWakeLockId);
                //tmpWakeLockId = null;
                break;
            case Intent.ACTION_DEVICE_STORAGE_LOW:
                MailService.actionCancel(context);
                tmpWakeLockId = null;
                break;
            case Intent.ACTION_DEVICE_STORAGE_OK:
                MailService.actionReset(context);
                tmpWakeLockId = null;
                break;
            case ConnectivityManager.CONNECTIVITY_ACTION:
                MailService.connectivityChange(context);
                tmpWakeLockId = null;
                break;
            case SYNC_CONNECTION_SETTING_CHANGED:
                XryptoMail.BACKGROUND_OPS bOps = XryptoMail.getBackgroundOps();
                if (bOps == XryptoMail.BACKGROUND_OPS.WHEN_CHECKED_AUTO_SYNC) {
                    MailService.actionReset(context);
                    tmpWakeLockId = null;
                }
                break;
            case FIRE_INTENT:
                String alarmedAction = alarmedIntent.getAction();
                Timber.i("BootReceiver Got alarm to fire alarmedIntent %s", alarmedAction);
                alarmedIntent.putExtra(WAKE_LOCK_ID, tmpWakeLockId);
                tmpWakeLockId = null;

                if (!TextUtils.isEmpty(alarmedAction)) {
                    if (alarmedAction.contains(MailService.MAIL_SERVICE_SIGNATURE))
                        MailService.startMailServiceOn(context, alarmedAction);
                    else if (alarmedAction.contains(SleepService.SLEEP_SERVICE_SIGNATURE))
                        SleepService.startSleepServiceOn(context, alarmedAction);
                    else {
                        // startService can crash apk on android-O
                        // context.startService(alarmedIntent);
                        Timber.w("Received unhandled FIRE_INTENT: %s", intent);
                    }
                }
                break;
            case SCHEDULE_INTENT:
                long atTime = intent.getLongExtra(AT_TIME, -1);
                Timber.i("BootReceiver Scheduling intent %s for %tc", alarmedIntent, new Date(atTime));
                pi = buildPendingIntent(context, intent);
                alarmMgr.set(AlarmManager.RTC_WAKEUP, atTime, pi);
                break;
            case CANCEL_INTENT:
                Timber.i("BootReceiver Canceling alarmedIntent %s", alarmedIntent);
                pi = buildPendingIntent(context, intent);
                alarmMgr.cancel(pi);
                break;
            default:
                break;
        }
        return tmpWakeLockId;
    }

    private PendingIntent buildPendingIntent(Context context, Intent intent)
    {
        Intent alarmedIntent = intent.getParcelableExtra(ALARMED_INTENT);
        String alarmedAction = alarmedIntent.getAction();

        Intent i = new Intent(context, BootReceiver.class);
        i.setAction(FIRE_INTENT);
        i.putExtra(ALARMED_INTENT, alarmedIntent);
        Uri uri = Uri.parse("action://" + alarmedAction);
        i.setData(uri);
        return PendingIntent.getBroadcast(context, 0, i, getPendingIntentFlag(false, true));
    }

    public static void scheduleIntent(Context context, long atTime, Intent alarmedIntent)
    {
        Timber.i("BootReceiver Got request to schedule alarmedIntent %s", alarmedIntent.getAction());

        Intent i = new Intent();
        i.setClass(context, BootReceiver.class);
        i.setAction(SCHEDULE_INTENT);
        i.putExtra(ALARMED_INTENT, alarmedIntent);
        i.putExtra(AT_TIME, atTime);
        i.setPackage(context.getPackageName());
        context.sendBroadcast(i);
    }

    public static void cancelIntent(Context context, Intent alarmedIntent)
    {
        Timber.i("BootReceiver Got request to cancel alarmedIntent %s", alarmedIntent.getAction());

        Intent i = new Intent();
        i.setClass(context, BootReceiver.class);
        i.setAction(CANCEL_INTENT);
        i.putExtra(ALARMED_INTENT, alarmedIntent);
        i.setPackage(context.getPackageName());
        context.sendBroadcast(i);
    }

    /**
     * Cancel any scheduled alarm.
     *
     * @param context
     */
    public static void purgeSchedule(final Context context)
    {
        final XryptoMailAlarmManager alarmService = XryptoMailAlarmManager.getAlarmManager(context);
        alarmService.cancel(PendingIntent.getBroadcast(context, 0, new Intent()
        {
            @Override
            public boolean filterEquals(final Intent other)
            {
                // we want to match all intents
                return true;
            }
        }, getPendingIntentFlag(false, true)));
   }
}
