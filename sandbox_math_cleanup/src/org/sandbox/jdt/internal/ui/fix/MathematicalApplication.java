/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.Analysis;
import de.regelsuche.sdk.optimization.SafetyProfile;

/** Headless Equinox entry point for an existing real workspace Java project. */
public final class MathematicalApplication implements IApplication {
 private final AtomicReference<IProgressMonitor> active=new AtomicReference<>();
 @Override public Object start(IApplicationContext context) {
  IProgressMonitor monitor=new NullProgressMonitor();active.set(monitor);
  try {
   String[] command=(String[])context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
   return execute(MathematicalArguments.parse(command==null?new String[0]:command),monitor);
  } catch(OperationCanceledException cancelled) { System.err.println("CANCELLED: mathematical analysis stopped");return Integer.valueOf(2);
  } catch(Exception failure) { System.err.println("MATHEMATICS_FAILED: "+failure.getMessage());return Integer.valueOf(1);
  } finally { active.compareAndSet(monitor,null); }
 }
 @Override public void stop() { IProgressMonitor monitor=active.get();if(monitor!=null)monitor.setCanceled(true); }
 private static Integer execute(MathematicalArguments arguments,IProgressMonitor monitor) throws CoreException,IOException {
  Map<String,String> config=configuration(arguments.configuration());
  IWorkspace workspace=ResourcesPlugin.getWorkspace();IProject resource=workspace.getRoot().getProject(arguments.project());
  if(!resource.exists() || !resource.isOpen() || !resource.hasNature(JavaCore.NATURE_ID))
   throw new IllegalArgumentException("--project must name an existing open Java project in this workspace");
  IJavaProject project=JavaCore.create(resource);
  MathCleanUpOptions options=MathCleanUpOptions.parse(config,MathematicalCleanUpCore.targetJava(project));
  if(arguments.apply() && options.safety()==SafetyProfile.CHECKED_THROW && !arguments.acceptChecked())
   throw new IllegalArgumentException("CHECKED_THROW application requires --accept-checked as well as checkedOptIn=true");
  List<ICompilationUnit> units=units(project);
  protectReport(arguments,units);
  List<Planned> plans=MathematicalAnalysisJob.runAndWait(worker->{
   try { return analyze(units,options,worker); } catch(CoreException failure) { throw new IllegalStateException(failure.getMessage(),failure); }
  },monitor);
  MathematicalArtifacts.Receipt artifacts=MathematicalArtifacts.capture(false);
  MathematicalReport proposal=report(arguments,options,config,artifacts,plans,arguments.apply()?"IN_PROGRESS":"SUCCESS",null,List.of());
  // Verify the requested destination is writable before any source changes.
  proposal.write(arguments.report());
  if(arguments.apply()) workspace.run(transaction->{
   List<Change> undo=new ArrayList<>();
   try {
    for(Planned plan:plans) {
     cancelled(transaction);
     if(plan.unit().hasUnsavedChanges()) throw new IllegalArgumentException("UNSAVED_EDITOR: refusing to apply over unsaved source");
     plan.change(options).requireCurrent();
    }
    for(Planned plan:plans) {
     cancelled(transaction);if(!plan.analysis().changed())continue;
     MathematicalChange change=plan.change(options);change.setSaveMode(TextFileChange.FORCE_SAVE);
     change.initializeValidationData(transaction);change.requireCurrent();
     if(change.isValid(transaction).hasFatalError()) throw new IllegalArgumentException("STALE_ANALYSIS: change validation failed");
     Change inverse=change.perform(transaction);undo.add(inverse);
     report(arguments,options,config,artifacts,plans,"IN_PROGRESS",null,List.of()).write(arguments.report());
     if(!plan.unit().getSource().equals(plan.file().replacement())) throw new IllegalArgumentException("Applied source differs from verified replacement");
    }
    report(arguments,options,config,artifacts,plans,"SUCCESS",null,List.of()).write(arguments.report());
   } catch(CoreException | IOException | RuntimeException failure) {
    var rollback=MathematicalRollback.attempt(undo);
    List<String> failures=new ArrayList<>();failures.add(failure.getClass().getSimpleName()+": "+failure.getMessage());failures.addAll(rollback.failures());
    MathematicalReport observed=report(arguments,options,config,artifacts,plans,"FAILURE",null,failures);
    boolean complete=rollback.complete() && observed.files().stream().allMatch(file->"ORIGINAL".equals(file.applicationStatus()));
    try { new MathematicalReport(observed.schemaVersion(),observed.project(),observed.mode(),observed.requestedOptions(),observed.configProperties(),
      observed.sdkSha256(),observed.adapterBundleSha256(),observed.files(),"FAILURE",complete,failures).write(arguments.report()); }
    catch(IOException | RuntimeException reporting) { failure.addSuppressed(reporting); }
    if(failure instanceof OperationCanceledException cancelled) throw cancelled;
    throw new CoreException(Status.error(complete?"Mathematics apply failed; observed sources were restored":"Mathematics apply failed; rollback incomplete or source state unavailable; inspect report and sources",failure));
   } finally { undo.stream().filter(java.util.Objects::nonNull).forEach(Change::dispose); }
  },resource,IWorkspace.AVOID_UPDATE,monitor);
  return IApplication.EXIT_OK;
 }
 private static List<Planned> analyze(List<ICompilationUnit> units,MathCleanUpOptions options,IProgressMonitor monitor) throws CoreException {
  List<Planned> plans=new ArrayList<>();
  for(ICompilationUnit unit:units) {
   cancelled(monitor);
   var environment=MathematicalEnvironment.capture(unit.getJavaProject());String source=unit.getSource();
   Map<String,String> compilerOptions=Map.copyOf(unit.getJavaProject().getOptions(true));
   ASTParser parser=ASTParser.newParser(AST.getJLSLatest());parser.setSource(unit);parser.setResolveBindings(true);parser.setCompilerOptions(compilerOptions);
   CompilationUnit ast=(CompilationUnit)parser.createAST(monitor);
   Analysis analysis=MathematicalAnalysis.analyze(ast,source,options,monitor,-1,0);
   cancelled(monitor);
   if(!environment.matches(unit.getJavaProject()) || !analysis.matches(unit.getSource(),unit.getJavaProject().getOptions(true)))
    throw new IllegalArgumentException("STALE_ANALYSIS: source or binding context changed during analysis");
   var file=MathematicalReport.planned(unit.getPath().toPortableString(),source,compilerOptions,options,environment,analysis,false);
   plans.add(new Planned(unit,analysis,environment,file));
  }
  return List.copyOf(plans);
 }
 private static MathematicalReport report(MathematicalArguments args,MathCleanUpOptions options,Map<String,String> config,
   MathematicalArtifacts.Receipt artifacts,List<Planned> plans,String status,Boolean rollbackComplete,List<String> failures) {
  return new MathematicalReport(1,args.project(),args.apply()?"apply":"analysis",options.toMap(),config,
    artifacts.sdkSha256(),artifacts.adapterBundleSha256(),plans.stream().map(plan->{
     try { return plan.file().withObserved(plan.unit().getSource()); }
     catch(CoreException | RuntimeException unavailable) { return plan.file().withObserved(null); }
    }).toList(),status,rollbackComplete,failures);
 }
 private static Map<String,String> configuration(Path file) throws IOException {
  Properties properties=new Properties() {
   private static final long serialVersionUID=1L;
   @Override public synchronized Object put(Object key,Object value) {
    if(containsKey(key)) throw new IllegalArgumentException("Duplicate configuration key: "+key);
    return super.put(key,value);
   }
  };
  try(Reader reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)) { properties.load(reader); }
  Map<String,String> values=new HashMap<>();
  for(String key:properties.stringPropertyNames()) {
   if(!MathCleanUpOptions.defaults(25).toMap().containsKey(key)) throw new IllegalArgumentException("Unknown mathematics configuration key: "+key);
   values.put(key,properties.getProperty(key));
  }
  return Map.copyOf(values);
 }
 private static List<ICompilationUnit> units(IJavaProject project) throws CoreException {
  List<ICompilationUnit> units=new ArrayList<>();
  for(var fragment:project.getPackageFragments()) if(fragment.getKind()==IPackageFragmentRoot.K_SOURCE)
   for(ICompilationUnit unit:fragment.getCompilationUnits()) units.add(unit);
  units.sort(Comparator.comparing(unit->unit.getPath().toPortableString()));return List.copyOf(units);
 }
 private static void protectReport(MathematicalArguments arguments,List<ICompilationUnit> units) throws IOException {
  Path report=arguments.report().toAbsolutePath().normalize();
  List<Path> protectedPaths=new ArrayList<>();protectedPaths.add(arguments.configuration().toAbsolutePath().normalize());
  for(ICompilationUnit unit:units) if(unit.getResource().getLocation()!=null) protectedPaths.add(unit.getResource().getLocation().toFile().toPath().toAbsolutePath().normalize());
  for(Path protectedPath:protectedPaths) if(report.equals(protectedPath) || Files.exists(report)&&Files.exists(protectedPath)&&Files.isSameFile(report,protectedPath))
   throw new IllegalArgumentException("The report must not overwrite configuration or source: "+report);
 }
 private static void cancelled(IProgressMonitor monitor) { if(monitor.isCanceled())throw new OperationCanceledException(); }
 private record Planned(ICompilationUnit unit,Analysis analysis,MathematicalEnvironment.Snapshot environment,MathematicalReport.FileReport file) {
  MathematicalChange change(MathCleanUpOptions options) { return new MathematicalChange(MathematicalCleanUpCore.label(options),unit,analysis,options,()->options,environment); }
 }
}
