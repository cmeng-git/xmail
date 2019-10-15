
package org.atalk.xryptomail.mail.internet;

import androidx.annotation.Nullable;

import org.apache.james.mime4j.codec.QuotedPrintableOutputStream;
import org.apache.james.mime4j.util.MimeUtil;
import org.atalk.xryptomail.mail.*;
import org.atalk.xryptomail.mail.filter.*;

import java.io.*;

import timber.log.Timber;

public class TextBody implements Body, SizeAware {
    /**
     * Immutable empty byte array
     */
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    private final String mText;
    private String mEncoding;
    private String mCharset = "UTF-8";
    // Length of the message composed (as opposed to quoted). I don't like the name of this variable and am open to
    // suggestions as to what it should otherwise be. -achen 20101207
    @Nullable
    private Integer mComposedMessageLength;
    // Offset from position 0 where the composed message begins.
    @Nullable
    private Integer mComposedMessageOffset;

    public TextBody(String body) {
        mText = body;
    }

    @Override
    public void writeTo(OutputStream out) throws IOException, MessagingException {
        if (mText != null) {
            byte[] bytes = mText.getBytes(mCharset);
            if (MimeUtil.ENC_QUOTED_PRINTABLE.equalsIgnoreCase(mEncoding)) {
                writeSignSafeQuotedPrintable(out, bytes);
            } else if (MimeUtil.ENC_8BIT.equalsIgnoreCase(mEncoding)) {
                out.write(bytes);
            } else {
                throw new IllegalStateException("Cannot get size for encoding!");
            }
        }
    }

    /**
     * Get the text of the body in it's unencoded format.
     * @return
     */
    public String getRawText() {
        return mText;
    }

    /**
     * Returns an InputStream that reads this body's text.
     */
    @Override
    public InputStream getInputStream() throws MessagingException {
        try {
            byte[] b;
            if (mText != null) {
                b = mText.getBytes(mCharset);
            } else {
                b = EMPTY_BYTE_ARRAY;
            }
            return new ByteArrayInputStream(b);
        } catch (UnsupportedEncodingException uee) {
            Timber.e(uee, "Unsupported charset: %s", mCharset);
            return null;
        }
    }

    @Override
    public void setEncoding(String encoding) {
        boolean isSupportedEncoding = MimeUtil.ENC_QUOTED_PRINTABLE.equalsIgnoreCase(encoding) ||
                MimeUtil.ENC_8BIT.equalsIgnoreCase(encoding);
        if (!isSupportedEncoding) {
            throw new IllegalArgumentException("Cannot encode to " + encoding);
        }
        mEncoding = encoding;
    }

    public void setCharset(String charset) {
        mCharset = charset;
    }

    @Nullable
    public Integer getComposedMessageLength() {
        return mComposedMessageLength;
    }

    public void setComposedMessageLength(@Nullable Integer composedMessageLength) {
        mComposedMessageLength = composedMessageLength;
    }

    @Nullable
    public Integer getComposedMessageOffset() {
        return mComposedMessageOffset;
    }

    public void setComposedMessageOffset(@Nullable Integer composedMessageOffset) {
        mComposedMessageOffset = composedMessageOffset;
    }

    @Override
    public long getSize() {
        try {
            byte[] bytes = mText.getBytes(mCharset);

            if (MimeUtil.ENC_QUOTED_PRINTABLE.equalsIgnoreCase(mEncoding)) {
                return getLengthWhenQuotedPrintableEncoded(bytes);
            } else if (MimeUtil.ENC_8BIT.equalsIgnoreCase(mEncoding)) {
                return bytes.length;
            } else {
                throw new IllegalStateException("Cannot get size for encoding!");
            }
        } catch (IOException e) {
            throw new RuntimeException("Couldn't get body size", e);
        }
    }

    private long getLengthWhenQuotedPrintableEncoded(byte[] bytes) throws IOException {
        CountingOutputStream countingOutputStream = new CountingOutputStream();
        writeSignSafeQuotedPrintable(countingOutputStream, bytes);
        return countingOutputStream.getCount();
    }

    private void writeSignSafeQuotedPrintable(OutputStream out, byte[] bytes) throws IOException {
        SignSafeOutputStream signSafeOutputStream = new SignSafeOutputStream(out);
        try {
            QuotedPrintableOutputStream signSafeQuotedPrintableOutputStream =
                    new QuotedPrintableOutputStream(signSafeOutputStream, false);
            try {
                signSafeQuotedPrintableOutputStream.write(bytes);
            } finally {
                signSafeQuotedPrintableOutputStream.close();
            }
        } finally {
            signSafeOutputStream.close();
        }
    }

    public String getEncoding() {
        return mEncoding;
    }
}
