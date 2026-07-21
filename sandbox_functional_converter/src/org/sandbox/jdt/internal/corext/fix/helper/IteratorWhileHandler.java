/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
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

import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import org.eclipse.jdt.internal.corext.dom.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;

import org.sandbox.functional.core.model.LoopModel;
import org.sandbox.functional.core.model.SourceDescriptor;
import org.sandbox.functional.core.terminal.CollectTerminal;
import org.sandbox.functional.core.terminal.ReduceTerminal;
import org.sandbox.functional.core.terminal.TerminalOperation;
import org.sandbox.functional.core.transformer.LoopModelTransformer;
import org.sandbox.jdt.internal.common.ASTProcessor;
import org.sandbox.jdt.internal.common.HelperVisitorProvider;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.helper.IteratorLoopBodyParser.ParsedBody;
import org.sandbox.jdt.internal.corext.fix.helper.IteratorPatternDetector.IteratorPattern;

/** Converts safe iterator while/for loops through the shared ULR pipeline. */
public class IteratorWhileHandler extends ASTProcessor {

	private final IteratorPatternDetector patternDetector= new IteratorPatternDetector();
	private final IteratorLoopAnalyzer loopAnalyzer= new IteratorLoopAnalyzer();
	private final IteratorLoopBodyParser bodyParser= new IteratorLoopBodyParser();
	private final JdtLoopExtractor loopExtractor= new JdtLoopExtractor();

	private record IteratorConversion(IteratorPattern pattern, ParsedBody parsedBody,
			JdtLoopExtractor.ExtractedLoop extractedLoop) {
	}

	public IteratorWhileHandler() {
		this(Map.of());
	}

	public IteratorWhileHandler(Map<String, String> options) {
		super(options);
	}

	@Override
	public void find(CompilationUnit compilationUnit, java.util.Set<ASTNode> nodesProcessed,
			ReferenceHolder<ASTNode, Object> data) {
		HelperVisitorProvider.getInstance().forWhileStatement()
				.in(compilationUnit)
				.excluding(nodesProcessed)
				.process(node -> {
					Statement previousStatement= findPreviousStatement(node);
					IteratorPattern pattern= patternDetector.detectWhilePattern(node, previousStatement);
					IteratorConversion conversion= analyzeAndCreateConversion(pattern, compilationUnit);
					if (conversion != null) {
						data.put(node, conversion);
						nodesProcessed.add(node);
						if (previousStatement != null) {
							nodesProcessed.add(previousStatement);
						}
					}
				});

		HelperVisitorProvider.getInstance().forForStatement()
				.in(compilationUnit)
				.excluding(nodesProcessed)
				.process(node -> {
					IteratorPattern pattern= patternDetector.detectForLoopPattern(node);
					IteratorConversion conversion= analyzeAndCreateConversion(pattern, compilationUnit);
					if (conversion != null) {
						data.put(node, conversion);
						nodesProcessed.add(node);
					}
				});
	}

	private IteratorConversion analyzeAndCreateConversion(IteratorPattern pattern, CompilationUnit compilationUnit) {
		if (pattern == null) {
			return null;
		}
		IteratorLoopAnalyzer.SafetyAnalysis safety=
				loopAnalyzer.analyze(pattern.loopBody(), pattern.iteratorVariableName());
		if (!safety.isSafe()) {
			return null;
		}
		ParsedBody parsedBody= bodyParser.parse(pattern.loopBody(), pattern.iteratorVariableName());
		if (parsedBody == null || parsedBody.actualBodyStatements().isEmpty()) {
			return null;
		}
		JdtLoopExtractor.ExtractedLoop extracted= loopExtractor.extractIterator(pattern, parsedBody, compilationUnit);
		if (extracted.model == null || extracted.model.getTerminal() == null) {
			return null;
		}
		return new IteratorConversion(pattern, parsedBody, extracted);
	}

	private Statement findPreviousStatement(Statement statement) {
		if (statement.getParent() instanceof Block block) {
			return IteratorPatternDetector.findPreviousStatement(block, statement);
		}
		return null;
	}

	@Override
	public boolean rewrite(CompilationUnitRewrite cuRewrite, TextEditGroup group, LinkedProposalModelCore linkedModel,
			ASTNode visited, ReferenceHolder<ASTNode, Object> data) throws CoreException {
		Object stored= data.get(visited);
		if (!(stored instanceof IteratorConversion conversion)) {
			return false;
		}

		LoopModel model= conversion.extractedLoop().model;
		TerminalOperation terminal= model.getTerminal();
		VariableDeclarationStatement accumulatorDeclaration= null;
		if (terminal instanceof CollectTerminal collectTerminal) {
			accumulatorDeclaration= findAccumulatorDeclaration(visited, collectTerminal.targetVariable());
			if (accumulatorDeclaration == null
					|| !collectTerminal.targetVariable().equals(
							CollectPatternDetector.isEmptyCollectionDeclaration(accumulatorDeclaration))) {
				return false;
			}
		} else if (terminal instanceof ReduceTerminal reduceTerminal) {
			accumulatorDeclaration= findAccumulatorDeclaration(visited, reduceTerminal.targetVariable());
			VariableDeclarationFragment fragment= singleFragment(accumulatorDeclaration, reduceTerminal.targetVariable());
			if (fragment == null || fragment.getInitializer() == null) {
				return false;
			}
			model.setTerminal(new ReduceTerminal(fragment.getInitializer().toString(), reduceTerminal.accumulator(),
					reduceTerminal.combiner(), reduceTerminal.reduceType(), reduceTerminal.targetVariable()));
		}

		AST ast= cuRewrite.getRoot().getAST();
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		ASTStreamRenderer renderer= new ASTStreamRenderer(ast, rewrite, cuRewrite.getRoot(),
				conversion.extractedLoop().originalBody);
		Expression streamExpression= new LoopModelTransformer<>(renderer).transform(model);
		if (streamExpression == null) {
			return false;
		}

		Statement replacement;
		if (accumulatorDeclaration != null) {
			replacement= createMergedDeclaration(ast, accumulatorDeclaration, streamExpression);
			rewrite.remove(accumulatorDeclaration, group);
		} else {
			replacement= ast.newExpressionStatement(streamExpression);
		}

		rewrite.replace(visited, replacement, group);
		if (visited instanceof WhileStatement) {
			Statement iteratorDeclaration= findPreviousStatement((Statement) visited);
			if (iteratorDeclaration != null) {
				rewrite.remove(iteratorDeclaration, group);
			}
		}
		addRequiredImports(cuRewrite, model);
		return true;
	}

	private VariableDeclarationStatement findAccumulatorDeclaration(ASTNode loop, String targetVariable) {
		if (targetVariable == null || !(loop instanceof Statement statement)
				|| !(statement.getParent() instanceof Block block)) {
			return null;
		}
		@SuppressWarnings("unchecked") //$NON-NLS-1$
		List<Statement> statements= block.statements();
		int loopIndex= statements.indexOf(statement);
		int candidateIndex= loopIndex - (statement instanceof WhileStatement ? 2 : 1);
		if (candidateIndex < 0 || candidateIndex >= statements.size()) {
			return null;
		}
		Statement candidate= statements.get(candidateIndex);
		if (!(candidate instanceof VariableDeclarationStatement declaration)) {
			return null;
		}
		return singleFragment(declaration, targetVariable) == null ? null : declaration;
	}

	private VariableDeclarationFragment singleFragment(VariableDeclarationStatement declaration,
			String targetVariable) {
		if (declaration == null || declaration.fragments().size() != 1) {
			return null;
		}
		VariableDeclarationFragment fragment=
				(VariableDeclarationFragment) declaration.fragments().get(0);
		return fragment.getName().getIdentifier().equals(targetVariable) ? fragment : null;
	}

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
		if (model.getSource().type() == SourceDescriptor.SourceType.ITERABLE) {
			cuRewrite.getImportRewrite().addImport("java.util.stream.StreamSupport"); //$NON-NLS-1$
		}
		if (model.getTerminal() instanceof CollectTerminal) {
			cuRewrite.getImportRewrite().addImport("java.util.stream.Collectors"); //$NON-NLS-1$
		}
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return "items.stream().forEach(item -> System.out.println(item));\n"; //$NON-NLS-1$
		}
		return "Iterator<String> it = items.iterator();\n" //$NON-NLS-1$
				+ "while (it.hasNext()) {\n" //$NON-NLS-1$
				+ "    String item = it.next();\n" //$NON-NLS-1$
				+ "    System.out.println(item);\n" //$NON-NLS-1$
				+ "}\n"; //$NON-NLS-1$
	}
}
