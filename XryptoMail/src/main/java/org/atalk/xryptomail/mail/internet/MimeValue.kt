package org.atalk.xryptomail.mail.internet

data class MimeValue(
        val value: String,
        val parameters: Map<String, String> = emptyMap(),
        val ignoredParameters: List<Pair<String, String>> = emptyList(),
        val parserErrorIndex: Int? = null
)
