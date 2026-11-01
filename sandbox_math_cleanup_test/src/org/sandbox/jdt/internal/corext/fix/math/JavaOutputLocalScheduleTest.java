/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import de.regelsuche.ast.Expr;
import de.regelsuche.ast.VariableExpr;
import de.regelsuche.sdk.optimization.*;
import de.regelsuche.search.program.JointComputationPlan;
import de.regelsuche.search.program.JointComputationPlan.Output;

class JavaOutputLocalScheduleTest {
    @TempDir Path temporary;
    @Test void sharedPreludeNeverReadsAnAuthoredLocalBeforeItsSourceDeclaration() throws Exception {
        Expr twice=op(NumericOperation.MULTIPLY,new VariableExpr("n"),JavaExpressions.literal(2));
        Expr sum=op(NumericOperation.ADD,twice,new VariableExpr("count"));
        var plan=new JointComputationPlan(Map.of("n",NumericKind.INT.type(),"count",NumericKind.INT.type()),Map.of(),List.of(
                new Output("first",NumericKind.INT.type(),twice),
                new Output("second",NumericKind.INT.type(),op(NumericOperation.MULTIPLY,sum,JavaExpressions.literal(3))),
                new Output("third",NumericKind.INT.type(),op(NumericOperation.MULTIPLY,sum,JavaExpressions.literal(5)))));
        var locals=new LinkedHashMap<String,String>(); locals.put("first","startG"); locals.put("second","a"); locals.put("third","b");
        var emission=new JavaComputationEmitter().emit(ComputationOptimizer.prepare(plan),Map.of("n","n","count","count"),Set.of("startG","a","b"),17,locals);
        var body=new StringBuilder(emission.statements());
        locals.forEach((output,name)->body.append("int ").append(name).append(" = ").append(emission.outputValues().get(output)).append(';'));
        String source="public class Sample { public static int[] f(int n,int count) {"+body+"return new int[]{startG,a,b};}}";
        Path file=temporary.resolve("Sample.java"); Files.writeString(file,source);
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","17","-d",temporary.toString(),file.toString()),source);
        try(var loader=new java.net.URLClassLoader(new java.net.URL[]{temporary.toUri().toURL()},ClassLoader.getPlatformClassLoader())) {
            var method=loader.loadClass("Sample").getMethod("f",int.class,int.class);
            assertArrayEquals(new int[]{-2,3,5},(int[])method.invoke(null,Integer.MAX_VALUE,3));
        }
    }
    private static Expr op(NumericOperation operation,Expr a,Expr b) { return JavaExpressions.operation(NumericKind.INT,operation,a,b); }
}
