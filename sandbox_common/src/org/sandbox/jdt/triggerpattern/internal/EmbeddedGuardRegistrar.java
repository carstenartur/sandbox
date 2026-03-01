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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.sandbox.jdt.triggerpattern.api.GuardFunction;
import org.sandbox.jdt.triggerpattern.internal.EmbeddedJavaCompiler.CompilationResult;

/**
 * Registers guard functions defined in {@code <? ?>} blocks in the
 * {@link GuardRegistry}.
 *
 * <p>The {@link EmbeddedJavaCompiler} parses the code via {@link org.eclipse.jdt.core.dom.ASTParser}
 * (no bytecode). Since we have no bytecode, guard functions from
 * {@code <? ?>} blocks are registered as stub functions that always return
 * {@code true}, with a log warning that full execution requires bytecode
 * compilation.</p>
 *
 * <p>Built-in guards are not overridden by embedded guards.</p>
 *
 * @since 1.5.0
 */
public final class EmbeddedGuardRegistrar {

	private static final Logger LOGGER = Logger.getLogger(EmbeddedGuardRegistrar.class.getName());

	/**
	 * Tracks which guard names were registered by which ruleId, for unregistration.
	 */
	private static final Map<String, List<String>> REGISTERED_GUARDS = new ConcurrentHashMap<>();

	private EmbeddedGuardRegistrar() {
		// utility class
	}

	/**
	 * Registers all guard methods from a {@link CompilationResult}.
	 *
	 * <p>Strategy:</p>
	 * <ol>
	 *   <li>For each {@link MethodDeclaration} in {@code result.guardMethods()}:</li>
	 *   <li>Extract the method name</li>
	 *   <li>Check if already registered (built-in has precedence)</li>
	 *   <li>Create a {@link GuardFunction} stub wrapper</li>
	 *   <li>Register in {@link GuardRegistry#getInstance()}</li>
	 * </ol>
	 *
	 * @param result the compilation result with guard methods
	 * @param ruleId the HintFile ID for logging and tracking
	 */
	public static void registerGuards(CompilationResult result, String ruleId) {
		GuardRegistry registry = GuardRegistry.getInstance();
		List<String> registered = new ArrayList<>();

		for (MethodDeclaration method : result.guardMethods()) {
			String guardName = method.getName().getIdentifier();

			// Built-in guards take precedence
			if (registry.get(guardName) != null) {
				LOGGER.log(Level.FINE,
						"Guard ''{0}'' already registered (built-in takes precedence), skipping for ruleId={1}", //$NON-NLS-1$
						new Object[] { guardName, ruleId });
				continue;
			}

			// Register as a stub that always returns true (AST-only, no bytecode)
			GuardFunction stubFn = (ctx, args) -> {
				LOGGER.log(Level.FINE,
						"Embedded guard ''{0}'' invoked as stub (always true). " //$NON-NLS-1$
								+ "Full execution requires bytecode compilation.", //$NON-NLS-1$
						guardName);
				return true;
			};
			registry.register(guardName, stubFn);
			registered.add(guardName);

			LOGGER.log(Level.FINE,
					"Registered embedded guard ''{0}'' from ruleId={1}", //$NON-NLS-1$
					new Object[] { guardName, ruleId });
		}

		if (!registered.isEmpty()) {
			REGISTERED_GUARDS.put(ruleId, registered);
		}
	}

	/**
	 * Removes all guards registered by a specific ruleId.
	 * Called when a HintFile is reloaded (e.g., after editor save).
	 *
	 * @param ruleId the HintFile ID whose guards should be removed
	 */
	public static void unregisterGuards(String ruleId) {
		List<String> guardNames = REGISTERED_GUARDS.remove(ruleId);
		if (guardNames == null) {
			return;
		}

		GuardRegistry registry = GuardRegistry.getInstance();
		for (String guardName : guardNames) {
			registry.unregister(guardName);
		}

		LOGGER.log(Level.FINE,
				"Unregistered {0} guards for ruleId={1}", //$NON-NLS-1$
				new Object[] { guardNames.size(), ruleId });
	}
}
