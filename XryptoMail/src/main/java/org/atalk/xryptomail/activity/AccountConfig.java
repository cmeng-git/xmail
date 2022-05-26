package org.atalk.xryptomail.activity;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import org.atalk.xryptomail.Account.DeletePolicy;
import org.atalk.xryptomail.Account.FolderMode;
import org.atalk.xryptomail.activity.setup.CheckDirection;
import org.atalk.xryptomail.mail.AuthType;
import org.atalk.xryptomail.mail.ConnectionSecurity;
import org.atalk.xryptomail.mail.MessagingException;
import org.atalk.xryptomail.mail.NetworkType;
import org.atalk.xryptomail.mail.Store;
import org.atalk.xryptomail.mail.store.StoreConfig;

public interface AccountConfig extends StoreConfig {
    ConnectionSecurity getIncomingSecurityType();
    AuthType getIncomingAuthType();
    String getIncomingPort();
    ConnectionSecurity getOutgoingSecurityType();
    AuthType getOutgoingAuthType();
    String getOutgoingPort();
    boolean isNotifyNewMail();
    boolean isShowOngoing();
    int getAutomaticCheckIntervalMinutes();
    int getDisplayCount();
    FolderMode getFolderPushMode();
    String getName();
    DeletePolicy getDeletePolicy();

    void init(String email, String password);

    String getEmail();
    String getDescription();
    Store getRemoteStore() throws MessagingException;

    void setName(String name);
    void setDescription(String description);
    void setDeletePolicy(DeletePolicy deletePolicy);
    void setEmail(String email);
    void setCompression(NetworkType networkType, boolean useCompression);

    void addCertificate(CheckDirection direction, X509Certificate certificate) throws CertificateException;

    void setSubscribedFoldersOnly(boolean subscribedFoldersOnly);

    void deleteCertificate(String newHost, int newPort, CheckDirection direction);
}
