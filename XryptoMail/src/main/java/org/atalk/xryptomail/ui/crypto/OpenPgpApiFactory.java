package org.atalk.xryptomail.ui.crypto;

import android.content.Context;

import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.IOpenPgpService2;

public class OpenPgpApiFactory {
    public OpenPgpApi createOpenPgpApi(Context context, IOpenPgpService2 service) {
        return new OpenPgpApi(context, service);
    }
}
