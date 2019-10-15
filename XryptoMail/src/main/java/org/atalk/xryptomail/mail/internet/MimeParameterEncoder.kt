package org.atalk.xryptomail.mail.internet

import org.atalk.xryptomail.mail.filter.Hex.appendHex
import org.atalk.xryptomail.helper.UrlEncodingHelper.encodeUtf8
import org.atalk.xryptomail.mail.helper.encodeUtf8
import org.atalk.xryptomail.mail.helper.utf8Size

/**
 * Encode MIME parameter values as specified in RFC 2045 and RFC 2231.
 */
object MimeParameterEncoder {
    // RFC 5322, section 2.1.1
    private const val MAX_LINE_LENGTH = 78

    private const val ENCODED_VALUE_PREFIX = "UTF-8''"


    /**
     * Create header field value with parameters encoded if necessary.
     */
    @JvmStatic
    fun encode(value: String, parameters: Map<String, String>): String {
        return if (parameters.isEmpty()) {
            value
        } else {
            buildString {
                append(value)
                encodeAndAppendParameters(parameters)
            }
        }
    }

    private fun StringBuilder.encodeAndAppendParameters(parameters: Map<String, String>) {
        for ((name, value) in parameters) {
            encodeAndAppendParameter(name, value)
        }
    }

    private fun StringBuilder.encodeAndAppendParameter(name: String, value: String) {
        val fixedCostLength = 1 /* folding space */ + name.length + 1 /* equals sign */ + 1 /* semicolon */
        val unencodedValueFitsOnSingleLine = fixedCostLength + value.length <= MAX_LINE_LENGTH
        val quotedValueMightFitOnSingleLine = fixedCostLength + value.length + 2 /* quotes */ <= MAX_LINE_LENGTH

        if (unencodedValueFitsOnSingleLine && value.isToken()) {
            appendParameter(name, value)
        } else if (quotedValueMightFitOnSingleLine && value.isQuotable() &&
                fixedCostLength + value.quotedLength() <= MAX_LINE_LENGTH) {
            appendParameter(name, value.quoted())
        } else {
            rfc2231EncodeAndAppendParameter(name, value)
        }
    }

    private fun StringBuilder.appendParameter(name: String, value: String) {
        append(";$CRLF ")
        append(name).append('=').append(value)
    }

    private fun StringBuilder.rfc2231EncodeAndAppendParameter(name: String, value: String) {
        val encodedValueLength = 1 /* folding space */ + name.length + 1 /* asterisk */ + 1 /* equal sign */ +
                ENCODED_VALUE_PREFIX.length + value.rfc2231EncodedLength() + 1 /* semicolon */

        if (encodedValueLength <= MAX_LINE_LENGTH) {
            appendRfc2231SingleLineParameter(name, value.rfc2231Encoded())
        } else {
            encodeAndAppendRfc2231MultiLineParameter(name, value)
        }
    }

    private fun StringBuilder.appendRfc2231SingleLineParameter(name: String, encodedValue: String) {
        append(";$CRLF ")
        append(name)
        append("*=$ENCODED_VALUE_PREFIX")
        append(encodedValue)
    }

    private fun StringBuilder.encodeAndAppendRfc2231MultiLineParameter(name: String, value: String) {
        var index = 0
        var line = 0
        var startOfLine = true
        var remainingSpaceInLine = 0
        val endIndex = value.length
        while (index < endIndex) {
            if (startOfLine) {
                append(";$CRLF ")
                val lineStartIndex = length - 1
                append(name).append('*').append(line).append("*=")
                if (line == 0) {
                    append(ENCODED_VALUE_PREFIX)
                }

                remainingSpaceInLine = MAX_LINE_LENGTH - (length - lineStartIndex) - 1 /* semicolon */
                if (remainingSpaceInLine < 3) {
                    throw UnsupportedOperationException("Parameter name too long")
                }

                startOfLine = false
                line++
            }

            val codePoint = value.codePointAt(index)

            // Keep all characters encoding a single code point on the same line
            val utf8Size = codePoint.utf8Size()
            if (utf8Size == 1 && codePoint.toChar().isAttributeChar() && remainingSpaceInLine >= 1) {
                append(codePoint.toChar())
                index++
                remainingSpaceInLine--
            } else if (remainingSpaceInLine >= utf8Size * 3) {
                codePoint.encodeUtf8 {
                    append('%')
                    appendHex(it, false)
                    remainingSpaceInLine -= 3
                }
                index += Character.charCount(codePoint)
            } else {
                startOfLine = true
            }
        }
    }

    private fun String.rfc2231Encoded() = buildString {
        this@rfc2231Encoded.encodeUtf8 { byte ->
            val c = byte.toChar()
            if (c.isAttributeChar()) {
                append(c)
            } else {
                append('%')
                appendHex(byte, false)
            }
        }
    }

    private fun String.rfc2231EncodedLength(): Int {
        var length = 0
        encodeUtf8 { byte ->
            length += if (byte.toChar().isAttributeChar()) 1 else 3
        }
        return length
    }

    private fun String.isToken() = when {
        isEmpty() -> false
        else -> all { it.isTokenChar() }
    }

    private fun String.isQuotable() = all { it.isQuotable() }

    private fun String.quoted(): String {
        // quoted-string = [CFWS] DQUOTE *([FWS] qcontent) [FWS] DQUOTE [CFWS]
        // qcontent      = qtext / quoted-pair
        // quoted-pair   = ("\" (VCHAR / WSP))

        return buildString(capacity = length + 16) {
            append(DQUOTE)
            for (c in this@quoted) {
                if (c.isQText() || c.isWsp()) {
                    append(c)
                } else if (c.isVChar()) {
                    append('\\').append(c)
                } else {
                    throw IllegalArgumentException("Unsupported character: $c")
                }
            }
            append(DQUOTE)
        }
    }

    private fun String.quotedLength(): Int {
        var length = 2 /* start and end quote */
        for (c in this) {
            if (c.isQText() || c.isWsp()) {
                length++
            } else if (c.isVChar()) {
                length += 2
            } else {
                throw IllegalArgumentException("Unsupported character: $c")
            }
        }
        return length
    }

    private fun Char.isQuotable() = when {
        isWsp() -> true
        isVChar() -> true
        else -> false
    }

    // RFC 5322: qtext = %d33 / %d35-91 / %d93-126 / obs-qtext
    private fun Char.isQText() = when (toInt()) {
        33 -> true
        in 35..91 -> true
        in 93..126 -> true
        else -> false
    }
}
