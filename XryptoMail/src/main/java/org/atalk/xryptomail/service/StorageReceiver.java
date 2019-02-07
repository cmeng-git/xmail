package org.atalk.xryptomail.service;

import android.content.*;
import android.net.Uri;

import org.atalk.xryptomail.mailstore.StorageManager;

import timber.log.Timber;

/**
 * That BroadcastReceiver is only interested in MOUNT events.
 */
public class StorageReceiver extends BroadcastReceiver
{

    @Override
    public void onReceive(final Context context, final Intent intent)
    {
        final String action = intent.getAction();
        final Uri uri = intent.getData();

        if ((uri == null) || (uri.getPath() == null)) {
            return;
        }

        Timber.v("StorageReceiver: %s", intent);
        final String path = uri.getPath();

        if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
            StorageManager.getInstance(context).onMount(path,
                    intent.getBooleanExtra("read-only", true));
        }
    }
}
