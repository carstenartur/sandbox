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

import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.OVERRIDABLE_IN_CONSTRUCTOR_CLEANUP;

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
import org.sandbox.jdt.internal.corext.fix.OverridableCallInConstructorFixCore;
import org.sandbox.jdt.triggerpattern.eclipse.HintFinding;
import org.sandbox.jdt.triggerpattern.eclipse.HintMarkerReporter;

/**
 * CleanUp for overridable method call in constructor detection.
 *
 * <p>This is a hint-only cleanup that detects constructors calling
 * overridable (non-private, non-final) methods and reports them as
 * problem markers.</p>
 */
public class OverridableCallInConstructorCleanUpCore extends AbstractCleanUp {

	public OverridableCallInConstructorCleanUpCore(final Map<String, String> options) {
		super(options);
	}

	public OverridableCallInConstructorCleanUpCore() {
	}

	@Override
	public CleanUpRequirements getRequirements() {
		return new CleanUpRequirements(requireAST(), false, false, null);
	}

	public boolean requireAST() {
		return isEnabled(OVERRIDABLE_IN_CONSTRUCTOR_CLEANUP);
	}

	@Override
	public ICleanUpFix createFix(final CleanUpContext context) throws CoreException {
		CompilationUnit compilationUnit = context.getAST();
		if (compilationUnit == null || !isEnabled(OVERRIDABLE_IN_CONSTRUCTOR_CLEANUP)) {
			return null;
		}

		List<HintFinding> findings = new ArrayList<>();
		OverridableCallInConstructorFixCore.findFindings(compilationUnit, findings);

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
		if (isEnabled(OVERRIDABLE_IN_CONSTRUCTOR_CLEANUP)) {
			result.add(MultiFixMessages.OverridableCallInConstructorCleanUp_description);
		}
		return result.toArray(new String[0]);
	}

	@Override
	public String getPreview() {
		if (isEnabled(OVERRIDABLE_IN_CONSTRUCTOR_CLEANUP)) {
			return "// Constructor calls only private/final methods\n"; //$NON-NLS-1$
		}
		return "// Constructor calls overridable method\n"; //$NON-NLS-1$
	}
}
