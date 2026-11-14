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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan.TargetKind;

/** Checks source observations which a reference search for the original Java method cannot find. */
final class ContainerSignatureBoundaries {

	private static final Set<String> METHOD_LOOKUPS= Set.of("getDeclaredMethod", "getMethod"); //$NON-NLS-1$ //$NON-NLS-2$
	private static final Set<String> METHOD_LISTS= Set.of("getDeclaredMethods", "getMethods"); //$NON-NLS-1$ //$NON-NLS-2$
	private static final Set<String> CONSTRUCTOR_LOOKUPS= Set.of("getDeclaredConstructor", "getConstructor", "getDeclaredConstructors", "getConstructors"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	private static final Set<String> HANDLE_LOOKUPS= Set.of("findStatic", "findVirtual", "findSpecial", "findConstructor"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

	enum Rejection {
		SIGNATURE_OVERLOAD_CONFLICT("A same-name overload outside the migrating family can collide after erasure or change which signature a caller selects."), //$NON-NLS-1$
		REFLECTIVE_SIGNATURE_BOUNDARY("A reflective method or constructor lookup can observe this signature outside its resolved Java callers."); //$NON-NLS-1$
		final String explanation;
		Rejection(String explanation) {
			this.explanation= explanation;
		}
	}

	private ContainerSignatureBoundaries() {
	}

	static Rejection check(IJavaProject project, ResolvedContainerFlowSearchPlan resolved, Map<String, CompilationUnit> roots,
			IProgressMonitor monitor) throws CoreException {
		Set<IMethod> methods= new HashSet<>();
		Set<IMethod> changingParameters= new HashSet<>();
		for (var target : resolved.targets()) {
			if (target.targetKind() == TargetKind.METHOD && JavaCore.create(target.javaElementHandle()) instanceof IMethod method) {
				methods.add(method);
				if (target.signatureIndex() >= 0) {
					changingParameters.add(method);
				}
			}
		}
		if (conflicts(changingParameters, methods, monitor)) {
			return Rejection.SIGNATURE_OVERLOAD_CONFLICT;
		}
		if (methods.isEmpty()) {
			return null;
		}
		List<Signature> signatures= new ArrayList<>();
		for (IMethod method : methods) {
			signatures.add(new Signature(method.getDeclaringType().getFullyQualifiedName(), method.getElementName(), method.isConstructor()));
		}
		return observesProject(project, roots, signatures, monitor) ? Rejection.REFLECTIVE_SIGNATURE_BOUNDARY : null;
	}

	private static boolean conflicts(Set<IMethod> changingParameters, Set<IMethod> methods, IProgressMonitor monitor) throws CoreException {
		Map<String, List<IMethod>> hierarchyMethods= new HashMap<>();
		for (IMethod original : changingParameters) {
			if (original.isConstructor()) {
				continue; // The constructor proof already excludes overloaded constructors.
			}
			IType owner= original.getDeclaringType();
			String typeKey= owner.getHandleIdentifier();
			List<IMethod> familyContext= hierarchyMethods.get(typeKey);
			if (familyContext == null) {
				familyContext= new ArrayList<>();
				for (IType type : owner.newTypeHierarchy(monitor).getAllTypes()) {
					familyContext.addAll(List.of(type.getMethods()));
				}
				hierarchyMethods.put(typeKey, familyContext);
			}
			for (IMethod candidate : familyContext) {
				if (!methods.contains(candidate) && original.getElementName().equals(candidate.getElementName())
						&& !candidate.isConstructor() && (candidate.getNumberOfParameters() == original.getNumberOfParameters()
								|| Flags.isVarargs(candidate.getFlags()))) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean observesProject(IJavaProject project, Map<String, CompilationUnit> roots, List<Signature> signatures,
			IProgressMonitor monitor) throws CoreException {
		// A lookup in a file that only names a method in a string will never be an ordinary Java method reference.
		// Parse only files containing a possible lookup. Unicode escapes can spell any identifier.
		List<ICompilationUnit> extra= new ArrayList<>();
		for (var fragment : project.getPackageFragments()) {
			if (fragment.getKind() != IPackageFragmentRoot.K_SOURCE) {
				continue;
			}
			for (var unit : fragment.getCompilationUnits()) {
				if (monitor != null && monitor.isCanceled()) {
					throw new OperationCanceledException();
				}
				String source= unit.getSource();
				if (!possibleLookup(source)) {
					continue;
				}
				var root= roots.get(unit.getPrimary().getHandleIdentifier());
				if (root == null) {
					extra.add(unit);
				} else if (observes(root, signatures)) {
					return true;
				}
			}
		}
		if (!extra.isEmpty()) {
			var additionalRoots= ClosedSourceContainerMigrationCoordinator.parse(project, extra, monitor);
			if (additionalRoots.size() != extra.size()) {
				return true;
			}
			for (var root : additionalRoots.values()) {
				if (observes(root, signatures)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean possibleLookup(String source) {
		return source.contains("\\u") || source.contains("getDeclared") || source.contains("getMethod") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				|| source.contains("getConstructor") || HANDLE_LOOKUPS.stream().anyMatch(source::contains); //$NON-NLS-1$
	}

	private record Signature(String owner, String method, boolean constructor) {
	}

	private static boolean observes(CompilationUnit root, Collection<Signature> signatures) {
		boolean[] observed= { false };
		root.accept(new ASTVisitor() {
			@Override
			public boolean preVisit2(ASTNode node) {
				return !observed[0];
			}

			@Override
			public boolean visit(SimpleName name) {
				String op= name.getIdentifier();
				boolean method= METHOD_LOOKUPS.contains(op) || METHOD_LISTS.contains(op);
				boolean constructor= CONSTRUCTOR_LOOKUPS.contains(op);
				boolean handle= HANDLE_LOOKUPS.contains(op);
				if (!method && !constructor && !handle) {
					return true;
				}
				ASTNode parent= name.getParent();
				if (parent instanceof MethodReference) {
					if (name.resolveBinding() instanceof IMethodBinding target && isReflectionAPI(target, handle)) {
						Expression owner= !handle && parent instanceof ExpressionMethodReference ref ? ref.getExpression() : null;
						observed[0]= signatures.stream().anyMatch(signature -> signature.constructor() == (constructor || "findConstructor".equals(op)) //$NON-NLS-1$
								&& possibleOwner(owner, signature.owner()));
					}
					return !observed[0];
				}
				if (!(parent instanceof MethodInvocation call) || name != call.getName()) {
					return true;
				}
				var binding= call.resolveMethodBinding();
				if (binding != null && !binding.isRecovered() && !isReflectionAPI(binding, handle)) {
					return true;
				}
				Expression target= handle ? argument(call, 0) : call.getExpression();
				Expression lookup= METHOD_LOOKUPS.contains(op) ? argument(call, 0) : handle ? argument(call, 1) : null;
				String member= lookup != null && lookup.resolveConstantExpressionValue() instanceof String stable ? stable : null;
				boolean needsConstructor= constructor || "findConstructor".equals(op); //$NON-NLS-1$
				for (Signature signature : signatures) {
					if (signature.constructor() == needsConstructor && (needsConstructor || member == null || signature.method().equals(member))
							&& possibleOwner(target, signature.owner())) {
						observed[0]= true;
						return false;
					}
				}
				return true;
			}
		});
		return observed[0];
	}

	private static boolean isReflectionAPI(IMethodBinding binding, boolean handle) {
		ITypeBinding declaring= binding.getMethodDeclaration().getDeclaringClass();
		return declaring != null && (handle ? "java.lang.invoke.MethodHandles.Lookup" : "java.lang.Class").equals(declaring.getQualifiedName()); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static Expression argument(MethodInvocation call, int at) {
		return call.arguments().size() > at ? (Expression) call.arguments().get(at) : null;
	}

	private static boolean possibleOwner(Expression target, String expected) {
		while (target instanceof ParenthesizedExpression parens) {
			target= parens.getExpression();
		}
		if (target instanceof TypeLiteral literal) {
			var type= literal.getType().resolveBinding();
			return type == null || type.isRecovered() || isOwnerOrSubtype(type, expected, new HashSet<>());
		}
		return true; // Aliases, Class.forName and computed receivers may denote the target.
	}

	private static boolean isOwnerOrSubtype(ITypeBinding type, String expected, Set<String> visited) {
		if (type == null || !visited.add(type.getKey())) {
			return false;
		}
		if (expected.equals(type.getErasure().getQualifiedName()) || isOwnerOrSubtype(type.getSuperclass(), expected, visited)) {
			return true;
		}
		for (var parent : type.getInterfaces()) {
			if (isOwnerOrSubtype(parent, expected, visited)) {
				return true;
			}
		}
		return false;
	}
}
