package org.atalk.xryptomail.mail.oauth;

import android.app.Activity;

import org.atalk.xryptomail.mail.AuthenticationFailedException;
import org.atalk.xryptomail.mail.OAuth2NeedUserPromptException;

// import java.util.List;

public abstract class OAuth2TokenProvider {
    /**
     * A default timeout value to use when fetching tokens.
     */
    public int OAUTH2_TIMEOUT = 30000;

    
    /**
     * @return Accounts suitable for OAuth 2.0 token provision.
     */
    // List<String> getAccounts();

    /**
     * Request API authorization. This is a foreground action that may produce a dialog to interact with.
     *
     * @param username
     *         Username
     * @param activity
     *         The responsible activity
     * @param callback
     *         A callback to process the asynchronous response
     */
    // void authorizeApi(String username, Activity activity, OAuth2TokenProviderAuthCallback callback);

    /**
     * Fetch a token. No guarantees are provided for validity.
     */
    public abstract String getToken(String email, long timeoutMillis) throws AuthenticationFailedException, OAuth2NeedUserPromptException;

    /**
     * Invalidate the token for this username.
     *
     * <p>
     * Note that the token should always be invalidated on credential failure. However invalidating a token every
     * single time is not recommended.
     * <p>
     * Invalidating a token and then failure with a new token should be treated as a permanent failure.
     */
    public abstract void invalidateToken(String email);
	public abstract void disconnectEmailWithXMail(String email);

    /**
     * Provides an asynchronous response to an
     * {@link OAuth2TokenProvider#authorizeApi(String, Activity, OAuth2TokenProviderAuthCallback)} request.
     */
//    interface OAuth2TokenProviderAuthCallback {
//        void success();
//        void failure(AuthorizationException e);
//    }
}
