package org.sandbox.jdt.internal.corext.fix;

/*-
 * #%L
 * Sandbox junit cleanup
 * %%
 * Copyright (C) 2024 hammer
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

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
import org.sandbox.jdt.internal.corext.fix.helper.AbstractTool;
import org.sandbox.jdt.internal.corext.fix.helper.AfterClassJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.AfterJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.AssertJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.AssumeJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.BeforeClassJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.BeforeJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.ExternalResourceJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.IgnoreJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.JunitHolder;
import org.sandbox.jdt.internal.corext.fix.helper.RuleExternalResourceJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.RuleTemporayFolderJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.RuleTestnameJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.RunWithJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.TestJUnit3Plugin;
import org.sandbox.jdt.internal.corext.fix.helper.TestJUnitPlugin;
import org.sandbox.jdt.internal.ui.fix.MultiFixMessages;

public enum JUnitCleanUpFixCore {

	BEFORE(new BeforeJUnitPlugin()),
	AFTER(new AfterJUnitPlugin()),
	TEST(new TestJUnitPlugin()),
	TEST3(new TestJUnit3Plugin()),
	BEFORECLASS(new BeforeClassJUnitPlugin()),
	AFTERCLASS(new AfterClassJUnitPlugin()),
	IGNORE(new IgnoreJUnitPlugin()),
	RUNWITH(new RunWithJUnitPlugin()),
	ASSERT(new AssertJUnitPlugin()),
	ASSUME(new AssumeJUnitPlugin()),
	RULEEXTERNALRESOURCE(new RuleExternalResourceJUnitPlugin()),
	RULETESTNAME(new RuleTestnameJUnitPlugin()),
	RULETEMPORARYFOLDER(new RuleTemporayFolderJUnitPlugin()),
	EXTERNALRESOURCE(new ExternalResourceJUnitPlugin());

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
