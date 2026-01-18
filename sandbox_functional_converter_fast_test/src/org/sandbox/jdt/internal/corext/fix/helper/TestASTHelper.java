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
package org.sandbox.jdt.internal.corext.fix.helper;

import org.eclipse.jdt.core.dom.*;
import java.util.Map;
import org.eclipse.jdt.core.JavaCore;

/**
 * Helper class for creating AST structures in tests without Eclipse runtime.
 */
public class TestASTHelper {
	/**
	 * Parses Java source code into a CompilationUnit AST.
	 * 
	 * @param code the Java source code to parse
	 * @return the parsed CompilationUnit
	 */
	public static CompilationUnit parse(String code) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(code.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_17, options);
		parser.setCompilerOptions(options);
		parser.setResolveBindings(false);
		return (CompilationUnit) parser.createAST(null);
	}
	
	/**
	 * Finds the first EnhancedForStatement in the given CompilationUnit.
	 * 
	 * @param cu the compilation unit to search
	 * @return the first EnhancedForStatement found, or null if none exists
	 */
	public static EnhancedForStatement findFirstEnhancedFor(CompilationUnit cu) {
		final EnhancedForStatement[] result = new EnhancedForStatement[1];
		cu.accept(new ASTVisitor() {
			@Override
			public boolean visit(EnhancedForStatement node) {
				if (result[0] == null) {
					result[0] = node;
				}
				return false;
			}
		});
		return result[0];
	}
}
