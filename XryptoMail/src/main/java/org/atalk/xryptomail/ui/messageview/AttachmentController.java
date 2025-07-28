package org.atalk.xryptomail.ui.messageview;

import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.atalk.xryptomail.Account;
import org.atalk.xryptomail.Preferences;
import org.atalk.xryptomail.R;
import org.atalk.xryptomail.controller.MessagingController;
import org.atalk.xryptomail.controller.SimpleMessagingListener;
import org.atalk.xryptomail.helper.FileBackend;
import org.atalk.xryptomail.mail.Message;
import org.atalk.xryptomail.mail.Part;
import org.atalk.xryptomail.mail.internet.MimeUtility;
import org.atalk.xryptomail.mailstore.AttachmentViewInfo;
import org.atalk.xryptomail.mailstore.LocalMessage;
import org.atalk.xryptomail.mailstore.LocalPart;
import org.atalk.xryptomail.provider.AttachmentTempFileProvider;

import timber.log.Timber;

public class AttachmentController {
    private final Context context;
    private final MessagingController controller;
    private final MessageViewFragment messageViewFragment;
    private final AttachmentViewInfo attachment;

    AttachmentController(MessagingController controller, MessageViewFragment messageViewFragment, AttachmentViewInfo attachment) {
        this.context = messageViewFragment.getApplicationContext();
        this.controller = controller;
        this.messageViewFragment = messageViewFragment;
        this.attachment = attachment;
    }

    public void viewAttachment() {
        if (!attachment.isContentAvailable()) {
            downloadAndViewAttachment((LocalPart) attachment.part);
        }
        else {
            viewLocalAttachment();
        }
    }

    public void saveAttachmentTo(Uri documentUri) {
        if (!attachment.isContentAvailable()) {
            downloadAndSaveAttachmentTo((LocalPart) attachment.part, documentUri);
        }
        else {
            saveLocalAttachmentTo(documentUri);
        }
    }

    private void downloadAndViewAttachment(LocalPart localPart) {
        downloadAttachment(localPart, this::viewLocalAttachment);
    }

    private void downloadAndSaveAttachmentTo(LocalPart localPart, final Uri documentUri) {
        downloadAttachment(localPart, () -> {
            messageViewFragment.refreshAttachmentThumbnail(attachment);
            saveLocalAttachmentTo(documentUri);
        });
    }

    private void downloadAttachment(LocalPart localPart, final Runnable attachmentDownloadedCallback) {
        String accountUuid = localPart.getAccountUuid();
        Account account = Preferences.getPreferences(context).getAccount(accountUuid);
        LocalMessage message = localPart.getMessage();

        messageViewFragment.showAttachmentLoadingDialog();
        controller.loadAttachment(account, message, attachment.part, new SimpleMessagingListener() {
            @Override
            public void loadAttachmentFinished(Account account, Message message, Part part) {
                attachment.setContentAvailable();
                messageViewFragment.hideAttachmentLoadingDialogOnMainThread();
                messageViewFragment.runOnUiThread(attachmentDownloadedCallback);
            }

            @Override
            public void loadAttachmentFailed(Account account, Message message,
                    Part part, String reason) {
                messageViewFragment.hideAttachmentLoadingDialogOnMainThread();
            }
        });
    }

    private void viewLocalAttachment() {
        new ViewAttachmentExecutor().execute();
    }

    private void saveLocalAttachmentTo(Uri documentUri) {
        new SaveAttachmentExecutor().execute(documentUri);
    }

    private void writeAttachment(Uri documentUri)
            throws IOException {
        ContentResolver contentResolver = context.getContentResolver();
        try (InputStream in = contentResolver.openInputStream(attachment.internalUri);
             OutputStream out = contentResolver.openOutputStream(documentUri)) {
            if (in != null && out != null) {
                FileBackend.copy(in, out);
                out.flush();
            }
        }
    }

    @WorkerThread
    private Intent getBestViewIntent() {
        Uri intentDataUri;
        try {
            intentDataUri = AttachmentTempFileProvider.createTempUriForContentUri(context, attachment.internalUri);
        } catch (IOException e) {
            Timber.e(e, "%s", "Error creating temp file for attachment!");
            return null;
        }

        String displayName = attachment.displayName;
        String inferredMimeType = MimeUtility.getMimeTypeByExtension(displayName);

        IntentAndResolvedActivitiesCount resolvedIntentInfo;
        String mimeType = attachment.mimeType;
        if (MimeUtility.isDefaultMimeType(mimeType)) {
            resolvedIntentInfo = getViewIntentForMimeType(intentDataUri, inferredMimeType);
        }
        else {
            resolvedIntentInfo = getViewIntentForMimeType(intentDataUri, mimeType);
            if (!resolvedIntentInfo.hasResolvedActivities() && !inferredMimeType.equals(mimeType)) {
                resolvedIntentInfo = getViewIntentForMimeType(intentDataUri, inferredMimeType);
            }
        }
        if (!resolvedIntentInfo.hasResolvedActivities()) {
            resolvedIntentInfo = getViewIntentForMimeType(intentDataUri, MimeUtility.DEFAULT_ATTACHMENT_MIME_TYPE);
        }
        return resolvedIntentInfo.getIntent();
    }

    private IntentAndResolvedActivitiesCount getViewIntentForMimeType(Uri contentUri, String mimeType) {
        Intent contentUriIntent = createViewIntentForAttachmentProviderUri(contentUri, mimeType);
        int contentUriActivitiesCount = getResolvedIntentActivitiesCount(contentUriIntent);

        return new IntentAndResolvedActivitiesCount(contentUriIntent, contentUriActivitiesCount);
    }

    private Intent createViewIntentForAttachmentProviderUri(Uri contentUri, String mimeType) {
        Uri uri = AttachmentTempFileProvider.getMimeTypeUri(contentUri, mimeType);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        addUiIntentFlags(intent);

        return intent;
    }

    private void addUiIntentFlags(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
    }

    private int getResolvedIntentActivitiesCount(Intent intent) {
        PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> resolveInfos =
                packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);

        return resolveInfos.size();
    }

    private void displayAttachmentNotSavedMessage() {
        String message = context.getString(R.string.message_view_status_attachment_not_saved);
        displayMessageToUser(message);
    }

    private void displayMessageToUser(String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

    private static class IntentAndResolvedActivitiesCount {
        private final Intent intent;
        private final int activitiesCount;

        IntentAndResolvedActivitiesCount(Intent intent, int activitiesCount) {
            this.intent = intent;
            this.activitiesCount = activitiesCount;
        }

        public Intent getIntent() {
            return intent;
        }

        public boolean hasResolvedActivities() {
            return activitiesCount > 0;
        }
    }

    private class ViewAttachmentExecutor {
        public ViewAttachmentExecutor() {
            messageViewFragment.disableAttachmentButtons(attachment);
        }

        public void execute() {
            try (ExecutorService eService = Executors.newWorkStealingPool()) {
                eService.execute(() -> {
                    final Intent intent = getBestViewIntent();

                    new Handler(Looper.getMainLooper()).post(() -> {
                        viewAttachment(intent);
                        messageViewFragment.enableAttachmentButtons(attachment);
                    });
                });
            }
        }

        private void viewAttachment(Intent intent) {
            try {
                context.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Timber.e(e, "Could not display attachment of type %s", attachment.mimeType);
                String message = context.getString(R.string.message_view_no_viewer, attachment.mimeType);
                displayMessageToUser(message);
            }
        }
    }

    private class SaveAttachmentExecutor {
        public SaveAttachmentExecutor() {
            messageViewFragment.disableAttachmentButtons(attachment);
        }

        public void execute(Uri documentUri) {
            try (ExecutorService eService = Executors.newWorkStealingPool()) {
                eService.execute(() -> {
                    boolean result = false;
                    try {
                        writeAttachment(documentUri);
                        result = true;
                    } catch (IOException e) {
                        Timber.e(e, "%s", "Error saving attachment");
                    }

                    final boolean success = result;
                    new Handler(Looper.getMainLooper()).post(() -> {
                        messageViewFragment.enableAttachmentButtons(attachment);
                        if (!success) {
                            displayAttachmentNotSavedMessage();
                        }
                    });
                });
            }
        }
    }
}
