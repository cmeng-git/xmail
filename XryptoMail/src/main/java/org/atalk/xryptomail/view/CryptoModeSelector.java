package org.atalk.xryptomail.view;

public interface CryptoModeSelector {
    void setCryptoStatusListener(CryptoStatusSelectedListener cryptoStatusListener);
    void setCryptoStatus(CryptoModeSelectorState status);

    interface CryptoStatusSelectedListener {
        void onCryptoStatusSelected(CryptoModeSelectorState type);
    }

    enum CryptoModeSelectorState {
        DISABLED, SIGN_ONLY, OPPORTUNISTIC, PRIVATE
    }
}
