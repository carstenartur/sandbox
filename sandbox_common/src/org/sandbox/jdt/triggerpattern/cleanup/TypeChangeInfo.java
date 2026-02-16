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
package org.sandbox.jdt.triggerpattern.cleanup;

/**
 * Information about a detected type change from {@code String} to
 * {@code Charset} in a DSL replacement.
 *
 * @param exceptionFQN        fully qualified name of the exception that should be removed
 *                             (e.g. {@code "java.io.UnsupportedEncodingException"})
 * @param exceptionSimpleName simple name of the exception
 *                             (e.g. {@code "UnsupportedEncodingException"})
 * @since 1.3.5
 */
public record TypeChangeInfo(String exceptionFQN, String exceptionSimpleName) {
}
