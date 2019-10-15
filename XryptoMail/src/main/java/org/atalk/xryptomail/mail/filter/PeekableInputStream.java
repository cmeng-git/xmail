
package org.atalk.xryptomail.mail.filter;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * A filtering InputStream that allows single byte "peeks" without consuming the byte. The
 * client of this stream can call peek() to see the next available byte in the stream
 * and a subsequent read will still return the peeked byte.
 */
public class PeekableInputStream extends FilterInputStream {
    private boolean mPeeked;
    private int mPeekedByte;

    public PeekableInputStream(InputStream in) {
        super(in);
    }

    @Override
    public int read() throws IOException {
        if (!mPeeked) {
            return in.read();
        } else {
            mPeeked = false;
            return mPeekedByte;
        }
    }

    public int peek() throws IOException {
        if (!mPeeked) {
            mPeekedByte = in.read();
            mPeeked = true;
        }
        return mPeekedByte;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (!mPeeked) {
            return in.read(buffer, offset, length);
        } else {
            buffer[offset] = (byte) mPeekedByte;
            mPeeked = false;
            int r = in.read(buffer, offset + 1, length - 1);
            if (r == -1) {
                return 1;
            } else {
                return r + 1;
            }
        }
    }

    @Override
    public int read(byte[] buffer) throws IOException {
        return read(buffer, 0, buffer.length);
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "PeekableInputStream(in=%s, peeked=%b, peekedByte=%d)",
        		in.toString(), mPeeked, mPeekedByte);
    }
}
