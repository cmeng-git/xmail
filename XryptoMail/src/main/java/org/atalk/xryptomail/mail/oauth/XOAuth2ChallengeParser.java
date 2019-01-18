package org.atalk.xryptomail.mail.oauth;

import org.atalk.xryptomail.mail.XryptoMailLib;
import org.atalk.xryptomail.mail.filter.Base64;
import org.json.*;
import timber.log.Timber;

/**
 * Parses Google's Error/Challenge responses
 * See: https://developers.google.com/gmail/xoauth2_protocol#error_response
 */
public class XOAuth2ChallengeParser {
    public static final String BAD_RESPONSE = "400";


    public static boolean shouldRetry(String response, String host) {
        String decodedResponse = Base64.decode(response);

        try {
            JSONObject json = new JSONObject(decodedResponse);
            String status = json.getString("status");
            if (XryptoMailLib.isDebug()) {
                Timber.i("Challenge response status: %s. Response: %s => %s", status, response, decodedResponse);
            }
            if (!BAD_RESPONSE.equals(status)) {
                return false;
            }
        } catch (JSONException jsonException) {
            Timber.e("Error decoding JSON response from: %s. Response: %s => %s", host, response, decodedResponse);
        }
        return true;
    }
}
