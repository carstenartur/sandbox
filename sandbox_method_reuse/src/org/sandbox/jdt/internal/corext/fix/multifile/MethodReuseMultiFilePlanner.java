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
package org.sandbox.jdt.internal.corext.fix.multifile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;

import org.sandbox.jdt.cleanup.multifile.JavaProjectCompilationUnits;
import org.sandbox.jdt.cleanup.multifile.MultiFileCandidateDiagnostic;
import org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpDiagnostics;
import org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpPlanResult;
import org.sandbox.jdt.cleanup.multifile.MultiFilePlanningBudget;
import org.sandbox.jdt.cleanup.multifile.MultiFilePlanningLimits;
import org.sandbox.jdt.cleanup.multifile.MultiFilePlanningMetrics;
import org.sandbox.jdt.cleanup.multifile.MultiFileScopeDiagnostic;
import org.sandbox.jdt.cleanup.multifile.SelectedCompilationUnitPlan;
import org.sandbox.jdt.cleanup.multifile.SourceRootPolicy;
import org.sandbox.jdt.internal.corext.fix.multifile.MethodReuseSemanticSupport.SequenceDescriptor;

/** Builds deterministic extraction plans for repeated statement sequences. */
public final class MethodReuseMultiFilePlanner {

	private static final String CLEANUP_ID= "method-reuse"; //$NON-NLS-1$
	private static final int MAX_WINDOWS_PER_METHOD= 4096;
	private static final Comparator<SequenceDescriptor> OCCURRENCE_ORDER=
			Comparator.comparing(SequenceDescriptor::declaringTypeQualifiedName)
					.thenComparing(SequenceDescriptor::methodName)
					.thenComparing(SequenceDescriptor::compilationUnitHandle)
					.thenComparingInt(SequenceDescriptor::startStatementIndex);

	private record Discovery(List<SequenceDescriptor> sequences, int skippedLargeMethods) {
		Discovery {
			sequences= List.copyOf(sequences);
		}
	}

	private record ReservedRange(int startInclusive, int endExclusive) {
		boolean overlaps(SequenceDescriptor sequence) {
			return startInclusive < sequence.endStatementIndexExclusive()
					&& sequence.startStatementIndex() < endExclusive;
		}
	}

	private MethodReuseMultiFilePlanner() {
	}

	/** Creates a fail-closed project plan using the configured minimum length. */
	public static MultiFileCleanUpPlanResult<MethodReuseMigrationPlan> create(IJavaProject project,
			ICompilationUnit[] selectedUnits, int minimumStatements, IProgressMonitor monitor)
			throws CoreException {
		if (monitor != null && monitor.isCanceled()) {
			throw new OperationCanceledException();
		}
		SelectedCompilationUnitPlan selectedScope= SelectedCompilationUnitPlan.of(project, selectedUnits);
		List<ICompilationUnit> allowedUnits= JavaProjectCompilationUnits.collect(project,
				Arrays.asList(selectedUnits), SourceRootPolicy.COMPLETE_PROJECT);
		Set<String> allowedHandles= handles(allowedUnits);
		boolean complete= selectedScope.compilationUnitHandles().containsAll(allowedHandles);
		if (!complete) {
			MethodReuseMigrationPlan emptyPlan= new MethodReuseMigrationPlan(selectedScope, List.of());
			return MultiFileCleanUpPlanResult.success(emptyPlan, new RefactoringStatus(),
					MultiFilePlanningMetrics.empty(), diagnostics(selectedUnits, false, List.of()));
		}

		long planningStarted= System.nanoTime();
		MultiFilePlanningBudget.Assessment budget= MultiFilePlanningBudget.assess(selectedUnits,
				MultiFilePlanningLimits.fromSystemProperties(), monitor);
		if (!budget.mayProceed()) {
			return new MultiFileCleanUpPlanResult<>(null, budget.status(), budget.metrics(),
					diagnostics(selectedUnits, true, List.of()));
		}

		long parseStarted= System.nanoTime();
		Map<String, CompilationUnit> roots= parse(project, selectedUnits, monitor);
		long parseNanos= System.nanoTime() - parseStarted;
		if (!roots.keySet().containsAll(selectedScope.compilationUnitHandles())) {
			MethodReuseMigrationPlan emptyPlan= new MethodReuseMigrationPlan(selectedScope, List.of());
			return MultiFileCleanUpPlanResult.success(emptyPlan, budget.status(), budget.metrics(),
					diagnostics(selectedUnits, false, List.of()));
		}

		Map<String, ICompilationUnit> unitsByHandle= new LinkedHashMap<>();
		for (ICompilationUnit unit : selectedUnits) {
			unitsByHandle.put(unit.getPrimary().getHandleIdentifier(), unit.getPrimary());
		}
		Discovery discovery= discover(unitsByHandle, roots, minimumStatements, monitor);
		Map<String, List<SequenceDescriptor>> grouped= new LinkedHashMap<>();
		for (SequenceDescriptor sequence : discovery.sequences()) {
			grouped.computeIfAbsent(sequence.groupKey(), ignored -> new ArrayList<>()).add(sequence);
		}

		List<List<SequenceDescriptor>> groups= grouped.values().stream()
				.filter(group -> group.size() >= 2)
				.sorted(Comparator
						.<List<SequenceDescriptor>>comparingInt(group -> group.get(0).statementCount())
						.reversed().thenComparing(group -> group.get(0).groupKey()))
				.toList();
		Map<String, List<ReservedRange>> reserved= new HashMap<>();
		Map<String, Integer> nextGeneratedMethodNumber= new HashMap<>();
		Map<String, Set<String>> allocatedNames= new HashMap<>();
		List<MethodReuseSequenceCandidate> candidates= new ArrayList<>();
		List<MultiFileCandidateDiagnostic> candidateDiagnostics= new ArrayList<>();

		for (List<SequenceDescriptor> rawGroup : groups) {
			MultiFilePlanningBudget.checkCanceled(monitor);
			List<SequenceDescriptor> available= rawGroup.stream().sorted(OCCURRENCE_ORDER)
					.filter(sequence -> !overlapsReserved(sequence, reserved)).toList();
			if (available.size() < 2) {
				continue;
			}
			SequenceDescriptor target= available.stream()
					.filter(SequenceDescriptor::targetTypeEligible).findFirst().orElse(null);
			if (target == null) {
				continue;
			}
			String generatedMethodName= allocateMethodName(target, roots,
					nextGeneratedMethodNumber, allocatedNames);
			if (generatedMethodName == null) {
				continue;
			}

			List<MethodReuseSequenceOccurrence> occurrences= available.stream()
					.map(sequence -> new MethodReuseSequenceOccurrence(
							sequence.compilationUnitHandle(), sequence.methodBindingKey(),
							sequence.startStatementIndex()))
					.toList();
			MethodReuseSequenceOccurrence canonicalOccurrence=
					new MethodReuseSequenceOccurrence(target.compilationUnitHandle(),
							target.methodBindingKey(), target.startStatementIndex());
			String candidateId= candidateId(target, available, generatedMethodName);
			MethodReuseSequenceCandidate candidate= new MethodReuseSequenceCandidate(candidateId,
					target.compilationUnitHandle(), target.declaringTypeBindingKey(),
					target.declaringTypeQualifiedName(), generatedMethodName,
					target.statementCount(), target.fingerprint(), target.inputTypeSignature(),
					target.outputTypeKey(), canonicalOccurrence, occurrences);
			candidates.add(candidate);
			for (SequenceDescriptor occurrence : available) {
				reserved.computeIfAbsent(occurrence.rangeOwner(), ignored -> new ArrayList<>())
						.add(new ReservedRange(occurrence.startStatementIndex(),
								occurrence.endStatementIndexExclusive()));
			}
			List<String> relatedHandles= available.stream()
					.map(SequenceDescriptor::compilationUnitHandle).distinct().sorted().toList();
			candidateDiagnostics.add(MultiFileCandidateDiagnostic.transformed(candidateId,
					target.compilationUnitHandle(),
					"Extract a repeated " + target.statementCount() //$NON-NLS-1$
							+ "-statement sequence occurring " + available.size() //$NON-NLS-1$
							+ " times into " + target.declaringTypeQualifiedName() + '.' //$NON-NLS-1$
							+ generatedMethodName + " and replace every occurrence with a call.", //$NON-NLS-1$
					relatedHandles));
		}

		candidates.sort(Comparator.comparing(MethodReuseSequenceCandidate::targetCompilationUnitHandle)
				.thenComparing(MethodReuseSequenceCandidate::generatedMethodName));
		RefactoringStatus status= budget.status();
		if (discovery.skippedLargeMethods() > 0) {
			status.addInfo(discovery.skippedLargeMethods()
					+ " method(s) were skipped because enumerating all configured sequence windows " //$NON-NLS-1$
					+ "would exceed the deterministic per-method analysis budget."); //$NON-NLS-1$
		}
		int retainedEntries= Math.toIntExact(candidates.stream()
				.mapToLong(candidate -> 1L + candidate.occurrences().size()).sum());
		MultiFilePlanningMetrics metrics= budget.metrics()
				.withDurations(parseNanos, System.nanoTime() - planningStarted)
				.withRetainedPlanEntries(retainedEntries);
		return MultiFileCleanUpPlanResult.success(new MethodReuseMigrationPlan(selectedScope, candidates),
				status, metrics, diagnostics(selectedUnits, true, candidateDiagnostics));
	}

	private static Discovery discover(Map<String, ICompilationUnit> unitsByHandle,
			Map<String, CompilationUnit> roots, int minimumStatements, IProgressMonitor monitor) {
		List<SequenceDescriptor> result= new ArrayList<>();
		int[] skipped= { 0 };
		for (Map.Entry<String, CompilationUnit> entry : roots.entrySet()) {
			MultiFilePlanningBudget.checkCanceled(monitor);
			ICompilationUnit unit= unitsByHandle.get(entry.getKey());
			if (unit == null) {
				continue;
			}
			CompilationUnit root= entry.getValue();
			root.accept(new ASTVisitor() {
				@Override
				public boolean visit(MethodDeclaration method) {
					MultiFilePlanningBudget.checkCanceled(monitor);
					if (method.getBody() == null) {
						return false;
					}
					@SuppressWarnings("unchecked")
					List<Statement> statements= method.getBody().statements();
					int count= statements.size();
					if (count < minimumStatements) {
						return false;
					}
					long windows= (long) (count - minimumStatements + 1)
							* (count - minimumStatements + 2) / 2;
					if (windows > MAX_WINDOWS_PER_METHOD) {
						skipped[0]++;
						return false;
					}
					for (int length= count; length >= minimumStatements; length--) {
						for (int start= 0; start + length <= count; start++) {
							MultiFilePlanningBudget.checkCanceled(monitor);
							SequenceDescriptor descriptor= MethodReuseSemanticSupport.describeSequence(
									unit, root, method, start, length);
							if (descriptor != null) {
								result.add(descriptor);
							}
						}
					}
					return false;
				}
			});
		}
		result.sort(Comparator.comparingInt(SequenceDescriptor::statementCount).reversed()
				.thenComparing(OCCURRENCE_ORDER));
		return new Discovery(result, skipped[0]);
	}

	private static boolean overlapsReserved(SequenceDescriptor sequence,
			Map<String, List<ReservedRange>> reserved) {
		return reserved.getOrDefault(sequence.rangeOwner(), List.of()).stream()
				.anyMatch(range -> range.overlaps(sequence));
	}

	private static String allocateMethodName(SequenceDescriptor target,
			Map<String, CompilationUnit> roots, Map<String, Integer> nextNumbers,
			Map<String, Set<String>> allocatedNames) {
		CompilationUnit root= roots.get(target.compilationUnitHandle());
		TypeDeclaration type= MethodReuseSemanticSupport.findType(root,
				target.declaringTypeBindingKey());
		if (type == null) {
			return null;
		}
		String typeKey= target.declaringTypeBindingKey();
		Set<String> reservedNames= allocatedNames.computeIfAbsent(typeKey,
				ignored -> new LinkedHashSet<>());
		int number= nextNumbers.getOrDefault(typeKey, Integer.valueOf(1)).intValue();
		while (number < Integer.MAX_VALUE) {
			String candidate= "sharedSequence" + number; //$NON-NLS-1$
			number++;
			if (!reservedNames.contains(candidate)
					&& MethodReuseSemanticSupport.methodNameAvailable(type, candidate)) {
				reservedNames.add(candidate);
				nextNumbers.put(typeKey, Integer.valueOf(number));
				return candidate;
			}
		}
		return null;
	}

	private static String candidateId(SequenceDescriptor target,
			List<SequenceDescriptor> occurrences, String generatedMethodName) {
		StringBuilder source= new StringBuilder(target.groupKey()).append('\n')
				.append(target.declaringTypeBindingKey()).append('\n')
				.append(generatedMethodName);
		for (SequenceDescriptor occurrence : occurrences) {
			source.append('\n').append(occurrence.occurrenceId());
		}
		try {
			byte[] digest= MessageDigest.getInstance("SHA-256") //$NON-NLS-1$
					.digest(source.toString().getBytes(StandardCharsets.UTF_8));
			return "method-reuse-" + HexFormat.of().formatHex(digest, 0, 8); //$NON-NLS-1$
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is unavailable", e); //$NON-NLS-1$
		}
	}

	private static Map<String, CompilationUnit> parse(IJavaProject project, ICompilationUnit[] units,
			IProgressMonitor monitor) {
		Map<String, CompilationUnit> roots= new LinkedHashMap<>();
		ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setProject(project);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(IASTSharedValues.SHARED_BINDING_RECOVERY);
		parser.setStatementsRecovery(IASTSharedValues.SHARED_AST_STATEMENT_RECOVERY);
		parser.setCompilerOptions(RefactoringASTParser.getCompilerOptions(project));
		parser.createASTs(units, new String[0], new ASTRequestor() {
			@Override
			public void acceptAST(ICompilationUnit source, CompilationUnit ast) {
				roots.put(source.getPrimary().getHandleIdentifier(), ast);
			}
		}, monitor);
		return roots;
	}

	private static MultiFileCleanUpDiagnostics diagnostics(ICompilationUnit[] selectedUnits,
			boolean complete, List<MultiFileCandidateDiagnostic> candidates) {
		List<String> selectedHandles= Arrays.stream(selectedUnits)
				.map(ICompilationUnit::getPrimary)
				.map(ICompilationUnit::getHandleIdentifier).sorted().toList();
		MultiFileScopeDiagnostic scope= complete
				? new MultiFileScopeDiagnostic(selectedHandles, List.of(),
						"CLOSED_EDITABLE_SOURCE_SCOPE", //$NON-NLS-1$
						"Every editable source compilation unit in the Java project was analysed for repeated statement sequences.", //$NON-NLS-1$
						true)
				: new MultiFileScopeDiagnostic(selectedHandles, List.of(),
						"INCOMPLETE_EDITABLE_SOURCE_SCOPE", //$NON-NLS-1$
						"The cleanup did not receive every editable source compilation unit required for safe sequence extraction.", //$NON-NLS-1$
						false);
		return new MultiFileCleanUpDiagnostics(CLEANUP_ID, scope, candidates);
	}

	private static Set<String> handles(List<ICompilationUnit> units) {
		Set<String> result= new LinkedHashSet<>();
		for (ICompilationUnit unit : units) {
			if (unit != null) {
				result.add(unit.getPrimary().getHandleIdentifier());
			}
		}
		return result;
	}
}
