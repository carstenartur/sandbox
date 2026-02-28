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
package org.sandbox.jdt.triggerpattern.editor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.sourcelookup.AbstractSourceLookupParticipant;
import org.sandbox.jdt.triggerpattern.internal.EmbeddedJavaCompiler.CompilationResult;

/**
 * Source lookup participant that maps debug stack frames from generated
 * synthetic classes back to {@code .sandbox-hint} files.
 *
 * <p>When a breakpoint is hit in a generated {@code HintCode_*} class,
 * this participant maps the stack frame back to the original
 * {@code .sandbox-hint} file so the debugger shows the correct source.</p>
 *
 * <p>Compilation results are registered via
 * {@link #registerMapping(String, String, CompilationResult)} when
 * embedded Java blocks are compiled.</p>
 *
 * @since 1.5.0
 */
public class EmbeddedJavaSourceLocator extends AbstractSourceLookupParticipant {

	private static final Logger LOGGER = Logger.getLogger(EmbeddedJavaSourceLocator.class.getName());

	private static final String SYNTHETIC_PREFIX = "org.sandbox.generated.HintCode_"; //$NON-NLS-1$

	/**
	 * Maps synthetic class names to their hint file paths and compilation results.
	 */
	private static final Map<String, SourceMapping> MAPPINGS = new ConcurrentHashMap<>();

	/**
	 * A source mapping entry.
	 *
	 * @param hintFilePath    the workspace-relative path to the {@code .sandbox-hint} file
	 * @param compilationResult the compilation result with line mappings
	 */
	public record SourceMapping(String hintFilePath, CompilationResult compilationResult) {
	}

	/**
	 * Registers a mapping from a synthetic class to its hint file.
	 *
	 * @param syntheticClassName the fully qualified synthetic class name
	 * @param hintFilePath       the workspace-relative path to the hint file
	 * @param result             the compilation result with line mappings
	 */
	public static void registerMapping(String syntheticClassName, String hintFilePath,
			CompilationResult result) {
		MAPPINGS.put(syntheticClassName, new SourceMapping(hintFilePath, result));
		LOGGER.log(Level.FINE, "Registered source mapping: {0} -> {1}", //$NON-NLS-1$
				new Object[] { syntheticClassName, hintFilePath });
	}

	/**
	 * Clears all registered mappings.
	 */
	public static void clearMappings() {
		MAPPINGS.clear();
	}

	/**
	 * Returns the source mapping for a synthetic class, if any.
	 *
	 * @param syntheticClassName the fully qualified synthetic class name
	 * @return the source mapping, or {@code null} if not found
	 */
	public static SourceMapping getMapping(String syntheticClassName) {
		return MAPPINGS.get(syntheticClassName);
	}

	/**
	 * Checks if the given type name is a synthetic class generated from a hint file.
	 *
	 * @param typeName the fully qualified type name
	 * @return {@code true} if the type is a generated hint code class
	 */
	public static boolean isSyntheticHintClass(String typeName) {
		return typeName != null && typeName.startsWith(SYNTHETIC_PREFIX);
	}

	@Override
	public String getSourceName(Object object) {
		if (object instanceof IStackFrame frame) {
			try {
				String typeName = frame.getModelIdentifier();
				if (isSyntheticHintClass(typeName)) {
					SourceMapping mapping = MAPPINGS.get(typeName);
					if (mapping != null) {
						return mapping.hintFilePath();
					}
				}
			} catch (Exception e) {
				LOGGER.log(Level.FINE, "Failed to get source name from stack frame", e); //$NON-NLS-1$
			}
		}
		return null;
	}
}
