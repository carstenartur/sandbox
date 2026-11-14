/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.cleanup.multifile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;

import org.sandbox.jdt.container.analysis.ParallelArrayRecordAnalyzer;
import org.sandbox.jdt.container.api.ContainerUsageProfile;
import org.sandbox.jdt.container.api.UsageEvidence.Kind;

/** Strengthens the paired-array report with the control-flow proof required to rewrite reads and writes. */
final class ParallelRecordRewrite extends CompilationUnitRewriteOperationWithSourceRange {
	private static final String DESCRIPTION= "Store private parallel arrays as local records in encounter order"; //$NON-NLS-1$
	private final VariableDeclarationStatement left;
	private final VariableDeclarationStatement right;
	private final Block growth;
	private final List<ArrayAccess> reads;
	private final String rowName;
	private final String listName;

	private ParallelRecordRewrite(VariableDeclarationStatement left, VariableDeclarationStatement right,
			Block growth, List<ArrayAccess> reads, String rowName, String listName) {
		this.left= left;
		this.right= right;
		this.growth= growth;
		this.reads= List.copyOf(reads);
		this.rowName= rowName;
		this.listName= listName;
	}

	static List<ParallelRecordRewrite> find(ICompilationUnit unit, CompilationUnit root) {
		var project= unit.getJavaProject();
		if (project == null || root.getAST().apiLevel() < AST.JLS16
				|| JavaCore.compareJavaVersions(project.getOption(JavaCore.COMPILER_SOURCE, true), JavaCore.VERSION_16) < 0
				|| JavaCore.compareJavaVersions(project.getOption(JavaCore.COMPILER_COMPLIANCE, true), JavaCore.VERSION_16) < 0) {
			return List.of();
		}
		Set<String> names= new HashSet<>();
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName name) {
				names.add(name.getIdentifier());
				return true;
			}
		});
		List<ParallelRecordRewrite> result= new ArrayList<>();
		for (var profile : ParallelArrayRecordAnalyzer.analyze(root, () -> { })) {
			ParallelRecordRewrite plan= plan(root, profile, names);
			if (plan != null) {
				result.add(plan);
			}
		}
		return result;
	}

	private static ParallelRecordRewrite plan(CompilationUnit root, ContainerUsageProfile profile, Set<String> names) {
		List<ArrayAccess> writes= new ArrayList<>();
		List<ArrayAccess> reads= new ArrayList<>();
		for (var evidence : profile.evidence()) {
			if (!(NodeFinder.perform(root, evidence.sourceStart(), evidence.sourceLength()) instanceof ArrayAccess access)) {
				return null;
			}
			(evidence.kind() == Kind.PAIRED_ARRAY_WRITE ? writes : reads).add(access);
		}
		if (writes.size() != 2 || reads.isEmpty()) {
			return null;
		}
		ArrayAccess first= writes.getFirst(), second= writes.getLast();
		var left= declaration(root, first.getArray());
		var right= declaration(root, second.getArray());
		var count= declaration(root, first.getIndex());
		if (left == null || right == null || count == null || !(left.getParent() instanceof Block scope)
				|| !(scope.getParent() instanceof MethodDeclaration) || !(left.getType() instanceof ArrayType leftType)
				|| !(right.getType() instanceof ArrayType rightType) || !simpleComponent(leftType) || !simpleComponent(rightType)
				|| left.getParent() != right.getParent() || left.getParent() != count.getParent()
				|| !literalZero(left) || !literalZero(right)
				|| !(first.getParent().getParent().getParent() instanceof Block growth)
				|| growth.statements().size() != 5 || !(growth.getParent() instanceof EnhancedForStatement loop)
				|| loop.getBody() != growth || loop.getParent() != scope) {
			return null;
		}
		int i= scope.statements().indexOf(left);
		if (i < 0 || i + 3 >= scope.statements().size() || scope.statements().get(i + 1) != right
				|| scope.statements().get(i + 2) != count || scope.statements().get(i + 3) != loop) {
			return null;
		}
		String countKey= ((VariableDeclarationFragment) count.fragments().getFirst()).resolveBinding().getKey();
		for (ArrayAccess read : reads) {
			if (!inBounds(read, scope, i + 3, countKey)) {
				return null;
			}
		}
		String leftName= ((VariableDeclarationFragment) left.fragments().getFirst()).getName().getIdentifier();
		String rightName= ((VariableDeclarationFragment) right.fragments().getFirst()).getName().getIdentifier();
		if (forbiddenComponent(leftName) || forbiddenComponent(rightName) || removesComment(root, growth)
				|| removesComment(root, left) || removesComment(root, right)) {
			return null;
		}
		return new ParallelRecordRewrite(left, right, growth, reads, unique("Row", names), unique("rows", names)); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static VariableDeclarationStatement declaration(CompilationUnit root, Expression expression) {
		return expression instanceof SimpleName name && name.resolveBinding() instanceof IVariableBinding binding
				&& root.findDeclaringNode(binding) instanceof VariableDeclarationFragment fragment
				&& fragment.getParent() instanceof VariableDeclarationStatement statement && statement.fragments().size() == 1
				&& statement.modifiers().isEmpty() && fragment.extraDimensions().isEmpty() ? statement : null;
	}

	/** Constant-valued qualifiers can have side effects; the removed empty-array creation must use a literal. */
	private static boolean literalZero(VariableDeclarationStatement statement) {
		var variable= (VariableDeclarationFragment) statement.fragments().getFirst();
		return variable.getInitializer() instanceof ArrayCreation array && array.dimensions().size() == 1
				&& array.dimensions().getFirst() instanceof NumberLiteral literal
				&& Integer.valueOf(0).equals(literal.resolveConstantExpressionValue());
	}

	private static boolean simpleComponent(ArrayType array) {
		var type= array.resolveBinding();
		return type != null && !type.isRecovered() && type.getDimensions() == 1 && type.getTypeAnnotations().length == 0
				&& type.getElementType().getTypeAnnotations().length == 0 && !type.getElementType().isTypeVariable()
				&& !type.getElementType().isParameterizedType() && !type.getElementType().isRawType() && !type.getElementType().isLocal()
				&& array.getElementType() instanceof SimpleType;
	}

	/** Protect exception class and timing: list.get is used only with a proven in-bounds, monotonic row index. */
	private static boolean inBounds(ArrayAccess read, Block scope, int populatedAt, String countKey) {
		ASTNode node= read;
		while (node != null && !(node instanceof ForStatement)) {
			if (node instanceof TryStatement || node instanceof LambdaExpression || node instanceof AnonymousClassDeclaration) {
				return false;
			}
			node= node.getParent();
		}
		if (!(node instanceof ForStatement loop) || loop.getParent() != scope || scope.statements().indexOf(loop) <= populatedAt
				|| loop.initializers().size() != 1 || !(loop.initializers().getFirst() instanceof VariableDeclarationExpression declaration)
				|| declaration.fragments().size() != 1 || !(declaration.fragments().getFirst() instanceof VariableDeclarationFragment index)
				|| index.getInitializer() == null || !Integer.valueOf(0).equals(index.getInitializer().resolveConstantExpressionValue())
				|| !(loop.getExpression() instanceof InfixExpression condition) || condition.getOperator() != InfixExpression.Operator.LESS
				|| condition.hasExtendedOperands() || !countKey.equals(key(condition.getRightOperand()))
				|| loop.updaters().size() != 1 || !(loop.updaters().getFirst() instanceof PostfixExpression update)
				|| update.getOperator() != PostfixExpression.Operator.INCREMENT || index.resolveBinding() == null
				|| !"int".equals(index.resolveBinding().getType().getName())) { //$NON-NLS-1$
			return false;
		}
		String indexKey= index.resolveBinding().getKey();
		if (!indexKey.equals(key(condition.getLeftOperand())) || !indexKey.equals(key(read.getIndex())) || !indexKey.equals(key(update.getOperand()))) {
			return false;
		}
		Statement body= loop.getBody();
		if (body instanceof Block block && block.statements().size() == 1) {
			body= (Statement) block.statements().getFirst();
		}
		if (!(body instanceof ExpressionStatement statement) || !(statement.getExpression() instanceof MethodInvocation)) {
			return false;
		}
		boolean[] safe= { true };
		body.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName name) {
				if (indexKey.equals(key(name)) && !(name.getParent() instanceof ArrayAccess access && access.getIndex() == name)) {
					safe[0]= false;
				}
				return safe[0];
			}
		});
		return safe[0] && read.getStartPosition() >= body.getStartPosition()
				&& read.getStartPosition() + read.getLength() <= body.getStartPosition() + body.getLength();
	}

	private static String key(Expression expression) {
		return expression instanceof SimpleName name && name.resolveBinding() instanceof IVariableBinding variable
				? variable.getVariableDeclaration().getKey() : ""; //$NON-NLS-1$
	}

	private static String unique(String base, Set<String> names) {
		String name= base;
		for (int suffix= 2; !names.add(name); suffix++) {
			name= base + suffix;
		}
		return name;
	}

	@Override
	public void rewriteASTInternal(CompilationUnitRewrite cu, LinkedProposalModelCore model) {
		AST ast= cu.getRoot().getAST();
		ASTRewrite rewrite= cu.getASTRewrite();
		TextEditGroup group= createTextEditGroup(DESCRIPTION, cu);
		var imports= cu.getImportRewrite();
		var context= new ContextSensitiveImportRewriteContext(left, imports);
		var listType= imports.addImport("java.util.List", context); //$NON-NLS-1$
		var implType= imports.addImport("java.util.ArrayList", context); //$NON-NLS-1$
		var record= ast.newRecordDeclaration();
		record.setName(ast.newSimpleName(rowName));
		List<String> components= List.of(component(ast, record, left), component(ast, record, right));
		var blockRewrite= rewrite.getListRewrite(left.getParent(), Block.STATEMENTS_PROPERTY);
		blockRewrite.insertBefore(ast.newTypeDeclarationStatement(record), left, group);
		var variable= ast.newVariableDeclarationFragment();
		variable.setName(ast.newSimpleName(listName));
		var initializer= ast.newClassInstanceCreation();
		initializer.setType(ast.newParameterizedType(ast.newSimpleType(ast.newName(implType))));
		variable.setInitializer(initializer);
		var rows= ast.newVariableDeclarationStatement(variable);
		var list= ast.newParameterizedType(ast.newSimpleType(ast.newName(listType)));
		list.typeArguments().add(ast.newSimpleType(ast.newSimpleName(rowName)));
		rows.setType(list);
		rewrite.replace(left, rows, group);
		rewrite.remove(right, group);
		var row= ast.newClassInstanceCreation();
		row.setType(ast.newSimpleType(ast.newSimpleName(rowName)));
		for (int i= 2; i < 4; i++) {
			var store= (Assignment) ((ExpressionStatement) growth.statements().get(i)).getExpression();
			row.arguments().add(ASTNode.copySubtree(ast, store.getRightHandSide()));
		}
		var add= ast.newMethodInvocation();
		add.setExpression(ast.newSimpleName(listName));
		add.setName(ast.newSimpleName("add")); //$NON-NLS-1$
		add.arguments().add(row);
		rewrite.replace((ASTNode) growth.statements().getFirst(), ast.newExpressionStatement(add), group);
		for (int i= 1; i < 4; i++) {
			rewrite.remove((ASTNode) growth.statements().get(i), group);
		}
		String leftKey= ((VariableDeclarationFragment) left.fragments().getFirst()).resolveBinding().getVariableDeclaration().getKey();
		for (ArrayAccess read : reads) {
			var get= ast.newMethodInvocation();
			get.setExpression(ast.newSimpleName(listName));
			get.setName(ast.newSimpleName("get")); //$NON-NLS-1$
			get.arguments().add(ASTNode.copySubtree(ast, read.getIndex()));
			var accessor= ast.newMethodInvocation();
			accessor.setExpression(get);
			accessor.setName(ast.newSimpleName(components.get(leftKey.equals(key(read.getArray())) ? 0 : 1)));
			rewrite.replace(read, accessor, group);
		}
	}

	private static String component(AST ast, RecordDeclaration record, VariableDeclarationStatement array) {
		var fragment= (VariableDeclarationFragment) array.fragments().getFirst();
		var declaration= ast.newSingleVariableDeclaration();
		declaration.setType((Type) ASTNode.copySubtree(ast, ((ArrayType) array.getType()).getElementType()));
		String name= fragment.getName().getIdentifier();
		declaration.setName(ast.newSimpleName(name));
		record.recordComponents().add(declaration);
		return name;
	}

	private static boolean removesComment(CompilationUnit root, ASTNode node) {
		int start= root.getExtendedStartPosition(node);
		int end= start + root.getExtendedLength(node);
		for (Object entry : root.getCommentList()) {
			var comment= (ASTNode) entry;
			if (comment.getStartPosition() < end && comment.getStartPosition() + comment.getLength() > start) {
				return true;
			}
		}
		return false;
	}

	private static boolean forbiddenComponent(String name) {
		return Set.of("clone", "finalize", "getClass", "hashCode", "notify", "notifyAll", "toString", "wait").contains(name); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$
	}

	@Override
	public String getAdditionalInfo() {
		return DESCRIPTION;
	}
}
