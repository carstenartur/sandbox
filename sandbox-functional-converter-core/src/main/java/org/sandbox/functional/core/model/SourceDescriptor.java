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
        /** A collection like List, Set, etc. Generates: {@code collection.stream()} */
        COLLECTION,
        
        /** An array. Generates: {@code Arrays.stream(array)} */
        ARRAY,
        
        /** An Iterable. Generates: {@code StreamSupport.stream(iterable.spliterator(), false)} */
        ITERABLE,
        
        /** An Iterator (future use) */
        ITERATOR,
        
        /** Already a Stream. Expression is used as-is */
        STREAM,
        
        /** 
         * Integer range from 0 to N (exclusive).
         * Expression format: {@code "N"} where N is the upper bound.
         * Generates: {@code IntStream.range(0, N)}
         * Example: {@code "10"} → {@code IntStream.range(0, 10)}
         */
        INT_RANGE,
        
        /** 
         * Explicit integer range with start and end.
         * Expression format: {@code "start,end"} (comma-separated).
         * Generates: {@code IntStream.range(start, end)}
         * Examples:
         * <ul>
         *   <li>{@code "0,10"} → {@code IntStream.range(0, 10)}</li>
         *   <li>{@code "start,end"} → {@code IntStream.range(start, end)}</li>
         *   <li>{@code "i+1,arr.length"} → {@code IntStream.range(i+1, arr.length)}</li>
         * </ul>
         * <p>
         * Note: The start and end expressions must not contain unescaped commas, as comma is
         * used as the delimiter. Simple expressions like variable names, field accesses, and
         * binary operations are supported (for example, {@code "i+1,arr.length"}).
         * </p>
         * Used for classic index-based for loops: {@code for(int i=start; i<end; i++)}
         */
        EXPLICIT_RANGE
    }
}
