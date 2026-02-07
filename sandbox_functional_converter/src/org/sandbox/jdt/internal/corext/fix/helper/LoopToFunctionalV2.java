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
import org.sandbox.functional.core.model.LoopMetadata;
import org.sandbox.functional.core.model.LoopModel;
import org.sandbox.functional.core.model.SourceDescriptor;
import org.sandbox.functional.core.transformer.LoopModelTransformer;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore;

/**
 * V2 implementation using the Unified Loop Representation (ULR) architecture.
 * 
 * <p>This class uses the abstract LoopModel from the core module and
 * the ASTStreamRenderer to generate JDT AST nodes.</p>
 * 
 * @see LoopModel
 * @see ASTStreamRenderer
 * @see LoopModelTransformer
 */
public class LoopToFunctionalV2 extends AbstractFunctionalCall<EnhancedForStatement> {
    
    private final JdtLoopExtractor extractor = new JdtLoopExtractor();
    
    @Override
    public void find(UseFunctionalCallFixCore fixcore, CompilationUnit compilationUnit,
                     Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed) {
        
        ReferenceHolder<ASTNode, Object> dataHolder = ReferenceHolder.create();
        HelperVisitor.callEnhancedForStatementVisitor(compilationUnit, dataHolder, nodesprocessed,
                (visited, holder) -> processFoundNode(fixcore, operations, nodesprocessed, visited, holder),
                (visited, holder) -> {});
    }
    
    private boolean processFoundNode(UseFunctionalCallFixCore fixcore,
                                      Set<CompilationUnitRewriteOperation> operations,
                                      Set<ASTNode> nodesprocessed,
                                      EnhancedForStatement visited,
                                      ReferenceHolder<ASTNode, Object> holder) {
        
        // Extract LoopModel AND original body from AST
        JdtLoopExtractor.ExtractedLoop extracted = extractor.extract(visited);
        
        // Check if convertible using the model's metadata
        if (!isConvertible(extracted.model)) {
            return false;
        }
        
        // Store extracted loop (model + body) for later rewrite in the shared holder
        holder.put(visited, extracted);
        operations.add(fixcore.rewrite(visited, holder));
        nodesprocessed.add(visited);
        
        return false;
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
        
        Expression streamExpression;
        boolean usedDirectForEach = false;
        
        // Check if we can use direct forEach (no intermediate operations, ForEachTerminal)
        if (canUseDirectForEach(extracted.model)) {
            // Use direct forEach rendering (e.g., list.forEach(...) instead of list.stream().forEach(...))
            org.sandbox.functional.core.terminal.ForEachTerminal terminal = 
                (org.sandbox.functional.core.terminal.ForEachTerminal) extracted.model.getTerminal();
            String varName = extracted.model.getElement() != null 
                ? extracted.model.getElement().variableName() 
                : "x";
            streamExpression = renderer.renderDirectForEach(
                extracted.model.getSource(), 
                terminal.bodyStatements(), 
                varName, 
                terminal.ordered()
            );
            usedDirectForEach = true;
        } else {
            // Use standard stream-based transformation
            LoopModelTransformer<Expression> transformer = new LoopModelTransformer<>(renderer);
            streamExpression = transformer.transform(extracted.model);
        }
        
        if (streamExpression != null) {
            // Create the replacement statement
            ExpressionStatement newStatement = ast.newExpressionStatement(streamExpression);
            
            // Replace the for statement
            rewrite.replace(visited, newStatement, group);
            
            // Add necessary imports (only for stream-based transformations)
            if (!usedDirectForEach) {
                addImports(cuRewrite, extracted.model);
            } else {
                // For direct forEach on arrays, we still need Arrays import
                if (extracted.model.getSource().type() == SourceDescriptor.SourceType.ARRAY) {
                    cuRewrite.getImportRewrite().addImport("java.util.Arrays");
                }
            }
        }
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
        
        // Don't convert if has break, continue, or return
        return !metadata.hasBreak() && 
               !metadata.hasContinue() && 
               !metadata.hasReturn() &&
               !metadata.modifiesCollection();
    }
    
    private void addImports(CompilationUnitRewrite cuRewrite, LoopModel model) {
        // Add necessary imports based on source type
        switch (model.getSource().type()) {
            case ARRAY:
                cuRewrite.getImportRewrite().addImport("java.util.Arrays");
                break;
            case ITERABLE:
                cuRewrite.getImportRewrite().addImport("java.util.stream.StreamSupport");
                break;
            default:
                // No additional imports needed for Collection.stream()
                break;
        }
        
        // Add Collectors import if using collect terminal
        if (model.getTerminal() instanceof org.sandbox.functional.core.terminal.CollectTerminal) {
            cuRewrite.getImportRewrite().addImport("java.util.stream.Collectors");
        }
    }
    
    @Override
    public String getPreview(boolean afterRefactoring) {
        if (afterRefactoring) {
            return "items.forEach(item -> System.out.println(item));\n"; //$NON-NLS-1$
        }
        return "for (String item : items)\n    System.out.println(item);\n"; //$NON-NLS-1$
    }
}
