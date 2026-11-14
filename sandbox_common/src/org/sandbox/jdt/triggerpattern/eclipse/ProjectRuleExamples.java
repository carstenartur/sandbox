/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.eclipse;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.sandbox.jdt.triggerpattern.api.RuleExampleVerifier;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlanContext;
import org.sandbox.jdt.triggerpattern.internal.GuardRegistry;

/** Replays authoring examples with the current destination project's bindings and compiler options. */
public final class ProjectRuleExamples {

	private ProjectRuleExamples() {
	}

	/** Replays rules using the project's options, classpath and exact source paths. */
	public static <T> T withVerifier(IJavaProject project, IProgressMonitor monitor, Function<RuleExampleVerifier, T> action) {
		checkCanceled(monitor);
		GuardRegistry.getInstance();
		Map<String, String> options = Map.copyOf(project.getOptions(true));
		try (var context = SemanticRewritePlanContext.install(SemanticRewritePlan.empty(), options,
				() -> checkCanceled(monitor))) {
			var verifier = new RuleExampleVerifier(options,
					(unitName, source) -> parseSource(project, options, unitName, source, monitor));
			T result = action.apply(verifier);
			checkCanceled(monitor);
			return result;
		}
	}

	public static CompilationUnit parseSource(IJavaProject project, Map<String, String> options,
			String unitName, String source, IProgressMonitor monitor) {
		checkCanceled(monitor);
		if (project == null || !project.exists() || !project.getProject().isAccessible()) {
			throw new IllegalArgumentException("An accessible Java project is required for example bindings"); //$NON-NLS-1$
		}
		if (unitName == null || !unitName.endsWith(".java") || unitName.indexOf('\\') >= 0 //$NON-NLS-1$
				|| unitName.indexOf(':') >= 0 || Arrays.stream(unitName.split("/", -1)) //$NON-NLS-1$
						.anyMatch(segment -> segment.isEmpty() || segment.equals(".") || segment.equals(".."))) { //$NON-NLS-1$ //$NON-NLS-2$
			throw new IllegalArgumentException("unitName must be a project-relative Java source path, such as src/p/Example.java"); //$NON-NLS-1$
		}
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setProject(project);
		parser.setCompilerOptions(options);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(source.toCharArray());
		// JDT needs the workspace path when setProject() is used; the sidecar stays
		// project-relative so its evidence can be reopened in a renamed project.
		parser.setUnitName(project.getPath().append(new Path(unitName)).toPortableString());
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(false);
		parser.setStatementsRecovery(false);
		CompilationUnit result = (CompilationUnit) parser.createAST(monitor);
		checkCanceled(monitor);
		return result;
	}

	public static void checkCanceled(IProgressMonitor monitor) {
		if (monitor.isCanceled() || Thread.currentThread().isInterrupted()) {
			throw new OperationCanceledException();
		}
	}

}
