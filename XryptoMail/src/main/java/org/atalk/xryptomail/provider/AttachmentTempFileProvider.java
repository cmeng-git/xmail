package org.atalk.xryptomail.provider;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.AsyncTask;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.content.FileProvider;

import org.apache.commons.io.IOUtils;
import org.atalk.xryptomail.BuildConfig;
import org.atalk.xryptomail.XryptoMail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import okio.ByteString;
import timber.log.Timber;

public class AttachmentTempFileProvider extends FileProvider
{
    private static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".tempfileprovider";
    private static final String CACHE_DIRECTORY = "temp";
    private static final long FILE_DELETE_THRESHOLD_MILLISECONDS = 3 * 60 * 1000;
    private static final Object tempFileWriteMonitor = new Object();
    private static final Object cleanupReceiverMonitor = new Object();

    private static AttachmentTempFileProviderCleanupReceiver cleanupReceiver = null;

    @WorkerThread
    public static Uri createTempUriForContentUri(Context context, Uri uri)
            throws IOException
    {
        Context applicationContext = context.getApplicationContext();

        File tempFile = getTempFileForUri(uri, applicationContext);
        writeUriContentToTempFileIfNotExists(context, uri, tempFile);
        Uri tempFileUri = FileProvider.getUriForFile(context, AUTHORITY, tempFile);

        registerFileCleanupReceiver(applicationContext);

        return tempFileUri;
    }

    @NonNull
    private static File getTempFileForUri(Uri uri, Context context)
    {
        Context applicationContext = context.getApplicationContext();

        String tempFilename = getTempFilenameForUri(uri);
        File tempDirectory = getTempFileDirectory(applicationContext);
        return new File(tempDirectory, tempFilename);
    }

    private static String getTempFilenameForUri(Uri uri)
    {
        return ByteString.encodeUtf8(uri.toString()).sha1().hex();
    }

    private static void writeUriContentToTempFileIfNotExists(Context context, Uri uri, File tempFile)
            throws IOException
    {
        synchronized (tempFileWriteMonitor) {
            if (tempFile.exists()) {
                return;
            }

            FileOutputStream outputStream = new FileOutputStream(tempFile);
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                throw new IOException("Failed to resolve content at uri: " + uri);
            }
            IOUtils.copy(inputStream, outputStream);

            outputStream.close();
            IOUtils.closeQuietly(inputStream);
        }
    }

    public static Uri getMimeTypeUri(Uri contentUri, String mimeType)
    {
        if (!AUTHORITY.equals(contentUri.getAuthority())) {
            throw new IllegalArgumentException("Can only call this method for URIs within this authority!");
        }
        if (contentUri.getQueryParameter("mime_type") != null) {
            throw new IllegalArgumentException("Can only call this method for not yet typed " + "URIs!");
        }
        return contentUri.buildUpon().appendQueryParameter("mime_type", mimeType).build();
    }

    public static boolean deleteOldTemporaryFiles(Context context)
    {
        File tempDirectory = getTempFileDirectory(context);
        boolean allFilesDeleted = true;
        long deletionThreshold = System.currentTimeMillis() - FILE_DELETE_THRESHOLD_MILLISECONDS;
        for (File tempFile : tempDirectory.listFiles()) {
            long lastModified = tempFile.lastModified();
            if (lastModified < deletionThreshold) {
                boolean fileDeleted = tempFile.delete();
                if (!fileDeleted) {
                    Timber.e("Failed to delete temporary file");
                    // TODO really do this? might cause our service to stay up indefinitely if a
                    // file can't be deleted
                    allFilesDeleted = false;
                }
            }
            else {
                if (XryptoMail.isDebug()) {
                    String timeLeftStr = String.format(Locale.ENGLISH, "%.2f",
                            (lastModified - deletionThreshold) / 1000 / 60.0);
                    Timber.e("Not deleting temp file (for another %s minutes)", timeLeftStr);
                }
                allFilesDeleted = false;
            }
        }

        return allFilesDeleted;
    }

    private static File getTempFileDirectory(Context context)
    {
        File directory = new File(context.getCacheDir(), CACHE_DIRECTORY);
        if (!directory.exists()) {
            if (!directory.mkdir()) {
                Timber.e("Error creating directory: %s", directory.getAbsolutePath());
            }
        }

        return directory;
    }


    @Override
    public String getType(Uri uri)
    {
        return uri.getQueryParameter("mime_type");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onTrimMemory(int level)
    {
        if (level < TRIM_MEMORY_COMPLETE) {
            return;
        }
        final Context context = getContext();
        if (context == null) {
            return;
        }

        new AsyncTask<Void, Void, Void>()
        {
            @Override
            protected Void doInBackground(Void... voids)
            {
                deleteOldTemporaryFiles(context);
                return null;
            }
        }.execute();

        unregisterFileCleanupReceiver(context);
    }

    private static void unregisterFileCleanupReceiver(Context context)
    {
        synchronized (cleanupReceiverMonitor) {
            if (cleanupReceiver == null) {
                return;
            }
            Timber.d("Unregistering temp file cleanup receiver");
            context.unregisterReceiver(cleanupReceiver);
            cleanupReceiver = null;
        }
    }

    private static void registerFileCleanupReceiver(Context context)
    {
        synchronized (cleanupReceiverMonitor) {
            if (cleanupReceiver != null) {
                return;
            }

            Timber.d("Registering temp file cleanup receiver");
            cleanupReceiver = new AttachmentTempFileProviderCleanupReceiver();

            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
            context.registerReceiver(cleanupReceiver, intentFilter);
        }
    }

    private static class AttachmentTempFileProviderCleanupReceiver extends BroadcastReceiver
    {
        @Override
        @MainThread
        public void onReceive(Context context, Intent intent)
        {
            if (!Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                throw new IllegalArgumentException("onReceive called with action that isn't screen off!");
            }

            Timber.d("Cleaning up temp files");

            boolean allFilesDeleted = deleteOldTemporaryFiles(context);
            if (allFilesDeleted) {
                unregisterFileCleanupReceiver(context);
            }
        }
    }
}
