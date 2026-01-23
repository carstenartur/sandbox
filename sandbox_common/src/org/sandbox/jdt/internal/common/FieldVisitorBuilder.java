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

/*-
 * #%L
 * Sandbox common
 * %%
 * Copyright (C) 2026 hammer
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

import java.util.function.BiPredicate;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.FieldDeclaration;

/**
 * Fluent builder for visiting field declarations.
 * 
 * <p><b>Example Usage:</b></p>
 * <pre>
 * // Field with annotation and type
 * HelperVisitor.forField()
 *     .withAnnotation("org.junit.Rule")
 *     .ofType("org.junit.rules.TemporaryFolder")
 *     .in(compilationUnit)
 *     .excluding(nodesprocessed)
 *     .processEach((field, holder) -&gt; addOperation(field));
 * </pre>
 * 
 * @author Carsten Hammer
 * @since 1.15
 */
public class FieldVisitorBuilder extends HelperVisitorBuilder<FieldDeclaration> {
    
    private String annotationFQN;
    private String typeFQN;
    
    /**
     * Configures the builder to find fields with the specified annotation.
     * 
     * @param annotationFQN the fully qualified name of the annotation
     * @return this builder for chaining
     */
    public FieldVisitorBuilder withAnnotation(String annotationFQN) {
        this.annotationFQN = annotationFQN;
        return this;
    }
    
    /**
     * Configures the builder to find fields of the specified type (or subtype).
     * 
     * @param typeFQN the fully qualified name of the type
     * @return this builder for chaining
     */
    public FieldVisitorBuilder ofType(String typeFQN) {
        this.typeFQN = typeFQN;
        return this;
    }
    
    @Override
    protected <V, H> void executeVisitors(ReferenceHolder<V, H> holder, 
            BiPredicate<ASTNode, ReferenceHolder<V, H>> processor) {
        HelperVisitor.callFieldDeclarationVisitor(annotationFQN, typeFQN, compilationUnit,
                holder, nodesprocessed, (node, h) -> processor.test(node, h));
    }
}
