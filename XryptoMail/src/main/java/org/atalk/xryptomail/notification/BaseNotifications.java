package org.atalk.xryptomail.notification;

import android.app.PendingIntent;
import android.content.Context;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.BigTextStyle;
import android.support.v4.app.NotificationCompat.Builder;

import org.atalk.xryptomail.Account;
import org.atalk.xryptomail.XryptoMail;
import org.atalk.xryptomail.XryptoMail.NotificationQuickDelete;
import org.atalk.xryptomail.R;

abstract class BaseNotifications {
    protected final Context context;
    protected final NotificationController controller;
    protected final NotificationActionCreator actionCreator;


    protected BaseNotifications(NotificationController controller, NotificationActionCreator actionCreator) {
        this.context = controller.getContext();
        this.controller = controller;
        this.actionCreator = actionCreator;
    }

    protected NotificationCompat.Builder createBigTextStyleNotification(Account account, NotificationHolder holder,
            int notificationId) {
        String accountName = controller.getAccountName(account);
        NotificationContent content = holder.content;
        String groupKey = NotificationGroupKeys.getGroupKey(account);

        NotificationCompat.Builder builder = createAndInitializeNotificationBuilder(account)
                .setTicker(content.summary)
                .setGroup(groupKey)
                .setContentTitle(content.sender)
                .setContentText(content.subject)
                .setSubText(accountName);

        NotificationCompat.BigTextStyle style = createBigTextStyle(builder);
        style.bigText(content.preview);

        builder.setStyle(style);

        PendingIntent contentIntent = actionCreator.createViewMessagePendingIntent(
                content.messageReference, notificationId);
        builder.setContentIntent(contentIntent);

        return builder;
    }

    protected NotificationCompat.Builder createAndInitializeNotificationBuilder(Account account) {
        return controller.createNotificationBuilder(NotificationHelper.EMAIL_GROUP)
                .setSmallIcon(getNewMailNotificationIcon())
                .setColor(account.getChipColor())
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY) // fix double alert in android-O
                .setCategory(NotificationCompat.CATEGORY_EMAIL);
    }

    protected boolean isDeleteActionEnabled() {
        NotificationQuickDelete deleteOption = XryptoMail.getNotificationQuickDeleteBehaviour();
        return deleteOption == NotificationQuickDelete.ALWAYS || deleteOption == NotificationQuickDelete.FOR_SINGLE_MSG;
    }

    protected BigTextStyle createBigTextStyle(Builder builder) {
        return new BigTextStyle(builder);
    }

    private int getNewMailNotificationIcon() {
        return R.drawable.notification_icon_new_mail;
    }
}
