package org.atalk.xryptomail.mail;

import org.apache.commons.io.IOUtils;
import org.apache.james.mime4j.util.MimeUtil;
import org.atalk.xryptomail.mail.internet.*;

import java.io.*;

public class DefaultBodyFactory implements BodyFactory {
    public Body createBody(String contentTransferEncoding, String contentType, InputStream inputStream)
            throws IOException {

        if (contentTransferEncoding != null) {
            contentTransferEncoding = MimeUtility.getHeaderParameter(contentTransferEncoding, null);
        }

        final BinaryTempFileBody tempBody;
        if (MimeUtil.isMessage(contentType)) {
            tempBody = new BinaryTempFileMessageBody(contentTransferEncoding);
        } else {
            tempBody = new BinaryTempFileBody(contentTransferEncoding);
        }

        OutputStream outputStream = tempBody.getOutputStream();
        try {
            copyData(inputStream, outputStream);
        } finally {
            outputStream.close();
        }

        return tempBody;
    }

    protected void copyData(InputStream inputStream, OutputStream outputStream) throws IOException {
        IOUtils.copy(inputStream, outputStream);
    }
}
