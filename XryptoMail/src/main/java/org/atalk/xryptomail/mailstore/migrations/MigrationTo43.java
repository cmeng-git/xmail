package org.atalk.xryptomail.mailstore.migrations;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import timber.log.Timber;

import org.atalk.xryptomail.Account;
import org.atalk.xryptomail.XryptoMail;
import org.atalk.xryptomail.R;
import org.atalk.xryptomail.mail.Message;
import org.atalk.xryptomail.mailstore.LocalFolder;
import org.atalk.xryptomail.mailstore.LocalStore;

import java.util.List;

class MigrationTo43 {
    public static void fixOutboxFolders(SQLiteDatabase db, MigrationsHelper migrationsHelper) {
        try {
            LocalStore localStore = migrationsHelper.getLocalStore();
            Account account = migrationsHelper.getAccount();
            Context context = migrationsHelper.getContext();

            // If folder "OUTBOX" (old, v3.800 - v3.802) exists, rename it to
            // "XRYPTOMAIL_INTERNAL_OUTBOX" (new)
            LocalFolder oldOutbox = new LocalFolder(localStore, "OUTBOX");
            if (oldOutbox.exists()) {
                ContentValues cv = new ContentValues();
                cv.put("name", Account.OUTBOX);
                db.update("folders", cv, "name = ?", new String[] { "OUTBOX" });
                Timber.i("Renamed folder OUTBOX to %s", Account.OUTBOX);
            }

            // Check if old (pre v3.800) localized outbox folder exists
            String localizedOutbox = context.getString(R.string.special_mailbox_name_outbox);
            LocalFolder obsoleteOutbox = new LocalFolder(localStore, localizedOutbox);
            if (obsoleteOutbox.exists()) {
                // Get all messages from the localized outbox ...
                List<? extends Message> messages = obsoleteOutbox.getMessages(null, false);

                if (messages.size() > 0) {
                    // ... and move them to the drafts folder (we don't want to
                    // surprise the user by sending potentially very old messages)
                    LocalFolder drafts = new LocalFolder(localStore, account.getDraftsFolderName());
                    obsoleteOutbox.moveMessages(messages, drafts);
                }

                // Now get rid of the localized outbox
                obsoleteOutbox.delete();
                obsoleteOutbox.delete(true);
            }
        } catch (Exception e) {
            Timber.e(e, "Error trying to fix the outbox folders");
        }
    }
}
