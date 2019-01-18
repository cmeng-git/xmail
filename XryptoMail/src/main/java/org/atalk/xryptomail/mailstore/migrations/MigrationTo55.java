package org.atalk.xryptomail.mailstore.migrations;

import android.database.sqlite.SQLiteDatabase;

class MigrationTo55
{
    static void createFtsSearchTable(SQLiteDatabase db)
    {
        db.execSQL("CREATE VIRTUAL TABLE messages_fulltext USING fts4 (fulltext)");
    }
}
