package org.atalk.xryptomail.activity.setup;

import static org.atalk.xryptomail.mail.ServerSettings.Type.IMAP;
import static org.atalk.xryptomail.mail.ServerSettings.Type.POP3;
import static org.atalk.xryptomail.mail.ServerSettings.Type.WebDAV;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import java.security.cert.X509Certificate;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.atalk.xryptomail.Account;
import org.atalk.xryptomail.BuildConfig;
import org.atalk.xryptomail.Preferences;
import org.atalk.xryptomail.R;
import org.atalk.xryptomail.account.GmailWebViewClient;
import org.atalk.xryptomail.account.OutlookWebViewClient;
import org.atalk.xryptomail.activity.Accounts;
import org.atalk.xryptomail.activity.setup.AccountSetupPresenter.Stage;
import org.atalk.xryptomail.fragment.ConfirmationDialogFragment.ConfirmationDialogFragmentListener;
import org.atalk.xryptomail.helper.Utility;
import org.atalk.xryptomail.mail.AuthType;
import org.atalk.xryptomail.mail.ConnectionSecurity;
import org.atalk.xryptomail.mail.ServerSettings.Type;
import org.atalk.xryptomail.view.ClientCertificateSpinner;
import org.atalk.xryptomail.view.ClientCertificateSpinner.OnClientCertificateChangedListener;

import me.zhanghai.android.materialprogressbar.MaterialProgressBar;
import timber.log.Timber;

public class AccountSetupActivity extends AppCompatActivity implements AccountSetupContract.View,
        ConfirmationDialogFragmentListener, OnClickListener, OnCheckedChangeListener {
    private static final String EXTRA_ACCOUNT = "account";
    private static final String EXTRA_STAGE = "stage";
    private static final String EXTRA_EDIT_SETTINGS = "edit_settings";
    private static final String EXTRA_MAKE_DEFAULT = "make_default";
    private static final String STATE_STAGE = "state_stage";
    private static final String STATE_ACCOUNT = "state_account";
    private static final String STAGE_CONFIG = "state_config";
    private static final String STATE_EDIT_SETTINGS = "state_edit_settings";
    private static final String STATE_MAKE_DEFAULT = "state_make_default";

    private AccountSetupPresenter presenter;
    private TextView messageView;
    private EditText usernameView;
    private EditText passwordView;
    @SuppressWarnings("FieldCanBeLocal")
    private Button manualSetupButton;
    private RadioGroup accountTypeRadioGroup;
    private ClientCertificateSpinner clientCertificateSpinner;
    private TextView clientCertificateLabelView;
    private TextInputLayout usernameViewLayout;
    private TextInputLayout passwordViewLayout;
    private TextInputLayout serverViewLayout;
    private TextInputEditText serverView;
    private TextInputEditText portView;
    private TextView securityTypeLabelView;
    private Spinner securityTypeView;
    private Spinner authTypeView;
    private CheckBox imapAutoDetectNamespaceView;
    private TextInputLayout imapPathPrefixLayout;
    private EditText imapPathPrefixView;
    private EditText webdavPathPrefixView;
    private EditText webdavAuthPathView;
    private EditText webdavMailboxPathView;
    private Button nextButton;
    private ViewGroup requireLoginSettingsView;
    private CheckBox compressionMobile;
    private CheckBox compressionWifi;
    private CheckBox compressionOther;
    private CheckBox subscribedFoldersOnly;
    private AuthTypeAdapter authTypeAdapter;
    private CoordinatorLayout coordinatorLayout;

    @SuppressWarnings("FieldCanBeLocal")
    private MaterialProgressBar progressBar;
    private CheckBox requireLoginView;
    private Spinner checkFrequencyView;
    private Spinner displayCountView;
    private CheckBox notifyView;
    private CheckBox notifySyncView;
    private CheckBox pushEnable;
    private EditText description;
    private EditText name;

    @SuppressWarnings("FieldCanBeLocal")
    private Button doneButton;
    private ViewFlipper flipper;
    AlertDialog authDialog;

    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private int position;

    int[] layoutIds = new int[]{R.layout.account_setup_basics,
            R.layout.account_setup_check_settings, R.layout.account_setup_account_type,
            R.layout.account_setup_incoming, R.layout.account_setup_outgoing,
            R.layout.account_setup_options, R.layout.account_setup_names};
    private EditText emailView;

    boolean editSettings;

    Stage stage;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.account_setup);

        flipper = findViewById(R.id.view_flipper);
        flipper.setInAnimation(this, R.anim.fade_in);
        flipper.setOutAnimation(this, R.anim.fade_out);

        Preferences preferences = Preferences.getPreferences(this);
        presenter = new AccountSetupPresenter(this, preferences, this);

        Intent intent = getIntent();

        stage = (Stage) intent.getSerializableExtra(EXTRA_STAGE);
        String accountUuid = intent.getStringExtra(EXTRA_ACCOUNT);
        editSettings = intent.getBooleanExtra(EXTRA_EDIT_SETTINGS, false);
        boolean makeDefault = intent.getBooleanExtra(EXTRA_MAKE_DEFAULT, false);

        if (savedInstanceState != null) {
            stage = (Stage) savedInstanceState.getSerializable(STATE_STAGE);
            editSettings = savedInstanceState.getBoolean(STATE_EDIT_SETTINGS, editSettings);

            accountUuid = savedInstanceState.getString(STATE_ACCOUNT, accountUuid);

            AccountConfigImpl accountConfig = savedInstanceState.getParcelable(STAGE_CONFIG);
            presenter.onGetAccountConfig(accountConfig);

            makeDefault = savedInstanceState.getBoolean(STATE_MAKE_DEFAULT, makeDefault);
            presenter.onGetMakeDefault(makeDefault);
        }

        presenter.onGetAccountUuid(accountUuid);

        if (stage == null) {
            stage = Stage.BASICS;
        }

        switch (stage) {
            case BASICS:
            case AUTOCONFIGURATION:
            case AUTOCONFIGURATION_INCOMING_CHECKING:
            case AUTOCONFIGURATION_OUTGOING_CHECKING:
                goToBasics();
                break;
            case ACCOUNT_TYPE:
                goToAccountType();
                break;
            case INCOMING:
            case INCOMING_CHECKING:
                goToIncoming();
                break;
            case OUTGOING:
            case OUTGOING_CHECKING:
                goToOutgoing();
                break;
            case ACCOUNT_NAMES:
                goToAccountNames();
                break;
        }
        getOnBackPressedDispatcher().addCallback(backPressedCallback);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putSerializable(STATE_STAGE, stage);
        final boolean editSettings = presenter.isEditSettings();
        outState.putBoolean(STATE_EDIT_SETTINGS, editSettings);
        if (editSettings) {
            outState.putString(STATE_ACCOUNT, presenter.getAccount().getUuid());
        }
        else {
            outState.putParcelable(STAGE_CONFIG, (AccountConfigImpl) presenter.getAccountConfig());
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        presenter.onRestoreStart();
        super.onRestoreInstanceState(savedInstanceState);
        presenter.onRestoreEnd();
    }

    private void basicsStart() {
        coordinatorLayout = findViewById(R.id.basics_coordinator_layout);
        emailView = findViewById(R.id.account_email);
        passwordViewLayout = findViewById(R.id.password_input_layout);
        passwordView = findViewById(R.id.account_password);
        manualSetupButton = findViewById(R.id.manual_setup);
        nextButton = findViewById(R.id.basics_next);
        nextButton.setOnClickListener(this);
        manualSetupButton.setOnClickListener(this);

        initializeViewListenersInBasics();

        presenter.onBasicsStart();
        onInputChangedInBasics();
    }

    private void onInputChangedInBasics() {
        if (presenter == null)
            return;

        presenter.onInputChangedInBasics(emailView.getText().toString(), passwordView.getText().toString());
    }

    private TextWatcher validationTextWatcherInBasics = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            presenter.onInputChangedInBasics(emailView.getText().toString(), passwordView.getText().toString());
        }
    };

    private void initializeViewListenersInBasics() {
        emailView.addTextChangedListener(validationTextWatcherInBasics);
        passwordView.addTextChangedListener(validationTextWatcherInBasics);
    }

    @Override
    public void setPasswordInBasicsEnabled(boolean enabled) {
        passwordViewLayout.setEnabled(enabled);
    }

    @Override
    public void setPasswordHintInBasics(String hint) {
        passwordViewLayout.setHint(hint);
    }

    @Override
    public void setManualSetupButtonInBasicsVisibility(int visibility) {
        manualSetupButton.setVisibility(visibility);
    }

    private void checkingStart() {
        messageView = findViewById(R.id.message);
        progressBar = findViewById(R.id.progress);
        progressBar.setIndeterminate(true);
    }

    private void accountTypeStart() {
        accountTypeRadioGroup = findViewById(R.id.account_type_radio_group);
        findViewById(R.id.account_type_next).setOnClickListener(this);
        presenter.onAccountTypeStart();
    }

    private int getPositionFromLayoutId(@LayoutRes int layoutId) {
        for (int i = 0; i < layoutIds.length; i++) {
            if (layoutIds[i] == layoutId) {
                return i;
            }
        }
        return -1;
    }

    private static long mLastClickTime = 0;

    private static boolean isActionValid() {
        long now = System.currentTimeMillis();
        boolean valid = (now - mLastClickTime > 1000);
        mLastClickTime = now;
        return valid;
    }

    public static void actionNewAccount(Context context) {
        if (isActionValid()) {
            Intent i = new Intent(context, AccountSetupActivity.class);
            context.startActivity(i);
        }
    }

    public void goToBasics() {
        stage = Stage.BASICS;
        setSelection(getPositionFromLayoutId(R.layout.account_setup_basics));
        basicsStart();
    }


    public void goToOutgoing() {
        stage = Stage.OUTGOING;
        setSelection(getPositionFromLayoutId(R.layout.account_setup_outgoing));
        outgoingStart();
    }


    @Override
    public void goToIncoming() {
        stage = Stage.INCOMING;
        setSelection(getPositionFromLayoutId(R.layout.account_setup_incoming));
        incomingStart();
    }


    @Override
    public void goToAutoConfiguration() {
        stage = Stage.AUTOCONFIGURATION;
        setSelection(getPositionFromLayoutId(R.layout.account_setup_check_settings));
        checkingStart();
        presenter.onCheckingStart(Stage.AUTOCONFIGURATION);
    }


    @Override
    public void goToAccountType() {
        stage = Stage.ACCOUNT_TYPE;
        setSelection(getPositionFromLayoutId(R.layout.account_setup_account_type));
        accountTypeStart();
    }

    @Override
    public void goToAccountNames() {
        stage = Stage.ACCOUNT_NAMES;
        setSelection(getPositionFromLayoutId(R.layout.account_setup_names));
        namesStart();
    }

    @Override
    public void goToOutgoingChecking() {
        stage = Stage.OUTGOING_CHECKING;
        setSelection(getPositionFromLayoutId(R.layout.account_setup_check_settings));
        checkingStart();
        presenter.onCheckingStart(stage);
    }

    @Override
    public void end() {
        finish();
    }

    @Override
    public void goToIncomingChecking() {
        stage = Stage.INCOMING_CHECKING;
        setSelection(getPositionFromLayoutId(R.layout.account_setup_check_settings));
        checkingStart();
        presenter.onCheckingStart(stage);
    }

    public void listAccounts() {
        Accounts.listAccounts(this);
    }

    private void setSelection(int position) {
        if (position == -1)
            return;

        this.position = position;
        flipper.setDisplayedChild(position);
    }

    @Override
    public void showAcceptKeyDialog(final int msgResId, final String exMessage, final String message,
            final X509Certificate certificate) {

        // TODO: refactor with DialogFragment.
        // This is difficult because we need to pass through chain[0] for onClick()
        new AlertDialog.Builder(AccountSetupActivity.this)
                .setTitle(getString(R.string.account_setup_failed_dlg_invalid_certificate_title))
                .setMessage(getString(msgResId, exMessage)
                        + " " + message
                )
                .setCancelable(true)
                .setPositiveButton(getString(R.string.account_setup_failed_dlg_invalid_certificate_accept),
                        (dialog, which) -> presenter.onCertificateAccepted(certificate))
                .setNegativeButton(getString(R.string.account_setup_failed_dlg_invalid_certificate_reject),
                        (dialog, which) -> presenter.onCertificateRefused())
                .show();
    }

    @Override
    public void showErrorDialog(@StringRes final int msgResId, final Object... args) {
        // TODO: 8/13/17 add a "detail" button and show exception details here
        Snackbar.make(coordinatorLayout, getString(msgResId, args), Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void showErrorDialog(String errorMessage) {
        Snackbar.make(coordinatorLayout, errorMessage, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void setMessage(@StringRes int id) {
        messageView.setText(getString(id));
    }

    @Override
    public Context getContext() {
        return this;
    }

    @Override
    public void doPositiveClick(int dialogId) {
        presenter.onPositiveClickedInConfirmationDialog();
    }

    @Override
    public void doNegativeClick(int dialogId) {
        presenter.onNegativeClickedInConfirmationDialog();
    }

    @Override
    public void dialogCancelled(int dialogId) {

    }

    // ------

    private void initializeViewListenersInIncoming() {
        securityTypeView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position,
                    long id) {

                onInputChangedInIncoming();

            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { /* unused */ }
        });

        authTypeView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position,
                    long id) {

                onInputChangedInIncoming();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { /* unused */ }
        });

        clientCertificateSpinner.setOnClientCertificateChangedListener(clientCertificateChangedListenerInIncoming);
        usernameView.addTextChangedListener(validationTextWatcherInIncoming);
        passwordView.addTextChangedListener(validationTextWatcherInIncoming);
        serverView.addTextChangedListener(validationTextWatcherInIncoming);
        portView.addTextChangedListener(validationTextWatcherInIncoming);
    }

    private void onInputChangedInIncoming() {
        if (presenter == null)
            return;

        final AuthType selectedAuthType = getSelectedAuthType();
        final ConnectionSecurity selectedSecurity = getSelectedSecurity();
        if (selectedAuthType == null || selectedSecurity == null)
            return;

        presenter.onInputChangedInIncoming(clientCertificateSpinner.getAlias(),
                serverView.getText().toString(),
                portView.getText().toString(), usernameView.getText().toString(),
                passwordView.getText().toString(), selectedAuthType, selectedSecurity);

    }


    protected void onNextInIncoming() {
        try {
            ConnectionSecurity connectionSecurity = getSelectedSecurity();

            String username = usernameView.getText().toString();
            String password = passwordView.getText().toString();
            String clientCertificateAlias = clientCertificateSpinner.getAlias();
            boolean autoDetectNamespace = imapAutoDetectNamespaceView.isChecked();
            String imapPathPrefix = imapPathPrefixView.getText().toString();
            String webdavPathPrefix = webdavPathPrefixView.getText().toString();
            String webdavAuthPath = webdavAuthPathView.getText().toString();
            String webdavMailboxPath = webdavMailboxPathView.getText().toString();

            AuthType authType = getSelectedAuthType();

            String host = serverView.getText().toString();
            int port = Integer.parseInt(portView.getText().toString());

            boolean compressMobile = compressionMobile.isChecked();
            boolean compressWifi = compressionWifi.isChecked();
            boolean compressOther = compressionOther.isChecked();
            boolean subscribeFoldersOnly = subscribedFoldersOnly.isChecked();

            presenter.onNextInIncomingClicked(username, password, clientCertificateAlias, autoDetectNamespace,
                    imapPathPrefix, webdavPathPrefix, webdavAuthPath, webdavMailboxPath, host, port,
                    connectionSecurity, authType, compressMobile, compressWifi, compressOther,
                    subscribeFoldersOnly);

        } catch (Exception e) {
            failure(e);
        }
    }

    public void onClick(View v) {
        try {
            switch (v.getId()) {
                case R.id.basics_next:
                    presenter.onNextButtonInBasicViewClicked(emailView.getText().toString(),
                            passwordView.getText().toString());
                    break;
                case R.id.account_type_next:
                    Type serverType;
                    switch (accountTypeRadioGroup.getCheckedRadioButtonId()) {
                        case R.id.imap:
                            serverType = IMAP;
                            break;
                        case R.id.pop:
                            serverType = POP3;
                            break;
                        case R.id.webdav:
                            serverType = WebDAV;
                            break;
                        default:
                            serverType = null;
                            break;
                    }
                    presenter.onNextButtonInAccountTypeClicked(serverType);
                    break;

                case R.id.incoming_next:
                    onNextInIncoming();
                    break;
                case R.id.outgoing_next:
                    onNextInOutgoing();
                    break;
                case R.id.done:
                    presenter.onNextButtonInNamesClicked(name.getText().toString(), description.getText().toString());
                    break;
                case R.id.manual_setup:
                    presenter.onManualSetupButtonClicked(emailView.getText().toString(),
                            passwordView.getText().toString());
                    break;
            }
        } catch (Exception e) {
            failure(e);
        }
    }

    private void failure(Exception use) {
        Timber.e(use, "Failure");
        String toastText = getString(R.string.account_setup_bad_uri, use.getMessage());

        Toast toast = Toast.makeText(getApplication(), toastText, Toast.LENGTH_LONG);
        toast.show();
    }


    private TextWatcher validationTextWatcherInIncoming = new TextWatcher() {
        public void afterTextChanged(Editable s) {
            onInputChangedInIncoming();
        }

        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    };

    private OnClientCertificateChangedListener clientCertificateChangedListenerInIncoming
            = alias -> onInputChangedInIncoming();

    @Override
    public void setAuthTypeInIncoming(AuthType authType) {
        OnItemSelectedListener onItemSelectedListener = authTypeView.getOnItemSelectedListener();
        authTypeView.setOnItemSelectedListener(null);
        int authTypePosition =
                ((AuthTypeAdapter) authTypeView.getAdapter()).getAuthPosition(authType);
        authTypeView.setSelection(authTypePosition, false);
        authTypeView.setOnItemSelectedListener(onItemSelectedListener);
    }

    @Override
    public void setSecurityTypeInIncoming(ConnectionSecurity security) {
        OnItemSelectedListener onItemSelectedListener = securityTypeView.getOnItemSelectedListener();
        securityTypeView.setOnItemSelectedListener(null);
        int connectionSecurityPosition = ((ConnectionSecurityAdapter)
                securityTypeView.getAdapter()).getConnectionSecurityPosition(security);
        securityTypeView.setSelection(connectionSecurityPosition, false);
        securityTypeView.setOnItemSelectedListener(onItemSelectedListener);
    }

    @Override
    public void setUsernameInIncoming(String username) {
        usernameView.removeTextChangedListener(validationTextWatcherInIncoming);
        usernameView.setText(username);
        usernameView.addTextChangedListener(validationTextWatcherInIncoming);
    }

    @Override
    public void setPasswordInIncoming(String password) {
        passwordView.removeTextChangedListener(validationTextWatcherInIncoming);
        passwordView.setText(password);
        passwordView.addTextChangedListener(validationTextWatcherInIncoming);
    }

    @Override
    public void setCertificateAliasInIncoming(String alias) {
        clientCertificateSpinner.setOnClientCertificateChangedListener(null);
        clientCertificateSpinner.setAlias(alias);
        clientCertificateSpinner.
                setOnClientCertificateChangedListener(clientCertificateChangedListenerInIncoming);
    }

    @Override
    public void setServerInIncoming(String server) {
        serverView.removeTextChangedListener(validationTextWatcherInIncoming);
        serverView.setText(server);
        serverView.addTextChangedListener(validationTextWatcherInIncoming);
    }

    @Override
    public void setPortInIncoming(String port) {
        portView.removeTextChangedListener(validationTextWatcherInIncoming);
        portView.setText(port);
        portView.addTextChangedListener(validationTextWatcherInIncoming);
    }

    @Override
    public void setServerLabel(String label) {
        serverViewLayout.setHint(label);
    }

    @Override
    public void hideViewsWhenPop3() {
        findViewById(R.id.imap_path_prefix_section).setVisibility(View.GONE);
        findViewById(R.id.webdav_advanced_header).setVisibility(View.GONE);
        findViewById(R.id.webdav_mailbox_alias_section).setVisibility(View.GONE);
        findViewById(R.id.webdav_owa_path_section).setVisibility(View.GONE);
        findViewById(R.id.webdav_auth_path_section).setVisibility(View.GONE);
        findViewById(R.id.compression_section).setVisibility(View.GONE);
        findViewById(R.id.compression_label).setVisibility(View.GONE);
        subscribedFoldersOnly.setVisibility(View.GONE);
    }

    @Override
    public void hideViewsWhenImap() {
        findViewById(R.id.webdav_advanced_header).setVisibility(View.GONE);
        findViewById(R.id.webdav_mailbox_alias_section).setVisibility(View.GONE);
        findViewById(R.id.webdav_owa_path_section).setVisibility(View.GONE);
        findViewById(R.id.webdav_auth_path_section).setVisibility(View.GONE);
    }

    @Override
    public void hideViewsWhenImapAndNotEdit() {
        findViewById(R.id.imap_folder_setup_section).setVisibility(View.GONE);
    }

    @Override
    public void hideViewsWhenWebDav() {
        findViewById(R.id.imap_path_prefix_section).setVisibility(View.GONE);
        findViewById(R.id.incoming_account_auth_type_label).setVisibility(View.GONE);
        findViewById(R.id.incoming_account_auth_type).setVisibility(View.GONE);
        findViewById(R.id.compression_section).setVisibility(View.GONE);
        findViewById(R.id.compression_label).setVisibility(View.GONE);
        subscribedFoldersOnly.setVisibility(View.GONE);
    }

    @Override
    public void setImapAutoDetectNamespace(boolean autoDetectNamespace) {
        imapAutoDetectNamespaceView.setChecked(autoDetectNamespace);
    }

    @Override
    public void setImapPathPrefix(String imapPathPrefix) {
        imapPathPrefixView.setText(imapPathPrefix);
    }

    @Override
    public void setWebDavPathPrefix(String webDavPathPrefix) {
        webdavPathPrefixView.setText(webDavPathPrefix);
    }

    @Override
    public void setWebDavAuthPath(String authPath) {
        webdavAuthPathView.setText(authPath);
    }

    @Override
    public void setWebDavMailboxPath(String mailboxPath) {
        webdavMailboxPathView.setText(mailboxPath);
    }

    private void incomingStart() {
        View incomingView = findViewById(R.id.account_setup_incoming);
        coordinatorLayout = findViewById(R.id.incoming_coordinator_layout);
        usernameView = incomingView.findViewById(R.id.incoming_account_username);
        usernameViewLayout = incomingView.findViewById(R.id.incoming_account_username_layout);
        passwordView = incomingView.findViewById(R.id.incoming_account_password);
        clientCertificateSpinner = incomingView.findViewById(R.id.incoming_account_client_certificate_spinner);
        clientCertificateLabelView = incomingView.findViewById(R.id.account_client_certificate_label);
        passwordViewLayout = incomingView.findViewById(R.id.incoming_account_password_layout);
        serverViewLayout = incomingView.findViewById(R.id.incoming_account_server_layout);
        serverView = incomingView.findViewById(R.id.incoming_account_server);
        portView = incomingView.findViewById(R.id.incoming_account_port);
        securityTypeLabelView = incomingView.findViewById(R.id.account_setup_incoming_security_label);
        securityTypeView = incomingView.findViewById(R.id.incoming_account_security_type);
        authTypeView = incomingView.findViewById(R.id.incoming_account_auth_type);
        imapAutoDetectNamespaceView = incomingView.findViewById(R.id.imap_autodetect_namespace);
        imapPathPrefixLayout = incomingView.findViewById(R.id.imap_path_prefix_layout);
        imapPathPrefixView = incomingView.findViewById(R.id.imap_path_prefix);
        webdavPathPrefixView = incomingView.findViewById(R.id.webdav_path_prefix);
        webdavAuthPathView = incomingView.findViewById(R.id.webdav_auth_path);
        webdavMailboxPathView = incomingView.findViewById(R.id.webdav_mailbox_path);
        nextButton = incomingView.findViewById(R.id.incoming_next);
        compressionMobile = incomingView.findViewById(R.id.compression_mobile);
        compressionWifi = incomingView.findViewById(R.id.compression_wifi);
        compressionOther = incomingView.findViewById(R.id.compression_other);
        subscribedFoldersOnly = incomingView.findViewById(R.id.subscribed_folders_only);

        nextButton.setOnClickListener(this);
        imapAutoDetectNamespaceView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && imapPathPrefixView.hasFocus()) {
                imapPathPrefixView.focusSearch(View.FOCUS_UP).requestFocus();
            }
            else if (!isChecked) {
                imapPathPrefixView.requestFocus();
            }
            imapPathPrefixLayout.setVisibility(isChecked ? View.GONE : View.VISIBLE);
        });

        authTypeAdapter = AuthTypeAdapter.get(this);
        authTypeView.setAdapter(authTypeAdapter);
        portView.setKeyListener(DigitsKeyListener.getInstance("0123456789"));
        initializeViewListenersInIncoming();

        boolean editSettings = false;
        if (getIntent().getAction() != null) {
            editSettings = getIntent().getAction().equals(Intent.ACTION_EDIT);
        }
        presenter.onIncomingStart(editSettings);
    }

    @Override
    public void setImapPathPrefixSectionVisibility(int visibility) {
        findViewById(R.id.imap_path_prefix_section).setVisibility(visibility);
    }

    @Override
    public void setCompressionSectionVisibility(int visibility) {
        findViewById(R.id.compression_label).setVisibility(visibility);
        findViewById(R.id.compression_section).setVisibility(visibility);
    }

    @Override
    public void setNextButtonInIncomingEnabled(boolean enabled) {
        nextButton.setEnabled(enabled);
    }

    @Override
    public void goToIncomingSettings() {
        goToIncoming();
    }

    @Override
    public void setNextButtonInBasicsEnabled(boolean enabled) {
        nextButton.setEnabled(enabled);
        Utility.setCompoundDrawablesAlpha(nextButton, nextButton.isEnabled() ? 255 : 128);
    }

    @Override
    public void setSecurityChoices(ConnectionSecurity[] choices) {
        // Note that connectionSecurityChoices is configured above based on server type
        ConnectionSecurityAdapter securityTypesAdapter = ConnectionSecurityAdapter.get(this, choices);
        securityTypeView.setAdapter(securityTypesAdapter);
    }

    @Override
    public void setAuthTypeInsecureText(boolean insecure) {
        authTypeAdapter.useInsecureText(insecure);
    }

    @Override
    public void setViewNotExternalInIncoming() {
        passwordViewLayout.setVisibility(View.VISIBLE);
        clientCertificateLabelView.setVisibility(View.GONE);
        clientCertificateSpinner.setVisibility(View.GONE);
        imapAutoDetectNamespaceView.setEnabled(true);
        passwordViewLayout.setEnabled(true);
        securityTypeView.setEnabled(true);
        portView.setEnabled(true);
        passwordView.requestFocus();
    }

    @Override
    public void setViewExternalInIncoming() {
        passwordViewLayout.setVisibility(View.GONE);
        clientCertificateLabelView.setVisibility(View.VISIBLE);
        clientCertificateSpinner.setVisibility(View.VISIBLE);
        imapAutoDetectNamespaceView.setEnabled(true);
        passwordViewLayout.setEnabled(true);
        securityTypeView.setEnabled(true);
        portView.setEnabled(true);
        clientCertificateSpinner.chooseCertificate();
    }

    @Override
    public void setViewOAuth2InIncoming() {
        imapAutoDetectNamespaceView.setEnabled(false);
        passwordViewLayout.setEnabled(false);
        securityTypeView.setEnabled(false);
        portView.setEnabled(false);
    }

    @Override
    public void showFailureToast(Exception use) {
        failure(use);
    }

    @Override
    public void setCompressionMobile(boolean compressionMobileBoolean) {
        compressionMobile.setChecked(compressionMobileBoolean);
    }

    @Override
    public void setCompressionWifi(boolean compressionWifiBoolean) {
        compressionWifi.setChecked(compressionWifiBoolean);
    }

    @Override
    public void setCompressionOther(boolean compressionOtherBoolean) {
        compressionOther.setChecked(compressionOtherBoolean);
    }

    @Override
    public void setSubscribedFoldersOnly(boolean subscribedFoldersOnlyBoolean) {
        subscribedFoldersOnly.setChecked(subscribedFoldersOnlyBoolean);
    }

    @Override
    public void showInvalidSettingsToast() {
        String toastText = getString(R.string.account_setup_outgoing_invalid_setting_combo_notice,
                getString(R.string.account_setup_incoming_auth_type_label),
                AuthType.EXTERNAL.toString(),
                getString(R.string.account_setup_incoming_security_label),
                ConnectionSecurity.NONE.toString());
        Toast.makeText(this, toastText, Toast.LENGTH_LONG).show();
    }

    @Override
    public void showInvalidOAuthError() {
        usernameViewLayout.setErrorEnabled(true);
        usernameViewLayout.setError(getString(R.string.OAuth2_not_supported));
    }

    @Override
    public void clearInvalidOAuthError() {
        usernameViewLayout.setError("");
    }

    // names

    public void namesStart() {
        doneButton = findViewById(R.id.done);
        doneButton.setOnClickListener(this);

        description = findViewById(R.id.account_description);
        name = findViewById(R.id.account_name);
        name.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                onInputChangeInNames();
            }
        });
        presenter.onNamesStart();
    }

    @Override
    public void setDoneButtonInNamesEnabled(boolean enabled) {
        doneButton.setEnabled(enabled);
    }

    private void onInputChangeInNames() {
        presenter.onInputChangedInNames(name.getText().toString(), description.getText().toString());
    }

    @Override
    public void goToListAccounts() {
        listAccounts();
    }
    // outgoing

    /**
     * Called at the end of either {@code onCreate()} or
     * {@code onRestoreInstanceState()}, after the views have been initialized,
     * so that the listeners are not triggered during the view initialization.
     * This avoids needless calls to {@code onInputChangedInOutgoing()} which is called
     * immediately after this is called.
     */
    private void initializeViewListenersInOutgoing() {

        /*
         * Updates the port when the user changes the security type. This allows
         * us to show a reasonable default which the user can change.
         */
        securityTypeView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                onInputChangedInOutgoing();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { /* unused */ }
        });

        authTypeView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                onInputChangedInOutgoing();

            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { /* unused */ }
        });

        requireLoginView.setOnCheckedChangeListener(this);
        clientCertificateSpinner.setOnClientCertificateChangedListener(clientCertificateChangedListenerInOutgoing);
        usernameView.addTextChangedListener(validationTextWatcherInOutgoing);
        passwordView.addTextChangedListener(validationTextWatcherInOutgoing);
        serverView.addTextChangedListener(validationTextWatcherInOutgoing);
        portView.addTextChangedListener(validationTextWatcherInOutgoing);
    }

    /**
     * This is invoked only when the user makes changes to a widget, not when
     * widgets are changed programmatically.  (The logic is simpler when you know
     * that this is the last thing called after an input change.)
     */
    private void onInputChangedInOutgoing() {
        if (presenter == null)
            return;

        presenter.onInputChangedInOutgoing(clientCertificateSpinner.getAlias(),
                serverView.getText().toString(),
                portView.getText().toString(), usernameView.getText().toString(),
                passwordView.getText().toString(), getSelectedAuthType(), getSelectedSecurity(),
                requireLoginView.isChecked());

    }

    protected void onNextInOutgoing() {
        ConnectionSecurity securityType = getSelectedSecurity();
        String username = usernameView.getText().toString();
        String password = passwordView.getText().toString();
        String clientCertificateAlias = clientCertificateSpinner.getAlias();
        AuthType authType = getSelectedAuthType();

        String newHost = serverView.getText().toString();
        int newPort = Integer.parseInt(portView.getText().toString());

        boolean requireLogin = requireLoginView.isChecked();
        presenter.onNextInOutgoingClicked(username, password, clientCertificateAlias, newHost, newPort, securityType,
                authType, requireLogin);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        requireLoginSettingsView.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        onInputChangedInOutgoing();
    }


    /*
     * Calls onInputChangedInOutgoing() which enables or disables the Next button
     * based on the fields' validity.
     */
    private TextWatcher validationTextWatcherInOutgoing = new TextWatcher() {
        public void afterTextChanged(Editable s) {
            onInputChangedInOutgoing();
        }

        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    };

    private OnClientCertificateChangedListener clientCertificateChangedListenerInOutgoing
            = alias -> onInputChangedInOutgoing();

    private AuthType getSelectedAuthType() {
        AuthTypeHolder holder = (AuthTypeHolder) authTypeView.getSelectedItem();
        if (holder == null)
            return null;
        return holder.getAuthType();
    }

    private ConnectionSecurity getSelectedSecurity() {
        ConnectionSecurityHolder holder = (ConnectionSecurityHolder) securityTypeView.getSelectedItem();
        if (holder == null)
            return null;
        return holder.getConnectionSecurity();
    }

    private void outgoingStart() {
        final View outgoingView = findViewById(R.id.account_setup_outgoing);
        coordinatorLayout = outgoingView.findViewById(R.id.outgoing_coordinator_layout);
        usernameView = outgoingView.findViewById(R.id.outgoing_account_username);
        usernameViewLayout = outgoingView.findViewById(R.id.outgoing_account_username_layout);
        passwordView = outgoingView.findViewById(R.id.outgoing_account_password);
        passwordViewLayout = outgoingView.findViewById(R.id.outgoing_account_password_layout);
        clientCertificateSpinner = outgoingView.findViewById(R.id.outgoing_account_client_certificate_spinner);
        clientCertificateLabelView = outgoingView.findViewById(R.id.account_client_certificate_label);
        serverView = outgoingView.findViewById(R.id.outgoing_account_server);
        portView = outgoingView.findViewById(R.id.outgoing_account_port);
        requireLoginView = outgoingView.findViewById(R.id.account_require_login);
        requireLoginSettingsView = outgoingView.findViewById(R.id.account_require_login_settings);
        securityTypeView = outgoingView.findViewById(R.id.outgoing_account_security_type);
        authTypeView = outgoingView.findViewById(R.id.outgoing_account_auth_type);
        nextButton = outgoingView.findViewById(R.id.outgoing_next);

        nextButton.setOnClickListener(this);
        securityTypeView.setAdapter(ConnectionSecurityAdapter.get(this));
        authTypeAdapter = AuthTypeAdapter.get(this);
        authTypeView.setAdapter(authTypeAdapter);

        portView.setKeyListener(DigitsKeyListener.getInstance("0123456789"));
        onCheckedChanged(requireLoginView, requireLoginView.isChecked());
        boolean editSettings = false;
        if (getIntent().getAction() != null) {
            editSettings = getIntent().getAction().equals(Intent.ACTION_EDIT);
        }
        presenter.onOutgoingStart(editSettings);

        initializeViewListenersInOutgoing();
        onInputChangedInOutgoing();
    }

    @Override
    public void setNextButtonInOutgoingEnabled(boolean enabled) {
        nextButton.setEnabled(enabled);
        Utility.setCompoundDrawablesAlpha(nextButton, nextButton.isEnabled() ? 255 : 128);
    }

    @Override
    public void setAuthTypeInOutgoing(AuthType authType) {
        OnItemSelectedListener onItemSelectedListener = authTypeView.getOnItemSelectedListener();
        authTypeView.setOnItemSelectedListener(null);
        authTypeView.setSelection(authTypeAdapter.getAuthPosition(authType), false);
        authTypeView.setOnItemSelectedListener(onItemSelectedListener);
    }

    @Override
    public void setSecurityTypeInOutgoing(ConnectionSecurity security) {
        OnItemSelectedListener onItemSelectedListener = securityTypeView.getOnItemSelectedListener();
        securityTypeView.setOnItemSelectedListener(null);
        securityTypeView.setSelection(security.ordinal(), false);
        securityTypeView.setOnItemSelectedListener(onItemSelectedListener);
    }

    @Override
    public void setUsernameInOutgoing(String username) {
        usernameView.removeTextChangedListener(validationTextWatcherInOutgoing);
        usernameView.setText(username);
        requireLoginView.setChecked(true);
        requireLoginSettingsView.setVisibility(View.VISIBLE);
        usernameView.addTextChangedListener(validationTextWatcherInOutgoing);
    }

    @Override
    public void setPasswordInOutgoing(String password) {
        passwordView.removeTextChangedListener(validationTextWatcherInOutgoing);
        passwordView.setText(password);
        passwordView.addTextChangedListener(validationTextWatcherInOutgoing);
    }

    @Override
    public void setCertificateAliasInOutgoing(String alias) {
        clientCertificateSpinner.setOnClientCertificateChangedListener(null);
        clientCertificateSpinner.setAlias(alias);
        clientCertificateSpinner.
                setOnClientCertificateChangedListener(clientCertificateChangedListenerInOutgoing);
    }

    @Override
    public void setServerInOutgoing(String server) {
        serverView.removeTextChangedListener(validationTextWatcherInOutgoing);
        serverView.setText(server);
        serverView.addTextChangedListener(validationTextWatcherInOutgoing);
    }

    @Override
    public void setPortInOutgoing(String port) {
        portView.removeTextChangedListener(validationTextWatcherInOutgoing);
        portView.setText(port);
        portView.addTextChangedListener(validationTextWatcherInOutgoing);
    }

    @Override
    public void showInvalidSettingsToastInOutgoing() {
        String toastText = getString(R.string.account_setup_outgoing_invalid_setting_combo_notice,
                getString(R.string.account_setup_incoming_auth_type_label),
                AuthType.EXTERNAL.toString(),
                getString(R.string.account_setup_incoming_security_label),
                ConnectionSecurity.NONE.toString());
        Toast.makeText(this, toastText, Toast.LENGTH_LONG).show();
    }

    @Override
    public void updateAuthPlainTextInOutgoing(boolean insecure) {
        authTypeAdapter.useInsecureText(insecure);
    }

    @Override
    public void setViewNotExternalInOutgoing() {
        // show password fields, hide client certificate fields
        passwordViewLayout.setVisibility(View.VISIBLE);
        clientCertificateLabelView.setVisibility(View.GONE);
        clientCertificateSpinner.setVisibility(View.GONE);
        passwordViewLayout.setEnabled(true);
        securityTypeView.setEnabled(true);
        portView.setEnabled(true);

        passwordView.requestFocus();
    }

    @Override
    public void setViewExternalInOutgoing() {
        // hide password fields, show client certificate fields
        passwordViewLayout.setVisibility(View.GONE);
        clientCertificateLabelView.setVisibility(View.VISIBLE);
        clientCertificateSpinner.setVisibility(View.VISIBLE);
        passwordViewLayout.setEnabled(true);
        securityTypeView.setEnabled(true);
        portView.setEnabled(true);

        // This may again invoke onInputChangedInOutgoing()
        clientCertificateSpinner.chooseCertificate();
    }

    @Override
    public void setViewOAuth2InOutgoing() {
        passwordViewLayout.setEnabled(false);
        securityTypeView.setEnabled(false);
        portView.setEnabled(false);
    }

    public static void actionEditIncomingSettings(Activity context, Account account) {
        context.startActivity(intentActionEditIncomingSettings(context, account));
    }

    public static Intent intentActionEditIncomingSettings(Context context, Account account) {
        Intent i = new Intent(context, AccountSetupActivity.class);
        i.setAction(Intent.ACTION_EDIT);
        i.putExtra(EXTRA_ACCOUNT, account.getUuid());
        i.putExtra(EXTRA_STAGE, Stage.INCOMING);
        return i;
    }

    public static void actionEditOutgoingSettings(Context context, Account account) {
        context.startActivity(intentActionEditOutgoingSettings(context, account));
    }

    public static Intent intentActionEditOutgoingSettings(Context context, Account account) {
        Intent i = new Intent(context, AccountSetupActivity.class);
        i.setAction(Intent.ACTION_EDIT);
        i.putExtra(EXTRA_ACCOUNT, account.getUuid());
        i.putExtra(EXTRA_STAGE, Stage.OUTGOING);
        return i;
    }

    OnBackPressedCallback backPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            presenter.onBackPressed();
        }
    };

    @Override
    public void goBack() {
        presenter.onBackPressed();
    }

    @Override
    public void startIntentForResult(Intent intent, int requestCode) {
        startActivityForResult(intent, requestCode);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void openGmailUrl(String url) {
        WebView webView = setUpAuthDialog();
        webView.setWebViewClient(new GmailWebViewClient(presenter));
        // authDialog.setTitle(R.string.linked_webview_title_gmail);
        webView.loadUrl(url);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void openOutlookUrl(String url) {
        WebView webView = setUpAuthDialog();
        webView.setWebViewClient(new OutlookWebViewClient(presenter));
        // no use; not showing in dialog
        // authDialog.setTitle(R.string.linked_webview_title_outlook);
        webView.loadUrl(url);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView setUpAuthDialog() {
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookies(null);

        authDialog = new AlertDialog.Builder(this)
                .setView(R.layout.oauth_webview)
                .setCancelable(true)
                .setOnDismissListener(dialog -> presenter.onWebViewDismiss())
                .show();

        // Must include this to show soft keyboard on webPage
        Window window = authDialog.getWindow();
        if (window != null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }

//        authDialog = new Dialog(this);
//        authDialog.setContentView(R.layout.oauth_webview);
//        authDialog.setCancelable(true);
//        authDialog.setOnDismissListener(dialog -> presenter.onWebViewDismiss());

        // set dialog layout to avoid content clipping; not working in API-36, use AlertDialog instead.
//        Window window = authDialog.getWindow();
//        if (window != null) {
//            // window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
//            window.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
//        }

        final WebView webView = authDialog.findViewById(R.id.web_view);
        final WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setSaveFormData(false);

        // ** Id-Version has problem with outlook.com auth site, content not showing for API-27-
        // But XMail Id-Version is required by gmail.com
        // getUserAgentString: outlook.com not working Mozilla/5.0 (Linux; Android 8.0.0; Android SDK built for x86 Build/OSR1.170901.043; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/58.0.3029.125 Mobile Safari/537.36
        String agent = "Mozilla/5.0 (Linux; Android 7.0; Nexus 4 Build/KRT16H) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/58.0.3029.125 Mobile Safari/537.36";
        webSettings.setUserAgentString(String.format("%s/%s %s", BuildConfig.APPLICATION_ID, BuildConfig.VERSION_NAME, agent));
        return webView;
    }

    @Override
    public void closeAuthDialog() {
        if (authDialog != null) {
            authDialog.dismiss();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        presenter.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onResume() {
        super.onResume();
        presenter.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        presenter.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        presenter.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        presenter.onDestroy();
    }
}
