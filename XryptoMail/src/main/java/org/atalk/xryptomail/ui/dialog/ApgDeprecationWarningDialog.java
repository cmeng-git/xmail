package org.atalk.xryptomail.ui.dialog;

import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.text.method.LinkMovementMethod;
import android.view.*;
import android.widget.TextView;

import org.atalk.xryptomail.R;

public class ApgDeprecationWarningDialog extends AlertDialog {
    public ApgDeprecationWarningDialog(Context context) {
        super(context);

        LayoutInflater inflater = LayoutInflater.from(context);

        @SuppressLint("InflateParams")
        View contentView = inflater.inflate(R.layout.dialog_apg_deprecated, null);

        TextView textViewLearnMore = contentView.findViewById(R.id.apg_learn_more);
        makeTextViewLinksClickable(textViewLearnMore);

        setIcon(R.drawable.ic_apg_small);
        setTitle(R.string.apg_deprecated_title);
        setView(contentView);
        setButton(Dialog.BUTTON_NEUTRAL, context.getString(R.string.apg_deprecated_ok), new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                cancel();
            }
        });
    }

    private void makeTextViewLinksClickable(TextView textView) {
        textView.setMovementMethod(LinkMovementMethod.getInstance());
    }
}
