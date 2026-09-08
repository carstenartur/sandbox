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
import org.sandbox.functional.core.operation.PeekOp;
import org.sandbox.functional.core.operation.StreamTypeConversionOp;
import org.sandbox.functional.core.terminal.ForEachTerminal;

/**
 * Extracts sequential stream/forEach expressions into the shared ULR.
 * Original nodes and resolved bindings remain in the accompanying JDT context.
 */
public final class JdtStreamExtractor {
	private static final String STREAM = "java.util.stream.Stream"; //$NON-NLS-1$
	private static final String ITERABLE = "java.lang.Iterable"; //$NON-NLS-1$
	private static final Set<String> STREAM_TYPES = Set.of(STREAM, "java.util.stream.IntStream", //$NON-NLS-1$
			"java.util.stream.LongStream", "java.util.stream.DoubleStream"); //$NON-NLS-1$ //$NON-NLS-2$
	private static final Set<String> FUNCTION_OPERATIONS = Set.of("filter", "peek", "map", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			"mapToInt", "mapToLong", "mapToDouble", "mapToObj"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	private static final Set<String> TYPE_OPERATIONS = Set.of("boxed", "asLongStream", "asDoubleStream"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

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
		SourceDescriptor.SourceType sourceKind;
		String streamExpression = null;
		Expression streamSource = null;
		if (standardStreamMethod(terminal)) {
			while (source instanceof MethodInvocation call && standardStreamMethod(call)
					&& (FUNCTION_OPERATIONS.contains(call.getName().getIdentifier()) || TYPE_OPERATIONS.contains(call.getName().getIdentifier()))) {
				calls.add(call);
				source = call.getExpression();
			}
			if (!(source instanceof MethodInvocation stream)) {
				return null;
			}
			elementType = streamElementType(stream);
			streamSource = stream;
			if (declaredBy(stream, "java.util.Collection") && "stream".equals(stream.getName().getIdentifier()) //$NON-NLS-1$ //$NON-NLS-2$
					&& stream.arguments().isEmpty()) {
				sourceKind = SourceDescriptor.SourceType.COLLECTION;
				source = stream.getExpression();
			} else if (declaredBy(stream, "java.util.Arrays") && "stream".equals(stream.getName().getIdentifier())) { //$NON-NLS-1$ //$NON-NLS-2$
				if (stream.arguments().size() == 1) {
					streamExpression = sourceText(stream);
					sourceKind = SourceDescriptor.SourceType.ARRAY;
					source = (Expression) stream.arguments().get(0);
				} else {
					// Retain JDK slice validation and evaluation of array/from/to exactly once.
					sourceKind = SourceDescriptor.SourceType.STREAM;
				}
			} else if (standardStreamMethod(stream) && org.eclipse.jdt.core.dom.Modifier.isStatic(stream.resolveMethodBinding().getModifiers())
					&& Set.of("of", "empty", "ofNullable", "range", "rangeClosed", "iterate", "generate") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
							.contains(stream.getName().getIdentifier())) {
				sourceKind = SourceDescriptor.SourceType.STREAM;
			} else {
				return null;
			}
		} else if ("forEach".equals(terminalName) && standardForEach(terminal)) { //$NON-NLS-1$
			elementType = source == null ? null : elementType(source.resolveTypeBinding(), ITERABLE);
			sourceKind = elementType(source == null ? null : source.resolveTypeBinding(), "java.util.Collection") != null //$NON-NLS-1$
					? SourceDescriptor.SourceType.COLLECTION : SourceDescriptor.SourceType.ITERABLE;
		} else {
			return null;
		}
		if (source == null || !denotable(elementType)) {
			return null;
		}
		Collections.reverse(calls);
		LoopModelBuilder builder = new LoopModelBuilder()
				.source(new SourceDescriptor(sourceKind, sourceText(source), elementType.getQualifiedName(), streamExpression))
				.metadata(false, false, false, false, true);
		Map<FunctionalExpression, Expression> functions = new IdentityHashMap<>();
		FunctionalExpression firstFunction = null;
		for (MethodInvocation call : calls) {
			String operation = call.getName().getIdentifier();
			if (TYPE_OPERATIONS.contains(operation)) {
				ITypeBinding input = streamElementType(call.getExpression());
				if (!call.arguments().isEmpty() || input == null) return null;
				builder.operation(new StreamTypeConversionOp(StreamTypeConversionOp.Kind.fromMethod(operation), input.getQualifiedName()));
				continue;
			}
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
			case "map", "mapToInt", "mapToLong", "mapToDouble", "mapToObj": //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
				builder.operation(new MapOp(expression, descriptor.outputType(), null, false, descriptor, MapOp.Kind.fromMethod(operation)));
				break;
			case "peek": //$NON-NLS-1$
				builder.operation(new PeekOp(expression, descriptor));
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
		LoopModel model = builder.element(elementName, elementType.getQualifiedName(), elementType.isPrimitive()).build();
		return new ExtractedStream(model, new JdtStreamContext(statement, source, elementType, functions, streamSource));
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

	private static boolean standardStreamMethod(MethodInvocation call) {
		IMethodBinding method = call.resolveMethodBinding();
		return method != null && !method.isRecovered() && method.getDeclaringClass() != null
				&& STREAM_TYPES.contains(method.getDeclaringClass().getErasure().getQualifiedName());
	}

	private static ITypeBinding streamElementType(Expression source) {
		ITypeBinding type = source.resolveTypeBinding();
		if (type == null || type.isRecovered()) return null;
		return switch (type.getErasure().getQualifiedName()) {
		case "java.util.stream.IntStream" -> source.getAST().resolveWellKnownType("int"); //$NON-NLS-1$ //$NON-NLS-2$
		case "java.util.stream.LongStream" -> source.getAST().resolveWellKnownType("long"); //$NON-NLS-1$ //$NON-NLS-2$
		case "java.util.stream.DoubleStream" -> source.getAST().resolveWellKnownType("double"); //$NON-NLS-1$ //$NON-NLS-2$
		default -> elementType(type, STREAM);
		};
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

	static boolean denotable(ITypeBinding type) {
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
				|| operation.startsWith("map") || hasReturn(lambda.getBody()))) { //$NON-NLS-1$
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
