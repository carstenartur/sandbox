package org.sandbox.jdt.internal.ui.fix;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ICompilationUnit;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;

class MathematicalQuickAssistTest {
 @Test void queuedCancellationRemovesPendingEntryAndAllowsTheSameSelectionAgain() throws Exception {
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
  Job blocker=new Job("hold shared mathematics rule") {
   @Override protected IStatus run(IProgressMonitor monitor) {
    entered.countDown();try { return release.await(5,TimeUnit.SECONDS)?Status.OK_STATUS:Status.CANCEL_STATUS; }
    catch(InterruptedException interrupted) { Thread.currentThread().interrupt();return Status.CANCEL_STATUS; }
   }
  };
  MathematicalAnalysisJob.schedule(blocker);assertTrue(entered.await(5,TimeUnit.SECONDS));
  MathematicalQuickAssist assist=new MathematicalQuickAssist();
  var key=new MathematicalQuickAssist.Key("unit","digest",Map.of(),MathCleanUpOptions.defaults(17),0,1,new MathematicalEnvironment.Snapshot("environment",Map.of()));
  Method schedule=MathematicalQuickAssist.class.getDeclaredMethod("schedule",MathematicalQuickAssist.Key.class,ICompilationUnit.class,String.class);schedule.setAccessible(true);
  Field field=MathematicalQuickAssist.class.getDeclaredField("pending");field.setAccessible(true);
  @SuppressWarnings("unchecked") Map<MathematicalQuickAssist.Key,Job> pending=(Map<MathematicalQuickAssist.Key,Job>)field.get(assist);
  try {
   schedule.invoke(assist,key,null,"source");Job cancelled=pending.get(key);assertNotNull(cancelled);
   assertTrue(cancelled.cancel());assertEquals(0,pending.size());
   schedule.invoke(assist,key,null,"source");Job retry=pending.get(key);
   assertNotNull(retry);assertNotSame(cancelled,retry);assertTrue(retry.cancel());assertTrue(pending.isEmpty());
  } finally { pending.values().forEach(Job::cancel);release.countDown();blocker.join(); }
 }
 @Test void keysIncludeSourceSettingsSelectionAndBindingContext() {
  var options=MathCleanUpOptions.defaults(17);var context=new MathematicalEnvironment.Snapshot("one",Map.of());
  var key=new MathematicalQuickAssist.Key("unit","source",Map.of(),options,0,1,context);
  assertNotEquals(key,new MathematicalQuickAssist.Key("unit","changed",Map.of(),options,0,1,context));
  assertNotEquals(key,new MathematicalQuickAssist.Key("unit","source",Map.of("option","new"),options,0,1,context));
  assertNotEquals(key,new MathematicalQuickAssist.Key("unit","source",Map.of(),options,1,1,context));
  assertNotEquals(key,new MathematicalQuickAssist.Key("unit","source",Map.of(),options,0,1,new MathematicalEnvironment.Snapshot("other",Map.of())));
 }
}
