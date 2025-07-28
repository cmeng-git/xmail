package org.atalk.xryptomail.activity.compose;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.os.BundleCompat;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;

import org.atalk.xryptomail.activity.MessageReference;
import org.atalk.xryptomail.activity.compose.ComposeCryptoStatus.AttachErrorState;
import org.atalk.xryptomail.activity.loader.AttachmentContentLoader;
import org.atalk.xryptomail.activity.loader.AttachmentInfoLoader;
import org.atalk.xryptomail.activity.misc.Attachment;
import org.atalk.xryptomail.activity.misc.Attachment.LoadingState;
import org.atalk.xryptomail.mailstore.AttachmentViewInfo;
import org.atalk.xryptomail.mailstore.LocalMessage;
import org.atalk.xryptomail.mailstore.MessageViewInfo;
import org.atalk.xryptomail.provider.RawMessageProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;


public class AttachmentPresenter {
    private static final String STATE_KEY_ATTACHMENTS = "org.atalk.xryptomail.activity.MessageCompose.attachments";
    private static final String STATE_KEY_WAITING_FOR_ATTACHMENTS = "waitingForAttachments";
    private static final String STATE_KEY_NEXT_LOADER_ID = "nextLoaderId";

    private static final String LOADER_ARG_ATTACHMENT = "attachment";
    private static final int LOADER_ID_MASK = 1 << 6;
    private static final int MAX_TOTAL_LOADERS = LOADER_ID_MASK - 1;
    private static final int REQUEST_CODE_ATTACHMENT_URI = 1;

    // injected state
    private final Context context;
    private final AttachmentMvpView attachmentMvpView;
    private final LoaderManager loaderManager;
    private final AttachmentsChangedListener listener;

    // persistent state
    private final LinkedHashMap<Uri, Attachment> attachments;
    private int nextLoaderId = 0;
    private WaitingAction actionToPerformAfterWaiting = WaitingAction.NONE;

    public AttachmentPresenter(Context context, AttachmentMvpView attachmentMvpView, LoaderManager loaderManager,
            AttachmentsChangedListener listener) {
        this.context = context;
        this.attachmentMvpView = attachmentMvpView;
        this.loaderManager = loaderManager;
        this.listener = listener;
        attachments = new LinkedHashMap<>();
    }

    public void onSaveInstanceState(Bundle outState) {
        outState.putString(STATE_KEY_WAITING_FOR_ATTACHMENTS, actionToPerformAfterWaiting.name());
        outState.putParcelableArrayList(STATE_KEY_ATTACHMENTS, createAttachmentList());
        outState.putInt(STATE_KEY_NEXT_LOADER_ID, nextLoaderId);
    }

    public void onRestoreInstanceState(Bundle savedInstanceState) {
        actionToPerformAfterWaiting = WaitingAction.valueOf(
                savedInstanceState.getString(STATE_KEY_WAITING_FOR_ATTACHMENTS));
        nextLoaderId = savedInstanceState.getInt(STATE_KEY_NEXT_LOADER_ID);

        ArrayList<Attachment> attachmentList = BundleCompat.getParcelableArrayList(savedInstanceState, STATE_KEY_ATTACHMENTS, Attachment.class);
        // noinspection  we know this is set in onSaveInstanceState
        for (Attachment attachment : attachmentList) {
            attachments.put(attachment.uri, attachment);
            attachmentMvpView.addAttachmentView(attachment);

            if (attachment.state == LoadingState.URI_ONLY) {
                initAttachmentInfoLoader(attachment);
            } else if (attachment.state == LoadingState.METADATA) {
                initAttachmentContentLoader(attachment);
            }
        }
    }

    public boolean checkOkForSendingOrDraftSaving() {
        if (actionToPerformAfterWaiting != WaitingAction.NONE) {
            return true;
        }

        if (hasLoadingAttachments()) {
            actionToPerformAfterWaiting = WaitingAction.SEND;
            attachmentMvpView.showWaitingForAttachmentDialog(actionToPerformAfterWaiting);
            return true;
        }
        return false;
    }

    private boolean hasLoadingAttachments() {
        for (Attachment attachment : attachments.values()) {
            Loader<?> loader = loaderManager.getLoader(attachment.loaderId);
            if (loader != null && loader.isStarted()) {
                return true;
            }
        }
        return false;
    }

    public ArrayList<Attachment> createAttachmentList() {
        return new ArrayList<>(attachments.values());
    }

    public List<Attachment> getAttachments() {
        return new ArrayList<>(attachments.values());
    }

    public void onClickAddAttachment(RecipientPresenter recipientPresenter) {
        ComposeCryptoStatus currentCachedCryptoStatus = recipientPresenter.getCurrentCachedCryptoStatus();
        if (currentCachedCryptoStatus == null) {
            return;
        }

        AttachErrorState maybeAttachErrorState = currentCachedCryptoStatus.getAttachErrorStateOrNull();
        if (maybeAttachErrorState != null) {
            recipientPresenter.showPgpAttachError(maybeAttachErrorState);
            return;
        }
        attachmentMvpView.showPickAttachmentDialog(REQUEST_CODE_ATTACHMENT_URI);
    }

    private void addAttachment(Uri uri) {
        addAttachment(uri, null);
    }

    private void addAttachment(AttachmentViewInfo attachmentViewInfo) {
        if (attachments.containsKey(attachmentViewInfo.internalUri)) {
            throw new IllegalStateException("Received the same attachmentViewInfo twice!");
        }

        int loaderId = getNextFreeLoaderId();
        Attachment attachment = Attachment.createAttachment(
                attachmentViewInfo.internalUri, loaderId, attachmentViewInfo.mimeType, true);
        attachment = attachment.deriveWithMetadataLoaded(
                attachmentViewInfo.mimeType, attachmentViewInfo.displayName, attachmentViewInfo.size);

        addAttachmentAndStartLoader(attachment);
    }

    public void addAttachment(Uri uri, String contentType) {
        addAttachment(uri, contentType, false);
    }

    private void addAttachment(Uri uri, String contentType, boolean allowMessageType) {
        if (attachments.containsKey(uri)) {
            return;
        }

        int loaderId = getNextFreeLoaderId();
        Attachment attachment = Attachment.createAttachment(uri, loaderId, contentType, allowMessageType);

        addAttachmentAndStartLoader(attachment);
    }

    public boolean loadNonInlineAttachments(MessageViewInfo messageViewInfo) {
        boolean allPartsAvailable = true;

        for (AttachmentViewInfo attachmentViewInfo : messageViewInfo.attachments) {
            if (attachmentViewInfo.inlineAttachment) {
                continue;
            }
            if (!attachmentViewInfo.isContentAvailable()) {
                allPartsAvailable = false;
                continue;
            }
            addAttachment(attachmentViewInfo);
        }

        return allPartsAvailable;
    }

    public void processMessageToForward(MessageViewInfo messageViewInfo) {
        boolean isMissingParts = !loadNonInlineAttachments(messageViewInfo);
        if (isMissingParts) {
            attachmentMvpView.showMissingAttachmentsPartialMessageWarning();
        }
    }

    public void processMessageToForwardAsAttachment(MessageViewInfo messageViewInfo) {
        if (messageViewInfo.isMessageIncomplete) {
            attachmentMvpView.showMissingAttachmentsPartialMessageForwardWarning();
        } else {
            LocalMessage localMessage = (LocalMessage) messageViewInfo.message;
            MessageReference messageReference = localMessage.makeMessageReference();
            Uri rawMessageUri = RawMessageProvider.getRawMessageUri(messageReference);

            addAttachment(rawMessageUri, "message/rfc822", true);
        }
    }

    private void addAttachmentAndStartLoader(Attachment attachment) {
        attachments.put(attachment.uri, attachment);
        listener.onAttachmentAdded();
        attachmentMvpView.addAttachmentView(attachment);

        if (attachment.state == LoadingState.URI_ONLY) {
            initAttachmentInfoLoader(attachment);
        } else if (attachment.state == LoadingState.METADATA) {
            initAttachmentContentLoader(attachment);
        } else {
            throw new IllegalStateException("Attachment can only be added in URI_ONLY or METADATA state!");
        }
    }

    private void initAttachmentInfoLoader(Attachment attachment) {
        if (attachment.state != LoadingState.URI_ONLY) {
            throw new IllegalStateException("initAttachmentInfoLoader can only be called for URI_ONLY state!");
        }

        Bundle bundle = new Bundle();
        bundle.putParcelable(LOADER_ARG_ATTACHMENT, attachment.uri);
        loaderManager.initLoader(attachment.loaderId, bundle, mAttachmentInfoLoaderCallback);
    }

    private void initAttachmentContentLoader(Attachment attachment) {
        if (attachment.state != LoadingState.METADATA) {
            throw new IllegalStateException("initAttachmentContentLoader can only be called for METADATA state!");
        }

        Bundle bundle = new Bundle();
        bundle.putParcelable(LOADER_ARG_ATTACHMENT, attachment.uri);
        loaderManager.initLoader(attachment.loaderId, bundle, mAttachmentContentLoaderCallback);
    }

    private int getNextFreeLoaderId() {
        if (nextLoaderId >= MAX_TOTAL_LOADERS) {
            throw new AssertionError("more than " + MAX_TOTAL_LOADERS + " attachments? hum.");
        }
        return LOADER_ID_MASK | nextLoaderId++;
    }

    private final LoaderManager.LoaderCallbacks<Attachment> mAttachmentInfoLoaderCallback
            = new LoaderManager.LoaderCallbacks<Attachment>() {
        @NonNull
        @Override
        public Loader<Attachment> onCreateLoader(int id, Bundle args) {
            Uri uri = BundleCompat.getParcelable(args, LOADER_ARG_ATTACHMENT, Uri.class);
            return new AttachmentInfoLoader(context, Objects.requireNonNull(attachments.get(uri)));
        }

        @Override
        public void onLoadFinished(Loader<Attachment> loader, Attachment attachment) {
            int loaderId = loader.getId();
            loaderManager.destroyLoader(loaderId);

            if (!attachments.containsKey(attachment.uri)) {
                return;
            }

            attachmentMvpView.updateAttachmentView(attachment);
            attachments.put(attachment.uri, attachment);
            initAttachmentContentLoader(attachment);
        }

        @Override
        public void onLoaderReset(@NonNull Loader<Attachment> loader) {
            // nothing to do
        }
    };

    private final LoaderManager.LoaderCallbacks<Attachment> mAttachmentContentLoaderCallback
            = new LoaderManager.LoaderCallbacks<Attachment>() {
        @NonNull
        @Override
        public Loader<Attachment> onCreateLoader(int id, Bundle args) {
            Uri uri = BundleCompat.getParcelable(args, LOADER_ARG_ATTACHMENT, Uri.class);
            return new AttachmentContentLoader(context, Objects.requireNonNull(attachments.get(uri)));
        }

        @Override
        public void onLoadFinished(Loader<Attachment> loader, Attachment attachment) {
            int loaderId = loader.getId();
            loaderManager.destroyLoader(loaderId);

            if (!attachments.containsKey(attachment.uri)) {
                return;
            }

            if (attachment.state == Attachment.LoadingState.COMPLETE) {
                attachmentMvpView.updateAttachmentView(attachment);
                attachments.put(attachment.uri, attachment);
            } else {
                attachments.remove(attachment.uri);
                attachmentMvpView.removeAttachmentView(attachment);
            }

            postPerformStalledAction();
        }

        @Override
        public void onLoaderReset(@NonNull Loader<Attachment> loader) {
            // nothing to do
        }
    };

    private void postPerformStalledAction() {
        new Handler(Looper.getMainLooper()).post(this::performStalledAction);
    }

    private void performStalledAction() {
        attachmentMvpView.dismissWaitingForAttachmentDialog();

        WaitingAction waitingFor = actionToPerformAfterWaiting;
        actionToPerformAfterWaiting = WaitingAction.NONE;

        switch (waitingFor) {
            case SEND: {
                attachmentMvpView.performSendAfterChecks();
                break;
            }
            case SAVE: {
                attachmentMvpView.performSaveAfterChecks();
                break;
            }
        }
    }

    private void addAttachmentsFromResultIntent(Intent data) {
        // TODO draftNeedsSaving = true
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int i = 0, end = clipData.getItemCount(); i < end; i++) {
                Uri uri = clipData.getItemAt(i).getUri();
                if (uri != null) {
                    addAttachment(uri);
                }
            }
            return;
        }
        Uri uri = data.getData();
        if (uri != null) {
            addAttachment(uri);
        }
    }

    public void attachmentProgressDialogCancelled() {
        actionToPerformAfterWaiting = WaitingAction.NONE;
    }

    public void onClickRemoveAttachment(Uri uri) {
        Attachment attachment = attachments.get(uri);
        loaderManager.destroyLoader(Objects.requireNonNull(attachment).loaderId);

        attachmentMvpView.removeAttachmentView(attachment);
        attachments.remove(uri);
        listener.onAttachmentRemoved();
    }

    public void onActivityResult(int resultCode, int requestCode, Intent data) {
        if (requestCode != REQUEST_CODE_ATTACHMENT_URI) {
            throw new AssertionError("onActivityResult must only be called for our request code");
        }
        if (resultCode != Activity.RESULT_OK) {
            return;
        }

        if (data == null) {
            return;
        }
        addAttachmentsFromResultIntent(data);
    }

    public enum WaitingAction {
        NONE,
        SEND,
        SAVE
    }

    public interface AttachmentMvpView {
        void showWaitingForAttachmentDialog(WaitingAction waitingAction);

        void dismissWaitingForAttachmentDialog();

        void showPickAttachmentDialog(int requestCode);

        void addAttachmentView(Attachment attachment);

        void removeAttachmentView(Attachment attachment);

        void updateAttachmentView(Attachment attachment);

        // TODO these should not really be here :\
        void performSendAfterChecks();

        void performSaveAfterChecks();

        void showMissingAttachmentsPartialMessageWarning();

        void showMissingAttachmentsPartialMessageForwardWarning();
    }

    public interface AttachmentsChangedListener {
        void onAttachmentAdded();

        void onAttachmentRemoved();
    }
}
