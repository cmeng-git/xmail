package org.atalk.xryptomail.autocrypt;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;

import org.atalk.xryptomail.mail.Address;
import org.atalk.xryptomail.mail.Message;
import org.atalk.xryptomail.mail.Message.RecipientType;
import org.atalk.xryptomail.mail.internet.MimeBodyPart;
import org.openintents.openpgp.AutocryptPeerUpdate;
import org.openintents.openpgp.util.OpenPgpApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class AutocryptOperations
{
    private final AutocryptHeaderParser autocryptHeaderParser;
    private final AutocryptGossipHeaderParser autocryptGossipHeaderParser;

    public static AutocryptOperations getInstance()
    {
        AutocryptHeaderParser autocryptHeaderParser = AutocryptHeaderParser.getInstance();
        AutocryptGossipHeaderParser autocryptGossipHeaderParser = AutocryptGossipHeaderParser.getInstance();
        return new AutocryptOperations(autocryptHeaderParser, autocryptGossipHeaderParser);
    }

    private AutocryptOperations(AutocryptHeaderParser autocryptHeaderParser,
            AutocryptGossipHeaderParser autocryptGossipHeaderParser)
    {
        this.autocryptHeaderParser = autocryptHeaderParser;
        this.autocryptGossipHeaderParser = autocryptGossipHeaderParser;
    }

    public boolean addAutocryptPeerUpdateToIntentIfPresent(Message currentMessage, Intent intent)
    {
        AutocryptHeader autocryptHeader = autocryptHeaderParser.getValidAutocryptHeader(currentMessage);
        if (autocryptHeader == null) {
            return false;
        }

        String messageFromAddress = currentMessage.getFrom()[0].getAddress();
        if (!autocryptHeader.addr.equalsIgnoreCase(messageFromAddress)) {
            return false;
        }

        Date messageDate = currentMessage.getSentDate();
        Date internalDate = currentMessage.getInternalDate();
        Date effectiveDate = messageDate.before(internalDate) ? messageDate : internalDate;

        AutocryptPeerUpdate data = AutocryptPeerUpdate.create(
                autocryptHeader.keyData, effectiveDate, autocryptHeader.isPreferEncryptMutual);
        intent.putExtra(OpenPgpApi.EXTRA_AUTOCRYPT_PEER_ID, messageFromAddress);
        intent.putExtra(OpenPgpApi.EXTRA_AUTOCRYPT_PEER_UPDATE, data);
        return true;
    }

    public boolean addAutocryptGossipUpdateToIntentIfPresent(Message message, MimeBodyPart decryptedPart, Intent intent)
    {
        Bundle updates = createGossipUpdateBundle(message, decryptedPart);

        if (updates == null) {
            return false;
        }

        intent.putExtra(OpenPgpApi.EXTRA_AUTOCRYPT_PEER_GOSSIP_UPDATES, updates);
        return true;
    }

    @Nullable
    private Bundle createGossipUpdateBundle(Message message, MimeBodyPart decryptedPart)
    {
        List<String> gossipAcceptedAddresses = getGossipAcceptedAddresses(message);
        if (gossipAcceptedAddresses.isEmpty()) {
            return null;
        }

        List<AutocryptGossipHeader> autocryptGossipHeaders =
                autocryptGossipHeaderParser.getAllAutocryptGossipHeaders(decryptedPart);
        if (autocryptGossipHeaders.isEmpty()) {
            return null;
        }

        Date messageDate = message.getSentDate();
        Date internalDate = message.getInternalDate();
        Date effectiveDate = messageDate.before(internalDate) ? messageDate : internalDate;
        return createGossipUpdateBundle(gossipAcceptedAddresses, autocryptGossipHeaders, effectiveDate);
    }

    @Nullable
    private Bundle createGossipUpdateBundle(List<String> gossipAcceptedAddresses,
            List<AutocryptGossipHeader> autocryptGossipHeaders, Date effectiveDate)
    {
        Bundle updates = new Bundle();
        for (AutocryptGossipHeader autocryptGossipHeader : autocryptGossipHeaders) {
            boolean isAcceptedAddress = gossipAcceptedAddresses.contains(autocryptGossipHeader.addr.toLowerCase());
            if (!isAcceptedAddress) {
                continue;
            }

            AutocryptPeerUpdate update = AutocryptPeerUpdate.create(autocryptGossipHeader.keyData, effectiveDate, false);
            updates.putParcelable(autocryptGossipHeader.addr, update);
        }
        if (updates.isEmpty()) {
            return null;
        }
        return updates;
    }

    private List<String> getGossipAcceptedAddresses(Message message)
    {
        ArrayList<String> result = new ArrayList<>();

        addRecipientsToList(result, message, RecipientType.TO);
        addRecipientsToList(result, message, RecipientType.CC);
        removeRecipientsFromList(result, message, RecipientType.DELIVERED_TO);

        return Collections.unmodifiableList(result);
    }

    private void addRecipientsToList(ArrayList<String> result, Message message, RecipientType recipientType)
    {
        for (Address address : message.getRecipients(recipientType)) {
            String addr = address.getAddress();
            if (addr != null) {
                result.add(addr.toLowerCase());
            }
        }
    }

    private void removeRecipientsFromList(ArrayList<String> result, Message message, RecipientType recipientType)
    {
        for (Address address : message.getRecipients(recipientType)) {
            String addr = address.getAddress();
            if (addr != null) {
                result.remove(addr);
            }
        }
    }

    public boolean hasAutocryptHeader(Message currentMessage)
    {
        return currentMessage.getHeader(AutocryptHeader.AUTOCRYPT_HEADER).length > 0;
    }

    public boolean hasAutocryptGossipHeader(MimeBodyPart part)
    {
        return part.getHeader(AutocryptGossipHeader.AUTOCRYPT_GOSSIP_HEADER).length > 0;
    }

    public void addAutocryptHeaderToMessage(Message message, byte[] keyData,
            String autocryptAddress, boolean preferEncryptMutual)
    {
        AutocryptHeader autocryptHeader = new AutocryptHeader(
                Collections.emptyMap(), autocryptAddress, keyData, preferEncryptMutual);
        String rawAutocryptHeader = autocryptHeader.toRawHeaderString();
        message.addRawHeader(AutocryptHeader.AUTOCRYPT_HEADER, rawAutocryptHeader);
    }

    public void addAutocryptGossipHeaderToPart(MimeBodyPart part, byte[] keyData, String autocryptAddress)
    {
        AutocryptGossipHeader autocryptGossipHeader = new AutocryptGossipHeader(autocryptAddress, keyData);
        String rawAutocryptHeader = autocryptGossipHeader.toRawHeaderString();

        part.addRawHeader(AutocryptGossipHeader.AUTOCRYPT_GOSSIP_HEADER, rawAutocryptHeader);
    }
}
