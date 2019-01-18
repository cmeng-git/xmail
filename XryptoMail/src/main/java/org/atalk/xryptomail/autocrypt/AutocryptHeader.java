package org.atalk.xryptomail.autocrypt;

import android.support.annotation.NonNull;

import java.util.Arrays;
import java.util.Map;

import okio.ByteString;

class AutocryptHeader
{
    static final String AUTOCRYPT_HEADER = "Autocrypt";

    static final String AUTOCRYPT_PARAM_ADDR = "addr";
    static final String AUTOCRYPT_PARAM_KEY_DATA = "keydata";

    static final String AUTOCRYPT_PARAM_TYPE = "type";
    static final String AUTOCRYPT_TYPE_1 = "1";

    static final String AUTOCRYPT_PARAM_PREFER_ENCRYPT = "prefer-encrypt";
    static final String AUTOCRYPT_PREFER_ENCRYPT_MUTUAL = "mutual";

    private static final int HEADER_LINE_LENGTH = 76;

    @NonNull
    final byte[] keyData;
    @NonNull
    final String addr;
    @NonNull
    final Map<String, String> parameters;
    final boolean isPreferEncryptMutual;

    AutocryptHeader(@NonNull Map<String, String> parameters, @NonNull String addr,
            @NonNull byte[] keyData, boolean isPreferEncryptMutual)
    {
        this.parameters = parameters;
        this.addr = addr;
        this.keyData = keyData;
        this.isPreferEncryptMutual = isPreferEncryptMutual;
    }

    String toRawHeaderString()
    {
        // TODO we don't properly fold lines here. if we want to support parameters, we need to do that somehow
        if (!parameters.isEmpty()) {
            throw new UnsupportedOperationException("arbitrary parameters not supported");
        }

        StringBuilder builder = new StringBuilder();
        builder.append(AutocryptHeader.AUTOCRYPT_HEADER).append(": ");
        builder.append(AutocryptHeader.AUTOCRYPT_PARAM_ADDR).append('=').append(addr).append("; ");
        if (isPreferEncryptMutual) {
            builder.append(AutocryptHeader.AUTOCRYPT_PARAM_PREFER_ENCRYPT)
                    .append('=').append(AutocryptHeader.AUTOCRYPT_PREFER_ENCRYPT_MUTUAL).append("; ");
        }
        builder.append(AutocryptHeader.AUTOCRYPT_PARAM_KEY_DATA).append("=");
        builder.append(createFoldedBase64KeyData(keyData));
        return builder.toString();
    }

    static String createFoldedBase64KeyData(byte[] keyData)
    {
        String base64KeyData = ByteString.of(keyData).base64();
        StringBuilder result = new StringBuilder();

        for (int i = 0, base64Length = base64KeyData.length(); i < base64Length; i += HEADER_LINE_LENGTH) {
            if (i + HEADER_LINE_LENGTH <= base64Length) {
                result.append("\r\n ");
                result.append(base64KeyData, i, i + HEADER_LINE_LENGTH);
            }
            else {
                result.append("\r\n ");
                result.append(base64KeyData, i, base64Length);
            }
        }
        return result.toString();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AutocryptHeader that = (AutocryptHeader) o;

        return isPreferEncryptMutual == that.isPreferEncryptMutual && Arrays.equals(keyData, that.keyData)
                && addr.equals(that.addr) && parameters.equals(that.parameters);
    }

    @Override
    public int hashCode()
    {
        int result = Arrays.hashCode(keyData);
        result = 31 * result + addr.hashCode();
        result = 31 * result + parameters.hashCode();
        result = 31 * result + (isPreferEncryptMutual ? 1 : 0);
        return result;
    }
}
