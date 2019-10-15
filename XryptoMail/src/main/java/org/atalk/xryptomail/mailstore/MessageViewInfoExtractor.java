package org.atalk.xryptomail.mailstore;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import org.atalk.xryptomail.Globals;
import org.atalk.xryptomail.R;
import org.atalk.xryptomail.XryptoMail;
import org.atalk.xryptomail.crypto.MessageCryptoStructureDetector;
import org.atalk.xryptomail.mail.Address;
import org.atalk.xryptomail.mail.Flag;
import org.atalk.xryptomail.mail.Message;
import org.atalk.xryptomail.mail.MessagingException;
import org.atalk.xryptomail.mail.Part;
import org.atalk.xryptomail.mail.filter.Base64;
import org.atalk.xryptomail.mail.internet.MessageExtractor;
import org.atalk.xryptomail.mail.internet.Viewable;
import org.atalk.xryptomail.mail.internet.Viewable.Alternative;
import org.atalk.xryptomail.mail.internet.Viewable.Flowed;
import org.atalk.xryptomail.mail.internet.Viewable.Html;
import org.atalk.xryptomail.mail.internet.Viewable.MessageHeader;
import org.atalk.xryptomail.mail.internet.Viewable.Text;
import org.atalk.xryptomail.mail.internet.Viewable.Textual;
import org.atalk.xryptomail.mailstore.CryptoResultAnnotation.CryptoError;
import org.atalk.xryptomail.mailstore.util.FlowedMessageUtils;
import org.atalk.xryptomail.message.extractors.AttachmentInfoExtractor;
import org.atalk.xryptomail.message.extractors.PreviewResult;
import org.atalk.xryptomail.message.html.HtmlConverter;
import org.atalk.xryptomail.message.html.HtmlProcessor;
import org.atalk.xryptomail.ui.crypto.MessageCryptoAnnotations;
import org.openintents.openpgp.util.OpenPgpUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import timber.log.Timber;

import static org.atalk.xryptomail.mail.internet.MimeUtility.getHeaderParameter;

public class MessageViewInfoExtractor
{
    private static final String TEXT_DIVIDER =
            "------------------------------------------------------------------------";
    private static final int TEXT_DIVIDER_LENGTH = TEXT_DIVIDER.length();
    private static final String FILENAME_PREFIX = "----- ";
    private static final int FILENAME_PREFIX_LENGTH = FILENAME_PREFIX.length();
    private static final String FILENAME_SUFFIX = " ";
    private static final int FILENAME_SUFFIX_LENGTH = FILENAME_SUFFIX.length();

    private final AttachmentInfoExtractor attachmentInfoExtractor;
    private final HtmlProcessor htmlProcessor;
    private final Context resourceProvider;
    private boolean mStealthMode = false;

    public static MessageViewInfoExtractor getInstance()
    {
        Context context = Globals.getContext();
        AttachmentInfoExtractor attachmentInfoExtractor = AttachmentInfoExtractor.getInstance();
        HtmlProcessor htmlProcessor = HtmlProcessor.newInstance();
        return new MessageViewInfoExtractor(context, attachmentInfoExtractor, htmlProcessor);
    }

    MessageViewInfoExtractor(Context context, AttachmentInfoExtractor attachmentInfoExtractor, HtmlProcessor htmlProcessor)
    {
        this.resourceProvider = context;
        this.attachmentInfoExtractor = attachmentInfoExtractor;
        this.htmlProcessor = htmlProcessor;
    }

    @WorkerThread
    public MessageViewInfo extractMessageForView(Message message, @Nullable MessageCryptoAnnotations cryptoAnnotations)
            throws MessagingException
    {
        mStealthMode = PreviewResult.PreviewType.STEALTH.equals((((LocalMessage) message).getPreviewType()));

        ArrayList<Part> extraParts = new ArrayList<>();
        Part cryptoContentPart = MessageCryptoStructureDetector.findPrimaryEncryptedOrSignedPart(message, extraParts);

        if (cryptoContentPart == null) {
            if (cryptoAnnotations != null && !cryptoAnnotations.isEmpty()) {
                Timber.e("Got crypto message cryptoContentAnnotations but no crypto root part!");
            }
            return extractSimpleMessageForView(message, message);
        }

        boolean isOpenPgpEncrypted = (MessageCryptoStructureDetector.isPartMultipartEncrypted(cryptoContentPart)
                && MessageCryptoStructureDetector.isMultipartEncryptedOpenPgpProtocol(cryptoContentPart))
                || MessageCryptoStructureDetector.isPartPgpInlineEncrypted(cryptoContentPart);
        if (!XryptoMail.isOpenPgpProviderConfigured() && isOpenPgpEncrypted) {
            CryptoResultAnnotation noProviderAnnotation = CryptoResultAnnotation.createErrorAnnotation(
                    CryptoError.OPENPGP_ENCRYPTED_NO_PROVIDER, null);
            return MessageViewInfo.createWithErrorState(message, false)
                    .withCryptoData(noProviderAnnotation, null, null);
        }

        CryptoResultAnnotation cryptoContentPartAnnotation
                = cryptoAnnotations != null ? cryptoAnnotations.get(cryptoContentPart) : null;
        if (cryptoContentPartAnnotation != null) {
            return extractCryptoMessageForView(message, extraParts, cryptoContentPart, cryptoContentPartAnnotation);
        }
        return extractSimpleMessageForView(message, message);
    }

    private MessageViewInfo extractCryptoMessageForView(Message message,
            ArrayList<Part> extraParts, Part cryptoContentPart, CryptoResultAnnotation cryptoContentPartAnnotation)
            throws MessagingException
    {
        if (cryptoContentPartAnnotation != null && cryptoContentPartAnnotation.hasReplacementData()) {
            cryptoContentPart = cryptoContentPartAnnotation.getReplacementData();
        }

        List<AttachmentViewInfo> extraAttachmentInfos = new ArrayList<>();
        ViewableExtractedText extraViewable = extractViewableAndAttachments(extraParts, extraAttachmentInfos);

        MessageViewInfo messageViewInfo = extractSimpleMessageForView(message, cryptoContentPart);
        return messageViewInfo.withCryptoData(cryptoContentPartAnnotation, extraViewable.text, extraAttachmentInfos);
    }

    private MessageViewInfo extractSimpleMessageForView(Message message, Part contentPart)
            throws MessagingException
    {
        List<AttachmentViewInfo> attachmentInfos = new ArrayList<>();
        ViewableExtractedText viewable = extractViewableAndAttachments(Collections.singletonList(contentPart), attachmentInfos);
        AttachmentResolver attachmentResolver = AttachmentResolver.createFromPart(contentPart);
        boolean isMessageIncomplete =
                !message.isSet(Flag.X_DOWNLOADED_FULL) || MessageExtractor.hasMissingParts(message);

        return MessageViewInfo.createWithExtractedContent(
                message, contentPart, isMessageIncomplete, viewable.html, attachmentInfos, attachmentResolver);
    }

    private ViewableExtractedText extractViewableAndAttachments(List<Part> parts, List<AttachmentViewInfo> attachmentInfos)
            throws MessagingException
    {
        ArrayList<Viewable> viewableParts = new ArrayList<>();
        ArrayList<Part> attachments = new ArrayList<>();

        for (Part part : parts) {
            MessageExtractor.findViewablesAndAttachments(part, viewableParts, attachments);
        }
        attachmentInfos.addAll(attachmentInfoExtractor.extractAttachmentInfoForView(attachments));
        return extractTextFromViewables(viewableParts);
    }

    /**
     * Extract the viewable textual parts of a message and return the rest as attachments.
     *
     * @return A {@link ViewableExtractedText} instance containing the textual parts of the message as
     * plain text and HTML, and a list of message parts considered attachments.
     * @throws org.atalk.xryptomail.mail.MessagingException in case of an error.
     */
    @VisibleForTesting
    ViewableExtractedText extractTextFromViewables(List<Viewable> viewables)
            throws MessagingException
    {
        try {
            /*
             * // Collect all viewable parts
             * Convert the tree of viewable parts into text and HTML
             */

            // Used to suppress the divider for the first viewable part
            boolean hideDivider = true;
            StringBuilder text = new StringBuilder();
            StringBuilder html = new StringBuilder();

            for (Viewable viewable : viewables) {
                if (viewable instanceof Textual) {
                    // This is either a text/plain or text/html part. Fill the variables 'text' and
                    // 'html', converting between plain text and HTML as necessary.
                    text.append(buildText(viewable, !hideDivider));
                    html.append(buildHtml(viewable, !hideDivider));
                    hideDivider = false;
                }
                else if (viewable instanceof MessageHeader) {
                    MessageHeader header = (MessageHeader) viewable;
                    Part containerPart = header.getContainerPart();
                    Message innerMessage = header.getMessage();

                    addTextDivider(text, containerPart, !hideDivider);
                    addMessageHeaderText(text, innerMessage);

                    addHtmlDivider(html, containerPart, !hideDivider);
                    addMessageHeaderHtml(html, innerMessage);

                    hideDivider = true;
                }
                else if (viewable instanceof Alternative) {
                    // Handle multipart/alternative contents
                    Alternative alternative = (Alternative) viewable;

                    /*
                     * We made sure at least one of text/plain or text/html is present when
                     * creating the Alternative object. If one part is not present we convert the
                     * other one to make sure 'text' and 'html' always contain the same text.
                     */
                    List<Viewable> textAlternative = alternative.getText().isEmpty() ?
                            alternative.getHtml() : alternative.getText();
                    List<Viewable> htmlAlternative = alternative.getHtml().isEmpty() ?
                            alternative.getText() : alternative.getHtml();

                    // Fill the 'text' variable
                    boolean divider = !hideDivider;
                    for (Viewable textViewable : textAlternative) {
                        text.append(buildText(textViewable, divider));
                        divider = true;
                    }

                    // Fill the 'html' variable
                    divider = !hideDivider;
                    for (Viewable htmlViewable : htmlAlternative) {
                        html.append(buildHtml(htmlViewable, divider));
                        divider = true;
                    }
                    hideDivider = false;
                }
            }

            // Stealth message is Base64 encoded
            String htmlBody = html.toString();
            if (mStealthMode)
                htmlBody = Base64.decode(htmlBody);
            String sanitizedHtml = htmlProcessor.processForDisplay(htmlBody);
            return new ViewableExtractedText(text.toString(), sanitizedHtml);
        } catch (Exception e) {
            throw new MessagingException("Couldn't extract viewable parts", e);
        }
    }

    /**
     * Use the contents of a {@link org.atalk.xryptomail.mail.internet.Viewable} to create the HTML to be displayed.
     *
     * <p>
     * This will use {@link HtmlConverter#textToHtml(String)} to convert plain text parts
     * to HTML if necessary.
     * </p>
     *
     * @param viewable The viewable part to build the HTML from.
     * @param prependDivider {@code true}, if the HTML divider should be inserted as first element.
     * {@code false}, otherwise.
     * @return The contents of the supplied viewable instance as HTML.
     */
    private StringBuilder buildHtml(Viewable viewable, boolean prependDivider)
    {
        StringBuilder html = new StringBuilder();
        if (viewable instanceof Textual) {
            Part part = ((Textual) viewable).getPart();
            addHtmlDivider(html, part, prependDivider);

            String t = getTextFromPart(part);
            if (t == null) {
                t = "";
            }
            else if (viewable instanceof Flowed) {
                boolean delSp = ((Flowed) viewable).isDelSp();
                t = FlowedMessageUtils.deflow(t, delSp);
                t = HtmlConverter.textToHtml(t);
            }
            else if (viewable instanceof Text) {
                t = HtmlConverter.textToHtml(t);
            }
            else if (!(viewable instanceof Html)) {
                throw new IllegalStateException("unhandled case!");
            }
            html.append(t);
        }
        else if (viewable instanceof Alternative) {
            // That's odd - an Alternative as child of an Alternative; go ahead and try to use the
            // text/html child; fall-back to the text/plain part.
            Alternative alternative = (Alternative) viewable;

            List<Viewable> htmlAlternative = alternative.getHtml().isEmpty() ?
                    alternative.getText() : alternative.getHtml();

            boolean divider = prependDivider;
            for (Viewable htmlViewable : htmlAlternative) {
                html.append(buildHtml(htmlViewable, divider));
                divider = true;
            }
        }
        return html;
    }

    private StringBuilder buildText(Viewable viewable, boolean prependDivider)
    {
        StringBuilder text = new StringBuilder();
        if (viewable instanceof Textual) {
            Part part = ((Textual) viewable).getPart();
            addTextDivider(text, part, prependDivider);

            String t = getTextFromPart(part);
            if (t == null) {
                t = "";
            }
            else if (viewable instanceof Html) {
                t = HtmlConverter.htmlToText(t);
            }
            else if (viewable instanceof Flowed) {
                boolean delSp = ((Flowed) viewable).isDelSp();
                t = FlowedMessageUtils.deflow(t, delSp);
            }
            else if (!(viewable instanceof Text)) {
                throw new IllegalStateException("unhandled case!");
            }
            text.append(t);
        }
        else if (viewable instanceof Alternative) {
            // That's odd - an Alternative as child of an Alternative; go ahead and try to use the
            // text/plain child; fall-back to the text/html part.
            Alternative alternative = (Alternative) viewable;

            List<Viewable> textAlternative = alternative.getText().isEmpty() ?
                    alternative.getHtml() : alternative.getText();

            boolean divider = prependDivider;
            for (Viewable textViewable : textAlternative) {
                text.append(buildText(textViewable, divider));
                divider = true;
            }
        }
        return text;
    }

    /**
     * Add an HTML divider between two HTML message parts.
     *
     * @param html The {@link StringBuilder} to append the divider to.
     * @param part The message part that will follow after the divider. This is used to extract the
     * part's name.
     * @param prependDivider {@code true}, if the divider should be appended. {@code false}, otherwise.
     */
    private void addHtmlDivider(StringBuilder html, Part part, boolean prependDivider)
    {
        if (prependDivider) {
            String filename = getPartName(part);

            html.append("<p style=\"margin-top: 2.5em; margin-bottom: 1em; border-bottom: 1px solid #000\">");
            html.append(filename);
            html.append("</p>");
        }
    }

    private String getTextFromPart(Part part)
    {
        String textFromPart = MessageExtractor.getTextFromPart(part);

        String extractedClearsignedMessage = OpenPgpUtils.extractClearsignedMessage(textFromPart);
        if (extractedClearsignedMessage != null) {
            textFromPart = extractedClearsignedMessage;
        }
        return textFromPart;
    }

    /**
     * Get the name of the message part.
     *
     * @param part The part to get the name for.
     * @return The (file)name of the part if available. An empty string, otherwise.
     */
    private static String getPartName(Part part)
    {
        String disposition = part.getDisposition();
        if (disposition != null) {
            String name = getHeaderParameter(disposition, "filename");
            return (name == null) ? "" : name;
        }
        return "";
    }

    /**
     * Add a plain text divider between two plain text message parts.
     *
     * @param text The {@link StringBuilder} to append the divider to.
     * @param part The message part that will follow after the divider. This is used to extract the
     * part's name.
     * @param prependDivider {@code true}, if the divider should be appended. {@code false}, otherwise.
     */
    private void addTextDivider(StringBuilder text, Part part, boolean prependDivider)
    {
        if (prependDivider) {
            String filename = getPartName(part);

            text.append("\r\n\r\n");
            int len = filename.length();
            if (len > 0) {
                if (len > TEXT_DIVIDER_LENGTH - FILENAME_PREFIX_LENGTH - FILENAME_SUFFIX_LENGTH) {
                    filename = filename.substring(0, TEXT_DIVIDER_LENGTH - FILENAME_PREFIX_LENGTH -
                            FILENAME_SUFFIX_LENGTH - 3) + "...";
                }
                text.append(FILENAME_PREFIX);
                text.append(filename);
                text.append(FILENAME_SUFFIX);
                text.append(TEXT_DIVIDER.substring(0, TEXT_DIVIDER_LENGTH -
                        FILENAME_PREFIX_LENGTH - filename.length() - FILENAME_SUFFIX_LENGTH));
            }
            else {
                text.append(TEXT_DIVIDER);
            }
            text.append("\r\n\r\n");
        }
    }

    /**
     * Extract important header values from a message to display inline (plain text version).
     *
     * @param text The {@link StringBuilder} that will receive the (plain text) output.
     * @param message The message to extract the header values from.
     * @throws org.atalk.xryptomail.mail.MessagingException in case of an error.
     */
    private void addMessageHeaderText(StringBuilder text, Message message)
            throws MessagingException
    {
        // From: <sender>
        Address[] from = message.getFrom();
        if (from != null && from.length > 0) {
            text.append(resourceProvider.getString(R.string.message_compose_quote_header_from));
            text.append(' ');
            text.append(Address.toString(from));
            text.append("\r\n");
        }

        // To: <recipients>
        Address[] to = message.getRecipients(Message.RecipientType.TO);
        if (to != null && to.length > 0) {
            text.append(resourceProvider.getString(R.string.message_compose_quote_header_to));
            text.append(' ');
            text.append(Address.toString(to));
            text.append("\r\n");
        }

        // Cc: <recipients>
        Address[] cc = message.getRecipients(Message.RecipientType.CC);
        if (cc != null && cc.length > 0) {
            text.append(resourceProvider.getString(R.string.message_compose_quote_header_cc));
            text.append(' ');
            text.append(Address.toString(cc));
            text.append("\r\n");
        }

        // Date: <date>
        Date date = message.getSentDate();
        if (date != null) {
            text.append(resourceProvider.getString(R.string.message_compose_quote_header_send_date));
            text.append(' ');
            text.append(date.toString());
            text.append("\r\n");
        }

        // Subject: <subject>
        String subject = message.getSubject();
        text.append(resourceProvider.getString(R.string.message_compose_quote_header_subject));
        text.append(' ');
        if (subject == null) {
            text.append(resourceProvider.getString(R.string.general_no_subject));
        }
        else {
            text.append(subject);
        }
        text.append("\r\n\r\n");
    }

    /**
     * Extract important header values from a message to display inline (HTML version).
     *
     * @param html The {@link StringBuilder} that will receive the (HTML) output.
     * @param message The message to extract the header values from.
     * @throws org.atalk.xryptomail.mail.MessagingException in case of an error.
     */
    private void addMessageHeaderHtml(StringBuilder html, Message message)
            throws MessagingException
    {

        html.append("<table style=\"border: 0\">");

        // From: <sender>
        Address[] from = message.getFrom();
        if (from != null && from.length > 0) {
            addTableRow(html, resourceProvider.getString(R.string.message_compose_quote_header_from),
                    Address.toString(from));
        }

        // To: <recipients>
        Address[] to = message.getRecipients(Message.RecipientType.TO);
        if (to != null && to.length > 0) {
            addTableRow(html, resourceProvider.getString(R.string.message_compose_quote_header_to),
                    Address.toString(to));
        }

        // Cc: <recipients>
        Address[] cc = message.getRecipients(Message.RecipientType.CC);
        if (cc != null && cc.length > 0) {
            addTableRow(html, resourceProvider.getString(R.string.message_compose_quote_header_cc),
                    Address.toString(cc));
        }

        // Date: <date>
        Date date = message.getSentDate();
        if (date != null) {
            addTableRow(html, resourceProvider.getString(R.string.message_compose_quote_header_send_date),
                    date.toString());
        }

        // Subject: <subject>
        String subject = message.getSubject();
        addTableRow(html, resourceProvider.getString(R.string.message_compose_quote_header_subject),
                (subject == null) ? resourceProvider.getString(R.string.general_no_subject) : subject);

        html.append("</table>");
    }

    /**
     * Output an HTML table two column row with some hardcoded style.
     *
     * @param html The {@link StringBuilder} that will receive the output.
     * @param header The string to be put in the {@code TH} element.
     * @param value The string to be put in the {@code TD} element.
     */
    private static void addTableRow(StringBuilder html, String header, String value)
    {
        html.append("<tr><th style=\"text-align: left; vertical-align: top;\">");
        html.append(header);
        html.append("</th>");
        html.append("<td>");
        html.append(value);
        html.append("</td></tr>");
    }

    @VisibleForTesting
    static class ViewableExtractedText
    {
        public final String text;
        public final String html;

        ViewableExtractedText(String text, String html)
        {
            this.text = text;
            this.html = html;
        }
    }
}
