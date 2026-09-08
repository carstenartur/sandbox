/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.internal.ui.text.correction.proposals.FixCorrectionProposal;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jdt.ui.text.java.IQuickAssistProcessor;
import org.sandbox.jdt.internal.corext.fix.helper.LoopConversionService;
import org.sandbox.jdt.internal.corext.fix.helper.LoopTargetFormat;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.preferences.cleanup.CleanUpMessages;

/** Native JDT proposals, including its change preview, undo and cleanup support. */
public final class LoopConversionQuickAssistProcessor implements IQuickAssistProcessor {

	@Override
	public boolean hasAssists(IInvocationContext context) {
		if (context.getASTRoot() == null) {
			return false;
		}
		for (LoopTargetFormat target : LoopTargetFormat.values()) {
			if (!analyze(context, target, context.getSelectionOffset(), context.getSelectionLength()).operations().isEmpty()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public IJavaCompletionProposal[] getAssists(IInvocationContext context, IProblemLocation[] locations) throws CoreException {
		if (context.getASTRoot() == null) {
			return new IJavaCompletionProposal[0];
		}
		var proposals = new ArrayList<IJavaCompletionProposal>();
		for (LoopTargetFormat target : LoopTargetFormat.values()) {
			var proposal = proposal(context, target, context.getSelectionOffset(), context.getSelectionLength());
			if (proposal != null) {
				proposals.add(proposal);
			}
		}
		return proposals.toArray(IJavaCompletionProposal[]::new);
	}

	static LoopConversionService.Analysis analyze(IInvocationContext context, LoopTargetFormat target, int offset, int length) {
		return LoopConversionService.analyzeSelection(context.getASTRoot(), LoopConversionService.handlers(target), offset, length);
	}

	static FixCorrectionProposal proposal(IInvocationContext context, LoopTargetFormat target, int offset, int length) {
		var fix = analyze(context, target, offset, length).createFix(label(target));
		return fix == null ? null : new FixCorrectionProposal(fix,
				new UseFunctionalCallCleanUp(options(target)), 50, null, context);
	}

	public static String label(LoopTargetFormat target) {
		return switch (target) {
		case STREAM -> CleanUpMessages.LoopConversion_Assist_Stream;
		case FOR_LOOP -> CleanUpMessages.LoopConversion_Assist_EnhancedFor;
		case WHILE_LOOP -> CleanUpMessages.LoopConversion_Assist_IteratorWhile;
		};
	}

	public static Map<String, String> options(LoopTargetFormat target) {
		Map<String, String> options = new HashMap<>();
		options.put(MYCleanUpConstants.LOOP_CONVERSION_ENABLED, CleanUpOptions.TRUE);
		options.put(MYCleanUpConstants.LOOP_CONVERSION_TARGET_FORMAT, target.getId());
		LoopConversionService.handlers(target).forEach(handler ->
				options.put(UseFunctionalCallCleanUpCore.sourceOption(handler), CleanUpOptions.TRUE));
		return options;
	}
}
