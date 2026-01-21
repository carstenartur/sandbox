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
/**
 * Renderer interfaces and implementations for stream pipeline generation.
 * 
 * <p>The key abstraction is {@link org.sandbox.functional.core.renderer.StreamPipelineRenderer}, 
 * which defines callbacks for rendering each pipeline element. Implementations include:</p>
 * <ul>
 *   <li>{@link org.sandbox.functional.core.renderer.StringRenderer} - generates Java code strings (for tests)</li>
 *   <li>ASTRenderer (in sandbox_functional_converter) - generates JDT AST</li>
 * </ul>
 */
package org.sandbox.functional.core.renderer;
