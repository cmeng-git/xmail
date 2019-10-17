package org.atalk.xryptomail.controller;

import android.content.Context;
import timber.log.Timber;

import org.atalk.xryptomail.*;
import org.atalk.xryptomail.mail.*;
import org.atalk.xryptomail.power.TracingPowerManager.TracingWakeLock;
import org.atalk.xryptomail.mailstore.*;
import org.atalk.xryptomail.service.SleepService;

import java.util.List;
import java.util.concurrent.CountDownLatch;

public class MessagingControllerPushReceiver implements PushReceiver {
    final Account account;
    final MessagingController controller;
    final Context context;

    public MessagingControllerPushReceiver(Context context, Account nAccount, MessagingController nController) {
        account = nAccount;
        controller = nController;
        this.context = context;
    }

    public void messagesFlagsChanged(Folder folder, List<Message> messages) {
        controller.messagesArrived(account, folder, messages, true);
    }
    public void messagesArrived(Folder folder, List<Message> messages) {
        controller.messagesArrived(account, folder, messages, false);
    }
    public void messagesRemoved(Folder folder, List<Message> messages) {
        controller.messagesArrived(account, folder, messages, true);
    }

    public void syncFolder(Folder folder) {
        Timber.v("syncFolder(%s)", folder.getServerId());

        final CountDownLatch latch = new CountDownLatch(1);
        controller.synchronizeMailbox(account, folder.getServerId(), new SimpleMessagingListener() {
            @Override
            public void synchronizeMailboxFinished(Account account, String folderServerId,
            int totalMessagesInMailbox, int numNewMessages) {
                latch.countDown();
            }

            @Override
            public void synchronizeMailboxFailed(Account account, String folderServerId,
            String message) {
                latch.countDown();
            }
        }, folder);

        Timber.v("syncFolder(%s) about to await latch release", folder.getServerId());

        try {
            latch.await();
            Timber.v("syncFolder(%s) got latch release", folder.getServerId());
        } catch (Exception e) {
            Timber.e(e, "Interrupted while awaiting latch release");
        }
    }

    @Override
    public void sleep(TracingWakeLock wakeLock, long millis) {
        SleepService.sleep(context, millis, wakeLock, XryptoMail.PUSH_WAKE_LOCK_TIMEOUT);
    }

    public void pushError(String errorMessage, Exception e) {
        String errMess = errorMessage;

        controller.notifyUserIfCertificateProblem(account, e, true);
        if (errMess == null && e != null) {
            errMess = e.getMessage();
        }
        Timber.e(e, "%s", errMess);
    }

    @Override
    public void authenticationFailed() {
        controller.handleAuthenticationFailure(account, true);
    }

    public String getPushState(String folderServerId) {
        LocalFolder localFolder = null;
        try {
            LocalStore localStore = account.getLocalStore();
            localFolder = localStore.getFolder(folderServerId);
            localFolder.open(Folder.OPEN_MODE_RW);
            return localFolder.getPushState();
        } catch (Exception e) {
            Timber.e(e, "Unable to get push state from account %s, folder %s", account.getDescription(), folderServerId);
            return null;
        } finally {
            if (localFolder != null) {
                localFolder.close();
            }
        }
    }

    public void setPushActive(String folderServerId, boolean enabled) {
        for (MessagingListener l : controller.getListeners()) {
            l.setPushActive(account, folderServerId, enabled);
        }
    }

    @Override
    public Context getContext() {
        return context;
    }
}
