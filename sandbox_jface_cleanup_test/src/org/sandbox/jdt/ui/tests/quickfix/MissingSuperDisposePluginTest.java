/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.helper.MissingSuperDisposePlugin;

/** Tests the executable dispose-call predicate used by the TriggerPattern helper. */
public class MissingSuperDisposePluginTest {

	@Test
	public void detectsTopLevelSuperDisposeCall() {
		Block methodBody= getMethodBody("""
				class Widget {
					void dispose() {
						cleanup();
						super.dispose();
					}
				}
				""", "dispose"); //$NON-NLS-1$

		assertTrue(MissingSuperDisposePlugin.containsSuperDisposeCall(methodBody));
	}

	@Test
	public void rejectsBodyWithoutSuperDisposeCall() {
		Block methodBody= getMethodBody("""
				class Widget {
					void dispose() {
						cleanup();
					}
				}
				""", "dispose"); //$NON-NLS-1$

		assertFalse(MissingSuperDisposePlugin.containsSuperDisposeCall(methodBody));
	}

	@Test
	public void doesNotTreatConditionalSuperCallAsUnconditional() {
		Block methodBody= getMethodBody("""
				class Widget {
					void dispose() {
						if (ready()) {
							super.dispose();
						}
					}
				}
				""", "dispose"); //$NON-NLS-1$

		assertFalse(MissingSuperDisposePlugin.containsSuperDisposeCall(methodBody));
	}

	@Test
	public void handlesBodylessMethod() {
		Block methodBody= getMethodBody("""
				abstract class Widget {
					abstract void dispose();
				}
				""", "dispose"); //$NON-NLS-1$

		assertNull(methodBody);
		assertFalse(MissingSuperDisposePlugin.containsSuperDisposeCall(methodBody));
	}

	private static Block getMethodBody(String code, String methodName) {
		CompilationUnit unit= parse(code);
		if (unit.types().isEmpty()) {
			return null;
		}
		TypeDeclaration type= (TypeDeclaration) unit.types().get(0);
		for (MethodDeclaration method : type.getMethods()) {
			if (methodName.equals(method.getName().getIdentifier())) {
				return method.getBody();
			}
		}
		return null;
	}

	private static CompilationUnit parse(String code) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(code.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		return (CompilationUnit) parser.createAST(null);
	}
}
