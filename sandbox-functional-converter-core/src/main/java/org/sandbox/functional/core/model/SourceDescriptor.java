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
package org.sandbox.functional.core.model;

/**
 * Describes the source of iteration.
 * 
 * @param type the source type
 * @param expression the expression to iterate over
 * @param elementTypeName the element type name
 * @since 1.0.0
 */
public record SourceDescriptor(
    SourceType type,
    String expression,
    String elementTypeName
) {
    /**
     * The type of iteration source.
     */
    public enum SourceType {
        COLLECTION,
        ARRAY,
        ITERABLE,
        ITERATOR,
        STREAM,
        INT_RANGE,
        EXPLICIT_RANGE
    }
}
