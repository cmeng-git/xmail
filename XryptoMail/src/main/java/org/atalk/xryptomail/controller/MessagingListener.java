package org.atalk.xryptomail.controller;


import android.content.Context;

import org.atalk.xryptomail.*;
import org.atalk.xryptomail.mail.*;
import org.atalk.xryptomail.mailstore.*;

import java.util.List;

public interface MessagingListener {
	void encryptionStarted(String strFile);
    void encryptionFinished(String strFile);
    void encryptionFailed(String strFile , String strReason);
    void decryptionStarted(String strFile);
    void decryptionFinished(String strFile);
    void decryptionFailed(String strFile , String strReason);
		
    void searchStats(AccountStats stats);

    void accountStatusChanged(BaseAccount account, AccountStats stats);
    void accountSizeChanged(Account account, long oldSize, long newSize);

    void listFoldersStarted(Account account);
    void listFolders(Account account, List<LocalFolder> folders);
    void listFoldersFinished(Account account);
    void listFoldersFailed(Account account, String message);

    void listLocalMessagesAddMessages(Account account, String folderServerId, List<LocalMessage> messages);

    void synchronizeMailboxStarted(Account account, String folderServerId);
    void synchronizeMailboxHeadersStarted(Account account, String folderServerId);
    void synchronizeMailboxHeadersProgress(Account account, String folderServerId, int completed, int total);
    void synchronizeMailboxHeadersFinished(Account account, String folderServerId, int totalMessagesInMailbox,
            int numNewMessages);
    void synchronizeMailboxProgress(Account account, String folderServerId, int completed, int total);
    void synchronizeMailboxNewMessage(Account account, String folderServerId, Message message);
    void synchronizeMailboxRemovedMessage(Account account, String folderServerId, Message message);
    void synchronizeMailboxFinished(Account account, String folderServerId, int totalMessagesInMailbox, int numNewMessages);
    void synchronizeMailboxFailed(Account account, String folderServerId, String message);

    void loadMessageRemoteFinished(Account account, String folderServerId, String uid);
    void loadMessageRemoteFailed(Account account, String folderServerId, String uid, Throwable t);

    void checkMailStarted(Context context, Account account);
    void checkMailFinished(Context context, Account account);

    void sendPendingMessagesStarted(Account account);
    void sendPendingMessagesCompleted(Account account);
    void sendPendingMessagesFailed(Account account);

    void emptyTrashCompleted(Account account);

    void folderStatusChanged(Account account, String folderServerId, int unreadMessageCount);
    void systemStatusChanged();

    void messageDeleted(Account account, String folderServerId, Message message);
    void messageUidChanged(Account account, String folderServerId, String oldUid, String newUid);

    void setPushActive(Account account, String folderServerId, boolean enabled);

    void loadAttachmentFinished(Account account, Message message, Part part);
    void loadAttachmentFailed(Account account, Message message, Part part, String reason);

    void pendingCommandStarted(Account account, String commandTitle);
    void pendingCommandsProcessing(Account account);
    void pendingCommandCompleted(Account account, String commandTitle);
    void pendingCommandsFinished(Account account);

    void remoteSearchStarted(String folder);
    void remoteSearchServerQueryComplete(String folderServerId, int numResults, int maxResults);
    void remoteSearchFinished(String folderServerId, int numResults, int maxResults, List<Message> extraResults);
    void remoteSearchFailed(String folderServerId, String err);

    void enableProgressIndicator(boolean enable);

    void updateProgress(int progress);
}
