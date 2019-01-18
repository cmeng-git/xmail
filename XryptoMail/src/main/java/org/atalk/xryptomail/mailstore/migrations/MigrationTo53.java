package org.atalk.xryptomail.mailstore.migrations;


import android.database.sqlite.SQLiteDatabase;


class MigrationTo53 {
    public static void removeNullValuesFromEmptyColumnInMessagesTable(SQLiteDatabase db) {
        db.execSQL("UPDATE messages SET empty = 0 WHERE empty IS NULL");
    }
}
