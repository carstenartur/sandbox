/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import de.regelsuche.sdk.optimization.NumericKind;

class JavaRegionSourceRewriterTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void mixedOutputEditsKeepMultilineTextBetweenOtherChangesAndUndoExactly(boolean removeInternals) throws Exception {
        String source = """
            class Sample {
                int f(int x) {
                    int dead1 = x + 1;
                    int a = x + 2;
                    int b = x + 3;
                    int dead2 = x + 4;
                    int c = x + 5;
                    return a + b + c;
                }
            }
            """;
        var ast = parse(source);
        var outputs = new ArrayList<JavaComputationRegion.OutputBinding>();
        var internal = new ArrayList<JavaComputationRegion.OutputBinding>();
        ast.accept(new ASTVisitor() {
            @Override public boolean visit(VariableDeclarationFragment fragment) {
                var expression = fragment.getInitializer();
                var statement = fragment.getParent();
                String name = fragment.getName().getIdentifier();
                var binding = new JavaComputationRegion.OutputBinding(name, name, NumericKind.INT,
                    expression.getStartPosition(), expression.getLength(), statement.getStartPosition(),
                    statement.getLength(), true, fragment.resolveBinding().getKey());
                if (name.startsWith("dead")) { if (removeInternals) internal.add(binding); }
                else outputs.add(binding);
                return true;
            }
        });
        int start = source.indexOf("int dead1"), end = source.indexOf("return a");
        // This source-edit unit test supplies real binding/range data; it does not invoke numerical search.
        var region = new JavaComputationRegion(null, null, outputs, Map.of(), Set.of(), Set.of(), Set.of(),
                start, end - start, internal);
        String multiline = "x\n            + 20";
        var values = Map.of("a", "x + 10", "b", multiline, "c", "x + 30");
        TextEdit edit = assertDoesNotThrow(() -> JavaRegionSourceRewriter.edits(ast, source, region, values, Map.of()));
        var document = new Document(source);
        var undo = edit.apply(document, TextEdit.CREATE_UNDO);
        String generated = document.get();
        assertTrue(generated.contains("int b = " + multiline + ";"), generated);
        assertTrue(generated.contains("int a = x + 10;"), generated);
        assertTrue(generated.contains("int c = x + 30;"), generated);
        assertEquals(!removeInternals, generated.contains("int dead1"));
        assertEquals(!removeInternals, generated.contains("int dead2"));
        parse(generated);
        undo.apply(document);
        assertEquals(source, document.get());
        assertEquals(generated.substring(start, end + generated.length() - source.length()),
                JavaRegionSourceRewriter.rewrite(ast, source, region, values, Map.of()));
    }
    private static CompilationUnit parse(String source) {
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(source.toCharArray()); parser.setUnitName("Sample.java");
        parser.setEnvironment(new String[0], new String[0], null, true); parser.setResolveBindings(true);
        var options = new HashMap<String,String>(); JavaCore.setComplianceOptions("17", options); parser.setCompilerOptions(options);
        var ast = (CompilationUnit) parser.createAST(null);
        for (var problem : ast.getProblems()) assertFalse(problem.isError(), problem.toString());
        return ast;
    }
}
