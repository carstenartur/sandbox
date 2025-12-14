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
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.AstProcessorBuilder;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JfaceCleanUpFixCore;

/**
 *
 * SubProgressMonitor has been deprecated What is affected: Clients that refer
 * to org.eclipse.core.runtime.SubProgressMonitor.
 *
 * Description: org.eclipse.core.runtime.SubProgressMonitor has been deprecated
 * and replaced by org.eclipse.core.runtime.SubMonitor.
 *
 * Action required:
 *
 * Calls to IProgressMonitor.beginTask on the root monitor should be replaced by
 * a call to SubMonitor.convert. Keep the returned SubMonitor around as a local
 * variable and refer to it instead of the root monitor for the remainder of the
 * method. All calls to SubProgressMonitor(IProgressMonitor, int) should be
 * replaced by calls to SubMonitor.split(int). If a SubProgressMonitor is
 * constructed using the SUPPRESS_SUBTASK_LABEL flag, it will be transformed to
 * SubMonitor.split(int, int) with the flags parameter preserved. It is not
 * necessary to call done on an instance of SubMonitor. Example:
 *
 * Consider the following example: void someMethod(IProgressMonitor pm) {
 * pm.beginTask("Main Task", 100); SubProgressMonitor subMonitor1= new
 * SubProgressMonitor(pm, 60); try { doSomeWork(subMonitor1); } finally {
 * subMonitor1.done(); } SubProgressMonitor subMonitor2= new
 * SubProgressMonitor(pm, 40); try { doSomeMoreWork(subMonitor2); } finally {
 * subMonitor2.done(); } } The above code should be refactored to this: void
 * someMethod(IProgressMonitor pm) { SubMonitor subMonitor =
 * SubMonitor.convert(pm, "Main Task", 100); doSomeWork(subMonitor.split(60));
 * doSomeMoreWork(subMonitor.split(40)); }
 */
public class JFacePlugin extends
AbstractTool<ReferenceHolder<Integer, JFacePlugin.MonitorHolder>> {

	public static final String CLASS_INSTANCE_CREATION = "ClassInstanceCreation"; //$NON-NLS-1$
	public static final String METHODINVOCATION = "MethodInvocation"; //$NON-NLS-1$

	public static class MonitorHolder {
		public MethodInvocation minv;
		public String minvname;
		public Set<ClassInstanceCreation> setofcic = new HashSet<>();
		public Set<ASTNode> nodesprocessed;
	}

	@Override
	public void find(JfaceCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed,
			boolean createForOnlyIfVarUsed) {
		ReferenceHolder<Integer, MonitorHolder> dataholder = new ReferenceHolder<>();
		
		AstProcessorBuilder.with(dataholder, nodesprocessed)
			.processor()
			.callMethodInvocationVisitor(IProgressMonitor.class, "beginTask", (node, holder) -> { //$NON-NLS-1$
				if (node.arguments().size() != 2) {
					return true;
				}
				System.out.println("begintask[" + node.getStartPosition() + "] " + node.getNodeType() + " :" + node); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				SimpleName sn = ASTNodes.as(node.getExpression(), SimpleName.class);
				if (sn != null) {
					IBinding ibinding = sn.resolveBinding();
					String name = ibinding.getName();
					MonitorHolder mh = new MonitorHolder();
					mh.minv = node;
					mh.minvname = name;
					mh.nodesprocessed = nodesprocessed;
					holder.put(holder.size(), mh);
				}
				return true;
			}
			System.out.println("begintask[" + node.getStartPosition() + "] " + node.getNodeType() + " :" + node); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			
			// Check if parent is ExpressionStatement, otherwise skip
			if (!(node.getParent() instanceof ExpressionStatement)) {
				return true;
			}
			
			Expression expr = node.getExpression();
			SimpleName sn = ASTNodes.as(expr, SimpleName.class);
			if (sn != null) {
				IBinding ibinding = sn.resolveBinding();
				// Add null-check for binding
				if (ibinding == null) {
					return true;
				}
				String name = ibinding.getName();
				MonitorHolder mh = new MonitorHolder();
				mh.minv = node;
				mh.minvname = name;
				mh.nodesprocessed = nodesprocessed;
				holder.put(holder.size(), mh);
			}
			return true;
		}, s -> ASTNodes.getTypedAncestor(s, Block.class))
		.callClassInstanceCreationVisitor(SubProgressMonitor.class, (node, holder) -> {
			// Guard against empty holder
			if (holder.isEmpty()) {
				return true;
			}
			MonitorHolder mh = holder.get(holder.size() - 1);
			List<?> arguments = node.arguments();
			if (arguments.isEmpty()) {
				return true;
			}
			
			// Safe handling of first argument - extract identifier from expression
			Object firstArg = arguments.get(0);
			String firstArgName = null;
			
			// Try to extract SimpleName from the expression
			SimpleName sn = ASTNodes.as((ASTNode) firstArg, SimpleName.class);
			if (sn != null) {
				firstArgName = sn.getIdentifier();
			}
			
			if (firstArgName == null || !mh.minvname.equals(firstArgName)) {
				return true;
			})
			.build(compilationUnit);
	}

	@Override
	public void rewrite(JfaceCleanUpFixCore upp, final ReferenceHolder<Integer, MonitorHolder> hit,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group) {
		ASTRewrite rewrite = cuRewrite.getASTRewrite();
		AST ast = cuRewrite.getRoot().getAST();
		ImportRewrite importRemover = cuRewrite.getImportRewrite();
		
		// Guard against empty holder
		if (hit.isEmpty()) {
			return;
		}
		
		Set<ASTNode> nodesprocessed = hit.get(hit.size() - 1).nodesprocessed;
		for (Entry<Integer, MonitorHolder> entry : hit.entrySet()) {

			MonitorHolder mh = entry.getValue();
			MethodInvocation minv = mh.minv;
			
			// Generate unique identifier name for SubMonitor variable
			String identifier = generateUniqueVariableName(minv, "subMonitor"); //$NON-NLS-1$
			
			if (!nodesprocessed.contains(minv)) {
				nodesprocessed.add(minv);
				System.out.println("rewrite methodinvocation [" + minv.getStartPosition() + "] " + minv); //$NON-NLS-1$ //$NON-NLS-2$
				
				// Ensure parent is ExpressionStatement
				if (!(minv.getParent() instanceof ExpressionStatement)) {
					continue;
				}
				
				List<ASTNode> arguments = minv.arguments();

				/**
				 * Here we process the "beginTask" and change it to "SubMonitor.convert"
				 *
				 * monitor.beginTask(NewWizardMessages.NewSourceFolderWizardPage_operation, 3);
				 * SubMonitor subMonitor =
				 * SubMonitor.convert(monitor,NewWizardMessages.NewSourceFolderWizardPage_operation,
				 * 3);
				 *
				 */

				SingleVariableDeclaration newVariableDeclarationStatement = ast.newSingleVariableDeclaration();

				newVariableDeclarationStatement.setName(ast.newSimpleName(identifier));
				newVariableDeclarationStatement
				.setType(ast.newSimpleType(addImport(SubMonitor.class.getCanonicalName(), cuRewrite, ast)));

				MethodInvocation staticCall = ast.newMethodInvocation();
				staticCall.setExpression(ASTNodeFactory.newName(ast, SubMonitor.class.getSimpleName()));
				staticCall.setName(ast.newSimpleName("convert")); //$NON-NLS-1$
				List<ASTNode> staticCallArguments = staticCall.arguments();
				staticCallArguments.add(
						ASTNodes.createMoveTarget(rewrite, ASTNodes.getUnparenthesedExpression(minv.getExpression())));
				staticCallArguments
				.add(ASTNodes.createMoveTarget(rewrite, ASTNodes.getUnparenthesedExpression(arguments.get(0))));
				staticCallArguments
				.add(ASTNodes.createMoveTarget(rewrite, ASTNodes.getUnparenthesedExpression(arguments.get(1))));
				newVariableDeclarationStatement.setInitializer(staticCall);

				ASTNodes.replaceButKeepComment(rewrite, minv, newVariableDeclarationStatement, group);
				System.out.println("result " + staticCall); //$NON-NLS-1$
			}
			
			for (ClassInstanceCreation submon : mh.setofcic) {
				List<?> arguments = submon.arguments();
				if (arguments.size() < 2) {
					continue;
				}
				
				ASTNode origarg = (ASTNode) arguments.get(1);
				System.out.println("rewrite spminstance [" + submon.getStartPosition() + "] " + submon); //$NON-NLS-1$ //$NON-NLS-2$
				
				/**
				 * Handle both 2-arg and 3-arg SubProgressMonitor constructors:
				 * 
				 * 2-arg: new SubProgressMonitor(monitor, work)
				 *   -> subMonitor.split(work)
				 *   
				 * 3-arg: new SubProgressMonitor(monitor, work, flags)
				 *   -> subMonitor.split(work, flags)
				 */
				MethodInvocation newMethodInvocation2 = ast.newMethodInvocation();
				newMethodInvocation2.setName(ast.newSimpleName("split")); //$NON-NLS-1$
				newMethodInvocation2.setExpression(ASTNodeFactory.newName(ast, identifier));
				List<ASTNode> splitCallArguments = newMethodInvocation2.arguments();

				// Add the work amount (second argument)
				splitCallArguments
				.add(ASTNodes.createMoveTarget(rewrite, ASTNodes.getUnparenthesedExpression(origarg)));
				
				// Check for 3-arg constructor (with flags)
				if (arguments.size() >= 3) {
					ASTNode flagsArg = (ASTNode) arguments.get(2);
					splitCallArguments.add(ASTNodes.createMoveTarget(rewrite, ASTNodes.getUnparenthesedExpression(flagsArg)));
				}
				
				ASTNodes.replaceButKeepComment(rewrite, submon, newMethodInvocation2, group);
				importRemover.removeImport(SubProgressMonitor.class.getCanonicalName());
			}
		}
	}
	
	/**
	 * Generate a unique variable name that doesn't collide with existing variables in scope.
	 * 
	 * @param node The AST node context for scope analysis
	 * @param baseName The base name to use (e.g., "subMonitor")
	 * @return A unique variable name
	 */
	private String generateUniqueVariableName(ASTNode node, String baseName) {
		Collection<String> usedNames = getUsedVariableNames(node);
		
		// If base name is not used, return it
		if (!usedNames.contains(baseName)) {
			return baseName;
		}
		
		// Otherwise, append a number until we find an unused name
		int counter = 2;
		String candidate = baseName + counter;
		while (usedNames.contains(candidate)) {
			counter++;
			candidate = baseName + counter;
		}
		return candidate;
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (!afterRefactoring) {
			return """
					monitor.beginTask(NewWizardMessages.NewSourceFolderWizardPage_operation, 3);
						IProgressMonitor subProgressMonitor= new SubProgressMonitor(monitor, 1);
						IProgressMonitor subProgressMonitor2= new SubProgressMonitor(monitor, 2);
				"""; //$NON-NLS-1$
		}
		return """
				SubMonitor subMonitor=SubMonitor.convert(monitor,NewWizardMessages.NewSourceFolderWizardPage_operation,3);
					IProgressMonitor subProgressMonitor= subMonitor.split(1);
					IProgressMonitor subProgressMonitor2= subMonitor.split(2);
			"""; //$NON-NLS-1$
	}
}
