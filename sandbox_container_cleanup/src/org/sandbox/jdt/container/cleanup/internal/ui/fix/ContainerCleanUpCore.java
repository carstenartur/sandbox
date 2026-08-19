/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.container.cleanup.internal.ui.fix;

import static org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpOptions.APPEND_ARRAY_TO_LIST;
import static org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpOptions.CLEANUP;
import static org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpOptions.UNIQUE_SEQUENCE_TO_SET;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

import org.sandbox.jdt.cleanup.multifile.ContainerCleanUpFix;
import org.sandbox.jdt.container.analysis.AppendOnlyArraySeedDetector;
import org.sandbox.jdt.container.analysis.ContainerBridgePolicyPlanner;
import org.sandbox.jdt.container.analysis.ContainerContractInferrer;
import org.sandbox.jdt.container.analysis.ContainerLocalRewritePlanner;
import org.sandbox.jdt.container.analysis.ContainerMigrationReadinessPlanner;
import org.sandbox.jdt.container.analysis.ContainerSignatureAtomicityPlanner;
import org.sandbox.jdt.container.analysis.LocalArrayUsageAnalyzer;
import org.sandbox.jdt.container.analysis.LocalUniqueSequenceAnalyzer;
import org.sandbox.jdt.container.analysis.UniqueSequenceContractInferrer;
import org.sandbox.jdt.container.analysis.UniqueSequenceLocalRewritePlanner;
import org.sandbox.jdt.container.api.ContainerFlowComponent;
import org.sandbox.jdt.container.api.ContainerFlowGraph.ClosureStatus;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowNode;
import org.sandbox.jdt.container.api.ContainerFlowGraph.NodeKind;
import org.sandbox.jdt.container.api.ContainerLocalRewritePlan;
import org.sandbox.jdt.container.api.ContainerUsageProfile;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan;
import org.sandbox.jdt.container.api.UniqueSequenceLocalRewritePlan;

/**
 * Production cleanup adapter for the first fail-closed semantic container
 * migrations. Broader signature, override and concurrency migrations remain
 * explicit project refactorings until their coordinated UI is wired.
 */
public final class ContainerCleanUpCore extends AbstractCleanUp {

	private final AppendOnlyArraySeedDetector arraySeedDetector=
			new AppendOnlyArraySeedDetector();
	private final LocalArrayUsageAnalyzer arrayUsageAnalyzer=
			new LocalArrayUsageAnalyzer();
	private final ContainerContractInferrer arrayInferrer=
			new ContainerContractInferrer();
	private final ContainerLocalRewritePlanner arrayRewritePlanner=
			new ContainerLocalRewritePlanner();
	private final LocalUniqueSequenceAnalyzer uniqueSequenceAnalyzer=
			new LocalUniqueSequenceAnalyzer();
	private final UniqueSequenceContractInferrer uniqueSequenceInferrer=
			new UniqueSequenceContractInferrer();
	private final UniqueSequenceLocalRewritePlanner uniqueSequenceRewritePlanner=
			new UniqueSequenceLocalRewritePlanner();
	private final ContainerSignatureAtomicityPlanner signaturePlanner=
			new ContainerSignatureAtomicityPlanner();
	private final ContainerBridgePolicyPlanner bridgePlanner=
			new ContainerBridgePolicyPlanner();
	private final ContainerMigrationReadinessPlanner readinessPlanner=
			new ContainerMigrationReadinessPlanner();

	public ContainerCleanUpCore(Map<String, String> options) {
		super(options);
	}

	public ContainerCleanUpCore() {
	}

	@Override
	public CleanUpRequirements getRequirements() {
		return new CleanUpRequirements(requireAST(), false, false, null);
	}

	public boolean requireAST() {
		return isEnabled(CLEANUP)
				&& (isEnabled(APPEND_ARRAY_TO_LIST)
						|| isEnabled(UNIQUE_SEQUENCE_TO_SET));
	}

	@Override
	public ICleanUpFix createFix(CleanUpContext context) throws CoreException {
		CompilationUnit root= context.getAST();
		ICompilationUnit unit= context.getCompilationUnit();
		if (root == null || unit == null || !requireAST()) {
			return null;
		}

		Map<String, ContainerLocalRewritePlan> arrayPlans= new LinkedHashMap<>();
		if (isEnabled(APPEND_ARRAY_TO_LIST)) {
			for (ContainerUsageProfile seed : arraySeedDetector.findSeeds(root)) {
				ContainerUsageProfile profile= arrayUsageAnalyzer.analyze(root, seed);
				arrayInferrer.infer(profile).ifPresent(recommendation -> {
					ContainerFlowComponent component= localComponent(
							unit.getHandleIdentifier(), profile);
					var signaturePlan= signaturePlanner.plan(
							component,
							ResolvedContainerFlowSearchPlan.empty(),
							recommendation);
					var bridgePlan= bridgePlanner.plan(signaturePlan, recommendation);
					var readiness= readinessPlanner.plan(
							component, recommendation, signaturePlan, bridgePlan);
					arrayRewritePlanner.plan(component, recommendation, readiness)
							.plan()
							.ifPresent(plan -> arrayPlans.putIfAbsent(
									plan.bindingKey(), plan));
				});
			}
		}

		Map<String, UniqueSequenceLocalRewritePlan> uniquePlans=
				new LinkedHashMap<>();
		if (isEnabled(UNIQUE_SEQUENCE_TO_SET)) {
			for (ContainerUsageProfile profile : uniqueSequenceAnalyzer.analyze(root)) {
				uniqueSequenceInferrer.infer(profile).ifPresent(recommendation -> {
					ContainerFlowComponent component= localComponent(
							unit.getHandleIdentifier(), profile);
					var signaturePlan= signaturePlanner.plan(
							component,
							ResolvedContainerFlowSearchPlan.empty(),
							recommendation);
					var bridgePlan= bridgePlanner.plan(signaturePlan, recommendation);
					var readiness= readinessPlanner.plan(
							component, recommendation, signaturePlan, bridgePlan);
					uniqueSequenceRewritePlanner.plan(
							unit.getHandleIdentifier(), recommendation, readiness)
							.plan()
							.ifPresent(plan -> uniquePlans.putIfAbsent(
									plan.bindingKey(), plan));
				});
			}
		}

		return ContainerCleanUpFix.create(
				unit, root, arrayPlans.values(), uniquePlans.values());
	}

	@Override
	public String[] getStepDescriptions() {
		if (!isEnabled(CLEANUP)) {
			return new String[0];
		}
		List<String> descriptions= new ArrayList<>();
		if (isEnabled(APPEND_ARRAY_TO_LIST)) {
			descriptions.add(
					"Replace strictly local append-only arrays with lists"); //$NON-NLS-1$
		}
		if (isEnabled(UNIQUE_SEQUENCE_TO_SET)) {
			descriptions.add(
					"Replace strictly local manually unique lists with ordered sets"); //$NON-NLS-1$
		}
		return descriptions.toArray(String[]::new);
	}

	@Override
	public String getPreview() {
		StringBuilder preview= new StringBuilder();
		if (isEnabled(CLEANUP) && isEnabled(APPEND_ARRAY_TO_LIST)) {
			preview.append("List<String> values = new ArrayList<>();\n"); //$NON-NLS-1$
			preview.append("values.add(value);\n"); //$NON-NLS-1$
		} else {
			preview.append("String[] values = new String[0];\n"); //$NON-NLS-1$
			preview.append(
					"values = Arrays.copyOf(values, values.length + 1);\n"); //$NON-NLS-1$
		}
		preview.append('\n');
		if (isEnabled(CLEANUP) && isEnabled(UNIQUE_SEQUENCE_TO_SET)) {
			preview.append("Set<String> names = new LinkedHashSet<>();\n"); //$NON-NLS-1$
			preview.append("names.add(name);\n"); //$NON-NLS-1$
		} else {
			preview.append("List<String> names = new ArrayList<>();\n"); //$NON-NLS-1$
			preview.append(
					"if (!names.contains(name)) names.add(name);\n"); //$NON-NLS-1$
		}
		return preview.toString();
	}

	static ContainerFlowComponent localComponent(
			String compilationUnitHandle,
			ContainerUsageProfile profile) {
		FlowNode root= new FlowNode(
				"variable:" + profile.identity().stableId(), //$NON-NLS-1$
				NodeKind.LOCAL_VARIABLE,
				profile.identity().bindingKey(),
				"", //$NON-NLS-1$
				compilationUnitHandle,
				"", //$NON-NLS-1$
				-1,
				true,
				profile.identity().sourceStart(),
				profile.identity().sourceLength());
		return new ContainerFlowComponent(
				root.stableId(),
				List.of(root),
				List.of(),
				ClosureStatus.LOCAL_CLOSED,
				List.of());
	}
}
