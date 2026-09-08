/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/** Prevents generated enum names from changing existing name resolution. */
public final class EnumNameSafety {

	private EnumNameSafety() { }

	/** Conservatively rejects names already declared or referenced inside the owner. */
	public static boolean conflicts(TypeDeclaration owner, String name) {
		boolean[] conflict= { false };
		owner.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName node) {
				if (name.equals(node.getIdentifier())) {
					conflict[0]= true;
				}
				return !conflict[0];
			}
		});
		return conflict[0];
	}
}
