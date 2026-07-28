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
package org.sandbox.jdt.cleanup.multifile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Modifier;

/** Binding-based hierarchy checks for prospective generated nested type names. */
public final class GeneratedNameHierarchyPolicy {

	private GeneratedNameHierarchyPolicy() {
	}

	/**
	 * Returns accessible inherited member types whose simple name would be hidden by
	 * a newly generated nested type in {@code ownerBinding}.
	 *
	 * @param ownerBinding the prospective owner type
	 * @param requestedName the generated nested type name
	 * @return deterministic qualified collision descriptions
	 */
	public static List<String> inheritedMemberTypeCollisions(ITypeBinding ownerBinding, String requestedName) {
		if (ownerBinding == null || requestedName == null || requestedName.isBlank()) {
			return List.of();
		}
		ITypeBinding owner= ownerBinding.getTypeDeclaration();
		Map<String, String> collisions= new TreeMap<>();
		Set<String> visited= new HashSet<>();
		collect(owner.getSuperclass(), owner, requestedName, visited, collisions);
		for (ITypeBinding interfaceBinding : owner.getInterfaces()) {
			collect(interfaceBinding, owner, requestedName, visited, collisions);
		}
		return List.copyOf(collisions.values());
	}

	private static void collect(ITypeBinding binding, ITypeBinding owner, String requestedName,
			Set<String> visited, Map<String, String> collisions) {
		if (binding == null) {
			return;
		}
		ITypeBinding declaration= binding.getTypeDeclaration();
		String identity= identity(declaration);
		if (!visited.add(identity)) {
			return;
		}
		for (ITypeBinding memberType : declaration.getDeclaredTypes()) {
			ITypeBinding memberDeclaration= memberType.getTypeDeclaration();
			if (requestedName.equals(memberDeclaration.getName()) && isAccessible(memberDeclaration, owner)) {
				String memberName= qualifiedName(memberDeclaration);
				String declaringName= qualifiedName(declaration);
				collisions.put(memberName, memberName + " inherited from " + declaringName); //$NON-NLS-1$
			}
		}
		collect(declaration.getSuperclass(), owner, requestedName, visited, collisions);
		for (ITypeBinding interfaceBinding : declaration.getInterfaces()) {
			collect(interfaceBinding, owner, requestedName, visited, collisions);
		}
	}

	private static boolean isAccessible(ITypeBinding memberType, ITypeBinding owner) {
		int modifiers= memberType.getModifiers();
		if (Modifier.isPrivate(modifiers)) {
			return false;
		}
		if (Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers)) {
			return true;
		}
		String memberPackage= packageName(memberType.getDeclaringClass());
		String ownerPackage= packageName(owner);
		return memberPackage.equals(ownerPackage);
	}

	private static String packageName(ITypeBinding binding) {
		return binding == null || binding.getPackage() == null ? "" : binding.getPackage().getName(); //$NON-NLS-1$
	}

	private static String identity(ITypeBinding binding) {
		String key= binding.getKey();
		return key == null || key.isBlank() ? qualifiedName(binding) : key;
	}

	private static String qualifiedName(ITypeBinding binding) {
		String qualifiedName= binding.getQualifiedName();
		if (qualifiedName != null && !qualifiedName.isBlank()) {
			return qualifiedName;
		}
		List<String> names= new ArrayList<>();
		ITypeBinding current= binding;
		while (current != null) {
			names.add(0, current.getName());
			current= current.getDeclaringClass();
		}
		String localName= String.join(".", names); //$NON-NLS-1$
		String packageName= packageName(binding);
		return packageName.isEmpty() ? localName : packageName + '.' + localName;
	}
}
