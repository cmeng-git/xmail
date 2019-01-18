package org.atalk.xryptomail.mail.internet;

import org.atalk.xryptomail.mail.Body;

/**
 * See {@link MimeUtility#decodeBody(Body)}
 */
public interface RawDataBody extends Body {
    String getEncoding();
}
