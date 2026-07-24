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
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.Type;
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
import org.sandbox.functional.core.transformer.LoopModelTransformer;
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
 * only effectively-final locals. Fresh collection accumulators are replaced only
 * when their constructor and assignment compatibility are modeled explicitly;
 * their runtime implementation is preserved with
 * {@code Collectors.toCollection(...)}.</p>
 */
public class SafeEnhancedForHandler extends EnhancedForHandler {

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
		if (extracted != null
				&& extracted.model.getTerminal() instanceof CollectTerminal terminal
				&& terminal.hasCollectionFactory()) {
			rewriteFreshCollect(visited, extracted, cuRewrite, group);
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
		if (isExistingArrayCollect(loop, extracted) && !hasCompatibleArrayStreamElementType(loop)) {
			return false;
		}
		if (model.getTerminal() instanceof CollectTerminal collectTerminal) {
			VariableDeclarationStatement accumulator= findFreshAccumulator(loop, collectTerminal.targetVariable());
			if (accumulator != null) {
				if (!collectTerminal.targetVariable().equals(
						CollectPatternDetector.isEmptyCollectionDeclaration(accumulator))) {
					return false;
				}
				CollectTerminal preserved= ConcreteCollectionFactory.preserveFactory(accumulator, collectTerminal);
				if (preserved == null) {
					return false;
				}
				model.setTerminal(preserved);
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

	private boolean hasCompatibleArrayStreamElementType(EnhancedForStatement loop) {
		ITypeBinding arrayType= loop.getExpression().resolveTypeBinding();
		IVariableBinding parameterBinding= loop.getParameter().resolveBinding();
		if (arrayType == null || !arrayType.isArray() || parameterBinding == null) {
			return false;
		}
		ITypeBinding componentType= arrayType.getComponentType();
		ITypeBinding parameterType= parameterBinding.getType();
		if (componentType == null || parameterType == null || !componentType.isEqualTo(parameterType)) {
			return false;
		}
		if (!componentType.isPrimitive()) {
			return true;
		}
		return switch (componentType.getName()) {
			case "int", "long", "double" -> true; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			default -> false;
		};
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
		if (originalBody instanceof Block block) {
			ExpressionStatement expressionStatement= singleExpressionWithoutComments(block, cuRewrite.getRoot());
			if (expressionStatement != null) {
				lambda.setBody(rewrite.createCopyTarget(expressionStatement.getExpression()));
			} else {
				lambda.setBody(rewrite.createCopyTarget(block));
			}
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

	private void rewriteFreshCollect(EnhancedForStatement loop, JdtLoopExtractor.ExtractedLoop extracted,
			CompilationUnitRewrite cuRewrite, TextEditGroup group) {
		LoopModel model= extracted.model;
		CollectTerminal terminal= (CollectTerminal) model.getTerminal();
		VariableDeclarationStatement accumulator= findFreshAccumulator(loop, terminal.targetVariable());
		if (accumulator == null || !terminal.targetVariable().equals(
				CollectPatternDetector.isEmptyCollectionDeclaration(accumulator))) {
			return;
		}
		CollectTerminal preserved= ConcreteCollectionFactory.preserveFactory(accumulator, terminal);
		if (preserved == null) {
			return;
		}
		model.setTerminal(preserved);

		AST ast= cuRewrite.getRoot().getAST();
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		ConcreteCollectionASTStreamRenderer renderer= new ConcreteCollectionASTStreamRenderer(
				ast, rewrite, cuRewrite.getRoot(), extracted.originalBody);
		Expression streamExpression= new LoopModelTransformer<>(renderer).transform(model);
		if (streamExpression == null) {
			return;
		}

		VariableDeclarationStatement replacement= createMergedDeclaration(ast, accumulator, streamExpression);
		rewrite.remove(accumulator, group);
		rewrite.replace(loop, replacement, group);
		addRequiredImports(cuRewrite, model);
	}

	@SuppressWarnings("unchecked")
	private VariableDeclarationStatement createMergedDeclaration(AST ast,
			VariableDeclarationStatement original, Expression initializer) {
		VariableDeclarationFragment originalFragment=
				(VariableDeclarationFragment) original.fragments().get(0);
		VariableDeclarationFragment fragment= ast.newVariableDeclarationFragment();
		fragment.setName(ast.newSimpleName(originalFragment.getName().getIdentifier()));
		fragment.setInitializer((Expression) ASTNode.copySubtree(ast, initializer));
		VariableDeclarationStatement declaration= ast.newVariableDeclarationStatement(fragment);
		declaration.setType((Type) ASTNode.copySubtree(ast, original.getType()));
		declaration.modifiers().addAll(ASTNode.copySubtrees(ast, original.modifiers()));
		return declaration;
	}

	private void addRequiredImports(CompilationUnitRewrite cuRewrite, LoopModel model) {
		switch (model.getSource().type()) {
			case ARRAY:
				cuRewrite.getImportRewrite().addImport("java.util.Arrays"); //$NON-NLS-1$
				break;
			case ITERABLE:
				cuRewrite.getImportRewrite().addImport("java.util.stream.StreamSupport"); //$NON-NLS-1$
				break;
			default:
				break;
		}
		cuRewrite.getImportRewrite().addImport("java.util.stream.Collectors"); //$NON-NLS-1$
	}

	@SuppressWarnings("unchecked")
	private ExpressionStatement singleExpressionWithoutComments(Block block, CompilationUnit compilationUnit) {
		List<Statement> statements= block.statements();
		if (statements.size() != 1 || !(statements.get(0) instanceof ExpressionStatement expressionStatement)) {
			return null;
		}
		int blockStart= block.getStartPosition();
		int blockEnd= blockStart + block.getLength();
		List<Comment> comments= compilationUnit.getCommentList();
		for (Comment comment : comments) {
			int commentStart= comment.getStartPosition();
			if (commentStart > blockStart && commentStart < blockEnd) {
				return null;
			}
		}
		return expressionStatement;
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
				|| declaration.fragments().size() != 1) {
			return null;
		}
		VariableDeclarationFragment fragment=
				(VariableDeclarationFragment) declaration.fragments().get(0);
		return targetVariable.equals(fragment.getName().getIdentifier()) ? declaration : null;
	}

	private boolean isConvertible(LoopModel model) {
		if (model == null) {
			return false;
		}
		LoopMetadata metadata= model.getMetadata();
		return metadata == null || !metadata.hasBreak() && !metadata.hasContinue();
	}
}
