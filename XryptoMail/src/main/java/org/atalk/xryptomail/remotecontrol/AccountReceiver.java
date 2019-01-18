package org.atalk.xryptomail.remotecontrol;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import timber.log.Timber;

class AccountReceiver extends BroadcastReceiver {
    XryptoMailAccountReceptor receptor = null;

    protected AccountReceiver(XryptoMailAccountReceptor nReceptor) {
        receptor = nReceptor;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (XryptoMailRemoteControl.CryptoMail_REQUEST_ACCOUNTS.equals(intent.getAction())) {
            Bundle bundle = getResultExtras(false);
            if (bundle == null) {
                Timber.w("Response bundle is empty");
                return;
            }
            receptor.accounts(bundle.getStringArray(XryptoMailRemoteControl.CryptoMail_ACCOUNT_UUIDS), bundle.getStringArray(XryptoMailRemoteControl.CryptoMail_ACCOUNT_DESCRIPTIONS));
        }
    }
}
