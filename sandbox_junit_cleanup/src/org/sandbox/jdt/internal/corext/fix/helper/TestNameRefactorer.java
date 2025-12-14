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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.util.ASTNavigationUtils;

import static org.sandbox.jdt.internal.corext.fix.helper.JUnitConstants.*;

/**
 * Helper class for refactoring JUnit 4 TestName rule to JUnit 5 TestInfo parameter.
 * Replaces @Rule TestName fields with a @BeforeEach method that captures test information.
 */
public final class TestNameRefactorer {

	// Private constructor to prevent instantiation
	private TestNameRefactorer() {
		throw new UnsupportedOperationException("Utility class");
	}

	/**
	 * Refactors TestName field usage in a class and optionally in its subclasses.
	 * Replaces JUnit 4 @Rule TestName with a @BeforeEach method that captures test info.
	 * 
	 * @param group the text edit group
	 * @param rewriter the AST rewriter
	 * @param ast the AST instance
	 * @param importRewrite the import rewriter
	 * @param node the TestName field declaration to replace
	 */
	public static void refactorTestnameInClass(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewrite, FieldDeclaration node) {
		if (node == null || rewriter == null || ast == null || importRewrite == null) {
			return;
		}

		// Remove the old @Rule TestName field
		rewriter.remove(node, group);

		// Add new infrastructure: @BeforeEach init method and private String testName field
		TypeDeclaration parentClass = ASTNodes.getParent(node, TypeDeclaration.class);
		addBeforeEachInitMethod(parentClass, rewriter, group);
		addTestNameField(parentClass, rewriter, group);

		// Update method references from testNameField.getMethodName() to just testName
		updateMethodReferences(parentClass, ast, rewriter, group);

		// Update imports
		importRewrite.addImport(ORG_JUNIT_JUPITER_API_TEST_INFO);
		importRewrite.addImport(ORG_JUNIT_JUPITER_API_BEFORE_EACH);
		importRewrite.removeImport(ORG_JUNIT_RULE);
		importRewrite.removeImport(ORG_JUNIT_RULES_TEST_NAME);
	}

	/**
	 * Refactors TestName usage in a class and all its subclasses.
	 * 
	 * @param group the text edit group
	 * @param rewriter the AST rewriter
	 * @param ast the AST instance
	 * @param importRewrite the import rewriter
	 * @param node the TestName field declaration to replace
	 */
	public static void refactorTestnameInClassAndSubclasses(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewrite, FieldDeclaration node) {
		refactorTestnameInClass(group, rewriter, ast, importRewrite, node);

		TypeDeclaration parentClass = ASTNodes.getParent(node, TypeDeclaration.class);
		if (parentClass == null) {
			return;
		}
		ITypeBinding typeBinding = parentClass.resolveBinding();
		List<ITypeBinding> subclasses = getAllSubclasses(typeBinding);

		for (ITypeBinding subclassBinding : subclasses) {
			IType subclassType = (IType) subclassBinding.getJavaElement();

			CompilationUnit subclassUnit = ASTNavigationUtils.parseCompilationUnit(subclassType.getCompilationUnit());
			subclassUnit.accept(new ASTVisitor() {
				@Override
				public boolean visit(TypeDeclaration subclassNode) {
					if (subclassNode.resolveBinding().equals(subclassBinding)) {
						refactorTestnameInClass(group, rewriter, subclassNode.getAST(), importRewrite, node);
					}
					return false;
				}
			});
		}
	}

	/**
	 * Adds a @BeforeEach init method that captures the test name from TestInfo.
	 * 
	 * @param parentClass the class to add the method to
	 * @param rewriter the AST rewriter
	 * @param group the text edit group
	 */
	public static void addBeforeEachInitMethod(TypeDeclaration parentClass, ASTRewrite rewriter, TextEditGroup group) {
		AST ast = parentClass.getAST();

		MethodDeclaration methodDeclaration = createInitMethod(ast);
		MarkerAnnotation beforeEachAnnotation = createBeforeEachAnnotation(ast);

		addMethodToClass(parentClass, methodDeclaration, beforeEachAnnotation, rewriter, group);
	}

	/**
	 * Adds a private String field named 'testName' to the class.
	 * Used when migrating JUnit 4 TestName rule to JUnit 5 TestInfo parameter.
	 * 
	 * @param parentClass the class to add the field to
	 * @param rewriter the AST rewriter
	 * @param group the text edit group
	 */
	public static void addTestNameField(TypeDeclaration parentClass, ASTRewrite rewriter, TextEditGroup group) {
		AST ast = parentClass.getAST();
		VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
		fragment.setName(ast.newSimpleName(TEST_NAME));

		FieldDeclaration fieldDeclaration = ast.newFieldDeclaration(fragment);
		fieldDeclaration.setType(ast.newSimpleType(ast.newName("String")));
		fieldDeclaration.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));

		ListRewrite listRewrite = rewriter.getListRewrite(parentClass, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
		listRewrite.insertFirst(fieldDeclaration, group);
	}

	/**
	 * Updates method references from testNameField.getMethodName() to just testName.
	 * 
	 * @param parentClass the class containing methods to update
	 * @param ast the AST instance
	 * @param rewriter the AST rewriter
	 * @param group the text edit group
	 */
	public static void updateMethodReferences(TypeDeclaration parentClass, AST ast, ASTRewrite rewriter,
			TextEditGroup group) {
		for (MethodDeclaration method : parentClass.getMethods()) {
			if (method.getBody() != null) {
				method.getBody().accept(new ASTVisitor() {
					@Override
					public boolean visit(MethodInvocation node) {
						if (node.getExpression() != null && ORG_JUNIT_RULES_TEST_NAME
								.equals(node.getExpression().resolveTypeBinding().getQualifiedName())) {
							SimpleName newFieldAccess = ast.newSimpleName(TEST_NAME);
							rewriter.replace(node, newFieldAccess, group);
						}
						return super.visit(node);
					}
				});
			}
		}
	}

	/**
	 * Gets all direct and indirect subclasses of the given type.
	 * Uses the JDT type hierarchy to discover subclasses in the project.
	 * 
	 * @param typeBinding the type binding to find subclasses for
	 * @return list of type bindings for all subclasses
	 */
	public static List<ITypeBinding> getAllSubclasses(ITypeBinding typeBinding) {
		List<ITypeBinding> subclasses = new ArrayList<>();

		try {
			// Create the corresponding IType of the given ITypeBinding
			IType type = (IType) typeBinding.getJavaElement();

			// Create the type hierarchy for the given type within the project (null uses entire project)
			ITypeHierarchy typeHierarchy = type.newTypeHierarchy(null);

			// Iterate through all direct and indirect subtypes and add them to the list
			for (IType subtype : typeHierarchy.getAllSubtypes(type)) {
				ITypeBinding subtypeBinding = subtype.getAdapter(ITypeBinding.class);
				if (subtypeBinding != null) {
					subclasses.add(subtypeBinding);
				}
			}
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
		return subclasses;
	}

	/**
	 * Creates the init method that assigns testInfo.getDisplayName() to this.testName.
	 * 
	 * @param ast the AST instance
	 * @return the method declaration
	 */
	private static MethodDeclaration createInitMethod(AST ast) {
		MethodDeclaration methodDeclaration = ast.newMethodDeclaration();
		methodDeclaration.setName(ast.newSimpleName("init"));
		methodDeclaration.setReturnType2(ast.newPrimitiveType(PrimitiveType.VOID));

		// Add parameter: TestInfo testInfo
		SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
		param.setType(ast.newSimpleType(ast.newName("TestInfo")));
		param.setName(ast.newSimpleName("testInfo"));
		methodDeclaration.parameters().add(param);

		// Create method body
		Block body = createInitMethodBody(ast);
		methodDeclaration.setBody(body);

		return methodDeclaration;
	}

	/**
	 * Creates the body of the init method: this.testName = testInfo.getDisplayName();
	 * 
	 * @param ast the AST instance
	 * @return the method body block
	 */
	private static Block createInitMethodBody(AST ast) {
		Block body = ast.newBlock();

		// Create assignment: this.testName = testInfo.getDisplayName()
		Assignment assignment = ast.newAssignment();

		// Left side: this.testName
		FieldAccess fieldAccess = ast.newFieldAccess();
		fieldAccess.setExpression(ast.newThisExpression());
		fieldAccess.setName(ast.newSimpleName(TEST_NAME));
		assignment.setLeftHandSide(fieldAccess);

		// Right side: testInfo.getDisplayName()
		MethodInvocation methodInvocation = ast.newMethodInvocation();
		methodInvocation.setExpression(ast.newSimpleName("testInfo"));
		methodInvocation.setName(ast.newSimpleName("getDisplayName"));
		assignment.setRightHandSide(methodInvocation);

		body.statements().add(ast.newExpressionStatement(assignment));
		return body;
	}

	/**
	 * Creates a @BeforeEach annotation.
	 * 
	 * @param ast the AST instance
	 * @return the annotation
	 */
	private static MarkerAnnotation createBeforeEachAnnotation(AST ast) {
		MarkerAnnotation annotation = ast.newMarkerAnnotation();
		annotation.setTypeName(ast.newName("BeforeEach"));
		return annotation;
	}

	/**
	 * Adds a method with its annotation to a class.
	 * 
	 * @param parentClass the class to add to
	 * @param method the method to add
	 * @param annotation the annotation to add to the method
	 * @param rewriter the AST rewriter
	 * @param group the text edit group
	 */
	private static void addMethodToClass(TypeDeclaration parentClass, MethodDeclaration method,
			MarkerAnnotation annotation, ASTRewrite rewriter, TextEditGroup group) {
		// Add method to class
		ListRewrite classBodyRewrite = rewriter.getListRewrite(parentClass, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
		classBodyRewrite.insertFirst(method, group);

		// Add annotation to method
		ListRewrite modifierRewrite = rewriter.getListRewrite(method, MethodDeclaration.MODIFIERS2_PROPERTY);
		modifierRewrite.insertFirst(annotation, group);
	}
}
