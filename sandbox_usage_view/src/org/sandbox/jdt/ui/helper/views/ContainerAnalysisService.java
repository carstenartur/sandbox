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

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.sandbox.jdt.container.analysis.AppendOnlyArraySeedDetector;
import org.sandbox.jdt.container.analysis.ContainerContractInferrer;
import org.sandbox.jdt.container.analysis.LocalArrayUsageAnalyzer;
import org.sandbox.jdt.container.analysis.LocalUniqueSequenceAnalyzer;
import org.sandbox.jdt.container.api.ContainerRecommendation;
import org.sandbox.jdt.container.api.ContainerRecommendation.ContractAssessment;
import org.sandbox.jdt.container.api.ContainerRecommendation.Preservation;
import org.sandbox.jdt.container.api.ContainerUsageProfile;
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
	private final ContainerContractInferrer contractInferrer= new ContainerContractInferrer();

	List<ContainerAnalysisRow> analyze(ICompilationUnit unit) {
		if (unit == null || !unit.exists()) {
			return List.of();
		}
		return analyze(parse(unit), unit.getPrimary().getHandleIdentifier());
	}

	List<ContainerAnalysisRow> analyze(CompilationUnit root, String compilationUnitHandle) {
		if (root == null || compilationUnitHandle == null || compilationUnitHandle.isBlank()) {
			return List.of();
		}
		Map<String, ContainerUsageProfile> profiles= new LinkedHashMap<>();
		for (ContainerUsageProfile seed : arraySeedDetector.findSeeds(root)) {
			ContainerUsageProfile profile= arrayUsageAnalyzer.analyze(root, seed);
			profiles.putIfAbsent(profile.identity().stableId(), profile);
		}
		for (ContainerUsageProfile profile : uniqueSequenceAnalyzer.analyze(root)) {
			profiles.putIfAbsent(profile.identity().stableId(), profile);
		}

		List<ContainerAnalysisRow> rows= new ArrayList<>();
		for (ContainerUsageProfile profile : profiles.values()) {
			Optional<ContainerRecommendation> recommendation= contractInferrer.infer(profile);
			if (profile.evidence().isEmpty()) {
				rows.add(row(compilationUnitHandle, profile, recommendation, null));
				continue;
			}
			for (UsageEvidence evidence : profile.evidence()) {
				rows.add(row(compilationUnitHandle, profile, recommendation, evidence));
			}
		}
		rows.sort(Comparator.comparingInt(ContainerAnalysisRow::sourceStart)
				.thenComparing(ContainerAnalysisRow::candidate)
				.thenComparing(ContainerAnalysisRow::evidenceKind));
		return List.copyOf(rows);
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
				.filter(assessment -> assessment.preservation() != Preservation.PRESERVED)
				.map(ContainerAnalysisService::assessmentSummary)
				.collect(Collectors.joining(System.lineSeparator()));
		if (assessments.isBlank()) {
			assessments= recommendation.assessments().stream()
					.map(ContainerAnalysisService::assessmentSummary)
					.collect(Collectors.joining(System.lineSeparator()));
		}
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
						|| evidence.kind() == UsageEvidence.Kind.UNSUPPORTED_CONTINUATION)
				.map(UsageEvidence::summary)
				.distinct()
				.collect(Collectors.joining(System.lineSeparator()));
	}

	private static CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(unit);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setStatementsRecovery(true);
		parser.setCompilerOptions(unit.getJavaProject().getOptions(true));
		return (CompilationUnit) parser.createAST(null);
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
