package org.atalk.xryptomail.notification;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.text.TextUtils;

import org.atalk.xryptomail.Account;
import org.atalk.xryptomail.BaseAccount;
import org.atalk.xryptomail.XryptoMail;
import org.atalk.xryptomail.activity.MessageReference;
import org.atalk.xryptomail.mail.Folder;
import org.atalk.xryptomail.mailstore.LocalMessage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import me.leolin.shortcutbadger.ShortcutBadger;
import timber.log.Timber;

import static org.atalk.xryptomail.XryptoMail.getGlobalContext;

public class NotificationController
{
    private static final int NOTIFICATION_LED_ON_TIME = 500;
    private static final int NOTIFICATION_LED_OFF_TIME = 2000;
    private static final int NOTIFICATION_LED_FAST_ON_TIME = 100;
    private static final int NOTIFICATION_LED_FAST_OFF_TIME = 100;
    static final int NOTIFICATION_LED_BLINK_SLOW = 0;
    static final int NOTIFICATION_LED_BLINK_FAST = 1;
    static final int NOTIFICATION_LED_FAILURE_COLOR = 0xffff0000;


    private final Context context;
    private final NotificationManagerCompat notificationManager;

    private final CertificateErrorNotifications certificateErrorNotifications;
    private final AuthenticationErrorNotifications authenticationErrorNotifications;
    private final SyncNotifications syncNotifications;
    private final SendFailedNotifications sendFailedNotifications;
    private final NewMailNotifications newMailNotifications;

    private static NotificationController mNotificationController;

    public static NotificationController newInstance(Context context)
    {
        if (mNotificationController == null) {
            Context appContext = context.getApplicationContext();
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(appContext);
            mNotificationController = new NotificationController(appContext, notificationManager);
        }
        return mNotificationController;
    }

    public static boolean platformSupportsExtendedNotifications()
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
    }

    public static boolean platformSupportsLockScreenNotifications()
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    NotificationController(Context context, NotificationManagerCompat notificationManager)
    {
        this.context = context;
        this.notificationManager = notificationManager;

        NotificationActionCreator actionBuilder = new NotificationActionCreator(context);
        certificateErrorNotifications = new CertificateErrorNotifications(this);
        authenticationErrorNotifications = new AuthenticationErrorNotifications(this);
        syncNotifications = new SyncNotifications(this, actionBuilder);
        sendFailedNotifications = new SendFailedNotifications(this, actionBuilder);
        newMailNotifications = NewMailNotifications.newInstance(this, actionBuilder);
    }

    public void showCertificateErrorNotification(Account account, boolean incoming)
    {
        certificateErrorNotifications.showCertificateErrorNotification(account, incoming);
    }

    public void clearCertificateErrorNotifications(Account account, boolean incoming)
    {
        certificateErrorNotifications.clearCertificateErrorNotifications(account, incoming);
    }

    public void showAuthenticationErrorNotification(Account account, boolean incoming)
    {
        authenticationErrorNotifications.showAuthenticationErrorNotification(account, incoming);
    }

    public void clearAuthenticationErrorNotification(Account account, boolean incoming)
    {
        authenticationErrorNotifications.clearAuthenticationErrorNotification(account, incoming);
    }

    public void showSendingNotification(Account account)
    {
        syncNotifications.showSendingNotification(account);
    }

    public void clearSendingNotification(Account account)
    {
        syncNotifications.clearSendingNotification(account);
    }

    public void showSendFailedNotification(Account account, Exception exception)
    {
        sendFailedNotifications.showSendFailedNotification(account, exception);
    }

    public void clearSendFailedNotification(Account account)
    {
        sendFailedNotifications.clearSendFailedNotification(account);
    }

    public void showFetchingMailNotification(Account account, Folder folder)
    {
        syncNotifications.showFetchingMailNotification(account, folder);
    }

    public void clearFetchingMailNotification(Account account)
    {
        syncNotifications.clearFetchingMailNotification(account);
    }

    public void addNewMailNotification(Account account, LocalMessage message, int previousUnreadMessageCount)
    {
        newMailNotifications.addNewMailNotification(account, message, previousUnreadMessageCount);
    }

    public void removeNewMailNotification(Account account, MessageReference messageReference)
    {
        newMailNotifications.removeNewMailNotification(account, messageReference);
    }

    public void clearNewMailNotifications(Account account)
    {
        newMailNotifications.clearNewMailNotifications(account);
    }

    void configureNotification(NotificationCompat.Builder builder, String ringtone, long[] vibrationPattern,
            Integer ledColor, int ledSpeed, boolean ringAndVibrate)
    {
        if (XryptoMail.isQuietTime()) {
            return;
        }

        if (ringAndVibrate) {
            if (ringtone != null && !TextUtils.isEmpty(ringtone)) {
                builder.setSound(Uri.parse(ringtone));
            }

            if (vibrationPattern != null) {
                builder.setVibrate(vibrationPattern);
            }
        }

        if (ledColor != null) {
            int ledOnMS;
            int ledOffMS;
            if (ledSpeed == NOTIFICATION_LED_BLINK_SLOW) {
                ledOnMS = NOTIFICATION_LED_ON_TIME;
                ledOffMS = NOTIFICATION_LED_OFF_TIME;
            }
            else {
                ledOnMS = NOTIFICATION_LED_FAST_ON_TIME;
                ledOffMS = NOTIFICATION_LED_FAST_OFF_TIME;
            }
            builder.setLights(ledColor, ledOnMS, ledOffMS);
        }
    }

    String getAccountName(Account account)
    {
        String accountDescription = account.getDescription();
        return TextUtils.isEmpty(accountDescription) ? account.getEmail() : accountDescription;
    }

    Context getContext()
    {
        return context;
    }

    NotificationManagerCompat getNotificationManager()
    {
        return notificationManager;
    }

    NotificationCompat.Builder createNotificationBuilder(String channelId)
    {
        return new NotificationCompat.Builder(context, channelId);
    }

    private Map<Account, Integer> accountUnreadCount = new HashMap<>();

    /**
     * set the launcher badge number. set notify == false if the call handle itself >= android.o
     *
     * @param account mail account
     * @param msgCount unread message count for the specified account
     * @param notify skip notification if null
     *
     * @return the total number of unread messages for all the account for badge update
     */
    public int updateBadgeNumber(Account account, int msgCount, boolean notify)
    {
        int  prevUnreadCount = -1;
        if (accountUnreadCount.containsKey(account)) {
            prevUnreadCount = accountUnreadCount.get(account);
        }
        accountUnreadCount.put(account, msgCount);

        // Badge will show the total unread messages for all accounts
        int totalMsgCount = 0;
        for (Map.Entry<Account, Integer> entry :accountUnreadCount.entrySet()) {
            totalMsgCount += entry.getValue();
        }

        Timber.d(new Exception(), "Update launcher badge number to %s(%s) for account: %s = %s",
                msgCount, prevUnreadCount, account, totalMsgCount);

        if (prevUnreadCount != msgCount) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                if (totalMsgCount > 0)
                    ShortcutBadger.applyCount(getGlobalContext(), totalMsgCount);
                else
                    ShortcutBadger.removeCount(getGlobalContext());
            }
            if ((msgCount > 0) && notify) {
                newMailNotifications.createMailBadgeNotification(account, msgCount, totalMsgCount);
            }
        }
        return totalMsgCount;
    }
}
