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
            BiPredicate<ASTNode, ReferenceHolder<V, H>> processor) {
        HelperVisitor.callImportDeclarationVisitor(importFQN, compilationUnit,
                holder, nodesprocessed, (node, h) -> processor.test(node, h));
    }
}
