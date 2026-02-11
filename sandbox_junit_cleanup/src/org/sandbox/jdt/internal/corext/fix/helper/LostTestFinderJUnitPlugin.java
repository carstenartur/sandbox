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
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ANNOTATION_TEST;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_JUPITER_TEST;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_TEST;

import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.util.AnnotationUtils;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractTool;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;

/**
 * Plugin to detect and fix "lost" JUnit 3 tests that were not properly migrated.
 * A method is considered a lost test when:
 * - The class (or its superclasses) contains @Test annotated methods
 * - Method name starts with "test"
 * - No @Test annotation present
 * - Public void signature with no parameters
 * - Not annotated with lifecycle annotations
 */
public class LostTestFinderJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	private static final Set<String> LIFECYCLE_ANNOTATIONS = Set.of(
			"Before", "After", "BeforeClass", "AfterClass",  // JUnit 4
			"BeforeEach", "AfterEach", "BeforeAll", "AfterAll",  // JUnit 5
			"Ignore", "Disabled"  // Skip annotations
	);

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		
		// Visit all type declarations to find classes that have @Test methods
		compilationUnit.accept(new ASTVisitor() {
			@Override
			public boolean visit(TypeDeclaration node) {
				// Check if this class has any @Test methods (including inherited)
				if (classHasTestMethods(node)) {
					// Find lost test methods in this class
					findLostTestMethods(fixcore, node, operations, nodesprocessed);
				}
				return true;
			}
		});
	}

	/**
	 * Checks if the class or any of its superclasses contains @Test annotated methods
	 */
	private boolean classHasTestMethods(TypeDeclaration typeDecl) {
		// Check current class methods
		for (MethodDeclaration method : typeDecl.getMethods()) {
			if (hasTestAnnotation(method)) {
				return true;
			}
		}
		
		// Check superclass hierarchy using ITypeBinding
		ITypeBinding binding = typeDecl.resolveBinding();
		if (binding != null) {
			ITypeBinding superclass = binding.getSuperclass();
			while (superclass != null) {
				for (IMethodBinding method : superclass.getDeclaredMethods()) {
					if (hasTestAnnotationOnBinding(method)) {
						return true;
					}
				}
				superclass = superclass.getSuperclass();
			}
		}
		
		return false;
	}

	/**
	 * Checks if a method has a @Test annotation
	 */
	private boolean hasTestAnnotation(MethodDeclaration method) {
		for (Object modifier : method.modifiers()) {
			if (modifier instanceof Annotation) {
				Annotation ann = (Annotation) modifier;
				String name = ann.getTypeName().getFullyQualifiedName();
				if (name.equals("Test") || 
					name.equals("org.junit.Test") || 
					name.equals("org.junit.jupiter.api.Test")) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Checks if a method binding has a @Test annotation
	 */
	private boolean hasTestAnnotationOnBinding(IMethodBinding methodBinding) {
		for (org.eclipse.jdt.core.dom.IAnnotationBinding annotation : methodBinding.getAnnotations()) {
			String annotationName = annotation.getAnnotationType().getQualifiedName();
			if (annotationName.equals("org.junit.Test") || 
				annotationName.equals("org.junit.jupiter.api.Test")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Find methods that look like lost tests in the given type
	 */
	private void findLostTestMethods(JUnitCleanUpFixCore fixcore, TypeDeclaration typeDecl,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		
		for (MethodDeclaration method : typeDecl.getMethods()) {
			if (isLostTestMethod(method) && !nodesprocessed.contains(method)) {
				nodesprocessed.add(method);
				
				ReferenceHolder<Integer, JunitHolder> dataHolder = ReferenceHolder.createIndexed();
				JunitHolder mh = new JunitHolder();
				mh.setMinv(method);
				dataHolder.put(0, mh);
				operations.add(fixcore.rewrite(dataHolder));
			}
		}
	}

	/**
	 * Determines if a method is a lost test method
	 */
	private boolean isLostTestMethod(MethodDeclaration method) {
		// Must start with "test"
		String methodName = method.getName().getIdentifier();
		if (!methodName.startsWith("test")) {
			return false;
		}
		
		// Must not already have @Test annotation
		if (hasTestAnnotation(method)) {
			return false;
		}
		
		// Must be public void with no parameters
		if (!Modifier.isPublic(method.getModifiers())) {
			return false;
		}
		
		// Check for void return type using bindings when available, with a safe AST fallback
		org.eclipse.jdt.core.dom.ITypeBinding returnBinding = null;
		org.eclipse.jdt.core.dom.IMethodBinding methodBinding = method.resolveBinding();
		if (methodBinding != null) {
			returnBinding = methodBinding.getReturnType();
		}

		if (returnBinding != null) {
			// Binding-based check: require primitive void
			if (!returnBinding.isPrimitive() || !"void".equals(returnBinding.getName())) {
				return false;
			}
		} else {
			// Fallback: inspect the AST return type node defensively
			org.eclipse.jdt.core.dom.Type astReturnType = method.getReturnType2();
			if (astReturnType == null) {
				return false;
			}
			if (astReturnType.isPrimitiveType()) {
				org.eclipse.jdt.core.dom.PrimitiveType primitiveType = (org.eclipse.jdt.core.dom.PrimitiveType) astReturnType;
				if (primitiveType.getPrimitiveTypeCode() != org.eclipse.jdt.core.dom.PrimitiveType.VOID) {
					return false;
				}
			} else {
				// Only PrimitiveType with VOID is valid - any other return type is not a lost test
				return false;
			}
		}
		
		if (!method.parameters().isEmpty()) {
			return false;
		}
		
		// Must not have lifecycle annotations
		if (hasLifecycleAnnotation(method)) {
			return false;
		}
		
		return true;
	}

	/**
	 * Checks if a method has a lifecycle annotation that would prevent it from being a test
	 */
	private boolean hasLifecycleAnnotation(MethodDeclaration method) {
		for (Object modifier : method.modifiers()) {
			if (modifier instanceof Annotation) {
				Annotation ann = (Annotation) modifier;
				String name = ann.getTypeName().getFullyQualifiedName();
				int lastDot = name.lastIndexOf('.');
				String simpleName = lastDot == -1 ? name : name.substring(lastDot + 1);
				// Check simple name and fully qualified JUnit names
				if (LIFECYCLE_ANNOTATIONS.contains(name)
						|| ((name.startsWith("org.junit.") || name.startsWith("org.junit.jupiter.api."))
								&& LIFECYCLE_ANNOTATIONS.contains(simpleName))) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Determines which @Test annotation to add based on imports in the compilation unit
	 */
	private boolean shouldUseJUnit5(CompilationUnit compilationUnit) {
		final boolean[] hasJUnit5Import = {false};
		final boolean[] hasJUnit4Import = {false};
		
		// Check imports
		List<?> imports = compilationUnit.imports();
		for (Object obj : imports) {
			if (obj instanceof ImportDeclaration) {
				ImportDeclaration imp = (ImportDeclaration) obj;
				String importName = imp.getName().getFullyQualifiedName();
				if (importName.startsWith("org.junit.jupiter.api")) {
					hasJUnit5Import[0] = true;
				} else if (importName.equals("org.junit.Test")
						|| (importName.equals("org.junit") && imp.isOnDemand())) {
					hasJUnit4Import[0] = true;
				}
			}
		}
		
		// Prefer JUnit 5 if both are imported or neither
		return hasJUnit5Import[0] || !hasJUnit4Import[0];
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		MethodDeclaration method = (MethodDeclaration) junitHolder.getMinv();
		CompilationUnit cu = (CompilationUnit) method.getRoot();
		
		// Determine which @Test to use
		boolean useJUnit5 = shouldUseJUnit5(cu);
		
		// Add @Test annotation
		ListRewrite modifiers = rewriter.getListRewrite(method, MethodDeclaration.MODIFIERS2_PROPERTY);
		MarkerAnnotation testAnnotation= AnnotationUtils.createMarkerAnnotation(ast, ANNOTATION_TEST);
		modifiers.insertFirst(testAnnotation, group);
		
		// Add import
		if (useJUnit5) {
			importRewriter.addImport(ORG_JUNIT_JUPITER_TEST);
		} else {
			importRewriter.addImport(ORG_JUNIT_TEST);
		}
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					@Test
					public void testEdgeCase() {
						assertEquals(0, calc.divide(0, 1));
					}
					"""; //$NON-NLS-1$
		}
		return """
				public void testEdgeCase() {
					assertEquals(0, calc.divide(0, 1));
				}
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "LostTests"; //$NON-NLS-1$
	}
}
