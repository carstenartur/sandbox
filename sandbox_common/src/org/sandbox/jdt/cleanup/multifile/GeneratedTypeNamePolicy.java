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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import javax.lang.model.SourceVersion;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * Deterministic, fail-closed policy for generated nested type names and their
 * source references.
 *
 * <p>The policy deliberately rejects domain type collisions instead of inventing
 * numeric suffixes. Consumers can store {@link Assessment#fingerprint()} in an
 * immutable plan and must re-run the assessment before applying edits.</p>
 */
public final class GeneratedTypeNamePolicy {

	/** Result of reserving a prospective nested type name. */
	public record Assessment(boolean available, String reasonCode, String explanation, String fingerprint) {
		/** Validates the immutable result. */
		public Assessment {
			reasonCode= Objects.requireNonNull(reasonCode);
			explanation= Objects.requireNonNull(explanation);
			fingerprint= Objects.requireNonNull(fingerprint);
		}
	}

	/** Result of choosing an accessible and unambiguous generated type reference. */
	public record ReferenceResolution(boolean accessible, String qualifier, String reasonCode, String explanation) {
		/** Validates the immutable result. */
		public ReferenceResolution {
			qualifier= qualifier == null ? "" : qualifier; //$NON-NLS-1$
			reasonCode= Objects.requireNonNull(reasonCode);
			explanation= Objects.requireNonNull(explanation);
		}
	}

	private GeneratedTypeNamePolicy() {
	}

	/**
	 * Assesses whether {@code proposedName} can be generated as a nested type of
	 * {@code ownerType} without colliding with supported Java source namespaces.
	 */
	public static Assessment assessNestedType(CompilationUnit ownerRoot, AbstractTypeDeclaration ownerType,
			Collection<CompilationUnit> affectedRoots, String proposedName) {
		Objects.requireNonNull(ownerRoot);
		Objects.requireNonNull(ownerType);
		Objects.requireNonNull(affectedRoots);
		Objects.requireNonNull(proposedName);

		List<String> namespace= namespaceSnapshot(ownerRoot, ownerType, affectedRoots);
		String fingerprint= fingerprint(ownerIdentity(ownerType), proposedName, namespace);
		if (!SourceVersion.isIdentifier(proposedName) || SourceVersion.isKeyword(proposedName)) {
			return rejected("INVALID_GENERATED_TYPE_NAME", //$NON-NLS-1$
					"Generated type name '" + proposedName + "' is not a valid Java identifier.", fingerprint); //$NON-NLS-1$ //$NON-NLS-2$
		}

		List<String> collisions= namespace.stream()
				.filter(entry -> entry.substring(entry.indexOf(':') + 1).equals(proposedName))
				.sorted()
				.toList();
		if (!collisions.isEmpty()) {
			return rejected("GENERATED_NAME_COLLISION", //$NON-NLS-1$
					"Generated type name '" + proposedName + "' conflicts with " + String.join(", ", collisions) + '.', //$NON-NLS-1$ //$NON-NLS-2$
					fingerprint);
		}
		return new Assessment(true, "AVAILABLE", //$NON-NLS-1$
				"Generated nested type name '" + proposedName + "' is unambiguous in the affected source scope.", //$NON-NLS-1$ //$NON-NLS-2$
				fingerprint);
	}

	/**
	 * Chooses the shortest unambiguous source qualifier for one generated nested
	 * enum. Cross-package callers are rejected while the generated type remains
	 * package-private.
	 */
	public static ReferenceResolution resolveReference(CompilationUnit currentRoot, String ownerQualifiedName,
			String generatedTypeName, boolean ownerCompilationUnit) {
		Objects.requireNonNull(currentRoot);
		Objects.requireNonNull(ownerQualifiedName);
		Objects.requireNonNull(generatedTypeName);
		if (ownerCompilationUnit) {
			return new ReferenceResolution(true, generatedTypeName, "OWNER_TYPE_SCOPE", //$NON-NLS-1$
					"The generated type is referenced from its declaring compilation unit."); //$NON-NLS-1$
		}

		String ownerPackage= packageName(ownerQualifiedName);
		String currentPackage= packageName(currentRoot);
		if (!ownerPackage.equals(currentPackage)) {
			return new ReferenceResolution(false, "", "INACCESSIBLE_GENERATED_TYPE", //$NON-NLS-1$ //$NON-NLS-2$
					"The generated nested type is package-private and cannot be referenced from package '" //$NON-NLS-1$
					+ currentPackage + "'."); //$NON-NLS-1$
		}

		String ownerSimpleName= simpleName(ownerQualifiedName);
		String ownerReference= hasTypeNameConflict(currentRoot, ownerSimpleName)
				? ownerQualifiedName
				: ownerSimpleName;
		return new ReferenceResolution(true, ownerReference + '.' + generatedTypeName,
				ownerReference.equals(ownerQualifiedName) ? "QUALIFIED_TO_AVOID_COLLISION" : "SAME_PACKAGE_SIMPLE_NAME", //$NON-NLS-1$ //$NON-NLS-2$
				ownerReference.equals(ownerQualifiedName)
						? "The owner type is fully qualified because the caller contains a conflicting type name." //$NON-NLS-1$
						: "The owner type is referenced by its ordinary same-package simple name."); //$NON-NLS-1$
	}

	private static Assessment rejected(String reasonCode, String explanation, String fingerprint) {
		return new Assessment(false, reasonCode, explanation, fingerprint);
	}

	private static List<String> namespaceSnapshot(CompilationUnit ownerRoot, AbstractTypeDeclaration ownerType,
			Collection<CompilationUnit> affectedRoots) {
		Set<String> entries= new TreeSet<>();
		entries.add("OWNER:" + ownerType.getName().getIdentifier()); //$NON-NLS-1$
		for (Object importObject : ownerRoot.imports()) {
			ImportDeclaration declaration= (ImportDeclaration) importObject;
			if (!declaration.isStatic() && !declaration.isOnDemand()) {
				entries.add("IMPORT:" + simpleName(declaration.getName().getFullyQualifiedName())); //$NON-NLS-1$
			}
		}
		ownerType.accept(new ASTVisitor() {
			@Override
			public boolean visit(TypeDeclaration node) {
				addType(node);
				return true;
			}

			@Override
			public boolean visit(EnumDeclaration node) {
				addType(node);
				return true;
			}

			@Override
			public boolean visit(AnnotationTypeDeclaration node) {
				addType(node);
				return true;
			}

			@Override
			public boolean visit(RecordDeclaration node) {
				addType(node);
				return true;
			}

			@Override
			public boolean visit(FieldDeclaration node) {
				for (Object fragmentObject : node.fragments()) {
					VariableDeclarationFragment fragment= (VariableDeclarationFragment) fragmentObject;
					entries.add("FIELD:" + fragment.getName().getIdentifier()); //$NON-NLS-1$
				}
				return true;
			}

			@Override
			public boolean visit(MethodDeclaration node) {
				if (!node.isConstructor()) {
					entries.add("METHOD:" + node.getName().getIdentifier()); //$NON-NLS-1$
				}
				return true;
			}

			@Override
			public boolean visit(TypeParameter node) {
				entries.add("TYPE_PARAMETER:" + node.getName().getIdentifier()); //$NON-NLS-1$
				return true;
			}

			private void addType(AbstractTypeDeclaration node) {
				if (node != ownerType) {
					entries.add("TYPE:" + node.getName().getIdentifier()); //$NON-NLS-1$
				}
			}
		});

		ITypeBinding binding= resolveBinding(ownerType);
		Set<String> visitedBindings= new TreeSet<>();
		collectInheritedTypes(binding == null ? null : binding.getSuperclass(), entries, visitedBindings);
		if (binding != null) {
			for (ITypeBinding interfaceBinding : binding.getInterfaces()) {
				collectInheritedTypes(interfaceBinding, entries, visitedBindings);
			}
		}

		String ownerPackage= packageName(ownerRoot);
		List<CompilationUnit> sortedRoots= new ArrayList<>(affectedRoots);
		sortedRoots.sort(Comparator.comparing(GeneratedTypeNamePolicy::rootIdentity));
		for (CompilationUnit root : sortedRoots) {
			if (!ownerPackage.equals(packageName(root))) {
				continue;
			}
			for (Object typeObject : root.types()) {
				if (typeObject instanceof AbstractTypeDeclaration declaration && declaration != ownerType) {
					entries.add("PACKAGE_TYPE:" + declaration.getName().getIdentifier()); //$NON-NLS-1$
				}
			}
		}
		return List.copyOf(entries);
	}

	private static void collectInheritedTypes(ITypeBinding binding, Set<String> entries, Set<String> visited) {
		if (binding == null) {
			return;
		}
		ITypeBinding declaration= binding.getTypeDeclaration();
		String key= declaration == null ? null : declaration.getKey();
		if (key == null || !visited.add(key)) {
			return;
		}
		for (ITypeBinding nested : declaration.getDeclaredTypes()) {
			entries.add("INHERITED_TYPE:" + nested.getName()); //$NON-NLS-1$
		}
		collectInheritedTypes(declaration.getSuperclass(), entries, visited);
		for (ITypeBinding interfaceBinding : declaration.getInterfaces()) {
			collectInheritedTypes(interfaceBinding, entries, visited);
		}
	}

	private static boolean hasTypeNameConflict(CompilationUnit root, String simpleName) {
		for (Object importObject : root.imports()) {
			ImportDeclaration declaration= (ImportDeclaration) importObject;
			if (!declaration.isStatic() && !declaration.isOnDemand()
					&& simpleName.equals(simpleName(declaration.getName().getFullyQualifiedName()))) {
				return true;
			}
		}
		final boolean[] conflict= { false };
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(TypeDeclaration node) {
				return visitType(node);
			}

			@Override
			public boolean visit(EnumDeclaration node) {
				return visitType(node);
			}

			@Override
			public boolean visit(AnnotationTypeDeclaration node) {
				return visitType(node);
			}

			@Override
			public boolean visit(RecordDeclaration node) {
				return visitType(node);
			}

			@Override
			public boolean visit(TypeParameter node) {
				if (simpleName.equals(node.getName().getIdentifier())) {
					conflict[0]= true;
				}
				return !conflict[0];
			}

			private boolean visitType(AbstractTypeDeclaration declaration) {
				if (simpleName.equals(declaration.getName().getIdentifier())) {
					conflict[0]= true;
				}
				return !conflict[0];
			}
		});
		return conflict[0];
	}

	private static ITypeBinding resolveBinding(AbstractTypeDeclaration declaration) {
		if (declaration instanceof TypeDeclaration type) {
			return type.resolveBinding();
		}
		if (declaration instanceof EnumDeclaration enumeration) {
			return enumeration.resolveBinding();
		}
		if (declaration instanceof AnnotationTypeDeclaration annotation) {
			return annotation.resolveBinding();
		}
		if (declaration instanceof RecordDeclaration record) {
			return record.resolveBinding();
		}
		return null;
	}

	private static String ownerIdentity(AbstractTypeDeclaration ownerType) {
		ITypeBinding binding= resolveBinding(ownerType);
		if (binding != null && binding.getTypeDeclaration().getKey() != null) {
			return binding.getTypeDeclaration().getKey();
		}
		return ownerType.getName().getIdentifier() + '@' + ownerType.getStartPosition();
	}

	private static String rootIdentity(CompilationUnit root) {
		Object javaElement= root.getJavaElement();
		return javaElement == null ? packageName(root) + '@' + root.getStartPosition() : javaElement.toString();
	}

	private static String packageName(CompilationUnit root) {
		return root.getPackage() == null ? "" : root.getPackage().getName().getFullyQualifiedName(); //$NON-NLS-1$
	}

	private static String packageName(String qualifiedTypeName) {
		int separator= qualifiedTypeName.lastIndexOf('.');
		return separator < 0 ? "" : qualifiedTypeName.substring(0, separator); //$NON-NLS-1$
	}

	private static String simpleName(String qualifiedName) {
		int separator= qualifiedName.lastIndexOf('.');
		return separator < 0 ? qualifiedName : qualifiedName.substring(separator + 1);
	}

	private static String fingerprint(String ownerIdentity, String proposedName, List<String> namespace) {
		try {
			MessageDigest digest= MessageDigest.getInstance("SHA-256"); //$NON-NLS-1$
			digest.update(ownerIdentity.getBytes(StandardCharsets.UTF_8));
			digest.update((byte) 0);
			digest.update(proposedName.getBytes(StandardCharsets.UTF_8));
			for (String entry : namespace) {
				digest.update((byte) 0);
				digest.update(entry.getBytes(StandardCharsets.UTF_8));
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is unavailable", e); //$NON-NLS-1$
		}
	}
}
