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
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.USEFUNCTIONALLOOP_FORMAT_FOR;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.USEFUNCTIONALLOOP_FORMAT_WHILE;
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
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore;

public class UseFunctionalCallCleanUpCore extends AbstractCleanUp {
	public UseFunctionalCallCleanUpCore(final Map<String, String> options) {
		super(options);
	}

	public UseFunctionalCallCleanUpCore() {
	}

	@Override
	public CleanUpRequirements getRequirements() {
		return new CleanUpRequirements(requireAST(), false, false, null);
	}

	public boolean requireAST() {
		return isEnabled(USEFUNCTIONALLOOP_CLEANUP) || isEnabled(LOOP_CONVERSION_ENABLED);
	}

	@Override
	public ICleanUpFix createFix(final CleanUpContext context) throws CoreException {
		CompilationUnit compilationUnit = context.getAST();
		if (compilationUnit == null) {
			return null;
		}
		EnumSet<UseFunctionalCallFixCore> computeFixSet = computeFixSet();
		if ((!isEnabled(USEFUNCTIONALLOOP_CLEANUP) && !isEnabled(LOOP_CONVERSION_ENABLED)) || computeFixSet.isEmpty()) {
			return null;
		}
		
		// Check target format preference (STREAM, FOR_LOOP, WHILE_LOOP)
		// Note: Currently only STREAM is fully implemented. FOR_LOOP and WHILE_LOOP
		// support will be added in future phases.
		// For backward compatibility, proceed with STREAM format unless FOR or WHILE is explicitly enabled
		if (isEnabled(USEFUNCTIONALLOOP_FORMAT_FOR) || isEnabled(USEFUNCTIONALLOOP_FORMAT_WHILE)) {
			// Not yet implemented - return null to skip transformation
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
		if (isEnabled(USEFUNCTIONALLOOP_CLEANUP)) {
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

		// Legacy functional loop cleanup (backward compatibility)
		if (isEnabled(USEFUNCTIONALLOOP_CLEANUP)) {
			// Add LOOP (V1) and ITERATOR_LOOP (Phase 7) for comprehensive loop conversions
			fixSet.add(UseFunctionalCallFixCore.LOOP);
			fixSet.add(UseFunctionalCallFixCore.ITERATOR_LOOP);
		}
		
		// Bidirectional Loop Conversion (Phase 9)
		if (isEnabled(LOOP_CONVERSION_ENABLED)) {
			// Determine target format by checking which format options are enabled
			// Default to stream if none specified
			String targetFormat = "stream";
			// Note: In the current implementation, the combo box stores the value directly in the option
			// but we can't access it here. For now, we'll use a simpler approach based on enabled flags.
			// TODO: Once we have access to the options map, use: options.get(LOOP_CONVERSION_TARGET_FORMAT)
			
			// For now, default to "stream" until we can properly access the combo value
			// The UI combo box will work, but the transformation logic defaults to stream
			
			// Add appropriate transformers based on source/target combination
			if (isEnabled(LOOP_CONVERSION_FROM_ENHANCED_FOR)) {
				if ("stream".equals(targetFormat)) {
					fixSet.add(UseFunctionalCallFixCore.LOOP); // existing: enhanced-for → stream
				} else if ("iterator_while".equals(targetFormat)) {
					fixSet.add(UseFunctionalCallFixCore.FOR_TO_ITERATOR); // new: enhanced-for → iterator-while
				}
				// enhanced-for → enhanced-for is no-op, skip
			}
			
			if (isEnabled(LOOP_CONVERSION_FROM_ITERATOR_WHILE)) {
				if ("stream".equals(targetFormat)) {
					fixSet.add(UseFunctionalCallFixCore.ITERATOR_LOOP); // existing: iterator-while → stream
				} else if ("enhanced_for".equals(targetFormat)) {
					fixSet.add(UseFunctionalCallFixCore.ITERATOR_TO_FOR); // new: iterator-while → enhanced-for
				}
				// iterator-while → iterator-while is no-op, skip
			}
			
			if (isEnabled(LOOP_CONVERSION_FROM_STREAM)) {
				if ("enhanced_for".equals(targetFormat)) {
					fixSet.add(UseFunctionalCallFixCore.STREAM_TO_FOR); // new: stream → enhanced-for
				} else if ("iterator_while".equals(targetFormat)) {
					fixSet.add(UseFunctionalCallFixCore.STREAM_TO_ITERATOR); // new: stream → iterator-while
				}
				// stream → stream is no-op, skip
			}
			
			// Classic for-loop conversion (experimental, not yet implemented)
			// if (isEnabled(LOOP_CONVERSION_FROM_CLASSIC_FOR)) {
			//     // TODO: Implement in future phase
			// }
		}
		
		return fixSet;
	}
}
