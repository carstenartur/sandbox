/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NodeFinder;

/** Separate edits preserve one proof per computation and put contracts on the enclosing method. */
final class MathematicalJavadoc {
    private final Map<MethodDeclaration, List<String>> notes = new LinkedHashMap<>();

    void add(CompilationUnit ast, JavaComputationRegion region, String note) {
        ASTNode node = NodeFinder.perform(ast, region.start(), region.length());
        while (node != null && !(node instanceof MethodDeclaration)) node = node.getParent();
        if (!(node instanceof MethodDeclaration method)) throw new IllegalArgumentException("MISSING_CONTRACT_METHOD");
        notes.computeIfAbsent(method, key -> new ArrayList<>()).add(note);
    }

    List<MathematicalAnalysis.Replacement> edits(String source) {
        List<MathematicalAnalysis.Replacement> edits = new ArrayList<>();
        String newline = source.contains("\r\n") ? "\r\n" : "\n";
        notes.forEach((method, descriptions) -> {
            int start = method.getStartPosition();
            int line = source.lastIndexOf('\n', start) + 1;
            String indentation = source.substring(line, start);
            if (!indentation.isBlank()) indentation = "";
            String text = String.join(" ", descriptions).replace("*/", "*&#47;");
            String note = newline + indentation + " * <p><strong>Mathematics cleanup:</strong> " + text + "</p>" + newline + indentation + " * ";
            // JDT can retain the declaration's comment start while doc-comment parsing is disabled.
            int commentStart = method.getJavadoc() == null ? start : method.getJavadoc().getStartPosition();
            if (source.startsWith("/**", commentStart)) {
                edits.add(new MathematicalAnalysis.Replacement(commentStart + 3, 0, note, "Document mathematical contract"));
            } else {
                edits.add(new MathematicalAnalysis.Replacement(start, 0, "/**" + note.stripTrailing() + "/" + newline + indentation, "Document mathematical contract"));
            }
        });
        return List.copyOf(edits);
    }
}
