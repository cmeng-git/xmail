package org.atalk.xryptomail.notification;

import android.app.Notification;
import android.content.Context;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationCompat.Builder;
import android.text.TextUtils;

import org.atalk.xryptomail.Account;
import org.atalk.xryptomail.XryptoMail;
import org.atalk.xryptomail.R;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

class LockScreenNotification {
    static final int MAX_NUMBER_OF_SENDERS_IN_LOCK_SCREEN_NOTIFICATION = 5;


    private final Context context;
    private final NotificationController controller;

    LockScreenNotification(NotificationController controller) {
        context = controller.getContext();
        this.controller = controller;
    }

    public static LockScreenNotification newInstance(NotificationController controller) {
        return new LockScreenNotification(controller);
    }

    public void configureLockScreenNotification(Builder builder, NotificationData notificationData) {
        switch (XryptoMail.getLockScreenNotificationVisibility()) {
            case NOTHING: {
                builder.setVisibility(NotificationCompat.VISIBILITY_SECRET);
                break;
            }
            case APP_NAME: {
                builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE);
                break;
            }
            case EVERYTHING: {
                builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
                break;
            }
            case SENDERS: {
                Notification publicNotification = createPublicNotificationWithSenderList(notificationData);
                builder.setPublicVersion(publicNotification);
                break;
            }
            case MESSAGE_COUNT: {
                Notification publicNotification = createPublicNotificationWithNewMessagesCount(notificationData);
                builder.setPublicVersion(publicNotification);
                break;
            }
        }
    }

    private Notification createPublicNotificationWithSenderList(NotificationData notificationData) {
        Builder builder = createPublicNotification(notificationData);
        int newMessages = notificationData.getNewMessagesCount();
        if (newMessages == 1) {
            NotificationHolder holder = notificationData.getHolderForLatestNotification();
            builder.setContentText(holder.content.sender);
        } else {
            List<NotificationContent> contents = notificationData.getContentForSummaryNotification();
            String senderList = createCommaSeparatedListOfSenders(contents);
            builder.setContentText(senderList);
        }
        return builder.build();
    }

    private Notification createPublicNotificationWithNewMessagesCount(NotificationData notificationData) {
        Builder builder = createPublicNotification(notificationData);
        Account account = notificationData.getAccount();
        String accountName = controller.getAccountName(account);
        builder.setContentText(accountName);

        return builder.build();
    }

    private Builder createPublicNotification(NotificationData notificationData) {
        Account account = notificationData.getAccount();
        int newMessages = notificationData.getNewMessagesCount();
        int unreadCount = notificationData.getUnreadMessageCount();
        String title = context.getResources().getQuantityString(R.plurals.notification_new_messages_title,
                newMessages, newMessages);

        return controller.createNotificationBuilder(NotificationHelper.EMAIL_GROUP)
                .setSmallIcon(R.drawable.notification_icon_new_mail)
                .setColor(account.getChipColor())
                .setNumber(unreadCount)
                .setContentTitle(title)
                .setCategory(NotificationCompat.CATEGORY_EMAIL);
    }


    String createCommaSeparatedListOfSenders(List<NotificationContent> contents) {
        // Use a LinkedHashSet so that we preserve ordering (newest to oldest), but still remove duplicates
        Set<CharSequence> senders = new LinkedHashSet<>(MAX_NUMBER_OF_SENDERS_IN_LOCK_SCREEN_NOTIFICATION);
        for (NotificationContent content : contents) {
            senders.add(content.sender);
            if (senders.size() == MAX_NUMBER_OF_SENDERS_IN_LOCK_SCREEN_NOTIFICATION) {
                break;
            }
        }
        return TextUtils.join(", ", senders);
    }
}
