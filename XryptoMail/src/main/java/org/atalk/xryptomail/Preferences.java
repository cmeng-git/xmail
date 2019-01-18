package org.atalk.xryptomail;

import android.content.Context;
import android.util.Log;

import org.atalk.xryptomail.mail.store.RemoteStore;
import org.atalk.xryptomail.mailstore.LocalStore;
import org.atalk.xryptomail.preferences.Storage;
import org.atalk.xryptomail.preferences.StorageEditor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import timber.log.Timber;
public class Preferences {

    /**
     * Immutable empty {@link Account} array
     */
    private static Preferences preferences;

    public static synchronized Preferences getPreferences(Context context) {
        Context appContext = context.getApplicationContext();
        if (preferences == null) {
            preferences = new Preferences(appContext);
        }
        return preferences;
    }

    private Storage mStorage;
    private Map<String, Account> accounts = null;
    private List<Account> accountsInOrder = null;
    private Account newAccount;
    private Context mContext;

    private Preferences(Context context) {
        mStorage = Storage.getStorage(context);
        mContext = context;
        if (mStorage.isEmpty()) {
            Timber.i("Preferences storage is zero-size, importing from Android-style preferences");
            StorageEditor editor = mStorage.edit();
            editor.copy(context.getSharedPreferences("AndroidMail.Main", Context.MODE_PRIVATE));
            editor.commit();
        }
    }

    public synchronized void loadAccounts() {
        accounts = new HashMap<>();
        accountsInOrder = new LinkedList<>();
        String accountUuids = getStorage().getString("accountUuids", null);
        if ((accountUuids != null) && (accountUuids.length() != 0)) {
            String[] uuids = accountUuids.split(",");
            for (String uuid : uuids) {
                Account newAccount = new Account(this, uuid);
                accounts.put(uuid, newAccount);
                accountsInOrder.add(newAccount);
            }
        }
        if ((newAccount != null) && newAccount.getAccountNumber() != -1) {
            accounts.put(newAccount.getUuid(), newAccount);
            if (!accountsInOrder.contains(newAccount)) {
                accountsInOrder.add(newAccount);
            }
            newAccount = null;
        }
    }

    /**
     * Returns an array of the accounts on the system. If no accounts are
     * registered the method returns an empty array.
     *
     * @return all accounts
     */
    public synchronized List<Account> getAccounts() {
        if (accounts == null) {
            loadAccounts();
        }
        return Collections.unmodifiableList(new ArrayList<>(accountsInOrder));
    }

    /**
     * Returns an array of the accounts on the system. If no accounts are
     * registered the method returns an empty array.
     * @return all accounts with {@link Account#isAvailable(Context)}
     */
    public synchronized Collection<Account> getAvailableAccounts() {
        List<Account> allAccounts = getAccounts();
        Collection<Account> retval = new ArrayList<>(accounts.size());
        for (Account account : allAccounts) {
            if (account.isEnabled() && account.isAvailable(mContext)) {
                retval.add(account);
            }
        }
        return retval;
    }

    public synchronized Account getAccount(String uuid) {
        if (accounts == null) {
            loadAccounts();
        }
        return accounts.get(uuid);
    }

    public synchronized Account newAccount() {
        newAccount = new Account(mContext);
        accounts.put(newAccount.getUuid(), newAccount);
        accountsInOrder.add(newAccount);
        return newAccount;
    }

    public synchronized void deleteAccount(Account account) {
        if (accounts != null) {
            accounts.remove(account.getUuid());
        }
        if (accountsInOrder != null) {
            accountsInOrder.remove(account);
        }

        try {
            RemoteStore.removeInstance(account);
        } catch (Exception e) {
            Timber.e(e, "Failed to reset remote store for account %s", account.getUuid());
        }
        LocalStore.removeAccount(account);
        account.delete(this);

        if (newAccount == account) {
            newAccount = null;
        }
    }

    /**
     * Returns the Account marked as default. If no account is marked as default
     * the first account in the list is marked as default and then returned. If
     * there are no accounts on the system the method returns null.
     */
    public Account getDefaultAccount() {
        String defaultAccountUuid = getStorage().getString("defaultAccountUuid", null);
        Account defaultAccount = getAccount(defaultAccountUuid);

        if (defaultAccount == null) {
            Collection<Account> accounts = getAvailableAccounts();
            if (!accounts.isEmpty()) {
                defaultAccount = accounts.iterator().next();
                setDefaultAccount(defaultAccount);
            }
        }
        return defaultAccount;
    }

    public void setDefaultAccount(Account account) {
        getStorage().edit().putString("defaultAccountUuid", account.getUuid()).commit();
    }

    public Storage getStorage() {
        return mStorage;
    }

    public static <T extends Enum<T>> T getEnumStringPref(Storage storage, String key, T defaultEnum) {
        String stringPref = storage.getString(key, null);

        if (stringPref == null) {
            return defaultEnum;
        } else {
            try {
                return Enum.valueOf(defaultEnum.getDeclaringClass(), stringPref);
            } catch (IllegalArgumentException ex) {
                Timber.w(ex, "Unable to convert preference key [%s] value [%s] to enum of type %s",
                        key, stringPref, defaultEnum.getDeclaringClass());

                return defaultEnum;
            }
        }
    }
}
