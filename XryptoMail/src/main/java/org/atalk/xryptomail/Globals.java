package org.atalk.xryptomail;

import android.content.Context;

import org.atalk.xryptomail.account.XMailOAuth2TokenProvider;

public class Globals {
    private static Context context;
    private static XMailOAuth2TokenProvider oAuth2TokenProvider;

    static void setContext(Context context) {
        Globals.context = context;
    }

    static void setOAuth2TokenProvider(XMailOAuth2TokenProvider oAuth2TokenProvider) {
        Globals.oAuth2TokenProvider = oAuth2TokenProvider;
    }

    public static Context getContext() {
        if (context == null) {
            throw new IllegalStateException("No context provided");
        }
        return context;
    }

    public static XMailOAuth2TokenProvider getOAuth2TokenProvider() {
        if (oAuth2TokenProvider == null) {
            throw new IllegalStateException("No OAuth 2.0 Token Provider provided");
        }
        return oAuth2TokenProvider;
    }
}
