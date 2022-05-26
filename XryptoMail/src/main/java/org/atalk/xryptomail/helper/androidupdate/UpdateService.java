/*
 * XryptoMail, android mail client
 * Copyright 2011-2022 Eng Chong Meng
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

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.Build;
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
 * Android update service implementation. It checks for update and schedules .apk download using <tt>DownloadManager</tt>.
 *
 * @author Eng Chong Meng
 */
public class UpdateService {
    // Default update link
    private static final String[] updateLinks = {"https://atalk.sytes.net"};

    /**
     * Apk mime type constant.
     */
    private static final String APK_MIME_TYPE = "application/vnd.android.package-archive";

    // path is case-sensitive
    private static final String filePath = "/releases/xryptomail-android/versionupdate.properties";

    /**
     * Current installed version string / version Code
     */
    private String currentVersion;
    private long currentVersionCode;

    /**
     * The latest version string / version code
     */
    private String latestVersion;
    private long latestVersionCode;

    private boolean mIsLatest = false;

    /* DownloadManager Broadcast Receiver Handler */
    private DownloadReceiver downloadReceiver = null;

    /**
     * The download link
     */
    private String downloadLink;

    /**
     * <tt>SharedPreferences</tt> used to store download ids.
     */
    private SharedPreferences store;

    /**
     * Name of <tt>SharedPreferences</tt> entry used to store old download ids. Ids are stored in
     * single string separated by ",".
     */
    private static final String ENTRY_NAME = "apk_ids";

    private static UpdateService mInstance = null;

    public static UpdateService getInstance() {
        if (mInstance == null) {
            mInstance = new UpdateService();
        }
        return mInstance;
    }

    /**
     * Checks for updates and take necessary action.
     *
     * @param notifyAboutNewestVersion <tt>true</tt> if the user is to be notified if they have the
     * newest version already; otherwise, <tt>false</tt>
     */
    public void checkForUpdates(boolean notifyAboutNewestVersion) {
        // cmeng: reverse the logic to !isLatestVersion() for testing
        mIsLatest = isLatestVersion();
        Timber.i("Is latest: %s\nCurrent version: %s\nLatest version: %s\nDownload link: %s",
                mIsLatest, currentVersion, latestVersion, downloadLink);

        if (!mIsLatest && (downloadLink != null)) {
            if (checkLastDLFileAction() < DownloadManager.ERROR_UNKNOWN)
                return;

            DialogActivity.showConfirmDialog(XryptoMail.getGlobalContext(),
                    R.string.updatechecker_DIALOG_TITLE,
                    R.string.updatechecker_DIALOG_MESSAGE,
                    R.string.updatechecker_BUTTON_DOWNLOAD,
                    new DialogActivity.DialogListener() {
                        @Override
                        public boolean onConfirmClicked(DialogActivity dialog) {
                            downloadApk();
                            return true;
                        }

                        @Override
                        public void onDialogCancelled(DialogActivity dialog) {
                        }
                    }, XryptoMail.getResString(R.string.app_name), latestVersion, latestVersionCode
            );
        } else if (notifyAboutNewestVersion) {
            // Notify that running version is up to date
            DialogActivity.showConfirmDialog(XryptoMail.getGlobalContext(),
                    R.string.updatechecker_DIALOG_NOUPDATE_TITLE,
                    R.string.updatechecker_DIALOG_NOUPDATE,
                    R.string.updatechecker_BUTTON_DOWNLOAD,
                    new DialogActivity.DialogListener() {
                        @Override
                        public boolean onConfirmClicked(DialogActivity dialog) {
                            if (checkLastDLFileAction() >= DownloadManager.ERROR_UNKNOWN)
                                downloadApk();
                            return true;
                        }

                        @Override
                        public void onDialogCancelled(DialogActivity dialog) {
                        }
                    }, currentVersion, currentVersionCode
            );
        }
    }

    /**
     * Check for any existing downloaded file and take appropriate action;
     *
     * @return Last DownloadManager status; default to DownloadManager.ERROR_UNKNOWN if status unknown
     */
    private int checkLastDLFileAction() {
        // Check old or scheduled downloads
        int lastJobStatus = DownloadManager.ERROR_UNKNOWN;

        List<Long> previousDownloads = getOldDownloads();
        if (previousDownloads.size() > 0) {
            long lastDownload = previousDownloads.get(previousDownloads.size() - 1);

            lastJobStatus = checkDownloadStatus(lastDownload);
            if (lastJobStatus == DownloadManager.STATUS_SUCCESSFUL) {
                DownloadManager downloadManager = XryptoMail.getDownloadManager();
                Uri fileUri = downloadManager.getUriForDownloadedFile(lastDownload);

                // Ask the user if he wants to install the valid apk when found
                if (isValidApkVersion(fileUri, latestVersionCode)) {
                    askInstallDownloadedApk(fileUri);
                }
            } else if (lastJobStatus != DownloadManager.STATUS_FAILED) {
                // Download is in progress or scheduled for retry
                DialogActivity.showDialog(XryptoMail.getGlobalContext(),
                        R.string.updatechecker_DIALOG_IN_PROGRESS_TITLE,
                        R.string.updatechecker_DIALOG_IN_PROGRESS);
            } else {
                // Download is in progress or scheduled for retry
                DialogActivity.showDialog(XryptoMail.getGlobalContext(),
                        R.string.updatechecker_DIALOG_TITLE, R.string.updatechecker_DOWNLOAD_FAILED);
            }
        }
        return lastJobStatus;
    }

    /**
     * Asks the user whether to install downloaded .apk.
     *
     * @param fileUri download file uri of the apk to install.
     */
    private void askInstallDownloadedApk(Uri fileUri) {
        DialogActivity.showConfirmDialog(XryptoMail.getGlobalContext(),
                R.string.updatechecker_DIALOG_DOWNLOADED_TITLE,
                R.string.updatechecker_DIALOG_DOWNLOADED,
                mIsLatest ? R.string.updatechecker_BUTTON_REINSTALL : R.string.updatechecker_BUTTON_INSTALL ,
                new DialogActivity.DialogListener() {
                    @Override
                    public boolean onConfirmClicked(DialogActivity dialog) {
                        Context context = XryptoMail.getGlobalContext();
                        // Need REQUEST_INSTALL_PACKAGES in manifest; Intent.ACTION_VIEW works for both
                        Intent intent;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                            intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                        else
                            intent = new Intent(Intent.ACTION_VIEW);

                        intent.setDataAndType(fileUri, APK_MIME_TYPE);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                        context.startActivity(intent);
                        return true;
                    }

                    @Override
                    public void onDialogCancelled(DialogActivity dialog) {
                    }
                }, latestVersion);
    }

    /**
     * Queries the <tt>DownloadManager</tt> for the status of download job identified by given <tt>id</tt>.
     *
     * @param id download identifier which status will be returned.
     * @return download status of the job identified by given id. If given job is not found
     * {@link DownloadManager#STATUS_FAILED} will be returned.
     */
    @SuppressLint("Range")
    private int checkDownloadStatus(long id) {
        DownloadManager downloadManager = XryptoMail.getDownloadManager();
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(id);

        try (Cursor cursor = downloadManager.query(query)) {
            if (!cursor.moveToFirst())
                return DownloadManager.STATUS_FAILED;
            else
                return cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
        }
    }

    /**
     * Schedules .apk download.
     */
    private void downloadApk() {
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

    private class DownloadReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (checkLastDLFileAction() < DownloadManager.ERROR_UNKNOWN)
                return;

            // unregistered downloadReceiver
            if (downloadReceiver != null) {
                XryptoMail.getGlobalContext().unregisterReceiver(downloadReceiver);
                downloadReceiver = null;
            }
        }
    }

    private SharedPreferences getStore() {
        if (store == null) {
            store = XryptoMail.getGlobalContext().getSharedPreferences("store", Context.MODE_PRIVATE);
        }
        return store;
    }

    private void rememberDownloadId(long id) {
        SharedPreferences store = getStore();
        String storeStr = store.getString(ENTRY_NAME, "");
        storeStr += id + ",";
        store.edit().putString(ENTRY_NAME, storeStr).apply();
    }

    private List<Long> getOldDownloads() {
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
    public void removeOldDownloads() {
        List<Long> apkIds = getOldDownloads();

        DownloadManager downloadManager = XryptoMail.getDownloadManager();
        for (long id : apkIds) {
            Timber.d("Removing .apk for id %s", id);
            downloadManager.remove(id);
        }
        getStore().edit().remove(ENTRY_NAME).apply();
    }

    /**
     * Validate the downloaded apk file for correct versionCode and its apk name
     *
     * @param fileUri apk Uri
     * @param versionCode use the given versionCode to check against the apk versionCode
     * @return true if apkFile has the specified versionCode
     */
    private boolean isValidApkVersion(Uri fileUri, long versionCode) {
        // Default to valid as getPackageArchiveInfo() always return null; but sometimes OK
        boolean isValid = true;
        File apkFile = new File(FilePathHelper.getFilePath(XryptoMail.getGlobalContext(), fileUri));

        if (apkFile.exists()) {
            // Get downloaded apk actual versionCode and check its versionCode validity
            PackageManager pm = XryptoMail.getGlobalContext().getPackageManager();
            PackageInfo pckgInfo = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);

            if (pckgInfo != null) {
                long apkVersionCode;
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
                    apkVersionCode = pckgInfo.versionCode;
                else
                    apkVersionCode = pckgInfo.getLongVersionCode();

                isValid = (versionCode == apkVersionCode);
                if (!isValid) {
                    XryptoMail.showToastMessage(R.string.updatechecker_VERSION_INVALID, apkVersionCode, versionCode);
                    Timber.d("Downloaded apk actual version code: %s (%s)", apkVersionCode, versionCode);
                }
            }
        }
        return isValid;
    }

    /**
     * Gets the latest available (software) version online.
     *
     * @return the latest (software) version
     */
    public String getLatestVersion() {
        return latestVersion;
    }

    /**
     * Determines whether we are currently running the latest version.
     *
     * @return <tt>true</tt> if current running application is the latest version; otherwise, <tt>false</tt>
     */
    public boolean isLatestVersion() {
        Properties mProperties = null;
        String errMsg = "";

        VersionService versionService = VersionService.getInstance();
        currentVersion = versionService.getCurrentVersionName();
        currentVersionCode = versionService.getCurrentVersionCode();

        if (updateLinks.length == 0) {
            Timber.d("Updates are disabled, emulates latest version.");
        } else {
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
                latestVersionCode = Long.parseLong(mProperties.getProperty("last_version_code"));
                if (BuildConfig.DEBUG) {
                    downloadLink = mProperties.getProperty("download_link-debug");
                } else {
                    downloadLink = mProperties.getProperty("download_link");
                }
                // return true is current running application is already the latest
                return (currentVersionCode >= latestVersionCode);
            } else {
                Timber.w("Could not retrieve version.properties for checking: %s", errMsg);
            }
        }
        return true;
    }
}
