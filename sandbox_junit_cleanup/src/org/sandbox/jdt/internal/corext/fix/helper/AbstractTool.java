/*******************************************************************************
 * Copyright (c) 2021, 2025 Carsten Hammer.
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

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.dom.ScopeAnalyzer;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.internal.corext.util.AnnotationUtils;
import org.sandbox.jdt.internal.corext.util.ASTNavigationUtils;
import org.sandbox.jdt.internal.corext.util.NamingUtils;
import org.sandbox.jdt.internal.corext.util.TypeCheckingUtils;

import static org.sandbox.jdt.internal.corext.fix.helper.JUnitConstants.*;

/**
 * Abstract base class for JUnit migration tools.
 * Provides common functionality for transforming JUnit 3/4 tests to JUnit 5.
 * Delegates to specialized helper classes for most operations.
 * 
 * @param <T> Type found in Visitor
 */
public abstract class AbstractTool<T> {

	/**
	 * Gets all variable names used in the scope of the given AST node.
	 * 
	 * @param node the AST node to analyze
	 * @return collection of variable names used in the node's scope
	 */
	public static Collection<String> getUsedVariableNames(ASTNode node) {
		CompilationUnit root = (CompilationUnit) node.getRoot();
		return new ScopeAnalyzer(root).getUsedVariableNames(node.getStartPosition(), node.getLength());
	}

	/**
	 * Extracts the class name from a field declaration's initializer.
	 * Delegates to {@link NamingUtils#extractClassNameFromField(FieldDeclaration)}.
	 * 
	 * @param field the field declaration to extract from
	 * @return the class name, or null if not found
	 */
	public String extractClassNameFromField(FieldDeclaration field) {
		return NamingUtils.extractClassNameFromField(field);
	}

	/**
	 * Extracts the fully qualified type name from a QualifiedType AST node.
	 * Delegates to {@link NamingUtils#extractQualifiedTypeName(QualifiedType)}.
	 * 
	 * @param qualifiedType the qualified type to extract from
	 * @return the fully qualified class name
	 */
	protected String extractQualifiedTypeName(QualifiedType qualifiedType) {
		return NamingUtils.extractQualifiedTypeName(qualifiedType);
	}

	/**
	 * Finds JUnit migration opportunities in the compilation unit.
	 * Implementations should scan for patterns that need to be migrated from JUnit 3/4 to JUnit 5.
	 * 
	 * @param fixcore the JUnit cleanup fix core
	 * @param compilationUnit the compilation unit to analyze
	 * @param operations set to collect rewrite operations
	 * @param nodesprocessed set of already processed AST nodes to avoid duplicates
	 */
	public abstract void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed);

	/**
	 * Gets the parent TypeDeclaration for the given AST node.
	 * 
	 * @param node the AST node to start from
	 * @return the enclosing TypeDeclaration, or null if none found
	 */
	protected TypeDeclaration getParentTypeDeclaration(ASTNode node) {
		return ASTNavigationUtils.getParentTypeDeclaration(node);
	}

	/**
	 * Gets a preview of the code before or after refactoring.
	 * Used to display examples in the Eclipse cleanup preferences UI.
	 * 
	 * @param afterRefactoring if true, returns the "after" preview; if false, returns the "before" preview
	 * @return a code snippet showing the transformation (formatted as Java source code)
	 */
	public abstract String getPreview(boolean afterRefactoring);

	/**
	 * Finds the type definition (TypeDeclaration or AnonymousClassDeclaration) for a field.
	 * Checks the field's initializer and type binding to locate the definition.
	 * 
	 * @param fieldDeclaration the field declaration to analyze
	 * @param cu the compilation unit containing the field
	 * @return the type definition node, or null if not found
	 */
	protected ASTNode getTypeDefinitionForField(FieldDeclaration fieldDeclaration, CompilationUnit cu) {
		return (ASTNode) fieldDeclaration.fragments().stream()
				.filter(VariableDeclarationFragment.class::isInstance)
				.map(VariableDeclarationFragment.class::cast)
				.map(fragment -> getTypeDefinitionFromFragment((VariableDeclarationFragment) fragment, cu))
				.filter(java.util.Objects::nonNull)
				.findFirst()
				.orElse(null);
	}

	private ASTNode getTypeDefinitionFromFragment(VariableDeclarationFragment fragment, CompilationUnit cu) {
		// Check initializer
		Expression initializer = fragment.getInitializer();
		if (initializer instanceof org.eclipse.jdt.core.dom.ClassInstanceCreation) {
			org.eclipse.jdt.core.dom.ClassInstanceCreation classInstanceCreation = (org.eclipse.jdt.core.dom.ClassInstanceCreation) initializer;

			// Check for anonymous class
			AnonymousClassDeclaration anonymousClass = classInstanceCreation.getAnonymousClassDeclaration();
			if (anonymousClass != null) {
				return anonymousClass;
			}

			// Check type binding
			ITypeBinding typeBinding = classInstanceCreation.resolveTypeBinding();
			return ASTNavigationUtils.findTypeDeclarationForBinding(typeBinding, cu);
		}

		// Check field type if no initialization is present
		IVariableBinding fieldBinding = fragment.resolveBinding();
		if (fieldBinding != null) {
			ITypeBinding fieldTypeBinding = fieldBinding.getType();
			return ASTNavigationUtils.findTypeDeclarationForBinding(fieldTypeBinding, cu);
		}

		return null;
	}

	/**
	 * Checks if a class has either a default constructor or no constructors at all.
	 * Used to determine if ExternalResource subclasses can be easily migrated.
	 * 
	 * @param classNode the class to check
	 * @return true if the class has a default constructor or no constructors
	 */
	protected boolean hasDefaultConstructorOrNoConstructor(TypeDeclaration classNode) {
		boolean hasConstructor = false;

		for (Object bodyDecl : classNode.bodyDeclarations()) {
			if (bodyDecl instanceof org.eclipse.jdt.core.dom.MethodDeclaration) {
				org.eclipse.jdt.core.dom.MethodDeclaration method = (org.eclipse.jdt.core.dom.MethodDeclaration) bodyDecl;
				if (method.isConstructor()) {
					hasConstructor = true;
					if (method.parameters().isEmpty() && method.getBody() != null
							&& method.getBody().statements().isEmpty()) {
						return true;
					}
				}
			}
		}
		return !hasConstructor;
	}

	/**
	 * Checks if a variable declaration fragment represents an anonymous class.
	 * 
	 * @param fragment the variable declaration fragment to check
	 * @return true if the fragment's initializer is an anonymous class
	 */
	public boolean isAnonymousClass(VariableDeclarationFragment fragment) {
		Expression initializer = fragment.getInitializer();
		return initializer instanceof org.eclipse.jdt.core.dom.ClassInstanceCreation
				&& ((org.eclipse.jdt.core.dom.ClassInstanceCreation) initializer).getAnonymousClassDeclaration() != null;
	}

	/**
	 * Checks if the given type binding directly matches ExternalResource.
	 * 
	 * @param fieldTypeBinding the type binding to check
	 * @return true if the type is exactly ExternalResource
	 */
	protected boolean isDirect(ITypeBinding fieldTypeBinding) {
		return ORG_JUNIT_RULES_EXTERNAL_RESOURCE.equals(fieldTypeBinding.getQualifiedName());
	}

	/**
	 * Checks if the given type binding directly extends ExternalResource.
	 * 
	 * @param binding the type binding to check
	 * @return true if the type's superclass is ExternalResource
	 */
	protected boolean isDirectlyExtendingExternalResource(ITypeBinding binding) {
		ITypeBinding superclass = binding.getSuperclass();
		return superclass != null && ORG_JUNIT_RULES_EXTERNAL_RESOURCE.equals(superclass.getQualifiedName());
	}

	/**
	 * Checks if the given type binding is or extends ExternalResource.
	 * 
	 * @param typeBinding the type binding to check
	 * @param typeToLookup the fully qualified type name to look for
	 * @return true if the type is or extends the specified type
	 */
	protected boolean isExternalResource(ITypeBinding typeBinding, String typeToLookup) {
		return TypeCheckingUtils.isTypeOrSubtype(typeBinding, typeToLookup);
	}

	/**
	 * Checks if a field is annotated with the specified annotation.
	 * 
	 * @param field the field declaration to check
	 * @param annotationClass the fully qualified annotation class name
	 * @return true if the field has the annotation
	 */
	protected boolean isFieldAnnotatedWith(FieldDeclaration field, String annotationClass) {
		return AnnotationUtils.isFieldAnnotatedWith(field, annotationClass);
	}

	/**
	 * Checks if a field has the static modifier.
	 * 
	 * @param field the field declaration to check
	 * @return true if the field is static
	 */
	protected boolean isFieldStatic(FieldDeclaration field) {
		return AnnotationUtils.isFieldStatic(field);
	}

	/**
	 * Checks if an expression has a String type.
	 * 
	 * @param expression the expression to check
	 * @param classType the class type (String.class)
	 * @return true if the expression resolves to String type
	 */
	protected boolean isStringType(Expression expression, Class<String> classType) {
		return TypeCheckingUtils.isStringType(expression, classType);
	}

	/**
	 * Checks if subtype is a subtype of or implements supertype.
	 * 
	 * @param subtype the potential subtype binding
	 * @param supertype the supertype binding
	 * @return true if subtype is a subtype of or implements supertype
	 */
	protected boolean isSubtypeOf(ITypeBinding subtype, ITypeBinding supertype) {
		return TypeCheckingUtils.isSubtypeOf(subtype, supertype);
	}

	/**
	 * Checks if the given type binding matches or is a subtype of the specified qualified name.
	 * Traverses the superclass hierarchy to find a match.
	 * 
	 * @param typeBinding the type binding to check
	 * @param qualifiedName the fully qualified type name to match
	 * @return true if the type or any of its supertypes matches the qualified name
	 */
	protected boolean isTypeOrSubtype(ITypeBinding typeBinding, String qualifiedName) {
		return TypeCheckingUtils.isTypeOrSubtype(typeBinding, qualifiedName);
	}

	/**
	 * Modifies a class that extends ExternalResource to use JUnit 5 extensions instead.
	 * Delegates to {@link ExternalResourceRefactorer#modifyExternalResourceClass}.
	 * 
	 * @param node the type declaration to modify
	 * @param field the field declaration with ExternalResource
	 * @param fieldStatic whether the field is static (affects callback type)
	 * @param rewriter the AST rewriter
	 * @param ast the AST instance
	 * @param group the text edit group
	 * @param importRewriter the import rewriter
	 */
	protected void modifyExternalResourceClass(TypeDeclaration node, FieldDeclaration field, boolean fieldStatic,
			ASTRewrite rewriter, AST ast, TextEditGroup group, ImportRewrite importRewriter) {
		ExternalResourceRefactorer.modifyExternalResourceClass(node, field, fieldStatic, rewriter, ast, group,
				importRewriter);
	}

	/**
	 * Process method for compatibility with older tools.
	 * Handles JUnit 4 @Rule ExternalResource fields.
	 * 
	 * @param node the annotation node
	 * @param jproject the Java project
	 * @param rewrite the AST rewriter
	 * @param ast the AST instance
	 * @param group the text edit group
	 * @param importRewriter the import rewriter
	 * @param cu the compilation unit
	 * @param className the class name
	 */
	public void process(Annotation node, IJavaProject jproject, ASTRewrite rewrite, AST ast, TextEditGroup group,
			ImportRewrite importRewriter, CompilationUnit cu, String className) {
		if (!ORG_JUNIT_RULE.equals(node.resolveTypeBinding().getQualifiedName())) {
			return;
		}
		FieldDeclaration field = org.eclipse.jdt.internal.corext.dom.ASTNodes.getParent(node, FieldDeclaration.class);
		ITypeBinding fieldTypeBinding = ((VariableDeclarationFragment) field.fragments().get(0)).resolveBinding()
				.getType();
		if (!isExternalResource(fieldTypeBinding, ORG_JUNIT_RULES_EXTERNAL_RESOURCE)
				|| fieldTypeBinding.isAnonymous()) {
			return;
		}
		if (isDirect(fieldTypeBinding)) {
			rewrite.remove(field, group);
			importRewriter.removeImport(ORG_JUNIT_RULE);
		}
		ExternalResourceRefactorer.addExtendWithAnnotation(rewrite, ast, group, importRewriter, className, field);
		importRewriter.removeImport(ORG_JUNIT_RULES_EXTERNAL_RESOURCE);
	}

	/**
	 * Processes a JUnit migration by applying the necessary AST rewrites.
	 * Implementations should transform the matched pattern into JUnit 5 compatible code.
	 * 
	 * @param group the text edit group for tracking changes
	 * @param rewriter the AST rewriter
	 * @param ast the AST instance
	 * @param importRewriter the import rewriter
	 * @param junitHolder the holder containing JUnit migration information
	 */
	abstract void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder);

	/**
	 * Refactors an anonymous ExternalResource class to implement JUnit 5 callback interfaces.
	 * Delegates to {@link ExternalResourceRefactorer#refactorAnonymousClassToImplementCallbacks}.
	 * 
	 * @param anonymousClass the anonymous class declaration to refactor
	 * @param fieldDeclaration the field containing the anonymous class
	 * @param fieldStatic whether the field is static
	 * @param rewriter the AST rewriter
	 * @param ast the AST instance
	 * @param group the text edit group
	 * @param importRewriter the import rewriter
	 */
	protected void refactorAnonymousClassToImplementCallbacks(AnonymousClassDeclaration anonymousClass,
			FieldDeclaration fieldDeclaration, boolean fieldStatic, ASTRewrite rewriter, AST ast, TextEditGroup group,
			ImportRewrite importRewriter) {
		ExternalResourceRefactorer.refactorAnonymousClassToImplementCallbacks(anonymousClass, fieldDeclaration,
				fieldStatic, rewriter, ast, group, importRewriter);
	}

	/**
	 * Refactors TestName field usage in a class.
	 * Delegates to {@link TestNameRefactorer#refactorTestnameInClass}.
	 * 
	 * @param group the text edit group
	 * @param rewriter the AST rewriter
	 * @param ast the AST instance
	 * @param importRewrite the import rewriter
	 * @param node the TestName field declaration to replace
	 */
	protected void refactorTestnameInClass(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewrite, FieldDeclaration node) {
		TestNameRefactorer.refactorTestnameInClass(group, rewriter, ast, importRewrite, node);
	}

	/**
	 * Refactors TestName field usage in a class and all its subclasses.
	 * Delegates to {@link TestNameRefactorer#refactorTestnameInClassAndSubclasses}.
	 * 
	 * @param group the text edit group
	 * @param rewriter the AST rewriter
	 * @param ast the AST instance
	 * @param importRewrite the import rewriter
	 * @param node the TestName field declaration to replace
	 */
	protected void refactorTestnameInClassAndSubclasses(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewrite, FieldDeclaration node) {
		TestNameRefactorer.refactorTestnameInClassAndSubclasses(group, rewriter, ast, importRewrite, node);
	}

	/**
	 * Reorders parameters in a method invocation to match JUnit 5 assertion parameter order.
	 * Delegates to {@link AssertionRefactorer#reorderParameters}.
	 * 
	 * @param node the method invocation to reorder
	 * @param rewriter the AST rewriter
	 * @param group the text edit group
	 * @param oneparam assertion methods with one value parameter
	 * @param twoparam assertion methods with two value parameters
	 */
	public void reorderParameters(MethodInvocation node, ASTRewrite rewriter, TextEditGroup group, Set<String> oneparam,
			Set<String> twoparam) {
		AssertionRefactorer.reorderParameters(node, rewriter, group, oneparam, twoparam);
	}

	/**
	 * Standard helper for processing found nodes in the common pattern.
	 * Creates a JunitHolder, stores the node, adds it to the data holder,
	 * and creates a rewrite operation.
	 * 
	 * @param fixcore the cleanup fix core
	 * @param operations the set of operations to add to
	 * @param node the AST node that was found
	 * @param dataHolder the reference holder for storing data
	 * @return false to continue visiting
	 */
	protected boolean addStandardRewriteOperation(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, ASTNode node,
			ReferenceHolder<Integer, JunitHolder> dataHolder) {
		JunitHolder mh = new JunitHolder();
		mh.minv = node;
		dataHolder.put(dataHolder.size(), mh);
		operations.add(fixcore.rewrite(dataHolder));
		return false;
	}

	/**
	 * Handles import declaration changes for migrating JUnit 4 to JUnit 5.
	 * Delegates to {@link ImportHelper#changeImportDeclaration}.
	 * 
	 * @param node the import declaration to change
	 * @param importRewriter the import rewriter to use
	 * @param sourceClass the JUnit 4 fully qualified class name
	 * @param targetClass the JUnit 5 fully qualified class name
	 */
	protected void changeImportDeclaration(ImportDeclaration node, ImportRewrite importRewriter, String sourceClass,
			String targetClass) {
		ImportHelper.changeImportDeclaration(node, importRewriter, sourceClass, targetClass);
	}

	/**
	 * Applies the JUnit migration rewrite to the compilation unit.
	 * Delegates to the abstract process2Rewrite method for actual transformation.
	 * 
	 * @param upp the JUnit cleanup fix core
	 * @param hit the reference holder containing migration information
	 * @param cuRewrite the compilation unit rewrite
	 * @param group the text edit group
	 */
	public void rewrite(JUnitCleanUpFixCore upp, ReferenceHolder<Integer, JunitHolder> hit,
			CompilationUnitRewrite cuRewrite, TextEditGroup group) {
		ASTRewrite rewriter = cuRewrite.getASTRewrite();
		AST ast = cuRewrite.getRoot().getAST();
		ImportRewrite importRewriter = cuRewrite.getImportRewrite();
		JunitHolder junitHolder = hit.get(hit.size() - 1);
		process2Rewrite(group, rewriter, ast, importRewriter, junitHolder);
		hit.remove(hit.size() - 1);
	}

	/**
	 * Adds the @ExtendWith annotation to a class for JUnit 5 extension integration.
	 * Delegates to {@link ExternalResourceRefactorer#addExtendWithAnnotation}.
	 * 
	 * @param rewrite the AST rewriter
	 * @param ast the AST instance
	 * @param group the text edit group
	 * @param importRewriter the import rewriter
	 * @param className the simple name of the extension class
	 * @param field the field that triggered the need for this annotation
	 */
	protected void addExtendWithAnnotation(ASTRewrite rewrite, AST ast, TextEditGroup group,
			ImportRewrite importRewriter, String className, FieldDeclaration field) {
		ExternalResourceRefactorer.addExtendWithAnnotation(rewrite, ast, group, importRewriter, className, field);
	}

	/**
	 * Adds an import to the class using ImportHelper.
	 *
	 * @param typeName  a fully qualified name of a type
	 * @param cuRewrite CompilationUnitRewrite
	 * @param ast       AST
	 * @return simple name of a class if the import was added and fully qualified
	 *         name if there was a conflict
	 */
	protected org.eclipse.jdt.core.dom.Name addImport(String typeName, final CompilationUnitRewrite cuRewrite,
			AST ast) {
		return ImportHelper.addImport(typeName, cuRewrite, ast);
	}
}
