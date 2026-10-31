/*******************************************************************************
 * Copyright (c) 2021 Carsten Hammer.
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

import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.LOOP_CONVERSION_ENABLED;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.LOOP_CONVERSION_FROM_CLASSIC_FOR;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.LOOP_CONVERSION_FROM_ENHANCED_FOR;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.LOOP_CONVERSION_FROM_ITERATOR_WHILE;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.LOOP_CONVERSION_FROM_STREAM;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.LOOP_CONVERSION_TARGET_FORMAT;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP_V2;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.USEFUNCTIONALLOOP_FORMAT_FOR;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.USEFUNCTIONALLOOP_FORMAT_WHILE;
import static org.sandbox.jdt.internal.ui.fix.MultiFixMessages.FunctionalCallCleanUpFix_refactor;
import static org.sandbox.jdt.internal.ui.preferences.cleanup.CleanUpMessages.LoopConversion_Description;

import java.util.EnumSet;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.LoopConversionService;
import org.sandbox.jdt.internal.corext.fix.helper.LoopTargetFormat;

public class UseFunctionalCallCleanUpCore extends AbstractCleanUp {

	// The superclass constructor calls setOptions; do not overwrite that reference
	// with a field initializer. JDT edits these options after creating the preview.
	private CleanUpOptions currentOptions;

	public UseFunctionalCallCleanUpCore(Map<String, String> options) {
		super(options);
	}

	public UseFunctionalCallCleanUpCore() {
	}

	@Override
	public void setOptions(CleanUpOptions options) {
		super.setOptions(options);
		currentOptions = options;
	}

	@Override
	public CleanUpRequirements getRequirements() {
		return new CleanUpRequirements(requireAST(), false, false, null);
	}

	public boolean requireAST() {
		return isEnabled(USEFUNCTIONALLOOP_CLEANUP) || isEnabled(USEFUNCTIONALLOOP_CLEANUP_V2) || isEnabled(LOOP_CONVERSION_ENABLED);
	}

	@Override
	public ICleanUpFix createFix(CleanUpContext context) throws CoreException {
		if (context.getAST() == null || !requireAST()) {
			return null;
		}
		// Both explicit profile flags delegate this transaction to the optional host.
		// A stale flag must never suppress the standalone converter when math is absent.
		var math = Platform.getBundle("sandbox_math_cleanup");
		if (isEnabled("cleanup.loop_conversion.with_mathematics") && isEnabled("cleanup.mathematics")
				&& !(context instanceof org.eclipse.jdt.internal.ui.fix.IMultiLineCleanUp.MultiLineCleanUpContext)
				&& math != null && (math.getState() & (org.osgi.framework.Bundle.RESOLVED
						| org.osgi.framework.Bundle.STARTING | org.osgi.framework.Bundle.ACTIVE)) != 0) return null;
		return LoopConversionService.analyze(context.getAST(), computeFixSet()).createFix(FunctionalCallCleanUpFix_refactor);
	}

	@Override
	public String[] getStepDescriptions() {
		return computeFixSet().isEmpty() ? new String[0] : new String[] { LoopConversion_Description };
	}

	@Override
	public String getPreview() {
		Map<String, String> options = optionsSnapshot();
		EnumSet<UseFunctionalCallFixCore> selected = LoopConversionService.configuredHandlers(options);
		EnumSet<UseFunctionalCallFixCore> shown = selected.isEmpty()
				? LoopConversionService.handlers(LoopTargetFormat.fromId(options.get(LOOP_CONVERSION_TARGET_FORMAT))) : selected;
		return shown.stream().map(handler -> handler.getPreview(selected.contains(handler)))
				.collect(Collectors.joining(System.lineSeparator()));
	}

	private Map<String, String> optionsSnapshot() {
		return currentOptions == null ? Map.of() : currentOptions.getKeys().stream()
				.collect(Collectors.toMap(key -> key, currentOptions::getValue));
	}

	private EnumSet<UseFunctionalCallFixCore> computeFixSet() {
		return LoopConversionService.configuredHandlers(optionsSnapshot());
	}

	public static String sourceOption(UseFunctionalCallFixCore handler) {
		return LoopConversionService.sourceOption(handler);
	}
}
