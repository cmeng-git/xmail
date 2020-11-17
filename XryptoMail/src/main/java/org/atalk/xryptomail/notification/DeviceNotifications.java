package org.atalk.xryptomail.notification;

import android.app.*;
import android.content.Context;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationCompat.*;

import org.atalk.xryptomail.*;
import org.atalk.xryptomail.XryptoMail.NotificationHideSubject;
import org.atalk.xryptomail.XryptoMail.NotificationQuickDelete;
import org.atalk.xryptomail.activity.MessageReference;

import java.util.ArrayList;
import java.util.List;

import static org.atalk.xryptomail.notification.NotificationController.NOTIFICATION_LED_BLINK_SLOW;
import static org.atalk.xryptomail.notification.NotificationController.platformSupportsExtendedNotifications;

class DeviceNotifications extends BaseNotifications
{
    private final WearNotifications wearNotifications;
    private final LockScreenNotification lockScreenNotification;

    DeviceNotifications(NotificationController controller, NotificationActionCreator actionCreator,
            LockScreenNotification lockScreenNotification, WearNotifications wearNotifications)
    {
        super(controller, actionCreator);
        this.wearNotifications = wearNotifications;
        this.lockScreenNotification = lockScreenNotification;
    }

    public static DeviceNotifications newInstance(NotificationController controller,
            NotificationActionCreator actionCreator, WearNotifications wearNotifications)
    {
        LockScreenNotification lockScreenNotification = LockScreenNotification.newInstance(controller);
        return new DeviceNotifications(controller, actionCreator, lockScreenNotification, wearNotifications);
    }

    public Notification buildSummaryNotification(Account account, NotificationData notificationData, boolean silent)
    {
        int unreadMessageCount = notificationData.getUnreadMessageCount();

        NotificationCompat.Builder builder;
        // Build custom badge number if not natively supported by android OS
        if (isPrivacyModeActive() || !platformSupportsExtendedNotifications()) {
            builder = createSimpleSummaryNotification(account, unreadMessageCount, NotificationHelper.EMAIL_GROUP);
        }
        else if (notificationData.isSingleMessageNotification()) {
            NotificationHolder holder = notificationData.getHolderForLatestNotification();
            builder = createBigTextStyleSummaryNotification(account, holder);
        }
        else {
            builder = createInboxStyleSummaryNotification(account, notificationData);
        }

        // Update launcher badge number with total unread messages for devices < android.o
        int totalCount = controller.updateBadgeNumber(account, unreadMessageCount, false);
        builder.setNumber(totalCount);

        if (notificationData.containsStarredMessages()) {
            builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        }

        int notificationId = NotificationIds.getNewMailSummaryNotificationId(account);
        PendingIntent deletePendingIntent = actionCreator.createDismissAllMessagesPendingIntent(account, notificationId);
        builder.setDeleteIntent(deletePendingIntent);

        lockScreenNotification.configureLockScreenNotification(builder, notificationData);

        boolean ringAndVibrate = false;
        if (!silent && !account.isRingNotified()) {
            account.setRingNotified(true);
            ringAndVibrate = true;
        }

        NotificationSetting notificationSetting = account.getNotificationSetting();
        controller.configureNotification(
                builder,
                (notificationSetting.isRingEnabled()) ? notificationSetting.getRingtone() : null,
                (notificationSetting.isVibrateEnabled()) ? notificationSetting.getVibration() : null,
                (notificationSetting.isLedEnabled()) ? notificationSetting.getLedColor() : null,
                NOTIFICATION_LED_BLINK_SLOW,
                ringAndVibrate);

        return builder.build();
    }

    protected NotificationCompat.Builder createSimpleSummaryNotification(Account account, int unreadMessageCount,
            String channelId)
    {
        String accountName = controller.getAccountName(account);
        CharSequence newMailText = context.getString(R.string.notification_new_title);
        int notificationId = NotificationIds.getNewMailSummaryNotificationId(account);
        // Timber.d("Notification Id: %s(%s): %s", controller.getAccountName(account), unreadMessageCount, notificationId);

        String unreadMessageCountText = context.getString(R.string.notification_new_one_account_fmt,
                unreadMessageCount, accountName);
        PendingIntent contentIntent = actionCreator.createAccountInBoxPendingIntent(account, notificationId);

        return createAndInitializeNotificationBuilder(account, channelId)
                .setTicker(newMailText)
                .setContentTitle(unreadMessageCountText)
                .setContentText(newMailText)
                .setContentIntent(contentIntent);
    }

    /**
     * Create a notification with non-shown to update badge number
     *
     * @param account Acount ID
     * @param channelId chanel ID
     * @return builder
     */
    protected NotificationCompat.Builder createBadgeNotification(Account account, String channelId)
    {
        int notificationId = NotificationIds.getNewMailSummaryNotificationId(account);

        PendingIntent contentIntent = actionCreator.createAccountInBoxPendingIntent(account, notificationId);
        return createAndInitializeNotificationBuilder(account, channelId)
                // .setTicker(newMailText)
                .setContentTitle(" ")
                // .setContentText(newMailText)
                .setContentIntent(contentIntent);
    }

    // Must setFullScreenIntent to wake android from sleep and for heads-up to show and trigger edge-light; implement???
    // See aTalk NotificationPopupHandler#showPopupMessage()
    private NotificationCompat.Builder createBigTextStyleSummaryNotification(Account account, NotificationHolder holder)
    {
        int notificationId = NotificationIds.getNewMailSummaryNotificationId(account);
        Builder builder = createBigTextStyleNotification(account, holder, notificationId)
                .setGroupSummary(true);

        NotificationContent content = holder.content;
        addReplyAction(builder, content, notificationId);
        addMarkAsReadAction(builder, content, notificationId);
        addDeleteAction(builder, content, notificationId);

        return builder;
    }

    private NotificationCompat.Builder createInboxStyleSummaryNotification(Account account, NotificationData notificationData)
    {
        NotificationHolder latestNotification = notificationData.getHolderForLatestNotification();

        int newMessagesCount = notificationData.getNewMessagesCount();
        String accountName = controller.getAccountName(account);
        String title = context.getResources().getQuantityString(R.plurals.notification_new_messages_title,
                newMessagesCount, newMessagesCount);
        String summary = (notificationData.hasSummaryOverflowMessages()) ?
                context.getString(R.string.notification_additional_messages,
                        notificationData.getSummaryOverflowMessagesCount(), accountName) : accountName;
        String groupKey = NotificationGroupKeys.getGroupKey(account);

        NotificationCompat.Builder builder = createAndInitializeNotificationBuilder(account, NotificationHelper.EMAIL_GROUP)
                .setTicker(latestNotification.content.summary)
                .setGroup(groupKey)
                .setGroupSummary(true)
                .setContentTitle(title)
                .setSubText(accountName);

        NotificationCompat.InboxStyle style = createInboxStyle(builder)
                .setBigContentTitle(title)
                .setSummaryText(summary);

        for (NotificationContent content : notificationData.getContentForSummaryNotification()) {
            style.addLine(content.summary);
        }
        builder.setStyle(style);

        addMarkAllAsReadAction(builder, notificationData);
        addDeleteAllAction(builder, notificationData);

        wearNotifications.addSummaryActions(builder, notificationData);

        int notificationId = NotificationIds.getNewMailSummaryNotificationId(account);
        List<MessageReference> messageReferences = notificationData.getAllMessageReferences();
        PendingIntent contentIntent = actionCreator.createViewMessagesPendingIntent(
                account, messageReferences, notificationId);
        builder.setContentIntent(contentIntent);

        return builder;
    }

    private void addMarkAsReadAction(Builder builder, NotificationContent content, int notificationId)
    {
        int icon = getMarkAsReadActionIcon();
        String title = context.getString(R.string.notification_action_mark_as_read);

        MessageReference messageReference = content.messageReference;
        PendingIntent action = actionCreator.createMarkMessageAsReadPendingIntent(messageReference, notificationId);

        Action markAsReadAction = new Action.Builder(icon, title, action)
                .setSemanticAction(Action.SEMANTIC_ACTION_MARK_AS_READ)
                .build();

        builder.addAction(markAsReadAction);
    }

    private void addMarkAllAsReadAction(Builder builder, NotificationData notificationData)
    {
        int icon = getMarkAsReadActionIcon();
        String title = context.getString(R.string.notification_action_mark_as_read);

        Account account = notificationData.getAccount();
        ArrayList<MessageReference> messageReferences = notificationData.getAllMessageReferences();
        int notificationId = NotificationIds.getNewMailSummaryNotificationId(account);
        PendingIntent markAllAsReadPendingIntent =
                actionCreator.createMarkAllAsReadPendingIntent(account, messageReferences, notificationId);

        Action markAllAsReadAction = new Action.Builder(icon, title, markAllAsReadPendingIntent)
                .setSemanticAction(Action.SEMANTIC_ACTION_MARK_AS_READ)
                .build();

        builder.addAction(markAllAsReadAction);
    }

    private void addDeleteAllAction(Builder builder, NotificationData notificationData)
    {
        if (XryptoMail.getNotificationQuickDeleteBehaviour() != NotificationQuickDelete.ALWAYS) {
            return;
        }

        int icon = getDeleteActionIcon();
        String title = context.getString(R.string.notification_action_delete);

        Account account = notificationData.getAccount();
        int notificationId = NotificationIds.getNewMailSummaryNotificationId(account);
        ArrayList<MessageReference> messageReferences = notificationData.getAllMessageReferences();
        PendingIntent action = actionCreator.createDeleteAllPendingIntent(account, messageReferences, notificationId);

        Action deleteAllAction = new Action.Builder(icon, title, action)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_DELETE)
                .build();

        builder.addAction(deleteAllAction);
    }

    private void addDeleteAction(Builder builder, NotificationContent content, int notificationId)
    {
        if (!isDeleteActionEnabled()) {
            return;
        }

        int icon = getDeleteActionIcon();
        String title = context.getString(R.string.notification_action_delete);

        MessageReference messageReference = content.messageReference;
        PendingIntent action = actionCreator.createDeleteMessagePendingIntent(messageReference, notificationId);

        Action deleteAction = new Action.Builder(icon, title, action)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_DELETE)
                .build();

        builder.addAction(deleteAction);
    }

    private void addReplyAction(Builder builder, NotificationContent content, int notificationId)
    {
        int icon = getReplyActionIcon();
        String title = context.getString(R.string.notification_action_reply);

        MessageReference messageReference = content.messageReference;
        PendingIntent replyToMessagePendingIntent =
                actionCreator.createReplyPendingIntent(messageReference, notificationId);

        Action replyAction = new Action.Builder(icon, title, replyToMessagePendingIntent)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .build();

        builder.addAction(replyAction);
    }

    private boolean isPrivacyModeActive()
    {
        KeyguardManager keyguardService = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);

        boolean privacyModeAlwaysEnabled = XryptoMail.getNotificationHideSubject() == NotificationHideSubject.ALWAYS;
        boolean privacyModeEnabledWhenLocked = XryptoMail.getNotificationHideSubject() == NotificationHideSubject.WHEN_LOCKED;
        boolean screenLocked = keyguardService.inKeyguardRestrictedInputMode();

        return privacyModeAlwaysEnabled || (privacyModeEnabledWhenLocked && screenLocked);
    }

    private int getMarkAsReadActionIcon()
    {
        return R.drawable.notification_action_mark_as_read;
    }

    private int getDeleteActionIcon()
    {
        return R.drawable.notification_action_delete;
    }

    private int getReplyActionIcon()
    {
        return R.drawable.notification_action_reply;
    }

    protected InboxStyle createInboxStyle(Builder builder)
    {
        return new InboxStyle(builder);
    }
}
