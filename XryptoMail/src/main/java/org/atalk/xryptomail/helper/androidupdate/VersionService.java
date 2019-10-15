/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.xryptomail.helper.androidupdate;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.atalk.xryptomail.XryptoMail;

import timber.log.Timber;

/**
 * Android version service implementation. Current version is parsed from android:versionName
 * attribute from PackageInfo.
 *
 * @author Eng Chong Meng
 */
public class VersionService
{
    /**
     * Current version instance.
     */
    private final int CURRENT_VERSION_CODE;

    private final String CURRENT_VERSION_NAME;

    /**
     * Creates new instance of <tt>VersionServiceImpl</tt> and parses current version from
     * android:versionName attribute from PackageInfo.
     */
    public VersionService()
    {
        Context ctx = XryptoMail.getGlobalContext();
        PackageManager pckgMan = ctx.getPackageManager();
        try {
            PackageInfo pckgInfo = pckgMan.getPackageInfo(ctx.getPackageName(), 0);

            CURRENT_VERSION_NAME = pckgInfo.versionName;
            CURRENT_VERSION_CODE = pckgInfo.versionCode;

            // cmeng - version must all be digits, otherwise no online update
            Timber.i("Device installed with aTalk-android version: %s, version code: %s",
                    CURRENT_VERSION_NAME, CURRENT_VERSION_CODE);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns a <tt>Version</tt> object containing version details of the Jitsi version that
     * we're currently running.
     *
     * @return a <tt>Version</tt> object containing version details of the Jitsi version that
     * we're currently running.
     */
    public int getCurrentVersionCode()
    {
        return CURRENT_VERSION_CODE;
    }

    public String getCurrentVersionName()
    {
        return CURRENT_VERSION_NAME;
    }
}
