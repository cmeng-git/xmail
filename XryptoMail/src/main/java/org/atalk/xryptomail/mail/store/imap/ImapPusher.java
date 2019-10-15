package org.atalk.xryptomail.mail.store.imap;

import org.atalk.xryptomail.mail.*;
import timber.log.Timber;
import java.util.*;

class ImapPusher implements Pusher {
    private final ImapStore store;
    private final PushReceiver pushReceiver;

    private final List<ImapFolderPusher> folderPushers = new ArrayList<>();
    private long lastRefresh = -1;

    public ImapPusher(ImapStore store, PushReceiver pushReceiver) {
        this.store = store;
        this.pushReceiver = pushReceiver;
    }

    @Override
    public void start(List<String> folderNames) {
        synchronized (folderPushers) {
            stop();
            setLastRefresh(currentTimeMillis());

            for (String folderName : folderNames) {
                ImapFolderPusher pusher = createImapFolderPusher(folderName);
                folderPushers.add(pusher);
                pusher.start();
            }
        }
    }

    @Override
    public void refresh() {
        synchronized (folderPushers) {
            for (ImapFolderPusher folderPusher : folderPushers) {
                try {
                    folderPusher.refresh();
                } catch (Exception e) {
                    Timber.e(e, "Got exception while refreshing for %s", folderPusher.getServerId());
                }
            }
        }
    }

    @Override
    public void stop() {
        if (XryptoMailLib.isDebug()) {
            Timber.i("Requested stop of IMAP pusher");
        }

        synchronized (folderPushers) {
            for (ImapFolderPusher folderPusher : folderPushers) {
                try {
                    if (XryptoMailLib.isDebug()) {
                        Timber.i("Requesting stop of IMAP folderPusher %s", folderPusher.getServerId());
                    }

                    folderPusher.stop();
                } catch (Exception e) {
                    Timber.e(e, "Got exception while stopping %s", folderPusher.getServerId());
                }
            }
            folderPushers.clear();
        }
    }

    @Override
    public int getRefreshInterval() {
        return (store.getStoreConfig().getIdleRefreshMinutes() * 60 * 1000);
    }

    @Override
    public long getLastRefresh() {
        return lastRefresh;
    }

    @Override
    public void setLastRefresh(long lastRefresh) {
        this.lastRefresh = lastRefresh;
    }

    ImapFolderPusher createImapFolderPusher(String folderName) {
        return new ImapFolderPusher(store, folderName, pushReceiver);
    }

    long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
