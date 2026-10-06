/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.preferences.cleanup;

import static org.junit.jupiter.api.Assertions.*;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;

class MathematicalOptionsInitializerTest {
 @Test void defaultsSupplyExactlyTheNineNormalizedDisabledOptions() {
  CleanUpOptions options=new CleanUpOptions();new MathematicalOptionsInitializer().setDefaultOptions(options);
  assertEquals(9,options.getKeys().size());
  MathCleanUpOptions.defaults(25).toMap().forEach((key,value)->assertEquals(value,options.getValue(key)));
  assertEquals(CleanUpOptions.FALSE,options.getValue(MathCleanUpOptions.CLEANUP));
  assertEquals("BIG_INTEGER",options.getValue(MathCleanUpOptions.KINDS));
  assertEquals("PRESERVE_JAVA",options.getValue(MathCleanUpOptions.SAFETY));
  assertEquals("LOWER_ESTIMATED_RUNTIME",options.getValue(MathCleanUpOptions.GOAL));
 }
}
