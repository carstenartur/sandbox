/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.ui.helper.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;

import org.eclipse.core.commands.operations.IOperationHistory;
import org.eclipse.core.commands.operations.IOperationHistoryListener;
import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.core.commands.operations.OperationHistoryEvent;
import org.eclipse.core.commands.operations.OperationHistoryFactory;

import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension4;
import org.eclipse.jface.text.IDocumentListener;

import org.eclipse.text.undo.DocumentUndoManagerRegistry;
import org.eclipse.text.undo.IDocumentUndoManager;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEclipseEditor;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.utils.SWTBotPreferences;
import org.eclipse.swtbot.swt.finder.waits.Conditions;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.TimeoutException;

import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/**
 * SWTBot probe for eclipse-jdt/eclipse.jdt.ui#454. Besides assertions, it
 * writes the public undo/redo history and editor selection after every step to
 * {@code target/issue-454}.
 */
public class UndoRedoHistorySWTBotTest {

	private static final String FILE_NAME= "UndoRedoProbe.java"; //$NON-NLS-1$
	private static final String INSERTED_TEXT= "restoredCode"; //$NON-NLS-1$
	private static final String INITIAL_SOURCE= """
			package issue454;

			public class UndoRedoProbe {
			    // suffix
			}
			"""; //$NON-NLS-1$
	private static final String EDITED_SOURCE=
			INITIAL_SOURCE.replace("// suffix", "// " + INSERTED_TEXT + "suffix"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	private static final String REPLACED_SOURCE=
			INITIAL_SOURCE.replace("// suffix", "// hsuffix"); //$NON-NLS-1$ //$NON-NLS-2$
	private static final String AWT_KEYBOARD=
			"org.eclipse.swtbot.swt.finder.keyboard.AWTKeyboardStrategy"; //$NON-NLS-1$

	@RegisterExtension
	final AbstractEclipseJava context= new EclipseJava17();

	private final List<String> trace= Collections.synchronizedList(new ArrayList<>());
	private final AtomicInteger documentChanges= new AtomicInteger();

	private SWTWorkbenchBot bot;
	private SWTBotEclipseEditor editor;
	private ITextEditor textEditor;
	private IDocument document;
	private IDocumentUndoManager undoManager;
	private IUndoContext undoContext;
	private IOperationHistory history;
	private IDocumentListener documentListener;
	private IOperationHistoryListener historyListener;
	private String testName;
	private String oldKeyboardStrategy;

	@BeforeEach
	void setUp(TestInfo testInfo) throws Exception {
		testName= testInfo.getTestMethod().map(Method::getName).orElse("unknown"); //$NON-NLS-1$
		oldKeyboardStrategy= SWTBotPreferences.KEYBOARD_STRATEGY;
		SWTBotPreferences.KEYBOARD_STRATEGY= AWT_KEYBOARD;

		bot= new SWTWorkbenchBot();
		closeWelcome();

		IPackageFragment pack= context.getSourceFolder().createPackageFragment("issue454", false, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit(FILE_NAME, INITIAL_SOURCE, true, null);
		IEditorPart part= ui(() -> JavaUI.openInEditor(unit));
		assertTrue(part instanceof ITextEditor, () -> "Not a text editor: " + part); //$NON-NLS-1$
		textEditor= (ITextEditor) part;
		editor= bot.editorByTitle(FILE_NAME).toTextEditor();
		editor.setFocus();

		document= ui(() -> textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput()));
		assertNotNull(document);
		undoManager= DocumentUndoManagerRegistry.getDocumentUndoManager(document);
		assertNotNull(undoManager, "No connected document undo manager"); //$NON-NLS-1$
		ui(() -> {
			undoManager.reset();
			return null;
		});

		undoContext= undoManager.getUndoContext();
		history= OperationHistoryFactory.getOperationHistory();
		installDiagnostics();
		log("Environment os=" + System.getProperty("os.name") //$NON-NLS-1$ //$NON-NLS-2$
				+ ", SWT=" + SWT.getVersion() //$NON-NLS-1$
				+ ", keyboard=" + SWTBotPreferences.KEYBOARD_STRATEGY); //$NON-NLS-1$
		snapshot("baseline"); //$NON-NLS-1$
		assertEquals(INITIAL_SOURCE, text(), this::diagnostics);
	}

	@AfterEach
	void tearDown() throws Exception {
		try {
			if (document != null && undoContext != null) {
				snapshot("tearDown"); //$NON-NLS-1$
			}
		} finally {
			if (document != null && documentListener != null) {
				document.removeDocumentListener(documentListener);
			}
			if (history != null && historyListener != null) {
				history.removeOperationHistoryListener(historyListener);
			}
			try {
				writeTrace();
			} finally {
				if (textEditor != null) {
					ui(() -> {
						PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
								.closeEditor(textEditor, false);
						return null;
					});
				}
				SWTBotPreferences.KEYBOARD_STRATEGY= oldKeyboardStrategy;
			}
		}
	}

	@Test
	void redoneTextIsSelectedButOneUndoRecoversItAfterTypingH() throws Exception {
		typeAndUndo();

		editor.pressShortcut(SWT.MOD1, 'y');
		waitFor(EDITED_SOURCE);
		snapshot("after redo"); //$NON-NLS-1$

		Point selection= selection();
		assertEquals(INSERTED_TEXT.length(), selection.y,
				() -> "Redo did not select the restored text." + diagnostics()); //$NON-NLS-1$
		assertEquals(INSERTED_TEXT, editor.getSelection(),
				() -> "Unexpected redo selection." + diagnostics()); //$NON-NLS-1$

		editor.typeText("h"); //$NON-NLS-1$
		waitFor(REPLACED_SOURCE);
		snapshot("after h"); //$NON-NLS-1$
		assertFalse(undoManager.redoable(),
				() -> "The new edit should replace the old redo branch." + diagnostics()); //$NON-NLS-1$
		assertTrue(undoManager.undoable(),
				() -> "Replacing the selection must itself be undoable." + diagnostics()); //$NON-NLS-1$

		editor.pressShortcut(SWT.MOD1, 'z');
		waitFor(EDITED_SOURCE);
		snapshot("after undoing h"); //$NON-NLS-1$
		assertEquals(EDITED_SOURCE, text(),
				() -> "One Undo did not restore the replaced code." + diagnostics()); //$NON-NLS-1$
	}

	@Test
	void ctrlHLeavesDocumentAndRedoStackUntouched() throws Exception {
		typeAndUndo();
		IUndoableOperation[] redoBefore= redoStack();
		int changeCountBefore= documentChanges.get();
		long stampBefore= stamp();
		snapshot("before Ctrl+H"); //$NON-NLS-1$

		editor.pressShortcut(SWT.MOD1, 'h');
		SWTBotShell search;
		try {
			bot.waitUntil(Conditions.shellIsActive("Search"), 10_000); //$NON-NLS-1$
			search= bot.shell("Search"); //$NON-NLS-1$
		} catch (TimeoutException e) {
			snapshot("Ctrl+H did not open Search"); //$NON-NLS-1$
			fail("Ctrl+H did not open Search." + diagnostics(), e); //$NON-NLS-1$
			return;
		}

		snapshot("Search open"); //$NON-NLS-1$
		assertEquals(INITIAL_SOURCE, text(), this::diagnostics);
		assertEquals(changeCountBefore, documentChanges.get(),
				() -> "Ctrl+H generated a document edit." + diagnostics()); //$NON-NLS-1$
		assertEquals(stampBefore, stamp(),
				() -> "Ctrl+H changed the document stamp." + diagnostics()); //$NON-NLS-1$
		assertSameStack(redoBefore, redoStack(), "Ctrl+H changed the redo stack"); //$NON-NLS-1$

		search.close();
		bot.waitUntil(Conditions.shellCloses(search), 10_000);
		editor.setFocus();
		editor.pressShortcut(SWT.MOD1, 'y');
		waitFor(EDITED_SOURCE);
		snapshot("redo after Ctrl+H"); //$NON-NLS-1$
	}

	@Test
	void cursorMovementLeavesDocumentAndRedoStackUntouched() throws Exception {
		typeAndUndo();
		IUndoableOperation[] redoBefore= redoStack();
		int changeCountBefore= documentChanges.get();
		long stampBefore= stamp();

		editor.pressShortcut(SWT.NONE, SWT.ARROW_LEFT, '\0');
		bot.sleep(100);
		snapshot("after cursor movement"); //$NON-NLS-1$

		assertEquals(INITIAL_SOURCE, text(), this::diagnostics);
		assertEquals(changeCountBefore, documentChanges.get(),
				() -> "Cursor movement generated a document edit." + diagnostics()); //$NON-NLS-1$
		assertEquals(stampBefore, stamp(),
				() -> "Cursor movement changed the document stamp." + diagnostics()); //$NON-NLS-1$
		assertSameStack(redoBefore, redoStack(), "Cursor movement changed the redo stack"); //$NON-NLS-1$

		editor.pressShortcut(SWT.MOD1, 'y');
		waitFor(EDITED_SOURCE);
		snapshot("redo after cursor movement"); //$NON-NLS-1$
	}

	private void typeAndUndo() throws Exception {
		editor.typeText(3, 7, INSERTED_TEXT);
		waitFor(EDITED_SOURCE);
		snapshot("after typing"); //$NON-NLS-1$
		assertTrue(undoManager.undoable(), this::diagnostics);

		editor.pressShortcut(SWT.MOD1, 'z');
		waitFor(INITIAL_SOURCE);
		snapshot("after undo"); //$NON-NLS-1$
		assertTrue(undoManager.redoable(),
				() -> "Undo did not create a redo entry." + diagnostics()); //$NON-NLS-1$
	}

	private void installDiagnostics() {
		documentListener= new IDocumentListener() {
			@Override
			public void documentAboutToBeChanged(DocumentEvent event) {
				log("DOCUMENT about-to-change offset=" + event.getOffset() //$NON-NLS-1$
						+ ", length=" + event.getLength() //$NON-NLS-1$
						+ ", inserted=" + quoted(event.getText())); //$NON-NLS-1$
			}

			@Override
			public void documentChanged(DocumentEvent event) {
				documentChanges.incrementAndGet();
				log("DOCUMENT changed offset=" + event.getOffset() //$NON-NLS-1$
						+ ", length=" + event.getLength() //$NON-NLS-1$
						+ ", inserted=" + quoted(event.getText()) //$NON-NLS-1$
						+ ", stamp=" + stamp()); //$NON-NLS-1$
			}
		};
		document.addDocumentListener(documentListener);

		historyListener= event -> {
			IUndoableOperation operation= event.getOperation();
			if (operation.hasContext(undoContext)) {
				log("HISTORY " + eventName(event.getEventType()) + ' ' + describe(operation)); //$NON-NLS-1$
			}
		};
		history.addOperationHistoryListener(historyListener);
	}

	private void snapshot(String phase) throws Exception {
		Point range= selection();
		log("\n=== " + phase + " ==="); //$NON-NLS-1$ //$NON-NLS-2$
		log("document=" + quoted(text())); //$NON-NLS-1$
		log("stamp=" + stamp()); //$NON-NLS-1$
		log("selection=offset:" + range.x + ", length:" + range.y //$NON-NLS-1$ //$NON-NLS-2$
				+ ", text:" + quoted(editor.getSelection())); //$NON-NLS-1$
		log("manager=undoable:" + undoManager.undoable() //$NON-NLS-1$
				+ ", redoable:" + undoManager.redoable()); //$NON-NLS-1$
		log("UNDO " + describe(undoStack())); //$NON-NLS-1$
		log("REDO " + describe(redoStack())); //$NON-NLS-1$
	}

	private String text() throws Exception {
		return ui(document::get);
	}

	private Point selection() throws Exception {
		return ui(() -> editor.getStyledText().widget.getSelectionRange());
	}

	private long stamp() {
		return document instanceof IDocumentExtension4 extension
				? extension.getModificationStamp()
				: IDocumentExtension4.UNKNOWN_MODIFICATION_STAMP;
	}

	private IUndoableOperation[] undoStack() {
		return history.getUndoHistory(undoContext);
	}

	private IUndoableOperation[] redoStack() {
		return history.getRedoHistory(undoContext);
	}

	private void assertSameStack(IUndoableOperation[] expected, IUndoableOperation[] actual, String message) {
		assertEquals(expected.length, actual.length, () -> message + diagnostics());
		for (int i= 0; i < expected.length; i++) {
			int index= i;
			assertSame(expected[i], actual[i],
					() -> message + " at index " + index + diagnostics()); //$NON-NLS-1$
		}
	}

	private static String describe(IUndoableOperation[] operations) {
		StringBuilder result= new StringBuilder("["); //$NON-NLS-1$
		for (int i= 0; i < operations.length; i++) {
			if (i > 0) {
				result.append(", "); //$NON-NLS-1$
			}
			result.append(i).append(':').append(describe(operations[i]));
		}
		return result.append(']').toString();
	}

	private static String describe(IUndoableOperation operation) {
		return "{id=" + Integer.toHexString(System.identityHashCode(operation)) //$NON-NLS-1$
				+ ", class=" + operation.getClass().getName() //$NON-NLS-1$
				+ ", label=" + quoted(operation.getLabel()) //$NON-NLS-1$
				+ ", canUndo=" + operation.canUndo() //$NON-NLS-1$
				+ ", canRedo=" + operation.canRedo() + '}'; //$NON-NLS-1$
	}

	private void waitFor(String expected) {
		bot.waitUntil(new DefaultCondition() {
			@Override
			public boolean test() throws Exception {
				return expected.equals(editor.getText());
			}

			@Override
			public String getFailureMessage() {
				return "Editor text did not become " + quoted(expected); //$NON-NLS-1$
			}
		}, 10_000);
	}

	private void closeWelcome() {
		try {
			bot.viewByTitle("Welcome").close(); //$NON-NLS-1$
		} catch (WidgetNotFoundException e) {
			// Already closed.
		}
	}

	private void log(String line) {
		trace.add(line);
		System.out.println("[issue-454] " + line); //$NON-NLS-1$
	}

	private String diagnostics() {
		synchronized (trace) {
			return "\n\nIssue #454 trace:\n" + String.join("\n", trace); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private void writeTrace() throws IOException {
		if (testName == null) {
			return;
		}
		Path root= Path.of(System.getProperty("sandbox.repository.root", ".")); //$NON-NLS-1$ //$NON-NLS-2$
		Path output= root.resolve("sandbox_usage_view_test/target/issue-454") //$NON-NLS-1$
				.resolve(testName + ".txt"); //$NON-NLS-1$
		Files.createDirectories(output.getParent());
		Files.writeString(output, diagnostics().stripLeading() + System.lineSeparator(), StandardCharsets.UTF_8);
	}

	private static String eventName(int type) {
		return switch (type) {
			case OperationHistoryEvent.ABOUT_TO_REDO -> "ABOUT_TO_REDO"; //$NON-NLS-1$
			case OperationHistoryEvent.ABOUT_TO_UNDO -> "ABOUT_TO_UNDO"; //$NON-NLS-1$
			case OperationHistoryEvent.OPERATION_ADDED -> "ADDED"; //$NON-NLS-1$
			case OperationHistoryEvent.OPERATION_CHANGED -> "CHANGED"; //$NON-NLS-1$
			case OperationHistoryEvent.OPERATION_REMOVED -> "REMOVED"; //$NON-NLS-1$
			case OperationHistoryEvent.REDONE -> "REDONE"; //$NON-NLS-1$
			case OperationHistoryEvent.UNDONE -> "UNDONE"; //$NON-NLS-1$
			default -> Integer.toString(type);
		};
	}

	private static String quoted(String value) {
		if (value == null) {
			return "null"; //$NON-NLS-1$
		}
		return '"' + value.replace("\\", "\\\\") //$NON-NLS-1$ //$NON-NLS-2$
				.replace("\r", "\\r") //$NON-NLS-1$ //$NON-NLS-2$
				.replace("\n", "\\n") //$NON-NLS-1$ //$NON-NLS-2$
				.replace("\t", "\\t") + '"'; //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static <T> T ui(Callable<T> callable) throws Exception {
		AtomicReference<T> result= new AtomicReference<>();
		AtomicReference<Throwable> failure= new AtomicReference<>();
		Display.getDefault().syncExec(() -> {
			try {
				result.set(callable.call());
			} catch (Throwable e) {
				failure.set(e);
			}
		});
		Throwable cause= failure.get();
		if (cause instanceof Exception exception) {
			throw exception;
		}
		if (cause instanceof Error error) {
			throw error;
		}
		return result.get();
	}
}
