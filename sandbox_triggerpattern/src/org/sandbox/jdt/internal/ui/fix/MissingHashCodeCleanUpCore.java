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
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;

import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.MISSING_HASHCODE_CLEANUP;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.sandbox.jdt.internal.corext.fix.MissingHashCodeFixCore;
import org.sandbox.jdt.triggerpattern.eclipse.HintFinding;
import org.sandbox.jdt.triggerpattern.eclipse.HintMarkerReporter;

/**
 * CleanUp for missing hashCode() detection.
 *
 * <p>This is a hint-only cleanup that detects classes overriding
 * {@code equals()} without {@code hashCode()} and reports them as
 * problem markers.</p>
 */
public class MissingHashCodeCleanUpCore extends AbstractCleanUp {

	public MissingHashCodeCleanUpCore(final Map<String, String> options) {
		super(options);
	}

	public MissingHashCodeCleanUpCore() {
	}

	@Override
	public CleanUpRequirements getRequirements() {
		return new CleanUpRequirements(requireAST(), false, false, null);
	}

	public boolean requireAST() {
		return isEnabled(MISSING_HASHCODE_CLEANUP);
	}

	@Override
	public ICleanUpFix createFix(final CleanUpContext context) throws CoreException {
		CompilationUnit compilationUnit = context.getAST();
		if (compilationUnit == null || !isEnabled(MISSING_HASHCODE_CLEANUP)) {
			return null;
		}

		List<HintFinding> findings = new ArrayList<>();
		MissingHashCodeFixCore.findFindings(compilationUnit, findings);

		if (!findings.isEmpty() && compilationUnit.getJavaElement() != null) {
			IResource resource = compilationUnit.getJavaElement().getResource();
			if (resource != null) {
				HintMarkerReporter.clearMarkers(resource);
				HintMarkerReporter.reportFindings(resource, findings);
			}
		}
		return null; // hint-only: no code change
	}

	@Override
	public String[] getStepDescriptions() {
		List<String> result = new ArrayList<>();
		if (isEnabled(MISSING_HASHCODE_CLEANUP)) {
			result.add(MultiFixMessages.MissingHashCodeCleanUp_description);
		}
		return result.toArray(new String[0]);
	}

	@Override
	public String getPreview() {
		if (isEnabled(MISSING_HASHCODE_CLEANUP)) {
			return "// equals() and hashCode() both overridden\n"; //$NON-NLS-1$
		}
		return "// equals() overridden, hashCode() missing\n"; //$NON-NLS-1$
	}
}
