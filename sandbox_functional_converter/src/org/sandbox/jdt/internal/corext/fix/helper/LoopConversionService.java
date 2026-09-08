/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.ASTNode;
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
		synchronized (unit) {
			try (var scope = new LoopVariableNames.Scope(unit, Set.of())) {
				Set<CompilationUnitRewriteOperation> operations = new LinkedHashSet<>();
				Set<ASTNode> processed = new HashSet<>();
				for (UseFunctionalCallFixCore handler : handlers) {
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
