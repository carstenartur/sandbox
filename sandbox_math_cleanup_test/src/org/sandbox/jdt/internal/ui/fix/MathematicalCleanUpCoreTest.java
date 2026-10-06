package org.sandbox.jdt.internal.ui.fix;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;

class MathematicalCleanUpCoreTest {
 @Test void disabledDefaultsDoNotRequestAnAstOrStep() {
  var cleanup=new MathematicalCleanUpCore(MathCleanUpOptions.defaults(17).toMap());
  assertFalse(cleanup.getRequirements().requiresAST());assertEquals(0,cleanup.getStepDescriptions().length);
 }
 @Test void explicitEnabledCleanupRequestsBindingsAndExplainsProfile() {
  var values=new HashMap<>(MathCleanUpOptions.defaults(17).toMap());values.put(MathCleanUpOptions.CLEANUP,"true");
  var cleanup=new MathematicalCleanUpCore(values);
  assertTrue(cleanup.getRequirements().requiresAST());
  assertTrue(cleanup.getStepDescriptions()[0].contains("PRESERVE_JAVA"));
  assertTrue(cleanup.getPreview().contains("BigInteger.valueOf(a * b)"));
 }
 @Test void checkedContractIsVisibleAndInvalidOptionsCannotPretendToBeValid() {
  var values=new HashMap<>(MathCleanUpOptions.defaults(17).toMap());values.put(MathCleanUpOptions.CLEANUP,"true");
  values.put(MathCleanUpOptions.SAFETY,"CHECKED_THROW");values.put(MathCleanUpOptions.CHECKED_OPT_IN,"true");
  var cleanup=new MathematicalCleanUpCore(values);
  assertTrue(cleanup.getStepDescriptions()[0].contains("ArithmeticException"));
  assertTrue(cleanup.getPreview().contains(MathCleanUpOptions.CHECKED_WARNING));
  values.put(MathCleanUpOptions.SAFETY,"typo");cleanup.setOptions(values);
  assertTrue(cleanup.getPreview().contains("Invalid"));
 }
}
