package org.sandbox.jdt.internal.ui.fix;
import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.junit.jupiter.api.Test;
class MathematicalRollbackTest {
 @Test void missingInverseCannotClaimSuccessfulRollback() {
  var result=MathematicalRollback.attempt(java.util.Arrays.asList((Change)null));assertFalse(result.complete());assertTrue(result.failures().getFirst().contains("No inverse"));
 }
 @Test void failedUndoIsReportedAndDoesNotPreventOtherUndoAttempts() {
  List<String> calls=new ArrayList<>();
  var result=MathematicalRollback.attempt(List.of(inverse("first",false,calls),inverse("second",true,calls),inverse("third",false,calls)));
  assertEquals(List.of("third","second","first"),calls);assertFalse(result.complete());assertEquals(1,result.failures().size());assertTrue(result.failures().getFirst().contains("second"));
 }
 @Test void onlySuccessfulInverseExecutionReportsCompleteRollback() {
  var result=MathematicalRollback.attempt(List.of(inverse("first",false,new ArrayList<>())));assertTrue(result.complete());assertTrue(result.failures().isEmpty());
 }
 private static Change inverse(String name,boolean fail,List<String> calls) {
  return new Change(){
   @Override public String getName(){return name;}
   @Override public void initializeValidationData(IProgressMonitor monitor){}
   @Override public RefactoringStatus isValid(IProgressMonitor monitor){return new RefactoringStatus();}
   @Override public Change perform(IProgressMonitor monitor)throws CoreException {calls.add(name);if(fail)throw new CoreException(Status.error("Cannot undo "+name));return null;}
   @Override public Object getModifiedElement(){return null;}
  };
 }
}
