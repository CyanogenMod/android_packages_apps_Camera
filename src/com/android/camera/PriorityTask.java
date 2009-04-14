// Copyright 2009 Google Inc. All Rights Reserved.

package com.android.camera;

import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Abstract class used for Tasks which can be canceled / change priority. The
 * task should be added into a <code>PriorityTaskQueue</code> for execution.
 *
 * @param <T> the type of the return value.
 */
public abstract class PriorityTask<T>
        implements Runnable, Comparable<PriorityTask<?>> {
    private static final String TAG = "PriorityTask";

    private static final int STATE_INITIAL = (1 << 0);
    private static final int STATE_QUEUED = (1 << 1);
    private static final int STATE_EXECUTING = (1 << 2);
    private static final int STATE_ERROR = (1 << 3);
    private static final int STATE_CANCELING = (1 << 4);
    private static final int STATE_CANCELED = (1 << 5);
    private static final int STATE_COMPLETE = (1 << 6);

    public static final int PRIORITY_DEFAULT = 0;
    public static final int PRIORITY_BACKGROUND = 10;
    public static final int PRIORITY_FOREGROUND = -10;

    private static final AtomicLong sCounter = new AtomicLong(0L);

    /**
     * The state of the task, possible transitions are:
     * <pre>
     *     INITIAL -> QUEUED, CANCELED
     *     QUEUED -> INITIAL, EXECUTING, CANCELED
     *     EXECUTING -> COMPLETE, CANCELING, ERROR
     *     CANCELING -> CANCELED
     * </pre>
     * When the task stop, it must be end with one of the following states:
     * COMPLETE, CANCELED, or ERROR;
     */
    private int mState = STATE_INITIAL;
    private int mPriority;
    private long mTimestamp;
    private PriorityTaskQueue mQueue;
    private Thread mThread;
    private T mResult;
    private Throwable mError;

    private List<Callback<T>> mCallbacks = new ArrayList<Callback<T>>();

    public PriorityTask() {
        this(PRIORITY_DEFAULT);
    }

    public PriorityTask(int priority) {
        setPriority(priority);
    }

    /**
     * Executes the task. Subclasses should override this function. Note: if
     * this function throws an <code>InterruptedException</code>, the task will
     * be considered as being canceled instead of fail.
     */
    protected abstract T execute() throws Exception;

    /**
     * Frees the result (which is not null) when the task has been canceled.
     */
    protected void freeCanceledResult(T result) {
        // Do nothing by default;
    }

    /**
     * Invoked when the task has been a terminate state. It could be complete,
     * canceled, or failed.
     *
     * @param <T> the type of result
     */
    public static interface Callback<T> {

        public void onResultAvailable(PriorityTask<T> t, T result);

        public void onFail(PriorityTask<T> t, Throwable error);

        public void onCanceled(PriorityTask<T> t);
    }

    public final void run() {
        try {
            synchronized (this) {
                if (mState == STATE_CANCELED) return;
                if (mState != STATE_QUEUED) {
                    throw new IllegalStateException();
                }
                mThread = Thread.currentThread();
                mState = STATE_EXECUTING;
            }
            T result = execute();
            synchronized (this) {
                if (mState == STATE_CANCELING) mState = STATE_CANCELED;
                if (mState == STATE_EXECUTING) mState = STATE_COMPLETE;
                notifyAll();
            }
            if (mState == STATE_COMPLETE) {
                mResult = result;
                fireOnResultAvailable(result);
            } else {
                if (result != null) freeCanceledResult(result);
                fireOnCanceled();
            }
        } catch (InterruptedException ie) {
            synchronized (this) {
                mState = STATE_CANCELED;
                notifyAll();
            }
            fireOnCanceled();
        } catch (Throwable throwable) {
            synchronized (this) {
                mError = throwable;
                mState = STATE_ERROR;
                notifyAll();
            }
            fireOnFail(throwable);
        } finally {
            mThread = null;
            mQueue = null;
            mCallbacks = null;
        }
    }

    /**
     * Whether the task's has been requested for cancel.
     */
    protected synchronized boolean isCanceling() {
        return mState == STATE_CANCELING;
    }

    public synchronized void setPriority(int priority) {
        if (mState != STATE_INITIAL) {
            throw new IllegalStateException();
        }
        if (mPriority == priority) return;
        mPriority = priority;
    }

    protected synchronized void interruptNow() {
        if (isInStates(STATE_EXECUTING | STATE_CANCELING)) mThread.interrupt();
    }

    /**
     * Requests the task to be canceled.
     *
     * @return true if the task has not been canceled but will be canceled;
     *         false otherwise (usually the task has been complete/failed/
     *         canceled.
     */
    public synchronized boolean requestCancel() {
        if (mState == STATE_EXECUTING) {
            mState = STATE_CANCELING;
            return true;
        }
        if (isInStates(STATE_QUEUED | STATE_INITIAL)) {
            mState = STATE_CANCELED;
            notifyAll();
            if (mQueue != null) mQueue.removeCanceledTask(this);
            fireOnCanceled();
            return true;
        }
        return !isInStates(STATE_COMPLETE | STATE_ERROR | STATE_CANCELED);
    }

    private void fireOnResultAvailable(T result) {
        for (Callback<T> callback : mCallbacks) {
            try {
                callback.onResultAvailable(this, result);
            } catch (Throwable t) {
                Log.e(TAG, "Ignore all throwable in callback - " + t);
            }
        }
    }

    private void fireOnCanceled() {
        for (Callback<T> callback : mCallbacks) {
            try {
                callback.onCanceled(this);
            } catch (Throwable t) {
                Log.e(TAG, "Ignore all throwable in callback - " + t);
            }
        }
    }

    private void fireOnFail(Throwable error) {
        for (Callback<T> callback : mCallbacks) {
            try {
                callback.onFail(this, error);
            } catch (Throwable t) {
                Log.e(TAG, "Ignore all throwable in callback - " + t);
            }
        }
    }

    private final boolean isInStates(int states) {
        return (mState & states) != 0;
    }

    boolean setupBeforeQueued(PriorityTaskQueue queue) {
        if (mState == STATE_CANCELED) return false;
        if (mState != STATE_INITIAL) throw new IllegalStateException();
        mQueue = queue;
        mState = STATE_QUEUED;
        mTimestamp = sCounter.getAndIncrement();
        return true;
    }

    public synchronized void addCallback(Callback<T> callback) {
        if (!isInStates(STATE_INITIAL)) throw new IllegalStateException();
        mCallbacks.add(callback);
    }

    public synchronized void removeCallback(Callback<T> callback) {
        if (!isInStates(STATE_INITIAL)) throw new IllegalStateException();
        mCallbacks.remove(callback);
    }

    public int compareTo(PriorityTask<?> other) {
        return (mPriority != other.mPriority)
                ? (mPriority - other.mPriority)
                : (int) (mTimestamp - other.mTimestamp);
    }

    public T get() throws InterruptedException, ExecutionException {
        await(0);
        if (mState == STATE_CANCELED) throw new CancellationException();
        if (mState == STATE_ERROR) throw new ExecutionException(mError);
        return mState == STATE_COMPLETE ? mResult : null;
    }

    public T get(long timeout)
            throws InterruptedException, ExecutionException, TimeoutException {
        if (!await(timeout)) throw new TimeoutException();
        if (mState == STATE_CANCELED) throw new CancellationException();
        if (mState == STATE_ERROR) throw new ExecutionException(mError);
        return mState == STATE_COMPLETE ? mResult : null;
    }

    /**
     * Waits the task to be in terminate state.
     *
     * @param timeout the timeout in milliseconds; 0 for no timeout.
     * @return whether the task is terminated
     * @throws InterruptedException
     */
    public synchronized boolean await(long timeout)
            throws InterruptedException {
        long now = SystemClock.elapsedRealtime();
        long due = now + timeout;
        while (!isInStates(STATE_CANCELED | STATE_ERROR | STATE_COMPLETE)) {
            wait(timeout == 0 ? 0 : due - now);
            now = SystemClock.elapsedRealtime();
            if (timeout != 0 && due <= now) return false;
        }
        return true;
    }

    // used only by PriorityTaskQueue#remove
    void resetState() {
        mState = STATE_INITIAL;
    }
}