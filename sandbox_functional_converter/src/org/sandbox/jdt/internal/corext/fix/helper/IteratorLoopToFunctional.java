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

import java.util.Set;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.IteratorLoopAnalyzer.SafetyAnalysis;
import org.sandbox.jdt.internal.corext.fix.helper.IteratorLoopBodyParser.ParsedBody;
import org.sandbox.jdt.internal.corext.fix.helper.IteratorPatternDetector.IteratorPattern;

/**
 * Converts iterator-based loops to functional stream operations.
 * 
 * <p>This converter handles:</p>
 * <ul>
 *   <li>while-iterator pattern: {@code Iterator<T> it = coll.iterator(); while (it.hasNext()) { T item = it.next(); ... }}</li>
 *   <li>for-loop-iterator pattern: {@code for (Iterator<T> it = coll.iterator(); it.hasNext(); ) { T item = it.next(); ... }}</li>
 * </ul>
 * 
 * <p>The conversion creates a synthetic EnhancedForStatement and delegates to the existing
 * LoopToFunctional infrastructure for the actual stream generation.</p>
 */
public class IteratorLoopToFunctional extends AbstractFunctionalCall<ASTNode> {
    
    private final IteratorPatternDetector patternDetector = new IteratorPatternDetector();
    private final IteratorLoopAnalyzer loopAnalyzer = new IteratorLoopAnalyzer();
    private final IteratorLoopBodyParser bodyParser = new IteratorLoopBodyParser();
    
    @Override
    public void find(UseFunctionalCallFixCore fixCore, CompilationUnit compilationUnit,
                     Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesProcessed) {
        
        compilationUnit.accept(new ASTVisitor() {
            
            @Override
            public boolean visit(WhileStatement node) {
                if (nodesProcessed.contains(node)) {
                    return false;
                }
                
                // Find previous statement for while-iterator pattern
                if (!IteratorPatternDetector.isStatementInBlock(node)) {
                    return true;
                }
                
                Block parentBlock = (Block) node.getParent();
                Statement previousStmt = IteratorPatternDetector.findPreviousStatement(parentBlock, node);
                
                IteratorPattern pattern = patternDetector.detectWhilePattern(node, previousStmt);
                if (pattern == null) {
                    return true;
                }
                
                if (!analyzeAndValidate(pattern)) {
                    return true;
                }
                
                // Mark both the iterator declaration and the while loop as processed
                nodesProcessed.add(previousStmt);
                nodesProcessed.add(node);
                
                ReferenceHolder<ASTNode, Object> holder = new ReferenceHolder<>();
                holder.put(node, pattern);
                holder.put(previousStmt, pattern); // Also store for the declaration
                
                operations.add(fixCore.rewrite(node, holder));
                
                return false;
            }
            
            @Override
            public boolean visit(ForStatement node) {
                if (nodesProcessed.contains(node)) {
                    return false;
                }
                
                IteratorPattern pattern = patternDetector.detectForLoopPattern(node);
                if (pattern == null) {
                    return true;
                }
                
                if (!analyzeAndValidate(pattern)) {
                    return true;
                }
                
                nodesProcessed.add(node);
                
                ReferenceHolder<ASTNode, Object> holder = new ReferenceHolder<>();
                holder.put(node, pattern);
                
                operations.add(fixCore.rewrite(node, holder));
                
                return false;
            }
        });
    }
    
    /**
     * Analyzes and validates that the pattern can be safely converted.
     */
    private boolean analyzeAndValidate(IteratorPattern pattern) {
        // Analyze for safety
        SafetyAnalysis analysis = loopAnalyzer.analyze(pattern.loopBody, pattern.iteratorVariableName);
        if (!analysis.isSafe) {
            // Cannot convert unsafe patterns
            return false;
        }
        
        // Parse body to ensure it has the expected structure
        ParsedBody parsedBody = bodyParser.parse(pattern.loopBody, pattern.iteratorVariableName);
        if (parsedBody == null) {
            // Body doesn't match expected pattern
            return false;
        }
        
        return true;
    }
    
    @Override
    public void rewrite(UseFunctionalCallFixCore fixCore, ASTNode node, 
                        CompilationUnitRewrite cuRewrite, TextEditGroup group,
                        ReferenceHolder<ASTNode, Object> holder) throws CoreException {
        
        Object data = holder.get(node);
        if (!(data instanceof IteratorPattern)) {
            return;
        }
        
        IteratorPattern pattern = (IteratorPattern) data;
        ParsedBody parsedBody = bodyParser.parse(pattern.loopBody, pattern.iteratorVariableName);
        
        if (parsedBody == null) {
            return;
        }
        
        AST ast = cuRewrite.getAST();
        ASTRewrite rewrite = cuRewrite.getASTRewrite();
        
        // Create a synthetic EnhancedForStatement for delegation
        EnhancedForStatement syntheticFor = createSyntheticEnhancedFor(ast, pattern, parsedBody);
        
        // Use the existing StreamPipelineBuilder/ASTStreamRenderer infrastructure
        // For now, just create a simple forEach conversion
        Expression streamPipeline = createStreamPipeline(ast, pattern, parsedBody);
        
        if (streamPipeline != null) {
            // Replace the original loop with the stream pipeline
            if (node instanceof WhileStatement) {
                // For while pattern, also remove the iterator declaration
                Block parentBlock = (Block) node.getParent();
                Statement previousStmt = IteratorPatternDetector.findPreviousStatement(parentBlock, (Statement) node);
                
                if (previousStmt != null) {
                    rewrite.remove(previousStmt, group);
                }
            }
            
            // Create an expression statement for the stream pipeline
            ExpressionStatement streamStmt = ast.newExpressionStatement(streamPipeline);
            rewrite.replace(node, streamStmt, group);
        }
    }
    
    /**
     * Creates a synthetic EnhancedForStatement for delegation to existing infrastructure.
     */
    private EnhancedForStatement createSyntheticEnhancedFor(AST ast, IteratorPattern pattern, 
                                                             ParsedBody parsedBody) {
        EnhancedForStatement enhancedFor = ast.newEnhancedForStatement();
        
        // Parameter: elementType elementVar
        SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
        param.setType(ast.newSimpleType(ast.newSimpleName(parsedBody.elementType)));
        param.setName(ast.newSimpleName(parsedBody.elementVariableName));
        enhancedFor.setParameter(param);
        
        // Expression: collection
        enhancedFor.setExpression((Expression) ASTNode.copySubtree(ast, pattern.collectionExpression));
        
        // Body: actual body statements
        Block syntheticBlock = bodyParser.createSyntheticBlock(ast, parsedBody);
        enhancedFor.setBody(syntheticBlock);
        
        return enhancedFor;
    }
    
    /**
     * Creates a stream pipeline expression.
     * This is a simplified version - full implementation would use StreamPipelineBuilder.
     */
    private Expression createStreamPipeline(AST ast, IteratorPattern pattern, ParsedBody parsedBody) {
        // collection.stream().forEach(item -> { ... })
        
        // collection.stream()
        MethodInvocation streamCall = ast.newMethodInvocation();
        streamCall.setExpression((Expression) ASTNode.copySubtree(ast, pattern.collectionExpression));
        streamCall.setName(ast.newSimpleName("stream"));
        
        // .forEach(item -> { ... })
        MethodInvocation forEachCall = ast.newMethodInvocation();
        forEachCall.setExpression(streamCall);
        forEachCall.setName(ast.newSimpleName("forEach"));
        
        // Lambda: item -> { ... }
        LambdaExpression lambda = ast.newLambdaExpression();
        VariableDeclarationFragment lambdaParam = ast.newVariableDeclarationFragment();
        lambdaParam.setName(ast.newSimpleName(parsedBody.elementVariableName));
        lambda.parameters().add(lambdaParam);
        lambda.setParentheses(false);
        
        // Lambda body
        if (parsedBody.actualBodyStatements.isEmpty()) {
            // Empty body - use empty block
            lambda.setBody(ast.newBlock());
        } else if (parsedBody.actualBodyStatements.size() == 1) {
            Statement stmt = parsedBody.actualBodyStatements.get(0);
            if (stmt instanceof ExpressionStatement) {
                // Single expression - use as lambda body
                ExpressionStatement exprStmt = (ExpressionStatement) stmt;
                lambda.setBody((Expression) ASTNode.copySubtree(ast, exprStmt.getExpression()));
            } else {
                // Single non-expression statement - wrap in block
                Block lambdaBlock = ast.newBlock();
                lambdaBlock.statements().add(ASTNode.copySubtree(ast, stmt));
                lambda.setBody(lambdaBlock);
            }
        } else {
            // Multiple statements - create block
            Block lambdaBlock = bodyParser.createSyntheticBlock(ast, parsedBody);
            lambda.setBody(lambdaBlock);
        }
        
        forEachCall.arguments().add(lambda);
        return forEachCall;
    }
    
    @Override
    public String getPreview(boolean afterRefactoring) {
        if (afterRefactoring) {
            return """
                   items.stream()
                       .forEach(item -> System.out.println(item));
                   """;
        } else {
            return """
                   Iterator<String> it = items.iterator();
                   while (it.hasNext()) {
                       String item = it.next();
                       System.out.println(item);
                   }
                   """;
        }
    }
}
