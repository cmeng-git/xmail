package org.atalk.xryptomail.activity.compose;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;

import android.view.LayoutInflater;
import android.view.View;

import org.atalk.xryptomail.R;
import org.atalk.xryptomail.view.HighlightDialogFragment;

public class PgpInlineDialog extends HighlightDialogFragment
{
    public static final String ARG_FIRST_TIME = "first_time";

    public static PgpInlineDialog newInstance(boolean firstTime, @IdRes int showcaseView)
    {
        PgpInlineDialog dialog = new PgpInlineDialog();

        Bundle args = new Bundle();
        args.putInt(ARG_FIRST_TIME, firstTime ? 1 : 0);
        args.putInt(ARG_HIGHLIGHT_VIEW, showcaseView);
        dialog.setArguments(args);

        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        Activity activity = getActivity();

        @SuppressLint("InflateParams")
        View view = LayoutInflater.from(activity).inflate(R.layout.openpgp_inline_dialog, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setView(view);

        if (getArguments().getInt(ARG_FIRST_TIME) != 0) {
            builder.setPositiveButton(R.string.openpgp_inline_ok, (dialog, which) -> dialog.dismiss());
        }
        else {
            builder.setPositiveButton(R.string.openpgp_inline_disable, (dialog, which) -> {
                Activity activity1 = getActivity();
                if (activity1 == null) {
                    return;
                }

                ((OnOpenPgpInlineChangeListener) activity1).onOpenPgpInlineChange(false);
                dialog.dismiss();
            });
            builder.setNegativeButton(R.string.openpgp_inline_keep_enabled, (dialog, which) -> dialog.dismiss());
        }

        return builder.create();
    }

    public interface OnOpenPgpInlineChangeListener
    {
        void onOpenPgpInlineChange(boolean enabled);
    }

}
