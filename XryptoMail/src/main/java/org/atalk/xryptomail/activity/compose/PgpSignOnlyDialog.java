package org.atalk.xryptomail.activity.compose;


import android.annotation.SuppressLint;
import android.app.*;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.view.LayoutInflater;
import android.view.View;

import org.atalk.xryptomail.R;
import org.atalk.xryptomail.view.HighlightDialogFragment;

public class PgpSignOnlyDialog extends HighlightDialogFragment
{
    public static final String ARG_FIRST_TIME = "first_time";


    public static PgpSignOnlyDialog newInstance(boolean firstTime, @IdRes int showcaseView)
    {
        PgpSignOnlyDialog dialog = new PgpSignOnlyDialog();

        Bundle args = new Bundle();
        args.putInt(ARG_FIRST_TIME, firstTime ? 1 : 0);
        args.putInt(ARG_HIGHLIGHT_VIEW, showcaseView);
        dialog.setArguments(args);

        return dialog;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        Activity activity = getActivity();

        @SuppressLint("InflateParams")
        View view = LayoutInflater.from(activity).inflate(R.layout.openpgp_sign_only_dialog, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setView(view);

        if (getArguments().getInt(ARG_FIRST_TIME) != 0) {
            builder.setPositiveButton(R.string.openpgp_sign_only_ok, (dialog, which) -> dialog.dismiss());
        }
        else {
            builder.setPositiveButton(R.string.openpgp_sign_only_disable, (dialog, which) -> {
                Activity activity1 = getActivity();
                if (activity1 == null) {
                    return;
                }
                ((OnOpenPgpSignOnlyChangeListener) activity1).onOpenPgpSignOnlyChange(false);
                dialog.dismiss();
            });
            builder.setNegativeButton(R.string.openpgp_sign_only_keep_enabled, (dialog, which) -> dialog.dismiss());
        }

        return builder.create();
    }

    public interface OnOpenPgpSignOnlyChangeListener
    {
        void onOpenPgpSignOnlyChange(boolean enabled);
    }
}
