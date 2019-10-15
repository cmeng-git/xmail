package org.atalk.xryptomail.mailstore.migrations;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.VisibleForTesting;

import org.atalk.xryptomail.controller.MessagingControllerCommands.*;
import org.atalk.xryptomail.controller.PendingCommandSerializer;
import org.atalk.xryptomail.helper.Utility;
import org.atalk.xryptomail.mail.Flag;

import java.nio.charset.Charset;
import java.util.*;

import static java.util.Collections.singletonList;

class MigrationTo60 {
    private static final String PENDING_COMMAND_MOVE_OR_COPY = "org.atalk.xryptomail.MessagingController.moveOrCopy";
    private static final String PENDING_COMMAND_MOVE_OR_COPY_BULK = "org.atalk.xryptomail.MessagingController.moveOrCopyBulk";
    private static final String PENDING_COMMAND_MOVE_OR_COPY_BULK_NEW = "org.atalk.xryptomail.MessagingController.moveOrCopyBulkNew";
    private static final String PENDING_COMMAND_EMPTY_TRASH = "org.atalk.xryptomail.MessagingController.emptyTrash";
    private static final String PENDING_COMMAND_SET_FLAG_BULK = "org.atalk.xryptomail.MessagingController.setFlagBulk";
    private static final String PENDING_COMMAND_SET_FLAG = "org.atalk.xryptomail.MessagingController.setFlag";
    private static final String PENDING_COMMAND_APPEND = "org.atalk.xryptomail.MessagingController.append";
    private static final String PENDING_COMMAND_MARK_ALL_AS_READ = "org.atalk.xryptomail.MessagingController.markAllAsRead";
    private static final String PENDING_COMMAND_EXPUNGE = "org.atalk.xryptomail.MessagingController.expunge";


    public static void migratePendingCommands(SQLiteDatabase db) {
        List<PendingCommand> pendingCommands = new ArrayList<>();

        if (columnExists(db, "pending_commands", "arguments")) {
            for (OldPendingCommand oldPendingCommand : getPendingCommands(db)) {
                PendingCommand newPendingCommand = migratePendingCommand(oldPendingCommand);
                pendingCommands.add(newPendingCommand);
            }

            db.execSQL("DROP TABLE IF EXISTS pending_commands");
            db.execSQL("CREATE TABLE pending_commands (" +
                    "id INTEGER PRIMARY KEY, " +
                    "command TEXT, " +
                    "data TEXT" +
                    ")");

            PendingCommandSerializer pendingCommandSerializer = PendingCommandSerializer.getInstance();
            for (PendingCommand pendingCommand : pendingCommands) {
                ContentValues cv = new ContentValues();
                cv.put("command", pendingCommand.getCommandName());
                cv.put("data", pendingCommandSerializer.serialize(pendingCommand));
                db.insert("pending_commands", "command", cv);
            }
        }
    }

    private static boolean columnExists(SQLiteDatabase db, String table, String columnName) {
        Cursor columnCursor = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        boolean foundColumn = false;
        while (columnCursor.moveToNext()) {
            String currentColumnName = columnCursor.getString(1);
            if (currentColumnName.equals(columnName)) {
                foundColumn = true;
                break;
            }
        }
        columnCursor.close();
        return foundColumn;
    }

    @VisibleForTesting
    static PendingCommand migratePendingCommand(OldPendingCommand oldPendingCommand) {
        switch (oldPendingCommand.command) {
            case PENDING_COMMAND_APPEND: {
                return migrateCommandAppend(oldPendingCommand);
            }
            case PENDING_COMMAND_SET_FLAG_BULK: {
                return migrateCommandSetFlagBulk(oldPendingCommand);
            }
            case PENDING_COMMAND_SET_FLAG: {
                return migrateCommandSetFlag(oldPendingCommand);
            }
            case PENDING_COMMAND_MARK_ALL_AS_READ: {
                return migrateCommandMarkAllAsRead(oldPendingCommand);
            }
            case PENDING_COMMAND_MOVE_OR_COPY_BULK: {
                return migrateCommandMoveOrCopyBulk(oldPendingCommand);
            }
            case PENDING_COMMAND_MOVE_OR_COPY_BULK_NEW: {
                return migrateCommandMoveOrCopyBulkNew(oldPendingCommand);
            }
            case PENDING_COMMAND_MOVE_OR_COPY: {
                return migrateCommandMoveOrCopy(oldPendingCommand);
            }
            case PENDING_COMMAND_EMPTY_TRASH: {
                return migrateCommandEmptyTrash();
            }
            case PENDING_COMMAND_EXPUNGE: {
                return migrateCommandExpunge(oldPendingCommand);
            }
            default: {
                throw new IllegalArgumentException("Tried to migrate unknown pending command!");
            }
        }
    }

    private static PendingCommand migrateCommandExpunge(OldPendingCommand command) {
        String folder = command.arguments[0];
        return PendingExpunge.create(folder);
    }

    private static PendingCommand migrateCommandEmptyTrash() {
        return PendingEmptyTrash.create();
    }

    private static PendingCommand migrateCommandMoveOrCopy(OldPendingCommand command) {
        String srcFolder = command.arguments[0];
        String uid = command.arguments[1];
        String destFolder = command.arguments[2];
        boolean isCopy = Boolean.parseBoolean(command.arguments[3]);

        return PendingMoveOrCopy.create(srcFolder, destFolder, isCopy, singletonList(uid));
    }

    private static PendingCommand migrateCommandMoveOrCopyBulkNew(OldPendingCommand command) {
        String srcFolder = command.arguments[0];
        String destFolder = command.arguments[1];
        boolean isCopy = Boolean.parseBoolean(command.arguments[2]);
        boolean hasNewUids = Boolean.parseBoolean(command.arguments[3]);

        if (hasNewUids) {
            Map<String, String> uidMap = new HashMap<>();
            int offset = (command.arguments.length - 4) / 2;
            for (int i = 4; i < 4 + offset; i++) {
                uidMap.put(command.arguments[i], command.arguments[i + offset]);
            }

            return PendingMoveOrCopy.create(srcFolder, destFolder, isCopy, uidMap);
        } else {
            List<String> uids = new ArrayList<>(command.arguments.length - 4);
            uids.addAll(Arrays.asList(command.arguments).subList(4, command.arguments.length));

            return PendingMoveOrCopy.create(srcFolder, destFolder, isCopy, uids);
        }
    }

    private static PendingCommand migrateCommandMoveOrCopyBulk(OldPendingCommand command) {
        int len = command.arguments.length;

        OldPendingCommand newCommand = new OldPendingCommand();
        newCommand.command = PENDING_COMMAND_MOVE_OR_COPY_BULK_NEW;
        newCommand.arguments = new String[len + 1];
        newCommand.arguments[0] = command.arguments[0];
        newCommand.arguments[1] = command.arguments[1];
        newCommand.arguments[2] = command.arguments[2];
        newCommand.arguments[3] = Boolean.toString(false);
        System.arraycopy(command.arguments, 3, newCommand.arguments, 4, len - 3);

        return migratePendingCommand(newCommand);
    }

    private static PendingCommand migrateCommandMarkAllAsRead(OldPendingCommand command) {
        return PendingMarkAllAsRead.create(command.arguments[0]);
    }

    private static PendingCommand migrateCommandSetFlag(OldPendingCommand command) {
        String folder = command.arguments[0];
        String uid = command.arguments[1];
        boolean newState = Boolean.parseBoolean(command.arguments[2]);
        Flag flag = Flag.valueOf(command.arguments[3]);

        return PendingSetFlag.create(folder, newState, flag, singletonList(uid));
    }

    private static PendingCommand migrateCommandSetFlagBulk(OldPendingCommand command) {
        String folder = command.arguments[0];
        boolean newState = Boolean.parseBoolean(command.arguments[1]);
        Flag flag = Flag.valueOf(command.arguments[2]);

        List<String> uids = new ArrayList<>(command.arguments.length - 3);
        uids.addAll(Arrays.asList(command.arguments).subList(3, command.arguments.length));

        return PendingSetFlag.create(folder, newState, flag, uids);
    }

    private static PendingCommand migrateCommandAppend(OldPendingCommand command) {
        String folder = command.arguments[0];
        String uid = command.arguments[1];
        return PendingAppend.create(folder, uid);
    }

    private static List<OldPendingCommand> getPendingCommands(SQLiteDatabase db) {
        Cursor cursor = null;
        try {
            cursor = db.query("pending_commands",
                    new String[] { "id", "command", "arguments" }, null, null, null, null, "id ASC");
            List<OldPendingCommand> commands = new ArrayList<>();
            while (cursor.moveToNext()) {
                OldPendingCommand command = new OldPendingCommand();
                command.command = cursor.getString(1);
                String arguments = cursor.getString(2);
                command.arguments = arguments.split(",");
                for (int i = 0; i < command.arguments.length; i++) {
                    command.arguments[i] = fastUrlDecode(command.arguments[i]);
                }
                commands.add(command);
            }
            return commands;
        } finally {
            Utility.closeQuietly(cursor);
        }
    }

    private static String fastUrlDecode(String s) {
        byte[] bytes = s.getBytes(Charset.forName("UTF-8"));
        byte ch;
        int length = 0;
        for (int i = 0, count = bytes.length; i < count; i++) {
            ch = bytes[i];
            if (ch == '%') {
                int h = (bytes[i + 1] - '0');
                int l = (bytes[i + 2] - '0');
                if (h > 9) {
                    h -= 7;
                }
                if (l > 9) {
                    l -= 7;
                }
                bytes[length] = (byte) ((h << 4) | l);
                i += 2;
            } else if (ch == '+') {
                bytes[length] = ' ';
            } else {
                bytes[length] = bytes[i];
            }
            length++;
        }
        return new String(bytes, 0, length, Charset.forName("UTF-8"));
    }


    @VisibleForTesting
    static class OldPendingCommand {
        public String command;
        public String[] arguments;
    }
}
