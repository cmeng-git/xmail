package org.atalk.xryptomail.message.quote;

import android.content.res.Resources;

import org.atalk.xryptomail.XryptoMail;
import org.atalk.xryptomail.mail.Message;

import java.text.DateFormat;
import java.util.*;

class QuoteHelper {
    // amount of extra buffer to allocate to accommodate quoting headers or prefixes
    static final int QUOTE_BUFFER_LENGTH = 512;


    /**
     * Extract the date from a message and convert it into a locale-specific
     * date string suitable for use in a header for a quoted message.
     *
     * @return A string with the formatted date/time
     */
    static String getSentDateText(Resources resources, Message message) {
        try {
            final int dateStyle = DateFormat.LONG;
            final int timeStyle = DateFormat.LONG;
            Date date = message.getSentDate();

            DateFormat dateFormat;
            if (XryptoMail.hideTimeZone()) {
                dateFormat = DateFormat.getDateTimeInstance(dateStyle, timeStyle, Locale.ROOT);
                dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            } else {
                Locale locale = resources.getConfiguration().locale;
                dateFormat = DateFormat.getDateTimeInstance(dateStyle, timeStyle, locale);
            }
            return dateFormat.format(date);
        } catch (Exception e) {
            return "";
        }
    }
}
