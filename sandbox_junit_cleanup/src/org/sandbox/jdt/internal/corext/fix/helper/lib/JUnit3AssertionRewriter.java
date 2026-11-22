/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0, available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper.lib;

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnit3SemanticSupport.JUNIT3_ASSERT;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnit3SemanticSupport.JUNIT3_TEST_CASE;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_JUPITER_API_ASSERTIONS;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.dom.ScopeAnalyzer;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;

import org.sandbox.jdt.internal.common.AstProcessing;

/** Shares evaluation-preserving assertion edits between local and coordinated migration. */
public final class JUnit3AssertionRewriter {

	private final Set<String> names= new HashSet<>();

	public JUnit3AssertionRewriter(CompilationUnit root) {
		AstProcessing.independent().visit(SimpleName.class, name -> names.add(name.getIdentifier())).build(root);
	}

	/** Whether this invocation has a proven rewrite, including any required preceding evaluations. */
	public static boolean supports(MethodInvocation invocation) {
		IMethodBinding binding= invocation.resolveMethodBinding();
		if (binding == null || binding.isRecovered()) return false;
		IMethodBinding declaration= binding.getMethodDeclaration();
		ITypeBinding owner= declaration == null ? null : declaration.getDeclaringClass();
		if (declaration == null || declaration.isRecovered() || owner == null || owner.isRecovered()
				|| !(JUNIT3_ASSERT.equals(owner.getQualifiedName()) || JUNIT3_TEST_CASE.equals(owner.getQualifiedName()))) {
			return false;
		}
		boolean precedingEvaluation= capturesMessage(invocation) || !hasDisposableReceiver(invocation);
		JUnit3AssertionNls nls= nls(invocation);
		if (nls == JUnit3AssertionNls.UNSAFE
				|| (precedingEvaluation || nls != JUnit3AssertionNls.NONE) && !(invocation.getParent() instanceof ExpressionStatement)) return false;
		if (!hasDisposableReceiver(invocation) && !(invocation.getExpression() instanceof MethodInvocation)) return false;
		if (isBooleanEquality(invocation)) {
			List<?> arguments= invocation.arguments();
			Expression expected= (Expression) arguments.get(arguments.size() - 2);
			Expression actual= (Expression) arguments.getLast();
			return expected instanceof BooleanLiteral || isPrimitiveBoolean(expected) && isPrimitiveBoolean(actual);
		}
		return true;
	}

	/** Simple existing hint rewrites remain sufficient when no evaluation or boolean overload needs adaptation. */
	public static boolean needsEvaluationRewrite(MethodInvocation invocation) {
		return !hasDisposableReceiver(invocation) || capturesMessage(invocation) || isBooleanEquality(invocation)
				|| nls(invocation) != JUnit3AssertionNls.NONE;
	}

	private static JUnit3AssertionNls nls(MethodInvocation invocation) {
		boolean receiver= !hasDisposableReceiver(invocation);
		if (!receiver && !JUnit3SemanticSupport.hasLeadingMessage(invocation, invocation.resolveMethodBinding())) return JUnit3AssertionNls.NONE;
		return JUnit3AssertionNls.inspect(invocation, receiver || capturesMessage(invocation));
	}

	private static boolean isPrimitiveBoolean(Expression expression) {
		ITypeBinding type= expression.resolveTypeBinding();
		return type != null && !type.isRecovered() && type.isPrimitive() && "boolean".equals(type.getName()); //$NON-NLS-1$
	}

	private static boolean isBooleanEquality(MethodInvocation invocation) {
		IMethodBinding binding= invocation.resolveMethodBinding();
		if (binding == null || !"assertEquals".equals(binding.getName())) return false; //$NON-NLS-1$
		ITypeBinding[] parameters= binding.getParameterTypes();
		return (parameters.length == 2 || parameters.length == 3)
				&& "boolean".equals(parameters[parameters.length - 2].getQualifiedName()) //$NON-NLS-1$
				&& "boolean".equals(parameters[parameters.length - 1].getQualifiedName()); //$NON-NLS-1$
	}

	private static boolean hasDisposableReceiver(MethodInvocation invocation) {
		Expression receiver= invocation.getExpression();
		if (receiver == null) return true;
		if (receiver instanceof ThisExpression) {
			ITypeBinding type= receiver.resolveTypeBinding();
			return type != null && !type.isRecovered();
		}
		return receiver instanceof Name name && name.resolveBinding() instanceof ITypeBinding type && !type.isRecovered();
	}

	private static boolean capturesMessage(MethodInvocation invocation) {
		return JUnit3SemanticSupport.hasLeadingMessage(invocation, invocation.resolveMethodBinding())
				&& !isConstantMessage((Expression) invocation.arguments().getFirst());
	}

	private static boolean isConstantMessage(Expression message) {
		if (!(message.resolveConstantExpressionValue() instanceof String)) return false;
		boolean[] passive= { true };
		message.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation node) { passive[0]= false; return false; }

			@Override
			public boolean visit(FieldAccess node) {
				passive[0]&= node.getExpression() instanceof ThisExpression;
				return false;
			}

			@Override
			public boolean visit(QualifiedName node) {
				if (node.resolveBinding() instanceof IVariableBinding) {
					passive[0]&= node.getQualifier().resolveBinding() instanceof ITypeBinding;
				}
				return false;
			}
		});
		return passive[0];
	}

	/** Applies one resolved invocation while retaining its original control-flow position. */
	public void rewrite(MethodInvocation invocation, ASTRewrite rewrite, ImportRewrite imports, TextEditGroup group) {
		if (!supports(invocation)) throw new IllegalArgumentException("Unproven JUnit 3 assertion rewrite"); //$NON-NLS-1$
		AST ast= invocation.getAST();
		List<Statement> preceding= new ArrayList<>();
		JUnit3AssertionNls nls= nls(invocation);
		if (!hasDisposableReceiver(invocation)) {
			Expression receiver= invocation.getExpression();
			Statement tagged= nls.taggedStatement(receiver, "", ASTNode.EXPRESSION_STATEMENT, rewrite); //$NON-NLS-1$
			preceding.add(tagged != null ? tagged : ast.newExpressionStatement((Expression) rewrite.createMoveTarget(receiver)));
		}
		List<?> arguments= invocation.arguments();
		ListRewrite changed= rewrite.getListRewrite(invocation, MethodInvocation.ARGUMENTS_PROPERTY);
		if (JUnit3SemanticSupport.hasLeadingMessage(invocation, invocation.resolveMethodBinding())) {
			Expression message= (Expression) arguments.getFirst();
			Expression finalMessage;
			if (capturesMessage(invocation) || nls != JUnit3AssertionNls.NONE) {
				String name= uniqueMessageName(invocation);
				VariableDeclarationFragment fragment= ast.newVariableDeclarationFragment();
				fragment.setName(ast.newSimpleName(name));
				String type= imports.addImport("java.lang.String", new ContextSensitiveImportRewriteContext(invocation, imports)); //$NON-NLS-1$
				Statement tagged= nls.taggedStatement(message, type + " " + name + " = ", ASTNode.VARIABLE_DECLARATION_STATEMENT, rewrite); //$NON-NLS-1$ //$NON-NLS-2$
				if (tagged == null) {
					fragment.setInitializer((Expression) rewrite.createMoveTarget(message));
					VariableDeclarationStatement capture= ast.newVariableDeclarationStatement(fragment);
					capture.setType(ast.newSimpleType(ast.newName(type)));
					preceding.add(capture);
				} else {
					preceding.add(tagged);
				}
				finalMessage= ast.newSimpleName(name);
			} else {
				finalMessage= (Expression) rewrite.createMoveTarget(message);
			}
			changed.remove(message, group);
			changed.insertLast(finalMessage, group);
		}
		if (isBooleanEquality(invocation)) {
			Expression expected= (Expression) arguments.get(arguments.size() - 2);
			Expression actual= (Expression) arguments.getLast();
			String assertion= "assertTrue"; //$NON-NLS-1$
			if (expected instanceof BooleanLiteral literal) {
				if (!literal.booleanValue()) assertion= "assertFalse"; //$NON-NLS-1$
			} else {
				InfixExpression equality= ast.newInfixExpression();
				equality.setOperator(InfixExpression.Operator.EQUALS);
				ParenthesizedExpression left= ast.newParenthesizedExpression();
				left.setExpression((Expression) rewrite.createMoveTarget(expected));
				ParenthesizedExpression right= ast.newParenthesizedExpression();
				right.setExpression((Expression) rewrite.createMoveTarget(actual));
				equality.setLeftOperand(left);
				equality.setRightOperand(right);
				changed.replace(actual, equality, group);
			}
			changed.remove(expected, group);
			rewrite.set(invocation, MethodInvocation.NAME_PROPERTY, ast.newSimpleName(assertion), group);
		}
		String assertionType= imports.addImport(ORG_JUNIT_JUPITER_API_ASSERTIONS,
				new ContextSensitiveImportRewriteContext(invocation, imports));
		rewrite.set(invocation, MethodInvocation.EXPRESSION_PROPERTY, ast.newName(assertionType), group);
		insertPreceding(invocation, preceding, rewrite, group);
	}

	/** Removes an obsolete qualifier import while retaining imports needed by moved receiver evaluations. */
	public static void registerQualifierChange(MethodInvocation invocation, CompilationUnitRewrite cuRewrite) {
		Expression receiver= invocation.getExpression();
		if (cuRewrite.getASTRewrite().get(invocation, MethodInvocation.EXPRESSION_PROPERTY) != receiver) {
			if (!(receiver instanceof MethodInvocation)) {
				cuRewrite.getImportRemover().registerRemovedNode(receiver == null ? invocation.getName() : receiver);
			}
			JUnit3AssertionNls.recordMoved(receiver, cuRewrite);
			if (!invocation.arguments().isEmpty()) JUnit3AssertionNls.recordMoved((Expression) invocation.arguments().getFirst(), cuRewrite);
			cuRewrite.getImportRemover().registerAddedImport(ORG_JUNIT_JUPITER_API_ASSERTIONS);
		}
	}

	private String uniqueMessageName(MethodInvocation invocation) {
		var scope= new ScopeAnalyzer((CompilationUnit) invocation.getRoot());
		for (var binding : scope.getDeclarationsInScope(invocation.getStartPosition(), ScopeAnalyzer.VARIABLES)) {
			names.add(binding.getName());
		}
		String candidate= "junitMessage"; //$NON-NLS-1$
		for (int suffix= 2; !names.add(candidate); suffix++) candidate= "junitMessage" + suffix; //$NON-NLS-1$
		return candidate;
	}

	@SuppressWarnings("unchecked")
	private static void insertPreceding(MethodInvocation invocation, List<Statement> preceding,
			ASTRewrite rewrite, TextEditGroup group) {
		if (preceding.isEmpty()) return;
		ExpressionStatement statement= (ExpressionStatement) invocation.getParent();
		if (statement.getParent() instanceof Block parent) {
			ListRewrite statements= rewrite.getListRewrite(parent, Block.STATEMENTS_PROPERTY);
			for (Statement before : preceding) statements.insertBefore(before, statement, group);
		} else {
			Block block= invocation.getAST().newBlock();
			block.statements().addAll(preceding);
			// Register edits within the assertion before moving it into the conditional/loop body.
			block.statements().add(rewrite.createMoveTarget(statement));
			rewrite.replace(statement, block, group);
		}
	}
}
