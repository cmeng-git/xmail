package org.atalk.xryptomail.helper;

import android.content.Context;
import android.util.TypedValue;

import org.atalk.xryptomail.XryptoMail;
import org.atalk.xryptomail.R;
import org.atalk.xryptomail.activity.misc.ContactPictureLoader;

public class ContactPicture {

    public static ContactPictureLoader getContactPictureLoader(Context context) {
        final int defaultBgColor;
        if (!XryptoMail.isColorizeMissingContactPictures()) {
            TypedValue outValue = new TypedValue();
            context.getTheme().resolveAttribute(R.attr.contactPictureFallbackDefaultBackgroundColor,
                    outValue, true);
            defaultBgColor = outValue.data;
        } else {
            defaultBgColor = 0;
        }
        return new ContactPictureLoader(context, defaultBgColor);
    }
}
