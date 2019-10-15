package org.atalk.xryptomail.mail.store;

import org.atalk.xryptomail.mail.NetworkType;

public interface StoreConfig
{
    String getStoreUri();

    String getTransportUri();
    void setStoreUri(String storeUri);
    void setTransportUri(String transportUri);

    boolean isSubscribedFoldersOnly();

    boolean useCompression(NetworkType type);

    String getInboxFolder();

    String getOutboxFolderName();

    String getDraftsFolderName();

    void setArchiveFolderName(String name);

    void setDraftsFolder(String name);

    void setTrashFolder(String name);

    void setSpamFolderName(String name);

    void setSentFolder(String name);

    void setAutoExpandFolder(String name);

    void setInboxFolder(String name);

    int getMaximumAutoDownloadMessageSize();

    boolean isAllowRemoteSearch();

    boolean isRemoteSearchFullText();

    boolean isPushPollOnConnect();

    int getDisplayCount();

    int getIdleRefreshMinutes();

    boolean shouldHideHostname();
}
