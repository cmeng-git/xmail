package org.atalk.xryptomail.notification;


import android.app.PendingIntent;
import android.content.Context;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.atalk.xryptomail.Account;
import org.atalk.xryptomail.R;
import org.atalk.xryptomail.helper.ExceptionHelper;

import static org.atalk.xryptomail.notification.NotificationController.NOTIFICATION_LED_BLINK_FAST;
import static org.atalk.xryptomail.notification.NotificationController.NOTIFICATION_LED_FAILURE_COLOR;

class SendFailedNotifications {
    private final NotificationController controller;
    private final NotificationActionCreator actionBuilder;


    public SendFailedNotifications(NotificationController controller, NotificationActionCreator actionBuilder) {
        this.controller = controller;
        this.actionBuilder = actionBuilder;
    }

    public void showSendFailedNotification(Account account, Exception exception) {
        Context context = controller.getContext();
        String title = context.getString(R.string.send_failure_subject);
        String text = ExceptionHelper.getRootCauseMessage(exception);

        int notificationId = NotificationIds.getSendFailedNotificationId(account);
        PendingIntent folderListPendingIntent = actionBuilder.createViewFolderListPendingIntent(
                account, notificationId);

        NotificationCompat.Builder builder = controller.createNotificationBuilder(NotificationHelper.ERROR_GROUP)
                .setSmallIcon(getSendFailedNotificationIcon())
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(true)
                .setTicker(title)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(folderListPendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_ERROR);

        controller.configureNotification(builder, null, null, NOTIFICATION_LED_FAILURE_COLOR,
                NOTIFICATION_LED_BLINK_FAST, true);

        getNotificationManager().notify(notificationId, builder.build());
    }

    public void clearSendFailedNotification(Account account) {
        int notificationId = NotificationIds.getSendFailedNotificationId(account);
        getNotificationManager().cancel(notificationId);
    }

    private int getSendFailedNotificationIcon() {
        //TODO: Use a different icon for send failure notifications
        return R.drawable.notification_icon_new_mail;
    }

    private NotificationManagerCompat getNotificationManager() {
        return controller.getNotificationManager();
    }
}
