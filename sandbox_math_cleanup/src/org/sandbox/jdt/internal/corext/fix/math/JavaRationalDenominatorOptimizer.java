/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.dom.*;

import de.regelsuche.math.algorithms.polynomial.RationalDenominatorReduction;
import de.regelsuche.math.algorithms.polynomial.RationalDenominatorReduction.Coefficient;
import de.regelsuche.math.algorithms.polynomial.RationalDenominatorReduction.GuardedPlan;
import de.regelsuche.math.algorithms.polynomial.RationalDenominatorReduction.Plan;
import de.regelsuche.sdk.optimization.CheckedPolicy;
import de.regelsuche.sdk.optimization.ComputationOptimizer;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.RuntimeObligations;
import de.regelsuche.sdk.optimization.SourceEvaluationTrace;
import de.regelsuche.sdk.optimization.VerificationEvidence;

/** Projects a pair of scalar Horner denominators and applies a checked common inverse. */
final class JavaRationalDenominatorOptimizer {
    private static final String DESCRIPTION = "Verified common polynomial denominator and inverse"; //$NON-NLS-1$
    private static final String SCHEMA = "rational-denominator/1"; //$NON-NLS-1$

    private JavaRationalDenominatorOptimizer() { }

    private record Horner(MethodDeclaration method, IVariableBinding table, IVariableBinding input) { }
    private record Denominator(Horner horner, List<Coefficient> coefficients, SimpleName modulus) { }
    private record Site(Denominator x, Denominator y, Statement start, VariableDeclarationStatement numerator,
            VariableDeclarationStatement resultX, VariableDeclarationStatement resultY, ReturnStatement end,
            MethodInvocation inverseX, MethodInvocation inverseY, SimpleName input, SimpleName weight) { }
    private record Emission(String source, String modulusField, String coefficientsField) { }

    static void optimize(CompilationUnit ast, String source, MathCleanUpOptions options, IProgressMonitor monitor,
            int selectionOffset, int selectionLength, Map<String, String> compiler,
            MathematicalAnalysis.SourceEnvironment environment, List<MathematicalAnalysis.Replacement> replacements,
            List<MathematicalAnalysis.VerifiedRegion> evidence, List<MathematicalAnalysis.Diagnostic> diagnostics,
            JavaValueSourceSummary.Work work) {
        if (!options.mathematicalOptIn() || !options.kinds().contains(NumericKind.BIG_INTEGER)
                || options.goal() != OptimizationGoal.LOWER_ESTIMATED_RUNTIME || !source.contains("modInverse")) return; //$NON-NLS-1$
        for (Object entry : ast.types()) {
            if (!(entry instanceof TypeDeclaration owner) || owner.isInterface()) continue;
            Set<String> generatedFields = new HashSet<>();
            for (MethodDeclaration method : owner.getMethods()) {
                try {
                    work.charge(1);
                    if (method.getBody() == null || method.resolveBinding() == null) continue;
                    int offset = method.getStartPosition(), length = method.getLength();
                    if (selectionOffset >= 0 && !overlaps(offset, length, selectionOffset, Math.max(1, selectionLength))) continue;
                    if (replacements.stream().anyMatch(edit -> overlaps(offset, length, edit.offset(), edit.length()))) continue;
                    String original = text(source, method);
                    if (original.contains(JavaComputationExtractor.ORIGINAL_FALLBACK_MARKER)
                            || original.indexOf("modInverse") == original.lastIndexOf("modInverse")) continue; //$NON-NLS-1$ //$NON-NLS-2$
                    work.charge(length);
                    Site site = inspect(ast, owner, method, false, work);
                    var proposed = RationalDenominatorReduction.inferModulus(site.x().coefficients(), site.y().coefficients(),
                        (stage, units) -> work.charge(units));
                    if (proposed.isEmpty()) continue;
                    GuardedPlan factored = proposed.orElseThrow();
                    Emission emission = emit(source, method, site, factored, generatedFields, work);
                    String generated = source.substring(0, offset) + emission.source() + source.substring(offset + length);
                    work.charge(generated.length());
                    var parsed = MathematicalAnalysis.checkGeneratedSource(ast, source, generated, compiler, options.targetJava(),
                        monitor, environment, offset, length, emission.source().length());
                    long verificationStart = work.used;
                    require(parsed.findDeclaringNode(method.resolveBinding().getKey()) instanceof MethodDeclaration);
                    replay(parsed, (MethodDeclaration)parsed.findDeclaringNode(method.resolveBinding().getKey()), work);
                    String beforeHash = JavaComputationTask.hash(original);
                    String afterHash = JavaComputationTask.hash(emission.source());
                    var absentScalarTrace = new SourceEvaluationTrace(List.of());
                    var proof = new VerificationEvidence(SCHEMA, ComputationOptimizer.SEMANTICS_REVISION,
                        "rational-denominator-coefficient-replay/1", "java-horner-projection/1", beforeHash, afterHash, beforeHash, //$NON-NLS-1$ //$NON-NLS-2$
                        JavaComputationTask.hash(factored.modulus().toString()), options.safety(), CheckedPolicy.NONE,
                        new RuntimeObligations(RuntimeObligations.GuardKind.NONE, absentScalarTrace, absentScalarTrace,
                            false, false, false, 0, 0),
                        List.of("MONIC_DENOMINATOR_SQUARE_AND_CUBE", "SAME_INVERTIBILITY_AND_CANONICAL_INVERSES", "EXACT_MODULUS_AND_OPERAND_GUARD"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        work.used - verificationStart);
                    var sourcePlan = new JavaSourceRewritePlan("Source.java", JavaComputationTask.hash(source), source.length(), //$NON-NLS-1$
                        offset, length, List.of(), List.of(), Map.of(),
                        List.of(new JavaSourceRewritePlan.Change(offset, length, beforeHash, emission.source())));
                    replacements.add(new MathematicalAnalysis.Replacement(offset, length, emission.source(), DESCRIPTION));
                    evidence.add(new MathematicalAnalysis.VerifiedRegion(offset, length, proof, null, original,
                        emission.source(), null, sourcePlan));
                    generatedFields.add(emission.modulusField());
                    generatedFields.add(emission.coefficientsField());
                } catch (UnsupportedShape unsupported) {
                    // A similarly written method may have entirely different source semantics.
                } catch (IllegalArgumentException unavailable) {
                    diagnostics.add(new MathematicalAnalysis.Diagnostic("RATIONAL_DENOMINATOR_UNAVAILABLE", unavailable.getMessage(), //$NON-NLS-1$
                        method.getStartPosition(), method.getLength(), MathematicalAnalysis.Stage.SOURCE));
                    return;
                }
            }
        }
    }

    private static Site inspect(CompilationUnit ast, TypeDeclaration owner, MethodDeclaration method, boolean generated,
            JavaValueSourceSummary.Work work) {
        var statements = method.getBody().statements();
        int start = statements.size() - 6;
        int numerator = start - (generated ? 2 : 1);
        require(numerator >= 0 && statements.getLast() instanceof ReturnStatement);
        var numeratorX = declaration(statements.get(numerator));
        var denominatorX = declaration(statements.get(start));
        var numeratorY = declaration(statements.get(start + 1));
        var denominatorY = declaration(statements.get(start + 2));
        var resultX = declaration(statements.get(start + 3));
        var resultY = declaration(statements.get(start + 4));
        var x = denominator(ast, owner, denominatorX.getInitializer(), work);
        var y = denominator(ast, owner, denominatorY.getInitializer(), work);
        require(same(x.modulus(), y.modulus().resolveBinding()) && x.horner().input().isEqualTo(y.horner().input())
            && x.horner().method() == y.horner().method());
        var horner = x.horner().method();
        var nx = hornerCall(ast, owner, numeratorX.getInitializer());
        var ny = hornerCall(ast, owner, numeratorY.getInitializer());
        require(nx.method() == horner && ny.method() == horner && nx.input().isEqualTo(x.horner().input())
            && ny.input().isEqualTo(x.horner().input()));
        verifyHorner(horner, (IVariableBinding)x.modulus().resolveBinding());
        for (var table : List.of(x.horner().table(), y.horner().table(), nx.table(), ny.table())) {
            coefficientTable(ast, table, work);
            unmodifiedTable(owner, table, horner.resolveBinding(), work);
        }
        var xMultiply = jdk(receiverOfMod(resultX.getInitializer(), x.modulus()), "multiply", 1); //$NON-NLS-1$
        require(same(xMultiply.getExpression(), numeratorX.resolveBinding()));
        var inverseX = inverse(argument(xMultiply, 0), denominatorX.resolveBinding(), x.modulus());
        var yMultiply = jdk(receiverOfMod(resultY.getInitializer(), x.modulus()), "multiply", 1); //$NON-NLS-1$
        var weighted = jdk(receiverOfMod(yMultiply.getExpression(), x.modulus()), "multiply", 1); //$NON-NLS-1$
        require(same(argument(weighted, 0), numeratorY.resolveBinding()) && weighted.getExpression() instanceof SimpleName
            && variable(weighted.getExpression()) != null && !variable(weighted.getExpression()).isField());
        var inverseY = inverse(argument(yMultiply, 0), denominatorY.resolveBinding(), x.modulus());
        onlyRead(method.getBody(), denominatorX.resolveBinding(), inverseX.getExpression(), work);
        onlyRead(method.getBody(), denominatorY.resolveBinding(), inverseY.getExpression(), work);
        var hornerCall = jdk(jdk(denominatorX.getInitializer(), "mod", 1).getExpression(), "add", 1).getExpression(); //$NON-NLS-1$ //$NON-NLS-2$
        var input = (SimpleName)argument((MethodInvocation)hornerCall, 2);
        var weight = (SimpleName)weighted.getExpression();
        require(inScopeBefore(ast, input, denominatorX) && inScopeBefore(ast, weight, denominatorX));
        return new Site(x, y, (Statement)denominatorX.getParent(), (VariableDeclarationStatement)numeratorY.getParent(),
            (VariableDeclarationStatement)resultX.getParent(), (VariableDeclarationStatement)resultY.getParent(),
            (ReturnStatement)statements.getLast(), inverseX, inverseY, input, weight);
    }

    private static boolean inScopeBefore(CompilationUnit ast, SimpleName name, ASTNode before) {
        var declaration = ast.findDeclaringNode(name.resolveBinding());
        return declaration != null && declaration.getStartPosition() < before.getStartPosition();
    }

    private static Denominator denominator(CompilationUnit ast, TypeDeclaration owner, Expression initializer, JavaValueSourceSummary.Work work) {
        var mod = jdk(initializer, "mod", 1); //$NON-NLS-1$
        require(argument(mod, 0) instanceof SimpleName);
        var modulus = (SimpleName)argument(mod, 0);
        var field = variable(modulus);
        require(field != null && field.isField() && Modifier.isFinal(field.getModifiers()) && jdkType(field.getType())
            && field.getDeclaringClass().isEqualTo(owner.resolveBinding()));
        var add = jdk(mod.getExpression(), "add", 1); //$NON-NLS-1$
        var horner = hornerCall(ast, owner, add.getExpression());
        require(argument(add, 0) instanceof MethodInvocation);
        var call = (MethodInvocation)argument(add, 0);
        var power = ownMethod(ast, owner, call, 2);
        require(same(argument(call, 0), horner.input()) && length(argument(call, 1), horner.table()));
        require(power.getBody().statements().size() == 1 && power.getBody().statements().getFirst() instanceof ReturnStatement);
        var result = jdk(((ReturnStatement)power.getBody().statements().getFirst()).getExpression(), "modPow", 2); //$NON-NLS-1$
        var exponent = jdk(argument(result, 0), "valueOf", 1); //$NON-NLS-1$
        require(same(result.getExpression(), parameter(power, 0)) && same(argument(exponent, 0), parameter(power, 1))
            && argument(exponent, 0).resolveTypeBinding().isPrimitive() && same(argument(result, 1), field));
        var coefficients = new ArrayList<>(coefficientTable(ast, horner.table(), work));
        coefficients.add(new Coefficient(BigInteger.ONE, BigInteger.ZERO));
        return new Denominator(horner, List.copyOf(coefficients), modulus);
    }

    private static Horner hornerCall(CompilationUnit ast, TypeDeclaration owner, Expression expression) {
        require(expression instanceof MethodInvocation);
        var call = (MethodInvocation)expression;
        var helper = ownMethod(ast, owner, call, 3);
        var table = variable(argument(call, 0));
        var input = variable(argument(call, 2));
        require(table != null && argument(call, 0) instanceof SimpleName && input != null && !input.isField()
            && argument(call, 2) instanceof SimpleName && jdkType(input.getType()));
        require(argument(call, 1) instanceof InfixExpression);
        var top = (InfixExpression)argument(call, 1);
        require(infix(top, InfixExpression.Operator.MINUS) && length(top.getLeftOperand(), table) && number(top.getRightOperand(), 1));
        return new Horner(helper, table, input);
    }

    private static void verifyHorner(MethodDeclaration helper, IVariableBinding modulus) {
        var statements = helper.getBody().statements();
        require(statements.size() == 3 && statements.get(1) instanceof ForStatement && statements.get(2) instanceof ReturnStatement);
        var total = declaration(statements.getFirst());
        var table = parameter(helper, 0);
        var top = parameter(helper, 1);
        var input = parameter(helper, 2);
        require(arrayRead(total.getInitializer(), table, top));
        var loop = (ForStatement)statements.get(1);
        require(loop.initializers().size() == 1 && loop.initializers().getFirst() instanceof VariableDeclarationExpression);
        var initializer = (VariableDeclarationExpression)loop.initializers().getFirst();
        require(initializer.fragments().size() == 1);
        var index = (VariableDeclarationFragment)initializer.fragments().getFirst();
        require(index.getInitializer() instanceof InfixExpression && loop.getExpression() instanceof InfixExpression);
        var initial = (InfixExpression)index.getInitializer();
        var condition = (InfixExpression)loop.getExpression();
        var i = index.resolveBinding();
        require(i != null && "int".equals(i.getType().getName()) && infix(initial, InfixExpression.Operator.MINUS) //$NON-NLS-1$
            && same(initial.getLeftOperand(), top) && number(initial.getRightOperand(), 1)
            && infix(condition, InfixExpression.Operator.GREATER_EQUALS) && same(condition.getLeftOperand(), i)
            && number(condition.getRightOperand(), 0) && loop.updaters().size() == 1
            && loop.updaters().getFirst() instanceof PrefixExpression);
        var update = (PrefixExpression)loop.updaters().getFirst();
        require(update.getOperator() == PrefixExpression.Operator.DECREMENT && same(update.getOperand(), i));
        require(loop.getBody() instanceof Block && ((Block)loop.getBody()).statements().size() == 1
            && ((Block)loop.getBody()).statements().getFirst() instanceof ExpressionStatement);
        var expression = ((ExpressionStatement)((Block)loop.getBody()).statements().getFirst()).getExpression();
        require(expression instanceof Assignment);
        var assignment = (Assignment)expression;
        require(assignment.getOperator() == Assignment.Operator.ASSIGN && same(assignment.getLeftHandSide(), total.resolveBinding()));
        var reduce = jdk(assignment.getRightHandSide(), "mod", 1); //$NON-NLS-1$
        var sum = jdk(reduce.getExpression(), "add", 1); //$NON-NLS-1$
        var multiply = jdk(sum.getExpression(), "multiply", 1); //$NON-NLS-1$
        require(same(argument(reduce, 0), modulus) && arrayRead(argument(sum, 0), table, i)
            && same(multiply.getExpression(), total.resolveBinding()) && same(argument(multiply, 0), input)
            && same(((ReturnStatement)statements.getLast()).getExpression(), total.resolveBinding()));
    }

    private static List<Coefficient> coefficientTable(CompilationUnit ast, IVariableBinding table, JavaValueSourceSummary.Work work) {
        require(table != null && table.isField() && Modifier.isStatic(table.getModifiers()) && Modifier.isFinal(table.getModifiers())
            && Modifier.isPrivate(table.getModifiers()) && table.getType().isArray() && table.getType().getDimensions() == 1
            && jdkType(table.getType().getElementType()) && ast.findDeclaringNode(table) instanceof VariableDeclarationFragment);
        var fragment = (VariableDeclarationFragment)ast.findDeclaringNode(table);
        require(fragment.getInitializer() instanceof ArrayInitializer);
        var values = ((ArrayInitializer)fragment.getInitializer()).expressions();
        require(!values.isEmpty() && values.size() <= 64);
        var coefficients = new ArrayList<Coefficient>();
        for (Object value : values) coefficients.add(new Coefficient(constant((Expression)value, work), BigInteger.ZERO));
        return List.copyOf(coefficients);
    }

    private static BigInteger constant(Expression value, JavaValueSourceSummary.Work work) {
        work.charge(1);
        if (value instanceof ClassInstanceCreation creation) {
            require(jdkType(creation.resolveTypeBinding()) && creation.getExpression() == null
                && creation.getAnonymousClassDeclaration() == null && creation.arguments().size() == 2
                && creation.arguments().getFirst() instanceof StringLiteral);
            String digits = ((StringLiteral)creation.arguments().getFirst()).getLiteralValue();
            var radix = ((Expression)creation.arguments().get(1)).resolveConstantExpressionValue();
            require(radix instanceof Integer && digits.length() <= 1024);
            work.charge(digits.length());
            return new BigInteger(digits, (Integer)radix);
        }
        if (value instanceof MethodInvocation) {
            var valueOf = jdk(value, "valueOf", 1); //$NON-NLS-1$
            var literal = argument(valueOf, 0).resolveConstantExpressionValue();
            require(literal instanceof Number && argument(valueOf, 0).resolveTypeBinding().isPrimitive());
            return BigInteger.valueOf(((Number)literal).longValue());
        }
        var binding = variable(value);
        if (binding == null || !jdkType(binding.getDeclaringClass()) || !Modifier.isStatic(binding.getModifiers())
                || !(value instanceof Name)) {
            throw new UnsupportedShape();
        }
        return switch (binding.getName()) {
            case "ZERO" -> BigInteger.ZERO; //$NON-NLS-1$
            case "ONE" -> BigInteger.ONE; //$NON-NLS-1$
            case "TWO" -> BigInteger.TWO; //$NON-NLS-1$
            case "TEN" -> BigInteger.TEN; //$NON-NLS-1$
            default -> throw new UnsupportedShape();
        };
    }

    private static void unmodifiedTable(TypeDeclaration owner, IVariableBinding table, IMethodBinding horner, JavaValueSourceSummary.Work work) {
        owner.accept(new ASTVisitor() {
            @Override public boolean visit(SimpleName name) {
                work.charge(1);
                if (name.isDeclaration() || !same(name, table)) return true;
                if (name.getParent() instanceof QualifiedName qualified && length(qualified, table)) return true;
                if (name.getParent() instanceof InfixExpression comparison && comparison.getLeftOperand() == name
                        && infix(comparison, InfixExpression.Operator.NOT_EQUALS) && comparison.getRightOperand() instanceof NullLiteral) return true;
                if (name.getParent() instanceof MethodInvocation call && !call.arguments().isEmpty() && call.arguments().getFirst() == name
                        && horner.isEqualTo(call.resolveMethodBinding())) return true;
                throw new UnsupportedShape();
            }
        });
    }

    private static void onlyRead(Block body, IVariableBinding binding, Expression expected, JavaValueSourceSummary.Work work) {
        body.accept(new ASTVisitor() {
            @Override public boolean visit(SimpleName name) {
                work.charge(1);
                require(name.isDeclaration() || name == expected || !same(name, binding));
                return true;
            }
        });
    }

    private static Emission emit(String source, MethodDeclaration method, Site site, GuardedPlan proposal,
            Set<String> generatedFields, JavaValueSourceSummary.Work work) {
        var reserved = new HashSet<>(generatedFields);
        method.getParent().accept(new ASTVisitor() {
            @Override public boolean visit(SimpleName name) {
                work.charge(1);
                reserved.add(name.getIdentifier());
                return true;
            }
        });
        String field = fresh(reserved, "mathDenominatorCoefficients"); //$NON-NLS-1$
        String modulus = fresh(reserved, "mathDenominatorModulus"); //$NON-NLS-1$
        String inverse = fresh(reserved, "mathDenominatorInverse"); //$NON-NLS-1$
        String square = fresh(reserved, "mathDenominatorInverseSquared"); //$NON-NLS-1$
        String cube = fresh(reserved, "mathDenominatorInverseCubed"); //$NON-NLS-1$
        String p = site.x().modulus().getIdentifier();
        String input = site.input().getIdentifier();
        String memberIndent = indent(source, method.getStartPosition());
        String baseIndent = indent(source, site.start().getStartPosition());
        String thenIndent = baseIndent + "    "; //$NON-NLS-1$
        String horner = site.x().horner().method().getName().getIdentifier();
        String coefficientIndent = memberIndent + "    "; //$NON-NLS-1$
        var factors = new StringJoiner(",\n" + coefficientIndent, "{\n" + coefficientIndent, "\n" + memberIndent + "}"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        for (var c : proposal.plan().denominator())
            factors.add(BigInteger.ONE.equals(c.real()) ? "java.math.BigInteger.ONE" : integerLiteral(c.real())); //$NON-NLS-1$
        String fields = "// Common factor of the two coordinate denominators, checked modulo " + modulus + ".\n" //$NON-NLS-1$ //$NON-NLS-2$
            + memberIndent + "private static final java.math.BigInteger " + modulus + " = " + integerLiteral(proposal.modulus()) + ";\n" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            + memberIndent + "private static final java.math.BigInteger[] " + field + " = " + factors + ";\n\n" + memberIndent; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        String guard = guard(site, proposal.modulus(), modulus, field, "\n" + thenIndent); //$NON-NLS-1$
        String optimized = "if (" + guard + ")\n" + baseIndent + "{\n" + thenIndent + text(source, site.numerator()) + "\n" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            + thenIndent + "java.math.BigInteger " + inverse + " = " + horner + "(" + field + ", " + field + ".length - 1, " + input + ").modInverse(" + p + ");\n" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
            + thenIndent + "java.math.BigInteger " + square + " = " + inverse + ".multiply(" + inverse + ").mod(" + p + ");\n" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
            + thenIndent + "java.math.BigInteger " + cube + " = " + square + ".multiply(" + inverse + ").mod(" + p + ");\n" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
            + thenIndent + substitute(source, site.resultX(), site.inverseX(), square) + "\n" //$NON-NLS-1$
            + thenIndent + substitute(source, site.resultY(), site.inverseY(), cube) + "\n" //$NON-NLS-1$
            + thenIndent + text(source, site.end()) + "\n" + baseIndent + "}\n\n" + baseIndent; //$NON-NLS-1$ //$NON-NLS-2$
        String before = source.substring(method.getStartPosition(), site.start().getStartPosition());
        String after = source.substring(site.start().getStartPosition(), method.getStartPosition() + method.getLength());
        return new Emission(fields + before + optimized + "// " + JavaComputationExtractor.ORIGINAL_FALLBACK_MARKER + "\n" + baseIndent + after, modulus, field); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static String guard(Site site, BigInteger modulus, String modulusField, String factorField, String newline) {
        String p = site.x().modulus().getIdentifier();
        String x = site.input().getIdentifier();
        String y = site.weight().getIdentifier();
        return exact(p) + " && " + p + ".equals(" + modulusField + ")" + newline //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            + "&& " + exact(x) + " && " + x + ".bitLength() <= " + modulus.bitLength() + newline //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            + "&& " + exact(y) + " && " + factorField + " != null" + newline //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            + "&& " + site.x().horner().table().getName() + " != null && " + site.y().horner().table().getName() + " != null"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    static boolean isRegion(MathematicalAnalysis.VerifiedRegion region) {
        return region.proof() != null && SCHEMA.equals(region.proof().schemaRevision());
    }

    static void verifyCombined(CompilationUnit ast, String source, MathematicalAnalysis.VerifiedRegion region,
            List<MathematicalAnalysis.Replacement> edits, JavaValueSourceSummary.Work work) {
        int start = region.offset();
        for (var edit : edits) {
            work.charge(1);
            if (edit.offset() < region.offset() || edit.offset() == region.offset() && edit.length() == 0)
                start += edit.replacement().length() - edit.length();
        }
        int end = start + region.replacement().length();
        require(start >= 0 && end <= source.length() && source.startsWith(region.replacement(), start));
        for (Object item : ast.types()) {
            if (!(item instanceof TypeDeclaration owner)) continue;
            for (var method : owner.getMethods()) {
                work.charge(1);
                if (method.getStartPosition() >= start && method.getStartPosition() + method.getLength() == end) {
                    replay(ast, method, work);
                    return;
                }
            }
        }
        throw new UnsupportedShape();
    }

    private static void replay(CompilationUnit parsed, MethodDeclaration method, JavaValueSourceSummary.Work work) {
        require(method.getParent() instanceof TypeDeclaration);
        var owner = (TypeDeclaration)method.getParent();
        Site sameSource = inspect(parsed, owner, method, true, work);
        var body = method.getBody().statements();
        require(body.get(body.size() - 7) instanceof IfStatement);
        var guard = (IfStatement)body.get(body.size() - 7);
        require(guard.getThenStatement() instanceof Block && guard.getElseStatement() == null);
        var statements = ((Block)guard.getThenStatement()).statements();
        require(statements.size() == 7);
        var inverse = declaration(statements.get(1));
        var inversion = jdk(inverse.getInitializer(), "modInverse", 1); //$NON-NLS-1$
        require(same(argument(inversion, 0), sameSource.x().modulus().resolveBinding()));
        var factored = hornerCall(parsed, owner, inversion.getExpression());
        require(factored.method() == sameSource.x().horner().method() && factored.input().isEqualTo(sameSource.x().horner().input()));
        SimpleName p = sameSource.x().modulus();
        var square = declaration(statements.get(2));
        var cube = declaration(statements.get(3));
        verifyProduct(square.getInitializer(), p, inverse.resolveBinding(), inverse.resolveBinding());
        verifyProduct(cube.getInitializer(), p, square.resolveBinding(), inverse.resolveBinding());
        var exactModulus = new IVariableBinding[1];
        guard.getExpression().accept(new ASTVisitor() {
            @Override public boolean visit(MethodInvocation call) {
                if (call.getName().getIdentifier().equals("equals") && same(call.getExpression(), p.resolveBinding()) //$NON-NLS-1$
                        && call.arguments().size() == 1) exactModulus[0] = variable(argument(call, 0));
                return true;
            }
        });
        var field = exactModulus[0];
        if (field == null || !field.isField() || !Modifier.isPrivate(field.getModifiers()) || !Modifier.isStatic(field.getModifiers())
                || !Modifier.isFinal(field.getModifiers()) || !(parsed.findDeclaringNode(field) instanceof VariableDeclarationFragment fragment)) {
            throw new UnsupportedShape();
        }
        BigInteger modulus = constant(fragment.getInitializer(), work);
        require(compact(guard.getExpression().toString()).equals(compact(guard(sameSource, modulus, field.getName(), factored.table().getName(), " ")))); //$NON-NLS-1$
        Map<String, IBinding> operands = new HashMap<>();
        for (SimpleName name : List.of(p, sameSource.input(), sameSource.weight())) operands.put(name.getIdentifier(), name.resolveBinding());
        for (var binding : List.of(field, factored.table(), sameSource.x().horner().table(), sameSource.y().horner().table())) operands.put(binding.getName(), binding);
        guard.getExpression().accept(new ASTVisitor() {
            @Override public boolean visit(SimpleName name) {
                var expected = operands.get(name.getIdentifier());
                if (expected != null) require(same(name, expected));
                return true;
            }
            @Override public boolean visit(TypeLiteral literal) {
                require(jdkType(literal.getType().resolveBinding()));
                return true;
            }
        });
        var checked = new Plan(coefficientTable(parsed, factored.table(), work));
        require(RationalDenominatorReduction.verify(sameSource.x().coefficients(), sameSource.y().coefficients(), modulus, checked,
            (stage, units) -> work.charge(units)));
    }

    private static String compact(String fragment) {
        return fragment.replaceAll("\\s+", ""); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static void verifyProduct(Expression expression, SimpleName modulus, IVariableBinding left, IVariableBinding right) {
        var product = jdk(receiverOfMod(expression, modulus), "multiply", 1); //$NON-NLS-1$
        require(same(product.getExpression(), left) && same(argument(product, 0), right));
    }

    private static MethodDeclaration ownMethod(CompilationUnit ast, TypeDeclaration owner, MethodInvocation call, int arity) {
        var binding = call.resolveMethodBinding();
        require(binding != null && !binding.isRecovered() && call.getExpression() == null && call.arguments().size() == arity
            && binding.getDeclaringClass().isEqualTo(owner.resolveBinding()) && Modifier.isPrivate(binding.getModifiers())
            && !Modifier.isSynchronized(binding.getModifiers()) && ast.findDeclaringNode(binding) instanceof MethodDeclaration);
        var method = (MethodDeclaration)ast.findDeclaringNode(binding);
        require(method.getBody() != null && method.parameters().size() == arity);
        return method;
    }
    private static IVariableBinding parameter(MethodDeclaration method, int i) {
        return ((SingleVariableDeclaration)method.parameters().get(i)).resolveBinding();
    }
    private static VariableDeclarationFragment declaration(Object statement) {
        require(statement instanceof VariableDeclarationStatement && ((VariableDeclarationStatement)statement).fragments().size() == 1);
        var fragment = (VariableDeclarationFragment)((VariableDeclarationStatement)statement).fragments().getFirst();
        require(fragment.resolveBinding() != null && jdkType(fragment.resolveBinding().getType()) && fragment.getInitializer() != null);
        return fragment;
    }
    private static MethodInvocation jdk(Expression expression, String method, int arity) {
        require(expression instanceof MethodInvocation);
        var call = (MethodInvocation)expression;
        var binding = call.resolveMethodBinding();
        if (binding == null || binding.isRecovered() || !jdkType(binding.getDeclaringClass()) || !jdkType(binding.getReturnType())
                || !binding.getName().equals(method) || call.arguments().size() != arity) {
            throw new UnsupportedShape();
        }
        if (Modifier.isStatic(binding.getModifiers())) {
            require(call.getExpression() instanceof Name name && name.resolveBinding() instanceof ITypeBinding type && jdkType(type));
        }
        return call;
    }
    private static boolean jdkType(ITypeBinding type) {
        return type != null && !type.isRecovered() && !type.isFromSource() && !type.isNested()
            && "java.math.BigInteger".equals(type.getBinaryName()); //$NON-NLS-1$
    }
    private static Expression receiverOfMod(Expression expression, SimpleName modulus) {
        var mod = jdk(expression, "mod", 1); //$NON-NLS-1$
        require(same(argument(mod, 0), modulus.resolveBinding()));
        return mod.getExpression();
    }
    private static MethodInvocation inverse(Expression expression, IVariableBinding denominator, SimpleName modulus) {
        var inverse = jdk(expression, "modInverse", 1); //$NON-NLS-1$
        require(same(inverse.getExpression(), denominator) && same(argument(inverse, 0), modulus.resolveBinding()));
        return inverse;
    }
    private static Expression argument(MethodInvocation call, int i) {
        return (Expression)call.arguments().get(i);
    }
    private static boolean arrayRead(Expression expression, IVariableBinding array, IVariableBinding index) {
        return expression instanceof ArrayAccess access && same(access.getArray(), array) && same(access.getIndex(), index);
    }
    private static boolean length(Expression expression, IVariableBinding table) {
        return expression instanceof QualifiedName name && name.getName().getIdentifier().equals("length") && same(name.getQualifier(), table); //$NON-NLS-1$
    }
    private static boolean infix(InfixExpression expression, InfixExpression.Operator operator) {
        return expression.getOperator() == operator && expression.extendedOperands().isEmpty();
    }
    private static boolean number(Expression expression, int n) {
        return expression.resolveConstantExpressionValue() instanceof Integer i && i == n;
    }
    private static IVariableBinding variable(Expression expression) {
        return expression instanceof Name name && name.resolveBinding() instanceof IVariableBinding field ? field : null;
    }
    private static boolean same(Expression expression, IBinding binding) {
        var value = variable(expression);
        return binding != null && value != null && value.isEqualTo(binding);
    }
    private static boolean overlaps(int a, int al, int b, int bl) {
        return a < (long)b + bl && b < (long)a + al;
    }
    private static String substitute(String source, ASTNode parent, ASTNode target, String value) {
        int from = target.getStartPosition(), end = from + target.getLength();
        return source.substring(parent.getStartPosition(), from) + value + source.substring(end, parent.getStartPosition() + parent.getLength());
    }
    private static String text(String source, ASTNode node) {
        return source.substring(node.getStartPosition(), node.getStartPosition() + node.getLength());
    }
    private static String indent(String source, int offset) {
        String prefix = source.substring(source.lastIndexOf('\n', Math.max(0, offset - 1)) + 1, offset);
        return prefix.isBlank() ? prefix : ""; //$NON-NLS-1$
    }
    private static String fresh(Set<String> reserved, String name) {
        while (!reserved.add(name)) name += "_"; //$NON-NLS-1$
        return name;
    }
    private static String integerLiteral(BigInteger value) {
        return "new java.math.BigInteger(\"" + value.toString(16) + "\", 16)"; //$NON-NLS-1$ //$NON-NLS-2$
    }
    private static String exact(String name) {
        return name + " != null && " + name + ".getClass() == java.math.BigInteger.class"; //$NON-NLS-1$ //$NON-NLS-2$
    }
    private static void require(boolean supported) {
        if (!supported) throw new UnsupportedShape();
    }
    private static final class UnsupportedShape extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
    }
}
