package org.atalk.xryptomail.mailstore;

import org.atalk.xryptomail.mail.MessagingException;
import org.atalk.xryptomail.mail.internet.SizeAware;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * An attachment whose contents are contained in a file.
 */
public class TempFileBody extends BinaryAttachmentBody implements SizeAware {
    private final File mFile;

    public TempFileBody(String filename) {
        mFile = new File(filename);
    }

    @Override
    public InputStream getInputStream() {
        try {
            return new FileInputStream(mFile);
        } catch (FileNotFoundException e) {
            return new ByteArrayInputStream(LocalStore.EMPTY_BYTE_ARRAY);
        }
    }

    @Override
    public long getSize() {
        return mFile.length();
    }
}
