/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Proxy;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/** Verifies the registered annotation provider through the real Eclipse model. */
public class AnnotationQuickAssistBindingTest {

	@RegisterExtension
	EclipseJava22 context = new EclipseJava22();

	@Test
	public void fallbackParsingResolvesThePrimitiveExampleAndAppliesItsProposal() throws Exception {
		String source = "package trial; public class Sample { boolean run(boolean flag) { return !!flag; } }";
		ICompilationUnit unit = context.getSourceFolder().createPackageFragment("trial", false, null)
				.createCompilationUnit("Sample.java", source, true, null);
		int offset = source.indexOf("!!flag") + 1;
		IInvocationContext invocation = (IInvocationContext) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { IInvocationContext.class }, (proxy, method, arguments) -> switch (method.getName()) {
				case "getCompilationUnit" -> unit;
				case "getASTRoot" -> null;
				case "getSelectionOffset" -> offset;
				case "getSelectionLength" -> 0;
				default -> null;
				});
		IJavaCompletionProposal[] proposals = new TriggerPatternQuickAssistProcessor()
				.getAssists(invocation, new IProblemLocation[0]);
		assertNotNull(proposals);
		assertEquals(1, proposals.length);
		assertEquals("Remove boolean double negation", proposals[0].getDisplayString());
		IEditorPart editor = JavaUI.openInEditor(unit);
		try {
			// CUCorrectionProposal applies to the compilation unit's editor document.
			ITextEditor textEditor = (ITextEditor) editor;
			IDocument document = textEditor.getDocumentProvider().getDocument(editor.getEditorInput());
			assertNotNull(document);
			proposals[0].apply(document);
			assertEquals(source.replace("!!flag", "flag"), document.get());
		} finally {
			editor.getSite().getPage().closeEditor(editor, false);
		}
	}
}
