package org.atalk.xryptomail.view;
import java.util.Arrays;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import timber.log.Timber;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.atalk.xryptomail.Account;
import org.atalk.xryptomail.FontSizes;
import org.atalk.xryptomail.R;
import org.atalk.xryptomail.XryptoMail;
import org.atalk.xryptomail.activity.misc.ContactPictureLoader;
import org.atalk.xryptomail.helper.ClipboardManager;
import org.atalk.xryptomail.helper.ContactPicture;
import org.atalk.xryptomail.helper.Contacts;
import org.atalk.xryptomail.helper.MessageHelper;
import org.atalk.xryptomail.helper.Utility;
import org.atalk.xryptomail.mail.Address;
import org.atalk.xryptomail.mail.Flag;
import org.atalk.xryptomail.mail.Message;
import org.atalk.xryptomail.mail.MessagingException;
import org.atalk.xryptomail.mail.internet.MimeUtility;
import org.atalk.xryptomail.ui.messageview.OnCryptoClickListener;
import org.atalk.xryptomail.ui.ContactBadge;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class MessageHeader extends LinearLayout implements OnClickListener, OnLongClickListener {
    private Context mContext;
    private TextView mFromView;
    private TextView mSenderView;
    private TextView mDateView;
    private TextView mToView;
    private TextView mToLabel;
    private TextView mCcView;
    private TextView mCcLabel;
    private TextView mBccView;
    private TextView mBccLabel;
    private TextView mSubjectView;
    private MessageCryptoStatusView mCryptoStatusIcon;

    private View mChip;
    private CheckBox mFlagged;
    private int defaultSubjectColor;
    private TextView mAdditionalHeadersView;
    private View mAnsweredIcon;
    private View mForwardedIcon;
    private Message mMessage;
    private Account mAccount;
    private FontSizes mFontSizes;
    private Contacts mContacts;
    private SavedState mSavedState;

    private MessageHelper mMessageHelper;
    private ContactPictureLoader mContactsPictureLoader;
    private ContactBadge mContactBadge; // = (ContactBadge) findViewById(R.id.contact_badge);

    private OnLayoutChangedListener mOnLayoutChangedListener;
    private OnCryptoClickListener onCryptoClickListener;

    /**
     * Pair class is only available since API Level 5, so we need
     * this helper class unfortunately
     */
    private static class HeaderEntry {
        public String label;
        public String value;

        public HeaderEntry(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }

    public MessageHeader(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mContacts = Contacts.getInstance(mContext);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
		mFontSizes = XryptoMail.getFontSizes();
        mAnsweredIcon = findViewById(R.id.answered);
        mForwardedIcon = findViewById(R.id.forwarded);
        mFromView = findViewById(R.id.from);
        mSenderView = findViewById(R.id.sender);
        mToView = findViewById(R.id.to);
        mToLabel = findViewById(R.id.to_label);
        mCcView = findViewById(R.id.cc);
        mCcLabel = findViewById(R.id.cc_label);
        mBccView = findViewById(R.id.bcc);
        mBccLabel = findViewById(R.id.bcc_label);
        mContactBadge = findViewById(R.id.contact_badge);

        mSubjectView = findViewById(R.id.subject);
        mAdditionalHeadersView = findViewById(R.id.additional_headers_view);
        mChip = findViewById(R.id.chip);
        mDateView = findViewById(R.id.date);
        mFlagged = findViewById(R.id.flagged);

        defaultSubjectColor = mSubjectView.getCurrentTextColor();
        mFontSizes.setViewTextSize(mSubjectView, mFontSizes.getMessageViewSubject());
        mFontSizes.setViewTextSize(mDateView, mFontSizes.getMessageViewDate());
        mFontSizes.setViewTextSize(mAdditionalHeadersView, mFontSizes.getMessageViewAdditionalHeaders());

        mFontSizes.setViewTextSize(mFromView, mFontSizes.getMessageViewSender());
        mFontSizes.setViewTextSize(mToView, mFontSizes.getMessageViewTo());
        mFontSizes.setViewTextSize(mToLabel, mFontSizes.getMessageViewTo());
        mFontSizes.setViewTextSize(mCcView, mFontSizes.getMessageViewCC());
        mFontSizes.setViewTextSize(mCcLabel, mFontSizes.getMessageViewCC());
        mFontSizes.setViewTextSize(mBccView, mFontSizes.getMessageViewBCC());
        mFontSizes.setViewTextSize(mBccLabel, mFontSizes.getMessageViewBCC());

        mFromView.setOnClickListener(this);
        mToView.setOnClickListener(this);
        mCcView.setOnClickListener(this);
        mBccView.setOnClickListener(this);

        mFromView.setOnLongClickListener(this);
        mToView.setOnLongClickListener(this);
        mCcView.setOnLongClickListener(this);
        mBccView.setOnLongClickListener(this);

        mCryptoStatusIcon = (MessageCryptoStatusView) findViewById(R.id.crypto_status_icon);
        mCryptoStatusIcon.setOnClickListener(this);

        mMessageHelper = MessageHelper.getInstance(mContext);
        hideAdditionalHeaders();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.from: {
                onAddSenderToContacts();
                break;
            }
            case R.id.to:
            case R.id.cc:
            case R.id.bcc: {
                expand((TextView)view, ((TextView)view).getEllipsize() != null);
                layoutChanged();
                break;
            }
            case R.id.crypto_status_icon: {
                onCryptoClickListener.onCryptoClick();
                break;
            }
        }
    }

    @Override
    public boolean onLongClick(View view) {
        switch (view.getId()) {
            case R.id.from:
                onAddAddressesToClipboard(mMessage.getFrom());
                break;
            case R.id.to:
                onAddRecipientsToClipboard(Message.RecipientType.TO);
                break;
            case R.id.cc:
                onAddRecipientsToClipboard(Message.RecipientType.CC);
                break;
        }
        return true;
    }

    private void onAddSenderToContacts() {
        if (mMessage != null) {
            try {
                final Address senderEmail = mMessage.getFrom()[0];
                mContacts.createContact(senderEmail);
            } catch (Exception e) {
                Timber.e(e, "Couldn't create contact");
            }
        }
    }

    public String createMessage(int addressesCount) {
        return mContext.getResources().getQuantityString(R.plurals.copy_address_to_clipboard, addressesCount);
    }

    private void onAddAddressesToClipboard(Address[] addresses) {
        String addressList = Address.toString(addresses);

        ClipboardManager clipboardManager = ClipboardManager.getInstance(mContext);
        clipboardManager.setText("addresses", addressList);

        Toast.makeText(mContext, createMessage(addresses.length), Toast.LENGTH_LONG).show();
    }

    private void onAddRecipientsToClipboard(Message.RecipientType recipientType) {
        onAddAddressesToClipboard(mMessage.getRecipients(recipientType));
    }

    public void setOnFlagListener(OnClickListener listener) {
        mFlagged.setOnClickListener(listener);
    }

    public boolean additionalHeadersVisible() {
        return (mAdditionalHeadersView != null &&
                mAdditionalHeadersView.getVisibility() == View.VISIBLE);
    }

    /**
     * Clear the text field for the additional headers display if they are
     * not shown, to save UI resources.
     */
    private void hideAdditionalHeaders() {
        mAdditionalHeadersView.setVisibility(View.GONE);
        mAdditionalHeadersView.setText("");
    }

    /**
     * Set up and then show the additional headers view. Called by
     * {@link #onShowAdditionalHeaders()}
     * (when switching between messages).
     */
    private void showAdditionalHeaders() {
        Integer messageToShow = null;
        try {
            // Retrieve additional headers
            List<HeaderEntry> additionalHeaders = getAdditionalHeaders(mMessage);
            if (!additionalHeaders.isEmpty()) {
                // Show the additional headers that we have got.
                populateAdditionalHeadersView(additionalHeaders);
                mAdditionalHeadersView.setVisibility(View.VISIBLE);
			} else {
                // All headers have been downloaded, but there are no additional headers.
                messageToShow = R.string.message_no_additional_headers_available;
            }
        } catch (Exception e) {
            messageToShow = R.string.message_additional_headers_retrieval_failed;
        }
        // Show a message to the user, if any
        if (messageToShow != null) {
            Toast toast = Toast.makeText(mContext, messageToShow, Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL, 0, 0);
            toast.show();
        }
    }

    public void populate(final Message message, final Account account) {
        final Contacts contacts = XryptoMail.showContactName() ? mContacts : null;
        final CharSequence from = MessageHelper.toFriendly(message.getFrom(), contacts);
        final CharSequence to = MessageHelper.toFriendly(message.getRecipients(Message.RecipientType.TO), contacts);
        final CharSequence cc = MessageHelper.toFriendly(message.getRecipients(Message.RecipientType.CC), contacts);
        final CharSequence bcc = MessageHelper.toFriendly(message.getRecipients(Message.RecipientType.BCC), contacts);

        Address[] fromAddrs = message.getFrom();
        Address[] toAddrs = message.getRecipients(Message.RecipientType.TO);
        Address[] ccAddrs = message.getRecipients(Message.RecipientType.CC);
        boolean fromMe = mMessageHelper.toMe(account, fromAddrs);

        Address counterpartyAddress = null;
        if (fromMe) {
            if (toAddrs.length > 0) {
                counterpartyAddress = toAddrs[0];
            } else if (ccAddrs.length > 0) {
                counterpartyAddress = ccAddrs[0];
            }
        } else if (fromAddrs.length > 0) {
            counterpartyAddress = fromAddrs[0];
        }

        /* We hide the subject by default for each new message, and MessageTitleView might show
         * it later by calling showSubjectLine(). */
        boolean newMessageShown = mMessage == null || !mMessage.getUid().equals(message.getUid());
        if (newMessageShown) {
            mSubjectView.setVisibility(GONE);
        }
        mMessage = message;
        mAccount = account;

        // mContactBadge can sometimes be null
//        if (mContactBadge == null)
//            mContactBadge = findViewById(R.id.contact_badge);

        if (XryptoMail.showContactPicture()) {
            mContactBadge.setVisibility(View.VISIBLE);
            mContactsPictureLoader = ContactPicture.getContactPictureLoader(mContext);
        }  else {
            mContactBadge.setVisibility(View.GONE);
        }

        if (shouldShowSender(message)) {
            mSenderView.setVisibility(VISIBLE);
            String sender = getResources().getString(R.string.message_view_sender_label,
                    MessageHelper.toFriendly(message.getSender(), contacts));
            mSenderView.setText(sender);
        } else {
            mSenderView.setVisibility(View.GONE);
        }
        final String subject = message.getSubject();
        if (TextUtils.isEmpty(subject)) {
            mSubjectView.setText(mContext.getText(R.string.general_no_subject));
        } else {
            mSubjectView.setText(subject);
        }
        mSubjectView.setTextColor(0xff000000 | defaultSubjectColor);

        String dateTime = DateUtils.formatDateTime(mContext,
                message.getSentDate().getTime(),
                DateUtils.FORMAT_SHOW_DATE
                | DateUtils.FORMAT_ABBREV_ALL
                | DateUtils.FORMAT_SHOW_TIME
                | DateUtils.FORMAT_SHOW_YEAR);
        mDateView.setText(dateTime);

        if (XryptoMail.showContactPicture()) {
            if (counterpartyAddress != null) {
                Utility.setContactForBadge(mContactBadge, counterpartyAddress);
                mContactsPictureLoader.loadContactPicture(counterpartyAddress, mContactBadge);
            } else {
                mContactBadge.setImageResource(R.drawable.ic_contact_picture);
            }
        }
        mFromView.setText(from);

        updateAddressField(mToView, to, mToLabel);
        updateAddressField(mCcView, cc, mCcLabel);
        updateAddressField(mBccView, bcc, mBccLabel);
        mAnsweredIcon.setVisibility(message.isSet(Flag.ANSWERED) ? View.VISIBLE : View.GONE);
        mForwardedIcon.setVisibility(message.isSet(Flag.FORWARDED) ? View.VISIBLE : View.GONE);
        mFlagged.setChecked(message.isSet(Flag.FLAGGED));

        mChip.setBackgroundColor(mAccount.getChipColor());
        setVisibility(View.VISIBLE);

        if (mSavedState != null) {
            if (mSavedState.additionalHeadersVisible) {
                showAdditionalHeaders();
            }
            mSavedState = null;
        } else {
            hideAdditionalHeaders();
        }
    }

    public static boolean shouldShowSender(Message message) {
        Address[] from = message.getFrom();
        Address[] sender = message.getSender();

        if (sender == null || sender.length == 0) {
            return false;
        }
        return !Arrays.equals(from, sender);
    }

    public void hideCryptoStatus() {
        mCryptoStatusIcon.setVisibility(View.GONE);
    }

    public void setCryptoStatusLoading() {
        mCryptoStatusIcon.setVisibility(View.VISIBLE);
        mCryptoStatusIcon.setEnabled(false);
        mCryptoStatusIcon.setCryptoDisplayStatus(MessageCryptoDisplayStatus.LOADING);
    }

    public void setCryptoStatusDisabled() {
        mCryptoStatusIcon.setVisibility(View.VISIBLE);
        mCryptoStatusIcon.setEnabled(false);
        mCryptoStatusIcon.setCryptoDisplayStatus(MessageCryptoDisplayStatus.DISABLED);
    }

    public void setCryptoStatus(MessageCryptoDisplayStatus displayStatus) {
        mCryptoStatusIcon.setVisibility(View.VISIBLE);
        mCryptoStatusIcon.setEnabled(true);
        mCryptoStatusIcon.setCryptoDisplayStatus(displayStatus);
    }

    public void onShowAdditionalHeaders() {
        int currentVisibility = mAdditionalHeadersView.getVisibility();
        if (currentVisibility == View.VISIBLE) {
            hideAdditionalHeaders();
            expand(mToView, false);
            expand(mCcView, false);
        } else {
            showAdditionalHeaders();
            expand(mToView, true);
            expand(mCcView, true);
        }
        layoutChanged();
    }

    private void updateAddressField(TextView v, CharSequence text, View label) {
        boolean hasText = !TextUtils.isEmpty(text);

        v.setText(text);
        v.setVisibility(hasText ? View.VISIBLE : View.GONE);
        label.setVisibility(hasText ? View.VISIBLE : View.GONE);
    }

    /**
     * Expand or collapse a TextView by removing or adding the 2 lines limitation
     */
    private void expand(TextView v, boolean expand) {
       if (expand) {
           v.setMaxLines(Integer.MAX_VALUE);
           v.setEllipsize(null);
       } else {
           v.setMaxLines(2);
           v.setEllipsize(android.text.TextUtils.TruncateAt.END);
       }
    }

    private List<HeaderEntry> getAdditionalHeaders(final Message message)
    throws MessagingException {
        List<HeaderEntry> additionalHeaders = new LinkedList<>();
        /*
        * Remove "Subject" header as it is already shown in the standard
        * message view header. But do show "From", "To", and "Cc" again.
        * This time including the email addresses. See issue 1805.
        */
        Set<String> headerNames = new LinkedHashSet<>(message.getHeaderNames());
        // headerNames.remove("Subject");
        for (String headerName : headerNames) {
            String[] headerValues = message.getHeader(headerName);
            for (String headerValue : headerValues) {
                additionalHeaders.add(new HeaderEntry(headerName, headerValue));
            }
        }
        return additionalHeaders;
    }

    /**
     * Set up the additional headers text view with the supplied header data.
     *
     * @param additionalHeaders List of header entries. Each entry consists of a header
     *                          name and a header value. Header names may appear multiple
     *                          times.
     *                          <p/>
     *                          This method is always called from within the UI thread by
     *                          {@link #showAdditionalHeaders()}.
     */
    private void populateAdditionalHeadersView(final List<HeaderEntry> additionalHeaders) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        boolean first = true;
        for (HeaderEntry additionalHeader : additionalHeaders) {
            if (!first) {
                sb.append("\n");
            } else {
                first = false;
            }
            StyleSpan boldSpan = new StyleSpan(Typeface.BOLD);
            SpannableString label = new SpannableString(additionalHeader.label + ": ");
            label.setSpan(boldSpan, 0, label.length(), 0);
            sb.append(label);
            sb.append(MimeUtility.unfoldAndDecode(additionalHeader.value));
        }
        mAdditionalHeadersView.setText(sb);
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState savedState = new SavedState(superState);
        savedState.additionalHeadersVisible = additionalHeadersVisible();
        return savedState;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if(!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState savedState = (SavedState)state;
        super.onRestoreInstanceState(savedState.getSuperState());
        mSavedState = savedState;
    }

    static class SavedState extends BaseSavedState {
        boolean additionalHeadersVisible;

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            this.additionalHeadersVisible = (in.readInt() != 0);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt((this.additionalHeadersVisible) ? 1 : 0);
        }
    }

    public interface OnLayoutChangedListener {
        void onLayoutChanged();
    }

    public void setOnLayoutChangedListener(OnLayoutChangedListener listener) {
        mOnLayoutChangedListener = listener;
    }

    private void layoutChanged() {
        if (mOnLayoutChangedListener != null) {
            mOnLayoutChangedListener.onLayoutChanged();
        }
    }

    public void showSubjectLine() {
        mSubjectView.setVisibility(VISIBLE);
    }

    public void setOnCryptoClickListener(OnCryptoClickListener onCryptoClickListener) {
        this.onCryptoClickListener = onCryptoClickListener;
    }
}
