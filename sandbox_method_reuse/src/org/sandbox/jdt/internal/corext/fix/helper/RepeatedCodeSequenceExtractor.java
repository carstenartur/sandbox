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
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.code.ExtractMethodRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.code.SnippetFinder;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

/**
 * Finds repeated statement sequences and delegates extraction and duplicate
 * replacement to JDT's existing Extract Method refactoring.
 *
 * <p>The finder deliberately performs only cheap candidate discovery. JDT remains
 * authoritative for input/output variables, return values, control flow,
 * exceptions, static context, accessibility, and the set of replaceable
 * duplicates.</p>
 */
public final class RepeatedCodeSequenceExtractor {

	/** Default lower bound used when a profile contains no valid value. */
	public static final int DEFAULT_MINIMUM_STATEMENTS= 3;

	private static final int MINIMUM_ALLOWED_STATEMENTS= 2;
	private static final int MAXIMUM_ALLOWED_STATEMENTS= 20;
	private static final int MAX_REFACTORING_CHECKS= 256;
	private static final String BASE_METHOD_NAME= "extractedSequence"; //$NON-NLS-1$

	private record Candidate(AbstractTypeDeclaration enclosingType, ASTNode[] nodes,
			int start, int length, int statementCount, String coarseShape) {
	}

	private record PreparedExtraction(ExtractMethodRefactoring refactoring,
			int score, int statementCount, int duplicateCount, int sourceOffset) {

		ICleanUpFix toCleanUpFix() {
			return monitor -> {
				IProgressMonitor effectiveMonitor= monitor == null ? new NullProgressMonitor() : monitor;
				Change change= refactoring.createChange(effectiveMonitor);
				if (change instanceof CompilationUnitChange compilationUnitChange) {
					return compilationUnitChange;
				}
				throw new CoreException(Status.error(
						"Extract Method did not produce a compilation-unit change.")); //$NON-NLS-1$
			};
		}
	}

	private RepeatedCodeSequenceExtractor() {
	}

	/**
	 * Creates one deterministic extraction fix for the most valuable repeated
	 * sequence in the current compilation unit.
	 *
	 * <p>One cleanup pass performs one extraction. JDT replaces every valid
	 * occurrence of that sequence in the enclosing type; subsequent cleanup runs
	 * can extract independent remaining groups without composing stale text edits.</p>
	 *
	 * @param unit current compilation unit
	 * @param root current binding-resolved AST
	 * @param configuredMinimum configured statement threshold
	 * @return prepared cleanup fix, or {@code null} when JDT validates no candidate
	 * @throws CoreException if JDT's refactoring infrastructure fails
	 */
	public static ICleanUpFix createFix(ICompilationUnit unit, CompilationUnit root,
			int configuredMinimum) throws CoreException {
		if (unit == null || root == null) {
			return null;
		}
		int minimumStatements= normalizeMinimum(configuredMinimum);
		List<List<Candidate>> groups= candidateGroups(root, minimumStatements);
		PreparedExtraction best= null;
		int checks= 0;
		for (List<Candidate> group : groups) {
			for (Candidate candidate : group) {
				if (checks++ >= MAX_REFACTORING_CHECKS) {
					return best == null ? null : best.toCleanUpFix();
				}
				if (!hasJdtDuplicate(candidate)) {
					continue;
				}
				PreparedExtraction prepared= prepare(root, candidate);
				if (isBetter(prepared, best)) {
					best= prepared;
				}
				// Candidates in one group have the same coarse shape and length.
				// The first JDT-valid representative is sufficient; JDT itself
				// discovers every semantically valid duplicate occurrence.
				if (prepared != null) {
					break;
				}
			}
		}
		return best == null ? null : best.toCleanUpFix();
	}

	/** Normalizes profile values to a conservative supported range. */
	public static int normalizeMinimum(int configuredMinimum) {
		if (configuredMinimum < MINIMUM_ALLOWED_STATEMENTS
				|| configuredMinimum > MAXIMUM_ALLOWED_STATEMENTS) {
			return DEFAULT_MINIMUM_STATEMENTS;
		}
		return configuredMinimum;
	}

	private static List<List<Candidate>> candidateGroups(CompilationUnit root, int minimumStatements) {
		Map<String, List<Candidate>> grouped= new LinkedHashMap<>();
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(Block block) {
				AbstractTypeDeclaration enclosingType= enclosingType(block);
				if (enclosingType == null || enclosingType.resolveBinding() == null
						|| enclosingMethod(block) == null) {
					return true;
				}
				@SuppressWarnings("unchecked")
				List<Statement> statements= block.statements();
				int maximumLength= Math.min(statements.size(), MAXIMUM_ALLOWED_STATEMENTS);
				for (int length= maximumLength; length >= minimumStatements; length--) {
					for (int startIndex= 0; startIndex + length <= statements.size(); startIndex++) {
						Candidate candidate= candidate(enclosingType, statements, startIndex, length);
						grouped.computeIfAbsent(candidate.coarseShape(), ignored -> new ArrayList<>())
								.add(candidate);
					}
				}
				return true;
			}
		});
		Comparator<Candidate> candidateOrder= Comparator.comparingInt(Candidate::start);
		return grouped.values().stream()
				.filter(group -> group.size() >= 2)
				.peek(group -> group.sort(candidateOrder))
				.sorted(Comparator
						.<List<Candidate>>comparingInt(group -> group.get(0).statementCount())
						.reversed()
						.thenComparing(Comparator.comparingInt(List<Candidate>::size).reversed())
						.thenComparingInt(group -> group.get(0).start()))
				.toList();
	}

	private static Candidate candidate(AbstractTypeDeclaration enclosingType,
			List<Statement> statements, int startIndex, int length) {
		ASTNode[] nodes= new ASTNode[length];
		StringBuilder shape= new StringBuilder(typeIdentity(enclosingType))
				.append(':').append(length);
		for (int index= 0; index < length; index++) {
			Statement statement= statements.get(startIndex + index);
			nodes[index]= statement;
			shape.append(':').append(statement.getNodeType());
		}
		ASTNode first= nodes[0];
		ASTNode last= nodes[nodes.length - 1];
		int start= first.getStartPosition();
		int end= last.getStartPosition() + last.getLength();
		return new Candidate(enclosingType, nodes, start, end - start, length, shape.toString());
	}

	private static String typeIdentity(AbstractTypeDeclaration type) {
		ITypeBinding binding= type.resolveBinding();
		String key= binding == null ? null : binding.getKey();
		return key == null || key.isBlank()
				? Integer.toString(type.getStartPosition())
				: key;
	}

	private static boolean hasJdtDuplicate(Candidate candidate) {
		for (SnippetFinder.Match match : SnippetFinder.perform(candidate.enclosingType(), candidate.nodes())) {
			if (match != null && !match.isInvalidNode()) {
				return true;
			}
		}
		return false;
	}

	private static PreparedExtraction prepare(CompilationUnit root, Candidate candidate)
			throws CoreException {
		ExtractMethodRefactoring refactoring= new ExtractMethodRefactoring(root,
				candidate.start(), candidate.length());
		RefactoringStatus initial= refactoring.checkInitialConditions(new NullProgressMonitor());
		if (hasError(initial) || refactoring.getNumberOfDuplicates() == 0) {
			return null;
		}
		refactoring.setMethodName(uniqueMethodName(candidate.enclosingType()));
		refactoring.setVisibility(Modifier.PRIVATE);
		refactoring.setGenerateJavadoc(false);
		refactoring.setReplaceDuplicates(true);
		RefactoringStatus finalStatus= refactoring.checkFinalConditions(new NullProgressMonitor());
		if (hasError(finalStatus)) {
			return null;
		}
		int duplicateCount= refactoring.getNumberOfDuplicates();
		int score= Math.multiplyExact(candidate.statementCount(), duplicateCount);
		return new PreparedExtraction(refactoring, score, candidate.statementCount(),
				duplicateCount, candidate.start());
	}

	private static boolean hasError(RefactoringStatus status) {
		return status != null && status.getSeverity() >= RefactoringStatus.ERROR;
	}

	private static boolean isBetter(PreparedExtraction candidate, PreparedExtraction current) {
		if (candidate == null) {
			return false;
		}
		if (current == null) {
			return true;
		}
		if (candidate.score() != current.score()) {
			return candidate.score() > current.score();
		}
		if (candidate.statementCount() != current.statementCount()) {
			return candidate.statementCount() > current.statementCount();
		}
		if (candidate.duplicateCount() != current.duplicateCount()) {
			return candidate.duplicateCount() > current.duplicateCount();
		}
		return candidate.sourceOffset() < current.sourceOffset();
	}

	private static String uniqueMethodName(AbstractTypeDeclaration type) {
		Set<String> names= new LinkedHashSet<>();
		for (Object declaration : type.bodyDeclarations()) {
			if (declaration instanceof MethodDeclaration method) {
				names.add(method.getName().getIdentifier());
			}
		}
		if (!names.contains(BASE_METHOD_NAME)) {
			return BASE_METHOD_NAME;
		}
		for (int suffix= 2; suffix < Integer.MAX_VALUE; suffix++) {
			String candidate= BASE_METHOD_NAME + suffix;
			if (!names.contains(candidate)) {
				return candidate;
			}
		}
		throw new IllegalStateException("Cannot allocate an extracted method name"); //$NON-NLS-1$
	}

	private static AbstractTypeDeclaration enclosingType(ASTNode node) {
		ASTNode current= node;
		while (current != null) {
			if (current instanceof AbstractTypeDeclaration type) {
				return type;
			}
			if (current.getNodeType() == ASTNode.ANONYMOUS_CLASS_DECLARATION) {
				return null;
			}
			current= current.getParent();
		}
		return null;
	}

	private static MethodDeclaration enclosingMethod(ASTNode node) {
		ASTNode current= node;
		while (current != null && !(current instanceof AbstractTypeDeclaration)) {
			if (current instanceof MethodDeclaration method) {
				return method;
			}
			current= current.getParent();
		}
		return null;
	}
}
