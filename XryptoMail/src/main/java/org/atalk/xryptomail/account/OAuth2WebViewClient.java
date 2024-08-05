package org.atalk.xryptomail.account;

import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import timber.log.Timber;

/**
 * bass class for standard authorization code flow like Google's or Microsoft's
 */
abstract class OAuth2WebViewClient extends WebViewClient {
    private final Oauth2PromptRequestHandler requestHandler;
    private OAuth2ErrorHandler errorHandler;

    public OAuth2WebViewClient(Oauth2PromptRequestHandler requestHandler) {
        this.requestHandler = requestHandler;
    }

    protected abstract boolean arrivedAtRedirectUri(Uri uri);

    protected abstract boolean getOutOfDomain(Uri uri);

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        Uri uri = Uri.parse(request.getUrl().toString());

        if (arrivedAtRedirectUri(uri)) {
            final String error = uri.getQueryParameter("error");
            if (error != null) {
                Timber.e("got oauth error: %s", error);
                errorHandler.onError(error);
                requestHandler.onErrorWhenGettingOAuthCode(error);
                return true;
            }

            String oAuthCode = uri.getQueryParameter("code");
            requestHandler.onOAuthCodeGot(oAuthCode);
            return true;
        }

        if (getOutOfDomain(uri)) {
            requestHandler.onErrorWhenGettingOAuthCode("Don't surf away"); // TODO: 2017/8/19 better error message
            return true;
        }
        return false;
    }
}
