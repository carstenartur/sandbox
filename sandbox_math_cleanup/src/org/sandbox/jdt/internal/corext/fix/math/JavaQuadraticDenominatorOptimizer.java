/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.dom.*;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.Diagnostic;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.Replacement;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.SourceEnvironment;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.Stage;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.VerifiedRegion;

import de.regelsuche.math.algorithms.polynomial.RationalDenominatorReduction;
import de.regelsuche.math.algorithms.polynomial.RationalDenominatorReduction.Coefficient;
import de.regelsuche.math.algorithms.polynomial.RationalDenominatorReduction.Plan;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;

/** Projects the audited BC Java representation into the shared polynomial proof. */
final class JavaQuadraticDenominatorOptimizer {
    private static final String BC = "org.bouncycastle.crypto.bls."; //$NON-NLS-1$
    private static final String MAP = BC + "BLS12_381G2HashToCurve"; //$NON-NLS-1$
    private static final String FIELD = BC + "Fp2Element"; //$NON-NLS-1$
    private static final String PRIME = BC + "BLS12_381G1"; //$NON-NLS-1$
    private static final String POINT = BC + "BLS12_381G2Point"; //$NON-NLS-1$
    private static final String BIG_INTEGER = "java.math.BigInteger"; //$NON-NLS-1$
    private static final String MAP_METHOD = "mapToCurveAndIso3"; //$NON-NLS-1$
    private static final String REDUCED = "mathIso3Reduced"; //$NON-NLS-1$
    private static final String OFFSET = "MATH_ISO3_OFFSET"; //$NON-NLS-1$
    private static final String REGION = "quadratic-denominator"; //$NON-NLS-1$
    private static final Coefficient ONE = new Coefficient(BigInteger.ONE, BigInteger.ZERO);

    /* Exact UTF-8 source snapshots at bcgit/bc-java 6b4f58259d2b3ee8301fda1d6c8798f816abc5ea.
     * MAP pins the private tables, their initialization/uses, pure Horner, and the surrounding Java flow.
     * FIELD pins the final two-component value class and the arithmetic, including Barrett reduction.
     * Reduced operands give add intermediates < 2p, and nonnegative reduce inputs < 2p^2 after
     * the signed correction. The Barrett quotient estimate is at most floor(x/p), so correction
     * produces canonical residues. For the BLS12-381 prime p = 3 (mod 4), a^2+b^2 vanishes iff
     * both components vanish. The same inverse method throws on a zero input in either schedule.
     * PRIME pins the actual modulus initialization. POINT pins the value-only result constructor.
     * Bindings must resolve to those sources. Unchecked input components take the original schedule. */
    private static final String MAP_SHA = "969b96583ddf32ea5e90487339ebaf1451ff0a1d351ba482e5446d9e946bcd3b"; //$NON-NLS-1$
    private static final String FIELD_SHA = "d02a0d82d766dac6f3d972cbbb1a627c656419bb5d02b8a06372957e3f782ec7"; //$NON-NLS-1$
    private static final String PRIME_SHA = "a00f2bb235c0d5d95d3c45298eaa5c769f704bd365064672857173fba9d2275b"; //$NON-NLS-1$
    private static final String POINT_SHA = "34ce74ab4abb6f9e90e97bcd06bebde682dc73e77054fe7b9685f665ebab5354"; //$NON-NLS-1$

    private static final String REWRITE = """
            boolean mathIso3UseFactor = mathIso3Reduced(xPrime) && mathIso3Reduced(yPrime);
            Fp2Element xNum = horner(K1, xPrime);
            Fp2Element mathIso3XFactor = mathIso3UseFactor
                ? xPrime.add(MATH_ISO3_OFFSET)
                : horner(K2, xPrime).add(xPrime.square());
            Fp2Element yNum = horner(K3, xPrime);
            Fp2Element yDen = mathIso3UseFactor ? null : horner(K4, xPrime).add(xPrime.square().mul(xPrime));

            Fp2Element mathIso3Inverse = (mathIso3UseFactor ? mathIso3XFactor : mathIso3XFactor.mul(yDen)).inverse();
            Fp2Element mathIso3InverseSquared = mathIso3UseFactor ? mathIso3Inverse.square() : mathIso3Inverse;
            Fp2Element x = mathIso3UseFactor ? xNum.mul(mathIso3InverseSquared) : xNum.mul(mathIso3Inverse).mul(yDen);
            Fp2Element y = mathIso3UseFactor
                ? yPrime.mul(yNum).mul(mathIso3InverseSquared.mul(mathIso3Inverse))
                : yPrime.mul(yNum).mul(mathIso3Inverse).mul(mathIso3XFactor);
            """;
    private static final String SUPPORT = """
            private static final Fp2Element MATH_ISO3_OFFSET = Fp2Element.of({{real}}L, {{imaginary}}L);

            private static boolean mathIso3Reduced(Fp2Element value)
            {
                if (value == null)
                {
                    return false;
                }
                BigInteger real = value.c0(), imaginary = value.c1();
                return real != null && imaginary != null
                    && real.getClass() == BigInteger.class && imaginary.getClass() == BigInteger.class
                    && real.signum() >= 0 && imaginary.signum() >= 0
                    && real.compareTo(Fp2Element.P) < 0 && imaginary.compareTo(Fp2Element.P) < 0;
            }
            """;

    private record Site(MethodDeclaration method, List<?> statements, int start, int stop, int end) { }

    private JavaQuadraticDenominatorOptimizer() { }

    static void optimize(CompilationUnit ast, String source, MathCleanUpOptions options, IProgressMonitor monitor,
            int selectionOffset, int selectionLength, Map<String, String> compiler, SourceEnvironment environment,
            List<Replacement> replacements, List<VerifiedRegion> evidence, List<Diagnostic> diagnostics,
            JavaValueSourceSummary.Work work) {
        if (!options.mathematicalOptIn() || !options.kinds().contains(NumericKind.BIG_INTEGER)
                || options.goal() != OptimizationGoal.LOWER_ESTIMATED_RUNTIME || ast.types().size() != 1
                || !(ast.types().getFirst() instanceof TypeDeclaration owner) || !type(owner.resolveBinding(), MAP)) return;
        JavaSourceContext.valueWork(ast, work);
        try {
            work.charge(1 + source.length() / 16);
            if (!MAP_SHA.equals(MathematicalAnalysis.digest(source)) || ast.getLength() != source.length()
                    || Arrays.stream(ast.getProblems()).anyMatch(problem -> problem.isError())) return;
            var site = site(owner, 8);
            int start = site.start(), length = site.end() - start;
            if (selectionOffset >= 0 && !overlaps(start, site.stop() - start, selectionOffset, Math.max(1, selectionLength))
                    || replacements.stream().anyMatch(edit -> overlaps(start, length, edit.offset(), Math.max(1, edit.length())))) return;
            var calls = new HashMap<String, Integer>();
            for (Object item : site.statements()) collectCalls((ASTNode) item, calls, work);
            require(calls.equals(Map.of("horner", 4, "add", 2, "square", 2, "mul", 7, "inverse", 1, "ofUnchecked", 1)), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                    "QUADRATIC_DENOMINATOR_ORIGINAL_CALLS"); //$NON-NLS-1$
            var field = site.method().resolveBinding().getParameterTypes()[0];
            audit(ast, field, FIELD, FIELD_SHA);
            audit(ast, site.method().resolveBinding().getReturnType(), POINT, POINT_SHA);
            var modulusField = field(field, "P"); //$NON-NLS-1$
            var modulusDeclaration = JavaSourceContext.definition(ast, modulusField);
            if (modulusDeclaration == null || !(modulusDeclaration.declaration() instanceof VariableDeclarationFragment fragment)
                    || !(fragment.getInitializer() instanceof QualifiedName name)
                    || !(name.resolveBinding() instanceof IVariableBinding primeField)) {
                throw new IllegalArgumentException("QUADRATIC_DENOMINATOR_PRIME"); //$NON-NLS-1$
            }
            audit(ast, primeField.getDeclaringClass(), PRIME, PRIME_SHA);
            var fact = JavaSourceContext.constant(ast, modulusField);
            if (fact == null || !fact.value().mod(BigInteger.valueOf(4)).equals(BigInteger.valueOf(3))) {
                throw new IllegalArgumentException("QUADRATIC_DENOMINATOR_PRIME"); //$NON-NLS-1$
            }
            BigInteger modulus = fact.value();
            var xd = coefficients(ast, site, 1, 2);
            var yd = coefficients(ast, site, 3, 3);
            var plan = RationalDenominatorReduction.discover(xd, yd, modulus, (phase, amount) -> work.charge(amount)).orElse(null);
            if (plan == null || plan.denominator().size() != 2) return;
            var coefficient = plan.denominator().getFirst();
            long real = signedLong(coefficient.real(), modulus), imaginary = signedLong(coefficient.imaginary(), modulus);
            String rewritten = REWRITE.stripTrailing().replace("\n", "\n        ").replace("\n        \n", "\n\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            String support = "\n\n" + SUPPORT.replace("{{real}}", Long.toString(real)).replace("{{imaginary}}", Long.toString(imaginary)) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    .indent(4).stripTrailing().replace("\n    \n", "\n\n"); //$NON-NLS-1$ //$NON-NLS-2$
            String original = source.substring(start, site.end());
            String replacement = rewritten + source.substring(site.stop(), site.end()) + support;
            String generated = source.substring(0, start) + replacement + source.substring(site.end());
            work.charge((long) source.length() + replacement.length());
            var after = MathematicalAnalysis.checkGeneratedSource(ast, source, generated, compiler, options.targetJava(), monitor,
                    environment, List.of(new GeneratedSourceDiagnostics.Edit(start, site.stop() - start, rewritten.length()),
                            new GeneratedSourceDiagnostics.Edit(site.end(), 0, support.length())));
            var emittedFactor = requireGenerated(after, start, work);
            require(RationalDenominatorReduction.verify(xd, yd, modulus, new Plan(List.of(emittedFactor, ONE)),
                    (phase, amount) -> work.charge(amount)), "QUADRATIC_DENOMINATOR_EMITTED_IDENTITIES"); //$NON-NLS-1$

            var anchors = new ArrayList<JavaSourceRewritePlan.Node>();
            for (int i = 0; i < 7; i++) anchors.add(anchor((ASTNode) site.statements().get(i), source));
            var sourcePlan = new JavaSourceRewritePlan(MAP.replace('.', '/') + ".java", MAP_SHA, source.length(), start, length, //$NON-NLS-1$
                    anchors, List.of(anchor((ASTNode) site.statements().get(7), source)), Map.of(REGION, List.of(anchors.getFirst().id())),
                    List.of(new JavaSourceRewritePlan.Change(start, site.stop() - start,
                                    MathematicalAnalysis.digest(source.substring(start, site.stop())), rewritten),
                            new JavaSourceRewritePlan.Change(site.end(), 0, MathematicalAnalysis.digest(""), support))); //$NON-NLS-1$
            sourcePlan.resolve(ast, source);
            String description = String.format(Locale.ROOT, "Verified the G2 denominator square and cube with offset (%d, %d). " //$NON-NLS-1$
                    + "The reduced path uses one inversion, six fewer field multiplications and one fewer square. " //$NON-NLS-1$
                    + "The original schedule handles unchecked components.", real, imaginary); //$NON-NLS-1$
            evidence.add(new VerifiedRegion(start, length, null, null, original, replacement, null, sourcePlan));
            replacements.add(new Replacement(start, length, replacement, description));
            diagnostics.add(new Diagnostic("VERIFIED_QUADRATIC_DENOMINATOR", description, start, length, Stage.PROOF)); //$NON-NLS-1$
        } catch (JavaValueSourceSummary.DeadlineExceeded cancelled) {
            throw cancelled;
        } catch (IllegalArgumentException | ArithmeticException unavailable) {
            diagnostics.add(new Diagnostic("QUADRATIC_DENOMINATOR_UNAVAILABLE", unavailable.getMessage(), 0, source.length(), Stage.SOURCE)); //$NON-NLS-1$
        } finally {
            JavaSourceContext.valueWork(ast, null);
        }
    }

    static boolean isRegion(VerifiedRegion region) {
        return region.sourcePlan() != null && region.sourcePlan().occurrenceOrigins().containsKey(REGION);
    }

    static void verifyCombined(CompilationUnit ast, String generated, VerifiedRegion region, List<Replacement> allEdits,
            JavaValueSourceSummary.Work work) {
        // The audited mapping, coefficient initialization and Horner must remain at the audited source version.
        work.charge((long) generated.length() + allEdits.size());
        int start = region.offset(), stop = start + region.replacement().length();
        require(stop <= generated.length() && generated.regionMatches(start, region.replacement(), 0, region.replacement().length())
                && MAP_SHA.equals(MathematicalAnalysis.digest(generated.substring(0, start) + region.original() + generated.substring(stop))),
                "QUADRATIC_DENOMINATOR_COMBINED_SOURCE"); //$NON-NLS-1$
        requireGenerated(ast, start, work);
    }

    private static Coefficient requireGenerated(CompilationUnit ast, int start, JavaValueSourceSummary.Work work) {
        require(ast.types().getFirst() instanceof TypeDeclaration, "QUADRATIC_DENOMINATOR_EMITTED_OWNER"); //$NON-NLS-1$
        var owner = (TypeDeclaration) ast.types().getFirst();
        var site = site(owner, 10);
        var calls = new HashMap<String, Integer>();
        for (Object item : site.statements()) collectCalls((ASTNode) item, calls, work);
        require(site.start() == start && calls.equals(Map.of(REDUCED, 2, "horner", 4, "add", 3, "square", 3, "mul", 11, "inverse", 1, "ofUnchecked", 1)), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                "QUADRATIC_DENOMINATOR_EMITTED_CALLS"); //$NON-NLS-1$
        var offset = field(owner.resolveBinding(), OFFSET);
        require(type(offset.getType(), FIELD) && Modifier.isPrivate(offset.getModifiers()) && Modifier.isFinal(offset.getModifiers())
                && ast.findDeclaringNode(offset) instanceof VariableDeclarationFragment declaration
                && declaration.getInitializer() instanceof MethodInvocation, "QUADRATIC_DENOMINATOR_EMITTED_FACTOR"); //$NON-NLS-1$
        var factory = (MethodInvocation) ((VariableDeclarationFragment) ast.findDeclaringNode(offset)).getInitializer();
        calls.clear();
        collectCalls(factory, calls, work);
        require(calls.equals(Map.of("of", 1)), "QUADRATIC_DENOMINATOR_EMITTED_FACTORY"); //$NON-NLS-1$ //$NON-NLS-2$
        var check = method(owner, REDUCED);
        calls.clear();
        collectCalls(check, calls, work);
        require(calls.equals(Map.of("c0", 1, "c1", 1, "getClass", 2, "signum", 2, "compareTo", 2)), "QUADRATIC_DENOMINATOR_EMITTED_GUARD"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
        Object real = ((Expression) factory.arguments().getFirst()).resolveConstantExpressionValue();
        Object imaginary = ((Expression) factory.arguments().get(1)).resolveConstantExpressionValue();
        require(real instanceof Long && imaginary instanceof Long, "QUADRATIC_DENOMINATOR_EMITTED_COEFFICIENT"); //$NON-NLS-1$
        return new Coefficient(BigInteger.valueOf((Long) real), BigInteger.valueOf((Long) imaginary));
    }

    private static Site site(TypeDeclaration owner, int statementCount) {
        var method = method(owner, MAP_METHOD);
        var binding = method.resolveBinding();
        require(binding != null && !binding.isRecovered() && binding.getParameterTypes().length == 1
                && type(binding.getParameterTypes()[0], FIELD) && type(binding.getReturnType(), POINT)
                && method.getBody() != null, "QUADRATIC_DENOMINATOR_MAP"); //$NON-NLS-1$
        var statements = method.getBody().statements();
        require(statements.size() >= statementCount, "QUADRATIC_DENOMINATOR_STATEMENTS"); //$NON-NLS-1$
        var tail = statements.subList(statements.size() - statementCount, statements.size());
        var first = (ASTNode) tail.getFirst();
        var lastDeclaration = (ASTNode) tail.get(statementCount - 2);
        return new Site(method, tail, first.getStartPosition(), end(lastDeclaration), end(method));
    }

    private static MethodDeclaration method(TypeDeclaration owner, String name) {
        for (var method : owner.getMethods()) if (method.getName().getIdentifier().equals(name)) return method;
        throw new IllegalArgumentException("QUADRATIC_DENOMINATOR_METHOD " + name); //$NON-NLS-1$
    }

    private static void audit(CompilationUnit ast, ITypeBinding binding, String owner, String sha) {
        require(type(binding, owner) && binding.isFromSource(), "QUADRATIC_DENOMINATOR_SOURCE_BINDING " + owner); //$NON-NLS-1$
        var definition = JavaSourceContext.definition(ast, binding);
        require(definition != null && sha.equals(MathematicalAnalysis.digest(definition.source())), "QUADRATIC_DENOMINATOR_SOURCE_DIGEST " + owner); //$NON-NLS-1$
    }

    private static IVariableBinding field(ITypeBinding owner, String name) {
        for (var field : owner.getDeclaredFields()) if (!field.isRecovered() && field.getName().equals(name)
                && Modifier.isStatic(field.getModifiers())) return field;
        throw new IllegalArgumentException("QUADRATIC_DENOMINATOR_FIELD " + name); //$NON-NLS-1$
    }

    private static List<Coefficient> coefficients(CompilationUnit ast, Site site, int statement, int count) {
        var row = (VariableDeclarationStatement) site.statements().get(statement);
        var add = (MethodInvocation) ((VariableDeclarationFragment) row.fragments().getFirst()).getInitializer();
        var horner = (MethodInvocation) add.getExpression();
        var name = (SimpleName) horner.arguments().getFirst();
        var binding = name.resolveBinding();
        require(binding instanceof IVariableBinding table && table.isField() && Modifier.isPrivate(table.getModifiers())
                && Modifier.isStatic(table.getModifiers()) && Modifier.isFinal(table.getModifiers())
                && type(table.getDeclaringClass(), MAP) && ast.findDeclaringNode(table) instanceof VariableDeclarationFragment,
                "QUADRATIC_DENOMINATOR_TABLE_BINDING"); //$NON-NLS-1$
        var init = ((VariableDeclarationFragment) ast.findDeclaringNode(binding)).getInitializer();
        require(init instanceof ArrayInitializer array && array.expressions().size() == count, "QUADRATIC_DENOMINATOR_TABLE"); //$NON-NLS-1$
        var result = new ArrayList<Coefficient>();
        for (Object item : ((ArrayInitializer) init).expressions()) {
            require(item instanceof MethodInvocation call && signature(call.resolveMethodBinding(), FIELD, "of", true, BIG_INTEGER, BIG_INTEGER), //$NON-NLS-1$
                    "QUADRATIC_DENOMINATOR_TABLE_FACTORY"); //$NON-NLS-1$
            var values = ((MethodInvocation) item).arguments();
            result.add(new Coefficient(component((Expression) values.getFirst()), component((Expression) values.get(1))));
        }
        result.add(ONE);
        return List.copyOf(result);
    }

    private static BigInteger component(Expression expression) {
        if (expression instanceof Name name && name.resolveBinding() instanceof IVariableBinding constant
                && type(constant.getDeclaringClass(), BIG_INTEGER) && !constant.getDeclaringClass().isFromSource()
                && Modifier.isStatic(constant.getModifiers()) && Modifier.isFinal(constant.getModifiers())
                && constant.getName().equals("ZERO")) return BigInteger.ZERO; //$NON-NLS-1$
        if (expression instanceof MethodInvocation call && signature(call.resolveMethodBinding(), BIG_INTEGER, "valueOf", true, "long") //$NON-NLS-1$ //$NON-NLS-2$
                && ((Expression) call.arguments().getFirst()).resolveConstantExpressionValue() instanceof Number value)
            return BigInteger.valueOf(value.longValue());
        if (expression instanceof ClassInstanceCreation creation && creation.getAnonymousClassDeclaration() == null
                && signature(creation.resolveConstructorBinding(), BIG_INTEGER, "BigInteger", false, "java.lang.String", "int") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                && creation.arguments().getFirst() instanceof StringLiteral text
                && ((Expression) creation.arguments().get(1)).resolveConstantExpressionValue() instanceof Integer radix)
            return new BigInteger(text.getLiteralValue(), radix);
        throw new IllegalArgumentException("QUADRATIC_DENOMINATOR_COEFFICIENT"); //$NON-NLS-1$
    }

    private static void collectCalls(ASTNode node, Map<String, Integer> calls, JavaValueSourceSummary.Work work) {
        node.accept(new ASTVisitor() {
            @Override public void preVisit(ASTNode child) { work.charge(1); }
            @Override public boolean visit(MethodInvocation call) {
                var binding = call.resolveMethodBinding();
                String name = call.getName().getIdentifier();
                boolean checked = switch (name) {
                    case "horner" -> signature(binding, MAP, name, true, FIELD + "[]", FIELD); //$NON-NLS-1$ //$NON-NLS-2$
                    case REDUCED -> signature(binding, MAP, name, true, FIELD);
                    case "ofUnchecked" -> signature(binding, POINT, name, true, FIELD, FIELD); //$NON-NLS-1$
                    case "of" -> signature(binding, FIELD, name, true, "long", "long"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    case "add", "mul" -> signature(binding, FIELD, name, false, FIELD); //$NON-NLS-1$ //$NON-NLS-2$
                    case "square", "inverse", "c0", "c1" -> signature(binding, FIELD, name, false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    case "getClass" -> signature(binding, "java.lang.Object", name, false); //$NON-NLS-1$ //$NON-NLS-2$
                    case "signum" -> signature(binding, BIG_INTEGER, name, false); //$NON-NLS-1$
                    case "compareTo" -> signature(binding, BIG_INTEGER, name, false, BIG_INTEGER); //$NON-NLS-1$
                    default -> false;
                };
                require(checked, "QUADRATIC_DENOMINATOR_CALL " + name); //$NON-NLS-1$
                calls.merge(name, 1, Integer::sum);
                return true;
            }
        });
    }

    private static boolean signature(IMethodBinding binding, String owner, String name, boolean isStatic, String... parameters) {
        if (binding == null || binding.isRecovered() || !binding.getName().equals(name)
                || !type(binding.getDeclaringClass(), owner) || binding.getDeclaringClass().isFromSource() != owner.startsWith(BC)
                || Modifier.isStatic(binding.getModifiers()) != isStatic || binding.getParameterTypes().length != parameters.length) return false;
        for (int i = 0; i < parameters.length; i++) if (!type(binding.getParameterTypes()[i], parameters[i])) return false;
        return true;
    }

    private static boolean type(ITypeBinding binding, String name) {
        return binding != null && !binding.isRecovered() && binding.getQualifiedName().equals(name);
    }

    private static JavaSourceRewritePlan.Node anchor(ASTNode node, String source) {
        int start = node.getStartPosition();
        String key = node instanceof VariableDeclarationStatement declaration
                ? ((VariableDeclarationFragment) declaration.fragments().getFirst()).resolveBinding().getKey() : ""; //$NON-NLS-1$
        return new JavaSourceRewritePlan.Node("source@" + start, start, node.getLength(), node.getNodeType(), key, //$NON-NLS-1$
                MathematicalAnalysis.digest(source.substring(start, end(node))));
    }

    private static long signedLong(BigInteger value, BigInteger modulus) {
        return (value.shiftLeft(1).compareTo(modulus) > 0 ? value.subtract(modulus) : value).longValueExact();
    }

    private static boolean overlaps(int a, int length, int b, int size) {
        return a < (long) b + size && b < (long) a + length;
    }

    private static int end(ASTNode node) { return node.getStartPosition() + node.getLength(); }

    private static void require(boolean condition, String failure) {
        if (!condition) throw new IllegalArgumentException(failure);
    }
}
