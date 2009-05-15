package com.android.camera.gallery;

import android.test.AndroidTestCase;
import android.util.Log;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

public class BaseCancelableUnitTests extends AndroidTestCase {
    private static final String TAG = "BaseCancelableTest";

    private static class TestTask extends BaseCancelable<Integer> {

        private boolean mDone = false;
        private boolean mCancel = false;
        private boolean mRunning = false;
        private boolean mFireError = false;

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

    public void testSimpleFlow() throws Exception {
        TestTask task = new TestTask();
        task.completeTask();
        assertEquals(0, task.get().intValue());
    }

    private void assertCancellationException(BaseCancelable<?> task)
            throws Exception {
        try {
            task.get();
            fail("expect a " + CancellationException.class);
        } catch (CancellationException e) {
            // expected
        }
    }

    private void assertExecutionException(BaseCancelable<?> task)
            throws Exception {
        try {
            task.get();
            fail("expect a " + ExecutionException.class);
        } catch (ExecutionException e) {
            // expected
        }
    }

    public void testCancelInInitialState() throws Exception {
        TestTask task = new TestTask();
        task.requestCancel();
        assertCancellationException(task);
    }

    public void testCancelInRunningState() throws Exception {
        final TestTask task = new TestTask();
        new Thread() {
            @Override
            public void run() {
                try {
                    task.waitUntilRunning();
                    assertTrue(task.requestCancel());
                } catch (InterruptedException e) {
                    Log.e(TAG, "interrupt", e);
                }
            }
        }.start();
       assertCancellationException(task);
    }

    public void testConcurrentGet() throws Exception {
        final TestTask task = new TestTask();
        new Thread() {
            @Override
            public void run() {
                try {
                    assertEquals(0, task.get().intValue());
                } catch (InterruptedException e) {
                    Log.e(TAG, "interrupt", e);
                } catch (ExecutionException e) {
                    Log.e(TAG, "execution fail: ", e);
                }
            }
        }.start();
        task.waitUntilRunning();
        task.completeTask();
        assertEquals(0, task.get().intValue());
    }

    public void testConcurrentGetOnCancled() throws Exception {
        final TestTask task = new TestTask();
        new Thread() {
            @Override
            public void run() {
                try {
                    assertCancellationException(task);
                } catch (Exception e) {
                    Log.e(TAG, "Exception", e);
                }
            }
        }.start();
        task.waitUntilRunning();
        task.requestCancel();
        assertCancellationException(task);
    }

    private static class AddTask extends BaseCancelable<Integer> {
        private final Cancelable<Integer> mTasks[];

        public AddTask(Cancelable<Integer> ... tasks) {
            mTasks = tasks.clone();
        }

        @Override
        protected Integer execute() throws Exception {
            int sum = 0;
            for (int i = 0, n = mTasks.length; i < n; ++i) {
                sum += runSubTask(mTasks[i]);
            }
            return sum;
        }
    }

    public void testExecuteSubtask() throws Exception {
        TestTask subtask = new TestTask();
        @SuppressWarnings("unchecked")
        AddTask addTask = new AddTask(subtask);
        subtask.completeTask();
        assertEquals(0, addTask.get().intValue());
    }

    public void testExecuteSubtaskWithError() throws Exception {
        TestTask subtask = new TestTask();
        @SuppressWarnings("unchecked")
        AddTask addTask = new AddTask(subtask);
        subtask.completeTaskWithException();
        assertExecutionException(addTask);
    }

    public void testCancelWithSubtask() throws Exception {
        final TestTask subtask = new TestTask();
        @SuppressWarnings("unchecked")
        final AddTask addTask = new AddTask(subtask);
        new Thread() {
            @Override
            public void run() {
                try {
                    subtask.waitUntilRunning();
                } catch (InterruptedException e) {
                    Log.e(TAG, "interrupted", e);
                }
                addTask.requestCancel();
            }
        }.start();
        assertCancellationException(addTask);
    }

    private static class TaskSet extends BaseCancelable<Integer> {
        private final TestTask mTasks[];
        private int mExecutedTaskCount = 0;

        public TaskSet(TestTask ... tasks) {
            mTasks = tasks.clone();
        }

        @Override
        protected Integer execute() throws Exception {
            int exceptionCount = 0;
            for (TestTask task : mTasks) {
                try {
                    ++ mExecutedTaskCount;
                    runSubTask(task);
                } catch (ExecutionException e) {
                    ++ exceptionCount;
                }
            }
            return exceptionCount;
        }

    }

    public void testHandleExceptionInSubTasks() throws Exception {
        TestTask task0 = new TestTask();
        TestTask task1 = new TestTask();
        TestTask task2 = new TestTask();

        task0.completeTask();
        task1.completeTaskWithException();
        task2.completeTask();
        TaskSet taskSet = new TaskSet(task0, task1, task2);

        assertEquals(1, taskSet.get().intValue());
        assertEquals(3, taskSet.mExecutedTaskCount);
    }
}
