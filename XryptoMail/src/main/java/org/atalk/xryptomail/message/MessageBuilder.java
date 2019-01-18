package org.atalk.xryptomail.message;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import org.apache.james.mime4j.codec.EncoderUtil;
import org.apache.james.mime4j.util.MimeUtil;
import org.atalk.xryptomail.Account.QuoteStyle;
import org.atalk.xryptomail.Identity;
import org.atalk.xryptomail.R;
import org.atalk.xryptomail.XryptoMail;
import org.atalk.xryptomail.activity.MessageReference;
import org.atalk.xryptomail.activity.misc.Attachment;
import org.atalk.xryptomail.mail.Address;
import org.atalk.xryptomail.mail.Body;
import org.atalk.xryptomail.mail.BoundaryGenerator;
import org.atalk.xryptomail.mail.Flag;
import org.atalk.xryptomail.mail.Message.RecipientType;
import org.atalk.xryptomail.mail.MessagingException;
import org.atalk.xryptomail.mail.filter.Base64;
import org.atalk.xryptomail.mail.internet.MessageIdGenerator;
import org.atalk.xryptomail.mail.internet.MimeBodyPart;
import org.atalk.xryptomail.mail.internet.MimeHeader;
import org.atalk.xryptomail.mail.internet.MimeMessage;
import org.atalk.xryptomail.mail.internet.MimeMessageHelper;
import org.atalk.xryptomail.mail.internet.MimeMultipart;
import org.atalk.xryptomail.mail.internet.MimeUtility;
import org.atalk.xryptomail.mail.internet.TextBody;
import org.atalk.xryptomail.mailstore.TempFileBody;
import org.atalk.xryptomail.message.quote.InsertableHtmlContent;

import java.util.Date;
import java.util.List;
import java.util.Locale;

import timber.log.Timber;

public abstract class MessageBuilder
{
    private final Context context;
    private final MessageIdGenerator messageIdGenerator;
    private final BoundaryGenerator boundaryGenerator;
    private String subject;
    private Date sentDate;
    private boolean hideTimeZone;
    private Address[] to;
    private Address[] cc;
    private Address[] bcc;
    private String inReplyTo;
    private String references;
    private boolean requestReadReceipt;
    private Identity identity;
    private SimpleMessageFormat messageFormat;
    private String text;
    private List<Attachment> attachments;
    private String signature;
    private QuoteStyle quoteStyle;
    private QuotedTextMode quotedTextMode;
    private String quotedText;
    private InsertableHtmlContent quotedHtmlContent;
    private boolean isReplyAfterQuote;
    private boolean isSignatureBeforeQuotedText;
    private boolean identityChanged;
    private boolean signatureChanged;
    private int cursorPosition;
    private MessageReference messageReference;
    private boolean isDraft;
    private boolean isPgpInlineEnabled;
    private boolean mStealthMode = false;

    protected MessageBuilder(Context context, MessageIdGenerator messageIdGenerator, BoundaryGenerator boundaryGenerator)
    {
        this.context = context;
        this.messageIdGenerator = messageIdGenerator;
        this.boundaryGenerator = boundaryGenerator;
    }

    /**
     * Build the message to be sent (or saved). If there is another message quoted in this one, it will be baked
     * into the message here.
     */
    protected MimeMessage build(boolean stealthMode)
            throws MessagingException
    {
        //FIXME: check arguments
        mStealthMode = stealthMode;
        MimeMessage message = new MimeMessage();
        buildHeader(message);
        buildBody(message);
        return message;
    }

    private void buildHeader(MimeMessage message)
            throws MessagingException
    {
        message.addSentDate(sentDate, hideTimeZone);
        Address from = new Address(identity.getEmail(), identity.getName());
        message.setFrom(from);
        message.setRecipients(RecipientType.TO, to);
        message.setRecipients(RecipientType.CC, cc);
        message.setRecipients(RecipientType.BCC, bcc);
        message.setSubject(subject);

        if (requestReadReceipt) {
            message.setHeader("Disposition-Notification-To", from.toEncodedString());
            message.setHeader("X-Confirm-Reading-To", from.toEncodedString());
            message.setHeader("Return-Receipt-To", from.toEncodedString());
        }

        if (!XryptoMail.hideUserAgent()) {
            message.setHeader("User-Agent", context.getString(R.string.message_header_mua));
        }

        final String replyTo = identity.getReplyTo();
        if (replyTo != null) {
            message.setReplyTo(new Address[]{new Address(replyTo)});
        }

        if (inReplyTo != null) {
            message.setInReplyTo(inReplyTo);
        }

        if (references != null) {
            message.setReferences(references);
        }

        String messageId = messageIdGenerator.generateMessageId(message);
        message.setMessageId(messageId);

        if (isDraft && isPgpInlineEnabled) {
            message.setFlag(Flag.X_DRAFT_OPENPGP_INLINE, true);
        }
    }

    protected MimeMultipart createMimeMultipart()
    {
        String boundary = boundaryGenerator.generateBoundary();
        return new MimeMultipart(boundary);
    }

    private void buildBody(MimeMessage message)
            throws MessagingException
    {
        // Build the body.
        // TODO FIXME - body can be either an HTML or Text part, depending on whether we're in
        // HTML mode or not.  Should probably fix this so we don't mix up html and text parts.
        TextBody body = buildText(isDraft);

        // text/plain part when messageFormat == MessageFormat.HTML
        TextBody bodyPlain = null;
        final boolean hasAttachments = !attachments.isEmpty();

        if (messageFormat == SimpleMessageFormat.HTML) {
            // HTML message (with alternative text part)

            // This is the compiled MIME part for an HTML message.
            MimeMultipart composedMimeMessage = createMimeMultipart();
            composedMimeMessage.setSubType("alternative");
            // Let the receiver select either the text or the HTML part.
            bodyPlain = buildText(isDraft, SimpleMessageFormat.TEXT);
            composedMimeMessage.addBodyPart(new MimeBodyPart(bodyPlain, "text/plain"));
            composedMimeMessage.addBodyPart(new MimeBodyPart(body, "text/html"));

            if (hasAttachments) {
                // If we're HTML and have attachments, we have a MimeMultipart container to hold the
                // whole message (mp here), of which one part is a MimeMultipart container
                // (composedMimeMessage) with the user's composed messages, and subsequent parts for
                // the attachments.
                MimeMultipart mp = createMimeMultipart();
                mp.addBodyPart(new MimeBodyPart(composedMimeMessage));
                addAttachmentsToMessage(mp);
                MimeMessageHelper.setBody(message, mp);
            }
            else {
                // If no attachments, our multipart/alternative part is the only one we need.
                MimeMessageHelper.setBody(message, composedMimeMessage);
            }
        }
        else if (messageFormat == SimpleMessageFormat.TEXT) {
            // Text-only message.
            if (hasAttachments) {
                MimeMultipart mp = createMimeMultipart();
                mp.addBodyPart(new MimeBodyPart(body, "text/plain"));
                addAttachmentsToMessage(mp);
                MimeMessageHelper.setBody(message, mp);
            }
            else {
                // No attachments to include, just stick the text body in the message and call it good.
                MimeMessageHelper.setBody(message, body);
            }
        }

        // If this is a draft, add metadata for thawing.
        if (isDraft) {
            // Add the identity to the message.
            message.addHeader(XryptoMail.IDENTITY_HEADER, buildIdentityHeader(body, bodyPlain));
        }
    }

    private String buildIdentityHeader(TextBody body, TextBody bodyPlain)
    {
        return new IdentityHeaderBuilder()
                .setCursorPosition(cursorPosition)
                .setIdentity(identity)
                .setIdentityChanged(identityChanged)
                .setMessageFormat(messageFormat)
                .setMessageReference(messageReference)
                .setQuotedHtmlContent(quotedHtmlContent)
                .setQuoteStyle(quoteStyle)
                .setQuoteTextMode(quotedTextMode)
                .setSignature(signature)
                .setSignatureChanged(signatureChanged)
                .setBody(body)
                .setBodyPlain(bodyPlain)
                .build();
    }

    /**
     * Add attachments as parts into a MimeMultipart container.
     *
     * @param mp MimeMultipart container in which to insert parts.
     * @throws MessagingException
     */
    private void addAttachmentsToMessage(final MimeMultipart mp)
            throws MessagingException
    {
        for (Attachment attachment : attachments) {
            if (attachment.state != Attachment.LoadingState.COMPLETE) {
                continue;
            }

            Body body = new TempFileBody(attachment.filename);
            MimeBodyPart bp = new MimeBodyPart(body);

            /*
             * Correctly encode the filename here. Otherwise the whole header value (all parameters at once) will
             * be encoded by MimeHeader.writeTo().
             */
            bp.addHeader(MimeHeader.HEADER_CONTENT_TYPE, String.format("%s;\r\n name=\"%s\"", attachment.contentType,
                    EncoderUtil.encodeIfNecessary(attachment.name,EncoderUtil.Usage.WORD_ENTITY, 7)));

            if (!MimeUtil.isMessage(attachment.contentType)) {
                bp.setEncoding(MimeUtility.getEncodingforType(attachment.contentType));
            }

            /*
             * TODO: Oh the joys of MIME...
             *
             * From RFC 2183 (The Content-Disposition Header Field):
             * "Parameter values longer than 78 characters, or which contain non-ASCII characters, MUST be encoded
             * as specified in [RFC 2184]."
             *
             * Example:
             *
             * Content-Type: application/x-stuff
             *  title*1*=us-ascii'en'This%20is%20even%20more%20
             *  title*2*=%2A%2A%2Afun%2A%2A%2A%20
             *  title*3="isn't it!"
             */
            bp.addHeader(MimeHeader.HEADER_CONTENT_DISPOSITION, String.format(Locale.US,
                    "attachment;\r\n filename=\"%s\";\r\n size=%d", attachment.name, attachment.size));

            mp.addBodyPart(bp);
        }
    }

    /**
     * Build the Body that will contain the text of the message. We'll decide where to
     * include it later. Draft messages are treated somewhat differently in that signatures are not
     * appended and HTML separators between composed text and quoted text are not added.
     *
     * @param isDraft If we should build a message that will be saved as a draft (as opposed to sent).
     */
    private TextBody buildText(boolean isDraft)
    {
        return buildText(isDraft, messageFormat);
    }

    /**
     * Build the {@link Body} that will contain the text of the message.
     *
     * <p>
     * Draft messages are treated somewhat differently in that signatures are not appended and HTML
     * separators between composed text and quoted text are not added.
     * </p>
     *
     * @param isDraft If {@code true} we build a message that will be saved as a draft (as opposed to
     * sent).
     * @param simpleMessageFormat Specifies what type of message to build ({@code text/plain} vs. {@code text/html}).
     * @return {@link TextBody} instance that contains the entered text and possibly the quoted
     * original message.
     */
    private TextBody buildText(boolean isDraft, SimpleMessageFormat simpleMessageFormat)
    {
        String messageText = text;
        TextBodyBuilder textBodyBuilder = new TextBodyBuilder(messageText, mStealthMode);

        /*
         * Find out if we need to include the original message as quoted text.
         *
         * We include the quoted text in the body if the user didn't choose to
         * hide it. We always include the quoted text when we're saving a draft.
         * That's so the user is able to "un-hide" the quoted text if (s)he
         * opens a saved draft.
         */
        boolean includeQuotedText = (isDraft || quotedTextMode == QuotedTextMode.SHOW);
        boolean isReplyAfterQuote = (quoteStyle == QuoteStyle.PREFIX && this.isReplyAfterQuote);

        textBodyBuilder.setIncludeQuotedText(false);
        if (includeQuotedText) {
            if (simpleMessageFormat == SimpleMessageFormat.HTML && quotedHtmlContent != null) {
                textBodyBuilder.setIncludeQuotedText(true);
                textBodyBuilder.setQuotedTextHtml(quotedHtmlContent);
                textBodyBuilder.setReplyAfterQuote(isReplyAfterQuote);
            }

            if (simpleMessageFormat == SimpleMessageFormat.TEXT && quotedText.length() > 0) {
                textBodyBuilder.setIncludeQuotedText(true);
                textBodyBuilder.setQuotedText(quotedText);
                textBodyBuilder.setReplyAfterQuote(isReplyAfterQuote);
            }
        }
        textBodyBuilder.setInsertSeparator(!isDraft);
        boolean useSignature = (!isDraft && identity.getSignatureUse());
        if (useSignature) {
            textBodyBuilder.setAppendSignature(true);
            textBodyBuilder.setSignature(signature);
            textBodyBuilder.setSignatureBeforeQuotedText(isSignatureBeforeQuotedText);
        }
        else {
            textBodyBuilder.setAppendSignature(false);
        }

        TextBody body;
        if (simpleMessageFormat == SimpleMessageFormat.HTML) {
            body = textBodyBuilder.buildTextHtml(isDraft);
        }
        else {
            body = textBodyBuilder.buildTextPlain(isDraft);
        }
        return body;
    }

    public MessageBuilder setSubject(String subject)
    {
        this.subject = subject;
        return this;
    }

    public MessageBuilder setSentDate(Date sentDate)
    {
        this.sentDate = sentDate;
        return this;
    }

    public MessageBuilder setHideTimeZone(boolean hideTimeZone)
    {
        this.hideTimeZone = hideTimeZone;
        return this;
    }

    public MessageBuilder setTo(List<Address> to)
    {
        this.to = to.toArray(new Address[to.size()]);
        return this;
    }

    public MessageBuilder setCc(List<Address> cc)
    {
        this.cc = cc.toArray(new Address[cc.size()]);
        return this;
    }

    public MessageBuilder setBcc(List<Address> bcc)
    {
        this.bcc = bcc.toArray(new Address[bcc.size()]);
        return this;
    }

    public MessageBuilder setInReplyTo(String inReplyTo)
    {
        this.inReplyTo = inReplyTo;
        return this;
    }

    public MessageBuilder setReferences(String references)
    {
        this.references = references;
        return this;
    }

    public MessageBuilder setRequestReadReceipt(boolean requestReadReceipt)
    {
        this.requestReadReceipt = requestReadReceipt;
        return this;
    }

    public MessageBuilder setIdentity(Identity identity)
    {
        this.identity = identity;
        return this;
    }

    public MessageBuilder setMessageFormat(SimpleMessageFormat messageFormat)
    {
        this.messageFormat = messageFormat;
        return this;
    }

    public MessageBuilder setText(String text)
    {
        this.text = text;
        return this;
    }

    public MessageBuilder setAttachments(List<Attachment> attachments)
    {
        this.attachments = attachments;
        return this;
    }

    public MessageBuilder setSignature(String signature)
    {
        this.signature = signature;
        return this;
    }

    public MessageBuilder setQuoteStyle(QuoteStyle quoteStyle)
    {
        this.quoteStyle = quoteStyle;
        return this;
    }

    public MessageBuilder setQuotedTextMode(QuotedTextMode quotedTextMode)
    {
        this.quotedTextMode = quotedTextMode;
        return this;
    }

    public MessageBuilder setQuotedText(String quotedText)
    {
        this.quotedText = quotedText;
        return this;
    }

    public MessageBuilder setQuotedHtmlContent(InsertableHtmlContent quotedHtmlContent)
    {
        this.quotedHtmlContent = quotedHtmlContent;
        return this;
    }

    public MessageBuilder setReplyAfterQuote(boolean isReplyAfterQuote)
    {
        this.isReplyAfterQuote = isReplyAfterQuote;
        return this;
    }

    public MessageBuilder setSignatureBeforeQuotedText(boolean isSignatureBeforeQuotedText)
    {
        this.isSignatureBeforeQuotedText = isSignatureBeforeQuotedText;
        return this;
    }

    public MessageBuilder setIdentityChanged(boolean identityChanged)
    {
        this.identityChanged = identityChanged;
        return this;
    }

    public MessageBuilder setSignatureChanged(boolean signatureChanged)
    {
        this.signatureChanged = signatureChanged;
        return this;
    }

    public MessageBuilder setCursorPosition(int cursorPosition)
    {
        this.cursorPosition = cursorPosition;
        return this;
    }

    public MessageBuilder setMessageReference(MessageReference messageReference)
    {
        this.messageReference = messageReference;
        return this;
    }

    public MessageBuilder setDraft(boolean isDraft)
    {
        this.isDraft = isDraft;
        return this;
    }

    public MessageBuilder setIsPgpInlineEnabled(boolean isPgpInlineEnabled)
    {
        this.isPgpInlineEnabled = isPgpInlineEnabled;
        return this;
    }

    public boolean isDraft()
    {
        return isDraft;
    }

    private Callback asyncCallback;
    private final Object callbackLock = new Object();

    // Postponed results, to be delivered upon reattachment of callback. There should only ever be one of these!
    private MimeMessage queuedMimeMessage;
    private MessagingException queuedException;
    private PendingIntent queuedPendingIntent;
    private int queuedRequestCode;

    /**
     * This method builds the message asynchronously, calling *exactly one* of the methods
     * on the callback on the UI thread after it finishes. The callback may thread-safely
     * be detached and reattached intermittently.
     */
    final public void buildAsync(Callback callback)
    {
        synchronized (callbackLock) {
            asyncCallback = callback;
            queuedMimeMessage = null;
            queuedException = null;
            queuedPendingIntent = null;
        }
        new AsyncTask<Void, Void, Void>()
        {
            @Override
            protected Void doInBackground(Void... params)
            {
                buildMessageInternal();
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid)
            {
                deliverResult();
            }
        }.execute();
    }

    final public void onActivityResult(final int requestCode, int resultCode, final Intent data, Callback callback)
    {
        synchronized (callbackLock) {
            asyncCallback = callback;
            queuedMimeMessage = null;
            queuedException = null;
            queuedPendingIntent = null;
        }
        if (resultCode != Activity.RESULT_OK) {
            asyncCallback.onMessageBuildCancel();
            return;
        }
        new AsyncTask<Void, Void, Void>()
        {
            @Override
            protected Void doInBackground(Void... params)
            {
                buildMessageOnActivityResult(requestCode, data);
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid)
            {
                deliverResult();
            }
        }.execute();
    }

    /**
     * This method is called in a worker thread, and should build the actual message. To deliver
     * its computation result, it must call *exactly one* of the queueMessageBuild* methods before
     * it finishes.
     */
    abstract protected void buildMessageInternal();

    abstract protected void buildMessageOnActivityResult(int requestCode, Intent data);

    /**
     * This method may be used to temporarily detach the callback. If a result is delivered
     * while the callback is detached, it will be delivered upon reattachment.
     */
    final public void detachCallback()
    {
        synchronized (callbackLock) {
            asyncCallback = null;
        }
    }

    /**
     * This method attaches a new callback, and must only be called after a previous one was
     * detached. If the computation finished while the callback was detached, it will be
     * delivered immediately upon reattachment.
     */
    final public void reattachCallback(Callback callback)
    {
        synchronized (callbackLock) {
            if (asyncCallback != null) {
                throw new IllegalStateException("need to detach callback before new one can be attached!");
            }
            asyncCallback = callback;
            deliverResult();
        }
    }

    final protected void queueMessageBuildSuccess(MimeMessage message)
    {
        synchronized (callbackLock) {
            queuedMimeMessage = message;
        }
    }

    final protected void queueMessageBuildException(MessagingException exception)
    {
        synchronized (callbackLock) {
            queuedException = exception;
        }
    }

    final protected void queueMessageBuildPendingIntent(PendingIntent pendingIntent, int requestCode)
    {
        synchronized (callbackLock) {
            queuedPendingIntent = pendingIntent;
            queuedRequestCode = requestCode;
        }
    }

    final protected void deliverResult()
    {
        synchronized (callbackLock) {
            if (asyncCallback == null) {
                Timber.d("Keeping message builder result in queue for later delivery");
                return;
            }
            if (queuedMimeMessage != null) {
                asyncCallback.onMessageBuildSuccess(queuedMimeMessage, isDraft);
                queuedMimeMessage = null;
            }
            else if (queuedException != null) {
                asyncCallback.onMessageBuildException(queuedException);
                queuedException = null;
            }
            else if (queuedPendingIntent != null) {
                asyncCallback.onMessageBuildReturnPendingIntent(queuedPendingIntent, queuedRequestCode);
                queuedPendingIntent = null;
            }
            asyncCallback = null;
        }
    }

    public interface Callback
    {
        void onMessageBuildSuccess(MimeMessage message, boolean isDraft);

        void onMessageBuildCancel();

        void onMessageBuildException(MessagingException exception);

        void onMessageBuildReturnPendingIntent(PendingIntent pendingIntent, int requestCode);
    }
}
