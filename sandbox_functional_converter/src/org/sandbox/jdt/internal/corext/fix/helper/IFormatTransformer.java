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

import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

/**
 * Interface for transforming loops to different target formats.
 * 
 * <p>Implementations of this interface convert loop structures from one format to another:
 * <ul>
 *   <li>Stream format: Java 8+ functional style (forEach, map, filter, etc.)</li>
 *   <li>For-loop format: Enhanced for-loop or classic for-loop</li>
 *   <li>While-loop format: While loop with iterator</li>
 * </ul>
 * </p>
 * 
 * @see LoopTargetFormat
 */
public interface IFormatTransformer {
    
    /**
     * Transforms a statement to the target format.
     * 
     * @param statement the source statement (EnhancedForStatement, WhileStatement, or stream expression)
     * @param cuRewrite the compilation unit rewrite context
     * @return the transformed statement, or null if transformation is not possible
     */
    Statement transform(Statement statement, CompilationUnitRewrite cuRewrite);
    
    /**
     * Checks if this transformer can handle the given statement.
     * 
     * @param statement the statement to check
     * @return true if this transformer can transform the statement
     */
    boolean canTransform(Statement statement);
    
    /**
     * Returns the target format of this transformer.
     * 
     * @return the target format
     */
    LoopTargetFormat getTargetFormat();
}
