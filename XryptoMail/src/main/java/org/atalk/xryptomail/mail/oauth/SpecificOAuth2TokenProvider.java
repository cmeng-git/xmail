package org.atalk.xryptomail.mail.oauth;

import org.atalk.xryptomail.mail.AuthenticationFailedException;

public abstract class SpecificOAuth2TokenProvider {
    public abstract OAuth2AuthorizationCodeFlowTokenProvider.Tokens exchangeCode(String username, String code) throws AuthenticationFailedException;

    public abstract String refreshToken(String username, String refreshToken) throws AuthenticationFailedException;

    public abstract void showAuthDialog();
}
