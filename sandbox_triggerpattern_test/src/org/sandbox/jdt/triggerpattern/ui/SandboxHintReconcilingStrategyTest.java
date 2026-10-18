/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.reconciler.IReconciler;
import org.eclipse.jface.text.reconciler.IReconcilingStrategy;
import org.eclipse.jface.text.reconciler.MonoReconciler;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.text.source.projection.ProjectionViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.triggerpattern.editor.SandboxHintEditor;
import org.sandbox.jdt.triggerpattern.editor.SandboxHintSourceViewerConfiguration;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/** Exercises reconciliation on a worker while the SWT thread stays unavailable. */
public class SandboxHintReconcilingStrategyTest {

	private static final String VALID= "$x.old()\n=> $x.modern()\n;;\n";
	private static final String EMBEDDED_MARKER= "sandbox_common.org.sandbox.jdt.triggerpattern.embeddedJavaProblem";

	@RegisterExtension
	EclipseJava22 context= new EclipseJava22();

	private Display display;
	private Shell shell;
	private SandboxHintEditor editor;
	private SourceViewer viewer;
	private MonoReconciler reconciler;
	private IReconcilingStrategy strategy;

	@BeforeEach
	public void setUp() throws Exception {
		display= Display.getDefault();
		onDisplay(() -> shell= new Shell(display));
	}

	@AfterEach
	public void tearDown() throws Exception {
		onDisplay(() -> {
			if (reconciler != null) {
				reconciler.uninstall();
			}
			if (editor != null) {
				editor.getSite().getPage().closeEditor(editor, false);
			}
			if (!shell.isDisposed()) {
				shell.dispose();
			}
		});
	}

	@Test
	public void aFilelessWizardViewerReconcilesWithoutAccessingSwt() throws Exception {
		createFilelessViewer();
		reconcileOnWorker();
	}

	@Test
	public void aDisposedWizardViewerCanFinishReconciliation() throws Exception {
		createFilelessViewer();
		onDisplay(() -> shell.dispose());
		reconcileOnWorker();
	}

	@Test
	public void realEditorInputChangesRetargetMarkersAndKeepOutlineAndFoldingUpdates() throws Exception {
		IFile first= file("first.sandbox-hint");
		IFile second= file("second.sandbox-hint");
		AtomicReference<IContentOutlinePage> outline= new AtomicReference<>();
		onDisplay(() -> {
			var page= PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
			editor= (SandboxHintEditor) IDE.openEditor(page, first, SandboxHintEditor.EDITOR_ID);
			viewer= (SourceViewer) editor.getAdapter(ITextOperationTarget.class);
			assertNotNull(viewer);
			viewer.unconfigure();
			viewer.configure(new SandboxHintSourceViewerConfiguration(editor) {
				@Override
				public IReconciler getReconciler(ISourceViewer sourceViewer) {
					reconciler= (MonoReconciler) super.getReconciler(sourceViewer);
					// Drive the real strategy deterministically rather than waiting for its timer.
					reconciler.setDelay(Integer.MAX_VALUE);
					return reconciler;
				}
			});
			strategy= reconciler.getReconcilingStrategy(IDocument.DEFAULT_CONTENT_TYPE);
			outline.set(editor.getAdapter(IContentOutlinePage.class));
			outline.get().createControl(shell);
			viewer.getDocument().set("/* Fold this\ncomment. */\n$x.changed()\n=> $x.modern()\n");
		});
		reconcileOnWorker();
		IMarker[] firstMarkers= first.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_ZERO);
		assertEquals(1, firstMarkers.length);
		assertEquals(IMarker.SEVERITY_ERROR, firstMarkers[0].getAttribute(IMarker.SEVERITY, -1));
		onDisplay(() -> {
			while (display.readAndDispatch()) {
				// Apply the strategy's asynchronous UI updates.
			}
			assertTrue(((ProjectionViewer) viewer).getProjectionAnnotationModel().getAnnotationIterator().hasNext());
			Tree tree= (Tree) outline.get().getControl();
			assertTrue(Arrays.stream(tree.getItems()).anyMatch(item -> item.getText().contains("$x.changed()")));
			editor.setInput(new FileEditorInput(second));
			viewer.getDocument().set("$x.second()\n=> $x.modern()\n");
		});
		reconcileOnWorker();
		assertTrue(firstMarkers[0].exists(), "Reusing the editor must not clear the previous input's markers");
		assertEquals(1, second.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_ZERO).length);

		onDisplay(() -> viewer.getDocument().set("<?\npublic boolean broken( { return true; }\n?>\n" + VALID));
		reconcileOnWorker();
		assertTrue(second.findMarkers(EMBEDDED_MARKER, true, IResource.DEPTH_ZERO).length > 0);
		onDisplay(() -> viewer.getDocument().set(VALID));
		reconcileOnWorker();
		assertEquals(0, second.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_ZERO).length);
		assertTrue(firstMarkers[0].exists());
	}

	private void createFilelessViewer() throws Exception {
		onDisplay(() -> {
			viewer= new SourceViewer(shell, null, SWT.NONE);
			reconciler= (MonoReconciler) new SandboxHintSourceViewerConfiguration().getReconciler(viewer);
			IDocument document= new Document(VALID);
			viewer.setDocument(document);
			strategy= reconciler.getReconcilingStrategy(IDocument.DEFAULT_CONTENT_TYPE);
			strategy.setDocument(document);
		});
	}

	private IFile file(String name) throws Exception {
		IFile file= context.getJavaProject().getProject().getFile(name);
		file.create(new ByteArrayInputStream(VALID.getBytes(StandardCharsets.UTF_8)), false, null);
		return file;
	}

	private void reconcileOnWorker() throws Exception {
		onDisplay(() -> {
			CompletableFuture<Void> completion= new CompletableFuture<>();
			Thread worker= new Thread(() -> {
				try {
					strategy.reconcile(new Region(0, 0));
					completion.complete(null);
				} catch (Throwable failure) {
					completion.completeExceptionally(failure);
				}
			}, "hint-reconciliation-regression");
			worker.setDaemon(true);
			worker.start();
			completion.get(10, TimeUnit.SECONDS);
		});
	}

	private void onDisplay(UiOperation operation) throws Exception {
		AtomicReference<Throwable> failure= new AtomicReference<>();
		display.syncExec(() -> {
			try {
				operation.run();
			} catch (Throwable exception) {
				failure.set(exception);
			}
		});
		if (failure.get() instanceof Exception exception) {
			throw exception;
		}
		if (failure.get() instanceof Error error) {
			throw error;
		}
	}

	@FunctionalInterface
	private interface UiOperation {
		void run() throws Exception;
	}
}
