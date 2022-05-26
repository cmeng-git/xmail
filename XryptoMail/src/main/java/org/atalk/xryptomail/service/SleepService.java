package org.atalk.xryptomail.service;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;

import org.atalk.xryptomail.power.TracingPowerManager.TracingWakeLock;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import timber.log.Timber;

import static java.lang.Thread.currentThread;

public class SleepService extends JobIntentService
{
    public static final String SLEEP_SERVICE_SIGNATURE = ".service.SleepService.";

    private static final String ALARM_FIRED = "org.atalk.xryptomail.service.SleepService.ALARM_FIRED";
    private static final String LATCH_ID = "org.atalk.xryptomail.service.SleepService.LATCH_ID_EXTRA";

    private static final ConcurrentHashMap<Integer, SleepDatum> sleepData = new ConcurrentHashMap<>();

    private static final AtomicInteger latchId = new AtomicInteger();

    /**
     * Unique job ID for this service.
     */
    static final int JOB_ID = 1300;

    public static void startSleepServiceOn(Context context, String action)
    {
        Intent i = new Intent();
        i.setAction(action);
        enqueueWork(context, i);
    }

    public static void enqueueWork(Context context, Intent work)
    {
        enqueueWork(context, SleepService.class, JOB_ID, work);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent)
    {
        if (intent.getAction().startsWith(ALARM_FIRED)) {
            Integer id = intent.getIntExtra(LATCH_ID, -1);
            endSleep(id);
        }
    }

    public static void sleep(Context context, long sleepTime, TracingWakeLock wakeLock, long wakeLockTimeout)
    {
        Integer id = latchId.getAndIncrement();
        Timber.d("SleepService Preparing CountDownLatch with id = %d, thread %s", id, currentThread().getName());

        SleepDatum sleepDatum = new SleepDatum();
        CountDownLatch latch = new CountDownLatch(1);
        sleepDatum.latch = latch;
        sleepDatum.reacquireLatch = new CountDownLatch(1);
        sleepData.put(id, sleepDatum);

        Intent i = new Intent(context, SleepService.class);
        i.putExtra(LATCH_ID, id);
        i.setAction(ALARM_FIRED + "." + id);
        long startTime = SystemClock.elapsedRealtime();
        long nextTime = startTime + sleepTime;
        BootReceiver.scheduleIntent(context, nextTime, i);
        if (wakeLock != null) {
            sleepDatum.wakeLock = wakeLock;
            sleepDatum.timeout = wakeLockTimeout;
            wakeLock.release();
        }
        try {
            boolean countedDown = latch.await(sleepTime, TimeUnit.MILLISECONDS);
            if (!countedDown) {
                Timber.d("SleepService latch timed out for id = %d, thread %s", id, currentThread().getName());
            }
        } catch (InterruptedException ie) {
            Timber.e(ie, "SleepService Interrupted while awaiting latch");
        }
        SleepDatum releaseDatum = sleepData.remove(id);
        if (releaseDatum == null) {
            try {
                Timber.d("SleepService waiting for reacquireLatch for id = %d, thread %s",
                        id, currentThread().getName());

                if (!sleepDatum.reacquireLatch.await(5000, TimeUnit.MILLISECONDS)) {
                    Timber.w("SleepService reacquireLatch timed out for id = %d, thread %s",
                            id, currentThread().getName());
                }
                else {
                    Timber.d("SleepService reacquireLatch finished for id = %d, thread %s",
                            id, currentThread().getName());
                }
            } catch (InterruptedException ie) {
                Timber.e(ie, "SleepService Interrupted while awaiting reacquireLatch");
            }
        }

        long endTime = SystemClock.elapsedRealtime();
        long actualSleep = endTime - startTime;

        if (actualSleep < sleepTime) {
            Timber.w("SleepService sleep time too short: requested was %d, actual was %d", sleepTime, actualSleep);
        }
        else {
            Timber.d("SleepService requested sleep time was %d, actual was %d", sleepTime, actualSleep);
        }
    }

    private static void endSleep(Integer id)
    {
        if (id != -1) {
            SleepDatum sleepDatum = sleepData.remove(id);
            if (sleepDatum != null) {
                CountDownLatch latch = sleepDatum.latch;
                if (latch == null) {
                    Timber.e("SleepService No CountDownLatch available with id = %s", id);
                }
                else {
                    Timber.d("SleepService Counting down CountDownLatch with id = %d", id);
                    latch.countDown();
                }
                sleepDatum.reacquireLatch.countDown();
            }
            else {
                Timber.d("SleepService Sleep for id %d already finished", id);
            }
        }
    }

    private static class SleepDatum
    {
        CountDownLatch latch;
        TracingWakeLock wakeLock;
        long timeout;
        CountDownLatch reacquireLatch;
    }
}
