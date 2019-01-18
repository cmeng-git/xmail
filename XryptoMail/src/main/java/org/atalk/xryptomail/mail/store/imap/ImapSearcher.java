package org.atalk.xryptomail.mail.store.imap;

import java.io.IOException;
import java.util.List;

import org.atalk.xryptomail.mail.MessagingException;

interface ImapSearcher {
    List<ImapResponse> search() throws IOException, MessagingException;
}
