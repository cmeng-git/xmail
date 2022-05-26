package org.atalk.xryptomail.mailstore;

import org.atalk.xryptomail.helper.FileBackend;
import org.atalk.xryptomail.mail.Body;
import org.atalk.xryptomail.mail.MessagingException;
import org.atalk.xryptomail.mail.internet.RawDataBody;
import org.atalk.xryptomail.mail.internet.SizeAware;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class FileBackedBody implements Body, SizeAware, RawDataBody {
    private final File file;
    private final String encoding;

    public FileBackedBody(File file, String encoding) {
        this.file = file;
        this.encoding = encoding;
    }

    @Override
    public InputStream getInputStream() throws MessagingException {
        try {
            return new FileInputStream(file);
        } catch (FileNotFoundException e) {
            throw new MessagingException("File not found", e);
        }
    }

    @Override
    public void setEncoding(String encoding) throws MessagingException {
        throw new RuntimeException("not supported");
    }

    @Override
    public void writeTo(OutputStream out) throws IOException, MessagingException {
        try (InputStream in = getInputStream()) {
            FileBackend.copy(in, out);
        }
    }

    @Override
    public long getSize() {
        return file.length();
    }

    @Override
    public String getEncoding() {
        return encoding;
    }
}
