package org.atalk.xryptomail.mail.store.webdav;

import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.scheme.LayeredSocketFactory;
import org.apache.http.params.HttpParams;
import org.atalk.xryptomail.mail.ssl.DefaultTrustedSocketFactory;
import org.atalk.xryptomail.mail.ssl.TrustManagerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

/*
 * TODO: find out what's going on here and document it.
 * Using two socket factories looks suspicious.
 */
public class WebDavSocketFactory implements LayeredSocketFactory
{
    private SSLSocketFactory mSocketFactory;
    private org.apache.http.conn.ssl.SSLSocketFactory mSchemeSocketFactory;

    public WebDavSocketFactory(String host, int port)
            throws NoSuchAlgorithmException, KeyManagementException
    {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[]{
                TrustManagerFactory.get(host, port)
        }, null);
        mSocketFactory = sslContext.getSocketFactory();
        mSchemeSocketFactory = org.apache.http.conn.ssl.SSLSocketFactory.getSocketFactory();
        mSchemeSocketFactory.setHostnameVerifier(
                org.apache.http.conn.ssl.SSLSocketFactory.STRICT_HOSTNAME_VERIFIER);
    }

    public Socket connectSocket(Socket sock, String host, int port,
            InetAddress localAddress, int localPort, HttpParams params)
            throws IOException
    {
        return mSchemeSocketFactory.connectSocket(sock, host, port, localAddress, localPort, params);
    }

    @Override
    public Socket createSocket()
            throws IOException
    {
        return mSocketFactory.createSocket();
    }

    public boolean isSecure(Socket sock)
            throws IllegalArgumentException
    {
        return mSchemeSocketFactory.isSecure(sock);
    }

    public Socket createSocket(final Socket socket, final String host, final int port, final boolean autoClose
    )
            throws IOException
    {
        SSLSocket sslSocket = (SSLSocket) mSocketFactory.createSocket(socket, host, port, autoClose);
        DefaultTrustedSocketFactory.setSniHost(mSocketFactory, sslSocket, host);
        //hostnameVerifier.verify(host, sslSocket);
        // verifyHostName() didn't blowup - good!
        return sslSocket;
    }
}
