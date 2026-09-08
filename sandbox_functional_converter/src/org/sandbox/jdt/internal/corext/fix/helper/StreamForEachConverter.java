/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.CreationReference;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeMethodReference;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore;

/**
 * Binding-aware reverse conversion shared by the two imperative targets.
 *
 * <p>The plan retains functional-interface types and AST bodies: reducing these to
 * the string-based loop model would lose map result types, lambda scopes and
 * overload selection. It is applied through the normal JDT cleanup rewrite.</p>
 */
public final class StreamForEachConverter {

	private static final String STREAM = "java.util.stream.Stream"; //$NON-NLS-1$
	private static final String ITERABLE = "java.lang.Iterable"; //$NON-NLS-1$

	private record Stage(String operation, Expression function, ITypeBinding functionType,
			ITypeBinding inputType, ITypeBinding outputType, String functionName) {
	}

	private record Plan(ExpressionStatement statement, Expression source, ITypeBinding elementType,
			List<Stage> stages, String elementName, String iteratorName, Set<String> names) {
	}

	private StreamForEachConverter() {
	}

	public static void find(UseFunctionalCallFixCore fix, CompilationUnit unit,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> processed) {
		Set<String> names = new HashSet<>();
		unit.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName name) {
				names.add(name.getIdentifier());
				return true;
			}
		});
		unit.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation node) {
				if (processed.contains(node)) {
					return false;
				}
				Plan plan = analyze(node, names);
				if (plan == null) {
					return true;
				}
				ReferenceHolder<ASTNode, Object> data = new ReferenceHolder<>();
				data.put(node, plan);
				operations.add(fix.rewrite(node, data));
				processed.add(node);
				// One owner per replaced statement, including nested calls in its lambdas.
				return false;
			}
		});
	}

	private static Plan analyze(MethodInvocation terminal, Set<String> names) {
		String terminalName = terminal.getName().getIdentifier();
		if (!(terminal.getParent() instanceof ExpressionStatement statement)
				|| !("forEach".equals(terminalName) || "forEachOrdered".equals(terminalName))) { //$NON-NLS-1$ //$NON-NLS-2$
			return null;
		}
		CompilationUnit unit = (CompilationUnit) statement.getRoot();
		for (var problem : unit.getProblems()) {
			if (problem.isError() && problem.getSourceStart() <= statement.getStartPosition() + statement.getLength()
					&& problem.getSourceEnd() >= statement.getStartPosition()) {
				return null;
			}
		}
		List<MethodInvocation> calls = new ArrayList<>();
		calls.add(terminal);
		Expression source = terminal.getExpression();
		ITypeBinding elementType;
		if (declaredBy(terminal, STREAM)) {
			while (source instanceof MethodInvocation call && declaredBy(call, STREAM)
					&& Set.of("filter", "map").contains(call.getName().getIdentifier())) { //$NON-NLS-1$ //$NON-NLS-2$
				calls.add(call);
				source = call.getExpression();
			}
			if (!(source instanceof MethodInvocation stream) || !declaredBy(stream, "java.util.Collection") //$NON-NLS-1$
					|| !"stream".equals(stream.getName().getIdentifier()) || !stream.arguments().isEmpty()) { //$NON-NLS-1$
				return null;
			}
			elementType = elementType(stream.resolveTypeBinding(), STREAM);
			source = stream.getExpression();
		} else if ("forEach".equals(terminalName) && standardForEach(terminal)) { //$NON-NLS-1$
			elementType = source == null ? null : elementType(source.resolveTypeBinding(), ITERABLE);
		} else {
			return null;
		}
		if (source == null || !denotable(elementType)) {
			return null;
		}
		Collections.reverse(calls);
		List<Stage> stages = new ArrayList<>();
		for (MethodInvocation call : calls) {
			if (call.arguments().size() != 1 || !(call.arguments().get(0) instanceof Expression function)
					|| !safeToMove(function)) {
				return null;
			}
			ITypeBinding type = function.resolveTypeBinding();
			IMethodBinding method = type == null ? null : type.getFunctionalInterfaceMethod();
			if (!denotable(type) || method == null || method.isRecovered() || method.getParameterTypes().length != 1
					|| !denotable(method.getParameterTypes()[0]) || !denotable(method.getReturnType())) {
				return null;
			}
			String operation = call.getName().getIdentifier();
			stages.add(new Stage(operation, function, type, method.getParameterTypes()[0], method.getReturnType(), null));
		}
		// Comments between pipeline calls have no unambiguous destination. Keep
		// the chain instead of silently deleting these comments with its syntax.
		for (Object entry : ((CompilationUnit) statement.getRoot()).getCommentList()) {
			Comment comment = (Comment) entry;
			if (contains(statement, comment) && !contains(source, comment)
					&& stages.stream().noneMatch(stage -> contains(stage.function(), comment))) {
				return null;
			}
		}
		// Reserve names only after the complete chain has passed validation.
		List<Stage> prepared = new ArrayList<>();
		for (Stage stage : stages) {
			String functionName = inlineable(stage) ? null : fresh("function", names); //$NON-NLS-1$
			prepared.add(new Stage(stage.operation(), stage.function(), stage.functionType(),
					stage.inputType(), stage.outputType(), functionName));
		}
		String elementName = stages.size() == 1 && prepared.get(0).functionName() == null
				? parameter((LambdaExpression) stages.get(0).function()).getName().getIdentifier()
				: fresh("element", names); //$NON-NLS-1$
		return new Plan(statement, source, elementType, List.copyOf(prepared), elementName,
				fresh("it", names), names); //$NON-NLS-1$
	}

	private static boolean contains(ASTNode outer, ASTNode inner) {
		return inner.getStartPosition() >= outer.getStartPosition()
				&& inner.getStartPosition() + inner.getLength() <= outer.getStartPosition() + outer.getLength();
	}

	private static boolean declaredBy(MethodInvocation call, String owner) {
		IMethodBinding method = call.resolveMethodBinding();
		return method != null && !method.isRecovered() && method.getDeclaringClass() != null
				&& owner.equals(method.getDeclaringClass().getErasure().getQualifiedName());
	}

	private static boolean standardForEach(MethodInvocation call) {
		IMethodBinding method = call.resolveMethodBinding();
		return method != null && !method.isRecovered() && method.getDeclaringClass() != null
				&& (declaredBy(call, ITERABLE) || method.getDeclaringClass().getQualifiedName().startsWith("java.util.")) //$NON-NLS-1$
				&& method.getParameterTypes().length == 1
				&& "java.util.function.Consumer".equals(method.getParameterTypes()[0].getErasure().getQualifiedName()); //$NON-NLS-1$
	}

	private static ITypeBinding elementType(ITypeBinding type, String owner) {
		if (type == null || type.isRecovered()) {
			return null;
		}
		if (owner.equals(type.getErasure().getQualifiedName())) {
			return type.getTypeArguments().length == 1 ? type.getTypeArguments()[0] : null;
		}
		for (ITypeBinding iface : type.getInterfaces()) {
			ITypeBinding element = elementType(iface, owner);
			if (element != null) {
				return element;
			}
		}
		return elementType(type.getSuperclass(), owner);
	}

	private static boolean denotable(ITypeBinding type) {
		if (type == null || type.isRecovered() || type.isCapture() || type.isWildcardType()
				|| type.isAnonymous() || type.isIntersectionType() || type.isNullType()) {
			return false;
		}
		if (type.isArray()) {
			return denotable(type.getElementType());
		}
		for (ITypeBinding argument : type.getTypeArguments()) {
			if (!(argument.isWildcardType() ? argument.getBound() == null || denotable(argument.getBound())
					: denotable(argument))) {
				return false;
			}
		}
		return true;
	}

	private static boolean safeToMove(Expression function) {
		if (function instanceof LambdaExpression || function instanceof TypeMethodReference
				|| function instanceof CreationReference) {
			return true;
		}
		if (function instanceof ExpressionMethodReference reference) {
			Expression receiver = reference.getExpression();
			// Arbitrary bound receivers have eager evaluation/null-check semantics.
			return receiver instanceof ThisExpression
					|| receiver instanceof Name name && name.resolveBinding() instanceof ITypeBinding;
		}
		return false;
	}

	private static VariableDeclaration parameter(LambdaExpression lambda) {
		return (VariableDeclaration) lambda.parameters().get(0);
	}

	private static boolean inlineable(Stage stage) {
		if (!(stage.function() instanceof LambdaExpression lambda) || lambda.parameters().size() != 1
				|| parameter(lambda).resolveBinding() == null) {
			return false;
		}
		CompilationUnit unit = (CompilationUnit) lambda.getRoot();
		for (Object entry : unit.getCommentList()) {
			Comment comment = (Comment) entry;
			if (contains(lambda, comment) && !contains(lambda.getBody(), comment)) {
				return false;
			}
		}
		// Intermediate blocks and terminal blocks with local returns retain their
		// functional boundary. Ordinary terminal blocks can be copied verbatim.
		if (lambda.getBody() instanceof Block && ("filter".equals(stage.operation()) //$NON-NLS-1$
				|| "map".equals(stage.operation()) || hasReturn(lambda.getBody()))) { //$NON-NLS-1$
			return false;
		}
		IVariableBinding binding = parameter(lambda).resolveBinding();
		boolean[] written = { false };
		lambda.getBody().accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName name) {
				if (binding.isEqualTo(name.resolveBinding())) {
					ASTNode operand = name;
					while (operand.getParent() instanceof ParenthesizedExpression) {
						operand = operand.getParent();
					}
					ASTNode parent = operand.getParent();
					written[0] |= parent instanceof Assignment assignment && assignment.getLeftHandSide() == operand
							|| parent instanceof PostfixExpression
							|| parent instanceof PrefixExpression prefix
									&& (prefix.getOperator() == PrefixExpression.Operator.INCREMENT
											|| prefix.getOperator() == PrefixExpression.Operator.DECREMENT);
				}
				return true;
			}
		});
		return !written[0];
	}

	private static boolean hasReturn(ASTNode body) {
		boolean[] found = { false };
		body.accept(new ASTVisitor() {
			@Override
			public boolean preVisit2(ASTNode node) {
				return !(node instanceof LambdaExpression || node instanceof AbstractTypeDeclaration
						|| node instanceof AnonymousClassDeclaration);
			}
			@Override
			public boolean visit(ReturnStatement node) {
				found[0] = true;
				return false;
			}
		});
		return found[0];
	}

	private static String fresh(String base, Set<String> names) {
		String name = base;
		for (int suffix = 1; !names.add(name); suffix++) {
			name = base + suffix;
		}
		return name;
	}

	public static void rewrite(ASTNode node, CompilationUnitRewrite cuRewrite, TextEditGroup group,
			ReferenceHolder<ASTNode, Object> data, boolean iteratorTarget) {
		if (data.get(node) instanceof Plan plan) {
			new Renderer(cuRewrite.getASTRewrite(), cuRewrite.getImportRewrite(), group).render(plan, iteratorTarget);
		}
	}

	@SuppressWarnings("unchecked")
	private static final class Renderer {
		private final ASTRewrite rewrite;
		private final ImportRewrite imports;
		private final TextEditGroup group;
		private final AST ast;

		Renderer(ASTRewrite rewrite, ImportRewrite imports, TextEditGroup group) {
			this.rewrite = rewrite;
			this.imports = imports;
			this.group = group;
			this.ast = rewrite.getAST();
		}

		void render(Plan plan, boolean iteratorTarget) {
			Block replacement = ast.newBlock();
			for (Stage stage : plan.stages()) {
				if (stage.functionName() != null) {
					replacement.statements().add(declaration(type(stage.functionType()), stage.functionName(), copy(stage.function())));
				}
			}
			Block body = ast.newBlock();
			String current = plan.elementName();
			ITypeBinding currentType = plan.elementType();
			for (Stage stage : plan.stages()) {
				String input = current;
				if (stage.functionName() == null && !stage.inputType().isEqualTo(currentType)) {
					input = fresh("value", plan.names()); //$NON-NLS-1$
					body.statements().add(declaration(type(stage.inputType()), input, ast.newSimpleName(current)));
				}
				if (stage.functionName() == null && ((LambdaExpression) stage.function()).getBody() instanceof Block block) {
					renameParameter((LambdaExpression) stage.function(), input);
					for (Object statement : block.statements()) {
						body.statements().add(rewrite.createCopyTarget((Statement) statement));
					}
					continue;
				}
				Expression expression = invocation(stage, input);
				if ("filter".equals(stage.operation())) { //$NON-NLS-1$
					PrefixExpression negated = ast.newPrefixExpression();
					negated.setOperator(PrefixExpression.Operator.NOT);
					ParenthesizedExpression parentheses = ast.newParenthesizedExpression();
					parentheses.setExpression(expression);
					negated.setOperand(parentheses);
					IfStatement guard = ast.newIfStatement();
					guard.setExpression(negated);
					Block rejected = ast.newBlock();
					rejected.statements().add(ast.newContinueStatement());
					guard.setThenStatement(rejected);
					body.statements().add(guard);
				} else if ("map".equals(stage.operation())) { //$NON-NLS-1$
					current = fresh("mapped", plan.names()); //$NON-NLS-1$
					currentType = stage.outputType();
					body.statements().add(declaration(type(currentType), current, expression));
				} else {
					body.statements().add(ast.newExpressionStatement(expression));
				}
			}
			if (iteratorTarget) {
				ParameterizedType iteratorType = ast.newParameterizedType(ast.newSimpleType(ast.newName(imports.addImport("java.util.Iterator")))); //$NON-NLS-1$
				iteratorType.typeArguments().add(type(plan.elementType()));
				replacement.statements().add(declaration(iteratorType, plan.iteratorName(), call(copy(plan.source()), "iterator"))); //$NON-NLS-1$
				body.statements().add(0, declaration(type(plan.elementType()), plan.elementName(), call(ast.newSimpleName(plan.iteratorName()), "next"))); //$NON-NLS-1$
				WhileStatement loop = ast.newWhileStatement();
				loop.setExpression(call(ast.newSimpleName(plan.iteratorName()), "hasNext")); //$NON-NLS-1$
				loop.setBody(body);
				replacement.statements().add(loop);
			} else {
				EnhancedForStatement loop = ast.newEnhancedForStatement();
				SingleVariableDeclaration parameter = ast.newSingleVariableDeclaration();
				parameter.setType(type(plan.elementType()));
				parameter.setName(ast.newSimpleName(plan.elementName()));
				loop.setParameter(parameter);
				loop.setExpression(copy(plan.source()));
				loop.setBody(body);
				replacement.statements().add(loop);
			}
			if (plan.statement().getParent() instanceof Block parent) {
				for (Object statement : List.copyOf(replacement.statements())) {
					((Statement) statement).delete();
					rewrite.getListRewrite(parent, Block.STATEMENTS_PROPERTY).insertBefore((Statement) statement, plan.statement(), group);
				}
				rewrite.remove(plan.statement(), group);
			} else if (replacement.statements().size() == 1) {
				Statement loop = (Statement) replacement.statements().remove(0);
				rewrite.replace(plan.statement(), loop, group);
			} else {
				rewrite.replace(plan.statement(), replacement, group);
			}
		}

		private Expression invocation(Stage stage, String input) {
			if (stage.functionName() != null) {
				String method = stage.functionType().getFunctionalInterfaceMethod().getName();
				MethodInvocation call = call(ast.newSimpleName(stage.functionName()), method);
				call.arguments().add(ast.newSimpleName(input));
				return call;
			}
			LambdaExpression lambda = (LambdaExpression) stage.function();
			renameParameter(lambda, input);
			return copy((Expression) lambda.getBody());
		}

		private void renameParameter(LambdaExpression lambda, String input) {
			IVariableBinding binding = parameter(lambda).resolveBinding();
			lambda.getBody().accept(new ASTVisitor() {
				@Override
				public boolean visit(SimpleName name) {
					IBinding resolved = name.resolveBinding();
					if (binding.isEqualTo(resolved) && !name.getIdentifier().equals(input)) {
						rewrite.replace(name, ast.newSimpleName(input), group);
					}
					return true;
				}
			});
		}

		private Type type(ITypeBinding binding) {
			return imports.addImport(binding, ast);
		}

		private Expression copy(Expression expression) {
			return (Expression) rewrite.createCopyTarget(expression);
		}

		private MethodInvocation call(Expression receiver, String name) {
			MethodInvocation call = ast.newMethodInvocation();
			call.setExpression(receiver);
			call.setName(ast.newSimpleName(name));
			return call;
		}

		private VariableDeclarationStatement declaration(Type type, String name, Expression initializer) {
			VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
			fragment.setName(ast.newSimpleName(name));
			fragment.setInitializer(initializer);
			VariableDeclarationStatement declaration = ast.newVariableDeclarationStatement(fragment);
			declaration.setType(type);
			return declaration;
		}
	}
}
