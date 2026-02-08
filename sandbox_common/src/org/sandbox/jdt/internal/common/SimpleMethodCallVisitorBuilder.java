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

import java.util.function.BiPredicate;

import org.eclipse.jdt.core.dom.MethodInvocation;

/**
 * Type-safe fluent builder for visiting method invocations only.
 * 
 * <p>This builder provides a type-safe API that returns {@code MethodInvocation} directly,
 * without requiring casts at the call site. Use this builder when you only need to visit
 * method invocations without handling imports.</p>
 * 
 * <p><b>Example Usage:</b></p>
 * <pre>
 * HelperVisitor.forMethodCall(String.class, "getBytes")
 *     .in(compilationUnit)
 *     .excluding(nodesprocessed)
 *     .processEach(holder, (methodInv, h) -&gt; {
 *         // methodInv is already MethodInvocation - no cast needed!
 *         processFoundNode(methodInv, h);
 *         return true;
 *     });
 * </pre>
 * 
 * <p><b>Note:</b> If you need to also process imports alongside method invocations,
 * use {@link MethodCallVisitorBuilder} instead via 
 * {@code HelperVisitor.forMethodCalls(...).andStaticImports()}.</p>
 * 
 * @author Carsten Hammer
 * @since 1.16
 */
public class SimpleMethodCallVisitorBuilder extends HelperVisitorBuilder<MethodInvocation> {
    
    private final String typeFQN;
    private final Class<?> typeClass;
    private final String methodName;
    
    /**
     * Creates a new simple method call visitor builder with a type name.
     * 
     * @param typeFQN the fully qualified name of the type containing the method
     * @param methodName the method name to find
     */
    public SimpleMethodCallVisitorBuilder(String typeFQN, String methodName) {
        this.typeFQN = typeFQN;
        this.typeClass = null;
        this.methodName = methodName;
    }
    
    /**
     * Creates a new simple method call visitor builder with a Class object.
     * 
     * @param typeClass the class containing the method
     * @param methodName the method name to find
     */
    public SimpleMethodCallVisitorBuilder(Class<?> typeClass, String methodName) {
        this.typeFQN = null;
        this.typeClass = typeClass;
        this.methodName = methodName;
    }
    
    @Override
    protected <V, H> void executeVisitors(ReferenceHolder<V, H> holder, 
            BiPredicate<MethodInvocation, ReferenceHolder<V, H>> processor) {
        if (typeFQN != null) {
            HelperVisitor.callMethodInvocationVisitor(typeFQN, methodName, compilationUnit,
                    holder, nodesprocessed, (node, h) -> processor.test(node, h));
        } else if (typeClass != null) {
            HelperVisitor.callMethodInvocationVisitor(typeClass, methodName, compilationUnit,
                    holder, nodesprocessed, (node, h) -> processor.test(node, h));
        }
    }
}
