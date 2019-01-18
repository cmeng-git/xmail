package org.atalk.xryptomail.mailstore;

public interface LocalPart {
    String getAccountUuid();
    long getPartId();
    long getSize();
    LocalMessage getMessage();
}
