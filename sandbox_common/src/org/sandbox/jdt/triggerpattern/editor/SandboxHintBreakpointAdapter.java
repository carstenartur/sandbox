/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.editor;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.ILineBreakpoint;
import org.eclipse.debug.ui.actions.IToggleBreakpointsTarget;
import org.eclipse.jdt.debug.core.JDIDebugModel;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * Breakpoint adapter for {@code .sandbox-hint} files.
 *
 * <p>Supports toggling line breakpoints inside embedded Java ({@code <? ?>})
 * blocks. Double-clicking the editor ruler inside an embedded Java region
 * creates a Java line breakpoint on the corresponding line in the synthetic
 * generated class.</p>
 *
 * <p>The breakpoint is stored with the hint file as its resource, and the
 * synthetic class type name is associated for JDT debug matching.</p>
 *
 * @since 1.5.0
 */
public class SandboxHintBreakpointAdapter implements IToggleBreakpointsTarget {

	private static final Logger LOGGER = Logger.getLogger(SandboxHintBreakpointAdapter.class.getName());

	@Override
	public void toggleLineBreakpoints(IWorkbenchPart part, ISelection selection) throws CoreException {
		if (!(part instanceof ITextEditor textEditor)) {
			return;
		}
		if (!(selection instanceof ITextSelection textSelection)) {
			return;
		}

		IEditorInput input = textEditor.getEditorInput();
		if (!(input instanceof IFileEditorInput fileInput)) {
			return;
		}

		IFile file = fileInput.getFile();
		int lineNumber = textSelection.getStartLine() + 1; // 1-based

		// Check if the selection is inside a <? ?> Java code partition
		IDocument document = textEditor.getDocumentProvider().getDocument(input);
		if (!isInsideJavaPartition(document, textSelection.getOffset())) {
			LOGGER.log(Level.FINE, "Breakpoint toggle at line {0}: not inside Java partition", lineNumber); //$NON-NLS-1$
			return;
		}

		// Check for existing breakpoint at this line
		IBreakpoint existingBreakpoint = findBreakpoint(file, lineNumber);
		if (existingBreakpoint != null) {
			DebugPlugin.getDefault().getBreakpointManager().removeBreakpoint(existingBreakpoint, true);
			LOGGER.log(Level.FINE, "Removed breakpoint at line {0}", lineNumber); //$NON-NLS-1$
			return;
		}

		// Determine the synthetic class type name from the source locator mappings
		String typeName = findSyntheticTypeName(file, lineNumber);
		if (typeName == null) {
			// Use a placeholder type name based on the file
			typeName = EmbeddedJavaSourceLocator.SYNTHETIC_PREFIX + sanitize(file.getName());
		}

		// Create a Java line breakpoint
		JDIDebugModel.createLineBreakpoint(
				file,           // resource
				typeName,       // type name
				lineNumber,     // line number
				-1,             // char start
				-1,             // char end
				0,              // hit count
				true,           // register
				null            // attributes
		);

		LOGGER.log(Level.FINE, "Created breakpoint at line {0} for type {1}", //$NON-NLS-1$
				new Object[] { lineNumber, typeName });
	}

	@Override
	public boolean canToggleLineBreakpoints(IWorkbenchPart part, ISelection selection) {
		if (!(part instanceof ITextEditor textEditor)) {
			return false;
		}
		if (!(selection instanceof ITextSelection textSelection)) {
			return false;
		}
		IDocument document = textEditor.getDocumentProvider()
				.getDocument(textEditor.getEditorInput());
		return isInsideJavaPartition(document, textSelection.getOffset());
	}

	@Override
	public void toggleMethodBreakpoints(IWorkbenchPart part, ISelection selection) throws CoreException {
		// Not supported for hint files
	}

	@Override
	public boolean canToggleMethodBreakpoints(IWorkbenchPart part, ISelection selection) {
		return false;
	}

	@Override
	public void toggleWatchpoints(IWorkbenchPart part, ISelection selection) throws CoreException {
		// Not supported for hint files
	}

	@Override
	public boolean canToggleWatchpoints(IWorkbenchPart part, ISelection selection) {
		return false;
	}

	/**
	 * Checks if the given offset is inside a {@link SandboxHintPartitionScanner#JAVA_CODE} partition.
	 */
	private boolean isInsideJavaPartition(IDocument document, int offset) {
		if (document == null) {
			return false;
		}
		try {
			ITypedRegion partition = document.getPartition(offset);
			return SandboxHintPartitionScanner.JAVA_CODE.equals(partition.getType());
		} catch (BadLocationException e) {
			return false;
		}
	}

	/**
	 * Finds an existing breakpoint at the given line in the file.
	 */
	private IBreakpoint findBreakpoint(IResource resource, int lineNumber) {
		IBreakpoint[] breakpoints = DebugPlugin.getDefault().getBreakpointManager()
				.getBreakpoints(JDIDebugModel.getPluginIdentifier());
		for (IBreakpoint bp : breakpoints) {
			if (resource.equals(bp.getMarker().getResource()) && bp instanceof ILineBreakpoint lineBp) {
				try {
					if (lineBp.getLineNumber() == lineNumber) {
						return bp;
					}
				} catch (CoreException e) {
					// ignore
				}
			}
		}
		return null;
	}

	/**
	 * Attempts to find the synthetic type name for a breakpoint at the given line.
	 * Looks up registered source mappings from the {@link EmbeddedJavaSourceLocator}.
	 */
	private static String findSyntheticTypeName(IFile file, int lineNumber) {
		// Currently returns null to use the placeholder name.
		// When EmbeddedJavaSourceLocator has active mappings, they will be
		// queried here to find the exact synthetic class name and mapped line.
		return null;
	}

	/**
	 * Sanitizes a filename for use as a Java identifier suffix.
	 */
	private String sanitize(String name) {
		if (name == null) {
			return "unknown"; //$NON-NLS-1$
		}
		// Remove file extension
		int dotIdx = name.lastIndexOf('.');
		if (dotIdx > 0) {
			name = name.substring(0, dotIdx);
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (Character.isJavaIdentifierPart(c)) {
				sb.append(c);
			} else {
				sb.append('_');
			}
		}
		return sb.toString();
	}
}
