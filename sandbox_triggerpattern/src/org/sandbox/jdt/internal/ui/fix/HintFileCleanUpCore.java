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
package org.sandbox.jdt.internal.ui.fix;

import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.HINTFILE_CLEANUP;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.HINTFILE_BUNDLE_COLLECTIONS;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.HINTFILE_BUNDLE_PERFORMANCE;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.HINTFILE_BUNDLE_MODERNIZE_JAVA9;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.HINTFILE_BUNDLE_MODERNIZE_JAVA11;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.HINTFILE_BUNDLE_ENCODING;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.HINTFILE_BUNDLE_JUNIT5;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.HINTFILE_BUNDLE_STREAM_PERFORMANCE;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.HINTFILE_BUNDLE_IO_PERFORMANCE;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.HINTFILE_BUNDLE_COLLECTION_PERFORMANCE;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.HINTFILE_BUNDLE_NUMBER_COMPARE;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.HINTFILE_BUNDLE_STRING_EQUALS;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.HINTFILE_BUNDLE_STRING_ISBLANK;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.HINTFILE_BUNDLE_ARRAYS;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.HINTFILE_BUNDLE_COLLECTION_TOARRAY;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.HINTFILE_BUNDLE_PROBABLE_BUGS;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.HINTFILE_BUNDLE_MISC;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.HINTFILE_BUNDLE_DEPRECATIONS;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.HINTFILE_BUNDLE_CLASSFILE_API;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.HINTFILE_BUNDLE_SERIALIZATION;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.sandbox.jdt.triggerpattern.cleanup.HintFileFixCore;
import org.sandbox.jdt.triggerpattern.eclipse.CleanUpResult;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;

/**
 * CleanUp that applies transformation rules from {@code .sandbox-hint} files.
 *
 * <p>This is the bridge between the {@code .sandbox-hint} DSL file format and the
 * Eclipse CleanUp framework. It reads all registered hint files from
 * {@link org.sandbox.jdt.triggerpattern.internal.HintFileRegistry HintFileRegistry}
 * and applies their transformation rules as cleanup operations.</p>
 *
 * <p>This enables users to define cleanup rules declaratively in
 * {@code .sandbox-hint} files without writing Java code.</p>
 *
 * @since 1.3.5
 */
public class HintFileCleanUpCore extends AbstractSandboxCleanUpCore {

	public HintFileCleanUpCore(final Map<String, String> options) {
		super(options);
	}

	public HintFileCleanUpCore() {
	}

	@Override
	protected String getCleanUpKey() {
		return HINTFILE_CLEANUP;
	}

	@Override
	protected String getFixLabel() {
		return MultiFixMessages.HintFileCleanUpFix_refactor;
	}

	@Override
	protected String getDescription() {
		return MultiFixMessages.HintFileCleanUp_description;
	}

	@Override
	protected void detect(CompilationUnit cu, CleanUpResult result) {
		Set<String> enabledBundles = getEnabledBundles();
		HintFileFixCore.findOperations(cu, result.getOperations(),
				enabledBundles, result.getFindings());
	}

	/**
	 * Returns the set of enabled bundled hint file IDs based on the current options.
	 * 
	 * <p>Returns a set of bundle IDs corresponding to the enabled per-bundle
	 * options. If all bundles are disabled, an empty set is returned, causing
	 * only project-level hint files to be processed.</p>
	 * 
	 * @return set of enabled bundle IDs (never {@code null})
	 */
	private Set<String> getEnabledBundles() {
		Set<String> enabled = new LinkedHashSet<>();
		if (isEnabled(HINTFILE_BUNDLE_COLLECTIONS)) {
			enabled.add(MYCleanUpConstants.HINTFILE_BUNDLE_ID_COLLECTIONS);
		}
		if (isEnabled(HINTFILE_BUNDLE_PERFORMANCE)) {
			enabled.add(MYCleanUpConstants.HINTFILE_BUNDLE_ID_PERFORMANCE);
		}
		if (isEnabled(HINTFILE_BUNDLE_MODERNIZE_JAVA9)) {
			enabled.add(MYCleanUpConstants.HINTFILE_BUNDLE_ID_MODERNIZE_JAVA9);
		}
		if (isEnabled(HINTFILE_BUNDLE_MODERNIZE_JAVA11)) {
			enabled.add(MYCleanUpConstants.HINTFILE_BUNDLE_ID_MODERNIZE_JAVA11);
		}
		if (isEnabled(HINTFILE_BUNDLE_ENCODING)) {
			enabled.add(MYCleanUpConstants.HINTFILE_BUNDLE_ID_ENCODING);
		}
		if (isEnabled(HINTFILE_BUNDLE_JUNIT5)) {
			enabled.add(MYCleanUpConstants.HINTFILE_BUNDLE_ID_JUNIT5);
			// assume5 and annotations5 are part of the JUnit 4→5 migration bundle
			enabled.add("assume5"); //$NON-NLS-1$
			enabled.add("annotations5"); //$NON-NLS-1$
		}
		if (isEnabled(HINTFILE_BUNDLE_STREAM_PERFORMANCE)) {
			enabled.add(MYCleanUpConstants.HINTFILE_BUNDLE_ID_STREAM_PERFORMANCE);
		}
		if (isEnabled(HINTFILE_BUNDLE_IO_PERFORMANCE)) {
			enabled.add(MYCleanUpConstants.HINTFILE_BUNDLE_ID_IO_PERFORMANCE);
		}
		if (isEnabled(HINTFILE_BUNDLE_COLLECTION_PERFORMANCE)) {
			enabled.add(MYCleanUpConstants.HINTFILE_BUNDLE_ID_COLLECTION_PERFORMANCE);
		}
		if (isEnabled(HINTFILE_BUNDLE_NUMBER_COMPARE)) {
			enabled.add(MYCleanUpConstants.HINTFILE_BUNDLE_ID_NUMBER_COMPARE);
		}
		if (isEnabled(HINTFILE_BUNDLE_STRING_EQUALS)) {
			enabled.add(MYCleanUpConstants.HINTFILE_BUNDLE_ID_STRING_EQUALS);
		}
		if (isEnabled(HINTFILE_BUNDLE_STRING_ISBLANK)) {
			enabled.add(MYCleanUpConstants.HINTFILE_BUNDLE_ID_STRING_ISBLANK);
		}
		if (isEnabled(HINTFILE_BUNDLE_ARRAYS)) {
			enabled.add(MYCleanUpConstants.HINTFILE_BUNDLE_ID_ARRAYS);
		}
		if (isEnabled(HINTFILE_BUNDLE_COLLECTION_TOARRAY)) {
			enabled.add(MYCleanUpConstants.HINTFILE_BUNDLE_ID_COLLECTION_TOARRAY);
		}
		if (isEnabled(HINTFILE_BUNDLE_PROBABLE_BUGS)) {
			enabled.add(MYCleanUpConstants.HINTFILE_BUNDLE_ID_PROBABLE_BUGS);
		}
		if (isEnabled(HINTFILE_BUNDLE_MISC)) {
			enabled.add(MYCleanUpConstants.HINTFILE_BUNDLE_ID_MISC);
		}
		if (isEnabled(HINTFILE_BUNDLE_DEPRECATIONS)) {
			enabled.add(MYCleanUpConstants.HINTFILE_BUNDLE_ID_DEPRECATIONS);
		}
		if (isEnabled(HINTFILE_BUNDLE_CLASSFILE_API)) {
			enabled.add(MYCleanUpConstants.HINTFILE_BUNDLE_ID_CLASSFILE_API);
		}
		if (isEnabled(HINTFILE_BUNDLE_SERIALIZATION)) {
			enabled.add(MYCleanUpConstants.HINTFILE_BUNDLE_ID_SERIALIZATION);
		}
		return enabled;
	}

	@Override
	public String getPreview() {
		if (isEnabled(HINTFILE_CLEANUP)) {
			return """
				// After applying .sandbox-hint rules:
				String s = String.valueOf(value);
				boolean empty = list.isEmpty();
				"""; //$NON-NLS-1$
		}
		return """
			// Before applying .sandbox-hint rules:
			String s = "" + value;
			boolean empty = list.size() == 0;
			"""; //$NON-NLS-1$
	}
}
