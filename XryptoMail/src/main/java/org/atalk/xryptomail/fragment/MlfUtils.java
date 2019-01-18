package org.atalk.xryptomail.fragment;

import android.database.Cursor;
import android.text.TextUtils;

import org.atalk.xryptomail.*;
import org.atalk.xryptomail.activity.MessageReference;
import org.atalk.xryptomail.helper.Utility;
import org.atalk.xryptomail.mail.*;
import org.atalk.xryptomail.mailstore.*;

import java.util.List;

import timber.log.Timber;

import static org.atalk.xryptomail.fragment.MLFProjectionInfo.SENDER_LIST_COLUMN;

public class MlfUtils {

    static LocalFolder getOpenFolder(String folderName, Account account) throws MessagingException {
        LocalStore localStore = account.getLocalStore();
        LocalFolder localFolder = localStore.getFolder(folderName);
        localFolder.open(Folder.OPEN_MODE_RO);
        return localFolder;
    }

    static void setLastSelectedFolderName(Preferences preferences,
            List<MessageReference> messages, String destFolderName) {
        try {
            MessageReference firstMsg = messages.get(0);
            Account account = preferences.getAccount(firstMsg.getAccountUuid());
            LocalFolder firstMsgFolder = MlfUtils.getOpenFolder(firstMsg.getFolderName(), account);
            firstMsgFolder.setLastSelectedFolderName(destFolderName);
        } catch (MessagingException e) {
            Timber.e(e, "Error getting folder for setLastSelectedFolderName()");
        }
    }

    static String getSenderAddressFromCursor(Cursor cursor) {
        String fromList = cursor.getString(SENDER_LIST_COLUMN);
        Address[] fromAddrs = Address.unpack(fromList);
        return (fromAddrs.length > 0) ? fromAddrs[0].getAddress() : null;
    }

    static String buildSubject(String subjectFromCursor, String emptySubject, int threadCount) {
        if (TextUtils.isEmpty(subjectFromCursor)) {
            return emptySubject;
        } else if (threadCount > 1) {
            // If this is a thread, strip the RE/FW from the subject.  "Be like Outlook."
            return Utility.stripSubject(subjectFromCursor);
        }
        return subjectFromCursor;
    }
}
