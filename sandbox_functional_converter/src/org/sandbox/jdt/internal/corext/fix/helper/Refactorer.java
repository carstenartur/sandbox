/*******************************************************************************
 * Copyright (c) 2021 Alexandru Gyori and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Alexandru Gyori original code
 *     Carsten Hammer initial port to Eclipse
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.sandbox.jdt.internal.corext.fix.LibStandardNames.METHOD_FOREACH;

//import com.sun.source.tree.BlockTree;
//import com.sun.source.tree.EnhancedForLoopTree;
//import com.sun.source.tree.ExpressionTree;
//import com.sun.source.tree.IfTree;
//import com.sun.source.tree.LiteralTree;
//import com.sun.source.tree.MethodInvocationTree;
//import com.sun.source.tree.ReturnTree;
//import com.sun.source.tree.StatementTree;
//import com.sun.source.tree.Tree;
//import com.sun.source.tree.VariableTree;
//import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression.Operator;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
//import javax.lang.model.element.TypeElement;
//import javax.lang.model.type.TypeMirror;
//import javax.lang.model.util.Types;
//import org.netbeans.api.java.source.TreeMaker;
//import org.netbeans.api.java.source.WorkingCopy;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;

public class Refactorer {
	List<ProspectiveOperation> prospectives;
	private final EnhancedForStatement loop;
	private boolean untransformable;
	private boolean hasIterable;
	AST workingCopy;
	private PreconditionsChecker preconditionsChecker;
	ASTRewrite rewrite;

	public Refactorer(EnhancedForStatement loop, AST ast, PreconditionsChecker pc, ASTRewrite rewrite) {
		this.loop=loop;
		this.workingCopy=ast;
		this.preconditionsChecker=pc;
		this.rewrite=rewrite;
	}

	public boolean isRefactorable() {
		prospectives = this.getListRepresentation(loop.getBody(), true);
		if ((prospectives == null) || prospectives.isEmpty()) {
			return false;
		}
		prospectives.get(prospectives.size() - 1).eagerize();
		if (this.untransformable) {
			return false;
		}
		for ( int i = 0; i < prospectives.size() - 1; i++) {
			if (!prospectives.get(i).isLazy()) {
				return false;
			}
		}
		hasIterable = false;
		//            Expression var = loop.getExpression();
		//            TypeElement el = workingCopy.getElements().getTypeElement("java.lang.Iterable"); // NOI18N
		//            if (el != null) {
		//                TreePath path = TreePath.getPath(workingCopy.getCompilationUnit(), loop.getExpression());
		//                TypeMirror m = workingCopy.getTrees().getTypeMirror(path);
		//                Types types  = workingCopy.getTypes();
		//                hasIterable =
		//                        types.isSubtype(
		//                            types.erasure(m),
		//                            types.erasure(el.asType())
		//                        );
		//            }
		prospectives = ProspectiveOperation.mergeIntoComposableOperations(prospectives);
		return prospectives != null;
	}

	private List<ProspectiveOperation> getListRepresentation(Statement tree, boolean last) {
		List<ProspectiveOperation> ls = new ArrayList<>();
		switch (tree.getNodeType()) {
		case ASTNode.BLOCK:
			ls.addAll(getBlockListRepresentation(tree, last));
			break;
		case ASTNode.IF_STATEMENT:
			ls.addAll(getIfListRepresentation(tree, last));
			break;
		default:
			ls.addAll(getSingleStatementListRepresentation(tree));
			break;
		}

		return ls;
	}

	private List<ProspectiveOperation> getSingleStatementListRepresentation( Statement tree) {
		List<ProspectiveOperation> ls = new ArrayList<>();
		if (this.preconditionsChecker.isReducer() && this.preconditionsChecker.getReducer().equals(tree)) {
			ls.addAll(ProspectiveOperation.createOperator(tree, ProspectiveOperation.OperationType.REDUCE, this.preconditionsChecker, this.workingCopy,rewrite));
		} else {
			ls.addAll(ProspectiveOperation.createOperator(tree, ProspectiveOperation.OperationType.MAP, this.preconditionsChecker, this.workingCopy,rewrite));
		}
		return ls;
	}

	private List<ProspectiveOperation> getIfListRepresentation( Statement tree, boolean last) {
		IfStatement ifTree = (IfStatement) tree;
		List<ProspectiveOperation> ls = new ArrayList<>();
		if (ifTree.getElseStatement() == null) {

			Statement then = ifTree.getThenStatement();
			if (isOneStatementBlock(then)) {
				then = (Statement) ((Block) then).statements().get(0);
			}
			if (then.getNodeType() == ASTNode.RETURN_STATEMENT) {
				ReturnStatement returnTree = (ReturnStatement) then;
				Expression returnExpression = returnTree.getExpression();
				if (returnExpression.getNodeType() == ASTNode.BOOLEAN_LITERAL && ((BooleanLiteral) returnExpression).booleanValue()) {
					ls.addAll(ProspectiveOperation.createOperator(ifTree, ProspectiveOperation.OperationType.ANYMATCH, this.preconditionsChecker, this.workingCopy,rewrite));
				} else if (returnExpression.getNodeType() == ASTNode.BOOLEAN_LITERAL && !((BooleanLiteral) returnExpression).booleanValue()) {
					ls.addAll(ProspectiveOperation.createOperator(ifTree, ProspectiveOperation.OperationType.NONEMATCH, this.preconditionsChecker, this.workingCopy,rewrite));
				}
			} else {
				ls.addAll(ProspectiveOperation.createOperator(ifTree, ProspectiveOperation.OperationType.FILTER, this.preconditionsChecker, this.workingCopy,rewrite));
				ls.addAll(getListRepresentation(ifTree.getThenStatement(), last));
			}
		} else {

			ls.addAll(ProspectiveOperation.createOperator(ifTree, ProspectiveOperation.OperationType.MAP, this.preconditionsChecker, this.workingCopy,rewrite));
		}
		return ls;
	}

	private boolean isOneStatementBlock( Statement then) {
		return then.getNodeType() == ASTNode.BLOCK && ((Block) then).statements().size() == 1;
	}

	private List<ProspectiveOperation> getBlockListRepresentation( Statement tree, boolean last) {
		List<ProspectiveOperation> ls = new ArrayList<>();
		Block blockTree = (Block) tree;
		List<? extends Statement> statements = blockTree.statements();
		for ( int i = 0; i < statements.size(); i++) {
			Statement statement = statements.get(i);
			boolean l = last &&  i == statements.size() - 1;
			if (statement.getNodeType() == ASTNode.IF_STATEMENT) {
				IfStatement ifTree = (IfStatement) statement;
				if (isIfWithContinue(ifTree)) {
					ifTree = refactorContinuingIf(ifTree, statements.subList(i + 1, statements.size()));
					// the if was refactored, so that all the statements are nested in it, so it became
					// the last (and single) statement within the parent
					ls.addAll(this.getListRepresentation(ifTree, last));
					break;
				}
				if (l) {
					ls.addAll(this.getListRepresentation(ifTree, true));
				} else {
					if (this.isReturningIf(ifTree)) {
						this.untransformable = true;
					}
					ls.addAll(ProspectiveOperation.createOperator(ifTree, ProspectiveOperation.OperationType.MAP, preconditionsChecker, workingCopy,rewrite));
				}
			} else {
				ls.addAll(getListRepresentation(statement, l));
			}
		}
		return ls;
	}

	private boolean isReturningIf( IfStatement ifTree) {
		Statement then = ifTree.getThenStatement();
		if (then.getNodeType() == ASTNode.RETURN_STATEMENT) {
			return true;
		}
		if (then.getNodeType() == ASTNode.BLOCK) {
			Block block = (Block) then;
			if (block.statements().size() == 1 && ((List<? extends Statement>)block.statements()).get(0).getNodeType() == ASTNode.RETURN_STATEMENT) {
				return true;
			}
		}
		return false;
	}

	private IfStatement refactorContinuingIf( IfStatement ifTree,  List<? extends Statement> newStatements) {
		PrefixExpression  newPredicate = workingCopy.newPrefixExpression();
		newPredicate.setOperator(Operator.COMPLEMENT);
		newPredicate.setOperand(ifTree.getExpression());
		Block newThen = workingCopy.newBlock();
		newThen.statements().add(newStatements);
		IfStatement newIfStatement = workingCopy.newIfStatement();
		newIfStatement.setExpression(newPredicate);
		newIfStatement.setThenStatement(newThen);
		return newIfStatement;
	}

	private Boolean isIfWithContinue( IfStatement ifTree) {
		Statement then = ifTree.getThenStatement();
		if (then.getNodeType() == ASTNode.CONTINUE_STATEMENT) {
			return true;
		}
		if (then.getNodeType() == ASTNode.BLOCK) {
			List<? extends Statement> statements = ((Block) then).statements();
			if (statements.size() == 1 && statements.get(0).getNodeType() == ASTNode.CONTINUE_STATEMENT) {
				return true;
			}

		}
		return false;
	}

	public MethodInvocation refactor(ASTRewrite rewrite) {
		/**
		 * for (Integer l : ls){
		 * 		  System.out.println(l);
		 * 		}
		 *
		 * loopBody= {  System.out.println(l);	}
		 *
		 * parameter= Integer l
		 *
		 * expr= ls
		 *
		 */
		ExpressionStatement loopBody = (ExpressionStatement) loop.getBody();
		SingleVariableDeclaration parameter = loop.getParameter();
		Expression expr = loop.getExpression();

		MethodInvocation forEach = chainAllProspectives(workingCopy, expr, rewrite);

		ProspectiveOperation lastOperation = prospectives.get(prospectives.size() - 1);
		Statement returnValue = propagateSideEffects(lastOperation, forEach, null);

		return forEach;
	}

	private Statement propagateSideEffects(ProspectiveOperation lastOperation, MethodInvocation forEach,
			Object object) {
		// TODO Auto-generated method stub
		return null;
	}

	private MethodInvocation chainAllProspectives(AST ast, Expression expr,ASTRewrite rewrite) {

		SingleVariableDeclaration parameter = loop.getParameter();
		ExpressionStatement loopBody = (ExpressionStatement) loop.getBody();

		// Special case: if the only operation is forEach{Ordered},
		if (hasIterable && prospectives.size() == 1 && prospectives.get(0).isForeach()) {
			ProspectiveOperation prospective = prospectives.get(0);
			MethodInvocation singleforeach= ast.newMethodInvocation();
			singleforeach.setName(ast.newSimpleName(METHOD_FOREACH));
			singleforeach.setExpression(ASTNodeFactory.newName(ast, expr.toString()));
			singleforeach.arguments().add(prospective.getArguments());

			return singleforeach;
		}

		MethodInvocation forEach = ast.newMethodInvocation();
		forEach.setExpression(ASTNodeFactory.newName(ast, expr.toString()));
		forEach.setName(ast.newSimpleName(METHOD_FOREACH));

		LambdaExpression lambdaExpression= ast.newLambdaExpression();
		List<VariableDeclaration> lambdaParameters= lambdaExpression.parameters();
		lambdaExpression.setParentheses(false);
		VariableDeclarationFragment lambdaParameter= ast.newVariableDeclarationFragment();
		lambdaParameter.setName((SimpleName) rewrite.createCopyTarget(parameter.getName()));
		lambdaParameters.add(lambdaParameter);
		Expression createMoveTarget = ASTNodes.createMoveTarget(rewrite, loopBody.getExpression());
		lambdaExpression.setBody(createMoveTarget);
		forEach.arguments().add(lambdaExpression);
		return forEach;
	}
}
