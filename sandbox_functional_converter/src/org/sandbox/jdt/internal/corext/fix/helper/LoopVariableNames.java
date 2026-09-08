/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.SimpleName;

/** Reserves identifiers across loop handlers sharing one cleanup AST. */
final class LoopVariableNames {

	private static final String KEY = LoopVariableNames.class.getName();
	private final Set<String> names = new HashSet<>();

	private LoopVariableNames(ASTNode root) {
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName name) {
				names.add(name.getIdentifier());
				return true;
			}
		});
	}

	static Set<String> usedNames(ASTNode node) {
		ASTNode root = node.getRoot();
		if (root.getProperty(KEY) instanceof LoopVariableNames existing) {
			return existing.names;
		}
		LoopVariableNames allocator = new LoopVariableNames(root);
		root.setProperty(KEY, allocator);
		return allocator.names;
	}

	static String fresh(String base, Set<String> names) {
		String name = base;
		for (int suffix = 1; !names.add(name); suffix++) {
			name = base + suffix;
		}
		return name;
	}

	/** Isolates repeated analyses and previews on JDT's shared editor AST. */
	static final class Scope implements AutoCloseable {
		private final ASTNode root;
		private final Object previous;

		Scope(ASTNode root, Set<String> reserved) {
			this.root = root;
			previous = root.getProperty(KEY);
			LoopVariableNames allocator = new LoopVariableNames(root);
			allocator.names.addAll(reserved);
			root.setProperty(KEY, allocator);
		}

		@Override
		public void close() {
			root.setProperty(KEY, previous);
		}
	}
}
