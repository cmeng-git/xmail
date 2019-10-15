package org.atalk.xryptomail.message.extractors;

import timber.log.Timber;
import org.atalk.xryptomail.XryptoMail;
import org.atalk.xryptomail.mail.Part;
import org.atalk.xryptomail.mail.internet.MessageExtractor;
import org.atalk.xryptomail.mail.internet.MimeUtility;
import org.atalk.xryptomail.message.SimpleMessageFormat;
import org.atalk.xryptomail.message.html.HtmlConverter;

//TODO: Get rid of this class and use MessageViewInfoExtractor instead
public class BodyTextExtractor {
    /** Fetch the body text from a messagePart in the desired messagePart format. This method handles
     * conversions between formats (html to text and vice versa) if necessary.
     */
    public static String getBodyTextFromMessage(Part messagePart, SimpleMessageFormat format) {
        Part part;
        if (format == SimpleMessageFormat.HTML) {
            // HTML takes precedence, then text.
            part = MimeUtility.findFirstPartByMimeType(messagePart, "text/html");
            if (part != null) {
                Timber.d("getBodyTextFromMessage: HTML requested, HTML found.");
                return MessageExtractor.getTextFromPart(part);
            }

            part = MimeUtility.findFirstPartByMimeType(messagePart, "text/plain");
            if (part != null) {
                Timber.d("getBodyTextFromMessage: HTML requested, text found.");
                String text = MessageExtractor.getTextFromPart(part);
                return HtmlConverter.textToHtml(text);
            }
        } else if (format == SimpleMessageFormat.TEXT) {
            // Text takes precedence, then html.
            part = MimeUtility.findFirstPartByMimeType(messagePart, "text/plain");
            if (part != null) {
                Timber.d("getBodyTextFromMessage: Text requested, text found.");
                return MessageExtractor.getTextFromPart(part);
            }

            part = MimeUtility.findFirstPartByMimeType(messagePart, "text/html");
            if (part != null) {
                Timber.d("getBodyTextFromMessage: Text requested, HTML found.");
                String text = MessageExtractor.getTextFromPart(part);
                return HtmlConverter.htmlToText(text);
            }
        }

        // If we had nothing interesting, return an empty string.
        return "";
    }
}
