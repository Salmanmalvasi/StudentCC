package com.salmanmalvasi.studentcc.sync;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unit tests verifying DAG dependency scheduling, concurrency constraints, cancellation, and failure cascades.
 */
public class DeterministicTaskSchedulerTest {

    @Test
    public void testDependencyOrderEnforced() {
        List<SyncTaskId> executionOrder = Collections.synchronizedList(new ArrayList<>());
        SyncContext context = new SyncContext(null, "2024-01", null);

        SyncTask taskA = new SyncTask(SyncTaskId.AUTH, Collections.emptySet()) {
            @Override
            public SyncTaskResult execute(SyncContext ctx) throws Exception {
                Thread.sleep(50);
                executionOrder.add(getId());
                return SyncTaskResult.success(50);
            }
        };

        SyncTask taskB = new SyncTask(SyncTaskId.COURSES, new HashSet<>(Collections.singletonList(SyncTaskId.AUTH))) {
            @Override
            public SyncTaskResult execute(SyncContext ctx) throws Exception {
                Thread.sleep(30);
                executionOrder.add(getId());
                return SyncTaskResult.success(30);
            }
        };

        SyncTask taskC = new SyncTask(SyncTaskId.ATTENDANCE_SUMMARY, new HashSet<>(Collections.singletonList(SyncTaskId.COURSES))) {
            @Override
            public SyncTaskResult execute(SyncContext ctx) throws Exception {
                Thread.sleep(20);
                executionOrder.add(getId());
                return SyncTaskResult.success(20);
            }
        };

        DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(2);
        DeterministicTaskScheduler.SchedulerResult result = scheduler.execute(Arrays.asList(taskC, taskB, taskA), context);

        Assert.assertTrue(result.overallSuccess);
        Assert.assertEquals(3, executionOrder.size());
        Assert.assertEquals(SyncTaskId.AUTH, executionOrder.get(0));
        Assert.assertEquals(SyncTaskId.COURSES, executionOrder.get(1));
        Assert.assertEquals(SyncTaskId.ATTENDANCE_SUMMARY, executionOrder.get(2));
    }

    @Test
    public void testConcurrencyLimitRespected() {
        AtomicInteger activeConcurrent = new AtomicInteger(0);
        AtomicInteger maxObservedConcurrent = new AtomicInteger(0);
        SyncContext context = new SyncContext(null, "2024-01", null);

        List<SyncTask> tasks = new ArrayList<>();
        SyncTaskId[] independentIds = new SyncTaskId[]{
                SyncTaskId.PROFILE, SyncTaskId.DEAN_HOD, SyncTaskId.SPOTLIGHT, SyncTaskId.RECEIPTS
        };

        for (SyncTaskId id : independentIds) {
            tasks.add(new SyncTask(id, Collections.emptySet()) {
                @Override
                public SyncTaskResult execute(SyncContext ctx) throws Exception {
                    int curr = activeConcurrent.incrementAndGet();
                    maxObservedConcurrent.updateAndGet(max -> Math.max(max, curr));
                    Thread.sleep(80);
                    activeConcurrent.decrementAndGet();
                    return SyncTaskResult.success(80);
                }
            });
        }

        int limit = 2;
        DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(limit);
        DeterministicTaskScheduler.SchedulerResult result = scheduler.execute(tasks, context);

        Assert.assertTrue(result.overallSuccess);
        Assert.assertTrue("Observed concurrency " + maxObservedConcurrent.get() + " should not exceed " + limit,
                maxObservedConcurrent.get() <= limit);
    }

    @Test
    public void testPrerequisiteFailureCascadesToSkipped() {
        SyncContext context = new SyncContext(null, "2024-01", null);

        SyncTask taskA = new SyncTask(SyncTaskId.AUTH, Collections.emptySet()) {
            @Override
            public SyncTaskResult execute(SyncContext ctx) throws Exception {
                return SyncTaskResult.failure("Auth server down", 503, 10);
            }
        };

        SyncTask dependentTask = new SyncTask(SyncTaskId.COURSES, new HashSet<>(Collections.singletonList(SyncTaskId.AUTH))) {
            @Override
            public SyncTaskResult execute(SyncContext ctx) throws Exception {
                return SyncTaskResult.success(10);
            }
        };

        DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(2);
        DeterministicTaskScheduler.SchedulerResult result = scheduler.execute(Arrays.asList(taskA, dependentTask), context);

        Assert.assertFalse(result.overallSuccess);
        Assert.assertEquals(DeterministicTaskScheduler.TaskState.FAILED, result.taskStates.get(SyncTaskId.AUTH));
        Assert.assertEquals(DeterministicTaskScheduler.TaskState.SKIPPED, result.taskStates.get(SyncTaskId.COURSES));
    }

    @Test
    public void testCancellationAbortsPendingTasks() {
        SyncContext context = new SyncContext(null, "2024-01", null);
        DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(1);

        SyncTask task1 = new SyncTask(SyncTaskId.AUTH, Collections.emptySet()) {
            @Override
            public SyncTaskResult execute(SyncContext ctx) throws Exception {
                Thread.sleep(50);
                scheduler.cancel(); // Cancel mid-execution
                return SyncTaskResult.success(50);
            }
        };

        SyncTask task2 = new SyncTask(SyncTaskId.PROFILE, Collections.emptySet()) {
            @Override
            public SyncTaskResult execute(SyncContext ctx) throws Exception {
                return SyncTaskResult.success(20);
            }
        };

        DeterministicTaskScheduler.SchedulerResult result = scheduler.execute(Arrays.asList(task1, task2), context);
        Assert.assertEquals(DeterministicTaskScheduler.TaskState.SUCCEEDED, result.taskStates.get(SyncTaskId.AUTH));
        Assert.assertEquals(DeterministicTaskScheduler.TaskState.CANCELLED, result.taskStates.get(SyncTaskId.PROFILE));
    }
}
