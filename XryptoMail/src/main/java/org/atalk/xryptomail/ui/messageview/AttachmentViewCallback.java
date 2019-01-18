package org.atalk.xryptomail.ui.messageview;

import org.atalk.xryptomail.mailstore.AttachmentViewInfo;

interface AttachmentViewCallback {
    void onViewAttachment(AttachmentViewInfo attachment);
    void onSaveAttachment(AttachmentViewInfo attachment);
    void onSaveAttachmentToUserProvidedDirectory(AttachmentViewInfo attachment);
}
