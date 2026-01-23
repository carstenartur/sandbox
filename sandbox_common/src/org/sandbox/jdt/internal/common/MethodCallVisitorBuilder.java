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
package org.sandbox.jdt.internal.common;

import java.util.Set;
import java.util.function.BiPredicate;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.MethodInvocation;

/**
 * Fluent builder for visiting method invocations.
 * 
 * <p><b>Example Usage:</b></p>
 * <pre>
 * // Single method
 * HelperVisitor.forMethodCall("org.junit.Assert", "assertTrue")
 *     .in(compilationUnit)
 *     .excluding(nodesprocessed)
 *     .processEach((methodInv, holder) -&gt; {
 *         addOperation(methodInv);
 *         return true;
 *     });
 *     
 * // Multiple methods with imports
 * HelperVisitor.forMethodCalls("org.junit.Assert", ALL_ASSERTION_METHODS)
 *     .andStaticImports()
 *     .andImportsOf("org.junit.Assert")
 *     .in(compilationUnit)
 *     .excluding(nodesprocessed)
 *     .processEach((node, holder) -&gt; {
 *         addOperation(node);
 *         return true;
 *     });
 * </pre>
 * 
 * <p><b>Note on mixed node types:</b> When {@code andStaticImports()} or {@code andImportsOf(...)} 
 * are enabled, the processor will receive both {@code MethodInvocation} and {@code ImportDeclaration} 
 * nodes. This is intentional to match the pattern used in JUnit cleanup plugins (e.g., AssertJUnitPlugin) 
 * where a single processor handles all related nodes polymorphically. Use {@code instanceof} checks 
 * if you need to handle different node types differently.</p>
 * 
 * @author Carsten Hammer
 * @since 1.15
 */
public class MethodCallVisitorBuilder extends HelperVisitorBuilder<MethodInvocation> {
    
    private final String typeFQN;
    private final Set<String> methodNames;
    private String importFQN;
    private boolean includeStaticImports = false;
    
    /**
     * Creates a new method call visitor builder.
     * 
     * @param typeFQN the fully qualified name of the type containing the methods
     * @param methodNames the set of method names to find
     */
    public MethodCallVisitorBuilder(String typeFQN, Set<String> methodNames) {
        this.typeFQN = typeFQN;
        this.methodNames = methodNames;
    }
    
    /**
     * Configures the builder to also process static imports for each method.
     * Static imports are of the form "typeFQN.methodName".
     * 
     * @return this builder for chaining
     */
    public MethodCallVisitorBuilder andStaticImports() {
        this.includeStaticImports = true;
        return this;
    }
    
    /**
     * Configures the builder to also process the regular import declaration.
     * 
     * @param importFQN the fully qualified name of the import to include
     * @return this builder for chaining
     */
    public MethodCallVisitorBuilder andImportsOf(String importFQN) {
        this.importFQN = importFQN;
        return this;
    }
    
    @Override
    protected <V, H> void executeVisitors(ReferenceHolder<V, H> holder, 
            BiPredicate<ASTNode, ReferenceHolder<V, H>> processor) {
        // Visit method invocations for each method name
        methodNames.forEach(methodName -> {
            HelperVisitor.callMethodInvocationVisitor(typeFQN, methodName, compilationUnit,
                    holder, nodesprocessed, (node, h) -> processor.test(node, h));
        });
        
        // Optionally include static imports for each method
        if (includeStaticImports) {
            methodNames.forEach(methodName -> {
                HelperVisitor.callImportDeclarationVisitor(typeFQN + "." + methodName, 
                        compilationUnit, holder, nodesprocessed, (node, h) -> processor.test(node, h));
            });
        }
        
        // Optionally include the regular import
        if (importFQN != null) {
            HelperVisitor.callImportDeclarationVisitor(importFQN, compilationUnit,
                    holder, nodesprocessed, (node, h) -> processor.test(node, h));
        }
    }
}
