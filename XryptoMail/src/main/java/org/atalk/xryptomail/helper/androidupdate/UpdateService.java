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

package org.atalk.xryptomail.helper.androidupdate;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.Environment;

import org.atalk.xryptomail.BuildConfig;
import org.atalk.xryptomail.R;
import org.atalk.xryptomail.XryptoMail;
import org.atalk.xryptomail.helper.DialogActivity;
import org.atalk.xryptomail.helper.FilePathHelper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import timber.log.Timber;

/**
 * Android update service implementation. It checks for update and schedules .apk download using
 * <tt>DownloadManager</tt>.
 *
 * @author Eng Chong Meng
 */
public class UpdateService
{
    /**
     * The name of the property which specifies the update link in the configuration file.
     */
    private static final String[] updateLinks = {"https://atalk.sytes.net", "https://atalk.mooo.com"};

    /**
     * Apk mime type constant.
     */
    private static final String APK_MIME_TYPE = "application/vnd.android.package-archive";

    // path are case sensitive
    private static final String filePath = "/releases/xryptomail-android/versionupdate.properties";

    /**
     * Current installed version string
     */
    private String currentVersion;
    private int currentVersionCode;

    /**
     * Latest version string
     */
    private String latestVersion;
    private int latestVersionCode;

    /* DownloadManager Broadcast Receiver Handler */
    private DownloadManager downloadManager;
    private DownloadReceiver downloadReceiver = null;

    /**
     * The download link
     */
    private String downloadLink;
    // private String changesLink;

    /**
     * <tt>SharedPreferences</tt> used to store download ids.
     */
    private SharedPreferences store;

    /**
     * Name of <tt>SharedPreferences</tt> entry used to store old download ids. Ids are stored in
     * single string separated by ",".
     */
    private static final String ENTRY_NAME = "apk_ids";

    /**
     * Checks for updates.
     *
     * @param notifyAboutNewestVersion <tt>true</tt> if the user is to be notified if they have the
     * newest version already; otherwise, <tt>false</tt>
     */
    public void checkForUpdates(boolean notifyAboutNewestVersion)
    {
        boolean isLatest = isLatestVersion();
        Timber.i("Is latest: %s\nCurrent version: %s\nLatest version: %s\nDownload link: %s",
                isLatest, currentVersion, latestVersion, downloadLink);
        // Timber.i("Changes link: %s", changesLink);

        // cmeng: reverse the logic for !isLast for testing
        if (!isLatest && (downloadLink != null)) {
            // Check old or scheduled downloads
            List<Long> previousDownloads = getOldDownloads();
            if (previousDownloads.size() > 0) {
                long lastDownload = previousDownloads.get(previousDownloads.size() - 1);

                int lastJobStatus = checkDownloadStatus(lastDownload);
                if (lastJobStatus == DownloadManager.STATUS_SUCCESSFUL) {
                    DownloadManager downloadManager = XryptoMail.getDownloadManager();
                    Uri fileUri = downloadManager.getUriForDownloadedFile(lastDownload);
                    File apkFile = new File(FilePathHelper.getPath(XryptoMail.getGlobalContext(), fileUri));

                    if (apkFile.exists()) {
                        // Ask the user if he wants to install
                        askInstallDownloadedApk(fileUri);
                        return;
                    }
                }
                else if (lastJobStatus != DownloadManager.STATUS_FAILED) {
                    // Download is in progress or scheduled for retry
                    DialogActivity.showDialog(XryptoMail.getGlobalContext(),
                            XryptoMail.getResString(R.string.updatechecker_DIALOG_IN_PROGRESS_TITLE),
                            XryptoMail.getResString(R.string.updatechecker_DIALOG_IN_PROGRESS));
                    return;
                }
            }

            DialogActivity.showConfirmDialog(XryptoMail.getGlobalContext(),
                    XryptoMail.getResString(R.string.updatechecker_DIALOG_TITLE),
                    XryptoMail.getResString(R.string.updatechecker_DIALOG_MESSAGE,
                            latestVersion, Integer.toString(latestVersionCode), XryptoMail.getResString(R.string.app_name)),
                    XryptoMail.getResString(R.string.updatechecker_BUTTON_DOWNLOAD),
                    new DialogActivity.DialogListener()
                    {
                        @Override
                        public boolean onConfirmClicked(DialogActivity dialog)
                        {
                            downloadApk();
                            return true;
                        }

                        @Override
                        public void onDialogCancelled(DialogActivity dialog)
                        {
                        }
                    }
            );
        }
        else if (notifyAboutNewestVersion) {
            // Notify that running version is up to date
            DialogActivity.showDialog(XryptoMail.getGlobalContext(),
                    XryptoMail.getResString(R.string.updatechecker_DIALOG_NOUPDATE_TITLE),
                    XryptoMail.getResString(R.string.updatechecker_DIALOG_NOUPDATE,
                            currentVersion, Integer.toString(currentVersionCode)));
        }
    }

    /**
     * Asks the user whether to install downloaded .apk.
     *
     * @param fileUri download file uri of the apk to install.
     */
    private void askInstallDownloadedApk(Uri fileUri)
    {
        DialogActivity.showConfirmDialog(XryptoMail.getGlobalContext(),
                XryptoMail.getResString(R.string.updatechecker_DIALOG_DOWNLOADED_TITLE),
                XryptoMail.getResString(R.string.updatechecker_DIALOG_DOWNLOADED),
                XryptoMail.getResString(R.string.updatechecker_BUTTON_INSTALL),
                new DialogActivity.DialogListener()
                {
                    @Override
                    public boolean onConfirmClicked(DialogActivity dialog)
                    {
                        // Need REQUEST_INSTALL_PACKAGES in manifest; Intent.ACTION_VIEW works for both
                        // Intent intent;
                        // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                        //   intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                        // else
                        //   intent = new Intent(Intent.ACTION_VIEW);

                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        intent.setDataAndType(fileUri, APK_MIME_TYPE);

                        XryptoMail.getGlobalContext().startActivity(intent);
                        return true;
                    }

                    @Override
                    public void onDialogCancelled(DialogActivity dialog)
                    {
                    }
                });
    }

    /**
     * Queries the <tt>DownloadManager</tt> for the status of download job identified by given <tt>id</tt>.
     *
     * @param id download identifier which status will be returned.
     * @return download status of the job identified by given id. If given job is not found
     * {@link DownloadManager#STATUS_FAILED} will be returned.
     */
    private int checkDownloadStatus(long id)
    {
        DownloadManager downloadManager = XryptoMail.getDownloadManager();
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(id);

        Cursor cursor = downloadManager.query(query);
        try {
            if (!cursor.moveToFirst())
                return DownloadManager.STATUS_FAILED;
            else
                return cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
        } finally {
            cursor.close();
        }
    }

    /**
     * Schedules .apk download.
     */
    private void downloadApk()
    {
        Uri uri = Uri.parse(downloadLink);
        String fileName = uri.getLastPathSegment();

        if (downloadReceiver == null) {
            downloadReceiver = new DownloadReceiver();
            XryptoMail.getGlobalContext().registerReceiver(downloadReceiver,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }

        DownloadManager.Request request = new DownloadManager.Request(uri);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setMimeType(APK_MIME_TYPE);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

        DownloadManager downloadManager = XryptoMail.getDownloadManager();
        long jobId = downloadManager.enqueue(request);
        rememberDownloadId(jobId);
    }

    private void rememberDownloadId(long id)
    {
        SharedPreferences store = getStore();
        String storeStr = store.getString(ENTRY_NAME, "");
        storeStr += id + ",";
        store.edit().putString(ENTRY_NAME, storeStr).apply();
    }

    private class DownloadReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            List<Long> previousDownloads = getOldDownloads();
            if (previousDownloads.size() > 0) {
                long lastDownload = previousDownloads.get(previousDownloads.size() - 1);

                int lastJobStatus = checkDownloadStatus(lastDownload);
                if (lastJobStatus == DownloadManager.STATUS_SUCCESSFUL) {
                    DownloadManager downloadManager = XryptoMail.getDownloadManager();
                    Uri fileUri = downloadManager.getUriForDownloadedFile(lastDownload);
                    File apkFile = new File(FilePathHelper.getPath(XryptoMail.getGlobalContext(), fileUri));

                    if (apkFile.exists()) {
                        // Ask the user if he wants to install
                        askInstallDownloadedApk(fileUri);
                        return;
                    }
                }
                else if (lastJobStatus == DownloadManager.STATUS_FAILED) {
                    // Download is in progress or scheduled for retry
                    DialogActivity.showDialog(XryptoMail.getGlobalContext(),
                            XryptoMail.getResString(R.string.updatechecker_DIALOG_TITLE),
                            XryptoMail.getResString(R.string.updatechecker_DOWNLOAD_FAILED));
                    return;
                }
            }

            // unregistered downloadReceiver
            if (downloadReceiver != null) {
                XryptoMail.getGlobalContext().unregisterReceiver(downloadReceiver);
                downloadReceiver = null;
            }
        }
    }

    private SharedPreferences getStore()
    {
        if (store == null) {
            store = XryptoMail.getGlobalContext().getSharedPreferences("store", Context.MODE_PRIVATE);
        }
        return store;
    }

    private List<Long> getOldDownloads()
    {
        String storeStr = getStore().getString(ENTRY_NAME, "");
        String[] idStrs = storeStr.split(",");
        List<Long> apkIds = new ArrayList<>(idStrs.length);
        for (String idStr : idStrs) {
            try {
                if (!idStr.isEmpty())
                    apkIds.add(Long.parseLong(idStr));
            } catch (NumberFormatException e) {
                Timber.e("Error parsing apk id for string: %s [%s]", idStr, storeStr);
            }
        }
        return apkIds;
    }

    /**
     * Removes old downloads.
     */
    void removeOldDownloads()
    {
        List<Long> apkIds = getOldDownloads();

        DownloadManager downloadManager = XryptoMail.getDownloadManager();
        for (long id : apkIds) {
            Timber.d("Removing .apk for id %s", id);
            downloadManager.remove(id);
        }
        getStore().edit().remove(ENTRY_NAME).apply();
    }

    /**
     * Gets the latest available (software) version online.
     *
     * @return the latest (software) version
     */
    public String getLatestVersion()
    {
        return latestVersion;
    }

    /**
     * Determines whether we are currently running the latest version.
     *
     * @return <tt>true</tt> if current running application is the latest version; otherwise, <tt>false</tt>
     */
    public boolean isLatestVersion()
    {
        VersionService versionService = new VersionService();
        currentVersion = versionService.getCurrentVersionName();
        currentVersionCode = versionService.getCurrentVersionCode();
        Properties mProperties = null;
		String errMsg = "";

        TrafficStats.setThreadStatsTag(XryptoMail.THREAD_ID);
        for (String aLink : updateLinks) {
            String urlStr = aLink.trim() + filePath;
            try {
                URL mUrl = new URL(urlStr);
                HttpURLConnection httpConnection = (HttpURLConnection) mUrl.openConnection();
                httpConnection.setRequestMethod("GET");
                httpConnection.setRequestProperty("Content-length", "0");
                httpConnection.setUseCaches(false);
                httpConnection.setAllowUserInteraction(false);
                httpConnection.setConnectTimeout(100000);
                httpConnection.setReadTimeout(100000);

                httpConnection.connect();
                int responseCode = httpConnection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream in = httpConnection.getInputStream();
                    mProperties = new Properties();
                    mProperties.load(in);
                    break;
                }
            } catch (IOException e) {
                errMsg = e.getMessage();
            }
        }

        if (mProperties != null) {
            latestVersion = mProperties.getProperty("last_version");
            latestVersionCode = Integer.valueOf(mProperties.getProperty("last_version_code"));
            if (BuildConfig.DEBUG) {
                downloadLink = mProperties.getProperty("download_link-debug");
            }
            else {
                downloadLink = mProperties.getProperty("download_link");
            }
            // return true is current running application is already the latest
            return (currentVersionCode >= latestVersionCode);
        }
        else {
            Timber.w("Could not retrieve version.properties for checking: %s", errMsg);
        }
        return true;
    }
}

