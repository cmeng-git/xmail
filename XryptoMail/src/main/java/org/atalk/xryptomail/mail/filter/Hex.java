/*
 * Copyright 2001-2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.atalk.xryptomail.mail.filter;

/**
 * This code was copied from the Apache Commons project.
 * The unnecessary parts have been left out.
 */
public class Hex
{
    /**
     * Used building output as Hex
     */
    private static final char[] LOWER_CASE = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
    private static final char[] UPPER_CASE = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    /**
     * Converts an array of bytes into an array of characters representing the hexadecimal values of each byte in order.
     * The returned array will be double the length of the passed array, as it takes two characters to represent any
     * given byte.
     *
     * @param data a byte[] to convert to Hex characters
     * @return A String containing lower-case hexadecimal characters
     */
    public static String encodeHex(byte[] data)
    {
        int l = data.length;
        char[] out = new char[l << 1];

        // two characters form the hex value.
        for (int i = 0, j = 0; i < l; i++) {
            out[j++] = LOWER_CASE[data[i] >>> 4 & 0x0F];
            out[j++] = LOWER_CASE[data[i] & 0x0F];
        }
        return new String(out);
    }

    public static StringBuilder appendHex(Byte value, boolean lowerCase)
    {
        StringBuilder out = new StringBuilder();
        char[] digits = (lowerCase) ? LOWER_CASE : UPPER_CASE;

        out.append(digits[value >>> 4 & 0x0F]);
        out.append(digits[value & 0x0F]);
        return out;
    }
}
