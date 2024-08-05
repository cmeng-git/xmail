
package org.atalk.xryptomail.mail.internet;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import org.atalk.xryptomail.mail.BodyPart;
import org.atalk.xryptomail.mail.BoundaryGenerator;
import org.atalk.xryptomail.mail.MessagingException;
import org.atalk.xryptomail.mail.Multipart;

public class MimeMultipart extends Multipart {
    private String mMimeType;
    private byte[] mPreamble;
    private byte[] epilogue;
    private final String mBoundary;

    public static MimeMultipart newInstance() {
        String boundary = BoundaryGenerator.getInstance().generateBoundary();
        return new MimeMultipart(boundary);
    }

    public MimeMultipart(String boundary) {
        this("multipart/mixed", boundary);
    }

    public MimeMultipart(String mimeType, String boundary) {
        if (mimeType == null) {
            throw new IllegalArgumentException("mimeType can't be null");
        }
        if (boundary == null) {
            throw new IllegalArgumentException("boundary can't be null");
        }

        mMimeType = mimeType;
        mBoundary = boundary;
    }

    @Override
    public String getBoundary() {
        return mBoundary;
    }

    public byte[] getPreamble() {
        return mPreamble;
    }

    public void setPreamble(byte[] preamble) {
        mPreamble = preamble;
    }

    public byte[] getEpilogue() {
        return epilogue;
    }

    public void setEpilogue(byte[] epilogue) {
        this.epilogue = epilogue;
    }

    @Override
    public String getMimeType() {
        return mMimeType;
    }

    public void setSubType(String subType) {
        mMimeType = "multipart/" + subType;
    }

    @Override
    public void writeTo(OutputStream out) throws IOException, MessagingException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out), 1024);

        if (mPreamble != null) {
            out.write(mPreamble);
            writer.write("\r\n");
        }

        if (getBodyParts().isEmpty()) {
            writer.write("--");
            writer.write(mBoundary);
            writer.write("\r\n");
        }
        else {
            for (BodyPart bodyPart : getBodyParts()) {
                writer.write("--");
                writer.write(mBoundary);
                writer.write("\r\n");
                writer.flush();
                bodyPart.writeTo(out);
                writer.write("\r\n");
            }
        }

        writer.write("--");
        writer.write(mBoundary);
        writer.write("--\r\n");
        writer.flush();
        if (epilogue != null) {
            out.write(epilogue);
        }
    }

    @Override
    public InputStream getInputStream() throws MessagingException {
        throw new UnsupportedOperationException();
    }
}
