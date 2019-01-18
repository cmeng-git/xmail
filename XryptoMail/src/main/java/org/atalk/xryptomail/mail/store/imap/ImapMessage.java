package org.atalk.xryptomail.mail.store.imap;

import org.atalk.xryptomail.mail.Flag;
import org.atalk.xryptomail.mail.Folder;
import org.atalk.xryptomail.mail.MessagingException;
import org.atalk.xryptomail.mail.internet.MimeMessage;

import java.util.Collections;

class ImapMessage extends MimeMessage
{
    ImapMessage(String uid, Folder folder)
    {
        this.mUid = uid;
        this.mFolder = folder;
    }

    public void setSize(int size)
    {
        this.mSize = size;
    }

    public void setFlagInternal(Flag flag, boolean set)
            throws MessagingException
    {
        super.setFlag(flag, set);
    }

    @Override
    public void setFlag(Flag flag, boolean set)
            throws MessagingException
    {
        super.setFlag(flag, set);
        mFolder.setFlags(Collections.singletonList(this), Collections.singleton(flag), set);
    }

    @Override
    public void delete(String trashFolderName)
            throws MessagingException
    {
        getFolder().delete(Collections.singletonList(this), trashFolderName);
    }
}
