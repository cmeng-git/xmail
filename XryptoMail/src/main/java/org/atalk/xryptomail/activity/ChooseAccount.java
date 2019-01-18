package org.atalk.xryptomail.activity;

import android.content.Intent;

import org.atalk.xryptomail.BaseAccount;

public class ChooseAccount extends AccountList {

    public static final String EXTRA_ACCOUNT_UUID = "org.atalk.xryptomail.ChooseAccount_account_uuid";

    @Override
    protected boolean displaySpecialAccounts() {
        return true;
    }

    @Override
    protected void onAccountSelected(BaseAccount account) {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_ACCOUNT_UUID, account.getUuid());
        setResult(RESULT_OK, intent);
        finish();
    }
}
