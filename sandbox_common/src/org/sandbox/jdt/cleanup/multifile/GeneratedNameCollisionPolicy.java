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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * Conservative, deterministic generated-name collision policy shared by
 * coordinated cleanups.
 *
 * <p>Domain type names are rejected rather than silently suffixed. The caller
 * must run this assessment against every affected compilation unit immediately
 * before creating changes so imports, local types and stale working-copy edits
 * participate in the decision.</p>
 */
public final class GeneratedNameCollisionPolicy {

	/** Namespace in which a prospective name is already occupied. */
	public enum Namespace {
		TYPE_DECLARATION,
		INHERITED_TYPE,
		IMPORT,
		MEMBER,
		TYPE_PARAMETER
	}

	/** One deterministic collision finding. */
	public record Collision(Namespace namespace, String compilationUnit, String description) {
		public Collision {
			namespace= Objects.requireNonNull(namespace);
			compilationUnit= Objects.requireNonNull(compilationUnit);
			description= Objects.requireNonNull(description);
		}
	}

	/** Complete assessment for one prospective generated name in one source unit. */
	public record Assessment(String generatedName, List<Collision> collisions) {
		public Assessment {
			generatedName= Objects.requireNonNull(generatedName);
			List<Collision> normalized= new ArrayList<>(collisions == null ? List.of() : collisions);
			normalized.sort(Comparator.comparing((Collision collision) -> collision.namespace().name())
					.thenComparing(Collision::compilationUnit).thenComparing(Collision::description));
			collisions= List.copyOf(normalized);
		}

		/** Whether the name remains available in this compilation unit. */
		public boolean available() {
			return collisions.isEmpty();
		}

		/** Stable explanation suitable for preview and stale-plan diagnostics. */
		public String explanation() {
			if (available()) {
				return "Generated name " + generatedName + " is available."; //$NON-NLS-1$ //$NON-NLS-2$
			}
			return collisions.stream()
					.map(collision -> collision.namespace() + " in " + collision.compilationUnit() //$NON-NLS-1$
							+ ": " + collision.description()) //$NON-NLS-1$
					.collect(java.util.stream.Collectors.joining("; ")); //$NON-NLS-1$
		}
	}

	private GeneratedNameCollisionPolicy() {
	}

	/**
	 * Assesses one fresh compilation-unit AST against a planned generated name.
	 *
	 * @param root fresh AST used to create the current local fix
	 * @param ownerTypeBindingKey binding key of the type that will own the artifact
	 * @param ownerTypeQualifiedName qualified source name of the owning type
	 * @param generatedName prospective simple name
	 * @return deterministic collision assessment
	 */
	public static Assessment assess(CompilationUnit root, String ownerTypeBindingKey,
			String ownerTypeQualifiedName, String generatedName) {
		Objects.requireNonNull(root);
		Objects.requireNonNull(ownerTypeQualifiedName);
		Objects.requireNonNull(generatedName);
		String unitName= compilationUnitName(root);
		List<Collision> collisions= new ArrayList<>();
		Set<String> identities= new HashSet<>();

		for (Object importObject : root.imports()) {
			ImportDeclaration declaration= (ImportDeclaration) importObject;
			if (!declaration.isOnDemand()
					&& generatedName.equals(declaration.getName().getFullyQualifiedName()
							.substring(declaration.getName().getFullyQualifiedName().lastIndexOf('.') + 1))) {
				add(collisions, identities, Namespace.IMPORT, unitName,
						"explicit import " + declaration.getName().getFullyQualifiedName()); //$NON-NLS-1$
			}
		}

		root.accept(new ASTVisitor() {
			@Override
			public void preVisit(ASTNode node) {
				if (node instanceof AbstractTypeDeclaration declaration) {
					SimpleName name= declaration.getName();
					if (name != null && generatedName.equals(name.getIdentifier())) {
						add(collisions, identities, Namespace.TYPE_DECLARATION, unitName,
								"type declaration " + name.getIdentifier()); //$NON-NLS-1$
					}
					if (isOwner(declaration, ownerTypeBindingKey, ownerTypeQualifiedName, root)) {
						assessOwnerMembers(declaration, generatedName, unitName, collisions, identities);
						assessInheritedTypes(declaration.resolveBinding(), generatedName, unitName,
								collisions, identities, new HashSet<>());
					}
				} else if (node instanceof TypeParameter parameter
						&& generatedName.equals(parameter.getName().getIdentifier())) {
					add(collisions, identities, Namespace.TYPE_PARAMETER, unitName,
							"type parameter " + parameter.getName().getIdentifier()); //$NON-NLS-1$
				}
			}
		});
		return new Assessment(generatedName, collisions);
	}

	private static void assessOwnerMembers(AbstractTypeDeclaration owner, String generatedName,
			String unitName, List<Collision> collisions, Set<String> identities) {
		for (Object bodyObject : owner.bodyDeclarations()) {
			if (bodyObject instanceof FieldDeclaration field) {
				for (Object fragmentObject : field.fragments()) {
					VariableDeclarationFragment fragment= (VariableDeclarationFragment) fragmentObject;
					if (generatedName.equals(fragment.getName().getIdentifier())) {
						add(collisions, identities, Namespace.MEMBER, unitName,
								"field " + fragment.getName().getIdentifier()); //$NON-NLS-1$
					}
				}
			} else if (bodyObject instanceof MethodDeclaration method
					&& generatedName.equals(method.getName().getIdentifier())) {
				add(collisions, identities, Namespace.MEMBER, unitName,
						"method " + method.getName().getIdentifier()); //$NON-NLS-1$
			}
		}
		if (owner instanceof TypeDeclaration type) {
			for (Object parameterObject : type.typeParameters()) {
				TypeParameter parameter= (TypeParameter) parameterObject;
				if (generatedName.equals(parameter.getName().getIdentifier())) {
					add(collisions, identities, Namespace.TYPE_PARAMETER, unitName,
							"owner type parameter " + parameter.getName().getIdentifier()); //$NON-NLS-1$
				}
			}
		}
	}

	private static void assessInheritedTypes(ITypeBinding binding, String generatedName, String unitName,
			List<Collision> collisions, Set<String> identities, Set<String> visited) {
		if (binding == null) {
			return;
		}
		String key= binding.getTypeDeclaration().getKey();
		if (key != null && !visited.add(key)) {
			return;
		}
		for (ITypeBinding nested : binding.getDeclaredTypes()) {
			if (generatedName.equals(nested.getName())) {
				add(collisions, identities, Namespace.INHERITED_TYPE, unitName,
						"nested type inherited from " + binding.getQualifiedName()); //$NON-NLS-1$
			}
		}
		assessInheritedTypes(binding.getSuperclass(), generatedName, unitName, collisions, identities, visited);
		for (ITypeBinding interfaceBinding : binding.getInterfaces()) {
			assessInheritedTypes(interfaceBinding, generatedName, unitName, collisions, identities, visited);
		}
	}

	private static boolean isOwner(AbstractTypeDeclaration declaration, String ownerTypeBindingKey,
			String ownerTypeQualifiedName, CompilationUnit root) {
		ITypeBinding binding= declaration.resolveBinding();
		if (binding != null && ownerTypeBindingKey != null
				&& ownerTypeBindingKey.equals(binding.getTypeDeclaration().getKey())) {
			return true;
		}
		if (!(declaration.getParent() instanceof CompilationUnit)) {
			return false;
		}
		String packageName= root.getPackage() == null ? "" //$NON-NLS-1$
				: root.getPackage().getName().getFullyQualifiedName();
		String qualifiedName= packageName.isEmpty() ? declaration.getName().getIdentifier()
				: packageName + '.' + declaration.getName().getIdentifier();
		return ownerTypeQualifiedName.equals(qualifiedName);
	}

	private static String compilationUnitName(CompilationUnit root) {
		if (root.getJavaElement() instanceof ICompilationUnit unit) {
			return unit.getElementName();
		}
		return "<unknown compilation unit>"; //$NON-NLS-1$
	}

	private static void add(List<Collision> collisions, Set<String> identities, Namespace namespace,
			String unitName, String description) {
		String identity= namespace + "\u0000" + unitName + "\u0000" + description; //$NON-NLS-1$ //$NON-NLS-2$
		if (identities.add(identity)) {
			collisions.add(new Collision(namespace, unitName, description));
		}
	}
}
