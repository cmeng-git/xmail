package org.atalk.xryptomail.service;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;

import org.atalk.xryptomail.BuildConfig;
import org.atalk.xryptomail.Preferences;
import org.atalk.xryptomail.R;
import org.atalk.xryptomail.XryptoMail;
import org.atalk.xryptomail.activity.OnlineUpdate;
import org.atalk.xryptomail.helper.DownloadFile;
import org.atalk.xryptomail.helper.MySqlConn;
import org.atalk.xryptomail.notification.NotificationHelper;
import org.atalk.xryptomail.preferences.Storage;
import org.atalk.xryptomail.preferences.StorageEditor;

import java.io.File;
import java.util.Calendar;
import java.util.regex.Pattern;

import timber.log.Timber;

public class OnlineUpdateService extends IntentService
{
    private static boolean mUpdateWithoutConfirm = false;
    private int mNotifID = 123;

    // in unit of seconds
    private static int CHECK_NEW_VERSION_INTERVAL = 24 * 60 * 60;

    private static final String LAST_VERSION_CHECK_DAY_OF_YEAR = "LAST_VERSION_CHECK_DAY_OF_YEAR";
    private static final String ONLINEUPDATESERVICE = "ONLINEUPDATESERVICE";

    public static final String KEY_APP_NAME = "XryptoMail_Android";
    public static final String KEY_APP_NAME_DEBUG = "XryptoMail_Android-debug";
    public static final String SKIP_VERSION_CHECK = "skip_version_check";

    public OnlineUpdateService()
    {
        super("OnlineUpdateService");
    }

    public class DownloadWithoutConfirm extends DownloadFile
    {
        @Override
        protected void onPreExecute()
        {
            super.onPreExecute();
        }

        @Override
        protected void onProgressUpdate(Integer... progress)
        {
            super.onProgressUpdate(progress);
        }

        @Override
        protected void onPostExecute(Integer Result)
        {
            super.onPostExecute(Result);
            if (RESULT_OK.equals(Result)) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setDataAndType(Uri.fromFile(new File(XryptoMail.mUpdateApkPath)), "application/vnd.android.package-archive");
                startActivity(intent);
            }
        }
    }

    @Override
    protected void onHandleIntent(Intent intent)
    {
        int notiID = (int) SystemClock.uptimeMillis();

        Preferences prefs = Preferences.getPreferences(OnlineUpdateService.this);
        Storage storage = prefs.getStorage();
        int lastVersionCheckDay = storage.getInt(LAST_VERSION_CHECK_DAY_OF_YEAR, -1);
        int curDayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);

        AlarmManager service = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(this.getApplicationContext(), OnlineUpdateService.class);
        // If, however, you will have two notification registered at once,
        // with different Intent extras, you will need to make the two
        // Intents be more materially different, such that filterEquals()
        // returns false when comparing the two. For example, you could call
        // setData() or setAction() and provide different values for each Intent
        i.setAction("OnlineUpdateService");
        i.setData(Uri.parse("email://accounts/" + Integer.toString((int) System.currentTimeMillis())));

        PendingIntent pending1 = PendingIntent.getService(this, (int) System.currentTimeMillis(), i, PendingIntent.FLAG_ONE_SHOT);
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(System.currentTimeMillis());
        cal.add(Calendar.SECOND, CHECK_NEW_VERSION_INTERVAL);

        service.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pending1);

        // cmeng - set to != for testing
        if (curDayOfYear == lastVersionCheckDay) {
            return;
        }

        NotificationManager notifMgr = (NotificationManager) getApplication().getSystemService(Context.NOTIFICATION_SERVICE);
        notifMgr.cancel(ONLINEUPDATESERVICE, mNotifID);

        NotificationCompat.Builder mBuilder
                = new NotificationCompat.Builder(getApplication(), NotificationHelper.SERVICE_GROUP)
                .setSmallIcon(R.drawable.ic_icon)
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(true)
                .setTicker(getApplication().getString(R.string.checking_update))
                .setContentTitle(getApplication().getString(R.string.app_name))
                .setContentText(getApplication().getString(R.string.checking_update));
        notifMgr.notify(notiID, mBuilder.build());

        String bbmID = XryptoMail.mCurUserID;
        String ServerIP = XryptoMail.CRYPTO_SERVER;
        MySqlConn sqlconn = new MySqlConn(ServerIP, bbmID);

        // Default mVersionOnServer same as installed version in cass sql access failed
        XryptoMail.mVersionOnServer = XryptoMail.mVersion;
        XryptoMail.mNewApkPathOnServer = "";

        try {
            ContentValues contentValues;
            if (BuildConfig.DEBUG) {
                // Support debug version update via network
                contentValues = sqlconn.newAppCheckup(KEY_APP_NAME_DEBUG);
            }
            else {
                contentValues = sqlconn.newAppCheckup(KEY_APP_NAME);
            }

            // proceed only if server access is successful with new contentValues
            if (contentValues != null) {
                XryptoMail.mNewApkPathOnServer = contentValues.getAsString("Path");
                XryptoMail.mVersionOnServer = contentValues.getAsString("Version");
            }
        } catch (Exception e) {
            Timber.e("Online update check failed: %s", e.getMessage());
        }

        notifMgr.cancel(notiID);
        if ((versionCompare(XryptoMail.mVersion, XryptoMail.mVersionOnServer) == -1)
                && !TextUtils.isEmpty(XryptoMail.mNewApkPathOnServer)) {

            // Event thread - Must execute in UiThread to download file
            if (mUpdateWithoutConfirm) {
                Handler mainHandler = new Handler(this.getApplicationContext().getMainLooper());
                Runnable myRunnable = () -> {
                    DownloadWithoutConfirm downloadFile = new DownloadWithoutConfirm();
                    downloadFile.execute(XryptoMail.mNewApkPathOnServer);
                };
                mainHandler.post(myRunnable);
            }
            else {
                mBuilder.setSmallIcon(R.drawable.ic_icon);
                mBuilder.setWhen(System.currentTimeMillis());
                mBuilder.setAutoCancel(true);
                mBuilder.setTicker(getApplication().getString(R.string.update_found));
                mBuilder.setContentTitle(getApplication().getString(R.string.app_name));
                mBuilder.setContentText(getApplication().getString(R.string.update_found));

                Intent intent1 = new Intent(this.getApplicationContext(), OnlineUpdate.class);
                // If, however, you will have two notification registered at once,
                // with different Intent extras, you will need to make the two
                // Intents be more materially different, such that filterEquals()
                // returns false when comparing the two. For example, you could call
                // setData() or setAction() and provide different values for each Intent
                intent1.setAction("OnlineUpdate");
                intent1.setData(Uri.parse("email://accounts/" + Integer.toString((int) System.currentTimeMillis())));
                intent1.putExtra(SKIP_VERSION_CHECK, true);
                PendingIntent pending = PendingIntent.getActivity(getApplication(), 0, intent1, PendingIntent.FLAG_CANCEL_CURRENT);
                mBuilder.setContentIntent(pending);

                //mNotifID = (int) SystemClock.uptimeMillis();
                notifMgr.notify(ONLINEUPDATESERVICE, mNotifID, mBuilder.build());
            }
        }
        StorageEditor editor = storage.edit();
        editor.putInt(LAST_VERSION_CHECK_DAY_OF_YEAR, curDayOfYear);
        editor.commit();
    }

    /**
     * @param installedVersion current installed apk version
     * @param compareVersion apk version on server
     * @return 0 = same, -1 means installedVersion < compareVersion,
     * 1 = installedVersion > compareVersion, 2 = unknown
     */
    public static int versionCompare(String installedVersion, String compareVersion)
    {
        if (TextUtils.isEmpty(installedVersion) || TextUtils.isEmpty(compareVersion))
            return 0;

        String iv = installedVersion.trim();
        int linebreak = iv.lastIndexOf('\n');
        if (linebreak != -1) {
            iv = iv.substring(0, linebreak);
        }

        String cv = compareVersion.trim();
        linebreak = cv.lastIndexOf('\n');
        if (linebreak != -1) {
            cv = cv.substring(0, linebreak);
        }

        String[] installedVer = iv.split(Pattern.quote("."));
        String[] compareVer = cv.split(Pattern.quote("."));
        int i = 0;
        try {
            // Most efficient way to skip past equal version subparts
            while (i < installedVer.length && i < compareVer.length
                    && installedVer[i].equals(compareVer[i]))
                i++;

            // If we didn't reach the end,
            if (i < installedVer.length && i < compareVer.length) {
                if (i == 3) // 4th sub version is assumed to be alpha letter
                    return installedVer[i].compareToIgnoreCase(compareVer[i]);

                // have to use integer comparison to avoid the "10" < "1" problem
                return Integer.valueOf(installedVer[i]).compareTo(Integer.valueOf(compareVer[i]));
            }

            if (i < installedVer.length) { // end of str2, check if str1 is all 0's
                boolean allZeros = true;
                for (int j = i; allZeros & (j < installedVer.length); j++)
                    allZeros &= (Integer.parseInt(installedVer[j]) == 0);
                return allZeros ? 0 : -1;
            }

            if (i < compareVer.length) { // end of str1, check if str2 is all 0's
                boolean allZeros = true;
                for (int j = i; allZeros & (j < compareVer.length); j++)
                    allZeros &= (Integer.parseInt(compareVer[j]) == 0);
                return allZeros ? 0 : 1;
            }
            return 0; // Should never happen (identical strings.)
        } catch (NumberFormatException nex) {
            if (i < compareVer.length)
                return -1;

            Timber.v("Invalid version format: %s (%s)", installedVer, compareVer);
            // Unable to complete the version comparison.
            // Return true to prevent further action (install or upgrade).
            return 2;
        }
    }
}