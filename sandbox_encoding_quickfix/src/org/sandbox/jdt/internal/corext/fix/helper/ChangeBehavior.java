/*******************************************************************************
 * Copyright (c) 2021 Carsten Hammer.
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
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.sandbox.jdt.internal.common.LibStandardNames.METHOD_DEFAULT_CHARSET;
import static org.sandbox.jdt.internal.common.LibStandardNames.METHOD_DISPLAY_NAME;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;

import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.ScopeAnalyzer;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

public enum ChangeBehavior {
	KEEP_BEHAVIOR() {
		@Override
		protected Expression computeCharsetASTNode(final CompilationUnitRewrite cuRewrite, AST ast, ASTNode visited,
				String charset, Map<String, QualifiedName> charsetConstants) {
			Expression callToCharsetDefaultCharset= null;

			if (charset != null) {
				callToCharsetDefaultCharset= addCharsetUTF8(cuRewrite, ast, charset);
			} else {
				// needs Java 1.5
				callToCharsetDefaultCharset= addCharsetComputation(cuRewrite, ast);
			}

			return callToCharsetDefaultCharset;
		}

		@Override
		protected String computeCharsetforPreview() {
			String insert= "Charset.defaultCharset()"; //$NON-NLS-1$
			return insert;
		}
	},
	ENFORCE_UTF8() {
		@Override
		protected Expression computeCharsetASTNode(final CompilationUnitRewrite cuRewrite, AST ast, ASTNode visited,
				String charset, Map<String, QualifiedName> charsetConstants) {
			String charset2= charset == null ? "UTF_8" : charset; //$NON-NLS-1$
			Expression callToCharsetDefaultCharset= addCharsetUTF8(cuRewrite, ast, charset2);
			return callToCharsetDefaultCharset;
		}

		@Override
		protected String computeCharsetforPreview() {
			String insert= "StandardCharsets.UTF_8"; //$NON-NLS-1$
			return insert;
		}
	},
	ENFORCE_UTF8_AGGREGATE() {
		@Override
		protected Expression computeCharsetASTNode(final CompilationUnitRewrite cuRewrite, AST ast, ASTNode visited,
				String charset2, Map<String, QualifiedName> charsetConstants) {
			String charset= charset2 == null ? "UTF_8" : charset2; //$NON-NLS-1$
			TypeDeclaration enclosingType= findAggregateCharsetOwner(cuRewrite, visited);
			if (enclosingType == null) {
				return addCharsetUTF8(cuRewrite, ast, normalizeCharsetFieldName(charset));
			}
			String ownerKey= aggregateCharsetOwnerKey(cuRewrite.getRoot(), enclosingType);
			String normalizedCharset= normalizeCharsetFieldName(charset);
			String reservationKey= ownerKey + '\u0000' + normalizedCharset;

			VariableDeclarationFragment existingField= findCompatibleCharsetField(enclosingType, normalizedCharset);
			if (existingField != null ? !isSafeToReadField(enclosingType, existingField, visited)
					: !canInitializeOwnFieldsFirst(enclosingType)) {
				return addCharsetUTF8(cuRewrite, ast, normalizedCharset);
			}

			String ownerName= aggregateOwnerName(cuRewrite.getRoot(), enclosingType, visited);
			if (ownerName == null) {
				return addCharsetUTF8(cuRewrite, ast, normalizedCharset);
			}
			QualifiedName cached= charsetConstants.get(reservationKey);
			if (cached != null) {
				// Reservations are owner-local; expression qualifiers are occurrence-local.
				return ast.newQualifiedName(ast.newName(ownerName),
						ast.newSimpleName(cached.getName().getIdentifier()));
			}

			String fieldName= existingField != null
					? existingField.getName().getIdentifier()
					: generateCharsetFieldName(enclosingType, ownerKey, normalizedCharset, charsetConstants);

			QualifiedName fieldReference= ast.newQualifiedName(
					ast.newName(ownerName), ast.newSimpleName(fieldName));
			if (existingField == null) {
				ImportRewrite importRewrite= cuRewrite.getImportRewrite();
				String standardCharsetsType= importRewrite.addImport(StandardCharsets.class.getCanonicalName());
				String charsetType= importRewrite.addImport(Charset.class.getCanonicalName());
				FieldAccess initializer= ast.newFieldAccess();
				initializer.setExpression(ast.newName(standardCharsetsType));
				initializer.setName(ast.newSimpleName(normalizedCharset));
				VariableDeclarationFragment fragment= ast.newVariableDeclarationFragment();
				fragment.setName(ast.newSimpleName(fieldName));
				fragment.setInitializer(initializer);

				FieldDeclaration fieldDeclaration= ast.newFieldDeclaration(fragment);
				fieldDeclaration.setType(ast.newSimpleType(ast.newName(charsetType)));
				fieldDeclaration.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));
				fieldDeclaration.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.STATIC_KEYWORD));
				fieldDeclaration.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.FINAL_KEYWORD));

				cuRewrite.getASTRewrite().getListRewrite(enclosingType, TypeDeclaration.BODY_DECLARATIONS_PROPERTY)
						.insertFirst(fieldDeclaration, null);
			}

			charsetConstants.put(reservationKey, fieldReference);
			return copyQualifiedName(ast, fieldReference);
		}

		@Override
		protected String computeCharsetforPreview() {
			return "CharsetConstant"; //$NON-NLS-1$
		}
	};

	abstract protected Expression computeCharsetASTNode(CompilationUnitRewrite cuRewrite, AST ast, ASTNode visited,
			String charset, Map<String, QualifiedName> charsetConstants);

	abstract protected String computeCharsetforPreview();

	protected static FieldDeclaration findStaticCharsetField(TypeDeclaration type, String fieldName) {
		for (FieldDeclaration field : type.getFields()) {
			for (Object fragment : field.fragments()) {
				if (fragment instanceof VariableDeclarationFragment varFrag
						&& varFrag.getName().getIdentifier().equals(fieldName)) {
					return field;
				}
			}
		}
		return null;
	}

	protected static Expression createCharsetAccessExpression(AST ast, String charset) {
		FieldAccess fieldAccess= ast.newFieldAccess();
		fieldAccess.setExpression(ast.newName(StandardCharsets.class.getSimpleName()));
		fieldAccess.setName(ast.newSimpleName(charset));
		return fieldAccess;
	}

	/**
	 * Create access to StandardCharsets.UTF_8, needs Java 1.7 or newer
	 *
	 * @param cuRewrite CompilationUnitRewrite
	 * @param ast AST
	 * @param charset Charset as String
	 * @return FieldAccess that returns Charset for UTF_8
	 */
	protected static FieldAccess addCharsetUTF8(CompilationUnitRewrite cuRewrite, AST ast, String charset) {
		/**
		 * Add import java.nio.charset.StandardCharsets - available since Java 1.7
		 */
		ImportRewrite importRewrite= cuRewrite.getImportRewrite();
		String standardCharsetsType= importRewrite.addImport(StandardCharsets.class.getCanonicalName());
		/**
		 * Add field access to StandardCharsets.UTF_8
		 */
		FieldAccess fieldaccess= ast.newFieldAccess();
		fieldaccess.setExpression(ASTNodeFactory.newName(ast, standardCharsetsType));

		fieldaccess.setName(ast.newSimpleName(charset));
		return fieldaccess;
	}

	/**
	 * Create call to Charset.defaultCharset(), needs Java 1.5 or newer
	 *
	 * @param cuRewrite CompilationUnitRewrite
	 * @param ast AST
	 * @return MethodInvocation that returns Charset for platform encoding
	 */
	protected static MethodInvocation addCharsetComputation(final CompilationUnitRewrite cuRewrite, AST ast) {
		/**
		 * Add import java.nio.charset.Charset
		 */
		ImportRewrite importRewrite= cuRewrite.getImportRewrite();
		importRewrite.addImport(Charset.class.getCanonicalName());
		/**
		 * Create call to Charset.defaultCharset() - this is available since Java 1.5
		 */
		MethodInvocation firstCall= ast.newMethodInvocation();
		firstCall.setExpression(ASTNodeFactory.newName(ast, Charset.class.getSimpleName()));
		firstCall.setName(ast.newSimpleName(METHOD_DEFAULT_CHARSET));
		return firstCall;
	}

	/**
	 * Create call to Charset.defaultCharset().displayName(), needs Java 1.5 or newer
	 *
	 * @param cuRewrite CompilationUnitRewrite
	 * @param ast AST
	 * @param cb ChangeBehavior
	 * @param charset Charset as String
	 * @return MethodInvocation that returns String
	 */
	protected MethodInvocation addCharsetStringComputation(final CompilationUnitRewrite cuRewrite, AST ast,
			ASTNode visited, ChangeBehavior cb, String charset, Map<String, QualifiedName> charsetConstants) {
		Expression callToCharsetDefaultCharset= computeCharsetASTNode(cuRewrite, ast, visited, charset, charsetConstants);
		/**
		 * Add second call to Charset.defaultCharset().displayName()
		 */
		MethodInvocation secondCall= ast.newMethodInvocation();
		secondCall.setExpression(callToCharsetDefaultCharset);
		secondCall.setName(ast.newSimpleName(METHOD_DISPLAY_NAME));
		return secondCall;
	}

	private static QualifiedName copyQualifiedName(AST ast, QualifiedName qualifiedName) {
		return ast.newQualifiedName(ast.newName(qualifiedName.getQualifier().getFullyQualifiedName()),
				ast.newSimpleName(qualifiedName.getName().getIdentifier()));
	}

	/** Resolve expression-name obscuring for this occurrence, not for the cached field. */
	private static String aggregateOwnerName(CompilationUnit root, TypeDeclaration owner, ASTNode visited) {
		ITypeBinding type= owner.resolveBinding();
		if (type == null || type.isRecovered()) {
			return null;
		}
		IBinding[] scope= new ScopeAnalyzer(root).getDeclarationsInScope(visited.getStartPosition(),
				ScopeAnalyzer.VARIABLES | ScopeAnalyzer.TYPES | ScopeAnalyzer.CHECK_VISIBILITY);
		String simple= owner.getName().getIdentifier();
		boolean obscured= hasEnclosingTypeParameter(visited, simple);
		for (IBinding binding : scope) {
			if (simple.equals(binding.getName()) && !binding.isEqualTo(type)) {
				obscured= true;
				break;
			}
		}
		if (!obscured) {
			return simple;
		}
		String qualified= type.getTypeDeclaration().getQualifiedName();
		int dot= qualified.indexOf('.');
		if (dot < 0) {
			return null; // A shadowed default-package type has no qualified alternative.
		}
		String first= qualified.substring(0, dot);
		if (hasEnclosingTypeParameter(visited, first)) {
			return null;
		}
		for (IBinding binding : scope) {
			if (first.equals(binding.getName())) {
				return null; // Qualification must not turn a package name into a variable/type.
			}
		}
		for (ITypeBinding member= type; member.getDeclaringClass() != null; member= member.getDeclaringClass()) {
			for (ITypeBinding declaring= member.getDeclaringClass(); declaring != null; declaring= declaring.getSuperclass()) {
				for (IVariableBinding field : declaring.getDeclaredFields()) {
					if (member.getName().equals(field.getName())) {
						return null; // An intermediate member name may also denote a field.
					}
				}
			}
		}
		return qualified;
	}

	private static boolean hasEnclosingTypeParameter(ASTNode visited, String name) {
		for (ASTNode current= visited; current != null; current= current.getParent()) {
			List<?> parameters;
			if (current instanceof MethodDeclaration method) {
				parameters= method.typeParameters();
			} else if (current instanceof TypeDeclaration type) {
				parameters= type.typeParameters();
			} else {
				continue;
			}
			for (Object parameter : parameters) {
				if (name.equals(((TypeParameter) parameter).getName().getIdentifier())) {
					return true;
				}
			}
		}
		return false;
	}

	private static TypeDeclaration findAggregateCharsetOwner(CompilationUnitRewrite cuRewrite, ASTNode visited) {
		TypeDeclaration owner= ASTNodes.getFirstAncestorOrNull(visited, TypeDeclaration.class);
		if (owner != null) {
			return owner;
		}
		if (!cuRewrite.getRoot().types().isEmpty() && cuRewrite.getRoot().types().get(0) instanceof TypeDeclaration type) {
			return type;
		}
		return null;
	}

	private static String aggregateCharsetOwnerKey(CompilationUnit root, TypeDeclaration owner) {
		ITypeBinding binding= owner.resolveBinding();
		if (binding != null) {
			ITypeBinding declaration= binding.getTypeDeclaration();
			if (declaration != null && declaration.getKey() != null) {
				return declaration.getKey();
			}
		}
		List<String> segments= new ArrayList<>();
		for (ASTNode current= owner; current != null; current= current.getParent()) {
			if (current instanceof TypeDeclaration type) {
				segments.add(0, type.getName().getIdentifier());
			}
		}
		String packageName= root.getPackage() == null ? "" : root.getPackage().getName().getFullyQualifiedName(); //$NON-NLS-1$
		String localName= String.join(".", segments); //$NON-NLS-1$
		return packageName.isEmpty() ? localName : packageName + '.' + localName;
	}

	private static String normalizeCharsetFieldName(String charset) {
		return charset.toUpperCase(Locale.ROOT).replace('-', '_');
	}

	private static VariableDeclarationFragment findCompatibleCharsetField(TypeDeclaration owner, String requestedCharset) {
		VariableDeclarationFragment fallback= null;
		for (FieldDeclaration field : owner.getFields()) {
			for (Object fragment : field.fragments()) {
				if (fragment instanceof VariableDeclarationFragment variable
						&& isCompatibleCharsetField(field, variable, requestedCharset)) {
					if (requestedCharset.equals(variable.getName().getIdentifier())) {
						return variable;
					}
					if (fallback == null) {
						fallback= variable;
					}
				}
			}
		}
		return fallback;
	}

	private static boolean isCompatibleCharsetField(FieldDeclaration field, VariableDeclarationFragment fragment,
			String requestedCharset) {
		if (!Modifier.isStatic(field.getModifiers()) || !Modifier.isFinal(field.getModifiers())
				|| fragment.getInitializer() == null) {
			return false;
		}
		ITypeBinding binding= field.getType().resolveBinding();
		if (binding != null) {
			if (!Charset.class.getCanonicalName().equals(binding.getErasure().getQualifiedName())) {
				return false;
			}
		} else {
			String typeName= field.getType().toString();
			if (!Charset.class.getSimpleName().equals(typeName) && !typeName.endsWith(".Charset")) { //$NON-NLS-1$
				return false;
			}
		}
		String resolvedCharset= resolveCharsetInitializer(fragment.getInitializer());
		return resolvedCharset != null && requestedCharset.equals(normalizeCharsetFieldName(resolvedCharset));
	}

	private static String resolveCharsetInitializer(Expression initializer) {
		Expression expression= ASTNodes.getUnparenthesedExpression(initializer);
		IVariableBinding field= null;
		if (expression instanceof Name name && name.resolveBinding() instanceof IVariableBinding variable) {
			field= variable;
		} else if (expression instanceof FieldAccess fieldAccess) {
			field= fieldAccess.resolveFieldBinding();
		}
		if (field != null && !field.isRecovered()) {
			ITypeBinding declaringClass= field.getDeclaringClass();
			if (declaringClass != null && !declaringClass.isRecovered()
					&& StandardCharsets.class.getCanonicalName().equals(declaringClass.getErasure().getQualifiedName())
					&& Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers())) {
				return field.getName().replace('_', '-');
			}
			return null;
		}
		if (expression instanceof MethodInvocation methodInvocation
				&& ASTNodes.usesGivenSignature(methodInvocation, Charset.class.getCanonicalName(), "forName", //$NON-NLS-1$
						String.class.getCanonicalName())
				&& methodInvocation.arguments().size() == 1
				&& methodInvocation.arguments().get(0) instanceof StringLiteral literal) {
			return literal.getLiteralValue().toUpperCase(Locale.ROOT);
		}
		return null;
	}

	private static boolean isSafeToReadField(TypeDeclaration owner, VariableDeclarationFragment targetField, ASTNode visited) {
		if (!(targetField.getParent() instanceof FieldDeclaration targetDeclaration)
				|| targetDeclaration.getParent() != owner || !Modifier.isStatic(targetDeclaration.getModifiers())) {
			return false;
		}
		BodyDeclaration enclosingDeclaration= findEnclosingBodyDeclaration(owner, visited);
		if (enclosingDeclaration == null) {
			return false;
		}
		if (enclosingDeclaration instanceof FieldDeclaration fieldDeclaration
				&& Modifier.isStatic(fieldDeclaration.getModifiers())) {
			return isSafeFromStaticFieldInitializer(owner, fieldDeclaration, targetField, visited);
		}
		if (enclosingDeclaration instanceof Initializer initializer && Modifier.isStatic(initializer.getModifiers())) {
			return bodyDeclarationIndex(owner, initializer) > bodyDeclarationIndex(owner, targetDeclaration);
		}
		// Methods (including constructors), instance initializers and nested types may
		// be reached through callbacks while this class is still being initialized.
		// A same-owner static-call graph cannot establish that those reads are safe.
		return canInitializeOwnFieldsFirst(owner) && isFirstExecutingStaticInitializer(owner, targetField);
	}

	/**
	 * Even a newly inserted first field runs after superclass and default-method
	 * interface initialization. Without a proof about that hierarchy, keep direct
	 * typed JDK constants instead of introducing an initialization dependency.
	 * Local and non-static member classes also cannot host this field on older
	 * supported source levels, and an interface cannot contain a private field.
	 */
	private static boolean canInitializeOwnFieldsFirst(TypeDeclaration owner) {
		ITypeBinding binding= owner.resolveBinding();
		if (binding == null || binding.isRecovered() || owner.isInterface() || binding.isLocal()
				|| binding.isAnonymous() || binding.isMember() && !Modifier.isStatic(binding.getModifiers())
				|| binding.getInterfaces().length != 0) {
			return false;
		}
		ITypeBinding superclass= binding.getSuperclass();
		return superclass != null && !superclass.isRecovered()
				&& Object.class.getCanonicalName().equals(superclass.getErasure().getQualifiedName());
	}

	private static boolean isFirstExecutingStaticInitializer(TypeDeclaration owner,
			VariableDeclarationFragment targetField) {
		for (Object declaration : owner.bodyDeclarations()) {
			if (declaration instanceof Initializer initializer && Modifier.isStatic(initializer.getModifiers())) {
				return false;
			}
			if (declaration instanceof FieldDeclaration field && Modifier.isStatic(field.getModifiers())) {
				for (Object candidate : field.fragments()) {
					if (candidate == targetField) {
						return true;
					}
					if (candidate instanceof VariableDeclarationFragment fragment
							&& fragment.getInitializer() != null
							&& fragment.getInitializer().resolveConstantExpressionValue() == null) {
						return false;
					}
				}
			}
		}
		return false;
	}

	private static boolean isSafeFromStaticFieldInitializer(TypeDeclaration owner, FieldDeclaration enclosingDeclaration,
			VariableDeclarationFragment targetField, ASTNode visited) {
		FieldDeclaration targetDeclaration= (FieldDeclaration) targetField.getParent();
		if (enclosingDeclaration != targetDeclaration) {
			return bodyDeclarationIndex(owner, enclosingDeclaration) > bodyDeclarationIndex(owner, targetDeclaration);
		}
		VariableDeclarationFragment enclosingFragment= findEnclosingFragment(enclosingDeclaration, visited);
		if (enclosingFragment == null) {
			return false;
		}
		return fragmentIndex(enclosingDeclaration, enclosingFragment) > fragmentIndex(enclosingDeclaration, targetField);
	}

	private static BodyDeclaration findEnclosingBodyDeclaration(TypeDeclaration owner, ASTNode visited) {
		for (ASTNode current= visited; current != null && current != owner; current= current.getParent()) {
			if (current instanceof BodyDeclaration declaration && current.getParent() == owner) {
				return declaration;
			}
		}
		return null;
	}

	private static VariableDeclarationFragment findEnclosingFragment(FieldDeclaration declaration, ASTNode visited) {
		for (ASTNode current= visited; current != null && current != declaration; current= current.getParent()) {
			if (current instanceof VariableDeclarationFragment fragment && current.getParent() == declaration) {
				return fragment;
			}
		}
		return null;
	}

	private static int bodyDeclarationIndex(TypeDeclaration owner, BodyDeclaration declaration) {
		return owner.bodyDeclarations().indexOf(declaration);
	}

	private static int fragmentIndex(FieldDeclaration declaration, VariableDeclarationFragment fragment) {
		return declaration.fragments().indexOf(fragment);
	}

	private static String generateCharsetFieldName(TypeDeclaration owner, String ownerKey, String requestedFieldName,
			Map<String, QualifiedName> charsetConstants) {
		if (!hasFieldName(owner, requestedFieldName) && !hasReservedFieldName(ownerKey, requestedFieldName, charsetConstants)) {
			return requestedFieldName;
		}
		for (int suffix= 1; ; suffix++) {
			String candidate= requestedFieldName + '_' + suffix;
			if (!hasFieldName(owner, candidate) && !hasReservedFieldName(ownerKey, candidate, charsetConstants)) {
				return candidate;
			}
		}
	}

	private static boolean hasFieldName(TypeDeclaration owner, String fieldName) {
		for (FieldDeclaration field : owner.getFields()) {
			for (Object fragment : field.fragments()) {
				if (fragment instanceof VariableDeclarationFragment variable
						&& fieldName.equals(variable.getName().getIdentifier())) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean hasReservedFieldName(String ownerKey, String fieldName,
			Map<String, QualifiedName> charsetConstants) {
		String prefix= ownerKey + '\u0000';
		return charsetConstants.entrySet().stream()
				.filter(entry -> entry.getKey().startsWith(prefix))
				.map(Map.Entry::getValue)
				.anyMatch(name -> fieldName.equals(name.getName().getIdentifier()));
	}
}
