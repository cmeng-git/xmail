package org.atalk.xryptomail.activity.compose;

import java.util.ArrayList;
import java.util.List;

import android.app.PendingIntent;
import org.atalk.xryptomail.activity.compose.RecipientMvpView.CryptoSpecialModeDisplayType;
import org.atalk.xryptomail.activity.compose.RecipientMvpView.CryptoStatusDisplayType;
import org.atalk.xryptomail.activity.compose.RecipientPresenter.CryptoMode;
import org.atalk.xryptomail.activity.compose.RecipientPresenter.CryptoProviderState;
import org.atalk.xryptomail.activity.compose.RecipientPresenter.XryptoMode;
import org.atalk.xryptomail.message.AutocryptStatusInteractor.RecipientAutocryptStatus;
import org.atalk.xryptomail.message.AutocryptStatusInteractor.RecipientAutocryptStatusType;
import org.atalk.xryptomail.view.RecipientSelectView.Recipient;

/** This is an immutable object which contains all relevant metadata entered
 * during e-mail composition to apply cryptographic operations before sending
 * or saving as draft.
 */
public class ComposeCryptoStatus {
    private CryptoProviderState cryptoProviderState;
    private Long openPgpKeyId;
    private String[] recipientAddresses;
    private boolean enablePgpInline;
    private boolean preferEncryptMutual;
    private CryptoMode mCryptoMode;
	private XryptoMode mXryptoMode;
    private RecipientAutocryptStatus recipientAutocryptStatus;

    public Long getOpenPgpKeyId() {
        return openPgpKeyId;
    }

    CryptoStatusDisplayType getCryptoStatusDisplayType() {
        switch (cryptoProviderState) {
            case UNCONFIGURED:
                return CryptoStatusDisplayType.UNCONFIGURED;
            case UNINITIALIZED:
                return CryptoStatusDisplayType.UNINITIALIZED;
            case LOST_CONNECTION:
            case ERROR:
                return CryptoStatusDisplayType.ERROR;
            case OK:
                // provider status is ok -> return value is based on mCryptoMode
                break;
            default:
                throw new AssertionError("all CryptoProviderStates must be handled!");
        }

        if (recipientAutocryptStatus == null) {
            throw new IllegalStateException("Display type must be obtained from provider!");
        }

        RecipientAutocryptStatusType recipientAutocryptStatusType = recipientAutocryptStatus.type;

        if (recipientAutocryptStatusType == RecipientAutocryptStatusType.ERROR) {
            return CryptoStatusDisplayType.ERROR;
        }

        switch (mCryptoMode) {
            case CHOICE_ENABLED:
                if (recipientAutocryptStatusType.canEncrypt()) {
                    if (recipientAutocryptStatusType.isConfirmed()) {
                        return CryptoStatusDisplayType.CHOICE_ENABLED_TRUSTED;
                    } else {
                        return CryptoStatusDisplayType.CHOICE_ENABLED_UNTRUSTED;
                    }
                } else {
                    return CryptoStatusDisplayType.CHOICE_ENABLED_ERROR;
                }
            case CHOICE_DISABLED:
                if (recipientAutocryptStatusType.canEncrypt()) {
                    if (recipientAutocryptStatusType.isConfirmed()) {
                        return CryptoStatusDisplayType.CHOICE_DISABLED_TRUSTED;
                    } else {
                        return CryptoStatusDisplayType.CHOICE_DISABLED_UNTRUSTED;
                    }
                } else {
                    return CryptoStatusDisplayType.CHOICE_DISABLED_UNAVAILABLE;
                }
            case NO_CHOICE:
                if (recipientAutocryptStatusType == RecipientAutocryptStatusType.NO_RECIPIENTS) {
                    return CryptoStatusDisplayType.NO_CHOICE_EMPTY;
                } else if (canEncryptAndIsMutualDefault()) {
                    if (recipientAutocryptStatusType.isConfirmed()) {
                        return CryptoStatusDisplayType.NO_CHOICE_MUTUAL_TRUSTED;
                    } else {
                        return CryptoStatusDisplayType.NO_CHOICE_MUTUAL;
                    }
                } else if (recipientAutocryptStatusType.canEncrypt()) {
                    if (recipientAutocryptStatusType.isConfirmed()) {
                        return CryptoStatusDisplayType.NO_CHOICE_AVAILABLE_TRUSTED;
                    } else {
                        return CryptoStatusDisplayType.NO_CHOICE_AVAILABLE;
                    }
                }
                return CryptoStatusDisplayType.NO_CHOICE_UNAVAILABLE;
            case SIGN_ONLY:
                return CryptoStatusDisplayType.SIGN_ONLY;
            default:
                throw new AssertionError("all CryptoModes must be handled!");
        }
    }

    CryptoSpecialModeDisplayType getCryptoSpecialModeDisplayType() {
        if (cryptoProviderState != CryptoProviderState.OK) {
            return CryptoSpecialModeDisplayType.NONE;
        }

        if (isSignOnly() && isPgpInlineModeEnabled()) {
            return CryptoSpecialModeDisplayType.SIGN_ONLY_PGP_INLINE;
        }

        if (isSignOnly()) {
            return CryptoSpecialModeDisplayType.SIGN_ONLY;
        }

        if (allRecipientsCanEncrypt() && isPgpInlineModeEnabled()) {
            return CryptoSpecialModeDisplayType.PGP_INLINE;
        }

        return CryptoSpecialModeDisplayType.NONE;
    }

    public boolean shouldUsePgpMessageBuilder() {
        // CryptoProviderState.ERROR will be handled as an actual error, see SendErrorState
        return cryptoProviderState != CryptoProviderState.UNCONFIGURED;
    }

    public boolean isEncryptionEnabled() {
        if (cryptoProviderState == CryptoProviderState.UNCONFIGURED) {
            return false;
        }

        boolean isExplicitlyEnabled = (mCryptoMode == CryptoMode.CHOICE_ENABLED);
        boolean isMutualAndNotDisabled = (mCryptoMode != CryptoMode.CHOICE_DISABLED && canEncryptAndIsMutualDefault());
        return isExplicitlyEnabled || isMutualAndNotDisabled;
    }

    public boolean isSignOnly() {
        return mCryptoMode == CryptoMode.SIGN_ONLY;
    }

    public boolean isSigningEnabled() {
        return mCryptoMode == CryptoMode.SIGN_ONLY || isEncryptionEnabled();
    }

    public boolean isPgpInlineModeEnabled() {
        return enablePgpInline;
    }

    public boolean isProviderStateOk() {
        return cryptoProviderState == CryptoProviderState.OK;
    }

    boolean allRecipientsCanEncrypt() {
        return recipientAutocryptStatus != null && recipientAutocryptStatus.type.canEncrypt();
    }

    public String[] getRecipientAddresses() {
        return recipientAddresses;
    }

    public boolean hasRecipients() {
        return recipientAddresses.length > 0;
    }

    public boolean isSenderPreferEncryptMutual() {
        return preferEncryptMutual;
    }

    private boolean isRecipientsPreferEncryptMutual() {
        return recipientAutocryptStatus.type.isMutual();
    }

    boolean canEncryptAndIsMutualDefault() {
        return allRecipientsCanEncrypt() && isSenderPreferEncryptMutual() && isRecipientsPreferEncryptMutual();
    }

    boolean hasAutocryptPendingIntent() {
        return recipientAutocryptStatus.hasPendingIntent();
    }

    PendingIntent getAutocryptPendingIntent() {
        return recipientAutocryptStatus.intent;
    }
	
	public void setXryptoMode(XryptoMode xryptoMode) {
		mXryptoMode = xryptoMode;
	}

	public XryptoMode getXryptoMode(){
		return mXryptoMode;
	}

    public static class ComposeCryptoStatusBuilder {
        private CryptoProviderState cryptoProviderState;
        private CryptoMode cryptoMode;
		private XryptoMode xryptoMode;
        private Long openPgpKeyId;
        private List<Recipient> recipients;
        private Boolean enablePgpInline;
        private Boolean preferEncryptMutual;

        public ComposeCryptoStatusBuilder setCryptoProviderState(CryptoProviderState cryptoProviderState) {
            this.cryptoProviderState = cryptoProviderState;
            return this;
        }

        public ComposeCryptoStatusBuilder setCryptoMode(CryptoMode cryptoMode) {
            this.cryptoMode = cryptoMode;
            return this;
        }

		public ComposeCryptoStatusBuilder setXryptoMode(XryptoMode xryptoMode) {
			this.xryptoMode = xryptoMode;
			return this;
		}

        public ComposeCryptoStatusBuilder setOpenPgpKeyId(Long openPgpKeyId) {
            this.openPgpKeyId = openPgpKeyId;
            return this;
        }

        public ComposeCryptoStatusBuilder setRecipients(List<Recipient> recipients) {
            this.recipients = recipients;
            return this;
        }

        public ComposeCryptoStatusBuilder setEnablePgpInline(boolean cryptoEnableCompat) {
            this.enablePgpInline = cryptoEnableCompat;
            return this;
        }

        public ComposeCryptoStatusBuilder setPreferEncryptMutual(boolean preferEncryptMutual) {
            this.preferEncryptMutual = preferEncryptMutual;
            return this;
        }

        public ComposeCryptoStatus build() {
            if (cryptoProviderState == null) {
                throw new AssertionError("cryptoProviderState must be set!");
            }
            if (cryptoMode == null) {
                throw new AssertionError("crypto mode must be set!");
            }
            if (recipients == null) {
                throw new AssertionError("recipients must be set!");
            }
            if (enablePgpInline == null) {
                throw new AssertionError("enablePgpInline must be set!");
            }
            if (preferEncryptMutual == null) {
                throw new AssertionError("preferEncryptMutual must be set!");
            }

            ArrayList<String> recipientAddresses = new ArrayList<>();
            for (Recipient recipient : recipients) {
                recipientAddresses.add(recipient.address.getAddress());
            }

            ComposeCryptoStatus result = new ComposeCryptoStatus();
            result.cryptoProviderState = cryptoProviderState;
            result.mCryptoMode = cryptoMode;
			result.mXryptoMode = xryptoMode;
            result.recipientAddresses = recipientAddresses.toArray(new String[0]);
            result.openPgpKeyId = openPgpKeyId;
            result.enablePgpInline = enablePgpInline;
            result.preferEncryptMutual = preferEncryptMutual;
            return result;
        }
    }

    ComposeCryptoStatus withRecipientAutocryptStatus(RecipientAutocryptStatus recipientAutocryptStatusType) {
        ComposeCryptoStatus result = new ComposeCryptoStatus();
        result.cryptoProviderState = cryptoProviderState;
        result.mCryptoMode = mCryptoMode;
		result.mXryptoMode = mXryptoMode;
        result.recipientAddresses = recipientAddresses;
        result.openPgpKeyId = openPgpKeyId;
        result.enablePgpInline = enablePgpInline;
        result.recipientAutocryptStatus = recipientAutocryptStatusType;
        return result;
    }

    public enum SendErrorState {
        PROVIDER_ERROR,
        KEY_CONFIG_ERROR,
        ENABLED_ERROR
    }

    public SendErrorState getSendErrorStateOrNull() {
        if (cryptoProviderState != CryptoProviderState.OK) {
            // TODO: be more specific about this error
            return SendErrorState.PROVIDER_ERROR;
        }
        if (openPgpKeyId == null && (isEncryptionEnabled() || isSignOnly())) {
            return SendErrorState.KEY_CONFIG_ERROR;
        }
        if (isEncryptionEnabled() && !allRecipientsCanEncrypt()) {
            return SendErrorState.ENABLED_ERROR;
        }
        return null;
    }

    enum AttachErrorState {
        IS_INLINE
    }

    AttachErrorState getAttachErrorStateOrNull() {
        if (cryptoProviderState == CryptoProviderState.UNCONFIGURED) {
            return null;
        }
        if (enablePgpInline) {
            return AttachErrorState.IS_INLINE;
        }
        return null;
    }
}
