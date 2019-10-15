package org.atalk.xryptomail.mailstore;

import org.atalk.xryptomail.mail.MessagingException;
import org.atalk.xryptomail.mail.internet.MimeBodyPart;

public class LocalBodyPart extends MimeBodyPart implements LocalPart {
    private final String accountUuid;
    private final LocalMessage message;
    private final long messagePartId;
    private final long size;

    public LocalBodyPart(String accountUuid, LocalMessage message, long messagePartId, long size)
            throws MessagingException {
        super();
        this.accountUuid = accountUuid;
        this.message = message;
        this.messagePartId = messagePartId;
        this.size = size;
    }

    @Override
    public String getAccountUuid() {
        return accountUuid;
    }

    @Override
    public long getPartId() {
        return messagePartId;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public LocalMessage getMessage() {
        return message;
    }
}
