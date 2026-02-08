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
package org.sandbox.jdt.triggerpattern.encoding;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.triggerpattern.api.CleanupPattern;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.RewriteRule;
import org.sandbox.jdt.triggerpattern.cleanup.AbstractPatternCleanupPlugin;

/**
 * Declarative cleanup plugin that transforms:
 * 
 * <pre>
 * Charset.forName("UTF-8")  →  StandardCharsets.UTF_8
 * </pre>
 * 
 * <p>
 * This is an example of a simple method call replacement using
 * {@link CleanupPattern} and {@link RewriteRule}.
 * </p>
 * 
 * <p>
 * <b>Note:</b> This plugin handles only the "UTF-8" case. For other encodings
 * (ISO-8859-1, US-ASCII, etc.), additional plugins or a more sophisticated
 * value mapping approach would be needed.
 * </p>
 * 
 * @since 1.2.5
 */
@CleanupPattern(value = "Charset.forName(\"UTF-8\")", kind = PatternKind.METHOD_CALL, qualifiedType = "java.nio.charset.Charset", cleanupId = "cleanup.encoding.charset.forname.utf8", description = "Replace Charset.forName(\"UTF-8\") with StandardCharsets.UTF_8")
@RewriteRule(replaceWith = "StandardCharsets.UTF_8", removeImports = {}, // Don't remove Charset - might be used
																			// elsewhere
		addImports = { "java.nio.charset.StandardCharsets" })
public class CharsetForNameEncodingPlugin extends AbstractPatternCleanupPlugin<EncodingHolder> {

	@Override
	protected EncodingHolder createHolder(Match match) {
		EncodingHolder holder = new EncodingHolder();
		holder.setMinv(match.getMatchedNode());
		holder.setBindings(match.getBindings());
		return holder;
	}

	@Override
	protected void processRewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			EncodingHolder holder) {
		// Delegate to the declarative @RewriteRule processing
		processRewriteWithRule(group, rewriter, ast, importRewriter, holder);
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					import java.nio.charset.StandardCharsets;

					Charset charset = StandardCharsets.UTF_8;
					""";
		}
		return """
				import java.nio.charset.Charset;

				Charset charset = Charset.forName("UTF-8");
				""";
	}
}
