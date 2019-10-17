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

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.webkit.WebView;
import android.widget.TextView;

import org.atalk.xryptomail.BuildConfig;
import org.atalk.xryptomail.R;
import org.atalk.xryptomail.XryptoMail;
import org.atalk.xryptomail.helper.androidupdate.UpdateService;

import de.cketti.library.changelog.ChangeLog;

/**
 * XryptoMail About activity
 */
public class About extends Activity implements OnClickListener
{
    private static String[][] USED_LIBRARIES = new String[][]{
            new String[]{"Android Support Library", "https://developer.android.com/topic/libraries/support-library"},
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

    public void onCreate(Bundle savedInstanceState)
    {
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
        }
        else {
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
    public void onClick(View view)
    {
        switch (view.getId()) {
            case R.id.ok_button:
                finish();
                break;
            case R.id.check_new_version:
                new Thread()
                {
                    @Override
                    public void run()
                    {
                        UpdateService updateService = new UpdateService();
                        updateService.checkForUpdates(true);
                    }
                }.start();
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

    private void xmailUrlAccess()
    {
        String url = getString(R.string.AboutDialog_Link);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        startActivity(intent);
    }

    private String getAboutInfo()
    {
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        View about = inflater.inflate(R.layout.about, null, false);
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);

            TextView textView = about.findViewById(R.id.AboutDialog_Version);
            textView.setText(String.format(getString(R.string.AboutDialog_Version), pi.versionName));
        } catch (NameNotFoundException ignore) {
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
    public static void showSendLogsDialog()
    {
        //        LogUploadService logUpload = ServiceUtils.getService(AndroidGUIActivator.bundleContext,
        //                LogUploadService.class);
        //        String defaultEmail = getConfig().getString("org.atalk.android.LOG_REPORT_EMAIL");
        //
        //        logUpload.sendLogs(new String[]{defaultEmail},
        //                getResString(R.string.service_gui_SEND_LOGS_SUBJECT),
        //                getResString(R.string.service_gui_SEND_LOGS_TITLE));
    }
}
