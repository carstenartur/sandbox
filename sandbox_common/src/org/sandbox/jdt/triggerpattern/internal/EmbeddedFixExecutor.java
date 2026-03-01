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
package org.sandbox.jdt.triggerpattern.internal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.sandbox.jdt.triggerpattern.internal.EmbeddedJavaCompiler.CompilationResult;

/**
 * Executor for fix functions defined in {@code <? ?>} blocks.
 *
 * <p>Analogous to {@link EmbeddedGuardRegistrar}, but for fix functions annotated
 * with {@code @FixFunction}. Searches compiled {@code <? ?>} classes for annotated
 * methods and invokes them.</p>
 *
 * <p>For the current AST-only implementation, fix functions are registered as stubs
 * that log a warning. Full execution requires bytecode compilation support.</p>
 *
 * @since 1.5.0
 */
public final class EmbeddedFixExecutor {

	private static final Logger LOGGER = Logger.getLogger(EmbeddedFixExecutor.class.getName());

	/**
	 * Tracks registered fix function names by ruleId.
	 */
	private static final Map<String, Map<String, MethodDeclaration>> REGISTERED_FIXES = new ConcurrentHashMap<>();

	private EmbeddedFixExecutor() {
		// utility class
	}

	/**
	 * Registers all fix methods from a {@link CompilationResult}.
	 *
	 * @param result the compilation result with fix methods
	 * @param ruleId the HintFile ID for tracking
	 */
	public static void registerFixes(CompilationResult result, String ruleId) {
		if (result.fixMethods().isEmpty()) {
			return;
		}

		Map<String, MethodDeclaration> fixMap = new ConcurrentHashMap<>();
		for (MethodDeclaration method : result.fixMethods()) {
			String fixName = method.getName().getIdentifier();
			fixMap.put(fixName, method);
			LOGGER.log(Level.FINE,
					"Registered embedded fix function ''{0}'' from ruleId={1}", //$NON-NLS-1$
					new Object[] { fixName, ruleId });
		}
		REGISTERED_FIXES.put(ruleId, fixMap);
	}

	/**
	 * Removes all fix functions registered by a specific ruleId.
	 *
	 * @param ruleId the HintFile ID whose fixes should be removed
	 */
	public static void unregisterFixes(String ruleId) {
		REGISTERED_FIXES.remove(ruleId);
	}

	/**
	 * Checks whether a fix function with the given name is registered.
	 *
	 * @param fixName the fix function name
	 * @return {@code true} if a fix with that name exists
	 */
	public static boolean hasFix(String fixName) {
		for (Map<String, MethodDeclaration> fixMap : REGISTERED_FIXES.values()) {
			if (fixMap.containsKey(fixName)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Executes a fix function by name.
	 *
	 * <p>In the current AST-only implementation, this logs a warning that full
	 * execution requires bytecode compilation. The fix function's AST
	 * {@link MethodDeclaration} is available for inspection but cannot be
	 * executed without bytecode.</p>
	 *
	 * @param fixName the fix function name
	 */
	public static void execute(String fixName) {
		LOGGER.log(Level.WARNING,
				"Embedded fix function ''{0}'' invoked as stub. " //$NON-NLS-1$
						+ "Full execution requires bytecode compilation.", //$NON-NLS-1$
				fixName);
	}
}
