package org.atalk.xryptomail.preferences;

import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.preference.DialogPreference;
import android.util.AttributeSet;

public class CheckBoxListPreference extends DialogPreference {
    private CharSequence[] mItems;
    private boolean[] mCheckedItems;

    /**
     * checkboxes state when the dialog is displayed
     */
    private boolean[] mPendingItems;

    public CheckBoxListPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public CheckBoxListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onPrepareDialogBuilder(final Builder builder) {
        mPendingItems = new boolean[mItems.length];
        System.arraycopy(mCheckedItems, 0, mPendingItems, 0, mCheckedItems.length);

        builder.setMultiChoiceItems(mItems, mPendingItems,
                (dialog, which, isChecked) -> mPendingItems[which] = isChecked);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            System.arraycopy(mPendingItems, 0, mCheckedItems, 0, mPendingItems.length);
        }
        mPendingItems = null;
    }

    public void setItems(final CharSequence[] items) {
        mItems = items;
    }

    public void setCheckedItems(final boolean[] items) {
        mCheckedItems = items;
    }

    public boolean[] getCheckedItems() {
        return mCheckedItems;
    }
}
