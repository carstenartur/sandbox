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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.api;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * AST model for guard expressions using a sealed interface pattern.
 * 
 * <p>Guard expressions are used to constrain pattern matches. They support
 * function calls, logical operators ({@code &&}, {@code ||}, {@code !}),
 * and parenthesized sub-expressions.</p>
 * 
 * <p>Examples:</p>
 * <ul>
 *   <li>{@code sourceVersionGE(11)}</li>
 *   <li>{@code $x instanceof String}</li>
 *   <li>{@code $x instanceof String && sourceVersionGE(11)}</li>
 *   <li>{@code !isStatic($x)}</li>
 * </ul>
 * 
 * @since 1.3.2
 */
public sealed interface GuardExpression
		permits GuardExpression.FunctionCall, GuardExpression.And, GuardExpression.Or, GuardExpression.Not {

	/** Semantic truth state independent from optional compatibility fallback. */
	enum TruthValue {
		MATCH,
		NO_MATCH,
		UNKNOWN
	}

	/**
	 * Detailed guard result.
	 *
	 * @param truthValue semantic three-valued result
	 * @param compatibilityValue historical boolean result used by optional hints
	 */
	record Evaluation(TruthValue truthValue, boolean compatibilityValue) {
	}
	
	/**
	 * Sets the guard function resolver used by {@link FunctionCall} to look up
	 * guard functions by name.
	 * 
	 * @param resolver a function that maps guard function names to implementations
	 */
	static void setGuardFunctionResolver(Function<String, GuardFunction> resolver) {
		GuardFunctionResolverHolder.setResolver(resolver);
	}
	
	/**
	 * Returns the current guard function resolver.
	 * 
	 * @return the resolver, or {@code null} if not set
	 */
	static Function<String, GuardFunction> getGuardFunctionResolver() {
		return GuardFunctionResolverHolder.getResolver();
	}
	
	/**
	 * Evaluates this guard expression against the given context.
	 * 
	 * @param ctx the guard context
	 * @return {@code true} if the guard condition is satisfied
	 */
	boolean evaluate(GuardContext ctx);

	/**
	 * Evaluates this expression with three-valued semantic tracking while
	 * retaining the historical boolean fallback for optional hints.
	 */
	default Evaluation evaluateDetailed(GuardContext ctx) {
		return switch (this) {
		case FunctionCall call -> evaluateFunction(call, ctx);
		case And and -> evaluateAnd(and, ctx);
		case Or or -> evaluateOr(or, ctx);
		case Not not -> evaluateNot(not, ctx);
		};
	}

	private static Evaluation evaluateFunction(FunctionCall call, GuardContext ctx) {
		int unknownBefore= ctx.unknownSemanticRequirementCount();
		boolean compatibility= call.evaluate(ctx);
		if (ctx.unknownSemanticRequirementCount() > unknownBefore) {
			return new Evaluation(TruthValue.UNKNOWN, compatibility);
		}
		return new Evaluation(compatibility ? TruthValue.MATCH : TruthValue.NO_MATCH,
				compatibility);
	}

	private static Evaluation evaluateAnd(And and, GuardContext ctx) {
		Evaluation left= and.left().evaluateDetailed(ctx);
		if (left.truthValue() == TruthValue.NO_MATCH) {
			return new Evaluation(TruthValue.NO_MATCH, false);
		}
		Evaluation right= and.right().evaluateDetailed(ctx);
		TruthValue truth= switch (left.truthValue()) {
		case MATCH -> right.truthValue();
		case UNKNOWN -> right.truthValue() == TruthValue.NO_MATCH
				? TruthValue.NO_MATCH : TruthValue.UNKNOWN;
		case NO_MATCH -> TruthValue.NO_MATCH;
		};
		return new Evaluation(truth,
				left.compatibilityValue() && right.compatibilityValue());
	}

	private static Evaluation evaluateOr(Or or, GuardContext ctx) {
		Evaluation left= or.left().evaluateDetailed(ctx);
		if (left.truthValue() == TruthValue.MATCH) {
			return new Evaluation(TruthValue.MATCH, true);
		}
		Evaluation right= or.right().evaluateDetailed(ctx);
		TruthValue truth= switch (left.truthValue()) {
		case NO_MATCH -> right.truthValue();
		case UNKNOWN -> right.truthValue() == TruthValue.MATCH
				? TruthValue.MATCH : TruthValue.UNKNOWN;
		case MATCH -> TruthValue.MATCH;
		};
		return new Evaluation(truth,
				left.compatibilityValue() || right.compatibilityValue());
	}

	private static Evaluation evaluateNot(Not not, GuardContext ctx) {
		Evaluation operand= not.operand().evaluateDetailed(ctx);
		TruthValue truth= switch (operand.truthValue()) {
		case MATCH -> TruthValue.NO_MATCH;
		case NO_MATCH -> TruthValue.MATCH;
		case UNKNOWN -> TruthValue.UNKNOWN;
		};
		return new Evaluation(truth, !operand.compatibilityValue());
	}
	
	/**
	 * A function call guard expression (e.g., {@code sourceVersionGE(11)},
	 * {@code $x instanceof String}).
	 * 
	 * <p>The {@code instanceof} expression is modeled as a function call with the name
	 * {@code "instanceof"}, the placeholder name as the first argument, and the type
	 * name as the second argument.</p>
	 * 
	 * @param name the function name
	 * @param args the function arguments
	 */
	record FunctionCall(String name, List<String> args) implements GuardExpression {
		
		/**
		 * Creates a function call guard expression.
		 * 
		 * @param name the function name
		 * @param args the function arguments
		 */
		public FunctionCall {
			Objects.requireNonNull(name, "Function name cannot be null"); //$NON-NLS-1$
			args = List.copyOf(args);
		}
		
		@Override
		public boolean evaluate(GuardContext ctx) {
			Function<String, GuardFunction> resolver = GuardFunctionResolverHolder.getResolver();
			GuardFunction fn = resolver != null ? resolver.apply(name) : null;
			if (fn == null) {
				throw new IllegalStateException("Unknown guard function: " + name); //$NON-NLS-1$
			}
			return fn.evaluate(ctx, args.toArray());
		}
	}
	
	/**
	 * Logical AND of two guard expressions.
	 * 
	 * @param left the left operand
	 * @param right the right operand
	 */
	record And(GuardExpression left, GuardExpression right) implements GuardExpression {
		
		/**
		 * Creates a logical AND guard expression.
		 * 
		 * @param left the left operand
		 * @param right the right operand
		 */
		public And {
			Objects.requireNonNull(left, "Left operand cannot be null"); //$NON-NLS-1$
			Objects.requireNonNull(right, "Right operand cannot be null"); //$NON-NLS-1$
		}
		
		@Override
		public boolean evaluate(GuardContext ctx) {
			return left.evaluate(ctx) && right.evaluate(ctx);
		}
	}
	
	/**
	 * Logical OR of two guard expressions.
	 * 
	 * @param left the left operand
	 * @param right the right operand
	 */
	record Or(GuardExpression left, GuardExpression right) implements GuardExpression {
		
		/**
		 * Creates a logical OR guard expression.
		 * 
		 * @param left the left operand
		 * @param right the right operand
		 */
		public Or {
			Objects.requireNonNull(left, "Left operand cannot be null"); //$NON-NLS-1$
			Objects.requireNonNull(right, "Right operand cannot be null"); //$NON-NLS-1$
		}
		
		@Override
		public boolean evaluate(GuardContext ctx) {
			return left.evaluate(ctx) || right.evaluate(ctx);
		}
	}
	
	/**
	 * Logical NOT of a guard expression.
	 * 
	 * @param operand the operand to negate
	 */
	record Not(GuardExpression operand) implements GuardExpression {
		
		/**
		 * Creates a logical NOT guard expression.
		 * 
		 * @param operand the operand
		 */
		public Not {
			Objects.requireNonNull(operand, "Operand cannot be null"); //$NON-NLS-1$
		}
		
		@Override
		public boolean evaluate(GuardContext ctx) {
			return !operand.evaluate(ctx);
		}
	}
}
