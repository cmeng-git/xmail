package org.atalk.xryptomail.mailstore.migrations;

import android.database.sqlite.SQLiteDatabase;

class MigrationTo61 {
    public static void removeErrorsFolder(SQLiteDatabase db) {
        db.execSQL("DELETE FROM folders WHERE name = 'XryptoMail-errors'");
    }
}
