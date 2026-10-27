/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import de.regelsuche.ast.Expr;
import de.regelsuche.ast.VariableExpr;
import de.regelsuche.sdk.optimization.*;
import de.regelsuche.search.program.ComputationBackend.Type;
import de.regelsuche.search.program.JointComputationPlan;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.*;

class JavaComputationTaskTest {
    @TempDir Path temporary;
    private static final Pattern ADDRESS = Pattern.compile("([xyz])\\[(?:([xyz]Off) \\+ )?(\\d+)\\]");
    private static final OptimizationBudget BUDGET = new OptimizationBudget(1_000_000, 20_000, 64, 5000);

    @Test void completePinnedNat256CarryBlockMatchesIndependentSdkPlan() throws Exception {
        String source = nat256();
        CompilationUnit unit = parseNat256(source);
        var extraction = new JavaComputationExtractor().extractTasks(unit, source, options());
        var task = extraction.tasks().stream().filter(t -> t.start() == source.indexOf("long c = 0;")).findFirst().orElseThrow();
        assertEquals(26, task.plan().outputs().size());
        assertEquals(16, task.plan().inputs().size());
        assertEquals(42, task.schedule().size());
        assertEquals(8, task.schedule().stream().filter(e -> e.kind() == JavaComputationTask.Kind.ARRAY_STORE).count());
        assertEquals(17, task.schedule().stream().filter(e -> e.kind() == JavaComputationTask.Kind.LOCAL_COMMIT).count());
        assertTrue(task.matchesSource(source));
        assertFalse(task.matchesSource(source + "\n"));
        assertInstanceOf(VerificationResult.Verified.class, new ComputationOptimizer().verify(
                task.numericRequest(OptimizationGoal.LOWER_ESTIMATED_RUNTIME, BUDGET), manualPlan(), CancellationToken.NONE));
        var extracted = task.numericRequest(OptimizationGoal.LOWER_ESTIMATED_RUNTIME, BUDGET);
        var manual = new OptimizationRequest(manualPlan(), task.trace(), extracted.selectedKinds(), extracted.semanticsRevision(),
                extracted.assumptions(), extracted.safetyProfile(), extracted.goal(), extracted.budget(), extracted.checkedPolicy());
        // Same actual source-occurrence trace and allowance; the value plans were assembled independently.
        search("JDT", extracted); search("manual", manual);
        var regions = new JavaComputationExtractor().extractRuntime(unit, source, options()).regions().stream()
                .filter(r -> r.start() >= task.start() && r.start() < task.start() + task.length()).toList();
        assertTrue(regions.size() > 8, "Ordinary edit regions retain store boundaries");
        assertTrue(regions.stream().allMatch(r -> r.length() < task.length()));
        System.out.println("NAT256_TASK_COMPARISON editRegions=" + regions.size() + " taskOutputs=26 reads=16 stores=8");
        var random = new SplittableRandom(1767);
        for (int run = 0; run < 32; run++) {
            int[] x = new int[8], y = new int[8];
            for (int i = 0; i < 8; i++) { x[i] = random.nextInt(); y[i] = random.nextInt(); }
            compare(task, source, x, 0, y, 0, new int[8], 0);
            compare(task, source, x, 0, y, 0, x, 0);
            compare(task, source, x, 0, x, 0, x, 0);
        }
        compare(task, source, null, 0, new int[8], 0, new int[8], 0);
        compare(task, source, new int[8], 0, new int[8], 0, new int[3], 0);
        compare(task, source, new int[8], 0, new int[2], 0, new int[8], 0);
    }

    private static void search(String label, OptimizationRequest request) {
        var optimizer = new ComputationOptimizer();
        var result = optimizer.optimize(request, CancellationToken.NONE);
        assertFalse(result instanceof OptimizationResult.Unsupported, result.toString());
        assertFalse(result instanceof OptimizationResult.Refuted, result.toString());
        if (result instanceof OptimizationResult.Candidate candidate) {
            assertInstanceOf(VerificationResult.Verified.class, optimizer.reverify(request, candidate, CancellationToken.NONE));
            System.out.println("NAT256_SDK_SEARCH " + label + " candidate cost=" + candidate.cost().candidateScore() + " work=" + candidate.work());
        } else System.out.println("NAT256_SDK_SEARCH " + label + " " + result);
    }

    @Test void overlappingOffsetReadsRemainAfterEarlierStores() throws Exception {
        String source = nat256();
        int start = source.indexOf("long c = 0;", source.indexOf("public static int add(int[] x, int xOff"));
        var task = new JavaComputationExtractor().extractTasks(parseNat256(source), source, options()).tasks().stream()
                .filter(t -> t.start() == start).findFirst().orElseThrow();
        int[] shared = { -1, -1, 4, 5, 6, 7, 8, 9, 10 };
        compare(task, source, shared, 0, new int[9], 0, shared, 1);
        compare(task, source, shared, 1, shared, 0, shared, 0);
        compare(task, source, shared, 0, null, 0, shared, 1);
        compare(task, source, shared, 0, shared, 0, shared, 2);
        var reads = task.schedule().stream().filter(e -> e.kind() == JavaComputationTask.Kind.SOURCE_READ).toList();
        assertEquals(16, reads.stream().map(JavaComputationTask.Event::valueId).distinct().count());
        assertTrue(task.schedule().indexOf(reads.get(2)) > task.schedule().indexOf(task.schedule().stream()
                .filter(e -> e.kind() == JavaComputationTask.Kind.ARRAY_STORE).findFirst().orElseThrow()));
    }

    @Test void failingStoreObservesTheCommittedSumBeforeTheCarryShift() throws Exception {
        String source = "public class Calculation { public static long run(int[] x, int[] z, long c) { try { "
                + "c += x[0] & 0xffffffffL; z[0] = (int)c; c >>>= 32; return c;"
                + " } catch (RuntimeException failure) { return c; } } }";
        var task = new JavaComputationExtractor().extractTasks(MathTestSupport.parse(source), source, options()).tasks().stream()
                .filter(t -> t.start() == source.indexOf("c +=")).findFirst().orElseThrow();
        assertEquals(List.of(JavaComputationTask.Kind.SOURCE_READ, JavaComputationTask.Kind.LOCAL_COMMIT,
                JavaComputationTask.Kind.ARRAY_STORE, JavaComputationTask.Kind.LOCAL_COMMIT, JavaComputationTask.Kind.RETURN),
                task.schedule().stream().map(JavaComputationTask.Event::kind).toList());
        var outcome = replay(task, source, new int[]{-1}, 0, null, 0, new int[0], 0, 5L);
        assertEquals(ArrayIndexOutOfBoundsException.class, outcome.failure());
        assertEquals(0x100000004L, outcome.committed());
        assertEquals(1L, replay(task, source, new int[]{-1}, 0, null, 0, new int[1], 0, 5L).returned());
        assertEquals(5L, replay(task, source, null, 0, null, 0, new int[1], 0, 5L).committed());
        Path file = temporary.resolve("Calculation.java"); Files.writeString(file, source);
        assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "17", "-d", temporary.toString(), file.toString()));
        try (var loader = new java.net.URLClassLoader(new java.net.URL[]{temporary.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            var method = loader.loadClass("Calculation").getMethod("run", int[].class, int[].class, long.class);
            for (int size : new int[]{0, 1}) for (int[] input : new int[][]{null, {-1}, {Integer.MIN_VALUE}}) {
                int[] original = new int[size], interpreted = new int[size];
                var result = replay(task, source, input, 0, null, 0, interpreted, 0, 5L);
                assertEquals(method.invoke(null, input, original, 5L), result.failure() == null ? result.returned() : result.committed());
                assertArrayEquals(original, interpreted);
            }
        }
    }

    @Test void expandedGraphsAreBoundedWithoutExpandingSharedDefinitions() {
        String source = "class Calculation { int run(int x) { int value = x; " + "value += value; ".repeat(24) + "return value; } }";
        var result = new JavaComputationExtractor().extractTasks(MathTestSupport.parse(source), source, options());
        assertTrue(result.tasks().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("TASK_GRAPH_BOUNDARY")));
    }

    @Test void readsInStoreOperandsFailBeforeTheStoreNullCheck() {
        String source = "class Calculation { int run(int[] x, int[] y, int[] z) { z[0] = x[0] + y[0]; return z[0]; } }";
        var task = new JavaComputationExtractor().extractTasks(MathTestSupport.parse(source), source, options()).tasks().getFirst();
        assertEquals(List.of(JavaComputationTask.Kind.SOURCE_READ, JavaComputationTask.Kind.SOURCE_READ,
                JavaComputationTask.Kind.ARRAY_STORE, JavaComputationTask.Kind.SOURCE_READ, JavaComputationTask.Kind.RETURN),
                task.schedule().stream().map(JavaComputationTask.Event::kind).toList());
        assertEquals(ArrayIndexOutOfBoundsException.class, replay(task, source, new int[0], 0, new int[1], 0, null, 0, 0).failure());
        assertEquals(ArrayIndexOutOfBoundsException.class, replay(task, source, new int[1], 0, new int[0], 0, null, 0, 0).failure());
        assertEquals(NullPointerException.class, replay(task, source, new int[1], 0, new int[1], 0, null, 0, 0).failure());
        assertThrows(NullPointerException.class, () -> new JavaComputationTask.Event(null, "output0", "binding", 0, 1, 0));
    }

    @Test void unsupportedEffectsAndControlFlowNeverJoinTasks() {
        String source = "class Calculation { int run(int[] x, int i) { int a = x[0]; "
                + "a = a / i; if (i > 0) { a += 1; } return a; } }";
        var result = new JavaComputationExtractor().extractTasks(MathTestSupport.parse(source), source, options());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("ARRAY_READ_ARITHMETIC_BOUNDARY")), result.toString());
        String scalar = source.replace("x[0]", "1");
        assertTrue(new JavaComputationExtractor().extractTasks(MathTestSupport.parse(scalar), scalar, options()).diagnostics()
                .stream().anyMatch(d -> d.code().equals("TASK_NON_TOTAL_ARITHMETIC")));
        int boundary = source.indexOf("a = a / i;");
        assertTrue(result.tasks().stream().noneMatch(t -> t.start() <= boundary && t.start() + t.length() > boundary));
        assertEquals(3, result.tasks().size());
        assertTrue(result.tasks().stream().allMatch(t -> t.schedule().size() <= 2));
        var checked = new MathCleanUpOptions(true, Set.of(NumericKind.INT), SafetyProfile.CHECKED_THROW,
                OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 100_000, 2000, true, 17, List.of());
        assertTrue(new JavaComputationExtractor().extractTasks(MathTestSupport.parse(source), source, checked).tasks().isEmpty());
    }

    private static JointComputationPlan manualPlan() {
        Map<String, Type> inputs = new LinkedHashMap<>();
        List<JointComputationPlan.Output> outputs = new ArrayList<>();
        // The source initializes a long with an int literal; retain that actual cast in its trace.
        Expr carry = JavaExpressions.cast(NumericKind.INT, NumericKind.LONG, JavaExpressions.literal(0));
        output(outputs, NumericKind.LONG, carry);
        for (int i = 0; i < 8; i++) {
            String left = "input" + (2 * i), right = "input" + (2 * i + 1);
            inputs.put(left, NumericKind.INT.type()); inputs.put(right, NumericKind.INT.type());
            Expr a = op(NumericOperation.AND, JavaExpressions.cast(NumericKind.INT, NumericKind.LONG, new VariableExpr(left)), JavaExpressions.literal(0xffffffffL));
            Expr b = op(NumericOperation.AND, JavaExpressions.cast(NumericKind.INT, NumericKind.LONG, new VariableExpr(right)), JavaExpressions.literal(0xffffffffL));
            Expr sum = op(NumericOperation.ADD, carry, op(NumericOperation.ADD, a, b));
            output(outputs, NumericKind.LONG, sum);
            output(outputs, NumericKind.INT, JavaExpressions.cast(NumericKind.LONG, NumericKind.INT, sum));
            carry = op(NumericOperation.UNSIGNED_SHIFT_RIGHT, sum, JavaExpressions.literal(32));
            output(outputs, NumericKind.LONG, carry);
        }
        output(outputs, NumericKind.INT, JavaExpressions.cast(NumericKind.LONG, NumericKind.INT, carry));
        return new JointComputationPlan(inputs, Map.of(), outputs);
    }
    private static Expr op(NumericOperation op, Expr... values) { return JavaExpressions.operation(NumericKind.LONG, op, values); }
    private static void output(List<JointComputationPlan.Output> values, NumericKind kind, Expr value) {
        values.add(new JointComputationPlan.Output("output" + values.size(), kind.type(), value));
    }

    private static void compare(JavaComputationTask task, String source, int[] x, int xOff, int[] y, int yOff, int[] z, int zOff) {
        var copies = new IdentityHashMap<int[], int[]>();
        int[] a = copy(x, copies), b = copy(y, copies), c = copy(z, copies);
        Object expected;
        try { expected = org.bouncycastle.math.raw.Nat256.add(a, xOff, b, yOff, c, zOff); }
        catch (RuntimeException failure) { expected = failure.getClass(); }
        var actual = replay(task, source, x, xOff, y, yOff, z, zOff, 0L);
        assertEquals(expected, actual.failure() == null ? actual.returned() : actual.failure());
        assertArrayEquals(a, x); assertArrayEquals(b, y); assertArrayEquals(c, z);
    }
    private static int[] copy(int[] value, IdentityHashMap<int[], int[]> copies) {
        return value == null ? null : copies.computeIfAbsent(value, v -> v.clone());
    }

    /** Test interpreter only for the pinned x/y/z array-address syntax; not a product emitter. */
    private static Outcome replay(JavaComputationTask task, String source, int[] x, int xOff, int[] y, int yOff, int[] z, int zOff, long initial) {
        Map<String, Object> inputs = new HashMap<>();
        task.plan().inputs().forEach((id, type) -> inputs.put(id, type.equals(NumericKind.LONG.type()) ? (Object) initial : 0));
        Object returned = null; long committed = initial;
        try {
            for (var event : task.schedule()) {
                if (event.kind() == JavaComputationTask.Kind.SOURCE_READ || event.kind() == JavaComputationTask.Kind.ARRAY_STORE) {
                    var address = ADDRESS.matcher(source.substring(event.start(), event.start() + event.length()));
                    assertTrue(address.matches(), address.toString());
                    int[] array = switch (address.group(1)) { case "x" -> x; case "y" -> y; default -> z; };
                    int offset = address.group(2) == null ? 0 : switch (address.group(2)) { case "xOff" -> xOff; case "yOff" -> yOff; default -> zOff; };
                    int index = offset + Integer.parseInt(address.group(3));
                    if (event.kind() == JavaComputationTask.Kind.SOURCE_READ) inputs.put(event.valueId(), array[index]);
                    else array[index] = ((Number) evaluate(task, event.valueId(), inputs)).intValue();
                } else {
                    Object value = evaluate(task, event.valueId(), inputs);
                    if (event.kind() == JavaComputationTask.Kind.RETURN) returned = value;
                    else committed = ((Number) value).longValue();
                }
            }
            return new Outcome(returned, committed, null);
        } catch (RuntimeException failure) { return new Outcome(null, committed, failure.getClass()); }
    }
    private static Object evaluate(JavaComputationTask task, String id, Map<String, Object> inputs) {
        var output = task.plan().outputs().stream().filter(o -> o.name().equals(id)).findFirst().orElseThrow();
        return ComputationOptimizer.prepare(new JointComputationPlan(task.plan().inputs(), Map.of(), List.of(output))).execute(inputs).get(id);
    }
    private record Outcome(Object returned, long committed, Class<?> failure) { }
    private static MathCleanUpOptions options() {
        return new MathCleanUpOptions(true, Set.of(NumericKind.INT, NumericKind.LONG), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 1_000_000, 20_000, false, 17, List.of());
    }
    private static String nat256() throws Exception {
        String configured = System.getProperty("sandbox.math.bc.sourceArchive");
        Path archive = configured == null ? FileLocator.getBundleFileLocation(Platform.getBundle("bcprov.source")).orElseThrow().toPath() : Path.of(configured);
        assertEquals(configured == null ? "9b15d2ccb83c76f9f393cb8cb97d0cb971d04dee667cc3f9d7f4ac28f582cb91"
                : "b37ac84b1d5435ab7b8d166c16ab9f75e09f68f8ec50479bae433939b241b03f", hash(Files.readAllBytes(archive)));
        try (var jar = new JarFile(archive.toFile()); var input = jar.getInputStream(jar.getJarEntry("org/bouncycastle/math/raw/Nat256.java"))) {
            byte[] bytes = input.readAllBytes();
            assertEquals("5d31e9bbf2ef750ed9e3279680b62724e80d54bc5bd36b3172f17071a01db3c9", hash(bytes));
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static CompilationUnit parseNat256(String source) throws Exception {
        var parser = ASTParser.newParser(AST.getJLSLatest()); parser.setSource(source.toCharArray());
        parser.setUnitName("org/bouncycastle/math/raw/Nat256.java"); parser.setResolveBindings(true);
        String configured = System.getProperty("sandbox.math.bc.jar");
        Path binary = configured == null ? FileLocator.getBundleFileLocation(Platform.getBundle("bcprov")).orElseThrow().toPath() : Path.of(configured);
        parser.setEnvironment(new String[]{binary.toString()}, new String[0], null, true);
        Map<String, String> compiler = new HashMap<>(); JavaCore.setComplianceOptions(JavaCore.VERSION_17, compiler); parser.setCompilerOptions(compiler);
        var unit = (CompilationUnit) parser.createAST(null);
        for (var problem : unit.getProblems()) assertFalse(problem.isError(), problem.toString());
        return unit;
    }
}
