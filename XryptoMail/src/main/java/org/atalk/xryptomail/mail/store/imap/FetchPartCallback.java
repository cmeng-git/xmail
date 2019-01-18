package org.atalk.xryptomail.mail.store.imap;

import org.atalk.xryptomail.mail.*;
import org.atalk.xryptomail.mail.filter.FixedLengthInputStream;
import org.atalk.xryptomail.mail.internet.MimeHeader;

import java.io.IOException;

class FetchPartCallback implements ImapResponseCallback
{
	private final Part mPart;
	private final BodyFactory bodyFactory;


	FetchPartCallback(Part part, BodyFactory bodyFactory)
	{
		mPart = part;
		this.bodyFactory = bodyFactory;
	}

	@Override
	public Object foundLiteral(ImapResponse response, FixedLengthInputStream literal)
			throws IOException
	{
		if (!response.isTagged()
				&& ImapResponseParser.equalsIgnoreCase(response.get(1), "FETCH")) {
			//TODO: check for correct UID

			String contentTransferEncoding = mPart.getHeader(
					MimeHeader.HEADER_CONTENT_TRANSFER_ENCODING)[0];
			String contentType = mPart.getHeader(MimeHeader.HEADER_CONTENT_TYPE)[0];

			return bodyFactory.createBody(contentTransferEncoding, contentType, literal);
		}
		return null;
	}
}
