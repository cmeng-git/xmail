
package org.atalk.xryptomail.mail;

public abstract class BodyPart implements Part {
    private String serverExtra;
    private Multipart mParent;

    @Override
    public String getServerExtra() {
        return serverExtra;
    }

    @Override
    public void setServerExtra(String serverExtra) {
        this.serverExtra = serverExtra;
    }

    public Multipart getParent() {
        return mParent;
    }

    public void setParent(Multipart parent) {
        mParent = parent;
    }

    public abstract void setEncoding(String encoding) throws MessagingException;
}
