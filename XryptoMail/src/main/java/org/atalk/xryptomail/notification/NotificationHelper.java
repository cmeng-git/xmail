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
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.RequiresApi;

import org.atalk.xryptomail.R;

/**
 * Helper class to manage notification channels, and create notifications.
 *
 * @author Eng Chong Meng
 */
public class NotificationHelper extends ContextWrapper
{
    /**
     * Default group that will use aTalk icon for notifications
     */
    public static final String SERVICE_GROUP = "service";

    /**
     * Message notifications group.
     */
    public static final String EMAIL_GROUP = "email";

    /**
     * Missed call event.
     */
    public static final String ERROR_GROUP = "error";

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
            NotificationChannel nEmail = new NotificationChannel(EMAIL_GROUP,
                    getString(R.string.noti_channel_EMAIL_GROUP), NotificationManager.IMPORTANCE_DEFAULT);
            // nMessage.setLightColor(Color.BLUE);
            nEmail.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            getManager().createNotificationChannel(nEmail);

            NotificationChannel nError = new NotificationChannel(ERROR_GROUP,
                    getString(R.string.noti_channel_ERROR_GROUP), NotificationManager.IMPORTANCE_LOW);
            nError.setLightColor(Color.RED);
            nError.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            getManager().createNotificationChannel(nError);

            NotificationChannel nDefault = new NotificationChannel(SERVICE_GROUP,
                    getString(R.string.noti_channel_SERVICE_GROUP), NotificationManager.IMPORTANCE_LOW);
            // nDefault.setLightColor(Color.WHITE);
            nDefault.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            getManager().createNotificationChannel(nDefault);
        }
    }

    /*
     * Send a notification.
     *
     * @param id The ID of the notification
     * @param notification The notification object
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
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
