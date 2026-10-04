/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.fix.CleanUpPreferenceUtil;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jdt.ui.text.java.IQuickAssistProcessor;
import org.eclipse.jdt.ui.text.java.correction.CUCorrectionProposal;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.Analysis;

/** Bounded per-processor result cache; selection search runs only in a cancellable Job. */
public final class MathematicalQuickAssist implements IQuickAssistProcessor {
 private final Map<Key,CachedAnalysis> cache=new LinkedHashMap<>();
 private final Map<Key,Job> pending=new LinkedHashMap<>();
 @Override public boolean hasAssists(IInvocationContext context) {
  return context.getCompilationUnit()!=null && context.getSelectionOffset()>=0;
 }
 @Override public IJavaCompletionProposal[] getAssists(IInvocationContext context,IProblemLocation[] problems) throws CoreException {
  ICompilationUnit unit=context.getCompilationUnit();
  if(unit==null) return new IJavaCompletionProposal[0];
  MathCleanUpOptions options;
  try { options=options(unit); } catch(IllegalArgumentException invalid) { showStatus(invalid.getMessage());return new IJavaCompletionProposal[0]; }
  String source=unit.getSource();
  Key key=new Key(unit.getHandleIdentifier(),MathematicalEnvironment.digest(source),Map.copyOf(unit.getJavaProject().getOptions(true)),
    options,context.getSelectionOffset(),context.getSelectionLength(),MathematicalEnvironment.captureStructure(unit.getJavaProject()));
  CachedAnalysis cached;
  synchronized(cache) {
   cached=cache.get(key);
   if(cached==null) { schedule(key,unit,source);return new IJavaCompletionProposal[0]; }
   // Refresh binary contents asynchronously, including replacements preceding a JDT delta.
   schedule(key,unit,source);
  }
  Analysis analysis=cached.analysis();
  if(!analysis.changed() || !analysis.matches(source,key.compilerOptions())) return new IJavaCompletionProposal[0];
  String label=MathematicalCleanUpCore.label(options);
  // Full binary verification belongs to the worker and the change's preview/apply guard.
  MathematicalChange change=new MathematicalChange(label,unit,analysis,options,()->options(unit),cached.environment());
  return new IJavaCompletionProposal[]{new CUCorrectionProposal(label,unit,change,50)};
 }
 private void schedule(Key key,ICompilationUnit unit,String source) {
  Job previous=pending.get(key);
  if(previous!=null && previous.getState()!=Job.NONE) return;
  pending.remove(key);
  while(pending.size()>=2) pending.remove(pending.keySet().iterator().next()).cancel();
  Job worker=new Job("Analyze mathematical selection") {
   @Override protected IStatus run(IProgressMonitor monitor) {
    try {
     if(monitor.isCanceled()) return Status.CANCEL_STATUS;
     MathematicalEnvironment.Snapshot environment=MathematicalEnvironment.capture(unit.getJavaProject());
     synchronized(cache) {
      CachedAnalysis existing=cache.get(key);
      if(existing!=null && existing.environment().equals(environment)) return Status.OK_STATUS;
      cache.remove(key);
     }
     ASTParser parser=ASTParser.newParser(AST.getJLSLatest());
     parser.setSource(unit);parser.setProject(unit.getJavaProject());parser.setCompilerOptions(key.compilerOptions());parser.setResolveBindings(true);
     CompilationUnit ast=(CompilationUnit)parser.createAST(monitor);
     if(!source.equals(unit.getSource()) || !key.compilerOptions().equals(unit.getJavaProject().getOptions(true))) return stale();
     Analysis analysis=MathematicalAnalysis.analyze(ast,source,key.options(),monitor,key.offset(),key.length());
     if(monitor.isCanceled()) return Status.CANCEL_STATUS;
     if(!key.environment().equals(MathematicalEnvironment.captureStructure(unit.getJavaProject())) || !environment.matches(unit.getJavaProject()) || !analysis.matches(source,key.compilerOptions())
       || !source.equals(unit.getSource()) || !key.compilerOptions().equals(unit.getJavaProject().getOptions(true))) return stale();
     synchronized(cache) {
      while(cache.size()>=8) cache.remove(cache.keySet().iterator().next());
      cache.put(key,new CachedAnalysis(analysis,environment));
     }
     showStatus(analysis.changed()?"Mathematics analysis complete. Invoke Quick Assist again to preview the verified change."
       :analysis.diagnostics().stream().findFirst().map(item->item.code()+": "+item.message()).orElse("Mathematics analysis found no verified improvement."));
     return Status.OK_STATUS;
    } catch(OperationCanceledException cancelled) { return Status.CANCEL_STATUS;
    } catch(CoreException | RuntimeException failure) { return Status.error("Mathematics analysis failed; source remains unchanged",failure);
    } finally { synchronized(cache) { pending.remove(key,this); } }
   }
  };
  // Queued cancellation never enters run(); cleanup must also happen in done().
  worker.addJobChangeListener(new JobChangeAdapter() {
   @Override public void done(IJobChangeEvent event) { synchronized(cache) { pending.remove(key,event.getJob()); } }
  });
  worker.setUser(true);pending.put(key,worker);MathematicalAnalysisJob.schedule(worker);
 }
 private static IStatus stale() { showStatus("STALE_ANALYSIS: analyze the current source and settings again");return Status.CANCEL_STATUS; }
 private static MathCleanUpOptions options(ICompilationUnit unit) {
  Map<String,String> profile=CleanUpPreferenceUtil.loadOptions(new ProjectScope(unit.getJavaProject().getProject()));
  Map<String,String> values=new HashMap<>(profile==null?Map.of():profile);
  values.put(MathCleanUpOptions.CLEANUP,"true");
  return MathCleanUpOptions.parse(values,MathematicalCleanUpCore.targetJava(unit.getJavaProject()));
 }
 private static void showStatus(String message) {
  if(PlatformUI.isWorkbenchRunning()) {
   var display=PlatformUI.getWorkbench().getDisplay();
   if(!display.isDisposed()) display.asyncExec(()->{
    if(!PlatformUI.isWorkbenchRunning()) return;
    IWorkbenchWindow window=PlatformUI.getWorkbench().getActiveWorkbenchWindow();
    if(window!=null && window.getActivePage()!=null && window.getActivePage().getActiveEditor()!=null)
     window.getActivePage().getActiveEditor().getEditorSite().getActionBars().getStatusLineManager().setMessage(message);
   });
  }
 }
 record Key(String unit,String sourceDigest,Map<String,String> compilerOptions,MathCleanUpOptions options,
   int offset,int length,MathematicalEnvironment.Snapshot environment) {
  Key { compilerOptions=Map.copyOf(compilerOptions); }
 }
 private record CachedAnalysis(Analysis analysis,MathematicalEnvironment.Snapshot environment) { }
}
