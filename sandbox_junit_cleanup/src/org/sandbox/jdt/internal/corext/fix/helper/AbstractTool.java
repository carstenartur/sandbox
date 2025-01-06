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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

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
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
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
import org.eclipse.jdt.internal.corext.dom.ScopeAnalyzer;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;

/**
 * @param <T> Type found in Visitor
 */
public abstract class AbstractTool<T> {

    /* === 1) Annotationen (direkt im Code verwendet) === */
    private static final String ANNOTATION_REGISTER_EXTENSION = "RegisterExtension";
    private static final String ANNOTATION_EXTEND_WITH       = "ExtendWith";
    protected static final String ANNOTATION_AFTER_EACH      = "AfterEach";
    protected static final String ANNOTATION_BEFORE_EACH     = "BeforeEach";
    protected static final String ANNOTATION_AFTER_ALL       = "AfterAll";
    protected static final String ANNOTATION_BEFORE_ALL      = "BeforeAll";
    protected static final String ANNOTATION_DISABLED        = "Disabled";
    protected static final String ANNOTATION_TEST            = "Test";
    protected static final String ANNOTATION_SELECT_CLASSES  = "SelectClasses";
    protected static final String ANNOTATION_SUITE           = "Suite";

    /* === 2) Methodennamen === */
    private static final String METHOD_AFTER_EACH   = "afterEach";
    private static final String METHOD_BEFORE_EACH  = "beforeEach";
    private static final String METHOD_AFTER_ALL    = "afterAll";
    private static final String METHOD_BEFORE_ALL   = "beforeAll";
    protected static final String METHOD_AFTER      = "after";
    protected static final String METHOD_BEFORE     = "before";

    /* === 3) Interne Klassen-/Interface-Namen (z. B. Assertions, Assumptions, etc.) === */
    protected static final String ASSERTIONS   = "Assertions";
    protected static final String ASSUMPTIONS  = "Assumptions";
    private static final String TEST_NAME      = "testName";
    private static final String VARIABLE_NAME_CONTEXT = "context";
    private static final String EXTENSION_CONTEXT     = "ExtensionContext";

    /* === 4) Vollqualifizierte Referenzen === */

    // --- 4a) JUnit 4 Referenzen ---
    protected static final String ORG_JUNIT_AFTER         = "org.junit.After";
    protected static final String ORG_JUNIT_BEFORE        = "org.junit.Before";
    protected static final String ORG_JUNIT_AFTERCLASS    = "org.junit.AfterClass";
    protected static final String ORG_JUNIT_BEFORECLASS   = "org.junit.BeforeClass";
    protected static final String ORG_JUNIT_RULE          = "org.junit.Rule";
    protected static final String ORG_JUNIT_CLASS_RULE    = "org.junit.ClassRule";
    protected static final String ORG_JUNIT_RULES_TEMPORARY_FOLDER = "org.junit.rules.TemporaryFolder";
    protected static final String ORG_JUNIT_RULES_TEST_NAME        = "org.junit.rules.TestName";
    protected static final String ORG_JUNIT_RULES_EXTERNAL_RESOURCE = "org.junit.rules.ExternalResource";
    protected static final String ORG_JUNIT_RUNWITH       = "org.junit.runner.RunWith";
    protected static final String ORG_JUNIT_SUITE         = "org.junit.runners.Suite";
    protected static final String ORG_JUNIT_SUITE_SUITECLASSES = "org.junit.runners.Suite.SuiteClasses";
    protected static final String ORG_JUNIT_TEST          = "org.junit.Test";
    protected static final String ORG_JUNIT_IGNORE        = "org.junit.Ignore";
    protected static final String ORG_JUNIT_ASSERT        = "org.junit.Assert";
    protected static final String ORG_JUNIT_ASSUME        = "org.junit.Assume";

    // --- 4b) JUnit 5 / Jupiter Referenzen ---
    protected static final String ORG_JUNIT_JUPITER_API_AFTER_EACH = "org.junit.jupiter.api.AfterEach";
    protected static final String ORG_JUNIT_JUPITER_API_AFTER_ALL  = "org.junit.jupiter.api.AfterAll";
    protected static final String ORG_JUNIT_JUPITER_API_BEFORE_ALL = "org.junit.jupiter.api.BeforeAll";
    protected static final String ORG_JUNIT_JUPITER_API_BEFORE_EACH= "org.junit.jupiter.api.BeforeEach";
    protected static final String ORG_JUNIT_JUPITER_API_ASSERTIONS = "org.junit.jupiter.api.Assertions";
    protected static final String ORG_JUNIT_JUPITER_DISABLED       = "org.junit.jupiter.api.Disabled";
    protected static final String ORG_JUNIT_JUPITER_API_IO_TEMP_DIR= "org.junit.jupiter.api.io.TempDir";
    protected static final String ORG_JUNIT_JUPITER_API_TEST_INFO  = "org.junit.jupiter.api.TestInfo";
    protected static final String ORG_JUNIT_JUPITER_API_EXTENSION_EXTENSION_CONTEXT = "org.junit.jupiter.api.extension.ExtensionContext";
    private static final String ORG_JUNIT_JUPITER_API_EXTENSION_REGISTER_EXTENSION   = "org.junit.jupiter.api.extension.RegisterExtension";
    private static final String ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_EACH_CALLBACK = "org.junit.jupiter.api.extension.BeforeEachCallback";
    private static final String ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_EACH_CALLBACK  = "org.junit.jupiter.api.extension.AfterEachCallback";
    private static final String ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_ALL_CALLBACK  = "org.junit.jupiter.api.extension.BeforeAllCallback";
    private static final String ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_ALL_CALLBACK   = "org.junit.jupiter.api.extension.AfterAllCallback";

    protected static final String ORG_JUNIT_JUPITER_TEST  = "org.junit.jupiter.api.Test";
    protected static final String ORG_JUNIT_JUPITER_API_ASSUMPTIONS = "org.junit.jupiter.api.Assumptions";

    // --- 4c) JUnit Platform Referenzen ---
    protected static final String ORG_JUNIT_JUPITER_SUITE = "org.junit.platform.suite.api.Suite";
    protected static final String ORG_JUNIT_PLATFORM_SUITE_API_SELECT_CLASSES = "org.junit.platform.suite.api.SelectClasses";

    private static final String AFTER_ALL_CALLBACK= "AfterAllCallback";
	private static final String BEFORE_ALL_CALLBACK= "BeforeAllCallback";
	private static final String AFTER_EACH_CALLBACK= "AfterEachCallback";
	private static final String BEFORE_EACH_CALLBACK= "BeforeEachCallback";

	protected static final String ORG_JUNIT_JUPITER_API_EXTENSION_EXTEND_WITH= "org.junit.jupiter.api.extension.ExtendWith";

	public static Collection<String> getUsedVariableNames(ASTNode node) {
		CompilationUnit root= (CompilationUnit) node.getRoot();
		return new ScopeAnalyzer(root).getUsedVariableNames(node.getStartPosition(), node.getLength());
	}

	private void adaptExternalResourceHierarchy(ITypeBinding typeBinding, ASTRewrite rewrite, AST ast,
			ImportRewrite importRewrite, TextEditGroup group) {
		while (typeBinding != null) {
			// Abbruchbedingung: Nicht weiter heruntersteigen, wenn der aktuelle Typ
			// ExternalResource ist
			if (ORG_JUNIT_RULES_EXTERNAL_RESOURCE.equals(typeBinding.getQualifiedName())) {
				break;
			}

			if (isExternalResource(typeBinding, ORG_JUNIT_RULES_EXTERNAL_RESOURCE)) {
				TypeDeclaration typeDecl= findTypeDeclarationInProject(typeBinding);
				if (typeDecl != null) {
					adaptTypeDeclaration(typeDecl, rewrite, ast, importRewrite, group);
				}
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
					addContextArgumentIfMissing(node, rewriter, ast, group);
				}
				return super.visit(node);
			}
		});
	}

	private void adaptTypeDeclaration(TypeDeclaration typeDecl, ASTRewrite globalRewrite, AST ast,
			ImportRewrite importRewrite, TextEditGroup group) {
		AST astOfNode= typeDecl.getAST();

		// Ermitteln der CompilationUnit
		CompilationUnit compilationUnit= findCompilationUnit(typeDecl);

		// Wählen Sie den passenden ASTRewrite basierend auf dem AST des Knotens
		ASTRewrite rewriteToUse= (astOfNode == ast) ? globalRewrite : ASTRewrite.create(astOfNode);
		ImportRewrite importRewriteToUse= (astOfNode == ast) ? importRewrite
				: ImportRewrite.create(compilationUnit, true);
//		    ASTRewrite rewriteToUse = globalRewrite;
//		    ImportRewrite importRewriteToUse = importRewrite;

		removeSuperclassType(typeDecl, rewriteToUse, group);
		updateLifecycleMethodsInClass(typeDecl, rewriteToUse, ast, group, importRewriteToUse, METHOD_BEFORE,
				METHOD_AFTER, METHOD_BEFORE_EACH, METHOD_AFTER_EACH);

		importRewriteToUse.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_EACH_CALLBACK);
		importRewriteToUse.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_EACH_CALLBACK);

		// Wenn ein neuer ASTRewrite erstellt wurde, wenden Sie die Änderungen an
		if (rewriteToUse != globalRewrite) {
			createChangeForRewrite(compilationUnit, rewriteToUse);
		}
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

	private void addInterfaceCallback(ListRewrite listRewrite, AST ast, String callbackName, TextEditGroup group,
			ImportRewrite importRewriter, String classtoimport) {
		// Prüfen, ob das Interface bereits in der Liste existiert
		boolean hasCallback= listRewrite.getRewrittenList().stream().anyMatch(type -> type instanceof SimpleType
				&& ((SimpleType) type).getName().getFullyQualifiedName().equals(callbackName));

		if (!hasCallback) {
			// Interface hinzufügen, wenn es noch nicht existiert
			listRewrite.insertLast(ast.newSimpleType(ast.newName(callbackName)), group);
		}
		importRewriter.addImport(classtoimport);
	}

	private void addRegisterExtensionAnnotation(ASTNode node, ASTRewrite rewrite, AST ast, ImportRewrite importRewrite,
			TextEditGroup group) {
		if (node instanceof FieldDeclaration) {
			// Direkt mit FieldDeclaration arbeiten
			FieldDeclaration field= (FieldDeclaration) node;

			// Prüfen, ob die Annotation bereits existiert
			boolean hasRegisterExtension= field.modifiers().stream()
					.anyMatch(modifier -> modifier instanceof Annotation && ((Annotation) modifier).getTypeName()
							.getFullyQualifiedName().equals(ANNOTATION_REGISTER_EXTENSION));

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
		} else if (node instanceof ClassInstanceCreation) {
			// Übergeordnetes Element der anonymen Klasse finden
			ASTNode parent= node.getParent();
			if (parent instanceof VariableDeclarationFragment) {
				VariableDeclarationFragment fragment= (VariableDeclarationFragment) parent;
				FieldDeclaration field= (FieldDeclaration) fragment.getParent();

				// Prüfen, ob die Annotation bereits existiert
				boolean hasRegisterExtension= field.modifiers().stream()
						.anyMatch(modifier -> modifier instanceof Annotation && ((Annotation) modifier).getTypeName()
								.getFullyQualifiedName().equals(ANNOTATION_REGISTER_EXTENSION));

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
		}
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

	private String capitalizeFirstLetter(String input) {
		if (input == null || input.isEmpty()) {
			return input;
		}
		return Character.toUpperCase(input.charAt(0)) + input.substring(1);
	}

	private CompilationUnitChange createChangeForRewrite(CompilationUnit compilationUnit, ASTRewrite rewrite) {
		try {
			// Zugriff auf das IDocument der CompilationUnit
			IDocument document= getDocumentForCompilationUnit(compilationUnit);

			// Änderungen beschreiben (aber nicht anwenden)
			TextEdit edits= rewrite.rewriteAST(document, null);

			// Ein TextChange-Objekt erstellen
			CompilationUnitChange change= new CompilationUnitChange("JUnit Migration",
					(ICompilationUnit) compilationUnit.getJavaElement());
			change.setEdit(edits);

			// Optional: Kommentare oder Markierungen hinzufügen
			change.addTextEditGroup(new TextEditGroup("Migrate JUnit", edits));

			return change;

		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Error creating change for rewrite: " + e.getMessage(), e);
		}
	}

	private MethodDeclaration createLifecycleCallbackMethod(AST ast, String methodName, String paramType, Block oldBody,
			TextEditGroup group) {

		MethodDeclaration method= ast.newMethodDeclaration();
		method.setName(ast.newSimpleName(methodName));
		method.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));
		method.setReturnType2(ast.newPrimitiveType(PrimitiveType.VOID));

		// Füge den ExtensionContext-Parameter hinzu
		SingleVariableDeclaration param= ast.newSingleVariableDeclaration();
		param.setType(ast.newSimpleType(ast.newName(paramType)));
		param.setName(ast.newSimpleName("context"));
		method.parameters().add(param);

		// Kopiere den Body der alten Methode
		if (oldBody != null) {
			Block newBody= (Block) ASTNode.copySubtree(ast, oldBody);
			method.setBody(newBody);
		}

		return method;
	}

	private TypeDeclaration createNestedClassFromAnonymous(AnonymousClassDeclaration anonymousClass, String className,
			boolean fieldStatic, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter, TextEditGroup group) {

		// Erstelle die neue TypeDeclaration
		TypeDeclaration nestedClass= ast.newTypeDeclaration();
		nestedClass.setName(ast.newSimpleName(className));
		if (fieldStatic) {
			nestedClass.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.STATIC_KEYWORD));
		}

		// Füge die Schnittstellen hinzu
		nestedClass.superInterfaceTypes()
				.add(ast.newSimpleType(ast.newName(ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_EACH_CALLBACK)));
		nestedClass.superInterfaceTypes()
				.add(ast.newSimpleType(ast.newName(ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_EACH_CALLBACK)));
		importRewriter.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_EACH_CALLBACK);
		importRewriter.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_EACH_CALLBACK);

		// Übertrage den Body der anonymen Klasse in die neue Klasse
		ListRewrite bodyRewrite= rewriter.getListRewrite(nestedClass, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
		for (Object decl : anonymousClass.bodyDeclarations()) {
			if (decl instanceof MethodDeclaration) {
				MethodDeclaration method= (MethodDeclaration) decl;

				// Konvertiere before() -> beforeEach() und after() -> afterEach()
				if (isLifecycleMethod(method, METHOD_BEFORE)) {
					MethodDeclaration beforeEachMethod= createLifecycleCallbackMethod(ast, "beforeEach",
							"ExtensionContext", method.getBody(), group);
					bodyRewrite.insertLast(beforeEachMethod, group);
				} else if (isLifecycleMethod(method, METHOD_AFTER)) {
					MethodDeclaration afterEachMethod= createLifecycleCallbackMethod(ast, "afterEach",
							"ExtensionContext", method.getBody(), group);
					bodyRewrite.insertLast(afterEachMethod, group);
				}
			}
		}

		// Füge die neue Klasse zur äußeren Klasse hinzu
		TypeDeclaration parentType= findEnclosingTypeDeclaration(anonymousClass);
		if (parentType != null) {
			ListRewrite enclosingBodyRewrite= rewriter.getListRewrite(parentType,
					TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
			enclosingBodyRewrite.insertLast(nestedClass, group);
		}

		return nestedClass;
	}

	private void ensureClassInstanceRewrite(ClassInstanceCreation classInstanceCreation, ASTRewrite rewriter,
	                                        ImportRewrite importRewriter, TextEditGroup group) {
	    removeExternalResourceSuperclass(classInstanceCreation, rewriter, importRewriter, group);
	    ensureImport(importRewriter, ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_EACH_CALLBACK);
	    ensureImport(importRewriter, ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_EACH_CALLBACK);
	    ensureImport(importRewriter, ORG_JUNIT_JUPITER_API_EXTENSION_EXTENSION_CONTEXT);
	}

	private void ensureExtensionContextParameter(MethodDeclaration method, ASTRewrite rewrite, AST ast,
			TextEditGroup group, ImportRewrite importRewrite) {

		// Prüfen, ob ExtensionContext bereits existiert (im AST oder im Rewrite)
		boolean hasExtensionContext= method.parameters().stream()
				.anyMatch(param -> param instanceof SingleVariableDeclaration && isExtensionContext(
						(SingleVariableDeclaration) param, ORG_JUNIT_JUPITER_API_EXTENSION_EXTENSION_CONTEXT))
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

	private void ensureImport(ImportRewrite importRewriter, String importName) {
	    if (importRewriter != null && importName != null) {
	        importRewriter.addImport(importName);
	    }
	}

	private void ensureRemoval(ImportRewrite importRewriter, String importName) {
	    if (importRewriter != null && importName != null) {
	        importRewriter.removeImport(importName);
	    }
	}

	public String extractClassNameFromField(FieldDeclaration field) {
	    for (Object fragmentObj : field.fragments()) {
	        if (fragmentObj instanceof VariableDeclarationFragment) {
	            VariableDeclarationFragment fragment = (VariableDeclarationFragment) fragmentObj;
	            Expression initializer = fragment.getInitializer();
	            if (initializer instanceof ClassInstanceCreation) {
	                return extractTypeName(((ClassInstanceCreation) initializer).getType());
	            }
	        }
	    }
	    return null; // Kein passender Typ gefunden
	}

	private String extractFieldName(FieldDeclaration fieldDeclaration) {
	    return (String) fieldDeclaration.fragments().stream()
	            .filter(VariableDeclarationFragment.class::isInstance)
	            .map(fragment -> ((VariableDeclarationFragment) fragment).getName().getIdentifier())
	            .findFirst()
	            .orElse("UnnamedField");
	}

	protected String extractQualifiedTypeName(QualifiedType qualifiedType) {
	    StringBuilder fullClassName = new StringBuilder();
	    Type currentType = qualifiedType;

	    while (currentType instanceof QualifiedType) {
	        QualifiedType currentQualified = (QualifiedType) currentType;
	        if (fullClassName.length() > 0) {
	            fullClassName.insert(0, ".");
	        }
	        fullClassName.insert(0, currentQualified.getName().getFullyQualifiedName());
	        currentType = currentQualified.getQualifier();
	    }
	    return fullClassName.toString();
	}

	/**
	 * General method to extract a type's fully qualified name.
	 */
	private String extractTypeName(Type type) {
	    if (type instanceof QualifiedType) {
	        return extractQualifiedTypeName((QualifiedType) type);
	    } else if (type instanceof SimpleType) {
	        return ((SimpleType) type).getName().getFullyQualifiedName();
	    }
	    return null;
	}


	public abstract void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed);

	private CompilationUnit findCompilationUnit(ASTNode node) {
	    while (node != null && !(node instanceof CompilationUnit)) {
	        node = node.getParent();
	    }
	    return (CompilationUnit) node;
	}

	private TypeDeclaration findEnclosingTypeDeclaration(ASTNode node) {
	    while (node != null && !(node instanceof TypeDeclaration)) {
	        node = node.getParent();
	    }
	    return (TypeDeclaration) node;
	}

	public TypeDeclaration findTypeDeclaration(IJavaProject javaProject, String fullyQualifiedTypeName) {
	    try {
	        IType type = javaProject.findType(fullyQualifiedTypeName);
	        if (type != null && type.exists()) {
	            CompilationUnit unit = parseCompilationUnit(type.getCompilationUnit());
	            return findTypeDeclarationInCompilationUnit(unit, fullyQualifiedTypeName);
	        }
	    } catch (JavaModelException e) {
	        e.printStackTrace();
	    }
	    return null;
	}

	private ASTNode findTypeDeclarationForBinding(ITypeBinding typeBinding, CompilationUnit cu) {
	    if (typeBinding == null) return null;

	    TypeDeclaration typeDecl = findTypeDeclarationInCompilationUnit(typeBinding, cu);
	    return typeDecl != null ? typeDecl : findTypeDeclarationInProject(typeBinding);
	}

	private TypeDeclaration findTypeDeclarationInCompilationUnit(CompilationUnit unit, String fullyQualifiedTypeName) {
	    for (Object obj : unit.types()) {
	        if (obj instanceof TypeDeclaration) {
	            TypeDeclaration typeDecl = (TypeDeclaration) obj;
	            TypeDeclaration result = findTypeDeclarationInType(typeDecl, fullyQualifiedTypeName);
	            if (result != null) {
	                return result;
	            }
	        }
	    }
	    return null;
	}

	private TypeDeclaration findTypeDeclarationInCompilationUnit(ITypeBinding typeBinding, CompilationUnit cu) {
	    final TypeDeclaration[] result = { null };

	    cu.accept(new ASTVisitor() {
	        private boolean checkAndMatchBinding(AbstractTypeDeclaration node, ITypeBinding typeBinding) {
	            ITypeBinding binding = node.resolveBinding();
	            if (binding != null && ASTNodes.areBindingsEqual(binding, typeBinding)) {
	                result[0] = (TypeDeclaration) node;
	                return false;
	            }
	            return true;
	        }

	        @Override
	        public boolean visit(AnnotationTypeDeclaration node) {
	            return checkAndMatchBinding(node, typeBinding);
	        }

	        @Override
	        public boolean visit(EnumDeclaration node) {
	            return checkAndMatchBinding(node, typeBinding);
	        }

	        @Override
	        public boolean visit(TypeDeclaration node) {
	            return checkAndMatchBinding(node, typeBinding);
	        }
	    });

	    return result[0];
	}

	private TypeDeclaration findTypeDeclarationInProject(ITypeBinding typeBinding) {
	    IType type = (IType) typeBinding.getJavaElement();
	    return type != null ? findTypeDeclaration(type.getJavaProject(), type.getFullyQualifiedName()) : null;
	}

	private TypeDeclaration findTypeDeclarationInType(TypeDeclaration typeDecl, String qualifiedTypeName) {
	    if (getQualifiedName(typeDecl).equals(qualifiedTypeName)) {
	        return typeDecl;
	    }

	    for (TypeDeclaration nestedType : typeDecl.getTypes()) {
	        TypeDeclaration result = findTypeDeclarationInType(nestedType, qualifiedTypeName);
	        if (result != null) {
	            return result;
	        }
	    }
	    return null;
	}


	private String generateChecksum(String input) {
		try {
			MessageDigest md= MessageDigest.getInstance("SHA-256");
			byte[] hashBytes= md.digest(input.getBytes(StandardCharsets.UTF_8));
			StringBuilder hexString= new StringBuilder();
			for (byte b : hashBytes) {
				String hex= Integer.toHexString(0xff & b);
				if (hex.length() == 1) {
					hexString.append('0');
				}
				hexString.append(hex);
			}
			return hexString.toString().substring(0, 5);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("SHA-256 algorithm not found",e);
		}
	}

	private String generateUniqueNestedClassName(AnonymousClassDeclaration anonymousClass, String baseName) {
		String anonymousCode= anonymousClass.toString(); // Der gesamte Code der anonymen Klasse
		String checksum= generateChecksum(anonymousCode);

		// Feldname großschreiben
		String capitalizedBaseName= capitalizeFirstLetter(baseName);

		return capitalizedBaseName + "_" + checksum;
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
			// Verbinden mit der Datei, die der CompilationUnit entspricht
			bufferManager.connect(icu.getPath(), LocationKind.IFILE, null);

			// Holen des zugehörigen TextFileBuffer
			ITextFileBuffer textFileBuffer= bufferManager.getTextFileBuffer(icu.getPath(), LocationKind.IFILE);

			if (textFileBuffer == null) {
				throw new RuntimeException("No text file buffer found for the provided compilation unit.");
			}

			// Rückgabe des Dokuments
			return textFileBuffer.getDocument();
		} catch (CoreException e) {
			throw new RuntimeException("Failed to connect to text file buffer: " + e.getMessage(), e);
		} finally {
			try {
				// Optional: Verbindung trennen, wenn keine weiteren Änderungen anstehen
				bufferManager.disconnect(icu.getPath(), LocationKind.IFILE, null);
			} catch (CoreException e) {
				// Trennen schlug fehl, aber wir protokollieren nur
				e.printStackTrace();
			}
		}
	}

	private ImportRewrite getImportRewrite(ASTNode node, AST globalAST, ImportRewrite globalImportRewrite) {
	    CompilationUnit compilationUnit = findCompilationUnit(node);
	    return (node.getAST() == globalAST) ? globalImportRewrite : ImportRewrite.create(compilationUnit, true);
	}

	protected TypeDeclaration getParentTypeDeclaration(ASTNode node) {
		while (node != null && !(node instanceof TypeDeclaration)) {
			node= node.getParent();
		}
		return (TypeDeclaration) node;
	}

	public abstract String getPreview(boolean afterRefactoring);

	// Hilfsmethode: Ermittelt den vollqualifizierten Namen einer TypeDeclaration
	private String getQualifiedName(TypeDeclaration typeDecl) {
	    StringBuilder qualifiedName = new StringBuilder(typeDecl.getName().getIdentifier());
	    ASTNode parent = typeDecl.getParent();

	    // Verschachtelte Klassen verarbeiten
	    while (parent instanceof TypeDeclaration) {
	        TypeDeclaration parentType = (TypeDeclaration) parent;
	        qualifiedName.insert(0, parentType.getName().getIdentifier() + "$"); // $ für verschachtelte Klassen
	        parent = parent.getParent();
	    }

	    // Paketnamen hinzufügen
	    CompilationUnit compilationUnit = (CompilationUnit) typeDecl.getRoot();
	    if (compilationUnit.getPackage() != null) {
	        String packageName = compilationUnit.getPackage().getName().getFullyQualifiedName();
	        qualifiedName.insert(0, packageName + ".");
	    }

	    return qualifiedName.toString();
	}

	protected ASTNode getTypeDefinitionForField(FieldDeclaration fieldDeclaration, CompilationUnit cu) {
	    return (ASTNode) fieldDeclaration.fragments().stream()
	            .filter(VariableDeclarationFragment.class::isInstance)
	            .map(VariableDeclarationFragment.class::cast)
	            .map(fragment -> getTypeDefinitionFromFragment((VariableDeclarationFragment) fragment, cu))
	            .filter(java.util.Objects::nonNull) // Nur nicht-null Ergebnisse berücksichtigen
	            .findFirst()
	            .orElse(null);
	}

	private ASTNode getTypeDefinitionFromFragment(VariableDeclarationFragment fragment, CompilationUnit cu) {
	    // Initialisierer prüfen
	    Expression initializer = fragment.getInitializer();
	    if (initializer instanceof ClassInstanceCreation) {
	        ClassInstanceCreation classInstanceCreation = (ClassInstanceCreation) initializer;

	        // Anonyme Klasse prüfen
	        AnonymousClassDeclaration anonymousClass = classInstanceCreation.getAnonymousClassDeclaration();
	        if (anonymousClass != null) {
	            return anonymousClass; // Anonyme Klasse gefunden
	        }

	        // Typbindung prüfen
	        ITypeBinding typeBinding = classInstanceCreation.resolveTypeBinding();
	        return findTypeDeclarationForBinding(typeBinding, cu);
	    }

	    // Typ des Feldes prüfen, wenn keine Initialisierung vorhanden ist
	    IVariableBinding fieldBinding = fragment.resolveBinding();
	    if (fieldBinding != null) {
	        ITypeBinding fieldTypeBinding = fieldBinding.getType();
	        return findTypeDeclarationForBinding(fieldTypeBinding, cu);
	    }

	    return null; // Keine passende Typdefinition gefunden
	}

	private boolean hasAnnotation(List<?> modifiers, String annotationClass) {
	    return modifiers.stream()
	            .filter(Annotation.class::isInstance)
	            .map(Annotation.class::cast)
	            .map(Annotation::resolveTypeBinding)
	            .anyMatch(binding -> binding != null && annotationClass.equals(binding.getQualifiedName()));
	}

	protected boolean hasDefaultConstructorOrNoConstructor(TypeDeclaration classNode) {
	    boolean hasConstructor = false;

	    for (Object bodyDecl : classNode.bodyDeclarations()) {
	        if (bodyDecl instanceof MethodDeclaration) {
	            MethodDeclaration method = (MethodDeclaration) bodyDecl;
	            if (method.isConstructor()) {
	                hasConstructor = true;
	                if (method.parameters().isEmpty() && method.getBody() != null
	                        && method.getBody().statements().isEmpty()) {
	                    return true; // Leerer Standardkonstruktor gefunden
	                }
	            }
	        }
	    }
	    return !hasConstructor; // Kein Konstruktor vorhanden
	}

	private boolean hasModifier(List<?> modifiers, Modifier.ModifierKeyword keyword) {
	    return modifiers.stream()
	            .filter(Modifier.class::isInstance)
	            .map(Modifier.class::cast)
	            .anyMatch(modifier -> modifier.getKeyword().equals(keyword));
	}

	private boolean implementsInterface(ITypeBinding subtype, ITypeBinding supertype) {
		for (ITypeBinding iface : subtype.getInterfaces()) {
			if (iface.getQualifiedName().equals(supertype.getQualifiedName())
					|| implementsInterface(iface, supertype)) {
				return true;
			}
		}
		return false;
	}

	private boolean isAnnotatedWithRule(BodyDeclaration declaration, String annotationClass) {
	    return hasAnnotation(declaration.modifiers(), annotationClass);
	}

	public boolean isAnonymousClass(VariableDeclarationFragment fragment) {
	    Expression initializer = fragment.getInitializer();
	    if (initializer instanceof ClassInstanceCreation) {
	        return ((ClassInstanceCreation) initializer).getAnonymousClassDeclaration() != null;
	    }
	    return false;
	}

	protected boolean isDirect(ITypeBinding fieldTypeBinding) {
	    return ORG_JUNIT_RULES_EXTERNAL_RESOURCE.equals(fieldTypeBinding.getQualifiedName());
	}

	protected boolean isDirectlyExtendingExternalResource(ITypeBinding binding) {
	    ITypeBinding superclass = binding.getSuperclass();
	    return superclass != null && ORG_JUNIT_RULES_EXTERNAL_RESOURCE.equals(superclass.getQualifiedName());
	}

	private boolean isExtensionContext(SingleVariableDeclaration param, String className) {
	    ITypeBinding binding = param.getType().resolveBinding();
	    return binding != null && className.equals(binding.getQualifiedName());
	}

	private boolean isExternalResource(FieldDeclaration field, String typeToLookup) {
	    ITypeBinding binding = ((VariableDeclarationFragment) field.fragments().get(0)).resolveBinding().getType();
	    return isTypeOrSubtype(binding, typeToLookup);
	}

	protected boolean isExternalResource(ITypeBinding typeBinding, String typeToLookup) {
	    return isTypeOrSubtype(typeBinding, typeToLookup);
	}

	protected boolean isFieldAnnotatedWith(FieldDeclaration field, String annotationClass) {
	    return hasAnnotation(field.modifiers(), annotationClass);
	}

	private boolean isFieldStatic(FieldDeclaration field) {
	    return hasModifier(field.modifiers(), Modifier.ModifierKeyword.STATIC_KEYWORD);
	}

	protected boolean isLifecycleMethod(MethodDeclaration method, String methodName) {
	    return methodName.equals(method.getName().getIdentifier());
	}

	private boolean isStringType(Expression expression, Class<String> classType) {
	    ITypeBinding typeBinding = expression.resolveTypeBinding();
	    return typeBinding != null && classType.getCanonicalName().equals(typeBinding.getQualifiedName());
	}

	private boolean isSubtypeOf(ITypeBinding subtype, ITypeBinding supertype) {
	    if (subtype == null || supertype == null) {
	        return false;
	    }

	    // Compare qualified names to detect exact matches
	    if (subtype.getQualifiedName().equals(supertype.getQualifiedName())) {
	        return true;
	    }

	    // Check the inheritance hierarchy
	    if (isTypeOrSubtype(subtype, supertype.getQualifiedName())) {
	        return true;
	    }

	    // Check implemented interfaces
	    return implementsInterface(subtype, supertype);
	}

	private boolean isTypeOrSubtype(ITypeBinding typeBinding, String qualifiedName) {
	    while (typeBinding != null) {
	        if (qualifiedName.equals(typeBinding.getQualifiedName())) {
	            return true;
	        }
	        typeBinding = typeBinding.getSuperclass();
	    }
	    return false;
	}

	protected void modifyExternalResourceClass(TypeDeclaration node, FieldDeclaration field, boolean fieldStatic,
			ASTRewrite rewriter, AST ast, TextEditGroup group, ImportRewrite importRewriter) {
		if (!shouldProcessNode(node)) {
			return;
		}

		String beforecallback;
		String aftercallback;
		String importbeforecallback;
		String importaftercallback;
		if (fieldStatic) {
			beforecallback= BEFORE_ALL_CALLBACK;
			aftercallback= AFTER_ALL_CALLBACK;
			importbeforecallback= ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_ALL_CALLBACK;
			importaftercallback= ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_ALL_CALLBACK;
		} else {
			beforecallback= BEFORE_EACH_CALLBACK;
			aftercallback= AFTER_EACH_CALLBACK;
			importbeforecallback= ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_EACH_CALLBACK;
			importaftercallback= ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_EACH_CALLBACK;
		}
		if (field != null) {
			if (isAnnotatedWithRule(field, ORG_JUNIT_RULE)
					&& isExternalResource(field, ORG_JUNIT_RULES_EXTERNAL_RESOURCE)) {
				removeRuleAnnotation(field, rewriter, group, importRewriter, ORG_JUNIT_RULE);
				addRegisterExtensionAnnotation(field, rewriter, ast, importRewriter, group);
				ITypeBinding fieldType= ((VariableDeclarationFragment) field.fragments().get(0)).resolveBinding()
						.getType();
				adaptExternalResourceHierarchy(fieldType, rewriter, ast, importRewriter, group);
			} else if (isAnnotatedWithRule(field, ORG_JUNIT_CLASS_RULE)
					&& isExternalResource(field, ORG_JUNIT_RULES_EXTERNAL_RESOURCE)) {
				removeRuleAnnotation(field, rewriter, group, importRewriter, ORG_JUNIT_CLASS_RULE);
				addRegisterExtensionAnnotation(field, rewriter, ast, importRewriter, group);
				ITypeBinding fieldType= ((VariableDeclarationFragment) field.fragments().get(0)).resolveBinding()
						.getType();
				adaptExternalResourceHierarchy(fieldType, rewriter, ast, importRewriter, group);
			}
		}
		if (isDirectlyExtendingExternalResource(node.resolveBinding())) {
			refactorToImplementCallbacks(node, rewriter, ast, group, importRewriter, beforecallback, aftercallback,
					importbeforecallback, importaftercallback);
		}

		updateLifecycleMethodsInClass(node, rewriter, ast, group, importRewriter, METHOD_BEFORE, METHOD_AFTER,
				fieldStatic ? METHOD_BEFORE_ALL : METHOD_BEFORE_EACH,
				fieldStatic ? METHOD_AFTER_ALL : METHOD_AFTER_EACH);
	}

	// Nutzen Sie eine Methode, um eine CompilationUnit aus einer ICompilationUnit zu erstellen
	private CompilationUnit parseCompilationUnit(ICompilationUnit iCompilationUnit) {
	    ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
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
		if (!isExternalResource(fieldTypeBinding, ORG_JUNIT_RULES_EXTERNAL_RESOURCE)
				|| fieldTypeBinding.isAnonymous()) {
			return;
		}
		if (isDirect(fieldTypeBinding)) {
			rewrite.remove(field, group);
			importRewriter.removeImport(ORG_JUNIT_RULE);
		}
		addExtendWithAnnotation(rewrite, ast, group, importRewriter, className, field);
		importRewriter.removeImport(ORG_JUNIT_RULES_EXTERNAL_RESOURCE);
	}

	abstract void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder mh);

	private void processMethod(MethodDeclaration method, ASTRewrite rewriter, AST ast, TextEditGroup group,
			ImportRewrite importRewriter, String methodname, String methodnamejunit5) {
		setPublicVisibilityIfProtected(method, rewriter, ast, group);
		adaptSuperBeforeCalls(methodname, methodnamejunit5, method, rewriter, ast, group);
		removeThrowsThrowable(method, rewriter, group);
		rewriter.replace(method.getName(), ast.newSimpleName(methodnamejunit5), group);
		ensureExtensionContextParameter(method, rewriter, ast, group, importRewriter);
	}

	// Optimierte Methoden
	protected void refactorAnonymousClassToImplementCallbacks(
	        AnonymousClassDeclaration anonymousClass, FieldDeclaration fieldDeclaration, boolean fieldStatic,
	        ASTRewrite rewriter, AST ast, TextEditGroup group, ImportRewrite importRewriter) {

	    if (anonymousClass == null) {
	        return;
	    }

	    // Zugriff auf die umgebende ClassInstanceCreation
	    ASTNode parent = anonymousClass.getParent();
	    if (parent instanceof ClassInstanceCreation) {
	        ClassInstanceCreation classInstanceCreation = (ClassInstanceCreation) parent;
	        ensureClassInstanceRewrite(classInstanceCreation, rewriter, importRewriter, group);

	        String fieldName = extractFieldName(fieldDeclaration);
	        String nestedClassName = generateUniqueNestedClassName(anonymousClass, fieldName);
	        TypeDeclaration nestedClass = createNestedClassFromAnonymous(anonymousClass, nestedClassName, fieldStatic,
	                rewriter, ast, importRewriter, group);

	        replaceFieldWithExtensionDeclaration(classInstanceCreation, nestedClassName, fieldStatic, rewriter, ast, group,
	                importRewriter);
	    }
	}

	protected void refactorTestnameInClass(TextEditGroup group, ASTRewrite rewriter, AST ast,
	                                       ImportRewrite importRewrite, FieldDeclaration node) {
	    if (node == null || rewriter == null || ast == null || importRewrite == null) {
	        return;
	    }

	    rewriter.remove(node, group);

	    TypeDeclaration parentClass = (TypeDeclaration) node.getParent();
	    addBeforeEachInitMethod(parentClass, rewriter, group);
	    addTestNameField(parentClass, rewriter, group);

	    // Aktualisierung der Methoden
	    updateMethodReferences(parentClass, ast, rewriter, group);

	    ensureImport(importRewrite, ORG_JUNIT_JUPITER_API_TEST_INFO);
	    ensureImport(importRewrite, ORG_JUNIT_JUPITER_API_BEFORE_EACH);
	    ensureRemoval(importRewrite, ORG_JUNIT_RULE);
	    ensureRemoval(importRewrite, ORG_JUNIT_RULES_TEST_NAME);
	}

	protected void refactorTestnameInClassAndSubclasses(TextEditGroup group, ASTRewrite rewriter, AST ast,
	                                                    ImportRewrite importRewrite, FieldDeclaration node) {
	    refactorTestnameInClass(group, rewriter, ast, importRewrite, node);

	    ITypeBinding typeBinding = ((TypeDeclaration) node.getParent()).resolveBinding();
	    List<ITypeBinding> subclasses = getAllSubclasses(typeBinding);

	    for (ITypeBinding subclassBinding : subclasses) {
	        IType subclassType = (IType) subclassBinding.getJavaElement();

	        CompilationUnit subclassUnit = parseCompilationUnit(subclassType.getCompilationUnit());
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
	    importRewriteToUse.removeImport(ORG_JUNIT_RULES_EXTERNAL_RESOURCE);

	    ListRewrite listRewrite = rewriteToUse.getListRewrite(node, TypeDeclaration.SUPER_INTERFACE_TYPES_PROPERTY);
	    addInterfaceCallback(listRewrite, ast, beforeCallback, group, importRewriteToUse, importBeforeCallback);
	    addInterfaceCallback(listRewrite, ast, afterCallback, group, importRewriteToUse, importAfterCallback);

	    if (rewriteToUse != rewriter) {
	        createChangeForRewrite(findCompilationUnit(node), rewriteToUse);
	    }
	}

	private void removeExternalResourceSuperclass(ClassInstanceCreation anonymousClass, ASTRewrite rewrite,
			ImportRewrite importRewriter, TextEditGroup group) {
		// Prüfen, ob die anonyme Klasse von ExternalResource erbt
		ITypeBinding typeBinding= anonymousClass.resolveTypeBinding();
		if (typeBinding.getSuperclass() != null
				&& ORG_JUNIT_RULES_EXTERNAL_RESOURCE.equals(typeBinding.getSuperclass().getQualifiedName())) {

			// Entfernen Sie die Superklasse durch Ersetzen des Typs im
			// ClassInstanceCreation
			Type type= anonymousClass.getType();
			if (type != null) {
				rewrite.replace(type,
						anonymousClass.getAST().newSimpleType(anonymousClass.getAST().newSimpleName("Object")), group);
			}

			// Entfernen Sie den Import der Superklasse
			importRewriter.removeImport(ORG_JUNIT_RULES_EXTERNAL_RESOURCE);
		}
	}
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
		if (!isStringType(arguments.get(0), String.class)) {
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



	private void replaceFieldWithExtensionDeclaration(ClassInstanceCreation classInstanceCreation,
			String nestedClassName, boolean fieldStatic, ASTRewrite rewriter, AST ast, TextEditGroup group,
			ImportRewrite importRewriter) {

		FieldDeclaration fieldDecl= ASTNodes.getParent(classInstanceCreation,
				FieldDeclaration.class);
		if (fieldDecl != null) {
			// Entferne die @Rule-Annotation
			removeRuleAnnotation(fieldDecl, rewriter, group, importRewriter, ORG_JUNIT_RULE);

			// Füge die @RegisterExtension-Annotation hinzu
			addRegisterExtensionAnnotation(fieldDecl, rewriter, ast, importRewriter, group);

			// Ändere den Typ der FieldDeclaration
			Type newType= ast.newSimpleType(ast.newName(nestedClassName));
			rewriter.set(fieldDecl.getType(), SimpleType.NAME_PROPERTY, newType, group);

			// Füge die Initialisierung hinzu
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

	public void rewrite(JUnitCleanUpFixCore upp, ReferenceHolder<Integer, JunitHolder> hit, CompilationUnitRewrite cuRewrite, TextEditGroup group) {
		ASTRewrite rewriter= cuRewrite.getASTRewrite();
		AST ast= cuRewrite.getRoot().getAST();
		ImportRewrite importRewriter= cuRewrite.getImportRewrite();
		JunitHolder mh= hit.get(hit.size() - 1);
		process2Rewrite(group, rewriter, ast, importRewriter, mh);
		hit.remove(hit.size() - 1);
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

	private boolean shouldProcessNode(TypeDeclaration node) {
		ITypeBinding binding= node.resolveBinding();
		return binding != null && isExternalResource(binding, ORG_JUNIT_RULES_EXTERNAL_RESOURCE);
	}

	private void updateLifecycleMethodsInClass(TypeDeclaration node, ASTRewrite globalRewrite, AST ast,
			TextEditGroup group, ImportRewrite importRewrite, String methodbefore, String methodafter,
			String methodbeforeeach, String methodaftereach) {

		for (MethodDeclaration method : node.getMethods()) {
			if (isLifecycleMethod(method, methodbefore)) {
				AST astOfNode= node.getAST();

				// Ermitteln der CompilationUnit
				CompilationUnit compilationUnit= findCompilationUnit(node);

				// Wählen Sie den passenden ASTRewrite basierend auf dem AST des Knotens
				ASTRewrite rewriteToUse= (astOfNode == ast) ? globalRewrite : ASTRewrite.create(astOfNode);
				ImportRewrite importRewriteToUse= (astOfNode == ast) ? importRewrite
						: ImportRewrite.create(compilationUnit, true);
				processMethod(method, rewriteToUse, ast, group, importRewriteToUse, methodbefore, methodbeforeeach);
				// Wenn ein neuer ASTRewrite erstellt wurde, wenden Sie die Änderungen an
				if (rewriteToUse != globalRewrite) {
					createChangeForRewrite(compilationUnit, rewriteToUse);
				}
			} else if (isLifecycleMethod(method, methodafter)) {
				AST astOfNode= node.getAST();

				// Ermitteln der CompilationUnit
				CompilationUnit compilationUnit= findCompilationUnit(node);

				// Wählen Sie den passenden ASTRewrite basierend auf dem AST des Knotens
				ASTRewrite rewriteToUse= (astOfNode == ast) ? globalRewrite : ASTRewrite.create(astOfNode);
				ImportRewrite importRewriteToUse= (astOfNode == ast) ? importRewrite
						: ImportRewrite.create(compilationUnit, true);
				processMethod(method, rewriteToUse, ast, group, importRewriteToUse, methodafter, methodaftereach);
				// Wenn ein neuer ASTRewrite erstellt wurde, wenden Sie die Änderungen an
				if (rewriteToUse != globalRewrite) {
					createChangeForRewrite(compilationUnit, rewriteToUse);
				}
			}
		}
	}

	private void updateMethodReferences(TypeDeclaration parentClass, AST ast, ASTRewrite rewriter, TextEditGroup group) {
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
}
