/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.lang.model.SourceVersion;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.sandbox.jdt.cleanup.multifile.GeneratedNameHierarchyPolicy;
import org.sandbox.jdt.internal.corext.fix.multifile.IntEnumMigrationMode;

/** Adds a typed entry for a public legacy method without rewriting existing members or callers. */
public final class PublicEnumAdapter {
	private PublicEnumAdapter() { }

	/** Adds owner-local changes for the explicitly selected compatibility profile. */
	public static void addOperations(CompilationUnit root, Set<ASTNode> processed,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations) {
		for (Object declaration : root.types()) {
			if (!(declaration instanceof TypeDeclaration owner) || owner.isInterface()
					|| !Modifier.isFinal(owner.getModifiers()) || !Modifier.isPublic(owner.getModifiers())
					|| !valid(owner.resolveBinding()) || processed.stream().anyMatch(node -> isInside(node, owner))) {
				continue;
			}
			List<Candidate> candidates= Arrays.stream(owner.getMethods())
					.map(method -> candidate(owner, method)).filter(java.util.Objects::nonNull).toList();
			for (Candidate candidate : candidates) {
				// Two methods may imply competing nested types or competing bridge names.
				if (candidates.stream().noneMatch(other -> candidate != other && (candidate.enumName.equals(other.enumName)
						|| candidate.entryName.equals(other.entryName)))) {
					operations.add(candidate.operation());
				}
			}
		}
	}

	private static boolean isInside(ASTNode node, TypeDeclaration owner) {
		for (ASTNode current= node; current != null; current= current.getParent()) {
			if (current == owner) return true;
		}
		return false;
	}

	private static Candidate candidate(TypeDeclaration owner, MethodDeclaration method) {
		IMethodBinding binding= method.resolveBinding();
		if (!valid(binding) || !Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())
				|| method.getBody() == null || method.parameters().size() != 1 || !method.typeParameters().isEmpty()
				|| !method.thrownExceptionTypes().isEmpty() || !(method.getReturnType2() instanceof PrimitiveType returns)
				|| returns.getPrimitiveTypeCode() != PrimitiveType.VOID) {
			return null;
		}
		var parameter= (SingleVariableDeclaration) method.parameters().get(0);
		var state= parameter.resolveBinding();
		if (!valid(state) || !valid(state.getType())
				|| !Set.of("int", "java.lang.String").contains(state.getType().getQualifiedName())) { //$NON-NLS-1$ //$NON-NLS-2$
			return null;
		}
		Set<String> compared= comparedConstants(owner, method, state);
		String prefix= SwitchIntToEnumHelper.findCommonPrefix(new ArrayList<>(compared));
		if (prefix == null) return null;
		String enumName= SwitchIntToEnumHelper.prefixToEnumName(prefix);
		String entryName= method.getName().getIdentifier() + "Value"; //$NON-NLS-1$
		if (!identifier(enumName) || EnumNameSafety.conflicts(owner, enumName)
				|| EnumNameSafety.identifierInUse(owner, entryName) || hierarchyConflicts(owner.resolveBinding(), enumName, entryName)) {
			return null;
		}

		List<String> fields= new ArrayList<>();
		Set<EnumConstantValue> values= new HashSet<>();
		String ownerName= owner.getName().getIdentifier();
		for (var field : owner.getFields()) {
			for (Object element : field.fragments()) {
				var fragment= (VariableDeclarationFragment) element;
				String name= fragment.getName().getIdentifier();
				if (!name.startsWith(prefix)) continue;
				var value= EnumConstantValue.from(fragment.resolveBinding());
				String symbol= name.substring(prefix.length());
				if (!Modifier.isPublic(field.getModifiers()) || value == null || !value.matches(state.getType())
						|| !values.add(value) || !compared.contains(name) || !identifier(symbol)
						|| symbol.equals(ownerName)) {
					return null;
				}
				fields.add(name);
			}
		}
		if (fields.size() != compared.size()) return null;
		String scalar= parameter.getType().toString();
		if (!Set.of("int", "String", "java.lang.String").contains(scalar)) return null; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		return new Candidate(owner, method, prefix, enumName, entryName, scalar, fields);
	}

	/** A complete direct if/else chain provides evidence that the public constants describe one parameter. */
	private static Set<String> comparedConstants(TypeDeclaration owner, MethodDeclaration method, IVariableBinding state) {
		Set<String> result= new LinkedHashSet<>();
		for (Object statement : method.getBody().statements()) {
			if (!(statement instanceof IfStatement first)) continue;
			if (!result.isEmpty()) return Set.of();
			for (IfStatement arm= first; arm != null; arm= arm.getElseStatement() instanceof IfStatement next ? next : null) {
				var operands= EnumComparison.operands(arm.getExpression());
				if (operands == null) return Set.of();
				IVariableBinding left= variable(operands.left());
				IVariableBinding right= variable(operands.right());
				IVariableBinding field= state.isEqualTo(left) ? right : state.isEqualTo(right) ? left : null;
				if (!valid(field) || !owner.resolveBinding().isEqualTo(field.getDeclaringClass())
						|| !result.add(field.getName())) return Set.of();
			}
		}
		return result.size() >= 2 ? result : Set.of();
	}

	private static IVariableBinding variable(Expression expression) {
		if (!EnumConstantValue.isDirectReference(expression)) return null;
		IBinding binding= expression instanceof Name name ? name.resolveBinding()
				: expression instanceof FieldAccess access ? access.resolveFieldBinding() : null;
		return binding instanceof IVariableBinding variable ? variable.getVariableDeclaration() : null;
	}

	private static boolean hierarchyConflicts(ITypeBinding owner, String enumName, String entryName) {
		if (!GeneratedNameHierarchyPolicy.inheritedMemberTypeCollisions(owner, enumName).isEmpty()
				|| Arrays.stream(owner.getDeclaredFields()).anyMatch(field -> field.getName().equals(owner.getName()))) {
			return true;
		}
		for (ITypeBinding type : Bindings.getAllSuperTypes(owner)) {
			// Adding a public method can change a serializable class's default serialVersionUID.
			if (!valid(type) || "java.io.Serializable".equals(type.getQualifiedName()) //$NON-NLS-1$
					|| Arrays.stream(type.getDeclaredFields()).anyMatch(field -> field.getName().equals(enumName)
							|| field.getName().equals(owner.getName()))
					|| Arrays.stream(type.getDeclaredMethods()).anyMatch(member -> member.getName().equals(entryName))) {
				return true;
			}
		}
		return false;
	}

	private static boolean valid(IBinding binding) {
		return binding != null && !binding.isRecovered();
	}

	private static boolean identifier(String name) {
		return SourceVersion.isIdentifier(name) && !SourceVersion.isKeyword(name);
	}

	private record Candidate(TypeDeclaration owner, MethodDeclaration method, String prefix, String enumName,
			String entryName, String scalar, List<String> constants) {
		CompilationUnitRewriteOperationWithSourceRange operation() {
			return new CompilationUnitRewriteOperationWithSourceRange() {
				@Override
				public void rewriteASTInternal(CompilationUnitRewrite unit, LinkedProposalModelCore linkedModel) {
					var rewrite= unit.getASTRewrite();
					var group= createTextEditGroup(IntEnumMigrationMode.PUBLIC_VALUE_ADAPTER_OPT_IN.previewStatement(), unit);
					// Keep both declarations in one insertion so the preview can apply or undo the adapter atomically.
					var members= rewrite.createStringPlaceholder(source(), ASTNode.ENUM_DECLARATION);
					rewrite.getListRewrite(owner, TypeDeclaration.BODY_DECLARATIONS_PROPERTY).insertAfter(members, method, group);
				}
			};
		}

		String source() {
			Set<String> reserved= new HashSet<>();
			ITypeBinding declaringType= owner.resolveBinding();
			reserved.add(declaringType.getName());
			Arrays.stream(declaringType.getDeclaredFields()).forEach(field -> reserved.add(field.getName()));
			for (ITypeBinding type : Bindings.getAllSuperTypes(declaringType)) {
				Arrays.stream(type.getDeclaredFields()).forEach(field -> reserved.add(field.getName()));
			}
			constants.forEach(name -> reserved.add(name.substring(prefix.length())));
			String backing= fresh("value", reserved); //$NON-NLS-1$
			String argument= fresh("rawValue", reserved); //$NON-NLS-1$
			String parameter= fresh("enumValue", reserved); //$NON-NLS-1$
			String elements= constants.stream().map(name -> {
				String reference= owner.getName().getIdentifier() + "." + name; //$NON-NLS-1$
				return "/** Legacy value for " + name + ". */\n\t" + name.substring(prefix.length()) + "(" + reference + ")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			}).collect(Collectors.joining(",\n\t")); //$NON-NLS-1$
			return """
					/** Named values accepted by {{method}}. */
					public enum {{enum}} {
						{{elements}};

						private final {{scalar}} {{backing}};

						{{enum}}({{scalar}} {{argument}}) {
							this.{{backing}} = {{argument}};
						}

						/**
						 * Returns the original constant value.
						 * @return the original constant value
						 */
						public {{scalar}} legacyValue() {
							return this.{{backing}};
						}
					}

					/**
					 * Invokes {{method}} with the named value.
					 * @param {{parameter}} the named value
					 * @throws java.lang.NullPointerException if the named value is null
					 */
					public void {{entry}}({{enum}} {{parameter}}) {
						this.{{method}}({{parameter}}.legacyValue());
					}""" //$NON-NLS-1$
					.replace("{{method}}", method.getName().getIdentifier()) //$NON-NLS-1$
					.replace("{{enum}}", enumName) //$NON-NLS-1$
					.replace("{{scalar}}", scalar) //$NON-NLS-1$
					.replace("{{entry}}", entryName) //$NON-NLS-1$
					.replace("{{backing}}", backing) //$NON-NLS-1$
					.replace("{{argument}}", argument) //$NON-NLS-1$
					.replace("{{parameter}}", parameter) //$NON-NLS-1$
					.replace("{{elements}}", elements); //$NON-NLS-1$
		}
	}

	private static String fresh(String base, Set<String> reserved) {
		String name= base;
		for (int suffix= 2; !reserved.add(name); suffix++) {
			name= base + suffix;
		}
		return name;
	}

	/** Example shown when public adapters are explicitly enabled in the cleanup profile. */
	public static String preview() {
		return """

				public void processValue(Status enumValue) {
					this.process(enumValue.legacyValue());
				}
				"""; //$NON-NLS-1$
	}
}
