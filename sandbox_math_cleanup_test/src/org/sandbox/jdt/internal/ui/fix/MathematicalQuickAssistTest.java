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
 @Test void cancellationDuringCachedFreshnessCaptureReturnsCancel() throws Exception {
  var listening=MathematicalEnvironment.class.getDeclaredField("listening");listening.setAccessible(true);boolean prior=listening.getBoolean(null);listening.setBoolean(null,true);
  var cancelDuringCapture=new java.util.concurrent.atomic.AtomicBoolean();
  var project=(org.eclipse.jdt.core.IJavaProject)java.lang.reflect.Proxy.newProxyInstance(ICompilationUnit.class.getClassLoader(),new Class<?>[]{org.eclipse.jdt.core.IJavaProject.class},
   (proxy,method,args)->switch(method.getName()) {
    case "getHandleIdentifier"->"=CancellationProbe";case "exists"->true;case "getOptions"->Map.of();
    case "getOutputLocation"->org.eclipse.core.runtime.IPath.fromPortableString("/CancellationProbe/bin");
    case "getRawClasspath"->{if(cancelDuringCapture.get())Job.getJobManager().currentJob().cancel();yield new org.eclipse.jdt.core.IClasspathEntry[0];}
    case "getResolvedClasspath"->new org.eclipse.jdt.core.IClasspathEntry[0];default->throw new AssertionError(method);
   });
  var unit=(ICompilationUnit)java.lang.reflect.Proxy.newProxyInstance(ICompilationUnit.class.getClassLoader(),new Class<?>[]{ICompilationUnit.class},
   (proxy,method,args)->method.getName().equals("getJavaProject")?project:null);
  try {
   var environment=MathematicalEnvironment.capture(project);var key=new MathematicalQuickAssist.Key("unit","source",Map.of(),MathCleanUpOptions.defaults(17),0,1,environment);
   var assist=new MathematicalQuickAssist();var cacheField=MathematicalQuickAssist.class.getDeclaredField("cache");cacheField.setAccessible(true);
   var cached=Class.forName("org.sandbox.jdt.internal.ui.fix.MathematicalQuickAssist$CachedAnalysis");var constructor=cached.getDeclaredConstructors()[0];constructor.setAccessible(true);
   @SuppressWarnings("unchecked") var cache=(Map<Object,Object>)cacheField.get(assist);Object original=constructor.newInstance(null,environment);cache.put(key,original);
   var pendingField=MathematicalQuickAssist.class.getDeclaredField("pending");pendingField.setAccessible(true);var schedule=MathematicalQuickAssist.class.getDeclaredMethod("schedule",MathematicalQuickAssist.Key.class,ICompilationUnit.class,String.class);schedule.setAccessible(true);
   cancelDuringCapture.set(true);Job worker;
   synchronized(cache) {schedule.invoke(assist,key,unit,"source");worker=(Job)((Map<?,?>)pendingField.get(assist)).get(key);}
   worker.join();assertTrue(worker.getResult().matches(IStatus.CANCEL),()->"Canceled freshness returned "+worker.getResult());assertSame(original,cache.get(key));
  } finally { listening.setBoolean(null,prior); }
 }
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
