package org.atalk.xryptomail.fragment;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.TextView;

import org.atalk.xryptomail.Account;
import org.atalk.xryptomail.FontSizes;
import org.atalk.xryptomail.R;
import org.atalk.xryptomail.XryptoMail;
import org.atalk.xryptomail.helper.Utility;
import org.atalk.xryptomail.mail.Address;
import org.atalk.xryptomail.mailstore.DatabasePreviewType;
import org.atalk.xryptomail.message.extractors.PreviewResult.PreviewType;
import org.atalk.xryptomail.ui.ContactBadge;

import static org.atalk.xryptomail.fragment.MLFProjectionInfo.ANSWERED_COLUMN;
import static org.atalk.xryptomail.fragment.MLFProjectionInfo.ATTACHMENT_COUNT_COLUMN;
import static org.atalk.xryptomail.fragment.MLFProjectionInfo.CC_LIST_COLUMN;
import static org.atalk.xryptomail.fragment.MLFProjectionInfo.DATE_COLUMN;
import static org.atalk.xryptomail.fragment.MLFProjectionInfo.FLAGGED_COLUMN;
import static org.atalk.xryptomail.fragment.MLFProjectionInfo.FOLDER_NAME_COLUMN;
import static org.atalk.xryptomail.fragment.MLFProjectionInfo.FORWARDED_COLUMN;
import static org.atalk.xryptomail.fragment.MLFProjectionInfo.PREVIEW_COLUMN;
import static org.atalk.xryptomail.fragment.MLFProjectionInfo.PREVIEW_TYPE_COLUMN;
import static org.atalk.xryptomail.fragment.MLFProjectionInfo.READ_COLUMN;
import static org.atalk.xryptomail.fragment.MLFProjectionInfo.SENDER_LIST_COLUMN;
import static org.atalk.xryptomail.fragment.MLFProjectionInfo.SUBJECT_COLUMN;
import static org.atalk.xryptomail.fragment.MLFProjectionInfo.THREAD_COUNT_COLUMN;
import static org.atalk.xryptomail.fragment.MLFProjectionInfo.TO_LIST_COLUMN;
import static org.atalk.xryptomail.fragment.MLFProjectionInfo.UID_COLUMN;

import androidx.core.content.res.ResourcesCompat;

import java.util.Locale;

public class MessageListAdapter extends CursorAdapter
{
    private final MessageListFragment fragment;
    private final Drawable mAttachmentIcon;
    private final Drawable mForwardedIcon;
    private final Drawable mAnsweredIcon;
    private final Drawable mForwardedAnsweredIcon;
    private final FontSizes fontSizes = XryptoMail.getFontSizes();

    MessageListAdapter(MessageListFragment fragment)
    {
        super(fragment.getActivity(), null, 0);
        this.fragment = fragment;
        Resources res = fragment.getResources();
        mAttachmentIcon = ResourcesCompat.getDrawable(res, R.drawable.ic_email_attachment_small, null);
        mAnsweredIcon = ResourcesCompat.getDrawable(res, R.drawable.ic_email_answered_small, null);
        mForwardedIcon = ResourcesCompat.getDrawable(res, R.drawable.ic_email_forwarded_small, null);
        mForwardedAnsweredIcon = ResourcesCompat.getDrawable(res, R.drawable.ic_email_forwarded_answered_small, null);
    }

    private String recipientSigil(boolean toMe, boolean ccMe)
    {
        if (toMe) {
            return fragment.getString(R.string.messagelist_sent_to_me_sigil);
        }
        else if (ccMe) {
            return fragment.getString(R.string.messagelist_sent_cc_me_sigil);
        }
        else {
            return "";
        }
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent)
    {
        View view = fragment.getXMLayoutInflater().inflate(R.layout.message_list_item, parent, false);
        MessageViewHolder holder = new MessageViewHolder(fragment);
        holder.date = view.findViewById(R.id.date);
        holder.chip = view.findViewById(R.id.chip);

        if (fragment.mPreviewLines == 0 && fragment.contactsPictureLoader == null) {
            view.findViewById(R.id.preview).setVisibility(View.GONE);
            holder.preview = view.findViewById(R.id.sender_compact);
            holder.flagged = view.findViewById(R.id.flagged_center_right);
            view.findViewById(R.id.flagged_bottom_right).setVisibility(View.GONE);
        }
        else {
            view.findViewById(R.id.sender_compact).setVisibility(View.GONE);
            holder.preview = view.findViewById(R.id.preview);
            holder.flagged = view.findViewById(R.id.flagged_bottom_right);
            view.findViewById(R.id.flagged_center_right).setVisibility(View.GONE);
        }

        ContactBadge contactBadge = view.findViewById(R.id.contact_badge);
        if (fragment.contactsPictureLoader != null) {
            holder.contactBadge = contactBadge;
        }
        else {
            contactBadge.setVisibility(View.GONE);
        }

        if (fragment.mSenderAboveSubject) {
            holder.from = view.findViewById(R.id.subject);
            fontSizes.setViewTextSize(holder.from, fontSizes.getMessageListSender());
        }
        else {
            holder.subject = view.findViewById(R.id.subject);
            fontSizes.setViewTextSize(holder.subject, fontSizes.getMessageListSubject());
        }
        fontSizes.setViewTextSize(holder.date, fontSizes.getMessageListDate());

        // 1 preview line is needed even if it is set to 0, because subject is part of the same text view
        holder.preview.setLines(Math.max(fragment.mPreviewLines, 1));
        fontSizes.setViewTextSize(holder.preview, fontSizes.getMessageListPreview());
        holder.threadCount = view.findViewById(R.id.thread_count);
        fontSizes.setViewTextSize(holder.threadCount, fontSizes.getMessageListSubject()); // thread count is next to subject
        view.findViewById(R.id.selected_checkbox_wrapper).setVisibility((fragment.mCheckboxes) ? View.VISIBLE : View.GONE);

        holder.flagged.setVisibility(fragment.mStars ? View.VISIBLE : View.GONE);
        holder.flagged.setOnClickListener(holder);

        holder.selected = view.findViewById(R.id.selected_checkbox);
        holder.selected.setOnClickListener(holder);

        view.setTag(holder);
        return view;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor)
    {
        Account account = fragment.getAccountFromCursor(cursor);
        String fromList = cursor.getString(SENDER_LIST_COLUMN);
        String toList = cursor.getString(TO_LIST_COLUMN);
        String ccList = cursor.getString(CC_LIST_COLUMN);
        Address[] fromAddrs = Address.unpack(fromList);
        Address[] toAddrs = Address.unpack(toList);
        Address[] ccAddrs = Address.unpack(ccList);

        boolean fromMe = fragment.messageHelper.toMe(account, fromAddrs);
        boolean toMe = fragment.messageHelper.toMe(account, toAddrs);
        boolean ccMe = fragment.messageHelper.toMe(account, ccAddrs);

        CharSequence displayName = fragment.messageHelper.getDisplayName(account, fromAddrs, toAddrs);
        CharSequence displayDate = DateUtils.getRelativeTimeSpanString(context, cursor.getLong(DATE_COLUMN));

        Address counterpartyAddress = fetchCounterPartyAddress(fromMe, toAddrs, ccAddrs, fromAddrs);
        int threadCount = (fragment.showingThreadedList) ? cursor.getInt(THREAD_COUNT_COLUMN) : 0;
        String subject = MlfUtils.buildSubject(cursor.getString(SUBJECT_COLUMN),
                fragment.getString(R.string.general_no_subject), threadCount);

        boolean read = (cursor.getInt(READ_COLUMN) == 1);
        boolean flagged = (cursor.getInt(FLAGGED_COLUMN) == 1);
        boolean answered = (cursor.getInt(ANSWERED_COLUMN) == 1);
        boolean forwarded = (cursor.getInt(FORWARDED_COLUMN) == 1);
        boolean hasAttachments = (cursor.getInt(ATTACHMENT_COUNT_COLUMN) > 0);

        MessageViewHolder holder = (MessageViewHolder) view.getTag();
        int maybeBoldTypeface = (read) ? Typeface.NORMAL : Typeface.BOLD;
        long uniqueId = cursor.getLong(fragment.mUniqueIdColumn);
        boolean selected = fragment.mSelected.contains(uniqueId);

        holder.chip.setBackgroundColor(account.getChipColor());
        if (fragment.mCheckboxes) {
            holder.selected.setChecked(selected);
        }
        if (fragment.mStars) {
            holder.flagged.setChecked(flagged);
        }
        holder.position = cursor.getPosition();
        if (holder.contactBadge != null) {
            updateContactBadge(holder, counterpartyAddress);
        }
        setBackgroundColor(cursor, view, selected, read);
        if (fragment.mActiveMessage != null) {
            changeBackgroundColorIfActiveMessage(cursor, account, view);
        }
        updateWithThreadCount(holder, threadCount);
        CharSequence beforePreviewText = (fragment.mSenderAboveSubject) ? subject : displayName;
        String sigil = recipientSigil(toMe, ccMe);
        SpannableStringBuilder messageStringBuilder = new SpannableStringBuilder(sigil).append(beforePreviewText);
        if (fragment.mPreviewLines > 0) {
            String preview = getPreview(cursor);
            messageStringBuilder.append(" ").append(preview);
        }
        holder.preview.setText(messageStringBuilder, TextView.BufferType.SPANNABLE);

        formatPreviewText(holder.preview, beforePreviewText, sigil);
        Drawable statusHolder = buildStatusHolder(forwarded, answered);

        if (holder.from != null) {
            holder.from.setTypeface(Typeface.create(holder.from.getTypeface(), maybeBoldTypeface));
            if (fragment.mSenderAboveSubject) {
                holder.from.setCompoundDrawablesWithIntrinsicBounds(
                        statusHolder, null, hasAttachments ? mAttachmentIcon : null, null);
                holder.from.setText(displayName);
            }
            else {
                holder.from.setText(new SpannableStringBuilder(sigil).append(displayName));
            }
        }
        if (holder.subject != null) {
            if (!fragment.mSenderAboveSubject) {
                holder.subject.setCompoundDrawablesWithIntrinsicBounds(
                        statusHolder, null, hasAttachments ? mAttachmentIcon : null, null);
            }
            holder.subject.setTypeface(Typeface.create(holder.subject.getTypeface(), maybeBoldTypeface));
            holder.subject.setText(subject);
        }
        holder.date.setText(displayDate);
    }

    private void formatPreviewText(TextView preview, CharSequence beforePreviewText, String sigil)
    {
        Spannable previewText = (Spannable) preview.getText();
        previewText.setSpan(buildSenderSpan(), 0, beforePreviewText.length() + sigil.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        int previewSpanColor = buildPreviewSpanColor();
        // Set span (color) for preview message
        previewText.setSpan(new ForegroundColorSpan(previewSpanColor), beforePreviewText.length() + sigil.length(),
                previewText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /**
     * Create a span section for the sender, and assign the correct font size and weight
     */
    private AbsoluteSizeSpan buildSenderSpan()
    {
        int fontSize = (fragment.mSenderAboveSubject) ? fontSizes.getMessageListSubject() : fontSizes.getMessageListSender();
        return new AbsoluteSizeSpan(fontSize, true);
    }

    private Address fetchCounterPartyAddress(boolean fromMe, Address[] toAddrs, Address[] ccAddrs, Address[] fromAddrs)
    {
        if (fromMe) {
            if (toAddrs.length > 0) {
                return toAddrs[0];
            }
            else if (ccAddrs.length > 0) {
                return ccAddrs[0];
            }
        }
        else if (fromAddrs.length > 0) {
            return fromAddrs[0];
        }
        return null;
    }

    private void updateContactBadge(MessageViewHolder holder, Address counterpartyAddress)
    {
        if (counterpartyAddress != null) {
            Utility.setContactForBadge(holder.contactBadge, counterpartyAddress);
            /*
             * At least in Android 2.2 a different background + padding is used when no
             * email address is available. ListView reuses the views but ContactBadge
             * doesn't reset the padding, so we do it ourselves.
             */
            holder.contactBadge.setPadding(0, 0, 0, 0);
            fragment.contactsPictureLoader.loadContactPicture(counterpartyAddress, holder.contactBadge);
        }
        else {
            holder.contactBadge.assignContactUri(null);
            holder.contactBadge.setImageResource(R.drawable.ic_contact_picture);
        }
    }

    private void changeBackgroundColorIfActiveMessage(Cursor cursor, Account account, View view)
    {
        String uid = cursor.getString(UID_COLUMN);
        String folderName = cursor.getString(FOLDER_NAME_COLUMN);

        if (account.getUuid().equals(fragment.mActiveMessage.getAccountUuid())
                && folderName.equals(fragment.mActiveMessage.getFolderServerId())
                && uid.equals(fragment.mActiveMessage.getUid())) {
            int res = R.attr.messageListActiveItemBackgroundColor;

            TypedValue outValue = new TypedValue();
            fragment.getActivity().getTheme().resolveAttribute(res, outValue, true);
            view.setBackgroundColor(outValue.data);
        }
    }

    private int buildPreviewSpanColor()
    {
        //TODO: make this part of the theme
        return (XryptoMail.getXMTheme() == XryptoMail.Theme.LIGHT) ?
                Color.rgb(105, 105, 105) :
                Color.rgb(160, 160, 160);
    }

    private Drawable buildStatusHolder(boolean forwarded, boolean answered)
    {
        if (forwarded && answered) {
            return mForwardedAnsweredIcon;
        }
        else if (answered) {
            return mAnsweredIcon;
        }
        else if (forwarded) {
            return mForwardedIcon;
        }
        return null;
    }

    /**
     * Hightlight messageList background based on read state and message encryption type
     * @param cursor the message cursor
     * @param view view
     * @param selected user selected
     * @param read read state
     */
    private void setBackgroundColor(Cursor cursor, View view, boolean selected, boolean read)
    {
        // cmeng - Background color for Crypto and Stealth message added
        PreviewType previewType = getPreviewType(cursor);
        if (selected || XryptoMail.useBackgroundAsUnreadIndicator()) {
            int res;
            if (previewType == PreviewType.ENCRYPTED) {
                if (selected) {
                    res = R.attr.messageListSelectedEncryptedBackgroundColor;
                }
                else if (read) {
                    // res = R.attr.messageListReadEncryptedBackgroundColor;
                    res = R.attr.messageListUnreadEncryptedBackgroundColor;
                }
                else {
                    res = R.attr.messageListUnreadEncryptedBackgroundColor;
                }
            }
            else if (previewType == PreviewType.STEALTH) {
                if (selected) {
                    res = R.attr.messageListSelectedStealthBackgroundColor;
                }
                else if (read) {
                    // res = R.attr.messageListReadStealthBackgroundColor;
                    res = R.attr.messageListUnreadStealthBackgroundColor;
                }
                else {
                    res = R.attr.messageListUnreadStealthBackgroundColor;
                }
            }
            else {
                if (selected) {
                    res = R.attr.messageListSelectedBackgroundColor;
                }
                else if (read) {
                    res = R.attr.messageListReadItemBackgroundColor;
                }
                else {
                    res = R.attr.messageListUnreadItemBackgroundColor;
                }
            }
            TypedValue outValue = new TypedValue();
            fragment.getActivity().getTheme().resolveAttribute(res, outValue, true);
            view.setBackgroundColor(outValue.data);
        }
        else {
            view.setBackgroundColor(Color.TRANSPARENT);
        }
    }
    private void updateWithThreadCount(MessageViewHolder holder, int threadCount)
    {
        if (threadCount > 1) {
            holder.threadCount.setText(String.format(Locale.US, "%d", threadCount));
            holder.threadCount.setVisibility(View.VISIBLE);
        }
        else {
            holder.threadCount.setVisibility(View.GONE);
        }
    }

    /**
     * Return the preview type of the message in the specified position
     * @param cursor the message location
     * @return the message preview type
     */
    private String getPreview(Cursor cursor)
    {
        String previewTypeString = cursor.getString(PREVIEW_TYPE_COLUMN);
        DatabasePreviewType previewType = DatabasePreviewType.fromDatabaseValue(previewTypeString);
        switch (previewType) {
            case NONE:
            case ERROR: {
                return "";
            }
            case ENCRYPTED: {
                return fragment.getString(R.string.preview_encrypted);
            }
            case STEALTH: {
                return fragment.getString(R.string.preview_stealth);
            }
            case TEXT: {
                return cursor.getString(PREVIEW_COLUMN);
            }
            default:
                break;
        }
        throw new AssertionError("Unknown preview type: " + previewType);
    }

    private PreviewType getPreviewType(Cursor cursor)
    {
        String previewTypeString = cursor.getString(PREVIEW_TYPE_COLUMN);
        return DatabasePreviewType.fromDatabaseValue(previewTypeString).getPreviewType();
    }
}
