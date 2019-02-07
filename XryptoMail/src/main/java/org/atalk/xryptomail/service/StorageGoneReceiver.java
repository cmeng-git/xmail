package org.atalk.xryptomail.service;

import android.content.*;
import android.net.Uri;
import timber.log.Timber;
import org.atalk.xryptomail.mailstore.StorageManager;

/**
 * That BroadcastReceiver is only interested in UNMOUNT events.
 *
 * <p>
 * Code was separated from {@link StorageReceiver} because we don't want that
 * receiver to be statically defined in manifest.
 * </p>
 */
public class StorageGoneReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final String action = intent.getAction();
        final Uri uri = intent.getData();

        if (uri == null || uri.getPath() == null) {
            return;
        }

        Timber.v("StorageGoneReceiver: %s", intent);
        final String path = uri.getPath();

        if (Intent.ACTION_MEDIA_EJECT.equals(action)) {
            StorageManager.getInstance(context).onBeforeUnmount(path);
        } else if (Intent.ACTION_MEDIA_UNMOUNTED.equals(action)) {
            StorageManager.getInstance(context).onAfterUnmount(path);
        }
    }

}
