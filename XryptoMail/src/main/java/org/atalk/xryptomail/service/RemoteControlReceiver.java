
package org.atalk.xryptomail.service;

import android.content.*;
import android.os.Bundle;

import org.atalk.xryptomail.*;
import org.atalk.xryptomail.remotecontrol.XryptoMailRemoteControl;

import java.util.List;

import timber.log.Timber;

import static org.atalk.xryptomail.remotecontrol.XryptoMailRemoteControl.*;

public class RemoteControlReceiver extends CoreReceiver {
    @Override
    public Integer receive(Context context, Intent intent, Integer tmpWakeLockId) {
        Timber.i("RemoteControlReceiver.onReceive %s", intent);

        if (XryptoMailRemoteControl.CryptoMail_SET.equals(intent.getAction())) {
            RemoteControlService.set(context, intent);
            tmpWakeLockId = null;
        } else if (XryptoMailRemoteControl.CryptoMail_REQUEST_ACCOUNTS.equals(intent.getAction())) {
            try {
                Preferences preferences = Preferences.getPreferences(context);
                List<Account> accounts = preferences.getAccounts();
                String[] uuids = new String[accounts.size()];
                String[] descriptions = new String[accounts.size()];
                for (int i = 0; i < accounts.size(); i++) {
                    //warning: account may not be isAvailable()
                    Account account = accounts.get(i);

                    uuids[i] = account.getUuid();
                    descriptions[i] = account.getDescription();
                }
                Bundle bundle = getResultExtras(true);
                bundle.putStringArray(CryptoMail_ACCOUNT_UUIDS, uuids);
                bundle.putStringArray(CryptoMail_ACCOUNT_DESCRIPTIONS, descriptions);
            } catch (Exception e) {
                Timber.e(e, "Could not handle K9_RESPONSE_INTENT");
            }
        }
        return tmpWakeLockId;
    }
}
