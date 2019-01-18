package org.atalk.xryptomail.mail.message;

import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.parser.ContentHandler;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.stream.MimeConfig;
import org.atalk.xryptomail.mail.MessagingException;
import org.atalk.xryptomail.mail.Part;

import java.io.IOException;
import java.io.InputStream;

public class MessageHeaderParser {

    public static void parse(final Part part, InputStream headerInputStream) throws MessagingException {
        MimeStreamParser parser = getMimeStreamParser();
        parser.setContentHandler(new MessageHeaderParserContentHandler(part));

        try {
            parser.parse(headerInputStream);
        } catch (MimeException me) {
            throw new MessagingException("Error parsing headers", me);
        } catch (IOException e) {
            throw new MessagingException("I/O error parsing headers", e);
        }
    }

    private static MimeStreamParser getMimeStreamParser() {
        MimeConfig parserConfig = new MimeConfig.Builder()
                .setMaxHeaderLen(-1)
                .setMaxLineLen(-1)
                .setMaxHeaderCount(-1)
                .build();

        return new MimeStreamParser(parserConfig);
    }

    private static class MessageHeaderParserContentHandler implements ContentHandler {
        private final Part part;

        public MessageHeaderParserContentHandler(Part part) {
            this.part = part;
        }

        @Override
        public void field(Field rawField) throws MimeException {
            String name = rawField.getName();
            String raw = rawField.getRaw().toString();
            part.addRawHeader(name, raw);
        }

        @Override
        public void startMessage() throws MimeException {
            /* do nothing */
        }

        @Override
        public void endMessage() throws MimeException {
            /* do nothing */
        }

        @Override
        public void startBodyPart() throws MimeException {
            /* do nothing */
        }

        @Override
        public void endBodyPart() throws MimeException {
            /* do nothing */
        }

        @Override
        public void startHeader() throws MimeException {
            /* do nothing */
        }

        @Override
        public void endHeader() throws MimeException {
            /* do nothing */
        }

        @Override
        public void preamble(InputStream is) throws MimeException, IOException {
            /* do nothing */
        }

        @Override
        public void epilogue(InputStream is) throws MimeException, IOException {
            /* do nothing */
        }

        @Override
        public void startMultipart(BodyDescriptor bd) throws MimeException {
            /* do nothing */
        }

        @Override
        public void endMultipart() throws MimeException {
            /* do nothing */
        }

        @Override
        public void body(BodyDescriptor bd, InputStream is) throws MimeException, IOException {
            /* do nothing */
        }

        @Override
        public void raw(InputStream is) throws MimeException, IOException {
            /* do nothing */
        }
    }
}
