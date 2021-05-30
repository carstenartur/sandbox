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


import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.internal.corext.dom.GenericVisitor;
import org.sandbox.jdt.internal.corext.fix.helper.PreconditionsChecker.VariablesVisitor;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;

public class PreconditionsChecker {

	private boolean hasUncaughtException = false;
	private Set innerVariables;
	private ForLoopTreeVisitor visitor;
	private boolean isIterable;

	public PreconditionsChecker(EnhancedForStatement forLoop, AST ast) {
//		this.hasUncaughtException = ast.getTreeUtilities()
//                .getUncaughtExceptions(TreePath.getPath(ast.getCompilationUnit(), forLoop)).stream().anyMatch(this::filterCheckedExceptions);
        this.innerVariables = this.getInnerVariables(forLoop, ast);
        this.visitor = new ForLoopTreeVisitor(this.innerVariables, ast, forLoop);
        this.isIterable = this.isIterbale((forLoop).getExpression());
        forLoop.accept(visitor);
//        visitor.scan(TreePath.getPath(workingCopy.getCompilationUnit(), forLoop), workingCopy.getTrees());
	}

	 private boolean isIterbale(Expression expression) {
//	        TypeMirror tm = workingCopy.getTrees().getTypeMirror(TreePath.getPath(workingCopy.getCompilationUnit(), expression));
//	        if (!Utilities.isValidType(tm)) {
//	            return false;
//	        }
//	        if (tm.getKind() == TypeKind.ARRAY) {
//	            return false;
//	        } else {
//	            tm = workingCopy.getTypes().erasure(tm);
//	            TypeElement typeEl = workingCopy.getElements().getTypeElement("java.util.Collection");
//	            if (typeEl != null) {
//	                TypeMirror collection = typeEl.asType();
//	                collection = workingCopy.getTypes().erasure(collection);
//	                if (this.workingCopy.getTypes().isSubtype(tm, collection)) {
//	                    return true;
//	                }
//	            }
//	        }

	        return false;
	    }

	private Set getInnerVariables(EnhancedForStatement forLoop, AST ast) {
		VariablesVisitor vis = new VariablesVisitor();
//	        vis.scan(tree, trees);
		forLoop.accept(vis);
	        return vis.getInnervariables();
	}

	public static class ForLoopTreeVisitor extends GenericVisitor {

		public Boolean hasNonEffectivelyFinalVars=false;
		private Boolean hasReturns=false;
		private Boolean hasContinue=false;
		private Boolean hasBreaks=false;
		public ASTNode reducerStatement;
		public Map<Name, String> varToType;

		public ForLoopTreeVisitor(Set innerVariables, AST ast, EnhancedForStatement forLoop) {
			// TODO Auto-generated constructor stub
		}

	    public Boolean containsReturn() {
            return this.hasReturns;
        }

        public Boolean containsBreak() {
            return this.hasBreaks;
        }

        public Boolean containsContinue() {
            return this.hasContinue;
        }

	}
	public static class VariablesVisitor extends GenericVisitor {

		public VariablesVisitor() {
			// TODO Auto-generated constructor stub
		}

		public Set getInnervariables() {
			// TODO Auto-generated method stub
			return null;
		}

		public Set<Name> getAllLocalVariablesUsed() {
			// TODO Auto-generated method stub
			return null;
		}

	}
	public boolean isSafeToRefactor() {
		return this.iteratesOverIterable()
                && !(this.throwsException()
                || this.containsNEFs()
                || this.containsReturn()
                || this.containsBreak()
                || this.containsContinue());
	}

	  /*
     * Precondition 2
     * The body of the lambda does not refer Non-Effectively-Final(NEF) references
     * that are defined outsed the loop. 
     * This is because from within a lambda you cannot have references to a NEF.
     */
    protected Boolean containsNEFs() {
        return visitor.hasNonEffectivelyFinalVars;
    }

    /*
     * Precondition 3
     * The method is not allowed to have any break statements
     */
    protected Boolean containsBreak() {
        return visitor.containsBreak();
    }
    /*
     * Precondition 4: overly conservative - to be weakened when handled properly;
     * The method is not allowed to have continues in it.
     */
    protected Boolean containsContinue() {
        return visitor.containsContinue();
    }
    /*
     * preocndition 5: overly conservative - to be weakened when handled properly.
     * The method is not allowed to have Returns in it.
     */
    protected Boolean containsReturn() {
        return visitor.containsReturn();
    }
  
    /*
     * Precondition 1
     * The signature of the lambda expressions used in list operations
     * does not have a throws clause in its signature.
     */
    protected Boolean throwsException() {
        return this.hasUncaughtException;
    }

	private boolean iteratesOverIterable() {
		return this.isIterable;
	}

	public Boolean isReducer() {
        return this.visitor.reducerStatement != null;
    }

	public ASTNode getReducer() {
        return this.visitor.reducerStatement;
    }

	public Set<Name> getInnerVariables() {
        return this.innerVariables;
    }

	 Map<Name, String> getVarToName() {
	        return this.visitor.varToType;
	    }
}
