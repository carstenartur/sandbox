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
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
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

	private static final int MAX_DOC_LENGTH = 2000;

	private static final int MIN_STRING_LITERAL_LENGTH = 3;

	private final Map<String, String> importMap;

	private final String packageName;

	private final List<String> types = new ArrayList<>();

	private final List<String> fqns = new ArrayList<>();

	private final List<String> methods = new ArrayList<>();

	private final List<String> fields = new ArrayList<>();

	private final List<String> superTypes = new ArrayList<>();

	private final List<String> interfaces = new ArrayList<>();

	private final List<String> annotationNames = new ArrayList<>();

	private final List<String> methodSignatures = new ArrayList<>();

	private final List<String> referencedTypes = new ArrayList<>();

	private final List<String> stringLiterals = new ArrayList<>();

	private boolean hasMainMethod;

	private String typeDocumentation = ""; //$NON-NLS-1$

	private String typeKind = ""; //$NON-NLS-1$

	private String visibility = ""; //$NON-NLS-1$

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
		if (typeKind.isEmpty()) {
			typeKind = node.isInterface() ? "interface" : "class"; //$NON-NLS-1$ //$NON-NLS-2$
			visibility = extractVisibility(node.getModifiers());
		}
		// Capture Javadoc of the primary type
		if (typeDocumentation.isEmpty() && node.getJavadoc() != null) {
			typeDocumentation = truncateDoc(
					node.getJavadoc().toString(), MAX_DOC_LENGTH);
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
		if (typeKind.isEmpty()) {
			typeKind = "enum"; //$NON-NLS-1$
			visibility = extractVisibility(node.getModifiers());
		}
		return true;
	}

	@Override
	public boolean visit(AnnotationTypeDeclaration node) {
		String name = node.getName().getIdentifier();
		types.add(name);
		fqns.add(buildFQN(name));
		if (typeKind.isEmpty()) {
			typeKind = "annotation"; //$NON-NLS-1$
			visibility = extractVisibility(node.getModifiers());
		}
		return true;
	}

	@Override
	public boolean visit(MarkerAnnotation node) {
		annotationNames.add(resolveAnnotationName(
				node.getTypeName().getFullyQualifiedName()));
		return true;
	}

	@Override
	public boolean visit(NormalAnnotation node) {
		annotationNames.add(resolveAnnotationName(
				node.getTypeName().getFullyQualifiedName()));
		return true;
	}

	@Override
	public boolean visit(SingleMemberAnnotation node) {
		annotationNames.add(resolveAnnotationName(
				node.getTypeName().getFullyQualifiedName()));
		return true;
	}

	@Override
	public boolean visit(MethodDeclaration node) {
		String methodName = node.getName().getIdentifier();
		methods.add(methodName);

		// Build method signature: methodName(ParamType1,ParamType2)
		StringBuilder sig = new StringBuilder();
		sig.append(methodName);
		sig.append('(');
		boolean first = true;
		for (Object param : node.parameters()) {
			if (param instanceof SingleVariableDeclaration svd) {
				if (!first) {
					sig.append(',');
				}
				String typeName = resolveTypeName(svd.getType());
				sig.append(typeName);
				referencedTypes.add(typeName);
				first = false;
			}
		}
		sig.append(')');
		methodSignatures.add(sig.toString());

		// Return type
		Type returnType = node.getReturnType2();
		if (returnType != null) {
			referencedTypes.add(resolveTypeName(returnType));
		}

		// Detect main method
		if ("main".equals(methodName) //$NON-NLS-1$
				&& Modifier.isPublic(node.getModifiers())
				&& Modifier.isStatic(node.getModifiers())
				&& node.parameters().size() == 1) {
			hasMainMethod = true;
		}

		return true;
	}

	@Override
	public boolean visit(FieldDeclaration node) {
		for (Object fragment : node.fragments()) {
			if (fragment instanceof VariableDeclarationFragment vdf) {
				fields.add(vdf.getName().getIdentifier());
			}
		}
		// Collect field type as referenced type
		if (node.getType() != null) {
			referencedTypes.add(resolveTypeName(node.getType()));
		}
		return true;
	}

	@Override
	public boolean visit(CatchClause node) {
		if (node.getException() != null
				&& node.getException().getType() != null) {
			referencedTypes.add(
					resolveTypeName(node.getException().getType()));
		}
		return true;
	}

	@Override
	public boolean visit(StringLiteral node) {
		String value = node.getLiteralValue();
		if (value != null && value.length() > MIN_STRING_LITERAL_LENGTH) {
			stringLiterals.add(value);
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

	/**
	 * Get the newline-separated list of annotation names.
	 *
	 * @return annotations
	 */
	public String getAnnotations() {
		return String.join("\n", annotationNames); //$NON-NLS-1$
	}

	/**
	 * Get the type kind (class, interface, enum, annotation).
	 *
	 * @return the type kind
	 */
	public String getTypeKind() {
		return typeKind;
	}

	/**
	 * Get the visibility modifier string.
	 *
	 * @return the visibility
	 */
	public String getVisibility() {
		return visibility;
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

	private String resolveAnnotationName(String simpleName) {
		if (importMap.containsKey(simpleName)) {
			return importMap.get(simpleName);
		}
		return simpleName;
	}

	private static String extractVisibility(int modifiers) {
		StringBuilder sb = new StringBuilder();
		if (Modifier.isPublic(modifiers)) {
			sb.append("public"); //$NON-NLS-1$
		} else if (Modifier.isProtected(modifiers)) {
			sb.append("protected"); //$NON-NLS-1$
		} else if (Modifier.isPrivate(modifiers)) {
			sb.append("private"); //$NON-NLS-1$
		} else {
			sb.append("package"); //$NON-NLS-1$
		}
		if (Modifier.isAbstract(modifiers)) {
			sb.append(" abstract"); //$NON-NLS-1$
		}
		if (Modifier.isFinal(modifiers)) {
			sb.append(" final"); //$NON-NLS-1$
		}
		return sb.toString().trim();
	}

	private static String truncateDoc(String doc, int maxLen) {
		if (doc == null) {
			return ""; //$NON-NLS-1$
		}
		// Strip leading/trailing whitespace and comment markers
		String clean = doc.replaceAll("/\\*\\*|\\*/|\\*", "").trim(); //$NON-NLS-1$ //$NON-NLS-2$
		if (clean.length() > maxLen) {
			return clean.substring(0, maxLen);
		}
		return clean;
	}

	/**
	 * Get the newline-separated list of method signatures.
	 *
	 * @return method signatures
	 */
	public String getMethodSignatures() {
		return String.join("\n", methodSignatures); //$NON-NLS-1$
	}

	/**
	 * Get the newline-separated list of referenced types.
	 *
	 * @return referenced types
	 */
	public String getReferencedTypes() {
		return String.join("\n", referencedTypes); //$NON-NLS-1$
	}

	/**
	 * Get the newline-separated list of string literals.
	 *
	 * @return string literals
	 */
	public String getStringLiterals() {
		return String.join("\n", stringLiterals); //$NON-NLS-1$
	}

	/**
	 * Check if a main method was detected.
	 *
	 * @return true if a main method was found
	 */
	public boolean hasMainMethod() {
		return hasMainMethod;
	}

	/**
	 * Get the type documentation (Javadoc).
	 *
	 * @return the type documentation
	 */
	public String getTypeDocumentation() {
		return typeDocumentation;
	}
}
