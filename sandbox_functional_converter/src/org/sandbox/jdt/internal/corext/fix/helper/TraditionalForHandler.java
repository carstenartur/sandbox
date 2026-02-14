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
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.functional.core.builder.LoopModelBuilder;
import org.sandbox.functional.core.model.ElementDescriptor;
import org.sandbox.functional.core.model.LoopModel;
import org.sandbox.functional.core.model.SourceDescriptor;
import org.sandbox.functional.core.terminal.ForEachTerminal;
import org.sandbox.functional.core.transformer.LoopModelTransformer;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore;

/**
 * Handler for converting traditional index-based for-loops to IntStream.range() operations.
 * 
 * <p>This handler converts classic for-loops with integer counters to functional IntStream operations:</p>
 * <pre>{@code
 * // Before:
 * for (int i = 0; i < 10; i++) {
 *     System.out.println(i);
 * }
 * 
 * // After:
 * IntStream.range(0, 10).forEach(i -> System.out.println(i));
 * }</pre>
 * 
 * <p><b>Supported Patterns:</b></p>
 * <ul>
 *   <li>Initializer: {@code int i = start} (single variable declaration with int type)</li>
 *   <li>Condition: {@code i < end} or {@code i <= end} (comparison with loop variable)</li>
 *   <li>Updater: {@code i++} or {@code ++i} (increment operator)</li>
 * </ul>
 * 
 * <p><b>Infrastructure:</b></p>
 * <ul>
 *   <li>Uses {@link SourceDescriptor.SourceType#EXPLICIT_RANGE} for range representation</li>
 *   <li>Leverages existing {@link ASTStreamRenderer} for IntStream.range() rendering</li>
 *   <li>Builds {@link LoopModel} with {@link ForEachTerminal} for body statements</li>
 * </ul>
 * 
 * <p><b>Thread-Safety Guards:</b></p>
 * <ul>
 *   <li><b>Nested loop guard</b>: {@code isNestedInsideLoop()} rejects loops inside other loops
 *       to avoid interfering with the {@code EnhancedForHandler}'s scope analysis and to
 *       prevent generating IntStream calls inside lambdas where mutable loop state could be captured</li>
 *   <li><b>Unconvertible statement detection</b>: {@code containsUnconvertibleStatements()} rejects
 *       loops with {@code break}, {@code continue}, or {@code return} which cannot be expressed
 *       in lambda bodies</li>
 *   <li><b>Sequential-only streams</b>: Always generates {@code IntStream.range()} (sequential),
 *       never parallel streams, avoiding complex synchronization requirements</li>
 *   <li><b>Synchronized block detection</b>: Handled upstream by {@code JdtLoopExtractor} and
 *       {@code PreconditionsChecker}, which reject loops containing synchronized statements
 *       before they reach this handler</li>
 * </ul>
 * 
 * <p><b>Naming Note:</b> This class is named after the <i>source</i> loop type (traditional for-loop),
 * not the target format. The architecture supports bidirectional transformations, so the name
 * describes what loop pattern this handler processes.</p>
 * 
 * @see SourceDescriptor.SourceType#EXPLICIT_RANGE
 * @see ASTStreamRenderer#renderSource(SourceDescriptor)
 */
public class TraditionalForHandler extends AbstractFunctionalCall<ForStatement> {
    
    @Override
    public void find(UseFunctionalCallFixCore fixcore, CompilationUnit compilationUnit,
                     Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed) {
        
        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(ForStatement node) {
                if (nodesprocessed.contains(node)) {
                    return false;
                }
                
                // Analyze the for-loop structure
                ForLoopPattern pattern = analyzeForLoop(node);
                if (pattern == null) {
                    return true; // Not a convertible pattern, continue visiting
                }
                
                // Mark as processed and add operation
                nodesprocessed.add(node);
                ReferenceHolder<ASTNode, Object> holder = ReferenceHolder.create();
                holder.put(node, pattern);
                operations.add(fixcore.rewrite(node, holder));
                
                return false; // Don't visit children of convertible loops
            }
        });
    }
    
    /**
     * Analyzes a ForStatement to determine if it follows the convertible pattern.
     * 
     * @param forStmt the ForStatement to analyze
     * @return a ForLoopPattern if convertible, null otherwise
     */
    private ForLoopPattern analyzeForLoop(ForStatement forStmt) {
        // Skip for-loops nested inside other loops to avoid converting
        // inner loops that the EnhancedForHandler expects to remain unchanged
        if (isNestedInsideLoop(forStmt)) {
            return null;
        }
        
        // Check initializer: must be single variable declaration of type int
        List<?> initializers = forStmt.initializers();
        if (initializers.size() != 1) {
            return null;
        }
        
        Object initObj = initializers.get(0);
        if (!(initObj instanceof VariableDeclarationExpression)) {
            return null;
        }
        
        VariableDeclarationExpression varDecl = (VariableDeclarationExpression) initObj;
        Type type = varDecl.getType();
        if (!isPrimitiveInt(type)) {
            return null;
        }
        
        List<?> fragments = varDecl.fragments();
        if (fragments.size() != 1) {
            return null;
        }
        
        VariableDeclarationFragment fragment = (VariableDeclarationFragment) fragments.get(0);
        String loopVarName = fragment.getName().getIdentifier();
        Expression startExpr = fragment.getInitializer();
        
        if (startExpr == null) {
            return null;
        }
        
        // Check condition: must be infix comparison with loop variable
        Expression condition = forStmt.getExpression();
        if (!(condition instanceof InfixExpression)) {
            return null;
        }
        
        InfixExpression infixCond = (InfixExpression) condition;
        InfixExpression.Operator operator = infixCond.getOperator();
        
        // Check that left operand is the loop variable
        Expression leftOperand = infixCond.getLeftOperand();
        if (!(leftOperand instanceof SimpleName)) {
            return null;
        }
        
        SimpleName leftName = (SimpleName) leftOperand;
        if (!leftName.getIdentifier().equals(loopVarName)) {
            return null;
        }
        
        Expression endExpr = infixCond.getRightOperand();
        if (endExpr == null) {
            return null;
        }
        
        boolean inclusive = false;
        if (operator == InfixExpression.Operator.LESS) {
            // i < end  →  IntStream.range(start, end)
            inclusive = false;
        } else if (operator == InfixExpression.Operator.LESS_EQUALS) {
            // i <= end  →  IntStream.rangeClosed(start, end) or range(start, end+1)
            inclusive = true;
        } else {
            return null; // Unsupported operator
        }
        
        // Check updater: must be i++ or ++i
        List<?> updaters = forStmt.updaters();
        if (updaters.size() != 1) {
            return null;
        }
        
        Object updaterObj = updaters.get(0);
        boolean isIncrement = false;
        
        if (updaterObj instanceof PostfixExpression) {
            PostfixExpression postfix = (PostfixExpression) updaterObj;
            if (postfix.getOperator() == PostfixExpression.Operator.INCREMENT) {
                Expression operand = postfix.getOperand();
                if (operand instanceof SimpleName && ((SimpleName) operand).getIdentifier().equals(loopVarName)) {
                    isIncrement = true;
                }
            }
        } else if (updaterObj instanceof PrefixExpression) {
            PrefixExpression prefix = (PrefixExpression) updaterObj;
            if (prefix.getOperator() == PrefixExpression.Operator.INCREMENT) {
                Expression operand = prefix.getOperand();
                if (operand instanceof SimpleName && ((SimpleName) operand).getIdentifier().equals(loopVarName)) {
                    isIncrement = true;
                }
            }
        }
        
        if (!isIncrement) {
            return null;
        }
        
        // Extract body
        Statement body = forStmt.getBody();
        
        // Check if body contains unconvertible statements (break, continue, return)
        if (containsUnconvertibleStatements(body)) {
            return null;
        }
        
        // Issue #670: Check if the index variable is used in the body for complex
        // patterns like a[i+1], a[i-1], or i%2 that indicate neighbor access or
        // non-trivial index semantics. These patterns suggest the loop relies on
        // index arithmetic that may not be safely convertible.
        if (usesIndexBeyondSimpleAccess(body, loopVarName)) {
            return null;
        }
        
        return new ForLoopPattern(loopVarName, startExpr, endExpr, inclusive, body);
    }
    
    /**
     * Checks if a Type represents primitive int.
     */
    private boolean isPrimitiveInt(Type type) {
        if (type instanceof PrimitiveType) {
            PrimitiveType primType = (PrimitiveType) type;
            return primType.getPrimitiveTypeCode() == PrimitiveType.INT;
        }
        return false;
    }
    
    /**
     * Checks if the for-loop is nested inside another loop (enhanced-for, while, for, do-while).
     * Nested traditional for-loops are skipped to avoid interfering with the EnhancedForHandler's
     * analysis of outer enhanced-for loops.
     */
    private boolean isNestedInsideLoop(ForStatement forStmt) {
        ASTNode parent = forStmt.getParent();
        while (parent != null) {
            if (parent instanceof EnhancedForStatement
                    || parent instanceof ForStatement
                    || parent instanceof WhileStatement
                    || parent instanceof DoStatement) {
                return true;
            }
            if (parent instanceof MethodDeclaration || parent instanceof TypeDeclaration) {
                break;
            }
            parent = parent.getParent();
        }
        return false;
    }
    
    /**
     * Checks if the loop body contains unconvertible statements.
     * Statements containing break, continue, or return cannot be converted to lambda.
     */
    private boolean containsUnconvertibleStatements(Statement body) {
        final boolean[] hasUnconvertible = {false};
        
        body.accept(new ASTVisitor() {
            @Override
            public boolean visit(BreakStatement node) {
                hasUnconvertible[0] = true;
                return false;
            }
            
            @Override
            public boolean visit(ContinueStatement node) {
                hasUnconvertible[0] = true;
                return false;
            }
            
            @Override
            public boolean visit(ReturnStatement node) {
                hasUnconvertible[0] = true;
                return false;
            }
        });
        
        return hasUnconvertible[0];
    }
    
    /**
     * Checks if the index variable is used in the loop body for complex patterns
     * that go beyond simple counter semantics.
     * 
     * <p>Detects the index variable participating in arithmetic InfixExpressions
     * (e.g., {@code i + 1}, {@code i - 1}, {@code i * 2}, {@code i % 2}).
     * This also catches array/list neighbor access patterns like {@code a[i+1]}
     * because the subscript expression {@code i+1} is itself an InfixExpression.</p>
     * 
     * <p>These patterns indicate the loop relies on index relationships between
     * iterations, which may not be safely convertible to stream operations.</p>
     * 
     * @param body the loop body statement
     * @param indexVarName the name of the index variable
     * @return true if the index is used in complex patterns
     * 
     * @see <a href="https://github.com/carstenartur/sandbox/issues/670">Issue #670</a>
     */
    private boolean usesIndexBeyondSimpleAccess(Statement body, String indexVarName) {
        final boolean[] hasComplexUsage = {false};
        
        body.accept(new ASTVisitor() {
            @Override
            public boolean visit(InfixExpression node) {
                // Check if the index variable is used in arithmetic operations
                // like i+1, i-1, i*2, i%2
                if (isIndexInArithmeticExpression(node, indexVarName)) {
                    hasComplexUsage[0] = true;
                    return false;
                }
                return true;
            }
        });
        
        return hasComplexUsage[0];
    }
    
    /**
     * Checks if an InfixExpression uses the index variable in arithmetic.
     * Detects patterns: i+1, i-1, i*2, i%2, etc., including nested and chained
     * arithmetic expressions (e.g., (i + 1) + offset, multiplier * (i - 1)).
     */
    private boolean isIndexInArithmeticExpression(InfixExpression expr, String indexVarName) {
        if (!isArithmeticOperator(expr.getOperator())) {
            return false;
        }

        // Recursively inspect all operands (left, right, and extended) for
        // arithmetic usage of the index variable.
        if (containsIndexInArithmeticOperand(expr.getLeftOperand(), indexVarName)) {
            return true;
        }
        if (containsIndexInArithmeticOperand(expr.getRightOperand(), indexVarName)) {
            return true;
        }
        @SuppressWarnings("unchecked")
        List<Expression> extended = expr.extendedOperands();
        for (Expression operand : extended) {
            if (containsIndexInArithmeticOperand(operand, indexVarName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if the given operator is an arithmetic operator
     * relevant for index arithmetic detection.
     */
    private boolean isArithmeticOperator(InfixExpression.Operator op) {
        return op == InfixExpression.Operator.PLUS
                || op == InfixExpression.Operator.MINUS
                || op == InfixExpression.Operator.TIMES
                || op == InfixExpression.Operator.DIVIDE
                || op == InfixExpression.Operator.REMAINDER;
    }

    /**
     * Recursively checks whether the given expression contains an arithmetic
     * use of the index variable.
     */
    private boolean containsIndexInArithmeticOperand(Expression expr, String indexVarName) {
        if (expr == null) {
            return false;
        }

        if (expr instanceof SimpleName) {
            return ((SimpleName) expr).getIdentifier().equals(indexVarName);
        }

        if (expr instanceof ParenthesizedExpression) {
            Expression inner = ((ParenthesizedExpression) expr).getExpression();
            return containsIndexInArithmeticOperand(inner, indexVarName);
        }

        if (expr instanceof InfixExpression infix) {
            if (!isArithmeticOperator(infix.getOperator())) {
                return false;
            }
            if (containsIndexInArithmeticOperand(infix.getLeftOperand(), indexVarName)) {
                return true;
            }
            if (containsIndexInArithmeticOperand(infix.getRightOperand(), indexVarName)) {
                return true;
            }
            @SuppressWarnings("unchecked")
            List<Expression> extended = infix.extendedOperands();
            for (Expression operand : extended) {
                if (containsIndexInArithmeticOperand(operand, indexVarName)) {
                    return true;
                }
            }
        }

        return false;
    }
    
    @Override
    public void rewrite(UseFunctionalCallFixCore upp, ForStatement visited,
                        CompilationUnitRewrite cuRewrite, TextEditGroup group,
                        ReferenceHolder<ASTNode, Object> data) throws CoreException {
        
        Object patternObj = data.get(visited);
        if (!(patternObj instanceof ForLoopPattern)) {
            return;
        }
        
        ForLoopPattern pattern = (ForLoopPattern) patternObj;
        
        AST ast = cuRewrite.getRoot().getAST();
        ASTRewrite rewrite = cuRewrite.getASTRewrite();
        
        // Build LoopModel
        LoopModel model = buildLoopModel(pattern, ast);
        
        // Create renderer
        ASTStreamRenderer renderer = new ASTStreamRenderer(ast, rewrite, cuRewrite.getRoot(), pattern.body);
        
        // Transform using LoopModelTransformer
        LoopModelTransformer<Expression> transformer = new LoopModelTransformer<>(renderer);
        Expression streamExpression = transformer.transform(model);
        
        if (streamExpression != null) {
            // Wrap in ExpressionStatement
            ExpressionStatement newStatement = ast.newExpressionStatement(streamExpression);
            rewrite.replace(visited, newStatement, group);
            
            // Add IntStream import
            cuRewrite.getImportRewrite().addImport("java.util.stream.IntStream");
        }
    }
    
    /**
     * Builds a LoopModel from the analyzed ForLoopPattern.
     */
    private LoopModel buildLoopModel(ForLoopPattern pattern, AST ast) {
        // Get start and end expressions as strings
        String startStr = pattern.startExpr.toString();
        String endStr = pattern.endExpr.toString();
        
        // Adjust end expression for inclusive range (i <= end)
        if (pattern.inclusive) {
            // For i <= end, we need IntStream.range(start, (end) + 1)
            // Parenthesize end expression to preserve operator precedence
            endStr = "(" + endStr + ") + 1";
        }
        
        // Build EXPLICIT_RANGE source descriptor
        String rangeExpression = startStr + "," + endStr;
        SourceDescriptor source = new SourceDescriptor(
            SourceDescriptor.SourceType.EXPLICIT_RANGE,
            rangeExpression,
            "int"
        );
        
        // Build element descriptor for the loop variable
        ElementDescriptor element = new ElementDescriptor(
            pattern.loopVarName,
            "int",
            false // not a collection element
        );
        
        // Extract body statements and convert to strings
        List<String> bodyStatements = extractBodyStatementsAsStrings(pattern.body);
        
        // Build ForEachTerminal
        ForEachTerminal terminal = new ForEachTerminal(bodyStatements, false); // uses forEach (not forEachOrdered)
        
        // Build and return LoopModel
        return new LoopModelBuilder()
            .source(source)
            .element(element)
            .terminal(terminal)
            .build();
    }
    
    /**
     * Extracts statements from the loop body and converts them to expression strings.
     * Trailing semicolons are stripped because ForEachTerminal / ASTStreamRenderer.createExpression()
     * expects pure expressions, not statements.  This matches how
     * {@code JdtLoopExtractor.addSimpleForEachTerminal()} produces body strings for
     * the existing EnhancedForHandler.
     */
    @SuppressWarnings("unchecked")
    private List<String> extractBodyStatementsAsStrings(Statement body) {
        return ExpressionHelper.bodyStatementsToStrings(body);
    }
    
    @Override
    public String getPreview(boolean afterRefactoring) {
        if (afterRefactoring) {
            return "IntStream.range(0, 10).forEach(i -> System.out.println(i));\n";
        }
        return "for (int i = 0; i < 10; i++)\n    System.out.println(i);\n";
    }
    
    /**
     * Represents an analyzed traditional for-loop pattern.
     */
    private static class ForLoopPattern {
        final String loopVarName;
        final Expression startExpr;
        final Expression endExpr;
        final boolean inclusive;  // true for <=, false for <
        final Statement body;
        
        ForLoopPattern(String loopVarName, Expression startExpr, Expression endExpr,
                      boolean inclusive, Statement body) {
            this.loopVarName = loopVarName;
            this.startExpr = startExpr;
            this.endExpr = endExpr;
            this.inclusive = inclusive;
            this.body = body;
        }
    }
}
