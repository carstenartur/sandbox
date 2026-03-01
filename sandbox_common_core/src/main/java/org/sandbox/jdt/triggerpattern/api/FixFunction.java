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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method in a {@code <? ?>} block as a fix function.
 * The method must have the signature {@code (Match, ASTRewrite) → void}.
 *
 * <p>DSL usage:</p>
 * <pre>
 * &lt;?
 * &#64;FixFunction
 * public void customFix(Match match, ASTRewrite rewrite) {
 *     // Custom rewrite logic
 * }
 * ?&gt;
 *
 * $x.oldMethod()
 * =&gt; &lt;?customFix?&gt;
 * ;;
 * </pre>
 *
 * @since 1.5.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface FixFunction {
	/**
	 * Optional name override. Default: method name.
	 *
	 * @return the fix function name override, or empty string to use the method name
	 */
	String value() default ""; //$NON-NLS-1$
}
