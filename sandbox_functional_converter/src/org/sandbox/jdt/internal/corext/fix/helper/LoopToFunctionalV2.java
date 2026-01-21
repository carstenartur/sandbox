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
        
        ReferenceHolder<ASTNode, Object> dataHolder = new ReferenceHolder<>();
        HelperVisitor.callEnhancedForStatementVisitor(compilationUnit, dataHolder, nodesprocessed,
                (visited, holder) -> processFoundNode(fixcore, operations, nodesprocessed, visited, holder, dataHolder),
                (visited, holder) -> {});
    }
    
    private boolean processFoundNode(UseFunctionalCallFixCore fixcore,
                                      Set<CompilationUnitRewriteOperation> operations,
                                      Set<ASTNode> nodesprocessed,
                                      EnhancedForStatement visited,
                                      ReferenceHolder<ASTNode, LoopModel> holder,
                                      ReferenceHolder<ASTNode, Object> dataHolder) {
        
        // Extract LoopModel from AST
        LoopModel model = extractor.extract(visited);
        
        // Check if convertible using the model's metadata
        if (!isConvertible(model)) {
            return false;
        }
        
        // Store for later rewrite
        holder.put(visited, model);
        dataHolder.put(visited, model);
        operations.add(fixcore.rewrite(visited, dataHolder));
        nodesprocessed.add(visited);
        
        return false;
    }
    
    @Override
    public void rewrite(UseFunctionalCallFixCore upp, EnhancedForStatement visited,
                        CompilationUnitRewrite cuRewrite, TextEditGroup group,
                        ReferenceHolder<ASTNode, Object> data) throws CoreException {
        
        // Get the model from the holder (passed from find())
        LoopModel model = (LoopModel) data.get(visited);
        
        if (model == null || !isConvertible(model)) {
            return;
        }
        
        AST ast = cuRewrite.getRoot().getAST();
        ASTRewrite rewrite = cuRewrite.getASTRewrite();
        
        // Create renderer and transformer
        ASTStreamRenderer renderer = new ASTStreamRenderer(ast, rewrite);
        LoopModelTransformer<Expression> transformer = new LoopModelTransformer<>(renderer);
        
        // Transform the model to JDT Expression
        Expression streamExpression = transformer.transform(model);
        
        if (streamExpression != null) {
            // Create the replacement statement
            ExpressionStatement newStatement = ast.newExpressionStatement(streamExpression);
            
            // Replace the for statement
            rewrite.replace(visited, newStatement, group);
            
            // Add necessary imports
            addImports(cuRewrite, model);
        }
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
            return "items.stream().forEach(item -> System.out.println(item));\n"; //$NON-NLS-1$
        }
        return "for (String item : items)\n    System.out.println(item);\n"; //$NON-NLS-1$
    }
}
