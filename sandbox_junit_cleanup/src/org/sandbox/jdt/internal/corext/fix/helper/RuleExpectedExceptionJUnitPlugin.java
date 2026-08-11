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
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_JUPITER_API_ASSERTIONS;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_RULE;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_RULES_EXPECTED_EXCEPTION;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_TEST;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
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
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;
import org.sandbox.jdt.internal.corext.fix.helper.lib.TriggerPatternCleanupPlugin;
import org.sandbox.jdt.triggerpattern.api.CleanupPattern;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.PatternKind;

/**
 * Fail-closed migration of the supported JUnit 4 ExpectedException contract.
 * Unsupported matchers or non-local rule references leave the complete field
 * unchanged instead of silently dropping expectations.
 */
@CleanupPattern(value = "@Rule public ExpectedException $name", kind = PatternKind.FIELD,
		qualifiedType = ORG_JUNIT_RULES_EXPECTED_EXCEPTION,
		cleanupId = "cleanup.junit.ruleexpectedexception",
		description = "Migrate a closed ExpectedException contract to assertThrows()",
		displayName = "JUnit 4 @Rule ExpectedException → JUnit 5 assertThrows()")
public class RuleExpectedExceptionJUnitPlugin extends TriggerPatternCleanupPlugin {

	/** Stable fail-closed decision exposed to regression tests. */
	public record Assessment(boolean eligible, String reasonCode, String explanation) {
		public Assessment {
			reasonCode= Objects.requireNonNull(reasonCode);
			explanation= Objects.requireNonNull(explanation);
		}
	}

	private record MethodPlan(MethodDeclaration method, int firstIndex, int lastIndex,
			TypeLiteral exceptionType, Expression messageSubstring, TypeLiteral causeType) {
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

	/** Proves that every reference can be represented without losing semantics. */
	public static Assessment assess(FieldDeclaration field) {
		if (field == null || field.fragments().size() != 1
				|| !(field.fragments().get(0) instanceof VariableDeclarationFragment fragment)) {
			return rejected("EXPECTED_EXCEPTION_FIELD_SHAPE_UNSUPPORTED", //$NON-NLS-1$
					"ExpectedException must be declared as one rule field."); //$NON-NLS-1$
		}
		IVariableBinding fieldBinding= fragment.resolveBinding();
		ITypeBinding type= fieldBinding == null ? null : fieldBinding.getType();
		if (fieldBinding == null || type == null
				|| !ORG_JUNIT_RULES_EXPECTED_EXCEPTION.equals(type.getErasure().getQualifiedName())) {
			return rejected("EXPECTED_EXCEPTION_BINDING_UNRESOLVED", //$NON-NLS-1$
					"The ExpectedException field binding could not be resolved."); //$NON-NLS-1$
		}
		TypeDeclaration owner= ASTNodes.getParent(field, TypeDeclaration.class);
		CompilationUnitView view= CompilationUnitView.of(field);
		if (owner == null || view.root() == null) {
			return rejected("EXPECTED_EXCEPTION_OWNER_UNRESOLVED", //$NON-NLS-1$
					"The declaring source type could not be resolved."); //$NON-NLS-1$
		}
		if (countExpectedExceptionFields(view.root()) != 1) {
			return rejected("EXPECTED_EXCEPTION_MULTIPLE_FIELDS", //$NON-NLS-1$
					"Multiple ExpectedException fields require a coordinated migration."); //$NON-NLS-1$
		}
		String fieldKey= variableKey(fieldBinding);
		if (fieldKey == null) {
			return rejected("EXPECTED_EXCEPTION_BINDING_UNRESOLVED", //$NON-NLS-1$
					"The field has no stable binding key."); //$NON-NLS-1$
		}

		List<MethodPlan> plans= new ArrayList<>();
		Set<MethodInvocation> allowedInvocations= new LinkedHashSet<>();
		for (MethodDeclaration method : owner.getMethods()) {
			MethodAnalysis analysis= analyzeMethod(method, fieldKey);
			if (!analysis.valid()) {
				return rejected(analysis.reasonCode(), analysis.explanation());
			}
			if (analysis.plan() != null) {
				plans.add(analysis.plan());
				allowedInvocations.addAll(analysis.configurationInvocations());
			}
		}
		if (plans.isEmpty()) {
			return rejected("EXPECTED_EXCEPTION_UNUSED_RULE", //$NON-NLS-1$
					"No supported ExpectedException contract was found in a JUnit 4 test method."); //$NON-NLS-1$
		}
		if (hasUnsupportedReference(view.root(), fragment, fieldKey, allowedInvocations)) {
			return rejected("EXPECTED_EXCEPTION_UNSUPPORTED_USE", //$NON-NLS-1$
					"The rule field is referenced outside the supported top-level expectation statements."); //$NON-NLS-1$
		}
		if (hasReferencesOutsideCompilationUnit(fieldBinding)) {
			return rejected("EXPECTED_EXCEPTION_EXTERNAL_REFERENCE", //$NON-NLS-1$
					"The rule field is referenced outside its compilation unit."); //$NON-NLS-1$
		}
		return new Assessment(true, "EXPECTED_EXCEPTION_CLOSED_CONTRACT", //$NON-NLS-1$
				"Every expectation is local, ordered and exactly representable with Jupiter assertions."); //$NON-NLS-1$
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, JunitHolder junitHolder) {
		FieldDeclaration field= junitHolder.getFieldDeclaration();
		Assessment assessment= assess(field);
		if (!assessment.eligible()) {
			return;
		}
		VariableDeclarationFragment fragment= (VariableDeclarationFragment) field.fragments().get(0);
		String fieldKey= variableKey(fragment.resolveBinding());
		TypeDeclaration owner= ASTNodes.getParent(field, TypeDeclaration.class);

		rewriter.remove(field, group);
		importRewriter.removeImport(ORG_JUNIT_RULES_EXPECTED_EXCEPTION);
		if (!hasOtherRuleAnnotation((org.eclipse.jdt.core.dom.CompilationUnit) field.getRoot(), field)) {
			importRewriter.removeImport(ORG_JUNIT_RULE);
		}
		importRewriter.addStaticImport(ORG_JUNIT_JUPITER_API_ASSERTIONS, "assertThrows", false); //$NON-NLS-1$

		for (MethodDeclaration method : owner.getMethods()) {
			MethodAnalysis analysis= analyzeMethod(method, fieldKey);
			if (analysis.plan() != null) {
				applyPlan(analysis.plan(), rewriter, ast, group, importRewriter);
			}
		}
	}

	private static void applyPlan(MethodPlan plan, ASTRewrite rewriter, AST ast, TextEditGroup group,
			ImportRewrite imports) {
		Block body= plan.method().getBody();
		@SuppressWarnings("unchecked")
		List<Statement> statements= body.statements();
		boolean capture= plan.messageSubstring() != null || plan.causeType() != null;
		String exceptionName= capture
				? uniqueVariableName("exception", getUsedVariableNames(plan.method())) //$NON-NLS-1$
				: null;

		MethodInvocation assertThrows= ast.newMethodInvocation();
		assertThrows.setName(ast.newSimpleName("assertThrows")); //$NON-NLS-1$
		assertThrows.arguments().add(ASTNode.copySubtree(ast, plan.exceptionType()));
		LambdaExpression lambda= ast.newLambdaExpression();
		lambda.setParentheses(true);
		Block lambdaBody= ast.newBlock();
		for (int index= plan.lastIndex() + 1; index < statements.size(); index++) {
			lambdaBody.statements().add(ASTNode.copySubtree(ast, statements.get(index)));
		}
		lambda.setBody(lambdaBody);
		assertThrows.arguments().add(lambda);

		Statement replacement;
		if (capture) {
			VariableDeclarationFragment declaration= ast.newVariableDeclarationFragment();
			declaration.setName(ast.newSimpleName(exceptionName));
			declaration.setInitializer(assertThrows);
			VariableDeclarationStatement variable= ast.newVariableDeclarationStatement(declaration);
			variable.setType((Type) ASTNode.copySubtree(ast, plan.exceptionType().getType()));
			replacement= variable;
		} else {
			replacement= ast.newExpressionStatement(assertThrows);
		}

		for (int index= statements.size() - 1; index >= plan.firstIndex(); index--) {
			rewriter.remove(statements.get(index), group);
		}
		ListRewriteFacade append= new ListRewriteFacade(rewriter, body, group);
		append.add(replacement);
		if (plan.messageSubstring() != null) {
			imports.addStaticImport(ORG_JUNIT_JUPITER_API_ASSERTIONS, "assertTrue", false); //$NON-NLS-1$
			append.add(messageAssertion(ast, exceptionName, plan.messageSubstring()));
		}
		if (plan.causeType() != null) {
			imports.addStaticImport(ORG_JUNIT_JUPITER_API_ASSERTIONS, "assertInstanceOf", false); //$NON-NLS-1$
			append.add(causeAssertion(ast, exceptionName, plan.causeType()));
		}
	}

	private static Statement messageAssertion(AST ast, String exceptionName, Expression expectedSubstring) {
		MethodInvocation firstMessage= messageCall(ast, exceptionName);
		InfixExpression notNull= ast.newInfixExpression();
		notNull.setLeftOperand(firstMessage);
		notNull.setOperator(InfixExpression.Operator.NOT_EQUALS);
		notNull.setRightOperand(ast.newNullLiteral());

		MethodInvocation contains= ast.newMethodInvocation();
		contains.setExpression(messageCall(ast, exceptionName));
		contains.setName(ast.newSimpleName("contains")); //$NON-NLS-1$
		contains.arguments().add(ASTNode.copySubtree(ast, expectedSubstring));
		InfixExpression condition= ast.newInfixExpression();
		condition.setLeftOperand(notNull);
		condition.setOperator(InfixExpression.Operator.CONDITIONAL_AND);
		condition.setRightOperand(contains);

		MethodInvocation assertion= ast.newMethodInvocation();
		assertion.setName(ast.newSimpleName("assertTrue")); //$NON-NLS-1$
		assertion.arguments().add(condition);
		return ast.newExpressionStatement(assertion);
	}

	private static MethodInvocation messageCall(AST ast, String exceptionName) {
		MethodInvocation result= ast.newMethodInvocation();
		result.setExpression(ast.newSimpleName(exceptionName));
		result.setName(ast.newSimpleName("getMessage")); //$NON-NLS-1$
		return result;
	}

	private static Statement causeAssertion(AST ast, String exceptionName, TypeLiteral causeType) {
		MethodInvocation cause= ast.newMethodInvocation();
		cause.setExpression(ast.newSimpleName(exceptionName));
		cause.setName(ast.newSimpleName("getCause")); //$NON-NLS-1$
		MethodInvocation assertion= ast.newMethodInvocation();
		assertion.setName(ast.newSimpleName("assertInstanceOf")); //$NON-NLS-1$
		assertion.arguments().add(ASTNode.copySubtree(ast, causeType));
		assertion.arguments().add(cause);
		return ast.newExpressionStatement(assertion);
	}

	private record MethodAnalysis(boolean valid, String reasonCode, String explanation,
			MethodPlan plan, Set<MethodInvocation> configurationInvocations) {
		static MethodAnalysis empty() {
			return new MethodAnalysis(true, "", "", null, Set.of()); //$NON-NLS-1$ //$NON-NLS-2$
		}
		static MethodAnalysis rejected(String code, String explanation) {
			return new MethodAnalysis(false, code, explanation, null, Set.of());
		}
	}

	private static MethodAnalysis analyzeMethod(MethodDeclaration method, String fieldKey) {
		Block body= method.getBody();
		if (body == null) {
			return MethodAnalysis.empty();
		}
		@SuppressWarnings("unchecked")
		List<Statement> statements= body.statements();
		List<Integer> indices= new ArrayList<>();
		Set<MethodInvocation> invocations= new LinkedHashSet<>();
		TypeLiteral exceptionType= null;
		Expression message= null;
		TypeLiteral cause= null;
		for (int index= 0; index < statements.size(); index++) {
			MethodInvocation invocation= configurationInvocation(statements.get(index), fieldKey);
			if (invocation == null) {
				continue;
			}
			indices.add(Integer.valueOf(index));
			invocations.add(invocation);
			String name= invocation.getName().getIdentifier();
			if ("expect".equals(name)) { //$NON-NLS-1$
				if (exceptionType != null || invocation.arguments().size() != 1
						|| !(invocation.arguments().get(0) instanceof TypeLiteral literal)) {
					return MethodAnalysis.rejected("EXPECTED_EXCEPTION_EXPECT_UNSUPPORTED", //$NON-NLS-1$
							"Only one expect(ExceptionType.class) call is supported per test method."); //$NON-NLS-1$
				}
				exceptionType= literal;
			} else if ("expectMessage".equals(name)) { //$NON-NLS-1$
				if (message != null || invocation.arguments().size() != 1
						|| !(invocation.arguments().get(0) instanceof Expression expression)
						|| !isJavaLangString(expression.resolveTypeBinding())) {
					return MethodAnalysis.rejected("EXPECTED_EXCEPTION_MESSAGE_MATCHER_UNSUPPORTED", //$NON-NLS-1$
							"Only one String substring expectation is supported; matcher overloads are rejected."); //$NON-NLS-1$
				}
				message= expression;
			} else if ("expectCause".equals(name)) { //$NON-NLS-1$
				TypeLiteral literal= causeType(invocation);
				if (cause != null || literal == null) {
					return MethodAnalysis.rejected("EXPECTED_EXCEPTION_CAUSE_MATCHER_UNSUPPORTED", //$NON-NLS-1$
							"Only instanceOf/isA(ExceptionType.class) cause matchers are supported."); //$NON-NLS-1$
				}
				cause= literal;
			} else {
				return MethodAnalysis.rejected("EXPECTED_EXCEPTION_UNSUPPORTED_USE", //$NON-NLS-1$
						"An unsupported ExpectedException method is used."); //$NON-NLS-1$
			}
		}
		if (indices.isEmpty()) {
			return MethodAnalysis.empty();
		}
		if (!isJUnit4Test(method)) {
			return MethodAnalysis.rejected("EXPECTED_EXCEPTION_NON_TEST_METHOD", //$NON-NLS-1$
					"Expectation calls in helper or lifecycle methods cannot be moved safely."); //$NON-NLS-1$
		}
		int first= indices.get(0).intValue();
		int last= indices.get(indices.size() - 1).intValue();
		if (last - first + 1 != indices.size()) {
			return MethodAnalysis.rejected("EXPECTED_EXCEPTION_INTERLEAVED_CONFIGURATION", //$NON-NLS-1$
					"Expectation calls must be consecutive top-level statements."); //$NON-NLS-1$
		}
		if (exceptionType == null) {
			return MethodAnalysis.rejected("EXPECTED_EXCEPTION_TYPE_MISSING", //$NON-NLS-1$
					"A supported expect(ExceptionType.class) call is required."); //$NON-NLS-1$
		}
		if (last + 1 >= statements.size()) {
			return MethodAnalysis.rejected("EXPECTED_EXCEPTION_NO_THROWING_BODY", //$NON-NLS-1$
					"No statements remain for the assertThrows executable."); //$NON-NLS-1$
		}
		return new MethodAnalysis(true, "", "", //$NON-NLS-1$ //$NON-NLS-2$
				new MethodPlan(method, first, last, exceptionType, message, cause), Set.copyOf(invocations));
	}

	private static MethodInvocation configurationInvocation(Statement statement, String fieldKey) {
		if (!(statement instanceof ExpressionStatement expressionStatement)
				|| !(expressionStatement.getExpression() instanceof MethodInvocation invocation)
				|| invocation.getExpression() == null
				|| !fieldKey.equals(expressionVariableKey(invocation.getExpression()))) {
			return null;
		}
		IMethodBinding binding= invocation.resolveMethodBinding();
		ITypeBinding declaring= binding == null ? null : binding.getDeclaringClass();
		return declaring != null && ORG_JUNIT_RULES_EXPECTED_EXCEPTION
				.equals(declaring.getErasure().getQualifiedName()) ? invocation : null;
	}

	private static TypeLiteral causeType(MethodInvocation invocation) {
		if (invocation.arguments().size() != 1
				|| !(invocation.arguments().get(0) instanceof MethodInvocation matcher)
				|| !("instanceOf".equals(matcher.getName().getIdentifier()) //$NON-NLS-1$
						|| "isA".equals(matcher.getName().getIdentifier())) //$NON-NLS-1$
				|| matcher.arguments().size() != 1
				|| !(matcher.arguments().get(0) instanceof TypeLiteral literal)) {
			return null;
		}
		IMethodBinding binding= matcher.resolveMethodBinding();
		ITypeBinding declaring= binding == null ? null : binding.getDeclaringClass();
		String packageName= declaring == null || declaring.getPackage() == null
				? "" : declaring.getPackage().getName(); //$NON-NLS-1$
		return packageName.startsWith("org.hamcrest") ? literal : null; //$NON-NLS-1$
	}

	private static boolean hasUnsupportedReference(org.eclipse.jdt.core.dom.CompilationUnit root,
			VariableDeclarationFragment declaration, String fieldKey,
			Set<MethodInvocation> allowedInvocations) {
		AtomicBoolean unsupported= new AtomicBoolean();
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName node) {
				IBinding binding= node.resolveBinding();
				if (!(binding instanceof IVariableBinding variable)
						|| !fieldKey.equals(variableKey(variable)) || node == declaration.getName()) {
					return true;
				}
				MethodInvocation invocation= ASTNodes.getParent(node, MethodInvocation.class);
				if (invocation == null || !allowedInvocations.contains(invocation)
						|| invocation.getExpression() == null
						|| !isDescendantOf(node, invocation.getExpression())) {
					unsupported.set(true);
				}
				return true;
			}
		});
		return unsupported.get();
	}

	private static boolean isJUnit4Test(MethodDeclaration method) {
		for (Object modifier : method.modifiers()) {
			if (modifier instanceof Annotation annotation) {
				ITypeBinding binding= annotation.resolveTypeBinding();
				if (binding != null && ORG_JUNIT_TEST.equals(binding.getQualifiedName())) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean isJavaLangString(ITypeBinding binding) {
		return binding != null && "java.lang.String".equals(binding.getErasure().getQualifiedName()); //$NON-NLS-1$
	}

	private static int countExpectedExceptionFields(org.eclipse.jdt.core.dom.CompilationUnit root) {
		int[] count= { 0 };
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(FieldDeclaration field) {
				ITypeBinding binding= field.getType().resolveBinding();
				if (binding != null && ORG_JUNIT_RULES_EXPECTED_EXCEPTION
						.equals(binding.getErasure().getQualifiedName())) {
					count[0]+= field.fragments().size();
				}
				return true;
			}
		});
		return count[0];
	}

	private static boolean hasOtherRuleAnnotation(org.eclipse.jdt.core.dom.CompilationUnit root,
			FieldDeclaration migratedField) {
		AtomicBoolean result= new AtomicBoolean();
		root.accept(new ASTVisitor() {
			@Override
			public boolean preVisit2(ASTNode node) {
				if (!(node instanceof Annotation annotation)) {
					return !result.get();
				}
				if (ASTNodes.getParent(annotation, FieldDeclaration.class) == migratedField) {
					return true;
				}
				ITypeBinding binding= annotation.resolveTypeBinding();
				if (binding != null && ORG_JUNIT_RULE.equals(binding.getQualifiedName())) {
					result.set(true);
				}
				return !result.get();
			}
		});
		return result.get();
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

	private static String uniqueVariableName(String base, Collection<String> used) {
		if (!used.contains(base)) {
			return base;
		}
		for (int suffix= 2; ; suffix++) {
			String candidate= base + suffix;
			if (!used.contains(candidate)) {
				return candidate;
			}
		}
	}

	private static Assessment rejected(String code, String explanation) {
		return new Assessment(false, code, explanation);
	}

	private record CompilationUnitView(org.eclipse.jdt.core.dom.CompilationUnit root) {
		static CompilationUnitView of(ASTNode node) {
			return new CompilationUnitView(node.getRoot() instanceof org.eclipse.jdt.core.dom.CompilationUnit root
					? root : null);
		}
	}

	private static final class ListRewriteFacade {
		private final org.eclipse.jdt.core.dom.rewrite.ListRewrite rewrite;
		private final TextEditGroup group;

		ListRewriteFacade(ASTRewrite astRewrite, Block body, TextEditGroup group) {
			this.rewrite= astRewrite.getListRewrite(body, Block.STATEMENTS_PROPERTY);
			this.group= group;
		}

		void add(Statement statement) {
			rewrite.insertLast(statement, group);
		}
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
				import static org.junit.jupiter.api.Assertions.assertThrows;
				import static org.junit.jupiter.api.Assertions.assertTrue;

				@Test
				void testException() {
					IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
						throw new IllegalArgumentException("Invalid argument details");
					});
					assertTrue(exception.getMessage() != null
							&& exception.getMessage().contains("Invalid argument"));
				}
				"""; //$NON-NLS-1$
		}
		return """
				@Rule
				public ExpectedException thrown = ExpectedException.none();

				@Test
				public void testException() {
					thrown.expect(IllegalArgumentException.class);
					thrown.expectMessage("Invalid argument");
					throw new IllegalArgumentException("Invalid argument details");
				}
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "RuleExpectedException"; //$NON-NLS-1$
	}
}
