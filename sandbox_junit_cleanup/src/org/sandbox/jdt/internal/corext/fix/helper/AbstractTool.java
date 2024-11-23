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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
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

	private static final String TEST_NAME= "testName";
	private static final String METHOD_AFTER_EACH= "afterEach";
	private static final String METHOD_BEFORE_EACH= "beforeEach";
	private static final String ORG_JUNIT_JUPITER_API_EXTENSION_EXTENSION_CONTEXT= "org.junit.jupiter.api.extension.ExtensionContext";
	private static final String AFTER_EACH_CALLBACK= "AfterEachCallback";
	private static final String BEFORE_EACH_CALLBACK= "BeforeEachCallback";
	private static final String ANNOTATION_REGISTER_EXTENSION= "RegisterExtension";
	protected static final String ORG_JUNIT_RULE= "org.junit.Rule";
	private static final String ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_EACH_CALLBACK= "org.junit.jupiter.api.extension.AfterEachCallback";
	private static final String ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_EACH_CALLBACK= "org.junit.jupiter.api.extension.BeforeEachCallback";
	private static final String ORG_JUNIT_JUPITER_API_EXTENSION_REGISTER_EXTENSION= "org.junit.jupiter.api.extension.RegisterExtension";
	private static final String ANNOTATION_EXTEND_WITH= "ExtendWith";
	protected static final String METHOD_AFTER= "after";
	protected static final String METHOD_BEFORE= "before";
	private static final String VARIABLE_NAME_CONTEXT= "context";
	private static final String EXTENSION_CONTEXT= "ExtensionContext";
	protected static final String ORG_JUNIT_RULES_EXTERNAL_RESOURCE= "org.junit.rules.ExternalResource";
	protected static final String ORG_JUNIT_JUPITER_API_EXTENSION_EXTEND_WITH= "org.junit.jupiter.api.extension.ExtendWith";
	protected static final String ORG_JUNIT_AFTER= "org.junit.After";
	protected static final String ORG_JUNIT_JUPITER_API_AFTER_EACH= "org.junit.jupiter.api.AfterEach";
	protected static final String ANNOTATION_AFTER_EACH= "AfterEach";
	protected static final String ORG_JUNIT_BEFORE= "org.junit.Before";
	protected static final String ANNOTATION_BEFORE_EACH= "BeforeEach";
	protected static final String ORG_JUNIT_AFTERCLASS= "org.junit.AfterClass";
	protected static final String ORG_JUNIT_JUPITER_API_AFTER_ALL= "org.junit.jupiter.api.AfterAll";
	protected static final String ANNOTATION_AFTER_ALL= "AfterAll";
	protected static final String ASSERTIONS= "Assertions";
	protected static final String ORG_JUNIT_JUPITER_API_ASSERTIONS= "org.junit.jupiter.api.Assertions";
	protected static final String ORG_JUNIT_ASSERT= "org.junit.Assert";
	protected static final String ORG_JUNIT_BEFORECLASS= "org.junit.BeforeClass";
	protected static final String ORG_JUNIT_JUPITER_API_BEFORE_ALL= "org.junit.jupiter.api.BeforeAll";
	protected static final String ANNOTATION_BEFORE_ALL= "BeforeAll";
	protected static final String ORG_JUNIT_IGNORE= "org.junit.Ignore";
	protected static final String ORG_JUNIT_JUPITER_DISABLED= "org.junit.jupiter.api.Disabled";
	protected static final String ANNOTATION_DISABLED= "Disabled";
	protected static final String ORG_JUNIT_JUPITER_API_IO_TEMP_DIR= "org.junit.jupiter.api.io.TempDir";
	protected static final String ORG_JUNIT_RULES_TEMPORARY_FOLDER= "org.junit.rules.TemporaryFolder";
	protected static final String ORG_JUNIT_JUPITER_API_TEST_INFO= "org.junit.jupiter.api.TestInfo";
	protected static final String ORG_JUNIT_RULES_TEST_NAME= "org.junit.rules.TestName";
	protected static final String ORG_JUNIT_JUPITER_API_BEFORE_EACH= "org.junit.jupiter.api.BeforeEach";
	protected static final String ORG_JUNIT_PLATFORM_SUITE_API_SELECT_CLASSES= "org.junit.platform.suite.api.SelectClasses";
	protected static final String ANNOTATION_SELECT_CLASSES= "SelectClasses";
	protected static final String ORG_JUNIT_RUNWITH= "org.junit.runner.RunWith";
	protected static final String ORG_JUNIT_JUPITER_SUITE= "org.junit.platform.suite.api.Suite";
	protected static final String ANNOTATION_SUITE= "Suite";
	protected static final String ORG_JUNIT_SUITE= "org.junit.runners.Suite";
	protected static final String ORG_JUNIT_SUITE_SUITECLASSES= "org.junit.runners.Suite.SuiteClasses";
	protected static final String ORG_JUNIT_TEST= "org.junit.Test";
	protected static final String ORG_JUNIT_JUPITER_TEST= "org.junit.jupiter.api.Test";
	protected static final String ANNOTATION_TEST= "Test";
	protected static final String ORG_JUNIT_JUPITER_API_ASSUMPTIONS= "org.junit.jupiter.api.Assumptions";
	protected static final String ORG_JUNIT_ASSUME= "org.junit.Assume";
	protected static final String ASSUMPTIONS= "Assumptions";
	protected static final String ORG_JUNIT_CLASS_RULE= "org.junit.ClassRule";

	public static Collection<String> getUsedVariableNames(ASTNode node) {
		CompilationUnit root= (CompilationUnit) node.getRoot();
		return new ScopeAnalyzer(root).getUsedVariableNames(node.getStartPosition(), node.getLength());
	}

	protected static boolean isOfType(ITypeBinding typeBinding, String typename) {
		if (typeBinding == null) {
			throw new AbortSearchException();
		}
		if (typeBinding.isArray()) {
			typeBinding= typeBinding.getElementType();
		}
		return typeBinding.getQualifiedName().equals(typename);
	}

	protected Optional<ASTNode> getInnerTypeDeclaration(FieldDeclaration fieldDeclaration) {
		for (Object fragment : fieldDeclaration.fragments()) {
			if (fragment instanceof VariableDeclarationFragment) {
				VariableDeclarationFragment variableFragment= (VariableDeclarationFragment) fragment;

				// Prüfen, ob die Initialisierung eine anonyme Klasse ist
				Expression initializer= variableFragment.getInitializer();
				if (initializer instanceof ClassInstanceCreation) {
					ClassInstanceCreation classInstance= (ClassInstanceCreation) initializer;

					// Anonyme Klasse gefunden
					if (classInstance.getAnonymousClassDeclaration() != null) {
						return Optional.of(classInstance.getAnonymousClassDeclaration());
					}

					// Falls keine anonyme Klasse, den Typ der inneren Klasse prüfen
					ITypeBinding typeBinding= classInstance.getType().resolveBinding();
					if (typeBinding != null && typeBinding.isClass() && typeBinding.getJavaElement() instanceof IType) {
						IType type= (IType) typeBinding.getJavaElement();
						IJavaProject javaProject= type.getJavaProject();
						String typeName= type.getElementName();

						// Verwende nun den Projektnamen und den Typnamen
						TypeDeclaration innerTypeDecl= findTypeDeclaration(javaProject, typeName);
						if (innerTypeDecl != null) {
							return Optional.of(innerTypeDecl);
						}
					}
				}
			}
		}
		return Optional.empty(); // Keine innere oder anonyme Klasse gefunden
	}

	private void addContextArgumentIfMissing(ASTNode node, ASTRewrite rewriter, AST ast, TextEditGroup group) {
		ListRewrite argsRewrite;
		if (node instanceof MethodInvocation) {
			argsRewrite= rewriter.getListRewrite(node, MethodInvocation.ARGUMENTS_PROPERTY);
		} else if (node instanceof SuperMethodInvocation) {
			argsRewrite= rewriter.getListRewrite(node, SuperMethodInvocation.ARGUMENTS_PROPERTY);
		} else {
			return; // Unterstützt nur MethodInvocation und SuperMethodInvocation
		}

		boolean hasContextArgument= argsRewrite.getRewrittenList().stream().anyMatch(
				arg -> arg instanceof SimpleName && ((SimpleName) arg).getIdentifier().equals(VARIABLE_NAME_CONTEXT));

		if (!hasContextArgument) {
			argsRewrite.insertFirst(ast.newSimpleName(VARIABLE_NAME_CONTEXT), group);
		}
	}

	private void setPublicVisibilityIfProtected(MethodDeclaration method, ASTRewrite rewrite, AST ast,
			TextEditGroup group) {
		// Durchlaufe die Modifiers und suche nach einem geschützten (protected)
		// Modifier
		for (Object modifier : method.modifiers()) {
			if (modifier instanceof Modifier) {
				Modifier mod= (Modifier) modifier;
				if (mod.isProtected()) {
					ListRewrite modifierRewrite= rewrite.getListRewrite(method, MethodDeclaration.MODIFIERS2_PROPERTY);
					Modifier publicModifier= ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD);
					modifierRewrite.replace(mod, publicModifier, group);
					break; // Stoppe die Schleife, sobald der Modifier ersetzt wurde
				}
			}
		}
	}

	private void adaptExternalResourceHierarchy(ITypeBinding typeBinding, ASTRewrite rewrite, AST ast,
			ImportRewrite importRewrite, TextEditGroup group) {
		while (typeBinding != null && isExternalResource(typeBinding)) {
			TypeDeclaration typeDecl= findTypeDeclarationInProject(typeBinding);
			if (typeDecl != null) {
				adaptTypeDeclaration(typeDecl, rewrite, ast, importRewrite, group);
			}
			typeBinding= typeBinding.getSuperclass();
		}
	}

	private TypeDeclaration findTypeDeclarationInProject(ITypeBinding typeBinding) {
		IType type= (IType) typeBinding.getJavaElement();
		return type != null ? findTypeDeclaration(type.getJavaProject(), type.getElementName()) : null;
	}

	private void processLifecycleMethod(MethodDeclaration method, ASTRewrite rewriter, AST ast, TextEditGroup group,
			ImportRewrite importRewriter, boolean isBefore) {
		String oldName= isBefore ? METHOD_BEFORE : METHOD_AFTER;
		String newName= isBefore ? METHOD_BEFORE_EACH : METHOD_AFTER_EACH;

		setPublicVisibilityIfProtected(method, rewriter, ast, group);
		adaptSuperBeforeCalls(oldName, newName, method, rewriter, ast, group);

		if (isBefore) {
			removeThrowsThrowable(method, rewriter, group);
		}

		rewriter.replace(method.getName(), ast.newSimpleName(newName), group);
		ensureExtensionContextParameter(method, rewriter, ast, group, importRewriter);
	}

	private void processMethodInvocation(MethodInvocation node, String fieldName, ASTRewrite rewriter, AST ast,
			TextEditGroup group) {
		if (node.getExpression() instanceof SimpleName
				&& ((SimpleName) node.getExpression()).getIdentifier().equals(fieldName)) {
			String methodName= node.getName().getIdentifier();
			if (METHOD_BEFORE.equals(methodName)) {
				rewriter.replace(node.getName(), ast.newSimpleName(METHOD_BEFORE_EACH), group);
				addContextArgumentIfMissing(node, rewriter, ast, group);
			} else if (METHOD_AFTER.equals(methodName)) {
				rewriter.replace(node.getName(), ast.newSimpleName(METHOD_AFTER_EACH), group);
			}
		}
	}

	private void adaptBeforeAfterCallsInTestClass(TypeDeclaration testClass, String fieldName, ASTRewrite rewriter,
			AST ast, TextEditGroup group) {
		testClass.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation node) {
				processMethodInvocation(node, fieldName, rewriter, ast, group);
				return super.visit(node);
			}
		});
	}

	protected void modifyExternalResourceClass(TypeDeclaration node, ASTRewrite rewriter, AST ast, TextEditGroup group,
			ImportRewrite importRewriter) {
		if (!shouldProcessNode(node)) {
			return;
		}

		if (isDirectlyExtendingExternalResource(node.resolveBinding())) {
			refactorToImplementCallbacks(node, rewriter, ast, group, importRewriter);
		}

		updateLifecycleMethodsInClass(node, rewriter, ast, group, importRewriter);
	}

	private boolean shouldProcessNode(TypeDeclaration node) {
		ITypeBinding binding= node.resolveBinding();
		return binding != null && isExternalResource(binding);
	}

	private void processBeforeMethod(MethodDeclaration method, ASTRewrite rewriter, AST ast, TextEditGroup group,
			ImportRewrite importRewriter) {
		setPublicVisibilityIfProtected(method, rewriter, ast, group);
		adaptSuperBeforeCalls(METHOD_BEFORE, METHOD_BEFORE_EACH, method, rewriter, ast, group);
		removeThrowsThrowable(method, rewriter, group);
		rewriter.replace(method.getName(), ast.newSimpleName(METHOD_BEFORE_EACH), group);
		ensureExtensionContextParameter(method, rewriter, ast, group, importRewriter);
	}

	private void processAfterMethod(MethodDeclaration method, ASTRewrite rewriter, AST ast, TextEditGroup group,
			ImportRewrite importRewriter) {
		setPublicVisibilityIfProtected(method, rewriter, ast, group);
		adaptSuperBeforeCalls(METHOD_AFTER, METHOD_AFTER_EACH, method, rewriter, ast, group);
		rewriter.replace(method.getName(), ast.newSimpleName(METHOD_AFTER_EACH), group);
		ensureExtensionContextParameter(method, rewriter, ast, group, importRewriter);
	}

	private void updateLifecycleMethodsInClass(TypeDeclaration node, ASTRewrite rewriter, AST ast, TextEditGroup group,
			ImportRewrite importRewriter) {
		for (MethodDeclaration method : node.getMethods()) {
			if (isLifecycleMethod(method, METHOD_BEFORE)) {
				processBeforeMethod(method, rewriter, ast, group, importRewriter);
			} else if (isLifecycleMethod(method, METHOD_AFTER)) {
				processAfterMethod(method, rewriter, ast, group, importRewriter);
			}
		}
	}

	private void updateLifecycleMethods(TypeDeclaration typeDecl, ASTRewrite rewrite, AST ast, TextEditGroup group,
			ImportRewrite importRewrite) {
		for (MethodDeclaration method : typeDecl.getMethods()) {
			if (isLifecycleMethod(method, METHOD_BEFORE) || isLifecycleMethod(method, METHOD_AFTER)) {
				processLifecycleMethod(method, rewrite, ast, group, importRewrite,
						METHOD_BEFORE.equals(method.getName().getIdentifier()));
			}
		}
	}

	private void adaptTypeDeclaration(TypeDeclaration typeDecl, ASTRewrite rewrite, AST ast,
			ImportRewrite importRewrite, TextEditGroup group) {
		removeSuperclassType(typeDecl, rewrite, group);
		addBeforeAndAfterEachCallbacks(typeDecl, rewrite, ast, importRewrite, group);
		updateLifecycleMethods(typeDecl, rewrite, ast, group, importRewrite);
		importRewrite.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_EACH_CALLBACK);
		importRewrite.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_EACH_CALLBACK);
	}

	private void refactorToImplementCallbacks(TypeDeclaration node, ASTRewrite rewriter, AST ast, TextEditGroup group,
			ImportRewrite importRewriter) {
		rewriter.remove(node.getSuperclassType(), group);
		importRewriter.removeImport(ORG_JUNIT_RULES_EXTERNAL_RESOURCE);
		ListRewrite listRewrite= rewriter.getListRewrite(node, TypeDeclaration.SUPER_INTERFACE_TYPES_PROPERTY);
		addInterfaceCallback(listRewrite, ast, BEFORE_EACH_CALLBACK, group,importRewriter, ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_EACH_CALLBACK);
		addInterfaceCallback(listRewrite, ast, AFTER_EACH_CALLBACK, group,importRewriter, ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_EACH_CALLBACK);
	}

	private void addBeforeAndAfterEachCallbacks(TypeDeclaration typeDecl, ASTRewrite rewrite, AST ast,
			ImportRewrite importRewrite, TextEditGroup group) {
		ListRewrite listRewrite= rewrite.getListRewrite(typeDecl, TypeDeclaration.SUPER_INTERFACE_TYPES_PROPERTY);
		addInterfaceCallback(listRewrite, ast, BEFORE_EACH_CALLBACK, group,importRewrite, ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_EACH_CALLBACK);
		addInterfaceCallback(listRewrite, ast, AFTER_EACH_CALLBACK, group,importRewrite, ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_EACH_CALLBACK);
	}

	private void adaptSuperBeforeCalls(String vorher, String nachher, MethodDeclaration method, ASTRewrite rewriter,
			AST ast, TextEditGroup group) {
		method.accept(new ASTVisitor() {
			@Override
			public boolean visit(SuperMethodInvocation node) {
				if (vorher.equals(node.getName().getIdentifier())) {
					rewriter.replace(node.getName(), ast.newSimpleName(nachher), group);
					addContextArgumentIfMissing(node, rewriter, ast, group);
				}
				return super.visit(node);
			}
		});
	}

	protected void addExtendWithAnnotation(ASTRewrite rewrite, AST ast, TextEditGroup group,
			ImportRewrite importRewriter, String className, FieldDeclaration field) {
		TypeDeclaration parentClass= getParentTypeDeclaration(field);
		if (parentClass == null) {
			return;
		}
		SingleMemberAnnotation newAnnotation= ast.newSingleMemberAnnotation();
		newAnnotation.setTypeName(ast.newName(ANNOTATION_EXTEND_WITH));
		final TypeLiteral newTypeLiteral= ast.newTypeLiteral();
		newTypeLiteral.setType(ast.newSimpleType(ast.newSimpleName(className)));
		newAnnotation.setValue(newTypeLiteral);
		ListRewrite modifierListRewrite= rewrite.getListRewrite(parentClass, TypeDeclaration.MODIFIERS2_PROPERTY);
		modifierListRewrite.insertFirst(newAnnotation, group);
		importRewriter.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_EXTEND_WITH);
	}

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

	private void manageImport(ImportRewrite importRewriter, String typeName, boolean add) {
		if (add) {
			importRewriter.addImport(typeName);
		} else {
			importRewriter.removeImport(typeName);
		}
	}

	private void addInterfaceCallback(ListRewrite listRewrite, AST ast, String callbackName, TextEditGroup group, ImportRewrite importRewriter, String classtoimport) {
		// Prüfen, ob das Interface bereits in der Liste existiert
		boolean hasCallback= listRewrite.getRewrittenList().stream().anyMatch(type -> type instanceof SimpleType
				&& ((SimpleType) type).getName().getFullyQualifiedName().equals(callbackName));

		if (!hasCallback) {
			// Interface hinzufügen, wenn es noch nicht existiert
			listRewrite.insertLast(ast.newSimpleType(ast.newName(callbackName)), group);
		}
		importRewriter.addImport(classtoimport);
	}

	private void addRegisterExtensionAnnotation(FieldDeclaration field, ASTRewrite rewrite, AST ast,
			ImportRewrite importRewrite, TextEditGroup group) {
		// Prüfen, ob die Annotation bereits existiert
		boolean hasRegisterExtension= field.modifiers().stream().anyMatch(modifier -> modifier instanceof Annotation
				&& ((Annotation) modifier).getTypeName().getFullyQualifiedName().equals(ANNOTATION_REGISTER_EXTENSION));

		// Prüfen, ob die Annotation bereits im Rewrite hinzugefügt wurde
		ListRewrite listRewrite= rewrite.getListRewrite(field, FieldDeclaration.MODIFIERS2_PROPERTY);
		boolean hasPendingRegisterExtension= listRewrite.getRewrittenList().stream()
				.anyMatch(rewritten -> rewritten instanceof MarkerAnnotation && ((MarkerAnnotation) rewritten)
						.getTypeName().getFullyQualifiedName().equals(ANNOTATION_REGISTER_EXTENSION));

		if (!hasRegisterExtension && !hasPendingRegisterExtension) {
			// Annotation hinzufügen, wenn sie weder im AST noch im Rewrite existiert
			MarkerAnnotation registerExtensionAnnotation= ast.newMarkerAnnotation();
			registerExtensionAnnotation.setTypeName(ast.newName(ANNOTATION_REGISTER_EXTENSION));
			listRewrite.insertFirst(registerExtensionAnnotation, group);

			// Import hinzufügen
			importRewrite.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_REGISTER_EXTENSION);
		}
	}

	private void ensureExtensionContextParameter(MethodDeclaration method, ASTRewrite rewrite, AST ast,
			TextEditGroup group, ImportRewrite importRewrite) {

		// Prüfen, ob ExtensionContext bereits existiert (im AST oder im Rewrite)
		boolean hasExtensionContext= method.parameters().stream()
				.anyMatch(param -> param instanceof SingleVariableDeclaration
						&& isExtensionContext((SingleVariableDeclaration) param, ORG_JUNIT_JUPITER_API_EXTENSION_EXTENSION_CONTEXT))
				|| rewrite.getListRewrite(method, MethodDeclaration.PARAMETERS_PROPERTY).getRewrittenList().stream()
						.anyMatch(param -> param instanceof SingleVariableDeclaration
								&& ((SingleVariableDeclaration) param).getType().toString().equals(EXTENSION_CONTEXT));

		if (!hasExtensionContext) {
			// Neuen Parameter hinzufügen
			SingleVariableDeclaration newParam= ast.newSingleVariableDeclaration();
			newParam.setType(ast.newSimpleType(ast.newName(EXTENSION_CONTEXT)));
			newParam.setName(ast.newSimpleName(VARIABLE_NAME_CONTEXT));
			ListRewrite listRewrite= rewrite.getListRewrite(method, MethodDeclaration.PARAMETERS_PROPERTY);
			listRewrite.insertLast(newParam, group);

			// Import hinzufügen
			importRewrite.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_EXTENSION_CONTEXT);
		}
	}

	// Hilfsmethode zum Vergleich des Typs
	private boolean isExtensionContext(SingleVariableDeclaration param, String classname) {
		ITypeBinding binding= param.getType().resolveBinding();
		return binding != null && classname.equals(binding.getQualifiedName());
	}

	public String extractClassNameFromField(FieldDeclaration field) {
		for (Object fragmentObj : field.fragments()) {
			VariableDeclarationFragment fragment= (VariableDeclarationFragment) fragmentObj;
			Expression initializer= fragment.getInitializer();
			if (initializer instanceof ClassInstanceCreation) {
				ClassInstanceCreation creation= (ClassInstanceCreation) initializer;
				Type createdType= creation.getType();
				if (createdType instanceof QualifiedType) {
					QualifiedType qualifiedType= (QualifiedType) createdType;
					return extractQualifiedTypeName(qualifiedType);
				} else if (createdType instanceof SimpleType) {
					return ((SimpleType) createdType).getName().getFullyQualifiedName();
				}
			}
		}
		return null;
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

	public abstract void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed);

	public TypeDeclaration findTypeDeclaration(IJavaProject javaProject, String fullyQualifiedTypeName) {
		try {
			IType type= javaProject.findType(fullyQualifiedTypeName);
			if (type != null && type.exists()) {
				CompilationUnit unit= parseCompilationUnit(type.getCompilationUnit());
				return (TypeDeclaration) unit.types().get(0);
			}
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
		return null;
	}

	protected TypeDeclaration getParentTypeDeclaration(ASTNode node) {
		while (node != null && !(node instanceof TypeDeclaration)) {
			node= node.getParent();
		}
		return (TypeDeclaration) node;
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

	private boolean isAnnotatedWithRule(BodyDeclaration declaration, String annotationclass) {
		for (Object modifier : declaration.modifiers()) {
			if (modifier instanceof Annotation) {
				Annotation annotation= (Annotation) modifier;
				ITypeBinding binding= annotation.resolveTypeBinding();
				if (binding != null && annotationclass.equals(binding.getQualifiedName())) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean isAnonymousClass(VariableDeclarationFragment fragmentObj) {
		VariableDeclarationFragment fragment= fragmentObj;
		Expression initializer= fragment.getInitializer();
		if (initializer instanceof ClassInstanceCreation) {
			ClassInstanceCreation classInstanceCreation= (ClassInstanceCreation) initializer;
			AnonymousClassDeclaration anonymousClassDeclaration= classInstanceCreation.getAnonymousClassDeclaration();
			if (anonymousClassDeclaration != null) {
				return true;
			}
		}
		return false;
	}

	protected boolean isDirect(ITypeBinding fieldTypeBinding) {
		return ORG_JUNIT_RULES_EXTERNAL_RESOURCE.equals(fieldTypeBinding.getQualifiedName());
	}

	protected boolean isDirectlyExtendingExternalResource(ITypeBinding binding) {
		return ORG_JUNIT_RULES_EXTERNAL_RESOURCE.equals(binding.getSuperclass().getQualifiedName());
	}

	private boolean isExternalResource(FieldDeclaration field) {
		VariableDeclarationFragment fragment= (VariableDeclarationFragment) field.fragments().get(0);
		ITypeBinding binding= fragment.resolveBinding().getType();
		return isExternalResource(binding);
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

	protected boolean isLifecycleMethod(MethodDeclaration method, String methodName) {
		return method.getName().getIdentifier().equals(methodName);
	}

	private boolean isStringType(Expression expression) {
		ITypeBinding typeBinding= expression.resolveTypeBinding();
		return typeBinding != null && String.class.getCanonicalName().equals(typeBinding.getQualifiedName());
	}

	public void migrateRuleToRegisterExtensionAndAdaptHierarchy(Optional<ASTNode> innerTypeDeclaration,
			TypeDeclaration testClass, ASTRewrite rewrite, AST ast, ImportRewrite importRewrite, TextEditGroup group,
			String varname) {
		if (innerTypeDeclaration.isPresent() && innerTypeDeclaration.get() instanceof TypeDeclaration) {
			adaptBeforeAfterCallsInTestClass((TypeDeclaration) innerTypeDeclaration.get(), varname, rewrite, ast,
					group);
		}
		for (FieldDeclaration field : testClass.getFields()) {
			if (isAnnotatedWithRule(field, ORG_JUNIT_RULE) && isExternalResource(field)) {
				removeRuleAnnotation(field, rewrite, group, importRewrite);
				addRegisterExtensionAnnotation(field, rewrite, ast, importRewrite, group);
				ITypeBinding fieldType= ((VariableDeclarationFragment) field.fragments().get(0)).resolveBinding()
						.getType();
				adaptExternalResourceHierarchy(fieldType, rewrite, ast, importRewrite, group);
			}
		}
	}

	private CompilationUnit parseCompilationUnit(ICompilationUnit iCompilationUnit) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(iCompilationUnit);
		parser.setResolveBindings(true);
		return (CompilationUnit) parser.createAST(null);
	}

	public void process(Annotation node, IJavaProject jproject, ASTRewrite rewrite, AST ast, TextEditGroup group,
			ImportRewrite importRewriter, CompilationUnit cu, String className) {
		if (!ORG_JUNIT_RULE.equals(node.resolveTypeBinding().getQualifiedName())) {
			return;
		}
		FieldDeclaration field= (FieldDeclaration) node.getParent();
		ITypeBinding fieldTypeBinding= ((VariableDeclarationFragment) field.fragments().get(0)).resolveBinding()
				.getType();
		if (!isExternalResource(fieldTypeBinding) || fieldTypeBinding.isAnonymous()) {
			return;
		}
		if (isDirect(fieldTypeBinding)) {
			rewrite.remove(field, group);
			importRewriter.removeImport(ORG_JUNIT_RULE);
		}
		addExtendWithAnnotation(rewrite, ast, group, importRewriter, className, field);
		importRewriter.removeImport(ORG_JUNIT_RULES_EXTERNAL_RESOURCE);
	}

	private void removeRuleAnnotation(BodyDeclaration declaration, ASTRewrite rewriter, TextEditGroup group,
			ImportRewrite importRewriter) {
		List<?> modifiers= declaration.modifiers();
		for (Object modifier : modifiers) {
			if (modifier instanceof Annotation) {
				Annotation annotation= (Annotation) modifier;
				ITypeBinding binding= annotation.resolveTypeBinding();
				if (binding != null && binding.getQualifiedName().equals(ORG_JUNIT_RULE)) {
					rewriter.remove(annotation, group);
					importRewriter.removeImport(ORG_JUNIT_RULE);
					break;
				}
			}
		}
	}

	private void removeSuperclassType(TypeDeclaration typeDecl, ASTRewrite rewrite, TextEditGroup group) {
		if (typeDecl.getSuperclassType() != null) {
			rewrite.remove(typeDecl.getSuperclassType(), group);
		}
	}

	private void removeThrowsThrowable(MethodDeclaration method, ASTRewrite rewriter, TextEditGroup group) {
		List<?> thrownExceptionTypes= method.thrownExceptionTypes();
		for (Object exceptionType : thrownExceptionTypes) {
			if (exceptionType instanceof SimpleType) {
				SimpleType exception= (SimpleType) exceptionType;
				if ("Throwable".equals(exception.getName().getFullyQualifiedName())) {
					ListRewrite listRewrite= rewriter.getListRewrite(method,
							MethodDeclaration.THROWN_EXCEPTION_TYPES_PROPERTY);
					listRewrite.remove(exception, group);
				}
			}
		}
	}

	private void reorderParameters(ASTRewrite rewriter, MethodInvocation node, TextEditGroup group, int... order) {
		ListRewrite listRewrite= rewriter.getListRewrite(node, MethodInvocation.ARGUMENTS_PROPERTY);
		List<Expression> arguments= node.arguments();
		Expression[] newArguments= new Expression[arguments.size()];
		for (int i= 0; i < order.length; i++) {
			newArguments[i]= (Expression) ASTNode.copySubtree(node.getAST(), arguments.get(order[i]));
		}
		if (!isStringType(arguments.get(0))) {
			return;
		}
		for (int i= 0; i < arguments.size(); i++) {
			listRewrite.replace(arguments.get(i), newArguments[i], group);
		}
	}

	public void reorderParameters(MethodInvocation node, ASTRewrite rewriter, TextEditGroup group, Set<String> oneparam,
			Set<String> twoparam) {
		String methodName= node.getName().getIdentifier();
		List<Expression> arguments= node.arguments();
		switch (arguments.size()) {
		case 2:
			if (oneparam.contains(methodName)) {
				reorderParameters(rewriter, node, group, 1, 0);
			}
			break;
		case 3:
			if (twoparam.contains(methodName)) {
				reorderParameters(rewriter, node, group, 1, 2, 0); // expected, actual, message
			}
			break;
		case 4:
			reorderParameters(rewriter, node, group, 1, 2, 3, 0); // expected, actual, delta, message
			break;
		default:
			break;
		}
	}

	public abstract void rewrite(JUnitCleanUpFixCore useExplicitEncodingFixCore, T holder,
			CompilationUnitRewrite cuRewrite, TextEditGroup group);

	protected void refactorTestname(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importrewriter,
			FieldDeclaration node) {
		rewriter.remove(node, group);
		TypeDeclaration parentClass= (TypeDeclaration) node.getParent();
		addBeforeEachInitMethod(parentClass, rewriter, group);
		addTestNameField(parentClass, rewriter, group);
		for (MethodDeclaration method : parentClass.getMethods()) {
			if (method.getBody() != null) {
				method.getBody().accept(new ASTVisitor() {
					@Override
					public boolean visit(MethodInvocation node) {
						if (node.getExpression() != null && node.getExpression().resolveTypeBinding().getQualifiedName()
								.equals(ORG_JUNIT_RULES_TEST_NAME)) {
							SimpleName newFieldAccess= ast.newSimpleName(TEST_NAME);
							rewriter.replace(node, newFieldAccess, group);
						}
						return super.visit(node);
					}
				});
			}
		}
		importrewriter.addImport(ORG_JUNIT_JUPITER_API_TEST_INFO);
		importrewriter.addImport(ORG_JUNIT_JUPITER_API_BEFORE_EACH);
		importrewriter.removeImport(ORG_JUNIT_RULE);
		importrewriter.removeImport(ORG_JUNIT_RULES_TEST_NAME);
	}

	private void addTestNameField(TypeDeclaration parentClass, ASTRewrite rewriter, TextEditGroup group) {
		AST ast= parentClass.getAST();
		VariableDeclarationFragment fragment= ast.newVariableDeclarationFragment();
		fragment.setName(ast.newSimpleName(TEST_NAME));

		FieldDeclaration fieldDeclaration= ast.newFieldDeclaration(fragment);
		fieldDeclaration.setType(ast.newSimpleType(ast.newName("String")));
		fieldDeclaration.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));

		ListRewrite listRewrite= rewriter.getListRewrite(parentClass, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
		listRewrite.insertFirst(fieldDeclaration, group);
	}

	private void addBeforeEachInitMethod(TypeDeclaration parentClass, ASTRewrite rewriter, TextEditGroup group) {
		AST ast= parentClass.getAST();

		MethodDeclaration methodDeclaration= ast.newMethodDeclaration();
		methodDeclaration.setName(ast.newSimpleName("init"));
		methodDeclaration.setReturnType2(ast.newPrimitiveType(PrimitiveType.VOID));

		SingleVariableDeclaration param= ast.newSingleVariableDeclaration();
		param.setType(ast.newSimpleType(ast.newName("TestInfo")));
		param.setName(ast.newSimpleName("testInfo"));
		methodDeclaration.parameters().add(param);

		Block body= ast.newBlock();
		Assignment assignment= ast.newAssignment();
		FieldAccess fieldAccess= ast.newFieldAccess();
		fieldAccess.setExpression(ast.newThisExpression());
		fieldAccess.setName(ast.newSimpleName(TEST_NAME));
		assignment.setLeftHandSide(fieldAccess);

		MethodInvocation methodInvocation= ast.newMethodInvocation();
		methodInvocation.setExpression(ast.newSimpleName("testInfo"));
		methodInvocation.setName(ast.newSimpleName("getDisplayName"));

		assignment.setRightHandSide(methodInvocation);

		ExpressionStatement statement= ast.newExpressionStatement(assignment);
		body.statements().add(statement);
		methodDeclaration.setBody(body);

		MarkerAnnotation beforeEachAnnotation= ast.newMarkerAnnotation();
		beforeEachAnnotation.setTypeName(ast.newName("BeforeEach"));

		ListRewrite listRewrite= rewriter.getListRewrite(parentClass, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
		listRewrite.insertFirst(methodDeclaration, group);

		listRewrite= rewriter.getListRewrite(methodDeclaration, MethodDeclaration.MODIFIERS2_PROPERTY);
		listRewrite.insertFirst(beforeEachAnnotation, group);
	}

	private List<ITypeBinding> getAllSubclasses(ITypeBinding typeBinding) {
		List<ITypeBinding> subclasses= new ArrayList<>();

		try {
			// Erzeuge den entsprechenden IType des gegebenen ITypeBindings
			IType type= (IType) typeBinding.getJavaElement();

			// Erzeuge die Typ-Hierarchie für den übergebenen Typ innerhalb des Projekts
			ITypeHierarchy typeHierarchy= type.newTypeHierarchy(null); // null verwendet das gesamte Projekt

			// Durchlaufe alle direkten und indirekten Subtypen und füge sie der Liste hinzu
			for (IType subtype : typeHierarchy.getAllSubtypes(type)) {
				ITypeBinding subtypeBinding= (ITypeBinding) subtype.getAdapter(ITypeBinding.class);
				if (subtypeBinding != null) {
					subclasses.add(subtypeBinding);
				}
			}
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
		return subclasses;
	}

	protected void refactorTestnameInClassAndSubclasses(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewrite, FieldDeclaration node) {
		// Refactoring in der aktuellen Klasse
		refactorTestnameInClass(group, rewriter, ast, importRewrite, node);

		// Ermittlung aller abgeleiteten Klassen
		ITypeBinding typeBinding= ((TypeDeclaration) node.getParent()).resolveBinding();
		List<ITypeBinding> subclasses= getAllSubclasses(typeBinding);

		for (ITypeBinding subclassBinding : subclasses) {
			IType subclassType= (IType) subclassBinding.getJavaElement();

			// Hole die AST-Darstellung der Subklasse (zum Beispiel durch ASTParser)
			CompilationUnit subclassUnit= parseCompilationUnit(subclassType.getCompilationUnit());
			subclassUnit.accept(new ASTVisitor() {
				@Override
				public boolean visit(TypeDeclaration subclassNode) {
					if (subclassNode.resolveBinding().equals(subclassBinding)) {
						refactorTestnameInClass(group, rewriter, subclassNode.getAST(), importRewrite, node);
					}
					return false; // Nur das passende Typ-Deklarations-Element verarbeiten
				}
			});
		}
	}

	protected void refactorTestnameInClass(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewrite, FieldDeclaration node) {
		// Entferne das alte TestName-Feld
		rewriter.remove(node, group);

		// Füge ein neues TestName-Feld hinzu und erzeuge eine BeforeEach-Init-Methode
		TypeDeclaration parentClass= (TypeDeclaration) node.getParent();
		addBeforeEachInitMethod(parentClass, rewriter, group);
		addTestNameField(parentClass, rewriter, group);

		// Ersetze alle Zugriffe auf das alte TestName-Feld durch das neue Feld
		// "testName"
		for (MethodDeclaration method : parentClass.getMethods()) {
			if (method.getBody() != null) {
				method.getBody().accept(new ASTVisitor() {
					@Override
					public boolean visit(MethodInvocation node) {
						// Prüfen, ob der Aufruf auf das alte TestName-Feld verweist
						if (node.getExpression() != null && ORG_JUNIT_RULES_TEST_NAME
								.equals(node.getExpression().resolveTypeBinding().getQualifiedName())) {
							// Ersetze den Zugriff durch "testName"
							SimpleName newFieldAccess= ast.newSimpleName(TEST_NAME);
							rewriter.replace(node, newFieldAccess, group);
						}
						return super.visit(node);
					}
				});
			}
		}

		// Importanpassungen für JUnit 5
		importRewrite.addImport(ORG_JUNIT_JUPITER_API_TEST_INFO);
		importRewrite.addImport(ORG_JUNIT_JUPITER_API_BEFORE_EACH);
		importRewrite.removeImport(ORG_JUNIT_RULE);
		importRewrite.removeImport(ORG_JUNIT_RULES_TEST_NAME);
	}
}
