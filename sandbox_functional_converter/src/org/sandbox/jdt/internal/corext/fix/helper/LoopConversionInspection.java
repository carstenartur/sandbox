/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.ArrayList;

import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.BuildContext;
import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.core.compiler.CompilationParticipant;
import org.eclipse.jdt.core.compiler.ReconcileContext;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblem;
import org.eclipse.jdt.internal.compiler.problem.ProblemSeverities;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore.LoopConversionOperation;
import org.sandbox.jdt.internal.ui.fix.LoopConversionQuickAssistProcessor;

/** Optional, managed JDT problems; disabled by default, also cleared on reconcile. */
public final class LoopConversionInspection extends CompilationParticipant {
	public static final String PLUGIN_ID = "sandbox_functional_converter"; //$NON-NLS-1$
	public static final String MARKER_TYPE = PLUGIN_ID + ".loopConversionProblem"; //$NON-NLS-1$
	public static final int PROBLEM_ID = 12001;
	public static final String SEVERITY = "loopConversion.inspectionSeverity"; //$NON-NLS-1$
	public static final String TARGET = "loopConversion.inspectionTarget"; //$NON-NLS-1$

	public static boolean isEnabled() {
		return !"off".equals(preference(SEVERITY, "off")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	public static LoopTargetFormat target() {
		return LoopTargetFormat.fromId(preference(TARGET, "stream")); //$NON-NLS-1$
	}

	private static String preference(String key, String fallback) {
		return Platform.getPreferencesService().getString(PLUGIN_ID, key, fallback, null);
	}

	@Override
	public boolean isActive(org.eclipse.jdt.core.IJavaProject project) {
		// Remain active when disabled so JDT can remove old managed problems.
		return true;
	}

	@Override
	public void buildStarting(BuildContext[] files, boolean batch) {
		for (BuildContext file : files) {
			if (!isEnabled()) {
				file.recordNewProblems(new CategorizedProblem[0]);
				continue;
			}
			var unit = JavaCore.createCompilationUnitFrom(file.getFile());
			if (unit != null) {
				ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
				parser.setSource(unit);
				parser.setResolveBindings(true);
				file.recordNewProblems(problems((CompilationUnit) parser.createAST(null)));
			}
		}
	}

	@Override
	public void reconcile(ReconcileContext context) {
		try {
			context.putProblems(MARKER_TYPE, isEnabled() ? problems(context.getAST(AST.getJLSLatest())) : new CategorizedProblem[0]);
		} catch (JavaModelException unavailable) {
			context.putProblems(MARKER_TYPE, new CategorizedProblem[0]);
		}
	}

	public static CategorizedProblem[] problems(CompilationUnit unit) {
		if (!isEnabled() || unit == null) {
			return new CategorizedProblem[0];
		}
		LoopTargetFormat target = target();
		var problems = new ArrayList<CategorizedProblem>();
		for (var operation : LoopConversionService.analyze(unit, LoopConversionService.handlers(target)).operations()) {
			if (operation instanceof LoopConversionOperation loop) {
				ASTNode anchor = loop.getAnchor();
				int severity = "warning".equals(preference(SEVERITY, "off")) ? ProblemSeverities.Warning : ProblemSeverities.Info; //$NON-NLS-1$ //$NON-NLS-2$
				char[] fileName = unit.getJavaElement() == null ? new char[0] : unit.getJavaElement().getPath().toString().toCharArray();
				problems.add(new DefaultProblem(fileName, LoopConversionQuickAssistProcessor.label(target), PROBLEM_ID,
						new String[] { target.getId() }, severity, anchor.getStartPosition(),
						anchor.getStartPosition() + anchor.getLength() - 1, unit.getLineNumber(anchor.getStartPosition()), 0) {
					@Override
					public String getMarkerType() {
						return MARKER_TYPE;
					}
					@Override
					public int getCategoryID() {
						return CAT_CODE_STYLE;
					}
				});
			}
		}
		return problems.toArray(CategorizedProblem[]::new);
	}
}
