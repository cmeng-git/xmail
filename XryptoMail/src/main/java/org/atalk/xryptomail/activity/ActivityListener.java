package org.atalk.xryptomail.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.text.format.DateUtils;

import net.jcip.annotations.GuardedBy;

import org.atalk.xryptomail.Account;
import org.atalk.xryptomail.AccountStats;
import org.atalk.xryptomail.R;
import org.atalk.xryptomail.controller.SimpleMessagingListener;
import org.atalk.xryptomail.service.MailService;

public class ActivityListener extends SimpleMessagingListener
{
    private final Object lock = new Object();
    @GuardedBy("lock")
    private Account mAccount = null;
    @GuardedBy("lock")
    private String mLoadingFolderName = null;
    @GuardedBy("lock")
    private String mLoadingHeaderFolderName = null;
    @GuardedBy("lock")
    private String mLoadingAccountDescription = null;
    @GuardedBy("lock")
    private String mSendingAccountDescription = null;
    @GuardedBy("lock")
    private int mFolderCompleted = 0;
    @GuardedBy("lock")
    private int mFolderTotal = 0;
    @GuardedBy("lock")
    private String mProcessingAccountDescription = null;
    @GuardedBy("lock")
    private String mProcessingCommandTitle = null;

    private BroadcastReceiver mTickReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            informUserOfStatus();
        }
    };

    public String getOperation(Context context)
    {
        synchronized (lock) {
            if (mLoadingAccountDescription != null
                    || mSendingAccountDescription != null
                    || mLoadingHeaderFolderName != null
                    || mProcessingAccountDescription != null) {

                return getActionInProgressOperation(context);
            }
        }

        long nextPollTime = MailService.getNextPollTime();
        if (nextPollTime != -1) {
            CharSequence relativeTimeSpanString = DateUtils.getRelativeTimeSpanString(
                    nextPollTime, System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS, 0);
            return context.getString(R.string.status_next_poll, relativeTimeSpanString);
        }
        // cmeng - Always display actual network status to user
        else if (MailService.isSyncDisabled()) {
            if (MailService.hasNoConnectivity()) {
                return context.getString(R.string.status_no_network);
            }
            else if (MailService.isSyncNoBackground()) {
                return context.getString(R.string.status_no_background);
            }
            else if (MailService.isSyncBlocked()) {
                return context.getString(R.string.status_syncing_blocked);
            }
            else if (MailService.isPollAndPushDisabled()) {
                return context.getString(R.string.status_poll_and_push_disabled);
            }
            else {
                return context.getString(R.string.status_syncing_off);
            }
        }
        else {
            return "";
        }
    }

    @GuardedBy("lock")
    private String getActionInProgressOperation(Context context)
    {
        String progress = ((mFolderTotal > 0)
                ? context.getString(R.string.folder_progress, mFolderCompleted, mFolderTotal) : "");

        if (mLoadingFolderName != null || mLoadingHeaderFolderName != null) {
            String displayName = null;
            if (mLoadingHeaderFolderName != null) {
                displayName = mLoadingHeaderFolderName;
            }
            else {
                displayName = mLoadingFolderName;
            }

            if ((mAccount != null)
                    && (displayName.equalsIgnoreCase(mAccount.getInboxFolderName()))) {
                displayName = context.getString(R.string.special_mailbox_name_inbox);
            }
            else if (displayName.equalsIgnoreCase(mAccount.getOutboxFolderName())) {
                displayName = context.getString(R.string.special_mailbox_name_outbox);
            }

            if (mLoadingHeaderFolderName != null) {
                return context.getString(R.string.status_loading_account_folder_headers,
                        mLoadingAccountDescription, displayName, progress);
            }
            else {
                return context.getString(R.string.status_loading_account_folder,
                        mLoadingAccountDescription, displayName, progress);
            }
        }
        else if (mSendingAccountDescription != null) {
            return context.getString(R.string.status_sending_account, mSendingAccountDescription, progress);
        }
        else if (mProcessingAccountDescription != null) {
            return context.getString(R.string.status_processing_account, mProcessingAccountDescription,
                    mProcessingCommandTitle != null ? mProcessingCommandTitle : "", progress);
        }
        else {
            return "";
        }
    }

    public void onResume(Context context)
    {
        context.registerReceiver(mTickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
    }

    public void onPause(Context context)
    {
        context.unregisterReceiver(mTickReceiver);
    }

    public void informUserOfStatus()
    {
    }

    @Override
    public void synchronizeMailboxFinished(Account account, String folder, int totalMessagesInMailbox, int numNewMessages)
    {
        synchronized (lock) {
            mLoadingAccountDescription = null;
            mLoadingFolderName = null;
            mAccount = null;
        }
        informUserOfStatus();
    }

    @Override
    public void synchronizeMailboxStarted(Account account, String folder)
    {
        synchronized (lock) {
            mLoadingAccountDescription = account.getDescription();
            mLoadingFolderName = folder;
            mAccount = account;
            mFolderCompleted = 0;
            mFolderTotal = 0;
        }
        informUserOfStatus();
    }

    @Override
    public void synchronizeMailboxHeadersStarted(Account account, String folder)
    {
        synchronized (lock) {
            mLoadingAccountDescription = account.getDescription();
            mLoadingHeaderFolderName = folder;
        }
        informUserOfStatus();
    }

    @Override
    public void synchronizeMailboxHeadersProgress(Account account, String folder, int completed, int total)
    {
        synchronized (lock) {
            mFolderCompleted = completed;
            mFolderTotal = total;
        }
        informUserOfStatus();
    }

    @Override
    public void synchronizeMailboxHeadersFinished(Account account, String folder, int total, int completed)
    {
        synchronized (lock) {
            mLoadingHeaderFolderName = null;
            mFolderCompleted = 0;
            mFolderTotal = 0;
        }
        informUserOfStatus();
    }

    @Override
    public void synchronizeMailboxProgress(Account account, String folder, int completed, int total)
    {
        synchronized (lock) {
            mFolderCompleted = completed;
            mFolderTotal = total;
        }
        informUserOfStatus();
    }

    @Override
    public void synchronizeMailboxFailed(Account account, String folder, String message)
    {
        synchronized (lock) {
            mLoadingAccountDescription = null;
            mLoadingHeaderFolderName = null;
            mLoadingFolderName = null;
            mAccount = null;
        }
        informUserOfStatus();
    }

    @Override
    public void sendPendingMessagesStarted(Account account)
    {
        synchronized (lock) {
            mSendingAccountDescription = account.getDescription();
        }
        informUserOfStatus();
    }

    @Override
    public void sendPendingMessagesCompleted(Account account)
    {
        synchronized (lock) {
            mSendingAccountDescription = null;
        }
        informUserOfStatus();
    }

    @Override
    public void sendPendingMessagesFailed(Account account)
    {
        synchronized (lock) {
            mSendingAccountDescription = null;
        }
        informUserOfStatus();
    }

    @Override
    public void pendingCommandsProcessing(Account account)
    {
        synchronized (lock) {
            mProcessingAccountDescription = account.getDescription();
            mFolderCompleted = 0;
            mFolderTotal = 0;
        }
        informUserOfStatus();
    }

    @Override
    public void pendingCommandsFinished(Account account)
    {
        synchronized (lock) {
            mProcessingAccountDescription = null;
        }
        informUserOfStatus();
    }

    @Override
    public void pendingCommandStarted(Account account, String commandTitle)
    {
        synchronized (lock) {
            mProcessingCommandTitle = commandTitle;
        }
        informUserOfStatus();
    }

    @Override
    public void pendingCommandCompleted(Account account, String commandTitle)
    {
        synchronized (lock) {
            mProcessingCommandTitle = null;
        }
        informUserOfStatus();
    }

    @Override
    public void searchStats(AccountStats stats)
    {
        informUserOfStatus();
    }

    @Override
    public void systemStatusChanged()
    {
        informUserOfStatus();
    }

    @Override
    public void folderStatusChanged(Account account, String folder, int unreadMessageCount)
    {
        informUserOfStatus();
    }

    public int getFolderCompleted()
    {
        synchronized (lock) {
            return mFolderCompleted;
        }
    }

    public int getFolderTotal()
    {
        synchronized (lock) {
            return mFolderTotal;
        }
    }
}
