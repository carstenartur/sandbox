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

import java.util.Collection;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.internal.corext.dom.AbortSearchException;
import org.eclipse.jdt.internal.corext.dom.ScopeAnalyzer;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;

/**
 * @param <T> Type found in Visitor
 */
public abstract class AbstractTool<T> {

	protected static final String ORG_JUNIT_JUPITER_API_EXTENSION_EXTENSION_CONTEXT= "org.junit.jupiter.api.extension.ExtensionContext";
	protected static final String ORG_JUNIT_RULE= "org.junit.Rule";
	protected static final String ORG_JUNIT_RULES_EXTERNAL_RESOURCE= "org.junit.rules.ExternalResource";
	protected static final String ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_EACH_CALLBACK= "org.junit.jupiter.api.extension.BeforeEachCallback";
	protected static final String ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_EACH_CALLBACK= "org.junit.jupiter.api.extension.AfterEachCallback";
	protected static final String ORG_JUNIT_JUPITER_API_EXTENSION_EXTEND_WITH= "org.junit.jupiter.api.extension.ExtendWith";
	protected static final String ORG_JUNIT_AFTER= "org.junit.After";
	protected static final String ORG_JUNIT_JUPITER_API_AFTER_EACH= "org.junit.jupiter.api.AfterEach";
	protected static final String AFTER_EACH= "AfterEach";
	protected static final String ORG_JUNIT_BEFORE= "org.junit.Before";
	protected static final String BEFORE_EACH= "BeforeEach";
	protected static final String ORG_JUNIT_AFTERCLASS= "org.junit.AfterClass";
	protected static final String ORG_JUNIT_JUPITER_API_AFTER_ALL= "org.junit.jupiter.api.AfterAll";
	protected static final String AFTER_ALL= "AfterAll";
	protected static final String ASSERTIONS= "Assertions";
	protected static final String ORG_JUNIT_JUPITER_API_ASSERTIONS= "org.junit.jupiter.api.Assertions";
	protected static final String ORG_JUNIT_ASSERT= "org.junit.Assert";
	protected static final String ORG_JUNIT_BEFORECLASS= "org.junit.BeforeClass";
	protected static final String ORG_JUNIT_JUPITER_API_BEFORE_ALL= "org.junit.jupiter.api.BeforeAll";
	protected static final String BEFORE_ALL= "BeforeAll";
	protected static final String ORG_JUNIT_IGNORE= "org.junit.Ignore";
	protected static final String ORG_JUNIT_JUPITER_DISABLED= "org.junit.jupiter.api.Disabled";
	protected static final String DISABLED= "Disabled";
	protected static final String ORG_JUNIT_JUPITER_API_IO_TEMP_DIR= "org.junit.jupiter.api.io.TempDir";
	protected static final String ORG_JUNIT_RULES_TEMPORARY_FOLDER= "org.junit.rules.TemporaryFolder";
	protected static final String ORG_JUNIT_JUPITER_API_TEST_INFO= "org.junit.jupiter.api.TestInfo";
	protected static final String ORG_JUNIT_RULES_TEST_NAME= "org.junit.rules.TestName";
	protected static final String ORG_JUNIT_JUPITER_API_BEFORE_EACH= "org.junit.jupiter.api.BeforeEach";
	protected static final String ORG_JUNIT_PLATFORM_SUITE_API_SELECT_CLASSES= "org.junit.platform.suite.api.SelectClasses";
	protected static final String SELECT_CLASSES= "SelectClasses";
	protected static final String ORG_JUNIT_RUNWITH= "org.junit.runner.RunWith";
	protected static final String ORG_JUNIT_JUPITER_SUITE= "org.junit.platform.suite.api.Suite";
	protected static final String SUITE= "Suite";
	protected static final String ORG_JUNIT_SUITE= "org.junit.runners.Suite";
	protected static final String ORG_JUNIT_SUITE_SUITECLASSES= "org.junit.runners.Suite.SuiteClasses";
	protected static final String ORG_JUNIT_TEST= "org.junit.Test";
	protected static final String ORG_JUNIT_JUPITER_TEST= "org.junit.jupiter.api.Test";
	protected static final String TEST= "Test";

	protected static boolean isOfType(ITypeBinding typeBinding, String typename) {
		if (typeBinding == null) {
			throw new AbortSearchException();
		}
		if (typeBinding.isArray()) {
			typeBinding= typeBinding.getElementType();
		}
		return typeBinding.getQualifiedName().equals(typename);
	}

	public abstract void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed);

	public abstract void rewrite(JUnitCleanUpFixCore useExplicitEncodingFixCore, T holder,
			CompilationUnitRewrite cuRewrite, TextEditGroup group);

	/**
	 * Adds an import to the class. This method should be used for every class
	 * reference added to the generated code.
	 *
	 * @param typeName  a fully qualified name of a type
	 * @param cuRewrite CompilationUnitRewrite
	 * @param ast       AST
	 * @return simple name of a class if the import was added and fully qualified
	 *         name if there was a conflict
	 */
	protected Name addImport(String typeName, final CompilationUnitRewrite cuRewrite, AST ast) {
		String importedName= cuRewrite.getImportRewrite().addImport(typeName);
		return ast.newName(importedName);
	}

	public abstract String getPreview(boolean afterRefactoring);

	protected boolean hasDefaultConstructorOrNoConstructor(TypeDeclaration classNode) {
		boolean hasConstructor= false;
		for (Object bodyDecl : classNode.bodyDeclarations()) {
			if (bodyDecl instanceof MethodDeclaration) {
				MethodDeclaration method= (MethodDeclaration) bodyDecl;
				if (method.isConstructor()) {
					hasConstructor= true;
					if (method.parameters().isEmpty() && method.getBody() != null
							&& method.getBody().statements().isEmpty()) {
						return true;
					}
				}
			}
		}
		return !hasConstructor;
	}

	protected boolean isExternalResource(ITypeBinding typeBinding) {
		while (typeBinding != null) {
			if (ORG_JUNIT_RULES_EXTERNAL_RESOURCE.equals(typeBinding.getQualifiedName())) {
				return true;
			}
			typeBinding= typeBinding.getSuperclass();
		}
		return false;
	}

	protected String extractQualifiedTypeName(QualifiedType qualifiedType) {
		StringBuilder fullClassName= new StringBuilder(qualifiedType.getName().getFullyQualifiedName());
		for (Type qualifier= qualifiedType
				.getQualifier(); qualifier instanceof QualifiedType; qualifier= ((QualifiedType) qualifier)
						.getQualifier()) {
			fullClassName.insert(0, ".");
			fullClassName.insert(0, ((QualifiedType) qualifier).getName().getFullyQualifiedName());
		}
		return fullClassName.toString();
	}

	public static Collection<String> getUsedVariableNames(ASTNode node) {
		CompilationUnit root= (CompilationUnit) node.getRoot();
		return new ScopeAnalyzer(root).getUsedVariableNames(node.getStartPosition(), node.getLength());
	}
}
