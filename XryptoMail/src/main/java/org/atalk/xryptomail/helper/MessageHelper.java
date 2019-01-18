package org.atalk.xryptomail.helper;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;

import org.atalk.xryptomail.Account;
import org.atalk.xryptomail.XryptoMail;
import org.atalk.xryptomail.R;
import org.atalk.xryptomail.activity.FolderInfoHolder;
import org.atalk.xryptomail.activity.MessageInfoHolder;
import org.atalk.xryptomail.mail.Address;
import org.atalk.xryptomail.mail.Flag;
import org.atalk.xryptomail.mail.Message.RecipientType;
import org.atalk.xryptomail.mailstore.LocalMessage;

public class MessageHelper {
    /**
     * If the number of addresses exceeds this value the addresses aren't
     * resolved to the names of Android contacts.
     *
     * <p>
     * TODO: This number was chosen arbitrarily and should be determined by
     * performance tests.
     * </p>
     *
     * @see #toFriendly(Address[], org.atalk.xryptomail.helper.Contacts)
     */
    private static final int TOO_MANY_ADDRESSES = 50;

    private static MessageHelper sInstance;

	public synchronized static MessageHelper getInstance(final Context context) {
		if (sInstance == null) {
			sInstance = new MessageHelper(context);
		}
		return sInstance;
	}

	private Context mContext;

	private MessageHelper(final Context context) {
		mContext = context;
	}

    public void populate(final MessageInfoHolder target,
                         final LocalMessage message,
                         final FolderInfoHolder folder,
                         Account account) {
        final Contacts contactHelper = XryptoMail.showContactName() ? Contacts.getInstance(mContext) : null;
        target.message = message;
        target.compareArrival = message.getInternalDate();
        target.compareDate = message.getSentDate();
        if (target.compareDate == null) {
            target.compareDate = message.getInternalDate();
        }

        target.folder = folder;

        target.read = message.isSet(Flag.SEEN);
        target.answered = message.isSet(Flag.ANSWERED);
        target.forwarded = message.isSet(Flag.FORWARDED);
        target.flagged = message.isSet(Flag.FLAGGED);

        Address[] addrs = message.getFrom();

        if (addrs.length > 0 &&  account.isAnIdentity(addrs[0])) {
            CharSequence to = toFriendly(message.getRecipients(RecipientType.TO), contactHelper);
            target.compareCounterparty = to.toString();
            target.sender = new SpannableStringBuilder(mContext.getString(R.string.message_to_label)).append(to);
        } else {
            target.sender = toFriendly(addrs, contactHelper);
            target.compareCounterparty = target.sender.toString();
        }

        if (addrs.length > 0) {
            target.senderAddress = addrs[0].getAddress();
        } else {
            // a reasonable fallback "whomever we were corresponding with
            target.senderAddress = target.compareCounterparty;
        }

        target.uid = message.getUid();
        target.account = message.getFolder().getAccountUuid();
        target.uri = message.getUri();
    }

    public CharSequence getDisplayName(Account account, Address[] fromAddrs, Address[] toAddrs) {
        final Contacts contactHelper = XryptoMail.showContactName() ? Contacts.getInstance(mContext) : null;

        CharSequence displayName;
        if (fromAddrs.length > 0 && account.isAnIdentity(fromAddrs[0])) {
            CharSequence to = toFriendly(toAddrs, contactHelper);
            displayName = new SpannableStringBuilder(
                    mContext.getString(R.string.message_to_label)).append(to);
        } else {
            displayName = toFriendly(fromAddrs, contactHelper);
        }

        return displayName;
    }

    public boolean toMe(Account account, Address[] toAddrs) {
        for (Address address : toAddrs) {
            if (account.isAnIdentity(address)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the name of the contact this email address belongs to if
     * the {@link Contacts contacts} parameter is not {@code null} and a
     * contact is found. Otherwise the personal portion of the {@link Address}
     * is returned. If that isn't available either, the email address is
     * returned.
     *
     * @param address An {@link org.atalk.xryptomail.mail.Address}
     * @param contacts A {@link Contacts} instance or {@code null}.
     * @return A "friendly" name for this {@link Address}.
     */
    public static CharSequence toFriendly(Address address, Contacts contacts) {
        return toFriendly(address,contacts,
                XryptoMail.showCorrespondentNames(),
                XryptoMail.changeContactNameColor(),
                XryptoMail.getContactNameColor());
    }

    public static CharSequence toFriendly(Address[] addresses, Contacts contacts) {
        if (addresses == null) {
            return null;
        }

        if (addresses.length >= TOO_MANY_ADDRESSES) {
            // Don't look up contacts if the number of addresses is very high.
            contacts = null;
        }

        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (int i = 0; i < addresses.length; i++) {
            sb.append(toFriendly(addresses[i], contacts));
            if (i < addresses.length - 1) {
                sb.append(',');
            }
        }
        return sb;
    }

    /* package, for testing */
    static CharSequence toFriendly(Address address, Contacts contacts,
             boolean showCorrespondentNames,
             boolean changeContactNameColor,
             int contactNameColor) {
        if (!showCorrespondentNames) {
            return address.getAddress();
        } else if (contacts != null) {
            final String name = contacts.getNameForAddress(address.getAddress());
            // TODO: The results should probably be cached for performance reasons.
            if (name != null) {
                if (changeContactNameColor) {
                    final SpannableString coloredName = new SpannableString(name);
                    coloredName.setSpan(new ForegroundColorSpan(contactNameColor),
                            0,
                            coloredName.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                    return coloredName;
                } else {
                    return name;
                }
            }
        }

        if (!TextUtils.isEmpty(address.getPersonal()) && !isSpoofAddress(address.getPersonal())) {
            return address.getPersonal();
        } else {
            return address.getAddress();
        }
    }

    private static boolean isSpoofAddress(String displayName) {
        return displayName.contains("@");
    }
}
