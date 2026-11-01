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
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import de.regelsuche.sdk.optimization.*;

class SourceNumericBranchTest {
    @TempDir java.nio.file.Path temporary;
    @Test void removesPureTotalConditionOnlyAfterProvingBothPrimitiveRightSidesEqual() throws Exception {
        String source = source("mode > 0", "a+b", "b+a");
        var result = analyze(source);
        assertTrue(result.changed(), result.diagnostics().toString());
        var receipt=result.evidence().stream().map(MathematicalAnalysis.VerifiedRegion::sourceNumericBranch)
                .filter(java.util.Objects::nonNull).findFirst().orElseThrow();
        receipt.requireSource(source);
        assertThrows(IllegalArgumentException.class,()->receipt.requireSource(source+" "));
        var document = new Document(source); var undo = result.newEdit().apply(document, TextEdit.CREATE_UNDO);
        assertFalse(result.evidence().stream().filter(e->e.sourceNumericBranch()!=null).findFirst().orElseThrow().replacement().contains("if("), document.get());
        undo.apply(document); assertEquals(source, document.get());
    }
    @Test void actualExecutionPreservesOverflowConditionsEffectsAndThrows() throws Exception {
        for(String condition:List.of("mode>0","test()","1/mode>0")) {
            String source=source(condition,"a+b","b+a");
            var result=analyze(source); assertTrue(result.changed(),result.diagnostics().toString());
            var document=new Document(source); result.newEdit().apply(document);
            try(var before=compile(source);var after=compile(document.get())) {
                for(int mode:new int[]{0,1,-1}) for(int a:new int[]{0,Integer.MIN_VALUE,Integer.MAX_VALUE,-7}) {
                    assertEquals(invoke(before,a,Integer.MAX_VALUE,mode),invoke(after,a,Integer.MAX_VALUE,mode));
                }
            }
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void equivalentBranchValuesPreserveAnAuthoredConstantFormulaOnEitherSide(boolean formulaOnLeft) throws Exception {
        for (String condition : List.of("mode>0", "test()", "1/mode>0")) {
            String source = source(condition, formulaOnLeft ? "32-11" : "21", formulaOnLeft ? "21" : "32-11");
            var result = analyze(source);
            var document = new Document(source); result.newEdit().apply(document);
            try (var before = compile(source); var after = compile(document.get())) {
                for (int mode : new int[]{-1, 0, 1})
                    assertEquals(invoke(before, 0, 0, mode), invoke(after, 0, 0, mode));
            }
            assertTrue(document.get().contains("32-11"), document.get());
            assertFalse(result.evidence().stream().anyMatch(e -> e.sourceNumericBranch() != null), result.replacements().toString());
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void equivalentBranchesRetainWholeFormulasFromRuntimeConstantChoices(boolean formulaOnLeft) throws Exception {
        String formula = "2 + 12 * numberEntries + 4", simplified = "6 + 12 * numberEntries";
        for (String condition : List.of("mode>0", "test()", "1/mode>0")) {
            String source = source(condition, formulaOnLeft ? formula : simplified, formulaOnLeft ? simplified : formula)
                    .replace("int value;", "int numberEntries = a > 0 ? 9 : 11; int value;");
            var result = analyze(source);
            var document = new Document(source); result.newEdit().apply(document);
            try (var before = compile(source); var after = compile(document.get())) {
                for (int mode : new int[]{-1, 0, 1}) for (int a : new int[]{-1, 0, 1})
                    assertEquals(invoke(before, a, 0, mode), invoke(after, a, 0, mode));
            }
            assertTrue(document.get().contains(formula), document.get());
            assertFalse(result.evidence().stream().anyMatch(e -> e.sourceNumericBranch() != null), result.replacements().toString());
        }
    }
    @Test void provedWholeBranchCanSubsumeItsEarlierScalarEditAndUseTheSimplerRhs() throws Exception {
        for(boolean braced:List.of(false,true)) {
            String left="value=(a-1)*b+b-mode;",right="value=a*b-mode;";
            String source="class Sample { static int calls; static int f(int a,int b,int mode) {int value; if(mode>0) "
                    +(braced?"{"+left+"}":left)+" else "+(braced?"{"+right+"}":right)+" return value;}}";
            var result=analyze(source);
            var branch=result.evidence().stream().filter(e->e.sourceNumericBranch()!=null).findFirst().orElseThrow(
                    ()->new AssertionError("Missing whole-branch proof after scalar rewrite: "+result.replacements()));
            assertFalse(branch.replacement().contains("if"),branch.replacement());
            assertFalse(branch.replacement().contains("a-1"),branch.replacement());
            var document=new Document(source); var undo=result.newEdit().apply(document,TextEdit.CREATE_UNDO);
            try(var before=compile(source);var after=compile(document.get())) {
                for(int a:new int[]{0,-1,Integer.MIN_VALUE,Integer.MAX_VALUE}) for(int mode:new int[]{-1,0,1})
                    assertEquals(invoke(before,a,Integer.MAX_VALUE,mode),invoke(after,a,Integer.MAX_VALUE,mode));
            }
            undo.apply(document); assertEquals(source,document.get());
        }
    }
    @Test void protectedSourceReservationsCannotBeSubsumedByABranchProof() {
        String source=source("mode>0","(a-1)*b+b-mode","a*b-mode");
        String protectedRhs="(a-1)*b+b-mode";
        var result=MathematicalAnalysis.analyze(parse(source),source,options(),new NullProgressMonitor(),-1,0,null,
                List.of(new org.eclipse.jdt.core.SourceRange(source.indexOf(protectedRhs),protectedRhs.length())));
        assertFalse(result.evidence().stream().anyMatch(e->e.sourceNumericBranch()!=null),result.replacements().toString());
    }
    @Test void admittedWhitespaceKeepsPureAndCommentedEffectfulConditionSemantics() throws Exception {
        for(String condition:List.of("mode>0","/* keep condition */test()")) {
            String gap=" ".repeat(4096);
            String source=source(condition,"a+b","b+a").replace("if("+condition+")","if"+gap+"("+condition+")"+gap);
            var result=analyze(source);
            var branch=result.evidence().stream().filter(e->e.sourceNumericBranch()!=null).findFirst().orElseThrow();
            assertEquals(condition.contains("test()"),branch.sourceNumericBranch().conditionRetained());
            if(branch.sourceNumericBranch().conditionRetained()) assertTrue(branch.replacement().contains(condition));
            var document=new Document(source); var undo=result.newEdit().apply(document,TextEdit.CREATE_UNDO);
            try(var before=compile(source);var after=compile(document.get())) {
                for(int mode:new int[]{-1,0,1}) assertEquals(invoke(before,Integer.MAX_VALUE,1,mode),invoke(after,Integer.MAX_VALUE,1,mode));
            }
            undo.apply(document); assertEquals(source,document.get());
        }
    }
    @Test void escapedIfKeywordsKeepProvedBranchApplyExecutionAndUndo() throws Exception {
        for(String keyword:List.of("\\u0069f","i\\u0066","\\uuuu0069\\u0066"))
            for(String condition:List.of("mode>0","/* keep condition */test()","1/mode>0")) {
                String source=source(condition,"a+b","b+a").replace("if("+condition+")",keyword+"("+condition+")");
                var result=analyze(source);
                var branches=result.evidence().stream().filter(e->e.sourceNumericBranch()!=null).toList();
                assertEquals(1,branches.size(),keyword+": "+result.diagnostics());
                assertEquals(!condition.equals("mode>0"),branches.getFirst().sourceNumericBranch().conditionRetained());
                var document=new Document(source); var undo=result.newEdit().apply(document,TextEdit.CREATE_UNDO);
                try(var before=compile(source);var after=compile(document.get())) {
                    for(int mode:new int[]{-1,0,1}) for(int a:new int[]{Integer.MIN_VALUE,0,Integer.MAX_VALUE})
                        assertEquals(invoke(before,a,1,mode),invoke(after,a,1,mode));
                }
                undo.apply(document); assertEquals(source,document.get());
            }
    }
    @Test void preservesEffectfulOrThrowingConditionsAndRejectsUnequalOrEffectfulRightSides() throws Exception {
        for (String condition : List.of("test()", "1/mode > 0", "/* keep condition */ mode > 0")) {
            String source = source(condition, "a+b", "b+a");
            var result = analyze(source);
            assertTrue(result.changed(), result.diagnostics().toString());
            var document = new Document(source); result.newEdit().apply(document);
            assertTrue(document.get().contains(condition), document.get());
        }
        for (String[] sides : List.of(new String[]{"a+b", "a-b"}, new String[]{"call(a)", "call(a)"},
                new String[]{"a/* retain */+b", "b+a"})) {
            var result = analyze(source("mode>0", sides[0], sides[1]));
            assertFalse(result.changed(), result.replacements().toString());
        }
    }
    private static String source(String condition, String left, String right) {
        return "class Sample { static int calls; static boolean test(){calls++; if(calls==2) throw new IllegalStateException(); return calls%2==0;} static int call(int x){return x;} "
                + "static int f(int a,int b,int mode){int value; if(" + condition + ") value=" + left + "; else value=" + right + "; return value;} }";
    }
    private java.net.URLClassLoader compile(String source) throws Exception {
        var directory=java.nio.file.Files.createTempDirectory(temporary,"compiled-");
        var file=directory.resolve("Sample.java"); java.nio.file.Files.writeString(file,source);
        assertEquals(0,javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","17","-d",directory.toString(),file.toString()));
        return new java.net.URLClassLoader(new java.net.URL[]{directory.toUri().toURL()},ClassLoader.getPlatformClassLoader());
    }
    private static String invoke(ClassLoader loader,int a,int b,int mode) throws Exception {
        var type=loader.loadClass("Sample"); var method=type.getDeclaredMethod("f",int.class,int.class,int.class); method.setAccessible(true);
        var calls=type.getDeclaredField("calls"); calls.setAccessible(true); String result;
        try { result=String.valueOf(method.invoke(null,a,b,mode)); }
        catch(java.lang.reflect.InvocationTargetException failure) { result=failure.getCause().getClass().getName(); }
        return result+":"+calls.getInt(null);
    }
    private static MathematicalAnalysis.Analysis analyze(String source) {
        return MathematicalAnalysis.analyze(parse(source),source,options(),new NullProgressMonitor(),-1,0);
    }
    private static MathCleanUpOptions options() {
        return new MathCleanUpOptions(true,Set.of(NumericKind.INT),SafetyProfile.PRESERVE_JAVA,OptimizationGoal.READABILITY,
                1_000_000,20_000,false,17,List.of());
    }
    private static CompilationUnit parse(String source) {
        var parser=ASTParser.newParser(AST.getJLSLatest()); parser.setSource(source.toCharArray()); parser.setUnitName("Sample.java");
        parser.setEnvironment(new String[0],new String[0],null,true); parser.setResolveBindings(true);
        var settings=new HashMap<String,String>(); JavaCore.setComplianceOptions("17",settings); parser.setCompilerOptions(settings);
        var ast=(CompilationUnit)parser.createAST(null); for(var problem:ast.getProblems()) assertFalse(problem.isError(),problem.toString());
        return ast;
    }
}
