package org.atalk.xryptomail.activity.compose;

import android.content.Context;
import android.os.Handler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.atalk.xryptomail.Account;
import org.atalk.xryptomail.activity.MessageCompose;
import org.atalk.xryptomail.controller.MessagingController;
import org.atalk.xryptomail.helper.Contacts;
import org.atalk.xryptomail.mail.Message;

public class SaveMessageTask {
    Context mContext;
    Account mAccount;
    Contacts mContacts;
    Handler mHandler;
    Message mMessage;
    long mDraftId;
    boolean mSaveRemotely;

    public SaveMessageTask(Context context, Account account, Contacts contacts,
            Handler handler, Message message, long draftId, boolean saveRemotely) {
        mContext = context;
        mAccount = account;
        mContacts = contacts;
        mHandler = handler;
        mMessage = message;
        mDraftId = draftId;
        mSaveRemotely = saveRemotely;
    }

    public void execute() {
        try (ExecutorService eService = Executors.newSingleThreadExecutor()) {
            eService.execute(() -> {
                final MessagingController messagingController = MessagingController.getInstance(mContext);
                Message draftMessage = messagingController.saveDraft(mAccount, mMessage, mDraftId, mSaveRemotely);
                mDraftId = messagingController.getId(draftMessage);

                android.os.Message msg = android.os.Message.obtain(mHandler, MessageCompose.MSG_SAVED_DRAFT, mDraftId);
                mHandler.sendMessage(msg);
            });
        }
    }
}
