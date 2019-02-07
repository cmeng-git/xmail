package org.atalk.xryptomail.activity.setup;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceScreen;
import android.text.TextUtils;
import android.widget.Toast;

import org.atalk.xryptomail.BuildConfig;
import org.atalk.xryptomail.Preferences;
import org.atalk.xryptomail.R;
import org.atalk.xryptomail.XryptoMail;
import org.atalk.xryptomail.XryptoMail.NotificationHideSubject;
import org.atalk.xryptomail.XryptoMail.NotificationQuickDelete;
import org.atalk.xryptomail.XryptoMail.SplitViewMode;
import org.atalk.xryptomail.activity.ColorPickerDialog;
import org.atalk.xryptomail.activity.XMPreferenceActivity;
import org.atalk.xryptomail.helper.FileBrowserHelper;
import org.atalk.xryptomail.helper.FileBrowserHelper.FileBrowserFailOverCallback;
import org.atalk.xryptomail.notification.NotificationController;
import org.atalk.xryptomail.preferences.CheckBoxListPreference;
import org.atalk.xryptomail.preferences.Storage;
import org.atalk.xryptomail.preferences.StorageEditor;
import org.atalk.xryptomail.preferences.TimePickerPreference;
import org.atalk.xryptomail.service.MailService;
import org.atalk.xryptomail.ui.dialog.ApgDeprecationWarningDialog;
import org.openintents.openpgp.util.OpenPgpAppPreference;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.atalk.xryptomail.XryptoMail.confirmMarkAllRead;

public class Prefs extends XMPreferenceActivity
{
    /**
     * Immutable empty {@link CharSequence} array
     */
    private static final CharSequence[] EMPTY_CHAR_SEQUENCE_ARRAY = new CharSequence[0];

    /*
     * Keys of the preferences defined in res/xml/global_preferences.xml
     */
    private static final String PREFERENCE_LANGUAGE = "language";
    private static final String PREFERENCE_THEME = "theme";
    private static final String PREFERENCE_MESSAGE_VIEW_THEME = "messageViewTheme";
    private static final String PREFERENCE_FIXED_MESSAGE_THEME = "fixed_message_view_theme";
    private static final String PREFERENCE_COMPOSER_THEME = "message_compose_theme";
    private static final String PREFERENCE_FONT_SIZE = "font_size";
    private static final String PREFERENCE_ANIMATIONS = "animations";
    private static final String PREFERENCE_GESTURES = "gestures";
    private static final String PREFERENCE_VOLUME_NAVIGATION = "volume_navigation";
    private static final String PREFERENCE_START_INTEGRATED_INBOX = "start_integrated_inbox";
    private static final String PREFERENCE_CONFIRM_ACTIONS = "confirm_actions";
    private static final String PREFERENCE_NOTIFICATION_HIDE_SUBJECT = "notification_hide_subject";
    private static final String PREFERENCE_MEASURE_ACCOUNTS = "measure_accounts";
    private static final String PREFERENCE_COUNT_SEARCH = "count_search";
    private static final String PREFERENCE_HIDE_SPECIAL_ACCOUNTS = "hide_special_accounts";
    private static final String PREFERENCE_MESSAGELIST_CHECKBOXES = "messagelist_checkboxes";
    private static final String PREFERENCE_MESSAGELIST_PREVIEW_LINES = "messagelist_preview_lines";
    private static final String PREFERENCE_MESSAGELIST_SENDER_ABOVE_SUBJECT = "messagelist_sender_above_subject";
    private static final String PREFERENCE_MESSAGELIST_STARS = "messagelist_stars";
    private static final String PREFERENCE_MESSAGELIST_SHOW_CORRESPONDENT_NAMES = "messagelist_show_correspondent_names";
    private static final String PREFERENCE_MESSAGELIST_SHOW_CONTACT_NAME = "messagelist_show_contact_name";
    private static final String PREFERENCE_MESSAGELIST_CONTACT_NAME_COLOR = "messagelist_contact_name_color";
    private static final String PREFERENCE_MESSAGELIST_SHOW_CONTACT_PICTURE = "messagelist_show_contact_picture";
    private static final String PREFERENCE_MESSAGELIST_COLORIZE_MISSING_CONTACT_PICTURES
            = "messagelist_colorize_missing_contact_pictures";
    private static final String PREFERENCE_MESSAGEVIEW_FIXEDWIDTH = "messageview_fixedwidth_font";
    private static final String PREFERENCE_MESSAGEVIEW_VISIBLE_REFILE_ACTIONS = "messageview_visible_refile_actions";

    private static final String PREFERENCE_MESSAGEVIEW_RETURN_TO_LIST = "messageview_return_to_list";
    private static final String PREFERENCE_MESSAGEVIEW_SHOW_NEXT = "messageview_show_next";
    private static final String PREFERENCE_QUIET_TIME_ENABLED = "quiet_time_enabled";
    private static final String PREFERENCE_DISABLE_NOTIFICATION_DURING_QUIET_TIME
            = "disable_notifications_during_quiet_time";
    private static final String PREFERENCE_QUIET_TIME_STARTS = "quiet_time_starts";
    private static final String PREFERENCE_QUIET_TIME_ENDS = "quiet_time_ends";
    private static final String PREFERENCE_NOTIF_QUICK_DELETE = "notification_quick_delete";
    private static final String PREFERENCE_LOCK_SCREEN_NOTIFICATION_VISIBILITY = "lock_screen_notification_visibility";
    private static final String PREFERENCE_HIDE_USERAGENT = "privacy_hide_useragent";
    private static final String PREFERENCE_HIDE_TIMEZONE = "privacy_hide_timezone";
    private static final String PREFERENCE_HIDE_HOSTNAME_WHEN_CONNECTING = "privacy_hide_hostname_when_connecting";
    private static final String PREFERENCE_OPENPGP_PROVIDER = "openpgp_provider";
    private static final String PREFERENCE_OPENPGP_SUPPORT_SIGN_ONLY = "openpgp_support_sign_only";

    private static final String PREFERENCE_AUTOFIT_WIDTH = "messageview_autofit_width";

    private static final String PREFERENCE_BACKGROUND_OPS = "background_ops";
    private static final String PREFERENCE_DEBUG_LOGGING = "debug_logging";
    private static final String PREFERENCE_SENSITIVE_LOGGING = "sensitive_logging";

    private static final String PREFERENCE_ATTACHMENT_DEF_PATH = "attachment_default_path";
    private static final String PREFERENCE_BACKGROUND_AS_UNREAD_INDICATOR = "messagelist_background_as_unread_indicator";
    private static final String PREFERENCE_THREADED_VIEW = "threaded_view";
    private static final String PREFERENCE_FOLDERLIST_WRAP_NAME = "folderlist_wrap_folder_name";
    private static final String PREFERENCE_SPLITVIEW_MODE = "splitview_mode";

    private static final String APG_PROVIDER_PLACEHOLDER = "apg-placeholder";

    private static final int ACTIVITY_CHOOSE_FOLDER = 1;

    private static final int DIALOG_APG_DEPRECATION_WARNING = 1;
    private static final int VISIBLE_REFILE_ACTIONS_DELETE = 0;
    private static final int VISIBLE_REFILE_ACTIONS_ARCHIVE = 1;
    private static final int VISIBLE_REFILE_ACTIONS_MOVE = 2;
    private static final int VISIBLE_REFILE_ACTIONS_COPY = 3;
    private static final int VISIBLE_REFILE_ACTIONS_SPAM = 4;

    private ListPreference mLanguage;
    private ListPreference mTheme;
    private CheckBoxPreference mFixedMessageTheme;
    private ListPreference mMessageTheme;
    private ListPreference mComposerTheme;
    private CheckBoxPreference mAnimations;
    private CheckBoxPreference mGestures;
    private CheckBoxListPreference mVolumeNavigation;
    private CheckBoxPreference mStartIntegratedInbox;
    private CheckBoxListPreference mConfirmActions;
    private ListPreference mNotificationHideSubject;
    private CheckBoxPreference mMeasureAccounts;
    private CheckBoxPreference mCountSearch;
    private CheckBoxPreference mHideSpecialAccounts;
    private ListPreference mPreviewLines;
    private CheckBoxPreference mSenderAboveSubject;
    private CheckBoxPreference mCheckboxes;
    private CheckBoxPreference mStars;
    private CheckBoxPreference mShowCorrespondentNames;
    private CheckBoxPreference mShowContactName;
    private CheckBoxPreference mChangeContactNameColor;
    private CheckBoxPreference mShowContactPicture;
    private CheckBoxPreference mColorizeMissingContactPictures;
    private CheckBoxPreference mFixedWidth;
    private CheckBoxPreference mReturnToList;
    private CheckBoxPreference mShowNext;
    private CheckBoxPreference mAutofitWidth;
    private ListPreference mBackgroundOps;
    private CheckBoxPreference mDebugLogging;
    private CheckBoxPreference mSensitiveLogging;
    private CheckBoxPreference mHideUserAgent;
    private CheckBoxPreference mHideTimeZone;
    private CheckBoxPreference mHideHostnameWhenConnecting;
    private CheckBoxPreference mWrapFolderNames;
    private CheckBoxListPreference mVisibleRefileActions;

    private OpenPgpAppPreference mOpenPgpProvider;
    private CheckBoxPreference mOpenPgpSupportSignOnly;

    private CheckBoxPreference mQuietTimeEnabled;
    private CheckBoxPreference mDisableNotificationDuringQuietTime;
    private org.atalk.xryptomail.preferences.TimePickerPreference mQuietTimeStarts;
    private org.atalk.xryptomail.preferences.TimePickerPreference mQuietTimeEnds;
    private ListPreference mNotificationQuickDelete;
    private ListPreference mLockScreenNotificationVisibility;
    private Preference mAttachmentPathPreference;

    private CheckBoxPreference mBackgroundAsUnreadIndicator;
    private CheckBoxPreference mThreadedView;
    private ListPreference mSplitViewMode;


    public static void actionPrefs(Context context)
    {
        Intent i = new Intent(context, Prefs.class);
        context.startActivity(i);
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        getActionBar().setTitle(R.string.prefs_title);
        addPreferencesFromResource(R.xml.global_preferences);

        mLanguage = (ListPreference) findPreference(PREFERENCE_LANGUAGE);
        List<CharSequence> entryVector = new ArrayList<>(Arrays.asList(mLanguage.getEntries()));
        List<CharSequence> entryValueVector = new ArrayList<>(Arrays.asList(mLanguage.getEntryValues()));
        String supportedLanguages[] = getResources().getStringArray(R.array.supported_languages);
        Set<String> supportedLanguageSet = new HashSet<>(Arrays.asList(supportedLanguages));
        for (int i = entryVector.size() - 1; i > -1; --i) {
            if (!supportedLanguageSet.contains(entryValueVector.get(i))) {
                entryVector.remove(i);
                entryValueVector.remove(i);
            }
        }
        initListPreference(mLanguage, XryptoMail.getXMLanguage(),
                entryVector.toArray(EMPTY_CHAR_SEQUENCE_ARRAY),
                entryValueVector.toArray(EMPTY_CHAR_SEQUENCE_ARRAY));

        mTheme = setupListPreference(PREFERENCE_THEME, themeIdToName(XryptoMail.getXMTheme()));
        mFixedMessageTheme = (CheckBoxPreference) findPreference(PREFERENCE_FIXED_MESSAGE_THEME);
        mFixedMessageTheme.setChecked(XryptoMail.useFixedMessageViewTheme());
        mMessageTheme = setupListPreference(PREFERENCE_MESSAGE_VIEW_THEME,
                themeIdToName(XryptoMail.getXMMessageViewThemeSetting()));
        mComposerTheme = setupListPreference(PREFERENCE_COMPOSER_THEME,
                themeIdToName(XryptoMail.getXMComposerThemeSetting()));

        findPreference(PREFERENCE_FONT_SIZE).setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener()
                {
                    public boolean onPreferenceClick(Preference preference)
                    {
                        onFontSizeSettings();
                        return true;
                    }
                });

        mAnimations = (CheckBoxPreference) findPreference(PREFERENCE_ANIMATIONS);
        mAnimations.setChecked(XryptoMail.showAnimations());

        mGestures = (CheckBoxPreference) findPreference(PREFERENCE_GESTURES);
        mGestures.setChecked(XryptoMail.gesturesEnabled());

        mVolumeNavigation = (CheckBoxListPreference) findPreference(PREFERENCE_VOLUME_NAVIGATION);
        mVolumeNavigation.setItems(new CharSequence[]{getString(R.string.volume_navigation_message), getString(R.string.volume_navigation_list)});
        mVolumeNavigation.setCheckedItems(new boolean[]{XryptoMail.useVolumeKeysForNavigationEnabled(), XryptoMail.useVolumeKeysForListNavigationEnabled()});

        mStartIntegratedInbox = (CheckBoxPreference) findPreference(PREFERENCE_START_INTEGRATED_INBOX);
        mStartIntegratedInbox.setChecked(XryptoMail.startIntegratedInbox());

        mConfirmActions = (CheckBoxListPreference) findPreference(PREFERENCE_CONFIRM_ACTIONS);
        boolean canDeleteFromNotification = NotificationController.platformSupportsExtendedNotifications();
        CharSequence[] confirmActionEntries = new CharSequence[canDeleteFromNotification ? 6 : 5];
        boolean[] confirmActionValues = new boolean[confirmActionEntries.length];

        int index = 0;
        confirmActionEntries[index] = getString(R.string.global_settings_confirm_action_delete);
        confirmActionValues[index++] = XryptoMail.confirmDelete();
        confirmActionEntries[index] = getString(R.string.global_settings_confirm_action_delete_starred);
        confirmActionValues[index++] = XryptoMail.confirmDeleteStarred();
        if (canDeleteFromNotification) {
            confirmActionEntries[index] = getString(R.string.global_settings_confirm_action_delete_notif);
            confirmActionValues[index++] = XryptoMail.confirmDeleteFromNotification();
        }
        confirmActionEntries[index] = getString(R.string.global_settings_confirm_action_spam);
        confirmActionValues[index++] = XryptoMail.confirmSpam();
        confirmActionEntries[index] = getString(R.string.global_settings_confirm_menu_discard);
        confirmActionValues[index++] = XryptoMail.confirmDiscardMessage();
        confirmActionEntries[index] = getString(R.string.global_settings_confirm_menu_mark_all_read);
        confirmActionValues[index] = confirmMarkAllRead();
        mConfirmActions.setItems(confirmActionEntries);
        mConfirmActions.setCheckedItems(confirmActionValues);

        mNotificationHideSubject = setupListPreference(PREFERENCE_NOTIFICATION_HIDE_SUBJECT,
                XryptoMail.getNotificationHideSubject().toString());

        mMeasureAccounts = (CheckBoxPreference) findPreference(PREFERENCE_MEASURE_ACCOUNTS);
        mMeasureAccounts.setChecked(XryptoMail.measureAccounts());

        mCountSearch = (CheckBoxPreference) findPreference(PREFERENCE_COUNT_SEARCH);
        mCountSearch.setChecked(XryptoMail.countSearchMessages());

        mHideSpecialAccounts = (CheckBoxPreference) findPreference(PREFERENCE_HIDE_SPECIAL_ACCOUNTS);
        mHideSpecialAccounts.setChecked(XryptoMail.isHideSpecialAccounts());

        mPreviewLines = setupListPreference(PREFERENCE_MESSAGELIST_PREVIEW_LINES,
                Integer.toString(XryptoMail.messageListPreviewLines()));

        mSenderAboveSubject = (CheckBoxPreference) findPreference(PREFERENCE_MESSAGELIST_SENDER_ABOVE_SUBJECT);
        mSenderAboveSubject.setChecked(XryptoMail.messageListSenderAboveSubject());
        mCheckboxes = (CheckBoxPreference) findPreference(PREFERENCE_MESSAGELIST_CHECKBOXES);
        mCheckboxes.setChecked(XryptoMail.messageListCheckboxes());

        mStars = (CheckBoxPreference) findPreference(PREFERENCE_MESSAGELIST_STARS);
        mStars.setChecked(XryptoMail.messageListStars());

        mShowCorrespondentNames = (CheckBoxPreference) findPreference(PREFERENCE_MESSAGELIST_SHOW_CORRESPONDENT_NAMES);
        mShowCorrespondentNames.setChecked(XryptoMail.showCorrespondentNames());

        mShowContactName = (CheckBoxPreference) findPreference(PREFERENCE_MESSAGELIST_SHOW_CONTACT_NAME);
        mShowContactName.setChecked(XryptoMail.showContactName());

        mShowContactPicture = (CheckBoxPreference) findPreference(PREFERENCE_MESSAGELIST_SHOW_CONTACT_PICTURE);
        mShowContactPicture.setChecked(XryptoMail.showContactPicture());

        mColorizeMissingContactPictures = (CheckBoxPreference) findPreference(PREFERENCE_MESSAGELIST_COLORIZE_MISSING_CONTACT_PICTURES);
        mColorizeMissingContactPictures.setChecked(XryptoMail.isColorizeMissingContactPictures());

        mBackgroundAsUnreadIndicator = (CheckBoxPreference) findPreference(PREFERENCE_BACKGROUND_AS_UNREAD_INDICATOR);
        mBackgroundAsUnreadIndicator.setChecked(XryptoMail.useBackgroundAsUnreadIndicator());
        mChangeContactNameColor = (CheckBoxPreference) findPreference(PREFERENCE_MESSAGELIST_CONTACT_NAME_COLOR);
        mChangeContactNameColor.setChecked(XryptoMail.changeContactNameColor());

        mThreadedView = (CheckBoxPreference) findPreference(PREFERENCE_THREADED_VIEW);
        mThreadedView.setChecked(XryptoMail.isThreadedViewEnabled());

        if (XryptoMail.changeContactNameColor()) {
            mChangeContactNameColor.setSummary(R.string.global_settings_registered_name_color_changed);
        }
        else {
            mChangeContactNameColor.setSummary(R.string.global_settings_registered_name_color_default);
        }

        mChangeContactNameColor.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
        {
            public boolean onPreferenceChange(Preference preference, Object newValue)
            {
                final Boolean checked = (Boolean) newValue;
                if (checked) {
                    onChooseContactNameColor();
                    mChangeContactNameColor.setSummary(R.string.global_settings_registered_name_color_changed);
                }
                else {
                    mChangeContactNameColor.setSummary(R.string.global_settings_registered_name_color_default);
                }
                mChangeContactNameColor.setChecked(checked);
                return false;
            }
        });

        mFixedWidth = (CheckBoxPreference) findPreference(PREFERENCE_MESSAGEVIEW_FIXEDWIDTH);
        mFixedWidth.setChecked(XryptoMail.messageViewFixedWidthFont());

        mReturnToList = (CheckBoxPreference) findPreference(PREFERENCE_MESSAGEVIEW_RETURN_TO_LIST);
        mReturnToList.setChecked(XryptoMail.messageViewReturnToList());

        mShowNext = (CheckBoxPreference) findPreference(PREFERENCE_MESSAGEVIEW_SHOW_NEXT);
        mShowNext.setChecked(XryptoMail.messageViewShowNext());

        mAutofitWidth = (CheckBoxPreference) findPreference(PREFERENCE_AUTOFIT_WIDTH);
        mAutofitWidth.setChecked(XryptoMail.autofitWidth());

        mQuietTimeEnabled = (CheckBoxPreference) findPreference(PREFERENCE_QUIET_TIME_ENABLED);
        mQuietTimeEnabled.setChecked(XryptoMail.getQuietTimeEnabled());

        mDisableNotificationDuringQuietTime = (CheckBoxPreference) findPreference(
                PREFERENCE_DISABLE_NOTIFICATION_DURING_QUIET_TIME);
        mDisableNotificationDuringQuietTime.setChecked(!XryptoMail.isNotificationDuringQuietTimeEnabled());

        mQuietTimeStarts = (TimePickerPreference) findPreference(PREFERENCE_QUIET_TIME_STARTS);
        mQuietTimeStarts.setDefaultValue(XryptoMail.getQuietTimeStarts());
        mQuietTimeStarts.setSummary(XryptoMail.getQuietTimeStarts());
        mQuietTimeStarts.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
        {
            public boolean onPreferenceChange(Preference preference, Object newValue)
            {
                final String time = (String) newValue;
                mQuietTimeStarts.setSummary(time);
                return false;
            }
        });

        mQuietTimeEnds = (TimePickerPreference) findPreference(PREFERENCE_QUIET_TIME_ENDS);
        mQuietTimeEnds.setSummary(XryptoMail.getQuietTimeEnds());
        mQuietTimeEnds.setDefaultValue(XryptoMail.getQuietTimeEnds());
        mQuietTimeEnds.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
        {
            public boolean onPreferenceChange(Preference preference, Object newValue)
            {
                final String time = (String) newValue;
                mQuietTimeEnds.setSummary(time);
                return false;
            }
        });

        mNotificationQuickDelete = setupListPreference(PREFERENCE_NOTIF_QUICK_DELETE,
                XryptoMail.getNotificationQuickDeleteBehaviour().toString());
        if (!NotificationController.platformSupportsExtendedNotifications()) {
            PreferenceScreen prefs = (PreferenceScreen) findPreference("notification_preferences");
            prefs.removePreference(mNotificationQuickDelete);
            mNotificationQuickDelete = null;
        }

        mLockScreenNotificationVisibility = setupListPreference(PREFERENCE_LOCK_SCREEN_NOTIFICATION_VISIBILITY,
                XryptoMail.getLockScreenNotificationVisibility().toString());
        if (!NotificationController.platformSupportsLockScreenNotifications()) {
            ((PreferenceScreen) findPreference("notification_preferences"))
                    .removePreference(mLockScreenNotificationVisibility);
            mLockScreenNotificationVisibility = null;
        }

        mBackgroundOps = setupListPreference(PREFERENCE_BACKGROUND_OPS, XryptoMail.getBackgroundOps().name());

        // cmeng - hide debug option from released version
        mDebugLogging = (CheckBoxPreference) findPreference(PREFERENCE_DEBUG_LOGGING);
        mSensitiveLogging = (CheckBoxPreference) findPreference(PREFERENCE_SENSITIVE_LOGGING);
        if (BuildConfig.DEBUG) {
            mDebugLogging.setChecked(XryptoMail.isDebug());
            mSensitiveLogging.setChecked(XryptoMail.DEBUG_SENSITIVE);
        }
        else {
            PreferenceScreen mPsDebug = getPreferenceScreen();
            Preference prefDebug = getPreferenceManager().findPreference("debug_preferences");
            mPsDebug.removePreference(prefDebug);
        }

        mHideUserAgent = (CheckBoxPreference) findPreference(PREFERENCE_HIDE_USERAGENT);
        mHideTimeZone = (CheckBoxPreference) findPreference(PREFERENCE_HIDE_TIMEZONE);
        mHideHostnameWhenConnecting = (CheckBoxPreference) findPreference(PREFERENCE_HIDE_HOSTNAME_WHEN_CONNECTING);
        mHideUserAgent.setChecked(XryptoMail.hideUserAgent());
        mHideTimeZone.setChecked(XryptoMail.hideTimeZone());
        mHideHostnameWhenConnecting.setChecked(XryptoMail.hideHostnameWhenConnecting());

        mOpenPgpProvider = (OpenPgpAppPreference) findPreference(PREFERENCE_OPENPGP_PROVIDER);
        mOpenPgpProvider.setValue(XryptoMail.getOpenPgpProvider());
        if (OpenPgpAppPreference.isApgInstalled(getApplicationContext())) {
            mOpenPgpProvider.addLegacyProvider(
                    APG_PROVIDER_PLACEHOLDER, getString(R.string.apg), R.drawable.ic_apg_small);
        }
        mOpenPgpProvider.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
        {
            public boolean onPreferenceChange(Preference preference, Object newValue)
            {
                String value = newValue.toString();
                if (APG_PROVIDER_PLACEHOLDER.equals(value)) {
                    mOpenPgpProvider.setValue("");
                    showDialog(DIALOG_APG_DEPRECATION_WARNING);
                }
                else {
                    mOpenPgpProvider.setValue(value);
                }
                return false;
            }
        });

        mOpenPgpSupportSignOnly = (CheckBoxPreference) findPreference(PREFERENCE_OPENPGP_SUPPORT_SIGN_ONLY);
        mOpenPgpSupportSignOnly.setChecked(XryptoMail.getOpenPgpSupportSignOnly());

        mAttachmentPathPreference = findPreference(PREFERENCE_ATTACHMENT_DEF_PATH);
        mAttachmentPathPreference.setSummary(XryptoMail.getAttachmentDefaultPath());
        mAttachmentPathPreference.setOnPreferenceClickListener(new OnPreferenceClickListener()
        {
            @Override
            public boolean onPreferenceClick(Preference preference)
            {
                FileBrowserHelper.getInstance().showFileBrowserActivity(
                        Prefs.this,
                        new File(XryptoMail.getAttachmentDefaultPath()),
                        ACTIVITY_CHOOSE_FOLDER, callback);
                return true;
            }

            FileBrowserFailOverCallback callback = new FileBrowserFailOverCallback()
            {
                @Override
                public void onPathEntered(String path)
                {
                    mAttachmentPathPreference.setSummary(path);
                    XryptoMail.setAttachmentDefaultPath(path);
                }

                @Override
                public void onCancel()
                {
                    // canceled, do nothing
                }
            };
        });

        mWrapFolderNames = (CheckBoxPreference) findPreference(PREFERENCE_FOLDERLIST_WRAP_NAME);
        mWrapFolderNames.setChecked(XryptoMail.wrapFolderNames());

        mVisibleRefileActions = (CheckBoxListPreference) findPreference(PREFERENCE_MESSAGEVIEW_VISIBLE_REFILE_ACTIONS);
        CharSequence[] visibleRefileActionsEntries = new CharSequence[5];
        visibleRefileActionsEntries[VISIBLE_REFILE_ACTIONS_DELETE] = getString(R.string.delete_action);
        visibleRefileActionsEntries[VISIBLE_REFILE_ACTIONS_ARCHIVE] = getString(R.string.archive_action);
        visibleRefileActionsEntries[VISIBLE_REFILE_ACTIONS_MOVE] = getString(R.string.move_action);
        visibleRefileActionsEntries[VISIBLE_REFILE_ACTIONS_COPY] = getString(R.string.copy_action);
        visibleRefileActionsEntries[VISIBLE_REFILE_ACTIONS_SPAM] = getString(R.string.spam_action);

        boolean[] visibleRefileActionsValues = new boolean[5];
        visibleRefileActionsValues[VISIBLE_REFILE_ACTIONS_DELETE] = XryptoMail.isMessageViewDeleteActionVisible();
        visibleRefileActionsValues[VISIBLE_REFILE_ACTIONS_ARCHIVE] = XryptoMail.isMessageViewArchiveActionVisible();
        visibleRefileActionsValues[VISIBLE_REFILE_ACTIONS_MOVE] = XryptoMail.isMessageViewMoveActionVisible();
        visibleRefileActionsValues[VISIBLE_REFILE_ACTIONS_COPY] = XryptoMail.isMessageViewCopyActionVisible();
        visibleRefileActionsValues[VISIBLE_REFILE_ACTIONS_SPAM] = XryptoMail.isMessageViewSpamActionVisible();

        mVisibleRefileActions.setItems(visibleRefileActionsEntries);
        mVisibleRefileActions.setCheckedItems(visibleRefileActionsValues);

        mSplitViewMode = (ListPreference) findPreference(PREFERENCE_SPLITVIEW_MODE);
        initListPreference(mSplitViewMode, XryptoMail.getSplitViewMode().name(),
                mSplitViewMode.getEntries(), mSplitViewMode.getEntryValues());
    }

    private static String themeIdToName(XryptoMail.Theme theme)
    {
        switch (theme) {
            case DARK:
                return "dark";
            case USE_GLOBAL:
                return "global";
            default:
                return "light";
        }
    }

    private static XryptoMail.Theme themeNameToId(String theme)
    {
        if (TextUtils.equals(theme, "dark")) {
            return XryptoMail.Theme.DARK;
        }
        else if (TextUtils.equals(theme, "global")) {
            return XryptoMail.Theme.USE_GLOBAL;
        }
        else {
            return XryptoMail.Theme.LIGHT;
        }
    }

    private void saveSettings()
    {
        Storage storage = Preferences.getPreferences(this).getStorage();

        XryptoMail.setXMLanguage(mLanguage.getValue());
        XryptoMail.setXMTheme(themeNameToId(mTheme.getValue()));
        XryptoMail.setUseFixedMessageViewTheme(mFixedMessageTheme.isChecked());
        XryptoMail.setXMMessageViewThemeSetting(themeNameToId(mMessageTheme.getValue()));
        XryptoMail.setXMComposerThemeSetting(themeNameToId(mComposerTheme.getValue()));
        XryptoMail.setAnimations(mAnimations.isChecked());
        XryptoMail.setGesturesEnabled(mGestures.isChecked());
        XryptoMail.setUseVolumeKeysForNavigation(mVolumeNavigation.getCheckedItems()[0]);
        XryptoMail.setUseVolumeKeysForListNavigation(mVolumeNavigation.getCheckedItems()[1]);
        XryptoMail.setStartIntegratedInbox(!mHideSpecialAccounts.isChecked() && mStartIntegratedInbox.isChecked());
        XryptoMail.setNotificationHideSubject(NotificationHideSubject.valueOf(mNotificationHideSubject.getValue()));

        int index = 0;
        XryptoMail.setConfirmDelete(mConfirmActions.getCheckedItems()[index++]);
        XryptoMail.setConfirmDeleteStarred(mConfirmActions.getCheckedItems()[index++]);
        if (NotificationController.platformSupportsExtendedNotifications()) {
            XryptoMail.setConfirmDeleteFromNotification(mConfirmActions.getCheckedItems()[index++]);
        }
        XryptoMail.setConfirmSpam(mConfirmActions.getCheckedItems()[index++]);
        XryptoMail.setConfirmDiscardMessage(mConfirmActions.getCheckedItems()[index++]);
        XryptoMail.setConfirmMarkAllRead(mConfirmActions.getCheckedItems()[index]);

        XryptoMail.setMeasureAccounts(mMeasureAccounts.isChecked());
        XryptoMail.setCountSearchMessages(mCountSearch.isChecked());
        XryptoMail.setHideSpecialAccounts(mHideSpecialAccounts.isChecked());
        XryptoMail.setMessageListPreviewLines(Integer.parseInt(mPreviewLines.getValue()));
        XryptoMail.setMessageListCheckboxes(mCheckboxes.isChecked());
        XryptoMail.setMessageListStars(mStars.isChecked());
        XryptoMail.setShowCorrespondentNames(mShowCorrespondentNames.isChecked());
        XryptoMail.setMessageListSenderAboveSubject(mSenderAboveSubject.isChecked());
        XryptoMail.setShowContactName(mShowContactName.isChecked());
        XryptoMail.setShowContactPicture(mShowContactPicture.isChecked());
        XryptoMail.setColorizeMissingContactPictures(mColorizeMissingContactPictures.isChecked());
        XryptoMail.setUseBackgroundAsUnreadIndicator(mBackgroundAsUnreadIndicator.isChecked());
        XryptoMail.setThreadedViewEnabled(mThreadedView.isChecked());
        XryptoMail.setChangeContactNameColor(mChangeContactNameColor.isChecked());
        XryptoMail.setMessageViewFixedWidthFont(mFixedWidth.isChecked());
        XryptoMail.setMessageViewReturnToList(mReturnToList.isChecked());
        XryptoMail.setMessageViewShowNext(mShowNext.isChecked());
        XryptoMail.setAutofitWidth(mAutofitWidth.isChecked());
        XryptoMail.setQuietTimeEnabled(mQuietTimeEnabled.isChecked());

        boolean[] enabledRefileActions = mVisibleRefileActions.getCheckedItems();
        XryptoMail.setMessageViewDeleteActionVisible(enabledRefileActions[VISIBLE_REFILE_ACTIONS_DELETE]);
        XryptoMail.setMessageViewArchiveActionVisible(enabledRefileActions[VISIBLE_REFILE_ACTIONS_ARCHIVE]);
        XryptoMail.setMessageViewMoveActionVisible(enabledRefileActions[VISIBLE_REFILE_ACTIONS_MOVE]);
        XryptoMail.setMessageViewCopyActionVisible(enabledRefileActions[VISIBLE_REFILE_ACTIONS_COPY]);
        XryptoMail.setMessageViewSpamActionVisible(enabledRefileActions[VISIBLE_REFILE_ACTIONS_SPAM]);

        XryptoMail.setNotificationDuringQuietTimeEnabled(!mDisableNotificationDuringQuietTime.isChecked());
        XryptoMail.setQuietTimeStarts(mQuietTimeStarts.getTime());
        XryptoMail.setQuietTimeEnds(mQuietTimeEnds.getTime());
        XryptoMail.setWrapFolderNames(mWrapFolderNames.isChecked());

        if (mNotificationQuickDelete != null) {
            XryptoMail.setNotificationQuickDeleteBehaviour(
                    NotificationQuickDelete.valueOf(mNotificationQuickDelete.getValue()));
        }

        if (mLockScreenNotificationVisibility != null) {
            XryptoMail.setLockScreenNotificationVisibility(
                    XryptoMail.LockScreenNotificationVisibility.valueOf(mLockScreenNotificationVisibility.getValue()));
        }
        XryptoMail.setSplitViewMode(SplitViewMode.valueOf(mSplitViewMode.getValue()));

        XryptoMail.setAttachmentDefaultPath(mAttachmentPathPreference.getSummary().toString());
        boolean needsRefresh = XryptoMail.setBackgroundOps(mBackgroundOps.getValue());

        if (!XryptoMail.isDebug() && mDebugLogging.isChecked()) {
            Toast.makeText(this, R.string.debug_logging_enabled, Toast.LENGTH_LONG).show();
        }
        XryptoMail.setDebug(mDebugLogging.isChecked());
        XryptoMail.DEBUG_SENSITIVE = mSensitiveLogging.isChecked();

        XryptoMail.setHideUserAgent(mHideUserAgent.isChecked());
        XryptoMail.setHideTimeZone(mHideTimeZone.isChecked());
        XryptoMail.setHideHostnameWhenConnecting(mHideHostnameWhenConnecting.isChecked());

        XryptoMail.setOpenPgpProvider(mOpenPgpProvider.getValue());
        XryptoMail.setOpenPgpSupportSignOnly(mOpenPgpSupportSignOnly.isChecked());

        StorageEditor editor = storage.edit();
        XryptoMail.save(editor);
        editor.commit();

        if (needsRefresh) {
            MailService.actionReset(this);
        }
    }

    @Override
    protected void onPause()
    {
        saveSettings();
        super.onPause();
    }

    private void onFontSizeSettings()
    {
        FontSizeSettings.actionEditSettings(this);
    }

    private void onChooseContactNameColor()
    {
        new ColorPickerDialog(this, new ColorPickerDialog.OnColorChangedListener()
        {
            public void colorChanged(int color)
            {
                XryptoMail.setContactNameColor(color);
            }
        },
                XryptoMail.getContactNameColor()).show();
    }

    @Override
    protected Dialog onCreateDialog(int id)
    {
        Dialog dialog = null;
        switch (id) {
            case DIALOG_APG_DEPRECATION_WARNING: {
                dialog = new ApgDeprecationWarningDialog(this);
                dialog.setOnCancelListener(dialog1 -> mOpenPgpProvider.show());
                break;
            }

        }
        return dialog;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        switch (requestCode) {
            case ACTIVITY_CHOOSE_FOLDER:
                if (resultCode == RESULT_OK && data != null) {
                    // obtain the filename
                    Uri fileUri = data.getData();
                    if (fileUri != null) {
                        String filePath = fileUri.getPath();
                        if (filePath != null) {
                            mAttachmentPathPreference.setSummary(filePath);
                            XryptoMail.setAttachmentDefaultPath(filePath);
                        }
                    }
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}
