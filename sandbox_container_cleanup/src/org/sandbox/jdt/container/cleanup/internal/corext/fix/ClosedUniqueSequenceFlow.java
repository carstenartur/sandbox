/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.container.cleanup.internal.corext.fix;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotatableType;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TextBlock;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

import org.sandbox.jdt.container.analysis.LocalUniqueSequenceAnalyzer;
import org.sandbox.jdt.container.analysis.UniqueSequencePattern;

/** Per-AST proof of the guarded builder, its direct callers and optional read-only parameter boundaries. */
public final class ClosedUniqueSequenceFlow {
	private static final String LIST= "java.util.List"; //$NON-NLS-1$
	private static final String ARRAY_LIST= "java.util.ArrayList"; //$NON-NLS-1$
	private static final String STRING= "java.lang.String"; //$NON-NLS-1$

	private ClosedUniqueSequenceFlow() {
	}

	public enum Kind {
		SET_TYPE, CALLER_SET_TYPE, ORDERED_SET_TYPE, GUARDED_ADD, SYNCHRONIZED_SET_FACTORY
	}

	/** Exact edit range in the parsed source. Type ranges name the raw type under its type arguments. */
	public record Edit(Kind kind, int start, int length) {
		Edit(Kind kind, ASTNode node) {
			this(kind, node.getStartPosition(), node.getLength());
		}
	}

	public static List<IMethod> seeds(CompilationUnit root) {
		if (hasErrors(root)) {
			return List.of();
		}
		List<IMethod> producers= new ArrayList<>();
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration method) {
				if (producerEdits(method).isPresent()) {
					producers.add(model(method.resolveBinding()));
				}
				return false;
			}
		});
		return List.copyOf(producers);
	}

	/** Empty optional means a producer reference or a use of its result was not closed. */
	public static Optional<List<IMethod>> readers(CompilationUnit root, IMethod producer) {
		if (hasErrors(root)) {
			return Optional.empty();
		}
		var uses= new CallerUses();
		return uses.scan(root, producer) ? Optional.of(List.copyOf(uses.readers)) : Optional.empty();
	}

	/** Checks every declaration and reference to this candidate's producer/readers before returning edits. */
	public static Optional<List<Edit>> edits(CompilationUnit root, IMethod producer, Collection<IMethod> readers) {
		if (hasErrors(root)) {
			return Optional.empty();
		}
		var uses= new CallerUses();
		if (!uses.scan(root, producer) || !readers.containsAll(uses.readers)) {
			return Optional.empty();
		}
		List<Edit> edits= new ArrayList<>(uses.edits);
		boolean closed= names(root, name -> {
			if (!(name.resolveBinding() instanceof IMethodBinding binding)) {
				return true;
			}
			IMethod method= model(binding);
			if (method == null || !producer.equals(method) && !readers.contains(method)) {
				return true;
			}
			if (name.getParent() instanceof MethodDeclaration declaration && declaration.getName() == name) {
				Optional<List<Edit>> member= producer.equals(method) ? producerEdits(declaration) : readerEdits(declaration);
				member.ifPresent(edits::addAll);
				return member.isPresent();
			}
			if (name.getParent() instanceof MethodInvocation invocation && invocation.getName() == name) {
				return producer.equals(method) || method.equals(uses.forwarded.get(invocation));
			}
			return false;
		});
		if (!closed) {
			return Optional.empty();
		}
		edits.sort(Comparator.comparingInt(Edit::start));
		return Optional.of(List.copyOf(edits));
	}

	private static boolean hasErrors(CompilationUnit root) {
		return Arrays.stream(root.getProblems()).anyMatch(IProblem::isError);
	}

	private static Optional<List<Edit>> producerEdits(MethodDeclaration method) {
		var registry= LockedRegistrySnapshot.edits(method);
		if (registry.isPresent()) {
			return registry;
		}
		if (!(method.getReturnType2() instanceof ParameterizedType type) || !list(type)
				|| !eligibleMethod(method.resolveBinding()) || method.getBody() == null || method.getBody().statements().isEmpty()
				|| !(method.getBody().statements().getLast() instanceof ReturnStatement published)
				|| !(published.getExpression() instanceof SimpleName result)
				|| !(result.resolveBinding() instanceof IVariableBinding binding)
				|| !oneReturn(method.getBody(), published)
				|| !(((CompilationUnit) method.getRoot()).findDeclaringNode(binding.getVariableDeclaration()) instanceof VariableDeclarationFragment variable)
				|| !(variable.getParent() instanceof VariableDeclarationStatement declaration) || declaration.getParent() != method.getBody()
				|| declaration.fragments().size() != 1 || !(declaration.getType() instanceof ParameterizedType declared) || !list(declared)
				|| !declared.resolveBinding().isEqualTo(type.resolveBinding())) {
			return Optional.empty();
		}
		var constructed= freshArrayList(variable, element(type.resolveBinding()));
		if (constructed.isEmpty()) {
			return Optional.empty();
		}
		var profile= new LocalUniqueSequenceAnalyzer().analyze(variable);
		if (profile.isEmpty()) {
			return Optional.empty();
		}
		List<Edit> guards= new ArrayList<>();
		boolean returned= false;
		boolean hashStable= false;
		for (var fact : profile.get().evidence()) {
			switch (fact.kind()) {
				case REFERENCE_COMPONENT, ENCOUNTER_ITERATION -> { /* Proven local observations. */ }
				case HASH_STABLE_COMPONENT -> hashStable= true;
				case UNSAFE_ESCAPE -> {
					// The sole escape admitted by this *cross-file* proof is the final publication.
					if (fact.sourceStart() != result.getStartPosition() || fact.sourceLength() != result.getLength()) {
						return Optional.empty();
					}
					returned= true;
				}
				case DUPLICATE_SUPPRESSION -> {
					var node= NodeFinder.perform(method.getRoot(), fact.sourceStart(), fact.sourceLength());
					if (!(node instanceof IfStatement guard) || losesGuardComment(guard)
							|| UniqueSequencePattern.match(guard, binding.getVariableDeclaration().getKey()).isEmpty()) {
						return Optional.empty();
					}
					guards.add(new Edit(Kind.GUARDED_ADD, guard));
				}
				default -> { return Optional.empty(); }
			}
		}
		if (!returned || !hashStable || guards.isEmpty()) {
			return Optional.empty();
		}
		List<Edit> edits= new ArrayList<>();
		edits.add(new Edit(Kind.SET_TYPE, type.getType()));
		edits.add(new Edit(Kind.SET_TYPE, declared.getType()));
		edits.add(new Edit(Kind.ORDERED_SET_TYPE, constructed.get()));
		edits.addAll(guards);
		return Optional.of(List.copyOf(edits));
	}

	static boolean losesGuardComment(IfStatement guard) {
		int start= guard.getStartPosition();
		int end= start + guard.getLength();
		var kept= guard.getThenStatement();
		int from= kept.getStartPosition();
		int until= from + kept.getLength();
		// The rewrite moves only the then statement. A comment in the deleted condition must survive as written.
		var comments= ((CompilationUnit) guard.getRoot()).getCommentList();
		if (comments == null) {
			return true;
		}
		for (Object raw : comments) {
			Comment comment= (Comment) raw;
			int at= comment.getStartPosition();
			int after= at + comment.getLength();
			if (at >= start && after <= end && (at < from || after > until)) {
				return true;
			}
		}
		return false;
	}

	private static Optional<Type> freshArrayList(VariableDeclarationFragment variable, ITypeBinding element) {
		if (!(variable.getInitializer() instanceof ClassInstanceCreation allocation) || allocation.getAnonymousClassDeclaration() != null
				|| !allocation.arguments().isEmpty() || !allocation.typeArguments().isEmpty()
				|| !(allocation.getType() instanceof ParameterizedType constructed) || !plain(constructed)) {
			return Optional.empty();
		}
		ITypeBinding type= allocation.resolveTypeBinding();
		return exact(type, ARRAY_LIST) && type.getTypeArguments().length == 1 && element.isEqualTo(element(type))
				? Optional.of(constructed.getType()) : Optional.empty();
	}

	private static boolean oneReturn(Block body, ReturnStatement expected) {
		boolean[] unique= { true };
		body.accept(new ASTVisitor() {
			@Override
			public boolean visit(ReturnStatement statement) {
				unique[0] &= statement == expected;
				return false;
			}
		});
		return unique[0];
	}

	private static Optional<List<Edit>> readerEdits(MethodDeclaration method) {
		if (!eligibleMethod(method.resolveBinding()) || method.getBody() == null || method.parameters().size() != 1
				|| !(method.parameters().getFirst() instanceof SingleVariableDeclaration parameter)
				|| !(parameter.getType() instanceof ParameterizedType type) || !list(type) || parameter.resolveBinding() == null) {
			return Optional.empty();
		}
		boolean verified= variableUses(method.getBody(), parameter.resolveBinding(), reference -> read(reference, element(type.resolveBinding())));
		return verified ? Optional.of(List.of(new Edit(Kind.SET_TYPE, type.getType()))) : Optional.empty();
	}

	private static final class CallerUses {
		final List<Edit> edits= new ArrayList<>();
		final Set<IMethod> readers= new LinkedHashSet<>();
		final Map<MethodInvocation, IMethod> forwarded= new LinkedHashMap<>();

		boolean scan(CompilationUnit root, IMethod producer) {
			return names(root, name -> {
				if (!(name.resolveBinding() instanceof IMethodBinding binding) || !producer.equals(model(binding))
						|| name.getParent() instanceof MethodDeclaration declaration && declaration.getName() == name) {
					return true;
				}
				return name.getParent() instanceof MethodInvocation call && call.getName() == name && acceptCall(call, binding);
			});
		}

		private boolean acceptCall(MethodInvocation call, IMethodBinding target) {
			if (!(call.getParent() instanceof VariableDeclarationFragment variable) || variable.getInitializer() != call
					|| !(variable.getParent() instanceof VariableDeclarationStatement declaration) || declaration.fragments().size() != 1
					|| !(declaration.getParent() instanceof Block scope) || !(declaration.getType() instanceof ParameterizedType type)
					|| !list(type) || !type.resolveBinding().isEqualTo(target.getReturnType()) || variable.resolveBinding() == null) {
				return false;
			}
			edits.add(new Edit(Kind.CALLER_SET_TYPE, type.getType()));
			return variableUses(scope, variable.resolveBinding(), reference -> {
				if (reference == variable.getName() || read(reference, element(target.getReturnType()))) {
					return true;
				}
				if (!(reference.getParent() instanceof MethodInvocation helper) || helper.arguments().size() != 1
						|| helper.arguments().getFirst() != reference || !eligibleMethod(helper.resolveMethodBinding())) {
					return false;
				}
				var reader= helper.resolveMethodBinding();
				if (!(helper.getExpression() instanceof Name qualifier) || !(qualifier.resolveBinding() instanceof ITypeBinding owner)
						|| !owner.isEqualTo(reader.getDeclaringClass())) {
					// An unqualified call may acquire another applicable method from a static import or an enclosing type.
					return false;
				}
				if (reader.getParameterTypes().length != 1 || !reader.getParameterTypes()[0].isEqualTo(target.getReturnType())) {
					return false;
				}
				IMethod handle= model(reader);
				readers.add(handle);
				forwarded.put(helper, handle);
				return true;
			});
		}
	}

	private static boolean variableUses(Block scope, IVariableBinding variable, Predicate<SimpleName> allowed) {
		return names(scope, name -> {
			if (!(name.resolveBinding() instanceof IVariableBinding use) || !use.getVariableDeclaration().isEqualTo(variable.getVariableDeclaration())) {
				return true;
			}
			for (ASTNode ancestor= name.getParent(); ancestor != scope; ancestor= ancestor.getParent()) {
				if (ancestor instanceof LambdaExpression || ancestor instanceof AnonymousClassDeclaration
						|| ancestor instanceof AbstractTypeDeclaration || ancestor instanceof MethodDeclaration) {
					return false;
				}
			}
			return allowed.test(name);
		});
	}

	private static boolean names(ASTNode root, Predicate<SimpleName> allowed) {
		boolean[] valid= { true };
		root.accept(new ASTVisitor() {
			@Override
			public boolean preVisit2(ASTNode node) {
				return valid[0];
			}
			@Override
			public boolean visit(SimpleName name) {
				valid[0]= allowed.test(name);
				return valid[0];
			}
		});
		return valid[0];
	}

	private static boolean read(SimpleName reference, ITypeBinding component) {
		if (reference.getParent() instanceof EnhancedForStatement loop && loop.getExpression() == reference) {
			return true;
		}
		if (!(reference.getParent() instanceof MethodInvocation call) || call.getExpression() != reference
				|| call.resolveMethodBinding() == null || call.resolveMethodBinding().isRecovered()
				|| !call.typeArguments().isEmpty()) {
			return false;
		}
		return switch (call.getName().getIdentifier()) {
			case "isEmpty", "size" -> call.arguments().isEmpty(); //$NON-NLS-1$ //$NON-NLS-2$
			// contains(Object) may otherwise dispatch to arbitrary equals/hashCode implementations.
			case "contains" -> call.arguments().size() == 1 && stableQuery((Expression) call.arguments().getFirst(), component); //$NON-NLS-1$
			default -> false;
		};
	}

	private static boolean stableQuery(Expression expression, ITypeBinding component) {
		while (expression instanceof ParenthesizedExpression grouped) {
			expression= grouped.getExpression();
		}
		if (expression instanceof NullLiteral) {
			return true;
		}
		if (expression instanceof StringLiteral || expression instanceof TextBlock) {
			return exact(component, STRING);
		}
		// contains(Object) does not insert a cast. A generic return or field substituted with String can
		// still contain a foreign object at runtime, which changes the equals/hashCode calls made here.
		if (expression instanceof Name name && name.resolveBinding() instanceof IVariableBinding binding) {
			var value= binding.getVariableDeclaration();
			return (!value.isField() || value.isEnumConstant()) && value.getType().isEqualTo(component);
		}
		return false;
	}

	static boolean eligibleMethod(IMethodBinding method) {
		IMethod source= model(method);
		if (source == null || method.isConstructor() || method.isGenericMethod() || method.isVarargs()
				|| !Modifier.isStatic(method.getModifiers()) || Modifier.isPublic(method.getModifiers()) || Modifier.isProtected(method.getModifiers())
				|| Modifier.isNative(method.getModifiers()) || method.getAnnotations().length != 0) {
			return false;
		}
		var owner= method.getDeclaringClass();
		if (owner == null || !owner.isTopLevel() || !Modifier.isFinal(owner.getModifiers()) || Modifier.isPublic(owner.getModifiers())
				|| owner.isInterface() || owner.isEnum() || owner.isRecord() || owner.getAnnotations().length != 0
				|| owner.isGenericType() || owner.getInterfaces().length != 0 || !exact(owner.getSuperclass(), "java.lang.Object")) { //$NON-NLS-1$
			return false;
		}
		// Eclipse's search-resolved reference AST can have incomplete source-type bindings.
		try {
			for (IMethod other : source.getDeclaringType().getMethods()) {
				if (source.getElementName().equals(other.getElementName()) && !source.equals(other)) {
					return false;
				}
			}
			return true;
		} catch (JavaModelException unavailable) {
			return false;
		}
	}

	private static IMethod model(IMethodBinding method) {
		return method != null && !method.isRecovered() && method.getMethodDeclaration().getJavaElement() instanceof IMethod source
				&& !source.isBinary() ? source : null;
	}

	static boolean list(ParameterizedType type) {
		ITypeBinding binding= type.resolveBinding();
		if (!plain(type) || !exact(binding, LIST) || binding.getTypeArguments().length != 1) {
			return false;
		}
		var value= element(binding);
		return value != null && !value.isRecovered() && (value.isEnum() || exact(value, STRING));
	}

	static boolean plain(ParameterizedType type) {
		return type.getType() instanceof AnnotatableType raw && raw.annotations().isEmpty() && raw.resolveBinding() != null
				&& raw.resolveBinding().getTypeAnnotations().length == 0 && type.resolveBinding() != null
				&& type.resolveBinding().getTypeAnnotations().length == 0;
	}

	static boolean exact(ITypeBinding type, String qualified) {
		return type != null && !type.isRecovered() && qualified.equals(type.getErasure().getQualifiedName());
	}

	private static ITypeBinding element(ITypeBinding type) {
		return type.getTypeArguments()[0];
	}
}
