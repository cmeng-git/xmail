package org.atalk.xryptomail.ui.crypto;

import org.atalk.xryptomail.mail.Part;
import org.atalk.xryptomail.mailstore.CryptoResultAnnotation;

import java.util.HashMap;

public class MessageCryptoAnnotations {
    private HashMap<Part, CryptoResultAnnotation> annotations = new HashMap<>();

    public void put(Part part, CryptoResultAnnotation annotation) {
        annotations.put(part, annotation);
    }

    public CryptoResultAnnotation get(Part part) {
        return annotations.get(part);
    }

    public boolean has(Part part) {
        return annotations.containsKey(part);
    }

    public boolean isEmpty() {
        return annotations.isEmpty();
    }

    public Part findKeyForAnnotationWithReplacementPart(Part part) {
        for (HashMap.Entry<Part, CryptoResultAnnotation> entry : annotations.entrySet()) {
            if (part == entry.getValue().getReplacementData()) {
                return entry.getKey();
            }
        }
        return null;
    }
}
