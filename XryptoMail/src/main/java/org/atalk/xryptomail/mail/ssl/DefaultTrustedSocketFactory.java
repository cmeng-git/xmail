package org.atalk.xryptomail.mail.ssl;

import android.content.Context;
import android.net.SSLCertificateSocketFactory;
import android.text.TextUtils;

import org.atalk.xryptomail.mail.MessagingException;

import java.io.IOException;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import timber.log.Timber;

/**
 * Prior to API 21 (and notably from API 10 - 2.3.4) Android weakened it's cipher list
 * by ordering them badly such that RC4-MD5 was preferred. To work around this we
 * remove the insecure ciphers and reorder them so the latest more secure ciphers are at the top.
 *
 * On more modern versions of Android we keep the system configuration.
 */
public class DefaultTrustedSocketFactory implements TrustedSocketFactory {
    private static final String[] ENABLED_CIPHERS;
    private static final String[] ENABLED_PROTOCOLS;

    private static final String[] DISALLOWED_CIPHERS = {
            "SSL_RSA_WITH_DES_CBC_SHA",
            "SSL_DHE_RSA_WITH_DES_CBC_SHA",
            "SSL_DHE_DSS_WITH_DES_CBC_SHA",
            "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
            "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
            "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
            "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA",
            "SSL_RSA_WITH_3DES_EDE_CBC_SHA",
            "SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA",
            "SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDHE_RSA_WITH_RC4_128_SHA",
            "TLS_ECDHE_ECDSA_WITH_RC4_128_SHA",
            "TLS_ECDH_RSA_WITH_RC4_128_SHA",
            "TLS_ECDH_ECDSA_WITH_RC4_128_SHA",
            "SSL_RSA_WITH_RC4_128_SHA",
            "SSL_RSA_WITH_RC4_128_MD5",
            "TLS_ECDH_RSA_WITH_NULL_SHA",
            "TLS_ECDH_anon_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDH_anon_WITH_NULL_SHA",
            "TLS_ECDH_anon_WITH_RC4_128_SHA",
            "TLS_RSA_WITH_NULL_SHA256"
    };

    private static final String[] DISALLOWED_PROTOCOLS = {
            "SSLv3"
    };

    static {
        String[] enabledCiphers = null;
        String[] supportedProtocols = null;

        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, null, null);
            SSLSocketFactory sf = sslContext.getSocketFactory();
            SSLSocket sock = (SSLSocket) sf.createSocket();
            enabledCiphers = sock.getEnabledCipherSuites();

            /*
             * Retrieve all supported protocols, not just the (default) enabled
             * ones. TLSv1.1 & TLSv1.2 are supported on API levels 16+, but are
             * only enabled by default on API levels 20+.
             */
            supportedProtocols = sock.getSupportedProtocols();
        } catch (Exception e) {
            Timber.e(e, "Error getting information about available SSL/TLS ciphers and protocols");
        }

        ENABLED_CIPHERS = (enabledCiphers == null) ? null : remove(enabledCiphers, DISALLOWED_CIPHERS);
        ENABLED_PROTOCOLS = (supportedProtocols == null) ? null : remove(supportedProtocols, DISALLOWED_PROTOCOLS);
    }

    private final Context context;

    public DefaultTrustedSocketFactory(Context context) {
        this.context = context;
    }

    protected static String[] remove(String[] enabled, String[] disallowed) {
        List<String> items = new ArrayList<>();
        Collections.addAll(items, enabled);

        // Remove disallowed items
        if (disallowed != null) {
            for (String item : disallowed) {
                items.remove(item);
            }
        }
        return items.toArray(new String[0]);
    }

    public Socket createSocket(Socket socket, String host, int port, String clientCertificateAlias)
            throws NoSuchAlgorithmException, KeyManagementException, MessagingException, IOException {

        TrustManager[] trustManagers = new TrustManager[]{TrustManagerFactory.get(host, port)};
        KeyManager[] keyManagers = null;
        if (!TextUtils.isEmpty(clientCertificateAlias)) {
            keyManagers = new KeyManager[]{new KeyChainKeyManager(context, clientCertificateAlias)};
        }

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, trustManagers, null);
        SSLSocketFactory socketFactory = sslContext.getSocketFactory();
        SSLSocket sslSocket;
        if (socket == null) {
            // sslSocket = (SSLSocket) socketFactory.createSocket();
            sslSocket = (SSLSocket) socketFactory.createSocket(host, port);
        } else {
            sslSocket = (SSLSocket) socketFactory.createSocket(socket, host, port, true);
        }

        hardenSocket(sslSocket);
        // setSniHost(socketFactory, sslSocket, host);
        return sslSocket;
    }

    private static void hardenSocket(SSLSocket sock) {
        if (ENABLED_CIPHERS != null) {
            sock.setEnabledCipherSuites(ENABLED_CIPHERS);
        }
        if (ENABLED_PROTOCOLS != null) {
            sock.setEnabledProtocols(ENABLED_PROTOCOLS);
        }
    }

    public static void setSniHost(SSLSocketFactory factory, SSLSocket sslSocket, String hostname) {
        if (factory instanceof SSLCertificateSocketFactory) {
            SSLCertificateSocketFactory sslCertificateSocketFactory = (SSLCertificateSocketFactory) factory;
            sslCertificateSocketFactory.setHostname(sslSocket, hostname);
        }
        else {
            setHostnameViaReflection(sslSocket, hostname);
        }
    }

    private static void setHostnameViaReflection(SSLSocket socket, String hostname) {
        try {
            socket.getClass().getMethod("setHostname", String.class).invoke(socket, hostname);
        } catch (Throwable e) {
            Timber.e(e, "Could not call SSLSocket#setHostname(String) method ");
        }
    }
}
