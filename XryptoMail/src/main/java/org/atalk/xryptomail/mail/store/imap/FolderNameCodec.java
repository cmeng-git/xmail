package org.atalk.xryptomail.mail.store.imap;

import com.beetstra.jutf7.CharsetProvider;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

class FolderNameCodec {
    private final Charset modifiedUtf7Charset;

    public static FolderNameCodec newInstance() {
        return new FolderNameCodec();
    }

    private FolderNameCodec() {
        modifiedUtf7Charset = new CharsetProvider().charsetForName("X-RFC-3501");
    }

    public String encode(String folderName) {
        ByteBuffer byteBuffer = modifiedUtf7Charset.encode(folderName);
        byte[] bytes = new byte[byteBuffer.limit()];
        byteBuffer.get(bytes);

        return new String(bytes, StandardCharsets.US_ASCII);
    }

    public String decode(String encodedFolderName) throws CharacterCodingException {
        CharsetDecoder decoder = modifiedUtf7Charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT);
        ByteBuffer byteBuffer = ByteBuffer.wrap(encodedFolderName.getBytes(StandardCharsets.US_ASCII));
        CharBuffer charBuffer = decoder.decode(byteBuffer);

        return charBuffer.toString();
    }
}
