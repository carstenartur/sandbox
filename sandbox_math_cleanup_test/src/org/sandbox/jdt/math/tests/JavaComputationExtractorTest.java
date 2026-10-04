/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 at https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.math.tests;

import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import javax.tools.ToolProvider;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.JavaComputationExtractor;
import org.sandbox.jdt.internal.corext.fix.math.JavaComputationRegion;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;

class JavaComputationExtractorTest {
   static MathCleanUpOptions options(Set<NumericKind> var0, SafetyProfile var1) {
      return new MathCleanUpOptions(true, var0, var1, OptimizationGoal.READABILITY, 100000L, 2000, var1 == SafetyProfile.CHECKED_THROW, 17, List.of());
   }

   @Test
   void retainsTwoOutputsAndOriginalOperationOccurrencesAcrossStatements() {
      String var1 = "class Calculation {\n  static int compute(int x) {\n    int a = x + 1;\n    int b = a - 1;\n    return a ^ b;\n  }\n}\n";
      JavaComputationExtractor.Extraction var2 = new JavaComputationExtractor()
         .extract(MathTestSupport.parse(var1), var1, options(Set.of(NumericKind.INT), SafetyProfile.PRESERVE_JAVA));
      Assertions.assertEquals(1, var2.regions().size(), var2.diagnostics().toString());
      JavaComputationRegion var3 = (JavaComputationRegion)var2.regions().getFirst();
      Assertions.assertEquals(List.of("a", "b"), var3.outputs().stream().map(var0 -> var0.javaName()).toList());
      Assertions.assertEquals(2, var3.trace().occurrences().size());
      Assertions.assertEquals(2, var3.plan().outputs().size());
      Assertions.assertEquals(Set.of("x"), Set.copyOf(var3.inputNames().values()));
   }

   @Test
   void bigintFilterDoesNotAbsorbPrimitiveProductPassedToValueOf() {
      String var1 = "import java.math.BigInteger;\nclass Calculation {\n  static int compute(int a, int b) {\n    BigInteger input = BigInteger.valueOf(a * b);\n    BigInteger sum = input.add(BigInteger.ONE);\n    BigInteger result = sum.subtract(BigInteger.ONE);\n    return result.intValue();\n  }\n}\n";
      JavaComputationExtractor.Extraction var2 = new JavaComputationExtractor()
         .extract(MathTestSupport.parse(var1), var1, options(Set.of(NumericKind.BIG_INTEGER), SafetyProfile.PRESERVE_JAVA));
      Assertions.assertEquals(1, var2.regions().size(), var2.diagnostics().toString());
      JavaComputationRegion var3 = (JavaComputationRegion)var2.regions().getFirst();
      Assertions.assertTrue(var3.start() > var1.indexOf("a * b"));
      Assertions.assertTrue(var3.trace().occurrences().stream().allMatch(var0 -> var0.evaluatedKind() == NumericKind.BIG_INTEGER));
   }

   @Test
   void promotionsDistinguishCastAfterMultiplyFromMultiplyAfterCast() {
      String var1 = "class Calculation {\n  static long compute(int a, int b) {\n    long narrow = (long)(a * b);\n    long wide = (long)a * b;\n    return narrow ^ wide;\n  }\n}\n";
      JavaComputationExtractor.Extraction var2 = new JavaComputationExtractor()
         .extract(MathTestSupport.parse(var1), var1, options(Set.of(NumericKind.INT, NumericKind.LONG), SafetyProfile.PRESERVE_JAVA));
      Assertions.assertEquals(1, var2.regions().size(), var2.diagnostics().toString());
      List var3 = ((JavaComputationRegion)var2.regions().getFirst()).plan().outputExpressions();
      Assertions.assertNotEquals(var3.get(0), var3.get(1));
      Assertions.assertTrue(
         ((JavaComputationRegion)var2.regions().getFirst()).trace().occurrences().stream().anyMatch(var0 -> var0.evaluatedKind() == NumericKind.INT)
      );
      Assertions.assertTrue(
         ((JavaComputationRegion)var2.regions().getFirst()).trace().occurrences().stream().anyMatch(var0 -> var0.evaluatedKind() == NumericKind.LONG)
      );
   }

   @Test
   void rejectsUnknownDispatchAndIdentityEscapes() {
      for (String var2 : List.of(
         "BigInteger a=x.add(BigInteger.ONE); BigInteger b=a.subtract(BigInteger.ONE); return b;",
         "BigInteger a=BigInteger.valueOf(4).add(BigInteger.ONE); BigInteger b=a.subtract(BigInteger.ONE); return b == x ? b : x;"
      )) {
         String var3 = "import java.math.BigInteger; class Calculation { BigInteger compute(BigInteger x) {" + var2 + "}}";
         JavaComputationExtractor.Extraction var4 = new JavaComputationExtractor()
            .extract(MathTestSupport.parse(var3), var3, options(Set.of(NumericKind.BIG_INTEGER), SafetyProfile.PRESERVE_JAVA));
         Assertions.assertTrue(var4.regions().isEmpty(), "unsafe BigInteger region accepted");
         Assertions.assertFalse(var4.diagnostics().isEmpty());
      }
   }

   @Test
   void excludesEffectsAndPartialOutputOnExceptionPaths() {
      String var1 = "class Calculation {\n  int calls;\n  int next() { return calls++; }\n  int compute(int x) {\n    int output = 0;\n    try { output = x + 1; output = output / next(); }\n    finally { calls = output; }\n    return output;\n  }\n}\n";
      JavaComputationExtractor.Extraction var2 = new JavaComputationExtractor()
         .extract(MathTestSupport.parse(var1), var1, options(Set.of(NumericKind.INT), SafetyProfile.CHECKED_THROW));
      Assertions.assertTrue(var2.regions().isEmpty());
      Assertions.assertFalse(var2.diagnostics().isEmpty());
   }

   @Test
   void byteOperandsRetainTheirIntPromotionAndNarrowing() {
      String var1 = "class Calculation { int compute(byte x, byte y) { byte a=(byte)(x+y); int b=a+1; return b; }}";
      JavaComputationExtractor.Extraction var2 = new JavaComputationExtractor()
         .extract(MathTestSupport.parse(var1), var1, options(Set.of(NumericKind.BYTE, NumericKind.INT), SafetyProfile.PRESERVE_JAVA));
      Assertions.assertEquals(1, var2.regions().size(), var2.diagnostics().toString());
      Assertions.assertEquals(
         NumericKind.BYTE, ((JavaComputationRegion.OutputBinding)((JavaComputationRegion)var2.regions().getFirst()).outputs().getFirst()).declaredKind()
      );
      Assertions.assertTrue(
         ((JavaComputationRegion)var2.regions().getFirst()).trace().occurrences().stream().anyMatch(var0 -> var0.evaluatedKind() == NumericKind.INT)
      );
   }

   @Test
   void compoundAssignmentsRecordPromotionAndImplicitNarrowing() {
      String var1 = "class Calculation { byte compute(byte x, int distance) { byte value=x; value += 1; value <<= distance; return value; }}";
      JavaComputationExtractor.Extraction var2 = new JavaComputationExtractor()
         .extract(MathTestSupport.parse(var1), var1, options(Set.of(NumericKind.BYTE, NumericKind.INT), SafetyProfile.PRESERVE_JAVA));
      Assertions.assertEquals(1, var2.regions().size(), var2.diagnostics().toString());
      JavaComputationRegion var3 = (JavaComputationRegion)var2.regions().getFirst();
      Assertions.assertEquals(3, var3.outputs().size());
      Assertions.assertTrue(var3.trace().occurrences().stream().anyMatch(var0 -> var0.evaluatedKind() == NumericKind.INT));
      Assertions.assertTrue(var3.trace().occurrences().stream().anyMatch(var0 -> var0.evaluatedKind() == NumericKind.BYTE));
   }

   @Test
   void staticFactoriesWithEffectfulQualifiersAreBoundaries() {
      String var1 = "import java.math.BigInteger;\nclass Calculation {\n  static int calls;\n  static BigInteger next() { calls++; return null; }\n  static int compute() {\n    BigInteger value=next().valueOf(5);\n    BigInteger result=value.add(BigInteger.ZERO);\n    return result.intValue();\n  }\n}\n";
      JavaComputationExtractor.Extraction var2 = new JavaComputationExtractor()
         .extract(MathTestSupport.parse(var1), var1, options(Set.of(NumericKind.BIG_INTEGER), SafetyProfile.PRESERVE_JAVA));
      Assertions.assertTrue(var2.diagnostics().stream().anyMatch(var0 -> var0.code().equals("STATIC_CALL_EFFECTFUL_QUALIFIER")));
      Assertions.assertTrue(var2.regions().stream().allMatch(var1x -> var1x.start() > var1.indexOf("next().valueOf(5)")));
   }

   @Test
   void valuePassedToPotentiallyOverriddenMethodDoesNotProveValueOnlyUse() {
      String var1 = "import java.math.BigInteger; class Calculation { boolean compute(BigInteger observer) { BigInteger value=BigInteger.valueOf(5).add(BigInteger.ZERO); return observer.equals(value); }}";
      JavaComputationExtractor.Extraction var2 = new JavaComputationExtractor()
         .extract(MathTestSupport.parse(var1), var1, options(Set.of(NumericKind.BIG_INTEGER), SafetyProfile.PRESERVE_JAVA));
      Assertions.assertTrue(var2.regions().isEmpty());
      Assertions.assertTrue(var2.diagnostics().stream().anyMatch(var0 -> var0.code().equals("BIG_INTEGER_IDENTITY_OR_ESCAPE")));
   }

   @Test
   void identityEscapesThroughBigIntegerReturningReceiverChainsAreRejected() {
      String var1 = "import java.math.BigInteger;\nclass Calculation {\n  BigInteger[] compute(int x) {\n    BigInteger input=BigInteger.valueOf(x);\n    BigInteger a=input.add(BigInteger.ONE);\n    BigInteger b=input.add(BigInteger.ONE);\n    return new BigInteger[]{a.add(BigInteger.ZERO),b.add(BigInteger.ZERO)};\n  }\n}\n";
      JavaComputationExtractor.Extraction var2 = new JavaComputationExtractor()
         .extract(MathTestSupport.parse(var1), var1, options(Set.of(NumericKind.BIG_INTEGER), SafetyProfile.PRESERVE_JAVA));
      Assertions.assertTrue(var2.regions().isEmpty());
      Assertions.assertTrue(var2.diagnostics().stream().anyMatch(var0 -> var0.code().equals("BIG_INTEGER_IDENTITY_OR_ESCAPE")));
   }

   @Test
   void externalConstantsAreNotFrozenWithoutDependencyEvidence(@TempDir Path var1) throws Exception {
      Path var2 = var1.resolve("Other.java");
      Files.writeString(var2, "public class Other { public static final int K=1; }");
      Assertions.assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "17", "-d", var1.toString(), var2.toString()));
      String var3 = "class Calculation { int compute() { int result=Other.K+0; return result; }}";
      JavaComputationExtractor.Extraction var4 = new JavaComputationExtractor()
         .extract(MathTestSupport.parse(var3, new String[]{var1.toString()}), var3, options(Set.of(NumericKind.INT), SafetyProfile.PRESERVE_JAVA));
      Assertions.assertTrue(var4.regions().isEmpty());
      Assertions.assertTrue(var4.diagnostics().stream().anyMatch(var0 -> var0.code().equals("EXTERNAL_CONSTANT_BINDING")));
      String var5 = "import java.math.BigInteger; class Calculation { int compute() { BigInteger result=BigInteger.valueOf(Other.K).add(BigInteger.ONE).subtract(BigInteger.ONE); return result.intValue(); }}";
      JavaComputationExtractor.Extraction var6 = new JavaComputationExtractor()
         .extract(MathTestSupport.parse(var5, new String[]{var1.toString()}), var5, options(Set.of(NumericKind.BIG_INTEGER), SafetyProfile.PRESERVE_JAVA));
      Assertions.assertTrue(var6.regions().isEmpty());
      Assertions.assertTrue(var6.diagnostics().stream().anyMatch(var0 -> var0.code().equals("EXTERNAL_CONSTANT_BINDING")));

      for (String var8 : List.of(
         "static final int K=Other.K; int compute() { int result=K+0; return result; }",
         "int compute() { final int K=Other.K; int result=K+0; return result; }"
      )) {
         String var9 = "class Calculation { " + var8 + " }";
         JavaComputationExtractor.Extraction var10 = new JavaComputationExtractor()
            .extract(MathTestSupport.parse(var9, new String[]{var1.toString()}), var9, options(Set.of(NumericKind.INT), SafetyProfile.PRESERVE_JAVA));
         Assertions.assertTrue(var10.regions().isEmpty(), var8);
      }
   }

   @Test
   void unresolvedBindingsNeverBecomeInputs() {
      String var1 = "class Calculation { int f() { int a=unknown+1; int b=a-1; return b; }}";
      ASTParser var2 = ASTParser.newParser(AST.getJLSLatest());
      var2.setSource(var1.toCharArray());
      CompilationUnit var3 = (CompilationUnit)var2.createAST(null);
      JavaComputationExtractor.Extraction var4 = new JavaComputationExtractor()
         .extract(var3, var1, options(Set.of(NumericKind.INT), SafetyProfile.PRESERVE_JAVA));
      Assertions.assertTrue(var4.regions().isEmpty());
      Assertions.assertFalse(var4.diagnostics().isEmpty());
   }
}
