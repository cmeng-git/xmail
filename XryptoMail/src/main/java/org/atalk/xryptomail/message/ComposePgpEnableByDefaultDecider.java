package org.atalk.xryptomail.message;

import java.util.List;

import org.atalk.xryptomail.crypto.MessageCryptoStructureDetector;
import org.atalk.xryptomail.mail.Message;
import org.atalk.xryptomail.mail.Part;

public class ComposePgpEnableByDefaultDecider {
    public boolean shouldEncryptByDefault(Message localMessage) {
        return messageIsEncrypted(localMessage);
    }

    private boolean messageIsEncrypted(Message localMessage) {
        List<Part> encryptedParts = MessageCryptoStructureDetector.findMultipartEncryptedParts(localMessage);
        return !encryptedParts.isEmpty();
    }
}
