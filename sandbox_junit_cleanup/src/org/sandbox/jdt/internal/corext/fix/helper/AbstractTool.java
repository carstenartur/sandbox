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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
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
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.base.AbstractToolBase;
import org.sandbox.jdt.internal.corext.fix.helper.base.JUnitConstants;
import org.sandbox.jdt.internal.corext.util.ASTNavigationUtils;
import org.sandbox.jdt.internal.corext.util.AnnotationUtils;
import org.sandbox.jdt.internal.corext.util.NamingUtils;
import org.sandbox.jdt.internal.corext.util.TypeCheckingUtils;

/**
 * Abstract base class for JUnit migration tools.
 * Provides common functionality for transforming JUnit 3/4 tests to JUnit 5.
 * 
 * @param <T> Type found in Visitor
 */
public abstract class AbstractTool<T> extends AbstractToolBase<T> {

	// Assertion Method Names
	protected static final Set<String> twoparam = Set.of("assertEquals", "assertNotEquals", "assertArrayEquals",
			"assertSame", "assertNotSame", "assertThat");
	protected static final Set<String> oneparam = Set.of("assertTrue", "assertFalse", "assertNull", "assertNotNull");
	private static final Set<String> noparam = Set.of("fail");
	protected static final Set<String> allassertionmethods = Stream.of(twoparam, oneparam, noparam).flatMap(Set::stream)
			.collect(Collectors.toSet());

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
	private void adaptExternalResourceHierarchy(ITypeBinding typeBinding, ASTRewrite rewrite, AST ast,
			ImportRewrite importRewrite, TextEditGroup group) {
		while (typeBinding != null) {
			// Stop when we reach ExternalResource itself
			if (JUnitConstants.ORG_JUNIT_RULES_EXTERNAL_RESOURCE.equals(typeBinding.getQualifiedName())) {
				break;
			}

			// Process types that extend ExternalResource
			if (isExternalResource(typeBinding, JUnitConstants.ORG_JUNIT_RULES_EXTERNAL_RESOURCE)) {
				TypeDeclaration typeDecl= ASTNavigationUtils.findTypeDeclarationInProject(typeBinding);
				if (typeDecl != null) {
					adaptTypeDeclaration(typeDecl, rewrite, ast, importRewrite, group);
				}
			}

			typeBinding= typeBinding.getSuperclass();
		}
	}

	/**
	 * Renames super.before() and super.after() calls to match JUnit 5 lifecycle method names.
	 * Also ensures that the ExtensionContext parameter is passed to super calls.
	 * 
	 * @param oldMethodName the old method name (e.g., "before")
	 * @param newMethodName the new method name (e.g., "beforeEach")
	 * @param method the method containing super calls to update
	 * @param rewriter the AST rewriter
	 * @param ast the AST instance
	 * @param group the text edit group
	 */
	private void adaptSuperBeforeCalls(String oldMethodName, String newMethodName, MethodDeclaration method, ASTRewrite rewriter,
			AST ast, TextEditGroup group) {
		method.accept(new ASTVisitor() {
			@Override
			public boolean visit(SuperMethodInvocation node) {
				if (oldMethodName.equals(node.getName().getIdentifier())) {
					rewriter.replace(node.getName(), ast.newSimpleName(newMethodName), group);
					addContextArgumentIfMissing(node, rewriter, ast, group);
				}
				return super.visit(node);
			}
		});
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
	private void adaptTypeDeclaration(TypeDeclaration typeDecl, ASTRewrite globalRewrite, AST ast,
			ImportRewrite importRewrite, TextEditGroup group) {
		// Create separate rewriters if the type declaration is in a different compilation unit
		ASTRewrite rewriteToUse = getASTRewrite(typeDecl, ast, globalRewrite);
		ImportRewrite importRewriteToUse = getImportRewrite(typeDecl, ast, importRewrite);

		// Remove ExternalResource superclass
		removeSuperclassType(typeDecl, rewriteToUse, group);
		
		// Update lifecycle methods: before() -> beforeEach(), after() -> afterEach()
		updateLifecycleMethodsInClass(typeDecl, rewriteToUse, ast, group, importRewriteToUse, JUnitConstants.METHOD_BEFORE,
				JUnitConstants.METHOD_AFTER, JUnitConstants.METHOD_BEFORE_EACH, JUnitConstants.METHOD_AFTER_EACH);

		// Add required JUnit 5 callback imports
		importRewriteToUse.addImport(JUnitConstants.ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_EACH_CALLBACK);
		importRewriteToUse.addImport(JUnitConstants.ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_EACH_CALLBACK);

		// If we created a separate rewriter, commit the change
		if (rewriteToUse != globalRewrite) {
			createChangeForRewrite(ASTNavigationUtils.findCompilationUnit(typeDecl), rewriteToUse);
		}
	}

	/**
	 * Adds a @BeforeEach init method that captures the test name from TestInfo.
	 * 
	 * @param parentClass the class to add the method to
	 * @param rewriter the AST rewriter
	 * @param group the text edit group
	 */
	private void addBeforeEachInitMethod(TypeDeclaration parentClass, ASTRewrite rewriter, TextEditGroup group) {
		AST ast = parentClass.getAST();
		
		MethodDeclaration methodDeclaration = createInitMethod(ast);
		MarkerAnnotation beforeEachAnnotation = createBeforeEachAnnotation(ast);
		
		addMethodToClass(parentClass, methodDeclaration, beforeEachAnnotation, rewriter, group);
	}

	/**
	 * Creates the init method that assigns testInfo.getDisplayName() to this.testName.
	 * 
	 * @param ast the AST instance
	 * @return the method declaration
	 */
	private MethodDeclaration createInitMethod(AST ast) {
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
	private Block createInitMethodBody(AST ast) {
		Block body = ast.newBlock();
		
		// Create assignment: this.testName = testInfo.getDisplayName()
		Assignment assignment = ast.newAssignment();
		
		// Left side: this.testName
		FieldAccess fieldAccess = ast.newFieldAccess();
		fieldAccess.setExpression(ast.newThisExpression());
		fieldAccess.setName(ast.newSimpleName(JUnitConstants.TEST_NAME));
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
	private MarkerAnnotation createBeforeEachAnnotation(AST ast) {
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
	private void addMethodToClass(TypeDeclaration parentClass, MethodDeclaration method, 
			MarkerAnnotation annotation, ASTRewrite rewriter, TextEditGroup group) {
		// Add method to class
		ListRewrite classBodyRewrite = rewriter.getListRewrite(parentClass, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
		classBodyRewrite.insertFirst(method, group);
		
		// Add annotation to method
		ListRewrite modifierRewrite = rewriter.getListRewrite(method, MethodDeclaration.MODIFIERS2_PROPERTY);
		modifierRewrite.insertFirst(annotation, group);
	}

	/**
	 * Adds the ExtensionContext parameter to method invocations if not already present.
	 * Used when migrating JUnit 4 lifecycle methods to JUnit 5 callback interfaces.
	 * 
	 * @param node the method invocation node (MethodInvocation or SuperMethodInvocation)
	 * @param rewriter the AST rewriter
	 * @param ast the AST instance
	 * @param group the text edit group
	 */
	private void addContextArgumentIfMissing(ASTNode node, ASTRewrite rewriter, AST ast, TextEditGroup group) {
		ListRewrite argsRewrite;
		if (node instanceof MethodInvocation) {
			argsRewrite= rewriter.getListRewrite(node, MethodInvocation.ARGUMENTS_PROPERTY);
		} else if (node instanceof SuperMethodInvocation) {
			argsRewrite= rewriter.getListRewrite(node, SuperMethodInvocation.ARGUMENTS_PROPERTY);
		} else {
			return; // Only supports MethodInvocation and SuperMethodInvocation
		}

		// Check if context argument is already present
		boolean hasContextArgument= argsRewrite.getRewrittenList().stream().anyMatch(
				arg -> arg instanceof SimpleName && JUnitConstants.VARIABLE_NAME_CONTEXT.equals(((SimpleName) arg).getIdentifier()));

		if (!hasContextArgument) {
			argsRewrite.insertFirst(ast.newSimpleName(JUnitConstants.VARIABLE_NAME_CONTEXT), group);
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
	protected void addExtendWithAnnotation(ASTRewrite rewrite, AST ast, TextEditGroup group,
			ImportRewrite importRewriter, String className, FieldDeclaration field) {
		TypeDeclaration parentClass= getParentTypeDeclaration(field);
		if (parentClass == null) {
			return;
		}
		
		// Create @ExtendWith(ClassName.class) annotation
		SingleMemberAnnotation newAnnotation= ast.newSingleMemberAnnotation();
		newAnnotation.setTypeName(ast.newName(JUnitConstants.ANNOTATION_EXTEND_WITH));
		TypeLiteral newTypeLiteral= ast.newTypeLiteral();
		newTypeLiteral.setType(ast.newSimpleType(ast.newSimpleName(className)));
		newAnnotation.setValue(newTypeLiteral);
		
		// Add annotation to class
		ListRewrite modifierListRewrite= rewrite.getListRewrite(parentClass, TypeDeclaration.MODIFIERS2_PROPERTY);
		modifierListRewrite.insertFirst(newAnnotation, group);
		
		// Add import for @ExtendWith
		importRewriter.addImport(JUnitConstants.ORG_JUNIT_JUPITER_API_EXTENSION_EXTEND_WITH);
	}

	/**
	 * Adds a JUnit 5 callback interface to a type's super interface list if not already present.
	 * Used when refactoring ExternalResource implementations to implement callback interfaces.
	 * 
	 * @param listRewrite the list rewrite for the super interface types
	 * @param ast the AST instance
	 * @param simpleCallbackName the simple name of the callback interface (e.g., "BeforeEachCallback")
	 * @param group the text edit group
	 * @param importRewriter the import rewriter
	 * @param fullyQualifiedCallbackName the fully qualified name of the callback interface to import
	 */
	private void addInterfaceCallback(ListRewrite listRewrite, AST ast, String simpleCallbackName, TextEditGroup group,
			ImportRewrite importRewriter, String fullyQualifiedCallbackName) {
		// Check if the interface already exists in the list
		boolean hasCallback= listRewrite.getRewrittenList().stream().anyMatch(type -> type instanceof SimpleType
				&& simpleCallbackName.equals(((SimpleType) type).getName().getFullyQualifiedName()));

		if (!hasCallback) {
			// Add interface if it doesn't already exist
			listRewrite.insertLast(ast.newSimpleType(ast.newName(simpleCallbackName)), group);
		}
		importRewriter.addImport(fullyQualifiedCallbackName);
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
	private void addRegisterExtensionAnnotation(ASTNode node, ASTRewrite rewrite, AST ast, ImportRewrite importRewrite,
			TextEditGroup group) {
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
	private FieldDeclaration resolveFieldDeclaration(ASTNode node) {
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
	private void addRegisterExtensionToField(FieldDeclaration field, ASTRewrite rewrite, AST ast,
			ImportRewrite importRewrite, TextEditGroup group) {
		boolean hasRegisterExtension = AnnotationUtils.hasAnnotationBySimpleName(field.modifiers(), JUnitConstants.ANNOTATION_REGISTER_EXTENSION);

		ListRewrite listRewrite = rewrite.getListRewrite(field, FieldDeclaration.MODIFIERS2_PROPERTY);
		boolean hasPendingRegisterExtension = listRewrite.getRewrittenList().stream()
				.anyMatch(rewritten -> rewritten instanceof MarkerAnnotation && ((MarkerAnnotation) rewritten)
						.getTypeName().getFullyQualifiedName().equals(JUnitConstants.ANNOTATION_REGISTER_EXTENSION));

		if (!hasRegisterExtension && !hasPendingRegisterExtension) {
			MarkerAnnotation registerExtensionAnnotation = ast.newMarkerAnnotation();
			registerExtensionAnnotation.setTypeName(ast.newName(JUnitConstants.ANNOTATION_REGISTER_EXTENSION));
			listRewrite.insertFirst(registerExtensionAnnotation, group);
			importRewrite.addImport(JUnitConstants.ORG_JUNIT_JUPITER_API_EXTENSION_REGISTER_EXTENSION);
		}
	}

	/**
	 * Adds a private String field named 'testName' to the class.
	 * Used when migrating JUnit 4 TestName rule to JUnit 5 TestInfo parameter.
	 * 
	 * @param parentClass the class to add the field to
	 * @param rewriter the AST rewriter
	 * @param group the text edit group
	 */
	private void addTestNameField(TypeDeclaration parentClass, ASTRewrite rewriter, TextEditGroup group) {
		AST ast= parentClass.getAST();
		VariableDeclarationFragment fragment= ast.newVariableDeclarationFragment();
		fragment.setName(ast.newSimpleName(JUnitConstants.TEST_NAME));

		FieldDeclaration fieldDeclaration= ast.newFieldDeclaration(fragment);
		fieldDeclaration.setType(ast.newSimpleType(ast.newName("String")));
		fieldDeclaration.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));

		ListRewrite listRewrite= rewriter.getListRewrite(parentClass, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
		listRewrite.insertFirst(fieldDeclaration, group);
	}

	/**
	 * Creates a compilation unit change for the given AST rewrite.
	 * Used when creating changes in separate compilation units during refactoring.
	 * 
	 * @param compilationUnit the compilation unit being modified
	 * @param rewrite the AST rewrite to apply
	 * @return the compilation unit change
	 */
	private CompilationUnitChange createChangeForRewrite(CompilationUnit compilationUnit, ASTRewrite rewrite) {
		try {
			// Access the IDocument of the CompilationUnit
			IDocument document= getDocumentForCompilationUnit(compilationUnit);

			// Describe changes (but don't apply them)
			TextEdit edits= rewrite.rewriteAST(document, null);

			// Create a TextChange object
			CompilationUnitChange change= new CompilationUnitChange("JUnit Migration",
					(ICompilationUnit) compilationUnit.getJavaElement());
			change.setEdit(edits);

			// Optional: Add comments or markers
			change.addTextEditGroup(new TextEditGroup("Migrate JUnit", edits));

			return change;

		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Error creating change for rewrite: " + e.getMessage(), e);
		}
	}

	/**
	 * Creates a lifecycle callback method for JUnit 5 extension interfaces.
	 * Used when converting ExternalResource before()/after() methods to callback methods.
	 * 
	 * @param ast the AST instance
	 * @param methodName the callback method name (e.g., "beforeEach", "afterEach")
	 * @param paramType the parameter type name (e.g., "ExtensionContext")
	 * @param oldBody the body from the original lifecycle method (will be copied)
	 * @param group the text edit group
	 * @return the new method declaration
	 */
	private MethodDeclaration createLifecycleCallbackMethod(AST ast, String methodName, String paramType, Block oldBody,
			TextEditGroup group) {

		MethodDeclaration method= ast.newMethodDeclaration();
		method.setName(ast.newSimpleName(methodName));
		method.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));
		method.setReturnType2(ast.newPrimitiveType(PrimitiveType.VOID));

		// Add the ExtensionContext (or similar) parameter
		SingleVariableDeclaration param= ast.newSingleVariableDeclaration();
		param.setType(ast.newSimpleType(ast.newName(paramType)));
		param.setName(ast.newSimpleName(JUnitConstants.VARIABLE_NAME_CONTEXT));
		method.parameters().add(param);

		// Copy the body from the old method
		if (oldBody != null) {
			Block newBody= (Block) ASTNode.copySubtree(ast, oldBody);
			method.setBody(newBody);
		}

		return method;
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
	private TypeDeclaration createNestedClassFromAnonymous(AnonymousClassDeclaration anonymousClass, String className,
			boolean fieldStatic, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter, TextEditGroup group) {

		// Create the new TypeDeclaration
		TypeDeclaration nestedClass= ast.newTypeDeclaration();
		nestedClass.setName(ast.newSimpleName(className));
		if (fieldStatic) {
			nestedClass.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.STATIC_KEYWORD));
		}

		// Add JUnit 5 callback interfaces (before/after each or all depending on static)
		nestedClass.superInterfaceTypes()
				.add(ast.newSimpleType(ast.newName(JUnitConstants.ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_EACH_CALLBACK)));
		nestedClass.superInterfaceTypes()
				.add(ast.newSimpleType(ast.newName(JUnitConstants.ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_EACH_CALLBACK)));
		importRewriter.addImport(JUnitConstants.ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_EACH_CALLBACK);
		importRewriter.addImport(JUnitConstants.ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_EACH_CALLBACK);

		// Transfer lifecycle methods from anonymous class to new class
		ListRewrite bodyRewrite= rewriter.getListRewrite(nestedClass, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
		for (Object decl : anonymousClass.bodyDeclarations()) {
			if (decl instanceof MethodDeclaration) {
				MethodDeclaration method= (MethodDeclaration) decl;

				// Convert before() -> beforeEach(ExtensionContext) and after() -> afterEach(ExtensionContext)
				if (isLifecycleMethod(method, JUnitConstants.METHOD_BEFORE)) {
					MethodDeclaration beforeEachMethod= createLifecycleCallbackMethod(ast, JUnitConstants.METHOD_BEFORE_EACH,
							JUnitConstants.EXTENSION_CONTEXT, method.getBody(), group);
					bodyRewrite.insertLast(beforeEachMethod, group);
				} else if (isLifecycleMethod(method, JUnitConstants.METHOD_AFTER)) {
					MethodDeclaration afterEachMethod= createLifecycleCallbackMethod(ast, JUnitConstants.METHOD_AFTER_EACH,
							JUnitConstants.EXTENSION_CONTEXT, method.getBody(), group);
					bodyRewrite.insertLast(afterEachMethod, group);
				}
			}
		}

		// Add the new class to the enclosing type
		TypeDeclaration parentType= ASTNavigationUtils.findEnclosingTypeDeclaration(anonymousClass);
		if (parentType != null) {
			ListRewrite enclosingBodyRewrite= rewriter.getListRewrite(parentType,
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
	private void ensureClassInstanceRewrite(ClassInstanceCreation classInstanceCreation, ASTRewrite rewriter,
	                                        ImportRewrite importRewriter, TextEditGroup group) {
	    removeExternalResourceSuperclass(classInstanceCreation, rewriter, importRewriter, group);
	    
	    // Add required JUnit 5 callback imports
	    importRewriter.addImport(JUnitConstants.ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_EACH_CALLBACK);
	    importRewriter.addImport(JUnitConstants.ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_EACH_CALLBACK);
	    importRewriter.addImport(JUnitConstants.ORG_JUNIT_JUPITER_API_EXTENSION_EXTENSION_CONTEXT);
	}

	/**
	 * Ensures that a method declaration has an ExtensionContext parameter.
	 * Adds the parameter if missing. Used when converting ExternalResource lifecycle
	 * methods to JUnit 5 callback interface methods.
	 * 
	 * @param method the method declaration to check and update
	 * @param rewrite the AST rewriter
	 * @param ast the AST instance
	 * @param group the text edit group
	 * @param importRewrite the import rewriter
	 */
	private void ensureExtensionContextParameter(MethodDeclaration method, ASTRewrite rewrite, AST ast,
			TextEditGroup group, ImportRewrite importRewrite) {

		// Check if ExtensionContext parameter already exists (in AST or pending rewrites)
		boolean hasExtensionContext= method.parameters().stream()
				.anyMatch(param -> param instanceof SingleVariableDeclaration && isExtensionContext(
						(SingleVariableDeclaration) param, JUnitConstants.ORG_JUNIT_JUPITER_API_EXTENSION_EXTENSION_CONTEXT))
				|| rewrite.getListRewrite(method, MethodDeclaration.PARAMETERS_PROPERTY).getRewrittenList().stream()
						.anyMatch(param -> param instanceof SingleVariableDeclaration
								&& JUnitConstants.EXTENSION_CONTEXT.equals(((SingleVariableDeclaration) param).getType().toString()));

		if (!hasExtensionContext) {
			// Add ExtensionContext parameter
			SingleVariableDeclaration newParam= ast.newSingleVariableDeclaration();
			newParam.setType(ast.newSimpleType(ast.newName(JUnitConstants.EXTENSION_CONTEXT)));
			newParam.setName(ast.newSimpleName(JUnitConstants.VARIABLE_NAME_CONTEXT));
			ListRewrite listRewrite= rewrite.getListRewrite(method, MethodDeclaration.PARAMETERS_PROPERTY);
			listRewrite.insertLast(newParam, group);

			// Add import for ExtensionContext
			importRewrite.addImport(JUnitConstants.ORG_JUNIT_JUPITER_API_EXTENSION_EXTENSION_CONTEXT);
		}
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
	 * Gets all direct and indirect subclasses of the given type.
	 * Uses the JDT type hierarchy to discover subclasses in the project.
	 * 
	 * @param typeBinding the type binding to find subclasses for
	 * @return list of type bindings for all subclasses
	 */
	private List<ITypeBinding> getAllSubclasses(ITypeBinding typeBinding) {
		List<ITypeBinding> subclasses= new ArrayList<>();

		try {
			// Create the corresponding IType of the given ITypeBinding
			IType type= (IType) typeBinding.getJavaElement();

			// Create the type hierarchy for the given type within the project (null uses entire project)
			ITypeHierarchy typeHierarchy= type.newTypeHierarchy(null);

			// Iterate through all direct and indirect subtypes and add them to the list
			for (IType subtype : typeHierarchy.getAllSubtypes(type)) {
				ITypeBinding subtypeBinding= subtype.getAdapter(ITypeBinding.class);
				if (subtypeBinding != null) {
					subclasses.add(subtypeBinding);
				}
			}
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
		return subclasses;
	}

	private ASTRewrite getASTRewrite(ASTNode node, AST globalAST, ASTRewrite globalRewrite) {
	    return (node.getAST() == globalAST) ? globalRewrite : ASTRewrite.create(node.getAST());
	}

	private IDocument getDocumentForCompilationUnit(CompilationUnit compilationUnit) {
		if (compilationUnit == null || compilationUnit.getJavaElement() == null) {
			throw new IllegalArgumentException("Invalid CompilationUnit or missing JavaElement.");
		}

		ICompilationUnit icu= (ICompilationUnit) compilationUnit.getJavaElement();
		ITextFileBufferManager bufferManager= FileBuffers.getTextFileBufferManager();

		try {
			// Connect to the file corresponding to the CompilationUnit
			bufferManager.connect(icu.getPath(), LocationKind.IFILE, null);

			// Get the associated TextFileBuffer
			ITextFileBuffer textFileBuffer= bufferManager.getTextFileBuffer(icu.getPath(), LocationKind.IFILE);

			if (textFileBuffer == null) {
				throw new RuntimeException("No text file buffer found for the provided compilation unit.");
			}

			// Return the document
			return textFileBuffer.getDocument();
		} catch (CoreException e) {
			throw new RuntimeException("Failed to connect to text file buffer: " + e.getMessage(), e);
		} finally {
			try {
				// Optional: Disconnect if no further changes are pending
				bufferManager.disconnect(icu.getPath(), LocationKind.IFILE, null);
			} catch (CoreException e) {
				// Disconnection failed, but we only log it
				e.printStackTrace();
			}
		}
	}

	private ImportRewrite getImportRewrite(ASTNode node, AST globalAST, ImportRewrite globalImportRewrite) {
	    CompilationUnit compilationUnit = ASTNavigationUtils.findCompilationUnit(node);
	    return (node.getAST() == globalAST) ? globalImportRewrite : ImportRewrite.create(compilationUnit, true);
	}

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
	            .filter(java.util.Objects::nonNull) // Only consider non-null results
	            .findFirst()
	            .orElse(null);
	}

	private ASTNode getTypeDefinitionFromFragment(VariableDeclarationFragment fragment, CompilationUnit cu) {
	    // Check initializer
	    Expression initializer = fragment.getInitializer();
	    if (initializer instanceof ClassInstanceCreation) {
	        ClassInstanceCreation classInstanceCreation = (ClassInstanceCreation) initializer;

	        // Check for anonymous class
	        AnonymousClassDeclaration anonymousClass = classInstanceCreation.getAnonymousClassDeclaration();
	        if (anonymousClass != null) {
	            return anonymousClass; // Anonymous class found
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

	    return null; // No matching type definition found
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
	        if (bodyDecl instanceof MethodDeclaration) {
	            MethodDeclaration method = (MethodDeclaration) bodyDecl;
	            if (method.isConstructor()) {
	                hasConstructor = true;
	                if (method.parameters().isEmpty() && method.getBody() != null
	                        && method.getBody().statements().isEmpty()) {
	                    return true; // Empty default constructor found
	                }
	            }
	        }
	    }
	    return !hasConstructor; // No constructor present
	}

	/**
	 * Checks if a variable declaration fragment represents an anonymous class.
	 * 
	 * @param fragment the variable declaration fragment to check
	 * @return true if the fragment's initializer is an anonymous class
	 */
	public boolean isAnonymousClass(VariableDeclarationFragment fragment) {
	    Expression initializer = fragment.getInitializer();
	    return initializer instanceof ClassInstanceCreation 
	           && ((ClassInstanceCreation) initializer).getAnonymousClassDeclaration() != null;
	}

	/**
	 * Checks if the given type binding directly matches ExternalResource.
	 * 
	 * @param fieldTypeBinding the type binding to check
	 * @return true if the type is exactly ExternalResource
	 */
	protected boolean isDirect(ITypeBinding fieldTypeBinding) {
	    return JUnitConstants.ORG_JUNIT_RULES_EXTERNAL_RESOURCE.equals(fieldTypeBinding.getQualifiedName());
	}
	
	/**
	 * Checks if the given type binding directly extends ExternalResource.
	 * 
	 * @param binding the type binding to check
	 * @return true if the type's superclass is ExternalResource
	 */
	protected boolean isDirectlyExtendingExternalResource(ITypeBinding binding) {
	    ITypeBinding superclass = binding.getSuperclass();
	    return superclass != null && JUnitConstants.ORG_JUNIT_RULES_EXTERNAL_RESOURCE.equals(superclass.getQualifiedName());
	}

	private boolean isExtensionContext(SingleVariableDeclaration param, String className) {
	    ITypeBinding binding = param.getType().resolveBinding();
	    return binding != null && className.equals(binding.getQualifiedName());
	}
	
	private boolean isExternalResource(FieldDeclaration field, String typeToLookup) {
	    ITypeBinding binding = ((VariableDeclarationFragment) field.fragments().get(0)).resolveBinding().getType();
	    return isTypeOrSubtype(binding, typeToLookup);
	}

	/**
	 * Checks if the given type binding is or extends ExternalResource.
	 * 
	 * @param typeBinding the type binding to check
	 * @param typeToLookup the fully qualified type name to look for
	 * @return true if the type is or extends the specified type
	 */
	protected boolean isExternalResource(ITypeBinding typeBinding, String typeToLookup) {
	    return isTypeOrSubtype(typeBinding, typeToLookup);
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
	 * Checks if a method is a lifecycle method with the given name.
	 * 
	 * @param method the method declaration to check
	 * @param methodName the expected method name
	 * @return true if the method name matches
	 */
	protected boolean isLifecycleMethod(MethodDeclaration method, String methodName) {
	    return methodName.equals(method.getName().getIdentifier());
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
		if (!shouldProcessNode(node)) {
			return;
		}

		CallbackConfig callbackConfig = determineCallbackConfig(fieldStatic);
		
		if (field != null) {
			processExternalResourceField(field, rewriter, ast, group, importRewriter);
		}
		
		if (isDirectlyExtendingExternalResource(node.resolveBinding())) {
			refactorToImplementCallbacks(node, rewriter, ast, group, importRewriter, 
					callbackConfig.beforeCallback, callbackConfig.afterCallback,
					callbackConfig.importBeforeCallback, callbackConfig.importAfterCallback);
		}

		updateLifecycleMethodsInClass(node, rewriter, ast, group, importRewriter, 
				JUnitConstants.METHOD_BEFORE, JUnitConstants.METHOD_AFTER,
				fieldStatic ? JUnitConstants.METHOD_BEFORE_ALL : JUnitConstants.METHOD_BEFORE_EACH,
				fieldStatic ? JUnitConstants.METHOD_AFTER_ALL : JUnitConstants.METHOD_AFTER_EACH);
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
	private void processExternalResourceField(FieldDeclaration field, ASTRewrite rewriter, AST ast, 
			TextEditGroup group, ImportRewrite importRewriter) {
		String ruleAnnotation = null;
		
		if (AnnotationUtils.isAnnotatedWith(field, JUnitConstants.ORG_JUNIT_RULE) 
				&& isExternalResource(field, JUnitConstants.ORG_JUNIT_RULES_EXTERNAL_RESOURCE)) {
			ruleAnnotation = JUnitConstants.ORG_JUNIT_RULE;
		} else if (AnnotationUtils.isAnnotatedWith(field, JUnitConstants.ORG_JUNIT_CLASS_RULE) 
				&& isExternalResource(field, JUnitConstants.ORG_JUNIT_RULES_EXTERNAL_RESOURCE)) {
			ruleAnnotation = JUnitConstants.ORG_JUNIT_CLASS_RULE;
		}
		
		if (ruleAnnotation != null) {
			removeRuleAnnotation(field, rewriter, group, importRewriter, ruleAnnotation);
			addRegisterExtensionAnnotation(field, rewriter, ast, importRewriter, group);
			ITypeBinding fieldType = ((VariableDeclarationFragment) field.fragments().get(0))
					.resolveBinding().getType();
			adaptExternalResourceHierarchy(fieldType, rewriter, ast, importRewriter, group);
		}
	}

	/**
	 * Determines the appropriate callback configuration based on whether the field is static.
	 * 
	 * @param fieldStatic whether the field is static
	 * @return the callback configuration with callback names and import paths
	 */
	private CallbackConfig determineCallbackConfig(boolean fieldStatic) {
		if (fieldStatic) {
			return new CallbackConfig(
					JUnitConstants.BEFORE_ALL_CALLBACK,
					JUnitConstants.AFTER_ALL_CALLBACK,
					JUnitConstants.ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_ALL_CALLBACK,
					JUnitConstants.ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_ALL_CALLBACK
			);
		} else {
			return new CallbackConfig(
					JUnitConstants.BEFORE_EACH_CALLBACK,
					JUnitConstants.AFTER_EACH_CALLBACK,
					JUnitConstants.ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_EACH_CALLBACK,
					JUnitConstants.ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_EACH_CALLBACK
			);
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

		CallbackConfig(String beforeCallback, String afterCallback, 
				String importBeforeCallback, String importAfterCallback) {
			this.beforeCallback = beforeCallback;
			this.afterCallback = afterCallback;
			this.importBeforeCallback = importBeforeCallback;
			this.importAfterCallback = importAfterCallback;
		}
	}

	// Use a method to create a CompilationUnit from an ICompilationUnit
	private CompilationUnit parseCompilationUnit(ICompilationUnit iCompilationUnit) {
	    return ASTNavigationUtils.parseCompilationUnit(iCompilationUnit);
	}

	public void process(Annotation node, IJavaProject jproject, ASTRewrite rewrite, AST ast, TextEditGroup group,
			ImportRewrite importRewriter, CompilationUnit cu, String className) {
		if (!JUnitConstants.ORG_JUNIT_RULE.equals(node.resolveTypeBinding().getQualifiedName())) {
			return;
		}
		FieldDeclaration field= ASTNodes.getParent(node, FieldDeclaration.class);
		ITypeBinding fieldTypeBinding= ((VariableDeclarationFragment) field.fragments().get(0)).resolveBinding()
				.getType();
		if (!isExternalResource(fieldTypeBinding, JUnitConstants.ORG_JUNIT_RULES_EXTERNAL_RESOURCE)
				|| fieldTypeBinding.isAnonymous()) {
			return;
		}
		if (isDirect(fieldTypeBinding)) {
			rewrite.remove(field, group);
			importRewriter.removeImport(JUnitConstants.ORG_JUNIT_RULE);
		}
		addExtendWithAnnotation(rewrite, ast, group, importRewriter, className, field);
		importRewriter.removeImport(JUnitConstants.ORG_JUNIT_RULES_EXTERNAL_RESOURCE);
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

	private void processMethod(MethodDeclaration method, ASTRewrite rewriter, AST ast, TextEditGroup group,
			ImportRewrite importRewriter, String methodname, String methodnamejunit5) {
		setPublicVisibilityIfProtected(method, rewriter, ast, group);
		adaptSuperBeforeCalls(methodname, methodnamejunit5, method, rewriter, ast, group);
		removeThrowsThrowable(method, rewriter, group);
		rewriter.replace(method.getName(), ast.newSimpleName(methodnamejunit5), group);
		ensureExtensionContextParameter(method, rewriter, ast, group, importRewriter);
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
	protected void refactorAnonymousClassToImplementCallbacks(
	        AnonymousClassDeclaration anonymousClass, FieldDeclaration fieldDeclaration, boolean fieldStatic,
	        ASTRewrite rewriter, AST ast, TextEditGroup group, ImportRewrite importRewriter) {

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

	        replaceFieldWithExtensionDeclaration(classInstanceCreation, nestedClassName, fieldStatic, rewriter, ast, group,
	                importRewriter);
	    }
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
	protected void refactorTestnameInClass(TextEditGroup group, ASTRewrite rewriter, AST ast,
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
	    importRewrite.addImport(JUnitConstants.ORG_JUNIT_JUPITER_API_TEST_INFO);
	    importRewrite.addImport(JUnitConstants.ORG_JUNIT_JUPITER_API_BEFORE_EACH);
	    importRewrite.removeImport(JUnitConstants.ORG_JUNIT_RULE);
	    importRewrite.removeImport(JUnitConstants.ORG_JUNIT_RULES_TEST_NAME);
	}

	protected void refactorTestnameInClassAndSubclasses(TextEditGroup group, ASTRewrite rewriter, AST ast,
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

	private void refactorToImplementCallbacks(TypeDeclaration node, ASTRewrite rewriter, AST ast, TextEditGroup group,
	                                           ImportRewrite importRewriter, String beforeCallback, String afterCallback,
	                                           String importBeforeCallback, String importAfterCallback) {

	    if (node == null || rewriter == null || ast == null || importRewriter == null) {
	        return;
	    }

	    ASTRewrite rewriteToUse = getASTRewrite(node, ast, rewriter);
	    ImportRewrite importRewriteToUse = getImportRewrite(node, ast, importRewriter);

	    rewriteToUse.remove(node.getSuperclassType(), group);
	    importRewriteToUse.removeImport(JUnitConstants.ORG_JUNIT_RULES_EXTERNAL_RESOURCE);

	    ListRewrite listRewrite = rewriteToUse.getListRewrite(node, TypeDeclaration.SUPER_INTERFACE_TYPES_PROPERTY);
	    addInterfaceCallback(listRewrite, ast, beforeCallback, group, importRewriteToUse, importBeforeCallback);
	    addInterfaceCallback(listRewrite, ast, afterCallback, group, importRewriteToUse, importAfterCallback);

	    if (rewriteToUse != rewriter) {
	        createChangeForRewrite(ASTNavigationUtils.findCompilationUnit(node), rewriteToUse);
	    }
	}

	private void removeExternalResourceSuperclass(ClassInstanceCreation anonymousClass, ASTRewrite rewrite,
			ImportRewrite importRewriter, TextEditGroup group) {
		// Check if the anonymous class inherits from ExternalResource
		ITypeBinding typeBinding= anonymousClass.resolveTypeBinding();
		if (typeBinding.getSuperclass() != null
				&& JUnitConstants.ORG_JUNIT_RULES_EXTERNAL_RESOURCE.equals(typeBinding.getSuperclass().getQualifiedName())) {

			// Remove the superclass by replacing the type in the ClassInstanceCreation
			Type type= anonymousClass.getType();
			if (type != null) {
				rewrite.replace(type,
						anonymousClass.getAST().newSimpleType(anonymousClass.getAST().newSimpleName("Object")), group);
			}

			// Remove the import of the superclass
			importRewriter.removeImport(JUnitConstants.ORG_JUNIT_RULES_EXTERNAL_RESOURCE);
		}
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
	private void removeRuleAnnotation(BodyDeclaration declaration, ASTRewrite rewriter, TextEditGroup group,
			ImportRewrite importRewriter, String annotationclass) {
		List<?> modifiers= declaration.modifiers();
		for (Object modifier : modifiers) {
			if (modifier instanceof Annotation) {
				Annotation annotation= (Annotation) modifier;
				ITypeBinding binding= annotation.resolveTypeBinding();
				if (binding != null && binding.getQualifiedName().equals(annotationclass)) {
					rewriter.remove(annotation, group);
					importRewriter.removeImport(annotationclass);
					break;
				}
			}
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
	private void removeSuperclassType(TypeDeclaration typeDecl, ASTRewrite rewrite, TextEditGroup group) {
		if (typeDecl.getSuperclassType() != null) {
			rewrite.remove(typeDecl.getSuperclassType(), group);
		}
	}

	/**
	 * Removes "throws Throwable" from a method declaration.
	 * JUnit 5 lifecycle methods don't need to declare Throwable.
	 * 
	 * @param method the method declaration to modify
	 * @param rewriter the AST rewriter
	 * @param group the text edit group
	 */
	private void removeThrowsThrowable(MethodDeclaration method, ASTRewrite rewriter, TextEditGroup group) {
		List<?> thrownExceptionTypes= method.thrownExceptionTypes();
		for (Object exceptionType : thrownExceptionTypes) {
			if (exceptionType instanceof SimpleType) {
				SimpleType exception= (SimpleType) exceptionType;
				if ("Throwable".equals(exception.getName().getFullyQualifiedName())) {
					ListRewrite listRewrite= rewriter.getListRewrite(method,
							MethodDeclaration.THROWN_EXCEPTION_TYPES_PROPERTY);
					listRewrite.remove(exception, group);
					break; // Only one Throwable should be present
				}
			}
		}
	}

	/**
	 * Reorders method invocation parameters according to the specified order.
	 * Used internally to reorder JUnit assertion parameters.
	 * 
	 * @param rewriter the AST rewriter
	 * @param node the method invocation to reorder
	 * @param group the text edit group
	 * @param order array specifying the new order (indices into current arguments)
	 */
	private void reorderParameters(ASTRewrite rewriter, MethodInvocation node, TextEditGroup group, int... order) {
		ListRewrite listRewrite= rewriter.getListRewrite(node, MethodInvocation.ARGUMENTS_PROPERTY);
		List<Expression> arguments= node.arguments();
		Expression[] newArguments= new Expression[arguments.size()];
		for (int i= 0; i < order.length; i++) {
			newArguments[i]= (Expression) ASTNode.copySubtree(node.getAST(), arguments.get(order[i]));
		}
		if (!isStringType(arguments.get(0), String.class)) {
			return;
		}
		for (int i= 0; i < arguments.size(); i++) {
			listRewrite.replace(arguments.get(i), newArguments[i], group);
		}
	}

	/**
	 * Reorders parameters in a method invocation to match JUnit 5 assertion parameter order.
	 * JUnit 5 places the message parameter last, whereas JUnit 4 placed it first.
	 * 
	 * @param node the method invocation to reorder
	 * @param rewriter the AST rewriter
	 * @param group the text edit group
	 * @param oneparam assertion methods with one value parameter
	 * @param twoparam assertion methods with two value parameters
	 */
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

	private void replaceFieldWithExtensionDeclaration(ClassInstanceCreation classInstanceCreation,
			String nestedClassName, boolean fieldStatic, ASTRewrite rewriter, AST ast, TextEditGroup group,
			ImportRewrite importRewriter) {

		FieldDeclaration fieldDecl= ASTNodes.getParent(classInstanceCreation,
				FieldDeclaration.class);
		if (fieldDecl != null) {
			// Remove the @Rule annotation
			removeRuleAnnotation(fieldDecl, rewriter, group, importRewriter, JUnitConstants.ORG_JUNIT_RULE);

			// Add the @RegisterExtension annotation
			addRegisterExtensionAnnotation(fieldDecl, rewriter, ast, importRewriter, group);

			// Change the type of the FieldDeclaration
			Type newType= ast.newSimpleType(ast.newName(nestedClassName));
			rewriter.set(fieldDecl.getType(), SimpleType.NAME_PROPERTY, newType, group);

			// Add the initialization
			for (Object fragment : fieldDecl.fragments()) {
				if (fragment instanceof VariableDeclarationFragment) {
					VariableDeclarationFragment fragmentNode= (VariableDeclarationFragment) fragment;
					ClassInstanceCreation newInstance= ast.newClassInstanceCreation();
					newInstance.setType(ast.newSimpleType(ast.newName(nestedClassName)));
					rewriter.set(fragmentNode, VariableDeclarationFragment.INITIALIZER_PROPERTY, newInstance, group);
				}
			}
		}
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
	public void rewrite(JUnitCleanUpFixCore upp, ReferenceHolder<Integer, JunitHolder> hit, CompilationUnitRewrite cuRewrite, TextEditGroup group) {
		ASTRewrite rewriter= cuRewrite.getASTRewrite();
		AST ast= cuRewrite.getRoot().getAST();
		ImportRewrite importRewriter= cuRewrite.getImportRewrite();
		JunitHolder junitHolder= hit.get(hit.size() - 1);
		process2Rewrite(group, rewriter, ast, importRewriter, junitHolder);
		hit.remove(hit.size() - 1);
	}

	private void setPublicVisibilityIfProtected(MethodDeclaration method, ASTRewrite rewrite, AST ast,
			TextEditGroup group) {
		// Iterate through modifiers and search for a protected modifier
		for (Object modifier : method.modifiers()) {
			if (modifier instanceof Modifier) {
				Modifier mod= (Modifier) modifier;
				if (mod.isProtected()) {
					ListRewrite modifierRewrite= rewrite.getListRewrite(method, MethodDeclaration.MODIFIERS2_PROPERTY);
					Modifier publicModifier= ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD);
					modifierRewrite.replace(mod, publicModifier, group);
					break; // Stop the loop as soon as the modifier is replaced
				}
			}
		}
	}

	private boolean shouldProcessNode(TypeDeclaration node) {
		ITypeBinding binding= node.resolveBinding();
		return binding != null && isExternalResource(binding, JUnitConstants.ORG_JUNIT_RULES_EXTERNAL_RESOURCE);
	}

	private void updateLifecycleMethodsInClass(TypeDeclaration node, ASTRewrite globalRewrite, AST ast,
			TextEditGroup group, ImportRewrite importRewrite, String methodbefore, String methodafter,
			String methodbeforeeach, String methodaftereach) {

		for (MethodDeclaration method : node.getMethods()) {
			if (isLifecycleMethod(method, methodbefore)) {
				processLifecycleMethod(node, method, globalRewrite, ast, group, importRewrite, methodbefore, methodbeforeeach);
			} else if (isLifecycleMethod(method, methodafter)) {
				processLifecycleMethod(node, method, globalRewrite, ast, group, importRewrite, methodafter, methodaftereach);
			}
		}
	}

	/**
	 * Processes a lifecycle method by setting up the appropriate rewriters and applying changes.
	 */
	private void processLifecycleMethod(TypeDeclaration node, MethodDeclaration method, ASTRewrite globalRewrite,
			AST ast, TextEditGroup group, ImportRewrite importRewrite, String oldMethodName, String newMethodName) {
		ASTRewrite rewriteToUse = getASTRewrite(node, ast, globalRewrite);
		ImportRewrite importRewriteToUse = getImportRewrite(node, ast, importRewrite);

		processMethod(method, rewriteToUse, ast, group, importRewriteToUse, oldMethodName, newMethodName);

		if (rewriteToUse != globalRewrite) {
			createChangeForRewrite(ASTNavigationUtils.findCompilationUnit(node), rewriteToUse);
		}
	}

	private void updateMethodReferences(TypeDeclaration parentClass, AST ast, ASTRewrite rewriter, TextEditGroup group) {
	    for (MethodDeclaration method : parentClass.getMethods()) {
	        if (method.getBody() != null) {
	            method.getBody().accept(new ASTVisitor() {
	                @Override
	                public boolean visit(MethodInvocation node) {
	                    if (node.getExpression() != null && JUnitConstants.ORG_JUNIT_RULES_TEST_NAME
	                            .equals(node.getExpression().resolveTypeBinding().getQualifiedName())) {
	                        SimpleName newFieldAccess = ast.newSimpleName(JUnitConstants.TEST_NAME);
	                        rewriter.replace(node, newFieldAccess, group);
	                    }
	                    return super.visit(node);
	                }
	            });
	        }
	    }
	}
}
