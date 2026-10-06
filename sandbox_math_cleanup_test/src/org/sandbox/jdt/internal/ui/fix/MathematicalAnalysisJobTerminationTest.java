/* SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.ui.fix;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.junit.jupiter.api.Test;

class MathematicalAnalysisJobTerminationTest {
    @Test
    void cancellationWaitsForTheWorkerBeforeResourcesCanBeDeleted() throws Exception {
        ISchedulingRule rule = MathematicalAnalysisJob.RULE;
        CountDownLatch started = new CountDownLatch(1), cancelled = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicBoolean ended = new AtomicBoolean();
        Job worker = blockedJob(rule, started, cancelled, release, ended);
        MathematicalAnalysisJob.schedule(worker);
        try (var executor = Executors.newSingleThreadExecutor()) {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            var drain = executor.submit(() -> { MathematicalAnalysisJob.cancelAllAndWait(Duration.ofSeconds(5)); return null; });
            assertTrue(cancelled.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> drain.get(100, TimeUnit.MILLISECONDS),
                    "A cancellation request is not worker completion");
            assertFalse(ended.get());
            release.countDown();
            drain.get(5, TimeUnit.SECONDS);
            assertTrue(ended.get());
            assertEquals(Job.NONE, worker.getState());
        } finally {
            release.countDown();
            worker.cancel();
            worker.join();
        }
    }

    @Test
    void queuedWorkersAreCancelledWithoutExecuting() throws Exception {
        ISchedulingRule rule = MathematicalAnalysisJob.RULE;
        AtomicBoolean executed = new AtomicBoolean();
        Job queued = new Job("Queued mathematics drain fixture") {
            @Override protected IStatus run(IProgressMonitor monitor) { executed.set(true); return Status.OK_STATUS; }
        };
        queued.setRule(rule);
        var manager = Job.getJobManager();
        manager.beginRule(rule, null);
        try {
            MathematicalAnalysisJob.schedule(queued);
            MathematicalAnalysisJob.cancelAllAndWait(Duration.ofSeconds(5));
            assertEquals(Job.NONE, queued.getState());
            assertFalse(executed.get());
        } finally {
            queued.cancel();
            manager.endRule(rule);
            queued.join();
        }
    }

    @Test
    void unrelatedWorkersAreNotCancelled() throws Exception {
        CountDownLatch started = new CountDownLatch(1), cancelled = new CountDownLatch(1), release = new CountDownLatch(1);
        Job unrelated = blockedJob(null, started, cancelled, release, new AtomicBoolean());
        unrelated.schedule();
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            MathematicalAnalysisJob.cancelAllAndWait(Duration.ofSeconds(5));
            assertEquals(1, cancelled.getCount());
            assertEquals(Job.RUNNING, unrelated.getState());
        } finally {
            release.countDown();
            unrelated.join();
        }
    }

    @Test
    void timeoutDoesNotDeclareAnUnfinishedWorkerSafe() throws Exception {
        ISchedulingRule rule = MathematicalAnalysisJob.RULE;
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        Job worker = blockedJob(rule, started, new CountDownLatch(1), release, new AtomicBoolean());
        MathematicalAnalysisJob.schedule(worker);
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> MathematicalAnalysisJob.cancelAllAndWait(Duration.ofMillis(30)));
            assertEquals(Job.RUNNING, worker.getState());
        } finally {
            release.countDown();
            worker.cancel();
            worker.join();
        }
    }

    @Test
    void aRegisteredWorkerCannotWaitForItself() throws Exception {
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Job worker = new Job("Self-join mathematics drain fixture") {
            @Override protected IStatus run(IProgressMonitor monitor) {
                try { MathematicalAnalysisJob.cancelAllAndWait(Duration.ofSeconds(1)); }
                catch (Exception expected) { failure.set(expected); }
                return Status.OK_STATUS;
            }
        };
        MathematicalAnalysisJob.schedule(worker);
        assertTrue(worker.join(5000, null), "The self-join guard must return without deadlocking");
        assertInstanceOf(IllegalStateException.class, failure.get());
    }

    @Test
    void drainRejectsMissingOrNonPositiveTimeout() {
        assertThrows(NullPointerException.class, () -> MathematicalAnalysisJob.cancelAllAndWait(null));
        assertThrows(IllegalArgumentException.class, () -> MathematicalAnalysisJob.cancelAllAndWait(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> MathematicalAnalysisJob.cancelAllAndWait(Duration.ofMillis(-1)));
    }

    private static Job blockedJob(ISchedulingRule rule, CountDownLatch started, CountDownLatch cancelled,
            CountDownLatch release, AtomicBoolean ended) {
        Job worker = new Job("Running mathematics drain fixture") {
            @Override protected void canceling() { cancelled.countDown(); }
            @Override protected IStatus run(IProgressMonitor monitor) {
                started.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) return Status.error("Fixture release timed out");
                    return monitor.isCanceled() ? Status.CANCEL_STATUS : Status.OK_STATUS;
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return Status.CANCEL_STATUS;
                } finally { ended.set(true); }
            }
        };
        worker.setRule(rule);
        return worker;
    }
}
