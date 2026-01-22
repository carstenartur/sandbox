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

import org.eclipse.jdt.core.dom.*;
import org.sandbox.functional.core.builder.LoopModelBuilder;
import org.sandbox.functional.core.model.*;
import org.sandbox.functional.core.model.SourceDescriptor.SourceType;

/**
 * Extracts a LoopModel from JDT AST nodes.
 * This bridges the JDT world with the abstract ULR model.
 */
public class JdtLoopExtractor {
    
    /**
     * Extracts a LoopModel from an EnhancedForStatement.
     */
    public LoopModel extract(EnhancedForStatement forStatement) {
        Expression iterable = forStatement.getExpression();
        SingleVariableDeclaration parameter = forStatement.getParameter();
        Statement body = forStatement.getBody();
        
        // Determine source type
        SourceType sourceType = determineSourceType(iterable);
        String sourceExpression = iterable.toString();
        String elementType = parameter.getType().toString();
        
        // Element info
        String varName = parameter.getName().getIdentifier();
        boolean isFinal = Modifier.isFinal(parameter.getModifiers());
        
        // Analyze metadata
        LoopBodyAnalyzer analyzer = new LoopBodyAnalyzer();
        body.accept(analyzer);
        
        // Build model
        LoopModelBuilder builder = new LoopModelBuilder()
            .source(sourceType, sourceExpression, elementType)
            .element(varName, elementType, isFinal)
            .metadata(analyzer.hasBreak(), analyzer.hasContinue(), 
                      analyzer.hasReturn(), analyzer.modifiesCollection(), true);
        
        // Analyze body and add operations/terminal
        analyzeAndAddOperations(body, builder, varName);
        
        return builder.build();
    }
    
    private SourceType determineSourceType(Expression iterable) {
        ITypeBinding binding = iterable.resolveTypeBinding();
        if (binding != null) {
            if (binding.isArray()) {
                return SourceType.ARRAY;
            }
            // Check for Collection
            if (isCollection(binding)) {
                return SourceType.COLLECTION;
            }
        }
        return SourceType.ITERABLE;
    }
    
    private boolean isCollection(ITypeBinding binding) {
        if (binding == null) {
            return false;
        }
        
        ITypeBinding erasure = binding.getErasure();
        if (erasure == null) {
            return false;
        }
        
        String qualifiedName = erasure.getQualifiedName();
        if ("java.util.Collection".equals(qualifiedName)
                || "java.util.List".equals(qualifiedName)
                || "java.util.Set".equals(qualifiedName)
                || "java.util.Queue".equals(qualifiedName)
                || "java.util.Deque".equals(qualifiedName)) {
            return true;
        }
        
        // Check interfaces
        for (ITypeBinding iface : erasure.getInterfaces()) {
            if (isCollection(iface)) {
                return true;
            }
        }
        
        // Check superclass
        ITypeBinding superclass = erasure.getSuperclass();
        if (superclass != null && isCollection(superclass)) {
            return true;
        }
        
        return false;
    }
    
    private void analyzeAndAddOperations(Statement body, LoopModelBuilder builder, String varName) {
        // For now, treat the entire body as a forEach terminal
        // More sophisticated analysis will be added later
        
        java.util.List<String> bodyStatements = new java.util.ArrayList<>();
        
        if (body instanceof Block) {
            Block block = (Block) body;
            for (Object stmt : block.statements()) {
                String stmtStr = stmt.toString();
                // Strip trailing semicolon for expression statements
                // This allows the renderer to use them as lambda expressions
                if (stmtStr.endsWith(";")) {
                    stmtStr = stmtStr.substring(0, stmtStr.length() - 1).trim();
                }
                bodyStatements.add(stmtStr);
            }
        } else {
            String stmtStr = body.toString();
            // Strip trailing semicolon for expression statements
            if (stmtStr.endsWith(";")) {
                stmtStr = stmtStr.substring(0, stmtStr.length() - 1).trim();
            }
            bodyStatements.add(stmtStr);
        }
        
        builder.forEach(bodyStatements, false); // unordered for simple enhanced for
    }
    
    /**
     * Visitor to analyze loop body for control flow.
     */
    private static class LoopBodyAnalyzer extends ASTVisitor {
        private boolean hasBreak = false;
        private boolean hasContinue = false;
        private boolean hasReturn = false;
        private boolean modifiesCollection = false;
        
        @Override
        public boolean visit(BreakStatement node) {
            hasBreak = true;
            return false;
        }
        
        @Override
        public boolean visit(ContinueStatement node) {
            hasContinue = true;
            return false;
        }
        
        @Override
        public boolean visit(ReturnStatement node) {
            hasReturn = true;
            return false;
        }
        
        @Override
        public boolean visit(MethodInvocation node) {
            String name = node.getName().getIdentifier();
            if ("add".equals(name) || "remove".equals(name) || 
                "clear".equals(name) || "set".equals(name)) {
                modifiesCollection = true;
            }
            return true;
        }
        
        public boolean hasBreak() { return hasBreak; }
        public boolean hasContinue() { return hasContinue; }
        public boolean hasReturn() { return hasReturn; }
        public boolean modifiesCollection() { return modifiesCollection; }
    }
}
