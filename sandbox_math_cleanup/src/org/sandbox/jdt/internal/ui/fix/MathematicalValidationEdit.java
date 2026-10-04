/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 at https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.TextEdit;

/** A child of each selectable replacement; copied by the standard JDT/LTK path. */
final class MathematicalValidationEdit extends MultiTextEdit {
 private final BooleanSupplier current;
 MathematicalValidationEdit(int offset, BooleanSupplier current) {
  super(offset,0); this.current=Objects.requireNonNull(current);
 }
 private MathematicalValidationEdit(MathematicalValidationEdit original) {
  super(original); current=original.current;
 }
 @Override protected TextEdit doCopy() { return new MathematicalValidationEdit(this); }
 @Override protected void checkIntegrity() throws MalformedTreeException {
  super.checkIntegrity();
  if(!current.getAsBoolean()) throw new MalformedTreeException(getParent(),this,
    "STALE_ANALYSIS: source, binding environment or mathematics options changed; analyze again");
 }
}
