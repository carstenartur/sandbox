/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.cleanup.actions;

import java.util.Objects;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

import org.eclipse.text.edits.TextEditGroup;

import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor.TransformationResult;
import org.sandbox.jdt.triggerpattern.api.RewriteActionValue;
import org.sandbox.jdt.triggerpattern.api.SemanticPlanValue;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan.NodeKey;

/** Shared execution context and fail-closed resolver for structured actions. */
public final class StructuredRewriteActionContext {

	private final TransformationResult result;
	private final SemanticRewritePlan plan;
	private final CompilationUnitRewrite cuRewrite;
	private final TextEditGroup editGroup;

	public StructuredRewriteActionContext(TransformationResult result, SemanticRewritePlan plan,
			CompilationUnitRewrite cuRewrite, TextEditGroup editGroup) {
		this.result= Objects.requireNonNull(result);
		this.plan= Objects.requireNonNull(plan);
		this.cuRewrite= Objects.requireNonNull(cuRewrite);
		this.editGroup= Objects.requireNonNull(editGroup);
	}

	public TransformationResult result() {
		return result;
	}

	public SemanticRewritePlan plan() {
		return plan;
	}

	public CompilationUnitRewrite cuRewrite() {
		return cuRewrite;
	}

	public TextEditGroup editGroup() {
		return editGroup;
	}

	public AST ast() {
		return cuRewrite.getRoot().getAST();
	}

	/** Resolves an exact target and verifies that it participates in the plan. */
	public ASTNode resolveAuthorizedNode(RewriteActionValue value) throws CoreException {
		ASTNode node= resolveNode(value);
		NodeKey key= NodeKey.from(node);
		if (key == null) {
			throw failure("Structured action target has no stable semantic key"); //$NON-NLS-1$
		}
		boolean planned= plan.rolesByNode().containsKey(key) || plan.valuesByNode().containsKey(key)
				|| plan.relations().stream().anyMatch(relation ->
						key.equals(relation.source()) || key.equals(relation.target()));
		if (!planned) {
			throw failure("Structured action target is not present in the semantic plan: " + key); //$NON-NLS-1$
		}
		return node;
	}

	/** Resolves the primary match or one explicit pattern binding. */
	public ASTNode resolveNode(RewriteActionValue value) throws CoreException {
		if (value instanceof RewriteActionValue.MatchedNode) {
			ASTNode node= result.match().getMatchedNode();
			if (node == null) {
				throw failure("Structured action has no primary matched node"); //$NON-NLS-1$
			}
			return node;
		}
		if (value instanceof RewriteActionValue.Binding binding) {
			ASTNode node= result.match().getBinding(binding.placeholder());
			if (node == null) {
				throw failure("Missing structured action binding " + binding.placeholder()); //$NON-NLS-1$
			}
			return node;
		}
		throw failure("Structured action target must be the primary match or a pattern binding"); //$NON-NLS-1$
	}

	/** Resolves a scalar typed value, including one semantic-plan fact reference. */
	public SemanticPlanValue resolveSemanticValue(RewriteActionValue value) throws CoreException {
		if (value instanceof RewriteActionValue.Literal literal) {
			return literal.value();
		}
		if (value instanceof RewriteActionValue.PlanValue reference) {
			ASTNode target= reference.placeholder() == null
					? result.match().getMatchedNode()
					: result.match().getBinding(reference.placeholder());
			if (target == null) {
				throw failure("Missing plan-value target binding " + reference.placeholder()); //$NON-NLS-1$
			}
			return plan.value(target, reference.factName()).orElseThrow(() ->
					failure("Missing semantic-plan fact " + reference.factName())); //$NON-NLS-1$
		}
		if (value instanceof RewriteActionValue.Binding || value instanceof RewriteActionValue.MatchedNode) {
			NodeKey key= NodeKey.from(resolveAuthorizedNode(value));
			return SemanticPlanValue.node(key);
		}
		throw failure("Expected a scalar structured action value"); //$NON-NLS-1$
	}

	/**
	 * Resolves a literal/plan string or derives an identifier from one bound
	 * declaration name. The latter lets a declaration-diff compiler reuse the
	 * source placeholder instead of copying its runtime name into the DSL.
	 */
	public String resolveString(RewriteActionValue value) throws CoreException {
		if (value instanceof RewriteActionValue.Binding || value instanceof RewriteActionValue.MatchedNode) {
			ASTNode node= resolveNode(value);
			if (node instanceof SimpleName name) {
				return name.getIdentifier();
			}
			if (node instanceof SingleVariableDeclaration parameter) {
				return parameter.getName().getIdentifier();
			}
			if (node instanceof MethodDeclaration method) {
				return method.getName().getIdentifier();
			}
			if (node instanceof VariableDeclarationFragment fragment) {
				return fragment.getName().getIdentifier();
			}
			throw failure("Bound action value does not denote a Java declaration name"); //$NON-NLS-1$
		}
		SemanticPlanValue resolved= resolveSemanticValue(value);
		if (resolved instanceof SemanticPlanValue.StringValue string) {
			return string.value();
		}
		throw failure("Structured action value must resolve to a string, but was " + resolved.kind()); //$NON-NLS-1$
	}

	/** Resolves an integer value without narrowing silently. */
	public int resolveInteger(RewriteActionValue value) throws CoreException {
		SemanticPlanValue resolved= resolveSemanticValue(value);
		if (resolved instanceof SemanticPlanValue.IntegerValue integer
				&& integer.value() >= Integer.MIN_VALUE && integer.value() <= Integer.MAX_VALUE) {
			return (int) integer.value();
		}
		throw failure("Structured action value must resolve to a 32-bit integer, but was " //$NON-NLS-1$
				+ resolved.kind());
	}

	/**
	 * Creates one detached primitive, imported reference or array type.
	 *
	 * <p>Signature actions intentionally reject generic, wildcard, varargs and
	 * {@code void} source text. Richer type shapes need dedicated typed values,
	 * not arbitrary Java fragments embedded in strings.</p>
	 */
	public Type createType(String requestedType) throws CoreException {
		if (requestedType == null || requestedType.isBlank()) {
			throw failure("Structured action type must not be blank"); //$NON-NLS-1$
		}
		String typeName= requestedType.trim();
		if (typeName.indexOf('<') >= 0 || typeName.indexOf('>') >= 0
				|| typeName.indexOf('?') >= 0 || typeName.endsWith("...")) { //$NON-NLS-1$
			throw failure("Structured signature actions currently support primitive, reference and array types only: " //$NON-NLS-1$
					+ typeName);
		}
		int dimensions= 0;
		while (typeName.endsWith("[]")) { //$NON-NLS-1$
			dimensions++;
			typeName= typeName.substring(0, typeName.length() - 2).trim();
		}
		PrimitiveType.Code primitive= PrimitiveType.toCode(typeName);
		if (primitive == PrimitiveType.VOID) {
			throw failure("Structured signature actions cannot use void as a field or parameter type"); //$NON-NLS-1$
		}
		Type elementType;
		try {
			elementType= primitive == null
					? ast().newSimpleType(ast().newName(cuRewrite.getImportRewrite().addImport(typeName)))
					: ast().newPrimitiveType(primitive);
		} catch (IllegalArgumentException exception) {
			throw failure("Structured action type is not a valid Java type name: " + requestedType); //$NON-NLS-1$
		}
		return dimensions == 0 ? elementType : ast().newArrayType(elementType, dimensions);
	}

	/** Builds one detached AST expression from a typed action value. */
	public Expression createExpression(RewriteActionValue value) throws CoreException {
		if (value instanceof RewriteActionValue.ClassLiteral classLiteral) {
			String typeName= resolveString(classLiteral.typeName());
			String imported= cuRewrite.getImportRewrite().addImport(typeName);
			TypeLiteral literal= ast().newTypeLiteral();
			literal.setType(ast().newSimpleType(ast().newName(imported)));
			return literal;
		}
		if (value instanceof RewriteActionValue.Name name) {
			return ast().newName(resolveString(name.qualifiedName()));
		}
		if (value instanceof RewriteActionValue.ListValue list) {
			ArrayInitializer initializer= ast().newArrayInitializer();
			for (RewriteActionValue element : list.values()) {
				initializer.expressions().add(createExpression(element));
			}
			return initializer;
		}
		return createLiteralExpression(resolveSemanticValue(value));
	}

	@SuppressWarnings("unchecked")
	private Expression createLiteralExpression(SemanticPlanValue value) throws CoreException {
		if (value instanceof SemanticPlanValue.StringValue string) {
			StringLiteral literal= ast().newStringLiteral();
			literal.setLiteralValue(string.value());
			return literal;
		}
		if (value instanceof SemanticPlanValue.BooleanValue bool) {
			BooleanLiteral literal= ast().newBooleanLiteral(bool.value());
			return literal;
		}
		if (value instanceof SemanticPlanValue.IntegerValue integer) {
			NumberLiteral literal= ast().newNumberLiteral(Long.toString(integer.value()));
			return literal;
		}
		if (value instanceof SemanticPlanValue.ListValue list) {
			ArrayInitializer initializer= ast().newArrayInitializer();
			for (SemanticPlanValue element : list.values()) {
				initializer.expressions().add(createLiteralExpression(element));
			}
			return initializer;
		}
		throw failure("Semantic-plan node references cannot be emitted as Java expressions"); //$NON-NLS-1$
	}

	public CoreException failure(String message) {
		return failure(message, null);
	}

	private static CoreException failure(String message, Throwable cause) {
		return new CoreException(new Status(IStatus.ERROR, "sandbox_common", message, cause)); //$NON-NLS-1$
	}
}
