package org.atalk.xryptomail.helper;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import timber.log.Timber;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import androidx.core.content.ContextCompat;

import org.atalk.xryptomail.mail.Address;
import org.atalk.xryptomail.mail.helper.EmptyCursor;

import java.util.HashMap;

/**
 * Helper class to access the contacts stored on the device.
 */
public class Contacts {
    /**
     * The order in which the search results are returned by
     * {@link #getContactByAddress(String)}.
     */
    protected static final String SORT_ORDER =
            ContactsContract.CommonDataKinds.Email.TIMES_CONTACTED + " DESC, " +
                    ContactsContract.Contacts.DISPLAY_NAME + ", " +
                    ContactsContract.CommonDataKinds.Email._ID;

    /**
     * Array of columns to load from the database.
     */
    protected static final String PROJECTION[] = {
            ContactsContract.CommonDataKinds.Email._ID,
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Email.CONTACT_ID,
            Photo.PHOTO_URI
    };

    /**
     * Index of the name field in the projection. This must match the order in
     * {@link #PROJECTION}.
     */
    protected static final int NAME_INDEX = 1;

    /**
     * Index of the contact id field in the projection. This must match the order in
     * {@link #PROJECTION}.
     */
    protected static final int CONTACT_ID_INDEX = 2;


    /**
     * Get instance of the Contacts class.
     *
     * <p>Note: This is left over from the days when we needed to have SDK-specific code to access
     * the contacts.</p>
     *
     * @param context A {@link Context} instance.
     * @return Appropriate {@link Contacts} instance for this device.
     */
    public static Contacts getInstance(Context context) {
        return new Contacts(context);
    }

    protected Context mContext;
    protected ContentResolver mContentResolver;
    private static HashMap<String, String> nameCache = new HashMap<>();


    /**
     * Constructor
     *
     * @param context A {@link Context} instance.
     */
    protected Contacts(Context context) {
        mContext = context;
        mContentResolver = context.getContentResolver();
    }

    /**
     * Start the activity to add information to an existing contact or add a
     * new one.
     *
     * @param email An {@link Address} instance containing the email address
     *              of the entity you want to add to the contacts. Optionally
     *              the instance also contains the (display) name of that
     *              entity.
     */
    public void createContact(final Address email) {
        final Uri contactUri = Uri.fromParts("mailto", email.getAddress(), null);

        final Intent contactIntent = new Intent(ContactsContract.Intents.SHOW_OR_CREATE_CONTACT);
        contactIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        contactIntent.setData(contactUri);

        // Pass along full email string for possible create dialog
        contactIntent.putExtra(ContactsContract.Intents.EXTRA_CREATE_DESCRIPTION,
                email.toString());

        // Only provide personal name hint if we have one
        final String senderPersonal = email.getPersonal();
        if (senderPersonal != null) {
            contactIntent.putExtra(ContactsContract.Intents.Insert.NAME, senderPersonal);
        }

        mContext.startActivity(contactIntent);
        clearCache();
    }

    /**
     * Start the activity to add a phone number to an existing contact or add a new one.
     *
     * @param phoneNumber
     *         The phone number to add to a contact, or to use when creating a new contact.
     */
    public void addPhoneContact(final String phoneNumber) {
        Intent addIntent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
        addIntent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
        addIntent.putExtra(ContactsContract.Intents.Insert.PHONE, Uri.decode(phoneNumber));
        addIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(addIntent);
        clearCache();
    }

    /**
     * Check whether the provided email address belongs to one of the contacts.
     *
     * @param emailAddress The email address to look for.
     * @return <tt>true</tt>, if the email address belongs to a contact.
     *         <tt>false</tt>, otherwise.
     */
    public boolean isInContacts(final String emailAddress) {
        boolean result = false;

        final Cursor c = getContactByAddress(emailAddress);

        if (c != null) {
            if (c.getCount() > 0) {
                result = true;
            }
            c.close();
        }

        return result;
    }

    /**
     * Check whether one of the provided addresses belongs to one of the contacts.
     *
     * @param addresses The addresses to search in contacts
     * @return <tt>true</tt>, if one address belongs to a contact.
     *         <tt>false</tt>, otherwise.
     */
    public boolean isAnyInContacts(final Address[] addresses) {
        if (addresses == null) {
            return false;
        }

        for (Address addr : addresses) {
            if (isInContacts(addr.getAddress())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the name of the contact an email address belongs to.
     *
     * @param address The email address to search for.
     * @return The name of the contact the email address belongs to. Or
     *      <tt>null</tt> if there's no matching contact.
     */
    public String getNameForAddress(String address) {
        if (address == null) {
            return null;
        } else if (nameCache.containsKey(address)) {
            return nameCache.get(address);
        }

        final Cursor c = getContactByAddress(address);

        String name = null;
        if (c != null) {
            if (c.getCount() > 0) {
                c.moveToFirst();
                name = c.getString(NAME_INDEX);
            }
            c.close();
        }

        nameCache.put(address, name);
        return name;
    }

    /**
     * Mark contacts with the provided email addresses as contacted.
     *
     * @param addresses Array of {@link Address} objects describing the
     *        contacts to be marked as contacted.
     */
    public void markAsContacted(final Address[] addresses) {
        //TODO: Optimize! Potentially a lot of database queries
        for (final Address address : addresses) {
            final Cursor c = getContactByAddress(address.getAddress());

            if (c != null) {
                if (c.getCount() > 0) {
                    c.moveToFirst();
                    final long personId = c.getLong(CONTACT_ID_INDEX);
                    ContactsContract.Contacts.markAsContacted(mContentResolver, personId);
                }
                c.close();
            }
        }
    }

    /**
     * Creates the intent necessary to open a contact picker.
     *
     * @return The intent necessary to open a contact picker.
     */
    public Intent contactPickerIntent() {
        return new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Email.CONTENT_URI);
    }

    /**
     * Get URI to the picture of the contact with the supplied email address.
     *
     * @param address
     *         An email address. The contact database is searched for a contact with this email
     *         address.
     *
     * @return URI to the picture of the contact with the supplied email address. {@code null} if
     *         no such contact could be found or the contact doesn't have a picture.
     */
    public Uri getPhotoUri(String address) {
        try {
            final Cursor c = getContactByAddress(address);
            if (c == null) {
                return null;
            }

            try {
                if (!c.moveToFirst()) {
                    return null;
                }
                int columnIndex = c.getColumnIndex(Photo.PHOTO_URI);
                final String uriString = c.getString(columnIndex);
                if (uriString == null)
                    return null;
                return Uri.parse(uriString);
            } catch (IllegalStateException e) {
                return null;
            } finally {
                c.close();
            }
        } catch (Exception e) {
            Timber.e(e, "Couldn't fetch photo for contact with email %s", address);
            return null;
        }
    }

    private boolean hasContactPermission() {
        boolean canRead = ContextCompat.checkSelfPermission(mContext,
                Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        boolean canWrite = ContextCompat.checkSelfPermission(mContext,
                Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        return  canRead && canWrite;
    }

    /**
     * Return a {@link Cursor} instance that can be used to fetch information
     * about the contact with the given email address.
     *
     * @param address The email address to search for.
     * @return A {@link Cursor} instance that can be used to fetch information
     *         about the contact with the given email address
     */
    private Cursor getContactByAddress(final String address) {
        final Uri uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Email.CONTENT_LOOKUP_URI, Uri.encode(address));

        if (hasContactPermission()) {
            return mContentResolver.query(
                    uri,
                    PROJECTION,
                    null,
                    null,
                    SORT_ORDER);
        } else {
            return new EmptyCursor();
        }
    }

    /**
     * Clears the cache for names and photo uris
     */
    public static void clearCache() {
        nameCache.clear();
    }

}
