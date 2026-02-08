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
 * <pre>
 * str.getBytes("UTF-8")  →  str.getBytes(StandardCharsets.UTF_8)
 * </pre>
 * 
 * <p>This removes the need for handling {@code UnsupportedEncodingException}
 * since {@code StandardCharsets.UTF_8} is guaranteed to be available.</p>
 * 
 * @since 1.2.5
 */
@CleanupPattern(
    value = "$str.getBytes(\"UTF-8\")",
    kind = PatternKind.METHOD_CALL,
    qualifiedType = "java.lang.String",
    cleanupId = "cleanup.encoding.string.getbytes.utf8",
    description = "Replace String.getBytes(\"UTF-8\") with String.getBytes(StandardCharsets.UTF_8)"
)
@RewriteRule(
    replaceWith = "$str.getBytes(StandardCharsets.UTF_8)",
    addImports = {"java.nio.charset.StandardCharsets"}
)
public class StringGetBytesEncodingPlugin extends AbstractPatternCleanupPlugin<EncodingHolder> {
    
    @Override
    protected EncodingHolder createHolder(Match match) {
        EncodingHolder holder = new EncodingHolder();
        holder.setMinv(match.getMatchedNode());
        holder.setBindings(match.getBindings());
        return holder;
    }
    
    @Override
    protected void processRewrite(TextEditGroup group, ASTRewrite rewriter, AST ast,
            ImportRewrite importRewriter, EncodingHolder holder) {
        // Delegate to the declarative @RewriteRule processing
        processRewriteWithRule(group, rewriter, ast, importRewriter, holder);
    }
    
    @Override
    public String getPreview(boolean afterRefactoring) {
        if (afterRefactoring) {
            return """
                import java.nio.charset.StandardCharsets;
                
                byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                """;
        }
        return """
            byte[] bytes = text.getBytes("UTF-8");
            """;
    }
}
