package org.sandbox.jdt.internal.ui.fix;

import java.util.Map;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUpCoreWrapper;

/** Standard JDT profile contribution; deliberately not registered as a save action. */
public final class MathematicalCleanUp extends AbstractCleanUpCoreWrapper<MathematicalCleanUpCore> {
 public MathematicalCleanUp() { this(Map.of()); }
 public MathematicalCleanUp(Map<String,String> options) { super(options,new MathematicalCleanUpCore()); }
}
