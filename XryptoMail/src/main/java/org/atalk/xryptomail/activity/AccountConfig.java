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
    public ConnectionSecurity getIncomingSecurityType();
    public AuthType getIncomingAuthType();
    public String getIncomingPort();
    public ConnectionSecurity getOutgoingSecurityType();
    public AuthType getOutgoingAuthType();
    public String getOutgoingPort();
    public boolean isNotifyNewMail();
    public boolean isShowOngoing();
    public int getAutomaticCheckIntervalMinutes();
    public int getDisplayCount();
    public FolderMode getFolderPushMode();
    public String getName();
    DeletePolicy getDeletePolicy();

    void init(String email, String password);

    public String getEmail();
    public String getDescription();
    Store getRemoteStore() throws MessagingException;

    public void setName(String name);
    public void setDescription(String description);
    public void setDeletePolicy(DeletePolicy deletePolicy);
    public void setEmail(String email);
    void setCompression(NetworkType networkType, boolean useCompression);

    void addCertificate(CheckDirection direction, X509Certificate certificate) throws CertificateException;

    void setSubscribedFoldersOnly(boolean subscribedFoldersOnly);

    void deleteCertificate(String newHost, int newPort, CheckDirection direction);
}
