package org.atalk.xryptomail.mailstore.migrations;

import android.content.Context;

import org.atalk.xryptomail.Account;
import org.atalk.xryptomail.mail.Flag;
import org.atalk.xryptomail.mailstore.LocalStore;
import org.atalk.xryptomail.preferences.Storage;

import java.util.List;

/**
 * Helper to allow accessing classes and methods that aren't visible or accessible to the 'migrations' package
 */
public interface MigrationsHelper {
    LocalStore getLocalStore();
    Storage getStorage();
    Account getAccount();
    Context getContext();
    String serializeFlags(List<Flag> flags);
}
