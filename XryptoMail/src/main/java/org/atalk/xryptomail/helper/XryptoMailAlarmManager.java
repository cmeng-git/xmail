package org.atalk.xryptomail.helper;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;

import androidx.annotation.VisibleForTesting;

import org.atalk.xryptomail.power.DozeChecker;

public class XryptoMailAlarmManager {
    private final AlarmManager alarmManager;
    private final DozeChecker dozeChecker;

    public static XryptoMailAlarmManager getAlarmManager(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        DozeChecker dozeChecker = new DozeChecker(context);
        return new XryptoMailAlarmManager(alarmManager, dozeChecker);
    }

    @VisibleForTesting
    private XryptoMailAlarmManager(AlarmManager alarmManager, DozeChecker dozeChecker) {
        this.alarmManager = alarmManager;
        this.dozeChecker = dozeChecker;
    }

    public void set(int type, long triggerAtMillis, PendingIntent operation) {
        if (dozeChecker.isDeviceIdleModeSupported() && dozeChecker.isAppWhitelisted()) {
            setAndAllowWhileIdle(type, triggerAtMillis, operation);
        } else {
            alarmManager.set(type, triggerAtMillis, operation);
        }
    }

    private void setAndAllowWhileIdle(int type, long triggerAtMillis, PendingIntent operation) {
        alarmManager.setAndAllowWhileIdle(type, triggerAtMillis, operation);
    }

    public void cancel(PendingIntent operation) {
        alarmManager.cancel(operation);
    }
}

