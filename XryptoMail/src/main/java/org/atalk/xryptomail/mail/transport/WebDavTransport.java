
package org.atalk.xryptomail.mail.transport;

import org.atalk.xryptomail.mail.Message;
import org.atalk.xryptomail.mail.MessagingException;
import org.atalk.xryptomail.mail.Transport;
import org.atalk.xryptomail.mail.XryptoMailLib;
import org.atalk.xryptomail.mail.store.StoreConfig;
import org.atalk.xryptomail.mail.store.webdav.WebDavHttpClient;
import org.atalk.xryptomail.mail.store.webdav.WebDavStore;

import java.util.Collections;

import timber.log.Timber;

public class WebDavTransport extends Transport
{
    private WebDavStore store;

    public WebDavTransport(StoreConfig storeConfig)
            throws MessagingException
    {
        store = new WebDavStore(storeConfig, new WebDavHttpClient.WebDavHttpClientFactory());
        if (XryptoMailLib.isDebug())
            Timber.d(">>> New WebDavTransport creation complete");
    }

    @Override
    public void open()
            throws MessagingException
    {
        if (XryptoMailLib.isDebug())
            Timber.d(">>> open called on WebDavTransport ");

        store.getHttpClient();
    }

    @Override
    public void close()
    {
    }

    @Override
    public void sendMessage(Message message)
            throws MessagingException
    {
        store.sendMessages(Collections.singletonList(message));
    }

    public void checkSettings() throws MessagingException {
        store.checkSettings();
    }
}
