package org.atalk.xryptomail.message.quote;

import android.content.res.Resources;

import org.atalk.xryptomail.Account.QuoteStyle;
import org.atalk.xryptomail.R;
import org.atalk.xryptomail.helper.Utility;
import org.atalk.xryptomail.mail.Address;
import org.atalk.xryptomail.mail.Message;
import org.atalk.xryptomail.mail.Message.RecipientType;
import org.atalk.xryptomail.mail.MessagingException;

import static org.apache.james.mime4j.util.CharsetUtil.CRLF;
import static org.atalk.xryptomail.message.quote.QuoteHelper.QUOTE_BUFFER_LENGTH;

public class TextQuoteCreator
{
    private static final int REPLY_WRAP_LINE_WIDTH = 72;

    /**
     * Add quoting markup to a text message.
     *
     * @param originalMessage Metadata for message being quoted.
     * @param messageBody Text of the message to be quoted.
     * @param quoteStyle Style of quoting.
     * @return Quoted text.
     * @throws MessagingException
     */
    public static String quoteOriginalTextMessage(Resources resources, Message originalMessage, String messageBody, QuoteStyle quoteStyle, String prefix)
    {
        String body = messageBody == null ? "" : messageBody;
        String sentDate = QuoteHelper.getSentDateText(resources, originalMessage);
        if (quoteStyle == QuoteStyle.PREFIX) {
            StringBuilder quotedText = new StringBuilder(body.length() + QUOTE_BUFFER_LENGTH);
            if (sentDate.length() != 0) {
                quotedText.append(String.format(
                        resources.getString(R.string.message_compose_reply_header_fmt_with_date) + CRLF,
                        sentDate,
                        Address.toString(originalMessage.getFrom())));
            }
            else {
                quotedText.append(String.format(
                        resources.getString(R.string.message_compose_reply_header_fmt) + CRLF,
                        Address.toString(originalMessage.getFrom()))
                );
            }

            final String wrappedText = Utility.wrap(body, REPLY_WRAP_LINE_WIDTH - prefix.length());

            // "$" and "\" in the quote prefix have to be escaped for
            // the replaceAll() invocation.
            final String escapedPrefix = prefix.replaceAll("(\\\\|\\$)", "\\\\$1");
            quotedText.append(wrappedText.replaceAll("(?m)^", escapedPrefix));

            // TODO is this correct?
            return quotedText.toString().replaceAll("\\\r", "");
        }
        else if (quoteStyle == QuoteStyle.HEADER) {
            StringBuilder quotedText = new StringBuilder(body.length() + QUOTE_BUFFER_LENGTH);
            quotedText.append(CRLF)
                    .append(resources.getString(R.string.message_compose_quote_header_separator))
                    .append(CRLF);
            if (originalMessage.getFrom() != null && Address.toString(originalMessage.getFrom()).length() != 0) {
                quotedText.append(resources.getString(R.string.message_compose_quote_header_from))
                        .append(" ")
                        .append(Address.toString(originalMessage.getFrom()))
                        .append(CRLF);
            }
            if (sentDate.length() != 0) {
                quotedText.append(resources.getString(R.string.message_compose_quote_header_send_date))
                        .append(" ")
                        .append(sentDate)
                        .append(CRLF);
            }
            if (originalMessage.getRecipients(RecipientType.TO) != null && originalMessage.getRecipients(RecipientType.TO).length != 0) {
                quotedText.append(resources.getString(R.string.message_compose_quote_header_to))
                        .append(" ")
                        .append(Address.toString(originalMessage.getRecipients(RecipientType.TO)))
                        .append(CRLF);
            }
            if (originalMessage.getRecipients(RecipientType.CC) != null && originalMessage.getRecipients(RecipientType.CC).length != 0) {
                quotedText.append(resources.getString(R.string.message_compose_quote_header_cc))
                        .append(" ")
                        .append(Address.toString(originalMessage.getRecipients(RecipientType.CC)))
                        .append(CRLF);
            }
            if (originalMessage.getSubject() != null) {
                quotedText.append(resources.getString(R.string.message_compose_quote_header_subject))
                        .append(" ")
                        .append(originalMessage.getSubject())
                        .append(CRLF);
            }
            quotedText.append(CRLF);
            quotedText.append(body);
            return quotedText.toString();
        }
        else {
            // Shouldn't ever happen.
            return body;
        }
    }
}
