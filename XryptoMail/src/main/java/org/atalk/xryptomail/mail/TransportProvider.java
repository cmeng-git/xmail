package org.atalk.xryptomail.mail;

import android.content.Context;

import org.atalk.xryptomail.mail.oauth.OAuth2AuthorizationCodeFlowTokenProvider;
import org.atalk.xryptomail.mail.oauth.OAuth2TokenProvider;
import org.atalk.xryptomail.mail.ssl.DefaultTrustedSocketFactory;
import org.atalk.xryptomail.mail.store.StoreConfig;
import org.atalk.xryptomail.mail.transport.smtp.SmtpTransport;
import org.atalk.xryptomail.mail.transport.WebDavTransport;

public class TransportProvider {
    private static TransportProvider transportProvider = new TransportProvider();

    public static TransportProvider getInstance() {
        return transportProvider;
    }

    public synchronized Transport getTransport(Context context, StoreConfig storeConfig)
        throws MessagingException {
        return getTransport(context, storeConfig, null);
    }

    public synchronized Transport getTransport(Context context, StoreConfig storeConfig, OAuth2TokenProvider oAuth2TokenProvider)
            throws MessagingException {
        String uri = storeConfig.getTransportUri();
        if (uri.startsWith("smtp")) {
            return new SmtpTransport(storeConfig, new DefaultTrustedSocketFactory(context), oAuth2TokenProvider);
        } else if (uri.startsWith("webdav")) {
            return new WebDavTransport(storeConfig);
        } else {
            throw new MessagingException("Unable to locate an applicable Transport for " + uri);
        }
    }
}
