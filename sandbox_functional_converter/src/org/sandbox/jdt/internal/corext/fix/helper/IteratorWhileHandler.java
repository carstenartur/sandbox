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
import org.sandbox.jdt.internal.corext.fix.helper.IteratorLoopAnalyzer.SafetyAnalysis;
import org.sandbox.jdt.internal.corext.fix.helper.IteratorLoopBodyParser.ParsedBody;
import org.sandbox.jdt.internal.corext.fix.helper.IteratorPatternDetector.IteratorPattern;

/**
 * Handler for converting iterator-based while-loops to functional stream operations.
 * 
 * <p>This handler processes:</p>
 * <ul>
 *   <li>while-iterator pattern: {@code Iterator<T> it = coll.iterator(); while (it.hasNext()) { T item = it.next(); ... }}</li>
 *   <li>for-loop-iterator pattern: {@code for (Iterator<T> it = coll.iterator(); it.hasNext(); ) { T item = it.next(); ... }}</li>
 * </ul>
 * 
 * <p>Uses the ULR pipeline: {@code LoopModelBuilder → LoopModel → LoopModelTransformer → ASTStreamRenderer}.</p>
 * 
 * <p><b>Naming Note:</b> This class is named after the <i>source</i> loop type (iterator while-loop),
 * not the target format. The architecture supports bidirectional transformations, so the name
 * describes what loop pattern this handler processes.</p>
 * 
 * @see LoopModel
 * @see ASTStreamRenderer
 * @see LoopModelTransformer
 */
public class IteratorWhileHandler extends AbstractFunctionalCall<ASTNode> {
    
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
                
                ReferenceHolder<ASTNode, Object> holder = ReferenceHolder.create();
                holder.put(node, pattern);
                
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
                
                ReferenceHolder<ASTNode, Object> holder = ReferenceHolder.create();
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
        
        AST ast = cuRewrite.getRoot().getAST();
        ASTRewrite rewrite = cuRewrite.getASTRewrite();
        
        // Build LoopModel using the ULR pipeline
        LoopModel model = buildLoopModel(pattern, parsedBody);
        
        // Create renderer with the original loop body for AST node access
        ASTStreamRenderer renderer = new ASTStreamRenderer(ast, rewrite, cuRewrite.getRoot(), pattern.loopBody);
        
        // Transform using LoopModelTransformer
        LoopModelTransformer<Expression> transformer = new LoopModelTransformer<>(renderer);
        Expression streamExpression = transformer.transform(model);
        
        if (streamExpression != null) {
            // For while pattern, also remove the iterator declaration
            if (node instanceof WhileStatement) {
                Block parentBlock = (Block) node.getParent();
                Statement previousStmt = IteratorPatternDetector.findPreviousStatement(parentBlock, (Statement) node);
                
                if (previousStmt != null) {
                    rewrite.remove(previousStmt, group);
                }
            }
            
            // Wrap in ExpressionStatement and replace the loop
            ExpressionStatement newStatement = ast.newExpressionStatement(streamExpression);
            rewrite.replace(node, newStatement, group);
        }
    }
    
    /**
     * Builds a LoopModel from the iterator pattern using the ULR pipeline.
     * Uses COLLECTION source type since the iterator comes from collection.iterator().
     */
    private LoopModel buildLoopModel(IteratorPattern pattern, ParsedBody parsedBody) {
        // Build COLLECTION source descriptor using the collection expression
        SourceDescriptor source = new SourceDescriptor(
            SourceDescriptor.SourceType.COLLECTION,
            pattern.collectionExpression.toString(),
            parsedBody.elementType
        );
        
        // Build element descriptor for the loop variable
        ElementDescriptor element = new ElementDescriptor(
            parsedBody.elementVariableName,
            parsedBody.elementType,
            true // is a collection element
        );
        
        // Extract body statements as expression strings (strip trailing semicolons)
        List<String> bodyStatements = extractBodyStatementsAsStrings(parsedBody.actualBodyStatements);
        
        // Build ForEachTerminal
        ForEachTerminal terminal = new ForEachTerminal(bodyStatements, false);
        
        // Build and return LoopModel
        return new LoopModelBuilder()
            .source(source)
            .element(element)
            .terminal(terminal)
            .build();
    }
    
    /**
     * Converts body statements to expression strings for the ForEachTerminal.
     * Trailing semicolons are stripped because ForEachTerminal / ASTStreamRenderer.createExpression()
     * expects pure expressions, not statements.
     */
    private List<String> extractBodyStatementsAsStrings(List<Statement> statements) {
        return ExpressionHelper.bodyStatementsToStrings(statements);
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
