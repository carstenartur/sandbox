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

import static org.sandbox.jdt.internal.corext.fix.helper.LoopVariableNames.fresh;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.CreationReference;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PatternInstanceofExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypeMethodReference;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.sandbox.functional.core.builder.LoopModelBuilder;
import org.sandbox.functional.core.model.FunctionalExpression;
import org.sandbox.functional.core.model.LoopModel;
import org.sandbox.functional.core.model.SourceDescriptor;
import org.sandbox.functional.core.operation.FilterOp;
import org.sandbox.functional.core.operation.MapOp;
import org.sandbox.functional.core.terminal.ForEachTerminal;

/**
 * Extracts sequential stream/forEach expressions into the shared ULR.
 * Original nodes and resolved bindings remain in the accompanying JDT context.
 */
public final class JdtStreamExtractor {
	private static final String STREAM = "java.util.stream.Stream"; //$NON-NLS-1$
	private static final String ITERABLE = "java.lang.Iterable"; //$NON-NLS-1$

	public record ExtractedStream(LoopModel model, JdtStreamContext context) {
	}

	private JdtStreamExtractor() {
	}

	public static ExtractedStream extract(MethodInvocation terminal) {
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
		LoopModelBuilder builder = new LoopModelBuilder()
				.source(elementType(source.resolveTypeBinding(), "java.util.Collection") != null //$NON-NLS-1$
						? SourceDescriptor.SourceType.COLLECTION : SourceDescriptor.SourceType.ITERABLE,
						sourceText(source), elementType.getQualifiedName())
				.metadata(false, false, false, false, true);
		Map<FunctionalExpression, Expression> functions = new IdentityHashMap<>();
		FunctionalExpression firstFunction = null;
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
			FunctionalExpression descriptor = new FunctionalExpression(sourceText(function),
					function instanceof LambdaExpression lambda ? parameter(lambda).getName().getIdentifier() : null,
					method.getParameterTypes()[0].getQualifiedName(), method.getReturnType().getQualifiedName(),
					type.getQualifiedName(), !inlineable(function, operation), hasPatternVariable(function));
			functions.put(descriptor, function);
			if (firstFunction == null) {
				firstFunction = descriptor;
			}
			String expression = function instanceof LambdaExpression lambda ? lambda.getBody().toString() : function.toString();
			switch (operation) {
			case "filter": //$NON-NLS-1$
				builder.operation(new FilterOp(expression, descriptor));
				break;
			case "map": //$NON-NLS-1$
				builder.operation(new MapOp(expression, descriptor.outputType(), null, false, descriptor));
				break;
			default:
				builder.terminal(new ForEachTerminal(List.of(expression), "forEachOrdered".equals(operation), descriptor)); //$NON-NLS-1$
			}
		}
		// Keep comments between calls at their original location.
		for (Object entry : unit.getCommentList()) {
			Comment comment = (Comment) entry;
			if (contains(statement, comment) && !contains(source, comment)
					&& functions.values().stream().noneMatch(function -> contains(function, comment))) {
				return null;
			}
		}
		Set<String> names = LoopVariableNames.usedNames(unit);
		String elementName = calls.size() == 1 && firstFunction != null && !firstFunction.requiresInvocation()
				? firstFunction.parameterName() : fresh("element", names); //$NON-NLS-1$
		LoopModel model = builder.element(elementName, elementType.getQualifiedName(), false).build();
		return new ExtractedStream(model, new JdtStreamContext(statement, source, elementType, functions));
	}

	private static boolean contains(ASTNode outer, ASTNode inner) {
		return inner.getStartPosition() >= outer.getStartPosition()
				&& inner.getStartPosition() + inner.getLength() <= outer.getStartPosition() + outer.getLength();
	}

	private static String sourceText(Expression expression) {
		CompilationUnit unit = (CompilationUnit) expression.getRoot();
		if (unit.getTypeRoot() != null) {
			try {
				var buffer = unit.getTypeRoot().getBuffer();
				if (buffer != null) {
					return buffer.getText(expression.getStartPosition(), expression.getLength());
				}
			} catch (JavaModelException unavailable) {
				// The JDT renderer still uses the original AST; text is a portable fallback.
			}
		}
		return expression.toString();
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

	static VariableDeclaration parameter(LambdaExpression lambda) {
		return (VariableDeclaration) lambda.parameters().get(0);
	}

	private static boolean inlineable(Expression function, String operation) {
		if (!(function instanceof LambdaExpression lambda) || lambda.parameters().size() != 1
				|| parameter(lambda).resolveBinding() == null) {
			return false;
		}
		CompilationUnit unit = (CompilationUnit) lambda.getRoot();
		for (Object entry : unit.getCommentList()) {
			Comment comment = (Comment) entry;
			if (contains(lambda, comment) && (!contains(lambda.getBody(), comment)
					|| lambda.getBody() instanceof Block block && block.statements().isEmpty())) {
				return false;
			}
		}
		// Intermediate blocks and terminal blocks with local returns retain their
		// functional boundary. Ordinary terminal blocks can be copied verbatim.
		if (lambda.getBody() instanceof Block && ("filter".equals(operation) //$NON-NLS-1$
				|| "map".equals(operation) || hasReturn(lambda.getBody()))) { //$NON-NLS-1$
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

	private static boolean hasPatternVariable(Expression function) {
		boolean[] found = { false };
		function.accept(new ASTVisitor() {
			@Override
			public boolean visit(PatternInstanceofExpression node) {
				found[0] = true;
				return false;
			}
		});
		return found[0];
	}

}
