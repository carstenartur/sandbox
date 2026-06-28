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
package org.sandbox.jdt.triggerpattern.internal;

import java.util.List;

import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NameQualifiedType;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.WildcardType;

/**
 * FQN-aware variant of {@link PlaceholderAstMatcher}.
 *
 * <p>DSL snippets should express concrete APIs using fully qualified names. This
 * matcher accepts source code that uses the same API via imports and simple
 * names. It applies that principle to type references, parameterized types,
 * arrays, constructors, type literals, casts and method receivers.</p>
 */
public final class FqnAwarePlaceholderAstMatcher extends PlaceholderAstMatcher {

	private final ASTMatcher structuralMatcher = new ASTMatcher();

	@Override
	public boolean match(SimpleType patternNode, Object other) {
		if (super.match(patternNode, other)) {
			return true;
		}
		return other instanceof SimpleType otherType
				&& namesEquivalent(patternNode.getName(), otherType.getName());
	}

	@Override
	public boolean match(QualifiedType patternNode, Object other) {
		return other instanceof Type otherType && typesEquivalent(patternNode, otherType);
	}

	@Override
	public boolean match(NameQualifiedType patternNode, Object other) {
		return other instanceof Type otherType && typesEquivalent(patternNode, otherType);
	}

	@Override
	public boolean match(ParameterizedType patternNode, Object other) {
		return other instanceof Type otherType && typesEquivalent(patternNode, otherType);
	}

	@Override
	public boolean match(ArrayType patternNode, Object other) {
		return other instanceof Type otherType && typesEquivalent(patternNode, otherType);
	}

	@Override
	public boolean match(PrimitiveType patternNode, Object other) {
		return other instanceof PrimitiveType otherType
				&& patternNode.getPrimitiveTypeCode().equals(otherType.getPrimitiveTypeCode());
	}

	@Override
	public boolean match(WildcardType patternNode, Object other) {
		if (!(other instanceof WildcardType otherType)) {
			return false;
		}
		if (patternNode.isUpperBound() != otherType.isUpperBound()) {
			return false;
		}
		return typesEquivalent(patternNode.getBound(), otherType.getBound());
	}

	@Override
	public boolean match(TypeLiteral patternNode, Object other) {
		return other instanceof TypeLiteral otherLiteral
				&& typesEquivalent(patternNode.getType(), otherLiteral.getType());
	}

	@Override
	public boolean match(CastExpression patternNode, Object other) {
		return other instanceof CastExpression otherCast
				&& typesEquivalent(patternNode.getType(), otherCast.getType())
				&& patternNode.getExpression().subtreeMatch(this, otherCast.getExpression());
	}

	@Override
	public boolean match(ClassInstanceCreation patternNode, Object other) {
		if (!(other instanceof ClassInstanceCreation otherCreation)) {
			return false;
		}
		if (!typesEquivalent(patternNode.getType(), otherCreation.getType())) {
			return false;
		}
		@SuppressWarnings("unchecked")
		List<Type> patternTypeArgs = patternNode.typeArguments();
		@SuppressWarnings("unchecked")
		List<Type> otherTypeArgs = otherCreation.typeArguments();
		if (!typeListsEquivalent(patternTypeArgs, otherTypeArgs)) {
			return false;
		}
		if (!subtreeNullable(patternNode.getExpression(), otherCreation.getExpression())) {
			return false;
		}
		@SuppressWarnings("unchecked")
		List<Expression> patternArgs = patternNode.arguments();
		@SuppressWarnings("unchecked")
		List<Expression> otherArgs = otherCreation.arguments();
		return expressionListsEquivalent(patternArgs, otherArgs);
	}

	@Override
	public boolean match(MethodInvocation patternNode, Object other) {
		if (!(other instanceof MethodInvocation otherInvocation)) {
			return false;
		}
		if (!patternNode.getName().subtreeMatch(this, otherInvocation.getName())) {
			return false;
		}
		if (!receiversEquivalent(patternNode.getExpression(), otherInvocation.getExpression())) {
			return false;
		}
		if (!methodBindingCompatible(patternNode, otherInvocation)) {
			return false;
		}
		@SuppressWarnings("unchecked")
		List<Type> patternTypeArgs = patternNode.typeArguments();
		@SuppressWarnings("unchecked")
		List<Type> otherTypeArgs = otherInvocation.typeArguments();
		if (!typeListsEquivalent(patternTypeArgs, otherTypeArgs)) {
			return false;
		}
		@SuppressWarnings("unchecked")
		List<Expression> patternArgs = patternNode.arguments();
		@SuppressWarnings("unchecked")
		List<Expression> otherArgs = otherInvocation.arguments();
		return expressionListsEquivalent(patternArgs, otherArgs);
	}

	private boolean typesEquivalent(Type patternType, Type sourceType) {
		if (patternType == null) {
			return sourceType == null;
		}
		if (sourceType == null) {
			return false;
		}
		if (patternType.subtreeMatch(structuralMatcher, sourceType)) {
			return true;
		}
		if (patternType instanceof SimpleType patternSimple && sourceType instanceof SimpleType sourceSimple) {
			return namesEquivalent(patternSimple.getName(), sourceSimple.getName());
		}
		if (patternType instanceof ParameterizedType patternParam && sourceType instanceof ParameterizedType sourceParam) {
			return typesEquivalent(patternParam.getType(), sourceParam.getType())
					&& typeListsEquivalent(patternParam.typeArguments(), sourceParam.typeArguments());
		}
		if (patternType instanceof ParameterizedType patternParam) {
			return typesEquivalent(patternParam.getType(), sourceType);
		}
		if (sourceType instanceof ParameterizedType sourceParam) {
			return typesEquivalent(patternType, sourceParam.getType());
		}
		if (patternType instanceof ArrayType patternArray && sourceType instanceof ArrayType sourceArray) {
			return patternArray.getDimensions() == sourceArray.getDimensions()
					&& typesEquivalent(patternArray.getElementType(), sourceArray.getElementType());
		}
		String patternIdentity = typeIdentity(patternType);
		String sourceIdentity = typeIdentity(sourceType);
		return patternIdentity != null && patternIdentity.equals(sourceIdentity);
	}

	private boolean typeListsEquivalent(List<?> patternTypes, List<?> sourceTypes) {
		if (patternTypes.size() != sourceTypes.size()) {
			return false;
		}
		for (int i = 0; i < patternTypes.size(); i++) {
			if (!typesEquivalent((Type) patternTypes.get(i), (Type) sourceTypes.get(i))) {
				return false;
			}
		}
		return true;
	}

	private boolean expressionListsEquivalent(List<Expression> patternArgs, List<Expression> sourceArgs) {
		if (patternArgs.size() != sourceArgs.size()) {
			return false;
		}
		for (int i = 0; i < patternArgs.size(); i++) {
			if (!patternArgs.get(i).subtreeMatch(this, sourceArgs.get(i))) {
				return false;
			}
		}
		return true;
	}

	private boolean subtreeNullable(ASTNode patternNode, ASTNode sourceNode) {
		if (patternNode == null) {
			return sourceNode == null;
		}
		return patternNode.subtreeMatch(this, sourceNode);
	}

	private boolean receiversEquivalent(Expression patternExpr, Expression sourceExpr) {
		if (patternExpr == null) {
			return sourceExpr == null;
		}
		if (sourceExpr == null) {
			return false;
		}
		if (patternExpr.subtreeMatch(this, sourceExpr)) {
			return true;
		}
		return patternExpr instanceof Name patternName
				&& sourceExpr instanceof Name sourceName
				&& namesEquivalent(patternName, sourceName);
	}

	private boolean methodBindingCompatible(MethodInvocation patternNode, MethodInvocation sourceNode) {
		if (!(patternNode.getExpression() instanceof Name patternReceiver)
				|| !isFullyQualified(patternReceiver.getFullyQualifiedName())) {
			return true;
		}
		IMethodBinding binding = sourceNode.resolveMethodBinding();
		if (binding == null || binding.getDeclaringClass() == null) {
			return true;
		}
		String declaringClass = binding.getDeclaringClass().getQualifiedName();
		return patternReceiver.getFullyQualifiedName().equals(declaringClass);
	}

	private boolean namesEquivalent(Name patternName, Name sourceName) {
		if (patternName.subtreeMatch(structuralMatcher, sourceName)) {
			return true;
		}
		if (patternName instanceof SimpleName patternSimple && patternSimple.getIdentifier().startsWith("$")) { //$NON-NLS-1$
			return patternName.subtreeMatch(this, sourceName);
		}
		String patternFqn = patternName.getFullyQualifiedName();
		String sourceFqn = sourceName.getFullyQualifiedName();
		if (patternFqn.equals(sourceFqn)) {
			return true;
		}
		if (isFullyQualified(patternFqn) && sourceName instanceof SimpleName sourceSimple) {
			return simpleNameResolvesTo(sourceSimple, patternFqn);
		}
		if (isFullyQualified(sourceFqn) && patternName instanceof SimpleName patternSimple) {
			return simpleNameResolvesTo(patternSimple, sourceFqn);
		}
		return false;
	}

	private String typeIdentity(Type type) {
		if (type == null) {
			return null;
		}
		if (type instanceof SimpleType simpleType) {
			return nameIdentity(simpleType.getName());
		}
		if (type instanceof ParameterizedType parameterizedType) {
			String raw = typeIdentity(parameterizedType.getType());
			@SuppressWarnings("unchecked")
			List<Type> args = parameterizedType.typeArguments();
			if (args.isEmpty()) {
				return raw;
			}
			StringBuilder sb = new StringBuilder(raw == null ? "" : raw); //$NON-NLS-1$
			sb.append('<');
			for (int i = 0; i < args.size(); i++) {
				if (i > 0) {
					sb.append(',');
				}
				sb.append(typeIdentity(args.get(i)));
			}
			sb.append('>');
			return sb.toString();
		}
		if (type instanceof ArrayType arrayType) {
			return typeIdentity(arrayType.getElementType()) + "[]".repeat(arrayType.getDimensions()); //$NON-NLS-1$
		}
		if (type instanceof PrimitiveType primitiveType) {
			return primitiveType.getPrimitiveTypeCode().toString();
		}
		if (type instanceof WildcardType wildcardType) {
			if (wildcardType.getBound() == null) {
				return "?"; //$NON-NLS-1$
			}
			return wildcardType.isUpperBound()
					? "? extends " + typeIdentity(wildcardType.getBound()) //$NON-NLS-1$
					: "? super " + typeIdentity(wildcardType.getBound()); //$NON-NLS-1$
		}
		return type.toString();
	}

	private String nameIdentity(Name name) {
		if (name instanceof SimpleName simpleName) {
			String resolved = resolveSimpleNameViaImports(simpleName);
			return resolved != null ? resolved : simpleName.getIdentifier();
		}
		return name.getFullyQualifiedName();
	}

	private boolean simpleNameResolvesTo(SimpleName simpleName, String expectedFqn) {
		String identifier = simpleName.getIdentifier();
		int lastDot = expectedFqn.lastIndexOf('.');
		String expectedSimpleName = lastDot >= 0 ? expectedFqn.substring(lastDot + 1) : expectedFqn;
		if (!identifier.equals(expectedSimpleName)) {
			return false;
		}
		String resolved = resolveSimpleNameViaImports(simpleName);
		if (expectedFqn.equals(resolved)) {
			return true;
		}
		if (expectedFqn.startsWith("java.lang.")) { //$NON-NLS-1$
			return true;
		}
		CompilationUnit cu = findCompilationUnit(simpleName);
		if (cu != null && cu.getPackage() != null) {
			String packageName = cu.getPackage().getName().getFullyQualifiedName();
			if (expectedFqn.equals(packageName + '.' + identifier)) {
				return true;
			}
		}
		return importOnDemandCanResolve(simpleName, expectedFqn);
	}

	private String resolveSimpleNameViaImports(SimpleName simpleName) {
		CompilationUnit cu = findCompilationUnit(simpleName);
		if (cu == null) {
			return null;
		}
		@SuppressWarnings("unchecked")
		List<ImportDeclaration> imports = cu.imports();
		for (ImportDeclaration importDecl : imports) {
			if (importDecl.isStatic() || importDecl.isOnDemand()) {
				continue;
			}
			String importFqn = importDecl.getName().getFullyQualifiedName();
			int lastDot = importFqn.lastIndexOf('.');
			String importSimpleName = lastDot >= 0 ? importFqn.substring(lastDot + 1) : importFqn;
			if (simpleName.getIdentifier().equals(importSimpleName)) {
				return importFqn;
			}
		}
		return null;
	}

	private boolean importOnDemandCanResolve(SimpleName simpleName, String expectedFqn) {
		CompilationUnit cu = findCompilationUnit(simpleName);
		if (cu == null) {
			return false;
		}
		@SuppressWarnings("unchecked")
		List<ImportDeclaration> imports = cu.imports();
		for (ImportDeclaration importDecl : imports) {
			if (importDecl.isStatic() || !importDecl.isOnDemand()) {
				continue;
			}
			String packageName = importDecl.getName().getFullyQualifiedName();
			if (expectedFqn.equals(packageName + '.' + simpleName.getIdentifier())) {
				return true;
			}
		}
		return false;
	}

	private CompilationUnit findCompilationUnit(ASTNode node) {
		ASTNode current = node;
		while (current != null) {
			if (current instanceof CompilationUnit cu) {
				return cu;
			}
			current = current.getParent();
		}
		return null;
	}

	private boolean isFullyQualified(String name) {
		return name != null && name.indexOf('.') > 0;
	}
}
