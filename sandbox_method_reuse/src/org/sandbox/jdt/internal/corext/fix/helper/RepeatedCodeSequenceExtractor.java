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
import java.util.Arrays;
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
import org.eclipse.core.runtime.SubMonitor;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NodeFinder;
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

	private record PreparedExtraction(ExtractMethodRefactoring refactoring, String ownerName,
			int score, int statementCount, int duplicateCount, int sourceOffset) {

		ICleanUpFix toCleanUpFix(String source) {
			return monitor -> {
				SubMonitor progress= SubMonitor.convert(monitor, 3);
				CompilationUnitChange change= extract(refactoring, progress.split(1));
				if (ownerName != null && canMakeGeneratedMethodStatic(change, ownerName,
						refactoring.getMethodName(), source, progress.split(1))) {
					// Extract Method uses this modifier mask to build the declaration and calculate type parameters.
					refactoring.setVisibility(Modifier.PRIVATE | Modifier.STATIC);
					try {
						return extract(refactoring, progress.split(1));
					} finally {
						refactoring.setVisibility(Modifier.PRIVATE);
						change.dispose();
					}
				}
				return change;
			};
		}
	}

	private static CompilationUnitChange extract(ExtractMethodRefactoring refactoring, IProgressMonitor monitor) throws CoreException {
		Change change= refactoring.createChange(monitor);
		if (change instanceof CompilationUnitChange compilationUnitChange) {
			return compilationUnitChange;
		}
		throw new CoreException(Status.error("Extract Method did not produce a compilation-unit change.")); //$NON-NLS-1$
	}

	private static boolean canMakeGeneratedMethodStatic(CompilationUnitChange change, String ownerName,
			String methodName, String source, IProgressMonitor monitor) throws CoreException {
		ICompilationUnit unit= change.getCompilationUnit();
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setProject(unit.getJavaProject());
		parser.setUnitName(unit.getPath().toPortableString());
		parser.setSource(previewSource(change, source).toCharArray());
		parser.setWorkingCopyOwner(unit.getOwner());
		var options= unit.getOptions(true);
		options.put(JavaCore.COMPILER_PB_MISSING_STATIC_ON_METHOD, JavaCore.WARNING);
		options.put(JavaCore.COMPILER_PB_POTENTIALLY_MISSING_STATIC_ON_METHOD, JavaCore.WARNING);
		parser.setCompilerOptions(options);
		parser.setResolveBindings(true);
		CompilationUnit preview= (CompilationUnit) parser.createAST(monitor);
		for (IProblem problem : preview.getProblems()) {
			if (problem.getID() != IProblem.MethodCanBeStatic) {
				continue;
			}
			// The diagnostic arguments
			// identify the declaration; the allocator guarantees a fresh name in this type.
			String[] arguments= problem.getArguments();
			if (arguments.length >= 2 && ownerName.equals(arguments[0]) && methodName.equals(arguments[1])) {
				return true;
			}
		}
		return false;
	}

	private RepeatedCodeSequenceExtractor() {
	}

	/**
	 * Creates one deterministic extraction fix for the most valuable repeated
	 * sequence in the current compilation unit.
	 *
	 * <p>For each compilation unit, one cleanup pass performs one extraction. JDT
	 * replaces every valid occurrence of that sequence in the enclosing type;
	 * subsequent cleanup runs can extract independent remaining groups without
	 * composing stale text edits.</p>
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
		String source= unit.getSource();
		int minimumStatements= normalizeMinimum(configuredMinimum);
		List<List<Candidate>> groups= candidateGroups(root, minimumStatements);
		PreparedExtraction best= null;
		int checks= 0;
		for (List<Candidate> group : groups) {
			for (Candidate candidate : group) {
				if (checks++ >= MAX_REFACTORING_CHECKS) {
					return validatedFix(best, unit, source);
				}
				if (!hasJdtDuplicate(candidate)) {
					continue;
				}
				PreparedExtraction prepared= prepare(root, candidate);
				if (isBetter(prepared, best)) {
					best= prepared;
				}
				// A coarse group may still contain several semantically distinct
				// sequences. Inspect every representative within the bounded budget;
				// JDT determines each candidate's actual duplicate set.
			}
		}
		return validatedFix(best, unit, source);
	}


	private record ProblemKey(boolean error, int id, List<String> arguments) {
	}

	private static ICleanUpFix validatedFix(PreparedExtraction extraction, ICompilationUnit unit,
			String source) throws CoreException {
		if (extraction == null) {
			return null;
		}
		return validateFix(extraction.toCleanUpFix(source), unit, source);
	}

	/** Checks both extraction and existing-method delegation against the complete source unit. */
	public static ICleanUpFix validateFix(ICleanUpFix fix, ICompilationUnit unit, String source) throws CoreException {
		CompilationUnitChange change= fix.createChange(new NullProgressMonitor());
		try {
			Map<ProblemKey, Integer> before= compilerProblems(unit, source);
			Map<ProblemKey, Integer> after= compilerProblems(unit, previewSource(change, source));
			for (var problem : after.entrySet()) {
				if (problem.getValue() > before.getOrDefault(problem.getKey(), 0)) {
					// JDT's semantic checks can accept duplicate replacements which fail
					// compilation or introduce diagnostics in the complete generated unit.
					return null;
				}
			}
			return fix;
		} finally {
			change.dispose();
		}
	}

	private static String previewSource(CompilationUnitChange change, String source) throws CoreException {
		// Cleanup fixpoint rounds use private working copies. TextChange's resource
		// preview can read an older file-buffer document instead of that exact source.
		Document document= new Document(source);
		try {
			change.getEdit().copy().apply(document);
		} catch (BadLocationException exception) {
			throw new CoreException(Status.error("Cannot preview repeated sequence extraction", exception)); //$NON-NLS-1$
		}
		return document.get();
	}

	private static Map<ProblemKey, Integer> compilerProblems(ICompilationUnit unit, String source) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setProject(unit.getJavaProject());
		parser.setUnitName(unit.getPath().toPortableString());
		parser.setSource(source.toCharArray());
		parser.setWorkingCopyOwner(unit.getOwner());
		var options= unit.getOptions(true);
		// Check optional warnings in the preview without changing project preferences.
		// A later warning-profile change must not expose diagnostics introduced here.
		options.replaceAll((key, severity) -> key.startsWith(JavaCore.PLUGIN_ID + ".compiler.problem.") //$NON-NLS-1$
				&& (JavaCore.IGNORE.equals(severity) || JavaCore.INFO.equals(severity)) ? JavaCore.WARNING : severity);
		options.put(JavaCore.COMPILER_PB_MAX_PER_UNIT, "0"); //$NON-NLS-1$
		parser.setCompilerOptions(options);
		parser.setResolveBindings(true);
		CompilationUnit root= (CompilationUnit) parser.createAST(null);
		Map<ProblemKey, Integer> problems= new LinkedHashMap<>();
		for (IProblem problem : root.getProblems()) {
			if (problem.isError() || problem.isWarning()) {
				ProblemKey key= new ProblemKey(problem.isError(), problem.getID(), problemArguments(problem, root));
				problems.merge(key, 1, Integer::sum);
			}
		}
		return problems;
	}

	private static List<String> problemArguments(IProblem problem, CompilationUnit root) {
		String[] arguments= problem.getArguments();
		if (problem.getID() == IProblem.PotentiallyUnclosedCloseable && arguments.length == 1
				&& ("<unassigned Closeable value>".equals(arguments[0]) //$NON-NLS-1$
						|| arguments[0].matches("<unassigned Closeable value from line [1-9][0-9]*>"))) { //$NON-NLS-1$
			int start= problem.getSourceStart();
			int length= problem.getSourceEnd() - start + 1;
			if (start >= 0 && length > 0
					&& NodeFinder.perform(root, start, length) instanceof Expression expression
					&& expression.getStartPosition() == start && expression.getLength() == length) {
				ASTNode owner= expression;
				while (owner != null && !(owner instanceof MethodDeclaration)) owner= owner.getParent();
				if (owner != null) {
					var method= ((MethodDeclaration) owner).resolveBinding();
					if (method != null && !method.isRecovered() && method.getDeclaringClass() != null
							&& !method.getDeclaringClass().isRecovered() && !method.getDeclaringClass().isLocal()
							&& !method.getDeclaringClass().isAnonymous()) {
						String key= method.getMethodDeclaration().getKey();
						if (key != null && !key.isBlank()) {
							// Existing leaks in callers cannot credit a new leak in an extracted
							// helper. Source-line shifts alone do not change resource identity.
							return List.of("<unassigned Closeable value>", key, expression.toString()); //$NON-NLS-1$
						}
					}
				}
			}
		}
		return Arrays.asList(arguments);
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
		ITypeBinding owner= candidate.enclosingType().resolveBinding();
		String ownerName= owner == null || owner.isInterface() ? null : owner.getErasure().getQualifiedName();
		return new PreparedExtraction(refactoring, ownerName, score, candidate.statementCount(),
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
