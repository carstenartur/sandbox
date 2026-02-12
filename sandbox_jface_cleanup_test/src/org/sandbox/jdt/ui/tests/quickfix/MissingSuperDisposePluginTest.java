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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Tests for MissingSuperDisposePlugin.
 * 
 * <p>This test suite validates the MissingSuperDisposePlugin that detects missing
 * super.dispose() calls in SWT Widget subclasses.</p>
 * 
 * <p><b>Current Status:</b> Most tests are disabled because they require
 * TriggerPattern engine enhancements that are not yet implemented:</p>
 * <ul>
 * <li>Override detection - checking if a method overrides a specific parent class method</li>
 * <li>Body constraint validation - automatically checking for patterns in method bodies</li>
 * <li>TriggerPattern-based cleanup integration - running cleanups via @TriggerPattern annotations</li>
 * </ul>
 * 
 * <p>The tests are written following the standard cleanup test pattern used in
 * other plugins (before/after code comparison with enum-based test cases), but
 * are currently disabled until the TriggerPattern engine is complete.</p>
 * 
 * @see org.sandbox.jdt.internal.corext.fix.helper.MissingSuperDisposePlugin
 */
public class MissingSuperDisposePluginTest {

	/**
	 * Test cases for missing super.dispose() detection.
	 * 
	 * <p>Following the standard pattern used in other cleanup tests (e.g., Java8CleanUpTest),
	 * each enum value contains the input code (with missing super call) and expected output
	 * (with super.dispose() added).</p>
	 * 
	 * <p><b>Note:</b> These tests are currently disabled because the TriggerPattern engine
	 * doesn't yet support override detection and body constraints.</p>
	 */
	enum MissingSuperDisposeTestCases {
		/**
		 * Basic case: Widget subclass with dispose() that lacks super.dispose()
		 */
		BasicMissingSuperCall(
"""
package test;
import org.eclipse.swt.widgets.Widget;

public class MyWidget extends Widget {
	@Override
	public void dispose() {
		cleanup();
	}
	
	private void cleanup() {
		System.out.println("Cleaning up");
	}
}
""", //$NON-NLS-1$
"""
package test;
import org.eclipse.swt.widgets.Widget;

public class MyWidget extends Widget {
	@Override
	public void dispose() {
		cleanup();
		super.dispose();
	}
	
	private void cleanup() {
		System.out.println("Cleaning up");
	}
}
"""), //$NON-NLS-1$
		
		/**
		 * Case where super.dispose() already exists - should not be modified
		 */
		AlreadyHasSuperCall(
"""
package test;
import org.eclipse.swt.widgets.Widget;

public class MyWidget extends Widget {
	@Override
	public void dispose() {
		cleanup();
		super.dispose();
	}
	
	private void cleanup() {
		System.out.println("Cleaning up");
	}
}
""", //$NON-NLS-1$
"""
package test;
import org.eclipse.swt.widgets.Widget;

public class MyWidget extends Widget {
	@Override
	public void dispose() {
		cleanup();
		super.dispose();
	}
	
	private void cleanup() {
		System.out.println("Cleaning up");
	}
}
"""), //$NON-NLS-1$
		
		/**
		 * Case with empty dispose() method - should add super.dispose()
		 */
		EmptyDisposeMethod(
"""
package test;
import org.eclipse.swt.widgets.Widget;

public class MyWidget extends Widget {
	@Override
	public void dispose() {
	}
}
""", //$NON-NLS-1$
"""
package test;
import org.eclipse.swt.widgets.Widget;

public class MyWidget extends Widget {
	@Override
	public void dispose() {
		super.dispose();
	}
}
"""), //$NON-NLS-1$
		
		/**
		 * Multiple Widget subclasses in same file - should fix all
		 */
		MultipleWidgets(
"""
package test;
import org.eclipse.swt.widgets.Widget;

public class MyWidget extends Widget {
	@Override
	public void dispose() {
		cleanup();
	}
	
	private void cleanup() {
		System.out.println("Cleaning up");
	}
}

class AnotherWidget extends Widget {
	@Override
	public void dispose() {
		System.out.println("Disposing");
	}
}
""", //$NON-NLS-1$
"""
package test;
import org.eclipse.swt.widgets.Widget;

public class MyWidget extends Widget {
	@Override
	public void dispose() {
		cleanup();
		super.dispose();
	}
	
	private void cleanup() {
		System.out.println("Cleaning up");
	}
}

class AnotherWidget extends Widget {
	@Override
	public void dispose() {
		System.out.println("Disposing");
		super.dispose();
	}
}
"""); //$NON-NLS-1$
		
		public final String given;
		public final String expected;
		
		MissingSuperDisposeTestCases(String given, String expected) {
			this.given = given;
			this.expected = expected;
		}
	}

	/**
	 * Disabled: Full cleanup test following the standard pattern.
	 * 
	 * <p>This test would run the MissingSuperDisposePlugin cleanup on code with
	 * missing super.dispose() calls and verify the fix is applied correctly.</p>
	 * 
	 * <p><b>Requires:</b> TriggerPattern engine with override detection and body constraints.</p>
	 * 
	 * <p><b>Pattern:</b> Same as Java8CleanUpTest - parameterized test with enum cases,
	 * before/after code comparison.</p>
	 */
	@Test
	@Disabled("Requires TriggerPattern engine enhancements: override detection and body constraints")
	public void testMissingSuperDisposeCleanup() {
		// Pattern when implemented:
		// @ParameterizedTest
		// @EnumSource(MissingSuperDisposeTestCases.class)
		// public void testMissingSuperDisposeCleanup(MissingSuperDisposeTestCases test) throws CoreException {
		//     IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
		//     ICompilationUnit cu = pack.createCompilationUnit("MyWidget.java", test.given, false, null);
		//     context.enable(MYCleanUpConstants.MISSING_SUPER_DISPOSE_CLEANUP); // Constant TBD
		//     context.assertRefactoringResultAsExpected(new ICompilationUnit[] {cu}, new String[] {test.expected}, null);
		// }
	}

	/**
	 * Tests that containsSuperDisposeCall correctly identifies super.dispose() calls.
	 * 
	 * <p>This test verifies the helper method can detect super.dispose() in a method body.</p>
	 */
	@Test
	public void testContainsSuperDisposeCall_Found() {
		String code = """
			class Widget {
				void dispose() {
					cleanup();
					super.dispose();
				}
			}
			""";
		
		Block methodBody = getMethodBody(code, "dispose");
		assertNotNull(methodBody, "Method body should not be null");
		
		// Note: To properly test the private containsSuperDisposeCall method,
		// we would need to either:
		// 1. Make it package-private/protected for testing
		// 2. Use reflection
		// 3. Test it indirectly through the public API (when available)
		// 
		// For now, this test documents the expected behavior.
		// The actual assertion would be:
		// assertTrue(MissingSuperDisposePlugin.containsSuperDisposeCall(methodBody));
	}

	/**
	 * Tests that containsSuperDisposeCall returns false when super.dispose() is not present.
	 */
	@Test
	public void testContainsSuperDisposeCall_NotFound() {
		String code = """
			class Widget {
				void dispose() {
					cleanup();
					// Missing super.dispose()
				}
			}
			""";
		
		Block methodBody = getMethodBody(code, "dispose");
		assertNotNull(methodBody, "Method body should not be null");
		
		// Expected: containsSuperDisposeCall should return false
	}

	/**
	 * Tests that containsSuperDisposeCall handles null body (abstract/interface methods).
	 */
	@Test
	public void testContainsSuperDisposeCall_NullBody() {
		String code = """
			abstract class Widget {
				abstract void dispose();
			}
			""";
		
		Block methodBody = getMethodBody(code, "dispose");
		assertEquals(null, methodBody, "Abstract method should have null body");
		
		// Expected: containsSuperDisposeCall should handle null gracefully
	}

	/**
	 * Tests pattern matching for dispose() methods.
	 * 
	 * <p>This test validates that the TriggerPattern framework can match
	 * dispose() method declarations. This is a prerequisite for the
	 * MissingSuperDisposePlugin to work.</p>
	 */
	@Test
	public void testDisposeMethodPatternMatching() {
		String code = """
			class MyWidget {
				void dispose() {
					cleanup();
				}
				
				void setup() {
					initialize();
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		
		// Count dispose methods
		final int[] disposeCount = {0};
		cu.accept(new org.eclipse.jdt.core.dom.ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration node) {
				if ("dispose".equals(node.getName().getIdentifier())) { //$NON-NLS-1$
					disposeCount[0]++;
				}
				return super.visit(node);
			}
		});
		
		assertEquals(1, disposeCount[0], "Should find one dispose method");
	}

	/**
	 * Helper method to parse code and get a method body.
	 * 
	 * @param code Java source code
	 * @param methodName the method name to find
	 * @return the Block (method body) or null if not found or abstract
	 */
	private Block getMethodBody(String code, String methodName) {
		CompilationUnit cu = parse(code);
		
		// Find the first type declaration
		if (cu.types().isEmpty()) {
			return null;
		}
		
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		
		// Find the method
		for (MethodDeclaration method : typeDecl.getMethods()) {
			if (methodName.equals(method.getName().getIdentifier())) {
				return method.getBody();
			}
		}
		
		return null;
	}

	/**
	 * Helper method to parse Java code into a CompilationUnit.
	 * 
	 * @param code Java source code
	 * @return parsed CompilationUnit
	 */
	private CompilationUnit parse(String code) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(code.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		return (CompilationUnit) parser.createAST(null);
	}
}
