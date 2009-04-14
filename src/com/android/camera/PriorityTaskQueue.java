package com.android.camera;

import java.lang.ref.WeakReference;
import java.util.IdentityHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The thread queue for <code>PriorityTask</code>.
 */
public class PriorityTaskQueue {

    private ThreadPoolExecutor mExecutor;

    /*private*/ boolean mShutdown = false;
    /*private*/ IdentityHashMap<PriorityTask<?>, Object> mActiveTasks =
            new IdentityHashMap<PriorityTask<?>, Object>();

    /**
     * Creates a queue with fixed pool size.
     */
    public PriorityTaskQueue(int size) {
        this(size, size, 0);
    }

    /**
     * Creates a queue with dynamic pool size.
     *
     * @param coreSize the minimum size of the thread pool
     * @param maxSize the maximum size of the thread pool
     * @param keepAlive the keep alive time for idle thread (in milliseconds)
     */
    public PriorityTaskQueue(int coreSize, int maxSize, long keepAlive) {
        mExecutor = new LocalExecutor(
                this, coreSize, maxSize, keepAlive, TimeUnit.MILLISECONDS,
                new PriorityBlockingQueue<Runnable>());
    }

    public boolean add(PriorityTask<?> task) {
        synchronized (task) {
            if (task.setupBeforeQueued(this)) {
                synchronized (mActiveTasks) {
                    if (mShutdown) throw new IllegalStateException();
                    mActiveTasks.put(task, null);
                }
                mExecutor.execute(task);
                return true;
            }
            return false;
        }
    }

    public boolean remove(PriorityTask<?> task) {
        synchronized (task) {
            if (mExecutor.remove(task)) {
                synchronized (mActiveTasks) {
                    if (!mShutdown) mActiveTasks.remove(task);
                }
                task.resetState();
                return true;
            }
            return false;
        }
    }

    /**
     * Shutdowns the task queue gracefully. The tasks added into the queue will
     * be executed, but no more tasks are allowed to enter the queue.
     */
    public void shutdown() {
        mShutdown = true;
        mExecutor.shutdown();
    }

    /**
     * Shutdowns the task queue immediately. All the tasks in the queue will be
     * requested to cancel. If some tasks ignore the cancel request, it may
     * blocked until those tasks are finished.
     */
    public void shutdownNow() {
        synchronized (mActiveTasks) {
            mShutdown = true;
            for (PriorityTask<?> task : mActiveTasks.keySet()) {
                task.requestCancel();
            }
        }
        mExecutor.shutdown();
    }

    <T> void removeCanceledTask(PriorityTask<T> t) {
        synchronized (mActiveTasks) {
            if (!mShutdown) mActiveTasks.remove(t);
        }
        mExecutor.remove(t);
    }

    @Override
    protected void finalize() {
        if (!mShutdown) shutdown();
    }

    private static class LocalExecutor extends ThreadPoolExecutor {
        // Uses weak reference so that PriorityTaskQueue can be finalized
        // when no others use it and than close this thread pool.
        private WeakReference<PriorityTaskQueue> mQueueRef;

        public LocalExecutor(PriorityTaskQueue queue,
                int corePoolSize, int maximumPoolSize, long keepAliveTime,
                TimeUnit unit, BlockingQueue<Runnable> workQueue) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit,
                    workQueue);
            mQueueRef = new WeakReference<PriorityTaskQueue>(queue);
        }

        @Override
        public void afterExecute(Runnable r, Throwable t) {
            PriorityTaskQueue queue = mQueueRef.get();
            if (queue != null) {
                synchronized (queue.mActiveTasks) {
                    if (!queue.mShutdown) queue.mActiveTasks.remove(r);
                }
            }
        }
    }

}
