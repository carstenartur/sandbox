/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.ltk.core.refactoring.Change;
/** Best-effort reverse execution; missing or failed inverses never count as success. */
final class MathematicalRollback {
 private MathematicalRollback() { }
 static Result attempt(List<Change> undo) {
  List<String> failures=new ArrayList<>();
  for(Change inverse:undo.reversed()) {
   if(inverse==null) { failures.add("No inverse was returned for a performed change");continue; }
   try {
    inverse.initializeValidationData(new NullProgressMonitor());
    var validation=inverse.isValid(new NullProgressMonitor());
    if(validation.hasFatalError()) throw new IllegalStateException(validation.toString());
    Change redo=inverse.perform(new NullProgressMonitor());if(redo!=null)redo.dispose();
   } catch(CoreException | RuntimeException failure) { failures.add(inverse.getName()+": "+failure.getMessage()); }
  }
  return new Result(failures.isEmpty(),List.copyOf(failures));
 }
 record Result(boolean complete,List<String> failures) { Result { failures=List.copyOf(failures); } }
}
