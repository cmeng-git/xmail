package org.atalk.xryptomail.ui.messageview;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcelable;
import androidx.annotation.Nullable;

import org.atalk.xryptomail.Account;
import org.atalk.xryptomail.XryptoMail;
import org.atalk.xryptomail.mailstore.CryptoResultAnnotation;
import org.atalk.xryptomail.mailstore.MessageViewInfo;
import org.atalk.xryptomail.view.MessageCryptoDisplayStatus;
import timber.log.Timber;


@SuppressWarnings("WeakerAccess")
public class MessageCryptoPresenter implements OnCryptoClickListener {
    public static final int REQUEST_CODE_UNKNOWN_KEY = 123;
    public static final int REQUEST_CODE_SECURITY_WARNING = 124;

    // injected state
    private final MessageCryptoMvpView messageCryptoMvpView;

    // persistent state
    private boolean overrideCryptoWarning;

    // transient state
    private CryptoResultAnnotation cryptoResultAnnotation;
    private boolean reloadOnResumeWithoutRecreateFlag;

    public MessageCryptoPresenter(Bundle savedInstanceState, MessageCryptoMvpView messageCryptoMvpView) {
        this.messageCryptoMvpView = messageCryptoMvpView;

        if (savedInstanceState != null) {
            overrideCryptoWarning = savedInstanceState.getBoolean("overrideCryptoWarning");
        }
    }

    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("overrideCryptoWarning", overrideCryptoWarning);
    }

    public void onResume() {
        if (reloadOnResumeWithoutRecreateFlag) {
            reloadOnResumeWithoutRecreateFlag = false;
            messageCryptoMvpView.restartMessageCryptoProcessing();
        }
    }

    public boolean maybeHandleShowMessage(MessageTopView messageView, Account account, MessageViewInfo messageViewInfo) {
        this.cryptoResultAnnotation = messageViewInfo.cryptoResultAnnotation;

        MessageCryptoDisplayStatus displayStatus =
                MessageCryptoDisplayStatus.fromResultAnnotation(messageViewInfo.cryptoResultAnnotation);
        if (displayStatus == MessageCryptoDisplayStatus.DISABLED) {
            return false;
        }

        boolean suppressSignOnlyMessages = !XryptoMail.getOpenPgpSupportSignOnly();
        if (suppressSignOnlyMessages && displayStatus.isUnencryptedSigned()) {
            return false;
        }

        if (cryptoResultAnnotation.isOverrideSecurityWarning()) {
            overrideCryptoWarning = true;
        }

        messageView.getMessageHeaderView().setCryptoStatus(displayStatus);

        switch (displayStatus) {
            case CANCELLED: {
                Drawable providerIcon = getOpenPgpApiProviderIcon(messageView.getContext());
                messageView.showMessageCryptoCancelledView(messageViewInfo, providerIcon);
                break;
            }

            case INCOMPLETE_ENCRYPTED: {
                Drawable providerIcon = getOpenPgpApiProviderIcon(messageView.getContext());
                messageView.showMessageEncryptedButIncomplete(messageViewInfo, providerIcon);
                break;
            }

            case ENCRYPTED_ERROR:
            case UNSUPPORTED_ENCRYPTED: {
                Drawable providerIcon = getOpenPgpApiProviderIcon(messageView.getContext());
                messageView.showMessageCryptoErrorView(messageViewInfo, providerIcon);
                break;
            }

            case ENCRYPTED_NO_PROVIDER: {
                messageView.showCryptoProviderNotConfigured(messageViewInfo);
                break;
            }

            case INCOMPLETE_SIGNED:
            case UNSUPPORTED_SIGNED:
            default: {
                messageView.showMessage(account, messageViewInfo);
                break;
            }

            case LOADING: {
                throw new IllegalStateException("Displaying message while in loading state!");
            }
        }
        return true;
    }

    @Override
    public void onCryptoClick() {
        if (cryptoResultAnnotation == null) {
            return;
        }
        MessageCryptoDisplayStatus displayStatus =
                MessageCryptoDisplayStatus.fromResultAnnotation(cryptoResultAnnotation);
        switch (displayStatus) {
            case LOADING:
                // no need to do anything, there is a progress bar...
                break;
            case UNENCRYPTED_SIGN_UNKNOWN:
                launchPendingIntent(cryptoResultAnnotation);
                break;
            default:
                displayCryptoInfoDialog(displayStatus);
                break;
        }
    }

    @SuppressWarnings("UnusedParameters") // for consistency with Activity.onActivityResult
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_UNKNOWN_KEY) {
            if (resultCode != Activity.RESULT_OK) {
                return;
            }

            messageCryptoMvpView.restartMessageCryptoProcessing();
        } else if (requestCode == REQUEST_CODE_SECURITY_WARNING) {
            messageCryptoMvpView.redisplayMessage();
        } else {
            throw new IllegalStateException("got an activity result that wasn't meant for us. this is a bug!");
        }
    }

    private void displayCryptoInfoDialog(MessageCryptoDisplayStatus displayStatus) {
        messageCryptoMvpView.showCryptoInfoDialog(
                displayStatus, cryptoResultAnnotation.hasOpenPgpInsecureWarningPendingIntent());
    }

    private void launchPendingIntent(CryptoResultAnnotation cryptoResultAnnotation) {
        try {
            PendingIntent pendingIntent = cryptoResultAnnotation.getOpenPgpPendingIntent();
            if (pendingIntent != null) {
                messageCryptoMvpView.startPendingIntentForCryptoPresenter(
                        pendingIntent.getIntentSender(), REQUEST_CODE_UNKNOWN_KEY, null, 0, 0, 0);
            }
        } catch (IntentSender.SendIntentException e) {
            Timber.e("SendIntentException: %s", e.getMessage());
        }
    }

    public void onClickShowCryptoKey() {
        try {
            PendingIntent pendingIntent = cryptoResultAnnotation.getOpenPgpSigningKeyIntentIfAny();
            if (pendingIntent != null) {
                messageCryptoMvpView.startPendingIntentForCryptoPresenter(
                        pendingIntent.getIntentSender(), null, null, 0, 0, 0);
            }
        } catch (IntentSender.SendIntentException e) {
            Timber.e("SendIntentException: %s", e.getMessage());
        }
    }

    public void onClickRetryCryptoOperation() {
        messageCryptoMvpView.restartMessageCryptoProcessing();
    }

    public void onClickShowCryptoWarningDetails() {
        try {
            PendingIntent pendingIntent = cryptoResultAnnotation.getOpenPgpInsecureWarningPendingIntent();
            if (pendingIntent != null) {
                messageCryptoMvpView.startPendingIntentForCryptoPresenter(
                        pendingIntent.getIntentSender(), REQUEST_CODE_SECURITY_WARNING, null, 0, 0, 0);
            }
        } catch (IntentSender.SendIntentException e) {
            Timber.e("SendIntentException: %s", e.getMessage());
        }
    }

    public Parcelable getDecryptionResultForReply() {
        if (cryptoResultAnnotation != null && cryptoResultAnnotation.isOpenPgpResult()) {
            return cryptoResultAnnotation.getOpenPgpDecryptionResult();
        }
        return null;
    }

    @Nullable
    private static Drawable getOpenPgpApiProviderIcon(Context context) {
        try {
            String openPgpProvider = XryptoMail.getOpenPgpProvider();
            if (XryptoMail.NO_OPENPGP_PROVIDER.equals(openPgpProvider)) {
                return null;
            }
            return context.getPackageManager().getApplicationIcon(openPgpProvider);
        } catch (NameNotFoundException e) {
            return null;
        }
    }

    public void onClickConfigureProvider() {
        reloadOnResumeWithoutRecreateFlag = true;
        messageCryptoMvpView.showCryptoConfigDialog();
    }

    public interface MessageCryptoMvpView {
        void redisplayMessage();
        void restartMessageCryptoProcessing();

        void startPendingIntentForCryptoPresenter(IntentSender si, Integer requestCode, Intent fillIntent,
                int flagsMask, int flagValues, int extraFlags) throws IntentSender.SendIntentException;

        void showCryptoInfoDialog(MessageCryptoDisplayStatus displayStatus, boolean hasSecurityWarning);
        void showCryptoConfigDialog();
    }
}
