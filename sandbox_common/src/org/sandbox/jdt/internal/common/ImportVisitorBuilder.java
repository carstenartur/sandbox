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

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ImportDeclaration;

/**
 * Fluent builder for visiting import declarations.
 * 
 * <p><b>Example Usage:</b></p>
 * <pre>
 * // Single import
 * HelperVisitor.forImport("org.junit.Assert")
 *     .in(compilationUnit)
 *     .excluding(nodesprocessed)
 *     .processEach((importDecl, holder) -&gt; addOperation(importDecl));
 * </pre>
 * 
 * @author Carsten Hammer
 * @since 1.15
 */
public class ImportVisitorBuilder extends HelperVisitorBuilder<ImportDeclaration> {
    
    private final String importFQN;
    
    /**
     * Creates a new import visitor builder.
     * 
     * @param importFQN the fully qualified name of the import to find
     */
    public ImportVisitorBuilder(String importFQN) {
        this.importFQN = importFQN;
    }
    
    @Override
    protected <V, H> void executeVisitors(ReferenceHolder<V, H> holder, 
            BiPredicate<ImportDeclaration, ReferenceHolder<V, H>> processor) {
        HelperVisitor.callImportDeclarationVisitor(importFQN, compilationUnit,
                holder, nodesprocessed, processor);
    }
}
