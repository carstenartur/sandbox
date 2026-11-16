/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.sandbox.jdt.internal.corext.fix.math.JavaStructuralRewritePlan.Invocation;
import org.sandbox.jdt.internal.corext.fix.math.JavaStructuralRewritePlan.Method;

import de.regelsuche.ast.BinaryExpr;
import de.regelsuche.ast.BinaryOperator;
import de.regelsuche.ast.Expr;
import de.regelsuche.ast.NumberExpr;
import de.regelsuche.ast.VariableExpr;
import de.regelsuche.math.algorithms.iteration.ProjectiveHandoff;
import de.regelsuche.sdk.optimization.OptimizationBudget;
import de.regelsuche.symbol.SymbolId;

class JavaStructuralRewritePlanTest {
    private static final String VALUE = "projection/Value.java";
    private static final String PROJECTION = "projection/Projection.java";
    private static final Map<String, String> AFFINE = Map.of(VALUE, """
        package projection;
        final class Value {
            private final int normalized;
            Value(int original) { normalized = original / 2; }
            int coordinate() { return normalized; }
        }
        """, PROJECTION, """
        package projection;
        public final class Projection {
            public static int project(int input) {
                return new Value(input).coordinate();
            }
        }
        """);
    private static final Map<String, String> CARRIED = Map.of(VALUE, """
        package projection;
        final class Value {
            private final int numerator, scale;
            Value(int numerator, int scale) { this.numerator = numerator; this.scale = scale; }
            int coordinate() { return numerator / scale; }
        }
        """, PROJECTION, """
        package projection;
        public final class Projection {
            public static int project(int input) {
                return new Value(input, 2).coordinate();
            }
        }
        """);

    @TempDir Path temporary;

    @Test void bindsConstructorAndCallerAndAppliesBothUnitsTogether() throws Exception {
        var project = new Method("projection.Projection", "project", List.of("int"), "int");
        var oldConstructor = new Method("projection.Value", "<init>", List.of("int"), "void");
        var newConstructor = new Method("projection.Value", "<init>", List.of("int", "int"), "void");
        var requested = List.of(new Invocation(project, oldConstructor, 1, 0), new Invocation(project, newConstructor, 0, 1));
        var plan = prepare(AFFINE, CARRIED, requested);
        var output = plan.apply(AFFINE);
        assertEquals(CARRIED, output);
        assertEquals(AFFINE, plan.undo(output));
        assertEquals(run(AFFINE), run(output));

        var stale = new HashMap<>(AFFINE);
        stale.compute(VALUE, (name, source) -> source + "\n// Later edit\n");
        assertThrows(IllegalArgumentException.class, () -> plan.apply(stale));
        assertEquals(AFFINE.get(PROJECTION), stale.get(PROJECTION));
        assertThrows(IllegalArgumentException.class,
            () -> prepare(AFFINE, CARRIED, List.of(new Invocation(project, oldConstructor, 1, 1))));
    }

    @Test void rejectsAnIncompleteOrPublicSignatureMigration() {
        var incomplete = Map.of(VALUE, CARRIED.get(VALUE), PROJECTION, AFFINE.get(PROJECTION));
        assertThrows(IllegalArgumentException.class, () -> prepare(AFFINE, incomplete, List.of()));
        var publicChange = new HashMap<>(CARRIED);
        publicChange.compute(PROJECTION, (path, source) -> source.replace("project(int input)", "project(long input)")
            .replace("new Value(input, 2)", "new Value((int)input, 2)"));
        assertThrows(IllegalArgumentException.class, () -> prepare(AFFINE, publicChange, List.of()));
    }

    @Test void rejectsAnUnplannedBindingChangeInsideAnUneditedMethod() {
        var original = new HashMap<>(AFFINE);
        original.compute(PROJECTION, (path, source) -> source.replace("return new Value(input).coordinate();", "return scale(input);")
            .replace("public static int project", "private static int scale(long n) { return (int)n; } public static int project"));
        var overload = new HashMap<>(original);
        overload.compute(PROJECTION, (path, source) -> source.replace("public static int project",
            "private static int scale(int n) { return -n; } public static int project")
            .replace("return scale(input);", "int step = 2; return scale(input) + step;"));
        assertThrows(IllegalArgumentException.class, () -> prepare(original, overload, List.of()));
    }

    @Test void rejectsRebindingInAMethodWhoseSignatureChanges() {
        String path = "projection/Value.java";
        String original = """
            package projection;
            class Value {
                private static int f(long n) { return 1; }
                int project(int input) { return f(input); }
                int sample() { return project(4); }
            }
            """;
        String edited = original.replace("project(int input)", "project(int input, int scale)")
            .replace("project(4)", "project(4, 2)")
            .replace("int sample()", "private static int f(int n) { return 2; } int sample()");
        var caller = new Method("projection.Value", "sample", List.of(), "int");
        var before = new Method("projection.Value", "project", List.of("int"), "int");
        var after = new Method("projection.Value", "project", List.of("int", "int"), "int");
        var edges = List.of(new Invocation(caller, before, 1, 0), new Invocation(caller, after, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> prepare(Map.of(path, original), Map.of(path, edited), edges));
    }

    @Test void rejectsRemovalOfPublicMembersInheritedThroughAPackagePrivateClass() {
        String path = "projection/Api.java";
        String original = """
            package projection;
            class Hidden { public void ping(int n) { } public static class Nested { } }
            public class Api extends Hidden { }
            """;
        String edited = original.replace("public void ping(int n) { }", "");
        assertThrows(IllegalArgumentException.class, () -> prepare(Map.of(path, original), Map.of(path, edited), List.of()));
        String noMemberType = original.replace("public static class Nested { }", "");
        assertThrows(IllegalArgumentException.class, () -> prepare(Map.of(path, original), Map.of(path, noMemberType), List.of()));
    }

    @Test void rejectsChangingAPublicVarargsMethodToAnArrayParameter() {
        String path = "projection/Api.java";
        String original = "package projection; public class Api { public void ping(String... input) { } }";
        String edited = original.replace("String...", "String[]");
        assertThrows(IllegalArgumentException.class, () -> prepare(Map.of(path, original), Map.of(path, edited), List.of()));
    }

    @Test void rejectsInheritedFieldRebindingAndParameterizedSupertypeChanges() {
        String path = "projection/Hierarchy.java";
        String original = """
            package projection;
            class Ancestor<T> { protected static final int VALUE = 9; }
            public final class Hierarchy extends Ancestor<String> {
                public int project() { return VALUE; }
            }
            """;
        String shadow = original.replace("public int project", "private static final int VALUE = -9; public int project");
        assertThrows(IllegalArgumentException.class, () -> prepare(Map.of(path, original), Map.of(path, shadow), List.of()));
        String retyped = original.replace("Ancestor<String>", "Ancestor<Integer>");
        assertThrows(IllegalArgumentException.class, () -> prepare(Map.of(path, original), Map.of(path, retyped), List.of()));
    }

    @Test void rejectsClasspathDriftBeforeEditingAnySource() throws IOException {
        Path binaryRoot = Files.createTempDirectory(temporary, "classes");
        var marker = Files.writeString(binaryRoot.resolve("receipt"), "original");
        var plan = JavaStructuralRewritePlan.verify(AFFINE, CARRIED, List.of(binaryRoot.toString()), 17, List.of(), null);
        Files.writeString(marker, "changed");
        assertThrows(IllegalArgumentException.class, () -> plan.apply(AFFINE));
    }

    @Test void verifiesANonCurveProjectiveProposalWithItsSourceTransition() throws Exception {
        var original = new HashMap<>(AFFINE);
        original.compute(VALUE, (path, source) -> source.replace("int original)", "int original, int scale)").replace("original / 2", "original / scale"));
        original.compute(PROJECTION, (path, source) -> source.replace("return new Value(input)",
            "int scale = 2 + (input & 1); return new Value(input, scale)"));
        var edited = new HashMap<>(CARRIED);
        edited.compute(VALUE, (path, source) -> source.replace("int coordinate()", """
            static Value carried(int numerator, int scale) { return new Value(numerator, scale); }
            int coordinate()"""));
        edited.compute(PROJECTION, (path, source) -> source.replace("return new Value(input, 2)",
            "int scale = 2 + (input & 1); return Value.carried(input, scale)"));
        var scope = new UUID(0, 42);
        Expr numerator = VariableExpr.scoped(new SymbolId(scope, 1));
        Expr base = VariableExpr.scoped(new SymbolId(scope, 2));
        var affine = List.<Expr>of(new BinaryExpr(numerator, BinaryOperator.DIV, base));
        var proposal = new ProjectiveHandoff.Candidate(base, List.of(1), List.of(numerator));
        var project = new Method("projection.Projection", "project", List.of("int"), "int");
        var factory = new Method("projection.Value", "carried", List.of("int", "int"), "projection.Value");
        var edges = List.of(new Invocation(project, factory, 0, 1));
        var plan = JavaProjectiveRewritePlan.verify(original, edited, List.of(), 17, edges, affine, proposal,
            OptimizationBudget.DEFAULT, null);
        assertEquals(List.of(1), plan.handoff().candidate().weights());
        assertEquals(run(original), run(plan.sources().apply(original)));
        var wrongNumerator = new ProjectiveHandoff.Candidate(base, List.of(1), List.of(new NumberExpr(7)));
        assertThrows(IllegalArgumentException.class, () -> JavaProjectiveRewritePlan.verify(original, edited, List.of(),
            17, edges, affine, wrongNumerator, OptimizationBudget.DEFAULT, null));
    }

    @Test void rejectsRetainedCallSitesTradingOverloads() {
        String path = "projection/Overloads.java";
        String original = """
            package projection;
            class Overloads {
                private static void f(String value) { }
                private static void f(CharSequence value) { }
                static void sample() {
                    { String x = "first"; f(x); }
                    { CharSequence x = "second"; f(x); }
                }
            }
            """;
        String swapped = original.replace("String x = \"first\"", "CharSequence x = \"first\"")
            .replace("CharSequence x = \"second\"", "String x = \"second\"");
        assertThrows(IllegalArgumentException.class, () -> prepare(Map.of(path, original), Map.of(path, swapped), List.of()));
        String inserted = original.replace("{ String x = \"first\"; f(x); }",
            "{ CharSequence x = \"first\"; f(x); } { String x = \"added\"; f(x); }");
        assertThrows(IllegalArgumentException.class, () -> prepare(Map.of(path, original), Map.of(path, inserted), List.of()));
    }

    private static JavaStructuralRewritePlan prepare(Map<String, String> original, Map<String, String> edited,
            List<Invocation> assertions) {
        return JavaStructuralRewritePlan.verify(original, edited, List.of(), 17, assertions, null);
    }

    private List<Integer> run(Map<String, String> sources) throws Exception {
        Path root = Files.createTempDirectory(temporary, "projection");
        var arguments = new java.util.ArrayList<>(List.of("--release", "17", "-d", root.toString()));
        for (var entry : sources.entrySet()) {
            Path file = root.resolve(entry.getKey());
            Files.createDirectories(Objects.requireNonNull(file.getParent(), "Projection source must have a parent directory"));
            Files.writeString(file, entry.getValue());
            arguments.add(file.toString());
        }
        if (ToolProvider.getSystemJavaCompiler().run(null, null, null, arguments.toArray(String[]::new)) != 0)
            throw new IOException("projection fixture did not compile");
        try (var loader = new URLClassLoader(new java.net.URL[] { root.toUri().toURL() }, ClassLoader.getPlatformClassLoader())) {
            var method = loader.loadClass("projection.Projection").getMethod("project", int.class);
            return List.of((Integer)method.invoke(null, 0), (Integer)method.invoke(null, 7), (Integer)method.invoke(null, -7));
        }
    }
}
