/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import static org.sandbox.jdt.internal.corext.fix.math.NormalizedScalarObservationTest.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.UnaryOperator;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.math.tests.StrictCompilerOptions;
import de.regelsuche.math.algorithms.modular.*;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.VerifiedRegion;

class NormalizedScalarReplayTest {
    @TempDir Path temporary;

    @Test void rejectsUnprovedFactoryConsumerBindingsAndEffects() {
        String base = SOURCE.replace(ENCODE, "static BigInteger saved; static int count; static boolean bit() { return true; } "
                + "static final BigInteger same = Q;\n" + ENCODE);
        for (var source : List.of(base.replace("{left, right}", "{right, left}"), base.replace("{left, right}", "{left, left}"),
                base.replace("return new BigInteger[]", "count++; return new BigInteger[]"),
                base.replace("static BigInteger[] pack", "static synchronized BigInteger[] pack"),
                base.replace("static BigInteger[] recover", "static synchronized BigInteger[] recover"),
                base.replace("static byte[] observe", "static synchronized byte[] observe"),
                base.replace("static byte[] observe", "byte[] observe"),
                base.replace("static byte[] encode", "static synchronized byte[] encode"),
                base.replace("pair[0]", "pair[1]"), base.replace("BigInteger y = pair[1];", "BigInteger y = pair[1], alias = y;"),
                base.replace("if ((x.signum", "saved = x; if ((x.signum"),
                base.replace("if ((x.signum", "Runnable captured = () -> System.out.print(pair); if ((x.signum"),
                base.replace("byte[] left =", "if (x == y) return null; byte[] left ="),
                base.replace("return out;", "count++; return out;"),
                base.replace("encode(x, y);", "encode(y, x);"),
                base.replace("x.signum() != 0 ? x : y", "x.signum() != 0 ? y : x"),
                base.replace("x.signum() != 0", "x.signum() == 0"), base.replace("testBit(0)", "testBit(1)"),
                base.replace("y = y.negate().mod(Q);", ""), base.replace("y.negate().mod(Q)", "y.negate()"),
                base.replace("y.negate().mod(Q)", "y.negate().mod(same)"), base.replace("!= target", "!= bit()"),
                base.replace("boolean target", "Boolean target"),
                base.replace("if ((x.signum", "target = !target; if ((x.signum"),
                base.replace("final BigInteger[] pair", "var pair"), base.replace("final BigInteger[] pair", "BigInteger pair[]"))) {
            assertNotEquals(base, source);
            var result = analyze(source, source.indexOf("recover(a, b, g)"));
            assertTrue(result.evidence().stream().noneMatch(r -> r.fieldRoot() != null), source);
        }
        String raw = base.replace(CALLER, "static BigInteger[] raw(BigInteger a, BigInteger b, BigInteger g) { return recover(a,b,g); }");
        for (int selection : new int[]{-1, raw.indexOf("return recover"), raw.indexOf("BigInteger seed")})
            assertTrue(analyze(raw, selection).evidence().stream().noneMatch(r -> r.fieldRoot() != null));
    }

    @Test void explicitTargetsAndSourceFactsSurviveScopedRestoration() throws Exception {
        String renamed = SOURCE.replace("recover(", "extract(").replace("BigInteger amber, BigInteger cobalt, BigInteger grey",
                "BigInteger grey, BigInteger cobalt, BigInteger amber").replace("extract(a, b, g)", "extract(g, b, a)")
                .replace("boolean target)", "boolean requested)").replace("final BigInteger[] pair =", """
                        BigInteger magnitude = new BigInteger("3");
                                boolean target = requested;
                                final BigInteger[] \\u0070air /* original */ =""").replace("extract(g, b, a)", "/* call */ extract(g, b, magnitude)");
        var analysis = analyze(renamed, -1);
        var doc = new Document(renamed);
        var undo = analysis.newEdit().apply(doc);
        var call = analysis.evidence().stream().filter(r -> r.fieldRoot() != null).findFirst().orElseThrow().fieldRoot().callSite();
        for (int i = 0; i < 3; i++)
            assertEquals(call.arguments().get(i).endsWith("#magnitude"), call.inputs().get(i).exactClass(), call::toString);
        assertTrue(doc.get().contains("\\u0070air;"));
        assertTrue(doc.get().contains("pair /* original */ = /* call */"));
        undo.apply(doc);
        assertEquals(renamed, doc.get());
    }

    @Test void repeatedWholeFileApplyReservesOnlyAVerifiedScopedCandidate() throws Exception {
        String loop = "static int sum(int n){int total=n;for(int i=1;i<=100;i++) total+=i;return total;}";
        String source = SOURCE.replace(ENCODE, loop + "\n" + ENCODE).replace(CALLER, CALLER + CALLER.replace("observe(", "other("));
        var doc = new Document(source);
        var first = analyze(source, -1);
        var undo = first.newEdit().apply(doc);
        var region = first.evidence().stream().filter(r -> r.fieldRoot() != null).findFirst().orElseThrow();
        assertTrue(first.evidence().stream().anyMatch(r -> r.finiteSum() != null && r.offset() < region.offset()),
                () -> first.diagnostics().toString());
        assertFalse(first.matches(source + " ", first.compilerOptions()));
        String output = doc.get();
        var repeat = analyze(output, -1);
        repeat.newEdit().apply(doc);
        assertEquals(output, doc.get(), () -> repeat.diagnostics().toString());
        String helper = region.fieldRoot().callSite().helperName();
        for (var changed : List.of(output.replace(helper + "(a, b, g)", helper + "(b, a, g)"),
                output.replace("cobalt.signum() > 0", "cobalt.signum() >= 0"),
                output.replace("!= null ? mathRootResult", "== null ? mathRootResult"),
                output.replace("static int sum", "static BigInteger[] extra(BigInteger a, BigInteger b, BigInteger g) { return "
                        + helper + "(a, b, g); } static int sum"))) {
            assertNotEquals(output, changed);
            var selected = analyze(changed, changed.lastIndexOf("recover(a, b, g)"));
            assertTrue(selected.evidence().stream().anyMatch(r -> r.fieldRoot() != null && r.fieldRoot().callSite().callerKey().contains("other(")),
                    () -> selected.diagnostics().toString());
        }
        undo.apply(doc);
        assertEquals(source, doc.get());
    }

    @Test void unrelatedPrefixBlocksAreOutsideTheRestoredOrdinal() throws Exception {
        String source = SOURCE.replace("final BigInteger[] pair", """
                int limit = 3;
                { int low = 1, high = limit; if (low > high) return null; }
                final BigInteger[] pair""");
        var normalized = apply(source).region().fieldRoot().callSite();
        assertEquals(2, normalized.normalization().ordinal());
        assertTrue(normalized.inputs().stream().noneMatch(FieldCallEvidence.Input::exactClass));
    }

    @Test void followsDirectAndTransitiveForeignSinkSourcesAtApplyTime() throws Exception {
        Path encoder = temporary.resolve("Encoder.java"), bytes = temporary.resolve("Bytes.java");
        Files.writeString(encoder, "import java.math.BigInteger; final class Encoder { "
                + "static byte[] encode(BigInteger x, BigInteger y) { return Bytes.encode(x, y); } }");
        Files.writeString(bytes, IMPORTS + "final class Bytes {" + ENCODE + "}");
        String source = SOURCE.replace(ENCODE, "").replace("return encode(x, y);", "return Encoder.encode(x, y);");
        var result = analyze(source, source.indexOf("recover(a, b, g)"), temporary.toString());
        var proof = result.evidence().stream().filter(r -> r.fieldRoot() != null).findFirst().orElseThrow().fieldRoot();
        assertEquals(2, proof.dependencies().size());
        for (Path external : List.of(encoder, bytes)) {
            String original = Files.readString(external);
            Files.writeString(external, original + "\n");
            assertFalse(result.matches(source, result.compilerOptions()));
            assertThrows(IllegalArgumentException.class, result::newEdit);
            Files.writeString(external, original);
            assertTrue(result.matches(source, result.compilerOptions()));
        }
        var doc = new Document(source);
        var undo = result.newEdit().apply(doc);
        assertNotEquals(source, doc.get());
        undo.apply(doc);
        assertEquals(source, doc.get());
        String saved = Files.readString(bytes);
        Files.writeString(bytes, saved.replace("byte[] left =", "if (x == y) return null; byte[] left ="));
        assertFalse(analyze(source, source.indexOf("recover(a, b, g)"), temporary.toString()).changed());
        Files.writeString(bytes, saved);
        String flag = "static boolean bit() { return true; }";
        Files.writeString(encoder, Files.readString(encoder).replace("static byte[] encode", flag + " static byte[] encode"));
        String inferred = source.replace("final BigInteger[] pair", "var inferred = Encoder.bit(); final BigInteger[] pair")
                .replace("!= target", "!= inferred");
        assertFalse(analyze(inferred, inferred.indexOf("recover(a, b, g)"), temporary.toString()).changed());
        Files.writeString(encoder, Files.readString(encoder).replace(flag, "static Boolean bit() { return null; }"));
        assertFalse(analyze(inferred, inferred.indexOf("recover(a, b, g)"), temporary.toString()).changed());
    }

    @Test void cachedApplyRetainsTheRangeGuardForAnExternalConstructorSign() throws Exception {
        Path origin = temporary.resolve("Origin.java");
        String constant = "final class Origin { static final int SIGN = 1; }";
        Files.writeString(origin, constant);
        String source = SOURCE.replace("final BigInteger[] pair = recover(a, b, g);",
                "BigInteger v = new BigInteger(Origin.SIGN, a.toByteArray()); final BigInteger[] pair = recover(v, b, g);");
        int at = source.indexOf("recover(v, b, g)");
        var cached = analyze(source, at, temporary.toString());
        Files.writeString(origin, constant.replace("= 1", "= -1"));
        for (var result : List.of(cached, analyze(source, at, temporary.toString()))) {
            var proof = result.evidence().stream().filter(r -> r.fieldRoot() != null).findFirst().orElseThrow().fieldRoot();
            var input = proof.callSite().inputs().getFirst();
            assertTrue(input.exactClass());
            var doc = new Document(source);
            result.newEdit().apply(doc);
            assertTrue(doc.get().contains("amber.signum() >= 0"), "A cached helper must guard a possibly negative magnitude");
            assertFalse(input.nonnegative());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"direct", "field", "local"})
    void primitiveSinkReadsBindTheirTransitiveConstantStatus(String use) throws Exception {
        Path origin = temporary.resolve("Origin.java"), encoder = temporary.resolve("Encoder.java");
        String constant = "final class Origin { static final int LIMIT = 1; }";
        Files.writeString(origin, constant);
        String source = SOURCE.replace(ENCODE, "").replace("return encode(x, y);", "return Encoder.encode(x, y);");
        String alias = "final int BOUND = 2 * Origin.LIMIT + 1; ";
        String field = use.equals("field") ? "static " + alias : "";
        String local = use.equals("local") ? alias : "";
        String guard = "if (x.intValue() == " + (use.equals("direct") ? "Origin.LIMIT" : "BOUND") + ") return new byte[0]; ";
        Files.writeString(encoder, IMPORTS + "final class Encoder {" + field
                + ENCODE.replace("byte[] left =", local + guard + "byte[] left =") + "}");
        int at = source.indexOf("recover(a, b, g)");
        var cached = analyze(source, at, temporary.toString());
        assertTrue(cached.changed());
        var doc = new Document(source);
        cached.newEdit().apply(doc);
        assertNotEquals(source, doc.get());
        Files.writeString(origin, constant.replace("static final", "static"));
        assertFalse(cached.matches(source, cached.compilerOptions()));
        assertThrows(IllegalArgumentException.class, cached::newEdit);
        assertFalse(analyze(source, at, temporary.toString()).changed());
        Files.writeString(origin, constant);
        assertTrue(cached.matches(source, cached.compilerOptions()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cachedApplyDeclinesAnInheritedOrImportedNestedFieldOwner(boolean imported) throws Exception {
        Path support = Files.createDirectory(temporary.resolve("support"));
        Files.writeString(support.resolve("Parent.java"), "package support; public class Parent { "
                + "public static class Holder { public static final int LIMIT = 1; } }");
        Path child = support.resolve("Child.java");
        String original = "package support; public final class Child extends Parent {}";
        Files.writeString(child, original);
        String imports = imported ? "import static support.Child.*; " : "import support.Child; ";
        String read = imported ? "Holder.LIMIT" : "Child.Holder.LIMIT";
        Files.writeString(temporary.resolve("Encoder.java"), imports + IMPORTS + "final class Encoder {"
                + ENCODE.replace("byte[] left =", "if (x.intValue() == " + read + ") return new byte[0]; byte[] left =") + "}");
        String source = SOURCE.replace(ENCODE, "").replace("return encode(x, y);", "return Encoder.encode(x, y);");
        assertNoCachedEditAfterShadowing(source, child, original.replace("{}", "{ public static class Holder { public static int LIMIT = 1; } }"));
    }

    @Test void aNewInheritedTypeCannotShadowAnOriginallyTopLevelFieldOwner() throws Exception {
        Path base = temporary.resolve("Base.java");
        Files.writeString(base, "class Base {}");
        Files.writeString(temporary.resolve("Origin.java"), "final class Origin { static final int LIMIT = 1; }");
        String source = SOURCE.replace("class Ocean {", "class Ocean extends Base {").replace(
                "byte[] left =", "if (x.intValue() == Origin.LIMIT) return new byte[0]; byte[] left =");
        assertNoCachedEditAfterShadowing(source, base, "class Base { static class Origin { static int LIMIT = 1; } }");
    }
    @ParameterizedTest
    @ValueSource(strings = {"root", "factory", "root modulus", "normalizer modulus", "sink", "transitive sink"})
    void cachedApplyRequiresDirectStaticMemberLookup(String role) throws Exception {
        String source = SOURCE, parent = "Ocean", hidden;
        String imports = IMPORTS;
        switch (role) {
            case "root" -> {
                source = source.replace("= recover(a, b, g);", "= Child.recover(a, b, g);");
                hidden = "static BigInteger[] recover(BigInteger a, BigInteger b, BigInteger g) { return null; }";
            }
            case "factory" -> {
                source = source.replace("return pack(u, v);", "return Child.pack(u, v);");
                hidden = "static BigInteger[] pack(BigInteger x, BigInteger y) { return null; }";
            }
            case "root modulus", "normalizer modulus" -> {
                source = role.equals("root modulus") ? source.replace("field = Q;", "field = Child.Q;")
                        : source.replace("y.negate().mod(Q)", "y.negate().mod(Child.Q)");
                hidden = "static final BigInteger Q = BigInteger.TWO;";
            }
            default -> {
                parent = "Parent";
                Files.writeString(temporary.resolve("Parent.java"), imports + "class Parent {" + ENCODE + "}");
                String dispatch = "Child.encode(x, y)";
                if (role.equals("transitive sink")) {
                    Files.writeString(temporary.resolve("Encoder.java"), imports + "class Encoder { "
                            + "static byte[] encode(BigInteger x, BigInteger y) { return " + dispatch + "; } }");
                    dispatch = "Encoder.encode(x, y)";
                }
                source = source.replace(ENCODE, "").replace("return encode(x, y);", "return " + dispatch + ";");
                hidden = "static byte[] encode(BigInteger x, BigInteger y) { return x == y ? null : new byte[0]; }";
            }
        }
        assertNotEquals(SOURCE, source);
        Files.writeString(temporary.resolve("Ocean.java"), source);
        String child = imports + "class Child extends " + parent + " {";
        Path file = Files.writeString(temporary.resolve("Child.java"), child + "}");
        assertNoCachedEditAfterShadowing(source, file, child + hidden + "}");
    }

    @Test void inheritedGuardFactCallsRetainTheRuntimeChecks() throws Exception {
        String imports = "import java.math.BigInteger; ";
        Files.writeString(temporary.resolve("Parent.java"), imports + "class Parent { "
                + "static BigInteger magnitude(BigInteger a) { return new BigInteger(1, a.toByteArray()); } }");
        Path child = Files.writeString(temporary.resolve("Child.java"), imports + "class Child extends Parent {}");
        String source = SOURCE.replace("final BigInteger[] pair = recover(a, b, g);",
                "BigInteger v = Child.magnitude(a); final BigInteger[] pair = recover(v, b, g);");
        assertGuardChecksAfterShadowing(source, child, imports + "class Child extends Parent { static BigInteger magnitude(BigInteger a) { return a; } }");
    }

    private void assertGuardChecksAfterShadowing(String source, Path shadow, String replacement) throws Exception {
        int selected = source.indexOf("recover(v, b, g)");
        var cached = analyze(source, selected, temporary.toString());
        Files.writeString(shadow, replacement);
        for (var result : List.of(cached, analyze(source, selected, temporary.toString()))) {
            var call = result.evidence().stream().filter(r -> r.fieldRoot() != null).findFirst().orElseThrow().fieldRoot().callSite();
            assertFalse(call.inputs().getFirst().exactClass());
            assertFalse(call.inputs().getFirst().nonnegative());
            var document = new Document(source);
            result.newEdit().apply(document);
            assertTrue(document.get().contains(".class.isInstance(amber)"));
            assertTrue(document.get().contains("amber.signum() >= 0"));
        }
    }

    @Test void generatedTypesStillCompileAfterAnUnrelatedTypeNamedJavaAppears() throws Exception {
        Path shadow = Files.writeString(temporary.resolve("java.java"), "/* no type yet */");
        var cached = analyze(SOURCE, SOURCE.indexOf("recover(a, b, g)"), temporary.toString());
        Files.writeString(shadow, "class java {}");
        var doc = new Document(SOURCE);
        cached.newEdit().apply(doc);
        assertNotEquals(SOURCE, doc.get());
        parse(doc.get(), temporary.toString());
    }

    @Test void implicitSystemCannotBindTheCachedSink() throws Exception {
        String source = SOURCE.replace("import java.lang.System;", "");
        Path shadow = Files.writeString(temporary.resolve("System.java"), "/* no type yet */");
        assertNoCachedEditAfterShadowing(source, shadow, "class System { static int copies; "
                + "static void arraycopy(byte[] l, int i, byte[] r, int j, int n) { copies++; } }");
    }

    @Test void aPackageQualifierCannotBindTheCachedSink() throws Exception {
        Path helpers = Files.createDirectory(temporary.resolve("helpers")), codecs = Files.createDirectory(temporary.resolve("codecs"));
        Path shadow = Files.writeString(helpers.resolve("codecs.java"), "package helpers;");
        Files.writeString(codecs.resolve("Raw.java"), "package codecs; " + IMPORTS + "public class Raw { public " + ENCODE + "}");
        Files.writeString(helpers.resolve("Encoder.java"), "package helpers; " + IMPORTS + "public class Encoder { "
                + "public static byte[] encode(BigInteger x, BigInteger y) { return codecs.Raw.encode(x, y); } }");
        String source = "import helpers.Encoder; " + SOURCE.replace(ENCODE, "").replace("return encode(x, y);", "return Encoder.encode(x, y);");
        assertNoCachedEditAfterShadowing(source, shadow, "package helpers; " + IMPORTS + "class codecs { static class Raw { "
                + "static byte[] encode(BigInteger x, BigInteger y) { return x == y ? null : new byte[0]; } } }");
    }

    @Test void aWildcardConstructorCannotSupplyCachedGuardFacts() throws Exception {
        Path helpers = Files.createDirectory(temporary.resolve("helpers"));
        Path shadow = Files.writeString(helpers.resolve("BigInteger.java"), "package helpers;");
        Path provider = Files.writeString(helpers.resolve("Provider.java"), "package helpers; import java.math.*; public class Provider { "
                + "public static java.math.BigInteger mag(java.math.BigInteger a) { return new BigInteger(1, a.toByteArray()); } }");
        String source = "import helpers.Provider; " + SOURCE.replace("final BigInteger[] pair = recover(a, b, g);",
                "BigInteger v = Provider.mag(a); final BigInteger[] pair = recover(v, b, g);");
        assertGuardChecksAfterShadowing(source, shadow, "package helpers; class BigInteger extends java.math.BigInteger { "
                + "BigInteger(int sign, byte[] bytes) { super(sign, bytes); } }");
        Files.writeString(provider, Files.readString(provider).replace("java.math.*", "java.math.BigInteger"));
        var imported = analyze(source, source.indexOf("recover(v, b, g)"), temporary.toString());
        var input = imported.evidence().stream().filter(r -> r.fieldRoot() != null).findFirst().orElseThrow()
                .fieldRoot().callSite().inputs().getFirst();
        assertTrue(input.exactClass() && input.nonnegative());
    }

    private void assertNoCachedEditAfterShadowing(String source, Path shadowed, String replacement) throws Exception {
        int selected = source.indexOf("recover(a, b, g)");
        var cached = analyze(source, selected, temporary.toString());
        Files.writeString(shadowed, replacement);
        assertFalse(analyze(source, selected, temporary.toString()).changed());
        var document = new Document(source);
        cached.newEdit().apply(document);
        assertEquals(source, document.get(), "An untracked type-lookup route cannot qualify");
    }

    @Test void stalePrimaryBindingsCannotEndorseAnUpdatedExternalConstant() throws Exception {
        Path origin = temporary.resolve("Origin.java");
        String constant = "final class Origin { static final int LIMIT = 1; }";
        Files.writeString(origin, constant);
        String source = SOURCE.replace(ENCODE, "static final int BOUND = 2 * Origin.LIMIT + 1;\n" + ENCODE.replace(
                "byte[] left =", "if (x.intValue() == BOUND) return new byte[0]; byte[] left ="));
        var stale = parse(source, temporary.toString());
        Files.writeString(origin, constant.replace("= 1", "= 7"));
        var environment = new MathematicalAnalysis.SourceEnvironment("Ocean.java", List.of(), List.of(temporary.toString()),
                List.of(), StrictCompilerOptions.allWarnings(17));
        int selected = source.indexOf("recover(a, b, g)");
        var result = MathematicalAnalysis.analyze(stale, source, OPTIONS, null, selected, 1, environment);
        assertFalse(result.changed(), "The primary alias still represents LIMIT=1 although the new receipt would read LIMIT=7");
        assertTrue(analyze(source, selected, temporary.toString()).changed());
    }

    private static FieldRootEvidence copy(FieldRootEvidence p, QuadraticRootSearch.Certificate root, FieldCallEvidence call,
            List<JavaSourceContext.Receipt> dependencies) {
        return new FieldRootEvidence(root, p.methodKey(), p.sinkKey(), p.prefix(), p.indent(), p.swappedInputs(), p.methodHash(),
                p.bindings(), p.localDefinitions(), dependencies, p.searchWork(), p.candidates(), call);
    }
    private static FieldCallEvidence copy(FieldCallEvidence c, FieldNormalizationEvidence norm) {
        return new FieldCallEvidence(c.callerKey(), c.callerHash(), c.helperName(), c.temporaryName(), c.arguments(), c.inputs(), c.normWitnessKey(), norm);
    }
    private static FieldNormalizationEvidence copy(FieldNormalizationEvidence n, QuadraticRootNormalization.Contract contract, String targetKey) {
        return new FieldNormalizationEvidence(contract, n.ordinal(), n.factoryKey(), n.factoryHash(), n.sinkKey(), n.sinkHash(),
                n.pairKey(), n.realKey(), n.imaginaryKey(), targetKey, n.modulusKey());
    }
    private static void verify(String source, String original, FieldRootEvidence proof, JavaValueSourceSummary.Work work) {
        var ast = parse(source);
        var caller = (MethodDeclaration)ast.findDeclaringNode(proof.callSite().callerKey());
        var owner = (org.eclipse.jdt.core.dom.TypeDeclaration)caller.getParent();
        var helper = java.util.Arrays.stream(owner.getMethods()).filter(m -> m.getName().getIdentifier().equals(proof.callSite().helperName()))
                .findFirst().orElseThrow();
        int start = caller.getStartPosition(), end = helper.getStartPosition() + helper.getLength();
        var region = new VerifiedRegion(start, original.length(), null, null, original, source.substring(start, end),
                null, null, null, null, null, null, null, null, null, null, proof);
        JavaGuardedFieldOptimizer.verify(ast, source, region, List.of(), OPTIONS, null, null, work);
    }
    private static JavaValueSourceSummary.Work work() { return new JavaValueSourceSummary.Work(OPTIONS.workBudget(), OPTIONS, () -> false); }

    @Test void replayRejectsChangedProgramsLayoutAndCoordinatedSourceIdentityForgeries() throws Exception {
        String input = SOURCE.replace("final BigInteger[] pair", "BigInteger[] other = null; BigInteger[] pair");
        var applied = apply(input);
        var p = applied.region().fieldRoot();
        var c = p.callSite();
        var n = c.normalization();
        String output = applied.source(), original = applied.region().original();
        verify(output, original, p, work());
        var mutations = List.<UnaryOperator<String>>of(s -> s.replace("!= null ? ", "== null ? "),
                s -> s.replace(": recover(a, b, g)", ": recover(b, a, g)"),
                s -> s.replace("pair = mathRootResult", "other = mathRootResult").replace("pair[", "other["),
                s -> s.replace("pair = mathRootResult", "other = mathRootResult; pair = mathRootResult"),
                s -> s.replace("return pack(mathRoot8, mathRoot9)", "return pack(mathRoot9, mathRoot8)"),
                s -> s.replace("private static BigInteger[] " + c.helperName(), "private static java.math.BigInteger[] " + c.helperName()),
                s -> s.replace("cobalt.signum() > 0", "cobalt.signum() >= 0"), s -> s.replace("new BigInteger[]{left, right}", "new BigInteger[]{right, left}"),
                s -> s.replace("static BigInteger[] recover", "static BigInteger[] extra(BigInteger a, BigInteger b, BigInteger g) { return "
                        + c.helperName() + "(a,b,g); } static BigInteger[] recover"));
        for (var mutate : mutations) {
            String changed = mutate.apply(output);
            assertNotEquals(output, changed);
            assertThrows(IllegalArgumentException.class, () -> verify(changed, original, p, work()), changed);
        }
        var root = p.certificate();
        var old = root.contract();
        var forgedRoot = new QuadraticRootContract("forged-root", old.modulus(), old.primeEvidence(), old.original(), old.observation());
        var point = (PrimeEvidence.EllipticOrder)old.primeEvidence();
        var alternatePoint = new PrimeEvidence.EllipticOrder(P, point.a(), point.b(), point.x(), P.subtract(point.y()), point.orderPrime());
        var alternatePrime = new QuadraticRootContract(old.sourceIdentity(), P, alternatePoint, old.original(), old.observation());
        String relabeled = JavaFieldNormalizationSite.identity(forgedRoot.sourceIdentity(), c.callerKey(), c.callerHash(), Integer.toString(n.ordinal()),
                n.factoryKey(), n.factoryHash(), n.sinkKey(), n.sinkHash(), n.pairKey(), n.realKey(), n.imaginaryKey(), n.targetKey(), n.modulusKey(),
                Integer.toString(n.contract().selector().mask()));
        var coordinated = copy(c, copy(n, new QuadraticRootNormalization.Contract(relabeled, P, n.contract().selector()), n.targetKey()));
        var renamed = new QuadraticRootNormalization.Contract("forged-normalizer", P, n.contract().selector());
        var swapped = new QuadraticRootNormalization.Contract(n.contract().sourceIdentity(), P, QuadraticRootNormalization.ParitySelector.imaginaryFirst());
        for (var fake : List.of(copy(p, new QuadraticRootSearch.Certificate(forgedRoot, root.power(), root.program()), c, p.dependencies()),
                copy(p, new QuadraticRootSearch.Certificate(forgedRoot, root.power(), root.program()), coordinated, p.dependencies()),
                copy(p, new QuadraticRootSearch.Certificate(alternatePrime, root.power(), root.program()), c, p.dependencies()),
                copy(p, root, copy(c, copy(n, renamed, n.targetKey())), p.dependencies()),
                copy(p, root, copy(c, copy(n, swapped, n.targetKey())), p.dependencies()),
                copy(p, root, copy(c, copy(n, n.contract(), "different-target")), p.dependencies()),
                copy(p, root, c, List.of(new JavaSourceContext.Receipt("unread", "forged")))))
            assertThrows(IllegalArgumentException.class, () -> verify(output, original, fake, work()), fake.toString());
        for (var limit : List.of(new JavaValueSourceSummary.Work(100, OPTIONS, () -> false),
                new JavaValueSourceSummary.Work(OPTIONS.workBudget(), OPTIONS, () -> true)))
            assertThrows(IllegalArgumentException.class, () -> verify(output, original, p, limit));
    }
}
