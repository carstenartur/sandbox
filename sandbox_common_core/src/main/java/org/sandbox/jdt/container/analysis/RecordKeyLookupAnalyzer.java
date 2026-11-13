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
package org.sandbox.jdt.container.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.ThisExpression;

import org.sandbox.jdt.container.api.ContainerRecommendation;
import org.sandbox.jdt.container.api.ContainerRecommendation.AutomationLevel;
import org.sandbox.jdt.container.api.ContainerRecommendation.Confidence;
import org.sandbox.jdt.container.api.ContainerRecommendation.ContractAssessment;
import org.sandbox.jdt.container.api.ContainerRecommendation.ContractProperty;
import org.sandbox.jdt.container.api.ContainerRecommendation.Preservation;
import org.sandbox.jdt.container.api.ContainerShape;
import org.sandbox.jdt.container.api.ContainerUsageProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AccessProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AliasingContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ConcurrencyProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ContainerIdentity;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ElementDomain;
import org.sandbox.jdt.container.api.ContainerUsageProfile.EscapeLevel;
import org.sandbox.jdt.container.api.ContainerUsageProfile.MutationLifecycle;
import org.sandbox.jdt.container.api.ContainerUsageProfile.NullContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.OrderRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.UniquenessRequirement;
import org.sandbox.jdt.container.api.TargetContainerContract;
import org.sandbox.jdt.container.api.TargetContainerContract.Mutability;
import org.sandbox.jdt.container.api.UsageEvidence;
import org.sandbox.jdt.container.api.UsageEvidence.Kind;

/** Finds repeated first-match scans over the same list declaration and generated String record accessor. */
public final class RecordKeyLookupAnalyzer {

	private record Lookup(String container, String accessor) {
	}
	private record Observations(ContainerIdentity identity, EscapeLevel escape, List<UsageEvidence> scans) {
	}

	private RecordKeyLookupAnalyzer() {
	}

	/** Returns one report-only seed for each list and key accessor seen at distinct lookup sites. */
	public static List<ContainerUsageProfile> analyze(CompilationUnit root) {
		Map<Lookup, Observations> observations= new LinkedHashMap<>();
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(EnhancedForStatement loop) {
				observe(root, loop, observations);
				return true;
			}
		});
		return observations.values().stream().filter(value -> value.scans().size() >= 2)
				.map(RecordKeyLookupAnalyzer::profile).toList();
	}

	private static void observe(CompilationUnit root, EnhancedForStatement loop, Map<Lookup, Observations> observations) {
		IVariableBinding container= listBinding(root, loop.getExpression());
		IVariableBinding element= loop.getParameter().resolveBinding();
		if (!resolved(container) || !resolved(element) || !resolved(container.getType())
				|| !"java.util.List".equals(container.getType().getErasure().getQualifiedName()) //$NON-NLS-1$
				|| container.getType().getTypeArguments().length != 1
				|| !container.getType().getTypeArguments()[0].isEqualTo(element.getType())) {
			return;
		}
		if (!(onlyStatement(loop.getBody()) instanceof IfStatement branch) || branch.getElseStatement() != null
				|| !(onlyStatement(branch.getThenStatement()) instanceof ReturnStatement result)
				|| !references(result.getExpression(), element)
				|| !(ContainerAstFacts.unwrap(branch.getExpression()) instanceof MethodInvocation equals)
				|| !isStringEquals(equals) || equals.arguments().size() != 1
				|| !(ContainerAstFacts.unwrap((Expression) equals.arguments().get(0)) instanceof MethodInvocation key)
				|| !references(key.getExpression(), element)) {
			return;
		}
		IMethodBinding accessor= key.resolveMethodBinding();
		if (!resolved(accessor) || !accessor.isSyntheticRecordMethod() || !accessor.getDeclaringClass().isRecord()
				|| !key.arguments().isEmpty() || !isString(accessor.getReturnType())
				|| ContainerAstFacts.variableBinding(equals.getExpression()).filter(IVariableBinding::isParameter).isEmpty()
				|| !isRecordComponent(accessor)) {
			return;
		}
		Expression source= loop.getExpression();
		String bindingKey= container.getVariableDeclaration().getKey();
		Lookup lookup= new Lookup(bindingKey, accessor.getMethodDeclaration().getKey());
		Observations group= observations.computeIfAbsent(lookup, unused -> new Observations(
				new ContainerIdentity(bindingKey, container.getName(), source.getStartPosition(), source.getLength()),
				escapeLevel(container),
				new ArrayList<>()));
		group.scans().add(new UsageEvidence(Kind.RECORD_KEY_LOOKUP,
				"Return the first list element whose %s() matches the requested String.".formatted(accessor.getName()), //$NON-NLS-1$
				loop.getStartPosition(), loop.getLength()));
	}

	private static EscapeLevel escapeLevel(IVariableBinding variable) {
		if (variable.isField()) {
			return EscapeLevel.FIELD;
		}
		return variable.isParameter() ? EscapeLevel.METHOD_BOUNDARY : EscapeLevel.UNKNOWN;
	}

	private static IVariableBinding directVariable(Expression source) {
		Expression expression= ContainerAstFacts.unwrap(source);
		if (expression instanceof SimpleName || expression instanceof FieldAccess field && currentInstance(field.getExpression())) {
			return ContainerAstFacts.variableBinding(expression).orElse(null);
		}
		return null;
	}

	private static boolean currentInstance(Expression expression) {
		return expression == null || expression instanceof ThisExpression self && self.getQualifier() == null;
	}

	private static IVariableBinding listBinding(CompilationUnit root, Expression source) {
		Expression expression= ContainerAstFacts.unwrap(source);
		if (!(expression instanceof MethodInvocation getter)) {
			return directVariable(expression);
		}
		IMethodBinding method= getter.resolveMethodBinding();
		if (!resolved(method) || !currentInstance(getter.getExpression()) || !getter.arguments().isEmpty()
				|| !(Modifier.isFinal(method.getDeclaringClass().getModifiers()) || Modifier.isFinal(method.getModifiers())
						|| Modifier.isPrivate(method.getModifiers()))
				|| !(root.findDeclaringNode(method.getMethodDeclaration()) instanceof MethodDeclaration declaration)
				|| declaration.getBody() == null || !(onlyStatement(declaration.getBody()) instanceof ReturnStatement result)
				|| result.getExpression() == null) {
			return null;
		}
		IVariableBinding field= directVariable(result.getExpression());
		return resolved(field) && field.isField() && Modifier.isFinal(field.getModifiers()) ? field : null;
	}

	private static Statement onlyStatement(Statement statement) {
		if (statement instanceof Block block && block.statements().size() == 1) {
			return (Statement) block.statements().get(0);
		}
		return statement;
	}

	private static boolean resolved(IBinding binding) {
		return binding != null && !binding.isRecovered() && binding.getKey() != null;
	}

	private static boolean references(Expression expression, IVariableBinding expected) {
		return expression != null && ContainerAstFacts.variableBinding(expression)
				.filter(binding -> binding.getVariableDeclaration().isEqualTo(expected.getVariableDeclaration())).isPresent();
	}

	private static boolean isString(ITypeBinding type) {
		return type != null && "java.lang.String".equals(type.getQualifiedName()); //$NON-NLS-1$
	}

	private static boolean isStringEquals(MethodInvocation invocation) {
		IMethodBinding method= invocation.resolveMethodBinding();
		return resolved(method) && "equals".equals(method.getName()) && isString(method.getDeclaringClass()) //$NON-NLS-1$
				&& invocation.getExpression() != null;
	}

	private static boolean isRecordComponent(IMethodBinding accessor) {
		for (IVariableBinding field : accessor.getDeclaringClass().getDeclaredFields()) {
			if (field.getName().equals(accessor.getName()) && !Modifier.isStatic(field.getModifiers())) {
				return true;
			}
		}
		return false;
	}

	private static ContainerUsageProfile profile(Observations observations) {
		return new ContainerUsageProfile(observations.identity(), ContainerShape.LIST, ElementDomain.REFERENCE,
				new AccessProfile(false, false, false, false, false, false, true),
				OrderRequirement.ENCOUNTER, UniquenessRequirement.UNKNOWN, MutationLifecycle.UNKNOWN, NullContract.UNKNOWN,
				AliasingContract.UNKNOWN, observations.escape(), ConcurrencyProfile.unknown(), AnalysisCompleteness.LOCAL_SEED,
				observations.scans());
	}

	static Optional<ContainerRecommendation> recommendation(ContainerUsageProfile profile) {
		if (profile.currentShape() != ContainerShape.LIST || !profile.access().keyLookup()
				|| profile.completeness() != AnalysisCompleteness.LOCAL_SEED
				|| profile.evidence().stream().filter(item -> item.kind() == Kind.RECORD_KEY_LOOKUP)
						.map(UsageEvidence::sourceStart).distinct().limit(2).count() != 2) {
			return Optional.empty();
		}
		TargetContainerContract target= new TargetContainerContract(ContainerShape.MAP, OrderRequirement.UNKNOWN,
				UniquenessRequirement.REQUIRED, Mutability.UNKNOWN, NullContract.UNKNOWN,
				"Consider a key index for repeated first-match scans; the list may still be needed by other callers."); //$NON-NLS-1$
		return Optional.of(new ContainerRecommendation(profile, target,
				ContainerRuleRegistry.find(ContainerRuleRegistry.RECORD_KEY_LOOKUP_MAP).orElseThrow(),
				Confidence.MEDIUM, AutomationLevel.REPORT_ONLY, List.of(
						new ContractAssessment(ContractProperty.SIGNATURES, Preservation.PRESERVED,
								"A supplementary index can retain existing list parameters, accessors and return types."), //$NON-NLS-1$
						new ContractAssessment(ContractProperty.ALIASING, Preservation.REQUIRES_PROOF,
								"Each match returns the original record instance, including to callers outside the compilation unit."), //$NON-NLS-1$
						new ContractAssessment(ContractProperty.ORDER, Preservation.REQUIRES_PROOF,
								"With duplicate keys the first matching entry in list order must remain the result."), //$NON-NLS-1$
						new ContractAssessment(ContractProperty.UNIQUENESS, Preservation.CHANGED,
								"A single-value index has one entry per key; the list permits duplicates."), //$NON-NLS-1$
						new ContractAssessment(ContractProperty.MUTABILITY, Preservation.REQUIRES_PROOF,
								"Any index must track insertion, removal, replacement and reordering through every alias."), //$NON-NLS-1$
						new ContractAssessment(ContractProperty.NULLS, Preservation.REQUIRES_PROOF,
								"Preserve null element and null query exceptions, including queries on an empty list."), //$NON-NLS-1$
						new ContractAssessment(ContractProperty.CONCURRENCY, Preservation.REQUIRES_PROOF,
								"Publication and concurrent reads or writes require separate analysis.")))); //$NON-NLS-1$
	}
}
