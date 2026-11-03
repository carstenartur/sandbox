/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import javax.tools.ToolProvider;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.*;
import de.regelsuche.sdk.optimization.*;

/**
 * Executes changed methods from pinned complete SWT archive sources. This local
 * archive gate supplements, and does not replace, SwtCurrentSourceRefinementTest's
 * independently enabled upstream Git identity check. Run under Xvfb on Linux.
 */
@EnabledIfSystemProperty(named = "sandbox.math.swt.methods", matches = "true")
@Timeout(180)
class SwtChangedMethodsTest {
    @TempDir Path temporary;

    @Test void jpegExtractionPreservesPixelsAndPartialWrites() throws Exception {
        var source = source("internal/image/JPEGFileFormat", "extractData", 6);
        List<String> original = qualify(source, "extractData", "srcIndex", loader -> jpeg(loader, false));
        assertTrue(original.getFirst().startsWith("ok:[24, 27, 30, 33, 36, 39, 42, 45,"),
                "The first block starts at image offset 136 and yields these eight hand-checked pixels");
    }

    @Test void jpegStoragePreservesPixelsAndPartialWrites() throws Exception {
        var source = source("internal/image/JPEGFileFormat", "storeData", 8);
        qualify(source, "storeData", "destIndex", loader -> jpeg(loader, true));
    }

    @Test void tiffPalettePreservesEndiannessAndReadFailures() throws Exception {
        var source = source("internal/image/TIFFDirectory", "getColorMap", 0);
        List<String> original = qualify(source, "getColorMap", "startB", this::palettes);
        assertTrue(original.getFirst().startsWith("ok:[RGB {10, 30, 50}, RGB {20, 40, 60}]"));
        assertTrue(original.stream().anyMatch(s -> s.startsWith("ok:[RGB {91, 93, 95}, RGB {92, 94, 96}]")));
    }

    @Test void rendererExecutesBothScrollBranchesWithRealWidgets() throws Exception {
        assertNotNull(System.getenv("DISPLAY"), "Run the real GTK renderer test under Xvfb");
        var source = source("custom/StyledTextRenderer", "getTextLayout", 4);
        List<String> before = renderer(compile(source, source.original(), true), source.binary());
        List<String> after = renderer(compile(source, source.generated(), true), source.binary());
        assertEquals(before, after, "Real getTextLayout scrolling must preserve callbacks and widget state");
        String mutant = mutate(source.generated(), "getTextLayout", "newVerticalScrollOffset");
        assertNotEquals(before, renderer(compile(source, mutant, true), source.binary()),
                "The test must reach the rewritten scrolling assignment");
        System.out.println("SWT_METHOD mutation detected: StyledTextRenderer.getTextLayout");
    }

    private List<String> qualify(Source source, String method, String local, Probe probe) throws Exception {
        List<String> before;
        try (var loader = new IsolatedSwt(compile(source, source.original(), false), source.binary())) {
            before = probe.run(loader);
        }
        try (var loader = new IsolatedSwt(compile(source, source.generated(), false), source.binary())) {
            List<String> after = probe.run(loader);
            assertEquals(before.size(), after.size());
            for (int i = 0; i < before.size(); i++) assertEquals(before.get(i), after.get(i), method + " case " + i);
        }
        try (var loader = new IsolatedSwt(compile(source, mutate(source.generated(), method, local), false), source.binary())) {
            assertNotEquals(before, probe.run(loader), "Zeroing " + local + " must expose a wrong result");
        }
        System.out.println("SWT_METHOD cases=" + before.size() + " mutation detected: " + method);
        return before;
    }

    private List<String> jpeg(ClassLoader loader, boolean store) throws Exception {
        Class<?> type = loader.loadClass("org.eclipse.swt.internal.image.JPEGFileFormat");
        var result = new ArrayList<String>();
        // MCU coordinates, sampling factors and row width; extreme indices must not hide partial writes.
        int[][] locations = {{1,1,1,1,0,0,16}, {0,0,1,1,0,0,16}, {1,0,2,2,1,1,32},
                {-1,0,1,1,0,0,16}, {Integer.MAX_VALUE,1,2,1,0,0,16}, {1,1,1,1,0,0,Integer.MAX_VALUE}};
        for (boolean aliasFrame : new boolean[] {false,true})
                for (int[] location : locations) for (int unitLength : new int[] {64,63,1,0,-1})
                for (int imageLength : new int[] {1024,256,140,0,-1}) {
            Object instance = construct(type);
            int[] unit = unitLength < 0 ? null : new int[unitLength];
            if (unit != null) for (int i = 0; i < unit.length; i++) unit[i] = (i % 4 == 0 ? -999 : i % 4 == 1 ? 999 : i - 32);
            byte[] image = imageLength < 0 ? null : new byte[imageLength];
            if (image != null) for (int i = 0; i < image.length; i++) image[i] = (byte) (3 * i);
            int[] component = aliasFrame && unit != null && unit.length >= 4 ? unit : new int[4];
            component[field(type, "HI").getInt(null)] = location[2];
            component[field(type, "VI").getInt(null)] = location[3];
            component[field(type, "CW").getInt(null)] = location[6];
            field(type, "imageComponents").set(instance, new byte[][] {image});
            field(type, "frameComponents").set(instance, new int[][] {component});
            field(type, "componentIds").set(instance, new int[] {0});
            String outcome;
            if (store) {
                outcome = invoke(method(type, "storeData", int[].class, int.class, int.class, int.class,
                        int.class, int.class, int.class, int.class), instance,
                        unit, 0, location[0], location[1], location[2], location[4], location[3], location[5]);
            } else {
                outcome = invoke(method(type, "extractData", int[].class, int.class, int.class, int.class,
                        int.class, int.class), instance, unit, 0, location[0], location[1], location[4], location[5]);
            }
            result.add(outcome + ":" + Arrays.toString(unit) + ":" + Arrays.toString(image));
        }
        assertTrue(result.stream().anyMatch(s -> s.startsWith("ok:")), "At least one complete pixel operation must execute");
        assertTrue(result.stream().anyMatch(s -> s.startsWith("java.lang.ArrayIndexOutOfBoundsException:")));
        return result;
    }

    private List<String> palettes(ClassLoader loader) throws Exception {
        Class<?> directory = loader.loadClass("org.eclipse.swt.internal.image.TIFFDirectory");
        Class<?> streamType = loader.loadClass("org.eclipse.swt.internal.image.LEDataInputStream");
        Class<?> accessType = loader.loadClass("org.eclipse.swt.internal.image.TIFFRandomFileAccess");
        var streamConstructor = streamType.getDeclaredConstructor(java.io.InputStream.class); streamConstructor.setAccessible(true);
        var accessConstructor = accessType.getDeclaredConstructor(streamType); accessConstructor.setAccessible(true);
        var directoryConstructor = directory.getDeclaredConstructor(accessType, boolean.class, loader.loadClass("org.eclipse.swt.graphics.ImageLoader"));
        directoryConstructor.setAccessible(true);
        var result = new ArrayList<String>();
        byte[] colors = {10,91,20,92,30,93,40,94,50,95,60,96};
        // SWT's unchanged seek helper does not terminate when seeking beyond EOF.
        // Exercise negative seeks and truncated palette reads without that unrelated unbounded path.
        for (boolean little : new boolean[] {false,true}) for (int offset : new int[] {0,-1})
                for (int size : new int[] {12,11,1,0})
                for (int[] bits : new int[][] {{1},{0},{32},{30},{31},{},null}) {
            var input = new ByteArrayInputStream(Arrays.copyOf(colors, size));
            Object access = accessConstructor.newInstance(streamConstructor.newInstance(input));
            Object instance = directoryConstructor.newInstance(access, little, null);
            field(directory, "bitsPerSample").set(instance, bits);
            field(directory, "colorMapOffset").setInt(instance, offset);
            String outcome;
            try {
                Object palette = method(directory, "getColorMap").invoke(instance);
                Object[] rgbs = (Object[]) palette.getClass().getMethod("getRGBs").invoke(palette);
                outcome = "ok:" + Arrays.toString(rgbs);
            } catch (InvocationTargetException failure) {
                if (failure.getCause() instanceof VirtualMachineError error) throw error;
                outcome = failure.getCause().getClass().getName();
            }
            result.add(outcome + ":remaining=" + input.available() + ":position=" + field(accessType, "current").getInt(access));
        }
        assertTrue(result.stream().anyMatch(s -> s.startsWith("ok:")), "A complete palette must be read");
        return result;
    }

    private Source source(String relative, String method, int parameterCount) throws Exception {
        Path binary = configured("sandbox.math.swt.methods.binary", "6c2b69d1c2096d9915df958536aeb6a7efec8210901e3b54b322d8423dbf0119");
        Path archive = configured("sandbox.math.swt.methods.sources", "7bbb0b52c43152f2b8c1faeee458f1e24fb04f8a9d516c3409cd509e6415dcbc");
        String entry = "org/eclipse/swt/" + relative + ".java";
        String original;
        Path context = Files.createDirectories(temporary.resolve("context"));
        try (var jar = new JarFile(archive.toFile())) {
            original = read(jar, entry);
            Path swt = context.resolve("org/eclipse/swt/SWT.java"); Files.createDirectories(swt.getParent());
            Files.writeString(swt, read(jar, "org/eclipse/swt/SWT.java"));
        }
        var environment = new MathematicalAnalysis.SourceEnvironment(entry, List.of(binary.toString()), List.of(context.toString()), List.of());
        var parser = ASTParser.newParser(AST.getJLSLatest()); parser.setSource(original.toCharArray()); parser.setUnitName(entry);
        parser.setResolveBindings(true); parser.setEnvironment(new String[] {binary.toString()}, new String[] {context.toString()}, null, true);
        var settings = new HashMap<String,String>(); JavaCore.setComplianceOptions("21", settings); parser.setCompilerOptions(settings);
        var ast = (CompilationUnit) parser.createAST(null);
        for (var problem : ast.getProblems()) assertFalse(problem.isError(), problem.toString());
        var options = new MathCleanUpOptions(true, Set.of(NumericKind.BYTE, NumericKind.SHORT, NumericKind.CHAR, NumericKind.INT, NumericKind.LONG),
                SafetyProfile.PRESERVE_JAVA, OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 1_000_000, 20_000, false, 21, List.of());
        var analysis = MathematicalAnalysis.analyze(ast, original, options, new NullProgressMonitor(), -1, 0, environment);
        assertTrue(analysis.changed(), "Ordinary whole-file Apply must supply the tested rewrite: " + entry);
        var methods = new ArrayList<MethodDeclaration>();
        ast.accept(new ASTVisitor() {
            @Override public boolean visit(MethodDeclaration node) {
                if (node.getName().getIdentifier().equals(method) && node.parameters().size() == parameterCount) methods.add(node);
                return false;
            }
        });
        assertEquals(1, methods.size(), "Identify exactly the original method being exercised: " + method);
        MethodDeclaration testedMethod = methods.getFirst();
        long acceptedInMethod = analysis.replacements().stream().filter(replacement ->
                replacement.offset() >= testedMethod.getStartPosition()
                && (long) replacement.offset() + replacement.length() <= (long) testedMethod.getStartPosition() + testedMethod.getLength()).count();
        assertTrue(acceptedInMethod > 0, "Ordinary whole-file Apply must rewrite the exercised method: " + method);
        System.out.printf("SWT_METHOD method=%s parameters=%d accepted=%d%n", method, parameterCount, acceptedInMethod);
        var document = new Document(original); var undo = analysis.newEdit().apply(document, TextEdit.CREATE_UNDO);
        String generated = document.get(); undo.apply(document); assertEquals(original, document.get());
        System.out.printf("SWT_METHOD entry=%s accepted=%d original=%s generated=%s%n", entry, analysis.replacements().size(), hash(original), hash(generated));
        return new Source(entry, binary, original, generated);
    }

    private Path compile(Source source, String content, boolean renderer) throws Exception {
        Path output = Files.createTempDirectory(temporary, "compiled-"); Path file = output.resolve(source.entry());
        Files.createDirectories(file.getParent()); Files.writeString(file, content);
        var args = new ArrayList<>(List.of("--release", "21", "-cp", source.binary().toString(), "-d", output.toString(), file.toString()));
        if (renderer) args.add(Path.of("qa/math-optimization/src/testFixtures/java/org/eclipse/swt/custom/RendererScrollProbe.java").toAbsolutePath().toString());
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
        assertTrue(Files.isRegularFile(output.resolve(source.entry().replace(".java", ".class"))),
                "Every complete source override must compile to its own class");
        return output;
    }

    private List<String> renderer(Path classes, Path binary) throws Exception {
        Path log = Files.createTempFile(temporary, "renderer-", ".log");
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(),
                "--enable-native-access=ALL-UNNAMED", "-Dorg.eclipse.swt.internal.gtk.disablePrinting", "-cp", System.getProperty("java.class.path"),
                RendererProcess.class.getName(), classes.toString(), binary.toString())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!process.waitFor(45, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("Renderer process timed out: " + Files.readString(log)); }
        assertEquals(0, process.exitValue(), Files.readString(log));
        List<String> result = Files.readAllLines(log).stream().filter(line -> line.startsWith("SWT_SCROLL ")).toList();
        assertEquals(3, result.size(), "Both signs and zero must reach the renderer callback: " + Files.readString(log));
        return result;
    }

    /** Mutate only the initializer/assignment in the named real method, never the optimizer. */
    private static String mutate(String source, String method, String local) {
        var parser = ASTParser.newParser(AST.getJLSLatest()); parser.setSource(source.toCharArray());
        var settings = new HashMap<String,String>(); JavaCore.setComplianceOptions("21", settings); parser.setCompilerOptions(settings);
        var ast = (CompilationUnit) parser.createAST(null); var values = new ArrayList<Expression>();
        ast.accept(new ASTVisitor() {
            @Override public boolean visit(MethodDeclaration node) {
                if (!node.getName().getIdentifier().equals(method)) return false;
                node.accept(new ASTVisitor() {
                    @Override public boolean visit(VariableDeclarationFragment variable) {
                        if (variable.getName().getIdentifier().equals(local) && variable.getInitializer() != null) values.add(variable.getInitializer());
                        return true;
                    }
                    @Override public boolean visit(Assignment assignment) {
                        if (assignment.getOperator() == Assignment.Operator.ASSIGN
                                && assignment.getLeftHandSide().toString().equals(local)) values.add(assignment.getRightHandSide());
                        return true;
                    }
                });
                return false;
            }
        });
        assertEquals(1, values.size(), "The mutation must target exactly one changed source calculation");
        Expression value = values.getFirst();
        return source.substring(0, value.getStartPosition()) + "0" + source.substring(value.getStartPosition() + value.getLength());
    }

    private static Path configured(String property, String expected) throws Exception {
        String configured = System.getProperty(property); assertNotNull(configured, "Explicit pinned archive required: " + property);
        Path path = Path.of(configured).toAbsolutePath(); assertEquals(expected, hash(Files.readAllBytes(path)), property); return path;
    }
    private static String hash(String source) throws Exception { return hash(source.getBytes(StandardCharsets.UTF_8)); }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static String read(JarFile archive, String entry) throws IOException {
        try (var input = archive.getInputStream(archive.getJarEntry(entry))) { return new String(input.readAllBytes(), StandardCharsets.UTF_8); }
    }
    private static Object construct(Class<?> type) throws Exception { var constructor = type.getDeclaredConstructor(); constructor.setAccessible(true); return constructor.newInstance(); }
    private static Field field(Class<?> type, String name) throws Exception { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
    private static Method method(Class<?> type, String name, Class<?>... types) throws Exception { Method method = type.getDeclaredMethod(name, types); method.setAccessible(true); return method; }
    private static String invoke(Method method, Object receiver, Object... arguments) throws Exception {
        try { method.invoke(receiver, arguments); return "ok"; }
        catch (InvocationTargetException failure) { return failure.getCause().getClass().getName(); }
    }
    private record Source(String entry, Path binary, String original, String generated) { }
    @FunctionalInterface private interface Probe { List<String> run(ClassLoader loader) throws Exception; }
    /** Use the same isolated loader as headless comparisons; do not mix signed JAR packages with overrides. */
    public static final class RendererProcess {
        public static void main(String[] args) throws Exception {
            try (var loader = new IsolatedSwt(Path.of(args[0]), Path.of(args[1]))) {
                loader.loadClass("org.eclipse.swt.custom.RendererScrollProbe").getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            }
        }
    }
    private static final class IsolatedSwt extends ClassLoader implements AutoCloseable {
        private final Path classes; private final JarFile dependency;
        IsolatedSwt(Path classes, Path binary) throws IOException { super(ClassLoader.getPlatformClassLoader()); this.classes = classes; dependency = new JarFile(binary.toFile()); }
        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!name.startsWith("org.eclipse.swt.")) throw new ClassNotFoundException(name);
            String entry = name.replace('.', '/') + ".class";
            try {
                byte[] bytes;
                if (Files.isRegularFile(classes.resolve(entry))) bytes = Files.readAllBytes(classes.resolve(entry));
                else { var item = dependency.getJarEntry(entry); if (item == null) throw new ClassNotFoundException(name);
                    try (var input = dependency.getInputStream(item)) { bytes = input.readAllBytes(); } }
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException failure) { throw new ClassNotFoundException(name, failure); }
        }
        @Override protected URL findResource(String name) {
            if (dependency.getJarEntry(name) == null) return null;
            try { return URI.create("jar:" + Path.of(dependency.getName()).toUri() + "!/" + name).toURL(); }
            catch (IOException failure) { throw new IllegalStateException(failure); }
        }
        @Override public void close() throws IOException { dependency.close(); }
    }
}
