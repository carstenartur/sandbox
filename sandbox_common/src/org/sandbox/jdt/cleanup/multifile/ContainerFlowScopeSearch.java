/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.cleanup.multifile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.sandbox.jdt.container.api.ContainerFlowSearchPlan;
import org.sandbox.jdt.container.api.ContainerFlowSearchPlan.SearchSeed;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan.ResolvedSearchTarget;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan.TargetKind;

/**
 * Resolves an AST-free container flow search plan to the source compilation units
 * required by coordinated multi-file planning.
 *
 * <p>This class deliberately does not implement another JDT reference search. It
 * resolves stable Java-model handles, expands method override families and delegates
 * all workspace matching, source-root policy and binary-boundary checks to
 * {@link RelatedCompilationUnitSearch}.</p>
 */
public final class ContainerFlowScopeSearch {

	/** Stable result of one flow-scope expansion attempt. */
	public record Result(
			List<ICompilationUnit> compilationUnits,
			ResolvedContainerFlowSearchPlan resolvedPlan,
			boolean complete,
			List<String> rejectionReasons) {

		public Result {
			compilationUnits= List.copyOf(compilationUnits);
			Objects.requireNonNull(resolvedPlan, "resolvedPlan"); //$NON-NLS-1$
			rejectionReasons= List.copyOf(rejectionReasons);
		}
	}

	@FunctionalInterface
	interface ElementResolver {
		IJavaElement resolve(String handleIdentifier);
	}

	@FunctionalInterface
	interface RelatedUnitFinder {
		RelatedCompilationUnitSearch.Result find(
				IJavaProject project,
				Collection<? extends IJavaElement> targets,
				Collection<ICompilationUnit> initialUnits,
				Collection<ICompilationUnit> allowedUnits,
				IProgressMonitor monitor) throws CoreException;
	}

	@FunctionalInterface
	interface MethodFamilyResolver {
		MethodFamily resolve(IMethod method, IProgressMonitor monitor);
	}

	record MethodFamily(List<IMethod> methods, boolean complete, List<String> rejectionReasons) {
		MethodFamily {
			methods= List.copyOf(methods);
			rejectionReasons= List.copyOf(rejectionReasons);
		}
	}

	private final ElementResolver elementResolver;
	private final MethodFamilyResolver methodFamilyResolver;
	private final RelatedUnitFinder relatedUnitFinder;

	private ContainerFlowScopeSearch() {
		this(JavaCore::create,
				ContainerFlowScopeSearch::resolveMethodFamily,
				RelatedCompilationUnitSearch::findReferences);
	}

	ContainerFlowScopeSearch(
			ElementResolver elementResolver,
			MethodFamilyResolver methodFamilyResolver,
			RelatedUnitFinder relatedUnitFinder) {
		this.elementResolver= Objects.requireNonNull(elementResolver, "elementResolver"); //$NON-NLS-1$
		this.methodFamilyResolver= Objects.requireNonNull(methodFamilyResolver, "methodFamilyResolver"); //$NON-NLS-1$
		this.relatedUnitFinder= Objects.requireNonNull(relatedUnitFinder, "relatedUnitFinder"); //$NON-NLS-1$
	}

	/**
	 * Finds all editable source units needed by the supplied flow search plan.
	 *
	 * @param project coordinated Java project
	 * @param plan AST-free search plan
	 * @param currentScope source units already admitted
	 * @param allowedUnits complete source-root-policy allow-list
	 * @param monitor progress monitor, may be {@code null}
	 * @return deterministic source closure and completeness diagnostics
	 * @throws CoreException if the delegated JDT search fails
	 */
	public static Result findRelatedUnits(
			IJavaProject project,
			ContainerFlowSearchPlan plan,
			Collection<ICompilationUnit> currentScope,
			Collection<ICompilationUnit> allowedUnits,
			IProgressMonitor monitor) throws CoreException {
		return new ContainerFlowScopeSearch().find(
				project, plan, currentScope, allowedUnits, monitor);
	}

	Result find(
			IJavaProject project,
			ContainerFlowSearchPlan plan,
			Collection<ICompilationUnit> currentScope,
			Collection<ICompilationUnit> allowedUnits,
			IProgressMonitor monitor) throws CoreException {
		Objects.requireNonNull(plan, "plan"); //$NON-NLS-1$
		checkCanceled(monitor);

		if (plan.isEmpty()) {
			RelatedCompilationUnitSearch.Result validated= validateCurrentScope(
					project, currentScope, allowedUnits);
			return result(
					validated,
					ResolvedContainerFlowSearchPlan.empty(),
					List.of());
		}

		Map<String, IJavaElement> targetsByHandle= new LinkedHashMap<>();
		Map<String, ResolvedSearchTarget> resolvedByKey= new LinkedHashMap<>();
		Set<String> rejectionReasons= new LinkedHashSet<>();
		for (SearchSeed seed : plan.seeds()) {
			checkCanceled(monitor);
			resolveSeed(
					seed,
					targetsByHandle,
					resolvedByKey,
					rejectionReasons,
					monitor);
		}

		RelatedCompilationUnitSearch.Result searched;
		if (targetsByHandle.isEmpty()) {
			searched= validateCurrentScope(project, currentScope, allowedUnits);
		} else {
			searched= relatedUnitFinder.find(
					project,
					targetsByHandle.values(),
					currentScope,
					allowedUnits,
					monitor);
		}
		return result(
				searched,
				new ResolvedContainerFlowSearchPlan(
						new ArrayList<>(resolvedByKey.values())),
				rejectionReasons);
	}

	private void resolveSeed(
			SearchSeed seed,
			Map<String, IJavaElement> targetsByHandle,
			Map<String, ResolvedSearchTarget> resolvedByKey,
			Set<String> rejectionReasons,
			IProgressMonitor monitor) {
		if (!seed.hasJavaElementHandle()) {
			rejectionReasons.add("A container flow search seed has no Java-model handle: " //$NON-NLS-1$
					+ seed.sourceNodeId());
			return;
		}

		IJavaElement element= elementResolver.resolve(seed.javaElementHandle());
		if (element == null || !element.exists()) {
			rejectionReasons.add("A container flow Java-model handle cannot be resolved: " //$NON-NLS-1$
					+ seed.javaElementHandle());
			return;
		}

		switch (seed.kind()) {
			case FIELD_REFERENCES -> addField(
					seed, element, targetsByHandle, resolvedByKey, rejectionReasons);
			case METHOD_DECLARATION, METHOD_CALLERS -> addMethod(
					seed, element, targetsByHandle, resolvedByKey, rejectionReasons);
			case METHOD_OVERRIDE_FAMILY -> addMethodFamily(
					seed,
					element,
					targetsByHandle,
					resolvedByKey,
					rejectionReasons,
					monitor);
		}
	}

	private static void addField(
			SearchSeed seed,
			IJavaElement element,
			Map<String, IJavaElement> targetsByHandle,
			Map<String, ResolvedSearchTarget> resolvedByKey,
			Set<String> rejectionReasons) {
		if (!(element instanceof IField field)) {
			rejectionReasons.add("A field-reference seed does not resolve to an IField: " //$NON-NLS-1$
					+ seed.javaElementHandle());
			return;
		}
		addTarget(field, targetsByHandle);
		addResolved(seed, TargetKind.FIELD, field, resolvedByKey);
	}

	private static void addMethod(
			SearchSeed seed,
			IJavaElement element,
			Map<String, IJavaElement> targetsByHandle,
			Map<String, ResolvedSearchTarget> resolvedByKey,
			Set<String> rejectionReasons) {
		if (!(element instanceof IMethod method)) {
			rejectionReasons.add("A method search seed does not resolve to an IMethod: " //$NON-NLS-1$
					+ seed.javaElementHandle());
			return;
		}
		addTarget(method, targetsByHandle);
		addResolved(seed, TargetKind.METHOD, method, resolvedByKey);
	}

	private void addMethodFamily(
			SearchSeed seed,
			IJavaElement element,
			Map<String, IJavaElement> targetsByHandle,
			Map<String, ResolvedSearchTarget> resolvedByKey,
			Set<String> rejectionReasons,
			IProgressMonitor monitor) {
		if (!(element instanceof IMethod method)) {
			rejectionReasons.add("An override-family seed does not resolve to an IMethod: " //$NON-NLS-1$
					+ seed.javaElementHandle());
			return;
		}

		MethodFamily family= methodFamilyResolver.resolve(method, monitor);
		for (IMethod member : family.methods()) {
			addTarget(member, targetsByHandle);
			addResolved(seed, TargetKind.METHOD, member, resolvedByKey);
		}
		if (!family.complete()) {
			rejectionReasons.addAll(family.rejectionReasons());
		}
	}

	private static void addResolved(
			SearchSeed seed,
			TargetKind targetKind,
			IJavaElement element,
			Map<String, ResolvedSearchTarget> resolvedByKey) {
		if (element == null || !element.exists()) {
			return;
		}
		String handle= element.getHandleIdentifier();
		if (handle == null || handle.isBlank()) {
			return;
		}
		ResolvedSearchTarget target= new ResolvedSearchTarget(
				seed.sourceNodeId(),
				seed.kind(),
				targetKind,
				seed.bindingKey(),
				seed.ownerKey(),
				handle,
				seed.signatureIndex(),
				seed.reason());
		resolvedByKey.putIfAbsent(target.stableKey(), target);
	}

	private static void addTarget(
			IJavaElement element,
			Map<String, IJavaElement> targetsByHandle) {
		if (element == null || !element.exists()) {
			return;
		}
		String handle= element.getHandleIdentifier();
		if (handle != null && !handle.isBlank()) {
			targetsByHandle.putIfAbsent(handle, element);
		}
	}

	private static MethodFamily resolveMethodFamily(IMethod method, IProgressMonitor monitor) {
		Map<String, IMethod> methodsByHandle= new LinkedHashMap<>();
		Set<String> reasons= new LinkedHashSet<>();
		addMethod(methodsByHandle, method);

		IType declaringType= method.getDeclaringType();
		if (declaringType == null || !declaringType.exists()) {
			reasons.add("The method declaring type is missing while expanding the override family."); //$NON-NLS-1$
			return methodFamily(methodsByHandle, reasons);
		}

		try {
			ITypeHierarchy hierarchy= declaringType.newTypeHierarchy(monitor);
			for (IType type : hierarchy.getAllTypes()) {
				checkCanceled(monitor);
				IMethod[] corresponding= type.findMethods(method);
				if (corresponding == null) {
					continue;
				}
				for (IMethod candidate : corresponding) {
					addMethod(methodsByHandle, candidate);
				}
			}
		} catch (JavaModelException exception) {
			reasons.add("JDT could not resolve the complete method override family: " //$NON-NLS-1$
					+ exception.getMessage());
		}
		return methodFamily(methodsByHandle, reasons);
	}

	private static MethodFamily methodFamily(
			Map<String, IMethod> methodsByHandle,
			Set<String> reasons) {
		List<IMethod> methods= new ArrayList<>(methodsByHandle.values());
		methods.sort(Comparator.comparing(IJavaElement::getHandleIdentifier));
		return new MethodFamily(methods, reasons.isEmpty(), new ArrayList<>(reasons));
	}

	private static void addMethod(Map<String, IMethod> methodsByHandle, IMethod method) {
		if (method == null || !method.exists()) {
			return;
		}
		String handle= method.getHandleIdentifier();
		if (handle != null && !handle.isBlank()) {
			methodsByHandle.putIfAbsent(handle, method);
		}
	}

	private static RelatedCompilationUnitSearch.Result validateCurrentScope(
			IJavaProject project,
			Collection<ICompilationUnit> currentScope,
			Collection<ICompilationUnit> allowedUnits) {
		RelatedCompilationUnitSearch.MatchAccumulator accumulator=
				new RelatedCompilationUnitSearch.MatchAccumulator(
						project, currentScope, allowedUnits);
		return accumulator.finish();
	}

	private static Result result(
			RelatedCompilationUnitSearch.Result searchResult,
			ResolvedContainerFlowSearchPlan resolvedPlan,
			Collection<String> additionalReasons) {
		Set<String> reasons= new LinkedHashSet<>(searchResult.rejectionReasons());
		reasons.addAll(additionalReasons);
		return new Result(
				searchResult.compilationUnits(),
				resolvedPlan,
				searchResult.complete() && reasons.isEmpty(),
				new ArrayList<>(reasons));
	}

	private static void checkCanceled(IProgressMonitor monitor) {
		if (monitor != null && monitor.isCanceled()) {
			throw new OperationCanceledException();
		}
	}
}
