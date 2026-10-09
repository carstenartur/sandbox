/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.SplittableRandom;
import java.util.Set;
import javax.tools.ToolProvider;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

/** Ordinary Java is the complete mathematical input; no prepared formula or named task. */
class GeneralMathematicsRoundTripTest {
    @TempDir Path temporary;

    @Test void generalDistributionAndCancellationProduceACheaperNonconstantResult() throws Exception {
        String source="public class Calculation { public static int compute(int x,int a,int b) { return x*(a+b)-x*a; }}";
        String generated=rewrite(source);
        assertEquals(1,multiplications(generated),generated);
        compare(source,generated);
    }

    @Test void severalOriginalStatementsUseTheSameGeneralMathSearch() throws Exception {
        String source="""
                public class Calculation {
                    public static int compute(int x,int a,int b) {
                        int left=x*(a+b);
                        // Preserve the programmer's explanation.
                        int removed=x*a;
                        int result=left-removed;
                        return result;
                    }
                }
                """;
        String generated=rewrite(source);
        assertEquals(1,multiplications(generated),generated);
        assertTrue(generated.contains("Preserve the programmer's explanation"),generated);
        compare(source,generated);
    }

    @Test void variableNamesAndGroupingAreNotAnApplicabilitySelector() throws Exception {
        for (int i=0;i<6;i++) {
            String x="position"+i,a="scale"+(i+13),b="input"+(37-i);
            String source="public class Calculation { public static int compute(int "+x+",int "+a+",int "+b+") {"
                    +" int part=("+a+"+"+b+")*"+x+"; int other="+a+"*"+x+"; return part-other; }}";
            String generated=rewrite(source);
            assertEquals(1,multiplications(generated),generated);
            compare(source,generated);
        }
    }

    @Test void authoredConstantCalculationsAreNotCleanupTargets() {
        for (String body:List.of("return 5*7*11;", "int documented=5*7*11; return documented;",
                "return x*(5*7*11);", "int documented=5*7*11; return x;")) {
            String source="public class Calculation { public static int compute(int x,int a,int b) { "+body+" }}";
            var result=analyze(source);
            assertFalse(result.changed(),body+" => "+result.replacements());
        }
    }

    @Test void aConstantResultFoundFromRuntimeVariablesRemainsEligible() throws Exception {
        String source="public class Calculation { public static int compute(int x,int a,int b) { return (x+a)+b-(b+(a+x)); }}";
        String generated=rewrite(source);
        assertEquals(0,multiplications(generated));
        compare(source,generated);
    }

    @Test void integerDivisionDoesNotBecomeFieldDivision() {
        String source="public class Calculation { public static int compute(int x,int a,int b) { return (x*2)/2; }}";
        assertFalse(analyze(source).changed());
    }

    private static MathCleanUpOptions options() {
        return new MathCleanUpOptions(true,Set.of(NumericKind.INT,NumericKind.LONG),SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.LOWER_ESTIMATED_RUNTIME,2_000_000L,20_000,false,17,List.of());
    }
    private static MathematicalAnalysis.Analysis analyze(String source) {
        return MathematicalAnalysis.analyze(MathTestSupport.parse(source),source,options(),new NullProgressMonitor(),-1,0);
    }
    private String rewrite(String source) throws Exception {
        var analysis=analyze(source);
        assertTrue(analysis.changed(),analysis.diagnostics().toString());
        assertFalse(analysis.evidence().isEmpty());
        Document document=new Document(source);
        var undo=analysis.newEdit().apply(document,TextEdit.CREATE_UNDO);
        String generated=document.get();
        undo.apply(document);
        assertEquals(source,document.get());
        assertFalse(generated.contains("de.regelsuche"),generated);
        return generated;
    }
    private static int multiplications(String source) {
        int[] count={0};
        MathTestSupport.parse(source).accept(new ASTVisitor(){
            @Override public boolean visit(InfixExpression expression){
                if(expression.getOperator()==InfixExpression.Operator.TIMES)count[0]++;
                return true;
            }
        });
        return count[0];
    }
    private void compare(String source,String generated)throws Exception{
        Method before=compile(source),after=compile(generated);
        int[] edges={0,1,-1,17,Integer.MIN_VALUE,Integer.MAX_VALUE,1<<30};
        for(int x:edges)for(int a:edges)for(int b:edges)
            assertEquals(before.invoke(null,x,a,b),after.invoke(null,x,a,b),"x="+x+",a="+a+",b="+b);
        SplittableRandom random=new SplittableRandom(1657);
        for(int i=0;i<128;i++){
            int x=random.nextInt(),a=random.nextInt(),b=random.nextInt();
            assertEquals(before.invoke(null,x,a,b),after.invoke(null,x,a,b));
        }
    }
    private Method compile(String source)throws Exception{
        Path directory=Files.createTempDirectory(temporary,"classes-");
        Path file=directory.resolve("Calculation.java");
        Files.writeString(file,source);
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","17","-d",directory.toString(),file.toString()),source);
        try(var loader=new URLClassLoader(new java.net.URL[]{directory.toUri().toURL()},null)){
            return loader.loadClass("Calculation").getMethod("compute",int.class,int.class,int.class);
        }
    }
}
