package org.atalk.xryptomail.notification;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;

import org.atalk.xryptomail.Account;
import org.atalk.xryptomail.R;
import org.atalk.xryptomail.XryptoMail;
import org.atalk.xryptomail.activity.MessageReference;
import org.atalk.xryptomail.helper.Contacts;
import org.atalk.xryptomail.helper.MessageHelper;
import org.atalk.xryptomail.mail.Address;
import org.atalk.xryptomail.mail.Flag;
import org.atalk.xryptomail.mail.Message;
import org.atalk.xryptomail.mailstore.LocalMessage;
import org.atalk.xryptomail.message.extractors.PreviewResult.PreviewType;

class NotificationContentCreator
{
    private final Context mContext;

    public NotificationContentCreator(Context context)
    {
        mContext = context;
    }

    public NotificationContent createFromMessage(Account account, LocalMessage message)
    {
        MessageReference messageReference = message.makeMessageReference();
        String sender = getMessageSender(account, message);
        String displaySender = getMessageSenderForDisplay(sender);
        String subject = getMessageSubject(message);
        CharSequence preview = getMessagePreview(message);
        CharSequence summary = buildMessageSummary(sender, subject);
        boolean starred = message.isSet(Flag.FLAGGED);

        return new NotificationContent(messageReference, displaySender, subject, preview, summary, starred);
    }

    private CharSequence getMessagePreview(LocalMessage message)
    {
        String subject = message.getSubject();
        String snippet = getPreview(message);

        boolean isSubjectEmpty = TextUtils.isEmpty(subject);
        boolean isSnippetPresent = snippet != null;
        if (isSubjectEmpty && isSnippetPresent) {
            return snippet;
        }

        String displaySubject = getMessageSubject(message);
        SpannableStringBuilder preview = new SpannableStringBuilder();
        preview.append(displaySubject);
        if (isSnippetPresent) {
            preview.append('\n');
            preview.append(snippet);
        }
        return preview;
    }

    private String getPreview(LocalMessage message)
    {
        PreviewType previewType = message.getPreviewType();
        switch (previewType) {
            case NONE:
            case ERROR:
                return null;
            case TEXT:
                return message.getPreview();
            case ENCRYPTED:
                return mContext.getString(R.string.preview_encrypted);
            case STEALTH:
                return mContext.getString(R.string.preview_stealth);
        }
        throw new AssertionError("Unknown preview type: " + previewType);
    }

    private CharSequence buildMessageSummary(String sender, String subject)
    {
        if (sender == null) {
            return subject;
        }

        SpannableStringBuilder summary = new SpannableStringBuilder();
        summary.append(sender);
        summary.append(" ");
        summary.append(subject);
        return summary;
    }

    private String getMessageSubject(Message message)
    {
        String subject = message.getSubject();
        if (!TextUtils.isEmpty(subject)) {
            return subject;
        }
        return mContext.getString(R.string.general_no_subject);
    }

    private String getMessageSender(Account account, Message message)
    {
        boolean isSelf = false;
        final Contacts contacts = XryptoMail.showContactName() ? Contacts.getInstance(mContext) : null;
        final Address[] fromAddresses = message.getFrom();

        if (fromAddresses != null) {
            isSelf = account.isAnIdentity(fromAddresses);
            if (!isSelf && fromAddresses.length > 0) {
                return MessageHelper.toFriendly(fromAddresses[0], contacts).toString();
            }
        }

        if (isSelf) {
            // show To: if the message was sent from me
            Address[] recipients = message.getRecipients(Message.RecipientType.TO);

            if (recipients != null && recipients.length > 0) {
                return mContext.getString(R.string.message_to_fmt,
                        MessageHelper.toFriendly(recipients[0], contacts).toString());
            }
        }
        return null;
    }

    private String getMessageSenderForDisplay(String sender)
    {
        return (sender != null) ? sender : mContext.getString(R.string.general_no_sender);
    }
}
