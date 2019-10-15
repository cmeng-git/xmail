package org.atalk.xryptomail.mailstore.util;

import java.io.File;
import java.io.IOException;

public interface FileFactory {
    File createFile() throws IOException;
}
