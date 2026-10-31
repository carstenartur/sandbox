/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.ui.fix;

import java.util.function.BooleanSupplier;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.CopySourceEdit;
import org.eclipse.text.edits.CopyTargetEdit;
import org.eclipse.text.edits.ISourceModifier;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEditGroup;

/**
 * One selectable diff: phase receipts and freshness guards survive JDT edit merging.
 * The native CU host shares its complete FileBuffer document with the primary
 * working copy, so current() also detects appended source. A detached raw
 * TextEdit only validates its analyzed replacement span: the public TextEdit API
 * exposes no document/EOF hook to a copied leaf or its source modifier.
 */
final class FunctionalMathChange extends CompilationUnitChange {
 private final ICompilationUnit unit;
 private final FunctionalMathCoordinator.Plan plan;
 private final BooleanSupplier optionsCurrent;
 private final MathematicalEnvironment.Snapshot environment;
 FunctionalMathChange(ICompilationUnit unit,FunctionalMathCoordinator.Plan plan,BooleanSupplier optionsCurrent,
   MathematicalEnvironment.Snapshot environment) {
  super("Optimize verified mathematics and convert remaining loops",unit);
  this.unit=unit;this.plan=plan;this.optionsCurrent=optionsCurrent;this.environment=environment;
  var replacement=new ReplaceEdit(0,plan.original().length(),plan.result());
  replacement.addChild(new MathematicalValidationEdit(0,()->current(false)));
  // Source computation runs before any document mutation. The empty copy has no
  // textual effect, but validates the actual replacement range after root merging.
  var source=new CopySourceEdit(0,plan.original().length());source.setSourceModifier(new SourceGuard(plan.original()));
  replacement.addChild(source);replacement.addChild(new CopyTargetEdit(plan.original().length(),source));
  var root=new MultiTextEdit();root.addChild(replacement);setEdit(root);
  addTextEditGroup(new TextEditGroup(getName(),replacement));
 }
 private record SourceGuard(String expected) implements ISourceModifier {
  @Override public ReplaceEdit[] getModifications(String actual) {
   if(!expected.equals(actual)) throw new MalformedTreeException(null,null,"STALE_DOCUMENT: analyzed replacement range differs");
   return new ReplaceEdit[]{new ReplaceEdit(0,actual.length(),"")};
  }
  @Override public ISourceModifier copy() { return new SourceGuard(expected); }
 }
 private boolean current(boolean full) {
  try {
   return optionsCurrent.getAsBoolean() && (full?environment.matches(unit.getJavaProject()):environment.revisionsCurrent())
     && plan.original().equals(unit.getSource()) && plan.compilerOptions().equals(unit.getJavaProject().getOptions(true))
     && plan.phases().stream().allMatch(phase->phase.sourceDependencies().current());
  } catch(CoreException | IllegalArgumentException stale) { return false; }
 }
 void requireCurrent() throws CoreException {
  if(!current(true)) throw new CoreException(Status.error("STALE_ANALYSIS: source, dependencies, classpath or cleanup profiles changed"));
 }
 @Override public RefactoringStatus isValid(IProgressMonitor monitor) throws CoreException {
  var status=super.isValid(monitor);
  if(!current(true)) status.addFatalError("STALE_ANALYSIS: analyze the current source and cleanup profiles again");
  return status;
 }
}
