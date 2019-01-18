package org.atalk.xryptomail.helper;

import android.support.annotation.VisibleForTesting;

import org.atalk.xryptomail.Account;
import org.atalk.xryptomail.mail.*;
import org.atalk.xryptomail.mail.Message.RecipientType;
import org.atalk.xryptomail.mail.internet.ListHeaders;

import java.util.*;

public class ReplyToParser
{

	public ReplyToAddresses getRecipientsToReplyTo(Message message, Account account)
	{
		Address[] candidateAddress;

		Address[] replyToAddresses = message.getReplyTo();
		Address[] listPostAddresses = ListHeaders.getListPostAddresses(message);
		Address[] fromAddresses = message.getFrom();

		if (replyToAddresses.length > 0) {
			candidateAddress = replyToAddresses;
		}
		else if (listPostAddresses.length > 0) {
			candidateAddress = listPostAddresses;
		}
		else {
			candidateAddress = fromAddresses;
		}

		boolean replyToAddressIsUserIdentity = account.isAnIdentity(candidateAddress);
		if (replyToAddressIsUserIdentity) {
			candidateAddress = message.getRecipients(RecipientType.TO);
		}
		return new ReplyToAddresses(candidateAddress);
	}

	public ReplyToAddresses getRecipientsToReplyAllTo(Message message, Account account)
	{
		List<Address> replyToAddresses
				= Arrays.asList(getRecipientsToReplyTo(message, account).to);

		HashSet<Address> alreadyAddedAddresses = new HashSet<>(replyToAddresses);
		ArrayList<Address> toAddresses = new ArrayList<>(replyToAddresses);
		ArrayList<Address> ccAddresses = new ArrayList<>();

		for (Address address : message.getFrom()) {
			if (!alreadyAddedAddresses.contains(address) && !account.isAnIdentity(address)) {
				toAddresses.add(address);
				alreadyAddedAddresses.add(address);
			}
		}

		for (Address address : message.getRecipients(RecipientType.TO)) {
			if (!alreadyAddedAddresses.contains(address) && !account.isAnIdentity(address)) {
				toAddresses.add(address);
				alreadyAddedAddresses.add(address);
			}
		}

		for (Address address : message.getRecipients(RecipientType.CC)) {
			if (!alreadyAddedAddresses.contains(address) && !account.isAnIdentity(address)) {
				ccAddresses.add(address);
				alreadyAddedAddresses.add(address);
			}
		}
		return new ReplyToAddresses(toAddresses, ccAddresses);
	}

	public static class ReplyToAddresses
	{
		public final Address[] to;
		public final Address[] cc;

		@VisibleForTesting
		public ReplyToAddresses(List<Address> toAddresses, List<Address> ccAddresses)
		{
			to = toAddresses.toArray(new Address[toAddresses.size()]);
			cc = ccAddresses.toArray(new Address[ccAddresses.size()]);
		}

		@VisibleForTesting
		public ReplyToAddresses(Address[] toAddresses)
		{
			to = toAddresses;
			cc = new Address[0];
		}
	}
}
