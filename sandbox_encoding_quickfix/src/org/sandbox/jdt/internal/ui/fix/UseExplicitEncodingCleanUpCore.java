/*******************************************************************************
 * Copyright (c) 2021 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;

import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.EXPLICITENCODING_AGGREGATE_TO_UTF8;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.EXPLICITENCODING_CLEANUP;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.EXPLICITENCODING_INSERT_UTF8;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.EXPLICITENCODING_KEEP_BEHAVIOR;
import static org.sandbox.jdt.internal.ui.fix.MultiFixMessages.ExplicitEncodingCleanUpFix_refactor;
import static org.sandbox.jdt.internal.ui.fix.MultiFixMessages.ExplicitEncodingCleanUp_description;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.text.edits.TextEditGroup;

import org.sandbox.jdt.cleanup.multifile.api.IMultiFileCleanUpScopeProvider;
import org.sandbox.jdt.internal.corext.fix.UseExplicitEncodingFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.ChangeBehavior;
import org.sandbox.jdt.internal.corext.fix.helper.EncodingCleanUpFix;
import org.sandbox.jdt.internal.corext.fix.helper.EncodingDslRemovedCatchImportCleanup;
import org.sandbox.jdt.triggerpattern.cleanup.CharsetConstructorMigration;
import org.sandbox.jdt.triggerpattern.cleanup.CheckedExceptionMigration;
import org.sandbox.jdt.triggerpattern.cleanup.HintFileFixCore;

public class UseExplicitEncodingCleanUpCore extends AbstractCleanUp implements IMultiFileCleanUpScopeProvider {
	private final CharsetCleanUpCoordinator coordinator= new CharsetCleanUpCoordinator(this);

	public UseExplicitEncodingCleanUpCore(final Map<String, String> options) {
		super(options);
	}

	public UseExplicitEncodingCleanUpCore() {
	}

	@Override
	public CleanUpRequirements getRequirements() {
		return new CleanUpRequirements(requireAST(), false, false, null);
	}

	public boolean requireAST() {
		return isEnabled(EXPLICITENCODING_CLEANUP) && !computeFixSet().isEmpty();
	}

	@Override
	public ICleanUpFix createFix(final CleanUpContext context) throws CoreException {
		if (context.getCompilationUnit() == null || context.getAST() == null || !requireAST()) return null;
		if (coordinator.isPrepared(context.getCompilationUnit().getJavaProject())) return coordinator.createFix(context);
		return createEncodingFix(context, CharsetConstructorMigration.plan(List.of(context.getAST())));
	}

	@Override
	public RefactoringStatus checkPreConditions(IJavaProject project, ICompilationUnit[] units, IProgressMonitor monitor) throws CoreException {
		return coordinator.checkPreConditions(project, units, monitor);
	}

	@Override
	public RefactoringStatus checkPostConditions(IProgressMonitor monitor) throws CoreException {
		return coordinator.checkPostConditions(monitor);
	}

	@Override
	public Collection<ICompilationUnit> expandCleanUpScope(IJavaProject project, Collection<ICompilationUnit> selected,
			IProgressMonitor monitor) throws CoreException {
		return coordinator.expandCleanUpScope(project, selected, monitor);
	}

	public Collection<Map<String, Object>> getCoordinatedCleanUpPreview(IJavaProject project) throws CoreException {
		return coordinator.getCoordinatedCleanUpPreview(project);
	}

	ICleanUpFix createEncodingFix(CleanUpContext context, CheckedExceptionMigration.Plan exceptionPlan) throws CoreException {
		CompilationUnit compilationUnit= context.getAST();
		if (compilationUnit == null) return null;
		EnumSet<UseExplicitEncodingFixCore> computeFixSet= computeFixSet();
		if (!isEnabled(EXPLICITENCODING_CLEANUP) || computeFixSet.isEmpty()) return null;

		ChangeBehavior cb= computeRefactorDeepth();
		Set<CompilationUnitRewriteOperation> operations= new LinkedHashSet<>();
		Set<ASTNode> nodesprocessed= new HashSet<>();

		if (exceptionPlan != null) {
			exceptionPlan.requireComplete();
			operations.add(new CompilationUnitRewriteOperation() {
				@Override
				public void rewriteAST(CompilationUnitRewrite rewrite, LinkedProposalModelCore model) {
					CharsetConstructorMigration.install(rewrite, exceptionPlan);
				}
			});
		}

		// Declarative rules run first; imperative helpers remain the fallback for
		// transformations whose source/exception semantics are not fully declarative.
		boolean dslActive= cb != ChangeBehavior.ENFORCE_UTF8_AGGREGATE;
		if (dslActive) {
			Map<String, String> compilerOptions= new HashMap<>();
			compilerOptions.put("sandbox.cleanup.mode", cb.name()); //$NON-NLS-1$
			if (compilationUnit.getJavaElement() != null
					&& compilationUnit.getJavaElement().getJavaProject() != null) {
				String sourceVersion= compilationUnit.getJavaElement().getJavaProject()
						.getOption(JavaCore.COMPILER_SOURCE, true);
				if (sourceVersion != null) compilerOptions.put(JavaCore.COMPILER_SOURCE, sourceVersion);
			}
			HintFileFixCore.findOperationsForBundle(
					compilationUnit, "encoding", operations, nodesprocessed, compilerOptions); //$NON-NLS-1$
		}

		computeFixSet.forEach(fix -> {
			if (!dslActive || !fix.isDslHandled()) {
				fix.findOperations(compilationUnit, operations, nodesprocessed, cb);
			}
		});

		if (dslActive) {
			CompilationUnitRewriteOperation importCleanup= EncodingDslRemovedCatchImportCleanup.create(nodesprocessed);
			if (importCleanup != null) operations.add(importCleanup);
		}

		if (exceptionPlan != null) {
			operations.add(new CompilationUnitRewriteOperation() {
				@Override
				public void rewriteAST(CompilationUnitRewrite rewrite, LinkedProposalModelCore model) throws CoreException {
					exceptionPlan.apply(rewrite, new TextEditGroup("Adapt checked exceptions to Charset constructors")); //$NON-NLS-1$
				}
			});
		}

		if (operations.isEmpty() || (exceptionPlan != null && operations.size() == 2
				&& !exceptionPlan.hasChanges(context.getCompilationUnit().getHandleIdentifier()))) return null;

		CompilationUnitRewriteOperation[] array= operations.toArray(
				new CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation[0]);
		return new EncodingCleanUpFix(ExplicitEncodingCleanUpFix_refactor, compilationUnit, array);
	}

	private ChangeBehavior computeRefactorDeepth() {
		ChangeBehavior cb= ChangeBehavior.KEEP_BEHAVIOR;
		if (isEnabled(EXPLICITENCODING_KEEP_BEHAVIOR)) cb= ChangeBehavior.KEEP_BEHAVIOR;
		if (isEnabled(EXPLICITENCODING_INSERT_UTF8)) cb= ChangeBehavior.ENFORCE_UTF8;
		if (isEnabled(EXPLICITENCODING_AGGREGATE_TO_UTF8)) cb= ChangeBehavior.ENFORCE_UTF8_AGGREGATE;
		return cb;
	}

	@Override
	public String[] getStepDescriptions() {
		List<String> result= new ArrayList<>();
		if (isEnabled(EXPLICITENCODING_CLEANUP)) {
			String with= computeRefactorDeepth().toString();
			result.add(Messages.format(ExplicitEncodingCleanUp_description,
					new Object[] { String.join(",", computeFixSet().stream().map(UseExplicitEncodingFixCore::toString).collect(Collectors.toList())), with })); //$NON-NLS-1$
		}
		return result.toArray(new String[0]);
	}

	@Override
	public String getPreview() {
		StringBuilder sb= new StringBuilder();
		EnumSet<UseExplicitEncodingFixCore> computeFixSet= computeFixSet();
		ChangeBehavior cb= computeRefactorDeepth();
		EnumSet.allOf(UseExplicitEncodingFixCore.class).forEach(e -> sb.append(e.getPreview(computeFixSet.contains(e), cb)));
		return sb.toString();
	}

	private EnumSet<UseExplicitEncodingFixCore> computeFixSet() {
		if (isEnabled(EXPLICITENCODING_CLEANUP)) return EnumSet.allOf(UseExplicitEncodingFixCore.class);
		return EnumSet.noneOf(UseExplicitEncodingFixCore.class);
	}
}
