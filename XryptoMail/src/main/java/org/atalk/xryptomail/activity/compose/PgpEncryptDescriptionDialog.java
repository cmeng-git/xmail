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

public class PgpEncryptDescriptionDialog extends HighlightDialogFragment
{
    public static PgpEncryptDescriptionDialog newInstance(@IdRes int showcaseView)
    {
        PgpEncryptDescriptionDialog dialog = new PgpEncryptDescriptionDialog();

        Bundle args = new Bundle();
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
        View view = LayoutInflater.from(activity).inflate(R.layout.openpgp_encrypt_description_dialog, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setView(view);

        builder.setPositiveButton(R.string.openpgp_sign_only_ok, (dialog, which) -> dialog.dismiss());

        return builder.create();
    }
}
