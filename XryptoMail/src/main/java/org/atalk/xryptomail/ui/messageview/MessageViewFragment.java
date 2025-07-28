package org.atalk.xryptomail.ui.messageview;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.james.mime4j.util.MimeUtil;
import org.atalk.xryptomail.Account;
import org.atalk.xryptomail.Preferences;
import org.atalk.xryptomail.R;
import org.atalk.xryptomail.XryptoMail;
import org.atalk.xryptomail.activity.ChooseFolder;
import org.atalk.xryptomail.activity.MessageLoaderHelper;
import org.atalk.xryptomail.activity.MessageLoaderHelper.MessageLoaderCallbacks;
import org.atalk.xryptomail.activity.MessageReference;
import org.atalk.xryptomail.activity.compose.RecipientPresenter.XryptoMode;
import org.atalk.xryptomail.activity.setup.OpenPgpAppSelectDialog;
import org.atalk.xryptomail.controller.MessagingController;
import org.atalk.xryptomail.fragment.AttachmentDownloadDialogFragment;
import org.atalk.xryptomail.fragment.BaseFragment;
import org.atalk.xryptomail.fragment.ConfirmationDialogFragment;
import org.atalk.xryptomail.fragment.ConfirmationDialogFragment.ConfirmationDialogFragmentListener;
import org.atalk.xryptomail.mail.Address;
import org.atalk.xryptomail.mail.Flag;
import org.atalk.xryptomail.mail.Message;
import org.atalk.xryptomail.mail.MessagingException;
import org.atalk.xryptomail.mail.internet.MimeHeader;
import org.atalk.xryptomail.mail.internet.MimeMessage;
import org.atalk.xryptomail.mail.internet.TextBody;
import org.atalk.xryptomail.mailstore.AttachmentViewInfo;
import org.atalk.xryptomail.mailstore.LocalMessage;
import org.atalk.xryptomail.mailstore.MessageViewInfo;
import org.atalk.xryptomail.ui.messageview.CryptoInfoDialog.OnClickShowCryptoKeyListener;
import org.atalk.xryptomail.ui.messageview.MessageCryptoPresenter.MessageCryptoMvpView;
import org.atalk.xryptomail.view.MessageCryptoDisplayStatus;
import org.atalk.xryptomail.view.MessageHeader;

import timber.log.Timber;

public class MessageViewFragment extends BaseFragment implements ConfirmationDialogFragmentListener,
        AttachmentViewCallback, OnClickShowCryptoKeyListener {
    private static final String ARG_REFERENCE = "reference";

    private static final int ACTIVITY_CHOOSE_FOLDER_MOVE = 1;
    private static final int ACTIVITY_CHOOSE_FOLDER_COPY = 2;
    private static final int REQUEST_CODE_CREATE_DOCUMENT = 3;

    public static final int REQUEST_MASK_LOADER_HELPER = (1 << 8);
    public static final int REQUEST_MASK_CRYPTO_PRESENTER = (1 << 9);

    public static final int PROGRESS_THRESHOLD_MILLIS = 500 * 1000;

    // STEALTH_MESSAGE_LIFESPAN is in unit of seconds and decremental @ STEALTH_TIMER_TICK_CHECK
    public static final int STEALTH_MESSAGE_LIFESPAN = 30;
    private static final int STEALTH_TIMER_TICK_CHECK = 1;

    // To allow fast next/previous scroll past Stealth message elapsed period in second without counting down
    private static final int STEALTH_GRACE_PERIOD = 3;
    // Stealth message is deleted if user press backspace and exceeded STEALTH_READ_MIN_TIME
    private static final int STEALTH_READ_MIN_TIME = 20;

    // STEALTH_MESSAGE_COUNT_DOWN_INTERVAL is in unit of milliseconds
    private static final long STEALTH_MESSAGE_COUNT_DOWN_INTERVAL = 1000;

    private static int mCountDownValue;
    private Handler stealthMessageTimerHandler = null;
    private Timer stealthMessageCountDownTimer = null;

    private MessageTopView mMessageView;
    private Account mAccount;
    private MessageReference mMessageReference;
    private LocalMessage mMessage;
    private MessagingController mController;
    private MessageLoaderHelper messageLoaderHelper;
    private MessageCryptoPresenter messageCryptoPresenter;
    private Long showProgressThreshold;

    /**
     * Used to temporarily store the destination folder for refile operations if a confirmation dialog is shown.
     */
    private String mDstFolder;
    private MessageViewFragmentListener mFragmentListener;

    /**
     * {@code true} after {@link #onCreate(Bundle)} has been executed. This is used by
     * {@code MessageList.configureMenu()} to make sure the fragment has been initialized before it is used.
     */
    private boolean mInitialized = false;
    private AttachmentViewInfo currentAttachmentViewInfo;

    public static MessageViewFragment newInstance(MessageReference reference) {
        MessageViewFragment fragment = new MessageViewFragment();

        Bundle args = new Bundle();
        args.putString(ARG_REFERENCE, reference.toIdentityString());
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    // cmeng: do not use onAttach(Context) - crash in Note-5
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mFragmentListener = (MessageViewFragmentListener) mContext;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.getClass() + " must implement MessageViewFragmentListener");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // This fragments adds options to the action bar
        setHasOptionsMenu(true);
        mController = MessagingController.getInstance(mContext);
        messageCryptoPresenter = new MessageCryptoPresenter(savedInstanceState, messageCryptoMvpView);
        messageLoaderHelper = new MessageLoaderHelper(mContext, getLoaderManager(), getFragmentManager(), messageLoaderCallbacks);
        mInitialized = true;
    }

    @Override
    public void onResume() {
        super.onResume();
        messageCryptoPresenter.onResume();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        messageCryptoPresenter.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        boolean isChangingConfigurations = mFragmentActivity != null && mFragmentActivity.isChangingConfigurations();
        if (isChangingConfigurations) {
            messageLoaderHelper.onDestroyChangingConfigurations();
        }
        else
            messageLoaderHelper.onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Context context = new ContextThemeWrapper(inflater.getContext(),
                XryptoMail.getXMThemeResourceId(XryptoMail.getXMMessageViewTheme()));
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        View view = layoutInflater.inflate(R.layout.message, container, false);

        mMessageView = view.findViewById(R.id.message_view);
        mMessageView.setAttachmentCallback(this);
        mMessageView.setMessageCryptoPresenter(messageCryptoPresenter);

        mMessageView.setOnToggleFlagClickListener(v -> onToggleFlagged());

        mMessageView.setOnDownloadButtonClickListener(v -> {
            mMessageView.disableDownloadButton();
            messageLoaderHelper.downloadCompleteMessage();
        });

        mFragmentListener.messageHeaderViewAvailable(mMessageView.getMessageHeaderView());
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle arguments = getArguments();
        String messageReferenceString = arguments.getString(ARG_REFERENCE);
        MessageReference messageReference = MessageReference.parse(messageReferenceString);
        displayMessage(messageReference);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        // cmeng - Stealth message is auto deleted if it exceeds STEALTH_READ_MIN_TIME on user Back Pressed
        if (mMessageView.getCryptoMode() == XryptoMode.STEALTH) {
            cancelStealthTimer();
            int elapseTime = (STEALTH_MESSAGE_LIFESPAN - mCountDownValue);
            if (mMessage != null) {
                if ((elapseTime > STEALTH_GRACE_PERIOD) && (elapseTime <= STEALTH_READ_MIN_TIME)) {
                    try {
                        mMessage.updateStealthTimer(mCountDownValue);
                    } catch (MessagingException e) {
                        e.printStackTrace();
                    }
                }
                else if (elapseTime > STEALTH_READ_MIN_TIME) {
                    mController.deleteMessages(Collections.singletonList(mMessageReference), null);
                    sendStealthAck(mMessage);
                    Toast.makeText(mContext, R.string.stealth_deleted_message, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void displayMessage(MessageReference messageReference) {
        mMessageReference = messageReference;
        Timber.d("MessageView displaying message %s", mMessageReference);

        mAccount = Preferences.getPreferences(getApplicationContext()).getAccount(mMessageReference.getAccountUuid());
        messageLoaderHelper.asyncStartOrResumeLoadingMessage(messageReference, null);
        mFragmentListener.updateMenu();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) mFragmentActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
        View decorView = mFragmentActivity.getWindow().getDecorView();
        if (imm != null) {
            imm.hideSoftInputFromWindow(decorView.getApplicationWindowToken(), 0);
        }
    }

    private void showUnableToDecodeError() {
        Toast.makeText(mContext, R.string.message_view_toast_unable_to_display_message, Toast.LENGTH_SHORT).show();
    }

    private void showMessage(MessageViewInfo messageViewInfo) {
        hideKeyboard();
        boolean handledByCryptoPresenter
                = messageCryptoPresenter.maybeHandleShowMessage(mMessageView, mAccount, messageViewInfo);
        if (!handledByCryptoPresenter) {
            mMessageView.showMessage(mAccount, messageViewInfo);
            if (XryptoMail.isOpenPgpProviderConfigured()) {
                mMessageView.getMessageHeaderView().setCryptoStatusDisabled();
            }
            else {
                mMessageView.getMessageHeaderView().hideCryptoStatus();
            }
        }

        if (messageViewInfo.subject != null) {
            displaySubject(messageViewInfo.subject);
        }
    }

    private void displayHeaderForLoadingMessage(LocalMessage message) {
        mMessageView.setHeaders(message, mAccount);
        if (XryptoMail.isOpenPgpProviderConfigured()) {
            mMessageView.getMessageHeaderView().setCryptoStatusLoading();
        }
        displayMessageSubject(getSubjectForMessage(message));
        mFragmentListener.updateMenu();
    }

    private void displaySubject(String subject) {
        if (TextUtils.isEmpty(subject)) {
            subject = mContext.getString(R.string.general_no_subject);
        }
        mMessageView.setSubject(subject);
    }

    /**
     * Called from UI thread when user select Delete
     */
    public void onDelete() {
        if (XryptoMail.confirmDelete() || (XryptoMail.confirmDeleteStarred() && mMessage.isSet(Flag.FLAGGED))) {
            showDialog(R.id.dialog_confirm_delete);
        }
        else {
            delete();
        }
    }

    public void onToggleAllHeadersView() {
        mMessageView.getMessageHeaderView().onShowAdditionalHeaders();
    }

    public boolean allHeadersVisible() {
        return mMessageView.getMessageHeaderView().additionalHeadersVisible();
    }

    private void delete() {
        if (mMessage != null) {
            // Disable the delete button after it is tapped - prevent accidental second click
            mFragmentListener.disableDeleteAction();
            LocalMessage messageToDelete = mMessage;

            // always force to message list after stealth deleted - avoid ripple deletion effect
            if (mMessageView.getCryptoMode() == XryptoMode.STEALTH) {
                cancelStealthTimer();
                sendStealthAck(messageToDelete);
                mFragmentListener.showMessageList();
            }
            else {
                mFragmentListener.showNextMessageOrReturn();
            }
            mController.deleteMessage(mMessageReference, null);
        }
    }

    private void sendStealthAck(LocalMessage msgStealth) {
        sendStealthAckMessage(mContext, mAccount, msgStealth);
    }

    private static void sendStealthAckMessage(Context context, Account account, LocalMessage msgStealth) {
        String subject = String.format(context.getString(R.string.stealth_ack_subject), msgStealth.getSubject());
        String contents = String.format(context.getString(R.string.stealth_ack_fmt1),
                account.getIdentity(0).getEmail(),
                msgStealth.getSentDate().toString());
        contents += "\r\n\r\n" + String.format(context.getString(R.string.stealth_ack_fmt2),
                new Date().toString());

        Address srcAddr = new Address(account.getIdentity(0).getEmail());
        Address[] dstAddr = msgStealth.getFrom();
        MimeMessage message = new MimeMessage();
        message.addSentDate(new Date(), false);
        message.setFrom(srcAddr);
        message.setRecipients(Message.RecipientType.TO, dstAddr);
        message.setSubject(subject);
        message.setHeader("User-Agent", context.getString(R.string.message_header_mua));
        message.setHeader(MimeHeader.HEADER_CONTENT_TYPE, "text/plain; charset=utf-8");
        message.setHeader(MimeHeader.HEADER_CONTENT_TRANSFER_ENCODING, MimeUtil.ENC_QUOTED_PRINTABLE);

        TextBody body = new TextBody(contents);
        body.setEncoding(MimeUtil.ENC_QUOTED_PRINTABLE);
        body.setComposedMessageLength(contents.length());
        body.setComposedMessageOffset(0);
        message.setBody(body);

        MessagingController.getInstance(context).sendMessage(account, message, null);
    }

    // Stop all stealth counter activities
    private void cancelStealthTimer() {
        if (stealthMessageCountDownTimer != null) {
            stealthMessageCountDownTimer.cancel();
            stealthMessageCountDownTimer = null;
            stealthMessageTimerHandler = null;
        }
    }

    public void onRefile(String dstFolder) {
        if (!mController.isMoveCapable(mAccount)) {
            return;
        }
        if (!mController.isMoveCapable(mMessageReference)) {
            XryptoMail.showToastMessage(R.string.move_copy_cannot_copy_unsynced_message);
            return;
        }

        if ((dstFolder == null) || XryptoMail.FOLDER_NONE.equalsIgnoreCase(dstFolder)) {
            return;
        }

        if (mAccount.getSpamFolder().equals(dstFolder) && XryptoMail.confirmSpam()) {
            mDstFolder = dstFolder;
            showDialog(R.id.dialog_confirm_spam);
        }
        else {
            refileMessage(dstFolder);
        }
    }

    private void refileMessage(String dstFolder) {
        String srcFolder = mMessageReference.getFolderServerId();
        MessageReference messageToMove = mMessageReference;
        mFragmentListener.showNextMessageOrReturn();
        mController.moveMessage(mAccount, srcFolder, messageToMove, dstFolder);
    }

    public void onReply() {
        if (mMessage != null) {
            mFragmentListener.onReply(mMessage.makeMessageReference(), messageCryptoPresenter.getDecryptionResultForReply());
        }
    }

    public void onReplyAll() {
        if (mMessage != null) {
            mFragmentListener.onReplyAll(mMessage.makeMessageReference(), messageCryptoPresenter.getDecryptionResultForReply());
        }
    }

    public void onForward() {
        if (mMessage != null) {
            mFragmentListener.onForward(mMessage.makeMessageReference(), messageCryptoPresenter.getDecryptionResultForReply());
        }
    }

    public void onForwardAsAttachment() {
        if (mMessage != null) {
            mFragmentListener.onForwardAsAttachment(mMessage.makeMessageReference(), messageCryptoPresenter.getDecryptionResultForReply());
        }
    }

    public void onResendMessage() {
        if (mMessage != null) {
            mFragmentListener.onResendMessage(mMessage.makeMessageReference(), messageCryptoPresenter.getDecryptionResultForReply());
        }
    }

    public void onToggleFlagged() {
        if (mMessage != null) {
            boolean newState = !mMessage.isSet(Flag.FLAGGED);
            mController.setFlag(mAccount, mMessage.getFolder().getServerId(),
                    Collections.singletonList(mMessage), Flag.FLAGGED, newState);
            mMessageView.setHeaders(mMessage, mAccount);
        }
    }

    public void onMove() {
        if ((!mController.isMoveCapable(mAccount))
                || (mMessage == null)) {
            return;
        }
        if (!mController.isMoveCapable(mMessageReference)) {
            XryptoMail.showToastMessage(R.string.move_copy_cannot_copy_unsynced_message);
            return;
        }
        startRefileActivity(ACTIVITY_CHOOSE_FOLDER_MOVE);
    }

    public void onCopy() {
        if ((!mController.isCopyCapable(mAccount))
                || (mMessage == null)) {
            return;
        }
        if (!mController.isCopyCapable(mMessageReference)) {
            XryptoMail.showToastMessage(R.string.move_copy_cannot_copy_unsynced_message);
            return;
        }
        startRefileActivity(ACTIVITY_CHOOSE_FOLDER_COPY);
    }

    public void onArchive() {
        onRefile(mAccount.getArchiveFolder());
    }

    public void onSpam() {
        onRefile(mAccount.getSpamFolder());
    }

    public void onSelectText() {
        // FIXME
        // mMessageView.beginSelectingText();
    }

    private void startRefileActivity(int activity) {
        Intent intent = new Intent(mContext, ChooseFolder.class);
        intent.putExtra(ChooseFolder.EXTRA_ACCOUNT, mAccount.getUuid());
        intent.putExtra(ChooseFolder.EXTRA_CUR_FOLDER, mMessageReference.getFolderServerId());
        intent.putExtra(ChooseFolder.EXTRA_SEL_FOLDER, mAccount.getLastSelectedFolder());
        intent.putExtra(ChooseFolder.EXTRA_MESSAGE, mMessageReference.toIdentityString());
        startActivityForResult(intent, activity);
    }

    private void startOpenPgpChooserActivity() {
        Intent i = new Intent(mContext, OpenPgpAppSelectDialog.class);
        startActivity(i);
    }


    public void onPendingIntentResult(int requestCode, int resultCode, Intent data) {
        if ((requestCode & REQUEST_MASK_LOADER_HELPER) == REQUEST_MASK_LOADER_HELPER) {
            hideKeyboard();
            requestCode ^= REQUEST_MASK_LOADER_HELPER;
            messageLoaderHelper.onActivityResult(requestCode, resultCode, data);
            return;
        }

        if ((requestCode & REQUEST_MASK_CRYPTO_PRESENTER) == REQUEST_MASK_CRYPTO_PRESENTER) {
            requestCode ^= REQUEST_MASK_CRYPTO_PRESENTER;
            messageCryptoPresenter.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            return;
        }

        // Note: because fragments do not have a startIntentSenderForResult method, pending intent activities are
        // launched through the MessageList activity, and delivered back via onPendingIntentResult()
        switch (requestCode) {
            case REQUEST_CODE_CREATE_DOCUMENT: {
                if (data != null && data.getData() != null) {
                    getAttachmentController(currentAttachmentViewInfo).saveAttachmentTo(data.getData());
                }
                break;
            }
            case ACTIVITY_CHOOSE_FOLDER_MOVE:
            case ACTIVITY_CHOOSE_FOLDER_COPY: {
                if (data == null) {
                    return;
                }

                String destFolder = data.getStringExtra(ChooseFolder.EXTRA_NEW_FOLDER);
                String messageReferenceString = data.getStringExtra(ChooseFolder.EXTRA_MESSAGE);
                MessageReference ref = MessageReference.parse(messageReferenceString);
                if (mMessageReference.equals(ref)) {
                    mAccount.setLastSelectedFolder(destFolder);
                    switch (requestCode) {
                        case ACTIVITY_CHOOSE_FOLDER_MOVE: {
                            mFragmentListener.showNextMessageOrReturn();
                            moveMessage(ref, destFolder);
                            break;
                        }
                        case ACTIVITY_CHOOSE_FOLDER_COPY: {
                            copyMessage(ref, destFolder);
                            break;
                        }
                    }
                }
                break;
            }
        }
    }

    public void onSendAlternate() {
        if (mMessage != null) {
            mController.sendAlternate(mContext, mAccount, mMessage);
        }
    }

    public void onToggleRead() {
        if (mMessage != null) {
            mController.setFlag(mAccount, mMessage.getFolder().getServerId(),
                    Collections.singletonList(mMessage), Flag.SEEN, !mMessage.isSet(Flag.SEEN));
            mMessageView.setHeaders(mMessage, mAccount);
            String subject = mMessage.getSubject();
            displayMessageSubject(subject);
            mFragmentListener.updateMenu();
        }
    }

    private void setProgress(boolean enable) {
        if (mFragmentListener != null) {
            mFragmentListener.setProgress(enable);
        }
    }

    private void displayMessageSubject(String subject) {
        if (mFragmentListener != null) {
            mFragmentListener.displayMessageSubject(subject);
        }
    }

    private String getSubjectForMessage(LocalMessage message) {
        String subject = message.getSubject();
        if (TextUtils.isEmpty(subject)) {
            return mContext.getString(R.string.general_no_subject);
        }
        return subject;
    }

    public void moveMessage(MessageReference reference, String destFolderName) {
        mController.moveMessage(mAccount, mMessageReference.getFolderServerId(), reference, destFolderName);
    }

    public void copyMessage(MessageReference reference, String destFolderName) {
        mController.copyMessage(mAccount, mMessageReference.getFolderServerId(), reference, destFolderName);
    }

    private void showDialog(int dialogId) {
        DialogFragment fragment;
        switch (dialogId) {
            case R.id.dialog_confirm_delete: {
                String title = getString(R.string.dialog_confirm_delete_title);
                String message = getString(R.string.dialog_confirm_delete_message);
                String confirmText = getString(R.string.dialog_confirm_delete_confirm_button);
                String cancelText = getString(R.string.dialog_confirm_delete_cancel_button);

                fragment = ConfirmationDialogFragment.newInstance(dialogId, title, message,
                        confirmText, cancelText);
                break;
            }
            case R.id.dialog_confirm_spam: {
                String title = getString(R.string.dialog_confirm_spam_title);
                String message = getResources().getQuantityString(R.plurals.dialog_confirm_spam_message, 1);
                String confirmText = getString(R.string.dialog_confirm_spam_confirm_button);
                String cancelText = getString(R.string.dialog_confirm_spam_cancel_button);

                fragment = ConfirmationDialogFragment.newInstance(dialogId, title, message,
                        confirmText, cancelText);
                break;
            }
            case R.id.dialog_attachment_progress: {
                String message = getString(R.string.dialog_attachment_progress_title);
                long size = currentAttachmentViewInfo.size;
                fragment = AttachmentDownloadDialogFragment.newInstance(size, message);
                break;
            }
            default: {
                throw new RuntimeException("Called showDialog(int) with unknown dialog id.");
            }
        }

        fragment.setTargetFragment(this, dialogId);
        fragment.show(getFragmentManager(), getDialogTag(dialogId));
    }

    private void removeDialog(int dialogId) {
        FragmentManager fm = getFragmentManager();

        if (fm == null || isRemoving() || isDetached()) {
            return;
        }

        // Make sure the "show dialog" transaction has been processed when we call  findFragmentByTag() below.
        // Otherwise the fragment won't be found and the dialog will never be dismissed.
        fm.executePendingTransactions();

        DialogFragment fragment = (DialogFragment) fm.findFragmentByTag(getDialogTag(dialogId));
        if (fragment != null) {
            fragment.dismissAllowingStateLoss();
        }
    }

    private String getDialogTag(int dialogId) {
        return String.format(Locale.US, "dialog-%d", dialogId);
    }

    public void zoom(KeyEvent event) {
        // mMessageView.zoom(event);
    }

    @Override
    public void doPositiveClick(int dialogId) {
        switch (dialogId) {
            case R.id.dialog_confirm_delete: {
                delete();
                break;
            }
            case R.id.dialog_confirm_spam: {
                refileMessage(mDstFolder);
                mDstFolder = null;
                break;
            }
        }
    }

    @Override
    public void doNegativeClick(int dialogId) {
        /* do nothing */
    }

    @Override
    public void dialogCancelled(int dialogId) {
        /* do nothing */
    }

    /**
     * Get the {@link MessageReference} of the currently displayed message.
     */
    public MessageReference getMessageReference() {
        return mMessageReference;
    }

    public boolean isMessageRead() {
        return (mMessage != null) && mMessage.isSet(Flag.SEEN);
    }

    public boolean isCopyCapable() {
        return mController.isCopyCapable(mAccount);
    }

    public boolean isMoveCapable() {
        return mController.isMoveCapable(mAccount);
    }

    public boolean canMessageBeArchived() {
        return (!mMessageReference.getFolderServerId().equals(mAccount.getArchiveFolder())
                && mAccount.hasArchiveFolder());
    }

    public boolean canMessageBeMovedToSpam() {
        return (!mMessageReference.getFolderServerId().equals(mAccount.getSpamFolder())
                && mAccount.hasSpamFolder());
    }

    public void updateTitle() {
        if (mMessage != null) {
            displayMessageSubject(mMessage.getSubject());
        }
    }

    public Context getApplicationContext() {
        return mContext;
    }

    public void disableAttachmentButtons(AttachmentViewInfo attachment) {
        // mMessageView.disableAttachmentButtons(attachment);
    }

    public void enableAttachmentButtons(AttachmentViewInfo attachment) {
        // mMessageView.enableAttachmentButtons(attachment);
    }

    public void showAttachmentLoadingDialog() {
        // mMessageView.disableAttachmentButtons();
        showDialog(R.id.dialog_attachment_progress);
    }

    public void hideAttachmentLoadingDialogOnMainThread() {
        runOnUiThread(() -> {
            removeDialog(R.id.dialog_attachment_progress);
            // mMessageView.enableAttachmentButtons();
        });
    }

    public void refreshAttachmentThumbnail(AttachmentViewInfo attachment) {
        // mMessageView.refreshAttachmentThumbnail(attachment);
    }

    private MessageCryptoMvpView messageCryptoMvpView = new MessageCryptoMvpView() {
        @Override
        public void redisplayMessage() {
            messageLoaderHelper.asyncReloadMessage();
        }

        @Override
        public void startPendingIntentForCryptoPresenter(IntentSender si, Integer requestCode, Intent fillIntent,
                int flagsMask, int flagValues, int extraFlags)
                throws SendIntentException {
            if (requestCode == null) {
                mContext.startIntentSender(si, fillIntent, flagsMask, flagValues, extraFlags);
                return;
            }

            requestCode |= REQUEST_MASK_CRYPTO_PRESENTER;
            mFragmentActivity.startIntentSenderForResult(
                    si, requestCode, fillIntent, flagsMask, flagValues, extraFlags);
        }

        @Override
        public void showCryptoInfoDialog(MessageCryptoDisplayStatus displayStatus, boolean hasSecurityWarning) {
            CryptoInfoDialog dialog = CryptoInfoDialog.newInstance(displayStatus, hasSecurityWarning);
            dialog.setTargetFragment(MessageViewFragment.this, 0);
            dialog.show(getFragmentManager(), "crypto_info_dialog");
        }

        @Override
        public void restartMessageCryptoProcessing() {
            mMessageView.setToLoadingState();
            messageLoaderHelper.asyncRestartMessageCryptoProcessing();
        }

        @Override
        public void showCryptoConfigDialog() {
            startOpenPgpChooserActivity();
        }
    };

    @Override
    public void onClickShowSecurityWarning() {
        messageCryptoPresenter.onClickShowCryptoWarningDetails();
    }

    @Override
    public void onClickShowCryptoKey() {
        messageCryptoPresenter.onClickShowCryptoKey();
    }

    public interface MessageViewFragmentListener {
        void onForward(MessageReference messageReference, Parcelable decryptionResultForReply);

        void onForwardAsAttachment(MessageReference messageReference, Parcelable decryptionResultForReply);

        void disableDeleteAction();

        void onReplyAll(MessageReference messageReference, Parcelable decryptionResultForReply);

        void onReply(MessageReference messageReference, Parcelable decryptionResultForReply);

        void onResendMessage(MessageReference messageReference, Parcelable decryptionResultForReply);

        void displayMessageSubject(String title);

        void setProgress(boolean b);

        void showNextMessageOrReturn();

        void showMessageList();

        void messageHeaderViewAvailable(MessageHeader messageHeaderView);

        void updateMenu();
    }

    public boolean isInitialized() {
        return mInitialized;
    }

    private final MessageLoaderCallbacks messageLoaderCallbacks = new MessageLoaderCallbacks() {
        @Override
        public void onMessageDataLoadFinished(LocalMessage message) {
            mMessage = message;
            displayHeaderForLoadingMessage(message);
            mMessageView.setToLoadingState();
            showProgressThreshold = null;
        }

        @Override
        public void onMessageDataLoadFailed() {
            XryptoMail.showToastMessage(R.string.status_loading_error);
            showProgressThreshold = null;
        }

        @Override
        public void onMessageViewInfoLoadFinished(MessageViewInfo messageViewInfo) {
            setupStealthTimer(messageViewInfo);
            showMessage(messageViewInfo);
            showProgressThreshold = null;
        }

        @Override
        public void onMessageViewInfoLoadFailed(MessageViewInfo messageViewInfo) {
            showMessage(messageViewInfo);
            showProgressThreshold = null;
        }

        @Override
        public void setLoadingProgress(int current, int max) {
            if (showProgressThreshold == null) {
                showProgressThreshold = SystemClock.elapsedRealtime() + PROGRESS_THRESHOLD_MILLIS;
            }
            else if (showProgressThreshold == 0L || SystemClock.elapsedRealtime() > showProgressThreshold) {
                showProgressThreshold = 0L;
                mMessageView.setLoadingProgress(current, max);
            }
        }

        @Override
        public void onDownloadErrorMessageNotFound() {
            mMessageView.enableDownloadButton();
            XryptoMail.showToastMessage(R.string.status_invalid_id_error);
        }

        @Override
        public void onDownloadErrorNetworkError() {
            mMessageView.enableDownloadButton();
            XryptoMail.showToastMessage(R.string.status_network_error);
        }

        @Override
        public void startIntentSenderForMessageLoaderHelper(IntentSender si, int requestCode, Intent fillIntent,
                int flagsMask, int flagValues, int extraFlags) {
            showProgressThreshold = null;
            try {
                requestCode |= REQUEST_MASK_LOADER_HELPER;
                mFragmentActivity.startIntentSenderForResult(
                        si, requestCode, fillIntent, flagsMask, flagValues, extraFlags);
            } catch (SendIntentException e) {
                Timber.e(e, "Irrecoverable error calling PendingIntent!");
            }
        }
    };

    private void setupStealthTimer(MessageViewInfo messageViewInfo) {
        if (mMessageView.getCryptoMode() == XryptoMode.STEALTH) {
            final TextView mCountDownView = mMessageView.getCountDownView();
            mCountDownValue = mMessage.getStealthTimerCount();

            // Do not start stealth timer if 'cryptoResultAnnotation (engine not installed)'
            // and getOpenPgpDecryptionResult return null
            if ((messageViewInfo.cryptoResultAnnotation == null)
                    || (messageViewInfo.cryptoResultAnnotation.getOpenPgpDecryptionResult() == null)) {
                mCountDownView.setVisibility(View.GONE);
                return;
            }

            stealthMessageTimerHandler = new Handler(Looper.getMainLooper()) {
                public void handleMessage(android.os.Message msg) {
                    switch (msg.what) {
                        case STEALTH_TIMER_TICK_CHECK:
                            if (mCountDownValue < 0) {
                                mCountDownView.setVisibility(View.GONE);
                                // force delete - bypass user confirmation prompt
                                delete();
                                Toast.makeText(mContext, R.string.stealth_deleted_message, Toast.LENGTH_SHORT).show();
                            }
                            mCountDownView.setText(String.format(mContext.getString(R.string.stealth_timer_message), mCountDownValue));
                            mCountDownValue--;
                            break;
                    }
                    super.handleMessage(msg);
                }
            };

            TimerTask stealthMessageTimerTask = new TimerTask() {
                @Override
                public void run() {
                    android.os.Message message = new android.os.Message();
                    message.what = STEALTH_TIMER_TICK_CHECK;
                    stealthMessageTimerHandler.sendMessage(message);
                }
            };

            mCountDownView.setVisibility(View.VISIBLE);
            stealthMessageCountDownTimer = new Timer(true);
            stealthMessageCountDownTimer.schedule(stealthMessageTimerTask, 0, STEALTH_MESSAGE_COUNT_DOWN_INTERVAL);
        }
    }

    @Override
    public void onViewAttachment(AttachmentViewInfo attachment) {
        currentAttachmentViewInfo = attachment;
        getAttachmentController(attachment).viewAttachment();
    }

    @Override
    public void onSaveAttachment(final AttachmentViewInfo attachment) {
        currentAttachmentViewInfo = attachment;

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType(attachment.mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, attachment.displayName);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        startActivityForResult(intent, REQUEST_CODE_CREATE_DOCUMENT);
    }

    private AttachmentController getAttachmentController(AttachmentViewInfo attachment) {
        return new AttachmentController(mController, this, attachment);
    }
}
