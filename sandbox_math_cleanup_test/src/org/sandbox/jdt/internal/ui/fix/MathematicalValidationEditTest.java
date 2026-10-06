/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jdt.internal.corext.refactoring.util.TextEditUtil;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.*;
import org.junit.jupiter.api.Test;

class MathematicalValidationEditTest {
 @Test void copiedMergedChangesValidateBeforeAnyMutation() throws Exception {
  var current=new AtomicBoolean(true);var root=new MultiTextEdit();var replacement=new ReplaceEdit(1,2,"XY");
  replacement.addChild(new MathematicalValidationEdit(1,current::get));root.addChild(replacement);
  var merged=TextEditUtil.merge(root,new ReplaceEdit(5,1,"!"));var copy=merged.copy();
  var preview=new Document("abcdef");copy.copy().apply(preview);assertEquals("aXYde!",preview.get());
  current.set(false);var live=new Document("abcdef");
  assertThrows(MalformedTreeException.class,()->copy.apply(live));assertEquals("abcdef",live.get());
 }
 @Test void everyIndividuallySelectedReplacementRetainsItsGuard() {
  for(int offset: new int[]{0,2,5}) {
   var replacement=new ReplaceEdit(offset,1,"changed");replacement.addChild(new MathematicalValidationEdit(offset,()->false));
   var root=new MultiTextEdit();root.addChild(replacement);var document=new Document("abcdef");
   assertThrows(MalformedTreeException.class,()->root.copy().apply(document));assertEquals("abcdef",document.get());
  }
 }
 @Test void undoUsesTheCapturedInverseWithoutRevalidatingOldEvidence() throws Exception {
  var current=new AtomicBoolean(true);var root=new MultiTextEdit();var replacement=new ReplaceEdit(0,1,"longer");
  replacement.addChild(new MathematicalValidationEdit(0,current::get));root.addChild(replacement);
  var document=new Document("abc");var undo=root.apply(document);assertEquals("longerbc",document.get());
  current.set(false);undo.apply(document);assertEquals("abc",document.get());
 }
}
