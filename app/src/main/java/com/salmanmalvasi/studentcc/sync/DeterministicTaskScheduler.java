package com.salmanmalvasi.studentcc.sync;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Deterministic, dependency-aware Directed Acyclic Graph (DAG) task scheduler.
 * Enforces bounded concurrency (C in 1..4), prerequisite completion, and section-level state tracking.
 */
public class DeterministicTaskScheduler {

    public enum TaskState {
        PENDING,
        RUNNING,
        SUCCEEDED,
        FAILED,
        SKIPPED,
        CANCELLED
    }

    public interface TaskProgressListener {
        void onTaskStateChanged(SyncTaskId taskId, TaskState state, SyncTaskResult result);
    }

    public static class SchedulerResult {
        public final boolean overallSuccess;
        public final Map<SyncTaskId, SyncTaskResult> taskResults;
        public final Map<SyncTaskId, TaskState> taskStates;
        public final long durationMs;

        public SchedulerResult(boolean overallSuccess, Map<SyncTaskId, SyncTaskResult> taskResults,
                               Map<SyncTaskId, TaskState> taskStates, long durationMs) {
            this.overallSuccess = overallSuccess;
            this.taskResults = Collections.unmodifiableMap(taskResults);
            this.taskStates = Collections.unmodifiableMap(taskStates);
            this.durationMs = durationMs;
        }

        public boolean isTaskSuccessful(SyncTaskId taskId) {
            TaskState state = taskStates.get(taskId);
            return state == TaskState.SUCCEEDED;
        }
    }

    private final int maxConcurrency;
    private final TaskProgressListener listener;
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);

    public DeterministicTaskScheduler(int maxConcurrency, TaskProgressListener listener) {
        if (maxConcurrency < 1) {
            throw new IllegalArgumentException("Concurrency limit must be at least 1");
        }
        this.maxConcurrency = maxConcurrency;
        this.listener = listener;
    }

    public DeterministicTaskScheduler(int maxConcurrency) {
        this(maxConcurrency, null);
    }

    public void cancel() {
        isCancelled.set(true);
    }

    public boolean isCancelled() {
        return isCancelled.get();
    }

    /**
     * Executes the provided list of SyncTasks respecting prerequisite dependencies and bounded concurrency.
     */
    public SchedulerResult execute(List<SyncTask> tasks, SyncContext context) {
        long startTime = System.currentTimeMillis();
        Map<SyncTaskId, SyncTask> taskMap = new HashMap<>();
        Map<SyncTaskId, TaskState> states = new ConcurrentHashMap<>();
        Map<SyncTaskId, SyncTaskResult> results = new ConcurrentHashMap<>();

        for (SyncTask task : tasks) {
            taskMap.put(task.getId(), task);
            states.put(task.getId(), TaskState.PENDING);
        }

        ExecutorService executor = Executors.newFixedThreadPool(maxConcurrency);
        Set<SyncTaskId> inFlight = Collections.synchronizedSet(new HashSet<>());
        final Object lock = new Object();

        try {
            while (hasUnfinishedTasks(states)) {
                if (isCancelled.get() || context.isCancelled()) {
                    for (SyncTask task : tasks) {
                        if (states.get(task.getId()) == TaskState.PENDING) {
                            states.put(task.getId(), TaskState.CANCELLED);
                            results.put(task.getId(), SyncTaskResult.cancelled());
                            notifyState(task.getId(), TaskState.CANCELLED, results.get(task.getId()));
                        }
                    }
                    break;
                }

                // Check for tasks whose prerequisites failed -> mark SKIPPED
                for (SyncTask task : tasks) {
                    SyncTaskId taskId = task.getId();
                    if (states.get(taskId) == TaskState.PENDING) {
                        boolean anyPrereqFailed = false;
                        for (SyncTaskId prereq : task.getPrerequisites()) {
                            TaskState prereqState = states.get(prereq);
                            if (prereqState == TaskState.FAILED || prereqState == TaskState.SKIPPED || prereqState == TaskState.CANCELLED) {
                                anyPrereqFailed = true;
                                break;
                            }
                        }
                        if (anyPrereqFailed) {
                            states.put(taskId, TaskState.SKIPPED);
                            SyncTaskResult skipResult = SyncTaskResult.skipped("Prerequisite task failed or skipped");
                            results.put(taskId, skipResult);
                            notifyState(taskId, TaskState.SKIPPED, skipResult);
                        }
                    }
                }

                // Find ready tasks whose prerequisites have all SUCCEEDED
                List<SyncTask> readyTasks = new ArrayList<>();
                for (SyncTask task : tasks) {
                    SyncTaskId taskId = task.getId();
                    if (states.get(taskId) == TaskState.PENDING && !inFlight.contains(taskId)) {
                        boolean allPrereqsSucceeded = true;
                        for (SyncTaskId prereq : task.getPrerequisites()) {
                            if (states.get(prereq) != TaskState.SUCCEEDED) {
                                allPrereqsSucceeded = false;
                                break;
                            }
                        }
                        if (allPrereqsSucceeded) {
                            readyTasks.add(task);
                        }
                    }
                }

                // Dispatch ready tasks up to available concurrency
                for (SyncTask task : readyTasks) {
                    synchronized (lock) {
                        if (inFlight.size() >= maxConcurrency) {
                            break;
                        }
                        SyncTaskId taskId = task.getId();
                        states.put(taskId, TaskState.RUNNING);
                        inFlight.add(taskId);
                        notifyState(taskId, TaskState.RUNNING, null);

                        executor.submit(() -> {
                            SyncTaskResult taskResult;
                            long taskStart = System.currentTimeMillis();
                            int attempt = 0;
                            int maxRetries = task.isSafeToRetry() ? task.getMaxRetries() : 0;

                            while (true) {
                                try {
                                    if (isCancelled.get() || context.isCancelled()) {
                                        taskResult = SyncTaskResult.cancelled();
                                        break;
                                    }
                                    taskResult = task.execute(context);
                                    if (taskResult.isSuccess()) {
                                        break;
                                    } else if (attempt < maxRetries) {
                                        attempt++;
                                        long backoff = Math.min(1000L * (1L << attempt), 5000L);
                                        Thread.sleep(backoff);
                                    } else {
                                        break;
                                    }
                                } catch (InterruptedException ie) {
                                    taskResult = SyncTaskResult.cancelled();
                                    break;
                                } catch (Exception e) {
                                    long dur = System.currentTimeMillis() - taskStart;
                                    if (attempt < maxRetries) {
                                        attempt++;
                                        try {
                                            Thread.sleep(Math.min(1000L * (1L << attempt), 5000L));
                                        } catch (InterruptedException ignored) {
                                            taskResult = SyncTaskResult.cancelled();
                                            break;
                                        }
                                    } else {
                                        taskResult = SyncTaskResult.failure(e.getMessage() != null ? e.getMessage() : e.toString(), 500, dur);
                                        break;
                                    }
                                }
                            }

                            synchronized (lock) {
                                inFlight.remove(taskId);
                                results.put(taskId, taskResult);
                                TaskState finalState;
                                if (taskResult.status == SyncTaskResult.Status.SUCCESS) {
                                    finalState = TaskState.SUCCEEDED;
                                } else if (taskResult.status == SyncTaskResult.Status.CANCELLED) {
                                    finalState = TaskState.CANCELLED;
                                } else {
                                    finalState = TaskState.FAILED;
                                }
                                states.put(taskId, finalState);
                                notifyState(taskId, finalState, taskResult);
                                lock.notifyAll();
                            }
                        });
                    }
                }

                // Wait for any running task to finish or timeout
                synchronized (lock) {
                    if (hasUnfinishedTasks(states)) {
                        try {
                            lock.wait(50);
                        } catch (InterruptedException e) {
                            cancel();
                            break;
                        }
                    }
                }
            }
        } finally {
            executor.shutdown();
            try {
                executor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        boolean overallSuccess = true;
        for (SyncTask task : tasks) {
            TaskState s = states.get(task.getId());
            if (task.isEssential() && (s == TaskState.FAILED || s == TaskState.CANCELLED)) {
                overallSuccess = false;
                break;
            }
        }

        return new SchedulerResult(overallSuccess, results, states, duration);
    }

    private boolean hasUnfinishedTasks(Map<SyncTaskId, TaskState> states) {
        for (TaskState s : states.values()) {
            if (s == TaskState.PENDING || s == TaskState.RUNNING) {
                return true;
            }
        }
        return false;
    }

    private void notifyState(SyncTaskId taskId, TaskState state, SyncTaskResult result) {
        if (listener != null) {
            try {
                listener.onTaskStateChanged(taskId, state, result);
            } catch (Exception ignored) {
            }
        }
    }
}
