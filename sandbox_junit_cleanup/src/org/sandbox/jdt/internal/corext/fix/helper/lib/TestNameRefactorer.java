/*******************************************************************************
 * Copyright (c) 2025, 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper.lib;

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_JUPITER_API_BEFORE_EACH;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_JUPITER_API_TEST_INFO;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_RULE;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_RULES_TEST_NAME;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.ThisExpression;
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

/**
 * Fail-closed migration of JUnit 4 {@code TestName} rules.
 *
 * <p>{@code TestName.getMethodName()} is equivalent to the Java method name,
 * not Jupiter's display name. The migration therefore derives the value from
 * {@code TestInfo.getTestMethod().orElseThrow().getName()} and rejects every
 * other use of the rule field.</p>
 */
public final class TestNameRefactorer {

	/** Stable eligibility decision used by the cleanup and its tests. */
	public record Assessment(boolean eligible, String reasonCode, String explanation) {
		public Assessment {
			reasonCode= Objects.requireNonNull(reasonCode);
			explanation= Objects.requireNonNull(explanation);
		}
	}

	private record SourceRange(int start, int length) {
	}

	private TestNameRefactorer() {
		throw new UnsupportedOperationException("Utility class"); //$NON-NLS-1$
	}

	/** Proves that all references are local, bound and exact getMethodName calls. */
	public static Assessment assess(FieldDeclaration field) {
		if (field == null || field.fragments().size() != 1
				|| !(field.fragments().get(0) instanceof VariableDeclarationFragment fragment)) {
			return rejected("TEST_NAME_FIELD_SHAPE_UNSUPPORTED", //$NON-NLS-1$
					"The TestName rule must contain exactly one field fragment."); //$NON-NLS-1$
		}
		IVariableBinding binding= fragment.resolveBinding();
		ITypeBinding fieldType= binding == null ? null : binding.getType();
		if (binding == null || fieldType == null
				|| !ORG_JUNIT_RULES_TEST_NAME.equals(fieldType.getErasure().getQualifiedName())) {
			return rejected("TEST_NAME_BINDING_UNRESOLVED", //$NON-NLS-1$
					"The TestName field binding could not be resolved exactly."); //$NON-NLS-1$
		}
		if (Modifier.isStatic(field.getModifiers())) {
			return rejected("TEST_NAME_STATIC_RULE", //$NON-NLS-1$
					"A JUnit 4 @Rule TestName field must remain instance-scoped."); //$NON-NLS-1$
		}
		TypeDeclaration owner= ASTNodes.getParent(field, TypeDeclaration.class);
		CompilationUnit root= field.getRoot() instanceof CompilationUnit unit ? unit : null;
		if (owner == null || root == null) {
			return rejected("TEST_NAME_OWNER_UNRESOLVED", //$NON-NLS-1$
					"The declaring source type could not be resolved."); //$NON-NLS-1$
		}
		if (countTestNameFields(root) != 1) {
			return rejected("TEST_NAME_MULTIPLE_FIELDS", //$NON-NLS-1$
					"Multiple TestName fields in one compilation unit require a coordinated migration."); //$NON-NLS-1$
		}

		String fieldKey= variableKey(binding);
		if (fieldKey == null) {
			return rejected("TEST_NAME_BINDING_UNRESOLVED", //$NON-NLS-1$
					"The TestName field has no stable binding key."); //$NON-NLS-1$
		}
		AtomicBoolean unsupported= new AtomicBoolean();
		int[] supportedInvocations= { 0 };
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName node) {
				IBinding resolved= node.resolveBinding();
				if (!(resolved instanceof IVariableBinding variable)
						|| !fieldKey.equals(variableKey(variable))) {
					return true;
				}
				if (node == fragment.getName()) {
					return true;
				}
				MethodInvocation invocation= ASTNodes.getParent(node, MethodInvocation.class);
				if (invocation != null && invocation.getExpression() != null
						&& isDescendantOf(node, invocation.getExpression())
						&& isSupportedInvocation(invocation, fieldKey)) {
					supportedInvocations[0]++;
					return true;
				}
				unsupported.set(true);
				return true;
			}
		});
		if (unsupported.get()) {
			return rejected("TEST_NAME_UNSUPPORTED_USE", //$NON-NLS-1$
					"The rule field is used for something other than getMethodName()."); //$NON-NLS-1$
		}
		if (hasReferencesOutsideCompilationUnit(binding, root)) {
			return rejected("TEST_NAME_EXTERNAL_REFERENCE", //$NON-NLS-1$
					"The TestName field is referenced outside its compilation unit."); //$NON-NLS-1$
		}
		if (supportedInvocations[0] == 0) {
			return new Assessment(true, "TEST_NAME_UNUSED_RULE", //$NON-NLS-1$
					"The TestName field has no workspace references and can be migrated without blocking the surrounding lifecycle."); //$NON-NLS-1$
		}
		return new Assessment(true, "TEST_NAME_LOCAL_GET_METHOD_NAME", //$NON-NLS-1$
				"Every workspace reference is a local bound getMethodName() call."); //$NON-NLS-1$
	}

	/** Performs the proven local migration. */
	public static void refactorTestnameInClass(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewrite, FieldDeclaration field) {
		Assessment assessment= assess(field);
		if (!assessment.eligible()) {
			return;
		}
		VariableDeclarationFragment fragment= (VariableDeclarationFragment) field.fragments().get(0);
		IVariableBinding binding= fragment.resolveBinding();
		String fieldKey= variableKey(binding);
		String fieldName= fragment.getName().getIdentifier();
		TypeDeclaration owner= ASTNodes.getParent(field, TypeDeclaration.class);
		CompilationUnit root= (CompilationUnit) field.getRoot();

		removeRuleAnnotation(field, rewriter, group);
		rewriter.replace(field.getType(), ast.newSimpleType(ast.newSimpleName("String")), group); //$NON-NLS-1$
		rewriter.set(fragment, VariableDeclarationFragment.INITIALIZER_PROPERTY, null, group);
		for (Object modifierObject : field.modifiers()) {
			if (modifierObject instanceof Modifier modifier && modifier.isFinal()) {
				rewriter.remove(modifier, group);
			}
		}

		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation invocation) {
				if (isSupportedInvocation(invocation, fieldKey)) {
					Expression replacement= (Expression) ASTNode.copySubtree(ast, invocation.getExpression());
					rewriter.replace(invocation, replacement, group);
					return false;
				}
				return true;
			}
		});

		String beforeEachName= importRewrite.addImport(ORG_JUNIT_JUPITER_API_BEFORE_EACH);
		String testInfoName= importRewrite.addImport(ORG_JUNIT_JUPITER_API_TEST_INFO);
		importRewrite.removeImport(ORG_JUNIT_RULES_TEST_NAME);
		if (!hasOtherRuleAnnotation(root, field)) {
			importRewrite.removeImport(ORG_JUNIT_RULE);
		}
		MethodDeclaration initializer= createInitializer(owner, fieldName, beforeEachName, testInfoName);
		rewriter.getListRewrite(owner, TypeDeclaration.BODY_DECLARATIONS_PROPERTY)
				.insertAfter(initializer, field, group);
	}

	/**
	 * Historical entry point. Project-search eligibility proves there are no
	 * subclass or cross-file references, so the safe operation is deliberately
	 * local rather than mutating independently parsed ASTs.
	 */
	public static void refactorTestnameInClassAndSubclasses(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewrite, FieldDeclaration field) {
		refactorTestnameInClass(group, rewriter, ast, importRewrite, field);
	}

	private static MethodDeclaration createInitializer(TypeDeclaration owner, String fieldName,
			String beforeEachName, String testInfoName) {
		AST ast= owner.getAST();
		MethodDeclaration method= ast.newMethodDeclaration();
		method.setName(ast.newSimpleName(uniqueMethodName(owner,
				"initialize" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1) //$NON-NLS-1$
						+ "FromTestInfo"))); //$NON-NLS-1$
		method.setReturnType2(ast.newPrimitiveType(PrimitiveType.VOID));
		MarkerAnnotation beforeEach= ast.newMarkerAnnotation();
		beforeEach.setTypeName(ast.newName(beforeEachName));
		method.modifiers().add(beforeEach);

		SingleVariableDeclaration parameter= ast.newSingleVariableDeclaration();
		parameter.setType(ast.newSimpleType(ast.newName(testInfoName)));
		parameter.setName(ast.newSimpleName("testInfo")); //$NON-NLS-1$
		method.parameters().add(parameter);

		MethodInvocation getTestMethod= ast.newMethodInvocation();
		getTestMethod.setExpression(ast.newSimpleName("testInfo")); //$NON-NLS-1$
		getTestMethod.setName(ast.newSimpleName("getTestMethod")); //$NON-NLS-1$
		MethodInvocation orElseThrow= ast.newMethodInvocation();
		orElseThrow.setExpression(getTestMethod);
		orElseThrow.setName(ast.newSimpleName("orElseThrow")); //$NON-NLS-1$
		MethodInvocation getName= ast.newMethodInvocation();
		getName.setExpression(orElseThrow);
		getName.setName(ast.newSimpleName("getName")); //$NON-NLS-1$

		ThisExpression thisExpression= ast.newThisExpression();
		FieldAccess target= ast.newFieldAccess();
		target.setExpression(thisExpression);
		target.setName(ast.newSimpleName(fieldName));
		Assignment assignment= ast.newAssignment();
		assignment.setLeftHandSide(target);
		assignment.setRightHandSide(getName);
		Block body= ast.newBlock();
		body.statements().add(ast.newExpressionStatement(assignment));
		method.setBody(body);
		return method;
	}

	private static String uniqueMethodName(TypeDeclaration owner, String base) {
		List<String> names= new ArrayList<>();
		for (MethodDeclaration method : owner.getMethods()) {
			names.add(method.getName().getIdentifier());
		}
		if (!names.contains(base)) {
			return base;
		}
		for (int suffix= 2; ; suffix++) {
			String candidate= base + suffix;
			if (!names.contains(candidate)) {
				return candidate;
			}
		}
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
	}

	private static boolean hasOtherRuleAnnotation(CompilationUnit root, FieldDeclaration migratedField) {
		AtomicBoolean other= new AtomicBoolean();
		root.accept(new ASTVisitor() {
			@Override
			public boolean preVisit2(ASTNode node) {
				if (!(node instanceof Annotation annotation)) {
					return !other.get();
				}
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

	private static int countTestNameFields(CompilationUnit root) {
		int[] count= { 0 };
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(FieldDeclaration field) {
				ITypeBinding binding= field.getType().resolveBinding();
				if (binding != null && ORG_JUNIT_RULES_TEST_NAME.equals(binding.getErasure().getQualifiedName())) {
					count[0]+= field.fragments().size();
				}
				return true;
			}
		});
		return count[0];
	}

	private static boolean isSupportedInvocation(MethodInvocation invocation, String fieldKey) {
		if (!"getMethodName".equals(invocation.getName().getIdentifier()) //$NON-NLS-1$
				|| !invocation.arguments().isEmpty() || invocation.getExpression() == null
				|| !fieldKey.equals(expressionVariableKey(invocation.getExpression()))) {
			return false;
		}
		IMethodBinding binding= invocation.resolveMethodBinding();
		ITypeBinding declaring= binding == null ? null : binding.getDeclaringClass();
		return declaring != null && ORG_JUNIT_RULES_TEST_NAME.equals(declaring.getErasure().getQualifiedName());
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

	private static boolean hasReferencesOutsideCompilationUnit(IVariableBinding binding, CompilationUnit root) {
		IJavaElement javaElement= binding.getJavaElement();
		IJavaElement ownerElement= javaElement == null ? null
				: javaElement.getAncestor(IJavaElement.COMPILATION_UNIT);
		if (!(javaElement instanceof IField field) || !(ownerElement instanceof ICompilationUnit owner)) {
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
				Object element= match.getElement();
				if (!(element instanceof IJavaElement matched)) {
					external.set(true);
					return;
				}
				IJavaElement matchedOwner= matched.getAncestor(IJavaElement.COMPILATION_UNIT);
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

	/**
	 * Copies an expression and rewrites only binding-proven TestName getMethodName
	 * invocations. This remains available to the TemporaryFolder migration.
	 */
	public static ASTNode copyAndTransformTestNameReferences(ASTNode originalNode, AST ast) {
		if (originalNode == null || ast == null) {
			return originalNode;
		}
		Map<SourceRange, Expression> replacements= new LinkedHashMap<>();
		originalNode.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation invocation) {
				IMethodBinding method= invocation.resolveMethodBinding();
				ITypeBinding declaring= method == null ? null : method.getDeclaringClass();
				if (declaring != null && ORG_JUNIT_RULES_TEST_NAME.equals(declaring.getErasure().getQualifiedName())
						&& "getMethodName".equals(invocation.getName().getIdentifier()) //$NON-NLS-1$
						&& invocation.arguments().isEmpty() && invocation.getExpression() != null) {
					replacements.put(new SourceRange(invocation.getStartPosition(), invocation.getLength()),
							invocation.getExpression());
				}
				return true;
			}
		});
		ASTNode copy= ASTNode.copySubtree(ast, originalNode);
		if (replacements.isEmpty()) {
			return copy;
		}
		copy.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation invocation) {
				Expression replacement= replacements.get(
						new SourceRange(invocation.getStartPosition(), invocation.getLength()));
				if (replacement != null) {
					replaceInParent(invocation, ASTNode.copySubtree(ast, replacement));
					return false;
				}
				return true;
			}
		});
		return copy;
	}

	/** Copied nodes without original bindings are deliberately left unchanged. */
	@Deprecated
	public static ASTNode transformTestNameReferencesInCopy(ASTNode copiedNode, AST ast) {
		return copiedNode;
	}

	@SuppressWarnings("unchecked")
	private static void replaceInParent(ASTNode oldNode, ASTNode newNode) {
		ASTNode parent= oldNode.getParent();
		if (parent == null) {
			return;
		}
		StructuralPropertyDescriptor location= oldNode.getLocationInParent();
		if (location.isChildProperty()) {
			parent.setStructuralProperty(location, newNode);
		} else if (location.isChildListProperty()) {
			List<ASTNode> list= (List<ASTNode>) parent.getStructuralProperty(location);
			int index= list.indexOf(oldNode);
			if (index >= 0) {
				list.set(index, newNode);
			}
		}
	}

	private static Assessment rejected(String reasonCode, String explanation) {
		return new Assessment(false, reasonCode, explanation);
	}
}
