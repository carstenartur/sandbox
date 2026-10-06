/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 at https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.math.tests;

import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.SafetyProfile;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import javax.tools.ToolProvider;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.UndoEdit;
import org.eclipse.text.edits.TextEdit;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

class JavaComputationRoundTripTest {
   @TempDir
   Path temporary;

	@Test
	void generatedLinesKeepSourceIndentationAndDelimitersWithExactUndo() throws Exception {
		for (String indentation : new String[] { "    ", "\t\t" }) { //$NON-NLS-1$ //$NON-NLS-2$
			for (String delimiter : new String[] { "\n", "\r\n" }) { //$NON-NLS-1$ //$NON-NLS-2$
				for (SafetyProfile safety : SafetyProfile.values()) {
					for (int targetJava : new int[] { 8, 17 }) {
						String source= String.join(delimiter, "import java.math.BigInteger;", "public class Calculation {", //$NON-NLS-1$ //$NON-NLS-2$
								"  public static int compute(int x) {", indentation + "int a=(x+1)-1;", //$NON-NLS-1$ //$NON-NLS-2$
								indentation + "// Keep this user's comment.", indentation + "int b=a+0;", //$NON-NLS-1$ //$NON-NLS-2$
								indentation + "return a+b;", "  }", "}", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
						var base= JavaComputationExtractorTest.options(Set.of(NumericKind.INT), safety);
						var options= new org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions(base.enabled(), base.kinds(), base.safety(),
								base.goal(), base.workBudget(), base.maxStates(), base.checkedOptIn(), targetJava, base.exclusions());
						var analysis= MathematicalAnalysis.analyze(MathTestSupport.parse(source), source, options, new NullProgressMonitor(), -1, 0);
						assertTrue(analysis.changed(), analysis.diagnostics().toString());
						for (var edit : analysis.replacements()) {
							String[] lines= edit.replacement().split("\\R", -1); //$NON-NLS-1$
							for (int line= 1; line < lines.length; line++)
								if (!lines[line].isBlank()) assertTrue(lines[line].startsWith(indentation), edit.replacement());
							if (delimiter.equals("\r\n")) assertFalse(edit.replacement().replace("\r\n", "").contains("\n")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
						}
						Document document= new Document(source);
						var undo= analysis.newEdit().apply(document, TextEdit.CREATE_UNDO);
						assertTrue(document.get().startsWith("import java.math.BigInteger;" + delimiter)); //$NON-NLS-1$
						assertTrue(document.get().contains(delimiter + indentation + "// Keep this user's comment." + delimiter)); //$NON-NLS-1$
						assertEquals(12, invoke(document.get(), int.class, 6, targetJava));
						undo.apply(document);
						assertEquals(source, document.get());
					}
				}
			}
		}
	}

   @Test
   void bigintDefaultKeepsPrimitivePrecomputationAndOptimizesBoundValues() throws Exception {
      String var1 = "import java.math.BigInteger;\npublic class Calculation {\n  public static int compute(int x) {\n    BigInteger input = BigInteger.valueOf(x * 2);\n    BigInteger sum = input.add(BigInteger.ONE);\n    BigInteger result = sum.subtract(BigInteger.ONE);\n    return result.intValue();\n  }\n}\n";
      MathematicalAnalysis.Analysis var2 = MathematicalAnalysis.analyze(
         MathTestSupport.parse(var1),
         var1,
         JavaComputationExtractorTest.options(Set.of(NumericKind.BIG_INTEGER), SafetyProfile.PRESERVE_JAVA),
         new NullProgressMonitor(),
         -1,
         0
      );
      Assertions.assertTrue(var2.changed(), var2.diagnostics().toString());
      Document var3 = new Document(var1);
      var2.newEdit().apply(var3);
      Assertions.assertTrue(var3.get().contains("BigInteger.valueOf(x * 2)"));
      Assertions.assertEquals(-2, this.invoke(var3.get(), 2147483647));
   }

   @Test
   void emitsCompilableMultipleOutputsWithCommentsAndExactUndo() throws Exception {
      String var1 = "public class Calculation {\n  public static int compute(int x) {\n    int _math0 = 7;\n    int a = (x + 1) - 1;\n    // This comment belongs to the user's computation.\n    int b = a + 0;\n    return (a ^ b) + _math0;\n  }\n}\n";
      MathCleanUpOptions var2 = JavaComputationExtractorTest.options(Set.of(NumericKind.INT), SafetyProfile.PRESERVE_JAVA);
      MathematicalAnalysis.Analysis var3 = MathematicalAnalysis.analyze(MathTestSupport.parse(var1), var1, var2, new NullProgressMonitor(), -1, 0);
      Assertions.assertTrue(var3.changed(), var3.diagnostics().toString());
      Document var4 = new Document(var1);
      UndoEdit var5 = var3.newEdit().apply(var4, 1);
      String var6 = var4.get();
      Assertions.assertTrue(var6.contains("// This comment belongs to the user's computation."));
      Assertions.assertEquals(7, this.invoke(var6, 2147483647));
      Assertions.assertEquals(7, this.invoke(var6, -2147483648));
      var5.apply(var4);
      Assertions.assertEquals(var1, var4.get());
      MathematicalAnalysis.Analysis var7 = MathematicalAnalysis.analyze(MathTestSupport.parse(var6), var6, var2, new NullProgressMonitor(), -1, 0);
      Assertions.assertFalse(var7.changed(), var7.diagnostics().toString());
   }

   @Test
   void doesNotCancelOverflowingMultiplyThenDivideInPreserveProfile() throws Exception {
      String var1 = "public class Calculation { public static int compute(int x) { int result=(x*2)/2; return result; }}";
      MathematicalAnalysis.Analysis var2 = MathematicalAnalysis.analyze(
         MathTestSupport.parse(var1),
         var1,
         JavaComputationExtractorTest.options(Set.of(NumericKind.INT), SafetyProfile.PRESERVE_JAVA),
         new NullProgressMonitor(),
         -1,
         0
      );
      Assertions.assertFalse(var2.changed());
      Assertions.assertEquals(-1, this.invoke(var1, 2147483647));
   }

   @Test
   void checkedProfileRetainsAnEliminatedOverflowObligation() throws Exception {
      String var1 = "public class Calculation { public static int compute(int x) { int result=(x+1)-1; return result; }}";
      MathematicalAnalysis.Analysis var2 = MathematicalAnalysis.analyze(
         MathTestSupport.parse(var1),
         var1,
         JavaComputationExtractorTest.options(Set.of(NumericKind.INT), SafetyProfile.CHECKED_THROW),
         new NullProgressMonitor(),
         -1,
         0
      );
      Assertions.assertTrue(var2.changed(), var2.diagnostics().toString());
      Document var3 = new Document(var1);
      var2.newEdit().apply(var3);
      Assertions.assertEquals(6, this.invoke(var3.get(), 6));
      InvocationTargetException var4 = (InvocationTargetException)Assertions.assertThrows(
         InvocationTargetException.class, () -> this.invoke(var3.get(), 2147483647)
      );
      Assertions.assertInstanceOf(ArithmeticException.class, var4.getCause());
      Assertions.assertTrue(((MathematicalAnalysis.Replacement)var2.replacements().getFirst()).description().contains("CHECKED_THROW"));
   }

   @Test
   void compoundAssignmentsBecomeExplicitAssignmentsWithTheSameResult() throws Exception {
      String var1 = "public class Calculation { public static int compute(int x) { int result=x; result += 0; result ^= 0; return result; }}";
      MathematicalAnalysis.Analysis var2 = MathematicalAnalysis.analyze(
         MathTestSupport.parse(var1),
         var1,
         JavaComputationExtractorTest.options(Set.of(NumericKind.INT), SafetyProfile.PRESERVE_JAVA),
         new NullProgressMonitor(),
         -1,
         0
      );
      Assertions.assertTrue(var2.changed(), var2.diagnostics().toString());
      Document var3 = new Document(var1);
      var2.newEdit().apply(var3);
      Assertions.assertEquals(2147483647, this.invoke(var3.get(), 2147483647));
      Assertions.assertEquals(-2147483648, this.invoke(var3.get(), -2147483648));
   }

   @Test
   void noOverflowIsNotAnIeeeEquivalenceProof() {
      String var1 = "class Calculation { double compute(double x) { double result=(x+1.0)-x; return result; }}";
      MathematicalAnalysis.Analysis var2 = MathematicalAnalysis.analyze(
         MathTestSupport.parse(var1),
         var1,
         JavaComputationExtractorTest.options(Set.of(NumericKind.DOUBLE), SafetyProfile.PRESERVE_JAVA),
         new NullProgressMonitor(),
         -1,
         0
      );
      Assertions.assertFalse(var2.changed());
      Assertions.assertEquals(0.0, 0.0);
   }

   @Test
   void preserveFloatingPointRequiresAProvedCanonicalObservationBoundary() throws Exception {
      String var1 = "public class Calculation { public static long compute(double x) { double result=x*1.0; return Double.doubleToLongBits(result); }}";
      MathCleanUpOptions var2 = JavaComputationExtractorTest.options(Set.of(NumericKind.DOUBLE), SafetyProfile.PRESERVE_JAVA);
      MathematicalAnalysis.Analysis var3 = MathematicalAnalysis.analyze(MathTestSupport.parse(var1), var1, var2, new NullProgressMonitor(), -1, 0);
      Assertions.assertTrue(var3.changed(), var3.diagnostics().toString());
      Document var4 = new Document(var1);
      var3.newEdit().apply(var4);

      for (double var8 : new double[]{-0.0, 0.0, 1.0E16, 5.0E-324, 1.7976931348623157E308, 1.0 / 0.0, 0.0 / 0.0}) {
         Assertions.assertEquals(Double.doubleToLongBits(var8 * 1.0), this.invoke(var4.get(), double.class, var8));
      }

      String var10 = var1.replace("doubleToLongBits", "doubleToRawLongBits");
      Assertions.assertFalse(MathematicalAnalysis.analyze(MathTestSupport.parse(var10), var10, var2, new NullProgressMonitor(), -1, 0).changed());
   }

   @Test
   void analysisRejectsAnAstForDifferentTextEvenWithTheSameLength() {
      String var1 = "class Calculation { int compute(int x) { int result=(x+1)-1; return result; }}";
      String var2 = var1.replace("(x+1)-1", "(x+2)-1");
      MathematicalAnalysis.Analysis var3 = MathematicalAnalysis.analyze(
         MathTestSupport.parse(var1),
         var2,
         JavaComputationExtractorTest.options(Set.of(NumericKind.INT), SafetyProfile.PRESERVE_JAVA),
         new NullProgressMonitor(),
         -1,
         0
      );
      Assertions.assertFalse(var3.changed());
      Assertions.assertTrue(var3.diagnostics().stream().anyMatch(var0 -> var0.code().equals("STALE_AST_SOURCE")));
   }

   @Test
   void guardedFinalVarUsesAnExplicitTypeAndRetainsFallback() throws Exception {
      String var1 = "import java.math.BigInteger;\npublic class Calculation {\n  public static int compute(BigInteger x) {\n    final var a=x.add(BigInteger.ONE);\n    final var b=a.subtract(BigInteger.ONE);\n    return b.intValue();\n  }\n}\n";
      MathematicalAnalysis.Analysis var2 = MathematicalAnalysis.analyze(
         MathTestSupport.parse(var1),
         var1,
         JavaComputationExtractorTest.options(Set.of(NumericKind.BIG_INTEGER), SafetyProfile.GUARDED_FALLBACK),
         new NullProgressMonitor(),
         -1,
         0
      );
      Assertions.assertTrue(var2.changed(), var2.diagnostics().toString());
      Document var3 = new Document(var1);
      var2.newEdit().apply(var3);
      Assertions.assertEquals(37, this.invoke(var3.get(), BigInteger.class, BigInteger.valueOf(37L)));
      InvocationTargetException var4 = (InvocationTargetException)Assertions.assertThrows(
         InvocationTargetException.class, () -> this.invoke(var3.get(), BigInteger.class, null)
      );
      Assertions.assertInstanceOf(NullPointerException.class, var4.getCause());
      Assertions.assertFalse(((MathematicalAnalysis.VerifiedRegion)var2.evidence().getFirst()).cost().estimatedRuntimeImprovement());
      Assertions.assertTrue(((MathematicalAnalysis.VerifiedRegion)var2.evidence().getFirst()).cost().checkWork() >= 5L);
   }

   @Test
   void receiverGuardCannotBypassIntegralRangeObligationsInMixedRegion() {
      String var1 = "import java.math.BigInteger;\nclass Calculation {\n  int compute(BigInteger x, int y) {\n    BigInteger a=x.add(BigInteger.ONE).subtract(BigInteger.ONE);\n    int b=(y*2)/2;\n    return a.intValue()+b;\n  }\n}\n";
      MathematicalAnalysis.Analysis var2 = MathematicalAnalysis.analyze(
         MathTestSupport.parse(var1),
         var1,
         JavaComputationExtractorTest.options(Set.of(NumericKind.BIG_INTEGER, NumericKind.INT), SafetyProfile.GUARDED_FALLBACK),
         new NullProgressMonitor(),
         -1,
         0
      );
      Assertions.assertFalse(var2.changed());
      Assertions.assertTrue(var2.diagnostics().stream().anyMatch(var0 -> var0.message().contains("MIXED_RECEIVER_NUMERIC_GUARD_UNSUPPORTED")));
   }

   @Test
   void generatedWrapperMustPreserveConstantVariableUseOutsideTheRegion() {
      String var1 = "class Calculation { int compute(int x) { final int N=1+0; switch(x) { case N: return 5; default: return 7; } }}";
      MathematicalAnalysis.Analysis var2 = MathematicalAnalysis.analyze(
         MathTestSupport.parse(var1),
         var1,
         JavaComputationExtractorTest.options(Set.of(NumericKind.INT), SafetyProfile.GUARDED_FALLBACK),
         new NullProgressMonitor(),
         -1,
         0
      );
      Assertions.assertFalse(var2.changed());
      Assertions.assertTrue(var2.diagnostics().stream().anyMatch(var0 -> var0.message().contains("GENERATED_REGION_INVALID")));
   }

   @Test
   void separateRegionsInTheSameBlockUseDisjointTemporaryNames() throws Exception {
      String var1 = "public class Calculation {\n  static int calls;\n  public static int compute(int x) {\n    calls=0;\n    int a=(x+1)-1+x*2;\n    calls++;\n    int b=(x+2)-2+x*3;\n    return a+b+calls;\n  }\n}\n";
      MathematicalAnalysis.Analysis var2 = MathematicalAnalysis.analyze(
         MathTestSupport.parse(var1),
         var1,
         JavaComputationExtractorTest.options(Set.of(NumericKind.INT), SafetyProfile.PRESERVE_JAVA),
         new NullProgressMonitor(),
         -1,
         0
      );
      Assertions.assertEquals(2, var2.replacements().size(), var2.diagnostics().toString());
      Document var3 = new Document(var1);
      var2.newEdit().apply(var3);
      Assertions.assertEquals(50, this.invoke(var3.get(), 7));
   }

   @Test
   void bigIntegerSupportedMagnitudeExceptionCannotBeEliminated() throws Exception {
      String var1 = "import java.math.BigInteger; public class Calculation { public static int compute(int x) { BigInteger input=BigInteger.valueOf(x); BigInteger result=input.pow(Integer.MAX_VALUE).subtract(input.pow(Integer.MAX_VALUE)); return result.intValue(); }}";
      MathematicalAnalysis.Analysis var2 = MathematicalAnalysis.analyze(
         MathTestSupport.parse(var1),
         var1,
         JavaComputationExtractorTest.options(Set.of(NumericKind.BIG_INTEGER), SafetyProfile.PRESERVE_JAVA),
         new NullProgressMonitor(),
         -1,
         0
      );
      Assertions.assertFalse(var2.changed());
      Assertions.assertTrue(var2.diagnostics().stream().anyMatch(var0 -> var0.message().contains("BIG_INTEGER_SUPPORTED_RANGE_NOT_PROVED")));
      InvocationTargetException var3 = (InvocationTargetException)Assertions.assertThrows(InvocationTargetException.class, () -> this.invoke(var1, 4));
      Assertions.assertInstanceOf(ArithmeticException.class, var3.getCause());
   }

   @Test
   void unknownBigIntegerSubclassDispatchUsesTheOriginalFallbackOnce() throws Exception {
      String var1 = "import java.math.BigInteger;\npublic class Calculation {\n  static int calls;\n  static class Child extends BigInteger {\n    Child(int x) { super(Integer.toString(x)); }\n    @Override public BigInteger add(BigInteger other) { calls++; return super.add(other).add(BigInteger.ONE); }\n  }\n  public static int compute(int x) {\n    calls=0;\n    BigInteger input=new Child(x);\n    BigInteger a=input.add(BigInteger.ONE);\n    BigInteger b=a.subtract(BigInteger.ONE);\n    return b.intValue()*10+calls;\n  }\n}\n";
      MathematicalAnalysis.Analysis var2 = MathematicalAnalysis.analyze(
         MathTestSupport.parse(var1),
         var1,
         JavaComputationExtractorTest.options(Set.of(NumericKind.BIG_INTEGER), SafetyProfile.GUARDED_FALLBACK),
         new NullProgressMonitor(),
         -1,
         0
      );
      Assertions.assertTrue(var2.changed(), var2.diagnostics().toString());
      Document var3 = new Document(var1);
      var2.newEdit().apply(var3);
      Assertions.assertEquals(81, this.invoke(var3.get(), 7));
   }

   @Test
   void checkedFloatingPointReapplicationDoesNotInstrumentItsOriginalTraceAgain() throws Exception {
      for (NumericKind var4 : new NumericKind[]{NumericKind.FLOAT, NumericKind.DOUBLE}) {
         String var5 = var4 == NumericKind.FLOAT ? "float" : "double";
         String var6 = var4 == NumericKind.FLOAT ? "int" : "long";
         String var7 = var4 == NumericKind.FLOAT ? "1.0F" : "1.0D";
         String var8 = var4 == NumericKind.FLOAT ? "Float.floatToIntBits" : "Double.doubleToLongBits";
         String var9 = "public class Calculation { public static "
            + var6
            + " compute("
            + var5
            + " x) { "
            + var5
            + " r=x*"
            + var7
            + "; return "
            + var8
            + "(r); }}";
         MathCleanUpOptions var10 = JavaComputationExtractorTest.options(Set.of(var4), SafetyProfile.CHECKED_THROW);

         for (int var11 = 0; var11 < 3; var11++) {
            MathematicalAnalysis.Analysis var12 = MathematicalAnalysis.analyze(MathTestSupport.parse(var9), var9, var10, new NullProgressMonitor(), -1, 0);
            Assertions.assertEquals(var11 == 0, var12.changed(), var4 + " pass " + var11 + ": " + var12.diagnostics());
            Document var13 = new Document(var9);
            var12.newEdit().apply(var13);
            var9 = var13.get();
            if (var4 == NumericKind.FLOAT) {
               Assertions.assertEquals(-2147483648, this.invoke(var9, float.class, -0.0F));
            } else {
               Assertions.assertEquals(-9223372036854775808L, this.invoke(var9, double.class, -0.0));
            }
         }
      }
   }

   @Test
   void staleSourceCancellationAndTinyBudgetCannotApply() {
      String var1 = "class Calculation { int compute(int x) { int result=(x+1)-1; return result; }}";
      MathCleanUpOptions var2 = JavaComputationExtractorTest.options(Set.of(NumericKind.INT), SafetyProfile.PRESERVE_JAVA);
      NullProgressMonitor var3 = new NullProgressMonitor();
      var3.setCanceled(true);
      Assertions.assertFalse(MathematicalAnalysis.analyze(MathTestSupport.parse(var1), var1, var2, var3, -1, 0).changed());
      MathematicalAnalysis.Analysis var4 = MathematicalAnalysis.analyze(MathTestSupport.parse(var1), var1, var2, new NullProgressMonitor(), -1, 0);
      Assertions.assertTrue(var4.matches(var1, Map.of()));
      Assertions.assertFalse(var4.matches(var1 + " ", Map.of()));
      Assertions.assertFalse(var4.matches(var1, Map.of("org.eclipse.jdt.core.compiler.source", "8")));
      MathCleanUpOptions var5 = new MathCleanUpOptions(true, var2.kinds(), var2.safety(), var2.goal(), 1L, 1, false, 17, var2.exclusions());
      Assertions.assertFalse(MathematicalAnalysis.analyze(MathTestSupport.parse(var1), var1, var5, new NullProgressMonitor(), -1, 0).changed());
   }

   private Object invoke(String var1, int var2) throws Exception {
      return this.invoke(var1, int.class, var2);
   }

   private Object invoke(String var1, Class<?> var2, Object var3) throws Exception {
      return invoke(var1, var2, var3, 17);
   }

   private Object invoke(String var1, Class<?> var2, Object var3, int targetJava) throws Exception {
      Path var4 = Files.createTempDirectory(this.temporary, "compiled-");
      Path var5 = var4.resolve("Calculation.java");
      Files.writeString(var5, var1);
      Assertions.assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", Integer.toString(targetJava), "-d", var4.toString(), var5.toString()));

      try (URLClassLoader var6 = new URLClassLoader(new URL[]{var4.toUri().toURL()}, null)) {
         return var6.loadClass("Calculation").getMethod("compute", var2).invoke(null, var3);
      }
   }
}
