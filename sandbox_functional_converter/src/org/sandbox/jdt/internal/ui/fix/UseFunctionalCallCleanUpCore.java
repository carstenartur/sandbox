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
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.LoopConversionService;
import org.sandbox.jdt.internal.corext.fix.helper.LoopTargetFormat;

public class UseFunctionalCallCleanUpCore extends AbstractCleanUp {

	private Map<String, String> optionsMap = Map.of();

	public UseFunctionalCallCleanUpCore(Map<String, String> options) {
		super(options);
		optionsMap = options;
	}

	public UseFunctionalCallCleanUpCore() {
	}

	@Override
	public void setOptions(CleanUpOptions options) {
		super.setOptions(options);
		optionsMap = options.getKeys().stream().collect(Collectors.toMap(key -> key, options::getValue));
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
		return LoopConversionService.analyze(context.getAST(), computeFixSet()).createFix(FunctionalCallCleanUpFix_refactor);
	}

	@Override
	public String[] getStepDescriptions() {
		return computeFixSet().isEmpty() ? new String[0] : new String[] { LoopConversion_Description };
	}

	@Override
	public String getPreview() {
		EnumSet<UseFunctionalCallFixCore> selected = computeFixSet();
		EnumSet<UseFunctionalCallFixCore> shown = selected.isEmpty()
				? LoopConversionService.handlers(targetFormat()) : selected;
		return shown.stream().map(handler -> handler.getPreview(selected.contains(handler)))
				.collect(Collectors.joining(System.lineSeparator()));
	}

	private LoopTargetFormat targetFormat() {
		return LoopTargetFormat.fromId(optionsMap.get(LOOP_CONVERSION_TARGET_FORMAT));
	}

	private EnumSet<UseFunctionalCallFixCore> computeFixSet() {
		// An explicit target takes precedence over legacy stream-only profile flags.
		if (isEnabled(LOOP_CONVERSION_ENABLED)) {
			EnumSet<UseFunctionalCallFixCore> result = LoopConversionService.handlers(targetFormat());
			result.removeIf(handler -> !isEnabled(sourceOption(handler)));
			return result;
		}
		if ((isEnabled(USEFUNCTIONALLOOP_CLEANUP) || isEnabled(USEFUNCTIONALLOOP_CLEANUP_V2))
				&& !isEnabled(USEFUNCTIONALLOOP_FORMAT_FOR) && !isEnabled(USEFUNCTIONALLOOP_FORMAT_WHILE)) {
			return LoopConversionService.handlers(LoopTargetFormat.STREAM);
		}
		return EnumSet.noneOf(UseFunctionalCallFixCore.class);
	}

	public static String sourceOption(UseFunctionalCallFixCore handler) {
		return switch (handler) {
		case LOOP, FOR_TO_ITERATOR -> LOOP_CONVERSION_FROM_ENHANCED_FOR;
		case ITERATOR_LOOP, ITERATOR_TO_FOR -> LOOP_CONVERSION_FROM_ITERATOR_WHILE;
		case STREAM_TO_FOR, STREAM_TO_ITERATOR -> LOOP_CONVERSION_FROM_STREAM;
		case TRADITIONAL_FOR_LOOP -> LOOP_CONVERSION_FROM_CLASSIC_FOR;
		};
	}
}
