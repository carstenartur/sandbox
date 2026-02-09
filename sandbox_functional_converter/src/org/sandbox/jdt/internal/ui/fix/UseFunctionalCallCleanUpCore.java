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
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.LOOP_CONVERSION_FROM_ENHANCED_FOR;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.LOOP_CONVERSION_FROM_ITERATOR_WHILE;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.LOOP_CONVERSION_FROM_STREAM;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.LOOP_CONVERSION_TARGET_FORMAT;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP_V2;
import static org.sandbox.jdt.internal.ui.fix.MultiFixMessages.FunctionalCallCleanUpFix_refactor;
import static org.sandbox.jdt.internal.ui.fix.MultiFixMessages.FunctionalCallCleanUp_description;
import static org.sandbox.jdt.internal.ui.preferences.cleanup.CleanUpMessages.LoopConversion_Description;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.internal.ui.fix.MapCleanUpOptions;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore;

public class UseFunctionalCallCleanUpCore extends AbstractCleanUp {
	
	private Map<String, String> optionsMap;
	
	public UseFunctionalCallCleanUpCore(final Map<String, String> options) {
		super(options);
		this.optionsMap = options;
	}

	public UseFunctionalCallCleanUpCore() {
	}

	@Override
	public void setOptions(CleanUpOptions options) {
		super.setOptions(options);
		if (options instanceof MapCleanUpOptions mapOptions) {
			this.optionsMap = mapOptions.getMap();
		}
	}

	@Override
	public CleanUpRequirements getRequirements() {
		return new CleanUpRequirements(requireAST(), false, false, null);
	}

	public boolean requireAST() {
		return isEnabled(USEFUNCTIONALLOOP_CLEANUP) || isEnabled(USEFUNCTIONALLOOP_CLEANUP_V2) || isEnabled(LOOP_CONVERSION_ENABLED);
	}

	@Override
	public ICleanUpFix createFix(final CleanUpContext context) throws CoreException {
		CompilationUnit compilationUnit = context.getAST();
		if (compilationUnit == null) {
			return null;
		}
		EnumSet<UseFunctionalCallFixCore> computeFixSet = computeFixSet();
		if ((!isEnabled(USEFUNCTIONALLOOP_CLEANUP) && !isEnabled(USEFUNCTIONALLOOP_CLEANUP_V2) && !isEnabled(LOOP_CONVERSION_ENABLED)) || computeFixSet.isEmpty()) {
			return null;
		}
		
		Set<CompilationUnitRewriteOperation> operations = new LinkedHashSet<>();
		Set<ASTNode> nodesprocessed = new HashSet<>();
		computeFixSet.forEach(i -> i.findOperations(compilationUnit, operations, nodesprocessed));
		if (operations.isEmpty()) {
			return null;
		}
		return new CompilationUnitRewriteOperationsFixCore(FunctionalCallCleanUpFix_refactor, compilationUnit,
				operations.toArray(new CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation[0]));
	}

	@Override
	public String[] getStepDescriptions() {
		List<String> result = new ArrayList<>();
		if (isEnabled(USEFUNCTIONALLOOP_CLEANUP) || isEnabled(USEFUNCTIONALLOOP_CLEANUP_V2)) {
			result.add(Messages.format(FunctionalCallCleanUp_description, new Object[] { String.join(",", //$NON-NLS-1$
					computeFixSet().stream().map(UseFunctionalCallFixCore::toString).collect(Collectors.toList())) }));
		}
		if (isEnabled(LOOP_CONVERSION_ENABLED)) {
			result.add(LoopConversion_Description);
		}
		return result.toArray(new String[0]);
	}

	@Override
	public String getPreview() {
		StringBuilder sb = new StringBuilder();
		EnumSet<UseFunctionalCallFixCore> computeFixSet = computeFixSet();
		EnumSet.allOf(UseFunctionalCallFixCore.class).forEach(e -> sb.append(e.getPreview(computeFixSet.contains(e))));
		return sb.toString();
	}

	private EnumSet<UseFunctionalCallFixCore> computeFixSet() {
		EnumSet<UseFunctionalCallFixCore> fixSet = EnumSet.noneOf(UseFunctionalCallFixCore.class);

		// Functional loop cleanup (handles both V1 and V2 constants for backward compatibility)
		if (isEnabled(USEFUNCTIONALLOOP_CLEANUP) || isEnabled(USEFUNCTIONALLOOP_CLEANUP_V2)) {
			// LOOP now uses the unified V2 implementation (ULR + Refactorer fallback)
			fixSet.add(UseFunctionalCallFixCore.LOOP);
			fixSet.add(UseFunctionalCallFixCore.ITERATOR_LOOP);
		}
		
		// Bidirectional Loop Conversion (Phase 9)
		if (isEnabled(LOOP_CONVERSION_ENABLED)) {
			String targetFormat = getTargetFormat();
			addBidirectionalTransformers(fixSet, targetFormat);
		}
		
		return fixSet;
	}

	private String getTargetFormat() {
		if (optionsMap != null) {
			String value = optionsMap.get(LOOP_CONVERSION_TARGET_FORMAT);
			if (value != null) {
				return value;
			}
		}
		return "stream"; //$NON-NLS-1$
	}

	private void addBidirectionalTransformers(EnumSet<UseFunctionalCallFixCore> fixSet, String targetFormat) {
		switch (targetFormat) {
		case "enhanced_for": //$NON-NLS-1$
			if (isEnabled(LOOP_CONVERSION_FROM_STREAM)) {
				fixSet.add(UseFunctionalCallFixCore.STREAM_TO_FOR);
			}
			if (isEnabled(LOOP_CONVERSION_FROM_ITERATOR_WHILE)) {
				fixSet.add(UseFunctionalCallFixCore.ITERATOR_TO_FOR);
			}
			break;
		case "iterator_while": //$NON-NLS-1$
			if (isEnabled(LOOP_CONVERSION_FROM_ENHANCED_FOR)) {
				fixSet.add(UseFunctionalCallFixCore.FOR_TO_ITERATOR);
			}
			if (isEnabled(LOOP_CONVERSION_FROM_STREAM)) {
				fixSet.add(UseFunctionalCallFixCore.STREAM_TO_ITERATOR);
			}
			break;
		case "stream": //$NON-NLS-1$
		default:
			if (isEnabled(LOOP_CONVERSION_FROM_ENHANCED_FOR)) {
				fixSet.add(UseFunctionalCallFixCore.LOOP);
			}
			if (isEnabled(LOOP_CONVERSION_FROM_ITERATOR_WHILE)) {
				fixSet.add(UseFunctionalCallFixCore.ITERATOR_LOOP);
			}
			break;
		}
	}
}
