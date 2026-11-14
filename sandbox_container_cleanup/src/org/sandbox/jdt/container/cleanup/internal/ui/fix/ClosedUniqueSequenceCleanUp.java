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
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
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
import org.sandbox.jdt.cleanup.multifile.MultiFileCandidateOutcome;
import org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpDiagnostics;
import org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpPlanResult;
import org.sandbox.jdt.cleanup.multifile.MultiFilePlanningMetrics;
import org.sandbox.jdt.cleanup.multifile.MultiFileScopeDiagnostic;
import org.sandbox.jdt.cleanup.multifile.api.IMultiFileCleanUpDiagnosticsProvider;
import org.sandbox.jdt.container.cleanup.internal.corext.fix.ClosedUniqueSequenceFlow;
import org.sandbox.jdt.container.cleanup.internal.corext.fix.ClosedUniqueSequenceFlow.Edit;
import org.sandbox.jdt.container.cleanup.internal.corext.fix.ClosedUniqueSequenceFlow.Kind;
import org.sandbox.jdt.container.cleanup.internal.corext.fix.ClosedUniqueSequencePlanner;
import org.sandbox.jdt.container.cleanup.internal.corext.fix.ClosedUniqueSequencePlanner.Plan;
import org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpOptions;
import org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpRule;

/** Project-wide ordered-set migration. Each producer, caller and reader is one atomic preview group. */
public final class ClosedUniqueSequenceCleanUp extends AbstractPlannedMultiFileCleanUp<List<Plan>>
		implements IMultiFileCleanUpDiagnosticsProvider {
	public static final String OPTION= ContainerCleanUpRule.CLOSED_UNIQUE_SEQUENCE.optionId();
	private static final String DESCRIPTION= "Migrate a unique list return, callers and read-only parameters to an encounter-ordered set"; //$NON-NLS-1$

	public ClosedUniqueSequenceCleanUp() {
	}

	public ClosedUniqueSequenceCleanUp(Map<String, String> options) {
		super(options);
	}

	private boolean enabled() {
		return isEnabled(ContainerCleanUpOptions.CLEANUP) && isEnabled(OPTION);
	}

	@Override
	public CleanUpRequirements getRequirements() {
		return new CleanUpRequirements(enabled(), false, false, null);
	}

	@Override
	protected Collection<ICompilationUnit> discoverAdditionalCompilationUnits(IJavaProject project, Collection<ICompilationUnit> selected,
			IProgressMonitor monitor) throws CoreException {
		if (!enabled()) {
			return List.of();
		}
		var roots= ClosedUniqueSequencePlanner.parse(project, selected, monitor);
		Set<ICompilationUnit> required= new LinkedHashSet<>();
		for (var root : roots.values()) {
			for (var producer : ClosedUniqueSequenceFlow.seeds(root)) {
				var result= ClosedUniqueSequencePlanner.analyze(producer, selected, roots, monitor);
				if (result.plan() != null) {
					required.addAll(result.required());
				}
			}
		}
		required.removeAll(selected);
		return List.copyOf(required);
	}

	@Override
	protected MultiFileCleanUpPlanResult<List<Plan>> createPlan(IJavaProject project, ICompilationUnit[] units,
			IProgressMonitor monitor) throws CoreException {
		if (!enabled()) {
			return MultiFileCleanUpPlanResult.noPlan();
		}
		var selected= List.of(units);
		var roots= ClosedUniqueSequencePlanner.parse(project, selected, monitor);
		var plans= new ArrayList<Plan>();
		var diagnostics= new ArrayList<MultiFileCandidateDiagnostic>();
		Set<String> shared= new HashSet<>();
		Set<String> claimed= new HashSet<>();
		for (var entry : roots.entrySet()) {
			String owner= entry.getKey();
			for (var producer : ClosedUniqueSequenceFlow.seeds(entry.getValue())) {
				var result= ClosedUniqueSequencePlanner.analyze(producer, selected, roots, monitor);
				var related= result.required().stream().map(unit -> unit.getPrimary().getHandleIdentifier()).filter(handle -> !owner.equals(handle)).sorted().toList();
				if (result.plan() == null || !roots.keySet().containsAll(result.plan().unitEdits().keySet())) {
					String reason= result.plan() == null ? result.reason() : "The selected scope omits a required producer, caller or reader."; //$NON-NLS-1$
					diagnostics.add(MultiFileCandidateDiagnostic.rejected(id(producer), owner, "ORDERED_SET_CLOSURE_INCOMPLETE", reason, related)); //$NON-NLS-1$
					continue;
				}
				var plan= result.plan();
				plans.add(plan);
				for (String handle : plan.unitEdits().keySet()) {
					if (!claimed.add(handle)) {
						shared.add(handle);
					}
				}
			}
		}
		var accepted= new ArrayList<Plan>();
		for (var plan : plans) {
			var method= (IMethod) JavaCore.create(plan.producerHandle());
			String owner= method.getCompilationUnit().getPrimary().getHandleIdentifier();
			var related= plan.unitEdits().keySet().stream().filter(handle -> !owner.equals(handle)).sorted().toList();
			if (plan.unitEdits().keySet().stream().anyMatch(shared::contains)) {
				diagnostics.add(MultiFileCandidateDiagnostic.rejected(id(method), owner, "OVERLAPPING_ORDERED_SET_CLOSURES", //$NON-NLS-1$
						"Multiple candidate groups require edits in the same compilation unit.", related)); //$NON-NLS-1$
			} else {
				accepted.add(plan);
				var decision= plan.compatibility();
				diagnostics.add(new MultiFileCandidateDiagnostic(id(method), owner, MultiFileCandidateOutcome.TRANSFORMED,
						"TRANSFORMED", DESCRIPTION, related, Optional.of(new MultiFileCandidateDiagnostic.Compatibility( //$NON-NLS-1$
								decision.mode().name(), decision.bridge().name()))));
			}
		}
		return new MultiFileCleanUpPlanResult<>(accepted.isEmpty() ? null : List.copyOf(accepted), new RefactoringStatus(),
				MultiFilePlanningMetrics.empty(), new MultiFileCleanUpDiagnostics(OPTION, MultiFileScopeDiagnostic.empty(), diagnostics));
	}

	@Override
	protected ICleanUpFix createFixForPlan(List<Plan> plans, CleanUpContext context) throws CoreException {
		var unit= context.getCompilationUnit();
		String handle= unit.getPrimary().getHandleIdentifier();
		for (var plan : plans) {
			if (!plan.unitEdits().containsKey(handle)) {
				continue;
			}
			var root= context.getAST();
			if (root == null || !(JavaCore.create(plan.producerHandle()) instanceof IMethod producer) || !producer.exists()) {
				throw new CoreException(Status.error("The producer or the current syntax tree is missing.")); //$NON-NLS-1$
			}
			var fresh= ClosedUniqueSequencePlanner.analyze(producer, List.of(unit), Map.of(handle, root), null);
			if (!plan.equals(fresh.plan())) {
				throw new CoreException(Status.error("The ordered-set producer, its callers or its read-only parameters changed after preview.")); //$NON-NLS-1$
			}
			return createFix(root, plan.unitEdits().get(handle));
		}
		return null;
	}

	@Override
	protected List<String> getCandidatePreviewDetails(List<Plan> plans, MultiFileCandidateDiagnostic candidate) throws CoreException {
		for (var plan : plans) {
			if (JavaCore.create(plan.producerHandle()) instanceof IMethod method && id(method).equals(candidate.candidateId())) {
				var lines= new ArrayList<String>();
				lines.add(plan.compatibility().summary());
				lines.add("Return List to Set: " + method.getDeclaringType().getFullyQualifiedName() + "." + method.getElementName()); //$NON-NLS-1$ //$NON-NLS-2$
				for (String handle : plan.readerHandles()) {
					if (JavaCore.create(handle) instanceof IMethod reader) {
						lines.add("Read-only parameter List to Set: " + reader.getDeclaringType().getFullyQualifiedName() + "." + reader.getElementName()); //$NON-NLS-1$ //$NON-NLS-2$
					}
				}
				plan.unitEdits().entrySet().stream().filter(entry -> entry.getValue().stream().anyMatch(edit -> edit.kind() == Kind.CALLER_SET_TYPE))
						.map(Map.Entry::getKey).sorted().forEach(handle -> lines.add("Source caller: " + JavaCore.create(handle).getElementName())); //$NON-NLS-1$
				return List.copyOf(lines);
			}
		}
		return List.of();
	}

	private static String id(IMethod method) {
		return "ordered-set-" + UUID.nameUUIDFromBytes(method.getHandleIdentifier().getBytes(StandardCharsets.UTF_8)); //$NON-NLS-1$
	}

	private static ICleanUpFix createFix(CompilationUnit root, List<Edit> localEdits) throws CoreException {
		var operations= new ArrayList<CompilationUnitRewriteOperationWithSourceRange>();
		for (var edit : localEdits) {
			ASTNode found= NodeFinder.perform(root, edit.start(), edit.length());
			// A SimpleName and its enclosing SimpleType have identical source ranges.
			while (found != null && found.getStartPosition() == edit.start() && found.getLength() == edit.length()
					&& !(found instanceof IfStatement) && !(found instanceof Type)
					&& !(edit.kind() == Kind.SYNCHRONIZED_SET_FACTORY && found instanceof SimpleName)) {
				found= found.getParent();
			}
			ASTNode node= found;
			if (node == null || node.getStartPosition() != edit.start() || node.getLength() != edit.length()
					|| !switch (edit.kind()) {
					case GUARDED_ADD -> node instanceof IfStatement;
					case SYNCHRONIZED_SET_FACTORY -> node instanceof SimpleName;
					default -> node instanceof Type;
					}) {
				throw new CoreException(Status.error("A planned ordered-set syntax change could not be resolved.")); //$NON-NLS-1$
			}
			operations.add(new CompilationUnitRewriteOperationWithSourceRange() {
				@Override
				public void rewriteASTInternal(CompilationUnitRewrite change, LinkedProposalModelCore linked) {
					var rewrite= change.getASTRewrite();
					var group= createTextEditGroup(DESCRIPTION, change);
					if (node instanceof IfStatement guard) {
						rewrite.replace(guard, rewrite.createMoveTarget(guard.getThenStatement()), group);
					} else if (edit.kind() == Kind.SYNCHRONIZED_SET_FACTORY) {
						rewrite.replace(node, root.getAST().newSimpleName("synchronizedSet"), group); //$NON-NLS-1$
					} else {
						change.getImportRemover().registerRemovedNode(node);
						var imports= change.getImportRewrite();
						String target= edit.kind() == Kind.ORDERED_SET_TYPE ? "java.util.LinkedHashSet" : "java.util.Set"; //$NON-NLS-1$ //$NON-NLS-2$
						String imported= imports.addImport(target, new ContextSensitiveImportRewriteContext(node, imports));
						var ast= root.getAST();
						rewrite.replace(node, ast.newSimpleType(ast.newName(imported)), group);
					}
				}
			});
		}
		return new CompilationUnitRewriteOperationsFixCore(DESCRIPTION, root, operations.toArray(CompilationUnitRewriteOperationWithSourceRange[]::new));
	}

	@Override
	public String getLastPlanningDiagnosticsJson(IJavaProject project) {
		var diagnostics= getPlanningDiagnostics(project);
		return OPTION.equals(diagnostics.cleanupId()) ? diagnostics.toJson() : ""; //$NON-NLS-1$
	}

	@Override
	public String[] getStepDescriptions() {
		return enabled() ? new String[] {DESCRIPTION} : new String[0];
	}

	@Override
	public String getPreview() {
		return enabled() ? "Set<String> names = unique(input);\n" : "List<String> names = unique(input);\n"; //$NON-NLS-1$ //$NON-NLS-2$
	}
}
