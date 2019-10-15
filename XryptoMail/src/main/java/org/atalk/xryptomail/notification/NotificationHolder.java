package org.atalk.xryptomail.notification;

class NotificationHolder {
    public final int notificationId;
    public final NotificationContent content;


    public NotificationHolder(int notificationId, NotificationContent content) {
        this.notificationId = notificationId;
        this.content = content;
    }
}
