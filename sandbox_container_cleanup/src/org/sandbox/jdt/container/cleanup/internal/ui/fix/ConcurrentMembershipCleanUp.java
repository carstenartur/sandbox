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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.sandbox.jdt.cleanup.multifile.AbstractPlannedMultiFileCleanUp;
import org.sandbox.jdt.cleanup.multifile.MultiFileCandidateDiagnostic;
import org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpDiagnostics;
import org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpPlanResult;
import org.sandbox.jdt.cleanup.multifile.MultiFilePlanningMetrics;
import org.sandbox.jdt.cleanup.multifile.MultiFileScopeDiagnostic;
import org.sandbox.jdt.cleanup.multifile.api.IMultiFileCleanUpDiagnosticsProvider;
import org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpOptions;
import org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpRule;
import org.sandbox.jdt.container.cleanup.internal.ui.fix.ConcurrentMembershipAnalyzer.Field;

/** Opt-in, previewable migration of membership-only fields whose complete Java source is in their declaring unit. */
public final class ConcurrentMembershipCleanUp extends AbstractPlannedMultiFileCleanUp<Map<String, List<Field>>>
		implements IMultiFileCleanUpDiagnosticsProvider {
	public static final String OPTION= ContainerCleanUpRule.CONCURRENT_MEMBERSHIP.optionId();
	private static final String DESCRIPTION= "Use a concurrent key set for private, null-free, individually atomic membership operations"; //$NON-NLS-1$

	public ConcurrentMembershipCleanUp() {
	}

	public ConcurrentMembershipCleanUp(Map<String, String> options) {
		super(options);
	}

	@Override
	public CleanUpRequirements getRequirements() {
		return new CleanUpRequirements(isEnabled(ContainerCleanUpOptions.CLEANUP) && isEnabled(OPTION), false, false, null);
	}

	@Override
	protected MultiFileCleanUpPlanResult<Map<String, List<Field>>> createPlan(IJavaProject project, ICompilationUnit[] units,
			IProgressMonitor monitor) {
		if (!isEnabled(ContainerCleanUpOptions.CLEANUP) || !isEnabled(OPTION)) {
			return MultiFileCleanUpPlanResult.noPlan();
		}
		Map<String, List<Field>> plan= new LinkedHashMap<>();
		List<MultiFileCandidateDiagnostic> diagnostics= new ArrayList<>();
		for (ICompilationUnit unit : units) {
			if (monitor != null && monitor.isCanceled()) {
				throw new OperationCanceledException();
			}
			ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
			parser.setSource(unit);
			parser.setResolveBindings(true);
			var ast= (CompilationUnit) parser.createAST(monitor);
			var fields= ConcurrentMembershipAnalyzer.analyze(ast);
			String handle= unit.getPrimary().getHandleIdentifier();
			List<Field> accepted= fields.values().stream().filter(f -> f.rejection().isEmpty()).toList();
			if (!accepted.isEmpty()) {
				plan.put(handle, accepted);
			}
			for (Field field : fields.values()) {
				String id= "membership-" + UUID.nameUUIDFromBytes((handle + "|" + field.key()).getBytes(StandardCharsets.UTF_8)); //$NON-NLS-1$ //$NON-NLS-2$
				diagnostics.add(field.rejection().isEmpty()
						? MultiFileCandidateDiagnostic.transformed(id, handle, DESCRIPTION, List.of())
						: MultiFileCandidateDiagnostic.rejected(id, handle, "UNPROVEN_MEMBERSHIP", field.rejection(), List.of())); //$NON-NLS-1$
			}
		}
		return new MultiFileCleanUpPlanResult<>(plan.isEmpty() ? null : Map.copyOf(plan), new RefactoringStatus(),
				MultiFilePlanningMetrics.empty(), new MultiFileCleanUpDiagnostics(OPTION, MultiFileScopeDiagnostic.empty(), diagnostics));
	}

	@Override
	protected ICleanUpFix createFixForPlan(Map<String, List<Field>> plan, CleanUpContext context) throws CoreException {
		var unit= context.getCompilationUnit();
		List<Field> fields= plan.get(unit.getPrimary().getHandleIdentifier());
		if (fields == null) {
			return null;
		}
		CompilationUnit ast= context.getAST();
		if (ast == null) {
			throw new CoreException(Status.error("Cannot revalidate the membership plan without the current syntax tree.")); //$NON-NLS-1$
		}
		var current= ConcurrentMembershipAnalyzer.analyze(ast);
		var operations= new ArrayList<CompilationUnitRewriteOperationWithSourceRange>();
		for (Field field : fields) {
			if (!field.equals(current.get(field.key()))
					|| !(ast.findDeclaringNode(field.key()) instanceof VariableDeclarationFragment declaration)) {
				throw new CoreException(Status.error("The private membership field or its uses changed after planning.")); //$NON-NLS-1$
			}
			operations.add(new CompilationUnitRewriteOperationWithSourceRange() {
				@Override
				public void rewriteASTInternal(CompilationUnitRewrite rewrite, LinkedProposalModelCore linked) {
					var factory= ast.getAST().newMethodInvocation();
					String owner= rewrite.getImportRewrite().addImport("java.util.concurrent.ConcurrentHashMap"); //$NON-NLS-1$
					factory.setExpression(ast.getAST().newName(owner));
					factory.setName(ast.getAST().newSimpleName("newKeySet")); //$NON-NLS-1$
					rewrite.getASTRewrite().replace(declaration.getInitializer(), factory, createTextEditGroup(DESCRIPTION, rewrite));
				}
			});
		}
		return new CompilationUnitRewriteOperationsFixCore(DESCRIPTION, ast,
				operations.toArray(CompilationUnitRewriteOperationWithSourceRange[]::new));
	}

	@Override
	public String getLastPlanningDiagnosticsJson(IJavaProject project) {
		return getPlanningDiagnostics(project).toJson();
	}

	@Override
	public String[] getStepDescriptions() {
		return isEnabled(ContainerCleanUpOptions.CLEANUP) && isEnabled(OPTION) ? new String[] { DESCRIPTION } : new String[0];
	}

	@Override
	public String getPreview() {
		return isEnabled(ContainerCleanUpOptions.CLEANUP) && isEnabled(OPTION)
				? "Set<String> seen = ConcurrentHashMap.newKeySet();\n" //$NON-NLS-1$
				: "Set<String> seen = Collections.synchronizedSet(new HashSet<>());\n"; //$NON-NLS-1$
	}
}
