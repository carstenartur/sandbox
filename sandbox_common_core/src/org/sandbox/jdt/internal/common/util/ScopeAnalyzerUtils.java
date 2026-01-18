/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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
package org.sandbox.jdt.internal.common.util;

import org.eclipse.jdt.core.dom.*;
import java.util.*;

/**
 * OSGi-free scope analysis utilities.
 * 
 * <p>This class provides utilities for analyzing variable usage and declarations
 * without dependencies on Eclipse OSGi internal classes, enabling fast unit testing
 * without Tycho.</p>
 */
public final class ScopeAnalyzerUtils {
	private ScopeAnalyzerUtils() {}
	
	/**
	 * Returns the collection of variable names used (referenced) in the given node and its subtree.
	 * 
	 * @param node the AST node to analyze
	 * @return collection of variable names that are referenced (not declared)
	 */
	public static Collection<String> getUsedVariableNames(ASTNode node) {
		Set<String> names = new HashSet<>();
		node.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName name) {
				if (!name.isDeclaration()) {
					names.add(name.getIdentifier());
				}
				return true;
			}
		});
		return names;
	}
	
	/**
	 * Returns the collection of variable names declared in the given node and its subtree.
	 * 
	 * @param node the AST node to analyze
	 * @return collection of variable names that are declared
	 */
	public static Collection<String> getDeclaredVariableNames(ASTNode node) {
		Set<String> names = new HashSet<>();
		node.accept(new ASTVisitor() {
			@Override
			public boolean visit(VariableDeclarationFragment fragment) {
				names.add(fragment.getName().getIdentifier());
				return true;
			}
			@Override
			public boolean visit(SingleVariableDeclaration decl) {
				names.add(decl.getName().getIdentifier());
				return true;
			}
		});
		return names;
	}
}
