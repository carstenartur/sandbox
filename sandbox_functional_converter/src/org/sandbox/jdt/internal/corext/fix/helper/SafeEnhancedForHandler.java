/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;

import org.sandbox.functional.core.model.LoopMetadata;
import org.sandbox.functional.core.model.LoopModel;
import org.sandbox.functional.core.model.SourceDescriptor;
import org.sandbox.functional.core.terminal.CollectTerminal;
import org.sandbox.functional.core.terminal.ReduceTerminal;
import org.sandbox.functional.core.tree.ConversionDecision;
import org.sandbox.functional.core.tree.LoopKind;
import org.sandbox.functional.core.tree.LoopTree;
import org.sandbox.functional.core.tree.LoopTreeNode;
import org.sandbox.jdt.internal.common.HelperVisitorFactory;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore;

/**
 * Enhanced-for handler that rejects unsafe conversions before a rewrite operation
 * is scheduled.
 *
 * <p>Array collect into an existing target is rendered as a sequential
 * {@code Arrays.stream(...).forEachOrdered(...)} operation that copies the
 * original loop body through {@link ASTRewrite}. Generated lambdas may capture
 * only effectively-final locals. A fresh collect accumulator is replaced only
 * when the collector result is assignable to its declared interface type;
 * concrete collection implementations remain unchanged until an explicit
 * {@code Collectors.toCollection(...)} model exists.</p>
 */
public class SafeEnhancedForHandler extends EnhancedForHandler {

	private static final String JAVA_UTIL_LIST= "java.util.List"; //$NON-NLS-1$
	private static final String JAVA_UTIL_SET= "java.util.Set"; //$NON-NLS-1$

	private final JdtLoopExtractor extractor= new JdtLoopExtractor();

	@Override
	public void find(UseFunctionalCallFixCore fixCore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesProcessed) {
		ReferenceHolder<String, Object> treeHolder= ReferenceHolder.create();
		LoopTree tree= new LoopTree();
		treeHolder.put("tree", tree); //$NON-NLS-1$
		ReferenceHolder<ASTNode, Object> dataHolder= ReferenceHolder.create();

		HelperVisitorFactory.callEnhancedForStatementVisitor(compilationUnit, dataHolder, nodesProcessed,
				(visited, holder) -> visitLoop(visited, treeHolder, nodesProcessed),
				(visited, holder) -> endVisitLoop(visited, treeHolder, compilationUnit));

		for (LoopTreeNode node : tree.getConvertibleNodes()) {
			EnhancedForStatement loop= (EnhancedForStatement) node.getAstNodeReference();
			if (loop == null || nodesProcessed.contains(loop)) {
				continue;
			}
			JdtLoopExtractor.ExtractedLoop extracted= (JdtLoopExtractor.ExtractedLoop) treeHolder
					.get("extracted_" + System.identityHashCode(loop)); //$NON-NLS-1$
			if (extracted == null) {
				extracted= extractor.extract(loop);
			}
			if (!isSafeToSchedule(loop, extracted)) {
				continue;
			}
			dataHolder.put(loop, extracted);
			operations.add(fixCore.rewrite(loop, dataHolder));
			nodesProcessed.add(loop);
		}
	}

	@Override
	public void rewrite(UseFunctionalCallFixCore fixCore, EnhancedForStatement visited,
			CompilationUnitRewrite cuRewrite, TextEditGroup group,
			ReferenceHolder<ASTNode, Object> data) throws CoreException {
		JdtLoopExtractor.ExtractedLoop extracted= (JdtLoopExtractor.ExtractedLoop) data.get(visited);
		if (isExistingArrayCollect(visited, extracted)) {
			rewriteExistingArrayCollect(visited, cuRewrite, group);
			return;
		}
		super.rewrite(fixCore, visited, cuRewrite, group, data);
	}

	private boolean visitLoop(EnhancedForStatement loop, ReferenceHolder<String, Object> treeHolder,
			Set<ASTNode> nodesProcessed) {
		if (nodesProcessed.contains(loop)) {
			return false;
		}
		LoopTree tree= (LoopTree) treeHolder.get("tree"); //$NON-NLS-1$
		if (tree == null) {
			return false;
		}
		LoopTreeNode node= tree.pushLoop(LoopKind.ENHANCED_FOR);
		node.setAstNodeReference(loop);
		LoopBodyScopeScanner scanner= new LoopBodyScopeScanner(loop);
		scanner.scan();
		scanner.populateScopeInfo(node.getScopeInfo());
		treeHolder.put("scanner_" + System.identityHashCode(loop), scanner); //$NON-NLS-1$
		return true;
	}

	private void endVisitLoop(EnhancedForStatement loop, ReferenceHolder<String, Object> treeHolder,
			CompilationUnit compilationUnit) {
		LoopTree tree= (LoopTree) treeHolder.get("tree"); //$NON-NLS-1$
		if (tree == null || !tree.isInsideLoop()) {
			return;
		}
		LoopTreeNode current= tree.current();
		if (current == null || current.getAstNodeReference() != loop) {
			return;
		}
		LoopTreeNode node= tree.popLoop();
		if (node.hasConvertibleDescendant()) {
			node.setDecision(ConversionDecision.SKIPPED_INNER_CONVERTED);
			return;
		}

		LoopBodyScopeScanner scanner= (LoopBodyScopeScanner) treeHolder
				.get("scanner_" + System.identityHashCode(loop)); //$NON-NLS-1$
		if (scanner != null && node.getParent() != null) {
			LoopTreeNode parent= node.getParent();
			while (parent != null) {
				for (String referencedVariable : scanner.getReferencedVariables()) {
					if (parent.getScopeInfo().getModifiedVariables().contains(referencedVariable)) {
						node.setDecision(ConversionDecision.NOT_CONVERTIBLE);
						return;
					}
				}
				parent= parent.getParent();
			}
		}

		JdtLoopExtractor.ExtractedLoop extracted= extractor.extract(loop, compilationUnit);
		if (!isConvertible(extracted.model) || extracted.model.getTerminal() == null) {
			node.setDecision(ConversionDecision.NOT_CONVERTIBLE);
			return;
		}
		treeHolder.put("extracted_" + System.identityHashCode(loop), extracted); //$NON-NLS-1$
		node.setDecision(ConversionDecision.CONVERTIBLE);
	}

	private boolean isSafeToSchedule(EnhancedForStatement loop, JdtLoopExtractor.ExtractedLoop extracted) {
		if (extracted == null || !isConvertible(extracted.model) || extracted.model.getTerminal() == null) {
			return false;
		}
		LoopModel model= extracted.model;
		if (model.getTerminal() instanceof CollectTerminal collectTerminal) {
			VariableDeclarationStatement accumulator= findFreshAccumulator(loop, collectTerminal.targetVariable());
			if (accumulator != null && !isCollectorResultAssignable(accumulator, collectTerminal)) {
				return false;
			}
		}
		return !LambdaCaptureSafety.hasUnsafeCapture(loop.getBody(), liftedAccumulatorNames(loop, model),
				loop.getParameter().resolveBinding());
	}

	private boolean isExistingArrayCollect(EnhancedForStatement loop,
			JdtLoopExtractor.ExtractedLoop extracted) {
		if (extracted == null || extracted.model == null
				|| extracted.model.getSource() == null
				|| extracted.model.getSource().type() != SourceDescriptor.SourceType.ARRAY
				|| !(extracted.model.getTerminal() instanceof CollectTerminal collectTerminal)) {
			return false;
		}
		return findFreshAccumulator(loop, collectTerminal.targetVariable()) == null;
	}

	@SuppressWarnings("unchecked")
	private void rewriteExistingArrayCollect(EnhancedForStatement loop,
			CompilationUnitRewrite cuRewrite, TextEditGroup group) {
		AST ast= cuRewrite.getRoot().getAST();
		ASTRewrite rewrite= cuRewrite.getASTRewrite();

		MethodInvocation stream= ast.newMethodInvocation();
		String arraysReference= cuRewrite.getImportRewrite().addImport("java.util.Arrays"); //$NON-NLS-1$
		stream.setExpression(ast.newName(arraysReference));
		stream.setName(ast.newSimpleName("stream")); //$NON-NLS-1$
		stream.arguments().add(ASTNode.copySubtree(ast, loop.getExpression()));

		MethodInvocation forEach= ast.newMethodInvocation();
		forEach.setExpression(stream);
		forEach.setName(ast.newSimpleName("forEachOrdered")); //$NON-NLS-1$

		LambdaExpression lambda= ast.newLambdaExpression();
		VariableDeclarationFragment parameter= ast.newVariableDeclarationFragment();
		parameter.setName(ast.newSimpleName(loop.getParameter().getName().getIdentifier()));
		lambda.parameters().add(parameter);
		lambda.setParentheses(false);

		Statement originalBody= loop.getBody();
		if (originalBody instanceof Block) {
			lambda.setBody(rewrite.createCopyTarget(originalBody));
		} else if (originalBody instanceof ExpressionStatement expressionStatement) {
			lambda.setBody(rewrite.createCopyTarget(expressionStatement.getExpression()));
		} else {
			Block lambdaBody= ast.newBlock();
			lambdaBody.statements().add(rewrite.createCopyTarget(originalBody));
			lambda.setBody(lambdaBody);
		}

		forEach.arguments().add(lambda);
		rewrite.replace(loop, ast.newExpressionStatement(forEach), group);
	}

	private Set<String> liftedAccumulatorNames(EnhancedForStatement loop, LoopModel model) {
		String targetVariable= null;
		if (model.getTerminal() instanceof ReduceTerminal reduceTerminal) {
			targetVariable= reduceTerminal.targetVariable();
		} else if (model.getTerminal() instanceof CollectTerminal collectTerminal
				&& findFreshAccumulator(loop, collectTerminal.targetVariable()) != null) {
			targetVariable= collectTerminal.targetVariable();
		}
		if (targetVariable == null || targetVariable.indexOf('.') >= 0) {
			return Set.of();
		}
		return Set.of(targetVariable);
	}

	private VariableDeclarationStatement findFreshAccumulator(EnhancedForStatement loop, String targetVariable) {
		if (targetVariable == null || !(loop.getParent() instanceof Block block)) {
			return null;
		}
		@SuppressWarnings("unchecked") //$NON-NLS-1$
		List<Statement> statements= block.statements();
		int loopIndex= statements.indexOf(loop);
		if (loopIndex <= 0) {
			return null;
		}
		Statement previous= statements.get(loopIndex - 1);
		if (!(previous instanceof VariableDeclarationStatement declaration)
				|| !targetVariable.equals(CollectPatternDetector.isEmptyCollectionDeclaration(declaration))) {
			return null;
		}
		return declaration;
	}

	private boolean isCollectorResultAssignable(VariableDeclarationStatement declaration,
			CollectTerminal terminal) {
		ITypeBinding typeBinding= declaration.getType().resolveBinding();
		if (typeBinding == null) {
			return false;
		}
		String qualifiedName= typeBinding.getErasure().getQualifiedName();
		return switch (terminal.collectorType()) {
			case TO_LIST -> JAVA_UTIL_LIST.equals(qualifiedName);
			case TO_SET -> JAVA_UTIL_SET.equals(qualifiedName);
			default -> false;
		};
	}

	private boolean isConvertible(LoopModel model) {
		if (model == null) {
			return false;
		}
		LoopMetadata metadata= model.getMetadata();
		return metadata == null || !metadata.hasBreak() && !metadata.hasContinue();
	}
}
