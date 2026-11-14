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
package org.sandbox.jdt.container.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.CreationReference;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import org.sandbox.jdt.container.api.ContainerParameterRewritePlan.EditKind;
import org.sandbox.jdt.container.api.ContainerParameterRewritePlan.ParameterEdit;

/** Proves the complete, same-unit ownership transfer and the field operations it permits. */
public record ConstructorOwnedArrayField(String bindingKey, List<ParameterEdit> edits) {

	public ConstructorOwnedArrayField {
		edits= List.copyOf(edits);
	}

	/** Recomputed during planning and again before applying an edit. */
	public static Optional<ConstructorOwnedArrayField> analyze(CompilationUnit root, String parameterKey,
			int callerArgumentStart, int callerArgumentLength) {
		if (Arrays.stream(root.getProblems()).anyMatch(IProblem::isError)) {
			return Optional.empty();
		}
		ASTNode argument= NodeFinder.perform(root, callerArgumentStart, callerArgumentLength);
		if (!(argument instanceof SimpleName argumentName) || !(argument.getParent() instanceof ClassInstanceCreation creation)
				|| !ContainerConstructorTarget.isClosed(creation) || !(creation.getParent() instanceof ReturnStatement returned)
				|| !(returned.getParent() instanceof Block body) || !(body.getParent() instanceof MethodDeclaration)
				|| body.statements().getLast() != returned || !creation.arguments().contains(argument)) {
			return Optional.empty();
		}
		var constructor= creation.resolveConstructorBinding().getMethodDeclaration();
		if (!(root.findDeclaringNode(constructor) instanceof MethodDeclaration method)
				|| !(method.getParent() instanceof TypeDeclaration owner) || !Modifier.isFinal(owner.getModifiers())
				|| owner.getParent() != root || owner.getSuperclassType() != null || !owner.superInterfaceTypes().isEmpty()
				|| owner.resolveBinding() == null || owner.resolveBinding().getAnnotations().length != 0
				|| constructor.getAnnotations().length != 0 || method.getBody() == null || method.getBody().statements().size() != 1) {
			return Optional.empty();
		}
		var parameter= (SingleVariableDeclaration) method.parameters().get(creation.arguments().indexOf(argument));
		if (parameter.isVarargs() || !parameter.extraDimensions().isEmpty() || !(parameter.getType() instanceof ArrayType)
				|| parameter.resolveBinding() == null || !parameterKey.equals(parameter.resolveBinding().getKey())
				|| !parameter.resolveBinding().getType().isEqualTo(argumentName.resolveTypeBinding())
				|| !(method.getBody().statements().getFirst() instanceof ExpressionStatement statement)
				|| !(statement.getExpression() instanceof Assignment store) || store.getOperator() != Assignment.Operator.ASSIGN
				|| !(store.getRightHandSide() instanceof SimpleName rhs) || !parameter.resolveBinding().isEqualTo(rhs.resolveBinding())) {
			return Optional.empty();
		}
		Expression left= store.getLeftHandSide();
		IVariableBinding field= SynchronizedListFieldFacts.currentInstanceField(left);
		if (field == null || !Modifier.isPrivate(field.getModifiers()) || !Modifier.isFinal(field.getModifiers())
				|| !field.getType().isEqualTo(parameter.resolveBinding().getType())
				|| !(root.findDeclaringNode(field) instanceof VariableDeclarationFragment fragment)
				|| !(fragment.getParent() instanceof FieldDeclaration declaration) || declaration.fragments().size() != 1
				|| !fragment.extraDimensions().isEmpty() || fragment.getInitializer() != null
				|| !(declaration.getType() instanceof ArrayType fieldType) || fieldType.getDimensions() != 1
				|| field.getAnnotations().length != 0 || hasTypeAnnotations(field.getType())
				|| parameter.resolveBinding().getAnnotations().length != 0 || hasTypeAnnotations(parameter.resolveBinding().getType())) {
			return Optional.empty();
		}
		List<ParameterEdit> edits= new ArrayList<>();
		edits.add(edit(EditKind.VERIFY_TERMINAL_ARGUMENT, argument));
		edits.add(edit(EditKind.CHANGE_OWNED_FIELD, declaration.getType()));
		edits.add(edit(EditKind.VERIFY_CONSTRUCTOR_STORE, rhs));
		boolean[] valid= { true };
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(ClassInstanceCreation other) {
				if (other != creation && other.resolveConstructorBinding() != null
						&& constructor.isEqualTo(other.resolveConstructorBinding().getMethodDeclaration())) {
					valid[0]= false;
				}
				return valid[0];
			}

			@Override
			public boolean visit(CreationReference reference) {
				if (reference.resolveMethodBinding() != null
						&& constructor.isEqualTo(reference.resolveMethodBinding().getMethodDeclaration())) {
					valid[0]= false;
				}
				return valid[0];
			}

			@Override
			public boolean visit(MethodDeclaration member) {
				if (Modifier.isNative(member.getModifiers())) {
					valid[0]= false;
				}
				return valid[0];
			}

			@Override
			public boolean visit(MethodInvocation invocation) {
				var binding= invocation.resolveMethodBinding();
				if (binding == null || binding.isRecovered()) {
					valid[0]= false;
				} else {
					String declaring= binding.getDeclaringClass().getErasure().getQualifiedName();
					if ("java.lang.Class".equals(declaring) || declaring.startsWith("java.lang.reflect.") //$NON-NLS-1$ //$NON-NLS-2$
							|| declaring.startsWith("java.lang.invoke.")) { //$NON-NLS-1$
						valid[0]= false;
					}
				}
				return valid[0];
			}

			@Override
			public boolean visit(SimpleName name) {
				IBinding binding= name.resolveBinding();
				if (binding == null && name.getIdentifier().equals(field.getName())) {
					valid[0]= false;
				}
				if (!field.isEqualTo(binding) || name == fragment.getName()) {
					return valid[0];
				}
				Expression reference= name;
				if (name.getParent() instanceof FieldAccess access && access.getName() == name) {
					reference= access;
				} else if (name.getParent() instanceof QualifiedName qualified && qualified.getName() == name) {
					reference= qualified;
				}
				if (reference == left) {
					return true;
				}
				if (reference.getParent() instanceof QualifiedName length && length.getQualifier() == reference
						&& "length".equals(length.getName().getIdentifier())) { //$NON-NLS-1$
					edits.add(edit(EditKind.REPLACE_FIELD_LENGTH, length));
				} else if (reference.getParent() instanceof FieldAccess length && length.getExpression() == reference
						&& "length".equals(length.getName().getIdentifier())) { //$NON-NLS-1$
					edits.add(edit(EditKind.REPLACE_FIELD_LENGTH, length));
				} else {
					valid[0]= false;
				}
				return valid[0];
			}
		});
		return valid[0] ? Optional.of(new ConstructorOwnedArrayField(field.getKey(), edits)) : Optional.empty();
	}

	private static boolean hasTypeAnnotations(ITypeBinding type) {
		return type.getTypeAnnotations().length != 0 || type.isArray() && hasTypeAnnotations(type.getElementType());
	}

	private static ParameterEdit edit(EditKind kind, ASTNode node) {
		return new ParameterEdit(kind, node.getStartPosition(), node.getLength());
	}
}
