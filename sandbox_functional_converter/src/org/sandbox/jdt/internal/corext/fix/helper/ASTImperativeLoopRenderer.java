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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.functional.core.model.FunctionalExpression;
import org.sandbox.functional.core.model.LoopModel;
import org.sandbox.functional.core.operation.FilterOp;
import org.sandbox.functional.core.operation.MapOp;
import org.sandbox.functional.core.terminal.ForEachTerminal;

/** Shared ULR operation lowering used by both imperative AST renderers. */
@SuppressWarnings("unchecked")
final class ASTImperativeLoopRenderer {
	private final ASTRewrite rewrite;
	private final ImportRewrite imports;
	private final TextEditGroup group;
	private final AST ast;
	private ImportRewriteContext importContext;
	private JdtStreamContext context;
	private final Map<FunctionalExpression, String> functionNames = new IdentityHashMap<>();

	ASTImperativeLoopRenderer(ASTRewrite rewrite, ImportRewrite imports, TextEditGroup group) {
		this.rewrite = rewrite;
		this.imports = imports;
		this.group = group;
		this.ast = rewrite.getAST();
	}

	void render(LoopModel model, JdtStreamContext sourceContext, boolean iteratorTarget) {
		context = sourceContext;
		// The ULR owns operation order, kinds, function boundaries and scopes.
		List<Object> operations = new ArrayList<>(model.getOperations());
		operations.add(model.getTerminal());
		for (Object operation : operations) {
			context.expression(function(operation));
		}
		Set<String> names = LoopVariableNames.usedNames(context.statement());
		importContext = new ContextSensitiveImportRewriteContext((CompilationUnit) context.statement().getRoot(),
				context.statement().getStartPosition(), imports);
		context.statement().setProperty(ASTNodes.UNTOUCH_COMMENT, Boolean.TRUE);
		Block replacement = ast.newBlock();
		for (Object operation : operations) {
			FunctionalExpression function = function(operation);
			if (function.requiresInvocation()) {
				String name = LoopVariableNames.fresh("function", names); //$NON-NLS-1$
				functionNames.put(function, name);
				replacement.statements().add(declaration(type(context.functionType(function)), name, copy(context.expression(function))));
			}
		}
		Block body = ast.newBlock();
		String current = model.getElement().variableName();
		ITypeBinding currentType = context.elementType();
		for (Object operation : operations) {
			FunctionalExpression function = function(operation);
			String input = current;
			if (!function.requiresInvocation() && !context.inputType(function).isEqualTo(currentType)) {
				input = LoopVariableNames.fresh("value", names); //$NON-NLS-1$
				body.statements().add(declaration(type(context.inputType(function)), input, ast.newSimpleName(current)));
			}
			if (!function.requiresInvocation() && ((LambdaExpression) context.expression(function)).getBody() instanceof Block block) {
				renameParameter((LambdaExpression) context.expression(function), input);
				for (Object statement : block.statements()) {
					body.statements().add(rewrite.createCopyTarget((Statement) statement));
				}
				continue;
			}
			Expression expression = invocation(function, input);
			if (operation instanceof FilterOp) {
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
				if (function.containsPatternVariable()) {
					Block scope = ast.newBlock();
					scope.statements().add(guard);
					body.statements().add(scope);
				} else {
					body.statements().add(guard);
				}
			} else if (operation instanceof MapOp) {
				current = LoopVariableNames.fresh("mapped", names); //$NON-NLS-1$
				currentType = context.outputType(function);
				body.statements().add(declaration(type(currentType), current, expression));
			} else {
				body.statements().add(ast.newExpressionStatement(expression));
			}
		}
		String elementName = model.getElement().variableName();
		if (iteratorTarget) {
			String iteratorName = LoopVariableNames.fresh("it", names); //$NON-NLS-1$
			ParameterizedType iteratorType = ast.newParameterizedType(ast.newSimpleType(ast.newName(imports.addImport("java.util.Iterator", importContext)))); //$NON-NLS-1$
			iteratorType.typeArguments().add(type(context.elementType()));
			replacement.statements().add(declaration(iteratorType, iteratorName, call(copy(context.source()), "iterator"))); //$NON-NLS-1$
			body.statements().add(0, declaration(type(context.elementType()), elementName, call(ast.newSimpleName(iteratorName), "next"))); //$NON-NLS-1$
			WhileStatement loop = ast.newWhileStatement();
			loop.setExpression(call(ast.newSimpleName(iteratorName), "hasNext")); //$NON-NLS-1$
			loop.setBody(body);
			replacement.statements().add(loop);
		} else {
			EnhancedForStatement loop = ast.newEnhancedForStatement();
			SingleVariableDeclaration parameter = ast.newSingleVariableDeclaration();
			parameter.setType(type(context.elementType()));
			parameter.setName(ast.newSimpleName(elementName));
			loop.setParameter(parameter);
			loop.setExpression(copy(context.source()));
			loop.setBody(body);
			replacement.statements().add(loop);
		}
		if (context.statement().getParent() instanceof Block parent) {
			for (Object statement : List.copyOf(replacement.statements())) {
				((Statement) statement).delete();
				rewrite.getListRewrite(parent, Block.STATEMENTS_PROPERTY).insertBefore((Statement) statement, context.statement(), group);
			}
			rewrite.remove(context.statement(), group);
		} else if (replacement.statements().size() == 1) {
			Statement loop = (Statement) replacement.statements().remove(0);
			rewrite.replace(context.statement(), loop, group);
		} else {
			rewrite.replace(context.statement(), replacement, group);
		}
	}

	private static FunctionalExpression function(Object operation) {
		FunctionalExpression function = switch (operation) {
			case FilterOp filter -> filter.function();
			case MapOp map -> map.function();
			case ForEachTerminal terminal -> terminal.function();
			default -> throw new IllegalArgumentException("Unsupported imperative ULR operation: " + operation); //$NON-NLS-1$
		};
		return Objects.requireNonNull(function, "Imperative pipeline rendering requires functional metadata"); //$NON-NLS-1$
	}

	private Expression invocation(FunctionalExpression function, String input) {
		if (function.requiresInvocation()) {
			String method = context.functionType(function).getFunctionalInterfaceMethod().getName();
			MethodInvocation call = call(ast.newSimpleName(functionNames.get(function)), method);
			call.arguments().add(ast.newSimpleName(input));
			return call;
		}
		LambdaExpression lambda = (LambdaExpression) context.expression(function);
		renameParameter(lambda, input);
		return copy((Expression) lambda.getBody());
	}

	private void renameParameter(LambdaExpression lambda, String input) {
		IVariableBinding binding = JdtStreamExtractor.parameter(lambda).resolveBinding();
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
		return imports.addImport(binding, ast, importContext);
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
