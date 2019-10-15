package org.atalk.xryptomail.mail;

public class XryptoMailLib
{
    private static DebugStatus debugStatus = new DefaultDebugStatus();

    private XryptoMailLib()
    {
    }

    public static final int PUSH_WAKE_LOCK_TIMEOUT = 60000;
    public static final String IDENTITY_HEADER = "X-XryptoMail-Identity";

    /**
     * Should Xrypto log the conversation it has over the wire with SMTP servers?
     */
    public static boolean DEBUG_PROTOCOL_SMTP = true;

    /**
     * Should Xrypto log the conversation it has over the wire with IMAP servers?
     */
    public static boolean DEBUG_PROTOCOL_IMAP = true;

    /**
     * Should Xrypto log the conversation it has over the wire with POP3 servers?
     */
    public static boolean DEBUG_PROTOCOL_POP3 = true;

    /**
     * Should Xrypto log the conversation it has over the wire with WebDAV servers?
     */
    public static boolean DEBUG_PROTOCOL_WEBDAV = true;

    public static boolean isDebug()
    {
        return debugStatus.enabled();
    }

    public static boolean isDebugSensitive()
    {
        return debugStatus.debugSensitive();
    }

    public static void setDebugSensitive(boolean b)
    {
        if (debugStatus instanceof WritableDebugStatus) {
            ((WritableDebugStatus) debugStatus).setSensitive(b);
        }
    }

    public static void setDebug(boolean b)
    {
        if (debugStatus instanceof WritableDebugStatus) {
            ((WritableDebugStatus) debugStatus).setEnabled(b);
        }
    }

    public interface DebugStatus
    {
        boolean enabled();

        boolean debugSensitive();
    }

    public static void setDebugStatus(DebugStatus status)
    {
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }
        debugStatus = status;
    }

    private interface WritableDebugStatus extends DebugStatus
    {
        void setEnabled(boolean enabled);

        void setSensitive(boolean sensitive);
    }

    private static class DefaultDebugStatus implements WritableDebugStatus
    {
        private boolean enabled;
        private boolean sensitive;

        @Override
        public boolean enabled()
        {
            return enabled;
        }

        @Override
        public boolean debugSensitive()
        {
            return sensitive;
        }

        @Override
        public void setEnabled(boolean enabled)
        {
            this.enabled = enabled;
        }

        @Override
        public void setSensitive(boolean sensitive)
        {
            this.sensitive = sensitive;
        }
    }
}
