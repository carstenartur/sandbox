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

import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.SYSTEM_OUT_CLEANUP;

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
import org.sandbox.jdt.internal.corext.fix.SystemOutFixCore;
import org.sandbox.jdt.triggerpattern.eclipse.HintFinding;
import org.sandbox.jdt.triggerpattern.eclipse.HintMarkerReporter;

/**
 * CleanUp for System.out/System.err detection.
 *
 * <p>This is a hint-only cleanup that detects usage of {@code System.out}
 * and {@code System.err} and reports them as problem markers.</p>
 */
public class SystemOutCleanUpCore extends AbstractCleanUp {

	public SystemOutCleanUpCore(final Map<String, String> options) {
		super(options);
	}

	public SystemOutCleanUpCore() {
	}

	@Override
	public CleanUpRequirements getRequirements() {
		return new CleanUpRequirements(requireAST(), false, false, null);
	}

	public boolean requireAST() {
		return isEnabled(SYSTEM_OUT_CLEANUP);
	}

	@Override
	public ICleanUpFix createFix(final CleanUpContext context) throws CoreException {
		CompilationUnit compilationUnit = context.getAST();
		if (compilationUnit == null || !isEnabled(SYSTEM_OUT_CLEANUP)) {
			return null;
		}

		List<HintFinding> findings = new ArrayList<>();
		SystemOutFixCore.findFindings(compilationUnit, findings);

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
		if (isEnabled(SYSTEM_OUT_CLEANUP)) {
			result.add(MultiFixMessages.SystemOutCleanUp_description);
		}
		return result.toArray(new String[0]);
	}

	@Override
	public String getPreview() {
		if (isEnabled(SYSTEM_OUT_CLEANUP)) {
			return "logger.info(message);\n"; //$NON-NLS-1$
		}
		return "System.out.println(message);\n"; //$NON-NLS-1$
	}
}
