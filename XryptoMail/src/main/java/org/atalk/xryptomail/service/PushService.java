package org.atalk.xryptomail.service;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;

import timber.log.Timber;

public class PushService extends JobIntentService
{
    private static final String START_SERVICE = "org.atalk.xryptomail.service.PushService.startService";
    private static final String STOP_SERVICE = "org.atalk.xryptomail.service.PushService.stopService";

    public static void startService(Context context)
    {
        Intent i = new Intent();
        i.setAction(PushService.START_SERVICE);
        enqueueWork(context, i);
    }

    public static void stopService(Context context)
    {
        Intent i = new Intent();
        i.setAction(PushService.STOP_SERVICE);
        enqueueWork(context, i);
    }

    /**
     * Unique job ID for this service.
     */
    static final int JOB_ID = 1200;

    public static void enqueueWork(Context context, Intent work)
    {
        enqueueWork(context, PushService.class, JOB_ID, work);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent)
    {
        if (START_SERVICE.equals(intent.getAction())) {
            Timber.i("***** PushService started *****");
        }
        else if (STOP_SERVICE.equals(intent.getAction())) {
            Timber.i("***** PushService stopping *****");
            stopSelf();
        }
    }
}
