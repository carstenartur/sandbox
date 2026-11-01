/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaFixedPowerActualTest {
    @TempDir Path directory;
    private Path binary;
    private static String rename(String text){return text.replace("BLS12_381Pairing","Harness").replace("BLS12_381","RenamedCurve").replace("Fp12Element","Canopy").replace("Fp6Element","Branch").replace("Fp2Element","Leaf").replace("ABS_X","TRACE_SIZE").replace("X_MINUS_1_SQ_OVER_3","FIXED_FACTOR").replace(".modPow(",".raise(").replace(" modPow("," raise(").replace("= modPow(","= raise(").replace("return modPow(","return raise(").replace(".mul(",".combine(").replace(" mul("," combine(").replace(".square()",".squared()").replace(" square()"," squared()").replaceAll("\\bc0\\b","amber").replaceAll("\\bc1\\b","blue").replaceAll("\\bc2\\b","cyan");}
    private Path fixture(boolean renamed)throws Exception {
        binary=Path.of(System.getProperty("sandbox.math.bc.jar"));Path archive=Path.of(System.getProperty("sandbox.math.bc.sourceArchive"));
        assertEquals("b37ac84b1d5435ab7b8d166c16ab9f75e09f68f8ec50479bae433939b241b03f",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        Path root=directory.resolve(renamed?"renamed":"actual");Files.createDirectories(root.resolve("org/bouncycastle/crypto/bls"));
        try(var zip=new java.util.zip.ZipFile(archive.toFile())) {
            for(var entry:zip.stream().filter(e->e.getName().startsWith("org/bouncycastle/crypto/bls/") && e.getName().endsWith(".java")).toList()) {
                String relative=entry.getName();String source=new String(zip.getInputStream(zip.getEntry(relative)).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                // JDK BigInteger.modPow is intentionally retained; only value operations are renamed.
                if(renamed)source=rename(source).replace("ySquared.raise(","ySquared.modPow(").replace("negThree.raise(","negThree.modPow(");
                Files.writeString(root.resolve(renamed?rename(relative):relative),source);
            }
        }
        return root;
    }
    private CompilationUnit parse(String source,Path root,String name) {
        var parser=ASTParser.newParser(AST.getJLSLatest());parser.setSource(source.toCharArray());parser.setUnitName(root.resolve("org/bouncycastle/crypto/bls/"+name+".java").toString());parser.setResolveBindings(true);
        parser.setEnvironment(new String[]{binary.toString()},new String[]{root.toString()},new String[]{"UTF-8"},true);
        var compiler=new HashMap<String,String>();JavaCore.setComplianceOptions("17",compiler);parser.setCompilerOptions(compiler);
        var ast=(CompilationUnit)parser.createAST(null);for(var problem:ast.getProblems())assertFalse(problem.isError(),problem.toString());return ast;
    }
    @Test void completeActualPairingBothFixedExponentsApplyCompileReplayAndUndo()throws Exception{exercise(false);}
    @Test void completeRenamedPairingHasTheSamePositiveGates()throws Exception{exercise(true);}
    private void exercise(boolean renamed)throws Exception {
        Path root=fixture(renamed);String name=renamed?"Harness":"BLS12_381Pairing";Path caller=root.resolve("org/bouncycastle/crypto/bls/"+name+".java");String source=Files.readString(caller);
        var environment=new MathematicalAnalysis.SourceEnvironment(caller.toString(),List.of(binary.toString()),List.of(root.toString()),List.of("UTF-8"));
        var options=JavaValueAlgebraProofTest.options();var ast=parse(source,root,name);
        var analysis=MathematicalAnalysis.analyze(ast,source,options,null,-1,0,environment);
        var powers=analysis.evidence().stream().filter(region->region.fixedPower()!=null).toList();assertEquals(1,powers.size(),analysis.diagnostics().toString());
        assertEquals(2,powers.getFirst().fixedPower().chains().size(),analysis.diagnostics().toString());
        String evidenceJson=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(analysis.evidence());
        assertTrue(evidenceJson.length()<1_000_000);assertTrue(evidenceJson.contains("ASSOCIATIVITY"));assertFalse(evidenceJson.contains("originalTrace"));
        var exponents=powers.getFirst().fixedPower().chains().stream().map(FixedPowerEvidence.Chain::exponent).collect(java.util.stream.Collectors.toSet());
        BigInteger x=new BigInteger("d201000000010000",16);assertEquals(Set.of(x,x.negate().subtract(BigInteger.ONE).pow(2).divide(BigInteger.valueOf(3))),exponents);
        var document=new Document(source);var undo=analysis.newEdit().apply(document);String generated=document.get();undo.apply(document);assertEquals(source,document.get());
        assertEquals(execute(root,name,source,renamed),execute(root,name,generated,renamed));
        String changed=generated.replace("// Mathematics fixed power:","// altered helper\n// Mathematics fixed power:");
        assertThrows(IllegalArgumentException.class,()->JavaFixedPowerOptimizer.verifyCombined(parse(changed,root,name),changed,powers.getFirst(),options,new JavaValueSourceSummary.Work(options.workBudget(),options,()->false)));
        Path helper=root.resolve("org/bouncycastle/crypto/bls/"+(renamed?"Leaf":"Fp2Element")+".java");Files.writeString(helper,Files.readString(helper).replace("ac.subtract(bd)","ac.add(bd)"));
        assertThrows(IllegalArgumentException.class,analysis::newEdit);
    }
    private List<String> execute(Path root,String name,String callerSource,boolean renamed)throws Exception {
        Path output=Files.createTempDirectory(directory,"classes");Path caller=root.resolve("org/bouncycastle/crypto/bls/"+name+".java");Files.writeString(caller,callerSource);
        var command=new ArrayList<>(List.of("--release","17","-cp",binary.toString(),"-d",output.toString()));
        try(var paths=Files.walk(root)){paths.filter(path->path.toString().endsWith(".java")).forEach(path->command.add(path.toString()));}
        assertEquals(0,javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,command.toArray(String[]::new)));
        try(var loader=new JavaImmutableValueTest.IsolatedBC(output,binary)) {
            var callerType=loader.loadClass("org.bouncycastle.crypto.bls."+name);var type=loader.loadClass("org.bouncycastle.crypto.bls."+(renamed?"Canopy":"Fp12Element"));
            var small=callerType.getDeclaredMethod("expByX",type);small.setAccessible(true);var hard=callerType.getMethod("hardPart",type);BigInteger modulus=(BigInteger)loader.loadClass("org.bouncycastle.crypto.bls."+(renamed?"Leaf":"Fp2Element")).getField("P").get(null);var results=new ArrayList<String>();
            for(int example=0;example<6;example++) {
                var leaves=new ArrayList<BigInteger>();for(int i=0;i<12;i++)leaves.add(example==0?BigInteger.ZERO:example==1?BigInteger.ONE:example==4?modulus.subtract(BigInteger.ONE):example==5?modulus:BigInteger.valueOf((i+1L)*(example==2?3:-7)));
                Object value=construct(type,leaves.iterator());
                for(var method:List.of(small,hard))try{Object result=method.invoke(null,value);assertNotSame(value,result);var flattened=new ArrayList<BigInteger>();flatten(result,flattened);results.add(flattened.toString());}catch(java.lang.reflect.InvocationTargetException failure){results.add(failure.getCause().getClass().getName());}
            }
            return results;
        }
    }
    private static Object construct(Class<?> type,Iterator<BigInteger> leaves)throws Exception {if(type==BigInteger.class)return leaves.next();var constructor=type.getDeclaredConstructors()[0];constructor.setAccessible(true);var arguments=new ArrayList<Object>();for(var parameter:constructor.getParameterTypes())arguments.add(construct(parameter,leaves));return constructor.newInstance(arguments.toArray());}
    private static void flatten(Object value,List<BigInteger> leaves)throws Exception {if(value instanceof BigInteger integer){leaves.add(integer);return;}for(var field:value.getClass().getDeclaredFields())if(!java.lang.reflect.Modifier.isStatic(field.getModifiers())){field.setAccessible(true);flatten(field.get(value),leaves);}}
}
