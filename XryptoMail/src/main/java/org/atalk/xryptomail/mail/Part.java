
package org.atalk.xryptomail.mail;

import android.support.annotation.NonNull;

import java.io.IOException;
import java.io.OutputStream;

public interface Part {
    void addHeader(String name, String value);

    void addRawHeader(String name, String raw);

    void removeHeader(String name);

    void setHeader(String name, String value);

    Body getBody();

    String getContentType();

    String getDisposition();

    String getContentId();

    /**
     * Returns an array of headers of the given name. The array may be empty.
     */
    @NonNull
    String[] getHeader(String name);

    boolean isMimeType(String mimeType);

    String getMimeType();

    void setBody(Body body);

    void writeTo(OutputStream out) throws IOException, MessagingException;

    void writeHeaderTo(OutputStream out) throws IOException, MessagingException;

    String getServerExtra();

    void setServerExtra(String serverExtra);
}