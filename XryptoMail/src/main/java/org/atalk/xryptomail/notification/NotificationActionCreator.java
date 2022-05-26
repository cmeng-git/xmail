package org.atalk.xryptomail.notification;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.app.TaskStackBuilder;

import org.atalk.xryptomail.Account;
import org.atalk.xryptomail.Preferences;
import org.atalk.xryptomail.XryptoMail;
import org.atalk.xryptomail.activity.Accounts;
import org.atalk.xryptomail.activity.FolderList;
import org.atalk.xryptomail.activity.MessageList;
import org.atalk.xryptomail.activity.MessageReference;
import org.atalk.xryptomail.activity.NotificationDeleteConfirmation;
import org.atalk.xryptomail.activity.compose.MessageActions;
import org.atalk.xryptomail.search.LocalSearch;

import java.util.List;

/**
 * This class contains methods to create the {@link PendingIntent}s for the actions of new mail notifications.
 *
 * <strong>Note:</strong>
 * We need to take special care to ensure the {@code PendingIntent}s are unique as defined in the documentation of
 * {@link PendingIntent}. Otherwise selecting a notification action might perform the action on the wrong message.
 *
 * We use the notification ID as {@code requestCode} argument to ensure each notification/action pair gets a unique
 * {@code PendingIntent}.
 */
class NotificationActionCreator {
    private final Context context;


    public NotificationActionCreator(Context context) {
        this.context = context;
    }

    public PendingIntent createViewMessagePendingIntent(MessageReference messageReference, int notificationId) {
        TaskStackBuilder stack = buildMessageViewBackStack(messageReference);
        return stack.getPendingIntent(notificationId,
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT
                        : PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);
    }

    public PendingIntent createViewFolderPendingIntent(Account account, String folderName, int notificationId) {
        TaskStackBuilder stack = buildMessageListBackStack(account, folderName);
        return stack.getPendingIntent(notificationId,
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT
                        : PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);
    }

    public PendingIntent createViewMessagesPendingIntent(Account account, List<MessageReference> messageReferences,
            int notificationId) {

        TaskStackBuilder stack;
        if (account.goToUnreadMessageSearch()) {
            stack = buildUnreadBackStack(account);
        } else {
            String folderName = getFolderNameOfAllMessages(messageReferences);

            if (folderName == null) {
                stack = buildFolderListBackStack(account);
            } else {
                stack = buildMessageListBackStack(account, folderName);
            }
        }
        return stack.getPendingIntent(notificationId,
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT
                        : PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);
    }

    /**
     * @param notificationId notification ID
     * @return intent ot launch InBox for the given account
     */
    public PendingIntent createAccountInBoxPendingIntent(Account account, int notificationId) {
        TaskStackBuilder stack = buildMessageListBackStack(account, Account.INBOX);
        return stack.getPendingIntent(notificationId,
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT
                        : PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);
    }

    public PendingIntent createViewFolderListPendingIntent(Account account, int notificationId) {
        TaskStackBuilder stack = buildFolderListBackStack(account);
        return stack.getPendingIntent(notificationId,
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT
                        : PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);
    }

    public PendingIntent createDismissAllMessagesPendingIntent(Account account, int notificationId) {
        Intent intent = NotificationActionService.createDismissAllMessagesIntent(context, account);
        return PendingIntent.getService(context, notificationId, intent,
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT
                        : PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);
    }

    public PendingIntent createDismissMessagePendingIntent(Context context, MessageReference messageReference, int notificationId) {
        Intent intent = NotificationActionService.createDismissMessageIntent(context, messageReference);
        return PendingIntent.getService(context, notificationId, intent,
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT
                        : PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);
    }

    public PendingIntent createReplyPendingIntent(MessageReference messageReference, int notificationId) {
        Intent intent = MessageActions.getActionReplyIntent(context, messageReference);
        return PendingIntent.getActivity(context, notificationId, intent,
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT
                        : PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT);
    }

    public PendingIntent createMarkMessageAsReadPendingIntent(MessageReference messageReference, int notificationId) {
        Intent intent = NotificationActionService.createMarkMessageAsReadIntent(context, messageReference);
        return PendingIntent.getService(context, notificationId, intent,
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT
                        : PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT);
    }

    public PendingIntent createMarkAllAsReadPendingIntent(Account account, List<MessageReference> messageReferences, int notificationId) {
        return getMarkAsReadPendingIntent(account, messageReferences, notificationId, context,
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT
                        : PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);
    }

    public PendingIntent getMarkAllAsReadPendingIntent(Account account, List<MessageReference> messageReferences, int notificationId) {
        return getMarkAsReadPendingIntent(account, messageReferences, notificationId, context,
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? PendingIntent.FLAG_NO_CREATE
                        : PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE);
    }

    private PendingIntent getMarkAsReadPendingIntent(Account account, List<MessageReference> messageReferences,
            int notificationId, Context context, int flags) {
        String accountUuid = account.getUuid();
        Intent intent = NotificationActionService.createMarkAllAsReadIntent(context, accountUuid, messageReferences);

        return PendingIntent.getService(context, notificationId, intent, flags);
    }

    public PendingIntent createDeleteMessagePendingIntent(MessageReference messageReference, int notificationId) {
        if (XryptoMail.confirmDeleteFromNotification()) {
            return createDeleteConfirmationPendingIntent(messageReference, notificationId);
        } else {
            return createDeleteServicePendingIntent(messageReference, notificationId);
        }
    }

    private PendingIntent createDeleteServicePendingIntent(MessageReference messageReference, int notificationId) {
        Intent intent = NotificationActionService.createDeleteMessageIntent(context, messageReference);

        return PendingIntent.getService(context, notificationId, intent,
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT
                        : PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT);
    }

    private PendingIntent createDeleteConfirmationPendingIntent(MessageReference messageReference, int notificationId) {
        Intent intent = NotificationDeleteConfirmation.getIntent(context, messageReference);
        return PendingIntent.getActivity(context, notificationId, intent,
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT
                        : PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public PendingIntent createDeleteAllPendingIntent(Account account, List<MessageReference> messageReferences, int notificationId) {
        if (XryptoMail.confirmDeleteFromNotification()) {
            return getDeleteAllConfirmationPendingIntent(messageReferences, notificationId,
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? PendingIntent.FLAG_CANCEL_CURRENT
                            : PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT);
        } else {
            return getDeleteAllServicePendingIntent(account, messageReferences, notificationId,
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT
                            : PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);
        }
    }

    public PendingIntent getDeleteAllPendingIntent(Account account, List<MessageReference> messageReferences, int notificationId) {
        if (XryptoMail.confirmDeleteFromNotification()) {
            return getDeleteAllConfirmationPendingIntent(messageReferences, notificationId,
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? PendingIntent.FLAG_NO_CREATE
                            : PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE);
        } else {
            return getDeleteAllServicePendingIntent(account, messageReferences, notificationId,
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? PendingIntent.FLAG_NO_CREATE
                            : PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE);
        }
    }

    private PendingIntent getDeleteAllConfirmationPendingIntent(List<MessageReference> messageReferences,
            int notificationId, int flags) {
        Intent intent = NotificationDeleteConfirmation.getIntent(context, messageReferences);

        return PendingIntent.getActivity(context, notificationId, intent, flags);
    }

    private PendingIntent getDeleteAllServicePendingIntent(Account account, List<MessageReference> messageReferences,
            int notificationId, int flags) {
        String accountUuid = account.getUuid();
        Intent intent = NotificationActionService.createDeleteAllMessagesIntent(
                context, accountUuid, messageReferences);

        return PendingIntent.getService(context, notificationId, intent, flags);
    }

    public PendingIntent createArchiveMessagePendingIntent(MessageReference messageReference, int notificationId) {
        Intent intent = NotificationActionService.createArchiveMessageIntent(context, messageReference);

        return PendingIntent.getService(context, notificationId, intent,
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT
                        : PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);
    }

    public PendingIntent createArchiveAllPendingIntent(Account account, List<MessageReference> messageReferences,
            int notificationId) {
        Intent intent = NotificationActionService.createArchiveAllIntent(context, account, messageReferences);

        return PendingIntent.getService(context, notificationId, intent,
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT
                        : PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);
    }

    public PendingIntent createMarkMessageAsSpamPendingIntent(MessageReference messageReference, int notificationId) {
        Intent intent = NotificationActionService.createMarkMessageAsSpamIntent(context, messageReference);

        return PendingIntent.getService(context, notificationId, intent,
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT
                        : PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);
    }

    private TaskStackBuilder buildAccountsBackStack() {
        TaskStackBuilder stack = TaskStackBuilder.create(context);
        if (!skipAccountsInBackStack()) {
            Intent intent = new Intent(context, Accounts.class);
            intent.putExtra(Accounts.EXTRA_STARTUP, false);
            stack.addNextIntent(intent);
        }
        return stack;
    }

    private TaskStackBuilder buildFolderListBackStack(Account account) {
        TaskStackBuilder stack = buildAccountsBackStack();

        Intent intent = FolderList.actionHandleAccountIntent(context, account, false);
        stack.addNextIntent(intent);
        return stack;
    }

    private TaskStackBuilder buildUnreadBackStack(final Account account) {
        TaskStackBuilder stack = buildAccountsBackStack();

        LocalSearch search = Accounts.createUnreadSearch(context, account);
        Intent intent = MessageList.intentDisplaySearch(context, search, true, false, false);

        stack.addNextIntent(intent);
        return stack;
    }

    private TaskStackBuilder buildMessageListBackStack(Account account, String folderName) {
        TaskStackBuilder stack = skipFolderListInBackStack(account, folderName)
                ? buildAccountsBackStack() : buildFolderListBackStack(account);

        LocalSearch search = new LocalSearch(folderName);
        search.addAllowedFolder(folderName);
        search.addAccountUuid(account.getUuid());
        Intent intent = MessageList.intentDisplaySearch(context, search, false, true, true);

        stack.addNextIntent(intent);
        return stack;
    }

    private TaskStackBuilder buildMessageViewBackStack(MessageReference message) {
        Account account = Preferences.getPreferences(context).getAccount(message.getAccountUuid());
        String folderName = message.getFolderServerId();
        TaskStackBuilder stack = buildMessageListBackStack(account, folderName);

        Intent intent = MessageList.actionDisplayMessageIntent(context, message);

        stack.addNextIntent(intent);
        return stack;
    }

    private String getFolderNameOfAllMessages(List<MessageReference> messageReferences) {
        MessageReference firstMessage = messageReferences.get(0);
        String folderName = firstMessage.getFolderServerId();

        for (MessageReference messageReference : messageReferences) {
            if (!TextUtils.equals(folderName, messageReference.getFolderServerId())) {
                return null;
            }
        }
        return folderName;
    }

    private boolean skipFolderListInBackStack(Account account, String folderName) {
        return (folderName != null) && folderName.equals(account.getAutoExpandFolder());
    }

    private boolean skipAccountsInBackStack() {
        return Preferences.getPreferences(context).getAccounts().size() == 1;
    }
}
