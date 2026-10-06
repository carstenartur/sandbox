/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import javax.tools.ToolProvider;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.SafetyProfile;

/** Runs only synthetic test programs, compiled without Regelsuche on their runtime classpath. */
class FloatingPointRoundTripTest {

	private static final String DOUBLE_SOURCE= "public class Calculation { public static double compute(double x) { double result=(x+1.0)-x; return result; }}"; //$NON-NLS-1$
	@TempDir Path temporary;

	@Test
	void checkedRoundTripRejectsFiniteRoundingMismatchAndNonfiniteInputs() throws Exception {
		var rewrite= rewrite(DOUBLE_SOURCE, NumericKind.DOUBLE, SafetyProfile.CHECKED_THROW);
		var method= compile(rewrite.source(), double.class);
		assertEquals(1.0, invoke(method, 1.0));
		assertEquals(0.0, invoke(compile(DOUBLE_SOURCE, double.class), 1e16));
		assertEquals("MATH_NUMERIC_MISMATCH", arithmetic(method, 1e16).getMessage()); //$NON-NLS-1$
		assertEquals("MATH_NON_FINITE", arithmetic(method, Double.POSITIVE_INFINITY).getMessage()); //$NON-NLS-1$
		assertEquals("MATH_NON_FINITE", arithmetic(method, Double.NaN).getMessage()); //$NON-NLS-1$
		assertTrue(rewrite.analysis().evidence().getFirst().cost().checkWork() > 0);
		assertFalse(rewrite.analysis().evidence().getFirst().cost().estimatedRuntimeImprovement());
		assertTrue(rewrite.analysis().replacements().getFirst().description().contains("CHECKED_THROW")); //$NON-NLS-1$
	}

	@Test
	void guardedRoundTripFallsBackForFiniteRoundingMismatchAndNan() throws Exception {
		var rewrite= rewrite(DOUBLE_SOURCE, NumericKind.DOUBLE, SafetyProfile.GUARDED_FALLBACK);
		assertFalse(rewrite.source().contains("catch ("), rewrite.source()); //$NON-NLS-1$
		var optimized= compile(rewrite.source(), double.class);
		var original= compile(DOUBLE_SOURCE, double.class);
		for (double input : new double[] {1.0, 1e16, -0.0, 0.0, Double.MIN_VALUE, Double.MAX_VALUE,
				Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.longBitsToDouble(0x7ff8000000000042L)}) {
			assertEquals(Double.doubleToRawLongBits((Double) invoke(original, input)),
					Double.doubleToRawLongBits((Double) invoke(optimized, input)), "input=" + input); //$NON-NLS-1$
		}
		assertEquals(0.0, invoke(optimized, 1e16));
		assertTrue(Double.isNaN((Double) invoke(optimized, Double.NaN)));
	}

	@Test
	void checkedDoubleNegationPreservesNegativeZeroAndExactSubnormals() throws Exception {
		String source= "public class Calculation { public static double compute(double x) { double result=-(-x); return result; }}"; //$NON-NLS-1$
		var rewrite= rewrite(source, NumericKind.DOUBLE, SafetyProfile.CHECKED_THROW);
		var method= compile(rewrite.source(), double.class);
		for (double input : new double[] {-0.0, 0.0, Double.MIN_VALUE, -Double.MIN_VALUE, Double.MIN_NORMAL, -42.0})
			assertEquals(Double.doubleToRawLongBits(input), Double.doubleToRawLongBits((Double) invoke(method, input)));
		arithmetic(method, Double.NaN);
	}

	@Test
	void guardedDoubleNegationPreservesNegativeZeroAndNanBits() throws Exception {
		String source= "public class Calculation { public static double compute(double x) { double result=-(-x); return result; }}"; //$NON-NLS-1$
		var rewrite= rewrite(source, NumericKind.DOUBLE, SafetyProfile.GUARDED_FALLBACK);
		var optimized= compile(rewrite.source(), double.class);
		var original= compile(source, double.class);
		for (double input : new double[] {-0.0, Double.MIN_VALUE, Double.POSITIVE_INFINITY,
				Double.longBitsToDouble(0x7ff8000000000042L), Double.longBitsToDouble(0xfff8000000000042L)})
			assertEquals(Double.doubleToRawLongBits((Double) invoke(original, input)),
					Double.doubleToRawLongBits((Double) invoke(optimized, input)));
	}

	@Test
	void checkedFloatRoundTripUsesFloatRoundingAtEachOperation() throws Exception {
		String source= "public class Calculation { public static float compute(float x) { float result=(x+1.0F)-x; return result; }}"; //$NON-NLS-1$
		var rewrite= rewrite(source, NumericKind.FLOAT, SafetyProfile.CHECKED_THROW);
		var method= compile(rewrite.source(), float.class);
		assertEquals(1.0F, invoke(method, 1.0F));
		assertEquals(0.0F, invoke(compile(source, float.class), 16_777_216F));
		assertEquals("MATH_NUMERIC_MISMATCH", arithmetic(method, 16_777_216F).getMessage()); //$NON-NLS-1$
		arithmetic(method, Float.POSITIVE_INFINITY);
	}

	private static Rewrite rewrite(String source, NumericKind kind, SafetyProfile safety) throws Exception {
		var options= JavaComputationExtractorTest.options(Set.of(kind), safety);
		var analysis= MathematicalAnalysis.analyze(MathTestSupport.parse(source), source, options, new NullProgressMonitor(), -1, 0);
		assertTrue(analysis.changed(), analysis.diagnostics().toString());
		Document document= new Document(source);
		analysis.newEdit().apply(document);
		return new Rewrite(document.get(), analysis);
	}

	private Method compile(String source, Class<?> parameter) throws Exception {
		Path directory= Files.createTempDirectory(temporary, "fp-roundtrip-"); //$NON-NLS-1$
		Path file= directory.resolve("Calculation.java"); //$NON-NLS-1$
		Files.writeString(file, source);
		var compiler= ToolProvider.getSystemJavaCompiler();
		assertNotNull(compiler, "Tests require a JDK"); //$NON-NLS-1$
		assertEquals(0, compiler.run(null, null, null, "--release", "17", "-d", directory.toString(), file.toString()), source); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		try (var loader= new URLClassLoader(new java.net.URL[] {directory.toUri().toURL()}, null)) {
			return loader.loadClass("Calculation").getMethod("compute", parameter); //$NON-NLS-1$
		}
	}

	private static Object invoke(Method method, Object input) throws Exception {
		return method.invoke(null, input);
	}

	private static ArithmeticException arithmetic(Method method, Object input) {
		return assertInstanceOf(ArithmeticException.class, assertThrows(InvocationTargetException.class, () -> invoke(method, input)).getCause());
	}

	private record Rewrite(String source, MathematicalAnalysis.Analysis analysis) {}
}
