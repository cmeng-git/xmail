package org.atalk.xryptomail.mail.store.imap;

import org.atalk.xryptomail.mail.AuthType;
import org.atalk.xryptomail.mail.ConnectionSecurity;
import org.atalk.xryptomail.mail.NetworkType;

/**
 * Settings source for IMAP. Implemented in order to remove coupling between {@link ImapStore} and {@link ImapConnection}.
 */
interface ImapSettings {
    String getHost();

    int getPort();

    ConnectionSecurity getConnectionSecurity();

    AuthType getAuthType();

    String getUsername();

    String getPassword();

    String getClientCertificateAlias();

    boolean useCompression(NetworkType type);

    String getPathPrefix();

    void setPathPrefix(String prefix);

    String getPathDelimiter();

    void setPathDelimiter(String delimiter);

    String getCombinedPrefix();

    void setCombinedPrefix(String prefix);
}
