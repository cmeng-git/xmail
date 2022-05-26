/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
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

package org.atalk.xryptomail.notification;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.provider.Settings;
import androidx.annotation.RequiresApi;

import org.atalk.xryptomail.R;

import java.util.Arrays;
import java.util.List;

/**
 * Helper class to manage notification channels, and create notifications.
 *
 * @author Eng Chong Meng
 */
public class NotificationHelper extends ContextWrapper
{
    /**
     * Message badge notifications group.
     */
    public static final String BADGE_GROUP = "badge";

    /**
     * Message badge notifications group.
     */
    public static final String BADGE_ONLY = "badgeOnly";

    /**
     * Message notifications group.
     */
    public static final String EMAIL_GROUP = "email";

    /**
     * Missed call event.
     */
    public static final String ERROR_GROUP = "error";

    /**
     * Default group that will use aTalk icon for notifications
     */
    public static final String SERVICE_GROUP = "service";

    private List<String> notificationIds = Arrays.asList(BADGE_GROUP, EMAIL_GROUP, ERROR_GROUP, SERVICE_GROUP);

    private NotificationManager manager;

    /**
     * Registers notification channels, which can be used later by individual notifications.
     *
     * @param ctx The application context
     */
    public NotificationHelper(Context ctx)
    {
        super(ctx);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            // Delete any unused channel IDs or force to re-init all notification channels
            deleteObsoletedChannelIds(false);

            NotificationChannel nEmail = new NotificationChannel(EMAIL_GROUP,
                    getString(R.string.noti_channel_EMAIL_GROUP), NotificationManager.IMPORTANCE_DEFAULT);
            nEmail.setLightColor(Color.BLUE);
            nEmail.enableLights(true);
            nEmail.enableVibration(true);
            nEmail.setShowBadge(true);
            nEmail.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            getManager().createNotificationChannel(nEmail);

            NotificationChannel nBadge = new NotificationChannel(BADGE_GROUP,
                    getString(R.string.noti_channel_BADGE_GROUP), NotificationManager.IMPORTANCE_LOW);
            nBadge.setShowBadge(true);
            nBadge.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            getManager().createNotificationChannel(nBadge);

//            NotificationChannel nBadgeOnly = new NotificationChannel(BADGE_ONLY,
//                    getString(R.string.noti_channel_BADGE_ONLY), NotificationManager.IMPORTANCE_MIN);
//            nBadgeOnly.setShowBadge(true);
//            nBadgeOnly.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
//            getManager().createNotificationChannel(nBadgeOnly);

            NotificationChannel nError = new NotificationChannel(ERROR_GROUP,
                    getString(R.string.noti_channel_ERROR_GROUP), NotificationManager.IMPORTANCE_LOW);
            nError.setShowBadge(false);
            nError.setLightColor(Color.RED);
            nError.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            getManager().createNotificationChannel(nError);

            NotificationChannel nDefault = new NotificationChannel(SERVICE_GROUP,
                    getString(R.string.noti_channel_SERVICE_GROUP), NotificationManager.IMPORTANCE_LOW);
            // nDefault.setLightColor(Color.WHITE);
            nDefault.setShowBadge(false);
            nDefault.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            getManager().createNotificationChannel(nDefault);
        }
    }

    // Clear any obsoleted notification channels
    @TargetApi(Build.VERSION_CODES.O)
    private void deleteObsoletedChannelIds(boolean force){
        List<NotificationChannel> channelGroups = getManager().getNotificationChannels();
        for (NotificationChannel nc: channelGroups) {
            if (force || !notificationIds.contains(nc.getId())) {
                getManager().deleteNotificationChannel(nc.getId());
            }
        }
    }

    /*
     * Send a notification.
     *
     * @param id The ID of the notification
     * @param notification The notification object
     */
    public void notify(int id, Notification.Builder notification)
    {
        getManager().notify(id, notification.build());
    }

    /**
     * Get the notification manager.
     *
     * Utility method as this helper works with it a lot.
     *
     * @return The system service NotificationManager
     */
    private NotificationManager getManager()
    {
        if (manager == null) {
            manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        }
        return manager;
    }

    /**
     * Send Intent to load system Notification Settings for this app.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public void goToNotificationSettings()
    {
        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        startActivity(i);
    }

    /**
     * Send intent to load system Notification Settings UI for a particular channel.
     *
     * @param channel Name of channel to configure
     */
    @TargetApi(Build.VERSION_CODES.O)
    public void goToNotificationSettings(String channel)
    {
        Intent i = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
        i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        i.putExtra(Settings.EXTRA_CHANNEL_ID, channel);
        startActivity(i);
    }
}
