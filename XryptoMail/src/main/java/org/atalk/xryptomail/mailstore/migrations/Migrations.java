package org.atalk.xryptomail.mailstore.migrations;

import android.database.sqlite.SQLiteDatabase;

import org.atalk.xryptomail.mailstore.LocalStore;

public class Migrations
{
    @SuppressWarnings("fallthrough")
    public static void upgradeDatabase(SQLiteDatabase db, MigrationsHelper migrationsHelper)
    {
        boolean shouldBuildFtsTable = false;
        switch (db.getVersion()) {
            case 40:
                MigrationTo41.db41FoldersAddClassColumns(db);
                MigrationTo41.db41UpdateFolderMetadata(db, migrationsHelper);
            case 41:
                boolean notUpdatingFromEarlierThan41 = db.getVersion() == 41;
                if (notUpdatingFromEarlierThan41) {
                    MigrationTo42.from41MoveFolderPreferences(migrationsHelper);
                }
            case 42:
                MigrationTo43.fixOutboxFolders(db, migrationsHelper);
            case 43:
                MigrationTo44.addMessagesThreadingColumns(db);
            case 44:
                MigrationTo45.changeThreadingIndexes(db);
            case 45:
                MigrationTo46.addMessagesFlagColumns(db, migrationsHelper);
            case 46:
                MigrationTo47.createThreadsTable(db);
            case 47:
                MigrationTo48.updateThreadsSetRootWhereNull(db);
            case 48:
                MigrationTo49.createMsgCompositeIndex(db);
            case 49:
                MigrationTo50.foldersAddNotifyClassColumn(db, migrationsHelper);
            case 50:
                MigrationTo51.db51MigrateMessageFormat(db, migrationsHelper);
            case 51:
                MigrationTo52.addMoreMessagesColumnToFoldersTable(db);
            case 52:
                MigrationTo53.removeNullValuesFromEmptyColumnInMessagesTable(db);
            case 53:
                MigrationTo54.addPreviewTypeColumn(db);
            case 54:
                MigrationTo55.createFtsSearchTable(db);
                shouldBuildFtsTable = true;
            case 55:
                MigrationTo56.cleanUpFtsTable(db);
            case 56:
                MigrationTo57.fixDataLocationForMultipartParts(db);
            case 57:
                MigrationTo58.cleanUpOrphanedData(db);
                MigrationTo58.createDeleteMessageTrigger(db);
            case 58:
                MigrationTo59.addMissingIndexes(db);
            case 59:
                MigrationTo60.migratePendingCommands(db);
            case 60:
                MigrationTo61.removeErrorsFolder(db);
        }

        if (shouldBuildFtsTable) {
            buildFtsTable(db, migrationsHelper);
        }
    }

    private static void buildFtsTable(SQLiteDatabase db, MigrationsHelper migrationsHelper)
    {
        LocalStore localStore = migrationsHelper.getLocalStore();
        FullTextIndexer fullTextIndexer = new FullTextIndexer(localStore, db);
        fullTextIndexer.indexAllMessages();
    }
}
