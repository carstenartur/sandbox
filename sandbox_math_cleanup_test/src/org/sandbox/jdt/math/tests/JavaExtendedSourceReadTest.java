/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import javax.tools.ToolProvider;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.JavaComputationExtractor;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

/** Complete source occurrences execute once; only their resulting primitive values are algebraic. */
@Timeout(180)
class JavaExtendedSourceReadTest {
    @TempDir Path directory;
    private static final String STATE = "public static String log=\"\"; public static int seen; public static int[] out;";
    private static final String ACCESSOR = "static class Reader { int n; int mode; Reader(int mode){this.mode=mode;}"
            + "int next(){log+=++n; if(mode==2 && n==2)throw new IllegalStateException(); return n*17;} }";

    @Test void nestedIntegralLoadsRetainEveryNullBoundsCheckAndAuthoredIndex() throws Exception {
        String source = "public class Calculation {" + STATE
                + "public static long run(int mode,int mask){ int[][] first=mode==1?null:mode==2?new int[0][]:new int[][]{mode==3?null:mode==4?new int[0]:new int[]{-7}};"
                + "int[][] second=mode==5?null:new int[][]{new int[]{31}}; return calculate(first,second,0,mask); }"
                + "static int calculate(int[][] first,int[][] second,int i,int mask){"
                + "int a=first[i + 0][(int)((long)i)] & mask; /* between reads */ int b=second[i][i] & mask; return a ^ b;} }";
        String generated = rewrite(source);
        assertEquals(1, occurrences(generated, "first[i + 0][(int)((long)i)]"));
        assertEquals(1, occurrences(generated, "second[i][i]"));
        assertTrue(generated.contains("/* between reads */"));
        var extraction = new JavaComputationExtractor().extractRuntime(MathTestSupport.parse(source), source, options());
        assertTrue(extraction.regions().stream().anyMatch(r -> r.arrayReads().size()==2));
        compare(source, generated, 6);
    }

    @Test void fieldChainsAreRereadAfterAnOpaqueAccessorMutatesTheReceiver() throws Exception {
        String source = "public class Calculation {" + STATE
                + "static class Node { Node parent; int value; Node(int value){this.value=value;} }"
                + "static class Reader {Node owner; Reader(Node owner){this.owner=owner;} int next(){log+=\"M\"; owner.parent=new Node(99); return 0;}}"
                + "public static long run(int mode,int mask){Node owner=mode==1?null:new Node(1); if(owner!=null)owner.parent=mode==2?null:new Node(3);"
                + "return calculate(owner,new Reader(owner),mask);}"
                + "static int calculate(Node owner,Reader reader,int mask){int a=owner.parent.value & mask;"
                + "int unused=reader.next(); int b=owner.parent.value & mask; return a ^ b;} }";
        String generated = rewrite(source);
        assertEquals(2, occurrences(generated, "owner.parent.value"));
        assertEquals(1, occurrences(generated, "reader.next()"));
        int first = generated.indexOf("owner.parent.value"), call = generated.indexOf("reader.next()");
        assertTrue(first < call && call < generated.lastIndexOf("owner.parent.value"));
        compare(source, generated, 3);
    }

    @Test void objectQualifiedNestedArraysKeepAllReceiverChecks() throws Exception {
        String source = "public class Calculation {" + STATE
                + "static class Node {Node parent; long[][] data;} public static long run(int mode,int mask){"
                + "Node owner=mode==1?null:new Node(); if(owner!=null){owner.parent=mode==2?null:new Node();"
                + "if(owner.parent!=null)owner.parent.data=mode==3?null:new long[][]{mode==4?null:mode==5?new long[0]:new long[]{Long.MIN_VALUE}};}"
                + "return calculate(owner,new long[][]{new long[]{-1}},mask);}"
                + "static long calculate(Node owner,long[][] other,int mask){long a=owner.parent.data[0][0] & mask;"
                + "long b=other[0][0] & mask; return a ^ b;} }";
        String generated = rewrite(source);
        assertEquals(1, occurrences(generated, "owner.parent.data[0][0]"));
        compare(source, generated, 6);
    }

    @Test void repeatedAndDeadAccessorCallsKeepCountOrderAndExceptions() throws Exception {
        String source = "public class Calculation {" + STATE + ACCESSOR
                + "public static long run(int mode,int mask){log=\"\"; return calculate(mode==1?null:new Reader(mode),new Reader(0),mask);}"
                + "static int calculate(Reader first,Reader second,int mask){int unused=first.next();"
                + "int a=first.next() & mask; int b=second.next() & mask; return a ^ b;} }";
        String generated = rewrite(source);
        assertEquals(2, occurrences(generated, "first.next()"));
        assertEquals(1, occurrences(generated, "second.next()"));
        var extraction = new JavaComputationExtractor().extractRuntime(MathTestSupport.parse(source), source, options());
        assertTrue(extraction.regions().stream().anyMatch(r -> r.arrayReads().size()==3 && r.plan().inputs().size()==4));
        compare(source, generated, 3);
        try(var code=compile(generated)) {
            assertEquals("121", outcome(code,0,-1).log());
            assertEquals("12", outcome(code,2,-1).log());
            assertEquals("", outcome(code,1,-1).log());
        }
    }

    @Test void implicitIntegralWrapperUnboxingUsesTheActualPrimitiveKindAndPromotion() throws Exception {
        for (String[] wrapper : List.of(new String[]{"Byte","byte","(byte)129"}, new String[]{"Short","short","(short)32769"},
                new String[]{"Character","char","(char)65535"}, new String[]{"Integer","int","Integer.MIN_VALUE"},
                new String[]{"Long","long","Long.MIN_VALUE"})) {
            String type=wrapper[0], primitive=wrapper[1], result=type.equals("Long")?"long":"int";
            String source="public class Calculation {"+STATE+" public static long run(int mode,int mask){return calculate(mode==1?null:"
                    + type+".valueOf("+wrapper[2]+"),mode==2?null:"+type+".valueOf(("+primitive+")31),mask);}"
                    + "static "+result+" calculate("+type+" first,"+type+" second,int mask){"+result+" a=(first) & mask;"
                    +result+" b=second & mask; return a ^ b;} }";
            var extraction=new JavaComputationExtractor().extractRuntime(MathTestSupport.parse(source), source, options());
            assertTrue(extraction.regions().stream().anyMatch(r -> r.arrayReads().size()==2
                    && r.arrayReads().stream().allMatch(read -> read.kind().name().equals(primitive.toUpperCase()))), type+": "+extraction);
            String generated=rewrite(source);
            compare(source,generated,3);
        }
    }

    @Test void explicitExactWrapperConversionsKeepNullAndNarrowingBehavior() throws Exception {
        for(String[] conversion:List.of(new String[]{"Byte","(byte)-1","longValue","long"},
                new String[]{"Short","(short)-32768","byteValue","int"},new String[]{"Character","(char)65535","charValue","int"},
                new String[]{"Integer","Integer.MIN_VALUE","shortValue","int"},new String[]{"Long","Long.MAX_VALUE","intValue","int"})) {
            String type=conversion[0], method=conversion[2], result=conversion[3];
            String source="public class Calculation {"+STATE+" public static long run(int mode,int mask){return calculate(mode==1?null:"
                    +type+".valueOf("+conversion[1]+"),mode==2?null:"+type+".valueOf("+conversion[1]+"),mask);}"
                    +"static "+result+" calculate("+type+" first,"+type+" second,int mask){"+result+" a=first."+method+"() & mask;"
                    +result+" b=second."+method+"() & mask; return a ^ b;} }";
            String generated=rewrite(source);
            assertEquals(1,occurrences(generated,"first."+method+"()"));
            compare(source,generated,3);
        }
    }

    @Test void finalImpureAccessorFallsBackOnlyAfterTheScalarSummaryFails() throws Exception {
        String source="public class Calculation {"+STATE
                +"static final class Reader {int n; int next(){int temp=1; temp=temp+1; log+=++n; return n*17;} }"
                +"public static long run(int mode,int mask){log=\"\"; return calculate(mode==1?null:new Reader(),mask);}"
                +"static int calculate(Reader reader,int mask){int a=reader.next() & mask; int b=reader.next() & mask; return a ^ b;} }";
        String generated=rewrite(source);
        assertEquals(2,occurrences(generated,"reader.next()"));
        var extraction=new JavaComputationExtractor().extractRuntime(MathTestSupport.parse(source),source,options());
        assertTrue(extraction.regions().stream().anyMatch(r -> r.arrayReads().size()==2 && r.trace().occurrences().size()==3));
        compare(source,generated,2);
    }

    @Test void boxedFieldsArraysAndBoxedAccessorReturnsKeepTheConversionAtTheRead() throws Exception {
        for(String expression:List.of("owner.parent.value","values[0]","reader.next()")) {
            String source="public class Calculation {"+STATE+" static class Node {Node parent; Integer value;}"
                    +"static class Reader {int mode; Reader(int mode){this.mode=mode;} Integer next(){log+=\"B\"; return mode==1?null:Integer.valueOf(-7);}}"
                    +"public static long run(int mode,int mask){log=\"\"; Node owner=new Node(); owner.parent=new Node(); owner.parent.value=mode==1?null:Integer.valueOf(-7);"
                    +"return calculate(owner,new Integer[]{mode==1?null:Integer.valueOf(-7)},new Reader(mode),mask);}"
                    +"static int calculate(Node owner,Integer[] values,Reader reader,int mask){int a="+expression+" & mask; int b="+expression+" & mask; return a ^ b;} }";
            String generated=rewrite(source);
            assertEquals(occurrences(source,expression),occurrences(generated,expression));
            compare(source,generated,2);
        }
    }

    @Test void assignmentUnboxingAndMixedLongShiftPromotionStayPrimitive() throws Exception {
        String source="public class Calculation {"+STATE+" public static long run(int mode,int mask){return calculate(mode==1?null:Byte.valueOf((byte)-1),"
                +"mode==2?null:Long.valueOf(Long.MIN_VALUE),mask);}"
                +"static long calculate(Byte first,Long second,int mask){int a=first; long b=(second >>> first) ^ a; return ((b+mask)+1)-1;} }";
        String generated=rewrite(source);
        compare(source,generated,3);
    }

    @Test void implicitUnboxingCannotBecomeAnUncapturedPrimitiveBoundaryInput() {
        String source="class Calculation {java.math.BigInteger calculate(Integer box,java.math.BigInteger value){"
                +"return value.shiftLeft(box);}}";
        var options=new MathCleanUpOptions(true,Set.of(NumericKind.INT,NumericKind.LONG,NumericKind.BIG_INTEGER),SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.READABILITY,1_000_000,20_000,false,17,List.of(),true);
        var extraction=new JavaComputationExtractor().extractRuntime(MathTestSupport.parse(source),source,options);
        assertTrue(extraction.regions().isEmpty(),extraction.toString());
    }

    @Test void previousDivisionLocalCommitsAndStoreFailuresStayBeforeLaterCalls() throws Exception {
        for(String body:List.of(
                "int divisor=mode==1?0:mask; int a=12/divisor; int b=reader.next(); return ((b+1)-1)+a;",
                "int saved=17; try {saved=mask+1; int a=reader.next() & mask; int b=reader.next() & mask; return a ^ b;}"
                    +"catch(RuntimeException e){seen=saved; throw e;}",
                "int a=reader.next(); out[0]=a; int b=reader.next(); return ((a+mask)+1)-1+b;")) {
            String source="public class Calculation {"+STATE+ACCESSOR
                    +"public static long run(int mode,int mask){log=\"\"; seen=0; out=mode==1?null:mode==3?new int[0]:new int[1]; return calculate(new Reader(mode),mode,mask);}"
                    +"static int calculate(Reader reader,int mode,int mask){"+body+"} }";
            String generated=rewrite(source);
            compare(source,generated,4);
        }
    }

    @Test void retainedHelperArgumentsWithNewReadsAreExplicitBoundaries() throws Exception {
        for(String argument:List.of("reader.next()","box","box.intValue()","values[0][0]")) for(String call:List.of("add","f.applyAsInt")) {
            String source="public class Calculation {"+STATE+ACCESSOR+" static int add(int x,int y){return x+y;}"
                    +"public static long run(int mode,int mask){log=\"\"; return calculate(mode==1?null:new Reader(mode),mode==3?null:Integer.valueOf(5),"
                    +"mode==4?null:new int[][]{new int[]{7}},mask);}"
                    +"static int calculate(Reader reader,Integer box,int[][] values,int mask){java.util.function.IntBinaryOperator f=Calculation::add;"
                    +"int a="+call+"("+argument+",mask); out=null; return (a+1)-1;} }";
            var tasks=new JavaComputationExtractor().extractTasks(MathTestSupport.parse(source),source,options());
            assertTrue(tasks.diagnostics().stream().anyMatch(d -> d.code().contains("RETAINED_CALL_READ_ARGUMENT")), tasks.toString());
            String generated=rewrite(source);
            assertEquals(1,occurrences(generated,call+"("+argument+",mask)"));
            compare(source,generated,5);
        }
    }

    @Test void wrapperIdentityOutputsAndUnsupportedReceiverEffectsRemainOutsideTheNumericModel() {
        for(String body:List.of("Integer a=first; return a;", "return first==second?first:second;")) {
            String source="class Calculation {Integer calculate(Integer first,Integer second){"+body+"}}";
            assertTrue(new JavaComputationExtractor().extractRuntime(MathTestSupport.parse(source),source,options()).regions().isEmpty());
        }
        for(String read:List.of("owner.volatileParent.value", "owner.parent.volatileValue", "owner.parent.data[i++]", "owner.parent.data[8/i]",
                "owner.next().value", "values[values[0][0]][0]", "reader.next(i=1)", "boxed.doubleValue()")) {
            String source="class Calculation {static class Node {Node parent; volatile Node volatileParent; int value; volatile int volatileValue; int[] data; Node next(){return this;}}"
                    +"static class Reader {int next(int x){return x;}} int calculate(Node owner,int[][] values,Reader reader,Double boxed,int i,int mask){return (int)("+read+")+0;} }";
            var extraction=new JavaComputationExtractor().extractRuntime(MathTestSupport.parse(source),source,options());
            assertTrue(extraction.regions().stream().allMatch(r -> r.arrayReads().isEmpty()),read+": "+extraction);
        }
    }

    private static MathCleanUpOptions options() {
        return new MathCleanUpOptions(true,Set.of(NumericKind.BYTE,NumericKind.SHORT,NumericKind.CHAR,NumericKind.INT,NumericKind.LONG),
                SafetyProfile.PRESERVE_JAVA,OptimizationGoal.READABILITY,1_000_000,20_000,false,17,List.of());
    }
    private static String rewrite(String source) throws Exception {
        var analysis=MathematicalAnalysis.analyze(MathTestSupport.parse(source),source,options(),null,-1,0);
        assertTrue(analysis.changed(),analysis.diagnostics().toString());
        assertEquals(analysis.replacements().size(),analysis.evidence().size());
        analysis.evidence().forEach(e -> assertNotNull(e.proof()));
        var document=new Document(source); var undo=analysis.newEdit().apply(document,TextEdit.CREATE_UNDO);
        String generated=document.get(); assertNotEquals(source,generated); MathTestSupport.parse(generated);
        undo.apply(document); assertEquals(source,document.get());
        return generated;
    }
    private void compare(String source,String generated,int modes) throws Exception {
        try(var before=compile(source);var after=compile(generated)) {
            for(int mode=0;mode<modes;mode++) for(int mask:new int[]{0,-1,Integer.MIN_VALUE,Integer.MAX_VALUE,0x55555555})
                assertEquals(outcome(before,mode,mask),outcome(after,mode,mask),"mode="+mode+", mask="+mask);
        }
    }
    private URLClassLoader compile(String source) throws Exception {
        Path root=Files.createTempDirectory(directory,"read-"); Path file=root.resolve("Calculation.java"); Files.writeString(file, source, StandardCharsets.UTF_8);
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","17","-proc:none","-d",root.toString(),file.toString()));
        return new URLClassLoader(new java.net.URL[]{root.toUri().toURL()},ClassLoader.getPlatformClassLoader());
    }
    private record Outcome(Object result,String log,int seen,String stores) { }
    private static Outcome outcome(ClassLoader loader,int mode,int mask) throws Exception {
        Class<?> type=loader.loadClass("Calculation"); Object result;
        try {result=type.getMethod("run",int.class,int.class).invoke(null,mode,mask);}
        catch(InvocationTargetException exception){result=exception.getCause().getClass().getName();}
        return new Outcome(result,(String)type.getField("log").get(null),type.getField("seen").getInt(null),
                java.util.Arrays.toString((int[])type.getField("out").get(null)));
    }
    private static int occurrences(String text,String token) {return text.split(java.util.regex.Pattern.quote(token),-1).length-1;}
}
