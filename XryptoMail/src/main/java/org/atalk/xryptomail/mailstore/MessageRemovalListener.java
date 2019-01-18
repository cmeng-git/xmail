package org.atalk.xryptomail.mailstore;

import org.atalk.xryptomail.mail.Message;

public interface MessageRemovalListener {
    public void messageRemoved(Message message);
}
