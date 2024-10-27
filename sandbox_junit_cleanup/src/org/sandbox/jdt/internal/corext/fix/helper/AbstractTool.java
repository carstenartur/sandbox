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
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
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
	private static final String METHOD_AFTER= "after";
	private static final String METHOD_BEFORE= "before";
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

	private void adaptBeforeAfterCallsInTestClass(TypeDeclaration testClass, String fieldName, ASTRewrite rewriter,
			AST ast, TextEditGroup group) {
		testClass.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation node) {
				if (node.getExpression() instanceof SimpleName) {
					SimpleName expr= (SimpleName) node.getExpression();
					if (expr.getIdentifier().equals(fieldName)) {
						String methodName= node.getName().getIdentifier();
						if (METHOD_BEFORE.equals(methodName)) {
							rewriter.replace(node.getName(), ast.newSimpleName(METHOD_BEFORE_EACH), group);
						}
						else if (METHOD_AFTER.equals(methodName)) {
							rewriter.replace(node.getName(), ast.newSimpleName(METHOD_AFTER_EACH), group);
						}
					}
				}
				return super.visit(node);
			}
		});
	}

	private void adaptExternalResourceHierarchy(ITypeBinding typeBinding, ASTRewrite rewrite, AST ast,
			ImportRewrite importRewrite, TextEditGroup group) {
		if (typeBinding == null) {
			return;
		}
		while (typeBinding != null && isExternalResource(typeBinding)) {
			IType type= (IType) typeBinding.getJavaElement();
			TypeDeclaration typeDecl= findTypeDeclaration(typeBinding.getJavaElement().getJavaProject(),
					type.getElementName());
			if (typeDecl != null) {
				removeSuperclassType(typeDecl, rewrite, group);
				addBeforeAndAfterEachCallbacks(typeDecl, rewrite, ast, importRewrite, group);
				updateLifecycleMethods(typeDecl, rewrite, ast, group, importRewrite);
				importRewrite.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_EACH_CALLBACK);
				importRewrite.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_EACH_CALLBACK);
			}
			typeBinding= typeBinding.getSuperclass();
		}
	}

	private void adaptSuperBeforeCalls(String vorher, String nachher, MethodDeclaration method, ASTRewrite rewriter,
			AST ast, TextEditGroup group) {
		method.accept(new ASTVisitor() {
			@Override
			public boolean visit(SuperMethodInvocation node) {
				if (vorher.equals(node.getName().getIdentifier())) {
					rewriter.replace(node.getName(), ast.newSimpleName(nachher), group);
					ListRewrite argumentsRewrite= rewriter.getListRewrite(node,
							SuperMethodInvocation.ARGUMENTS_PROPERTY);
					if (node.arguments().isEmpty()) {
						argumentsRewrite.insertFirst(ast.newSimpleName(VARIABLE_NAME_CONTEXT), group);
					}
				}
				return super.visit(node);
			}
		});
	}

	private void addBeforeAndAfterEachCallbacks(TypeDeclaration typeDecl, ASTRewrite rewrite, AST ast,
			ImportRewrite importRewrite, TextEditGroup group) {
		ListRewrite listRewrite= rewrite.getListRewrite(typeDecl, TypeDeclaration.SUPER_INTERFACE_TYPES_PROPERTY);
		listRewrite.insertLast(ast.newSimpleType(ast.newName(BEFORE_EACH_CALLBACK)), group);
		listRewrite.insertLast(ast.newSimpleType(ast.newName(AFTER_EACH_CALLBACK)), group);
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

	private void addInterfaceCallback(ListRewrite listRewrite, AST ast, String callbackName, TextEditGroup group) {
		listRewrite.insertLast(ast.newSimpleType(ast.newName(callbackName)), group);
	}

	private void addRegisterExtensionAnnotation(FieldDeclaration field, ASTRewrite rewrite, AST ast,
			ImportRewrite importRewrite, TextEditGroup group) {
		MarkerAnnotation registerExtensionAnnotation= ast.newMarkerAnnotation();
		registerExtensionAnnotation.setTypeName(ast.newName(ANNOTATION_REGISTER_EXTENSION));
		rewrite.getListRewrite(field, FieldDeclaration.MODIFIERS2_PROPERTY).insertFirst(registerExtensionAnnotation,
				group);
	}

	private void ensureExtensionContextParameter(MethodDeclaration method, ASTRewrite rewrite, AST ast,
			TextEditGroup group, ImportRewrite importRewrite) {
		boolean hasExtensionContext= method.parameters().stream()
				.anyMatch(param -> param instanceof SingleVariableDeclaration
						&& ((SingleVariableDeclaration) param).getType().toString().equals(EXTENSION_CONTEXT));
		if (!hasExtensionContext) {
			SingleVariableDeclaration newParam= ast.newSingleVariableDeclaration();
			newParam.setType(ast.newSimpleType(ast.newName(EXTENSION_CONTEXT)));
			newParam.setName(ast.newSimpleName(VARIABLE_NAME_CONTEXT));
			ListRewrite listRewrite= rewrite.getListRewrite(method, MethodDeclaration.PARAMETERS_PROPERTY);
			listRewrite.insertLast(newParam, group);
			importRewrite.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_EXTENSION_CONTEXT);
		}
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

	private boolean isAnnotatedWithRule(BodyDeclaration declaration) {
		for (Object modifier : declaration.modifiers()) {
			if (modifier instanceof Annotation) {
				Annotation annotation= (Annotation) modifier;
				ITypeBinding binding= annotation.resolveTypeBinding();
				if (binding != null && ORG_JUNIT_RULE.equals(binding.getQualifiedName())) {
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

	private boolean isLifecycleMethod(MethodDeclaration method, String methodName) {
		return method.getName().getIdentifier().equals(methodName);
	}

	private boolean isStringType(Expression expression) {
		ITypeBinding typeBinding= expression.resolveTypeBinding();
		return typeBinding != null && String.class.getCanonicalName().equals(typeBinding.getQualifiedName());
	}

	public void migrateRuleToRegisterExtensionAndAdaptHierarchy(TypeDeclaration testClass, ASTRewrite rewrite, AST ast,
			ImportRewrite importRewrite, TextEditGroup group, String varname) {
		adaptBeforeAfterCallsInTestClass(testClass, varname, rewrite, ast, group);
		for (FieldDeclaration field : testClass.getFields()) {
			if (isAnnotatedWithRule(field) && isExternalResource(field)) {
				removeRuleAnnotation(field, rewrite, group, importRewrite);
				addRegisterExtensionAnnotation(field, rewrite, ast, importRewrite, group);
				importRewrite.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_REGISTER_EXTENSION);
				ITypeBinding fieldType= ((VariableDeclarationFragment) field.fragments().get(0)).resolveBinding()
						.getType();
				adaptExternalResourceHierarchy(fieldType, rewrite, ast, importRewrite, group);
			}
		}
	}

	protected void modifyExternalResourceClass(TypeDeclaration node, ASTRewrite rewriter, AST ast,
			TextEditGroup group, ImportRewrite importRewriter) {
		ITypeBinding binding= node.resolveBinding();
		if (binding.isAnonymous() || !isExternalResource(binding) || !hasDefaultConstructorOrNoConstructor(node)) {
			return;
		}
		if (isDirectlyExtendingExternalResource(binding)) {
			refactorToImplementCallbacks(node, rewriter, ast, group, importRewriter);
		}
		for (MethodDeclaration method : node.getMethods()) {
			if (isLifecycleMethod(method, METHOD_BEFORE)) {
				adaptSuperBeforeCalls(METHOD_BEFORE, METHOD_BEFORE_EACH, method, rewriter, ast, group);
				removeThrowsThrowable(method, rewriter, group);
				refactorMethod(rewriter, ast, method, METHOD_BEFORE_EACH, group, importRewriter);
			} else if (isLifecycleMethod(method, METHOD_AFTER)) {
				adaptSuperBeforeCalls(METHOD_AFTER, METHOD_AFTER_EACH, method, rewriter, ast, group);
				refactorMethod(rewriter, ast, method, METHOD_AFTER_EACH, group, importRewriter);
			}
		}
		importRewriter.removeImport(ORG_JUNIT_RULE);
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

	private void refactorMethod(ASTRewrite rewriter, AST ast, MethodDeclaration method, String newMethodName,
			TextEditGroup group, ImportRewrite importRewriter) {
		rewriter.replace(method.getName(), ast.newSimpleName(newMethodName), group);
		ensureExtensionContextParameter(method, rewriter, ast, group, importRewriter);
	}

	private void refactorToImplementCallbacks(TypeDeclaration node, ASTRewrite rewriter, AST ast, TextEditGroup group,
			ImportRewrite importRewriter) {
		rewriter.remove(node.getSuperclassType(), group);
		importRewriter.removeImport(ORG_JUNIT_RULES_EXTERNAL_RESOURCE);
		ListRewrite listRewrite= rewriter.getListRewrite(node, TypeDeclaration.SUPER_INTERFACE_TYPES_PROPERTY);
		addInterfaceCallback(listRewrite, ast, BEFORE_EACH_CALLBACK, group);
		addInterfaceCallback(listRewrite, ast, AFTER_EACH_CALLBACK, group);
		importRewriter.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_EACH_CALLBACK);
		importRewriter.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_EACH_CALLBACK);
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

	private void updateLifecycleMethods(TypeDeclaration typeDecl, ASTRewrite rewrite, AST ast, TextEditGroup group,
			ImportRewrite importRewrite) {
		for (MethodDeclaration method : typeDecl.getMethods()) {
			if (method.getName().getIdentifier().equals(METHOD_BEFORE)) {
				removeThrowsThrowable(method, rewrite, group);
				adaptSuperBeforeCalls(METHOD_BEFORE, METHOD_BEFORE_EACH, method, rewrite, ast, group);
				rewrite.replace(method.getName(), ast.newSimpleName(METHOD_BEFORE_EACH), group);
				ensureExtensionContextParameter(method, rewrite, ast, group, importRewrite);
			} else if (method.getName().getIdentifier().equals(METHOD_AFTER)) {
				adaptSuperBeforeCalls(METHOD_AFTER, METHOD_AFTER_EACH, method, rewrite, ast, group);
				rewrite.replace(method.getName(), ast.newSimpleName(METHOD_AFTER_EACH), group);
				ensureExtensionContextParameter(method, rewrite, ast, group, importRewrite);
			}
		}
	}
}
