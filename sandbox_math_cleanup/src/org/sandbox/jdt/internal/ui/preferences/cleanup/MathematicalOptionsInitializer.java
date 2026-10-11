/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.preferences.cleanup;

import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUpOptionsInitializer;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;

/** Initializes normalized options; cleanup and contract-changing opt-ins stay disabled. */
public final class MathematicalOptionsInitializer implements ICleanUpOptionsInitializer {
 @Override public void setDefaultOptions(CleanUpOptions options) {
  MathCleanUpOptions.defaults(25).toMap().forEach(options::setOption);
 }
}
