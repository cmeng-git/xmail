package org.atalk.xryptomail.account;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;

import org.atalk.xryptomail.mail.AuthenticationFailedException;
import org.atalk.xryptomail.mail.OAuth2NeedUserPromptException;
import org.atalk.xryptomail.mail.oauth.OAuth2TokenProvider;

public class XMailOAuth2TokenProvider extends OAuth2TokenProvider {
    private static final String GOOGLE_ACCOUNT_TYPE = "com.google";

    private AccountManager accountManager;
    private AndroidAccountOAuth2TokenStore gmailTokenProviderWithAccountSystem;
    private XMailOAuth2AuthorizationCodeFlowTokenProvider authorizationCodeFlowTokenProvider;
    private Oauth2PromptRequestHandler promptRequestHandler;

    public XMailOAuth2TokenProvider(Context context) {
        accountManager = AccountManager.get(context);
        gmailTokenProviderWithAccountSystem = new AndroidAccountOAuth2TokenStore(context);
        authorizationCodeFlowTokenProvider = new XMailOAuth2AuthorizationCodeFlowTokenProvider(context);
    }

    public XMailOAuth2AuthorizationCodeFlowTokenProvider getAuthorizationCodeFlowTokenProvider() {
        return authorizationCodeFlowTokenProvider;
    }

    private Account getAccountFromManager(String emailAddress) {
        android.accounts.Account[] accounts = accountManager.getAccountsByType(GOOGLE_ACCOUNT_TYPE);
        for (android.accounts.Account account : accounts) {
            if (account.name.equals(emailAddress)) {
                return account;
            }
        }
        return null;
    }

    @Override
    public String getToken(String email, long timeoutMillis)
            throws AuthenticationFailedException, OAuth2NeedUserPromptException {
        Account gmailAccount = getAccountFromManager(email);

        if (gmailAccount != null) {
            return gmailTokenProviderWithAccountSystem.getToken(email, gmailAccount, timeoutMillis);
        }
        return authorizationCodeFlowTokenProvider.getToken(email, timeoutMillis);
    }

    @Override
    public void invalidateToken(String email) {
        Account gmailAccount = getAccountFromManager(email);

        if (gmailAccount != null) {
            gmailTokenProviderWithAccountSystem.invalidateAccessToken(email);
        } else {
            authorizationCodeFlowTokenProvider.invalidateAccessToken(email);
        }
    }

    @Override
    public void disconnectEmailWithXMail(String email) {
        Account gmailAccount = getAccountFromManager(email);

        if (gmailAccount != null) {
            gmailTokenProviderWithAccountSystem.invalidateAccessToken(email);
        } else {
            authorizationCodeFlowTokenProvider.invalidateRefreshToken(email);
        }
    }

    public void setPromptRequestHandler(Oauth2PromptRequestHandler promptRequestHandler) {
        this.promptRequestHandler = promptRequestHandler;
        gmailTokenProviderWithAccountSystem.setPromptRequestHandler(promptRequestHandler);
        authorizationCodeFlowTokenProvider.setPromptRequestHandler(promptRequestHandler);
    }
}
