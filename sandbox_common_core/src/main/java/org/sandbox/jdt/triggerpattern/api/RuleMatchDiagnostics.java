/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0, available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.Type;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor.TransformationResult;
import org.sandbox.jdt.triggerpattern.api.GuardContext.UnknownSemanticRequirement;
import org.sandbox.jdt.triggerpattern.api.GuardExpression.Evaluation;
import org.sandbox.jdt.triggerpattern.api.GuardExpression.TruthValue;

/**
 * Read-only explanations of structurally matching candidates on a current AST.
 *
 * <p>Only structurally similar candidates are considered, and selection and count
 * limits bound the returned details. Replacements come from the ordinary batch
 * processor; this service never authorizes a change that processor rejected.
 * Guard success is not a proof of behavioral equivalence, compilation, or a
 * completed project migration.</p>
 *
 * <p>The caller owns the current AST and hint configuration and must keep both
 * unchanged during {@link #explain}. Candidates retain the original match and
 * AST references so node identity and resolved bindings remain available. The
 * returned lists and excerpts are bounded snapshots; they do not isolate or
 * freeze the underlying JDT tree. The service reads the current configuration
 * for each call and does not rewrite that tree.</p>
 */
public final class RuleMatchDiagnostics {
	private static final int MAX_DESCRIPTION_CHARS= 2048;
	private static final int MAX_SOURCE_CHARS= 1200;
	private static final int MAX_BINDING_CHARS= 240;
	private static final int MAX_ENTRIES= 64;
	private static final String TRUNCATED= "… [truncated]"; //$NON-NLS-1$

	/** One evaluated guard, or an alternative not reached by ordered selection. */
	public record GuardCheck(String condition, TruthValue truthValue, boolean evaluated) {
		public GuardCheck {
			condition= clip(condition, MAX_BINDING_CHARS);
		}
	}

	/** A placeholder's original node/range and bounded source/semantic excerpts. */
	public record BindingDetail(String placeholder, ASTNode originalNode, String source,
			int offset, int length, String resolvedType, String resolvedMethod) {
		public BindingDetail {
			placeholder= clip(placeholder, MAX_BINDING_CHARS);
			source= clip(source, MAX_BINDING_CHARS);
			resolvedType= clip(resolvedType, MAX_BINDING_CHARS);
			resolvedMethod= clip(resolvedMethod, MAX_BINDING_CHARS);
		}
	}

	/**
	 * One structural candidate, including rejected and unknown cases. Source and
	 * diagnostic fields are bounded excerpts; the match retains original ranges
	 * and {@code plannedReplacement} remains the exact batch processor result.
	 */
	public record Candidate(TransformationRule rule, Match match, String originalSource,
			String plannedReplacement, GuardCheck sourceGuard, List<GuardCheck> alternatives,
			List<BindingDetail> bindings, String matchedMethod,
			List<UnknownSemanticRequirement> unknownSemanticRequirements, boolean batchAccepted) {
		public Candidate {
			originalSource= clip(originalSource, MAX_SOURCE_CHARS);
			matchedMethod= clip(matchedMethod, MAX_BINDING_CHARS);
			alternatives= List.copyOf(alternatives.subList(0, Math.min(MAX_ENTRIES, alternatives.size())));
			bindings= List.copyOf(bindings.subList(0, Math.min(MAX_ENTRIES, bindings.size())));
			unknownSemanticRequirements= unknownSemanticRequirements.stream().limit(MAX_ENTRIES)
					.map(unknown -> new UnknownSemanticRequirement(clip(unknown.guardName(), MAX_BINDING_CHARS),
							clip(unknown.detail(), MAX_BINDING_CHARS))).toList();
		}

		/** Explanation limited to 2048 characters, including explicit truncation markers. */
		public String describe() {
			BoundedText text= new BoundedText(MAX_DESCRIPTION_CHARS);
			text.append("Source requirement: ").append(sourceGuard.truthValue()).append('\n'); //$NON-NLS-1$
			text.append(sourceGuard.condition()).append('\n');
			if (matchedMethod != null) {
				text.append("Resolved method: ").append(matchedMethod).append('\n'); //$NON-NLS-1$
			}
			text.append("Before:\n").append(clip(originalSource, 480)).append('\n'); //$NON-NLS-1$
			if (plannedReplacement != null) {
				text.append("Planned replacement:\n").append(clip(plannedReplacement, 480)).append('\n'); //$NON-NLS-1$
			} else {
				text.append(batchAccepted ? "No text replacement selected.\n" : "No change authorized by the batch processor.\n"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			for (UnknownSemanticRequirement unknown : unknownSemanticRequirements) {
				text.append("UNKNOWN: ").append(unknown.guardName()).append(": ").append(unknown.detail()).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
				if (text.isFull()) {
					break;
				}
			}
			for (BindingDetail binding : bindings) {
				text.append(binding.placeholder()).append(" = ").append(binding.source()); //$NON-NLS-1$
				text.append("; type: ").append(binding.resolvedType() == null ? "unresolved" : binding.resolvedType()); //$NON-NLS-1$ //$NON-NLS-2$
				if (binding.resolvedMethod() != null) {
					text.append("; method: ").append(binding.resolvedMethod()); //$NON-NLS-1$
				}
				text.append('\n');
				if (text.isFull()) {
					break;
				}
			}
			for (int i= 0; i < alternatives.size(); i++) {
				GuardCheck check= alternatives.get(i);
				text.append("Alternative ").append(i + 1).append(": ") //$NON-NLS-1$ //$NON-NLS-2$
						.append(check.evaluated() ? check.truthValue() : "not reached") //$NON-NLS-1$
						.append(" — ").append(check.condition()).append('\n'); //$NON-NLS-1$
				if (text.isFull()) {
					break;
				}
			}
			if (batchAccepted && !unknownSemanticRequirements.isEmpty()) {
				text.append("Unresolved observations are listed above; each guard's truth state determines selection.\n"); //$NON-NLS-1$
			}
			return text.toString();
		}
	}

	private final HintFile hintFile;

	public RuleMatchDiagnostics(HintFile hintFile) {
		this.hintFile= Objects.requireNonNull(hintFile);
	}

	/**
	 * Explains at most {@code maxCandidates} structural candidates, in source order.
	 * A negative selection offset selects the whole compilation unit; zero length
	 * selects candidates containing the cursor. Source must be the snapshot used
	 * to parse {@code cu}. Compiler options and semantic plan must match execution.
	 */
	public List<Candidate> explain(CompilationUnit cu, String source, int selectionOffset,
			int selectionLength, int maxCandidates, Map<String, String> compilerOptions,
			SemanticRewritePlan semanticPlan) {
		if (cu == null || maxCandidates <= 0) {
			return List.of();
		}
		Objects.requireNonNull(source, "Original source snapshot is required"); //$NON-NLS-1$
		if (selectionLength < 0) {
			throw new IllegalArgumentException("Selection length cannot be negative"); //$NON-NLS-1$
		}
		SemanticRewritePlan plan= semanticPlan == null ? SemanticRewritePlan.empty() : semanticPlan;
		BatchTransformationProcessor processor= new BatchTransformationProcessor(hintFile);
		var structural= processor.getPatternIndex().findAllMatches(cu);
		List<Map.Entry<TransformationRule, Match>> candidates= new ArrayList<>();
		structural.forEach((rule, matches) -> matches.stream()
				.filter(match -> selected(match, selectionOffset, selectionLength))
				.forEach(match -> candidates.add(Map.entry(rule, match))));
		candidates.sort(Comparator.comparingInt(entry -> entry.getValue().getOffset()));
		if (candidates.isEmpty()) {
			return List.of();
		}
		List<TransformationResult> transformations= processor.process(cu, compilerOptions, plan);
		boolean requiredBindings= hintFile.getBindingPolicy() == HintBindingPolicy.REQUIRED || !plan.isEmpty();
		List<Candidate> result= new ArrayList<>();
		for (var entry : candidates.subList(0, Math.min(maxCandidates, candidates.size()))) {
			TransformationRule rule= entry.getKey();
			boolean strict= requiredBindings || rule.alternatives().stream()
					.anyMatch(alternative -> alternative.hasRewrite() || alternative.isEmbeddedFix());
			Match match= entry.getValue();
			GuardContext context= GuardContext.fromMatch(match, cu, compilerOptions, plan);
			boolean unknownSource= !match.getUnknownSemanticRequirements().isEmpty();
			if (strict) {
				match.getUnknownSemanticRequirements().forEach(unknown -> context.markUnknown(unknown.guardName(), unknown.detail()));
			}
			Evaluation sourceEvaluation= rule.sourceGuard() == null ? new Evaluation(TruthValue.MATCH, true)
							: rule.sourceGuard().evaluateDetailed(context);
			if (unknownSource && sourceEvaluation.truthValue() == TruthValue.MATCH) {
				sourceEvaluation= new Evaluation(TruthValue.UNKNOWN, sourceEvaluation.compatibilityValue());
			}
			String sourceCondition= rule.sourceGuard() == null ? "No source guard" : guardText(rule.sourceGuard()); //$NON-NLS-1$
			if (unknownSource) {
				sourceCondition= "Concrete source API identity unresolved; " + sourceCondition; //$NON-NLS-1$
			}
			GuardCheck sourceCheck= new GuardCheck(sourceCondition, sourceEvaluation.truthValue(), true);
			boolean reached= sourceEvaluation.compatibilityValue()
					&& !(strict && sourceEvaluation.truthValue() == TruthValue.UNKNOWN);
			List<GuardCheck> alternatives= new ArrayList<>();
			for (RewriteAlternative alternative : rule.alternatives().subList(0, Math.min(MAX_ENTRIES, rule.alternatives().size()))) {
				String condition= alternative.isOtherwise() ? "otherwise" : guardText(alternative.condition()); //$NON-NLS-1$
				if (!reached) {
					alternatives.add(new GuardCheck(condition, TruthValue.UNKNOWN, false));
					continue;
				}
				Evaluation evaluation= alternative.isOtherwise() ? new Evaluation(TruthValue.MATCH, true)
						: alternative.condition().evaluateDetailed(context);
				alternatives.add(new GuardCheck(condition, evaluation.truthValue(), true));
				if (evaluation.compatibilityValue() || strict && evaluation.truthValue() == TruthValue.UNKNOWN) {
					reached= false;
				}
			}
			TransformationResult actual= transformations.stream().filter(transformation -> transformation.rule() == rule
					&& transformation.match().getOffset() == match.getOffset()
					&& transformation.match().getLength() == match.getLength()).findFirst().orElse(null);
			List<UnknownSemanticRequirement> unknowns= new ArrayList<>(match.getUnknownSemanticRequirements().stream().limit(MAX_ENTRIES).toList());
			context.getUnknownSemanticRequirements().stream().filter(unknown -> !unknowns.contains(unknown))
					.limit(MAX_ENTRIES - unknowns.size()).forEach(unknowns::add);
			if (actual != null) {
				for (UnknownSemanticRequirement unknown : actual.unknownSemanticRequirements()) {
					if (unknowns.size() == MAX_ENTRIES) {
						break;
					}
					if (!unknowns.contains(unknown)) {
						unknowns.add(unknown);
					}
				}
			}
			result.add(new Candidate(rule, match, snippet(source, match.getOffset(), match.getLength(), MAX_SOURCE_CHARS),
					actual == null ? null : actual.replacement(), sourceCheck, alternatives,
					bindingDetails(match, source), methodSignature(resolveMethod(match.getMatchedNode())),
					unknowns, actual != null && !actual.isSemanticUnknown()));
		}
		return List.copyOf(result);
	}

	private static String guardText(GuardExpression guard) {
		BoundedText text= new BoundedText(MAX_BINDING_CHARS);
		appendGuard(text, guard);
		return text.toString();
	}

	private static void appendGuard(BoundedText text, GuardExpression guard) {
		if (text.isFull()) {
			return;
		}
		switch (guard) {
		case GuardExpression.FunctionCall call -> {
			if ("instanceof".equals(call.name()) && call.args().size() == 2) { //$NON-NLS-1$
				text.append(call.args().get(0)).append(" instanceof ").append(call.args().get(1)); //$NON-NLS-1$
			} else {
				text.append(call.name()).append('(');
				for (int i= 0; i < call.args().size() && !text.isFull(); i++) {
					if (i > 0) {
						text.append(", "); //$NON-NLS-1$
					}
					text.append(call.args().get(i));
				}
				text.append(')');
			}
		}
		case GuardExpression.And and -> {
			text.append('(');
			appendGuard(text, and.left());
			text.append(" && "); //$NON-NLS-1$
			appendGuard(text, and.right());
			text.append(')');
		}
		case GuardExpression.Or or -> {
			text.append('(');
			appendGuard(text, or.left());
			text.append(" || "); //$NON-NLS-1$
			appendGuard(text, or.right());
			text.append(')');
		}
		case GuardExpression.Not not -> {
			text.append("!("); //$NON-NLS-1$
			appendGuard(text, not.operand());
			text.append(')');
		}
		}
	}

	private static boolean selected(Match match, int offset, int length) {
		if (offset < 0) {
			return true;
		}
		long end= (long) match.getOffset() + match.getLength();
		return length == 0 ? match.getOffset() <= offset && offset < end
				: match.getOffset() < (long) offset + length && offset < end;
	}

	private static List<BindingDetail> bindingDetails(Match match, String source) {
		List<BindingDetail> details= new ArrayList<>();
		for (var binding : match.getBindings().entrySet().stream().sorted(Map.Entry.comparingByKey()).limit(MAX_ENTRIES).toList()) {
			if (details.size() == MAX_ENTRIES) {
				break;
			}
			if (binding.getValue() instanceof ASTNode node) {
				details.add(bindingDetail(binding.getKey(), node, source));
			} else if (binding.getValue() instanceof List<?> nodes) {
				for (int i= 0; i < nodes.size() && details.size() < MAX_ENTRIES; i++) {
					if (nodes.get(i) instanceof ASTNode node) {
						details.add(bindingDetail(binding.getKey() + "[" + i + "]", node, source)); //$NON-NLS-1$ //$NON-NLS-2$
					}
				}
			}
		}
		return details;
	}

	private static BindingDetail bindingDetail(String name, ASTNode node, String source) {
		ITypeBinding type= switch (node) {
		case AbstractTypeDeclaration value -> value.resolveBinding();
		case Name value -> value.resolveTypeBinding();
		case Expression value -> value.resolveTypeBinding();
		case Type value -> value.resolveBinding();
		default -> null;
		};
		String excerpt= "$this".equals(name) && node instanceof AbstractTypeDeclaration enclosing //$NON-NLS-1$
				? "enclosing type " + clip(type == null || type.isRecovered() ? enclosing.getName().getIdentifier() //$NON-NLS-1$
						: type.getErasure().getQualifiedName(), MAX_BINDING_CHARS - 15)
				: snippet(source, node.getStartPosition(), node.getLength(), MAX_BINDING_CHARS);
		return new BindingDetail(name, node, excerpt,
				node.getStartPosition(), node.getLength(), type == null || type.isRecovered() ? null : type.getQualifiedName(),
				methodSignature(resolveMethod(node)));
	}

	private static IMethodBinding resolveMethod(ASTNode node) {
		return switch (node) {
		case MethodInvocation invocation -> invocation.resolveMethodBinding();
		case ClassInstanceCreation creation -> creation.resolveConstructorBinding();
		case MethodDeclaration declaration -> declaration.resolveBinding();
		default -> null;
		};
	}

	private static String methodSignature(IMethodBinding binding) {
		if (binding == null || binding.isRecovered()) {
			return null;
		}
		IMethodBinding declaration= binding.getMethodDeclaration();
		ITypeBinding declaringClass= declaration.getDeclaringClass();
		BoundedText text= new BoundedText(MAX_BINDING_CHARS);
		text.append(declaringClass == null ? "?" : declaringClass.getErasure().getQualifiedName()) //$NON-NLS-1$
				.append('#').append(declaration.getName()).append('(');
		ITypeBinding[] parameters= declaration.getParameterTypes();
		for (int i= 0; i < parameters.length && !text.isFull(); i++) {
			if (i > 0) {
				text.append(", "); //$NON-NLS-1$
			}
			text.append(parameters[i].getQualifiedName());
		}
		return text.append(')').toString();
	}

	private static String snippet(String source, int offset, int length, int limit) {
		if (offset < 0 || length < 0 || (long) offset + length > source.length()) {
			return "[source unavailable for this AST node]"; //$NON-NLS-1$
		}
		return new BoundedText(limit).append(source, offset, offset + length).toString();
	}

	private static String clip(String value, int limit) {
		return value == null || value.length() <= limit ? value : new BoundedText(limit).append(value).toString();
	}

	/** Appends only a bounded range, without allocating the full source substring. */
	private static final class BoundedText {
		private final int limit;
		private final StringBuilder value;
		private boolean truncated;

		BoundedText(int limit) {
			this.limit= limit;
			value= new StringBuilder(limit);
		}

		BoundedText append(Object text) {
			String string= String.valueOf(text);
			return append(string, 0, string.length());
		}

		BoundedText append(CharSequence source, int start, int end) {
			if (truncated) {
				return this;
			}
			int remaining= limit - value.length();
			if (end - start <= remaining) {
				value.append(source, start, end);
			} else {
				int prefix= Math.max(0, remaining - TRUNCATED.length());
				value.append(source, start, start + prefix);
				value.setLength(Math.min(value.length(), limit - TRUNCATED.length()));
				value.append(TRUNCATED);
				truncated= true;
			}
			return this;
		}

		boolean isFull() {
			return truncated;
		}

		@Override
		public String toString() {
			return value.toString();
		}
	}
}
