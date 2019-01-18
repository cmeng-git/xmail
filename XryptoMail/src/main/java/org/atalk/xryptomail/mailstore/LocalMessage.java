package org.atalk.xryptomail.mailstore;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.VisibleForTesting;

import org.atalk.xryptomail.Account;
import org.atalk.xryptomail.BuildConfig;
import org.atalk.xryptomail.activity.MessageReference;
import org.atalk.xryptomail.mail.Address;
import org.atalk.xryptomail.mail.Flag;
import org.atalk.xryptomail.mail.Folder;
import org.atalk.xryptomail.mail.MessagingException;
import org.atalk.xryptomail.mail.internet.MimeMessage;
import org.atalk.xryptomail.mail.message.MessageHeaderParser;
import org.atalk.xryptomail.mailstore.LockableDatabase.DbCallback;
import org.atalk.xryptomail.mailstore.LockableDatabase.WrappedException;
import org.atalk.xryptomail.message.extractors.PreviewResult.PreviewType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;

import timber.log.Timber;

public class LocalMessage extends MimeMessage
{
    private final LocalStore mLocalStore;
    private long databaseId;
    private int mStealthTimerCount;
    private long mRootId;
    private long mThreadId;
    private long messagePartId;
    private MessageReference messageReference;
    private int mAttachmentCount;
    private String mSubject;
    private String mPreview = "";
    private String mimeType;
    private PreviewType previewType;
    private boolean headerNeedsUpdating = false;

    private LocalMessage(LocalStore localStore)
    {
        mLocalStore = localStore;
    }

    public LocalMessage(LocalStore localStore, String uid, Folder folder)
    {
        mLocalStore = localStore;
        this.mUid = uid;
        this.mFolder = folder;
    }

    void populateFromGetMessageCursor(Cursor cursor)
            throws MessagingException
    {
        final String subject = cursor.getString(LocalStore.MSG_INDEX_SUBJECT);
        this.setSubject(subject == null ? "" : subject);

        Address[] from = Address.unpack(cursor.getString(LocalStore.MSG_INDEX_SENDER_LIST));
        if (from.length > 0) {
            this.setFrom(from[0]);
        }
        this.setInternalSentDate(new Date(cursor.getLong(LocalStore.MSG_INDEX_DATE)));
        this.setUid(cursor.getString(LocalStore.MSG_INDEX_UID));
        String flagList = cursor.getString(LocalStore.MSG_INDEX_FLAGS);
        if (flagList != null && flagList.length() > 0) {
            String[] flags = flagList.split(",");
            for (String flag : flags) {
                try {
                    this.setFlagInternal(Flag.valueOf(flag), true);
                } catch (Exception e) {
                    if (!"X_BAD_FLAG".equals(flag)) {
                        Timber.w("Unable to parse flag %s", flag);
                    }
                }
            }
        }
        this.databaseId = cursor.getLong(LocalStore.MSG_INDEX_ID);
        this.setRecipients(RecipientType.TO, Address.unpack(cursor.getString(LocalStore.MSG_INDEX_TO)));
        this.setRecipients(RecipientType.CC, Address.unpack(cursor.getString(LocalStore.MSG_INDEX_CC)));
        this.setRecipients(RecipientType.BCC, Address.unpack(cursor.getString(LocalStore.MSG_INDEX_BCC)));
        this.setReplyTo(Address.unpack(cursor.getString(LocalStore.MSG_INDEX_REPLY_TO)));

        this.mAttachmentCount = cursor.getInt(LocalStore.MSG_INDEX_ATTACHMENT_COUNT);
        this.setInternalDate(new Date(cursor.getLong(LocalStore.MSG_INDEX_INTERNAL_DATE)));
        this.setMessageId(cursor.getString(LocalStore.MSG_INDEX_MESSAGE_ID_HEADER));

        String previewTypeString = cursor.getString(LocalStore.MSG_INDEX_PREVIEW_TYPE);
        DatabasePreviewType databasePreviewType = DatabasePreviewType.fromDatabaseValue(previewTypeString);
        previewType = databasePreviewType.getPreviewType();
        if (previewType == PreviewType.TEXT) {
            mPreview = cursor.getString(LocalStore.MSG_INDEX_PREVIEW);
        }
        else {
            mPreview = "";
        }

        if (this.mFolder == null) {
            LocalFolder f = new LocalFolder(mLocalStore, cursor.getInt(LocalStore.MSG_INDEX_FOLDER_ID));
            f.open(LocalFolder.OPEN_MODE_RW);
            this.mFolder = f;
        }

        mThreadId = (cursor.isNull(LocalStore.MSG_INDEX_THREAD_ID)) ? -1 : cursor.getLong(LocalStore.MSG_INDEX_THREAD_ID);
        mRootId = (cursor.isNull(LocalStore.MSG_INDEX_THREAD_ROOT_ID)) ? -1 : cursor.getLong(LocalStore.MSG_INDEX_THREAD_ROOT_ID);

        boolean deleted = (cursor.getInt(LocalStore.MSG_INDEX_FLAG_DELETED) == 1);
        boolean read = (cursor.getInt(LocalStore.MSG_INDEX_FLAG_READ) == 1);
        boolean flagged = (cursor.getInt(LocalStore.MSG_INDEX_FLAG_FLAGGED) == 1);
        boolean answered = (cursor.getInt(LocalStore.MSG_INDEX_FLAG_ANSWERED) == 1);
        boolean forwarded = (cursor.getInt(LocalStore.MSG_INDEX_FLAG_FORWARDED) == 1);

        setFlagInternal(Flag.DELETED, deleted);
        setFlagInternal(Flag.SEEN, read);
        setFlagInternal(Flag.FLAGGED, flagged);
        setFlagInternal(Flag.ANSWERED, answered);
        setFlagInternal(Flag.FORWARDED, forwarded);

        setMessagePartId(cursor.getLong(LocalStore.MSG_INDEX_MESSAGE_PART_ID));
        mimeType = cursor.getString(LocalStore.MSG_INDEX_MIME_TYPE);
        mStealthTimerCount = cursor.getInt(LocalStore.MSG_INDEX_STEALTH_TIMER);

        byte[] header = cursor.getBlob(LocalStore.MSG_INDEX_HEADER_DATA);
        if (header != null) {
            MessageHeaderParser.parse(this, new ByteArrayInputStream(header));
        }
        else {
            Timber.d("No headers available for this message!");
        }
        headerNeedsUpdating = false;
    }

    @VisibleForTesting
    void setMessagePartId(long messagePartId)
    {
        this.messagePartId = messagePartId;
    }

    public long getMessagePartId()
    {
        return messagePartId;
    }

    @Override
    public String getMimeType()
    {
        return mimeType;
    }

    /* 
     * Custom version of writeTo that updates the MIME message based on localMessage changes.
     */

    public PreviewType getPreviewType()
    {
        return previewType;
    }

    public String getPreview()
    {
        return mPreview;
    }

    @Override
    public String getSubject()
    {
        return mSubject;
    }

    @Override
    public void setSubject(String subject)
    {
        mSubject = subject;
        headerNeedsUpdating = true;
    }

    @Override
    public void setMessageId(String messageId)
    {
        mMessageId = messageId;
        headerNeedsUpdating = true;
    }

    @Override
    public void setUid(String uid)
    {
        super.setUid(uid);
        this.messageReference = null;
    }

    @Override
    public boolean hasAttachments()
    {
        return (mAttachmentCount > 0);
    }

    public int getAttachmentCount()
    {
        return mAttachmentCount;
    }

    @Override
    public void setFrom(Address from)
    {
        this.mFrom = new Address[]{from};
        headerNeedsUpdating = true;
    }

    @Override
    public void setReplyTo(Address[] replyTo)
    {
        if (replyTo == null || replyTo.length == 0) {
            mReplyTo = null;
        }
        else {
            mReplyTo = replyTo;
        }

        headerNeedsUpdating = true;
    }

    /*
     * For performance reasons, we add headers instead of setting them (see super implementation)
     * which removes (expensive) them before adding them
     */
    @Override
    public void setRecipients(RecipientType type, Address[] addresses)
    {
        if (type == RecipientType.TO) {
            if (addresses == null || addresses.length == 0) {
                this.mTo = null;
            }
            else {
                this.mTo = addresses;
            }
        }
        else if (type == RecipientType.CC) {
            if (addresses == null || addresses.length == 0) {
                this.mCc = null;
            }
            else {
                this.mCc = addresses;
            }
        }
        else if (type == RecipientType.BCC) {
            if (addresses == null || addresses.length == 0) {
                this.mBcc = null;
            }
            else {
                this.mBcc = addresses;
            }
        }
        else {
            throw new IllegalArgumentException("Unrecognized recipient type.");
        }

        headerNeedsUpdating = true;
    }

    public void setFlagInternal(Flag flag, boolean set)
            throws MessagingException
    {
        super.setFlag(flag, set);
    }

    public long getDatabaseId()
    {
        return databaseId;
    }

    @Override
    public void setFlag(final Flag flag, final boolean set)
            throws MessagingException
    {
        try {
            mLocalStore.getDatabase().execute(true, new DbCallback<Void>()
            {
                @Override
                public Void doDbWork(final SQLiteDatabase db)
                        throws WrappedException, UnavailableStorageException
                {
                    try {
                        if (flag == Flag.DELETED && set) {
                            delete();
                        }
                        LocalMessage.super.setFlag(flag, set);
                    } catch (MessagingException e) {
                        throw new WrappedException(e);
                    }
                    /*
                     * Set the flags on the message.
                     */
                    ContentValues cv = new ContentValues();
                    cv.put("flags", LocalStore.serializeFlags(getFlags()));
                    cv.put("read", isSet(Flag.SEEN) ? 1 : 0);
                    cv.put("flagged", isSet(Flag.FLAGGED) ? 1 : 0);
                    cv.put("answered", isSet(Flag.ANSWERED) ? 1 : 0);
                    cv.put("forwarded", isSet(Flag.FORWARDED) ? 1 : 0);

                    db.update("messages", cv, "id = ?", new String[]{Long.toString(databaseId)});
                    return null;
                }
            });
        } catch (WrappedException e) {
            throw (MessagingException) e.getCause();
        }
        mLocalStore.notifyChange();
    }

    public int getStealthTimerCount()
    {
        return mStealthTimerCount;
    }

    /**
     * update the Stealth timer counter for the current message.
     *
     * @param stealthCounter The remaining timer counter for the last read Stealth Message
     */
    public void updateStealthTimer(final int stealthCounter)
            throws MessagingException
    {
        try {
            mLocalStore.getDatabase().execute(true, new DbCallback<Void>()
            {
                @Override
                public Void doDbWork(final SQLiteDatabase db)
                        throws WrappedException, UnavailableStorageException
                {
                    ContentValues cv = new ContentValues();
                    cv.put("stealth_timer", stealthCounter);
                    db.update("messages", cv, "id = ?", new String[]{Long.toString(databaseId)});
                    return null;
                }
            });
        } catch (WrappedException e) {
            throw (MessagingException) e.getCause();
        }
        mLocalStore.notifyChange();
    }

    /*
     * If a message is being marked as deleted we want to clear out its content. Delete will not actually remove the
     * row since we need to retain the UID for synchronization purposes.
     */
    private void delete()
            throws MessagingException
    {
        try {
            mLocalStore.getDatabase().execute(true, new DbCallback<Void>()
            {
                @Override
                public Void doDbWork(final SQLiteDatabase db)
                        throws WrappedException, UnavailableStorageException
                {
                    ContentValues cv = new ContentValues();
                    cv.put("deleted", 1);
                    cv.put("empty", 1);
                    cv.putNull("subject");
                    cv.putNull("sender_list");
                    cv.putNull("date");
                    cv.putNull("to_list");
                    cv.putNull("cc_list");
                    cv.putNull("bcc_list");
                    cv.putNull("preview");
                    cv.putNull("reply_to_list");
                    cv.putNull("message_part_id");
                    cv.putNull("stealth_timer");

                    db.update("messages", cv, "id = ?", new String[]{Long.toString(databaseId)});
                    try {
                        ((LocalFolder) mFolder).deleteMessagePartsAndDataFromDisk(messagePartId);
                    } catch (MessagingException e) {
                        throw new WrappedException(e);
                    }

                    getFolder().deleteFulltextIndexEntry(db, databaseId);

                    return null;
                }
            });
        } catch (WrappedException e) {
            throw (MessagingException) e.getCause();
        }
        mLocalStore.notifyChange();
    }

    public void debugClearLocalData()
            throws MessagingException
    {
        if (!BuildConfig.DEBUG) {
            throw new AssertionError("method must only be used in debug build!");
        }

        try {
            mLocalStore.getDatabase().execute(true, new DbCallback<Void>()
            {
                @Override
                public Void doDbWork(final SQLiteDatabase db)
                        throws WrappedException, MessagingException
                {
                    ContentValues cv = new ContentValues();
                    cv.putNull("message_part_id");

                    db.update("messages", cv, "id = ?", new String[]{Long.toString(databaseId)});
                    try {
                        ((LocalFolder) mFolder).deleteMessagePartsAndDataFromDisk(messagePartId);
                    } catch (MessagingException e) {
                        throw new WrappedException(e);
                    }

                    setFlag(Flag.X_DOWNLOADED_FULL, false);
                    setFlag(Flag.X_DOWNLOADED_PARTIAL, false);

                    return null;
                }
            });
        } catch (WrappedException e) {
            throw (MessagingException) e.getCause();
        }
        mLocalStore.notifyChange();
    }

    /*
     * Completely remove a message from the local database
     *
     * TODO: document how this updates the thread structure
     */
    @Override
    public void destroy()
            throws MessagingException
    {
        getFolder().destroyMessage(this);
    }

    @Override
    public LocalMessage clone()
    {
        LocalMessage message = new LocalMessage(mLocalStore);
        super.copy(message);

        message.messageReference = messageReference;
        message.databaseId = databaseId;
        message.mAttachmentCount = mAttachmentCount;
        message.mSubject = mSubject;
        message.mPreview = mPreview;
        message.mThreadId = mThreadId;
        message.mRootId = mRootId;
        message.messagePartId = messagePartId;
        message.mimeType = mimeType;
        message.previewType = previewType;
        message.headerNeedsUpdating = headerNeedsUpdating;

        return message;
    }

    public long getThreadId()
    {
        return mThreadId;
    }

    public long getRootId()
    {
        return mRootId;
    }

    public Account getAccount()
    {
        return mLocalStore.getAccount();
    }

    public MessageReference makeMessageReference()
    {
        if (messageReference == null) {
            messageReference = new MessageReference(getFolder().getAccountUuid(), getFolder().getName(), mUid, null);
        }
        return messageReference;
    }

    @Override
    public LocalFolder getFolder()
    {
        return (LocalFolder) super.getFolder();
    }

    public String getUri()
    {
        return "email://messages/" + getAccount().getAccountNumber() + "/" + getFolder().getName() + "/" + getUid();
    }

    @Override
    public void writeTo(OutputStream out)
            throws IOException, MessagingException
    {
        if (headerNeedsUpdating) {
            updateHeader();
        }
        super.writeTo(out);
    }

    private void updateHeader()
    {
        super.setSubject(mSubject);
        super.setReplyTo(mReplyTo);
        super.setRecipients(RecipientType.TO, mTo);
        super.setRecipients(RecipientType.CC, mCc);
        super.setRecipients(RecipientType.BCC, mBcc);

        if (mFrom != null && mFrom.length > 0) {
            super.setFrom(mFrom[0]);
        }

        if (mMessageId != null) {
            super.setMessageId(mMessageId);
        }
        headerNeedsUpdating = false;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        // distinguish by account uuid, in addition to what Message.equals() does above
        String thisAccountUuid = getAccountUuid();
        String thatAccountUuid = ((LocalMessage) o).getAccountUuid();
        return thisAccountUuid != null ? thisAccountUuid.equals(thatAccountUuid) : thatAccountUuid == null;
    }

    @Override
    public int hashCode()
    {
        int result = super.hashCode();
        result = 31 * result + (getAccountUuid() != null ? getAccountUuid().hashCode() : 0);
        return result;
    }

    private String getAccountUuid()
    {
        return getAccount().getUuid();
    }
}
