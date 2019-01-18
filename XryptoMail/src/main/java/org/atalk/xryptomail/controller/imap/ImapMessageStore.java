package org.atalk.xryptomail.controller.imap;


import android.content.Context;

import org.atalk.xryptomail.Account;
import org.atalk.xryptomail.controller.MessagingController;
import org.atalk.xryptomail.controller.MessagingListener;
import org.atalk.xryptomail.controller.RemoteMessageStore;
import org.atalk.xryptomail.mail.Folder;
import org.atalk.xryptomail.notification.NotificationController;


public class ImapMessageStore implements RemoteMessageStore {
    private final ImapSync imapSync;

    public ImapMessageStore(NotificationController notificationController, MessagingController controller,
            Context context) {
        this.imapSync = new ImapSync(notificationController, controller, context);
    }

    @Override
    public void sync(Account account, String folder, MessagingListener listener, Folder providedRemoteFolder) {
        imapSync.sync(account, folder, listener, providedRemoteFolder);
    }
}
