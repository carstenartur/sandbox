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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.lang.model.SourceVersion;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * Deterministically validates names proposed by multi-file cleanups.
 *
 * <p>The allocator deliberately does not invent numeric suffixes for generated
 * domain types. A requested name is either available or rejected with stable,
 * structured collision information that can be shown in previews and reused
 * for stale-plan validation.</p>
 */
public final class GeneratedNameAllocator {

	/** Java namespaces relevant to generated nested type references. */
	public enum Namespace {
		/** Existing or prospective type declaration. */
		TYPE,
		/** Field, enum constant, method or annotation member. */
		MEMBER,
		/** Parameter, local variable or other local declaration. */
		LOCAL,
		/** Explicit import using the requested simple name. */
		IMPORT,
		/** Another cleanup candidate reserved the same generated name. */
		PLANNED
	}

	/** Identity and target of one requested nested type. */
	public record NestedTypeRequest(String requestId, String ownerCompilationUnitHandle,
			String ownerTypeBindingKey, String ownerTypeQualifiedName, String requestedName) {

		/** Validates and normalizes request identity. */
		public NestedTypeRequest {
			requestId= requireText(requestId, "requestId"); //$NON-NLS-1$
			ownerCompilationUnitHandle= normalize(ownerCompilationUnitHandle);
			ownerTypeBindingKey= normalize(ownerTypeBindingKey);
			ownerTypeQualifiedName= normalize(ownerTypeQualifiedName);
			requestedName= requireText(requestedName, "requestedName"); //$NON-NLS-1$
			if (ownerTypeBindingKey.isEmpty() && ownerTypeQualifiedName.isEmpty()) {
				throw new IllegalArgumentException(
						"Either ownerTypeBindingKey or ownerTypeQualifiedName must be provided."); //$NON-NLS-1$
			}
			if (!SourceVersion.isIdentifier(requestedName) || SourceVersion.isKeyword(requestedName)) {
				throw new IllegalArgumentException("Not a Java identifier: " + requestedName); //$NON-NLS-1$
			}
		}
	}

	/** One declaration or reservation conflicting with a requested name. */
	public record Collision(Namespace namespace, String compilationUnitHandle,
			String declarationKind, String context) {

		/** Normalizes diagnostic fields. */
		public Collision {
			namespace= Objects.requireNonNull(namespace, "namespace"); //$NON-NLS-1$
			compilationUnitHandle= normalize(compilationUnitHandle);
			declarationKind= requireText(declarationKind, "declarationKind"); //$NON-NLS-1$
			context= requireText(context, "context"); //$NON-NLS-1$
		}
	}

	/** Result for one requested generated name. */
	public record Allocation(NestedTypeRequest request, List<Collision> collisions) {

		/** Defensively copies and sorts collision diagnostics. */
		public Allocation {
			request= Objects.requireNonNull(request, "request"); //$NON-NLS-1$
			collisions= collisions.stream().map(collision -> Objects.requireNonNull(collision, "collision")) //$NON-NLS-1$
					.sorted(COLLISION_ORDER).toList();
		}

		/** Returns whether the requested name can be generated unchanged. */
		public boolean available() {
			return collisions.isEmpty();
		}

		/** Returns a stable human-readable explanation of all collisions. */
		public String diagnosticMessage() {
			return collisions.stream()
					.map(collision -> collision.namespace().name().toLowerCase(Locale.ROOT) + " " //$NON-NLS-1$
							+ collision.declarationKind() + " (" + collision.context() + ")") //$NON-NLS-1$ //$NON-NLS-2$
					.collect(Collectors.joining("; ")); //$NON-NLS-1$
		}
	}

	private record Owner(CompilationUnit root, AbstractTypeDeclaration type) {
	}

	private static final Comparator<Collision> COLLISION_ORDER= Comparator
			.comparing((Collision collision) -> collision.namespace().ordinal())
			.thenComparing(Collision::compilationUnitHandle)
			.thenComparing(Collision::declarationKind)
			.thenComparing(Collision::context);

	private GeneratedNameAllocator() {
	}

	/**
	 * Validates nested type requests against the supplied compilation units and
	 * against all other prospective requests.
	 *
	 * @return allocations keyed by request ID in deterministic request-ID order
	 */
	public static Map<String, Allocation> allocateNestedTypes(Collection<CompilationUnit> roots,
			Collection<NestedTypeRequest> requests) {
		Objects.requireNonNull(roots, "roots"); //$NON-NLS-1$
		Objects.requireNonNull(requests, "requests"); //$NON-NLS-1$

		List<CompilationUnit> orderedRoots= roots.stream()
				.map(root -> Objects.requireNonNull(root, "root")) //$NON-NLS-1$
				.sorted(Comparator.comparing(GeneratedNameAllocator::compilationUnitKey)).toList();
		List<NestedTypeRequest> orderedRequests= requests.stream()
				.map(request -> Objects.requireNonNull(request, "request")) //$NON-NLS-1$
				.sorted(Comparator.comparing(NestedTypeRequest::requestId)).toList();
		ensureUniqueRequestIds(orderedRequests);

		Map<String, List<NestedTypeRequest>> reservationGroups= new LinkedHashMap<>();
		for (NestedTypeRequest request : orderedRequests) {
			reservationGroups.computeIfAbsent(reservationKey(request), ignored -> new ArrayList<>()).add(request);
		}

		Map<String, Allocation> allocations= new LinkedHashMap<>();
		for (NestedTypeRequest request : orderedRequests) {
			List<Collision> collisions= new ArrayList<>();
			addProspectiveCollisions(request, reservationGroups.get(reservationKey(request)), collisions);
			Owner owner= findOwner(orderedRoots, request);
			if (owner == null) {
				collisions.add(new Collision(Namespace.TYPE, request.ownerCompilationUnitHandle(),
						"owner type", "owner declaration could not be resolved")); //$NON-NLS-1$ //$NON-NLS-2$
			} else {
				collectImportCollisions(owner, request, collisions);
				collectDeclarationCollisions(owner, request, collisions);
			}
			allocations.put(request.requestId(), new Allocation(request, collisions));
		}
		return Collections.unmodifiableMap(allocations);
	}

	private static void ensureUniqueRequestIds(List<NestedTypeRequest> requests) {
		Set<String> ids= new HashSet<>();
		for (NestedTypeRequest request : requests) {
			if (!ids.add(request.requestId())) {
				throw new IllegalArgumentException("Duplicate generated-name request ID: " + request.requestId()); //$NON-NLS-1$
			}
		}
	}

	private static void addProspectiveCollisions(NestedTypeRequest request, List<NestedTypeRequest> group,
			List<Collision> collisions) {
		if (group == null || group.size() < 2) {
			return;
		}
		String competingIds= group.stream().map(NestedTypeRequest::requestId)
				.filter(candidateId -> !candidateId.equals(request.requestId())).sorted()
				.collect(Collectors.joining(", ")); //$NON-NLS-1$
		collisions.add(new Collision(Namespace.PLANNED, request.ownerCompilationUnitHandle(),
				"nested type reservation", "also requested by " + competingIds)); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static Owner findOwner(List<CompilationUnit> roots, NestedTypeRequest request) {
		if (!request.ownerCompilationUnitHandle().isEmpty()) {
			for (CompilationUnit root : roots) {
				if (request.ownerCompilationUnitHandle().equals(compilationUnitHandle(root))) {
					AbstractTypeDeclaration type= findOwnerType(root, request);
					if (type != null) {
						return new Owner(root, type);
					}
				}
			}
		}
		for (CompilationUnit root : roots) {
			AbstractTypeDeclaration type= findOwnerType(root, request);
			if (type != null) {
				return new Owner(root, type);
			}
		}
		return null;
	}

	private static AbstractTypeDeclaration findOwnerType(CompilationUnit root, NestedTypeRequest request) {
		AbstractTypeDeclaration[] result= new AbstractTypeDeclaration[1];
		root.accept(new ASTVisitor() {
			@Override
			public void preVisit(ASTNode node) {
				if (result[0] == null && node instanceof AbstractTypeDeclaration type && matchesOwner(root, type, request)) {
					result[0]= type;
				}
			}
		});
		return result[0];
	}

	private static boolean matchesOwner(CompilationUnit root, AbstractTypeDeclaration type,
			NestedTypeRequest request) {
		ITypeBinding binding= type.resolveBinding();
		ITypeBinding declaration= binding == null ? null : binding.getTypeDeclaration();
		if (declaration != null && !request.ownerTypeBindingKey().isEmpty()
				&& request.ownerTypeBindingKey().equals(declaration.getKey())) {
			return true;
		}
		String qualifiedName= declaration == null ? qualifiedName(root, type) : declaration.getQualifiedName();
		return !request.ownerTypeQualifiedName().isEmpty()
				&& request.ownerTypeQualifiedName().equals(qualifiedName);
	}

	private static void collectImportCollisions(Owner owner, NestedTypeRequest request,
			List<Collision> collisions) {
		for (Object importObject : owner.root().imports()) {
			ImportDeclaration importDeclaration= (ImportDeclaration) importObject;
			if (importDeclaration.isOnDemand()) {
				continue;
			}
			String importedName= importDeclaration.getName().getFullyQualifiedName();
			int separator= importedName.lastIndexOf('.');
			String simpleName= separator < 0 ? importedName : importedName.substring(separator + 1);
			if (request.requestedName().equals(simpleName)) {
				collisions.add(collision(Namespace.IMPORT, owner.root(), request,
						importDeclaration.isStatic() ? "static import" : "type import", //$NON-NLS-1$ //$NON-NLS-2$
						importedName, importDeclaration));
			}
		}
	}

	private static void collectDeclarationCollisions(Owner owner, NestedTypeRequest request,
			List<Collision> collisions) {
		owner.type().accept(new ASTVisitor() {
			@Override
			public void preVisit(ASTNode node) {
				String requestedName= request.requestedName();
				if (node instanceof AbstractTypeDeclaration type
						&& requestedName.equals(type.getName().getIdentifier())) {
					String kind= type == owner.type() ? "owner type" : "type declaration"; //$NON-NLS-1$ //$NON-NLS-2$
					collisions.add(collision(Namespace.TYPE, owner.root(), request, kind,
							qualifiedName(owner.root(), type), type));
				} else if (node instanceof TypeParameter parameter
						&& requestedName.equals(parameter.getName().getIdentifier())) {
					collisions.add(collision(Namespace.TYPE, owner.root(), request,
							"type parameter", parameter.getName().getIdentifier(), parameter)); //$NON-NLS-1$
				} else if (node instanceof FieldDeclaration field) {
					for (Object fragmentObject : field.fragments()) {
						VariableDeclarationFragment fragment= (VariableDeclarationFragment) fragmentObject;
						if (requestedName.equals(fragment.getName().getIdentifier())) {
							collisions.add(collision(Namespace.MEMBER, owner.root(), request,
									"field", fragment.getName().getIdentifier(), fragment)); //$NON-NLS-1$
						}
					}
				} else if (node instanceof EnumConstantDeclaration enumConstant
						&& requestedName.equals(enumConstant.getName().getIdentifier())) {
					collisions.add(collision(Namespace.MEMBER, owner.root(), request,
							"enum constant", enumConstant.getName().getIdentifier(), enumConstant)); //$NON-NLS-1$
				} else if (node instanceof MethodDeclaration method && !method.isConstructor()
						&& requestedName.equals(method.getName().getIdentifier())) {
					collisions.add(collision(Namespace.MEMBER, owner.root(), request,
							"method", method.getName().getIdentifier(), method)); //$NON-NLS-1$
				} else if (node instanceof AnnotationTypeMemberDeclaration member
						&& requestedName.equals(member.getName().getIdentifier())) {
					collisions.add(collision(Namespace.MEMBER, owner.root(), request,
							"annotation member", member.getName().getIdentifier(), member)); //$NON-NLS-1$
				} else if (node instanceof SingleVariableDeclaration variable
						&& requestedName.equals(variable.getName().getIdentifier())) {
					collisions.add(collision(Namespace.LOCAL, owner.root(), request,
							"parameter or local variable", variable.getName().getIdentifier(), variable)); //$NON-NLS-1$
				} else if (node instanceof VariableDeclarationFragment fragment
						&& !(fragment.getParent() instanceof FieldDeclaration)
						&& requestedName.equals(fragment.getName().getIdentifier())) {
					collisions.add(collision(Namespace.LOCAL, owner.root(), request,
							"local variable", fragment.getName().getIdentifier(), fragment)); //$NON-NLS-1$
				}
			}
		});
	}

	private static Collision collision(Namespace namespace, CompilationUnit root, NestedTypeRequest request,
			String declarationKind, String detail, ASTNode node) {
		String handle= compilationUnitHandle(root);
		if (handle.isEmpty()) {
			handle= request.ownerCompilationUnitHandle();
		}
		int line= root.getLineNumber(node.getStartPosition());
		String position= line > 0 ? "line " + line : "offset " + node.getStartPosition(); //$NON-NLS-1$ //$NON-NLS-2$
		return new Collision(namespace, handle, declarationKind, detail + " at " + position); //$NON-NLS-1$
	}

	private static String reservationKey(NestedTypeRequest request) {
		String ownerIdentity= !request.ownerTypeBindingKey().isEmpty()
				? request.ownerTypeBindingKey()
				: request.ownerCompilationUnitHandle() + ':' + request.ownerTypeQualifiedName();
		return ownerIdentity + '\u0000' + request.requestedName();
	}

	private static String compilationUnitKey(CompilationUnit root) {
		String handle= compilationUnitHandle(root);
		if (!handle.isEmpty()) {
			return handle;
		}
		String packageName= root.getPackage() == null
				? "" //$NON-NLS-1$
				: root.getPackage().getName().getFullyQualifiedName();
		String types= root.types().stream()
				.filter(AbstractTypeDeclaration.class::isInstance)
				.map(AbstractTypeDeclaration.class::cast)
				.map(type -> type.getName().getIdentifier()).sorted().collect(Collectors.joining(",")); //$NON-NLS-1$
		return packageName + ':' + types;
	}

	private static String compilationUnitHandle(CompilationUnit root) {
		return root.getJavaElement() instanceof ICompilationUnit unit
				? unit.getPrimary().getHandleIdentifier()
				: ""; //$NON-NLS-1$
	}

	private static String qualifiedName(CompilationUnit root, AbstractTypeDeclaration type) {
		ITypeBinding binding= type.resolveBinding();
		if (binding != null && !binding.getTypeDeclaration().getQualifiedName().isEmpty()) {
			return binding.getTypeDeclaration().getQualifiedName();
		}
		List<String> typeNames= new ArrayList<>();
		ASTNode current= type;
		while (current != null) {
			if (current instanceof AbstractTypeDeclaration declaration) {
				typeNames.add(0, declaration.getName().getIdentifier());
			}
			current= current.getParent();
		}
		String packageName= root.getPackage() == null
				? "" //$NON-NLS-1$
				: root.getPackage().getName().getFullyQualifiedName();
		String localName= String.join(".", typeNames); //$NON-NLS-1$
		return packageName.isEmpty() ? localName : packageName + '.' + localName;
	}

	private static String normalize(String value) {
		return value == null ? "" : value.strip(); //$NON-NLS-1$
	}

	private static String requireText(String value, String fieldName) {
		String normalized= normalize(value);
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException(fieldName + " must not be blank."); //$NON-NLS-1$
		}
		return normalized;
	}
}