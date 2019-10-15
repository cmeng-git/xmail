package org.atalk.xryptomail.message.extractors;

import org.atalk.xryptomail.mail.Message;
import org.atalk.xryptomail.mail.MessagingException;
import org.atalk.xryptomail.mail.Part;
import org.atalk.xryptomail.mail.internet.MessageExtractor;

import java.util.ArrayList;
import java.util.List;

public class AttachmentCounter {
	private final EncryptionDetector encryptionDetector;

	AttachmentCounter(EncryptionDetector encryptionDetector) {
		this.encryptionDetector = encryptionDetector;
	}

	public static AttachmentCounter newInstance() {
		TextPartFinder textPartFinder = new TextPartFinder();
		EncryptionDetector encryptionDetector = new EncryptionDetector(textPartFinder);
		return new AttachmentCounter(encryptionDetector);
	}

	public int getAttachmentCount(Message message)
			throws MessagingException {
		if (encryptionDetector.isEncrypted(message)) {
			return 0;
		}
		List<Part> attachmentParts = new ArrayList<>();
		MessageExtractor.findViewablesAndAttachments(message, null, attachmentParts);

		return attachmentParts.size();
	}
}
