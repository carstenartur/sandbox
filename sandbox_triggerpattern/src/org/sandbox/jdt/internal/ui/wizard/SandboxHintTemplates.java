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
package org.sandbox.jdt.internal.ui.wizard;

/**
 * Pre-built templates for new {@code .sandbox-hint} files.
 *
 * <p>Each template provides a label for the wizard UI and a method to generate
 * rule content that is appended after the metadata block.</p>
 *
 * @since 1.5.0
 */
public enum SandboxHintTemplates {

	/** Metadata-only file without rules. */
	EMPTY("Empty file (metadata only)") { //$NON-NLS-1$
		@Override
		public String getRuleContent() {
			return ""; //$NON-NLS-1$
		}
	},

	/** A single source-pattern &rarr; replacement rule. */
	SIMPLE("Simple transformation rule") { //$NON-NLS-1$
		@Override
		public String getRuleContent() {
			return """

				$x.getBytes("UTF-8")
				=> $x.getBytes(java.nio.charset.StandardCharsets.UTF_8)
				;;
				"""; //$NON-NLS-1$
		}
	},

	/** Detection-only rule without a replacement. */
	HINT_ONLY("Hint-only rule (detection without fix)") { //$NON-NLS-1$
		@Override
		public String getRuleContent() {
			return """

				$x.toString()
				;;
				"""; //$NON-NLS-1$
		}
	},

	/** File skeleton with two example rules. */
	MULTI("Multi-rule file") { //$NON-NLS-1$
		@Override
		public String getRuleContent() {
			return """

				$x.getBytes("UTF-8")
				=> $x.getBytes(java.nio.charset.StandardCharsets.UTF_8)
				;;

				new String($bytes, "UTF-8")
				=> new String($bytes, java.nio.charset.StandardCharsets.UTF_8)
				;;
				"""; //$NON-NLS-1$
		}
	};

	private final String label;

	SandboxHintTemplates(String label) {
		this.label = label;
	}

	/**
	 * Returns the human-readable label shown in the wizard radio group.
	 *
	 * @return the label
	 */
	public String getLabel() {
		return label;
	}

	/**
	 * Returns the rule body text to append after the metadata header. An empty
	 * string means no rules are generated.
	 *
	 * @return the rule DSL text (may be empty, never {@code null})
	 */
	public abstract String getRuleContent();
}
