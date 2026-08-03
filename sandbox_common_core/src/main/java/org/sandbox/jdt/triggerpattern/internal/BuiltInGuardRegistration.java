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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.sandbox.jdt.triggerpattern.api.GuardContext;
import org.sandbox.jdt.triggerpattern.api.GuardFunction;
import org.sandbox.jdt.triggerpattern.api.SemanticPlanValue;
import org.sandbox.jdt.triggerpattern.api.SemanticPlanValue.ListValue;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan.NodeKey;

/** Canonical registration entry point for built-in guard functions. */
public final class BuiltInGuardRegistration {

	private BuiltInGuardRegistration() {
	}

	/** Registers built-in guards with strict plan-aware migration guards. */
	public static void registerAll(Map<String, GuardFunction> guards) {
		BuiltInGuards.registerAll(guards);
		guards.put("instanceof", BuiltInGuardRegistration::evaluateInstanceOf); //$NON-NLS-1$
		guards.put("subtypeOf", BuiltInGuardRegistration::evaluateSubtypeOf); //$NON-NLS-1$
		guards.put("genericTypeIs", BuiltInGuardRegistration::evaluateGenericTypeIs); //$NON-NLS-1$
		guards.put("plannedRole", BuiltInGuardRegistration::evaluatePlannedRole); //$NON-NLS-1$
		guards.put("enclosingPlannedRole", BuiltInGuardRegistration::evaluateEnclosingPlannedRole); //$NON-NLS-1$
		guards.put("plannedValue", BuiltInGuardRegistration::evaluatePlannedValue); //$NON-NLS-1$
		guards.put("enclosingPlannedValue", BuiltInGuardRegistration::evaluateEnclosingPlannedValue); //$NON-NLS-1$
		guards.put("plannedNodeValue", BuiltInGuardRegistration::evaluatePlannedNodeValue); //$NON-NLS-1$
		guards.put("plannedListContains", BuiltInGuardRegistration::evaluatePlannedListContains); //$NON-NLS-1$
		guards.put("plannedRelation", BuiltInGuardRegistration::evaluatePlannedRelation); //$NON-NLS-1$
		guards.put("plannedRelationValue", BuiltInGuardRegistration::evaluatePlannedRelationValue); //$NON-NLS-1$
		guards.put("plannedOutgoingRelation", BuiltInGuardRegistration::evaluatePlannedOutgoingRelation); //$NON-NLS-1$
		guards.put("plannedIncomingRelation", BuiltInGuardRegistration::evaluatePlannedIncomingRelation); //$NON-NLS-1$
		guards.put("plannedRelationCount", BuiltInGuardRegistration::evaluatePlannedRelationCount); //$NON-NLS-1$
	}

	private static boolean evaluatePlannedRole(GuardContext context, Object... args) {
		PlanNodeArguments parsed= planNodeArguments(context, args, 1);
		return parsed != null && parsed.node() != null && context.getSemanticPlan().hasRole(parsed.node(),
				stripQuotes(argument(args, parsed.offset())));
	}

	private static boolean evaluateEnclosingPlannedRole(GuardContext context, Object... args) {
		PlanNodeArguments parsed= planNodeArguments(context, args, 1);
		return parsed != null && parsed.node() != null && context.getSemanticPlan().hasEnclosingRole(parsed.node(),
				stripQuotes(argument(args, parsed.offset())));
	}

	private static boolean evaluatePlannedValue(GuardContext context, Object... args) {
		PlanNodeArguments parsed= planNodeArguments(context, args, 2);
		return parsed != null && parsed.node() != null && context.getSemanticPlan().hasValue(parsed.node(),
				stripQuotes(argument(args, parsed.offset())), literal(args, parsed.offset() + 1));
	}

	private static boolean evaluateEnclosingPlannedValue(GuardContext context, Object... args) {
		PlanNodeArguments parsed= planNodeArguments(context, args, 2);
		return parsed != null && parsed.node() != null && context.getSemanticPlan().hasEnclosingValue(parsed.node(),
				stripQuotes(argument(args, parsed.offset())), literal(args, parsed.offset() + 1));
	}

	private static boolean evaluatePlannedNodeValue(GuardContext context, Object... args) {
		PlanNodeArguments parsed= planNodeArguments(context, args, 2);
		if (parsed == null || parsed.node() == null) {
			return false;
		}
		NodeKey expectedKey= NodeKey.from(context.getBinding(argument(args, parsed.offset() + 1)));
		return expectedKey != null && context.getSemanticPlan().hasValue(parsed.node(),
				stripQuotes(argument(args, parsed.offset())), SemanticPlanValue.node(expectedKey));
	}

	private static boolean evaluatePlannedListContains(GuardContext context, Object... args) {
		PlanNodeArguments parsed= planNodeArguments(context, args, 2);
		if (parsed == null || parsed.node() == null) {
			return false;
		}
		String name= stripQuotes(argument(args, parsed.offset()));
		SemanticPlanValue expected= literal(args, parsed.offset() + 1);
		return context.getSemanticPlan().value(parsed.node(), name)
				.filter(ListValue.class::isInstance)
				.map(ListValue.class::cast)
				.map(value -> value.values().contains(expected))
				.orElse(false);
	}

	private static boolean evaluatePlannedRelation(GuardContext context, Object... args) {
		PlanNodeArguments parsed= planNodeArguments(context, args, 2);
		if (parsed == null || parsed.node() == null) {
			return false;
		}
		ASTNode target= context.getBinding(argument(args, parsed.offset() + 1));
		return target != null && context.getSemanticPlan().hasRelation(parsed.node(),
				stripQuotes(argument(args, parsed.offset())), target);
	}

	private static boolean evaluatePlannedRelationValue(GuardContext context, Object... args) {
		PlanNodeArguments parsed= planNodeArguments(context, args, 4);
		if (parsed == null || parsed.node() == null) {
			return false;
		}
		ASTNode target= context.getBinding(argument(args, parsed.offset() + 1));
		return target != null && context.getSemanticPlan().hasRelationValue(parsed.node(),
				stripQuotes(argument(args, parsed.offset())), target,
				stripQuotes(argument(args, parsed.offset() + 2)), literal(args, parsed.offset() + 3));
	}

	private static boolean evaluatePlannedOutgoingRelation(GuardContext context, Object... args) {
		PlanNodeArguments parsed= planNodeArguments(context, args, 1);
		return parsed != null && parsed.node() != null && !context.getSemanticPlan()
				.outgoing(parsed.node(), stripQuotes(argument(args, parsed.offset()))).isEmpty();
	}

	private static boolean evaluatePlannedIncomingRelation(GuardContext context, Object... args) {
		PlanNodeArguments parsed= planNodeArguments(context, args, 1);
		return parsed != null && parsed.node() != null && !context.getSemanticPlan()
				.incoming(parsed.node(), stripQuotes(argument(args, parsed.offset()))).isEmpty();
	}

	private static boolean evaluatePlannedRelationCount(GuardContext context, Object... args) {
		PlanNodeArguments parsed= planNodeArguments(context, args, 2);
		if (parsed == null || parsed.node() == null) {
			return false;
		}
		try {
			return context.getSemanticPlan()
					.outgoing(parsed.node(), stripQuotes(argument(args, parsed.offset()))).size()
					== Integer.parseInt(argument(args, parsed.offset() + 1));
		} catch (NumberFormatException e) {
			return false;
		}
	}

	/**
	 * Resolves the optional leading bound-node argument shared by all plan guards.
	 * Extra or missing arguments are rejected rather than ignored.
	 */
	private static PlanNodeArguments planNodeArguments(GuardContext context, Object[] args, int implicitArity) {
		if (args == null) {
			return null;
		}
		if (args.length == implicitArity) {
			return new PlanNodeArguments(context.getMatchedNode(), 0);
		}
		if (args.length == implicitArity + 1) {
			return new PlanNodeArguments(context.getBinding(argument(args, 0)), 1);
		}
		return null;
	}

	private static boolean evaluateInstanceOf(GuardContext context, Object... args) {
		if (args.length < 2) {
			return false;
		}
		ASTNode node= context.getBinding(argument(args, 0));
		if (node == null) {
			return false;
		}
		ITypeBinding binding= resolveTypeBinding(node);
		if (binding == null) {
			context.markUnknown("instanceof", //$NON-NLS-1$
					"Cannot resolve the type of " + argument(args, 0)); //$NON-NLS-1$
			return true;
		}
		String expectedType= stripQuotes(argument(args, 1));
		if (expectedType.endsWith("[]") && expectedType.length() > 2) { //$NON-NLS-1$
			return binding.isArray() && matchesTypeName(binding.getElementType(),
					expectedType.substring(0, expectedType.length() - 2));
		}
		return matchesTypeName(binding, expectedType);
	}

	private static boolean evaluateSubtypeOf(GuardContext context, Object... args) {
		if (args.length < 2) {
			return false;
		}
		ASTNode node= context.getBinding(argument(args, 0));
		if (node == null) {
			return false;
		}
		ITypeBinding binding= resolveTypeBinding(node);
		if (binding == null) {
			context.markUnknown("subtypeOf", //$NON-NLS-1$
					"Cannot resolve the type hierarchy of " + argument(args, 0)); //$NON-NLS-1$
			return true;
		}
		return isSubtypeOf(binding, stripQuotes(argument(args, 1)), new HashSet<>());
	}

	private static boolean isSubtypeOf(ITypeBinding binding, String expectedType, Set<String> visited) {
		if (binding == null || binding.isRecovered()) {
			return false;
		}
		ITypeBinding declaration= binding.getTypeDeclaration();
		String key= declaration.getKey();
		if (key != null && !visited.add(key)) {
			return false;
		}
		if (matchesTypeName(declaration, expectedType)) {
			return true;
		}
		if (isSubtypeOf(declaration.getSuperclass(), expectedType, visited)) {
			return true;
		}
		for (ITypeBinding iface : declaration.getInterfaces()) {
			if (isSubtypeOf(iface, expectedType, visited)) {
				return true;
			}
		}
		return false;
	}

	private static boolean evaluateGenericTypeIs(GuardContext context, Object... args) {
		if (args.length < 3) {
			return false;
		}
		ASTNode node= context.getBinding(argument(args, 0));
		if (node == null) {
			return false;
		}
		int index;
		try {
			index= Integer.parseInt(argument(args, 1));
		} catch (NumberFormatException e) {
			return false;
		}
		ITypeBinding binding= resolveTypeBinding(node);
		if (binding == null) {
			context.markUnknown("genericTypeIs", //$NON-NLS-1$
					"Cannot resolve generic type arguments for " + argument(args, 0)); //$NON-NLS-1$
			return true;
		}
		ITypeBinding[] arguments= binding.getTypeArguments();
		return index >= 0 && index < arguments.length
				&& matchesTypeName(arguments[index], stripQuotes(argument(args, 2)));
	}

	private static SemanticPlanValue literal(Object[] args, int index) {
		return SemanticPlanValue.fromGuardLiteral(argument(args, index));
	}

	private static String argument(Object[] args, int index) {
		if (args == null || index < 0 || index >= args.length || args[index] == null) {
			return ""; //$NON-NLS-1$
		}
		return args[index].toString();
	}

	private static ITypeBinding resolveTypeBinding(ASTNode node) {
		if (node instanceof Expression expression) {
			return usableTypeBinding(expression.resolveTypeBinding());
		}
		if (node instanceof Type type) {
			return usableTypeBinding(type.resolveBinding());
		}
		if (node instanceof SingleVariableDeclaration declaration) {
			IVariableBinding binding= declaration.resolveBinding();
			return usableTypeBinding(binding == null ? declaration.getType().resolveBinding() : binding.getType());
		}
		if (node instanceof VariableDeclarationFragment fragment) {
			IVariableBinding binding= fragment.resolveBinding();
			return usableTypeBinding(binding == null ? null : binding.getType());
		}
		return null;
	}

	private static ITypeBinding usableTypeBinding(ITypeBinding binding) {
		return binding == null || binding.isRecovered() ? null : binding;
	}

	private static boolean matchesTypeName(ITypeBinding binding, String expectedType) {
		if (binding == null || binding.isRecovered() || expectedType == null || expectedType.isBlank()) {
			return false;
		}
		ITypeBinding declaration= binding.getTypeDeclaration();
		String qualifiedName= declaration.getQualifiedName();
		return expectedType.equals(qualifiedName) || expectedType.equals(declaration.getName());
	}

	private static String stripQuotes(String value) {
		String stripped= value == null ? "" : value.trim(); //$NON-NLS-1$
		if (stripped.length() >= 2 && stripped.startsWith("\"") && stripped.endsWith("\"")) { //$NON-NLS-1$ //$NON-NLS-2$
			return stripped.substring(1, stripped.length() - 1);
		}
		return stripped;
	}

	private record PlanNodeArguments(ASTNode node, int offset) {
	}
}
