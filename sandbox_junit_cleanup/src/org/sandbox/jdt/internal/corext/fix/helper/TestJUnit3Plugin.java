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

import java.util.List;

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
import org.eclipse.jdt.core.dom.Expression;
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
import org.sandbox.jdt.internal.common.HelperVisitor;
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
		ReferenceHolder<Integer, JunitHolder> dataHolder= ReferenceHolder.createIndexed();
		HelperVisitor.callTypeDeclarationVisitor("junit.framework.TestCase", compilationUnit, dataHolder,
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
			JunitHolder mh= new JunitHolder();
			mh.setMinv(node);
			dataHolder.put(dataHolder.size(), mh);
			operations.add(fixcore.rewrite(dataHolder));
		}
		return false;
	}

	@SuppressWarnings("unused") // Method intended for future assertion order correction feature
	private void correctAssertionOrder(MethodInvocation node, ASTRewrite rewriter, AST ast, TextEditGroup group) {
	    String methodName = node.getName().getIdentifier();

	    // Prüfe, ob es sich um eine bekannte Assertion handelt
	    if ("assertEquals".equals(methodName) || "assertArrayEquals".equals(methodName)) {
	        List<?> arguments = node.arguments();
	        if (arguments.size() == 3) {
	            // Reorganisiere die Reihenfolge: message, expected, actual -> expected, actual, message
	            Expression expected = (Expression) arguments.get(1);
	            Expression actual = (Expression) arguments.get(2);
	            Expression message = (Expression) arguments.get(0);

	            ListRewrite listRewrite = rewriter.getListRewrite(node, MethodInvocation.ARGUMENTS_PROPERTY);
	            listRewrite.replace((ASTNode) arguments.get(0), expected, group);
	            listRewrite.replace((ASTNode) arguments.get(1), actual, group);
	            listRewrite.replace((ASTNode) arguments.get(2), message, group);
	        }
	    }
	}

	private boolean isTestMethod(MethodDeclaration method) {
	    // Konstruktoren ausschließen
	    if (method.isConstructor()) {
	        return false;
	    }

	    String methodName = method.getName().getIdentifier();

	    // Prüfen auf typische JUnit 3-Testmethoden
	    if (methodName.startsWith("test")) {
	        return true;
	    }

	    // Prüfen auf alternative Namensschemata
	    if (methodName.endsWith("_test") || methodName.startsWith("should") || methodName.contains("Test")) {
	        return true;
	    }

	    // Zusätzliche Bedingungen: public, void, keine Parameter
	    return Modifier.isPublic(method.getModifiers()) && "void".equals(method.getReturnType2().toString())
	            && method.parameters().isEmpty();
	}


	@Override
	protected
	void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		TypeDeclaration node= junitHolder.getTypeDeclaration();
		// Remove `extends TestCase`
		Type superclass= node.getSuperclassType();
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

		    // Bearbeite Assertions und Assumptions in allen relevanten Methoden
		    if (method.getBody() != null) {
		        rewriteAssertionsAndAssumptions(method, rewriter, ast, group,importRewriter);
		    }
		}

	}

	private void rewriteAssertionsAndAssumptions(MethodDeclaration method, ASTRewrite rewriter, AST ast, TextEditGroup group, ImportRewrite importRewriter) {
	    method.accept(new ASTVisitor() {
	        @Override
	        public boolean visit(MethodInvocation node) {
	            // Überprüfen, ob das MethodBinding aufgelöst werden kann
	            if (node.resolveMethodBinding() != null) {
	                String fullyQualifiedName = node.resolveMethodBinding().getDeclaringClass().getQualifiedName();

	                if ("junit.framework.Assert".equals(fullyQualifiedName) || "junit.framework.Assume".equals(fullyQualifiedName)) {
//	                    correctAssertionOrder(node, rewriter, ast, group);

	                    reorderParameters(node, rewriter, group, ONEPARAM_ASSERTIONS, TWOPARAM_ASSERTIONS);
//	    				SimpleName newQualifier= ast.newSimpleName(ASSERTIONS);
//	    				Expression expression= assertexpression;
//	    				if (expression != null) {
//	    					ASTNodes.replaceButKeepComment(rewriter, expression, newQualifier, group);
//	    				}
	                    
	                    
	                    
	                    
	                    // Ändere den Qualifier (z.B. Assert.assertEquals -> Assertions.assertEquals)
	                    rewriter.set(node.getExpression(), SimpleName.IDENTIFIER_PROPERTY, "Assertions", group);
//	                    ASTNodes.replaceButKeepComment(rewriter, expression, newQualifier, group);

	                    // Passe Importe an
	                    addImportForAssertion(node.getName().getIdentifier(), ast, rewriter, group, importRewriter);
	                }
	            }

	            return super.visit(node);
	        }
	    });
	}


	private void addImportForAssertion(String assertionMethod, AST ast, ASTRewrite rewriter, TextEditGroup group, ImportRewrite importRewriter) {
	    String importToAdd = null;

	    switch (assertionMethod) {
	        case "assertEquals":
	        case "assertArrayEquals":
	        case "assertTrue":
	        case "assertFalse":
	        case "assertNull":
	        case "assertNotNull":
	            importToAdd = "org.junit.jupiter.api.Assertions";
	            break;
	        case "assumeTrue":
	        case "assumeFalse":
	        case "assumeNotNull":
	            importToAdd = "org.junit.jupiter.api.Assumptions";
	            break;
	        case "assertThat":
	            importToAdd = "org.hamcrest.MatcherAssert";
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
		// Add annotation
		ListRewrite modifiers= rewrite.getListRewrite(method, MethodDeclaration.MODIFIERS2_PROPERTY);
		MarkerAnnotation newMarkerAnnotation= ast.newMarkerAnnotation();
		newMarkerAnnotation.setTypeName(ast.newSimpleName(annotation));
		modifiers.insertFirst(newMarkerAnnotation, group);
		importRewrite.addImport("org.junit.jupiter.api." + annotation.substring(1));
	}

	private void addAnnotationToMethod(MethodDeclaration method, String annotation, ImportRewrite importRewrite,
			ASTRewrite rewrite, AST ast, TextEditGroup group) {
// Füge die Annotation zum Methodenkopf hinzu
		ListRewrite modifiers= rewrite.getListRewrite(method, MethodDeclaration.MODIFIERS2_PROPERTY);
		MarkerAnnotation newMarkerAnnotation= ast.newMarkerAnnotation();
		newMarkerAnnotation.setTypeName(ast.newSimpleName(annotation)); // annotation sollte nur den Namen enthalten, z.
																		// B. "Test"
		modifiers.insertFirst(newMarkerAnnotation, group);

// Import der Annotation hinzufügen
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
