package org.atalk.xryptomail.activity;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.atalk.xryptomail.Account;
import org.atalk.xryptomail.BaseAccount;
import org.atalk.xryptomail.FontSizes;
import org.atalk.xryptomail.Preferences;
import org.atalk.xryptomail.R;
import org.atalk.xryptomail.XryptoMail;
import org.atalk.xryptomail.search.SearchAccount;

/**
 * Activity displaying the list of accounts.
 *
 * <p>
 * Classes extending this abstract class have to provide an {@link #onAccountSelected(BaseAccount)}
 * method to perform an action when an account is selected.
 * </p>
 */
public abstract class AccountList extends XMListActivity implements OnItemClickListener {
    private final FontSizes mFontSizes = XryptoMail.getFontSizes();

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setResult(RESULT_CANCELED);
        setContentView(R.layout.account_list);
        ListView listView = getListView();
        listView.setOnItemClickListener(this);
        listView.setItemsCanFocus(false);
    }

    /**
     * Reload list of accounts when this activity is resumed.
     */
    @Override
    public void onResume() {
        super.onResume();
        new LoadAccounts().execute();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        BaseAccount account = (BaseAccount) parent.getItemAtPosition(position);
        onAccountSelected(account);
    }

    /**
     * Create a new {@link AccountsAdapter} instance and assign it to the {@link ListView}.
     *
     * @param realAccounts An array of accounts to display.
     */
    public void populateListView(List<Account> realAccounts) {
        List<BaseAccount> accounts = new ArrayList<>();

        if (displaySpecialAccounts() && !XryptoMail.isHideSpecialAccounts()) {
            BaseAccount unifiedInboxAccount = SearchAccount.createUnifiedInboxAccount(this);
            BaseAccount allMessagesAccount = SearchAccount.createAllMessagesAccount(this);

            accounts.add(unifiedInboxAccount);
            accounts.add(allMessagesAccount);
        }

        accounts.addAll(realAccounts);
        AccountsAdapter adapter = new AccountsAdapter(accounts);
        ListView listView = getListView();
        listView.setAdapter(adapter);
        listView.invalidate();
    }

    /**
     * Implementing decide whether or not to display special accounts in the list.
     *
     * @return {@code true}, if special accounts should be listed. {@code false}, otherwise.
     */
    protected abstract boolean displaySpecialAccounts();

    /**
     * This method will be called when an account was selected.
     *
     * @param account The account the user selected.
     */
    protected abstract void onAccountSelected(BaseAccount account);

    class AccountsAdapter extends ArrayAdapter<BaseAccount> {
        public AccountsAdapter(List<BaseAccount> accounts) {
            super(AccountList.this, 0, accounts);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final BaseAccount account = getItem(position);

            final View view;
            if (convertView != null) {
                view = convertView;
            }
            else {
                view = getLayoutInflater().inflate(R.layout.accounts_item, parent, false);
                view.findViewById(R.id.active_icons).setVisibility(View.GONE);
                view.findViewById(R.id.folders).setVisibility(View.GONE);
            }

            AccountViewHolder holder = (AccountViewHolder) view.getTag();
            if (holder == null) {
                holder = new AccountViewHolder();
                holder.description = view.findViewById(R.id.description);
                holder.email = view.findViewById(R.id.email);
                holder.chip = view.findViewById(R.id.chip);
                view.setTag(holder);
            }

            String description = account.getDescription();
            if (account.getEmail().equals(description)) {
                holder.email.setVisibility(View.GONE);
            }
            else {
                holder.email.setVisibility(View.VISIBLE);
                holder.email.setText(account.getEmail());
            }

            if (description == null || description.isEmpty()) {
                description = account.getEmail();
            }

            holder.description.setText(description);

            if (account instanceof Account) {
                Account realAccount = (Account) account;
                holder.chip.setBackgroundColor(realAccount.getChipColor());
            }
            else {
                holder.chip.setBackgroundColor(0xff999999);
            }

            holder.chip.getBackground().setAlpha(255);

            mFontSizes.setViewTextSize(holder.description, mFontSizes.getAccountName());
            mFontSizes.setViewTextSize(holder.email, mFontSizes.getAccountDescription());
            return view;
        }

        class AccountViewHolder {
            public TextView description;
            public TextView email;
            public View chip;
        }
    }

    /**
     * Load accounts in a background thread
     */
    class LoadAccounts {
        public void execute() {
            try (ExecutorService eService = Executors.newSingleThreadExecutor()) {
                eService.execute(() -> {
                    final List<Account> accounts = Preferences.getPreferences(getApplicationContext()).getAccounts();

                    runOnUiThread(() -> populateListView(accounts));
                });
            }
        }
    }
}