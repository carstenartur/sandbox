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

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.rules.EndOfLineRule;
import org.eclipse.jface.text.rules.IPredicateRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.MultiLineRule;
import org.eclipse.jface.text.rules.RuleBasedPartitionScanner;
import org.eclipse.jface.text.rules.Token;

/**
 * Partition scanner for {@code .sandbox-hint} files.
 *
 * <p>Defines the document partitions:</p>
 * <ul>
 *   <li>{@link #COMMENT} – single-line ({@code //}) and multi-line ({@code /* ... * /}) comments</li>
 *   <li>{@link #METADATA} – metadata directives ({@code <!...>})</li>
 *   <li>{@link IDocument#DEFAULT_CONTENT_TYPE} – everything else (code)</li>
 * </ul>
 *
 * @since 1.3.6
 */
public class SandboxHintPartitionScanner extends RuleBasedPartitionScanner {

	/**
	 * Content type for comment partitions.
	 */
	public static final String COMMENT = "__sandbox_hint_comment"; //$NON-NLS-1$

	/**
	 * Content type for metadata directive partitions.
	 */
	public static final String METADATA = "__sandbox_hint_metadata"; //$NON-NLS-1$

	/**
	 * All partition types produced by this scanner.
	 */
	public static final String[] PARTITION_TYPES = {
		COMMENT,
		METADATA
	};

	public SandboxHintPartitionScanner() {
		IToken commentToken = new Token(COMMENT);
		IToken metadataToken = new Token(METADATA);

		IPredicateRule[] rules = {
			new EndOfLineRule("//", commentToken), //$NON-NLS-1$
			new MultiLineRule("/*", "*/", commentToken), //$NON-NLS-1$ //$NON-NLS-2$
			new MultiLineRule("<!", ">", metadataToken), //$NON-NLS-1$ //$NON-NLS-2$
		};

		setPredicateRules(rules);
	}
}
