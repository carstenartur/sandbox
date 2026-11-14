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
package org.sandbox.jdt.ui.helper.views;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.sandbox.jdt.container.analysis.AppendOnlyArraySeedDetector;
import org.sandbox.jdt.container.analysis.ContainerContractInferrer;
import org.sandbox.jdt.container.analysis.LocalArrayUsageAnalyzer;
import org.sandbox.jdt.container.analysis.LocalDequeSequenceAnalyzer;
import org.sandbox.jdt.container.analysis.LocalEnumIndexedMembershipAnalyzer;
import org.sandbox.jdt.container.analysis.LocalUniqueSequenceAnalyzer;
import org.sandbox.jdt.container.analysis.RecordKeyLookupAnalyzer;
import org.sandbox.jdt.container.analysis.SynchronizedListIterationAnalyzer;
import org.sandbox.jdt.container.api.ConcurrencyProtocol.Evidence;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment.DiagnosticOnly;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment.RetainExistingLocking;
import org.sandbox.jdt.container.api.ContainerRecommendation;
import org.sandbox.jdt.container.api.ContainerRecommendation.AutomationLevel;
import org.sandbox.jdt.container.api.ContainerRecommendation.ContractAssessment;
import org.sandbox.jdt.container.api.ContainerUsageProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.TargetContainerContract;
import org.sandbox.jdt.container.api.UsageEvidence;

/**
 * Read-only adapter from the shared semantic container analysis to Usage View
 * presentation rows. It deliberately owns no rewrite, readiness or migration
 * policy.
 */
final class ContainerAnalysisService {

	private final AppendOnlyArraySeedDetector arraySeedDetector= new AppendOnlyArraySeedDetector();
	private final LocalArrayUsageAnalyzer arrayUsageAnalyzer= new LocalArrayUsageAnalyzer();
	private final LocalUniqueSequenceAnalyzer uniqueSequenceAnalyzer= new LocalUniqueSequenceAnalyzer();
	private final LocalDequeSequenceAnalyzer dequeSequenceAnalyzer= new LocalDequeSequenceAnalyzer();
	private final LocalEnumIndexedMembershipAnalyzer enumMembershipAnalyzer=
			new LocalEnumIndexedMembershipAnalyzer();
	private final SynchronizedListIterationAnalyzer synchronizedListAnalyzer= new SynchronizedListIterationAnalyzer();
	private final ContainerContractInferrer contractInferrer= new ContainerContractInferrer();

	List<ContainerAnalysisRow> analyze(ICompilationUnit unit, IProgressMonitor monitor) {
		checkCanceled(monitor);
		if (unit == null || !unit.exists()) {
			return List.of();
		}
		return analyze(parse(unit, monitor), unit.getPrimary().getHandleIdentifier(), monitor);
	}

	List<ContainerAnalysisRow> analyze(CompilationUnit root, String compilationUnitHandle) {
		return analyze(root, compilationUnitHandle, null);
	}

	List<ContainerAnalysisRow> analyze(CompilationUnit root, String compilationUnitHandle, IProgressMonitor monitor) {
		checkCanceled(monitor);
		if (root == null || compilationUnitHandle == null || compilationUnitHandle.isBlank()) {
			return List.of();
		}
		Map<String, ContainerUsageProfile> profiles= new LinkedHashMap<>();
		for (ContainerUsageProfile seed : arraySeedDetector.findSeeds(root)) {
			checkCanceled(monitor);
			addProfile(profiles, arrayUsageAnalyzer.analyze(root, seed));
		}
		checkCanceled(monitor);
		uniqueSequenceAnalyzer.analyze(root).forEach(profile -> addProfile(profiles, profile));
		checkCanceled(monitor);
		dequeSequenceAnalyzer.analyze(root).forEach(profile -> addProfile(profiles, profile));
		checkCanceled(monitor);
		enumMembershipAnalyzer.analyze(root).forEach(profile -> addProfile(profiles, profile));

		List<ContainerAnalysisRow> rows= new ArrayList<>();
		// Distinct accessors on the same list represent independent index candidates.
		RecordKeyLookupAnalyzer.analyze(root, () -> checkCanceled(monitor)).forEach(profile -> appendRows(compilationUnitHandle, rows, profile));
		profiles.values().forEach(profile -> appendRows(compilationUnitHandle, rows, profile));
		checkCanceled(monitor);
		for (DiagnosticOnly observation : synchronizedListAnalyzer.analyze(root)) {
			appendConcurrencyRows(compilationUnitHandle, rows, observation);
		}
		for (var assessment : synchronizedListAnalyzer.assessDrains(root)) {
			appendConcurrencyRows(compilationUnitHandle, rows, assessment);
		}
		checkCanceled(monitor);
		rows.sort(Comparator.comparingInt(ContainerAnalysisRow::sourceStart)
				.thenComparing(ContainerAnalysisRow::candidate)
				.thenComparing(ContainerAnalysisRow::evidenceKind));
		return List.copyOf(rows);
	}

	private void appendRows(String handle, List<ContainerAnalysisRow> rows, ContainerUsageProfile profile) {
		Optional<ContainerRecommendation> recommendation= contractInferrer.infer(profile);
		if (profile.evidence().isEmpty()) {
			rows.add(row(handle, profile, recommendation, null));
		}
		for (UsageEvidence evidence : profile.evidence()) {
			rows.add(row(handle, profile, recommendation, evidence));
		}
	}

	private static ContainerAnalysisRow concurrencyRow(String compilationUnitHandle,
			ConcurrencyProtocolAssessment observation, Evidence evidence) {
		boolean retain= observation instanceof RetainExistingLocking;
		return new ContainerAnalysisRow(compilationUnitHandle, observation.protocol().identity().displayName(),
				observation.protocol().summary().synchronization().name(),
				retain ? "Retain existing locking" : "No proven target", retain ? "HIGH" : "N/A", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				retain ? "NO_MIGRATION_RECOMMENDED" : AutomationLevel.REPORT_ONLY.name(), //$NON-NLS-1$
				evidence.kind().name(), evidence.summary(), String.join(System.lineSeparator(), observation.explanations()),
				evidence.sourceStart(), evidence.sourceLength());
	}

	private static void appendConcurrencyRows(String handle, List<ContainerAnalysisRow> rows, ConcurrencyProtocolAssessment assessment) {
		for (Evidence evidence : assessment.protocol().evidence()) {
			rows.add(concurrencyRow(handle, assessment, evidence));
		}
	}

	private static void addProfile(Map<String, ContainerUsageProfile> profiles,
			ContainerUsageProfile profile) {
		profiles.merge(profile.identity().stableId(), profile, ContainerAnalysisService::preferProfile);
	}

	private static ContainerUsageProfile preferProfile(
			ContainerUsageProfile existing, ContainerUsageProfile candidate) {
		int existingRank= completenessRank(existing.completeness());
		int candidateRank= completenessRank(candidate.completeness());
		if (candidateRank != existingRank) {
			return candidateRank > existingRank ? candidate : existing;
		}
		return candidate.evidence().size() > existing.evidence().size() ? candidate : existing;
	}

	private static int completenessRank(AnalysisCompleteness completeness) {
		return switch (completeness) {
			case FLOW_COMPLETE -> 4;
			case LOCAL_USAGE_COMPLETE -> 3;
			case LOCAL_SEED -> 2;
			case REJECTED -> 1;
		};
	}

	private static ContainerAnalysisRow row(String compilationUnitHandle,
			ContainerUsageProfile profile, Optional<ContainerRecommendation> recommendation,
			UsageEvidence evidence) {
		String target= recommendation.map(ContainerAnalysisService::targetSummary)
				.orElse("No proven target"); //$NON-NLS-1$
		String confidence= recommendation.map(value -> value.confidence().name())
				.orElse("N/A"); //$NON-NLS-1$
		String status= recommendation.map(value -> value.automationLevel().name())
				.orElseGet(() -> profile.completeness().name());
		String semanticSummary= recommendation.map(ContainerAnalysisService::semanticSummary)
				.orElseGet(() -> rejectionSummary(profile));
		int sourceStart= evidence == null ? profile.identity().sourceStart() : evidence.sourceStart();
		int sourceLength= evidence == null ? profile.identity().sourceLength() : evidence.sourceLength();
		String evidenceKind= evidence == null ? "CANDIDATE" : evidence.kind().name(); //$NON-NLS-1$
		String evidenceSummary= evidence == null
				? "Container candidate has no additional evidence rows" //$NON-NLS-1$
				: evidence.summary();
		return new ContainerAnalysisRow(
				compilationUnitHandle,
				profile.identity().displayName(),
				profile.currentShape().name(),
				target,
				confidence,
				status,
				evidenceKind,
				evidenceSummary,
				semanticSummary,
				sourceStart,
				sourceLength);
	}

	private static String targetSummary(ContainerRecommendation recommendation) {
		TargetContainerContract target= recommendation.targetContract();
		return "%s; order=%s; uniqueness=%s; mutability=%s; nulls=%s".formatted( //$NON-NLS-1$
				target.shape(), target.orderRequirement(), target.uniquenessRequirement(),
				target.mutability(), target.nullContract());
	}

	private static String semanticSummary(ContainerRecommendation recommendation) {
		String assessments= recommendation.assessments().stream()
				.map(ContainerAnalysisService::assessmentSummary)
				.collect(Collectors.joining(System.lineSeparator()));
		return recommendation.targetContract().rationale()
				+ (assessments.isBlank() ? "" : System.lineSeparator() + assessments); //$NON-NLS-1$
	}

	private static String assessmentSummary(ContractAssessment assessment) {
		return "%s: %s — %s".formatted(assessment.property(), assessment.preservation(), //$NON-NLS-1$
				assessment.explanation());
	}

	private static String rejectionSummary(ContainerUsageProfile profile) {
		return profile.evidence().stream()
				.filter(evidence -> evidence.kind() == UsageEvidence.Kind.REJECTION_BOUNDARY
						|| evidence.kind() == UsageEvidence.Kind.UNSAFE_ESCAPE
						|| evidence.kind() == UsageEvidence.Kind.UNRESOLVED_BINDING
						|| evidence.kind() == UsageEvidence.Kind.UNCLASSIFIED_USAGE
						|| evidence.kind() == UsageEvidence.Kind.UNSUPPORTED_CONTINUATION
						|| evidence.kind() == UsageEvidence.Kind.CAPTURED_USAGE
						|| evidence.kind() == UsageEvidence.Kind.ARRAY_IDENTITY
						|| evidence.kind() == UsageEvidence.Kind.ARRAY_LENGTH_READ)
				.map(UsageEvidence::summary)
				.distinct()
				.collect(Collectors.joining(System.lineSeparator()));
	}

	private static void checkCanceled(IProgressMonitor monitor) {
		if (monitor != null && monitor.isCanceled()) {
			throw new OperationCanceledException();
		}
	}

	static CompilationUnit parse(ICompilationUnit unit, IProgressMonitor monitor) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(unit);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setStatementsRecovery(true);
		parser.setCompilerOptions(unit.getJavaProject().getOptions(true));
		return (CompilationUnit) parser.createAST(monitor);
	}

	record ContainerAnalysisRow(
			String compilationUnitHandle,
			String candidate,
			String currentShape,
			String targetContract,
			String confidence,
			String status,
			String evidenceKind,
			String evidenceSummary,
			String semanticSummary,
			int sourceStart,
			int sourceLength) {

		String details() {
			return "Candidate: %s%nCurrent: %s%nTarget: %s%nConfidence: %s%nStatus: %s%nEvidence: %s — %s%n%n%s" //$NON-NLS-1$
					.formatted(candidate, currentShape, targetContract, confidence, status,
							evidenceKind, evidenceSummary, semanticSummary);
		}
	}
}
