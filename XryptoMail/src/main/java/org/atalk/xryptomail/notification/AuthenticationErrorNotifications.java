package org.atalk.xryptomail.notification;

import static org.atalk.xryptomail.notification.NotificationController.NOTIFICATION_LED_BLINK_FAST;
import static org.atalk.xryptomail.notification.NotificationController.NOTIFICATION_LED_FAILURE_COLOR;
import static org.atalk.xryptomail.notification.NotificationHelper.getPendingIntentFlag;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationCompat.BigTextStyle;
import androidx.core.app.NotificationManagerCompat;

import org.atalk.xryptomail.Account;
import org.atalk.xryptomail.R;
import org.atalk.xryptomail.activity.setup.AccountSetupActivity;

class AuthenticationErrorNotifications {
    private final NotificationController controller;


    public AuthenticationErrorNotifications(NotificationController controller) {
        this.controller = controller;
    }

    public void showAuthenticationErrorNotification(Account account, boolean incoming) {
        int notificationId = NotificationIds.getAuthenticationErrorNotificationId(account, incoming);
        Context context = controller.getContext();

        PendingIntent editServerSettingsPendingIntent = createContentIntent(context, account, incoming);
        String title = context.getString(R.string.notification_authentication_error_title);
        String text = context.getString(R.string.notification_authentication_error_text, account.getDescription());

        NotificationCompat.Builder builder = controller.createNotificationBuilder(NotificationHelper.ERROR_GROUP)
                .setSmallIcon(R.drawable.notification_icon_warning)
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(true)
                .setTicker(title)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(editServerSettingsPendingIntent)
                .setStyle(new BigTextStyle().bigText(text))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_ERROR);

        controller.configureNotification(builder, null, null,
                NOTIFICATION_LED_FAILURE_COLOR,
                NOTIFICATION_LED_BLINK_FAST, true);

        getNotificationManager().notify(notificationId, builder.build());
    }

    public void clearAuthenticationErrorNotification(Account account, boolean incoming) {
        int notificationId = NotificationIds.getAuthenticationErrorNotificationId(account, incoming);
        getNotificationManager().cancel(notificationId);
    }

    PendingIntent createContentIntent(Context context, Account account, boolean incoming) {
        Intent editServerSettingsIntent = incoming ?
                AccountSetupActivity.intentActionEditIncomingSettings(context, account) :
                AccountSetupActivity.intentActionEditOutgoingSettings(context, account);

        return PendingIntent.getActivity(context, account.getAccountNumber(), editServerSettingsIntent,
                getPendingIntentFlag(false, true));
    }

    private NotificationManagerCompat getNotificationManager() {
        return controller.getNotificationManager();
    }
}
