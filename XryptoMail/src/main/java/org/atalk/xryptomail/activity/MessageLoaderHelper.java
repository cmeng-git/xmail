package org.atalk.xryptomail.activity;

import android.app.FragmentManager;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.Loader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.atalk.xryptomail.Account;
import org.atalk.xryptomail.Preferences;
import org.atalk.xryptomail.XryptoMail;
import org.atalk.xryptomail.autocrypt.AutocryptOperations;
import org.atalk.xryptomail.controller.MessagingController;
import org.atalk.xryptomail.controller.MessagingListener;
import org.atalk.xryptomail.controller.SimpleMessagingListener;
import org.atalk.xryptomail.helper.RetainFragment;
import org.atalk.xryptomail.mail.Flag;
import org.atalk.xryptomail.mailstore.LocalMessage;
import org.atalk.xryptomail.mailstore.MessageViewInfo;
import org.atalk.xryptomail.ui.crypto.MessageCryptoAnnotations;
import org.atalk.xryptomail.ui.crypto.MessageCryptoCallback;
import org.atalk.xryptomail.ui.crypto.MessageCryptoHelper;
import org.atalk.xryptomail.ui.crypto.OpenPgpApiFactory;
import org.atalk.xryptomail.ui.message.LocalMessageExtractorLoader;
import org.atalk.xryptomail.ui.message.LocalMessageLoader;
import org.openintents.openpgp.OpenPgpDecryptionResult;

import timber.log.Timber;


/**
 * This class is responsible for loading a message start to finish, and
 * retaining or reloading the loading state on configuration changes.
 *
 * In particular, it takes care of the following:
 * - load raw message data from the database, using LocalMessageLoader
 * - download partial message content if it is missing using MessagingController
 * - apply crypto operations if applicable, using MessageCryptoHelper
 * - extract MessageViewInfo from the message and crypto data using DecodeMessageLoader
 * - download complete message content for partially downloaded messages if requested
 *
 * No state is retained in this object itself. Instead, state is stored in the
 * message loaders and the MessageCryptoHelper which is stored in a
 * RetainFragment. The public interface is intended for use by an Activity or
 * Fragment, which should construct a new instance of this class in onCreate,
 * then call asyncStartOrResumeLoadingMessage to start or resume loading the
 * message, receiving callbacks when it is loaded.
 *
 * When the Activity or Fragment is ultimately destroyed, it should call
 * onDestroy, which stops loading and deletes all state kept in loaders and
 * fragments by this object. If it is only destroyed for a configuration
 * change, it should call onDestroyChangingConfigurations, which cancels any
 * further callbacks from this object but retains the loading state to resume
 * from at the next call to asyncStartOrResumeLoadingMessage.
 *
 * If the message is already loaded, a call to asyncStartOrResumeLoadingMessage
 * will typically load by starting the decode message loader, retrieving the
 * already cached LocalMessage. This message will be passed to the retained
 * CryptoMessageHelper instance, returning the already cached
 * MessageCryptoAnnotations. These two objects will be checked against the
 * retained DecodeMessageLoader, returning the final result. At each
 * intermediate step, the input of the respective loaders will be checked for
 * consistency, reloading if there is a mismatch.
 */
public class MessageLoaderHelper
{
    private static final int LOCAL_MESSAGE_LOADER_ID = 1;
    private static final int DECODE_MESSAGE_LOADER_ID = 2;


    // injected state - all of this may be cleared to avoid data leakage!
    private Context context;
    private FragmentManager fragmentManager;
    private LoaderManager loaderManager;
    @Nullable // make this explicitly nullable, make sure to cancel/ignore any operation if this is null
    private MessageLoaderCallbacks callback;
    private final boolean processSignedOnly;
    private Handler handler = new Handler(Looper.getMainLooper());

    // transient state
    private boolean onlyLoadMetadata;
    private MessageReference messageReference;
    private Account account;
    private LocalMessage localMessage;
    private MessageCryptoAnnotations messageCryptoAnnotations;
    private OpenPgpDecryptionResult cachedDecryptionResult;

    private MessageCryptoHelper messageCryptoHelper;

    public MessageLoaderHelper(Context context, LoaderManager loaderManager, FragmentManager fragmentManager,
            @NonNull MessageLoaderCallbacks callback)
    {
        this.context = context;
        this.loaderManager = loaderManager;
        this.fragmentManager = fragmentManager;
        this.callback = callback;
        processSignedOnly = XryptoMail.getOpenPgpSupportSignOnly();
    }

    // public interface
    @UiThread
    public void asyncStartOrResumeLoadingMessage(MessageReference messageReference, Parcelable cachedDecryptionResult)
    {
        onlyLoadMetadata = false;
        this.messageReference = messageReference;
        this.account = Preferences.getPreferences(context).getAccount(messageReference.getAccountUuid());

        if (cachedDecryptionResult != null) {
            if (cachedDecryptionResult instanceof OpenPgpDecryptionResult) {
                this.cachedDecryptionResult = (OpenPgpDecryptionResult) cachedDecryptionResult;
            }
            else {
                Timber.e("Got decryption result of unknown type - ignoring");
            }
        }
        startOrResumeLocalMessageLoader();
    }

    @UiThread
    public void asyncStartOrResumeLoadingMessageMetadata(MessageReference messageReference)
    {
        onlyLoadMetadata = true;
        this.messageReference = messageReference;
        this.account = Preferences.getPreferences(context).getAccount(messageReference.getAccountUuid());
        startOrResumeLocalMessageLoader();
    }

    @UiThread
    public void asyncReloadMessage()
    {
        startOrResumeLocalMessageLoader();
    }

    @UiThread
    public void asyncRestartMessageCryptoProcessing()
    {
        cancelAndClearCryptoOperation();
        cancelAndClearDecodeLoader();
        if (XryptoMail.isOpenPgpProviderConfigured()) {
            startOrResumeCryptoOperation();
        }
        else {
            startOrResumeDecodeMessage();
        }
    }

    /**
     * Cancels all loading processes, prevents future callbacks, and destroys all loading state.
     */
    @UiThread
    public void onDestroy()
    {
        if (messageCryptoHelper != null) {
            messageCryptoHelper.cancelIfRunning();
        }
        callback = null;
        context = null;
        fragmentManager = null;
        loaderManager = null;
    }

    /**
     * Prevents future callbacks, but retains loading state to pick up from in a call to
     * asyncStartOrResumeLoadingMessage in a new instance of this class.
     */
    @UiThread
    public void onDestroyChangingConfigurations()
    {
        cancelAndClearDecodeLoader();

        if (messageCryptoHelper != null) {
            messageCryptoHelper.detachCallback();
        }
        callback = null;
        context = null;
        fragmentManager = null;
        loaderManager = null;
    }

    @UiThread
    public void downloadCompleteMessage()
    {
        startDownloadingMessageBody(true);
    }

    @UiThread
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        messageCryptoHelper.onActivityResult(requestCode, resultCode, data);
    }

    // load from database
    private void startOrResumeLocalMessageLoader()
    {
        LocalMessageLoader loader = (LocalMessageLoader) loaderManager.<LocalMessage>getLoader(LOCAL_MESSAGE_LOADER_ID);
        boolean isLoaderStale = (loader == null) || !loader.isCreatedFor(messageReference);

        if (isLoaderStale) {
            Timber.d("Creating new local message loader");
            cancelAndClearCryptoOperation();
            cancelAndClearDecodeLoader();
            loaderManager.restartLoader(LOCAL_MESSAGE_LOADER_ID, null, localMessageLoaderCallback);
        }
        else {
            Timber.d("Reusing local message loader");
            loaderManager.initLoader(LOCAL_MESSAGE_LOADER_ID, null, localMessageLoaderCallback);
        }
    }

    @UiThread
    private void onLoadMessageFromDatabaseFinished()
    {
        if (callback == null) {
            throw new IllegalStateException("unexpected call when callback is already detached");
        }
        callback.onMessageDataLoadFinished(localMessage);

        boolean downloadedCompletely = localMessage.isSet(Flag.X_DOWNLOADED_FULL);
        boolean downloadedPartially = localMessage.isSet(Flag.X_DOWNLOADED_PARTIAL);
        boolean messageIncomplete = !downloadedCompletely && !downloadedPartially;
        if (messageIncomplete) {
            startDownloadingMessageBody(false);
            return;
        }

        if (onlyLoadMetadata) {
            MessageViewInfo messageViewInfo = MessageViewInfo.createForMetadataOnly(localMessage, !downloadedCompletely);
            onDecodeMessageFinished(messageViewInfo);
            return;
        }

        // ### cmeng - can implement Xrypto engine here???
        if (XryptoMail.isOpenPgpProviderConfigured()) {
            startOrResumeCryptoOperation();
            return;
        }
        startOrResumeDecodeMessage();
    }

    private void onLoadMessageFromDatabaseFailed()
    {
        if (callback == null) {
            throw new IllegalStateException("unexpected call when callback is already detached");
        }
        callback.onMessageDataLoadFailed();
    }

    private void cancelAndClearLocalMessageLoader()
    {
        loaderManager.destroyLoader(LOCAL_MESSAGE_LOADER_ID);
    }

    private LoaderCallbacks<LocalMessage> localMessageLoaderCallback = new LoaderCallbacks<LocalMessage>()
    {
        @Override
        public Loader<LocalMessage> onCreateLoader(int id, Bundle args)
        {
            if (id != LOCAL_MESSAGE_LOADER_ID) {
                throw new IllegalStateException("loader id must be message loader id");
            }
            MessagingController messagingController = MessagingController.getInstance(context);
            return new LocalMessageLoader(context, messagingController, account, messageReference, onlyLoadMetadata);
        }

        @Override
        public void onLoadFinished(Loader<LocalMessage> loader, LocalMessage message)
        {
            if (loader.getId() != LOCAL_MESSAGE_LOADER_ID) {
                throw new IllegalStateException("loader id must be message loader id");
            }

            localMessage = message;
            if (message == null) {
                onLoadMessageFromDatabaseFailed();
            }
            else {
                onLoadMessageFromDatabaseFinished();
            }
        }

        @Override
        public void onLoaderReset(Loader<LocalMessage> loader)
        {
            if (loader.getId() != LOCAL_MESSAGE_LOADER_ID) {
                throw new IllegalStateException("loader id must be message loader id");
            }
            // Do nothing
        }
    };

    // process with crypto helper
    private void startOrResumeCryptoOperation()
    {
        RetainFragment<MessageCryptoHelper> retainCryptoHelperFragment = getMessageCryptoHelperRetainFragment(true);
        if (retainCryptoHelperFragment.hasData()) {
            messageCryptoHelper = retainCryptoHelperFragment.getData();
        }
        if (messageCryptoHelper == null || !messageCryptoHelper.isConfiguredForOpenPgpProvider()) {
            messageCryptoHelper = new MessageCryptoHelper(
                    context, new OpenPgpApiFactory(), AutocryptOperations.getInstance());
            retainCryptoHelperFragment.setData(messageCryptoHelper);
        }
        messageCryptoHelper.asyncStartOrResumeProcessingMessage(
                localMessage, messageCryptoCallback, cachedDecryptionResult, processSignedOnly);
    }

    private void cancelAndClearCryptoOperation()
    {
        RetainFragment<MessageCryptoHelper> retainCryptoHelperFragment = getMessageCryptoHelperRetainFragment(false);
        if (retainCryptoHelperFragment != null) {
            if (retainCryptoHelperFragment.hasData()) {
                messageCryptoHelper = retainCryptoHelperFragment.getData();
                messageCryptoHelper.cancelIfRunning();
                messageCryptoHelper = null;
            }
            retainCryptoHelperFragment.clearAndRemove(fragmentManager);
        }
    }

    private RetainFragment<MessageCryptoHelper> getMessageCryptoHelperRetainFragment(boolean createIfNotExists)
    {
        if (createIfNotExists) {
            return RetainFragment.findOrCreate(fragmentManager, "crypto_helper_" + messageReference.hashCode());
        }
        else {
            return RetainFragment.findOrNull(fragmentManager, "crypto_helper_" + messageReference.hashCode());
        }
    }

    private MessageCryptoCallback messageCryptoCallback = new MessageCryptoCallback()
    {
        @Override
        public void onCryptoHelperProgress(int current, int max)
        {
            if (callback == null) {
                throw new IllegalStateException("unexpected call when callback is already detached");
            }
            callback.setLoadingProgress(current, max);
        }

        @Override
        public void onCryptoOperationsFinished(MessageCryptoAnnotations annotations)
        {
            if (callback == null) {
                throw new IllegalStateException("unexpected call when callback is already detached");
            }
            messageCryptoAnnotations = annotations;
            startOrResumeDecodeMessage();
        }

        @Override
        public void startPendingIntentForCryptoHelper(IntentSender si, int requestCode, Intent fillIntent,
                int flagsMask, int flagValues, int extraFlags)
        {
            if (callback == null) {
                throw new IllegalStateException("unexpected call when callback is already detached");
            }
            callback.startIntentSenderForMessageLoaderHelper(si, requestCode, fillIntent,
                    flagsMask, flagValues, extraFlags);
        }
    };

    // decode message
    private void startOrResumeDecodeMessage()
    {
        LocalMessageExtractorLoader loader =
                (LocalMessageExtractorLoader) loaderManager.<MessageViewInfo>getLoader(DECODE_MESSAGE_LOADER_ID);
        boolean isLoaderStale = (loader == null) || !loader.isCreatedFor(localMessage, messageCryptoAnnotations);

        if (isLoaderStale) {
            Timber.d("Creating new decode message loader");
            loaderManager.restartLoader(DECODE_MESSAGE_LOADER_ID, null, decodeMessageLoaderCallback);
        }
        else {
            Timber.d("Reusing decode message loader");
            loaderManager.initLoader(DECODE_MESSAGE_LOADER_ID, null, decodeMessageLoaderCallback);
        }
    }

    private void onDecodeMessageFinished(MessageViewInfo messageViewInfo)
    {
        if (callback == null) {
            throw new IllegalStateException("unexpected call when callback is already detached");
        }

        if (messageViewInfo == null) {
            messageViewInfo = createErrorStateMessageViewInfo();
            callback.onMessageViewInfoLoadFailed(messageViewInfo);
            return;
        }
        callback.onMessageViewInfoLoadFinished(messageViewInfo);
    }

    @NonNull
    private MessageViewInfo createErrorStateMessageViewInfo()
    {
        boolean isMessageIncomplete = !localMessage.isSet(Flag.X_DOWNLOADED_FULL);
        return MessageViewInfo.createWithErrorState(localMessage, isMessageIncomplete);
    }

    private void cancelAndClearDecodeLoader()
    {
        loaderManager.destroyLoader(DECODE_MESSAGE_LOADER_ID);
    }

    private LoaderCallbacks<MessageViewInfo> decodeMessageLoaderCallback = new LoaderCallbacks<MessageViewInfo>()
    {
        @Override
        public Loader<MessageViewInfo> onCreateLoader(int id, Bundle args)
        {
            if (id != DECODE_MESSAGE_LOADER_ID) {
                throw new IllegalStateException("loader id must be message decoder id");
            }
            return new LocalMessageExtractorLoader(context, localMessage, messageCryptoAnnotations);
        }

        @Override
        public void onLoadFinished(Loader<MessageViewInfo> loader, MessageViewInfo messageViewInfo)
        {
            if (loader.getId() != DECODE_MESSAGE_LOADER_ID) {
                throw new IllegalStateException("loader id must be message decoder id");
            }
            onDecodeMessageFinished(messageViewInfo);
        }

        @Override
        public void onLoaderReset(Loader<MessageViewInfo> loader)
        {
            if (loader.getId() != DECODE_MESSAGE_LOADER_ID) {
                throw new IllegalStateException("loader id must be message decoder id");
            }
            // Do nothing
        }
    };

    // download missing body
    private void startDownloadingMessageBody(boolean downloadComplete)
    {
        if (downloadComplete) {
            MessagingController.getInstance(context).loadMessageRemote(
                    account, messageReference.getFolderServerId(), messageReference.getUid(), downloadMessageListener);
        }
        else {
            MessagingController.getInstance(context).loadMessageRemotePartial(
                    account, messageReference.getFolderServerId(), messageReference.getUid(), downloadMessageListener);
        }
    }

    private void onMessageDownloadFinished()
    {
        if (callback == null) {
            return;
        }

        cancelAndClearLocalMessageLoader();
        cancelAndClearDecodeLoader();
        cancelAndClearCryptoOperation();
        startOrResumeLocalMessageLoader();
    }

    private void onDownloadMessageFailed(final Throwable t)
    {
        if (callback == null) {
            return;
        }

        if (t instanceof IllegalArgumentException) {
            callback.onDownloadErrorMessageNotFound();
        }
        else {
            callback.onDownloadErrorNetworkError();
        }
    }

    MessagingListener downloadMessageListener = new SimpleMessagingListener()
    {
        @Override
        public void loadMessageRemoteFinished(final Account account, final String folder, final String uid)
        {
            handler.post(() -> {
                if (!messageReference.equals(account.getUuid(), folder, uid)) {
                    return;
                }
                onMessageDownloadFinished();
            });
        }

        @Override
        public void loadMessageRemoteFailed(Account account, String folder, String uid, final Throwable t)
        {
            handler.post(() -> onDownloadMessageFailed(t));
        }
    };

    // callback interface
    public interface MessageLoaderCallbacks
    {
        void onMessageDataLoadFinished(LocalMessage message);

        void onMessageDataLoadFailed();

        void onMessageViewInfoLoadFinished(MessageViewInfo messageViewInfo);

        void onMessageViewInfoLoadFailed(MessageViewInfo messageViewInfo);

        void setLoadingProgress(int current, int max);

        void startIntentSenderForMessageLoaderHelper(IntentSender si, int requestCode, Intent fillIntent, int flagsMask,
                int flagValues, int extraFlags);

        void onDownloadErrorMessageNotFound();

        void onDownloadErrorNetworkError();
    }
}
