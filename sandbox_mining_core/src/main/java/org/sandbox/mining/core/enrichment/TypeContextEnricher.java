/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.mining.core.enrichment;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enriches LLM prompts with type hierarchy context extracted
 * from commit diffs. This helps the LLM understand Eclipse-specific
 * type relationships when evaluating commits.
 *
 * <p>Scans diffs for common Eclipse type patterns and adds
 * context about their position in the Eclipse type hierarchy.</p>
 */
public class TypeContextEnricher {

	private static final Map<String, String> ECLIPSE_TYPE_HIERARCHY = new LinkedHashMap<>();

	static {
		ECLIPSE_TYPE_HIERARCHY.put("IResource", //$NON-NLS-1$
				"IResource -> IAdaptable; subtypes: IFile, IFolder, IProject, IWorkspaceRoot"); //$NON-NLS-1$
		ECLIPSE_TYPE_HIERARCHY.put("ICompilationUnit", //$NON-NLS-1$
				"ICompilationUnit -> ITypeRoot -> IJavaElement -> IAdaptable"); //$NON-NLS-1$
		ECLIPSE_TYPE_HIERARCHY.put("ASTNode", //$NON-NLS-1$
				"ASTNode; subtypes: Expression, Statement, Type, BodyDeclaration, etc."); //$NON-NLS-1$
		ECLIPSE_TYPE_HIERARCHY.put("IStatus", //$NON-NLS-1$
				"IStatus; impl: Status, MultiStatus. Factory: Status.error(), Status.warning()"); //$NON-NLS-1$
		ECLIPSE_TYPE_HIERARCHY.put("IProgressMonitor", //$NON-NLS-1$
				"IProgressMonitor; impl: NullProgressMonitor, SubMonitor"); //$NON-NLS-1$
		ECLIPSE_TYPE_HIERARCHY.put("Job", //$NON-NLS-1$
				"Job -> InternalJob -> PlatformObject -> IAdaptable"); //$NON-NLS-1$
		ECLIPSE_TYPE_HIERARCHY.put("IStructuredSelection", //$NON-NLS-1$
				"IStructuredSelection -> ISelection; impl: StructuredSelection"); //$NON-NLS-1$
		ECLIPSE_TYPE_HIERARCHY.put("IAction", //$NON-NLS-1$
				"IAction; impl: Action -> AbstractAction; subtypes: many"); //$NON-NLS-1$
		ECLIPSE_TYPE_HIERARCHY.put("IPreferenceStore", //$NON-NLS-1$
				"IPreferenceStore; impl: PreferenceStore, ScopedPreferenceStore"); //$NON-NLS-1$
		ECLIPSE_TYPE_HIERARCHY.put("Display", //$NON-NLS-1$
				"Display -> Device; Display.getDefault(), Display.syncExec(), Display.asyncExec()"); //$NON-NLS-1$
		ECLIPSE_TYPE_HIERARCHY.put("Composite", //$NON-NLS-1$
				"Composite -> Scrollable -> Control -> Widget -> IAdaptable"); //$NON-NLS-1$
		ECLIPSE_TYPE_HIERARCHY.put("Shell", //$NON-NLS-1$
				"Shell -> Decorations -> Canvas -> Composite"); //$NON-NLS-1$
	}

	/**
	 * Pattern to match Java type names in diffs.
	 */
	private static final Pattern TYPE_PATTERN = Pattern.compile("\\b([A-Z][A-Za-z0-9]+)\\b"); //$NON-NLS-1$

	/**
	 * Enriches a diff by extracting referenced Eclipse types and
	 * adding type hierarchy context.
	 *
	 * @param diff the commit diff
	 * @return type context section to include in prompt, empty if no types found
	 */
	public String enrichFromDiff(String diff) {
		if (diff == null || diff.isBlank()) {
			return ""; //$NON-NLS-1$
		}

		Map<String, String> found = new LinkedHashMap<>();
		Matcher matcher = TYPE_PATTERN.matcher(diff);
		while (matcher.find()) {
			String typeName = matcher.group(1);
			String hierarchy = ECLIPSE_TYPE_HIERARCHY.get(typeName);
			if (hierarchy != null) {
				found.putIfAbsent(typeName, hierarchy);
			}
		}

		if (found.isEmpty()) {
			return ""; //$NON-NLS-1$
		}

		StringBuilder sb = new StringBuilder();
		sb.append("## Type Context\n\n"); //$NON-NLS-1$
		sb.append("The following Eclipse types appear in this diff:\n\n"); //$NON-NLS-1$
		for (Map.Entry<String, String> entry : found.entrySet()) {
			sb.append("- **").append(entry.getKey()).append("**: ") //$NON-NLS-1$ //$NON-NLS-2$
					.append(entry.getValue()).append("\n"); //$NON-NLS-1$
		}
		sb.append("\n"); //$NON-NLS-1$
		return sb.toString();
	}

	/**
	 * Returns the number of known Eclipse types in the hierarchy database.
	 *
	 * @return number of known types
	 */
	public int getKnownTypeCount() {
		return ECLIPSE_TYPE_HIERARCHY.size();
	}
}
