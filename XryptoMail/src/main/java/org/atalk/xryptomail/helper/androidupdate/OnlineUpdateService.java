/*
 * XryptoMail, android mail client
 * Copyright 2011-2022 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.atalk.xryptomail.helper.androidupdate;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import org.atalk.xryptomail.R;
import org.atalk.xryptomail.notification.NotificationHelper;

import java.util.Calendar;

/**
 * Online Update Service started on first XryptoMail launched. It is set to check for an update every 24hours
 *
 * @author Eng Chong Meng
 */
public class OnlineUpdateService extends IntentService {
    public static final String ACTION_AUTO_UPDATE_APP = "org.atalk.android.ACTION_AUTO_UPDATE_APP";
    public static final String ACTION_AUTO_UPDATE_START = "org.atalk.android.ACTION_AUTO_UPDATE_START";
    public static final String ACTION_AUTO_UPDATE_STOP = "org.atalk.android.ACTION_AUTO_UPDATE_STOP";

    private static final String ACTION_UPDATE_AVAILABLE = "org.atalk.android.ACTION_UPDATE_AVAILABLE";
    private static final String ONLINE_UPDATE_SERVICE = "OnlineUpdateService";
    private static final String UPDATE_AVAIL_TAG = "XryptoMail Update Available";

    // in unit of seconds
    public static int CHECK_INTERVAL_ON_LAUNCH = 30;
    public static int CHECK_NEW_VERSION_INTERVAL = 24 * 60 * 60;
    private static final int UPDATE_AVAIL_NOTIFY_ID = 1;

    private NotificationManager mNotificationMgr;

    public OnlineUpdateService() {
        super(ONLINE_UPDATE_SERVICE);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mNotificationMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case ACTION_AUTO_UPDATE_APP:
                        checkAppUpdate();
                        break;
                    case ACTION_UPDATE_AVAILABLE:
                        UpdateService.getInstance().checkForUpdates(true);
                        break;
                    case ACTION_AUTO_UPDATE_START:
                        setNextAlarm(CHECK_INTERVAL_ON_LAUNCH);
                        break;
                    case ACTION_AUTO_UPDATE_STOP:
                        stopAlarm();
                        break;
                }
            }
        }
    }

    private void checkAppUpdate() {
        UpdateService updateService = UpdateService.getInstance();
        boolean isLatest = updateService.isLatestVersion();

        if (!isLatest) {
            NotificationCompat.Builder nBuilder;
            nBuilder = new NotificationCompat.Builder(this, NotificationHelper.SERVICE_GROUP);

            String msgString = getString(R.string.updatechecker_NEW_UPDATE_DIALOG_MESSAGE, updateService.getLatestVersion());
            nBuilder.setSmallIcon(R.drawable.ic_icon);
            nBuilder.setWhen(System.currentTimeMillis());
            nBuilder.setAutoCancel(true);
            nBuilder.setTicker(msgString);
            nBuilder.setContentTitle(getString(R.string.app_name));
            nBuilder.setContentText(msgString);

            Intent intent = new Intent(this.getApplicationContext(), OnlineUpdateService.class);
            intent.setAction(ACTION_UPDATE_AVAILABLE);
            PendingIntent pending = PendingIntent.getService(this, 0, intent,
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT
                            : PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            nBuilder.setContentIntent(pending);
            mNotificationMgr.notify(UPDATE_AVAIL_TAG, UPDATE_AVAIL_NOTIFY_ID, nBuilder.build());
        }
        setNextAlarm(CHECK_NEW_VERSION_INTERVAL);
    }

    private void setNextAlarm(int nextAlarmTime) {
        AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this.getApplicationContext(), OnlineUpdateService.class);
        intent.setAction(ACTION_AUTO_UPDATE_APP);
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent,
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT
                        : PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(System.currentTimeMillis());
        cal.add(Calendar.SECOND, nextAlarmTime);
        alarmManager.cancel(pendingIntent);
        alarmManager.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pendingIntent);
    }

    private void stopAlarm() {
        AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this.getApplicationContext(), OnlineUpdateService.class);
        intent.setAction(ACTION_AUTO_UPDATE_APP);
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent,
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT
                        : PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        alarmManager.cancel(pendingIntent);
    }
}