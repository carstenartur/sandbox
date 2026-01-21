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
package org.sandbox.jdt.internal.corext.fix;

import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.util.TightSourceRangeComputer;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.helper.AfterClassJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.AfterJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.AssertJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.AssertOptimizationJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.AssumeJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.AssumeOptimizationJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.BeforeClassJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.BeforeJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.CategoryJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.ExternalResourceJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.IgnoreJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.ParameterizedTestJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.RuleExpectedExceptionJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.RuleExternalResourceJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.RuleTemporayFolderJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.RuleTestnameJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.RuleTimeoutJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.RunWithJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.TestJUnit3Plugin;
import org.sandbox.jdt.internal.corext.fix.helper.TestJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.TestTimeoutJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.TestExpectedJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.LostTestFinderJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractTool;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;
import org.sandbox.jdt.internal.ui.fix.MultiFixMessages;

public enum JUnitCleanUpFixCore {

	BEFORE(new BeforeJUnitPlugin()),
	AFTER(new AfterJUnitPlugin()),
	TEST(new TestJUnitPlugin()),
	TEST3(new TestJUnit3Plugin()),
	TEST_TIMEOUT(new TestTimeoutJUnitPlugin()),
	TEST_EXPECTED(new TestExpectedJUnitPlugin()),
	BEFORECLASS(new BeforeClassJUnitPlugin()),
	AFTERCLASS(new AfterClassJUnitPlugin()),
	IGNORE(new IgnoreJUnitPlugin()),
	CATEGORY(new CategoryJUnitPlugin()),
	RUNWITH(new RunWithJUnitPlugin()),
	ASSERT(new AssertJUnitPlugin()),
	ASSERT_OPTIMIZATION(new AssertOptimizationJUnitPlugin()),
	ASSUME(new AssumeJUnitPlugin()),
	ASSUME_OPTIMIZATION(new AssumeOptimizationJUnitPlugin()),
	RULEEXTERNALRESOURCE(new RuleExternalResourceJUnitPlugin()),
	RULETESTNAME(new RuleTestnameJUnitPlugin()),
	RULETEMPORARYFOLDER(new RuleTemporayFolderJUnitPlugin()),
	RULETIMEOUT(new RuleTimeoutJUnitPlugin()),
	RULEEXPECTEDEXCEPTION(new RuleExpectedExceptionJUnitPlugin()),
	EXTERNALRESOURCE(new ExternalResourceJUnitPlugin()),
	LOSTTESTS(new LostTestFinderJUnitPlugin()),
	PARAMETERIZED(new ParameterizedTestJUnitPlugin());

	AbstractTool<ReferenceHolder<Integer, JunitHolder>> junitfound;

	@SuppressWarnings("unchecked")
	JUnitCleanUpFixCore(AbstractTool<? extends ReferenceHolder<Integer, JunitHolder>> junitprocess) {
		this.junitfound= (AbstractTool<ReferenceHolder<Integer, JunitHolder>>) junitprocess;
	}

	public String getPreview(boolean i) {
		long countother= junitfound.getPreview(!i).lines().count();
		StringBuilder preview= new StringBuilder(junitfound.getPreview(i));
		long countnow= preview.toString().lines().count();
		if(countnow<countother) {
			for (long ii=0;ii<countother-countnow;ii++) {
				preview.append(System.lineSeparator());
			}
		}
		return preview.toString();
	}

	/**
	 * Compute set of CompilationUnitRewriteOperation to refactor supported
	 * situations
	 *
	 * @param compilationUnit        unit to search in
	 * @param operations             set of all CompilationUnitRewriteOperations
	 *                               created already
	 * @param nodesprocessed         list to remember nodes already processed
	 * @param createForOnlyIfVarUsed true if for loop should be created only only if
	 *                               loop var used within
	 */
	public void findOperations(final CompilationUnit compilationUnit,
			final Set<CompilationUnitRewriteOperationWithSourceRange> operations, final Set<ASTNode> nodesprocessed) {
		junitfound.find(this, compilationUnit, operations, nodesprocessed);
	}

	public CompilationUnitRewriteOperationWithSourceRange rewrite(final ReferenceHolder<Integer, JunitHolder> hit) {
		return new CompilationUnitRewriteOperationWithSourceRange() {
			@Override
			public void rewriteASTInternal(final CompilationUnitRewrite cuRewrite,
					final LinkedProposalModelCore linkedModel) throws CoreException {
				TextEditGroup group= createTextEditGroup(Messages.format(MultiFixMessages.JUnitCleanUp_description,
						new Object[] { JUnitCleanUpFixCore.this.toString() }), cuRewrite);
				TightSourceRangeComputer rangeComputer;
				ASTRewrite rewrite= cuRewrite.getASTRewrite();
				if (rewrite.getExtendedSourceRangeComputer() instanceof TightSourceRangeComputer) {
					rangeComputer= (TightSourceRangeComputer) rewrite.getExtendedSourceRangeComputer();
				} else {
					rangeComputer= new TightSourceRangeComputer();
				}
				rangeComputer.addTightSourceNode(hit.get(0).minv);
				rewrite.setTargetSourceRangeComputer(rangeComputer);
				junitfound.rewrite(JUnitCleanUpFixCore.this, hit, cuRewrite, group);
			}
		};
	}

	@Override
	public String toString() {
		return junitfound.toString();
	}
}
