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

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.InfixExpression.Operator;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MemberRef;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.compiler.ast.CompoundAssignment;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.sandbox.jdt.internal.corext.fix.helper.PreconditionsChecker.VariablesVisitor;
import org.sandbox.jdt.internal.corext.fix.helper.ProspectiveOperation.OperationType;

class ProspectiveOperation {
	
	private static final String UNKNOWN_NAME = "_item";
	
	public static enum OperationType {
		MAP, FOREACH, FILTER, REDUCE, ANYMATCH, NONEMATCH
	}

	private OperationType opType;
	private ASTNode correspondingTree;
	private Collection<?> innerLoopVariables;
	private AST workingCopy;
	private Map<Name, String> varToType;
	private Set<Name> neededVariables;
	private Expression reducingVariable;
	private ASTRewrite treeMaker;
	private Set<Name> availableVariables;

	public ProspectiveOperation(ASTNode tree, OperationType operationType, Set<Name> innerLoopVariables, AST workingCopy, Map<Name, String> varToType, ASTRewrite rewrite) {
		 this.opType = operationType;
	        this.correspondingTree = tree;
	        this.innerLoopVariables = innerLoopVariables;
//	        this.treeMaker = workingCopy.getTreeMaker();
	        this.treeMaker= rewrite;
	        this.workingCopy = workingCopy;
	        this.varToType = varToType;
	}

	public void eagerize() {
		if (this.opType == OperationType.MAP) {
			this.opType = OperationType.FOREACH;
		}
	}

	public Boolean isLazy() {
		return this.opType == OperationType.MAP || this.opType == OperationType.FILTER;
	}

	public boolean isForeach() {
		return opType == OperationType.FOREACH;
	}

	 List<Expression> getArguments() {
		 ASTNode var;
        Expression lambda;
        ASTNode lambdaBody;
        if (this.correspondingTree.getNodeType() == ASTNode.BLOCK) {
            lambdaBody = this.correspondingTree;
        } else {
            switch (this.opType) {
                case FILTER:
                case ANYMATCH:
                case NONEMATCH:
                    lambdaBody = ((IfStatement) this.correspondingTree).getExpression();
                    break;
                case MAP:
                    lambdaBody = getLambdaForMap();
                    break;
                case FOREACH:
                    lambdaBody = blockify(castToStatementTree(this.correspondingTree));
                    break;
                default:
                    return getArgumentsForReducer();
            }
        }
        var = getLambdaArguments();
        LambdaExpression newLambdaExpression = workingCopy.newLambdaExpression();
        newLambdaExpression.setBody(lambdaBody);
        newLambdaExpression.parameters().addAll(Arrays.asList(var));
        lambda=newLambdaExpression;
//        lambda = workingCopy.newLambdaExpression(Arrays.asList(var), lambdaBody);
        List<Expression> args = new ArrayList<>();

        args.add(lambda);
        return args;
    }

	 private ASTNode blockify( Statement correspondingTree) {
		 Block newBlock = this.workingCopy.newBlock();
		 newBlock.statements().addAll(Arrays.asList(correspondingTree));
		 return newBlock;
	    }
	 private Statement castToStatementTree(ASTNode currentTree) {
	        if (currentTree instanceof Statement) {
	            return (Statement) currentTree;
	        } else {
	        	ExpressionStatement newExpressionStatement = workingCopy.newExpressionStatement((Expression) currentTree);
	            return newExpressionStatement;
	        }
	    }

	 private ASTNode getLambdaForMap() {
		 ASTNode lambdaBody;
	        if (isNumericLiteral(this.correspondingTree)) {
	            lambdaBody = this.correspondingTree;
	        } else {
	            lambdaBody = ((ExpressionStatement) this.correspondingTree).getExpression();
	        }
	        return lambdaBody;
	    }

	 private List<Expression> getArgumentsForReducer() {
		 VariableDeclaration var;
//	        VariableTree var;
		 Expression lambda;
	        InfixExpression lambdaBody;
//	        int opKind = this.correspondingTree.getNodeType();
	        
	        List<Expression> args = new ArrayList<>();
	        args.add(this.reducingVariable);
	        if (TreeUtilities.isPreOrPostfixOp(this.correspondingTree)) {
	        	ASTNode type = null;//treeMaker.Type("Integer");
//	            var = this.treeMaker.Variable(treeMaker.Modifiers(new HashSet<>()), "accumulator", null, null);
	            var= this.workingCopy.newVariableDeclarationFragment();
	            var.setName(this.workingCopy.newSimpleName("accumulator"));
	            VariableDeclaration var1 = makeUnknownVariable();
	            if (ASTNodes.hasOperator((PostfixExpression) this.correspondingTree, PostfixExpression.Operator.INCREMENT) || ASTNodes.hasOperator((PrefixExpression) this.correspondingTree, PrefixExpression.Operator.INCREMENT)) {
	                if (isInteger(this.reducingVariable, workingCopy)) {
	                    lambda = makeIntegerSumReducer();
	                } else {
//	                    lambdaBody = this.treeMaker.Binary(ASTNode.PLUS, this.treeMaker.Identifier("accumulator"), this.treeMaker.Literal(1));
//	                    lambda = treeMaker.LambdaExpression(Arrays.asList(var, var1), lambdaBody);
	                    
	                    
	                    MethodInvocation accumulator = this.workingCopy.newMethodInvocation();
		                accumulator.setName(this.workingCopy.newSimpleName("accumulator"));
		                NumberLiteral newStringLiteral = this.workingCopy.newNumberLiteral("1");
		                
		                lambdaBody= this.workingCopy.newInfixExpression();
		                lambdaBody.setLeftOperand(accumulator);
		                lambdaBody.setRightOperand(newStringLiteral);
		                lambdaBody.setOperator(InfixExpression.Operator.PLUS);
		                
		                 LambdaExpression newLambdaExpression = this.workingCopy.newLambdaExpression();
						
		         		List<VariableDeclaration> lambdaParameters= newLambdaExpression.parameters();
		         		newLambdaExpression.setParentheses(false);
		         		newLambdaExpression.setBody(lambdaBody);
		         		lambdaParameters.addAll(Arrays.asList(var, var1));
		         		lambda= newLambdaExpression;
	                }

	            } else //if (opKind == Tree.Kind.POSTFIX_DECREMENT || opKind == Tree.Kind.PREFIX_DECREMENT) {
	            {
//	                lambdaBody = this.treeMaker.Binary(ASTNode.PREFIX_EXPRESSION .pref.MINUS, this.treeMaker.Identifier("accumulator"), this.treeMaker.Literal(1));
	                
	                MethodInvocation accumulator = this.workingCopy.newMethodInvocation();
	                accumulator.setName(this.workingCopy.newSimpleName("accumulator"));
	                NumberLiteral newStringLiteral = this.workingCopy.newNumberLiteral("1");
	                
	                lambdaBody= this.workingCopy.newInfixExpression();
	                lambdaBody.setLeftOperand(accumulator);
	                lambdaBody.setRightOperand(newStringLiteral);
	                lambdaBody.setOperator(InfixExpression.Operator.PLUS);
	                
	                 LambdaExpression newLambdaExpression = this.workingCopy.newLambdaExpression();
					
	         		List<VariableDeclaration> lambdaParameters= newLambdaExpression.parameters();
	         		newLambdaExpression.setParentheses(false);
	         		newLambdaExpression.setBody(lambdaBody);
	         		lambdaParameters.addAll(Arrays.asList(var, var1));
	         		lambda= newLambdaExpression;
//	                lambda = treeMaker.LambdaExpression(Arrays.asList(var, var1), lambdaBody);
	            }

	            args.add(lambda);


	        } else if (TreeUtilities.isCompoundAssignementAssignement(this.correspondingTree)) {
	        	ASTNode type = null;//treeMaker.Type("Integer");

//	            var = this.treeMaker.Variable(treeMaker.Modifiers(new HashSet<>()), "accumulator", null, null);
	            var= this.workingCopy.newVariableDeclarationFragment();
	            
	    		var.setName(this.workingCopy.newSimpleName("accumulator"));
	    		VariableDeclaration var1 = makeUnknownVariable();
	            if (ASTNodes.hasOperator((Assignment) correspondingTree, Assignment.Operator.PLUS_ASSIGN)) {
	                if (isString(this.reducingVariable)) {
	                    lambda = makeStringConcatReducer();
	                } else {
	                    if (isInteger(this.reducingVariable, workingCopy)) {
	                        lambda = makeIntegerSumReducer();
	                    } else {
	                        lambda = makeSimpleExplicitReducer(this.correspondingTree, var, var1);
	                    }
	                }
	            } else //if (opKind == Tree.Kind.MINUS_ASSIGNEMENT  ||  any other compound op) {
	            {
	                lambda = makeSimpleExplicitReducer(this.correspondingTree, var, var1);
	            }

	            args.add(lambda);
	            return args;
	        } else {
	            return null;
	        }
	        return args;
	    }

	private Expression makeSimpleExplicitReducer(ASTNode opKind, VariableDeclaration var, VariableDeclaration var1) {
//		ASTNode lambdaBody;
//        Expression lambda;
//        lambdaBody = this.treeMaker.Binary(this.getSuitableOperator(opKind), this.treeMaker.Identifier("accumulator"), this.treeMaker.Identifier(UNKNOWN_NAME));
//        lambda = treeMaker.LambdaExpression(Arrays.asList(var, var1), lambdaBody);
        
        MethodInvocation accumulator = this.workingCopy.newMethodInvocation();
//        newMethodInvocation.setExpression(ASTNodeFactory.newName(this.workingCopy, expr.toString()));
        accumulator.setName(this.workingCopy.newSimpleName("accumulator"));
        MethodInvocation unknown = this.workingCopy.newMethodInvocation();
        unknown.setName(this.workingCopy.newSimpleName(UNKNOWN_NAME));
        InfixExpression lambdaBody= this.workingCopy.newInfixExpression();
        lambdaBody.setLeftOperand(accumulator);
        lambdaBody.setRightOperand(unknown);
        lambdaBody.setOperator(this.getSuitableOperator(opKind));
        
        LambdaExpression lambda= this.workingCopy.newLambdaExpression();
 		List<VariableDeclaration> lambdaParameters= lambda.parameters();
 		lambda.setParentheses(false);
// 		VariableDeclarationFragment lambdaParameter= this.workingCopy.newVariableDeclarationFragment();
// 		lambdaParameter.setName((SimpleName) this.treeMaker.createCopyTarget(parameter.getName()));
// 		lambdaParameters.add(lambdaParameter);
// 		Expression createMoveTarget = (Expression) ASTNodes.createMoveTarget(this.treeMaker, lambdaBody);
 		lambda.setBody(lambdaBody);
 		lambdaParameters.addAll(Arrays.asList(var, var1));
        
        return lambda;
    }

	private InfixExpression.Operator getSuitableOperator(ASTNode kind) {
        if (ASTNodes.hasOperator((Assignment) correspondingTree, Assignment.Operator.BIT_AND_ASSIGN)) {
            return InfixExpression.Operator.AND;
        }
        if (ASTNodes.hasOperator((Assignment) correspondingTree, Assignment.Operator.BIT_OR_ASSIGN)) {
            return InfixExpression.Operator.OR;
        }
        if (ASTNodes.hasOperator((Assignment) correspondingTree, Assignment.Operator.PLUS_ASSIGN)) {
            return InfixExpression.Operator.PLUS;
        }
        if (ASTNodes.hasOperator((Assignment) correspondingTree, Assignment.Operator.MINUS_ASSIGN)) {
            return InfixExpression.Operator.MINUS;
        }
        if (ASTNodes.hasOperator((Assignment) correspondingTree, Assignment.Operator.DIVIDE_ASSIGN)) {
            return InfixExpression.Operator.DIVIDE;
        }
        if (ASTNodes.hasOperator((Assignment) correspondingTree, Assignment.Operator.TIMES_ASSIGN)) {
            return InfixExpression.Operator.TIMES;
        }
        if (ASTNodes.hasOperator((Assignment) correspondingTree, Assignment.Operator.REMAINDER_ASSIGN)) {
            return InfixExpression.Operator.REMAINDER;
        }
        if (ASTNodes.hasOperator((Assignment) correspondingTree, Assignment.Operator.LEFT_SHIFT_ASSIGN)) {
            return InfixExpression.Operator.LEFT_SHIFT;
        }
        if (ASTNodes.hasOperator((Assignment) correspondingTree, Assignment.Operator.RIGHT_SHIFT_SIGNED_ASSIGN)) {
            return InfixExpression.Operator.RIGHT_SHIFT_SIGNED;
        }
        if (ASTNodes.hasOperator((Assignment) correspondingTree, Assignment.Operator.RIGHT_SHIFT_UNSIGNED_ASSIGN)) {
            return InfixExpression.Operator.RIGHT_SHIFT_UNSIGNED;
        }
        return null;
    }

	private static boolean isInteger(Expression reducingVariable, AST workingCopy) {
		return isType(reducingVariable, workingCopy, "java.lang.Integer");
	}
	
	 private static boolean isType(Expression reducingVariable, AST workingCopy, String fqn) {
//		 ASTNodes.isCastCompatible(reducingVariable, reducingVariable)
//	        TypeMirror tm = workingCopy.getTrees().getTypeMirror(TreePath.getPath(workingCopy.getCompilationUnit(), reducingVariable));
//	        TypeElement typeEl = workingCopy.getElements().getTypeElement(fqn);
//	        if (typeEl != null) {
//	            TypeMirror integer = typeEl.asType();
//
//	            if (tm != null && workingCopy.getTypeUtilities().isCastable(tm, integer)) {
//	                return true;
//	            }
//	        }
	        return false;
	    }

	private Expression makeIntegerSumReducer() {
		MethodInvocation sum= this.workingCopy.newMethodInvocation();
		sum.setExpression(ASTNodeFactory.newName(this.workingCopy, Integer.class.getSimpleName()));
		sum.setName(this.workingCopy.newSimpleName("sum"));
		return sum;
//		return this.treeMaker.MemberReference(MemberReferenceTree.ReferenceMode.INVOKE, this.treeMaker.Identifier("Integer"), "sum", new ArrayList<>());
	}

	private Expression makeStringConcatReducer() {
		MethodInvocation sum= this.workingCopy.newMethodInvocation();
		sum.setExpression(ASTNodeFactory.newName(this.workingCopy, String.class.getSimpleName()));
		sum.setName(this.workingCopy.newSimpleName("concat"));
		return sum;
//		return this.treeMaker.MemberReference(MemberReferenceTree.ReferenceMode.INVOKE, this.treeMaker.Identifier("String"), "concat", new ArrayList<>());
	}

	private boolean isString(Expression reducingVariable2) {
		return false;
//		ASTNodes.getTargetType(reducingVariable2)
//		TypeMirror tm = this.workingCopy.getTrees().getTypeMirror(TreePath.getPath(this.workingCopy.getCompilationUnit(), this.reducingVariable));
//        return tm != null && tm.toString().equals("java.lang.String");
	}

	private VariableDeclaration makeUnknownVariable() {
		VariableDeclarationFragment unknownvar= this.workingCopy.newVariableDeclarationFragment();
		unknownvar.setName(this.workingCopy.newSimpleName(UNKNOWN_NAME));
		return unknownvar;
//        return this.treeMaker.Variable(treeMaker.Modifiers(new HashSet<>()), UNKNOWN_NAME, null, null);
    }

	   private ASTNode getLambdaArguments() {
		   VariableDeclaration var;
	        if (this.getNeededVariables().isEmpty()) {
	            var = makeUnknownVariable();
	        } else {
	        	Name varName = getOneFromSet(this.neededVariables);
	            //If types need to be made explicit the null should be replaced with the commented expression
	        	ASTNode type = null;// treeMaker.Type(this.varToType.get(varName).toString());
//	            var = this.treeMaker.Variable(treeMaker.Modifiers(new HashSet<>()), varName.toString(), type, null);
	            var= this.workingCopy.newVariableDeclarationFragment();
	            var.setName(this.workingCopy.newSimpleName(varName.toString()));
	        }
	        return var;
	    }

	 public static List<ProspectiveOperation> mergeIntoComposableOperations(List<ProspectiveOperation> ls) {
	        List<ProspectiveOperation> result = mergeRecursivellyIntoComposableOperations(ls);
	        if (result == null || result.contains(null)) {
	            return null;
	        } else {
	            return result;
	        }
	    }

	 private static List<ProspectiveOperation> mergeRecursivellyIntoComposableOperations(List<ProspectiveOperation> ls) {
	        for ( int i = ls.size() - 1; i > 0; i--) {
	            ProspectiveOperation current = ls.get(i);
	            ProspectiveOperation prev = ls.get(i - 1);
	            if (!(areComposable(current, prev))) {
	                if (!current.isMergeable() || !prev.isMergeable()) {
	                    return null;
	                }
	                if (current.opType == OperationType.FILTER || prev.opType == OperationType.FILTER) {
	                    int lengthOfLs;
	                    ProspectiveOperation last;
	                    ProspectiveOperation nlast;
	                    while ((lengthOfLs = ls.size()) > i) {
	                        last = ls.get(lengthOfLs - 1);
	                        nlast = ls.get(lengthOfLs - 2);
	                        ls.remove(lengthOfLs - 1);
	                        //method mutates in place, no need to remove and add again.
	                        nlast.merge(last);
	                    }
	                } else {
	                    prev.merge(current);
	                    ls.remove(i);
	                }
	            }
	        }
	        beautify(ls);
	        return ls;
	    }

	  private static void beautify(List<ProspectiveOperation> ls) {
	        for ( int i = ls.size() - 1; i > 0; i--) {
	            ProspectiveOperation current = ls.get(i - 1);
	            ProspectiveOperation next = ls.get(i);
	            Set<Name> needed = next.getNeededVariables();
	            current.beautify(needed);
	        }
	        for (Iterator<ProspectiveOperation> it = ls.iterator(); it.hasNext();) {
	            if (it.next().correspondingTree == null)
	                it.remove();
	        }
	    }

	 private void beautify(Set<Name> needed) {
        if (this.opType == OperationType.MAP) {
            beautifyLazy(needed);
        }
    }

	   private void beautifyLazy(Set<Name> needed) {
	        if (needed.isEmpty()) {
	            {
	                if (!this.getNeededVariables().isEmpty()) {
	                    this.beautify(this.getNeededVariables());
	                } else {
	                    Set<Name> newSet = new HashSet<>();
	                    newSet.add(null);
	                    beautifyLazy(newSet);
	                }
	            }
	        } else {
	            ASTNode currentTree = this.correspondingTree;
	            if (currentTree.getNodeType() == ASTNode.BLOCK) {
	                beautifyBlock(currentTree, needed);
	            } else if (currentTree.getNodeType() == ASTNode.VARIABLE_DECLARATION_FRAGMENT) {
	                beautifyVariable(currentTree, needed);
	            } else if (currentTree.getNodeType() == ASTNode.EXPRESSION_STATEMENT
	                    && ((ExpressionStatement) currentTree).getExpression().getNodeType() == ASTNode.ASSIGNMENT) {
	                beautifyAssignement(currentTree, needed);
	            } else if (isNumericLiteral(currentTree)) {
	                //do nothing
	            } else {
	                this.correspondingTree = this.addReturn(castToStatementTree(currentTree), getOneFromSet(needed));
	            }
	        }
	    }
	
	private boolean isNumericLiteral(ASTNode currentTree) {
       int kind = currentTree.getNodeType();
		return kind == ASTNode.CHARACTER_LITERAL
				|| kind == ASTNode.NUMBER_LITERAL;
//        return kind == ASTNode.INT_LITERAL
//                || kind == ASTNode.CHAR_LITERAL
//                || kind == ASTNode.DOUBLE_LITERAL
//                || kind == ASTNode.FLOAT_LITERAL
//                || kind == ASTNode.LONG_LITERAL;
    }

	private void beautifyAssignement(ASTNode currentTree, Set<Name> needed) {
//        Assignment assigned = (Assignment) ((ExpressionStatement) currentTree).getExpression();
//        Expression variable = assigned.getLeftHandSide();
//        if (variable.getNodeType() == ASTNode..IDENTIFIER) {
//            Identifier id = (Identifier) variable;
//
//            if (needed.contains(id.getName())) {
//                this.correspondingTree = treeMaker.ExpressionStatement(assigned.getExpression());
//            } else {
//
//                this.correspondingTree = this.addReturn(castToStatementTree(currentTree), getOneFromSet(needed));
//            }
//        } else {
//            this.correspondingTree = this.addReturn(castToStatementTree(currentTree), getOneFromSet(needed));
//        }
    }

	private void beautifyVariable(ASTNode currentTree, Set<Name> needed) {
//		VariableDeclaration varTree = (VariableDeclaration) currentTree;
//        if (needed.contains(varTree.getName())) {
//            this.correspondingTree = varTree.getInitializer() != null
//                    ? treeMaker.ExpressionStatement(varTree.getInitializer())
//                    : null;
//        } else {
//
//            this.correspondingTree = this.addReturn(castToStatementTree(currentTree), getOneFromSet(needed));
//        }
    }

	private void beautifyBlock(ASTNode currentTree, Set<Name> needed) {
        Block currentBlock = (Block) currentTree;
        if (currentBlock.statements().size() == 1) {
            this.correspondingTree = (ASTNode) currentBlock.statements().get(0);
            this.beautify(needed);
        } else {
            this.correspondingTree = this.addReturn(currentBlock, getOneFromSet(needed));
        }
    }

	 private Block addReturn(Statement statement, Name varName) {
	        List<Statement> ls = new ArrayList<>();
	        if (statement.getNodeType() == ASTNode.BLOCK) {
	            ls.addAll(((Block) statement).statements());
	        } else {
	            ls.add(statement);
	        }
	        if (varName != null) {
//	            Statement return1 = this.treeMaker.Return(treeMaker.Identifier(varName.toString()));
	            ReturnStatement return1 = this.workingCopy.newReturnStatement();
	            return1.setExpression(varName);
				ls.add(return1);
	        } else {
//	            Statement return1 = this.treeMaker.Return(treeMaker.Identifier(UNKNOWN_NAME));
	            ReturnStatement return1 = this.workingCopy.newReturnStatement();
	            return1.setExpression(ASTNodeFactory.newName(this.workingCopy, UNKNOWN_NAME));
				ls.add(return1);
	        }
	        Block newBlock = this.workingCopy.newBlock();
	        newBlock.statements().add(ls);
	        return newBlock;
//	        return treeMaker.Block(ls, false);
	    }

	 private Name getOneFromSet(Set<Name> needed) {
	        return needed.iterator().next();
	    }

	 public Set<Name> getNeededVariables() {
	        if (neededVariables == null) {
	            if (this.opType == OperationType.REDUCE) {
	                return new HashSet<>();
	            }
	            PreconditionsChecker.VariablesVisitor treeVariableVisitor = new PreconditionsChecker.VariablesVisitor();
	            if (this.correspondingTree.getNodeType() == ASTNode.SINGLE_VARIABLE_DECLARATION) {
	            	((VariableDeclaration) correspondingTree).getInitializer().accept(treeVariableVisitor);
//	                treeVariableVisitor.scan(((VariableDeclaration) correspondingTree).getInitializer(), this.workingCopy.getTrees());
	            } else {
	            	 correspondingTree.accept(treeVariableVisitor);
//	                treeVariableVisitor.scan(correspondingTree, this.workingCopy.getTrees());
	            }
	            this.neededVariables = buildNeeded(treeVariableVisitor);
	        }
	        return this.neededVariables;
	    }

	 private Set<Name> buildNeeded(PreconditionsChecker.VariablesVisitor treeVariableVisitor) {
	        Set<Name> allVariablesUsedInCurrentOp = treeVariableVisitor.getAllLocalVariablesUsed();
	        //Remove the ones also declared in the current block.
	        allVariablesUsedInCurrentOp.removeAll(treeVariableVisitor.getInnervariables());
	        //Keeps the ones that are local to the loop. These are the ones that need to be passed around
	        //in a pipe-like fashion.
	        allVariablesUsedInCurrentOp.retainAll(this.innerLoopVariables);
	        return allVariablesUsedInCurrentOp;
	    }

	public void merge(ProspectiveOperation op) {
	        if (this.opType == OperationType.FILTER) {
	            this.opType = op.opType;
//	            IfStatement ifTree = this.treeMaker.If(((IfStatement) this.correspondingTree).getExpression(), (Statement) op.correspondingTree, null);
	            IfStatement ifTree =  this.workingCopy.newIfStatement();
	            ifTree.setExpression(((IfStatement) this.correspondingTree).getExpression());
	            ifTree.setThenStatement((Statement) ASTNodes.createMoveTarget(this.treeMaker, op.correspondingTree));
	            this.correspondingTree = ifTree;
	        } else {
	            this.opType = op.opType;
	            List<Statement> statements = new ArrayList<>();

	            if (this.correspondingTree.getNodeType() == ASTNode.BLOCK) {
	                statements.addAll(((Block) this.correspondingTree).statements());
	            } else {
	                statements.add(castToStatementTree(this.correspondingTree));
	            }

	            if (op.correspondingTree.getNodeType() == ASTNode.BLOCK) {
	                statements.addAll(((Block) op.correspondingTree).statements());
	            } else {
	                statements.add(castToStatementTree(op.correspondingTree));
	            }
	            HashSet<Name> futureAvailable = new HashSet<>();
	            HashSet<Name> futureNeeded = new HashSet<>();

	            futureAvailable.addAll(this.getAvailableVariables());
	            futureAvailable.addAll(op.getAvailableVariables());

	            futureNeeded.addAll(op.getNeededVariables());
	            futureNeeded.removeAll(this.getAvailableVariables());
	            futureNeeded.addAll(this.getNeededVariables());

	            this.neededVariables = futureNeeded;
	            this.availableVariables = futureAvailable;
//	            this.correspondingTree = this.treeMaker.Block(statements, false);
	            Block newBlock = this.workingCopy.newBlock();
	            newBlock.statements().addAll(statements);
	            this.correspondingTree=newBlock;
	        }
	    }

	 private Set<Name> getAvailableVariables() {
	        if (this.availableVariables == null) {
	            PreconditionsChecker.VariablesVisitor treeVariableVisitor = new PreconditionsChecker.VariablesVisitor();
	            if (this.correspondingTree.getNodeType() == ASTNode.VARIABLE_DECLARATION_FRAGMENT) {
	            	((VariableDeclaration) correspondingTree).getInitializer().accept(treeVariableVisitor);
//	                treeVariableVisitor.scan(((VariableDeclaration) correspondingTree).getInitializer(), this.workingCopy.getTrees());
	                this.availableVariables = buildAvailables(treeVariableVisitor);
	                this.availableVariables.add(((VariableDeclaration) correspondingTree).getName());
	            } else {
	            	correspondingTree.accept(treeVariableVisitor);
//	                treeVariableVisitor.scan(correspondingTree, this.workingCopy.getTrees());
	                this.availableVariables = buildAvailables(treeVariableVisitor);
	            }
	        }
	        //If the operation is a filter, then it only makes available what it gets
	        //if needed is empty, it can pull anything needed from upstream.
	        if (this.opType == OperationType.FILTER) {
	            return this.getNeededVariables();
	        }
	        return this.availableVariables;
	    }

	private Set<Name> buildAvailables(PreconditionsChecker.VariablesVisitor treeVariableVisitor) {
        Set<Name> allVariablesUsedInCurrentOp = treeVariableVisitor.getAllLocalVariablesUsed();
        Set<Name> allVariablesDeclaredInCurrentOp = treeVariableVisitor.getInnervariables();
        allVariablesUsedInCurrentOp.addAll(allVariablesDeclaredInCurrentOp);
        return allVariablesUsedInCurrentOp;
    }

	private Boolean isMergeable() {
        return this.opType == OperationType.FOREACH
                || this.opType == OperationType.MAP
                || this.opType == OperationType.FILTER;
    }

	 private static boolean areComposable(ProspectiveOperation current, ProspectiveOperation prev) {
	        Set<Name> needed = current.getNeededVariables();
	        return needed.size() <= 1 && prev.areAvailableVariables(needed);
	    }

	    public Boolean areAvailableVariables(Set<Name> needed) {
	        Set<Name> available = this.getAvailableVariables();
	        //If the prospective operations does not need any variables from upstream
	        //(available is a superset of needeld so the test is sound - the available set includes all the uses)
	        //(because, for example, it uses fields or other variables that remain in scope even after refactoring)        
	        if (available.isEmpty()) {
	            //then the needed variables propagate from the downstream operation in order to facillitate chaining.
	            //(both to the needed and available sets).
	            available.addAll(needed);
	            this.getNeededVariables().addAll(needed);
	            return true;
	        }
	        return available.containsAll(needed);
	    }

	//Creates a non-eager operation according to the tree type
    public static List<ProspectiveOperation> createOperator(Statement tree,
            OperationType operationType, PreconditionsChecker precond, AST workingCopy, ASTRewrite rewrite) {
        List<ProspectiveOperation> ls = new ArrayList<>();
        if (OperationType.REDUCE == operationType) {
            return createProspectiveReducer(tree, workingCopy, operationType, precond, ls, rewrite);
        } else {
            ProspectiveOperation operation = new ProspectiveOperation(tree, operationType, precond.getInnerVariables(), workingCopy, precond.getVarToName(),rewrite);
            operation.getNeededVariables();
            ls.add(operation);
            return ls;
        }
    }
	
	 private static List<ProspectiveOperation> createProspectiveReducer(Statement tree, AST workingCopy, OperationType operationType, PreconditionsChecker precond, List<ProspectiveOperation> ls, ASTRewrite rewrite) throws IllegalStateException {
	        Expression expr = ((ExpressionStatement) tree).getExpression();
//	        TreeMaker tm = workingCopy.getTreeMaker();
	        ProspectiveOperation redOp = null;
	        AST tm = null;
			if (TreeUtilities.isCompoundAssignementAssignement(expr)) {
	            redOp = handleCompoundAssignementReducer(tm, expr, operationType, precond, workingCopy, ls, redOp,rewrite);
	        } else if (TreeUtilities.isPreOrPostfixOp(expr)) {
	            redOp = handlePreOrPostFixReducer(expr, workingCopy, tm, operationType, precond, ls, redOp);
	        }
	        ls.add(redOp);
	        return ls;
	    }

	 private static ProspectiveOperation handlePreOrPostFixReducer(Expression expr, AST workingCopy, AST tm, OperationType operationType, PreconditionsChecker precond, List<ProspectiveOperation> ls, ProspectiveOperation redOp) {
//	        Expression reducing = ((Unary) expr).getExpression();
//	        ProspectiveOperation map;
//	        if (isInteger(reducing, workingCopy) || isLong(reducing, workingCopy) || isChar(reducing, workingCopy)) {
//	            map = new ProspectiveOperation(tm.Literal(1), OperationType.MAP, precond.getInnerVariables(), workingCopy, precond.getVarToName());
//	        } else {
//	            map = new ProspectiveOperation(tm.Literal(1.), OperationType.MAP, precond.getInnerVariables(), workingCopy, precond.getVarToName());
//	        }
//	        ls.add(map);
//	        redOp = new ProspectiveOperation(expr, operationType, precond.getInnerVariables(), workingCopy, precond.getVarToName());
//	        redOp.reducingVariable = reducing;
	        return redOp;
	    }

	
	 private static boolean isChar(Expression reducing, AST workingCopy) {
		 return isType(reducing, workingCopy, "java.lang.Character");
	}

	private static boolean isLong(Expression reducing, AST workingCopy) {
		return isType(reducing, workingCopy, "java.lang.Long");
	}

	private static ProspectiveOperation handleCompoundAssignementReducer(AST tm, Expression expr, OperationType operationType, PreconditionsChecker precond, AST workingCopy, List<ProspectiveOperation> ls, ProspectiveOperation redOp,ASTRewrite rewrite) {
	        //this variable will be removed at a later stage.
//	        Variable var = tm.Variable(tm.Modifiers(new HashSet<>()), "dummyVar18912", tm.Type("Object"), ((CompoundAssignment) expr).getExpression());
//	        ProspectiveOperation map = new ProspectiveOperation(var, OperationType.MAP, precond.getInnerVariables(), workingCopy, precond.getVarToName());
//	        map.getAvailableVariables().add(var.getName());
//	        ls.add(map);
	        redOp = new ProspectiveOperation(expr, operationType, precond.getInnerVariables(), workingCopy, precond.getVarToName(), rewrite);
	        redOp.neededVariables = new HashSet<>();
//	        redOp.neededVariables.add(var.getName());
	        redOp.reducingVariable = ((Assignment) expr).getLeftHandSide();
	        return redOp;
	    }
}
