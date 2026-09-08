/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.stream.Stream;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.jdt.internal.ui.preferences.formatter.IModifyDialogTabPage.IModificationListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.preferences.cleanup.CleanUpMessages;
import org.sandbox.jdt.internal.ui.preferences.cleanup.SandboxCodeTabPage;

/** Actual SWT controls must retain JDT selection and dependency behavior. */
class LoopConversionPreferencesTest {
	@Test
	void sourceSelectionAndAvailabilitySurviveMasterAndTargetChanges() {
		Display.getDefault().syncExec(() -> {
			Shell shell = new Shell();
			try {
				var values = new HashMap<String, String>();
				for (String key : new String[] { MYCleanUpConstants.LOOP_CONVERSION_ENABLED,
						MYCleanUpConstants.LOOP_CONVERSION_FROM_ENHANCED_FOR, MYCleanUpConstants.LOOP_CONVERSION_FROM_ITERATOR_WHILE,
						MYCleanUpConstants.LOOP_CONVERSION_FROM_STREAM, MYCleanUpConstants.LOOP_CONVERSION_FROM_CLASSIC_FOR }) {
					values.put(key, "true");
				}
				values.put(MYCleanUpConstants.LOOP_CONVERSION_TARGET_FORMAT, "stream");
				var page = new SandboxCodeTabPage();
				page.setWorkingValues(values);
				page.setModifyListener(new IModificationListener() {
					@Override public void valuesModified() { }
					@Override public void updateStatus(IStatus status) { }
				});
				Composite contents = page.createContents(shell);
				Button master = button(contents, CleanUpMessages.LoopConversion_Enable);
				Button enhanced = button(contents, CleanUpMessages.LoopConversion_From_EnhancedFor);
				Button iterator = button(contents, CleanUpMessages.LoopConversion_From_IteratorWhile);
				Button stream = button(contents, CleanUpMessages.LoopConversion_From_Stream);
				Button classic = button(contents, CleanUpMessages.LoopConversion_From_ClassicFor);
				Combo target = controls(contents).filter(Combo.class::isInstance).map(Combo.class::cast).findFirst().orElseThrow();
				assertEquals(5, page.getCleanUpCount());
				assertFalse(stream.getEnabled());
				page.doSetAll(false);
				assertEquals(0, page.getSelectedCleanUpCount());
				assertFalse(target.getEnabled());
				assertTrue(Stream.of(master, enhanced, iterator, stream, classic).noneMatch(Button::getSelection));
				page.doSetAll(true);
				assertEquals(5, page.getSelectedCleanUpCount());
				assertTrue(Stream.of(master, enhanced, iterator, stream, classic).allMatch(Button::getSelection));
				assertFalse(stream.getEnabled());
				target.select(1);
				target.notifyListeners(SWT.Selection, new Event());
				assertFalse(enhanced.getEnabled());
				assertFalse(classic.getEnabled());
				assertTrue(iterator.getEnabled() && stream.getEnabled());
				master.setSelection(false);
				master.notifyListeners(SWT.Selection, new Event());
				assertTrue(Stream.of(enhanced, iterator, stream, classic).noneMatch(Button::getEnabled));
				master.setSelection(true);
				master.notifyListeners(SWT.Selection, new Event());
				assertFalse(enhanced.getEnabled());
				assertFalse(classic.getEnabled());
				assertTrue(iterator.getEnabled() && stream.getEnabled());
			} finally {
				shell.dispose();
			}
		});
	}

	private static Button button(Composite parent, String label) {
		return controls(parent).filter(Button.class::isInstance).map(Button.class::cast)
				.filter(button -> label.equals(button.getText())).findFirst().orElseThrow();
	}

	private static Stream<Control> controls(Composite parent) {
		return Stream.of(parent.getChildren()).flatMap(control -> control instanceof Composite composite
				? Stream.concat(Stream.of(control), controls(composite)) : Stream.of(control));
	}
}
