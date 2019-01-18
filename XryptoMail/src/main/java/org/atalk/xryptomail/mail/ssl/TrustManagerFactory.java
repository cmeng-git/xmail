
package org.atalk.xryptomail.mail.ssl;

import org.apache.http.conn.ssl.StrictHostnameVerifier;
import org.atalk.xryptomail.mail.CertificateChainException;
import timber.log.Timber;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public final class TrustManagerFactory {
    private static X509TrustManager defaultTrustManager;
    private static LocalKeyStore keyStore;

    private static class SecureX509TrustManager implements X509TrustManager {
        private static final Map<String, SecureX509TrustManager> mTrustManager =
            new HashMap<String, SecureX509TrustManager>();

        private final String mHost;
        private final int mPort;

        private SecureX509TrustManager(String host, int port) {
            mHost = host;
            mPort = port;
        }

        public synchronized static X509TrustManager getInstance(String host, int port) {
            String key = host + ":" + port;
            SecureX509TrustManager trustManager;
            if (mTrustManager.containsKey(key)) {
                trustManager = mTrustManager.get(key);
            } else {
                trustManager = new SecureX509TrustManager(host, port);
                mTrustManager.put(key, trustManager);
            }

            return trustManager;
        }

        public void checkClientTrusted(X509Certificate[] chain, String authType)
        throws CertificateException {
            defaultTrustManager.checkClientTrusted(chain, authType);
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            String message = null;
            X509Certificate certificate = chain[0];

            Throwable cause = null;

            try {
                defaultTrustManager.checkServerTrusted(chain, authType);
                new StrictHostnameVerifier().verify(mHost, certificate);
                return;
            } catch (CertificateException e) {
                // cert. chain can't be validated
                message = e.getMessage();
                cause = e;
            } catch (SSLException e) {
                // host name doesn't match certificate
                message = e.getMessage();
                cause = e;
            }

            // Check the local key store if we couldn't verify the certificate using the global
            // key store or if the host name doesn't match the certificate name
            if (!keyStore.isValidCertificate(certificate, mHost, mPort)) {
                throw new CertificateChainException(message, chain, cause);
            }
        }

        public X509Certificate[] getAcceptedIssuers() {
            return defaultTrustManager.getAcceptedIssuers();
        }
    }

    static {
        try {
            keyStore = LocalKeyStore.getInstance();

            javax.net.ssl.TrustManagerFactory tmf = javax.net.ssl.TrustManagerFactory.getInstance("X509");
            tmf.init((KeyStore) null);

            TrustManager[] tms = tmf.getTrustManagers();
            if (tms != null) {
                for (TrustManager tm : tms) {
                    if (tm instanceof X509TrustManager) {
                        defaultTrustManager = (X509TrustManager) tm;
                        break;
                    }
                }
            }
        } catch (NoSuchAlgorithmException e) {
            Timber.e(e, "Unable to get X509 Trust Manager ");
        } catch (KeyStoreException e) {
            Timber.e(e, "Key Store exception while initializing TrustManagerFactory");
        }
    }

    private TrustManagerFactory() {
    }

    public static X509TrustManager get(String host, int port) {
        return SecureX509TrustManager.getInstance(host, port);
    }
}
