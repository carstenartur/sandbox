/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.eclipse.jgit.storage.hibernate.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * An {@link ASTVisitor} that collects structural metadata from Java source
 * files.
 * <p>
 * Extracts declared types, methods, fields, supertypes, and interfaces from
 * the AST. Fully qualified names are constructed using the package name and
 * an import map for best-effort resolution of simple type references.
 * </p>
 */
public class JavaStructureVisitor extends ASTVisitor {

	private final Map<String, String> importMap;

	private final String packageName;

	private final List<String> types = new ArrayList<>();

	private final List<String> fqns = new ArrayList<>();

	private final List<String> methods = new ArrayList<>();

	private final List<String> fields = new ArrayList<>();

	private final List<String> superTypes = new ArrayList<>();

	private final List<String> interfaces = new ArrayList<>();

	/**
	 * Create a new visitor.
	 *
	 * @param importMap
	 *            mapping from simple names to fully qualified names
	 * @param packageName
	 *            the package name for FQN construction
	 */
	public JavaStructureVisitor(Map<String, String> importMap,
			String packageName) {
		this.importMap = importMap;
		this.packageName = packageName != null ? packageName : ""; //$NON-NLS-1$
	}

	@Override
	public boolean visit(TypeDeclaration node) {
		String name = node.getName().getIdentifier();
		types.add(name);
		fqns.add(buildFQN(name));

		if (node.getSuperclassType() != null) {
			superTypes.add(resolveTypeName(node.getSuperclassType()));
		}

		for (Object iface : node.superInterfaceTypes()) {
			if (iface instanceof Type) {
				interfaces.add(resolveTypeName((Type) iface));
			}
		}
		return true;
	}

	@Override
	public boolean visit(EnumDeclaration node) {
		String name = node.getName().getIdentifier();
		types.add(name);
		fqns.add(buildFQN(name));

		for (Object constant : node.enumConstants()) {
			if (constant instanceof EnumConstantDeclaration ecd) {
				fields.add(ecd.getName().getIdentifier());
			}
		}

		for (Object iface : node.superInterfaceTypes()) {
			if (iface instanceof Type) {
				interfaces.add(resolveTypeName((Type) iface));
			}
		}
		return true;
	}

	@Override
	public boolean visit(AnnotationTypeDeclaration node) {
		String name = node.getName().getIdentifier();
		types.add(name);
		fqns.add(buildFQN(name));
		return true;
	}

	@Override
	public boolean visit(MethodDeclaration node) {
		methods.add(node.getName().getIdentifier());
		return true;
	}

	@Override
	public boolean visit(FieldDeclaration node) {
		for (Object fragment : node.fragments()) {
			if (fragment instanceof VariableDeclarationFragment vdf) {
				fields.add(vdf.getName().getIdentifier());
			}
		}
		return true;
	}

	/**
	 * Get the newline-separated list of declared type names.
	 *
	 * @return declared types
	 */
	public String getTypes() {
		return String.join("\n", types); //$NON-NLS-1$
	}

	/**
	 * Get the newline-separated list of fully qualified names.
	 *
	 * @return fully qualified names
	 */
	public String getFQNs() {
		return String.join("\n", fqns); //$NON-NLS-1$
	}

	/**
	 * Get the newline-separated list of declared methods.
	 *
	 * @return declared methods
	 */
	public String getMethods() {
		return String.join("\n", methods); //$NON-NLS-1$
	}

	/**
	 * Get the newline-separated list of declared fields.
	 *
	 * @return declared fields
	 */
	public String getFields() {
		return String.join("\n", fields); //$NON-NLS-1$
	}

	/**
	 * Get the newline-separated list of supertypes.
	 *
	 * @return supertypes
	 */
	public String getSuperTypes() {
		return String.join("\n", superTypes); //$NON-NLS-1$
	}

	/**
	 * Get the newline-separated list of implemented interfaces.
	 *
	 * @return implemented interfaces
	 */
	public String getInterfaces() {
		return String.join("\n", interfaces); //$NON-NLS-1$
	}

	private String buildFQN(String simpleName) {
		if (packageName.isEmpty()) {
			return simpleName;
		}
		return packageName + "." + simpleName; //$NON-NLS-1$
	}

	private String resolveTypeName(Type type) {
		String simpleName;
		if (type instanceof SimpleType simpleType) {
			simpleName = simpleType.getName().getFullyQualifiedName();
		} else if (type instanceof ParameterizedType paramType) {
			// For List<String>, extract just "List"
			return resolveTypeName(paramType.getType());
		} else {
			simpleName = type.toString();
		}
		if (importMap.containsKey(simpleName)) {
			return importMap.get(simpleName);
		}
		// Best effort: assume same package
		return buildFQN(simpleName);
	}
}
