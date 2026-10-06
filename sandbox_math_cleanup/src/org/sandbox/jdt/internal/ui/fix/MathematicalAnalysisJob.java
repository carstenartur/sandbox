/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 at https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

/** One cancellable mathematical search at a time, never on the display thread. */
final class MathematicalAnalysisJob {
 static final String PLUGIN_ID="sandbox_math_cleanup";
 static final ISchedulingRule RULE=new ISchedulingRule() {
  @Override public boolean contains(ISchedulingRule other) { return other==this; }
  @Override public boolean isConflicting(ISchedulingRule other) { return other==this; }
 };
 private static final Set<Job> ACTIVE=ConcurrentHashMap.newKeySet();
 private MathematicalAnalysisJob() { }

 static void schedule(Job job) {
  job.setRule(RULE);
  job.addJobChangeListener(new JobChangeAdapter() {
   @Override public void done(IJobChangeEvent event) { ACTIVE.remove(event.getJob()); }
  });
  ACTIVE.add(job);
  try { job.schedule(); } catch(RuntimeException failure) { ACTIVE.remove(job);throw failure; }
 }

 static void cancelAll() { ACTIVE.forEach(Job::cancel); }

 static <T> T runAndWait(Function<IProgressMonitor,T> operation,IProgressMonitor caller) {
  Objects.requireNonNull(operation);
  IProgressMonitor parent=caller==null?new NullProgressMonitor():caller;
  if(parent.isCanceled()) throw new OperationCanceledException();
  if(PlatformUI.isWorkbenchRunning() && Display.getCurrent()!=null)
   throw new IllegalStateException("Mathematical search must not run or wait on the display thread");
  Job current=Job.getJobManager().currentJob();
  if(current!=null && current.getRule()==RULE) return operation.apply(parent);
  AtomicReference<T> result=new AtomicReference<>();
  AtomicReference<Throwable> failure=new AtomicReference<>();
  Job worker=new Job("Analyze verified mathematics") {
   @Override protected IStatus run(IProgressMonitor monitor) {
    IProgressMonitor combined=new NullProgressMonitor() {
     @Override public boolean isCanceled() { return super.isCanceled() || monitor.isCanceled() || parent.isCanceled(); }
    };
    try {
     if(combined.isCanceled()) return Status.CANCEL_STATUS;
     result.set(operation.apply(combined));
     return combined.isCanceled()?Status.CANCEL_STATUS:Status.OK_STATUS;
    } catch(OperationCanceledException cancelled) { return Status.CANCEL_STATUS;
    } catch(RuntimeException | Error problem) {
     failure.set(problem);return Status.error("Mathematics analysis failed",problem);
    }
   }
  };
  worker.setSystem(true);
  schedule(worker);
  try {
   while(!worker.join(100,new NullProgressMonitor())) {
    if(parent.isCanceled()) { worker.cancel();throw new OperationCanceledException(); }
   }
  } catch(InterruptedException interrupted) {
   worker.cancel();Thread.currentThread().interrupt();throw new OperationCanceledException();
  }
  if(parent.isCanceled() || worker.getResult()==null || worker.getResult().matches(IStatus.CANCEL))
   throw new OperationCanceledException();
  if(failure.get() instanceof RuntimeException problem) throw problem;
  if(failure.get() instanceof Error problem) throw problem;
  return result.get();
 }
}
