/*
 * XryptoMail, android mail client
 * Copyright 2011 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.xryptomail.activity;

import android.app.*;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.widget.TextView;
import de.cketti.library.changelog.ChangeLog;
import org.atalk.xryptomail.*;
import org.atalk.xryptomail.service.OnlineUpdateService;

import java.io.File;

/**
 * XryptoMail About activity
 */
public class About extends Activity implements OnClickListener {
    private final int FETCH_ERROR = 10;
    private final int NO_NEW_VERSION = 20;
    private final int DOWNLOAD_ERROR = 30;

    private final static int CHECK_NEW_VERSION = 10;

    private static String[][] USED_LIBRARIES = new String[][]{
            new String[]{"Android Support Library", "https://developer.android.com/topic/libraries/support-library/index.html"},
            new String[]{"butterknife", "https://github.com/JakeWharton/butterknife"},
            new String[]{"ckChangeLog", "https://github.com/cketti/ckChangeLog"},
            new String[]{"Commons IO", "http://commons.apache.org/io/"},
            new String[]{"Dexter", "https://github.com/Karumi/Dexter"},
            new String[]{"dnsjava", "https://github.com/dnsjava/dnsjava"},
            new String[]{"Glide", "https://github.com/bumptech/glide"},
            new String[]{"HoloColorPicker", "https://github.com/LarsWerkman/HoloColorPicker"},
            new String[]{"james-mime4j", "https://github.com/apache/james-mime4j"},
            new String[]{"jsoup", "https://jsoup.org/"},
            new String[]{"jutf7", "http://jutf7.sourceforge.net/"},
            new String[]{"jcip-annotations", "https://github.com/stephenc/jcip-annotations"},
            new String[]{"JZlib", "http://www.jcraft.com/jzlib/"},
            new String[]{"K-9 Mai", "https://github.com/k9mail/k-9"},
            new String[]{"kotlin", "https://github.com/JetBrains/kotlin"},
            new String[]{"MaterialProgressBar", "https://github.com/DreaminginCodeZH/MaterialProgressBar"},
            new String[]{"Mime4j", "http://james.apache.org/mime4j/"},
            new String[]{"Moshi", "https://github.com/square/moshi"},
            new String[]{"Okio", "https://github.com/square/okio"},
            new String[]{"openpgp-api", "https://github.com/open-keychain/openpgp-api"},
            new String[]{"retrofit", "https://github.com/square/retrofit"},
            new String[]{"SafeContentResolver", "https://github.com/cketti/SafeContentResolver"},
            new String[]{"ShortcutBadger", "https://github.com/leolin310148/ShortcutBadger"},
            new String[]{"ShowcaseView", "https://github.com/amlcurran/ShowcaseView"},
            new String[]{"Timber", "https://github.com/JakeWharton/timber"},
            new String[]{"TokenAutoComplete", "https://github.com/splitwise/TokenAutoComplete/"},
    };

    /**
     * Default CSS styles used to format the change log.
     */
    public static final String DEFAULT_CSS =
            "h1 { margin-left: 0px; font-size: 1.2em; }" + "\n" +
                    "li { margin-left: 0px; font-size: 0.9em;}" + "\n" +
                    "ul { padding-left: 2em; }";

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_LEFT_ICON);
        setContentView(R.layout.about);

        View atakUrl = findViewById(R.id.xmail_link);
        atakUrl.setOnClickListener(this);

        TextView atalkHelp = findViewById(R.id.xmail_help);
        atalkHelp.setTextColor(getResources().getColor(R.color.light_blue));
        atalkHelp.setOnClickListener(this);

        findViewById(R.id.ok_button).setOnClickListener(this);
        findViewById(R.id.history_log).setOnClickListener(this);

        View btn_submitLogs = findViewById(R.id.submit_logs);
        btn_submitLogs.setOnClickListener(this);
        btn_submitLogs.setVisibility(View.GONE);

        View btn_update = findViewById(R.id.check_new_version);
        if (BuildConfig.DEBUG) {
            btn_update.setVisibility(View.VISIBLE);
            btn_update.setOnClickListener(this);
        } else {
            btn_update.setVisibility(View.GONE);
        }

        String aboutInfo = getAboutInfo();
        WebView wv = findViewById(R.id.AboutDialog_Info);
        wv.loadDataWithBaseURL("file:///android_res/drawable/", aboutInfo, "text/html", "utf-8", null);

        setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, android.R.drawable.ic_dialog_info);
        this.setTitle(getString(R.string.AboutDialog_title));
        TextView textView = findViewById(R.id.AboutDialog_Version);
        textView.setText(String.format(getString(R.string.AboutDialog_Version), XryptoMail.mVersion));
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.ok_button:
                finish();
                break;
            case R.id.check_new_version:
                XryptoMail.mUpdateApkPath = null;
                Intent intent = new Intent(About.this, OnlineUpdate.class);
                intent.putExtra(OnlineUpdateService.SKIP_VERSION_CHECK, false);
                About.this.startActivityForResult(intent, CHECK_NEW_VERSION);
                break;
//            case R.id.submit_logs:
//                aTalkApp.showSendLogsDialog();
//                break;
            case R.id.history_log:
                ChangeLog cl = new ChangeLog(this, DEFAULT_CSS);
                cl.getFullLogDialog().show();
                break;
            case R.id.xmail_help:
            case R.id.xmail_link:
                xmailUrlAccess();
                break;
            default:
                finish();
                break;
        }
    }

    private void xmailUrlAccess() {
        String url = getString(R.string.AboutDialog_Link);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        startActivity(intent);
    }

    private String getAboutInfo() {
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        View about = inflater.inflate(R.layout.about, null, false);
        //String versionTitle = getString(R.string.AboutDialog_title);
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            // versionTitle += " v" + pi.versionName;

            TextView textView = about.findViewById(R.id.AboutDialog_Version);
            textView.setText(String.format(getString(R.string.AboutDialog_Version), pi.versionName));
        } catch (NameNotFoundException e) {
        }

        StringBuilder libs = new StringBuilder().append("<ul>");
        for (String[] library : USED_LIBRARIES) {
            libs.append("<li><a href=\"")
                    .append(library[1])
                    .append("\">")
                    .append(library[0])
                    .append("</a></li>");
        }
        libs.append("</ul>");

        String html = "<meta http-equiv=\"content-type\" content=\"text/html; charset=utf-8\"/>" +
                "<html><head><style type=\"text/css\">" +
                DEFAULT_CSS +
                "</style></head><body>" +
                String.format(getString(R.string.app_libraries), libs.toString()) +
                "</p><hr/><p>" +
                "</body></html>";
        return html;
    }

    /**
     * Displays the send logs dialog.
     */
    public static void showSendLogsDialog() {
//        LogUploadService logUpload = ServiceUtils.getService(AndroidGUIActivator.bundleContext,
//                LogUploadService.class);
//        String defaultEmail = getConfig().getString("org.atalk.android.LOG_REPORT_EMAIL");
//
//        logUpload.sendLogs(new String[]{defaultEmail},
//                getResString(R.string.service_gui_SEND_LOGS_SUBJECT),
//                getResString(R.string.service_gui_SEND_LOGS_TITLE));
    }

    /**
     * Get current version number.
     *
     * @return String version
     */
    private String getVersionNumber() {
        String version = "?";
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = pi.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            // Log.e(TAG, "Package name not found", e);
        }
        return version;
    }


    /**
     * Check for new version
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == CHECK_NEW_VERSION) {
            if (OnlineUpdate.OnlineUpdateStatus.FETCH_VERSION_ERROR.getValue() == resultCode) {
                showDialog(FETCH_ERROR);
            } else if (OnlineUpdate.OnlineUpdateStatus.NO_NEW_VERSION_FOUND.getValue() == resultCode) {
                showDialog(NO_NEW_VERSION);
            } else if (OnlineUpdate.OnlineUpdateStatus.DOWNLOAD_APK_ERROR.getValue() == resultCode) {
                showDialog(DOWNLOAD_ERROR);
            } else if (OnlineUpdate.OnlineUpdateStatus.DOWNLOAD_APK_SUCCESSFUL.getValue() == resultCode) {
                if (!TextUtils.isEmpty(XryptoMail.mUpdateApkPath)) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(
                            Uri.fromFile(new File(XryptoMail.mUpdateApkPath)), "application/vnd.android.package-archive");
                    startActivity(intent);
                }
            }
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        String appVersion = "";
        switch (id) {
            case FETCH_ERROR:
                new AlertDialog.Builder(this)
                        .setMessage(getString(R.string.fetch_version_error))
                        .setPositiveButton(R.string.okay_action,
                                (d, c) -> d.dismiss()).show();
                break;

            case NO_NEW_VERSION:
                String msgstr = String.format(getString(R.string.no_new_version_found), appVersion);
                new AlertDialog.Builder(this)
                        .setMessage(msgstr)
                        .setPositiveButton(R.string.okay_action,
                                (d, c) -> d.dismiss()).show();
                break;

            case DOWNLOAD_ERROR:
                new AlertDialog.Builder(this)
                        .setMessage(getString(R.string.download_apk_error))
                        .setPositiveButton(R.string.okay_action,
                                (d, c) -> d.dismiss()).show();
                break;
        }
        return super.onCreateDialog(id);
    }
}
