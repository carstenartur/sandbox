/*******************************************************************************
 * Copyright (c) 2021, 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_JUPITER_API_IO_TEMP_DIR;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_RULE;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_RULES_TEMPORARY_FOLDER;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.text.edits.TextEditGroup;

import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;
import org.sandbox.jdt.internal.corext.fix.helper.lib.TestNameRefactorer;
import org.sandbox.jdt.internal.corext.fix.helper.lib.TriggerPatternCleanupPlugin;
import org.sandbox.jdt.triggerpattern.api.CleanupPattern;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.PatternKind;

/** Binding-proven migration of JUnit 4 TemporaryFolder to Jupiter TempDir. */
@CleanupPattern(value = "@Rule public TemporaryFolder $name", kind = PatternKind.FIELD,
		qualifiedType = ORG_JUNIT_RULES_TEMPORARY_FOLDER,
		cleanupId = "cleanup.junit.ruletemporaryfolder",
		description = "Migrate a closed TemporaryFolder contract to @TempDir",
		displayName = "JUnit 4 @Rule TemporaryFolder → JUnit 5 @TempDir")
public class RuleTemporayFolderJUnitPlugin extends TriggerPatternCleanupPlugin {

	/** Stable fail-closed decision used by the cleanup and regression tests. */
	public record Assessment(boolean eligible, String reasonCode, String explanation) {
		public Assessment {
			reasonCode= Objects.requireNonNull(reasonCode);
			explanation= Objects.requireNonNull(explanation);
		}
	}

	private record InvocationPlan(MethodInvocation invocation, Operation operation) {
	}

	private enum Operation {
		GET_ROOT,
		NEW_FILE_RANDOM,
		NEW_FILE_NAMED,
		NEW_FOLDER_RANDOM,
		NEW_FOLDER_NAMED
	}

	@Override
	protected JunitHolder createHolder(Match match) {
		FieldDeclaration field= (FieldDeclaration) match.getMatchedNode();
		if (!assess(field).eligible()) {
			return null;
		}
		JunitHolder holder= new JunitHolder();
		holder.setMinv(field);
		return holder;
	}

	/** Proves that every use has an exact TempDir expression equivalent. */
	public static Assessment assess(FieldDeclaration field) {
		if (field == null || field.fragments().size() != 1
				|| !(field.fragments().get(0) instanceof VariableDeclarationFragment fragment)) {
			return rejected("TEMPORARY_FOLDER_FIELD_SHAPE_UNSUPPORTED", //$NON-NLS-1$
					"TemporaryFolder must be declared as one rule field."); //$NON-NLS-1$
		}
		IVariableBinding fieldBinding= fragment.resolveBinding();
		ITypeBinding type= fieldBinding == null ? null : fieldBinding.getType();
		if (fieldBinding == null || type == null || !ORG_JUNIT_RULES_TEMPORARY_FOLDER
				.equals(type.getErasure().getQualifiedName())) {
			return rejected("TEMPORARY_FOLDER_BINDING_UNRESOLVED", //$NON-NLS-1$
					"The TemporaryFolder field binding could not be resolved."); //$NON-NLS-1$
		}
		if (Modifier.isStatic(field.getModifiers())) {
			return rejected("TEMPORARY_FOLDER_STATIC_RULE", //$NON-NLS-1$
					"A JUnit 4 @Rule TemporaryFolder is instance-scoped."); //$NON-NLS-1$
		}
		Expression initializer= fragment.getInitializer();
		if (!(initializer instanceof ClassInstanceCreation creation)
				|| creation.getAnonymousClassDeclaration() != null || !creation.arguments().isEmpty()) {
			return rejected("TEMPORARY_FOLDER_INITIALIZER_UNSUPPORTED", //$NON-NLS-1$
					"Only new TemporaryFolder() without custom builder or subclass semantics is supported."); //$NON-NLS-1$
		}
		org.eclipse.jdt.core.dom.CompilationUnit root= field.getRoot() instanceof org.eclipse.jdt.core.dom.CompilationUnit unit
				? unit : null;
		if (root == null || ASTNodes.getParent(field, TypeDeclaration.class) == null) {
			return rejected("TEMPORARY_FOLDER_OWNER_UNRESOLVED", //$NON-NLS-1$
					"The declaring source type could not be resolved."); //$NON-NLS-1$
		}
		if (countTemporaryFolderFields(root) != 1) {
			return rejected("TEMPORARY_FOLDER_MULTIPLE_FIELDS", //$NON-NLS-1$
					"Multiple TemporaryFolder fields require a coordinated migration."); //$NON-NLS-1$
		}
		String fieldKey= variableKey(fieldBinding);
		if (fieldKey == null) {
			return rejected("TEMPORARY_FOLDER_BINDING_UNRESOLVED", //$NON-NLS-1$
					"The TemporaryFolder field has no stable binding key."); //$NON-NLS-1$
		}

		Set<MethodInvocation> allowed= new LinkedHashSet<>();
		AtomicBoolean unsupported= new AtomicBoolean();
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation invocation) {
				if (fieldKey.equals(expressionVariableKey(invocation.getExpression()))) {
					Operation operation= operation(invocation);
					if (operation == null) {
						unsupported.set(true);
					} else {
						allowed.add(invocation);
					}
				}
				return true;
			}

			@Override
			public boolean visit(SimpleName node) {
				IBinding binding= node.resolveBinding();
				if (!(binding instanceof IVariableBinding variable)
						|| !fieldKey.equals(variableKey(variable)) || node == fragment.getName()) {
					return true;
				}
				MethodInvocation invocation= ASTNodes.getParent(node, MethodInvocation.class);
				if (invocation == null || !allowed.contains(invocation)
						|| invocation.getExpression() == null
						|| !isDescendantOf(node, invocation.getExpression())) {
					unsupported.set(true);
				}
				return true;
			}
		});
		if (unsupported.get()) {
			return rejected("TEMPORARY_FOLDER_UNSUPPORTED_USE", //$NON-NLS-1$
					"The rule is passed, assigned, referenced externally, or uses an unsupported TemporaryFolder operation."); //$NON-NLS-1$
		}
		if (allowed.isEmpty()) {
			return rejected("TEMPORARY_FOLDER_UNUSED_RULE", //$NON-NLS-1$
					"No supported TemporaryFolder operation was found."); //$NON-NLS-1$
		}
		if (hasReferencesOutsideCompilationUnit(fieldBinding)) {
			return rejected("TEMPORARY_FOLDER_EXTERNAL_REFERENCE", //$NON-NLS-1$
					"The TemporaryFolder field is referenced outside its compilation unit."); //$NON-NLS-1$
		}
		return new Assessment(true, "TEMPORARY_FOLDER_CLOSED_CONTRACT", //$NON-NLS-1$
				"Every reference has an exact binding-proven TempDir equivalent."); //$NON-NLS-1$
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite imports, JunitHolder holder) {
		FieldDeclaration field= holder.getFieldDeclaration();
		if (!assess(field).eligible()) {
			return;
		}
		VariableDeclarationFragment fragment= (VariableDeclarationFragment) field.fragments().get(0);
		String fieldKey= variableKey(fragment.resolveBinding());
		String fieldName= fragment.getName().getIdentifier();
		org.eclipse.jdt.core.dom.CompilationUnit root= (org.eclipse.jdt.core.dom.CompilationUnit) field.getRoot();

		removeRuleAnnotation(field, rewriter, group);
		String pathName= imports.addImport("java.nio.file.Path"); //$NON-NLS-1$
		rewriter.replace(field.getType(), ast.newSimpleType(ast.newName(pathName)), group);
		rewriter.set(fragment, VariableDeclarationFragment.INITIALIZER_PROPERTY, null, group);
		for (Object modifierObject : field.modifiers()) {
			if (modifierObject instanceof Modifier modifier && modifier.isFinal()) {
				rewriter.remove(modifier, group);
			}
		}
		MarkerAnnotation tempDir= ast.newMarkerAnnotation();
		tempDir.setTypeName(ast.newName(imports.addImport(ORG_JUNIT_JUPITER_API_IO_TEMP_DIR)));
		rewriter.getListRewrite(field, FieldDeclaration.MODIFIERS2_PROPERTY).insertFirst(tempDir, group);
		imports.removeImport(ORG_JUNIT_RULES_TEMPORARY_FOLDER);
		if (!hasOtherRuleAnnotation(root, field)) {
			imports.removeImport(ORG_JUNIT_RULE);
		}

		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation invocation) {
				if (!fieldKey.equals(expressionVariableKey(invocation.getExpression()))) {
					return true;
				}
				Operation operation= operation(invocation);
				if (operation == null) {
					return false;
				}
				if (operation != Operation.GET_ROOT) {
					imports.addImport("java.nio.file.Files"); //$NON-NLS-1$
				}
				rewriter.replace(invocation, replacement(invocation, operation, fieldName, ast), group);
				return false;
			}
		});
	}

	private static Expression replacement(MethodInvocation invocation, Operation operation,
			String fieldName, AST ast) {
		return switch (operation) {
		case GET_ROOT -> toFile(ast, ast.newSimpleName(fieldName));
		case NEW_FILE_RANDOM -> toFile(ast,
				filesCall(ast, "createTempFile", ast.newSimpleName(fieldName), //$NON-NLS-1$
						stringLiteral(ast, "junit"), ast.newNullLiteral())); //$NON-NLS-1$
		case NEW_FILE_NAMED -> toFile(ast,
				filesCall(ast, "createFile", resolve(ast, fieldName, //$NON-NLS-1$
						(Expression) invocation.arguments().get(0))));
		case NEW_FOLDER_RANDOM -> toFile(ast,
				filesCall(ast, "createTempDirectory", ast.newSimpleName(fieldName), //$NON-NLS-1$
						stringLiteral(ast, "junit"))); //$NON-NLS-1$
		case NEW_FOLDER_NAMED -> toFile(ast,
				filesCall(ast, "createDirectory", resolve(ast, fieldName, //$NON-NLS-1$
						(Expression) invocation.arguments().get(0))));
		};
	}

	private static MethodInvocation resolve(AST ast, String fieldName, Expression argument) {
		MethodInvocation result= ast.newMethodInvocation();
		result.setExpression(ast.newSimpleName(fieldName));
		result.setName(ast.newSimpleName("resolve")); //$NON-NLS-1$
		result.arguments().add(TestNameRefactorer.copyAndTransformTestNameReferences(argument, ast));
		return result;
	}

	private static MethodInvocation filesCall(AST ast, String name, Expression... arguments) {
		MethodInvocation result= ast.newMethodInvocation();
		result.setExpression(ast.newSimpleName("Files")); //$NON-NLS-1$
		result.setName(ast.newSimpleName(name));
		for (Expression argument : arguments) {
			result.arguments().add(argument);
		}
		return result;
	}

	private static MethodInvocation toFile(AST ast, Expression expression) {
		MethodInvocation result= ast.newMethodInvocation();
		result.setExpression(expression);
		result.setName(ast.newSimpleName("toFile")); //$NON-NLS-1$
		return result;
	}

	private static org.eclipse.jdt.core.dom.StringLiteral stringLiteral(AST ast, String value) {
		org.eclipse.jdt.core.dom.StringLiteral result= ast.newStringLiteral();
		result.setLiteralValue(value);
		return result;
	}

	private static Operation operation(MethodInvocation invocation) {
		IMethodBinding binding= invocation.resolveMethodBinding();
		ITypeBinding declaring= binding == null ? null : binding.getDeclaringClass();
		if (declaring == null || !ORG_JUNIT_RULES_TEMPORARY_FOLDER
				.equals(declaring.getErasure().getQualifiedName())) {
			return null;
		}
		String name= invocation.getName().getIdentifier();
		int arguments= invocation.arguments().size();
		if ("getRoot".equals(name) && arguments == 0) { //$NON-NLS-1$
			return Operation.GET_ROOT;
		}
		if ("newFile".equals(name)) { //$NON-NLS-1$
			return arguments == 0 ? Operation.NEW_FILE_RANDOM
					: arguments == 1 ? Operation.NEW_FILE_NAMED : null;
		}
		if ("newFolder".equals(name)) { //$NON-NLS-1$
			return arguments == 0 ? Operation.NEW_FOLDER_RANDOM
					: arguments == 1 ? Operation.NEW_FOLDER_NAMED : null;
		}
		return null;
	}

	private static void removeRuleAnnotation(FieldDeclaration field, ASTRewrite rewriter,
			TextEditGroup group) {
		for (Object modifier : field.modifiers()) {
			if (modifier instanceof Annotation annotation) {
				ITypeBinding binding= annotation.resolveTypeBinding();
				if (binding != null && ORG_JUNIT_RULE.equals(binding.getQualifiedName())) {
					rewriter.remove(annotation, group);
					return;
				}
		}
	}

	private static boolean hasOtherRuleAnnotation(org.eclipse.jdt.core.dom.CompilationUnit root,
			FieldDeclaration migratedField) {
		AtomicBoolean other= new AtomicBoolean();
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(Annotation annotation) {
				if (ASTNodes.getParent(annotation, FieldDeclaration.class) == migratedField) {
					return true;
				}
				ITypeBinding binding= annotation.resolveTypeBinding();
				if (binding != null && ORG_JUNIT_RULE.equals(binding.getQualifiedName())) {
					other.set(true);
				}
				return !other.get();
			}
		});
		return other.get();
	}

	private static int countTemporaryFolderFields(org.eclipse.jdt.core.dom.CompilationUnit root) {
		int[] count= { 0 };
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(FieldDeclaration candidate) {
				ITypeBinding binding= candidate.getType().resolveBinding();
				if (binding != null && ORG_JUNIT_RULES_TEMPORARY_FOLDER
						.equals(binding.getErasure().getQualifiedName())) {
					count[0]+= candidate.fragments().size();
				}
				return true;
			}
		});
		return count[0];
	}

	private static boolean hasReferencesOutsideCompilationUnit(IVariableBinding binding) {
		IJavaElement element= binding.getJavaElement();
		IJavaElement ownerElement= element == null ? null : element.getAncestor(IJavaElement.COMPILATION_UNIT);
		if (!(element instanceof IField field) || !(ownerElement instanceof ICompilationUnit owner)) {
			return true;
		}
		SearchPattern pattern= SearchPattern.createPattern(field, IJavaSearchConstants.REFERENCES);
		if (pattern == null) {
			return true;
		}
		AtomicBoolean external= new AtomicBoolean();
		SearchRequestor requestor= new SearchRequestor() {
			@Override
			public void acceptSearchMatch(SearchMatch match) {
				Object matchElement= match.getElement();
				if (!(matchElement instanceof IJavaElement javaElement)) {
					external.set(true);
					return;
				}
				IJavaElement matchedOwner= javaElement.getAncestor(IJavaElement.COMPILATION_UNIT);
				if (!(matchedOwner instanceof ICompilationUnit matchedUnit)
						|| !owner.getPrimary().equals(matchedUnit.getPrimary())) {
					external.set(true);
				}
			}
		};
		try {
			new SearchEngine().search(pattern,
					new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
					SearchEngine.createWorkspaceScope(), requestor, new NullProgressMonitor());
		} catch (CoreException exception) {
			return true;
		}
		return external.get();
	}

	private static String expressionVariableKey(Expression expression) {
		if (expression instanceof SimpleName name && name.resolveBinding() instanceof IVariableBinding variable) {
			return variableKey(variable);
		}
		if (expression instanceof FieldAccess access) {
			return variableKey(access.resolveFieldBinding());
		}
		if (expression instanceof QualifiedName name && name.resolveBinding() instanceof IVariableBinding variable) {
			return variableKey(variable);
		}
		return null;
	}

	private static String variableKey(IVariableBinding binding) {
		if (binding == null) {
			return null;
		}
		IVariableBinding declaration= binding.getVariableDeclaration();
		return declaration == null ? null : declaration.getKey();
	}

	private static boolean isDescendantOf(ASTNode node, ASTNode ancestor) {
		for (ASTNode current= node; current != null; current= current.getParent()) {
			if (current == ancestor) {
				return true;
			}
		}
		return false;
	}

	private static Assessment rejected(String code, String explanation) {
		return new Assessment(false, code, explanation);
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					@TempDir
					Path tempFolder;

					@Test
					public void test3() throws IOException {
						File newFile = Files.createFile(tempFolder.resolve("myfile.txt")).toFile();
					}
					"""; //$NON-NLS-1$
		}
		return """
					@Rule
					public TemporaryFolder tempFolder = new TemporaryFolder();

					@Test
					public void test3() throws IOException {
						File newFile = tempFolder.newFile("myfile.txt");
					}
					"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "RuleTemporaryFolder"; //$NON-NLS-1$
	}
}
