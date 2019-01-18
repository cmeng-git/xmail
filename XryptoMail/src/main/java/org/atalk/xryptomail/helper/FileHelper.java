package org.atalk.xryptomail.helper;

import android.annotation.TargetApi;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import timber.log.Timber;

import org.apache.commons.io.IOUtils;

import java.io.*;
import java.util.Locale;

public class FileHelper {
    /**
     * Regular expression that represents characters we won't allow in file names.
     *
     * <p>
     * Allowed are:
     * <ul>
     *   <li>word characters (letters, digits, and underscores): {@code \w}</li>
     *   <li>spaces: {@code " "}</li>
     *   <li>special characters: {@code !}, {@code #}, {@code $}, {@code %}, {@code &}, {@code '},
     *       {@code (}, {@code )}, {@code -}, {@code @}, {@code ^}, {@code `}, <code>&#123;</code>,
     *       <code>&#125;</code>, {@code ~}, {@code .}, {@code ,}</li>
     * </ul></p>
     *
     * @see #sanitizeFilename(String)
     */
    private static final String INVALID_CHARACTERS = "[^\\w !#$%&'()\\-@\\^`{}~.,]";

    /**
     * Invalid characters in a file name are replaced by this character.
     *
     * @see #sanitizeFilename(String)
     */
    private static final String REPLACEMENT_CHARACTER = "_";

    /**
     * Creates a unique file in the given directory by appending a hyphen
     * and a number to the given filename.
	 *
	 * @param directory
	 * @param filename
	 * @return
     */
    public static File createUniqueFile(File directory, String filename) {
        File file = new File(directory, filename);
        if (!file.exists()) {
            return file;
        }
        // Get the extension of the file, if any.
        int index = filename.lastIndexOf('.');
        String name;
        String extension;
        if (index != -1) {
            name = filename.substring(0, index);
            extension = filename.substring(index);
        } else {
            name = filename;
            extension = "";
        }
        for (int i = 2; i < Integer.MAX_VALUE; i++) {
            file = new File(directory, String.format(Locale.US, "%s-%d%s", name, i, extension));
            if (!file.exists()) {
                return file;
            }
        }
        return null;
    }

    public static File createUniqueFileWithSeqAdded(File directory,	String filename) {
		return cloneFile(directory, filename, true);
	}

	private static File cloneFile(File directory, String filename, boolean addSeqAtAnyTime) {
		File file = new File(directory, filename);
		if (!addSeqAtAnyTime) {
			if (!file.exists()) {
				return file;
			}
		}
		// replace % with %%
		filename = filename.replaceAll("\u0025", "\u0025\u0025");
		// Get the extension of the file, if any.
		int index = filename.lastIndexOf('.');
		String format;

		if (index != -1) {
			String name = filename.substring(0, index);
			String extension = filename.substring(index);
			format = name + "-%d" + extension;
		} else {
			format = filename + "-%d";
		}
		for (int i = 2; i < Integer.MAX_VALUE; i++) {
			file = new File(directory, String.format(format, i));
			if (!file.exists()) {
				return file;
			}
		}
		return null;
	}

	/**
	 * @param parentDir
	 * @param name
	 *			Never <code>null</code>.
	 */
    public static void touchFile(final File parentDir, final String name) {
        final File file = new File(parentDir, name);
        try {
            if (!file.exists()) {
                if (!file.createNewFile()) {
                    Timber.d("Unable to create file: %s", file.getAbsolutePath());
                }
            } else {
                if (!file.setLastModified(System.currentTimeMillis())) {
                    Timber.d("Unable to change last modification date: %s", file.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            Timber.d(e, "Unable to touch file: %s", file.getAbsolutePath());
        }
    }

    private static void copyFile(File from, File to) throws IOException
	{
        FileInputStream in = new FileInputStream(from);
        FileOutputStream out = new FileOutputStream(to);
        try {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = in.read(buffer)) > 0) {
                out.write(buffer, 0, count);
            }
            out.close();
        } finally {
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(out);
        }
    }

    public static void renameOrMoveByCopying(File from, File to) throws IOException {
        deleteFileIfExists(to);

        boolean renameFailed = !from.renameTo(to);
        if (renameFailed) {
            copyFile(from, to);

            boolean deleteFromFailed = !from.delete();
            if (deleteFromFailed) {
                Timber.e("Unable to delete source file after copying to destination!");
            }
        }
    }

    private static void deleteFileIfExists(File to) throws IOException {
        boolean fileDoesNotExist = !to.exists();
        if (fileDoesNotExist) {
            return;
        }

        boolean deleteOk = to.delete();
        if (deleteOk) {
            return;
        }

        throw new IOException("Unable to delete file: " + to.getAbsolutePath());
    }

    public static boolean move(final File from, final File to) {
        if (to.exists()) {
            if (!to.delete()) {
                Timber.d("Unable to delete file: %s", to.getAbsolutePath());
            }
        }

        if (!to.getParentFile().mkdirs()) {
            Timber.d("Unable to make directories: %s", to.getParentFile().getAbsolutePath());
        }

        try {
            copyFile(from, to);

            boolean deleteFromFailed = !from.delete();
            if (deleteFromFailed) {
                Timber.e("Unable to delete source file after copying to destination!");
            }
            return true;
        } catch (Exception e) {
            Timber.w(e, "cannot move %s to %s", from.getAbsolutePath(), to.getAbsolutePath());
            return false;
        }
    }

    public static void moveRecursive(final File fromDir, final File toDir) {
        if (!fromDir.exists()) {
            return;
        }
        if (!fromDir.isDirectory()) {
            if (toDir.exists()) {
                if (!toDir.delete()) {
                    Timber.w("cannot delete already existing file/directory %s", toDir.getAbsolutePath());
                }
            }
            if (!fromDir.renameTo(toDir)) {
                Timber.w("cannot rename %s to %s - moving instead", fromDir.getAbsolutePath(), toDir.getAbsolutePath());
                move(fromDir, toDir);
            }
            return;
        }
        if (!toDir.exists() || !toDir.isDirectory()) {
            if (toDir.exists()) {
                if (!toDir.delete()) {
                    Timber.d("Unable to delete file: %s", toDir.getAbsolutePath());
                }
            }
            if (!toDir.mkdirs()) {
                Timber.w("cannot create directory %s", toDir.getAbsolutePath());
            }
        }
        File[] files = fromDir.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                moveRecursive(file, new File(toDir, file.getName()));
                if (!file.delete()) {
                    Timber.d("Unable to delete file: %s", toDir.getAbsolutePath());
                }
            } else {
                File target = new File(toDir, file.getName());
                if (!file.renameTo(target)) {
                    Timber.w("cannot rename %s to %s - moving instead",
                            file.getAbsolutePath(), target.getAbsolutePath());
                    move(file, target);
                }
            }
        }
        if (!fromDir.delete()) {
            Timber.w("cannot delete %s", fromDir.getAbsolutePath());
        }
    }

    /**
     * Replace characters we don't allow in file names with a replacement character.
     *
     * @param filename
     *         The original file name.
     *
     * @return The sanitized file name containing only allowed characters.
     */
    public static String sanitizeFilename(String filename) {
        return filename.replaceAll(INVALID_CHARACTERS, REPLACEMENT_CHARACTER);
    }

	/**
	 * Get a file path from a Uri. This will get the the path for Storage Access Framework
	 * Documents, as well as the _data field for the MediaStore and other file-based
	 * ContentProviders.
	 *
	 * @param context
	 *        The context.
	 * @param uri
	 *        The Uri to query.
	 * @author paulburke
	 */
	@TargetApi(Build.VERSION_CODES.KITKAT)
	public static String getRealFilePath(final Context context, final Uri uri)
	{
		final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

		// DocumentProvider
		if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
			// ExternalStorageProvider
			if (isExternalStorageDocument(uri)) {
				final String docId = DocumentsContract.getDocumentId(uri);
				final String[] split = docId.split(":");
				final String type = split[0];

				if ("primary".equalsIgnoreCase(type)) {
					return Environment.getExternalStorageDirectory() + "/" + split[1];
				}
				// TODO handle non-primary volumes
			}
			// DownloadsProvider
			else if (isDownloadsDocument(uri)) {

				final String id = DocumentsContract.getDocumentId(uri);
				final Uri contentUri = ContentUris.withAppendedId(
					Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

				return getDataColumn(context, contentUri, null, null);
			}
			// MediaProvider
			else if (isMediaDocument(uri)) {
				final String docId = DocumentsContract.getDocumentId(uri);
				final String[] split = docId.split(":");
				final String type = split[0];

				Uri contentUri = null;
				if ("image".equals(type)) {
					contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
				}
				else if ("video".equals(type)) {
					contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
				}
				else if ("audio".equals(type)) {
					contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
				}
				final String selection = "_id=?";
				final String[] selectionArgs = new String[] { split[1] };

				return getDataColumn(context, contentUri, selection, selectionArgs);
			}
		}
		// MediaStore (and general)
		else if ("content".equalsIgnoreCase(uri.getScheme())) {
			return getDataColumn(context, uri, null, null);
		}
		// File
		else if ("file".equalsIgnoreCase(uri.getScheme())) {
			return uri.getPath();
		}
		return null;
	}

	/**
	 * Get the value of the data column for this Uri. This is useful for MediaStore Uris, and other
	 * file-based ContentProviders.
	 *
	 * @param context
	 *        The context.
	 * @param uri
	 *        The Uri to query.
	 * @param selection
	 *        (Optional) Filter used in the query.
	 * @param selectionArgs
	 *        (Optional) Selection arguments used in the query.
	 * @return The value of the _data column, which is typically a file path.
	 */
	public static String getDataColumn(Context context, Uri uri, String selection,
		String[] selectionArgs)
	{

		Cursor cursor = null;
		final String column = "_data";
		final String[] projection = { column };

		try {
			context.grantUriPermission(context.getPackageName(), uri,
				Intent.FLAG_GRANT_READ_URI_PERMISSION);
			cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
				null);
			if (cursor != null && cursor.moveToFirst()) {
				final int column_index = cursor.getColumnIndexOrThrow(column);
				return cursor.getString(column_index);
			}
		}
		finally {
			if (cursor != null)
				cursor.close();
		}
		return null;
	}

	/**
	 * @param uri
	 *        The Uri to check.
	 * @return Whether the Uri authority is ExternalStorageProvider.
	 */
	public static boolean isExternalStorageDocument(Uri uri)
	{
		return "com.android.externalstorage.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri
	 *        The Uri to check.
	 * @return Whether the Uri authority is DownloadsProvider.
	 */
	public static boolean isDownloadsDocument(Uri uri)
	{
		return "com.android.providers.downloads.documents".equals(uri.getAuthority());
	}
	
	/**
	 * @param uri
	*        The Uri to check.
	 * @return Whether the Uri authority is MediaProvider.
	 */
	public static boolean isMediaDocument(Uri uri)
	{
		return "com.android.providers.media.documents".equals(uri.getAuthority());
	}
}
