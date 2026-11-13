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
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.Statement;
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
import org.sandbox.jdt.container.cleanup.internal.ui.fix.ListenerSnapshotAnalyzer.Plan;

/** Opt-in, previewable migration of listener-only fields whose complete Java source is in their declaring unit. */
public final class ListenerSnapshotCleanUp extends AbstractPlannedMultiFileCleanUp<Map<String, List<Plan>>>
		implements IMultiFileCleanUpDiagnosticsProvider {
	public static final String OPTION= ContainerCleanUpRule.LISTENER_SNAPSHOT.optionId();
	private static final String DESCRIPTION= "Use copy-on-write snapshots for private listeners (each registration copies the list)"; //$NON-NLS-1$

	public ListenerSnapshotCleanUp() {
	}

	public ListenerSnapshotCleanUp(Map<String, String> options) {
		super(options);
	}

	@Override
	public CleanUpRequirements getRequirements() {
		return new CleanUpRequirements(isEnabled(ContainerCleanUpOptions.CLEANUP) && isEnabled(OPTION), false, false, null);
	}

	@Override
	protected MultiFileCleanUpPlanResult<Map<String, List<Plan>>> createPlan(IJavaProject project, ICompilationUnit[] units,
			IProgressMonitor monitor) {
		if (!isEnabled(ContainerCleanUpOptions.CLEANUP) || !isEnabled(OPTION)) {
			return MultiFileCleanUpPlanResult.noPlan();
		}
		Map<String, List<Plan>> plan= new LinkedHashMap<>();
		List<MultiFileCandidateDiagnostic> diagnostics= new ArrayList<>();
		for (ICompilationUnit unit : units) {
			if (monitor != null && monitor.isCanceled()) {
				throw new OperationCanceledException();
			}
			ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
			parser.setSource(unit);
			parser.setResolveBindings(true);
			var ast= (CompilationUnit) parser.createAST(monitor);
			List<Plan> fields= ListenerSnapshotAnalyzer.analyze(ast).values().stream()
					.map(ListenerSnapshotAnalyzer.Observed::plan).toList();
			String handle= unit.getPrimary().getHandleIdentifier();
			List<Plan> accepted= fields.stream().filter(f -> f.rejection().isEmpty()).toList();
			if (!accepted.isEmpty()) {
				plan.put(handle, accepted);
			}
			for (Plan field : fields) {
				String id= "listener-" + UUID.nameUUIDFromBytes((handle + "|" + field.key()).getBytes(StandardCharsets.UTF_8)); //$NON-NLS-1$ //$NON-NLS-2$
				diagnostics.add(field.rejection().isEmpty()
						? MultiFileCandidateDiagnostic.transformed(id, handle, DESCRIPTION, List.of())
						: MultiFileCandidateDiagnostic.rejected(id, handle, "UNPROVEN_LISTENER_SNAPSHOT", field.rejection(), List.of())); //$NON-NLS-1$
			}
		}
		return new MultiFileCleanUpPlanResult<>(plan.isEmpty() ? null : Map.copyOf(plan), new RefactoringStatus(),
				MultiFilePlanningMetrics.empty(), new MultiFileCleanUpDiagnostics(OPTION, MultiFileScopeDiagnostic.empty(), diagnostics));
	}

	@Override
	protected ICleanUpFix createFixForPlan(Map<String, List<Plan>> plan, CleanUpContext context) throws CoreException {
		var unit= context.getCompilationUnit();
		List<Plan> fields= plan.get(unit.getPrimary().getHandleIdentifier());
		if (fields == null) {
			return null;
		}
		CompilationUnit ast= context.getAST();
		if (ast == null) {
			throw new CoreException(Status.error("Cannot revalidate the listener plan without the current syntax tree.")); //$NON-NLS-1$
		}
		var current= ListenerSnapshotAnalyzer.analyze(ast);
		var operations= new ArrayList<CompilationUnitRewriteOperationWithSourceRange>();
		for (Plan field : fields) {
			var protocol= current.get(field.key());
			if (protocol == null || !field.equals(protocol.plan())) {
				throw new CoreException(Status.error("The private listener field or its uses changed after planning.")); //$NON-NLS-1$
			}
			operations.add(new CompilationUnitRewriteOperationWithSourceRange() {
				@Override
				public void rewriteASTInternal(CompilationUnitRewrite rewrite, LinkedProposalModelCore linked) {
					var syntax= ast.getAST();
					var edits= rewrite.getASTRewrite();
					var group= createTextEditGroup(DESCRIPTION, rewrite);
					String owner= rewrite.getImportRewrite().addImport("java.util.concurrent.CopyOnWriteArrayList"); //$NON-NLS-1$
					var creation= syntax.newClassInstanceCreation();
					ParameterizedType type= syntax.newParameterizedType(syntax.newSimpleType(syntax.newName(owner)));
					creation.setType(type);
					edits.replace(protocol.field().getInitializer(), creation, group);
					for (var lock : protocol.registrations()) {
						Statement add= (Statement) lock.getBody().statements().getFirst();
						edits.replace(lock, edits.createMoveTarget(add), group);
					}
					for (var notification : protocol.notifications()) {
						edits.remove(notification.array(), group);
						edits.remove(notification.lock(), group);
						edits.replace(notification.loop().getExpression(), syntax.newSimpleName(protocol.field().getName().getIdentifier()), group);
					}
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
				? "List<Runnable> listeners = new CopyOnWriteArrayList<>();\n" //$NON-NLS-1$
				: "List<Runnable> listeners = new ArrayList<>();\n"; //$NON-NLS-1$
	}
}
