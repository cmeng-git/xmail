package org.atalk.xryptomail.provider;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.atalk.xryptomail.Account;
import org.atalk.xryptomail.BuildConfig;
import org.atalk.xryptomail.Preferences;
import org.atalk.xryptomail.activity.MessageReference;
import org.atalk.xryptomail.mail.FetchProfile;
import org.atalk.xryptomail.mail.Folder;
import org.atalk.xryptomail.mail.MessagingException;
import org.atalk.xryptomail.mail.filter.CountingOutputStream;
import org.atalk.xryptomail.mailstore.LocalFolder;
import org.atalk.xryptomail.mailstore.LocalMessage;
import org.atalk.xryptomail.mailstore.LocalStore;
import org.openintents.openpgp.util.OpenPgpApi.OpenPgpDataSource;
import timber.log.Timber;

/**
 * A simple ContentProvider that allows file access to a raw message.
 */
public class RawMessageProvider extends ContentProvider {
    private static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".rawmessageprovider";
    private static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    private static final String[] DEFAULT_PROJECTION = new String[] {
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE
    };


    public static Uri getRawMessageUri(MessageReference messageReference) {
        return CONTENT_URI.buildUpon()
                .appendPath(messageReference.toIdentityString())
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(@NonNull Uri uri) {
        return "message/rfc822";
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {

        String[] columnNames = (projection == null) ? DEFAULT_PROJECTION : projection;

        List<String> segments = uri.getPathSegments();
        String messageReferenceString = segments.get(0);
        MessageReference messageReference = MessageReference.parse(messageReferenceString);

        LocalMessage message = loadMessage(messageReference);
        if (message == null) {
            return null;
        }

        MatrixCursor ret = new MatrixCursor(columnNames);
        Object[] values = new Object[columnNames.length];
        for (int i = 0, count = columnNames.length; i < count; i++) {
            String column = columnNames[i];
            if (OpenableColumns.DISPLAY_NAME.equals(column)) {
                values[i] = buildAttachmentFileName(message);
            } else if (OpenableColumns.SIZE.equals(column)) {
                values[i] = computeMessageSize(message);
            }
        }
        ret.addRow(values);
        return ret;
    }

    private String buildAttachmentFileName(LocalMessage message) {
        return message.getSubject() + ".eml";
    }

    private long computeMessageSize(LocalMessage message) {
        // TODO: Store message size in database when saving message so this can be a simple lookup instead.
        try {
            CountingOutputStream countingOutputStream = new CountingOutputStream();
            message.writeTo(countingOutputStream);
            return countingOutputStream.getCount();
        } catch (IOException | MessagingException e) {
            Timber.w(e, "Unable to compute message size");
            return 0;
        }
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(@NonNull Uri uri, String arg1, String[] arg2) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        List<String> segments = uri.getPathSegments();
        String messageReferenceString = segments.get(0);
        MessageReference messageReference = MessageReference.parse(messageReferenceString);

        ParcelFileDescriptor parcelFileDescriptor = openMessage(messageReference);
        if (parcelFileDescriptor == null) {
            throw new FileNotFoundException("Message missing or cannot be opened!");
        }
        return parcelFileDescriptor;
    }

    @Nullable
    private ParcelFileDescriptor openMessage(MessageReference messageReference) {
        try {
            OpenPgpDataSource openPgpDataSource = getRawMessageDataSource(messageReference);
            if (openPgpDataSource == null) {
                return null;
            }
            return openPgpDataSource.startPumpThread();
        } catch (IOException e) {
            Timber.e(e, "Error creating ParcelFileDescriptor");
            return null;
        }
    }

    @Nullable
    private OpenPgpDataSource getRawMessageDataSource(MessageReference messageReference) {
        final LocalMessage message = loadMessage(messageReference);
        if (message == null) {
            return null;
        }

        return new OpenPgpDataSource() {
            @Override
            public void writeTo(OutputStream os) throws IOException {
                try {
                    message.writeTo(os);
                } catch (MessagingException e) {
                    throw new IOException(e);
                }
            }
        };
    }

    private LocalMessage loadMessage(MessageReference messageReference) {
        String accountUuid = messageReference.getAccountUuid();
        String folderName = messageReference.getFolderName();
        String uid = messageReference.getUid();

        Account account = Preferences.getPreferences(getContext()).getAccount(accountUuid);
        if (account == null) {
            Timber.w("Account not found: %s", accountUuid);
            return null;
        }

        try {
            LocalStore localStore = LocalStore.getInstance(account, getContext());
            LocalFolder localFolder = localStore.getFolder(folderName);
            localFolder.open(Folder.OPEN_MODE_RW);

            LocalMessage message = localFolder.getMessage(uid);
            if (message == null || message.getDatabaseId() == 0) {
                Timber.w("Message not found: folder=%s, uid=%s", folderName, uid);
                return null;
            }

            FetchProfile fetchProfile = new FetchProfile();
            fetchProfile.add(FetchProfile.Item.BODY);
            localFolder.fetch(Collections.singletonList(message), fetchProfile, null);
            localFolder.close();

            return message;
        } catch (MessagingException e) {
            Timber.e(e, "Error loading message: folder=%s, uid=%s", folderName, uid);
            return null;
        }
    }
}
