package org.atalk.xryptomail.ui.message;

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import org.atalk.xryptomail.mail.Message;
import org.atalk.xryptomail.mailstore.LocalMessage;
import org.atalk.xryptomail.mailstore.MessageViewInfo;
import org.atalk.xryptomail.mailstore.MessageViewInfoExtractor;
import org.atalk.xryptomail.ui.crypto.MessageCryptoAnnotations;

import timber.log.Timber;

public class LocalMessageExtractorLoader extends AsyncTaskLoader<MessageViewInfo>
{
    private static final MessageViewInfoExtractor messageViewInfoExtractor = MessageViewInfoExtractor.getInstance();

    private final Message message;
    private MessageViewInfo messageViewInfo;
    @Nullable
    private MessageCryptoAnnotations annotations;

    public LocalMessageExtractorLoader(Context context, Message message, @Nullable MessageCryptoAnnotations annotations)
    {
        super(context);
        this.message = message;
        this.annotations = annotations;
    }

    @Override
    protected void onStartLoading()
    {
        if (messageViewInfo != null) {
            super.deliverResult(messageViewInfo);
        }
        if (takeContentChanged() || messageViewInfo == null) {
            forceLoad();
        }
    }

    @Override
    public void deliverResult(MessageViewInfo messageViewInfo)
    {
        this.messageViewInfo = messageViewInfo;
        super.deliverResult(messageViewInfo);
    }

    @Override
    @WorkerThread
    public MessageViewInfo loadInBackground()
    {
        try {
            return messageViewInfoExtractor.extractMessageForView(message, annotations);
        } catch (Exception e) {
            Timber.e(e, "Error while decoding message");
            return null;
        }
    }

    public boolean isCreatedFor(LocalMessage localMessage, MessageCryptoAnnotations messageCryptoAnnotations)
    {
        return annotations == messageCryptoAnnotations && message.equals(localMessage);
    }
}
