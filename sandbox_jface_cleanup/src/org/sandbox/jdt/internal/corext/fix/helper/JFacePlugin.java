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

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
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
import org.osgi.framework.Bundle;
import org.sandbox.jdt.internal.common.AstProcessorBuilder;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JfaceCleanUpFixCore;

/**
 * Cleanup transformation for migrating from deprecated {@link SubProgressMonitor} to {@link SubMonitor}.
 * 
 * <p>This helper transforms progress monitor usage patterns in Eclipse JDT code:</p>
 * <ul>
 * <li>Converts {@code IProgressMonitor.beginTask()} to {@code SubMonitor.convert()}</li>
 * <li>Replaces {@code new SubProgressMonitor(monitor, work)} with {@code subMonitor.split(work)}</li>
 * <li>Handles both 2-argument and 3-argument SubProgressMonitor constructors</li>
 * <li>Generates unique variable names to avoid collisions in scope</li>
 * </ul>
 * 
 * <p><b>Migration Pattern:</b></p>
 * <pre>
 * // Before:
 * monitor.beginTask("Main Task", 100);
 * IProgressMonitor subMon = new SubProgressMonitor(monitor, 60);
 * 
 * // After:
 * SubMonitor subMonitor = SubMonitor.convert(monitor, "Main Task", 100);
 * IProgressMonitor subMon = subMonitor.split(60);
 * </pre>
 * 
 * @see SubProgressMonitor
 * @see SubMonitor
 */
public class JFacePlugin extends
AbstractTool<ReferenceHolder<Integer, JFacePlugin.MonitorHolder>> {

	public static final String CLASS_INSTANCE_CREATION = "ClassInstanceCreation"; //$NON-NLS-1$
	public static final String METHODINVOCATION = "MethodInvocation"; //$NON-NLS-1$

	/** Debug option key for enabling JFace plugin transformation logging */
	private static final String DEBUG_OPTION = "sandbox_jface_cleanup/debug/jfaceplugin"; //$NON-NLS-1$
	
	/** Bundle symbolic name for logging */
	private static final String BUNDLE_ID = "sandbox_jface_cleanup"; //$NON-NLS-1$

	/**
	 * Holder for monitor-related transformation data.
	 * Tracks beginTask invocations and associated SubProgressMonitor instances.
	 */
	public static class MonitorHolder {
		/** The beginTask method invocation to be converted */
		public MethodInvocation minv;
		/** The monitor variable name from beginTask expression */
		public String minvname;
		/** Set of SubProgressMonitor constructions to be converted to split() calls */
		public Set<ClassInstanceCreation> setofcic = new HashSet<>();
		/** Nodes that have been processed to avoid duplicate transformations */
		public Set<ASTNode> nodesprocessed;
	}

	/**
	 * Checks if debug logging is enabled for JFace plugin transformations.
	 * 
	 * @return {@code true} if debug logging is enabled, {@code false} otherwise
	 */
	private static boolean isDebugEnabled() {
		return Platform.inDebugMode() && "true".equalsIgnoreCase(Platform.getDebugOption(DEBUG_OPTION)); //$NON-NLS-1$
	}

	/**
	 * Logs a debug message if debug mode is enabled.
	 * 
	 * @param message the message to log
	 */
	private static void logDebug(String message) {
		if (isDebugEnabled()) {
			try {
				Bundle bundle = Platform.getBundle(BUNDLE_ID);
				if (bundle != null) {
					ILog log = Platform.getLog(bundle);
					log.log(new Status(IStatus.INFO, BUNDLE_ID, "JFacePlugin: " + message)); //$NON-NLS-1$
				}
			} catch (Exception e) {
				System.err.println("Failed to log debug message: " + e.getMessage());
				e.printStackTrace(System.err);
			}
		}
	}

	/**
	 * Finds and identifies SubProgressMonitor usage patterns to be transformed.
	 * 
	 * <p>This method scans the compilation unit for:</p>
	 * <ul>
	 * <li>{@code beginTask} method invocations on IProgressMonitor instances</li>
	 * <li>{@code SubProgressMonitor} constructor invocations that reference the same monitor</li>
	 * </ul>
	 * 
	 * <p>When both patterns are found in the same scope, a cleanup operation is registered
	 * to transform them to the SubMonitor pattern.</p>
	 * 
	 * @param fixcore the cleanup fix core instance
	 * @param compilationUnit the compilation unit to analyze
	 * @param operations set to collect identified cleanup operations
	 * @param nodesprocessed set of nodes already processed to avoid duplicates
	 * @param createForOnlyIfVarUsed flag to control when operations are created (unused in this implementation)
	 */
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
				logDebug("Found beginTask at position " + node.getStartPosition() + ": " + node); //$NON-NLS-1$ //$NON-NLS-2$
				
				// Check if parent is ExpressionStatement, otherwise skip
				if (!(node.getParent() instanceof ExpressionStatement)) {
					return true;
				}
				
				Expression expr = node.getExpression();
				if (expr == null) {
					return true;
				}
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
				Expression firstArg = (Expression) arguments.get(0);
				String firstArgName = null;
				
				// Try to extract SimpleName from the expression
				SimpleName sn = ASTNodes.as(firstArg, SimpleName.class);
				if (sn != null) {
					firstArgName = sn.getIdentifier();
				}
				
				if (firstArgName == null || !mh.minvname.equals(firstArgName)) {
					return true;
				}
				logDebug("Found SubProgressMonitor at position " + node.getStartPosition() + ": " + node); //$NON-NLS-1$ //$NON-NLS-2$
				mh.setofcic.add(node);
				operations.add(fixcore.rewrite(holder));
				return true;
			})
			.build(compilationUnit);
	}

	/**
	 * Rewrites AST nodes to transform SubProgressMonitor patterns to SubMonitor.
	 * 
	 * <p>Performs two main transformations:</p>
	 * <ol>
	 * <li><b>beginTask → convert:</b> Transforms {@code monitor.beginTask(msg, work)} 
	 *     to {@code SubMonitor subMonitor = SubMonitor.convert(monitor, msg, work)}</li>
	 * <li><b>SubProgressMonitor → split:</b> Transforms constructor calls:
	 *     <ul>
	 *     <li>2-arg: {@code new SubProgressMonitor(monitor, work)} → {@code subMonitor.split(work)}</li>
	 *     <li>3-arg: {@code new SubProgressMonitor(monitor, work, flags)} → {@code subMonitor.split(work, flags)}</li>
	 *     </ul>
	 * </li>
	 * </ol>
	 * 
	 * <p>The transformation ensures:</p>
	 * <ul>
	 * <li>Unique variable names for SubMonitor to avoid collisions</li>
	 * <li>Preservation of flags parameter in 3-arg constructors</li>
	 * <li>Removal of SubProgressMonitor import</li>
	 * <li>Addition of SubMonitor import</li>
	 * </ul>
	 * 
	 * @param upp the cleanup fix core instance
	 * @param hit the holder containing identified monitor patterns to transform
	 * @param cuRewrite the compilation unit rewrite context
	 * @param group the text edit group for tracking changes
	 */
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
				logDebug("Rewriting beginTask at position " + minv.getStartPosition() + ": " + minv); //$NON-NLS-1$ //$NON-NLS-2$
				
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
				logDebug("Created SubMonitor.convert call: " + staticCall); //$NON-NLS-1$
			}
			
			for (ClassInstanceCreation submon : mh.setofcic) {
				List<?> arguments = submon.arguments();
				if (arguments.size() < 2) {
					continue;
				}
				
				ASTNode origarg = (ASTNode) arguments.get(1);
				logDebug("Rewriting SubProgressMonitor at position " + submon.getStartPosition() + ": " + submon); //$NON-NLS-1$ //$NON-NLS-2$
				
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
	 * Generates a unique variable name that doesn't collide with existing variables in scope.
	 * 
	 * <p>This method ensures the SubMonitor variable name doesn't conflict with other
	 * variables visible at the transformation point. If the base name is already in use,
	 * a numeric suffix is appended (e.g., "subMonitor2", "subMonitor3", etc.).</p>
	 * 
	 * @param node the AST node context for scope analysis
	 * @param baseName the base name to use (e.g., "subMonitor")
	 * @return a unique variable name that doesn't exist in the current scope
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
