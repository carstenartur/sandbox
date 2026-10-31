/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongConsumer;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore.LoopConversionOperation;

/** One semantic decision path for cleanup, Quick Assist and style diagnostics. */
public final class LoopConversionService {
	private static final String SCAN_ROOT = LoopConversionService.class.getName();

	private LoopConversionService() {
	}

	public static EnumSet<UseFunctionalCallFixCore> handlers(LoopTargetFormat target) {
		return switch (target) {
		case STREAM -> EnumSet.of(UseFunctionalCallFixCore.LOOP, UseFunctionalCallFixCore.ITERATOR_LOOP,
				UseFunctionalCallFixCore.TRADITIONAL_FOR_LOOP);
		case FOR_LOOP -> EnumSet.of(UseFunctionalCallFixCore.STREAM_TO_FOR, UseFunctionalCallFixCore.ITERATOR_TO_FOR);
		case WHILE_LOOP -> EnumSet.of(UseFunctionalCallFixCore.FOR_TO_ITERATOR, UseFunctionalCallFixCore.STREAM_TO_ITERATOR);
		};
	}

	/** The explicit profile and ordinary cleanup share exactly the same routing. */
	public static EnumSet<UseFunctionalCallFixCore> configuredHandlers(Map<String, String> options) {
		if ("true".equals(options.get(LOOP_CONVERSION_ENABLED))) {
			var selected = handlers(LoopTargetFormat.fromId(options.get(LOOP_CONVERSION_TARGET_FORMAT)));
			selected.removeIf(handler -> !"true".equals(options.get(sourceOption(handler))));
			return selected;
		}
		if (("true".equals(options.get(USEFUNCTIONALLOOP_CLEANUP)) || "true".equals(options.get(USEFUNCTIONALLOOP_CLEANUP_V2)))
				&& !"true".equals(options.get(USEFUNCTIONALLOOP_FORMAT_FOR)) && !"true".equals(options.get(USEFUNCTIONALLOOP_FORMAT_WHILE)))
			return handlers(LoopTargetFormat.STREAM);
		return EnumSet.noneOf(UseFunctionalCallFixCore.class);
	}

	public static String sourceOption(UseFunctionalCallFixCore handler) {
		return switch (handler) {
		case LOOP, FOR_TO_ITERATOR -> LOOP_CONVERSION_FROM_ENHANCED_FOR;
		case ITERATOR_LOOP, ITERATOR_TO_FOR -> LOOP_CONVERSION_FROM_ITERATOR_WHILE;
		case STREAM_TO_FOR, STREAM_TO_ITERATOR -> LOOP_CONVERSION_FROM_STREAM;
		case TRADITIONAL_FOR_LOOP -> LOOP_CONVERSION_FROM_CLASSIC_FOR;
		};
	}

	/**
	 * Render from a fresh, resolved working-copy AST without applying it. Callers
	 * own the transaction and must also validate edits outside operation anchors
	 * (for example imports). No numerical service is loaded by this API.
	 */
	public static CompilationUnitChange renderUnchanged(CompilationUnit unit, Map<String, String> options,
			List<? extends ISourceRange> excluded, LongConsumer charge, IProgressMonitor monitor) throws CoreException {
		var selected = configuredHandlers(options);
		if (selected.isEmpty()) return null;
		Runnable checkpoint = () -> { if (monitor.isCanceled()) throw new OperationCanceledException(); };
		int[] size = {0, 0};
		unit.accept(new ASTVisitor() {
			@Override public void preVisit(ASTNode node) {
				checkpoint.run(); charge.accept(1); size[0]++;
				if (node instanceof ForStatement || node instanceof EnhancedForStatement || node instanceof WhileStatement
						|| isForEachStatement(node)) size[1]++;
			}
		});
		// Reserve every selected traversal and nested-loop admission, including rejected work.
		charge.accept((long) size[0] * (selected.size() + 1) * (size[1] + 1));
		Analysis analysis = analyze(unit, selected, checkpoint);
		var safe = analysis.operations().stream().filter(operation -> operation instanceof LoopConversionOperation loop
				&& excluded.stream().noneMatch(range -> loop.getAnchor().getStartPosition() < (long) range.getOffset() + range.getLength()
						&& range.getOffset() < (long) loop.getAnchor().getStartPosition() + loop.getAnchor().getLength())).toList();
		checkpoint.run(); charge.accept((long) size[0] * (safe.size() + 1));
		var fix = new Analysis(unit, safe, analysis.names()).createFix("Convert remaining loops");
		var change = fix == null ? null : fix.createChange(monitor);
		checkpoint.run(); return change;
	}

	public record Analysis(CompilationUnit unit, List<CompilationUnitRewriteOperation> operations, Set<String> names) {
		public Analysis {
			operations = List.copyOf(operations);
			names = Set.copyOf(names);
		}

		public CompilationUnitRewriteOperationsFixCore createFix(String label) {
			if (operations.isEmpty()) {
				return null;
			}
			return new CompilationUnitRewriteOperationsFixCore(label, unit,
					operations.toArray(CompilationUnitRewriteOperation[]::new)) {
				@Override
				public CompilationUnitChange createChange(IProgressMonitor monitor) throws CoreException {
					synchronized (unit) {
						try (var scope = new LoopVariableNames.Scope(unit, names)) {
							return super.createChange(monitor);
						}
					}
				}
			};
		}

		/** A caret targets the innermost enclosing loop, never a neighbouring loop. */
		public Analysis atSelection(int offset, int length) {
			ASTNode anchor = selection(unit, offset, length);
			List<CompilationUnitRewriteOperation> matches = operations.stream()
					.filter(operation -> operation instanceof LoopConversionOperation loop && loop.getAnchor() == anchor)
					.toList();
			return new Analysis(unit, matches, names);
		}
	}

	/** Scope editor discovery before overlap suppression can hide a selected inner loop. */
	public static Analysis analyzeSelection(CompilationUnit unit, EnumSet<UseFunctionalCallFixCore> handlers, int offset, int length) {
		synchronized (unit) {
			ASTNode selected = selection(unit, offset, length);
			if (selected == null) return new Analysis(unit, List.of(), Set.of());
			Object previous = unit.getProperty(SCAN_ROOT);
			unit.setProperty(SCAN_ROOT, selected);
			try {
				return analyze(unit, handlers).atSelection(offset, length);
			} finally {
				unit.setProperty(SCAN_ROOT, previous);
			}
		}
	}

	static ASTNode scanRoot(CompilationUnit unit) {
		return unit.getProperty(SCAN_ROOT) instanceof ASTNode root ? root : unit;
	}

	private static ASTNode selection(CompilationUnit unit, int offset, int length) {
		if (offset < 0 || length < 0 || (long) offset + length > unit.getLength()) return null;
		ASTNode selected = NodeFinder.perform(unit, offset, length);
		while (selected != null && !(selected instanceof EnhancedForStatement || selected instanceof ForStatement
				|| selected instanceof WhileStatement || isForEachStatement(selected))) selected = selected.getParent();
		return selected;
	}

	public static Analysis analyze(CompilationUnit unit, EnumSet<UseFunctionalCallFixCore> handlers) {
		return analyze(unit, handlers, () -> { });
	}

	private static Analysis analyze(CompilationUnit unit, EnumSet<UseFunctionalCallFixCore> handlers, Runnable checkpoint) {
		synchronized (unit) {
			try (var scope = new LoopVariableNames.Scope(unit, Set.of())) {
				Set<CompilationUnitRewriteOperation> operations = new LinkedHashSet<>();
				Set<ASTNode> processed = new HashSet<>();
				for (UseFunctionalCallFixCore handler : handlers) {
					checkpoint.run();
					if (supportsSourceLevel(unit, handler)) {
						handler.findOperations(unit, operations, processed);
					}
				}
				List<CompilationUnitRewriteOperation> safe = new ArrayList<>(operations);
				safe.removeIf(operation -> operation instanceof LoopConversionOperation loop && hasErrors(loop.getAnchor()));
				safe.sort(Comparator.comparingInt(operation -> operation instanceof LoopConversionOperation loop
						? loop.getAnchor().getStartPosition() : 0));
				return new Analysis(unit, safe, LoopVariableNames.usedNames(unit));
			}
		}
	}

	private static boolean isForEachStatement(ASTNode node) {
		return node instanceof ExpressionStatement statement && statement.getExpression() instanceof MethodInvocation call
				&& Set.of("forEach", "forEachOrdered").contains(call.getName().getIdentifier()); //$NON-NLS-1$ //$NON-NLS-2$
	}

	public static boolean hasErrors(ASTNode node) {
		for (var problem : ((CompilationUnit) node.getRoot()).getProblems()) {
			if (problem.isError() && problem.getSourceStart() < node.getStartPosition() + node.getLength()
					&& problem.getSourceEnd() >= node.getStartPosition()) {
				return true;
			}
		}
		return false;
	}

	private static boolean supportsSourceLevel(CompilationUnit unit, UseFunctionalCallFixCore handler) {
		if (unit.getJavaElement() == null) {
			return true;
		}
		String source = unit.getJavaElement().getJavaProject().getOption(JavaCore.COMPILER_SOURCE, true);
		String minimum = handlers(LoopTargetFormat.STREAM).contains(handler) ? JavaCore.VERSION_1_8 : JavaCore.VERSION_1_5;
		return JavaCore.compareJavaVersions(source, minimum) >= 0;
	}
}
