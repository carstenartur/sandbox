/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.Job;
import org.junit.jupiter.api.Test;

class MathematicalAnalysisJobTest {
 @Test void analysisRunsOffTheCallingThread() {
  Thread caller=Thread.currentThread();
  Thread worker=MathematicalAnalysisJob.runAndWait(monitor->Thread.currentThread(),new NullProgressMonitor());
  assertNotSame(caller,worker);
 }
 @Test void cancellationBeforeSchedulingDoesNotRunSearch() {
  var monitor=new NullProgressMonitor();monitor.setCanceled(true);var searched=new AtomicBoolean();
  assertThrows(OperationCanceledException.class,()->MathematicalAnalysisJob.runAndWait(m->{searched.set(true);return 1;},monitor));
  assertFalse(searched.get());
 }
 @Test void callerCancellationReachesRunningWorker() throws Exception {
  var monitor=new NullProgressMonitor();var entered=new CountDownLatch(1);
  try(var executor=Executors.newSingleThreadExecutor()) {
   var future=executor.submit(()->MathematicalAnalysisJob.runAndWait(m->{entered.countDown();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!m.isCanceled() && System.nanoTime()<deadline)java.util.concurrent.locks.LockSupport.parkNanos(1000000);return m.isCanceled();},monitor));
   assertTrue(entered.await(5,TimeUnit.SECONDS));monitor.setCanceled(true);
   var failure=assertThrows(ExecutionException.class,()->future.get(5,TimeUnit.SECONDS));
   assertInstanceOf(OperationCanceledException.class,failure.getCause());
  }
 }
 @Test void sharedRuleSerializesIndependentlyScheduledAnalyses() throws Exception {
  var firstEntered=new CountDownLatch(1);var release=new CountDownLatch(1);var secondEntered=new CountDownLatch(1);
  Job first=new Job("First bounded mathematics test"){@Override protected IStatus run(IProgressMonitor m){firstEntered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))return Status.error("Test latch timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();return Status.CANCEL_STATUS;}return Status.OK_STATUS;}};
  Job second=new Job("Second bounded mathematics test"){@Override protected IStatus run(IProgressMonitor m){secondEntered.countDown();return Status.OK_STATUS;}};
  try{
   MathematicalAnalysisJob.schedule(first);assertTrue(firstEntered.await(5,TimeUnit.SECONDS));
   MathematicalAnalysisJob.schedule(second);assertFalse(secondEntered.await(150,TimeUnit.MILLISECONDS),"Two mathematical searches must not execute concurrently");
  }finally{release.countDown();first.join(5000,new NullProgressMonitor());second.join(5000,new NullProgressMonitor());first.cancel();second.cancel();}
  assertEquals(0,secondEntered.getCount());
 }
}
