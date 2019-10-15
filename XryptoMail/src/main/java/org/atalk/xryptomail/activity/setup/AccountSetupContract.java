package org.atalk.xryptomail.activity.setup;

import java.net.URISyntaxException;
import java.security.cert.X509Certificate;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.StringRes;

import org.atalk.xryptomail.Account;
import org.atalk.xryptomail.activity.AccountConfig;
import org.atalk.xryptomail.activity.setup.AccountSetupPresenter.Stage;
import org.atalk.xryptomail.mail.AuthType;
import org.atalk.xryptomail.mail.ConnectionSecurity;
import org.atalk.xryptomail.mail.ServerSettings.Type;

interface AccountSetupContract {
    interface View {
        // account type
        void goToIncomingSettings();

        // basics
        void setPasswordInBasicsEnabled(boolean enabled);
        void setPasswordHintInBasics(String hint);
        void setManualSetupButtonInBasicsVisibility(int visibility);
        void setNextButtonInBasicsEnabled(boolean enabled);
        void goToAccountType();
        void goToAutoConfiguration();

        // check settings
        void showAcceptKeyDialog(final int msgResId, final String exMessage, String message, X509Certificate certificate);
        void showErrorDialog(final int msgResId, final Object... args);
        void showErrorDialog(String string);
        void setMessage(@StringRes int id);

        void goToBasics();
        void goToIncoming();
        void goToOutgoing();
        Context getContext();

        /* --incoming-- */

        void goToIncomingChecking();
        void setNextButtonInIncomingEnabled(boolean enabled);
        void setAuthTypeInIncoming(AuthType authType);
        void setSecurityTypeInIncoming(ConnectionSecurity security);

        void setUsernameInIncoming(String username);
        void setPasswordInIncoming(String password);
        void setCertificateAliasInIncoming(String alias);
        void setServerInIncoming(String server);
        void setPortInIncoming(String port);
        void setServerLabel(String label);

        void hideViewsWhenPop3();
        void hideViewsWhenImap();
        void hideViewsWhenImapAndNotEdit();
        void hideViewsWhenWebDav();

        void setImapPathPrefixSectionVisibility(int visibility);
        void setImapAutoDetectNamespace(boolean autoDetectNamespace);
        void setImapPathPrefix(String prefix);

        void setWebDavPathPrefix(String prefix);
        void setWebDavAuthPath(String authPath);
        void setWebDavMailboxPath(String mailboxPath);

        void setSecurityChoices(ConnectionSecurity[] choices);
        void setAuthTypeInsecureText(boolean insecure);

        void setViewNotExternalInIncoming();
        void setViewExternalInIncoming();
        void setViewOAuth2InIncoming();
        void showFailureToast(Exception use);

        void setCompressionSectionVisibility(int visibility);
        void setCompressionMobile(boolean compressionMobile);
        void setCompressionWifi(boolean compressionWifi);
        void setCompressionOther(boolean compressionOther);

        void setSubscribedFoldersOnly(boolean subscribedFoldersOnly);

        void showInvalidSettingsToast();
        void showInvalidOAuthError();
        void clearInvalidOAuthError();

        /* --Names-- */
        void setDoneButtonInNamesEnabled(boolean enabled);
        void goToListAccounts();

        /* --outgoing-- */

        void setNextButtonInOutgoingEnabled(boolean enabled);

        void setAuthTypeInOutgoing(AuthType authType);
        void setSecurityTypeInOutgoing(ConnectionSecurity security);

        void setUsernameInOutgoing(String username);
        void setPasswordInOutgoing(String password);
        void setCertificateAliasInOutgoing(String alias);
        void setServerInOutgoing(String server);
        void setPortInOutgoing(String port);

        void showInvalidSettingsToastInOutgoing();

        void updateAuthPlainTextInOutgoing(boolean insecure);

        void setViewNotExternalInOutgoing();
        void setViewExternalInOutgoing();
        void setViewOAuth2InOutgoing();
        void goToOutgoingChecking();
        void goToAccountNames();

        // ---
        void goBack();
        void end();
        void startIntentForResult(Intent intent, int requestCode);
        void openGmailUrl(String url);
        void openOutlookUrl(String url);
        void closeAuthDialog();
    }

    interface Presenter
    {
        // account type
        void onAccountTypeStart();
        void onNextButtonInAccountTypeClicked(Type serverType) throws URISyntaxException;

        // basics
        void onBasicsStart();
        void onInputChangedInBasics(String email, String password);
        void onManualSetupButtonClicked(String email, String password);
        void onNextButtonInBasicViewClicked(String email, String password);
        void setAccount(Account account);
        Account getAccount();

        /* checking */
        void onNegativeClickedInConfirmationDialog();
        void onCheckingStart(Stage stage);

        void onCertificateAccepted(X509Certificate certificate);
        void onPositiveClickedInConfirmationDialog();

        /* incoming */

        void onIncomingStart(boolean editSettings);
        void onIncomingStart();
        void onInputChangedInIncoming(String certificateAlias, String server, String port,
                                      String username, String password, AuthType authType,
                                      ConnectionSecurity connectionSecurity);

        void onNextInIncomingClicked(String username, String password, String clientCertificateAlias,
                boolean autoDetectNamespace, String imapPathPrefix, String webdavPathPrefix, String webdavAuthPath,
                String webdavMailboxPath, String host, int port, ConnectionSecurity connectionSecurity,
                AuthType authType, boolean compressMobile, boolean compressWifi, boolean compressOther,
                boolean subscribedFoldersOnly);

        /* --names--*/
        void onNamesStart();
        void onInputChangedInNames(String name, String description);
        void onNextButtonInNamesClicked(String name, String description);

        // outgoing
        void onOutgoingStart();
        void onOutgoingStart(boolean editSettings);
        void onNextInOutgoingClicked(String username, String password, String clientCertificateAlias,
                String host, int port, ConnectionSecurity connectionSecurity,
                AuthType authType, boolean requireLogin);

        void onInputChangedInOutgoing(String certificateAlias, String server, String port,
                String username, String password, AuthType authType,
                ConnectionSecurity connectionSecurity, boolean requireLogin);

        void onCertificateRefused();

        // ---

        void onBackPressed();
        void onGetMakeDefault(boolean makeDefault);
        void onGetAccountUuid(String accountUuid);
        void onGetAccountConfig(AccountConfigImpl accountConfig);
        void onRestoreStart();
        void onRestoreEnd();

        void onActivityResult(int requestCode, int resultCode, Intent data);

        AccountSetupPresenter.AccountSetupStatus getStatus();
        AccountConfig getAccountConfig();
        void onWebViewDismiss();

        void onPause();
        void onStop();
        void onDestroy();
        void onResume();
    }
}
