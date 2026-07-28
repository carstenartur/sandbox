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

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_JUPITER_SUITE;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_RUNWITH;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_SUITE;

import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.TypeLiteral;
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
import org.sandbox.jdt.internal.corext.fix.helper.FixMethodOrderJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.IgnoreJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.LostTestFinderJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.RuleErrorCollectorJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.RuleExpectedExceptionJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.RuleExternalResourceJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.RuleTemporayFolderJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.RuleTestnameJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.RuleTimeoutJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.RunWithJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.TestExpectedAndTimeoutJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.TestExpectedJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.TestJUnit3Plugin;
import org.sandbox.jdt.internal.corext.fix.helper.TestJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.TestTimeoutJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.ThrowingRunnableJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractTool;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;
import org.sandbox.jdt.internal.ui.fix.MultiFixMessages;

public enum JUnitCleanUpFixCore {

	BEFORE(new BeforeJUnitPlugin()),
	AFTER(new AfterJUnitPlugin()),
	TEST(new TestJUnitPlugin()),
	TEST3(new TestJUnit3Plugin()),
	TEST_EXPECTED_TIMEOUT(new TestExpectedAndTimeoutJUnitPlugin()),
	TEST_TIMEOUT(new TestTimeoutJUnitPlugin()),
	TEST_EXPECTED(new TestExpectedJUnitPlugin()),
	BEFORECLASS(new BeforeClassJUnitPlugin()),
	AFTERCLASS(new AfterClassJUnitPlugin()),
	IGNORE(new IgnoreJUnitPlugin()),
	CATEGORY(new CategoryJUnitPlugin()),
	FIX_METHOD_ORDER(new FixMethodOrderJUnitPlugin()),
	RUNWITH(new RunWithJUnitPlugin()),
	ASSERT(new AssertJUnitPlugin()),
	ASSERT_OPTIMIZATION(new AssertOptimizationJUnitPlugin()),
	ASSUME(new AssumeJUnitPlugin()),
	ASSUME_OPTIMIZATION(new AssumeOptimizationJUnitPlugin()),
	// Dedicated rule migrations must claim their types before the generic
	// ExternalResource rule fallback sees the same field.
	RULETESTNAME(new RuleTestnameJUnitPlugin()),
	RULETEMPORARYFOLDER(new RuleTemporayFolderJUnitPlugin()),
	RULETIMEOUT(new RuleTimeoutJUnitPlugin()),
	RULEEXPECTEDEXCEPTION(new RuleExpectedExceptionJUnitPlugin()),
	RULEERRORCOLLECTOR(new RuleErrorCollectorJUnitPlugin()),
	RULEEXTERNALRESOURCE(new RuleExternalResourceJUnitPlugin()),
	EXTERNALRESOURCE(new ExternalResourceJUnitPlugin()),
	LOSTTESTS(new LostTestFinderJUnitPlugin()),
	PARAMETERIZED(new ClosedParameterizedTestJUnitPlugin()),
	THROWINGRUNNABLE(new ThrowingRunnableJUnitPlugin());

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

	public void findOperations(final CompilationUnit compilationUnit,
			final Set<CompilationUnitRewriteOperationWithSourceRange> operations, final Set<ASTNode> nodesprocessed) {
		junitfound.find(this, compilationUnit, operations, nodesprocessed);
	}

	public CompilationUnitRewriteOperationWithSourceRange rewrite(final ReferenceHolder<Integer, JunitHolder> hit) {
		JunitHolder rangeAnchor= hit.get(0);
		JunitHolder snapshot= hit.get(hit.size() - 1);
		ReferenceHolder<Integer, JunitHolder> operationData= ReferenceHolder.createIndexed();
		operationData.put(Integer.valueOf(0), snapshot);
		boolean suiteRunnerPresent= this == RUNWITH && hasSuiteRunner(snapshot.getMinv());
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
				// Preserve historical operation ordering while keeping rewrite data isolated.
				rangeComputer.addTightSourceNode(rangeAnchor.getMinv());
				rewrite.setTargetSourceRangeComputer(rangeComputer);
				junitfound.rewrite(JUnitCleanUpFixCore.this, operationData, cuRewrite, group);
				if (suiteRunnerPresent) {
					// A later SuiteClasses rewrite removes the old Suite simple name. Re-add
					// the JUnit Platform import after every related independent operation.
					cuRewrite.getImportRewrite().addImport(ORG_JUNIT_JUPITER_SUITE);
				}
			}
		};
	}

	private static boolean hasSuiteRunner(ASTNode node) {
		if (!(node.getRoot() instanceof CompilationUnit root)) {
			return false;
		}
		boolean[] result= new boolean[1];
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(SingleMemberAnnotation annotation) {
				ITypeBinding annotationType= annotation.resolveTypeBinding();
				String annotationName= annotationType == null
						? annotation.getTypeName().getFullyQualifiedName()
						: annotationType.getQualifiedName();
				if (!ORG_JUNIT_RUNWITH.equals(annotationName) && !"RunWith".equals(annotationName)) { //$NON-NLS-1$
					return true;
				}
				if (annotation.getValue() instanceof TypeLiteral typeLiteral) {
					ITypeBinding runnerBinding= typeLiteral.getType().resolveBinding();
					String runnerName= runnerBinding == null ? typeLiteral.getType().toString()
							: runnerBinding.getQualifiedName();
					if (ORG_JUNIT_SUITE.equals(runnerName) || "Suite".equals(runnerName)) { //$NON-NLS-1$
						result[0]= true;
						return false;
					}
				}
				return true;
			}
		});
		return result[0];
	}

	@Override
	public String toString() {
		return junitfound.toString();
	}
}
