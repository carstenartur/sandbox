/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import de.regelsuche.sdk.optimization.*;

class OutputLocalReuseTest {
    @Test void retainsAuthoredLayoutFormulaFromAnImmutableChoiceOfConstants() throws Exception {
        String formula="int lengthDirectory = 2 + 12 * numberEntries + 4;";
        String source="class Sample { void consume(int x,int y) {} void f(boolean biLevel) { int numberEntries = biLevel ? 9 : 11; "
                +formula+" int nextOffset = 8 + lengthDirectory; consume(lengthDirectory,nextOffset); } }";
        var options = new MathCleanUpOptions(true, Set.of(NumericKind.INT), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 1_000_000, 20_000, false, 17, List.of());
        var result=MathematicalAnalysis.analyze(parse(source),source,options,new NullProgressMonitor(),-1,0);
        var document=new Document(source); result.newEdit().apply(document);
        assertTrue(document.get().contains(formula),document.get());
    }
    @Test void ordinaryApplyReusesEarlierMeaningfulOutputInsteadOfDuplicatingItIntoTemporary() throws Exception {
        String source = "class Sample { void consume(int x,int y) {} void f(int numColors) { "
                + "int startG = 2 * numColors; int startB = startG + 2 * numColors; consume(startG,startB); } }";
        var options = new MathCleanUpOptions(true, Set.of(NumericKind.INT), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.READABILITY, 1_000_000, 20_000, false, 17, List.of());
        var result = MathematicalAnalysis.analyze(parse(source), source, options, new NullProgressMonitor(), -1, 0);
        assertTrue(result.changed(), result.diagnostics().toString());
        var document = new Document(source); var undo = result.newEdit().apply(document, TextEdit.CREATE_UNDO);
        String generated = document.get();
        assertFalse(generated.contains("_math"), generated);
        assertTrue(generated.contains("int startG ="), generated);
        assertTrue(generated.substring(generated.indexOf("int startB")).contains("startG"), generated);
        parse(generated);
        undo.apply(document); assertEquals(source, document.get());
    }
    private static CompilationUnit parse(String source) {
        var parser = ASTParser.newParser(AST.getJLSLatest()); parser.setSource(source.toCharArray()); parser.setUnitName("Sample.java");
        parser.setEnvironment(new String[0], new String[0], null, true); parser.setResolveBindings(true);
        var settings = new HashMap<String,String>(); JavaCore.setComplianceOptions("17", settings); parser.setCompilerOptions(settings);
        var ast = (CompilationUnit)parser.createAST(null);
        for (var problem : ast.getProblems()) assertFalse(problem.isError(), problem.toString());
        return ast;
    }
}
