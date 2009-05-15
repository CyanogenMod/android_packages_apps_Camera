package com.android.camera;

import android.os.SystemClock;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

/**
 * Test cases for <code>PriorityTaskQueue</code>.
 */
@SmallTest
public class PriorityTaskQueueUnitTests extends AndroidTestCase {

    private static class TestTask extends PriorityTask<Integer> {
        private boolean mDone = false;
        private boolean mCancel = false;
        private boolean mRunning = false;
        private boolean mFireError = false;

        private TestTask() {
        }

        private TestTask(int priority) {
            super(priority);
        }

        @Override
        public synchronized boolean requestCancel() {
            if (super.requestCancel()) {
                mCancel = true;
                notifyAll();
                return true;
            }
            return false;
        }

        public synchronized void waitUntilRunning()
                throws InterruptedException {
            while (!mRunning) {
                wait();
            }
        }

        @Override
        protected synchronized Integer execute() throws Exception {
            mRunning = true;
            notifyAll();

            while (!mDone && !mCancel) {
                wait();
            }
            if (mFireError) throw new IllegalStateException();
            return 0;
        }

        public synchronized void completeTask() {
            mDone = true;
            notifyAll();
        }

        public synchronized void completeTaskWithException() {
            mDone = true;
            mFireError = true;
            notifyAll();
        }
    }

    public void testOneTask() throws Throwable {
        PriorityTaskQueue queue = new PriorityTaskQueue(1);
        TestTask task = new TestTask();
        queue.add(task);
        task.completeTask();
        assertEquals(0, task.get().intValue());
        queue.shutdown();
    }

    public void testShutdownNow() throws Exception {
        PriorityTaskQueue queue = new PriorityTaskQueue(1);
        TestTask taskOne = new TestTask();
        TestTask taskTwo = new TestTask();
        queue.add(taskOne);
        queue.add(taskTwo);
        taskOne.waitUntilRunning();
        queue.shutdownNow();
        assertTaskCanceled(taskOne);
        assertTaskCanceled(taskTwo);
    }

    public void testCancelRunningTask() throws Throwable {
        PriorityTaskQueue queue = new PriorityTaskQueue(3);
        TestTask task = new TestTask();

        queue.add(task);
        task.waitUntilRunning();
        task.requestCancel();
        assertTaskCanceled(task);
        queue.shutdownNow();
    }

    public void testCancelQueueingTask() throws Exception {
        PriorityTaskQueue queue = new PriorityTaskQueue(1);
        TestTask runningTask = new TestTask();
        TestTask taskToBeCanceled = new TestTask();
        queue.add(runningTask);
        queue.add(taskToBeCanceled);
        taskToBeCanceled.requestCancel();
        assertTaskCanceled(taskToBeCanceled);
        queue.shutdownNow();
        assertTaskCanceled(runningTask);
    }

    public void testTaskExecuteWithException() throws Exception {
        PriorityTaskQueue queue = new PriorityTaskQueue(1);
        TestTask task = new TestTask();
        queue.add(task);
        task.completeTaskWithException();
        assertTaskFail(task);
        queue.shutdownNow();
    }

    @SuppressWarnings("unchecked")
    private static class TaskCollector implements PriorityTask.Callback {

        private ArrayList<PriorityTask> list = new ArrayList<PriorityTask>();
        private int mCount;

        public TaskCollector(PriorityTask ... tasks) {
            mCount = tasks.length;
            for (PriorityTask<?> task : tasks) {
                task.addCallback(this);
            }
        }

        public void onCanceled(PriorityTask t) {
        }

        public void onFail(PriorityTask t, Throwable error) {
        }

        public synchronized void onResultAvailable(PriorityTask t, Object r) {
            list.add(t);
            notifyAll();
        }

        public PriorityTask<?> get(int index) {
            return list.get(index);
        }

        public synchronized void waitForTasks()
                throws InterruptedException {
            while (list.size() < mCount) {
                wait();
            }
        }
    }

    public void testPriorityOrder() throws Exception {
        TestTask task0 = new TestTask(0);
        TestTask task1 = new TestTask(1);
        TestTask task2 = new TestTask(2);

        TaskCollector collector = new TaskCollector(task0, task1, task2);

        PriorityTaskQueue queue = new PriorityTaskQueue(1);

        queue.add(task2);
        queue.add(task1);
        queue.add(task0);

        task2.completeTask();
        task1.completeTask();
        task0.completeTask();
        collector.waitForTasks();

        assertSame(task2, collector.get(0));
        assertSame(task0, collector.get(1));
        assertSame(task1, collector.get(2));
        queue.shutdownNow();
    }

    public void testTimestampOrder() throws Exception {
        TestTask task0 = new TestTask();
        TestTask task1 = new TestTask();
        TestTask task2 = new TestTask();
        TaskCollector collector = new TaskCollector(task0, task1, task2);
        PriorityTaskQueue queue = new PriorityTaskQueue(1);

        queue.add(task0);
        queue.add(task1);
        queue.add(task2);

        task0.completeTask();
        task2.completeTask();
        task1.completeTask();
        collector.waitForTasks();

        assertSame(task0, collector.get(0));
        assertSame(task1, collector.get(1));
        assertSame(task2, collector.get(2));
        queue.shutdownNow();
    }

    public void testSetPriorityOnInitial() throws Exception {
        TestTask task0 = new TestTask(0);
        TestTask task1 = new TestTask(1);
        TestTask task2 = new TestTask(2);

        TaskCollector collector = new TaskCollector(task0, task1, task2);

        PriorityTaskQueue queue = new PriorityTaskQueue(1);

        // re-priority task 1 and task 2 to let task run first
        task1.setPriority(2);
        task2.setPriority(1);

        queue.add(task0);
        queue.add(task1);
        queue.add(task2);

        task0.completeTask();
        task1.completeTask();
        task2.completeTask();
        collector.waitForTasks();

        assertSame(task0, collector.get(0));
        assertSame(task2, collector.get(1));
        assertSame(task1, collector.get(2));
        queue.shutdownNow();
    }

    public void testRemoveQueuedTask() throws Exception {
        TestTask task0 = new TestTask(0);
        TestTask task1 = new TestTask(1);

        PriorityTaskQueue queue = new PriorityTaskQueue(1);

        queue.add(task0);
        queue.add(task1);
        assertTrue(queue.remove(task1));
        task0.completeTask();

        queue.add(task1);
        task1.completeTask();
        assertEquals(0, task1.get().intValue());
        queue.shutdownNow();
    }

    public void testRemoveRunningTask() throws Exception {
        TestTask task0 = new TestTask(0);

        PriorityTaskQueue queue = new PriorityTaskQueue(1);

        queue.add(task0);
        task0.waitUntilRunning();
        assertFalse(queue.remove(task0));
        task0.completeTask();
        queue.shutdownNow();
    }

    public void testAddTaskOnShutdownQueue() {
        PriorityTaskQueue queue = new PriorityTaskQueue(1);
        queue.shutdown();
        try {
            queue.add(new TestTask());
            fail();
        } catch (IllegalStateException e) {
            //expected
        }
    }

    public void testPriorityTaskAwait() throws Exception {
        PriorityTaskQueue queue = new PriorityTaskQueue(1);
        TestTask task = new TestTask();
        queue.add(task);
        long timestamp = SystemClock.elapsedRealtime();
        assertFalse(task.await(100));
        assertTrue(SystemClock.elapsedRealtime() - timestamp > 50);
        task.completeTask();
        assertTrue(task.await(10000));
    }

    private static <T> void assertTaskCanceled(
            PriorityTask<T> task) throws Exception {
        try {
            task.get();
            fail();
        } catch (CancellationException e) {
            // expected
        }
    }

    private static <T> void assertTaskFail(
            PriorityTask<T> task) throws Exception {
        try {
            task.get();
            fail();
        } catch (ExecutionException e) {
            // expected
        }
    }
}
