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
import org.sandbox.functional.core.model.LoopMetadata;
import org.sandbox.functional.core.model.LoopModel;
import org.sandbox.functional.core.model.SourceDescriptor;
import org.sandbox.functional.core.terminal.CollectTerminal;
import org.sandbox.functional.core.terminal.ForEachTerminal;
import org.sandbox.functional.core.terminal.ReduceTerminal;
import org.sandbox.functional.core.transformer.LoopModelTransformer;
import org.sandbox.functional.core.tree.ConversionDecision;
import org.sandbox.functional.core.tree.LoopKind;
import org.sandbox.functional.core.tree.LoopTree;
import org.sandbox.functional.core.tree.LoopTreeNode;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.ConsecutiveLoopGroupDetector.ConsecutiveLoopGroup;

/**
 * Handler for converting enhanced for-loops to functional stream operations.
 * 
 * <p>This handler processes enhanced for-loops (for-each loops) using the Unified Loop Representation (ULR) pipeline:
 * {@code JdtLoopExtractor → LoopModel → LoopModelTransformer → ASTStreamRenderer}.</p>
 * 
 * <p>The {@link JdtLoopExtractor} bridges JDT AST nodes to the abstract {@link LoopModel},
 * detecting all supported patterns (filter, map, collect, reduce, match, forEach).
 * The {@link LoopModelTransformer} then drives the {@link ASTStreamRenderer} to produce
 * JDT AST nodes for the stream pipeline.</p>
 * 
 * <p>The core transformation logic (pattern detection, model building) is testable
 * without OSGi via {@code sandbox-functional-converter-core}.</p>
 * 
 * @see LoopModel
 * @see JdtLoopExtractor
 * @see ASTStreamRenderer
 * @see LoopModelTransformer
 */
public class EnhancedForHandler extends AbstractFunctionalCall<EnhancedForStatement> {
    
    private final JdtLoopExtractor extractor = new JdtLoopExtractor();
    
    @Override
    public void find(UseFunctionalCallFixCore fixcore, CompilationUnit compilationUnit,
                     Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed) {
        
        // PHASE 8: Pre-process to detect consecutive loops adding to same collection
        // This must happen before individual loop processing to avoid incorrect overwrites
        detectAndProcessConsecutiveLoops(fixcore, compilationUnit, operations, nodesprocessed);
        
        // PHASE 9: Use LoopTree for nested loop analysis
        // Continue with individual loop processing for non-grouped loops using LoopTree
        ReferenceHolder<String, Object> treeHolder = ReferenceHolder.create();
        
        // Initialize the LoopTree in the shared holder
        LoopTree tree = new LoopTree();
        treeHolder.put("tree", tree);
        
        // Use BiPredicate (visit) and BiConsumer (endVisit) for tree-based analysis
        ReferenceHolder<ASTNode, Object> dataHolder = ReferenceHolder.create();
        HelperVisitorFactory.callEnhancedForStatementVisitor(compilationUnit, dataHolder, nodesprocessed,
                // Visit (BiPredicate): pushLoop and continue traversal
                (visited, holder) -> visitLoop(visited, treeHolder, nodesprocessed, holder),
                // EndVisit (BiConsumer): popLoop and make conversion decision
                (visited, holder) -> endVisitLoop(visited, treeHolder, compilationUnit));
        
        // After traversal, collect convertible nodes and add operations
        List<LoopTreeNode> convertibleNodes = tree.getConvertibleNodes();
        for (LoopTreeNode node : convertibleNodes) {
            EnhancedForStatement loopStatement = (EnhancedForStatement) node.getAstNodeReference();
            if (loopStatement != null && !nodesprocessed.contains(loopStatement)) {
                // Reuse cached ExtractedLoop from endVisitLoop to avoid duplicate extraction
                JdtLoopExtractor.ExtractedLoop extracted = 
                    (JdtLoopExtractor.ExtractedLoop) treeHolder.get("extracted_" + System.identityHashCode(loopStatement));
                
                // Fallback: if not cached (shouldn't happen), extract now
                if (extracted == null) {
                    extracted = extractor.extract(loopStatement);
                }
                
                // Store extracted loop (model + body) for later rewrite
                dataHolder.put(loopStatement, extracted);
                operations.add(fixcore.rewrite(loopStatement, dataHolder));
                nodesprocessed.add(loopStatement);
            }
        }
    }
    
    /**
     * Detects and processes consecutive loops that add to the same collection.
     * 
     * <p>Phase 8 feature: Multiple consecutive for-loops adding to the same list
     * are converted to Stream.concat() instead of being converted individually
     * (which would cause overwrites).</p>
     * 
     * @param fixcore the fix core instance
     * @param compilationUnit the compilation unit to scan
     * @param operations the set to add operations to
     * @param nodesprocessed the set of already processed nodes
     */
    private void detectAndProcessConsecutiveLoops(UseFunctionalCallFixCore fixcore, 
            CompilationUnit compilationUnit,
            Set<CompilationUnitRewriteOperation> operations, 
            Set<ASTNode> nodesprocessed) {
        
        // Visit all blocks to find consecutive loop groups
        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(Block block) {
                List<ConsecutiveLoopGroup> groups = ConsecutiveLoopGroupDetector.detectGroups(block);
                
                for (ConsecutiveLoopGroup group : groups) {
                    // Create a rewrite operation for this group
                    operations.add(fixcore.rewriteConsecutiveLoops(group));
                    
                    // Mark all loops in the group as processed to prevent individual conversion
                    for (EnhancedForStatement loop : group.getLoops()) {
                        nodesprocessed.add(loop);
                    }
                }
                
                return true; // Continue visiting nested blocks
            }
        });
    }

    /**
     * Visit handler for entering a loop node.
     * 
     * <p>PHASE 9: This method is called when visiting an EnhancedForStatement.
     * It pushes a new node onto the LoopTree and sets the AST reference.</p>
     * 
     * @param visited the EnhancedForStatement being visited
     * @param treeHolder the holder containing the LoopTree
     * @param nodesprocessed the set of already processed nodes
     * @param holder the data holder (unused here but required by BiPredicate signature)
     * @return true to continue visiting children, false to skip
     */
    private boolean visitLoop(EnhancedForStatement visited, 
            ReferenceHolder<String, Object> treeHolder,
            Set<ASTNode> nodesprocessed,
            ReferenceHolder<ASTNode, Object> holder) {
        // Skip loops that have already been processed (e.g., as part of a consecutive loop group)
        if (nodesprocessed.contains(visited)) {
            return false; // Don't visit children of already-processed loops
        }
        
        // Get the LoopTree from the holder
        LoopTree tree = (LoopTree) treeHolder.get("tree");
        if (tree == null) {
            return false;
        }
        
        // Push a new loop node onto the tree
        LoopTreeNode node = tree.pushLoop(LoopKind.ENHANCED_FOR);
        
        // Set the AST node reference for later rewriting
        node.setAstNodeReference(visited);
        
        // Populate ScopeInfo by scanning the loop body 
        LoopBodyScopeScanner scanner = new LoopBodyScopeScanner(visited);
        scanner.scan();
        scanner.populateScopeInfo(node.getScopeInfo());
        
        // Store the scanner for access in endVisitLoop (to check referenced variables)
        treeHolder.put("scanner_" + System.identityHashCode(visited), scanner);
        
        // Continue visiting children (nested loops)
        return true;
    }
    
    /**
     * EndVisit handler for exiting a loop node.
     * 
     * <p>PHASE 9: This method is called when exiting an EnhancedForStatement.
     * It pops the node from the tree and makes a conversion decision based on
     * preconditions and whether any descendant loops are convertible.</p>
     * 
     * <p>The conversion decision uses ULR-based convertibility checks instead of
     * PreconditionsChecker since this implementation uses the LoopModel for analysis.</p>
     * 
     * @param visited the EnhancedForStatement being exited
     * @param treeHolder the holder containing the LoopTree
     * @param compilationUnit the compilation unit for analysis
     */
    private void endVisitLoop(EnhancedForStatement visited,
            ReferenceHolder<String, Object> treeHolder,
            CompilationUnit compilationUnit) {
        // Get the LoopTree from the holder
        LoopTree tree = (LoopTree) treeHolder.get("tree");
        if (tree == null || !tree.isInsideLoop()) {
            return;
        }
        
        // Verify this is the correct node to pop (guard against stack corruption)
        LoopTreeNode currentNode = tree.current();
        if (currentNode == null || currentNode.getAstNodeReference() != visited) {
            return; // Stack mismatch - visitLoop must have returned false, so no pushLoop occurred
        }
        
        // Pop the current loop node
        LoopTreeNode node = tree.popLoop();
        
        // Make conversion decision based on bottom-up analysis
        // If any descendant is convertible, skip this loop
        if (node.hasConvertibleDescendant()) {
            node.setDecision(ConversionDecision.SKIPPED_INNER_CONVERTED);
            return;
        }
        
        // Check ScopeInfo: if this loop references variables that are modified
        // in an ANCESTOR loop's scope, it cannot be converted (lambda capture requires
        // effectively final variables).
        LoopBodyScopeScanner scanner = (LoopBodyScopeScanner) treeHolder.get("scanner_" + System.identityHashCode(visited));
        if (scanner != null && node.getParent() != null) {
            // Walk up the tree and check if any referenced variable is modified in ancestor scopes
            LoopTreeNode parent = node.getParent();
            while (parent != null) {
                for (String referencedVar : scanner.getReferencedVariables()) {
                    if (parent.getScopeInfo().getModifiedVariables().contains(referencedVar)) {
                        node.setDecision(ConversionDecision.NOT_CONVERTIBLE);
                        return;
                    }
                }
                parent = parent.getParent();
            }
        }
        
        // Extract ULR LoopModel — this now does full body analysis (filter, map, collect, etc.)
        JdtLoopExtractor.ExtractedLoop extracted = extractor.extract(visited, compilationUnit);
        
        // Use LoopModel-based convertibility check
        if (!isConvertible(extracted.model)) {
            node.setDecision(ConversionDecision.NOT_CONVERTIBLE);
            return;
        }
        
        // Verify the model has a terminal (i.e., the body was analyzed successfully)
        if (extracted.model.getTerminal() == null) {
            node.setDecision(ConversionDecision.NOT_CONVERTIBLE);
            return;
        }
        
        // Cache the extracted loop so that later phases (e.g., rewrite construction)
        // can reuse it without re-running the extraction on the same AST node.
        treeHolder.put("extracted_" + System.identityHashCode(visited), extracted);
        
        // Loop is convertible
        node.setDecision(ConversionDecision.CONVERTIBLE);
    }
    
    @Override
    public void rewrite(UseFunctionalCallFixCore upp, EnhancedForStatement visited,
                        CompilationUnitRewrite cuRewrite, TextEditGroup group,
                        ReferenceHolder<ASTNode, Object> data) throws CoreException {
        
        // Get the extracted loop from the holder (passed from find())
        JdtLoopExtractor.ExtractedLoop extracted = (JdtLoopExtractor.ExtractedLoop) data.get(visited);
        
        if (extracted == null || !isConvertible(extracted.model)) {
            return;
        }
        
        AST ast = cuRewrite.getRoot().getAST();
        ASTRewrite rewrite = cuRewrite.getASTRewrite();
        CompilationUnit compilationUnit = cuRewrite.getRoot();
        
        // Create renderer with original body for AST node access
        ASTStreamRenderer renderer = new ASTStreamRenderer(ast, rewrite, compilationUnit, extracted.originalBody);
        
        // Check if we can use direct forEach (no operations, collection source)
        if (canUseDirectForEach(extracted.model)) {
            ForEachTerminal terminal = (ForEachTerminal) extracted.model.getTerminal();
            String varName = extracted.model.getElement() != null 
                ? extracted.model.getElement().variableName() 
                : "x";
            Expression streamExpression = renderer.renderDirectForEach(
                extracted.model.getSource(), 
                terminal.bodyStatements(), 
                varName, 
                terminal.ordered()
            );
            
            if (streamExpression != null) {
                ExpressionStatement newStatement = ast.newExpressionStatement(streamExpression);
                rewrite.replace(visited, newStatement, group);
                
                if (extracted.model.getSource().type() == SourceDescriptor.SourceType.ARRAY) {
                    cuRewrite.getImportRewrite().addImport("java.util.Arrays");
                }
                return;
            }
        }
        
        // Use LoopModelTransformer for ALL patterns (filter, map, collect, reduce, match, forEach with ops)
        LoopModelTransformer<Expression> transformer = new LoopModelTransformer<>(renderer);
        Expression streamExpression = transformer.transform(extracted.model);
        
        if (streamExpression != null) {
            // Wrap the stream expression appropriately based on terminal type
            Statement replacement = createReplacement(ast, streamExpression, extracted.model, visited);
            if (replacement != null) {
                // For COLLECT: try to merge with preceding empty collection declaration
                if (extracted.model.getTerminal() instanceof CollectTerminal collectTerminal) {
                    Statement merged = tryMergeWithPrecedingDeclaration(
                        ast, rewrite, group, visited, streamExpression, collectTerminal.targetVariable());
                    if (merged != null) {
                        replacement = merged;
                    }
                }
                
                rewrite.replace(visited, replacement, group);
                addRequiredImports(cuRewrite, extracted.model);
            }
        }
    }
    
    /**
     * Creates the replacement statement based on the terminal type.
     * 
     * <p>For most terminals (forEach, collect), the stream expression is wrapped in an
     * ExpressionStatement. For collect/reduce, we may need to create an assignment
     * to the original target variable.</p>
     */
    private Statement createReplacement(AST ast, Expression streamExpression, LoopModel model,
                                         EnhancedForStatement originalLoop) {
        if (model.getTerminal() instanceof CollectTerminal collectTerminal) {
            // For collect: targetVar = stream.collect(...)
            String targetVar = collectTerminal.targetVariable();
            if (targetVar != null && !targetVar.isEmpty()) {
                // Create: targetVar = streamExpression;
                Assignment assignment = ast.newAssignment();
                assignment.setLeftHandSide(ast.newSimpleName(targetVar));
                assignment.setOperator(Assignment.Operator.ASSIGN);
                assignment.setRightHandSide(streamExpression);
                return ast.newExpressionStatement(assignment);
            }
        }
        
        if (model.getTerminal() instanceof ReduceTerminal reduceTerminal) {
            // For reduce: accumVar = stream.reduce(...)
            String targetVar = reduceTerminal.targetVariable();
            if (targetVar != null && !targetVar.isEmpty()) {
                Assignment assignment = ast.newAssignment();
                assignment.setLeftHandSide(ast.newSimpleName(targetVar));
                assignment.setOperator(Assignment.Operator.ASSIGN);
                assignment.setRightHandSide(streamExpression);
                return ast.newExpressionStatement(assignment);
            }
            return ast.newExpressionStatement(streamExpression);
        }
        
        if (model.getTerminal() instanceof org.sandbox.functional.core.terminal.MatchTerminal matchTerminal) {
            // For match: wrap in if-statement matching original behavior
            // anyMatch: if (stream.anyMatch(...)) { return true; }
            // noneMatch: if (!stream.noneMatch(...)) { return false; }
            // allMatch: if (!stream.allMatch(...)) { return false; }
            return createMatchIfStatement(ast, streamExpression, matchTerminal);
        }
        
        // Default: wrap in ExpressionStatement
        return ast.newExpressionStatement(streamExpression);
    }
    
    /**
     * Creates an IfStatement wrapping a match expression, matching standard behavior.
     * 
     * <p>For anyMatch: {@code if (stream.anyMatch(...)) { return true; }}</p>
     * <p>For noneMatch: {@code if (!stream.noneMatch(...)) { return false; }}</p>
     * <p>For allMatch: {@code if (!stream.allMatch(...)) { return false; }}</p>
     */
    @SuppressWarnings("unchecked")
    private Statement createMatchIfStatement(AST ast, Expression streamExpression,
                                              org.sandbox.functional.core.terminal.MatchTerminal matchTerminal) {
        IfStatement ifStmt = ast.newIfStatement();
        
        org.sandbox.functional.core.terminal.MatchTerminal.MatchType matchType = matchTerminal.matchType();
        
        if (matchType == org.sandbox.functional.core.terminal.MatchTerminal.MatchType.ANY_MATCH) {
            // if (stream.anyMatch(...)) { return true; }
            ifStmt.setExpression(streamExpression);
            Block thenBlock = ast.newBlock();
            ReturnStatement returnStmt = ast.newReturnStatement();
            returnStmt.setExpression(ast.newBooleanLiteral(true));
            thenBlock.statements().add(returnStmt);
            ifStmt.setThenStatement(thenBlock);
        } else {
            // noneMatch/allMatch: if (!stream.noneMatch/allMatch(...)) { return false; }
            PrefixExpression negation = ast.newPrefixExpression();
            negation.setOperator(PrefixExpression.Operator.NOT);
            negation.setOperand(streamExpression);
            ifStmt.setExpression(negation);
            Block thenBlock = ast.newBlock();
            ReturnStatement returnStmt = ast.newReturnStatement();
            returnStmt.setExpression(ast.newBooleanLiteral(false));
            thenBlock.statements().add(returnStmt);
            ifStmt.setThenStatement(thenBlock);
        }
        
        return ifStmt;
    }
    
    /**
     * Adds required imports based on the LoopModel operations and terminals.
     */
    private void addRequiredImports(CompilationUnitRewrite cuRewrite, LoopModel model) {
        // Source-based imports
        switch (model.getSource().type()) {
            case ARRAY:
                cuRewrite.getImportRewrite().addImport("java.util.Arrays");
                break;
            case ITERABLE:
                cuRewrite.getImportRewrite().addImport("java.util.stream.StreamSupport");
                break;
            default:
                break;
        }
        
        // Terminal-based imports
        if (model.getTerminal() instanceof CollectTerminal) {
            cuRewrite.getImportRewrite().addImport("java.util.stream.Collectors");
        }
    }
    
    /**
     * Attempts to merge a COLLECT operation with its preceding empty collection declaration.
     * 
     * <p>V1 Pattern: If the preceding statement is an empty collection initialization
     * (e.g., {@code List<X> result = new ArrayList<>()}), merge it with the stream
     * collect to produce {@code List<X> result = stream.collect(Collectors.toList())}.</p>
     * 
     * @return merged VariableDeclarationStatement, or null if merge not possible
     */
    @SuppressWarnings("unchecked")
    private Statement tryMergeWithPrecedingDeclaration(AST ast, ASTRewrite rewrite, TextEditGroup group,
                                                        EnhancedForStatement forLoop, 
                                                        Expression streamExpression, 
                                                        String targetVariable) {
        if (targetVariable == null || targetVariable.isEmpty()) {
            return null;
        }
        
        // Get the parent block
        ASTNode parent = forLoop.getParent();
        if (!(parent instanceof Block block)) {
            return null;
        }
        
        java.util.List<Statement> statements = block.statements();
        
        // Find the index of the for-loop in the parent block
        int forLoopIndex = -1;
        for (int i = 0; i < statements.size(); i++) {
            if (statements.get(i) == forLoop) {
                forLoopIndex = i;
                break;
            }
        }
        
        if (forLoopIndex <= 0) {
            return null;
        }
        
        Statement precedingStmt = statements.get(forLoopIndex - 1);
        
        // Check if preceding statement is an empty collection declaration for the same variable
        String declaredVar = CollectPatternDetector.isEmptyCollectionDeclaration(precedingStmt);
        if (declaredVar == null || !declaredVar.equals(targetVariable)) {
            return null;
        }
        
        // Merge: create List<X> result = stream.collect(...)
        VariableDeclarationStatement originalDecl = (VariableDeclarationStatement) precedingStmt;
        
        VariableDeclarationFragment newFragment = ast.newVariableDeclarationFragment();
        newFragment.setName(ast.newSimpleName(targetVariable));
        newFragment.setInitializer((Expression) ASTNode.copySubtree(ast, streamExpression));
        
        VariableDeclarationStatement newDecl = ast.newVariableDeclarationStatement(newFragment);
        newDecl.setType((Type) ASTNode.copySubtree(ast, originalDecl.getType()));
        newDecl.modifiers().addAll(ASTNode.copySubtrees(ast, originalDecl.modifiers()));
        
        // Remove the preceding empty declaration
        rewrite.remove(precedingStmt, group);
        
        return newDecl;
    }
    
    /**
     * Checks if the loop model can use direct forEach (without .stream() prefix).
     * 
     * <p>Direct forEach is used for the simplest forEach patterns to generate more idiomatic code:</p>
     * <ul>
     *   <li>No intermediate operations (no filter, map, etc.)</li>
     *   <li>Terminal operation is ForEachTerminal</li>
     *   <li>Source is COLLECTION or ITERABLE (arrays need Arrays.stream().forEach())</li>
     * </ul>
     * 
     * <p><b>Immutability Considerations:</b></p>
     * <p>Direct forEach is safe for both mutable and immutable collections:
     * <ul>
     *   <li>Immutable collections (List.of, Collections.unmodifiableList, etc.) support forEach</li>
     *   <li>forEach is a terminal operation that only reads elements</li>
     *   <li>No structural modifications are made to the collection</li>
     *   <li>Side effects within the lambda body are the user's responsibility</li>
     * </ul>
     * </p>
     * 
     * @param model the loop model to check
     * @return true if direct forEach can be used
     */
    private boolean canUseDirectForEach(LoopModel model) {
        if (model == null || model.getTerminal() == null) {
            return false;
        }
        
        // Must have no intermediate operations
        if (!model.getOperations().isEmpty()) {
            return false;
        }
        
        // Terminal must be ForEachTerminal
        if (!(model.getTerminal() instanceof org.sandbox.functional.core.terminal.ForEachTerminal)) {
            return false;
        }
        
        // Source must be COLLECTION or ITERABLE
        // Arrays don't have a forEach method, so they still need a stream-based forEach path
        // and are intentionally handled outside of this direct-forEach optimization.
        SourceDescriptor.SourceType sourceType = model.getSource().type();
        return sourceType == SourceDescriptor.SourceType.COLLECTION 
            || sourceType == SourceDescriptor.SourceType.ITERABLE;
    }
    
    private boolean isConvertible(LoopModel model) {
        if (model == null) return false;
        
        LoopMetadata metadata = model.getMetadata();
        if (metadata == null) return true; // No metadata = assume convertible
        
        // Only reject truly unconvertible patterns:
        // - break: cannot be expressed in stream operations
        // - labeled continue (stored in hasContinue): cannot be expressed in stream operations
        // Note: unlabeled continue → filter, return → match, add() → collect
        // are all convertible and handled by JdtLoopExtractor.analyzeAndAddOperations()
        return !metadata.hasBreak() && !metadata.hasContinue();
    }
    
    @Override
    public String getPreview(boolean afterRefactoring) {
        if (afterRefactoring) {
            return "items.forEach(item -> System.out.println(item));\n"; //$NON-NLS-1$
        }
        return "for (String item : items)\n    System.out.println(item);\n"; //$NON-NLS-1$
    }
}
