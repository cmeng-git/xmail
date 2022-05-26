package org.atalk.xryptomail.mailstore;

import org.apache.james.mime4j.codec.QuotedPrintableOutputStream;
import org.apache.james.mime4j.util.MimeUtil;
import org.atalk.xryptomail.helper.FileBackend;
import org.atalk.xryptomail.mail.Body;
import org.atalk.xryptomail.mail.MessagingException;
import org.atalk.xryptomail.mail.filter.Base64OutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Superclass for attachments that contain binary data.
 * The source for the data differs for the subclasses.
 */
abstract class BinaryAttachmentBody implements Body {
    protected String mEncoding;

    @Override
    public abstract InputStream getInputStream();

    @Override
    public void writeTo(OutputStream out) throws IOException, MessagingException {
        try (InputStream in = getInputStream()) {
            boolean closeStream = false;
            if (MimeUtil.isBase64Encoding(mEncoding)) {
                out = new Base64OutputStream(out);
                closeStream = true;
            } else if (MimeUtil.isQuotedPrintableEncoded(mEncoding)) {
                out = new QuotedPrintableOutputStream(out, false);
                closeStream = true;
            }

            try {
                FileBackend.copy(in, out);
            } finally {
                if (closeStream) {
                    out.close();
                }
            }
        }
    }

    @Override
    public void setEncoding(String encoding) throws MessagingException {
        mEncoding = encoding;
    }

    public String getEncoding() {
        return mEncoding;
    }
}
