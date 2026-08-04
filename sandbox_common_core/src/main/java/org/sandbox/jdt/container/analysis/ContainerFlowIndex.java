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
package org.sandbox.jdt.container.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.sandbox.jdt.internal.common.AstProcessing;
import org.sandbox.jdt.internal.common.ReferenceHolder;

/**
 * Deterministic binding and reference index for one compilation unit.
 *
 * <p>The index is intentionally scoped to one parser result. AST nodes never leave
 * the current analysis pass; only the resulting flow graph is retained.</p>
 */
final class ContainerFlowIndex {

	private final Map<String, BindingInfo> bindings;
	private final Map<String, List<SimpleName>> references;
	private final Map<String, MethodInfo> methods;
	private final String compilationUnitHandle;

	private ContainerFlowIndex(
			Map<String, BindingInfo> bindings,
			Map<String, List<SimpleName>> references,
			Map<String, MethodInfo> methods,
			String compilationUnitHandle) {
		this.bindings= immutableLinkedMap(bindings);
		this.references= immutableReferenceMap(references);
		this.methods= immutableLinkedMap(methods);
		this.compilationUnitHandle= compilationUnitHandle;
	}

	static ContainerFlowIndex create(CompilationUnit compilationUnit) {
		Map<String, BindingInfo> bindings= new LinkedHashMap<>();
		Map<String, List<SimpleName>> references= new LinkedHashMap<>();
		Map<String, MethodInfo> methods= new LinkedHashMap<>();
		IJavaElement javaElement= compilationUnit.getJavaElement();
		String unitHandle= javaElement == null ? "" : javaElement.getHandleIdentifier(); //$NON-NLS-1$

		AstProcessing.independent(ReferenceHolder.<String, Object>create())
				.on(MethodDeclaration.class, (method, holder) -> {
					indexMethod(method, methods);
					return true;
				})
				.on(SimpleName.class, (name, holder) -> {
					indexVariable(name, bindings, references, unitHandle);
					return true;
				})
				.build(compilationUnit);

		return new ContainerFlowIndex(bindings, references, methods, unitHandle);
	}

	Map<String, BindingInfo> bindings() {
		return bindings;
	}

	Map<String, List<SimpleName>> references() {
		return references;
	}

	Map<String, MethodInfo> methods() {
		return methods;
	}

	String compilationUnitHandle() {
		return compilationUnitHandle;
	}

	private static void indexMethod(
			MethodDeclaration method,
			Map<String, MethodInfo> methods) {
		IMethodBinding binding= method.resolveBinding();
		if (binding == null) {
			return;
		}
		List<String> parameterBindingKeys= new ArrayList<>();
		for (Object parameterObject : method.parameters()) {
			SingleVariableDeclaration parameter= (SingleVariableDeclaration) parameterObject;
			IVariableBinding parameterBinding= parameter.resolveBinding();
			parameterBindingKeys.add(parameterBinding == null
					? "" : parameterBinding.getVariableDeclaration().getKey()); //$NON-NLS-1$
		}
		methods.put(
				binding.getMethodDeclaration().getKey(),
				new MethodInfo(parameterBindingKeys));
	}

	private static void indexVariable(
			SimpleName name,
			Map<String, BindingInfo> bindings,
			Map<String, List<SimpleName>> references,
			String unitHandle) {
		IBinding resolved= name.resolveBinding();
		if (!(resolved instanceof IVariableBinding variable)) {
			return;
		}
		IVariableBinding declaration= variable.getVariableDeclaration();
		String bindingKey= declaration.getKey();
		if (bindingKey == null || bindingKey.isBlank()) {
			return;
		}

		BindingInfo info= bindings.computeIfAbsent(
				bindingKey,
				ignored -> new BindingInfo(declaration, unitHandle));
		if (isDeclarationName(name)) {
			info.recordDeclaration(name);
		}
		references.computeIfAbsent(bindingKey, ignored -> new ArrayList<>()).add(name);
	}

	private static boolean isDeclarationName(SimpleName name) {
		return name.getParent() instanceof VariableDeclarationFragment fragment
				&& fragment.getName() == name
				|| name.getParent() instanceof SingleVariableDeclaration declaration
						&& declaration.getName() == name;
	}

	private static <K, V> Map<K, V> immutableLinkedMap(Map<K, V> source) {
		return Collections.unmodifiableMap(new LinkedHashMap<>(source));
	}

	private static Map<String, List<SimpleName>> immutableReferenceMap(
			Map<String, List<SimpleName>> source) {
		Map<String, List<SimpleName>> copy= new LinkedHashMap<>();
		source.forEach((key, value) -> copy.put(key, List.copyOf(value)));
		return Collections.unmodifiableMap(copy);
	}

	/** Binding metadata used while the parser result is alive. */
	static final class BindingInfo {

		private final IVariableBinding binding;
		private final String compilationUnitHandle;
		private SimpleName declarationName;

		BindingInfo(IVariableBinding binding, String compilationUnitHandle) {
			this.binding= binding.getVariableDeclaration();
			this.compilationUnitHandle= compilationUnitHandle;
		}

		void recordDeclaration(SimpleName name) {
			declarationName= name;
		}

		IVariableBinding binding() {
			return binding;
		}

		SimpleName anchor() {
			return declarationName;
		}

		String compilationUnitHandle() {
			return compilationUnitHandle;
		}
	}

	/** Parameter binding keys for one local method declaration. */
	static record MethodInfo(List<String> parameterBindingKeys) {

		MethodInfo {
			parameterBindingKeys= List.copyOf(parameterBindingKeys);
		}
	}
}
