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

import org.eclipse.jface.text.templates.Template;

/**
 * Provides code templates for {@code .sandbox-hint} file content assist.
 *
 * <p>Templates offered:</p>
 * <ul>
 *   <li><b>guard</b> &mdash; New guard function in a {@code <? ?>} block</li>
 *   <li><b>fix</b> &mdash; New fix function (annotated with {@code @FixFunction})</li>
 *   <li><b>rule</b> &mdash; New transformation rule</li>
 *   <li><b>meta</b> &mdash; Metadata block with common directives</li>
 * </ul>
 *
 * @since 1.5.0
 */
public final class SandboxHintTemplateStore {

	/**
	 * Context type ID for sandbox hint templates.
	 */
	public static final String CONTEXT_TYPE_ID = "org.sandbox.jdt.triggerpattern.editor.templateContextType"; //$NON-NLS-1$

	private static final Template[] TEMPLATES = {
		new Template(
			"guard", //$NON-NLS-1$
			"New guard function", //$NON-NLS-1$
			CONTEXT_TYPE_ID,
			"<?\npublic boolean ${name}(GuardContext ctx, Object... args) {\n    ${cursor}return true;\n}\n?>", //$NON-NLS-1$
			true),
		new Template(
			"fix", //$NON-NLS-1$
			"New fix function", //$NON-NLS-1$
			CONTEXT_TYPE_ID,
			"<?\n@FixFunction\npublic void ${name}(Match match, ASTRewrite rewrite) {\n    ${cursor}\n}\n?>", //$NON-NLS-1$
			true),
		new Template(
			"rule", //$NON-NLS-1$
			"New transformation rule", //$NON-NLS-1$
			CONTEXT_TYPE_ID,
			"${source_pattern}\n=> ${replacement}\n;;", //$NON-NLS-1$
			true),
		new Template(
			"meta", //$NON-NLS-1$
			"Metadata block", //$NON-NLS-1$
			CONTEXT_TYPE_ID,
			"<!id: ${id}>\n<!description: ${description}>\n<!severity: ${severity}>\n<!minJavaVersion: ${version}>", //$NON-NLS-1$
			true),
	};

	private SandboxHintTemplateStore() {
		// utility class
	}

	/**
	 * Returns all available sandbox hint templates.
	 *
	 * @return array of templates
	 */
	public static Template[] getTemplates() {
		return TEMPLATES.clone();
	}
}
