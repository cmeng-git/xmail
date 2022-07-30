package org.atalk.xryptomail.preferences;

import android.os.Environment;

import org.atalk.xryptomail.*;
import org.atalk.xryptomail.Account.SortType;
import org.atalk.xryptomail.XryptoMail.*;
import org.atalk.xryptomail.preferences.Settings.*;

import java.io.File;
import java.util.*;

public class GlobalSettings {
    static final Map<String, TreeMap<Integer, SettingsDescription>> SETTINGS;
    private static final Map<Integer, SettingsUpgrader> UPGRADERS;

    static {
        Map<String, TreeMap<Integer, SettingsDescription>> s = new LinkedHashMap<>();

        /**
         * When adding new settings here, be sure to increment {@link Settings.VERSION}
         * and use that for whatever you add here.
         */
        s.put("animations", Settings.versions(
                new V(1, new BooleanSetting(false))
            ));
        s.put("attachmentdefaultpath", Settings.versions(
                new V(1, new DirectorySetting(Environment.getExternalStorageDirectory())),
                new V(41, new DirectorySetting(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS)))
            ));
        s.put("backgroundOperations", Settings.versions(
                new V(1, new EnumSetting<>(
                        XryptoMail.BACKGROUND_OPS.class, XryptoMail.BACKGROUND_OPS.WHEN_CHECKED_AUTO_SYNC))
            ));
        s.put("changeRegisteredNameColor", Settings.versions(
                new V(1, new BooleanSetting(false))
            ));
        s.put("confirmDelete", Settings.versions(
                new V(1, new BooleanSetting(false))
            ));
        s.put("confirmDeleteStarred", Settings.versions(
                new V(2, new BooleanSetting(false))
            ));
        s.put("confirmSpam", Settings.versions(
                new V(1, new BooleanSetting(false))
            ));
        s.put("confirmMarkAllRead", Settings.versions(
                new V(44, new BooleanSetting(true))
        ));
        s.put("countSearchMessages", Settings.versions(
                new V(1, new BooleanSetting(false))
            ));
        s.put("enableDebugLogging", Settings.versions(
                new V(1, new BooleanSetting(false))
            ));
        s.put("enableSensitiveLogging", Settings.versions(
                new V(1, new BooleanSetting(false))
            ));
        s.put("fontSizeAccountDescription", Settings.versions(
                new V(1, new FontSizeSetting(FontSizes.SMALL))
            ));
        s.put("fontSizeAccountName", Settings.versions(
                new V(1, new FontSizeSetting(FontSizes.MEDIUM))
            ));
        s.put("fontSizeFolderName", Settings.versions(
                new V(1, new FontSizeSetting(FontSizes.LARGE))
            ));
        s.put("fontSizeFolderStatus", Settings.versions(
                new V(1, new FontSizeSetting(FontSizes.SMALL))
            ));
        s.put("fontSizeMessageComposeInput", Settings.versions(
                new V(5, new FontSizeSetting(FontSizes.MEDIUM))
            ));
        s.put("fontSizeMessageListDate", Settings.versions(
                new V(1, new FontSizeSetting(FontSizes.SMALL))
            ));
        s.put("fontSizeMessageListPreview", Settings.versions(
                new V(1, new FontSizeSetting(FontSizes.SMALL))
            ));
        s.put("fontSizeMessageListSender", Settings.versions(
                new V(1, new FontSizeSetting(FontSizes.SMALL))
            ));
        s.put("fontSizeMessageListSubject", Settings.versions(
                new V(1, new FontSizeSetting(FontSizes.FONT_16SP))
            ));
        s.put("fontSizeMessageViewAdditionalHeaders", Settings.versions(
                new V(1, new FontSizeSetting(FontSizes.FONT_12SP))
            ));
        s.put("fontSizeMessageViewCC", Settings.versions(
                new V(1, new FontSizeSetting(FontSizes.FONT_12SP))
            ));
        s.put("fontSizeMessageViewContent", Settings.versions(
                new V(1, new WebFontSizeSetting(3)),
                new V(31, null)
            ));
        s.put("fontSizeMessageViewDate", Settings.versions(
                new V(1, new FontSizeSetting(FontSizes.FONT_10SP))
            ));
        s.put("fontSizeMessageViewSender", Settings.versions(
                new V(1, new FontSizeSetting(FontSizes.SMALL))
            ));
        s.put("fontSizeMessageViewSubject", Settings.versions(
                new V(1, new FontSizeSetting(FontSizes.FONT_12SP))
            ));
        s.put("fontSizeMessageViewTime", Settings.versions(
                new V(1, new FontSizeSetting(FontSizes.FONT_10SP))
            ));
        s.put("fontSizeMessageViewTo", Settings.versions(
                new V(1, new FontSizeSetting(FontSizes.FONT_12SP))
            ));
        s.put("gesturesEnabled", Settings.versions(
                new V(1, new BooleanSetting(true)),
                new V(4, new BooleanSetting(false))
            ));
        s.put("hideSpecialAccounts", Settings.versions(
                new V(1, new BooleanSetting(false))
            ));
        s.put("keyguardPrivacy", Settings.versions(
                new V(1, new BooleanSetting(false)),
                new V(12, null)
            ));
        s.put("language", Settings.versions(
               new V(1, new LanguageSetting())
            ));
        s.put("measureAccounts", Settings.versions(
                new V(1, new BooleanSetting(true))
            ));
        s.put("messageListCheckboxes", Settings.versions(
                new V(1, new BooleanSetting(false))
            ));
        s.put("messageListPreviewLines", Settings.versions(
                new V(1, new IntegerRangeSetting(1, 100, 2))
            ));
        s.put("messageListStars", Settings.versions(
                new V(1, new BooleanSetting(true))
            ));
        s.put("messageViewFixedWidthFont", Settings.versions(
                new V(1, new BooleanSetting(false))
            ));
        s.put("messageViewReturnToList", Settings.versions(
                new V(1, new BooleanSetting(false))
            ));
        s.put("messageViewShowNext", Settings.versions(
                new V(1, new BooleanSetting(true))
            ));
        s.put("quietTimeEnabled", Settings.versions(
                new V(1, new BooleanSetting(true))
            ));
        s.put("quietTimeEnds", Settings.versions(
                new V(1, new TimeSetting("7:00"))
            ));
        s.put("quietTimeStarts", Settings.versions(
                new V(1, new TimeSetting("23:00"))
            ));
        s.put("registeredNameColor", Settings.versions(
                new V(1, new ColorSetting(0xFF00008F))
            ));
        s.put("showContactName", Settings.versions(
                new V(1, new BooleanSetting(false))
            ));
        s.put("showCorrespondentNames", Settings.versions(
                new V(1, new BooleanSetting(true))
            ));
        s.put("sortTypeEnum", Settings.versions(
                new V(10, new EnumSetting<>(SortType.class, Account.DEFAULT_SORT_TYPE))
            ));
        s.put("sortAscending", Settings.versions(
                new V(10, new BooleanSetting(Account.DEFAULT_SORT_ASCENDING))
            ));
        s.put("startIntegratedInbox", Settings.versions(
                new V(1, new BooleanSetting(false))
            ));
        s.put("theme", Settings.versions(
                new V(1, new ThemeSetting(XryptoMail.Theme.LIGHT))
            ));
        s.put("messageViewTheme", Settings.versions(
                new V(16, new ThemeSetting(XryptoMail.Theme.LIGHT)),
                new V(24, new SubThemeSetting(XryptoMail.Theme.USE_GLOBAL))
            ));
        s.put("useVolumeKeysForListNavigation", Settings.versions(
                new V(1, new BooleanSetting(false))
            ));
        s.put("useVolumeKeysForNavigation", Settings.versions(
                new V(1, new BooleanSetting(false))
            ));
        s.put("wrapFolderNames", Settings.versions(
                new V(22, new BooleanSetting(false))
            ));
        s.put("notificationHideSubject", Settings.versions(
                new V(12, new EnumSetting<>(NotificationHideSubject.class, NotificationHideSubject.NEVER))
            ));
        s.put("useBackgroundAsUnreadIndicator", Settings.versions(
                new V(19, new BooleanSetting(true))
            ));
        s.put("threadedView", Settings.versions(
                new V(20, new BooleanSetting(true))
            ));
        s.put("splitViewMode", Settings.versions(
                new V(23, new EnumSetting<>(SplitViewMode.class, SplitViewMode.NEVER))
            ));
        s.put("messageComposeTheme", Settings.versions(
                new V(24, new SubThemeSetting(XryptoMail.Theme.USE_GLOBAL))
            ));
        s.put("fixedMessageViewTheme", Settings.versions(
                new V(24, new BooleanSetting(true))
            ));
        s.put("showContactPicture", Settings.versions(
                new V(25, new BooleanSetting(true))
            ));
        s.put("autofitWidth", Settings.versions(
                new V(28, new BooleanSetting(true))
            ));
        s.put("colorizeMissingContactPictures", Settings.versions(
                new V(29, new BooleanSetting(true))
            ));
        s.put("messageViewDeleteActionVisible", Settings.versions(
                new V(30, new BooleanSetting(true))
            ));
        s.put("messageViewArchiveActionVisible", Settings.versions(
                new V(30, new BooleanSetting(false))
            ));
        s.put("messageViewMoveActionVisible", Settings.versions(
                new V(30, new BooleanSetting(false))
            ));
        s.put("messageViewCopyActionVisible", Settings.versions(
                new V(30, new BooleanSetting(false))
            ));
        s.put("messageViewSpamActionVisible", Settings.versions(
                new V(30, new BooleanSetting(false))
            ));
        s.put("fontSizeMessageViewContentPercent", Settings.versions(
                new V(31, new IntegerRangeSetting(40, 250, 100))
            ));
        s.put("hideUserAgent", Settings.versions(
                new V(32, new BooleanSetting(false))
            ));
        s.put("hideTimeZone", Settings.versions(
                new V(32, new BooleanSetting(false))
            ));
        s.put("lockScreenNotificationVisibility", Settings.versions(
                new V(37, new EnumSetting<>(LockScreenNotificationVisibility.class,
                        LockScreenNotificationVisibility.MESSAGE_COUNT))
            ));
        s.put("confirmDeleteFromNotification", Settings.versions(
                new V(38, new BooleanSetting(true))
            ));
        s.put("messageListSenderAboveSubject", Settings.versions(
                new V(38, new BooleanSetting(false))
            ));
        s.put("notificationQuickDelete", Settings.versions(
                new V(38, new EnumSetting<>(NotificationQuickDelete.class, NotificationQuickDelete.NEVER))
            ));
        s.put("notificationDuringQuietTimeEnabled", Settings.versions(
                new V(39, new BooleanSetting(true))
            ));
        s.put("confirmDiscardMessage", Settings.versions(
                new V(40, new BooleanSetting(true))
            ));
        s.put("pgpInlineDialogCounter", Settings.versions(
                new V(43, new IntegerRangeSetting(0, Integer.MAX_VALUE, 0))
        ));
        s.put("pgpSignOnlyDialogCounter", Settings.versions(
                new V(45, new IntegerRangeSetting(0, Integer.MAX_VALUE, 0))
        ));
        s.put("openPgpProvider", Settings.versions(
                new V(46, new StringSetting(XryptoMail.NO_OPENPGP_PROVIDER))
        ));
        s.put("openPgpSupportSignOnly", Settings.versions(
                new V(47, new BooleanSetting(false))
        ));
        s.put("fontSizeMessageViewBCC", Settings.versions(
                new V(48, new FontSizeSetting(FontSizes.FONT_DEFAULT))
        ));
        s.put("hideHostnameWhenConnecting", Settings.versions(
                new V(49, new BooleanSetting(false))
        ));

        SETTINGS = Collections.unmodifiableMap(s);

        Map<Integer, SettingsUpgrader> u = new HashMap<>();
        u.put(12, new SettingsUpgraderV12());
        u.put(24, new SettingsUpgraderV24());
        u.put(31, new SettingsUpgraderV31());

        UPGRADERS = Collections.unmodifiableMap(u);
    }

    static Map<String, Object> validate(int version, Map<String, String> importedSettings) {
        return Settings.validate(version, SETTINGS, importedSettings, false);
    }

    public static Set<String> upgrade(int version, Map<String, Object> validatedSettings) {
        return Settings.upgrade(version, UPGRADERS, SETTINGS, validatedSettings);
    }

    public static Map<String, String> convert(Map<String, Object> settings) {
        return Settings.convert(settings, SETTINGS);
    }

    static Map<String, String> getGlobalSettings(Storage storage) {
        Map<String, String> result = new HashMap<>();
        for (String key : SETTINGS.keySet()) {
            String value = storage.getString(key, null);
            if (value != null) {
                result.put(key, value);
            }
        }
        return result;
    }

    /**
     * Upgrades the settings from version 11 to 12
     *
     * Map the 'keyguardPrivacy' value to the new NotificationHideSubject enum.
     */
    private static class SettingsUpgraderV12 implements SettingsUpgrader {

        @Override
        public Set<String> upgrade(Map<String, Object> settings) {
            Boolean keyguardPrivacy = (Boolean) settings.get("keyguardPrivacy");
            if (keyguardPrivacy != null && keyguardPrivacy) {
                // current setting: only show subject when unlocked
                settings.put("notificationHideSubject", NotificationHideSubject.WHEN_LOCKED);
            } else {
                // always show subject [old default]
                settings.put("notificationHideSubject", NotificationHideSubject.NEVER);
            }
            return new HashSet<>(Collections.singletonList("keyguardPrivacy"));
        }
    }

    /**
     * Upgrades the settings from version 23 to 24.
     *
     * <p>
     * Set <em>messageViewTheme</em> to {@link XryptoMail.Theme#USE_GLOBAL} if
     * <em>messageViewTheme</em> has the same value as <em>theme</em>.
     * </p>
     */
    private static class SettingsUpgraderV24 implements SettingsUpgrader {

        @Override
        public Set<String> upgrade(Map<String, Object> settings) {
            XryptoMail.Theme messageViewTheme = (XryptoMail.Theme) settings.get("messageViewTheme");
            XryptoMail.Theme theme = (XryptoMail.Theme) settings.get("theme");
            if (theme != null && messageViewTheme != null && theme == messageViewTheme) {
                settings.put("messageViewTheme", XryptoMail.Theme.USE_GLOBAL);
            }
            return null;
        }
    }

    /**
     * Upgrades the settings from version 30 to 31.
     *
     * <p>
     * Convert value from <em>fontSizeMessageViewContent</em> to
     * <em>fontSizeMessageViewContentPercent</em>.
     * </p>
     */
    public static class SettingsUpgraderV31 implements SettingsUpgrader {

        @Override
        public Set<String> upgrade(Map<String, Object> settings) {
            int oldSize = (Integer) settings.get("fontSizeMessageViewContent");

            int newSize = convertFromOldSize(oldSize);

            settings.put("fontSizeMessageViewContentPercent", newSize);

            return new HashSet<>(Collections.singletonList("fontSizeMessageViewContent"));
        }

        public static int convertFromOldSize(int oldSize) {
            switch (oldSize) {
                case 1: {
                    return 40;
                }
                case 2: {
                    return 75;
                }
                case 4: {
                    return 175;
                }
                case 5: {
                    return 250;
                }
                case 3:
                default: {
                    return 100;
                }
            }
        }
    }

    /**
     * The language setting.
     *
     * <p>
     * Valid values are read from {@code settings_language_values} in
     * {@code res/values/arrays.xml}.
     * </p>
     */
    private static class LanguageSetting extends PseudoEnumSetting<String> {
        private final Map<String, String> mMapping;

        LanguageSetting() {
            super("");

            Map<String, String> mapping = new HashMap<>();
            String[] values = XryptoMail.mInstance.getResources().getStringArray(R.array.settings_language_values);
            for (String value : values) {
                if (value.length() == 0) {
                    mapping.put("", "default");
                } else {
                    mapping.put(value, value);
                }
            }
            mMapping = Collections.unmodifiableMap(mapping);
        }

        @Override
        protected Map<String, String> getMapping() {
            return mMapping;
        }

        @Override
        public String fromString(String value) throws InvalidSettingValueException {
            if (mMapping.containsKey(value)) {
                return value;
            }
            throw new InvalidSettingValueException();
        }
    }

    /**
     * The theme setting.
     */
    static class ThemeSetting extends SettingsDescription<XryptoMail.Theme> {
        private static final String THEME_LIGHT = "light";
        private static final String THEME_DARK = "dark";

        ThemeSetting(XryptoMail.Theme defaultValue) {
            super(defaultValue);
        }

        @Override
        public XryptoMail.Theme fromString(String value) throws InvalidSettingValueException {
            try {
                Integer theme = Integer.parseInt(value);
                if (theme == XryptoMail.Theme.LIGHT.ordinal() ||
                        // We used to store the resource ID of the theme in the preference storage,
                        // but don't use the database upgrade mechanism to update the values. So
                        // we have to deal with the old format here.
                        theme == android.R.style.Theme_Light) {
                    return XryptoMail.Theme.LIGHT;
                } else if (theme == XryptoMail.Theme.DARK.ordinal() || theme == android.R.style.Theme) {
                    return XryptoMail.Theme.DARK;
                }
            } catch (NumberFormatException e) { /* do nothing */ }

            throw new InvalidSettingValueException();
        }

        @Override
        public XryptoMail.Theme fromPrettyString(String value) throws InvalidSettingValueException {
            if (THEME_LIGHT.equals(value)) {
                return XryptoMail.Theme.LIGHT;
            } else if (THEME_DARK.equals(value)) {
                return XryptoMail.Theme.DARK;
            }

            throw new InvalidSettingValueException();
        }

        @Override
        public String toPrettyString(XryptoMail.Theme value) {
            switch (value) {
                case DARK: {
                    return THEME_DARK;
                }
                default: {
                    return THEME_LIGHT;
                }
            }
        }

        @Override
        public String toString(XryptoMail.Theme value) {
            return Integer.toString(value.ordinal());
        }
    }

    /**
     * The message view theme setting.
     */
    private static class SubThemeSetting extends ThemeSetting {
        private static final String THEME_USE_GLOBAL = "use_global";

        SubThemeSetting(Theme defaultValue) {
            super(defaultValue);
        }

        @Override
        public XryptoMail.Theme fromString(String value) throws InvalidSettingValueException {
            try {
                Integer theme = Integer.parseInt(value);
                if (theme == XryptoMail.Theme.USE_GLOBAL.ordinal()) {
                    return XryptoMail.Theme.USE_GLOBAL;
                }

                return super.fromString(value);
            } catch (NumberFormatException e) {
                throw new InvalidSettingValueException();
            }
        }

        @Override
        public XryptoMail.Theme fromPrettyString(String value) throws InvalidSettingValueException {
            if (THEME_USE_GLOBAL.equals(value)) {
                return XryptoMail.Theme.USE_GLOBAL;
            }

            return super.fromPrettyString(value);
        }

        @Override
        public String toPrettyString(XryptoMail.Theme value) {
            if (value == XryptoMail.Theme.USE_GLOBAL) {
                return THEME_USE_GLOBAL;
            }
            return super.toPrettyString(value);
        }
    }

    /**
     * A time setting.
     */
    private static class TimeSetting extends SettingsDescription<String>  {
        TimeSetting(String defaultValue) {
            super(defaultValue);
        }

        @Override
        public String fromString(String value) throws InvalidSettingValueException {
            if (!value.matches(TimePickerPreference.VALIDATION_EXPRESSION)) {
                throw new InvalidSettingValueException();
            }
            return value;
        }
    }

    /**
     * A directory on the file system.
     */
    private static class DirectorySetting extends SettingsDescription<String> {
        DirectorySetting(File defaultPath) {
            super(defaultPath.toString());
        }

        @Override
        public String fromString(String value) throws InvalidSettingValueException {
            try {
                if (new File(value).isDirectory()) {
                    return value;
                }
            } catch (Exception e) { /* do nothing */ }

            throw new InvalidSettingValueException();
        }
    }
}
