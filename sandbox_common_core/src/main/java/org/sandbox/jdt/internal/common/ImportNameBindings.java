/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.common;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Name;

/** Preserves static import bindings before a batch parser releases source dependency scopes. */
public final class ImportNameBindings {

	private static final String PROPERTY= ImportNameBindings.class.getName();

	private ImportNameBindings() {
	}

	/**
	 * Call from {@code ASTRequestor.acceptAST}, while source dependency methods can
	 * still be resolved. A snapshot, including an unresolved result, belongs only
	 * to this AST; subsequent reference scans must not repeat that resolution.
	 */
	public static void capture(CompilationUnit root) {
		for (Object value : root.imports()) {
			ImportDeclaration declaration= (ImportDeclaration) value;
			if (!declaration.isStatic() || declaration.isOnDemand()) {
				continue;
			}
			declaration.accept(new ASTVisitor() {
				@Override
				public void preVisit(ASTNode node) {
					if (node instanceof Name name) {
						name.setProperty(PROPERTY, new Snapshot(name.resolveBinding()));
					}
				}
			});
		}
	}

	/** Resolves ordinary references normally and retains captured import identities, including null. */
	public static IBinding resolve(Name name) {
		return name.getProperty(PROPERTY) instanceof Snapshot snapshot ? snapshot.binding() : name.resolveBinding();
	}

	private record Snapshot(IBinding binding) {
	}
}
