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
package org.sandbox.jdt.triggerpattern.modules;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.sandbox.jdt.triggerpattern.api.PatternContext;
import org.sandbox.jdt.triggerpattern.api.PatternHandler;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.cleanup.ReflectivePatternCleanupPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Cleanup plugin that converts if-else chains to switch statements using the reflective pattern framework.
 * 
 * <p>This plugin demonstrates the use of {@link ReflectivePatternCleanupPlugin} and
 * {@link PatternHandler} annotations to implement pattern-based code transformations.</p>
 * 
 * <p><b>Transformation Example:</b></p>
 * 
 * <p><b>Before:</b></p>
 * <pre>
 * if (value == 1) {
 *     doOne();
 * } else if (value == 2) {
 *     doTwo();
 * } else {
 *     doDefault();
 * }
 * </pre>
 * 
 * <p><b>After:</b></p>
 * <pre>
 * switch (value) {
 *     case 1:
 *         doOne();
 *         break;
 *     case 2:
 *         doTwo();
 *         break;
 *     default:
 *         doDefault();
 *         break;
 * }
 * </pre>
 * 
 * <p><b>Pattern Recognition:</b></p>
 * <ul>
 *   <li>Detects if-else chains with equality comparisons</li>
 *   <li>Extracts the common variable being compared</li>
 *   <li>Converts each if/else-if branch to a case statement</li>
 *   <li>Converts final else branch to default case</li>
 * </ul>
 * 
 * @since 1.3.0
 */
public class ConvertToSwitchPatternPlugin extends ReflectivePatternCleanupPlugin {
    
    /**
     * Handles if-else chain patterns and converts them to switch statements.
     * 
     * <p>This handler is invoked when an if-else chain is detected that can be
     * converted to a switch statement. The pattern matches if statements with
     * a then-part and an else-part.</p>
     * 
     * @param context the pattern context containing match information and rewrite capabilities
     */
    @PatternHandler(
        pattern = "if ($condition) { $then } else { $else }",
        kind = PatternKind.STATEMENT,
        priority = 1
    )
    public void convertIfElseToSwitch(PatternContext context) {
        ASTNode matchedNode = context.getMatchedNode();
        
        if (!(matchedNode instanceof IfStatement)) {
            return;
        }
        
        IfStatement ifStatement = (IfStatement) matchedNode;
        
        // Analyze the if-else chain to determine if it can be converted to a switch
        SwitchConversionInfo conversionInfo = analyzeSwitchConversion(ifStatement);
        
        if (conversionInfo == null || !conversionInfo.isConvertible()) {
            return;
        }
        
        // Perform the conversion
        AST ast = context.getAST();
        ASTRewrite rewriter = context.getRewriter();
        
        SwitchStatement switchStatement = createSwitchStatement(ast, conversionInfo);
        
        // Replace the if statement with the switch statement
        ASTNodes.replaceButKeepComment(rewriter, ifStatement, switchStatement, context.getGroup());
    }
    
    /**
     * Analyzes an if-else chain to determine if it can be converted to a switch statement.
     * 
     * @param ifStatement the root if statement
     * @return conversion information if convertible, null otherwise
     */
    private SwitchConversionInfo analyzeSwitchConversion(IfStatement ifStatement) {
        SwitchConversionInfo info = new SwitchConversionInfo();
        
        IfStatement current = ifStatement;
        
        while (current != null) {
            Expression condition = current.getExpression();
            
            // Check if condition is an equality comparison (==)
            if (!(condition instanceof InfixExpression)) {
                return null;
            }
            
            InfixExpression infixExpr = (InfixExpression) condition;
            if (infixExpr.getOperator() != InfixExpression.Operator.EQUALS) {
                return null;
            }
            
            Expression left = infixExpr.getLeftOperand();
            Expression right = infixExpr.getRightOperand();
            
            // Determine which operand is the variable and which is the constant
            Expression variable;
            Expression caseValue;
            
            if (isConstant(right)) {
                variable = left;
                caseValue = right;
            } else if (isConstant(left)) {
                variable = right;
                caseValue = left;
            } else {
                // Neither operand is a constant
                return null;
            }
            
            // Check if all comparisons use the same variable
            if (info.switchVariable == null) {
                info.switchVariable = variable;
            } else if (!ASTNodes.match(info.switchVariable, variable)) {
                return null;
            }
            
            // Add this case
            Statement thenStatement = current.getThenStatement();
            info.addCase(caseValue, thenStatement);
            
            // Check for else-if or final else
            Statement elseStatement = current.getElseStatement();
            if (elseStatement instanceof IfStatement) {
                current = (IfStatement) elseStatement;
            } else if (elseStatement != null) {
                // Final else becomes default case
                info.setDefaultCase(elseStatement);
                current = null;
            } else {
                current = null;
            }
        }
        
        return info.isConvertible() ? info : null;
    }
    
    /**
     * Checks if an expression is a constant value.
     * 
     * @param expr the expression to check
     * @return true if the expression is a constant
     */
    private boolean isConstant(Expression expr) {
        // Simple heuristic: number literals, string literals, boolean literals, null
        switch (expr.getNodeType()) {
            case ASTNode.NUMBER_LITERAL:
            case ASTNode.STRING_LITERAL:
            case ASTNode.BOOLEAN_LITERAL:
            case ASTNode.NULL_LITERAL:
            case ASTNode.CHARACTER_LITERAL:
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Creates a switch statement from the conversion information.
     * 
     * @param ast the AST instance
     * @param info the conversion information
     * @return the created switch statement
     */
    @SuppressWarnings("unchecked")
    private SwitchStatement createSwitchStatement(AST ast, SwitchConversionInfo info) {
        SwitchStatement switchStatement = ast.newSwitchStatement();
        switchStatement.setExpression((Expression) ASTNode.copySubtree(ast, info.switchVariable));
        
        List<Statement> statements = switchStatement.statements();
        
        // Add case statements
        for (CaseInfo caseInfo : info.cases) {
            // Create case label
            SwitchCase switchCase = ast.newSwitchCase();
            switchCase.expressions().add(ASTNode.copySubtree(ast, caseInfo.caseValue));
            statements.add(switchCase);
            
            // Add case body
            Statement body = caseInfo.body;
            if (body instanceof Block) {
                Block block = (Block) body;
                for (Object stmt : block.statements()) {
                    statements.add((Statement) ASTNode.copySubtree(ast, (Statement) stmt));
                }
            } else {
                statements.add((Statement) ASTNode.copySubtree(ast, body));
            }
            
            // Add break statement
            statements.add(ast.newBreakStatement());
        }
        
        // Add default case if present
        if (info.defaultCase != null) {
            SwitchCase defaultCase = ast.newSwitchCase();
            // Default case has no expression
            statements.add(defaultCase);
            
            Statement body = info.defaultCase;
            if (body instanceof Block) {
                Block block = (Block) body;
                for (Object stmt : block.statements()) {
                    statements.add((Statement) ASTNode.copySubtree(ast, (Statement) stmt));
                }
            } else {
                statements.add((Statement) ASTNode.copySubtree(ast, body));
            }
            
            statements.add(ast.newBreakStatement());
        }
        
        return switchStatement;
    }
    
    @Override
    public String getPreview(boolean afterRefactoring) {
        if (afterRefactoring) {
            return """
                switch (value) {
                    case 1:
                        doOne();
                        break;
                    case 2:
                        doTwo();
                        break;
                    default:
                        doDefault();
                        break;
                }
                """; //$NON-NLS-1$
        }
        return """
            if (value == 1) {
                doOne();
            } else if (value == 2) {
                doTwo();
            } else {
                doDefault();
            }
            """; //$NON-NLS-1$
    }
    
    /**
     * Holds information about a potential switch conversion.
     */
    private static class SwitchConversionInfo {
        Expression switchVariable;
        List<CaseInfo> cases = new ArrayList<>();
        Statement defaultCase;
        
        void addCase(Expression caseValue, Statement body) {
            cases.add(new CaseInfo(caseValue, body));
        }
        
        void setDefaultCase(Statement body) {
            this.defaultCase = body;
        }
        
        boolean isConvertible() {
            return switchVariable != null && !cases.isEmpty();
        }
    }
    
    /**
     * Holds information about a single case in the switch.
     */
    private static class CaseInfo {
        final Expression caseValue;
        final Statement body;
        
        CaseInfo(Expression caseValue, Statement body) {
            this.caseValue = caseValue;
            this.body = body;
        }
    }
}
