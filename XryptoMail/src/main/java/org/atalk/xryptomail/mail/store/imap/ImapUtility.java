/*
 * Copyright (C) 2012 The K-9 Dog Walkers
 * Copyright (C) 2011 The Android Open Source Project
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

package org.atalk.xryptomail.mail.store.imap;

import org.atalk.xryptomail.mail.Flag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import timber.log.Timber;

/**
 * Utility methods for use with IMAP.
 */
class ImapUtility
{
    /**
     * Gets all of the values in a sequence set per RFC 3501.
     *
     * Any ranges are expanded into a list of individual numbers.
     *
     * <pre>
     * sequence-number = nz-number / "*"
     * sequence-range  = sequence-number ":" sequence-number
     * sequence-set    = (sequence-number / sequence-range) *("," sequence-set)
     * </pre>
     *
     * @param set The sequence set string as received by the server.
     * @return The list of IDs as strings in this sequence set. If the set is invalid, an empty
     * list is returned.
     */
    public static List<String> getImapSequenceValues(String set)
    {
        List<String> list = new ArrayList<>();
        if (set != null) {
            String[] setItems = set.split(",");
            for (String item : setItems) {
                if (item.indexOf(':') == -1) {
                    // simple item
                    if (isNumberValid(item)) {
                        list.add(item);
                    }
                }
                else {
                    // range
                    list.addAll(getImapRangeValues(item));
                }
            }
        }
        return list;
    }

    /**
     * Expand the given number range into a list of individual numbers.
     *
     * <pre>
     * sequence-number = nz-number / "*"
     * sequence-range  = sequence-number ":" sequence-number
     * sequence-set    = (sequence-number / sequence-range) *("," sequence-set)
     * </pre>
     *
     * @param range The range string as received by the server.
     * @return The list of IDs as strings in this range. If the range is not valid, an empty list
     * is returned.
     */
    public static List<String> getImapRangeValues(String range)
    {
        List<String> list = new ArrayList<>();
        try {
            if (range != null) {
                int colonPos = range.indexOf(':');
                if (colonPos > 0) {
                    long first = Long.parseLong(range.substring(0, colonPos));
                    long second = Long.parseLong(range.substring(colonPos + 1));
                    if (is32bitValue(first) && is32bitValue(second)) {
                        if (first < second) {
                            for (long i = first; i <= second; i++) {
                                list.add(Long.toString(i));
                            }
                        }
                        else {
                            for (long i = first; i >= second; i--) {
                                list.add(Long.toString(i));
                            }
                        }
                    }
                    else {
                        Timber.d("Invalid range: %s", range);
                    }
                }
            }
        } catch (NumberFormatException e) {
            Timber.d(e, "Invalid range value: %s", range);
        }
        return list;
    }

    private static boolean isNumberValid(String number)
    {
        try {
            long value = Long.parseLong(number);
            if (is32bitValue(value)) {
                return true;
            }
        } catch (NumberFormatException e) {
            // do nothing
        }

        Timber.d("Invalid UID value: %s", number);
        return false;
    }

    private static boolean is32bitValue(long value)
    {
        return ((value & ~0xFFFFFFFFL) == 0L);
    }

    /**
     * Encode a string to be able to use it in an IMAP command.
     *
     * "A quoted string is a sequence of zero or more 7-bit characters,
     * excluding CR and LF, with double quote (<">) characters at each
     * end." - Section 4.3, RFC 3501
     *
     * Double quotes and backslash are escaped by prepending a backslash.
     *
     * @param str The input string (only 7-bit characters allowed).
     * @return The string encoded as quoted (IMAP) string.
     */
    public static String encodeString(String str)
    {
        return "\"" + str.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public static ImapResponse getLastResponse(List<ImapResponse> responses)
    {
        int lastIndex = responses.size() - 1;
        return responses.get(lastIndex);
    }

    public static String combineFlags(Iterable<Flag> flags, boolean canCreateForwardedFlag)
    {
        List<String> flagNames = new ArrayList<>();
        for (Flag flag : flags) {
            if (flag == Flag.SEEN) {
                flagNames.add("\\Seen");
            }
            else if (flag == Flag.DELETED) {
                flagNames.add("\\Deleted");
            }
            else if (flag == Flag.ANSWERED) {
                flagNames.add("\\Answered");
            }
            else if (flag == Flag.FLAGGED) {
                flagNames.add("\\Flagged");
            }
            else if (flag == Flag.FORWARDED && canCreateForwardedFlag) {
                flagNames.add("$Forwarded");
            }
        }
        return ImapUtility.join(" ", flagNames);
    }

    public static String join(String delimiter, Collection<? extends Object> tokens)
    {
        if (tokens == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        boolean firstTime = true;
        for (Object token : tokens) {
            if (firstTime) {
                firstTime = false;
            }
            else {
                sb.append(delimiter);
            }
            sb.append(token);
        }
        return sb.toString();
    }
}
