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

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.util.AnnotationUtils;
import org.sandbox.jdt.internal.corext.util.ASTNavigationUtils;
import org.sandbox.jdt.internal.corext.util.NamingUtils;

import static org.sandbox.jdt.internal.corext.fix.helper.JUnitConstants.*;

/**
 * Helper class for refactoring JUnit 4 ExternalResource to JUnit 5 lifecycle callbacks.
 * Handles transformation of ExternalResource classes and anonymous instances.
 */
public final class ExternalResourceRefactorer {

	// Private constructor to prevent instantiation
	private ExternalResourceRefactorer() {
		throw new UnsupportedOperationException("Utility class");
	}

	/**
	 * Modifies a class that extends ExternalResource to use JUnit 5 extensions instead.
	 * 
	 * @param node the type declaration to modify
	 * @param field the field declaration with ExternalResource
	 * @param fieldStatic whether the field is static (affects callback type)
	 * @param rewriter the AST rewriter
	 * @param ast the AST instance
	 * @param group the text edit group
	 * @param importRewriter the import rewriter
	 */
	public static void modifyExternalResourceClass(TypeDeclaration node, FieldDeclaration field, boolean fieldStatic,
			ASTRewrite rewriter, AST ast, TextEditGroup group, ImportRewrite importRewriter) {
		if (!shouldProcessNode(node)) {
			return;
		}

		CallbackConfig callbackConfig = determineCallbackConfig(fieldStatic);

		if (field != null) {
			processExternalResourceField(field, rewriter, ast, group, importRewriter);
		}

		if (isDirectlyExtendingExternalResource(node.resolveBinding())) {
			refactorToImplementCallbacks(node, rewriter, ast, group, importRewriter, callbackConfig.beforeCallback,
					callbackConfig.afterCallback, callbackConfig.importBeforeCallback,
					callbackConfig.importAfterCallback);
		}

		LifecycleMethodAdapter.updateLifecycleMethodsInClass(node, rewriter, ast, group, importRewriter, METHOD_BEFORE,
				METHOD_AFTER, fieldStatic ? METHOD_BEFORE_ALL : METHOD_BEFORE_EACH,
				fieldStatic ? METHOD_AFTER_ALL : METHOD_AFTER_EACH);
	}

	/**
	 * Processes an ExternalResource field by removing JUnit 4 annotations and adding JUnit 5 equivalents.
	 * 
	 * @param field the field to process
	 * @param rewriter the AST rewriter
	 * @param ast the AST instance
	 * @param group the text edit group
	 * @param importRewriter the import rewriter
	 */
	public static void processExternalResourceField(FieldDeclaration field, ASTRewrite rewriter, AST ast,
			TextEditGroup group, ImportRewrite importRewriter) {
		String ruleAnnotation = null;

		if (AnnotationUtils.isAnnotatedWith(field, ORG_JUNIT_RULE)
				&& isExternalResource(field, ORG_JUNIT_RULES_EXTERNAL_RESOURCE)) {
			ruleAnnotation = ORG_JUNIT_RULE;
		} else if (AnnotationUtils.isAnnotatedWith(field, ORG_JUNIT_CLASS_RULE)
				&& isExternalResource(field, ORG_JUNIT_RULES_EXTERNAL_RESOURCE)) {
			ruleAnnotation = ORG_JUNIT_CLASS_RULE;
		}

		if (ruleAnnotation != null) {
			removeRuleAnnotation(field, rewriter, group, importRewriter, ruleAnnotation);
			addRegisterExtensionAnnotation(field, rewriter, ast, importRewriter, group);
			ITypeBinding fieldType = ((VariableDeclarationFragment) field.fragments().get(0)).resolveBinding()
					.getType();
			adaptExternalResourceHierarchy(fieldType, rewriter, ast, importRewriter, group);
		}
	}

	/**
	 * Adapts the superclass hierarchy for types extending ExternalResource.
	 * Walks up the inheritance chain, transforming each type to use JUnit 5 extensions
	 * until reaching ExternalResource itself.
	 * 
	 * @param typeBinding the type binding to start from
	 * @param rewrite the AST rewriter
	 * @param ast the AST instance
	 * @param importRewrite the import rewriter
	 * @param group the text edit group
	 */
	public static void adaptExternalResourceHierarchy(ITypeBinding typeBinding, ASTRewrite rewrite, AST ast,
			ImportRewrite importRewrite, TextEditGroup group) {
		while (typeBinding != null) {
			// Stop when we reach ExternalResource itself
			if (ORG_JUNIT_RULES_EXTERNAL_RESOURCE.equals(typeBinding.getQualifiedName())) {
				break;
			}

			// Process types that extend ExternalResource
			if (isExternalResource(typeBinding, ORG_JUNIT_RULES_EXTERNAL_RESOURCE)) {
				TypeDeclaration typeDecl = ASTNavigationUtils.findTypeDeclarationInProject(typeBinding);
				if (typeDecl != null) {
					adaptTypeDeclaration(typeDecl, rewrite, ast, importRewrite, group);
				}
			}

			typeBinding = typeBinding.getSuperclass();
		}
	}

	/**
	 * Adapts a type declaration that extends ExternalResource to use JUnit 5 lifecycle callbacks.
	 * Removes the ExternalResource superclass and updates lifecycle methods (before/after).
	 * 
	 * @param typeDecl the type declaration to adapt
	 * @param globalRewrite the global AST rewriter (may be different from typeDecl's AST)
	 * @param ast the AST instance
	 * @param importRewrite the import rewriter
	 * @param group the text edit group
	 */
	public static void adaptTypeDeclaration(TypeDeclaration typeDecl, ASTRewrite globalRewrite, AST ast,
			ImportRewrite importRewrite, TextEditGroup group) {
		// Create separate rewriters if the type declaration is in a different compilation unit
		ASTRewrite rewriteToUse = getASTRewrite(typeDecl, ast, globalRewrite);
		ImportRewrite importRewriteToUse = getImportRewrite(typeDecl, ast, importRewrite);

		// Remove ExternalResource superclass
		removeSuperclassType(typeDecl, rewriteToUse, group);

		// Update lifecycle methods: before() -> beforeEach(), after() -> afterEach()
		LifecycleMethodAdapter.updateLifecycleMethodsInClass(typeDecl, rewriteToUse, ast, group, importRewriteToUse,
				METHOD_BEFORE, METHOD_AFTER, METHOD_BEFORE_EACH, METHOD_AFTER_EACH);

		// Add required JUnit 5 callback imports
		importRewriteToUse.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_EACH_CALLBACK);
		importRewriteToUse.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_EACH_CALLBACK);

		// If we created a separate rewriter, commit the change
		if (rewriteToUse != globalRewrite) {
			DocumentHelper.createChangeForRewrite(ASTNavigationUtils.findCompilationUnit(typeDecl), rewriteToUse);
		}
	}

	/**
	 * Refactors an anonymous ExternalResource class to implement JUnit 5 callback interfaces.
	 * Converts the anonymous class to a named nested class with before/after callback methods.
	 * 
	 * @param anonymousClass the anonymous class declaration to refactor
	 * @param fieldDeclaration the field containing the anonymous class
	 * @param fieldStatic whether the field is static
	 * @param rewriter the AST rewriter
	 * @param ast the AST instance
	 * @param group the text edit group
	 * @param importRewriter the import rewriter
	 */
	public static void refactorAnonymousClassToImplementCallbacks(AnonymousClassDeclaration anonymousClass,
			FieldDeclaration fieldDeclaration, boolean fieldStatic, ASTRewrite rewriter, AST ast, TextEditGroup group,
			ImportRewrite importRewriter) {

		if (anonymousClass == null) {
			return;
		}

		// Access the surrounding ClassInstanceCreation
		ASTNode parent = anonymousClass.getParent();
		if (parent instanceof ClassInstanceCreation) {
			ClassInstanceCreation classInstanceCreation = (ClassInstanceCreation) parent;
			ensureClassInstanceRewrite(classInstanceCreation, rewriter, importRewriter, group);

			String fieldName = NamingUtils.extractFieldName(fieldDeclaration);
			String nestedClassName = NamingUtils.generateUniqueNestedClassName(anonymousClass, fieldName);
			TypeDeclaration nestedClass = createNestedClassFromAnonymous(anonymousClass, nestedClassName, fieldStatic,
					rewriter, ast, importRewriter, group);

			replaceFieldWithExtensionDeclaration(classInstanceCreation, nestedClassName, fieldStatic, rewriter, ast,
					group, importRewriter);
		}
	}

	/**
	 * Creates a nested class from an anonymous ExternalResource declaration.
	 * Converts anonymous class lifecycle methods (before/after) to JUnit 5 callback methods
	 * (beforeEach/afterEach) and implements the appropriate callback interfaces.
	 * 
	 * @param anonymousClass the anonymous class to convert
	 * @param className the name for the new nested class
	 * @param fieldStatic whether the field is static (affects which callbacks to implement)
	 * @param rewriter the AST rewriter
	 * @param ast the AST instance
	 * @param importRewriter the import rewriter
	 * @param group the text edit group
	 * @return the newly created nested class declaration
	 */
	public static TypeDeclaration createNestedClassFromAnonymous(AnonymousClassDeclaration anonymousClass,
			String className, boolean fieldStatic, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			TextEditGroup group) {

		// Create the new TypeDeclaration
		TypeDeclaration nestedClass = ast.newTypeDeclaration();
		nestedClass.setName(ast.newSimpleName(className));
		if (fieldStatic) {
			nestedClass.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.STATIC_KEYWORD));
		}

		// Add JUnit 5 callback interfaces (before/after each or all depending on static)
		if (fieldStatic) {
			nestedClass.superInterfaceTypes()
					.add(ast.newSimpleType(ast.newName(BEFORE_ALL_CALLBACK)));
			nestedClass.superInterfaceTypes()
					.add(ast.newSimpleType(ast.newName(AFTER_ALL_CALLBACK)));
			importRewriter.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_ALL_CALLBACK);
			importRewriter.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_ALL_CALLBACK);
		} else {
			nestedClass.superInterfaceTypes()
					.add(ast.newSimpleType(ast.newName(BEFORE_EACH_CALLBACK)));
			nestedClass.superInterfaceTypes()
					.add(ast.newSimpleType(ast.newName(AFTER_EACH_CALLBACK)));
			importRewriter.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_EACH_CALLBACK);
			importRewriter.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_EACH_CALLBACK);
		}

		// Transfer lifecycle methods from anonymous class to new class
		ListRewrite bodyRewrite = rewriter.getListRewrite(nestedClass, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
		for (Object decl : anonymousClass.bodyDeclarations()) {
			if (decl instanceof MethodDeclaration) {
				MethodDeclaration method = (MethodDeclaration) decl;

				// Convert before() -> beforeEach(ExtensionContext) and after() -> afterEach(ExtensionContext)
				if (isLifecycleMethod(method, METHOD_BEFORE)) {
					MethodDeclaration beforeEachMethod = LifecycleMethodAdapter.createLifecycleCallbackMethod(ast,
							METHOD_BEFORE_EACH, EXTENSION_CONTEXT, method.getBody(), group);
					bodyRewrite.insertLast(beforeEachMethod, group);
				} else if (isLifecycleMethod(method, METHOD_AFTER)) {
					MethodDeclaration afterEachMethod = LifecycleMethodAdapter.createLifecycleCallbackMethod(ast,
							METHOD_AFTER_EACH, EXTENSION_CONTEXT, method.getBody(), group);
					bodyRewrite.insertLast(afterEachMethod, group);
				}
			}
		}

		// Add the new class to the enclosing type
		TypeDeclaration parentType = ASTNavigationUtils.findEnclosingTypeDeclaration(anonymousClass);
		if (parentType != null) {
			ListRewrite enclosingBodyRewrite = rewriter.getListRewrite(parentType,
					TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
			enclosingBodyRewrite.insertLast(nestedClass, group);
		}

		return nestedClass;
	}

	/**
	 * Ensures that an anonymous ExternalResource class is properly rewritten for JUnit 5.
	 * Removes the ExternalResource superclass and adds necessary JUnit 5 callback imports.
	 * 
	 * @param classInstanceCreation the class instance creation containing the anonymous class
	 * @param rewriter the AST rewriter
	 * @param importRewriter the import rewriter
	 * @param group the text edit group
	 */
	public static void ensureClassInstanceRewrite(ClassInstanceCreation classInstanceCreation, ASTRewrite rewriter,
			ImportRewrite importRewriter, TextEditGroup group) {
		removeExternalResourceSuperclass(classInstanceCreation, rewriter, importRewriter, group);

		// Add required JUnit 5 callback imports
		importRewriter.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_EACH_CALLBACK);
		importRewriter.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_EACH_CALLBACK);
		importRewriter.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_EXTENSION_CONTEXT);
	}

	/**
	 * Removes the ExternalResource superclass from an anonymous class.
	 * 
	 * @param anonymousClass the class instance creation
	 * @param rewrite the AST rewriter
	 * @param importRewriter the import rewriter
	 * @param group the text edit group
	 */
	private static void removeExternalResourceSuperclass(ClassInstanceCreation anonymousClass, ASTRewrite rewrite,
			ImportRewrite importRewriter, TextEditGroup group) {
		// Check if the anonymous class inherits from ExternalResource
		ITypeBinding typeBinding = anonymousClass.resolveTypeBinding();
		if (typeBinding.getSuperclass() != null
				&& ORG_JUNIT_RULES_EXTERNAL_RESOURCE.equals(typeBinding.getSuperclass().getQualifiedName())) {

			// Remove the superclass by replacing the type in the ClassInstanceCreation
			Type type = anonymousClass.getType();
			if (type != null) {
				rewrite.replace(type, anonymousClass.getAST().newSimpleType(anonymousClass.getAST().newSimpleName("Object")),
						group);
			}

			// Remove the import of the superclass
			importRewriter.removeImport(ORG_JUNIT_RULES_EXTERNAL_RESOURCE);
		}
	}

	/**
	 * Refactors a type to implement JUnit 5 callback interfaces instead of extending ExternalResource.
	 * 
	 * @param node the type declaration
	 * @param rewriter the AST rewriter
	 * @param ast the AST instance
	 * @param group the text edit group
	 * @param importRewriter the import rewriter
	 * @param beforeCallback the before callback simple name
	 * @param afterCallback the after callback simple name
	 * @param importBeforeCallback the before callback fully qualified name
	 * @param importAfterCallback the after callback fully qualified name
	 */
	private static void refactorToImplementCallbacks(TypeDeclaration node, ASTRewrite rewriter, AST ast,
			TextEditGroup group, ImportRewrite importRewriter, String beforeCallback, String afterCallback,
			String importBeforeCallback, String importAfterCallback) {

		if (node == null || rewriter == null || ast == null || importRewriter == null) {
			return;
		}

		ASTRewrite rewriteToUse = getASTRewrite(node, ast, rewriter);
		ImportRewrite importRewriteToUse = getImportRewrite(node, ast, importRewriter);

		rewriteToUse.remove(node.getSuperclassType(), group);
		importRewriteToUse.removeImport(ORG_JUNIT_RULES_EXTERNAL_RESOURCE);

		ListRewrite listRewrite = rewriteToUse.getListRewrite(node, TypeDeclaration.SUPER_INTERFACE_TYPES_PROPERTY);
		ImportHelper.addInterfaceCallback(listRewrite, ast, beforeCallback, group, importRewriteToUse,
				importBeforeCallback);
		ImportHelper.addInterfaceCallback(listRewrite, ast, afterCallback, group, importRewriteToUse,
				importAfterCallback);

		if (rewriteToUse != rewriter) {
			DocumentHelper.createChangeForRewrite(ASTNavigationUtils.findCompilationUnit(node), rewriteToUse);
		}
	}

	/**
	 * Removes the superclass type from a type declaration.
	 * Used when converting ExternalResource subclasses to implement callback interfaces.
	 * 
	 * @param typeDecl the type declaration to modify
	 * @param rewrite the AST rewriter
	 * @param group the text edit group
	 */
	private static void removeSuperclassType(TypeDeclaration typeDecl, ASTRewrite rewrite, TextEditGroup group) {
		if (typeDecl.getSuperclassType() != null) {
			rewrite.remove(typeDecl.getSuperclassType(), group);
		}
	}

	/**
	 * Adds the @RegisterExtension annotation to a field.
	 * Resolves the field declaration from the given node and delegates to addRegisterExtensionToField.
	 * 
	 * @param node the AST node (either a FieldDeclaration or a ClassInstanceCreation)
	 * @param rewrite the AST rewriter
	 * @param ast the AST instance
	 * @param importRewrite the import rewriter
	 * @param group the text edit group
	 */
	private static void addRegisterExtensionAnnotation(ASTNode node, ASTRewrite rewrite, AST ast,
			ImportRewrite importRewrite, TextEditGroup group) {
		FieldDeclaration field = resolveFieldDeclaration(node);
		if (field != null) {
			addRegisterExtensionToField(field, rewrite, ast, importRewrite, group);
		}
	}

	/**
	 * Resolves the {@link FieldDeclaration} from the given AST node.
	 * 
	 * @param node an AST node that is either a {@link FieldDeclaration} or a
	 *             {@link ClassInstanceCreation} within a field initializer
	 * @return the resolved {@link FieldDeclaration}, or {@code null} if not found
	 */
	private static FieldDeclaration resolveFieldDeclaration(ASTNode node) {
		if (node instanceof FieldDeclaration) {
			return (FieldDeclaration) node;
		} else if (node instanceof ClassInstanceCreation) {
			return ASTNodes.getParent(node, FieldDeclaration.class);
		}
		return null;
	}

	/**
	 * Adds the {@code @RegisterExtension} annotation to the given field if not already present.
	 */
	private static void addRegisterExtensionToField(FieldDeclaration field, ASTRewrite rewrite, AST ast,
			ImportRewrite importRewrite, TextEditGroup group) {
		boolean hasRegisterExtension = AnnotationUtils.hasAnnotationBySimpleName(field.modifiers(),
				ANNOTATION_REGISTER_EXTENSION);

		ListRewrite listRewrite = rewrite.getListRewrite(field, FieldDeclaration.MODIFIERS2_PROPERTY);
		boolean hasPendingRegisterExtension = listRewrite.getRewrittenList().stream()
				.anyMatch(rewritten -> rewritten instanceof MarkerAnnotation && ((MarkerAnnotation) rewritten)
						.getTypeName().getFullyQualifiedName().equals(ANNOTATION_REGISTER_EXTENSION));

		if (!hasRegisterExtension && !hasPendingRegisterExtension) {
			MarkerAnnotation registerExtensionAnnotation = ast.newMarkerAnnotation();
			registerExtensionAnnotation.setTypeName(ast.newName(ANNOTATION_REGISTER_EXTENSION));
			listRewrite.insertFirst(registerExtensionAnnotation, group);
			importRewrite.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_REGISTER_EXTENSION);
		}
	}

	/**
	 * Adds the @ExtendWith annotation to a class for JUnit 5 extension integration.
	 * Used when migrating JUnit 4 @Rule fields to JUnit 5 @RegisterExtension.
	 * 
	 * @param rewrite the AST rewriter
	 * @param ast the AST instance
	 * @param group the text edit group
	 * @param importRewriter the import rewriter
	 * @param className the simple name of the extension class
	 * @param field the field that triggered the need for this annotation
	 */
	public static void addExtendWithAnnotation(ASTRewrite rewrite, AST ast, TextEditGroup group,
			ImportRewrite importRewriter, String className, FieldDeclaration field) {
		TypeDeclaration parentClass = getParentTypeDeclaration(field);
		if (parentClass == null) {
			return;
		}

		// Create @ExtendWith(ClassName.class) annotation
		SingleMemberAnnotation newAnnotation = ast.newSingleMemberAnnotation();
		newAnnotation.setTypeName(ast.newName(ANNOTATION_EXTEND_WITH));
		TypeLiteral newTypeLiteral = ast.newTypeLiteral();
		newTypeLiteral.setType(ast.newSimpleType(ast.newSimpleName(className)));
		newAnnotation.setValue(newTypeLiteral);

		// Add annotation to class
		ListRewrite modifierListRewrite = rewrite.getListRewrite(parentClass, TypeDeclaration.MODIFIERS2_PROPERTY);
		modifierListRewrite.insertFirst(newAnnotation, group);

		// Add import for @ExtendWith
		importRewriter.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_EXTEND_WITH);
	}

	/**
	 * Removes a @Rule or @ClassRule annotation from a body declaration.
	 * Also removes the corresponding import statement.
	 * 
	 * @param declaration the body declaration to remove the annotation from
	 * @param rewriter the AST rewriter
	 * @param group the text edit group
	 * @param importRewriter the import rewriter
	 * @param annotationclass the fully qualified annotation class name to remove
	 */
	private static void removeRuleAnnotation(BodyDeclaration declaration, ASTRewrite rewriter, TextEditGroup group,
			ImportRewrite importRewriter, String annotationclass) {
		java.util.List<?> modifiers = declaration.modifiers();
		for (Object modifier : modifiers) {
			if (modifier instanceof Annotation) {
				Annotation annotation = (Annotation) modifier;
				ITypeBinding binding = annotation.resolveTypeBinding();
				if (binding != null && binding.getQualifiedName().equals(annotationclass)) {
					rewriter.remove(annotation, group);
					importRewriter.removeImport(annotationclass);
					break;
				}
			}
		}
	}

	private static void replaceFieldWithExtensionDeclaration(ClassInstanceCreation classInstanceCreation,
			String nestedClassName, boolean fieldStatic, ASTRewrite rewriter, AST ast, TextEditGroup group,
			ImportRewrite importRewriter) {

		FieldDeclaration fieldDecl = ASTNodes.getParent(classInstanceCreation, FieldDeclaration.class);
		if (fieldDecl != null) {
			// Remove the @Rule annotation
			removeRuleAnnotation(fieldDecl, rewriter, group, importRewriter, ORG_JUNIT_RULE);

			// Add the @RegisterExtension annotation
			addRegisterExtensionAnnotation(fieldDecl, rewriter, ast, importRewriter, group);

			// Change the type of the FieldDeclaration
			Type newType = ast.newSimpleType(ast.newName(nestedClassName));
			rewriter.replace(fieldDecl.getType(), newType, group);

			// Add the initialization
			for (Object fragment : fieldDecl.fragments()) {
				if (fragment instanceof VariableDeclarationFragment) {
					VariableDeclarationFragment fragmentNode = (VariableDeclarationFragment) fragment;
					ClassInstanceCreation newInstance = ast.newClassInstanceCreation();
					newInstance.setType(ast.newSimpleType(ast.newName(nestedClassName)));
					rewriter.replace(fragmentNode.getInitializer(), newInstance, group);
				}
			}
		}
	}

	/**
	 * Determines the appropriate callback configuration based on whether the field is static.
	 * 
	 * @param fieldStatic whether the field is static
	 * @return the callback configuration with callback names and import paths
	 */
	private static CallbackConfig determineCallbackConfig(boolean fieldStatic) {
		if (fieldStatic) {
			return new CallbackConfig(BEFORE_ALL_CALLBACK, AFTER_ALL_CALLBACK,
					ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_ALL_CALLBACK,
					ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_ALL_CALLBACK);
		} else {
			return new CallbackConfig(BEFORE_EACH_CALLBACK, AFTER_EACH_CALLBACK,
					ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_EACH_CALLBACK,
					ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_EACH_CALLBACK);
		}
	}

	/**
	 * Configuration holder for callback names and import paths.
	 */
	private static class CallbackConfig {
		final String beforeCallback;
		final String afterCallback;
		final String importBeforeCallback;
		final String importAfterCallback;

		CallbackConfig(String beforeCallback, String afterCallback, String importBeforeCallback,
				String importAfterCallback) {
			this.beforeCallback = beforeCallback;
			this.afterCallback = afterCallback;
			this.importBeforeCallback = importBeforeCallback;
			this.importAfterCallback = importAfterCallback;
		}
	}

	private static boolean shouldProcessNode(TypeDeclaration node) {
		ITypeBinding binding = node.resolveBinding();
		return binding != null && isExternalResource(binding, ORG_JUNIT_RULES_EXTERNAL_RESOURCE);
	}

	private static boolean isExternalResource(FieldDeclaration field, String typeToLookup) {
		ITypeBinding binding = ((VariableDeclarationFragment) field.fragments().get(0)).resolveBinding().getType();
		return org.sandbox.jdt.internal.corext.util.TypeCheckingUtils.isTypeOrSubtype(binding, typeToLookup);
	}

	private static boolean isExternalResource(ITypeBinding typeBinding, String typeToLookup) {
		return org.sandbox.jdt.internal.corext.util.TypeCheckingUtils.isTypeOrSubtype(typeBinding, typeToLookup);
	}

	private static boolean isDirectlyExtendingExternalResource(ITypeBinding binding) {
		ITypeBinding superclass = binding.getSuperclass();
		return superclass != null && ORG_JUNIT_RULES_EXTERNAL_RESOURCE.equals(superclass.getQualifiedName());
	}

	private static boolean isLifecycleMethod(MethodDeclaration method, String methodName) {
		return methodName.equals(method.getName().getIdentifier());
	}

	private static TypeDeclaration getParentTypeDeclaration(ASTNode node) {
		return ASTNavigationUtils.getParentTypeDeclaration(node);
	}

	private static ASTRewrite getASTRewrite(ASTNode node, AST globalAST, ASTRewrite globalRewrite) {
		return (node.getAST() == globalAST) ? globalRewrite : ASTRewrite.create(node.getAST());
	}

	private static ImportRewrite getImportRewrite(ASTNode node, AST globalAST, ImportRewrite globalImportRewrite) {
		org.eclipse.jdt.core.dom.CompilationUnit compilationUnit = ASTNavigationUtils.findCompilationUnit(node);
		return (node.getAST() == globalAST) ? globalImportRewrite : ImportRewrite.create(compilationUnit, true);
	}
}
