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
 * Declarative encoding cleanup plugins using TriggerPattern.
 * 
 * <p>This package demonstrates how to create encoding-related cleanup plugins
 * using the declarative {@code @CleanupPattern} and {@code @RewriteRule} annotations,
 * avoiding the need for manual AST manipulation.</p>
 * 
 * <h2>Available Plugins</h2>
 * <table border="1">
 *   <tr>
 *     <th>Plugin</th>
 *     <th>Pattern</th>
 *     <th>Replacement</th>
 *   </tr>
 *   <tr>
 *     <td>{@link StringConstructorEncodingPlugin}</td>
 *     <td>{@code new String($bytes, "UTF-8")}</td>
 *     <td>{@code new String($bytes, StandardCharsets.UTF_8)}</td>
 *   </tr>
 *   <tr>
 *     <td>{@link CharsetForNameEncodingPlugin}</td>
 *     <td>{@code Charset.forName("UTF-8")}</td>
 *     <td>{@code StandardCharsets.UTF_8}</td>
 *   </tr>
 *   <tr>
 *     <td>{@link StringGetBytesEncodingPlugin}</td>
 *     <td>{@code $str.getBytes("UTF-8")}</td>
 *     <td>{@code $str.getBytes(StandardCharsets.UTF_8)}</td>
 *   </tr>
 * </table>
 * 
 * <h2>Comparison with Traditional Approach</h2>
 * <p>The traditional {@code sandbox_encoding_quickfix} plugin uses manual AST visitors
 * and complex rewrite logic. Each transformation requires ~80-150 lines of code.</p>
 * 
 * <p>With declarative plugins, the same transformation requires ~30-50 lines, most of
 * which is boilerplate that can be reduced further with inheritance.</p>
 * 
 * <h2>Limitations</h2>
 * <p>The current declarative approach handles simple pattern replacements. More complex
 * transformations still require manual implementation:</p>
 * <ul>
 *   <li>Value mapping (e.g., "UTF-8" → UTF_8, "ISO-8859-1" → ISO_8859_1)</li>
 *   <li>Structural changes (e.g., FileReader → InputStreamReader+FileInputStream)</li>
 *   <li>Exception handling changes (removing UnsupportedEncodingException)</li>
 *   <li>Java version constraints</li>
 * </ul>
 * 
 * @since 1.2.5
 * @see org.sandbox.jdt.triggerpattern.api.CleanupPattern
 * @see org.sandbox.jdt.triggerpattern.api.RewriteRule
 */
package org.sandbox.jdt.triggerpattern.encoding;
