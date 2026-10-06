/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;

import java.util.Objects;
import java.util.function.Supplier;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.Analysis;

/** A standard JDT change whose copied edit leaves retain a pre-mutation guard. */
final class MathematicalChange extends CompilationUnitChange {
 private final ICompilationUnit unit;
 private final Analysis analysis;
 private final MathCleanUpOptions analyzedOptions;
 private final Supplier<MathCleanUpOptions> currentOptions;
 private final MathematicalEnvironment.Snapshot environment;
 MathematicalChange(String name,ICompilationUnit unit,Analysis analysis,MathCleanUpOptions options,
   Supplier<MathCleanUpOptions> currentOptions,MathematicalEnvironment.Snapshot environment) {
  super(name,unit);
  this.unit=Objects.requireNonNull(unit);this.analysis=Objects.requireNonNull(analysis);
  this.analyzedOptions=Objects.requireNonNull(options);this.currentOptions=Objects.requireNonNull(currentOptions);
  this.environment=Objects.requireNonNull(environment);
  TextEdit root=analysis.newEdit();TextEdit[] replacements=root.getChildren();
  for(TextEdit replacement:replacements) replacement.addChild(new MathematicalValidationEdit(replacement.getOffset(),this::isCurrentForEdit));
  setEdit(root);
  // JDT merges and detaches roots; groups must reference the actual guarded replacements.
  addTextEditGroup(new TextEditGroup(name,replacements));
 }
 boolean isCurrent() {
  try {
   return analyzedOptions.equals(currentOptions.get()) && environment.matches(unit.getJavaProject())
     && analysis.matches(unit.getSource(),unit.getJavaProject().getOptions(true));
  } catch(CoreException | IllegalArgumentException stale) { return false; }
 }
 private boolean isCurrentForEdit() {
  try {
   return analyzedOptions.equals(currentOptions.get()) && environment.revisionsCurrent()
     && analysis.matches(unit.getSource(),unit.getJavaProject().getOptions(true));
  } catch(CoreException | IllegalArgumentException stale) { return false; }
 }
 void requireCurrent() throws CoreException {
  if(!isCurrent()) throw new CoreException(Status.error("STALE_ANALYSIS: source, compiler settings, classpath or mathematics profile changed"));
 }
 @Override public RefactoringStatus isValid(IProgressMonitor monitor) throws CoreException {
  RefactoringStatus status=super.isValid(monitor);
  if(!isCurrent()) status.addFatalError("STALE_ANALYSIS: analyze the current source and settings again");
  return status;
 }
}
