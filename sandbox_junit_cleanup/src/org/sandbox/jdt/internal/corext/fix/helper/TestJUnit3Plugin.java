/*******************************************************************************
 * Copyright (c) 2021 Carsten Hammer.
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

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.*;

/*-
 * #%L
 * Sandbox junit cleanup
 * %%
 * Copyright (C) 2024 hammer
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.util.AnnotationUtils;
import org.sandbox.jdt.internal.common.HelperVisitorFactory;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractTool;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;

/**
 * Plugin to migrate JUnit 3 TestCase classes to JUnit 5.
 */
public class TestJUnit3Plugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataHolder = ReferenceHolder.createIndexed();
		HelperVisitorFactory.callTypeDeclarationVisitor("junit.framework.TestCase", compilationUnit, dataHolder,
				nodesprocessed,
				(visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder, nodesprocessed));
	}

	private boolean processFoundNode(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, TypeDeclaration node,
			ReferenceHolder<Integer, JunitHolder> dataHolder, Set<ASTNode> nodesprocessed) {
		if (!nodesprocessed.contains(node)) {
			boolean hasLifecycleMethod = false;
			for (MethodDeclaration method : node.getMethods()) {
				if (!isTestMethod(method)) {
					hasLifecycleMethod = true;
					break;
				}
			}
			if (!hasLifecycleMethod) {
				return false;
			}

			nodesprocessed.add(node);
			JunitHolder mh = new JunitHolder();
			mh.setMinv(node);
			dataHolder.put(dataHolder.size(), mh);
			operations.add(fixcore.rewrite(dataHolder));
		}
		return false;
	}

	private boolean isTestMethod(MethodDeclaration method) {
		// Exclude constructors
		if (method.isConstructor()) {
			return false;
		}

		String methodName = method.getName().getIdentifier();

		// Check for typical JUnit 3 test methods
		if (methodName.startsWith("test")) {
			return true;
		}

		// Check for alternative naming schemes
		if (methodName.endsWith("_test") || methodName.startsWith("should") || methodName.contains("Test")) {
			return true;
		}

		// Additional conditions: public, void, no parameters
		Type returnType = method.getReturnType2();
		return Modifier.isPublic(method.getModifiers()) && returnType != null && "void".equals(returnType.toString())
				&& method.parameters().isEmpty();
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		TypeDeclaration node = junitHolder.getTypeDeclaration();
		// Remove `extends TestCase`
		Type superclass = node.getSuperclassType();
		if (superclass != null && "TestCase".equals(superclass.toString())) {
			rewriter.remove(node.getSuperclassType(), group);
		}

		for (MethodDeclaration method : node.getMethods()) {
			if (isSetupMethod(method)) {
				convertToAnnotation(method, "BeforeEach", importRewriter, rewriter, ast, group);
			} else if (isTeardownMethod(method)) {
				convertToAnnotation(method, "AfterEach", importRewriter, rewriter, ast, group);
			} else if (isTestMethod(method)) {
				addAnnotationToMethod(method, "Test", importRewriter, rewriter, ast, group);
			}

			// Process assertions and assumptions in all relevant methods
			if (method.getBody() != null) {
				rewriteAssertionsAndAssumptions(method, rewriter, ast, group, importRewriter);
			}
		}

	}

	private void rewriteAssertionsAndAssumptions(MethodDeclaration method, ASTRewrite rewriter, AST ast,
			TextEditGroup group, ImportRewrite importRewriter) {
		method.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation node) {
				// Check if the method binding can be resolved
				if (node.resolveMethodBinding() != null) {
					String fullyQualifiedName = node.resolveMethodBinding().getDeclaringClass().getQualifiedName();

					if ("junit.framework.Assert".equals(fullyQualifiedName)
							|| "junit.framework.Assume".equals(fullyQualifiedName)) {
						reorderParameters(node, rewriter, group, ONEPARAM_ASSERTIONS, TWOPARAM_ASSERTIONS);

						// Update qualifier (e.g., Assert.assertEquals -> Assertions.assertEquals)
						rewriter.set(node.getExpression(), SimpleName.IDENTIFIER_PROPERTY, "Assertions", group);

						// Update imports
						addImportForAssertion(node.getName().getIdentifier(), importRewriter);
					}
				}

				return super.visit(node);
			}
		});
	}

	private void addImportForAssertion(String assertionMethod, ImportRewrite importRewriter) {
		String importToAdd = null;

		switch (assertionMethod) {
		case "assertEquals":
		case "assertArrayEquals":
		case "assertTrue":
		case "assertFalse":
		case "assertNull":
		case "assertNotNull":
			importToAdd = ORG_JUNIT_JUPITER_API_ASSERTIONS;
			break;
		case "assumeTrue":
		case "assumeFalse":
		case "assumeNotNull":
			importToAdd = ORG_JUNIT_JUPITER_API_ASSUMPTIONS;
			break;
		case "assertThat":
			importToAdd = ORG_HAMCREST_MATCHER_ASSERT;
			break;
		default:
			break;
		}

		if (importToAdd != null) {
			importRewriter.addImport(importToAdd);
		}
	}

	private boolean isSetupMethod(MethodDeclaration method) {
		return "setUp".equals(method.getName().getIdentifier()) && method.parameters().isEmpty()
				&& method.getReturnType2() == null;
	}

	private boolean isTeardownMethod(MethodDeclaration method) {
		return "tearDown".equals(method.getName().getIdentifier()) && method.parameters().isEmpty()
				&& method.getReturnType2() == null;
	}

	private void convertToAnnotation(MethodDeclaration method, String annotation, ImportRewrite importRewrite,
			ASTRewrite rewrite, AST ast, TextEditGroup group) {
		ListRewrite modifiers = rewrite.getListRewrite(method, MethodDeclaration.MODIFIERS2_PROPERTY);
		MarkerAnnotation newMarkerAnnotation = AnnotationUtils.createMarkerAnnotation(ast, annotation);
		modifiers.insertFirst(newMarkerAnnotation, group);
		importRewrite.addImport("org.junit.jupiter.api." + annotation);
	}

	private void addAnnotationToMethod(MethodDeclaration method, String annotation, ImportRewrite importRewrite,
			ASTRewrite rewrite, AST ast, TextEditGroup group) {
		ListRewrite modifiers = rewrite.getListRewrite(method, MethodDeclaration.MODIFIERS2_PROPERTY);
		MarkerAnnotation newMarkerAnnotation = AnnotationUtils.createMarkerAnnotation(ast, annotation);
		modifiers.insertFirst(newMarkerAnnotation, group);
		importRewrite.addImport("org.junit.jupiter.api." + annotation);
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					import org.junit.jupiter.api.Test;
					"""; //$NON-NLS-1$
		}
		return """
				import junit.framework.TestCase;
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "TestCase"; //$NON-NLS-1$
	}
}
