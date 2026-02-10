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
 * <p><b>Naming Note:</b> This class is named after the <i>source</i> loop type (traditional for-loop),
 * not the target format. The architecture supports bidirectional transformations, so the name
 * describes what loop pattern this handler processes.</p>
 * 
 * @see SourceDescriptor.SourceType#INT_RANGE
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
        
        // Analyze index usage to determine if we can eliminate the index variable
        IndexUsageAnalysis indexAnalysis = analyzeIndexUsage(body, loopVarName, endExpr);
        
        if (indexAnalysis.indexEliminable) {
            return new ForLoopPattern(loopVarName, startExpr, endExpr, inclusive, body,
                                     true, indexAnalysis.collectionExpr, indexAnalysis.elementVarName);
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
     * Analyzes how the loop index variable is used in the body.
     * Detects if the index is ONLY used for collection.get(i) calls and if so,
     * enables index elimination (convert to collection.forEach instead of IntStream.range).
     * 
     * @param body the loop body
     * @param loopVarName the loop variable name (e.g., "i")
     * @param conditionRightOperand the right operand of the loop condition (e.g., "items.size()")
     * @return IndexUsageAnalysis with elimination decision and collection info
     */
    private IndexUsageAnalysis analyzeIndexUsage(Statement body, String loopVarName, Expression conditionRightOperand) {
        // First, check if the condition is collection.size()
        Expression collectionExpr = extractCollectionFromSizeCall(conditionRightOperand);
        if (collectionExpr == null) {
            return IndexUsageAnalysis.notEliminable();
        }
        
        String collectionName = collectionExpr.toString();
        
        // Scan the body for all uses of the loop variable
        IndexUsageScanner scanner = new IndexUsageScanner(loopVarName, collectionName);
        body.accept(scanner);
        
        if (!scanner.allUsesAreCollectionGet) {
            return IndexUsageAnalysis.notEliminable();
        }
        
        if (scanner.usageCount == 0) {
            // Index not used at all - unusual but could happen
            return IndexUsageAnalysis.notEliminable();
        }
        
        // Derive element variable name from collection name
        String elementVarName = deriveElementVarName(collectionName);
        
        return new IndexUsageAnalysis(true, collectionExpr, elementVarName);
    }
    
    /**
     * Extracts the collection expression from a .size() method call.
     * E.g., "items.size()" → Expression for "items"
     * 
     * @param expr the expression to analyze
     * @return the collection expression if it's a .size() call, null otherwise
     */
    private Expression extractCollectionFromSizeCall(Expression expr) {
        if (!(expr instanceof MethodInvocation)) {
            return null;
        }
        
        MethodInvocation methodInv = (MethodInvocation) expr;
        String methodName = methodInv.getName().getIdentifier();
        
        if (!"size".equals(methodName)) {
            return null;
        }
        
        // Check that there are no arguments
        if (!methodInv.arguments().isEmpty()) {
            return null;
        }
        
        // Return the expression the method is called on
        return methodInv.getExpression();
    }
    
    /**
     * Derives an element variable name from the collection name.
     * E.g., "items" → "item", "list" → "element", "values" → "value"
     */
    private String deriveElementVarName(String collectionName) {
        // Simple heuristic: if collection name ends with 's', remove it
        if (collectionName.endsWith("s") && collectionName.length() > 1) {
            return collectionName.substring(0, collectionName.length() - 1);
        }
        
        // Otherwise use generic name
        return "element";
    }
    
    /**
     * Helper class to track index usage analysis results.
     */
    private static class IndexUsageAnalysis {
        final boolean indexEliminable;
        final Expression collectionExpr;
        final String elementVarName;
        
        IndexUsageAnalysis(boolean indexEliminable, Expression collectionExpr, String elementVarName) {
            this.indexEliminable = indexEliminable;
            this.collectionExpr = collectionExpr;
            this.elementVarName = elementVarName;
        }
        
        static IndexUsageAnalysis notEliminable() {
            return new IndexUsageAnalysis(false, null, null);
        }
    }
    
    /**
     * AST visitor that scans for uses of the loop index variable.
     * Checks if ALL uses are of the form collection.get(index).
     */
    private static class IndexUsageScanner extends ASTVisitor {
        private final String loopVarName;
        private final String collectionName;
        boolean allUsesAreCollectionGet = true;
        int usageCount = 0;
        
        IndexUsageScanner(String loopVarName, String collectionName) {
            this.loopVarName = loopVarName;
            this.collectionName = collectionName;
        }
        
        @Override
        public boolean visit(SimpleName node) {
            if (!node.getIdentifier().equals(loopVarName)) {
                return true;
            }
            
            usageCount++;
            
            // Check if this usage is inside a collection.get(i) call
            ASTNode parent = node.getParent();
            if (!(parent instanceof MethodInvocation)) {
                allUsesAreCollectionGet = false;
                return true;
            }
            
            MethodInvocation methodInv = (MethodInvocation) parent;
            
            // Check method name is "get"
            if (!"get".equals(methodInv.getName().getIdentifier())) {
                allUsesAreCollectionGet = false;
                return true;
            }
            
            // Check that the index is the first (and only) argument
            @SuppressWarnings("unchecked")
            List<Expression> args = methodInv.arguments();
            if (args.size() != 1 || args.get(0) != node) {
                allUsesAreCollectionGet = false;
                return true;
            }
            
            // Check that the method is called on the expected collection
            Expression methodExpr = methodInv.getExpression();
            if (methodExpr == null || !methodExpr.toString().equals(collectionName)) {
                allUsesAreCollectionGet = false;
                return true;
            }
            
            return true;
        }
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
        SourceDescriptor source;
        ElementDescriptor element;
        
        if (pattern.indexEliminable) {
            // Index can be eliminated - convert to collection.forEach()
            String collectionExpr = pattern.collectionExpr.toString();
            
            // Build COLLECTION source descriptor
            source = new SourceDescriptor(
                SourceDescriptor.SourceType.COLLECTION,
                collectionExpr,
                "Object" // Type will be inferred by ASTStreamRenderer
            );
            
            // Build element descriptor using the derived element variable name
            element = new ElementDescriptor(
                pattern.elementVarName,
                "Object", // Type will be inferred
                false
            );
        } else {
            // Standard IntStream.range() conversion
            // Get start and end expressions as strings
            String startStr = pattern.startExpr.toString();
            String endStr = pattern.endExpr.toString();
            
            // Adjust end expression for inclusive range (i <= end)
            if (pattern.inclusive) {
                // For i <= end, we need IntStream.range(start, end+1)
                // The expression parser in ASTStreamRenderer will handle "end+1" format
                endStr = endStr + " + 1";
            }
            
            // Build EXPLICIT_RANGE source descriptor
            String rangeExpression = startStr + "," + endStr;
            source = new SourceDescriptor(
                SourceDescriptor.SourceType.EXPLICIT_RANGE,
                rangeExpression,
                "int"
            );
            
            // Build element descriptor for the loop variable
            element = new ElementDescriptor(
                pattern.loopVarName,
                "int",
                false // not a collection element
            );
        }
        
        // Extract body statements - need to transform them if index is eliminable
        List<Statement> bodyStatements = extractAndTransformBodyStatements(pattern);
        
        // Convert statements to strings for ForEachTerminal
        List<String> bodyStmtStrings = new java.util.ArrayList<>();
        for (Statement stmt : bodyStatements) {
            bodyStmtStrings.add(stmt.toString());
        }
        
        // Build ForEachTerminal
        ForEachTerminal terminal = new ForEachTerminal(bodyStmtStrings, false); // ordered = false
        
        // Build and return LoopModel
        return LoopModel.builder()
            .source(source)
            .element(element)
            .terminal(terminal)
            .build();
    }
    
    /**
     * Extracts statements from the loop body.
     */
    @SuppressWarnings("unchecked")
    private List<Statement> extractBodyStatements(Statement body) {
        if (body instanceof Block) {
            Block block = (Block) body;
            return block.statements();
        } else {
            // Single statement body
            return List.of(body);
        }
    }
    
    /**
     * Extracts and transforms body statements.
     * If index is eliminable, removes the element variable declaration
     * (e.g., "String item = items.get(i);") since the forEach will provide the element directly.
     */
    @SuppressWarnings("unchecked")
    private List<Statement> extractAndTransformBodyStatements(ForLoopPattern pattern) {
        List<Statement> originalStatements = extractBodyStatements(pattern.body);
        
        if (!pattern.indexEliminable) {
            // No transformation needed
            return originalStatements;
        }
        
        // Transform statements: remove variable declarations that assign from collection.get(i)
        // The remaining statements will use the element variable name that forEach provides
        List<Statement> transformedStatements = new java.util.ArrayList<>();
        String collectionName = pattern.collectionExpr.toString();
        
        for (Statement stmt : originalStatements) {
            // Check if this is a variable declaration like: String item = items.get(i);
            if (stmt instanceof VariableDeclarationStatement) {
                VariableDeclarationStatement varDeclStmt = (VariableDeclarationStatement) stmt;
                List<VariableDeclarationFragment> fragments = varDeclStmt.fragments();
                
                if (fragments.size() == 1) {
                    VariableDeclarationFragment fragment = fragments.get(0);
                    Expression initializer = fragment.getInitializer();
                    
                    // Check if initializer is collection.get(i)
                    if (isCollectionGetCall(initializer, collectionName, pattern.loopVarName)) {
                        // Skip this declaration - the element variable from forEach replaces it
                        continue;
                    }
                }
            }
            
            // Keep the statement
            transformedStatements.add(stmt);
        }
        
        return transformedStatements;
    }
    
    /**
     * Checks if an expression is a collection.get(index) call.
     */
    private boolean isCollectionGetCall(Expression expr, String collectionName, String indexVar) {
        if (!(expr instanceof MethodInvocation)) {
            return false;
        }
        
        MethodInvocation methodInv = (MethodInvocation) expr;
        
        if (!"get".equals(methodInv.getName().getIdentifier())) {
            return false;
        }
        
        @SuppressWarnings("unchecked")
        List<Expression> args = methodInv.arguments();
        if (args.size() != 1) {
            return false;
        }
        
        Expression arg = args.get(0);
        if (!(arg instanceof SimpleName)) {
            return false;
        }
        
        SimpleName argName = (SimpleName) arg;
        if (!argName.getIdentifier().equals(indexVar)) {
            return false;
        }
        
        Expression methodExpr = methodInv.getExpression();
        if (methodExpr == null || !methodExpr.toString().equals(collectionName)) {
            return false;
        }
        
        return true;
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
        
        // Index elimination fields
        final boolean indexEliminable;      // true if index is only used for collection.get(i)
        final Expression collectionExpr;    // the collection being accessed (e.g., items)
        final String elementVarName;        // derived element variable name (e.g., item)
        
        ForLoopPattern(String loopVarName, Expression startExpr, Expression endExpr,
                      boolean inclusive, Statement body) {
            this(loopVarName, startExpr, endExpr, inclusive, body, false, null, null);
        }
        
        ForLoopPattern(String loopVarName, Expression startExpr, Expression endExpr,
                      boolean inclusive, Statement body,
                      boolean indexEliminable, Expression collectionExpr, String elementVarName) {
            this.loopVarName = loopVarName;
            this.startExpr = startExpr;
            this.endExpr = endExpr;
            this.inclusive = inclusive;
            this.body = body;
            this.indexEliminable = indexEliminable;
            this.collectionExpr = collectionExpr;
            this.elementVarName = elementVarName;
        }
    }
}
