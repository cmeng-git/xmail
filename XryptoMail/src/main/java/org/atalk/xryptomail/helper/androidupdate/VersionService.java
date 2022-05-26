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

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import org.atalk.xryptomail.XryptoMail;

import timber.log.Timber;

/**
 * An android version service implementation. The Current version is parsed from android:versionName
 * attribute from PackageInfo.
 *
 * @author Eng Chong Meng
 */
public class VersionService
{
    private static VersionService mInstance;

    /**
     * Current version instance.
     */
    private final long CURRENT_VERSION_CODE;
    private final String CURRENT_VERSION_NAME;

    /**
     * Creates new instance of <tt>VersionServiceImpl</tt> and parses current version from
     * android:versionName attribute from PackageInfo.
     */
    public static VersionService getInstance()
    {
        if (mInstance == null)
            mInstance = new VersionService();
        return mInstance;
    }

    public VersionService()
    {
        mInstance = this;
        Context ctx = XryptoMail.getGlobalContext();
        PackageManager pckgMan = ctx.getPackageManager();
        try {
            PackageInfo pckgInfo = pckgMan.getPackageInfo(ctx.getPackageName(), 0);
            String versionName = pckgInfo.versionName;

            long versionCode;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
                versionCode = pckgInfo.versionCode;
            else
                versionCode = pckgInfo.getLongVersionCode();

            CURRENT_VERSION_NAME = versionName;
            CURRENT_VERSION_CODE = versionCode;

            // cmeng - version must all be digits, otherwise no online update
            Timber.i("Device installed with XryptoMail version: %s, version code: %s",
                    CURRENT_VERSION_NAME, CURRENT_VERSION_CODE);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get the <tt>Version</tt> of the current running XryptoMail app.
     *
     * @return the <tt>Version</tt> of the current running XryptoMail app.
     */
    public long getCurrentVersionCode()
    {
        return CURRENT_VERSION_CODE;
    }

    public String getCurrentVersionName()
    {
        return CURRENT_VERSION_NAME;
    }
}
