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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.views.contentoutline.ContentOutlinePage;

/**
 * Outline view for {@code .sandbox-hint} files showing the document structure.
 *
 * <p>The outline tree displays:</p>
 * <ul>
 *   <li>Transformation rules (pattern → replacement)</li>
 *   <li>Embedded Java blocks with their guard/fix methods</li>
 *   <li>Metadata directives</li>
 * </ul>
 *
 * <p>Clicking an outline element selects and reveals the corresponding
 * region in the editor.</p>
 *
 * @since 1.5.0
 */
public class SandboxHintOutlinePage extends ContentOutlinePage {

	private final ITextEditor editor;

	/**
	 * Creates a new outline page for the given editor.
	 *
	 * @param editor the text editor this outline belongs to
	 */
	public SandboxHintOutlinePage(ITextEditor editor) {
		this.editor = editor;
	}

	@Override
	public void createControl(Composite parent) {
		super.createControl(parent);
		TreeViewer viewer = getTreeViewer();
		viewer.setContentProvider(new OutlineContentProvider());
		viewer.setLabelProvider(new OutlineLabelProvider());
		viewer.addSelectionChangedListener(this::handleSelection);
		update();
	}

	/**
	 * Refreshes the outline content from the current document.
	 */
	public void update() {
		TreeViewer viewer = getTreeViewer();
		if (viewer != null) {
			Control control = viewer.getControl();
			if (control != null && !control.isDisposed()) {
				IDocument document = getDocument();
				if (document != null) {
					viewer.setInput(document);
				}
			}
		}
	}

	private IDocument getDocument() {
		IEditorInput input = editor.getEditorInput();
		if (input != null && editor.getDocumentProvider() != null) {
			return editor.getDocumentProvider().getDocument(input);
		}
		return null;
	}

	private void handleSelection(SelectionChangedEvent event) {
		if (event.getSelection() instanceof TreeSelection treeSelection) {
			Object element = treeSelection.getFirstElement();
			if (element instanceof OutlineElement outlineElement) {
				editor.selectAndReveal(outlineElement.offset(), outlineElement.length());
			}
		}
	}

	/**
	 * An element in the outline tree.
	 *
	 * @param label    the display label
	 * @param type     the element type
	 * @param offset   the character offset in the document
	 * @param length   the character length
	 * @param children child elements
	 */
	record OutlineElement(String label, ElementType type, int offset, int length,
			List<OutlineElement> children) {
	}

	/**
	 * The type of outline element.
	 */
	enum ElementType {
		/** A transformation rule */
		RULE,
		/** An embedded Java block */
		JAVA_BLOCK,
		/** A metadata directive */
		METADATA,
		/** A comment */
		COMMENT
	}

	/**
	 * Content provider that parses the document into outline elements.
	 */
	private static class OutlineContentProvider implements ITreeContentProvider {

		@Override
		public Object[] getElements(Object inputElement) {
			if (!(inputElement instanceof IDocument document)) {
				return new Object[0];
			}
			return buildOutline(document).toArray();
		}

		@Override
		public Object[] getChildren(Object parentElement) {
			if (parentElement instanceof OutlineElement element && element.children() != null) {
				return element.children().toArray();
			}
			return new Object[0];
		}

		@Override
		public Object getParent(Object element) {
			return null;
		}

		@Override
		public boolean hasChildren(Object element) {
			return element instanceof OutlineElement oe
					&& oe.children() != null && !oe.children().isEmpty();
		}

		private List<OutlineElement> buildOutline(IDocument document) {
			List<OutlineElement> elements = new ArrayList<>();
			try {
				ITypedRegion[] partitions = document.computePartitioning(0, document.getLength());
				int ruleIndex = 0;
				for (ITypedRegion partition : partitions) {
					String type = partition.getType();
					int offset = partition.getOffset();
					int length = partition.getLength();

					if (SandboxHintPartitionScanner.JAVA_CODE.equals(type)) {
						String text = document.get(offset, length);
						String label = buildJavaBlockLabel(text);
						elements.add(new OutlineElement(
								label, ElementType.JAVA_BLOCK, offset, length, List.of()));
					} else if (SandboxHintPartitionScanner.METADATA.equals(type)) {
						String text = document.get(offset, length).trim();
						elements.add(new OutlineElement(
								text, ElementType.METADATA, offset, length, List.of()));
					} else if (SandboxHintPartitionScanner.COMMENT.equals(type)) {
						// Skip comments in outline
					} else {
						// Default content — look for rule separators
						String text = document.get(offset, length);
						if (text.contains("=>") || text.contains(";;")) { //$NON-NLS-1$ //$NON-NLS-2$
							ruleIndex++;
							String ruleLabel = buildRuleLabel(text, ruleIndex);
							elements.add(new OutlineElement(
									ruleLabel, ElementType.RULE, offset, length, List.of()));
						}
					}
				}
			} catch (BadLocationException e) {
				// ignore — return partial results
			}
			return elements;
		}

		private static String buildJavaBlockLabel(String text) {
			// Extract first method signature for a more descriptive label
			String stripped = text;
			if (stripped.startsWith("<?")) { //$NON-NLS-1$
				stripped = stripped.substring(2);
			}
			if (stripped.endsWith("?>")) { //$NON-NLS-1$
				stripped = stripped.substring(0, stripped.length() - 2);
			}
			stripped = stripped.trim();
			// Find "public boolean methodName" or "public void methodName"
			int parenIdx = stripped.indexOf('(');
			if (parenIdx > 0) {
				String beforeParen = stripped.substring(0, parenIdx).trim();
				int lastSpace = beforeParen.lastIndexOf(' ');
				if (lastSpace > 0) {
					String methodName = beforeParen.substring(lastSpace + 1);
					return "<? " + methodName + "() ?>"; //$NON-NLS-1$ //$NON-NLS-2$
				}
			}
			if (stripped.length() > 40) {
				stripped = stripped.substring(0, 40) + "..."; //$NON-NLS-1$
			}
			return "<? " + stripped + " ?>"; //$NON-NLS-1$ //$NON-NLS-2$
		}

		private static String buildRuleLabel(String text, int index) {
			String trimmed = text.trim();
			// Extract the pattern (first line typically)
			int newlineIdx = trimmed.indexOf('\n');
			String firstLine = (newlineIdx > 0) ? trimmed.substring(0, newlineIdx).trim() : trimmed;
			if (firstLine.length() > 60) {
				firstLine = firstLine.substring(0, 60) + "..."; //$NON-NLS-1$
			}
			return "Rule " + index + ": " + firstLine; //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	/**
	 * Label provider for outline elements.
	 */
	private static class OutlineLabelProvider extends LabelProvider {
		@Override
		public String getText(Object element) {
			if (element instanceof OutlineElement outlineElement) {
				return outlineElement.label();
			}
			return super.getText(element);
		}

		@Override
		public Image getImage(Object element) {
			// No custom images for now; could add JDT/PDE shared images later
			return null;
		}
	}
}
