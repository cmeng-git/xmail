/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.xryptomail;

import android.os.Handler;

import java.util.*;

import timber.log.Timber;
/**
 * This class used to "throttle" a flow of events.
 *
 * When {@link #onEvent()} is called, it calls the callback in a certain timeout later.
 * Initially {@link #minTimeout} is used as the timeout, but if it gets multiple {@link #onEvent}
 * calls in a certain amount of time, it extends the timeout, until it reaches {@link #maxTimeout}.
 *
 * This class is primarily used to throttle content changed events.
 */
public class Throttle {
    private static final int TIMEOUT_EXTEND_INTERVAL = 500;

    private static Timer TIMER = new Timer();

    private final Clock mClock;
    private final Timer mTimer;

    /** Name of the instance.  Only for logging. */
    private final String mName;

    /** Handler for UI thread. */
    private final Handler mHandler;

    /** Callback to be called */
    private final Runnable mCallback;

    /** Minimum (default) timeout, in milliseconds.  */
    private final int mMinTimeout;

    /** Max timeout, in milliseconds.  */
    private final int mMaxTimeout;

    /** Current timeout, in milliseconds. */
    private int mCurrentTimeout;

    /** When {@link #onEvent()} was last called. */
    private long mLastEventTime;

    private MyTimerTask mRunningTimerTask;

    /** Constructor that takes custom timeout */
    public Throttle(String name, Runnable callback, Handler handler,int minTimeout,
            int maxTimeout) {
        this(name, callback, handler, minTimeout, maxTimeout, Clock.INSTANCE, TIMER);
    }

    /** Constructor for tests */
    private Throttle(String name, Runnable callback, Handler handler, int minTimeout,
            int maxTimeout, Clock clock, Timer timer) {
        if (maxTimeout < minTimeout) {
            throw new IllegalArgumentException();
        }
        mName = name;
        mCallback = callback;
        mClock = clock;
        mTimer = timer;
        mHandler = handler;
        mMinTimeout = minTimeout;
        mMaxTimeout = maxTimeout;
        mCurrentTimeout = mMinTimeout;
    }

    private boolean isCallbackScheduled() {
        return mRunningTimerTask != null;
    }

    public void cancelScheduledCallback() {
        if (mRunningTimerTask != null) {
            Timber.d("Throttle: [%s] Canceling scheduled callback", mName);
            mRunningTimerTask.cancel();
            mRunningTimerTask = null;
        }
    }

    private void updateTimeout() {
        final long now = mClock.getTime();
        if ((now - mLastEventTime) <= TIMEOUT_EXTEND_INTERVAL) {
            mCurrentTimeout *= 2;
            if (mCurrentTimeout >= mMaxTimeout) {
                mCurrentTimeout = mMaxTimeout;
            }
            Timber.d("Throttle: [%s] Timeout extended %d", mName, mCurrentTimeout);
        } else {
            mCurrentTimeout = mMinTimeout;
            Timber.d("Throttle: [%s] Timeout reset to %d", mName, mCurrentTimeout);
        }

        mLastEventTime = now;
    }

    public void onEvent() {
        Timber.d("Throttle: [%s] onEvent", mName);

        updateTimeout();

        if (isCallbackScheduled()) {
            Timber.d("Throttle: [%s]     callback already scheduled", mName);
        } else {
            Timber.d("Throttle: [%s]     scheduling callback", mName);
            mRunningTimerTask = new MyTimerTask();
            mTimer.schedule(mRunningTimerTask, mCurrentTimeout);
        }
    }

    /**
     * Timer task called on timeout,
     */
    private class MyTimerTask extends TimerTask {
        private boolean mCanceled;

        @Override
        public void run() {
            mHandler.post(new HandlerRunnable());
        }

        @Override
        public boolean cancel() {
            mCanceled = true;
            return super.cancel();
        }

        private class HandlerRunnable implements Runnable {
            @Override
            public void run() {
                mRunningTimerTask = null;
                if (!mCanceled) { // This check has to be done on the UI thread.
                    Timber.d("Throttle: [%s] Kicking callback", mName);
                    mCallback.run();
                }
            }
        }
    }
}
