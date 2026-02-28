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

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.text.edits.TextEdit;

/**
 * Formats embedded Java code ({@code <? ?>}) blocks in {@code .sandbox-hint}
 * files by delegating to JDT's {@link CodeFormatter}.
 *
 * <p>The formatter wraps the embedded Java source in a synthetic class body
 * (matching the structure used by
 * {@link org.sandbox.jdt.triggerpattern.internal.EmbeddedJavaCompiler}),
 * formats it with JDT's formatter, and then extracts and applies the
 * formatted body back to the document.</p>
 *
 * @since 1.5.0
 */
public final class SandboxHintJavaFormatter {

	private static final Logger LOGGER = Logger.getLogger(SandboxHintJavaFormatter.class.getName());

	private static final String SYNTHETIC_HEADER = "class _Fmt {\n"; //$NON-NLS-1$
	private static final String SYNTHETIC_FOOTER = "\n}"; //$NON-NLS-1$

	private SandboxHintJavaFormatter() {
		// utility class
	}

	/**
	 * Formats all embedded Java ({@code <? ?>}) blocks in the given document.
	 *
	 * @param document the document to format
	 */
	public static void formatEmbeddedJavaBlocks(IDocument document) {
		if (document == null) {
			return;
		}
		try {
			ITypedRegion[] partitions = document.computePartitioning(0, document.getLength());
			// Process partitions in reverse order to preserve offsets
			for (int i = partitions.length - 1; i >= 0; i--) {
				ITypedRegion partition = partitions[i];
				if (SandboxHintPartitionScanner.JAVA_CODE.equals(partition.getType())) {
					formatJavaPartition(document, partition);
				}
			}
		} catch (BadLocationException e) {
			LOGGER.log(Level.WARNING, "Failed to format embedded Java blocks", e); //$NON-NLS-1$
		}
	}

	/**
	 * Formats a single {@code <? ?>} partition.
	 */
	private static void formatJavaPartition(IDocument document, ITypedRegion partition)
			throws BadLocationException {
		int offset = partition.getOffset();
		int length = partition.getLength();
		String text = document.get(offset, length);

		// Strip <? and ?>
		String javaSource = text;
		boolean hasDelimiters = false;
		if (text.startsWith("<?") && text.endsWith("?>")) { //$NON-NLS-1$ //$NON-NLS-2$
			javaSource = text.substring(2, text.length() - 2);
			hasDelimiters = true;
		}

		// Wrap in synthetic class
		String syntheticSource = SYNTHETIC_HEADER + javaSource + SYNTHETIC_FOOTER;

		Map<String, String> options = JavaCore.getOptions();
		CodeFormatter formatter = ToolFactory.createCodeFormatter(options);
		TextEdit edit = formatter.format(
				CodeFormatter.K_COMPILATION_UNIT,
				syntheticSource,
				0, syntheticSource.length(),
				0, null);

		if (edit == null) {
			return; // formatting failed (e.g., syntax errors)
		}

		// Apply to a temporary document to get the formatted result
		org.eclipse.jface.text.Document tempDoc = new org.eclipse.jface.text.Document(syntheticSource);
		edit.apply(tempDoc);
		String formatted = tempDoc.get();

		// Extract the body between the synthetic header/footer
		int bodyStart = formatted.indexOf('{');
		int bodyEnd = formatted.lastIndexOf('}');
		if (bodyStart < 0 || bodyEnd <= bodyStart) {
			return;
		}
		String formattedBody = formatted.substring(bodyStart + 1, bodyEnd);
		// Trim leading/trailing newlines from the body
		if (formattedBody.startsWith("\n")) { //$NON-NLS-1$
			formattedBody = formattedBody.substring(1);
		}
		if (formattedBody.endsWith("\n")) { //$NON-NLS-1$
			formattedBody = formattedBody.substring(0, formattedBody.length() - 1);
		}

		// Rebuild with delimiters
		String replacement;
		if (hasDelimiters) {
			replacement = "<?\n" + formattedBody + "\n?>"; //$NON-NLS-1$ //$NON-NLS-2$
		} else {
			replacement = formattedBody;
		}

		// Only replace if changed
		if (!replacement.equals(text)) {
			document.replace(offset, length, replacement);
		}
	}
}
