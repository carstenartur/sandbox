/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jdt.ui.text.java.IQuickFixProcessor;
import org.sandbox.jdt.internal.corext.fix.helper.LoopConversionInspection;

/** Offers fixes only for the converter's explicitly enabled style diagnostics. */
public final class LoopConversionQuickFixProcessor implements IQuickFixProcessor {

	@Override
	public boolean hasCorrections(ICompilationUnit unit, int problemId) {
		return problemId == LoopConversionInspection.PROBLEM_ID && LoopConversionInspection.isEnabled();
	}

	@Override
	public IJavaCompletionProposal[] getCorrections(IInvocationContext context, IProblemLocation[] locations) {
		var proposals = new ArrayList<IJavaCompletionProposal>();
		Set<Integer> offsets = new HashSet<>();
		if (context.getASTRoot() != null && locations != null && LoopConversionInspection.isEnabled()) {
			for (IProblemLocation location : locations) {
				if (location.getProblemId() == LoopConversionInspection.PROBLEM_ID
						&& LoopConversionInspection.MARKER_TYPE.equals(location.getMarkerType())
						&& offsets.add(location.getOffset())) {
					var proposal = LoopConversionQuickAssistProcessor.proposal(context, LoopConversionInspection.target(),
							location.getOffset(), location.getLength());
					if (proposal != null) {
						proposals.add(proposal);
					}
				}
			}
		}
		return proposals.toArray(IJavaCompletionProposal[]::new);
	}
}
