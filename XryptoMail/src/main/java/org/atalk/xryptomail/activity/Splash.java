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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Window;

import org.atalk.xryptomail.BuildConfig;
import org.atalk.xryptomail.R;
import org.atalk.xryptomail.XryptoMail;
import org.atalk.xryptomail.service.OnlineUpdateService;

import java.io.File;

/**
 * Splash screen activity
 */
public class Splash extends XMActivity
{
    private final static int ONLINE_UPDATE = 10;

    // in unit of milliseconds
    private static final int SPLASH_SCREEN_SHOW_TIME = 1000;
    private static boolean mFirstRun = true;
    private Intent nextIntent = null;

    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.splash);

        nextIntent = getIntent().getParcelableExtra(XryptoMail.NEXT_INTENT);
        // run a thread with delay SPLASH_SCREEN_SHOW_TIME before returning to defined home screen
        Handler handler = new Handler();
        handler.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                // Start update service for debug version only
                if (BuildConfig.DEBUG) {
                    startService(new Intent(getApplicationContext(), OnlineUpdateService.class));
                }
                // no further action - exit splash screen
                finish();
            }
        }, SPLASH_SCREEN_SHOW_TIME); // wait time in milliseconds until the run() method will be called
        mFirstRun = false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == ONLINE_UPDATE) {
            if (OnlineUpdate.OnlineUpdateStatus.DOWNLOAD_APK_SUCCESSFUL.getValue() != resultCode) {
                Intent intent = new Intent(Splash.this, Accounts.class);
                Splash.this.startActivity(intent);
            }
            else {
                if (!TextUtils.isEmpty(XryptoMail.mUpdateApkPath)) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.fromFile(new File(XryptoMail.mUpdateApkPath)),
                            "application/vnd.android.package-archive");
                    startActivity(intent);
                }
            }
            finish();
        }
    }

    static public boolean isFirstRun()
    {
        return mFirstRun;
    }
}
