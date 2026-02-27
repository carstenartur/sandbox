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
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix;

import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IMarker;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.sandbox.jdt.triggerpattern.eclipse.HintFinding;

/**
 * Fix core for detecting obsolete collection types.
 *
 * <p>Warns on usage of {@code Vector}, {@code Hashtable}, and {@code Stack},
 * which are legacy synchronized collections that should typically be replaced.
 * Findings are reported as problem markers via {@link HintFinding}.</p>
 */
public class ObsoleteCollectionFixCore {

	private static final Set<String> OBSOLETE_TYPES = Set.of(
			"java.util.Vector", //$NON-NLS-1$
			"java.util.Hashtable", //$NON-NLS-1$
			"java.util.Stack" //$NON-NLS-1$
	);

	/**
	 * Finds obsolete collection usage in the compilation unit.
	 *
	 * @param compilationUnit the compilation unit to search
	 * @param findings the list to collect hint-only findings into
	 */
	public static void findFindings(CompilationUnit compilationUnit,
			List<HintFinding> findings) {

		compilationUnit.accept(new ASTVisitor() {
			@Override
			public boolean visit(ClassInstanceCreation node) {
				ITypeBinding typeBinding = node.resolveTypeBinding();
				if (typeBinding != null && OBSOLETE_TYPES.contains(typeBinding.getErasure().getQualifiedName())) {
					findings.add(new HintFinding(
							"Obsolete collection type \u2014 consider using ArrayList, HashMap, or ArrayDeque", //$NON-NLS-1$
							compilationUnit.getLineNumber(node.getStartPosition()),
							node.getStartPosition(),
							node.getStartPosition() + node.getLength(),
							IMarker.SEVERITY_WARNING));
				}
				return true;
			}

			@Override
			public boolean visit(VariableDeclarationStatement node) {
				Type type = node.getType();
				if (type instanceof SimpleType simpleType) {
					ITypeBinding binding = simpleType.resolveBinding();
					if (binding != null && OBSOLETE_TYPES.contains(binding.getErasure().getQualifiedName())) {
						findings.add(new HintFinding(
								"Obsolete collection type \u2014 consider using ArrayList, HashMap, or ArrayDeque", //$NON-NLS-1$
								compilationUnit.getLineNumber(node.getStartPosition()),
								node.getStartPosition(),
								node.getStartPosition() + node.getLength(),
								IMarker.SEVERITY_WARNING));
					}
				}
				return true;
			}
		});
	}
}
