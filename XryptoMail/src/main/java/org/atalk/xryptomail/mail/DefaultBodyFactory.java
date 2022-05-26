package org.atalk.xryptomail.mail;

import org.apache.james.mime4j.util.MimeUtil;
import org.atalk.xryptomail.helper.FileBackend;
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

        try (OutputStream outputStream = tempBody.getOutputStream()) {
            copyData(inputStream, outputStream);
        }
        return tempBody;
    }

    protected void copyData(InputStream inputStream, OutputStream outputStream) throws IOException {
        FileBackend.copy(inputStream, outputStream);
    }
}
