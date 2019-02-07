package org.atalk.xryptomail.ui.messageview;

import android.app.DownloadManager;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.support.annotation.WorkerThread;
import android.widget.Toast;

import org.apache.commons.io.IOUtils;
import org.atalk.xryptomail.*;
import org.atalk.xryptomail.cache.TemporaryAttachmentStore;
import org.atalk.xryptomail.controller.*;
import org.atalk.xryptomail.helper.FileHelper;
import org.atalk.xryptomail.mail.Message;
import org.atalk.xryptomail.mail.*;
import org.atalk.xryptomail.mail.internet.MimeUtility;
import org.atalk.xryptomail.mailstore.*;
import org.atalk.xryptomail.provider.AttachmentTempFileProvider;

import java.io.*;
import java.util.List;

import timber.log.Timber;

public class AttachmentController
{
	private final Context context;
	private final MessagingController controller;
	private final MessageViewFragment messageViewFragment;
	private final AttachmentViewInfo attachment;
	private final DownloadManager downloadManager;


	AttachmentController(MessagingController controller, DownloadManager downloadManager,
			MessageViewFragment messageViewFragment, AttachmentViewInfo attachment)
	{
		this.context = messageViewFragment.getApplicationContext();
		this.controller = controller;
		this.downloadManager = downloadManager;
		this.messageViewFragment = messageViewFragment;
		this.attachment = attachment;
	}

	public void viewAttachment()
	{
		if (!attachment.isContentAvailable()) {
			downloadAndViewAttachment((LocalPart) attachment.part);
		}
		else {
			viewLocalAttachment();
		}
	}

	public void saveAttachment()
	{
		saveAttachmentTo(XryptoMail.getAttachmentDefaultPath());
	}

	public void saveAttachmentTo(String directory)
	{
		saveAttachmentTo(new File(directory));
	}

	private void downloadAndViewAttachment(LocalPart localPart)
	{
		downloadAttachment(localPart, () -> viewLocalAttachment());
	}

	private void downloadAndSaveAttachmentTo(LocalPart localPart, final File directory)
	{
		downloadAttachment(localPart, () -> {
            messageViewFragment.refreshAttachmentThumbnail(attachment);
            saveLocalAttachmentTo(directory);
        });
	}

	private void downloadAttachment(LocalPart localPart, final Runnable attachmentDownloadedCallback)
	{
		String accountUuid = localPart.getAccountUuid();
		Account account = Preferences.getPreferences(context).getAccount(accountUuid);
		LocalMessage message = localPart.getMessage();

		messageViewFragment.showAttachmentLoadingDialog();
		controller.loadAttachment(account, message, attachment.part, new SimpleMessagingListener()
		{
			@Override
			public void loadAttachmentFinished(Account account, Message message, Part part)
			{
				attachment.setContentAvailable();
				messageViewFragment.hideAttachmentLoadingDialogOnMainThread();
				messageViewFragment.runOnMainThread(attachmentDownloadedCallback);
			}

			@Override
			public void loadAttachmentFailed(Account account, Message message,
					Part part, String reason)
			{
				messageViewFragment.hideAttachmentLoadingDialogOnMainThread();
			}
		});
	}

	private void viewLocalAttachment()
	{
		new ViewAttachmentAsyncTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	private void saveAttachmentTo(File directory)
	{
		boolean isExternalStorageMounted = Environment.getExternalStorageState().equals(
				Environment.MEDIA_MOUNTED);
		if (!isExternalStorageMounted) {
			String message = context.getString(R.string.message_view_status_attachment_not_saved);
			displayMessageToUser(message);
			return;
		}

		if (attachment.size > directory.getFreeSpace()) {
			String message = context.getString(R.string.message_view_status_no_space);
			displayMessageToUser(message);
			return;
		}

		if (!attachment.isContentAvailable()) {
			downloadAndSaveAttachmentTo((LocalPart) attachment.part, directory);
		}
		else {
			saveLocalAttachmentTo(directory);
		}
	}

	private void saveLocalAttachmentTo(File directory)
	{
		new SaveAttachmentAsyncTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, directory);
	}

	private File saveAttachmentWithUniqueFileName(File directory)
			throws IOException
	{
		String filename = FileHelper.sanitizeFilename(attachment.displayName);
		File file = FileHelper.createUniqueFile(directory, filename);

		writeAttachmentToStorage(file);
		addSavedAttachmentToDownloadsDatabase(file);
		return file;
	}

	private void writeAttachmentToStorage(File file)
			throws IOException
	{
		InputStream in = context.getContentResolver().openInputStream(attachment.internalUri);
		try {
			OutputStream out = new FileOutputStream(file);
			try {
				IOUtils.copy(in, out);
				out.flush();
			} finally {
				out.close();
			}
		} finally {
			in.close();
		}
	}

	private void addSavedAttachmentToDownloadsDatabase(File file)
	{
		String fileName = file.getName();
		String path = file.getAbsolutePath();
		long fileLength = file.length();
		String mimeType = attachment.mimeType;

		downloadManager.addCompletedDownload(fileName, fileName, true, mimeType, path, fileLength,
				true);
	}

	@WorkerThread
	private Intent getBestViewIntentAndSaveFile()
	{
		Uri intentDataUri;
		try {
			intentDataUri = AttachmentTempFileProvider.createTempUriForContentUri(context,
					attachment.internalUri);
		}
		catch (IOException e) {
			Timber.e(e, "Error creating temp file for attachment!");
			return null;
		}

		String displayName = attachment.displayName;
		String inferredMimeType = MimeUtility.getMimeTypeByExtension(displayName);

		IntentAndResolvedActivitiesCount resolvedIntentInfo;
		String mimeType = attachment.mimeType;
		if (MimeUtility.isDefaultMimeType(mimeType)) {
			resolvedIntentInfo = getBestViewIntentForMimeType(intentDataUri, inferredMimeType);
		}
		else {
			resolvedIntentInfo = getBestViewIntentForMimeType(intentDataUri, mimeType);
			if (!resolvedIntentInfo.hasResolvedActivities()
					&& !inferredMimeType.equals(mimeType)) {
				resolvedIntentInfo = getBestViewIntentForMimeType(intentDataUri, inferredMimeType);
			}
		}

		if (!resolvedIntentInfo.hasResolvedActivities()) {
			resolvedIntentInfo = getBestViewIntentForMimeType(intentDataUri,
					MimeUtility.DEFAULT_ATTACHMENT_MIME_TYPE);
		}

		Intent viewIntent;
		if (resolvedIntentInfo.hasResolvedActivities() && resolvedIntentInfo.containsFileUri()) {
			try {
				File tempFile = TemporaryAttachmentStore.getFileForWriting(context, displayName);
				writeAttachmentToStorage(tempFile);
				viewIntent = createViewIntentForFileUri(resolvedIntentInfo.getMimeType(),
						Uri.fromFile(tempFile));
			}
			catch (IOException e) {
				Timber.e(e, "Error while saving attachment to use file:// URI with ACTION_VIEW " +
								"Intent");
				viewIntent = createViewIntentForAttachmentProviderUri(intentDataUri,
						MimeUtility.DEFAULT_ATTACHMENT_MIME_TYPE);
			}
		}
		else {
			viewIntent = resolvedIntentInfo.getIntent();
		}
		return viewIntent;
	}

	private IntentAndResolvedActivitiesCount getBestViewIntentForMimeType(Uri contentUri,
			String mimeType)
	{
		Intent contentUriIntent = createViewIntentForAttachmentProviderUri(contentUri, mimeType);
		int contentUriActivitiesCount = getResolvedIntentActivitiesCount(contentUriIntent);

		if (contentUriActivitiesCount > 0) {
			return new IntentAndResolvedActivitiesCount(contentUriIntent,
					contentUriActivitiesCount);
		}

		File tempFile = TemporaryAttachmentStore.getFile(context, attachment.displayName);
		Uri tempFileUri = Uri.fromFile(tempFile);
		Intent fileUriIntent = createViewIntentForFileUri(mimeType, tempFileUri);
		int fileUriActivitiesCount = getResolvedIntentActivitiesCount(fileUriIntent);

		if (fileUriActivitiesCount > 0) {
			return new IntentAndResolvedActivitiesCount(fileUriIntent, fileUriActivitiesCount);
		}
		return new IntentAndResolvedActivitiesCount(contentUriIntent, contentUriActivitiesCount);
	}

	private Intent createViewIntentForAttachmentProviderUri(Uri contentUri, String mimeType)
	{
		Uri uri = AttachmentTempFileProvider.getMimeTypeUri(contentUri, mimeType);

		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setDataAndType(uri, mimeType);
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		addUiIntentFlags(intent);

		return intent;
	}

	private Intent createViewIntentForFileUri(String mimeType, Uri uri)
	{
		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setDataAndType(uri, mimeType);
		addUiIntentFlags(intent);
		return intent;
	}

	private void addUiIntentFlags(Intent intent)
	{
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
				| Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
	}

	private int getResolvedIntentActivitiesCount(Intent intent)
	{
		PackageManager packageManager = context.getPackageManager();

		List<ResolveInfo> resolveInfos =
				packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);

		return resolveInfos.size();
	}

	private void displayAttachmentNotSavedMessage()
	{
		String message = context.getString(R.string.message_view_status_attachment_not_saved);
		displayMessageToUser(message);
	}

	private void displayMessageToUser(String message)
	{
		Toast.makeText(context, message, Toast.LENGTH_LONG).show();
	}

	private static class IntentAndResolvedActivitiesCount
	{
		private Intent intent;
		private int activitiesCount;

		IntentAndResolvedActivitiesCount(Intent intent, int activitiesCount)
		{
			this.intent = intent;
			this.activitiesCount = activitiesCount;
		}

		public Intent getIntent()
		{
			return intent;
		}

		public boolean hasResolvedActivities()
		{
			return activitiesCount > 0;
		}

		public String getMimeType()
		{
			return intent.getType();
		}

		public boolean containsFileUri()
		{
			return "file".equals(intent.getData().getScheme());
		}
	}

	private class ViewAttachmentAsyncTask extends AsyncTask<Void, Void, Intent>
	{

		@Override
		protected void onPreExecute()
		{
			messageViewFragment.disableAttachmentButtons(attachment);
		}

		@Override
		protected Intent doInBackground(Void... params)
		{
			return getBestViewIntentAndSaveFile();
		}

		@Override
		protected void onPostExecute(Intent intent)
		{
			viewAttachment(intent);
			messageViewFragment.enableAttachmentButtons(attachment);
		}

		private void viewAttachment(Intent intent)
		{
			try {
				context.startActivity(intent);
			}
			catch (ActivityNotFoundException e) {
				Timber.e(e, "Could not display attachment of type %s", attachment.mimeType);

				String message = context.getString(R.string.message_view_no_viewer,
						attachment.mimeType);
				displayMessageToUser(message);
			}
		}
	}

	private class SaveAttachmentAsyncTask extends AsyncTask<File, Void, File>
	{

		@Override
		protected void onPreExecute()
		{
			messageViewFragment.disableAttachmentButtons(attachment);
		}

		@Override
		protected File doInBackground(File... params)
		{
			try {
				File directory = params[0];
				return saveAttachmentWithUniqueFileName(directory);
			}
			catch (IOException e) {
				Timber.e(e, "Error saving attachment");
				return null;
			}
		}

		@Override
		protected void onPostExecute(File file)
		{
			messageViewFragment.enableAttachmentButtons(attachment);
			if (file == null) {
				displayAttachmentNotSavedMessage();
			}
		}
	}
}
