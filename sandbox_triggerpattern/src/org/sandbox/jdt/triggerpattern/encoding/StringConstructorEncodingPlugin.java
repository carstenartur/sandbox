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
 * new String(bytes, "UTF-8")  →  new String(bytes, StandardCharsets.UTF_8)
 * </pre>
 * 
 * <p>This demonstrates how to use {@link CleanupPattern} and {@link RewriteRule}
 * for fully declarative code transformations without manual AST manipulation.</p>
 * 
 * <p><b>How it works:</b></p>
 * <ul>
 *   <li>{@code @CleanupPattern} defines the pattern to match: {@code new String($bytes, "UTF-8")}</li>
 *   <li>{@code @RewriteRule} defines the replacement: {@code new String($bytes, StandardCharsets.UTF_8)}</li>
 *   <li>The {@code $bytes} placeholder is automatically preserved in the replacement</li>
 *   <li>Imports are managed automatically via {@code addImports} and (optionally) {@code removeImports}</li>
 * </ul>
 * 
 * <p><b>Benefits over traditional cleanup:</b></p>
 * <table border="1">
 *   <tr><th>Aspect</th><th>Traditional</th><th>Declarative (@RewriteRule)</th></tr>
 *   <tr><td>Lines of code</td><td>80-150</td><td>~30</td></tr>
 *   <tr><td>Pattern definition</td><td>Scattered in find()</td><td>Single annotation</td></tr>
 *   <tr><td>Replacement logic</td><td>Manual AST creation</td><td>Declarative template</td></tr>
 *   <tr><td>Import handling</td><td>Manual ImportRewrite</td><td>Annotation arrays</td></tr>
 * </table>
 * 
 * @since 1.2.5
 * @see CleanupPattern
 * @see RewriteRule
 */
@CleanupPattern(
    value = "new String($bytes, \"UTF-8\")",
    kind = PatternKind.CONSTRUCTOR,
    qualifiedType = "java.lang.String",
    cleanupId = "cleanup.encoding.string.utf8",
    description = "Replace String constructor with UTF-8 literal with StandardCharsets.UTF_8"
)
@RewriteRule(
    replaceWith = "new String($bytes, StandardCharsets.UTF_8)",
    addImports = {"java.nio.charset.StandardCharsets"}
)
public class StringConstructorEncodingPlugin extends AbstractPatternCleanupPlugin<EncodingHolder> {
    
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
                
                String text = new String(bytes, StandardCharsets.UTF_8);
                """;
        }
        return """
            String text = new String(bytes, "UTF-8");
            """;
    }
}
