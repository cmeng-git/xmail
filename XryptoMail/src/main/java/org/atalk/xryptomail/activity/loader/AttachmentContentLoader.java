package org.atalk.xryptomail.activity.loader;

import android.content.Context;

import androidx.loader.content.AsyncTaskLoader;

import org.atalk.xryptomail.activity.misc.Attachment;
import org.atalk.xryptomail.activity.misc.Attachment.LoadingState;
import org.atalk.xryptomail.helper.FileBackend;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import de.cketti.safecontentresolver.SafeContentResolver;
import de.cketti.safecontentresolver.SafeContentResolverCompat;
import timber.log.Timber;

/**
 * Loader to fetch the content of an attachment.
 *
 * This will copy the data to a temporary file in our app's cache directory.
 */
public class AttachmentContentLoader extends AsyncTaskLoader<Attachment> {
    private static final String FILENAME_PREFIX = "attachment";
    private final Attachment sourceAttachment;
    private Attachment cachedResultAttachment;

    public AttachmentContentLoader(Context context, Attachment attachment) {
        super(context);
        if (attachment.state != LoadingState.METADATA) {
            throw new IllegalArgumentException("Attachment provided to content loader must be in METADATA state");
        }
        sourceAttachment = attachment;
    }

    @Override
    protected void onStartLoading() {
        if (cachedResultAttachment != null) {
            deliverResult(sourceAttachment);
        }

        if (takeContentChanged() || cachedResultAttachment == null) {
            forceLoad();
        }
    }

    @Override
    public Attachment loadInBackground() {
        Context context = getContext();

        try {
            File file = File.createTempFile(FILENAME_PREFIX, null, context.getCacheDir());
            file.deleteOnExit();

            Timber.v("Saving attachment to %s", file.getAbsolutePath());

            SafeContentResolver safeContentResolver = SafeContentResolverCompat.newInstance(context);
            InputStream in = safeContentResolver.openInputStream(sourceAttachment.uri);
            try (FileOutputStream out = new FileOutputStream(file)) {
                if (in != null)
                    FileBackend.copy(in, out);
            }

            cachedResultAttachment = sourceAttachment.deriveWithLoadComplete(file.getAbsolutePath());
            return cachedResultAttachment;
        } catch (IOException e) {
            Timber.e(e, "Error saving attachment!");
        }

        cachedResultAttachment = sourceAttachment.deriveWithLoadCancelled();
        return cachedResultAttachment;
    }
}
