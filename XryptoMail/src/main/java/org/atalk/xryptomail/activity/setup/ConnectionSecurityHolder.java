package org.atalk.xryptomail.activity.setup;

import android.content.res.Resources;

import androidx.annotation.NonNull;

import org.atalk.xryptomail.R;
import org.atalk.xryptomail.mail.ConnectionSecurity;

public class ConnectionSecurityHolder {
    final ConnectionSecurity connectionSecurity;
    private final Resources resources;

    public ConnectionSecurityHolder(ConnectionSecurity connectionSecurity, Resources resources) {
        this.connectionSecurity = connectionSecurity;
        this.resources = resources;
    }

    @NonNull
    public String toString() {
        final int resourceId = resourceId();
        if (resourceId == 0) {
            return connectionSecurity.name();
        } else {
            return resources.getString(resourceId);
        }
    }

    private int resourceId() {
        switch (connectionSecurity) {
            case NONE: return R.string.account_setup_incoming_security_none_label;
            case STARTTLS_REQUIRED: return R.string.account_setup_incoming_security_tls_label;
            case SSL_TLS_REQUIRED:  return R.string.account_setup_incoming_security_ssl_label;
            default: return 0;
        }
    }

    public ConnectionSecurity getConnectionSecurity() {
        return connectionSecurity;
    }
}
