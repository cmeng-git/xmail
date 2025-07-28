package org.atalk.xryptomail.activity.misc;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.atalk.xryptomail.helper.ProgressDialog;

import timber.log.Timber;

/**
 * Extends {@link ExecutorService} with methods to attach and detach an {@link Activity}.
 *
 * <p>
 * This is necessary to properly handle configuration changes that will restart an activity.
 * </p><p>
 * <strong>Note:</strong>
 * Implementing classes need to make sure they have no reference to the {@code Activity} instance
 * that created the instance of that class. So if it's implemented as inner class, it needs to be
 * {@code static}.
 * </p>
 *
 * @see #restore(Activity)
 * @see #retain()
 */
public abstract class ExtendedExecutorService<Params, Progress, Result>
        implements NonConfigurationInstance {

    public enum Status {
        PENDING,
        RUNNING,
        FINISHED
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private Result result;
    private Future<Result> future;
    private ExecutorService executorService;
    private Status status = Status.PENDING;

    protected Activity mActivity;
    protected Context mContext;
    protected long mPDialogId;

    private void setStatus(Status status) {
        this.status = status;
    }

    protected abstract Result doInBackground(Params... params);

    protected void onPreExecute() {
        new Handler(Looper.getMainLooper()).post(this::showProgressDialog);
    }
    protected void onPostExecute(Result result) {}
    protected void onProgressUpdate(Progress progress) {}

    protected void onCancelled() {}
    protected void onCancelled(Result result) {
        this.onCancelled();
    }
    protected boolean isShutdown() {
        return this.executorService.isShutdown();
    }

    protected ExtendedExecutorService(Activity activity) {
        mActivity = activity;
        mContext = activity.getApplicationContext();
    }

    @MainThread
    public final Future<Result> execute(@Nullable Params params) {
        setStatus(Status.RUNNING);
        executorService = Executors.newSingleThreadExecutor();

        onPreExecute();
        try {
            Callable<Result> backgroundCallable = () -> doInBackground(params);
            this.future = executorService.submit(backgroundCallable);

            // pass the getResult function to return the result after completion
            executorService.submit(this.getResult());
            return future;
        } finally {
            if (executorService != null) {
                executorService.shutdown();
            }
        }
    }

    @WorkerThread
    public final void publishProgress(Progress progress) {
        if (!isCancelled()) {
            mainHandler.post(() -> onProgressUpdate(progress));
        }
    }

    @MainThread
    public final void cancel(boolean interruptRunning) {
        cancelled.set(true);
        if (future != null) {
            future.cancel(interruptRunning);
        }
    }

    @AnyThread
    public final boolean isCancelled() {
        return cancelled.get();
    }

    @AnyThread
    public final Status getStatus() {
        return this.status;
    }

    private Runnable getResult() {
        return () -> {
            try {
                if (!isCancelled()) {
                    result = future.get();
                    mainHandler.post(() -> onPostExecute(result));
                } else {
                    mainHandler.post(this::onCancelled);
                }

                // update the task status as finished
                setStatus(Status.FINISHED);

            } catch (InterruptedException | ExecutionException e) {
                Timber.e("Exception occurred while trying to get result: %s", e.getMessage());
            }
        };
    }

    /**
     * Creates a {@link ProgressDialog} that is shown while the background thread is running.
     * This needs to store a {@code ProgressDialog} instance in {@link #mPDialogId} or
     * override {@link #removeProgressDialog()}.
     */
    protected abstract void showProgressDialog();

    protected void removeProgressDialog() {
        ProgressDialog.dismiss(mPDialogId);
    }

    /**
     * Detach this {@link ExecutorService} from the {@link Activity} it was bound to.
     * This needs to be called when the current activity is being destroyed
     * during an activity restart due to a configuration change.<br/>
     * We also have to destroy the progress dialog because it's bound to the
     * activity that's being destroyed.
     *
     * @return {@code true} if this instance should be retained; {@code false} otherwise.
     *
     * @see Activity#onRetainNonConfigurationInstance()
     */
    @Override
    public boolean retain() {
        boolean retain = false;
        if (mPDialogId != -1) {
            removeProgressDialog();
            retain = true;
        }
        mActivity = null;

        return retain;
    }

    /**
     * Connect this {@link ExecutorService} to a new {@link Activity} instance after
     * the activity was restarted due to a configuration change.
     * This also creates a new progress dialog that is bound to the new activity.
     *
     * @param activity The new {@code Activity} instance. Never {@code null}.
     */
    @Override
    public void restore(Activity activity) {
        mActivity = activity;
        showProgressDialog();
    }
}
