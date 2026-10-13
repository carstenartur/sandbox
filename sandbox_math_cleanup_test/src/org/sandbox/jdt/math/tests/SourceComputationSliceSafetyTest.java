/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import javax.tools.ToolProvider;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import com.fasterxml.jackson.databind.ObjectMapper;

class SourceComputationSliceSafetyTest {
    @TempDir Path temporary;
    // A fresh seeded generator belongs to each JUnit test instance.
    private final Random random = new Random(4711);
    private static final String PURE = "int left=a*b; audit(); int right=a*c; return left+right;";
    private static final String HEADER = "public class Sample { public static int visits; public static boolean fail; "
            + "static void audit() { visits++; if(fail) throw new IllegalStateException(\"audit\"); } "
            + "public static int f(int a,int b,int c) {";

    @Test void sourceOriginsSurviveSharingAndOnlyResolveAgainstTheirOwnSnapshot() throws Exception {
        String source = HEADER + PURE + "}}";
        var ast = SourceComputationSliceTest.parse(source);
        var result = MathematicalAnalysis.analyze(ast, source, SourceComputationSliceTest.options(false), null, -1, 0);
        var item = result.evidence().getFirst(); var plan = item.sourcePlan(); assertNotNull(plan);
        assertEquals(3, plan.occurrenceOrigins().size(), "Two multiplies plus the original add, not the optimized node count");
        var resolved = plan.resolve(ast, source);
        for (var origins : plan.occurrenceOrigins().values()) for (var id : origins) {
            assertTrue(resolved.containsKey(id)); assertSame(ast.getAST(), resolved.get(id).getAST());
        }
        assertEquals(1, plan.pinned().size());
        assertEquals("audit();", source.substring(plan.pinned().getFirst().offset(), plan.pinned().getFirst().offset() + plan.pinned().getFirst().length()));
        String changed = source.replace("visits++", "visits--");
        assertEquals(source.length(), changed.length());
        assertThrows(IllegalArgumentException.class, () -> plan.newEdit(changed));
        assertThrows(IllegalArgumentException.class, () -> plan.resolve(SourceComputationSliceTest.parse(changed), changed));
        assertFalse(result.matches(changed, java.util.Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new MathematicalAnalysis.VerifiedRegion(item.offset(), item.length(),
                item.proof(), item.cost(), item.original(), item.replacement() + " ", item.explanation(), plan));
        var mapper = new ObjectMapper();
        var decoded = mapper.readValue(mapper.writeValueAsString(plan), plan.getClass());
        assertEquals(plan, decoded);
        assertFalse(mapper.writeValueAsString(plan).contains("org.eclipse.jdt.core.dom"), "JDT object graphs do not escape in the saved plan");
        var doc = new Document(source); decoded.newEdit(source).apply(doc); SourceComputationSliceTest.parse(doc.get());
    }

    @Test void overflowValuesAndThrowingInterveningCallKeepExactlyTheSameBehavior() throws Exception {
        String source = HEADER + PURE + "}}";
        var analysis = SourceComputationSliceTest.analyze(source, false);
        assertTrue(analysis.changed());
        var document = new Document(source); analysis.newEdit().apply(document);
        try (var before = compile(source, "before"); var after = compile(document.get(), "after")) {
            Class<?> left = before.loadClass("Sample"), right = after.loadClass("Sample");
            int[] edges = {Integer.MIN_VALUE, Integer.MAX_VALUE, -1, 0, 1, 0x40000000};
            for (boolean failure : new boolean[]{false,true}) for (int a : edges) for (int b : edges) for (int c : edges)
                assertEquals(outcome(left,a,b,c,failure), outcome(right,a,b,c,failure));
            for(int n=0;n<500;n++) {
                int a=random.nextInt(),b=random.nextInt(),c=random.nextInt();
                assertEquals(outcome(left,a,b,c,false), outcome(right,a,b,c,false));
            }
        }
    }

    @Test void threeSeparatedRegionsCanJoinAtTheirFinalUse() throws Exception {
        String source = HEADER + "int left=a*b; audit(); int right=a*c; audit(); return left+right; }}";
        var analysis=SourceComputationSliceTest.analyze(source,false);
        assertTrue(analysis.changed(),analysis.diagnostics().toString());
        assertEquals(2,analysis.evidence().getFirst().sourcePlan().pinned().size());
        var doc=new Document(source); var undo=analysis.newEdit().apply(doc,org.eclipse.text.edits.TextEdit.CREATE_UNDO);
        assertEquals(2,doc.get().split("audit\\(\\);",-1).length-1); undo.apply(doc); assertEquals(source,doc.get());
    }

    @Test void commentsAndCrLfOutsideTheChangedExpressionsRemainByteExact() throws Exception {
        String source=(HEADER+"\n  int left=a*b; // explanation of left\n  audit(); /* important side effect */\n"
                + "  int right=a*c;\n  return left+right;\n}}").replace("\n","\r\n");
        var analysis=SourceComputationSliceTest.analyze(source,false);
        assertTrue(analysis.changed(),analysis.diagnostics().toString());
        var doc=new Document(source); var undo=analysis.newEdit().apply(doc,org.eclipse.text.edits.TextEdit.CREATE_UNDO);
        assertTrue(doc.get().contains("// explanation of left"));
        assertTrue(doc.get().contains("audit(); /* important side effect */\r\n"));
        undo.apply(doc);assertEquals(source,doc.get());
    }

    @ParameterizedTest
    @ValueSource(strings={"int left=a/b; audit(); int right=a*c; return left+right;",
            "int left=a%b; audit(); int right=a*c; return left+right;",
            "int left=a*b; if(c>0) audit(); int right=a*c; return left+right;",
            "int left=a*b; synchronized(Sample.class) { audit(); } int right=a*c; return left+right;",
            "int left=a*b; java.util.function.IntSupplier s=()->left; audit(); int right=a*c; return left+right;",
            "int left=a*b; audit(); a++; int right=a*c; return left+right;"})
    void unsupportedEffectsOrControlFlowDoNotAcquireASlicePlan(String body) {
        var result=SourceComputationSliceTest.analyze(HEADER+body+"}}",false);
        assertTrue(result.evidence().stream().allMatch(item->item.sourcePlan()==null),result.diagnostics().toString());
    }

    @Test void explicitMathematicalModeKeepsItsExistingBoundaries() {
        var result=SourceComputationSliceTest.analyze(HEADER+PURE+"}}",true);
        assertTrue(result.evidence().stream().allMatch(item->item.sourcePlan()==null));
    }

    private URLClassLoader compile(String source,String name) throws Exception {
        Path dir=Files.createDirectory(temporary.resolve(name));Path file=dir.resolve("Sample.java");Files.writeString(file, source, StandardCharsets.UTF_8);
        var compiler=ToolProvider.getSystemJavaCompiler();assertNotNull(compiler);
        assertEquals(0,compiler.run(null,null,null,"--release","17","-d",dir.toString(),file.toString()));
        return new URLClassLoader(new java.net.URL[]{dir.toUri().toURL()},ClassLoader.getPlatformClassLoader());
    }
    private static List<Object> outcome(Class<?> type,int a,int b,int c,boolean fail) throws Exception {
        type.getField("visits").setInt(null,0);type.getField("fail").setBoolean(null,fail);
        Object outcome;
        try { outcome=type.getMethod("f",int.class,int.class,int.class).invoke(null,a,b,c); }
        catch(InvocationTargetException error) { outcome=error.getCause().getClass().getName()+":"+error.getCause().getMessage(); }
        int calls=type.getField("visits").getInt(null);assertEquals(1,calls);return List.of(outcome,calls);
    }
}
