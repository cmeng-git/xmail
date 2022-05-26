package org.atalk.xryptomail.account;

import android.content.Context;
import android.content.SharedPreferences;

import org.atalk.xryptomail.mail.oauth.OAuth2AuthorizationCodeFlowTokenProvider;
import org.atalk.xryptomail.mail.oauth.SpecificOAuth2TokenProvider;

public class XMailOAuth2AuthorizationCodeFlowTokenProvider extends OAuth2AuthorizationCodeFlowTokenProvider {
    private final Context context;
    private static final String REFRESH_TOKEN_SP = "refresh_token";

    private enum TYPE {
        GMAIL,
        OUTLOOK,
    }

    private final GmailOAuth2TokenStore gmailOAuth2TokenStore;
    private final OutlookOAuth2TokenStore outlookOAuth2TokenStore;

    public XMailOAuth2AuthorizationCodeFlowTokenProvider(Context context) {
        this.context = context;
        gmailOAuth2TokenStore = new GmailOAuth2TokenStore();
        outlookOAuth2TokenStore = new OutlookOAuth2TokenStore();
    }

    public void setPromptRequestHandler(Oauth2PromptRequestHandler promptRequestHandler) {
        gmailOAuth2TokenStore.setPromptRequestHandler(promptRequestHandler);
        outlookOAuth2TokenStore.setPromptRequestHandler(promptRequestHandler);
    }

    @Override
    protected void saveRefreshToken(String email, String refreshToken) {
        getSharedPreference().edit().putString(email, refreshToken).apply();
    }

    @Override
    public void showAuthDialog(String email) {
        SpecificOAuth2TokenProvider provider = getSpecificProviderFromEmail(email);
        if (provider == null) return;
        provider.showAuthDialog();
    }

    @Override
    protected String getRefreshToken(String email) {
        return getSharedPreference().getString(email, null);
    }

    @Override
    public void invalidateRefreshToken(String email) {
        getSharedPreference().edit().remove(email).apply();
    }

    @Override
    protected SpecificOAuth2TokenProvider getSpecificProviderFromEmail(String email) {
        TYPE type = getServerTypeFromEmail(email);
        if (type == null) return null;
        switch (type) {
            case GMAIL:
                return gmailOAuth2TokenStore;
            case OUTLOOK:
                return outlookOAuth2TokenStore;
        }
        return null;
    }

    private TYPE getServerTypeFromEmail(String email) {
        String domain = email.split("@")[1];
        switch (domain) {
            case "gmail.com":
                return TYPE.GMAIL;
            case "outlook.com":
                return TYPE.OUTLOOK;
        }
        return null;
    }

    private SharedPreferences getSharedPreference() {
        return context.getSharedPreferences(REFRESH_TOKEN_SP, Context.MODE_PRIVATE);
    }

}
