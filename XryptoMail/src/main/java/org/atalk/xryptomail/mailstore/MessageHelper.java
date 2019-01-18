package org.atalk.xryptomail.mailstore;

import org.atalk.xryptomail.mail.Body;
import org.atalk.xryptomail.mail.BodyPart;
import org.atalk.xryptomail.mail.MessagingException;
import org.atalk.xryptomail.mail.Multipart;
import org.atalk.xryptomail.mail.Part;
import org.atalk.xryptomail.mail.internet.MimeBodyPart;

import java.util.Stack;

public class MessageHelper {

    public static boolean isCompletePartAvailable(Part part) {
        Stack<Part> partsToCheck = new Stack<Part>();
        partsToCheck.push(part);

        while (!partsToCheck.isEmpty()) {
            Part currentPart = partsToCheck.pop();
            Body body = currentPart.getBody();

            boolean isBodyMissing = body == null;
            if (isBodyMissing) {
                return false;
            }

            if (body instanceof Multipart) {
                Multipart multipart = (Multipart) body;
                for (BodyPart bodyPart : multipart.getBodyParts()) {
                    partsToCheck.push(bodyPart);
                }
            }
        }
        return true;
    }

    public static MimeBodyPart createEmptyPart() {
        try {
            return new MimeBodyPart(null);
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }
}
