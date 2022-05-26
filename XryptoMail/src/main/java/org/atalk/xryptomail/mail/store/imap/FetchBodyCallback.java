package org.atalk.xryptomail.mail.store.imap;

import org.atalk.xryptomail.mail.Message;
import org.atalk.xryptomail.mail.MessagingException;
import org.atalk.xryptomail.mail.filter.FixedLengthInputStream;

import java.io.IOException;
import java.util.Map;

class FetchBodyCallback implements ImapResponseCallback {
    private final Map<String, Message> mMessageMap;

    FetchBodyCallback(Map<String, Message> messageMap) {
        mMessageMap = messageMap;
    }

    @Override
    public Object foundLiteral(ImapResponse response,
                               FixedLengthInputStream literal) throws MessagingException, IOException {
        if (!response.isTagged() &&
                ImapResponseParser.equalsIgnoreCase(response.get(1), "FETCH")) {
            ImapList fetchList = (ImapList)response.getKeyedValue("FETCH");
            String uid = fetchList.getKeyedString("UID");

            ImapMessage message = (ImapMessage) mMessageMap.get(uid);
            message.parse(literal);

            // Return placeholder object
            return 1;
        }
        return null;
    }
}