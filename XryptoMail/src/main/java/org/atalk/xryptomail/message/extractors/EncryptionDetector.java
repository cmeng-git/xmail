package org.atalk.xryptomail.message.extractors;

import androidx.annotation.NonNull;

import org.atalk.xryptomail.activity.compose.RecipientPresenter;
import org.atalk.xryptomail.crypto.MessageCryptoStructureDetector;
import org.atalk.xryptomail.mail.Body;
import org.atalk.xryptomail.mail.BodyPart;
import org.atalk.xryptomail.mail.Message;
import org.atalk.xryptomail.mail.Multipart;
import org.atalk.xryptomail.mail.Part;
import org.atalk.xryptomail.mail.internet.MimeUtility;

public class EncryptionDetector {
    private static final String MULTIPART_ENCRYPTED = "multipart/encrypted";
    private static final String PKCS7_MIME = "application/pkcs7-mime";
    private static final String STEALTH_TYPE = "STEALTH";
    private static final String MODE_PARAMETER = "mode";

    private final TextPartFinder textPartFinder;

    EncryptionDetector(TextPartFinder textPartFinder) {
        this.textPartFinder = textPartFinder;
    }

    public boolean isEncrypted(@NonNull Message message) {
        return isPgpMimeOrSMimeEncrypted(message) || containsInlinePgpEncryptedText(message);
    }

    private boolean isPgpMimeOrSMimeEncrypted(Message message) {
        return containsPartWithMimeType(message, MULTIPART_ENCRYPTED, PKCS7_MIME);
    }

    private boolean containsInlinePgpEncryptedText(Message message) {
        Part textPart = textPartFinder.findFirstTextPart(message);
        return MessageCryptoStructureDetector.isPartPgpInlineEncrypted(textPart);
    }

    private boolean containsPartWithMimeType(Part part, String... wantedMimeTypes) {
        String mimeType = part.getMimeType();
        if (isMimeTypeAnyOf(mimeType, wantedMimeTypes)) {
            return true;
        }

        Body body = part.getBody();
        if (body instanceof Multipart) {
            Multipart multipart = (Multipart) body;
            for (BodyPart bodyPart : multipart.getBodyParts()) {
                if (containsPartWithMimeType(bodyPart, wantedMimeTypes)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isMimeTypeAnyOf(String mimeType, String... wantedMimeTypes) {
        for (String wantedMimeType : wantedMimeTypes) {
            if (MimeUtility.isSameMimeType(mimeType, wantedMimeType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check the header mode paramter for stealth content
     *
     * @param message the message to check for
     * @return true if header indicate it is a Stealth message
     */
    public boolean isStealth(@NonNull Message message) {
        String mContentType = message.getContentType();
        String sMode = MimeUtility.getHeaderParameter(mContentType, MODE_PARAMETER);
        // sMode can be null
        return STEALTH_TYPE.equalsIgnoreCase(sMode);
    }

    /**
     * Return the message type based on header content information
     * @param message the message
     * @return XrytpMode type
     */
    public static RecipientPresenter.XryptoMode getXryptoMode(@NonNull Message message) {

        if (MimeUtility.isSameMimeType(message.getMimeType(), MULTIPART_ENCRYPTED)) {
            String mContentType = message.getContentType();
            String sMode = MimeUtility.getHeaderParameter(mContentType, MODE_PARAMETER);

            // sMode can be null
            if (STEALTH_TYPE.equalsIgnoreCase(sMode)) {
                return RecipientPresenter.XryptoMode.STEALTH;
            }
            else {
                return RecipientPresenter.XryptoMode.OPEN_PGP;
            }
        }
        else {
            return RecipientPresenter.XryptoMode.NORMAL;
        }
    }
}
