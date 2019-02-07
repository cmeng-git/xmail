package org.atalk.xryptomail.service;

import android.content.*;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.app.JobIntentService;

import org.atalk.xryptomail.*;
import org.atalk.xryptomail.Account.FolderMode;
import org.atalk.xryptomail.controller.MessagingController;
import org.atalk.xryptomail.helper.Utility;
import org.atalk.xryptomail.mail.Pusher;
import org.atalk.xryptomail.preferences.Storage;
import org.atalk.xryptomail.preferences.StorageEditor;

import java.util.*;

import timber.log.Timber;

public class MailService extends JobIntentService
{
    public static final String MAIL_SERVICE_SIGNATURE = ".intent.action.MAIL_SERVICE_";

    private static final String ACTION_CHECK_MAIL = "org.atalk.xryptomail.intent.action.MAIL_SERVICE_WAKEUP";
    private static final String ACTION_RESET = "org.atalk.xryptomail.intent.action.MAIL_SERVICE_RESET";
    private static final String ACTION_RESCHEDULE_POLL = "org.atalk.xryptomail.intent.action.MAIL_SERVICE_RESCHEDULE_POLL";
    private static final String ACTION_CANCEL = "org.atalk.xryptomail.intent.action.MAIL_SERVICE_CANCEL";
    private static final String ACTION_REFRESH_PUSHERS = "org.atalk.xryptomail.intent.action.MAIL_SERVICE_REFRESH_PUSHERS";
    private static final String ACTION_RESTART_PUSHERS = "org.atalk.xryptomail.intent.action.MAIL_SERVICE_RESTART_PUSHERS";
    private static final String CONNECTIVITY_CHANGE = "org.atalk.xryptomail.intent.action.MAIL_SERVICE_CONNECTIVITY_CHANGE";
    private static final String CANCEL_CONNECTIVITY_NOTICE = "org.atalk.xryptomail.intent.action.MAIL_SERVICE_CANCEL_CONNECTIVITY_NOTICE";

    private static long nextCheck = -1;
    private static boolean pushingRequested = false;
    private static boolean pollingRequested = false;
    private static boolean syncNoBackground = false;

    public static void actionReset(Context context)
    {
        Intent i = new Intent();
        i.setAction(MailService.ACTION_RESET);
        enqueueWork(context, i);
    }

    public static void actionRestartPushers(Context context)
    {
        Intent i = new Intent();
        i.setAction(MailService.ACTION_RESTART_PUSHERS);
        enqueueWork(context, i);
    }

    public static void actionReschedulePoll(Context context)
    {
        Intent i = new Intent();
        i.setAction(MailService.ACTION_RESCHEDULE_POLL);
        enqueueWork(context, i);
    }

    public static void actionCancel(Context context)
    {
        Intent i = new Intent();
        i.setAction(MailService.ACTION_CANCEL);
        enqueueWork(context, i);
    }

    public static void connectivityChange(Context context)
    {
        Intent i = new Intent();
        i.setAction(MailService.CONNECTIVITY_CHANGE);
        enqueueWork(context, i);
    }

    public static void startMailServiceOn(Context context, String action)
    {
        Intent i = new Intent();
        i.setAction(action);
        enqueueWork(context, i);
    }

    /**
     * Unique job ID for this service.
     */
    static final int JOB_ID = 1000;

    public static void enqueueWork(Context context, Intent work)
    {
        enqueueWork(context, MailService.class, JOB_ID, work);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent)
    {
        long startTime = SystemClock.elapsedRealtime();
        boolean oldIsSyncDisabled = isSyncDisabled();
        boolean doBackground;

        boolean hasConnectivity = Utility.hasConnectivity(getApplication());
        boolean autoSync = ContentResolver.getMasterSyncAutomatically();
        XryptoMail.BACKGROUND_OPS bOps = XryptoMail.getBackgroundOps();
        switch (bOps) {
            case NEVER:
                doBackground = false;
                break;
            case ALWAYS:
                doBackground = true;
                break;
            case WHEN_CHECKED_AUTO_SYNC:
                doBackground = autoSync;
                break;
            default:
                doBackground = true;
        }

        syncNoBackground = !doBackground;
        Timber.i("***** MailService Handle Work = %s, hasConnectivity = %s, doBackground = %s",
                intent, hasConnectivity, doBackground);

        // MessagingController.getInstance(getApplication()).addListener(mListener);
        switch (Objects.requireNonNull(intent.getAction())) {
            case ACTION_CHECK_MAIL:
                if (hasConnectivity && doBackground) {
                    Timber.i("***** MailService *****: polling mail");
                    PollService.startService(this);
                }
                reschedulePollInBackground(hasConnectivity, doBackground, false);
                break;
            case ACTION_CANCEL:
                Timber.v("***** MailService *****: cancel");
                cancel();
                break;
            case ACTION_RESET:
                Timber.v("***** MailService *****: reschedule");
                rescheduleAllInBackground(hasConnectivity, doBackground);
                break;
            case ACTION_RESTART_PUSHERS:
                Timber.v("***** MailService *****: restarting pushers");
                reschedulePushersInBackground(hasConnectivity, doBackground);
                break;
            case ACTION_RESCHEDULE_POLL:
                Timber.v("***** MailService *****: rescheduling poll");
                reschedulePollInBackground(hasConnectivity, doBackground, true);
                break;
            case ACTION_REFRESH_PUSHERS:
                refreshPushersInBackground(hasConnectivity, doBackground);
                break;
            case CONNECTIVITY_CHANGE:
                rescheduleAllInBackground(hasConnectivity, doBackground);
                Timber.i("Got connectivity action with hasConnectivity = %s, doBackground = %s",
                        hasConnectivity, doBackground);
                break;
            case CANCEL_CONNECTIVITY_NOTICE:
                /* do nothing */
                break;
        }
        if (isSyncDisabled() != oldIsSyncDisabled) {
            MessagingController.getInstance(getApplication()).systemStatusChanged();
        }
        Timber.i("MailService.onStart took %d ms", SystemClock.elapsedRealtime() - startTime);
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        Timber.v("***** MailService *****: onCreate");
    }

    @Override
    public void onDestroy()
    {
        Timber.v("***** MailService *****: onDestroy()");
        super.onDestroy();
    }

    private void cancel()
    {
        Intent i = new Intent(this, MailService.class);
        i.setAction(ACTION_CHECK_MAIL);
        BootReceiver.cancelIntent(this, i);
    }

    private final static String PREVIOUS_INTERVAL = "MailService.previousInterval";
    private final static String LAST_CHECK_END = "MailService.lastCheckEnd";

    public static void saveLastCheckEnd(Context context)
    {
        long lastCheckEnd = System.currentTimeMillis();
        Timber.i("Saving lastCheckEnd = %tc", new Date(lastCheckEnd));

        Preferences prefs = Preferences.getPreferences(context);
        Storage storage = prefs.getStorage();
        StorageEditor editor = storage.edit();
        editor.putLong(LAST_CHECK_END, lastCheckEnd);
        editor.commit();
    }

    private void rescheduleAllInBackground(final boolean hasConnectivity, final boolean doBackground)
    {
        reschedulePoll(hasConnectivity, doBackground, true);
        reschedulePushers(hasConnectivity, doBackground);
    }

    private void reschedulePollInBackground(final boolean hasConnectivity, final boolean doBackground,
            final boolean considerLastCheckEnd)
    {
        reschedulePoll(hasConnectivity, doBackground, considerLastCheckEnd);
    }

    private void reschedulePushersInBackground(final boolean hasConnectivity, final boolean doBackground)
    {
        reschedulePushers(hasConnectivity, doBackground);
    }

    private void refreshPushersInBackground(boolean hasConnectivity, boolean doBackground)
    {
        if (hasConnectivity && doBackground) {
            refreshPushers();
            schedulePushers();
        }
    }

    private void reschedulePoll(final boolean hasConnectivity, final boolean doBackground, boolean considerLastCheckEnd)
    {
        if (!(hasConnectivity && doBackground)) {
            Timber.i("No connectivity, canceling check for %s", getApplication().getPackageName());
            nextCheck = -1;
            cancel();
            return;
        }

        Preferences prefs = Preferences.getPreferences(MailService.this);
        Storage storage = prefs.getStorage();
        int previousInterval = storage.getInt(PREVIOUS_INTERVAL, -1);
        long lastCheckEnd = storage.getLong(LAST_CHECK_END, -1);

        // find the shortest autotmatic mail check interval amongst all accounts
        int shortestInterval = -1;
        for (Account account : prefs.getAvailableAccounts()) {
            if (account.getAutomaticCheckIntervalMinutes() != -1
                    && account.getFolderSyncMode() != FolderMode.NONE
                    && (account.getAutomaticCheckIntervalMinutes() < shortestInterval
                    || shortestInterval == -1)) {
                shortestInterval = account.getAutomaticCheckIntervalMinutes();
            }
        }

        // Invalidate the lastCheckEnd if the value is older than (shortestInterval + 1)
        long now = System.currentTimeMillis();
        if ((lastCheckEnd > now) || (now - lastCheckEnd) > (shortestInterval + 1) * 60 * 60 * 1000) {
            Timber.w("Database has invalid last mail check time (%tc). Reset it to present: %tc",
                    new Date(lastCheckEnd), new Date());
            lastCheckEnd = now;
        }

        StorageEditor editor = storage.edit();
        editor.putInt(PREVIOUS_INTERVAL, shortestInterval);
        editor.commit();

        if (shortestInterval == -1) {
            Timber.i("No next check scheduled for package %s", getApplication().getPackageName());
            nextCheck = -1;
            pollingRequested = false;
            cancel();
        }
        else {
            long delay = (shortestInterval * (60 * 1000));
            long base = (previousInterval == -1 || lastCheckEnd == -1
                    || !considerLastCheckEnd ? System.currentTimeMillis() : lastCheckEnd);
            long nextTime = base + delay;

            Timber.i("previousInterval = %d, shortestInterval = %d, lastCheckEnd = %tc, considerLastCheckEnd = %b",
                    previousInterval, shortestInterval, new Date(lastCheckEnd), considerLastCheckEnd);

            nextCheck = nextTime;
            pollingRequested = true;
            try {
                Timber.i("Next check for package %s scheduled for %tc", getApplication().getPackageName(),
                        new Date(nextTime));
            } catch (Exception e) {
                // I once got a NullPointerException deep in new Date();
                Timber.e(e, "Exception while logging");
            }
            Intent i = new Intent(this, MailService.class);
            i.setAction(ACTION_CHECK_MAIL);
            BootReceiver.scheduleIntent(MailService.this, nextTime, i);
        }
    }

    public static boolean isSyncDisabled()
    {
        return isSyncBlocked() || (!pollingRequested && !pushingRequested);
    }

    public static boolean hasNoConnectivity()
    {
        // cmeng - connectivity may change after service start.
        return !Utility.hasConnectivity(XryptoMail.instance);
    }

    public static boolean isSyncNoBackground()
    {
        return syncNoBackground;
    }

    public static boolean isSyncBlocked()
    {
        // syncBlocked = !(doBackground && hasConnectivity);
        return syncNoBackground || !hasNoConnectivity();
    }

    public static boolean isPollAndPushDisabled()
    {
        return (!pollingRequested && !pushingRequested);
    }

    private void stopPushers()
    {
        MessagingController.getInstance(getApplication()).stopAllPushing();
        PushService.stopService(MailService.this);
    }

    private void reschedulePushers(boolean hasConnectivity, boolean doBackground)
    {
        Timber.i("Rescheduling pushers");
        stopPushers();
        if (!(hasConnectivity && doBackground)) {
            Timber.i("Not scheduling pushers:  connectivity? %s -- doBackground? %s", hasConnectivity, doBackground);
            return;
        }
        setupPushers();
        schedulePushers();
    }

    private void setupPushers()
    {
        boolean pushing = false;
        for (Account account : Preferences.getPreferences(MailService.this).getAccounts()) {
            Timber.i("Setting up pushers for account %s", account.getDescription());

            if (account.isEnabled() && account.isAvailable(getApplicationContext())) {
                pushing |= MessagingController.getInstance(getApplication()).setupPushing(account);
            }
            else {
                // TODO: setupPushing of unavailable accounts when they become available (sd-card inserted)
            }
        }
        if (pushing) {
            PushService.startService(MailService.this);
        }
        pushingRequested = pushing;
    }

    private void refreshPushers()
    {
        try {
            long nowTime = System.currentTimeMillis();
            Timber.i("Refreshing pushers");

            Collection<Pusher> pushers = MessagingController.getInstance(getApplication()).getPushers();
            for (Pusher pusher : pushers) {
                long lastRefresh = pusher.getLastRefresh();
                int refreshInterval = pusher.getRefreshInterval();
                long sinceLast = nowTime - lastRefresh;
                if (sinceLast + 10000 > refreshInterval) { // Add 10 seconds to keep pushers in sync, avoid drift
                    Timber.d("PUSHREFRESH: refreshing lastRefresh = %d, interval = %d, nowTime = %d, " +
                            "sinceLast = %d", lastRefresh, refreshInterval, nowTime, sinceLast);
                    pusher.refresh();
                    pusher.setLastRefresh(nowTime);
                }
                else {
                    Timber.d("PUSHREFRESH: NOT refreshing lastRefresh = %d, interval = %d, nowTime = %d, " +
                            "sinceLast = %d", lastRefresh, refreshInterval, nowTime, sinceLast);
                }
            }
            // Whenever we refresh our pushers, send any unsent messages
            Timber.d("PUSHREFRESH:  trying to send mail in all folders!");
            MessagingController.getInstance(getApplication()).sendPendingMessages(null);
        } catch (Exception e) {
            Timber.e(e, "Exception while refreshing pushers");
        }
    }

    private void schedulePushers()
    {
        int minInterval = -1;
        Collection<Pusher> pushers = MessagingController.getInstance(getApplication()).getPushers();
        for (Pusher pusher : pushers) {
            int interval = pusher.getRefreshInterval();
            if (interval > 0 && (interval < minInterval || minInterval == -1)) {
                minInterval = interval;
            }
        }

        Timber.v("Pusher refresh interval = %d", minInterval);
        if (minInterval > 0) {
            long nextTime = System.currentTimeMillis() + minInterval;
            Timber.d("Next pusher refresh scheduled for %tc", nextTime);

            Intent i = new Intent(this, MailService.class);
            i.setAction(ACTION_REFRESH_PUSHERS);
            BootReceiver.scheduleIntent(MailService.this, nextTime, i);
        }
    }

    public static long getNextPollTime()
    {
        return nextCheck;
    }
}
