/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.api;

/** Defines the kinds of AST patterns that can be matched. */
public enum PatternKind {
	/** Java expression. */
	EXPRESSION,
	/** Java statement. */
	STATEMENT,
	/** Java annotation. */
	ANNOTATION,
	/** Java method invocation. */
	METHOD_CALL,
	/** Import declaration. */
	IMPORT,
	/** Field declaration. */
	FIELD,
	/** Class-instance creation expression. */
	CONSTRUCTOR,
	/** Method or constructor declaration. */
	METHOD_DECLARATION,
	/**
	 * Class, interface, enum, record or annotation-type declaration.
	 *
	 * <p>Type patterns are header patterns: an empty pattern body does not require
	 * the source type to have an empty body. Name, kind and every explicitly
	 * declared modifier, type parameter or supertype remain constraints.</p>
	 */
	TYPE_DECLARATION,
	/** Complete block. */
	BLOCK,
	/** Consecutive statement sequence. */
	STATEMENT_SEQUENCE,
	/** Local variable declaration statement. */
	DECLARATION
}
