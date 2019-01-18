package org.atalk.xryptomail.message.extractors;

import android.support.annotation.NonNull;

import org.atalk.xryptomail.mail.Message;
import org.atalk.xryptomail.mail.Part;

public class MessagePreviewCreator {
    private final TextPartFinder textPartFinder;
    private final PreviewTextExtractor previewTextExtractor;
    private final EncryptionDetector encryptionDetector;


    MessagePreviewCreator(TextPartFinder textPartFinder, PreviewTextExtractor previewTextExtractor,
            EncryptionDetector encryptionDetector) {
        this.textPartFinder = textPartFinder;
        this.previewTextExtractor = previewTextExtractor;
        this.encryptionDetector = encryptionDetector;
    }

    public static MessagePreviewCreator newInstance() {
        TextPartFinder textPartFinder = new TextPartFinder();
        PreviewTextExtractor previewTextExtractor = new PreviewTextExtractor();
        EncryptionDetector encryptionDetector = new EncryptionDetector(textPartFinder);
        return new MessagePreviewCreator(textPartFinder, previewTextExtractor, encryptionDetector);
    }

    public PreviewResult createPreview(@NonNull Message message) {
        if (encryptionDetector.isEncrypted(message)) {
            if (encryptionDetector.isStealth(message)) {
                return PreviewResult.stealth();
            }
            return PreviewResult.encrypted();
        }
        return extractText(message);
    }

    private PreviewResult extractText(Message message) {
        Part textPart = textPartFinder.findFirstTextPart(message);
        if (textPart == null || hasEmptyBody(textPart)) {
            return PreviewResult.none();
        }

        try {
            String previewText = previewTextExtractor.extractPreview(textPart);
            return PreviewResult.text(previewText);
        } catch (PreviewExtractionException e) {
            return PreviewResult.error();
        }
    }

    private boolean hasEmptyBody(Part textPart) {
        return textPart.getBody() == null;
    }
}
