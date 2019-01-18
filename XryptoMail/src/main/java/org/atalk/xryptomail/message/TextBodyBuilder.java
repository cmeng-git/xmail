package org.atalk.xryptomail.message;

import android.text.TextUtils;
import timber.log.Timber;

import org.atalk.xryptomail.XryptoMail;
import org.atalk.xryptomail.mail.Body;
import org.atalk.xryptomail.mail.filter.Base64;
import org.atalk.xryptomail.mail.internet.TextBody;
import org.atalk.xryptomail.message.html.HtmlConverter;
import org.atalk.xryptomail.message.quote.InsertableHtmlContent;

class TextBodyBuilder {
    private boolean mIncludeQuotedText = true;
    private boolean mReplyAfterQuote = false;
    private boolean mSignatureBeforeQuotedText = false;
    private boolean mInsertSeparator = false;
    private boolean mAppendSignature = true;
    private boolean mStealthMode = false;

    private String mMessageContent;
    private String mSignature;
    private String mQuotedText;
    private InsertableHtmlContent mQuotedTextHtml;

    public TextBodyBuilder(String messageContent, boolean stealthMode) {
        mMessageContent = messageContent;
        mStealthMode = stealthMode;
    }

    /**
     * Build the {@link Body} that will contain the text of the message.
     *
     * @return {@link org.atalk.xryptomail.mail.internet.TextBody} instance that contains the entered text and
     *         possibly the quoted original message.
     */
    public TextBody buildTextHtml(boolean isDraft) {
        // The length of the formatted version of the user-supplied text/reply
        int composedMessageLength;

        // The offset of the user-supplied text/reply in the final text body
        int composedMessageOffset;

        // Get the user-supplied text
        String text = mMessageContent;

        // Do we have to modify an existing message to include our reply?
        if (mIncludeQuotedText) {
            InsertableHtmlContent quotedHtmlContent = getQuotedTextHtml();

            if (XryptoMail.isDebug()) {
                Timber.d("insertable: %s", quotedHtmlContent.toDebugString());
            }

            if (mAppendSignature) {
                // Append signature to the reply
                if (mReplyAfterQuote || mSignatureBeforeQuotedText) {
                    text += getSignature();
                }
            }

            // Convert the text to HTML
            text = textToHtmlFragment(text);

            /*
             * Set the insertion location based upon our reply after quote setting. Additionally, add some extra
             * separators between the composed message and quoted message depending on the quote location. We only
             * add the extra separators when we're sending, that way when we load a draft, we don't have to know
             * the length of the separators to remove them before editing.
             */
            if (mReplyAfterQuote) {
                quotedHtmlContent.setInsertionLocation(InsertableHtmlContent.InsertionLocation.AFTER_QUOTE);
                if (mInsertSeparator) {
                    text = "<br clear=\"all\">" + text;
                }
            } else {
                quotedHtmlContent.setInsertionLocation(InsertableHtmlContent.InsertionLocation.BEFORE_QUOTE);
                if (mInsertSeparator) {
                    text += "<br><br>";
                }
            }

            if (mAppendSignature) {
                // Place signature immediately after the quoted text
                if (!(mReplyAfterQuote || mSignatureBeforeQuotedText)) {
                    quotedHtmlContent.insertIntoQuotedFooter(getSignatureHtml());
                }
            }
            quotedHtmlContent.setUserContent(text);

            // Save length of the body and its offset.  This is used when thawing drafts.
            composedMessageLength = text.length();
            composedMessageOffset = quotedHtmlContent.getInsertionPoint();
            text = quotedHtmlContent.toString();

        } else {
            // There is no text to quote so simply append the signature if available
            if (mAppendSignature) {
                text += getSignature();
            }

            // Convert the text to HTML
            text = textToHtmlFragment(text);

            //TODO: Wrap this in proper HTML tags
            composedMessageLength = text.length();
            composedMessageOffset = 0;
        }

        if (mStealthMode && !isDraft)
            text = Base64.encode(text);
        TextBody body = new TextBody(text);

        body.setComposedMessageLength(composedMessageLength);
        body.setComposedMessageOffset(composedMessageOffset);
        return body;
    }

    /**
     * Build the {@link Body} that will contain the text of the message.
     *
     * @return {@link TextBody} instance that contains the entered text and
     *         possibly the quoted original message.
     */
    public TextBody buildTextPlain(boolean isDraft) {
        // The length of the formatted version of the user-supplied text/reply
        int composedMessageLength;

        // The offset of the user-supplied text/reply in the final text body
        int composedMessageOffset;

        // Get the user-supplied text
        String text = mMessageContent;

        // Capture composed message length before we start attaching quoted parts and signatures.
        composedMessageLength = text.length();
        composedMessageOffset = 0;

        // Do we have to modify an existing message to include our reply?
        if (mIncludeQuotedText) {
            String quotedText = getQuotedText();

            if (mAppendSignature) {
                // Append signature to the text/reply
                if (mReplyAfterQuote || mSignatureBeforeQuotedText) {
                    text += getSignature();
                }
            }

            if (mReplyAfterQuote) {
                composedMessageOffset = quotedText.length() + "\r\n".length();
                text = quotedText + "\r\n" + text;
            } else {
                text += "\r\n\r\n" + quotedText;
            }

            if (mAppendSignature) {
                // Place signature immediately after the quoted text
                if (!(mReplyAfterQuote || mSignatureBeforeQuotedText)) {
                    text += getSignature();
                }
            }
        } else {
            // There is no text to quote so simply append the signature if available
            if (mAppendSignature) {
                // Append signature to the text/reply
                text += getSignature();
            }
        }

        if (mStealthMode  && !isDraft)
            text = Base64.encode(text);
        TextBody body = new TextBody(text);

        body.setComposedMessageLength(composedMessageLength);
        body.setComposedMessageOffset(composedMessageOffset);
        return body;
    }

    private String getSignature() {
        String signature = "";
        if (!TextUtils.isEmpty(mSignature)) {
            signature = "\r\n" + mSignature;
        }

        return signature;
    }

    private String getSignatureHtml() {
        String signature = "";
        if (!TextUtils.isEmpty(mSignature)) {
            signature = textToHtmlFragment("\r\n" + mSignature);
        }
        return signature;
    }

    private String getQuotedText() {
        String quotedText = "";
        if (!TextUtils.isEmpty(mQuotedText)) {
            quotedText = mQuotedText;
        }
        return quotedText;
    }

    private InsertableHtmlContent getQuotedTextHtml() {
        return mQuotedTextHtml;
    }

    /**
     * protected for unit-test purposes
     */
    protected String textToHtmlFragment(String text) {
        return HtmlConverter.textToHtmlFragment(text);
    }

    public void setSignature(String signature) {
        mSignature = signature;
    }

    public void setIncludeQuotedText(boolean includeQuotedText) {
        mIncludeQuotedText = includeQuotedText;
    }

    public void setQuotedText(String quotedText) {
        mQuotedText = quotedText;
    }

    public void setQuotedTextHtml(InsertableHtmlContent quotedTextHtml) {
        mQuotedTextHtml = quotedTextHtml;
    }

    public void setInsertSeparator(boolean insertSeparator) {
        mInsertSeparator = insertSeparator;
    }

    public void setSignatureBeforeQuotedText(boolean signatureBeforeQuotedText) {
        mSignatureBeforeQuotedText = signatureBeforeQuotedText;
    }

    public void setReplyAfterQuote(boolean replyAfterQuote) {
        mReplyAfterQuote = replyAfterQuote;
    }

    public void setAppendSignature(boolean appendSignature) {
        mAppendSignature = appendSignature;
    }
}
