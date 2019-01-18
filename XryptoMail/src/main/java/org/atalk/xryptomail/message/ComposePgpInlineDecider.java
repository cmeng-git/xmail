package org.atalk.xryptomail.message;

import org.atalk.xryptomail.crypto.MessageCryptoStructureDetector;
import org.atalk.xryptomail.mail.Message;
import org.atalk.xryptomail.mail.Part;

import java.util.List;

public class ComposePgpInlineDecider {
    public boolean shouldReplyInline(Message localMessage) {
        // TODO more criteria for this? maybe check the User-Agent header?
        return messageHasPgpInlineParts(localMessage);
    }

    private boolean messageHasPgpInlineParts(Message localMessage) {
        List<Part> inlineParts = MessageCryptoStructureDetector.findPgpInlineParts(localMessage);
        return !inlineParts.isEmpty();
    }
}
