package org.atalk.xryptomail.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.atalk.xryptomail.Account;
import org.atalk.xryptomail.BuildConfig;
import org.atalk.xryptomail.Preferences;
import org.atalk.xryptomail.mail.MessagingException;
import org.atalk.xryptomail.mail.internet.MimeUtility;
import org.atalk.xryptomail.mailstore.LocalStore;
import org.atalk.xryptomail.mailstore.LocalStore.AttachmentInfo;
import org.openintents.openpgp.util.OpenPgpApi.OpenPgpDataSource;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import timber.log.Timber;

/**
 * A simple ContentProvider that allows file access to attachments.
 *
 * <p>
 * Warning! We make heavy assumptions about the Uris used by the {@link LocalStore} for an
 * {@link Account} here.
 * </p>
 */
public class AttachmentProvider extends ContentProvider
{
    private static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".attachmentprovider";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    private static final String[] DEFAULT_PROJECTION = new String[]{
            AttachmentProviderColumns._ID,
            AttachmentProviderColumns.DATA,
    };

    public static class AttachmentProviderColumns
    {
        public static final String _ID = "_id";
        public static final String DATA = "_data";
        public static final String DISPLAY_NAME = "_display_name";
        public static final String SIZE = "_size";
    }

    public static Uri getAttachmentUri(String accountUuid, long id)
    {
        return CONTENT_URI.buildUpon()
                .appendPath(accountUuid)
                .appendPath(Long.toString(id))
                .build();
    }

    @Override
    public boolean onCreate()
    {
        return true;
    }

    @Override
    public String getType(@NonNull Uri uri)
    {
        List<String> segments = uri.getPathSegments();
        String accountUuid = segments.get(0);
        String id = segments.get(1);
        String mimeType = (segments.size() < 3) ? null : segments.get(2);

        return getType(accountUuid, id, mimeType);
    }

    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode)
            throws FileNotFoundException
    {
        List<String> segments = uri.getPathSegments();
        String accountUuid = segments.get(0);
        String attachmentId = segments.get(1);

        ParcelFileDescriptor parcelFileDescriptor = openAttachment(accountUuid, attachmentId);
        if (parcelFileDescriptor == null) {
            throw new FileNotFoundException("Attachment missing or cannot be opened!");
        }
        return parcelFileDescriptor;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)
    {

        String[] columnNames = (projection == null) ? DEFAULT_PROJECTION : projection;

        List<String> segments = uri.getPathSegments();
        String accountUuid = segments.get(0);
        String id = segments.get(1);

        final AttachmentInfo attachmentInfo;
        try {
            final Account account = Preferences.getPreferences(getContext()).getAccount(accountUuid);
            attachmentInfo = LocalStore.getInstance(account, getContext()).getAttachmentInfo(id);
        } catch (MessagingException e) {
            Timber.e(e, "Unable to retrieve attachment info from local store for ID: %s", id);
            return null;
        }

        if (attachmentInfo == null) {
            Timber.d("No attachment info for ID: %s", id);
            return null;
        }

        MatrixCursor ret = new MatrixCursor(columnNames);
        Object[] values = new Object[columnNames.length];
        for (int i = 0, count = columnNames.length; i < count; i++) {
            String column = columnNames[i];
            if (AttachmentProviderColumns._ID.equals(column)) {
                values[i] = id;
            }
            else if (AttachmentProviderColumns.DATA.equals(column)) {
                values[i] = uri.toString();
            }
            else if (AttachmentProviderColumns.DISPLAY_NAME.equals(column)) {
                values[i] = attachmentInfo.name;
            }
            else if (AttachmentProviderColumns.SIZE.equals(column)) {
                values[i] = attachmentInfo.size;
            }
        }
        ret.addRow(values);
        return ret;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(@NonNull Uri uri, String arg1, String[] arg2)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values)
    {
        throw new UnsupportedOperationException();
    }

    private String getType(String accountUuid, String id, String mimeType)
    {
        String type;
        final Account account = Preferences.getPreferences(getContext()).getAccount(accountUuid);

        try {
            final LocalStore localStore = LocalStore.getInstance(account, getContext());

            AttachmentInfo attachmentInfo = localStore.getAttachmentInfo(id);
            if (mimeType != null) {
                type = mimeType;
            }
            else {
                // When accessing the "raw" message we deliver the original MIME type.
                type = attachmentInfo.type;
            }
        } catch (MessagingException e) {
            Timber.e(e, "Unable to retrieve LocalStore for %s", account);
            type = MimeUtility.DEFAULT_ATTACHMENT_MIME_TYPE;
        }
        return type;
    }

    @Nullable
    private ParcelFileDescriptor openAttachment(String accountUuid, String attachmentId)
    {
        try {
            OpenPgpDataSource openPgpDataSource = getAttachmentDataSource(accountUuid, attachmentId);
            if (openPgpDataSource == null) {
                Timber.e("Error getting data source for attachment (part doesn't exist?)");
                return null;
            }
            return openPgpDataSource.startPumpThread();
        } catch (MessagingException e) {
            Timber.e(e, "Error getting InputStream for attachment");
            return null;
        } catch (IOException e) {
            Timber.e(e, "Error creating ParcelFileDescriptor");
            return null;
        }
    }

    @Nullable
    private OpenPgpDataSource getAttachmentDataSource(String accountUuid, String attachmentId)
            throws MessagingException
    {
        final Account account = Preferences.getPreferences(getContext()).getAccount(accountUuid);
        LocalStore localStore = LocalStore.getInstance(account, getContext());
        return localStore.getAttachmentDataSource(attachmentId);
    }
}
