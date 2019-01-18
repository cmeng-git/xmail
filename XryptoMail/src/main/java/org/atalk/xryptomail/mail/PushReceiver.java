package org.atalk.xryptomail.mail;

import android.content.Context;

import org.atalk.xryptomail.mail.power.TracingPowerManager.TracingWakeLock;

import java.util.List;

public interface PushReceiver {
    Context getContext();
    void syncFolder(Folder folder);
    void messagesArrived(Folder folder, List<Message> mess);
    void messagesFlagsChanged(Folder folder, List<Message> mess);
    void messagesRemoved(Folder folder, List<Message> mess);
    String getPushState(String folderName);
    void pushError(String errorMessage, Exception e);
    void authenticationFailed();
    void setPushActive(String folderName, boolean enabled);
    void sleep(TracingWakeLock wakeLock, long millis);
}
