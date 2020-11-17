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
import android.os.Bundle;
import android.os.Handler;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

import androidx.fragment.app.FragmentActivity;

import org.atalk.xryptomail.*;
import org.atalk.xryptomail.helper.androidupdate.OnlineUpdateService;
import org.atalk.xryptomail.helper.androidupdate.UpdateService;

/**
 * Splash screen activity
 */
public class Splash extends FragmentActivity
{
    private final static int ONLINE_UPDATE = 10;

    // in unit of milliseconds
    private static final int SPLASH_SCREEN_SHOW_TIME = 1000;
    private static boolean mFirstRun = true;
    private Intent nextIntent = null;

    // Show the splash screen if first launch and wait for it to complete before continue
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        // Request indeterminate progress for splash screen
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setProgressBarIndeterminateVisibility(true);
        setContentView(R.layout.splash);

        // Starts fade in animation
        ImageView myImageView = findViewById(R.id.loadingImage);
        Animation myFadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        myImageView.startAnimation(myFadeInAnimation);
        mFirstRun = false;

        // run a thread with delay SPLASH_SCREEN_SHOW_TIME before returning to defined home screen
        new Handler().postDelayed(() -> {
            // Start update service for debug version only
            if (BuildConfig.DEBUG) {
                UpdateService.getInstance().removeOldDownloads();
                Intent dailyCheckupIntent = new Intent(getApplicationContext(), OnlineUpdateService.class);
                dailyCheckupIntent.setAction(OnlineUpdateService.ACTION_AUTO_UPDATE_START);
                startService(dailyCheckupIntent);
            }
            // must exit splash screen
            finish();
        }, SPLASH_SCREEN_SHOW_TIME);
    }

    static public boolean isFirstRun()
    {
        return mFirstRun;
    }
}
