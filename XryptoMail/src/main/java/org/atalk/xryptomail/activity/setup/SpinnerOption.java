/**
 *
 */

package org.atalk.xryptomail.activity.setup;

import android.widget.Spinner;

import androidx.annotation.NonNull;

public class SpinnerOption {
    public Object value;

    public String label;

    public static void setSpinnerOptionValue(Spinner spinner, Object value) {
        for (int i = 0, count = spinner.getCount(); i < count; i++) {
            SpinnerOption so = (SpinnerOption)spinner.getItemAtPosition(i);
            if (so.value.equals(value)) {
                spinner.setSelection(i, true);
                return;
            }
        }
    }

    public SpinnerOption(Object value, String label) {
        this.value = value;
        this.label = label;
    }

    @NonNull
    @Override
    public String toString() {
        return label;
    }
}
