/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;

import java.util.HashMap;
import java.util.Map;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.corext.fix.CleanUpPreferenceUtil;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import de.regelsuche.sdk.optimization.SafetyProfile;

/** Explicit JDT cleanup. Effective profiles are scoped to each project invocation. */
public final class MathematicalCleanUpCore extends AbstractCleanUp {
 private Map<String,String> values=Map.of();
 private IProgressMonitor cancellation=new NullProgressMonitor();
 private RefactoringStatus diagnostics=new RefactoringStatus();
 private final Map<String,MathematicalEnvironment.Snapshot> environments=new HashMap<>();
 private final Map<String,String> originalSources=new HashMap<>();
 private final Map<String,ProfileSnapshot> profiles=new HashMap<>();
 public MathematicalCleanUpCore() { this(Map.of()); }
 public MathematicalCleanUpCore(Map<String,String> values) { setOptions(values); }
 @Override public void setOptions(CleanUpOptions options) {
  super.setOptions(options);Map<String,String> copy=new HashMap<>();
  options.getKeys().forEach(key->copy.put(key,options.getValue(key)));values=Map.copyOf(copy);
 }
 @Override public CleanUpRequirements getRequirements() { return new CleanUpRequirements(isEnabled(MathCleanUpOptions.CLEANUP),false,false,null); }
 @Override public RefactoringStatus checkPreConditions(IJavaProject project,ICompilationUnit[] units,IProgressMonitor monitor) {
  cancellation=monitor==null?new NullProgressMonitor():monitor;diagnostics=new RefactoringStatus();
  try {
   MathCleanUpOptions options=MathCleanUpOptions.parse(values,targetJava(project));
   profiles.put(project.getHandleIdentifier(),new ProfileSnapshot(options,persistedMathOptions(project)));
   if(options.enabled()) {
    environments.put(project.getHandleIdentifier(),MathematicalEnvironment.capture(project));
    for(ICompilationUnit unit:units) originalSources.put(unit.getPrimary().getHandleIdentifier(),unit.getPrimary().getSource());
    if(options.safety()==SafetyProfile.CHECKED_THROW) diagnostics.addWarning(MathCleanUpOptions.CHECKED_WARNING);
    if(options.mathematicalOptIn()) diagnostics.addWarning(MathCleanUpOptions.MATHEMATICAL_WARNING);
   }
  } catch(IllegalArgumentException | CoreException invalid) { diagnostics.addFatalError(invalid.getMessage()); }
  return diagnostics;
 }
 @Override public ICleanUpFix createFix(CleanUpContext context) throws CoreException {
  ICompilationUnit unit=context.getCompilationUnit();
  if(context.getAST()==null || unit==null) return null;
  IJavaProject project=unit.getJavaProject();ProfileSnapshot profile;MathCleanUpOptions options;
  try {
   profile=profiles.get(project.getHandleIdentifier());
   if(profile==null) {
    profile=new ProfileSnapshot(MathCleanUpOptions.parse(values,targetJava(project)),persistedMathOptions(project));
    profiles.put(project.getHandleIdentifier(),profile);
   }
   options=currentOptions(project,profile);
  } catch(IllegalArgumentException invalid) { diagnostics.addFatalError(invalid.getMessage());return null; }
  if(!options.enabled()) return null;
  ICompilationUnit primary=unit.getPrimary();String source=unit.getSource();
  String original=originalSources.getOrDefault(primary.getHandleIdentifier(),primary.getSource());
  if(!source.equals(original) || !primary.getSource().equals(original)) {
   diagnostics.addInfo("MATHEMATICS_PHASE_CHANGED: another cleanup or editor changed this source; run mathematics on the current file in a separate operation");
   return null;
  }
  MathematicalEnvironment.Snapshot environment=environments.get(project.getHandleIdentifier());
  if(environment==null) environment=MathematicalEnvironment.capture(project);
  Map<String,String> compilerOptions=Map.copyOf(project.getOptions(true));
  var analysis=MathematicalAnalysisJob.runAndWait(monitor->MathematicalAnalysis.analyze(context.getAST(),source,options,monitor,-1,0),cancellation);
  if(!environment.matches(project) || !analysis.matches(source,compilerOptions) || !source.equals(unit.getSource())
    || !compilerOptions.equals(project.getOptions(true))) {
   diagnostics.addFatalError("STALE_ANALYSIS: source or compiler settings changed during analysis");return null;
  }
  analysis.diagnostics().stream().limit(20).forEach(item->diagnostics.addInfo(item.code()+": "+item.message()));
  if(!analysis.changed()) return null;
  ProfileSnapshot analyzedProfile=profile;MathematicalEnvironment.Snapshot analyzedEnvironment=environment;
  return monitor->{
   if(monitor!=null && monitor.isCanceled()) throw new OperationCanceledException();
   MathematicalChange change=new MathematicalChange(label(options),primary,analysis,options,()->currentOptions(project,analyzedProfile),analyzedEnvironment);
   change.requireCurrent();return change;
  };
 }
 private MathCleanUpOptions currentOptions(IJavaProject project,ProfileSnapshot profile) {
  if(profiles.get(project.getHandleIdentifier())!=profile || !profile.persisted().equals(persistedMathOptions(project)))
   throw new IllegalArgumentException("STALE_ANALYSIS: this project's mathematics profile changed");
  return profile.requested();
 }
 private static Map<String,String> persistedMathOptions(IJavaProject project) {
  Map<String,String> all=CleanUpPreferenceUtil.loadOptions(new ProjectScope(project.getProject()));Map<String,String> math=new HashMap<>();
  if(all!=null) all.forEach((key,value)->{if(key.equals(MathCleanUpOptions.CLEANUP)||key.startsWith(MathCleanUpOptions.CLEANUP+"."))math.put(key,value);});
  return Map.copyOf(math);
 }
 @Override public RefactoringStatus checkPostConditions(IProgressMonitor monitor) { return diagnostics; }
 @Override public String[] getStepDescriptions() {
  if(!isEnabled(MathCleanUpOptions.CLEANUP)) return new String[0];
  try { return new String[]{label(MathCleanUpOptions.parse(values,25))}; }
  catch(IllegalArgumentException invalid) { return new String[]{invalid.getMessage()}; }
 }
 @Override public String getPreview() {
  try {
   MathCleanUpOptions options=MathCleanUpOptions.parse(values,25);
   if(options.mathematicalOptIn()) return "// "+MathCleanUpOptions.MATHEMATICAL_WARNING+"\n"
     +"// Example: int 2*x/2 may become x, with the proven local ranges in Javadoc.\n";
   return (options.safety()==SafetyProfile.CHECKED_THROW?"// "+MathCleanUpOptions.CHECKED_WARNING+"\n":"")
     +"// The standard file preview shows the verified edits, generated checks and diagnostics.\n"
     +"BigInteger input = BigInteger.valueOf(a * b);\nBigInteger sum = input.add(BigInteger.ONE);\n"
     +"BigInteger result = sum.subtract(BigInteger.ONE);\nreturn result.intValue();\n";
  } catch(IllegalArgumentException invalid) { return "// Invalid mathematics options: "+invalid.getMessage()+"\n"; }
 }
 static String label(MathCleanUpOptions options) {
  if(options.mathematicalOptIn()) return "Optimize mathematics (explicit mathematical contract; document local ranges)";
  return options.safety()==SafetyProfile.CHECKED_THROW?"Optimize mathematics (CHECKED_THROW: may add ArithmeticException)"
    :"Optimize verified mathematics ("+options.safety()+")";
 }
 static int targetJava(IJavaProject project) {
  String version=project.getOption(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM,true);
  return Integer.parseInt(version.startsWith("1.")?version.substring(2):version);
 }
 private record ProfileSnapshot(MathCleanUpOptions requested,Map<String,String> persisted) { }
}
