package org.atalk.xryptomail.service;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;

import org.atalk.xryptomail.Account;
import org.atalk.xryptomail.controller.MessagingController;
import org.atalk.xryptomail.controller.SimpleMessagingListener;
import org.atalk.xryptomail.power.TracingPowerManager.TracingWakeLock;

import java.util.HashMap;
import java.util.Map;

import timber.log.Timber;

public class PollService extends JobIntentService
{
    private static final String START_SERVICE = "org.atalk.xryptomail.service.PollService.startService";
    private static final String STOP_SERVICE = "org.atalk.xryptomail.service.PollService.stopService";

    private Listener mListener = new Listener();

    public static void startService(Context context)
    {
        Intent i = new Intent();
        i.setAction(PollService.START_SERVICE);
        enqueueWork(context, i);
    }

    public static void stopService(Context context)
    {
        Intent i = new Intent();
        i.setAction(PollService.STOP_SERVICE);
        enqueueWork(context, i);
    }

    /**
     * Unique job ID for this service.
     */
    static final int JOB_ID = 1100;

    public static void enqueueWork(Context context, Intent work)
    {
        enqueueWork(context, PollService.class, JOB_ID, work);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent)
    {
        if (START_SERVICE.equals(intent.getAction())) {
            Timber.i("***** PollService started *****");

            MessagingController controller = MessagingController.getInstance(getApplication());
            Listener listener = (Listener) controller.getCheckMailListener();
            if (listener == null) {
                Timber.i("***** PollService *****: starting new check");
                controller.setCheckMailListener(mListener);
                controller.checkMail(this, null, false, false, mListener);
            }
        }
        else if (STOP_SERVICE.equals(intent.getAction())) {
            Timber.i("PollService stopping");
            stopSelf();
        }
    }

    class Listener extends SimpleMessagingListener
    {
        Map<String, Integer> accountsChecked = new HashMap<>();
        private TracingWakeLock wakeLock = null;

        @Override
        public void checkMailStarted(Context context, Account account)
        {
            accountsChecked.clear();
        }

        @Override
        public void synchronizeMailboxFinished(
                Account account,
                String folder,
                int totalMessagesInMailbox,
                int numNewMessages)
        {
            if (account.isNotifyNewMail()) {
                Integer existingNewMessages = accountsChecked.get(account.getUuid());
                if (existingNewMessages == null) {
                    existingNewMessages = 0;
                }
                accountsChecked.put(account.getUuid(), existingNewMessages + numNewMessages);
            }
        }

        private void release()
        {
            MessagingController controller = MessagingController.getInstance(getApplication());
            controller.setCheckMailListener(null);
            MailService.saveLastCheckEnd(getApplication());
            MailService.actionReschedulePoll(PollService.this);
        }

        @Override
        public void checkMailFinished(Context context, Account account)
        {
            Timber.v("***** PollService *****: checkMailFinished");
            release();
        }
    }
}
