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
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
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
 * Cleanup transformation for migrating from deprecated {@code SubProgressMonitor} to {@link SubMonitor}.
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
		/** Standalone SubProgressMonitor constructions (without associated beginTask) */
		public Set<ClassInstanceCreation> standaloneSubProgressMonitors = new HashSet<>();
		/** SubProgressMonitor on already-SubMonitor variables (use split() directly) */
		public Set<ClassInstanceCreation> subProgressMonitorOnSubMonitor = new HashSet<>();
		/** SubProgressMonitor type references to be replaced with IProgressMonitor */
		public Set<org.eclipse.jdt.core.dom.Type> typesToReplace = new HashSet<>();
		/** Nodes that have been processed to avoid duplicate transformations (references shared Set passed during construction) */
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
		
		// Track which SubProgressMonitor nodes are associated with beginTask (prevents Pass 2 from re-processing them)
		Set<ASTNode> beginTaskAssociated = new HashSet<>();
		
		// Pass 1: Find beginTask + SubProgressMonitor patterns (chained visitors)
		AstProcessorBuilder.with(dataholder, nodesprocessed)
			.processor()
			.callMethodInvocationVisitor(IProgressMonitor.class, "beginTask", (node, holder) -> { //$NON-NLS-1$
				if (node.arguments().size() != 2) {
					return true;
				}
				logDebug("Found beginTask at position " + node.getStartPosition() + " (type: " + node.getClass().getSimpleName() + ")"); //$NON-NLS-1$ //$NON-NLS-2$
				
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
			.callClassInstanceCreationVisitor("org.eclipse.core.runtime.SubProgressMonitor", (node, holder) -> { //$NON-NLS-1$
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
				
				// Check if this SubProgressMonitor is associated with a beginTask
				if (!holder.isEmpty() && firstArgName != null) {
					MonitorHolder mh = holder.get(holder.size() - 1);
					if (mh.minvname.equals(firstArgName)) {
						logDebug("Found SubProgressMonitor construction at position " + node.getStartPosition() + " for variable '" + firstArgName + "' with beginTask"); //$NON-NLS-1$ //$NON-NLS-2$
						mh.setofcic.add(node);
						beginTaskAssociated.add(node);
					}
				}
				
				return true;
			}, s -> ASTNodes.getTypedAncestor(s, Block.class))
			.build(compilationUnit);
		
		// Add operations for beginTask-associated monitors
		if (!dataholder.isEmpty()) {
			operations.add(fixcore.rewrite(dataholder));
		}
		
		// Pass 2: Find standalone SubProgressMonitor instances using direct ASTVisitor
		ReferenceHolder<Integer, MonitorHolder> standaloneHolder = new ReferenceHolder<>();
		
		compilationUnit.accept(new ASTVisitor() {
			@Override
			public boolean visit(ClassInstanceCreation node) {
				// Check if this is a SubProgressMonitor construction (use simple name like Pass 1)
				ITypeBinding binding = node.resolveTypeBinding();
				if (binding == null || !"SubProgressMonitor".equals(binding.getName())) { //$NON-NLS-1$
					return true;
				}
				
				// Skip nodes already associated with beginTask from pass 1
				if (beginTaskAssociated.contains(node)) {
					return true;
				}
				
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
				
				// Check if the variable is already a SubMonitor type (use simple name like Pass 1)
				boolean isSubMonitorType = false;
				if (sn != null) {
					IBinding snBinding = sn.resolveBinding();
					if (snBinding != null && snBinding.getKind() == IBinding.VARIABLE) {
						ITypeBinding typeBinding = 
							((org.eclipse.jdt.core.dom.IVariableBinding) snBinding).getType();
						if (typeBinding != null) {
							isSubMonitorType = "SubMonitor".equals(typeBinding.getName()); //$NON-NLS-1$
						}
					}
				}
				
				// If already SubMonitor type, handle separately (use split() directly)
				if (isSubMonitorType && firstArgName != null) {
					logDebug("Found SubProgressMonitor on already-SubMonitor variable at position " + node.getStartPosition() + " for variable '" + firstArgName + "'"); //$NON-NLS-1$ //$NON-NLS-2$
					MonitorHolder mh = new MonitorHolder();
					mh.minvname = firstArgName;
					mh.nodesprocessed = nodesprocessed;
					mh.subProgressMonitorOnSubMonitor.add(node);
					standaloneHolder.put(standaloneHolder.size(), mh);
					return true;
				}
				
				// Standalone SubProgressMonitor (not associated with beginTask)
				String varName = firstArgName != null ? firstArgName : "monitor"; //$NON-NLS-1$
				logDebug("Found standalone SubProgressMonitor construction at position " + node.getStartPosition() + " for variable '" + varName + "' without beginTask"); //$NON-NLS-1$ //$NON-NLS-2$
				MonitorHolder mh = new MonitorHolder();
				mh.minvname = varName;
				mh.nodesprocessed = nodesprocessed;
				mh.standaloneSubProgressMonitors.add(node);
				standaloneHolder.put(standaloneHolder.size(), mh);
				
				return true;
			}
		});
		
		// Add operations for standalone SubProgressMonitor
		if (!standaloneHolder.isEmpty()) {
			operations.add(fixcore.rewrite(standaloneHolder));
		}
		
		// Pass 3: Find SubProgressMonitor type references for type replacement
		ReferenceHolder<Integer, MonitorHolder> typeReplacementHolder = new ReferenceHolder<>();
		MonitorHolder typeHolder = new MonitorHolder();
		typeHolder.nodesprocessed = nodesprocessed;
		
		compilationUnit.accept(new ASTVisitor() {
			/**
			 * Helper to check if a Type is SubProgressMonitor
			 */
			private boolean isSubProgressMonitorType(org.eclipse.jdt.core.dom.Type type) {
				if (type == null) {
					return false;
				}
				
				// First try binding (most reliable)
				ITypeBinding binding = type.resolveBinding();
				if (binding != null && !binding.isRecovered()) {
					return "org.eclipse.core.runtime.SubProgressMonitor".equals(binding.getQualifiedName()); //$NON-NLS-1$
				}
				
				// Fallback: check type name
				String typeName = getTypeName(type);
				return "SubProgressMonitor".equals(typeName) || //$NON-NLS-1$
					   "org.eclipse.core.runtime.SubProgressMonitor".equals(typeName); //$NON-NLS-1$
			}
			
			/**
			 * Extract type name from Type node
			 */
			private String getTypeName(org.eclipse.jdt.core.dom.Type type) {
				if (type.isSimpleType()) {
					org.eclipse.jdt.core.dom.SimpleType simpleType = (org.eclipse.jdt.core.dom.SimpleType) type;
					return simpleType.getName().getFullyQualifiedName();
				}
				if (type.isQualifiedType()) {
					org.eclipse.jdt.core.dom.QualifiedType qualifiedType = (org.eclipse.jdt.core.dom.QualifiedType) type;
					return qualifiedType.getName().getFullyQualifiedName();
				}
				if (type.isNameQualifiedType()) {
					org.eclipse.jdt.core.dom.NameQualifiedType nameQualifiedType = (org.eclipse.jdt.core.dom.NameQualifiedType) type;
					return nameQualifiedType.getName().getFullyQualifiedName();
				}
				return type.toString();
			}
			
			@Override
			public boolean visit(org.eclipse.jdt.core.dom.FieldDeclaration node) {
				org.eclipse.jdt.core.dom.Type fieldType = node.getType();
				if (isSubProgressMonitorType(fieldType)) {
					logDebug("Found SubProgressMonitor field declaration at position " + node.getStartPosition()); //$NON-NLS-1$
					typeHolder.typesToReplace.add(fieldType);
				}
				return true;
			}
			
			@Override
			public boolean visit(org.eclipse.jdt.core.dom.VariableDeclarationStatement node) {
				org.eclipse.jdt.core.dom.Type varType = node.getType();
				if (isSubProgressMonitorType(varType)) {
					logDebug("Found SubProgressMonitor variable declaration at position " + node.getStartPosition()); //$NON-NLS-1$
					typeHolder.typesToReplace.add(varType);
				}
				return true;
			}
			
			@Override
			public boolean visit(org.eclipse.jdt.core.dom.MethodDeclaration node) {
				org.eclipse.jdt.core.dom.Type returnType = node.getReturnType2();
				if (isSubProgressMonitorType(returnType)) {
					logDebug("Found SubProgressMonitor return type at position " + node.getStartPosition()); //$NON-NLS-1$
					typeHolder.typesToReplace.add(returnType);
				}
				return true;
			}
			
			@Override
			public boolean visit(org.eclipse.jdt.core.dom.SingleVariableDeclaration node) {
				org.eclipse.jdt.core.dom.Type paramType = node.getType();
				if (isSubProgressMonitorType(paramType)) {
					logDebug("Found SubProgressMonitor parameter type at position " + node.getStartPosition()); //$NON-NLS-1$
					typeHolder.typesToReplace.add(paramType);
				}
				return true;
			}
			
			@Override
			public boolean visit(org.eclipse.jdt.core.dom.CastExpression node) {
				org.eclipse.jdt.core.dom.Type castType = node.getType();
				if (isSubProgressMonitorType(castType)) {
					logDebug("Found SubProgressMonitor cast at position " + node.getStartPosition()); //$NON-NLS-1$
					typeHolder.typesToReplace.add(castType);
				}
				return true;
			}
		});
		
		// Add operations for type replacement if any types were found
		if (!typeHolder.typesToReplace.isEmpty()) {
			typeReplacementHolder.put(0, typeHolder);
			operations.add(fixcore.rewrite(typeReplacementHolder));
		}
		}
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
		
		// Track whether any flag was passed through unmapped (may still reference SubProgressMonitor)
		boolean hasUnmappedFlags = false;
		
		for (Entry<Integer, MonitorHolder> entry : hit.entrySet()) {

			MonitorHolder mh = entry.getValue();
			Set<ASTNode> nodesprocessed = mh.nodesprocessed;
			
			// Handle SubProgressMonitor on already-SubMonitor variables (use split() directly)
			if (!mh.subProgressMonitorOnSubMonitor.isEmpty()) {
				for (ClassInstanceCreation submon : mh.subProgressMonitorOnSubMonitor) {
					if (nodesprocessed.contains(submon)) {
						continue;
					}
					nodesprocessed.add(submon);
					
					List<?> arguments = submon.arguments();
					if (arguments.size() < 2) {
						continue;
					}
					
					logDebug("Rewriting SubProgressMonitor on SubMonitor variable at position " + submon.getStartPosition()); //$NON-NLS-1$
					
					// Create subMonitor.split(work [, flags]) call
					MethodInvocation splitCall = ast.newMethodInvocation();
					splitCall.setExpression(ASTNodes.createMoveTarget(rewrite, 
						ASTNodes.getUnparenthesedExpression((Expression) arguments.get(0))));
					splitCall.setName(ast.newSimpleName("split")); //$NON-NLS-1$
					List<ASTNode> splitArgs = splitCall.arguments();
					
					// Add work amount (second argument)
					splitArgs.add(ASTNodes.createMoveTarget(rewrite, 
						ASTNodes.getUnparenthesedExpression((Expression) arguments.get(1))));
					
					// Handle flags if present (3-arg constructor)
					if (arguments.size() >= 3) {
						Expression flagsArg = (Expression) arguments.get(2);
						FlagMappingResult flagResult = mapSubProgressMonitorFlags(flagsArg, ast, cuRewrite);
						
						if (flagResult.mappedExpression() != null) {
							splitArgs.add(flagResult.mappedExpression());
							if (flagResult.referencesSubMonitor()) {
								importRemover.addImport("org.eclipse.core.runtime.SubMonitor"); //$NON-NLS-1$
							}
						}
						if (flagResult.passedThrough()) {
							hasUnmappedFlags = true;
						}
					}
					
					ASTNodes.replaceButKeepComment(rewrite, submon, splitCall, group);
				}
				
				continue;
			}
			
			// Handle standalone SubProgressMonitor (without beginTask)
			if (!mh.standaloneSubProgressMonitors.isEmpty()) {
				for (ClassInstanceCreation submon : mh.standaloneSubProgressMonitors) {
					if (nodesprocessed.contains(submon)) {
						continue;
					}
					nodesprocessed.add(submon);
					
					List<?> arguments = submon.arguments();
					if (arguments.size() < 2) {
						continue;
					}
					
					logDebug("Rewriting standalone SubProgressMonitor at position " + submon.getStartPosition()); //$NON-NLS-1$
					
					// Create SubMonitor.convert(monitor) call
					MethodInvocation convertCall = ast.newMethodInvocation();
					convertCall.setExpression(addImport(SubMonitor.class.getCanonicalName(), cuRewrite, ast));
					convertCall.setName(ast.newSimpleName("convert")); //$NON-NLS-1$
					convertCall.arguments().add(
						ASTNodes.createMoveTarget(rewrite, ASTNodes.getUnparenthesedExpression((Expression) arguments.get(0))));
					
					// Create .split(work [, flags]) call chained on convert
					MethodInvocation splitCall = ast.newMethodInvocation();
					splitCall.setExpression(convertCall);
					splitCall.setName(ast.newSimpleName("split")); //$NON-NLS-1$
					List<ASTNode> splitArgs = splitCall.arguments();
					
					// Add work amount (second argument)
					splitArgs.add(ASTNodes.createMoveTarget(rewrite, 
						ASTNodes.getUnparenthesedExpression((Expression) arguments.get(1))));
					
					// Handle flags if present (3-arg constructor)
					if (arguments.size() >= 3) {
						Expression flagsArg = (Expression) arguments.get(2);
						FlagMappingResult flagResult = mapSubProgressMonitorFlags(flagsArg, ast, cuRewrite);
						
						if (flagResult.mappedExpression() != null) {
							splitArgs.add(flagResult.mappedExpression());
							if (flagResult.referencesSubMonitor()) {
								importRemover.addImport("org.eclipse.core.runtime.SubMonitor"); //$NON-NLS-1$
							}
						}
						if (flagResult.passedThrough()) {
							hasUnmappedFlags = true;
						}
					}
					
					ASTNodes.replaceButKeepComment(rewrite, submon, splitCall, group);
				}
				
				continue;
			}
			
			// Handle beginTask + SubProgressMonitor pattern
			MethodInvocation minv = mh.minv;
			
			// Skip if no beginTask (already handled as standalone above)
			if (minv == null) {
				continue;
			}
			
			// Generate unique identifier name for SubMonitor variable
			String identifier = generateUniqueVariableName(minv, "subMonitor"); //$NON-NLS-1$
			
			if (!nodesprocessed.contains(minv)) {
				nodesprocessed.add(minv);
				logDebug("Rewriting beginTask at position " + minv.getStartPosition() + " (method: " + minv.getName() + ")"); //$NON-NLS-1$ //$NON-NLS-2$
				
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

				// Create the static call to SubMonitor.convert
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

				// Create the variable declaration fragment (name + initializer)
				VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
				fragment.setName(ast.newSimpleName(identifier));
				fragment.setInitializer(staticCall);

				// Create the variable declaration statement (type + fragment)
				VariableDeclarationStatement varDeclStmt = ast.newVariableDeclarationStatement(fragment);
				varDeclStmt.setType(ast.newSimpleType(addImport(SubMonitor.class.getCanonicalName(), cuRewrite, ast)));

				// Replace the entire ExpressionStatement (parent of beginTask), not just the MethodInvocation
				ASTNodes.replaceButKeepComment(rewrite, minv.getParent(), varDeclStmt, group);
				logDebug("Created SubMonitor.convert call: " + staticCall); //$NON-NLS-1$
			}
			
			for (ClassInstanceCreation submon : mh.setofcic) {
				if (nodesprocessed.contains(submon)) {
					continue;
				}
				nodesprocessed.add(submon);
				
				List<?> arguments = submon.arguments();
				if (arguments.size() < 2) {
					continue;
				}
				
				ASTNode origarg = (ASTNode) arguments.get(1);
				logDebug("Rewriting SubProgressMonitor at position " + submon.getStartPosition() + " (ClassInstanceCreation)"); //$NON-NLS-1$ //$NON-NLS-2$
				
				/**
				 * Handle both 2-arg and 3-arg SubProgressMonitor constructors:
				 * 
				 * 2-arg: new SubProgressMonitor(monitor, work)
				 *   -> subMonitor.split(work)
				 *   
				 * 3-arg: new SubProgressMonitor(monitor, work, flags)
				 *   -> subMonitor.split(work, mappedFlags)
				 *   
				 * Flag mapping:
				 *   - SUPPRESS_SUBTASK_LABEL -> SUPPRESS_SUBTASK
				 *   - PREPEND_MAIN_LABEL_TO_SUBTASK -> dropped (no equivalent)
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
					Expression flagsArg = (Expression) arguments.get(2);
					FlagMappingResult flagResult = mapSubProgressMonitorFlags(flagsArg, ast, cuRewrite);
					
					// Only add the flag if it wasn't dropped (PREPEND_MAIN_LABEL_TO_SUBTASK is dropped)
					if (flagResult.mappedExpression() != null) {
						splitCallArguments.add(flagResult.mappedExpression());
						if (flagResult.referencesSubMonitor()) {
							importRemover.addImport("org.eclipse.core.runtime.SubMonitor"); //$NON-NLS-1$
						}
					}
					if (flagResult.passedThrough()) {
						hasUnmappedFlags = true;
					}
				}
				
				ASTNodes.replaceButKeepComment(rewrite, submon, newMethodInvocation2, group);
			}
		}
		
		// Handle type replacements (fields, parameters, return types, casts)
		for (Entry<Integer, MonitorHolder> entry : hit.entrySet()) {
			MonitorHolder mh = entry.getValue();
			
			if (!mh.typesToReplace.isEmpty()) {
				logDebug("Processing " + mh.typesToReplace.size() + " SubProgressMonitor type replacements"); //$NON-NLS-1$ //$NON-NLS-2$
				
				for (org.eclipse.jdt.core.dom.Type typeToReplace : mh.typesToReplace) {
					// Skip if already processed
					if (mh.nodesprocessed.contains(typeToReplace)) {
						continue;
					}
					mh.nodesprocessed.add(typeToReplace);
					
					logDebug("Replacing SubProgressMonitor type at position " + typeToReplace.getStartPosition()); //$NON-NLS-1$
					
					// Create replacement type: IProgressMonitor
					org.eclipse.jdt.core.dom.Type newType = ast.newSimpleType(
						addImport(IProgressMonitor.class.getCanonicalName(), cuRewrite, ast));
					
					// Replace the type in AST
					rewrite.replace(typeToReplace, newType, group);
				}
			}
		}
		
		// Only remove SubProgressMonitor import if no unmapped flags may still reference it
		if (!hasUnmappedFlags) {
			importRemover.removeImport("org.eclipse.core.runtime.SubProgressMonitor"); //$NON-NLS-1$
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

	/**
	 * Result of mapping SubProgressMonitor flags to SubMonitor flags.
	 */
	private record FlagMappingResult(
		/** The mapped expression, or null if the flag should be dropped */
		Expression mappedExpression,
		/** True if the mapped expression references SubMonitor (needs import) */
		boolean referencesSubMonitor,
		/** True if the flag was passed through unchanged (may still reference SubProgressMonitor) */
		boolean passedThrough
	) {}

	/**
	 * Maps SubProgressMonitor flags to SubMonitor flags.
	 * 
	 * <p>Flag mappings:</p>
	 * <ul>
	 * <li>{@code SubProgressMonitor.SUPPRESS_SUBTASK_LABEL} → {@code SubMonitor.SUPPRESS_SUBTASK}</li>
	 * <li>{@code SubProgressMonitor.PREPEND_MAIN_LABEL_TO_SUBTASK} → removed (no equivalent)</li>
	 * </ul>
	 * 
	 * <p><b>Limitations:</b> This method only handles single flag constants. Combined flag expressions
	 * using bitwise OR (e.g., {@code FLAG1 | FLAG2}) or numeric literals are not mapped and will be
	 * passed through unchanged. When flags are passed through, the SubProgressMonitor import is
	 * preserved to avoid breaking references in the unmapped expression.</p>
	 * 
	 * @param flagExpr the original flag expression from SubProgressMonitor constructor
	 * @param ast the AST to create new nodes
	 * @param cuRewrite the compilation unit rewrite context
	 * @return the flag mapping result containing the mapped expression and metadata
	 */
	private FlagMappingResult mapSubProgressMonitorFlags(Expression flagExpr, AST ast, CompilationUnitRewrite cuRewrite) {
		// Handle field access: SubProgressMonitor.SUPPRESS_SUBTASK_LABEL
		if (flagExpr instanceof QualifiedName) {
			QualifiedName qn = (QualifiedName) flagExpr;
			String fieldName = qn.getName().getIdentifier();
			
			if ("SUPPRESS_SUBTASK_LABEL".equals(fieldName)) { //$NON-NLS-1$
				// Map to SubMonitor.SUPPRESS_SUBTASK
				QualifiedName newFlag = ast.newQualifiedName(
					ast.newSimpleName(SubMonitor.class.getSimpleName()),
					ast.newSimpleName("SUPPRESS_SUBTASK")); //$NON-NLS-1$
				return new FlagMappingResult(newFlag, true, false);
			} else if ("PREPEND_MAIN_LABEL_TO_SUBTASK".equals(fieldName)) { //$NON-NLS-1$
				// Drop this flag - no equivalent in SubMonitor
				return new FlagMappingResult(null, false, false);
			}
		}
		
		// Handle FieldAccess syntax (e.g., expression.FIELD_NAME)
		if (flagExpr instanceof FieldAccess) {
			FieldAccess fa = (FieldAccess) flagExpr;
			String fieldName = fa.getName().getIdentifier();
			
			if ("SUPPRESS_SUBTASK_LABEL".equals(fieldName)) { //$NON-NLS-1$
				// Map to SubMonitor.SUPPRESS_SUBTASK
				FieldAccess newFlag = ast.newFieldAccess();
				newFlag.setExpression(ast.newSimpleName(SubMonitor.class.getSimpleName()));
				newFlag.setName(ast.newSimpleName("SUPPRESS_SUBTASK")); //$NON-NLS-1$
				return new FlagMappingResult(newFlag, true, false);
			} else if ("PREPEND_MAIN_LABEL_TO_SUBTASK".equals(fieldName)) { //$NON-NLS-1$
				// Drop this flag - no equivalent in SubMonitor
				return new FlagMappingResult(null, false, false);
			}
		}
		
		// For numeric literals, pass through unchanged but don't mark as passedThrough
		// since they don't reference SubProgressMonitor (safe to remove import)
		if (flagExpr instanceof NumberLiteral) {
			Expression passedExpr = ASTNodes.createMoveTarget(cuRewrite.getASTRewrite(), ASTNodes.getUnparenthesedExpression(flagExpr));
			return new FlagMappingResult(passedExpr, false, false);
		}
		
		// For other expressions (variables, bitwise OR), pass through unchanged
		// Mark as passedThrough so SubProgressMonitor import is preserved
		Expression passedExpr = ASTNodes.createMoveTarget(cuRewrite.getASTRewrite(), ASTNodes.getUnparenthesedExpression(flagExpr));
		return new FlagMappingResult(passedExpr, false, true);
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
