package org.atalk.xryptomail.activity;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.support.annotation.NonNull;

import org.atalk.xryptomail.*;
import org.atalk.xryptomail.controller.MessagingController;
import org.atalk.xryptomail.notification.NotificationActionService;

import java.util.*;

import static org.atalk.xryptomail.activity.MessageReferenceHelper.toMessageReferenceList;
import static org.atalk.xryptomail.activity.MessageReferenceHelper.toMessageReferenceStringList;


public class NotificationDeleteConfirmation extends Activity {
    private final static String EXTRA_ACCOUNT_UUID = "accountUuid";
    private final static String EXTRA_MESSAGE_REFERENCES = "messageReferences";

    private final static int DIALOG_CONFIRM = 1;

    private Account account;
    private List<MessageReference> messagesToDelete;


    public static Intent getIntent(Context context, MessageReference messageReference) {
        return getIntent(context, Collections.singletonList(messageReference));
    }

    public static Intent getIntent(Context context, List<MessageReference> messageReferences) {
        String accountUuid = messageReferences.get(0).getAccountUuid();

        Intent intent = new Intent(context, NotificationDeleteConfirmation.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(EXTRA_ACCOUNT_UUID, accountUuid);
        intent.putExtra(EXTRA_MESSAGE_REFERENCES, toMessageReferenceStringList(messageReferences));

        return intent;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setTheme(XryptoMail.getXMTheme() == XryptoMail.Theme.LIGHT ?
                R.style.Theme_XMail_Dialog_Translucent_Light : R.style.Theme_XMail_Dialog_Translucent_Dark);

        extractExtras();
        showDialog(DIALOG_CONFIRM);
    }

    private void extractExtras() {
        Intent intent = getIntent();
        String accountUuid = intent.getStringExtra(EXTRA_ACCOUNT_UUID);
        List<String> messageReferenceStrings = intent.getStringArrayListExtra(EXTRA_MESSAGE_REFERENCES);
        List<MessageReference> messagesToDelete = toMessageReferenceList(messageReferenceStrings);

        if (accountUuid == null) {
            throw new IllegalArgumentException(EXTRA_ACCOUNT_UUID + " can't be null");
        }

        if (messagesToDelete == null) {
            throw new IllegalArgumentException(EXTRA_MESSAGE_REFERENCES + " can't be null");
        }

        if (messagesToDelete.isEmpty()) {
            throw new IllegalArgumentException(EXTRA_MESSAGE_REFERENCES + " can't be empty");
        }

        Account account = getAccountFromUuid(accountUuid);
        if (account == null) {
            throw new IllegalStateException(EXTRA_ACCOUNT_UUID + " couldn't be resolved to an account");
        }

        this.account = account;
        this.messagesToDelete = messagesToDelete;
    }

    @Override
    public Dialog onCreateDialog(int dialogId) {
        switch (dialogId) {
            case DIALOG_CONFIRM: {
                return createDeleteConfirmationDialog(dialogId);
            }
        }

        return super.onCreateDialog(dialogId);
    }

    @Override
    public void onPrepareDialog(int dialogId, @NonNull Dialog dialog) {
        AlertDialog alert = (AlertDialog) dialog;
        switch (dialogId) {
            case DIALOG_CONFIRM: {
                int messageCount = messagesToDelete.size();
                alert.setMessage(getResources().getQuantityString(
                        R.plurals.dialog_confirm_delete_messages, messageCount, messageCount));
                break;
            }
        }
        super.onPrepareDialog(dialogId, dialog);
    }

    private Account getAccountFromUuid(String accountUuid) {
        Preferences preferences = Preferences.getPreferences(this);
        return preferences.getAccount(accountUuid);
    }

    private Dialog createDeleteConfirmationDialog(int dialogId) {
        return ConfirmationDialog.create(this, dialogId,
                R.string.dialog_confirm_delete_title, "",
                R.string.dialog_confirm_delete_confirm_button,
                R.string.dialog_confirm_delete_cancel_button,
                this::deleteAndFinish,
                this::finish);
    }

    private void deleteAndFinish() {
        cancelNotifications();
        triggerDelete();
        finish();
    }

    private void cancelNotifications() {
        MessagingController controller = MessagingController.getInstance(this);
        for (MessageReference messageReference : messagesToDelete) {
            controller.cancelNotificationForMessage(account, messageReference);
        }
    }

    private void triggerDelete() {
        String accountUuid = account.getUuid();
        Intent intent = NotificationActionService.createDeleteAllMessagesIntent(this, accountUuid, messagesToDelete);
        startService(intent);
    }
}
