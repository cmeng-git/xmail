package org.atalk.xryptomail.crypto;

import android.text.TextUtils;

import org.atalk.xryptomail.Identity;

public class OpenPgpApiHelper {

    /**
     * Create an "account name" from the supplied identity for use with the OpenPgp API's
     * <code>EXTRA_ACCOUNT_NAME</code>.
     *
     * @return A string with the following format:
     *         <code>display name &lt;user@example.com&gt;</code>
     *
     * @see org.openintents.openpgp.util.OpenPgpApi#EXTRA_USER_ID
     */
    public static String buildUserId(Identity identity) {
        StringBuilder sb = new StringBuilder();

        String name = identity.getName();
        if (!TextUtils.isEmpty(name)) {
            sb.append(name).append(" ");
        }
        sb.append("<").append(identity.getEmail()).append(">");

        return sb.toString();
    }
}
