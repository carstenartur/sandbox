/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.sandbox.jdt.internal.corext.fix.math.JavaGuardedFieldSource.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.*;
import de.regelsuche.math.algorithms.modular.QuadraticRootProgram;
import de.regelsuche.math.algorithms.modular.QuadraticRootProgram.Arm;
import de.regelsuche.math.algorithms.modular.QuadraticRootProgram.Step;

/** Emits field opcodes and decodes the reparsed Java instructions using resolved register bindings.
 * Guard and binary Jacobi are trusted primitive lowerings, bound in full including all JDK calls. */
final class JavaFieldProgramLowering {
    private final JavaGuardedFieldSource site;
    private final String prefix, p, factory, indent;
    private final JavaFieldGuardFacts facts;
    private final String witness;
    private final List<Ref> boundInputs;
    private final String modulusKey;
    private final Map<String,String> external = new HashMap<>();

    JavaFieldProgramLowering(JavaGuardedFieldSource site, String prefix, String indent) {
        this(site, prefix, indent, null, "", site.inputs, site.modulus.key());
    }

    JavaFieldProgramLowering(JavaGuardedFieldSource site, String prefix, String indent, JavaFieldGuardFacts facts,
            String witness, List<Ref> boundInputs, String modulusKey) {
        this.site = site; this.prefix = prefix; this.indent = indent;
        this.facts = facts; this.witness = witness; this.boundInputs = List.copyOf(boundInputs); this.modulusKey = modulusKey;
        p = site.modulus.spelling();
        factory = (site.sink.getExpression() == null ? "" : ((Name)site.sink.getExpression()).getFullyQualifiedName() + ".")
                + site.sink.getName().getIdentifier();
        for (var ref : boundInputs) external.put(ref.spelling(), ref.key());
        external.put(p, modulusKey);
        for (var ref : site.constants.values()) external.put(ref.spelling(), ref.key());
    }

    String emit(QuadraticRootProgram program) {
        var out = new StringBuilder("if (").append(guard()).append(") {\n");
        emitSteps(out, program.prefix(), 3, "    ");
        out.append("    if (").append(reg(program.guardLeft())).append(".equals(").append(reg(program.guardRight())).append(")) {\n");
        arm(out, program.equal(), program.prefix().size() + 3, "        ");
        out.append("    }\n");
        arm(out, program.unequal(), program.prefix().size() + 3, "    ");
        out.append("}\n");
        return out.toString().replace("\n", "\n" + indent);
    }

    private String guard() {
        String a = reg(0), b = reg(1), g = reg(2);
        var checks = new ArrayList<String>();
        var refs = new ArrayList<>(site.inputs);
        refs.add(site.modulus);
        refs.addAll(site.constants.values());
        for (var ref : refs) {
            String s = ref.spelling();
            int role = site.inputs.indexOf(ref);
            if (facts != null && role >= 0 && facts.inputs().get(role).exactClass()) continue;
            // Class.isInstance is null-safe and does not introduce a nullable flow fact into the
            // unchanged fallback. The retained zero test has already dereferenced the imaginary input.
            checks.add((s.equals(b) && facts == null ? "" : BIG + ".class.isInstance(" + s + ") && ") + s + ".getClass() == java.math.BigInteger.class");
        }
        for (var input : site.inputs) {
            String s = input.spelling();
            var fact = facts == null ? null : facts.inputs().get(site.inputs.indexOf(input));
            var bounds = new ArrayList<String>();
            if (s.equals(b) || fact == null || !fact.nonnegative())
                bounds.add(s + (s.equals(b) ? ".signum() > 0" : ".signum() >= 0"));
            if (fact == null || !fact.belowModulus()) bounds.add(s + ".compareTo(" + p + ") < 0");
            if (!bounds.isEmpty()) checks.add(String.join(" && ", bounds));
        }
        String norm = g + ".multiply(" + g + ").mod(" + p + ").equals(" + a + ".multiply(" + a + ").add("
                + b + ".multiply(" + b + ")).mod(" + p + "))";
        checks.add(witness.isEmpty() ? norm : "(" + witness + " || " + norm + ")");
        return String.join("\n        && ", checks);
    }

    private String reg(int index) { return index < 3 ? site.inputs.get(index).spelling() : prefix + index; }
    private String constant(BigInteger value) {
        for (var ref : site.constants.values()) if (value.equals(ref.value())) return ref.spelling();
        if (value.bitLength() <= 63) return BIG + ".valueOf(" + value + "L)";
        for (var ref : site.constants.values()) if (value.add(BigInteger.ONE).equals(ref.value()))
            return ref.spelling() + ".subtract(" + BIG + ".ONE)";
        return "new java.math.BigInteger(\"" + value.toString(16) + "\", 16)";
    }

    private void emitSteps(StringBuilder out, List<Step> steps, int index, String spaces) {
        for (var step : steps) {
            String left = step.left() < 0 ? "" : reg(step.left());
            String right = step.right() < 0 ? "" : reg(step.right());
            String mod = ".mod(" + p + ")";
            String expr = switch (step.op()) {
                case CONSTANT -> constant(step.literal());
                case ADD -> left + ".add(" + right + ")" + mod;
                case SUBTRACT -> left + ".subtract(" + right + ")" + mod;
                case MULTIPLY -> left + ".multiply(" + right + ")" + mod;
                case SQUARE -> left + ".multiply(" + left + ")" + mod;
                case NEGATE -> left + ".negate()" + mod;
                case INVERSE -> left + ".modInverse(" + p + ")";
                case POWER -> left + ".modPow(" + constant(step.literal()) + ", " + p + ")";
            };
            out.append(spaces).append(BIG).append(' ').append(reg(index++)).append(" = ").append(expr).append(";\n");
        }
    }

    private void arm(StringBuilder out, Arm arm, int index, String spaces) {
        emitSteps(out, arm.steps(), index, spaces);
        String x = reg(arm.real()), y = reg(arm.imaginary());
        if (arm.normalizeRealCharacter()) {
            out.append(spaces).append("{\n");
            String inner = jacobi(x, y);
            for (String line : inner.lines().toList()) out.append(spaces).append("    ").append(line).append('\n');
            out.append(spaces).append("}\n");
        }
        out.append(spaces).append("return ").append(factory).append('(').append(x).append(", ").append(y).append(");\n");
    }

    private String jacobi(String x, String y) {
        String n = prefix + "N", m = prefix + "M", s = prefix + "Sign", t = prefix + "Twos", r = prefix + "Rem";
        return """
                java.math.BigInteger %1$s = %6$s, %2$s = %8$s;
                int %3$s = 1;
                while (%1$s.signum() != 0) {
                    int %4$s = %1$s.getLowestSetBit();
                    %1$s = %1$s.shiftRight(%4$s);
                    if ((%4$s & 1) != 0 && ((%2$s.intValue() & 7) == 3 || (%2$s.intValue() & 7) == 5)) %3$s = -%3$s;
                    if (%1$s.testBit(1) && %2$s.testBit(1)) %3$s = -%3$s;
                    java.math.BigInteger %5$s = %2$s.mod(%1$s);
                    %2$s = %1$s;
                    %1$s = %5$s;
                }
                if (%3$s < 0) return %9$s(%6$s.negate().mod(%8$s), %7$s.negate().mod(%8$s));
                """.replace("%1$s", n).replace("%2$s", m).replace("%3$s", s)
                .replace("%4$s", t).replace("%5$s", r).replace("%6$s", x)
                .replace("%7$s", y).replace("%8$s", p).replace("%9$s", factory);
    }

    QuadraticRootProgram decode(IfStatement fast) {
        require(fast.getElseStatement() == null && text(fast.getExpression()).equals(guard().replace("\n", "\n" + indent)));
        checkBindings(fast);
        var block = requireType(fast.getThenStatement(), Block.class);
        List<?> body = block.statements();
        int split = 0;
        while (split < body.size() && body.get(split) instanceof VariableDeclarationStatement) split++;
        require(split < body.size());
        var branch = requireType(body.get(split), IfStatement.class);
        require(branch.getElseStatement() == null);
        var registers = new HashMap<String,Integer>();
        for (int i = 0; i < 3; i++) registers.put(boundInputs.get(i).key(), i);
        var prefixSteps = instructions(body.subList(0, split), registers);
        var predicate = requireType(branch.getExpression(), MethodInvocation.class);
        require(jdk(predicate, "equals", "java.lang.Object"));
        int lhs = register(registers, predicate.getExpression()), rhs = register(registers, arg(predicate, 0));
        return new QuadraticRootProgram(prefixSteps, lhs, rhs, exit(requireType(branch.getThenStatement(), Block.class).statements(), registers, "        "),
                exit(body.subList(split + 1, body.size()), registers, "    "));
    }

    private Arm exit(List<?> statements, Map<String,Integer> before, String spaces) {
        var registers = new HashMap<>(before);
        require(!statements.isEmpty());
        var ret = requireType(statements.getLast(), ReturnStatement.class);
        boolean normalized = statements.size() > 1 && statements.get(statements.size() - 2) instanceof Block;
        var steps = instructions(statements.subList(0, statements.size() - (normalized ? 2 : 1)), registers);
        var output = requireType(ret.getExpression(), MethodInvocation.class);
        require(staticCall(output) && site.sink.resolveMethodBinding().isEqualTo(output.resolveMethodBinding()));
        int x = register(registers, arg(output, 0)), y = register(registers, arg(output, 1));
        if (normalized) {
            var correction = (Block)statements.get(statements.size() - 2);
            String expected = "{\n" + jacobi(reg(x), reg(y)).lines().map(line -> indent + spaces + "    " + line + "\n")
                    .collect(java.util.stream.Collectors.joining()) + indent + spaces + "}";
            require(text(correction).equals(expected));
        }
        return new Arm(steps, x, y, normalized);
    }

    private List<Step> instructions(List<?> declarations, Map<String,Integer> registers) {
        var steps = new ArrayList<Step>();
        for (Object item : declarations) {
            var var = requireType(item, VariableDeclarationStatement.class);
            require(var.fragments().size() == 1);
            var fragment = (VariableDeclarationFragment)var.fragments().getFirst();
            var binding = fragment.resolveBinding();
            require(big(binding.getType()) && fragment.getName().getIdentifier().equals(reg(registers.size())));
            var expr = fragment.getInitializer();
            Step step;
            if (expr instanceof MethodInvocation call && jdk(call, "modPow", BIG, BIG)) {
                modulus(arg(call, 1));
                step = Step.power(register(registers, call.getExpression()), literal(arg(call, 0)));
            } else if (expr instanceof MethodInvocation call && jdk(call, "modInverse", BIG)) {
                modulus(arg(call, 0)); step = Step.inverse(register(registers, call.getExpression()));
            } else if (expr instanceof MethodInvocation call && jdk(call, "mod", BIG)) {
                modulus(arg(call, 0));
                var op = requireType(call.getExpression(), MethodInvocation.class);
                int a = register(registers, op.getExpression());
                if (jdk(op, "negate")) step = Step.negate(a);
                else {
                    require(op.arguments().size() == 1);
                    int b = register(registers, arg(op, 0));
                    if (jdk(op, "add", BIG)) step = Step.add(a, b);
                    else if (jdk(op, "subtract", BIG)) step = Step.subtract(a, b);
                    else {
                        require(jdk(op, "multiply", BIG));
                        step = a == b ? Step.square(a) : Step.multiply(a, b);
                    }
                }
            } else step = Step.constant(literal(expr));
            registers.put(key(binding), registers.size());
            steps.add(step);
        }
        return List.copyOf(steps);
    }

    private BigInteger literal(Expression expr) {
        if (expr instanceof MethodInvocation call && staticCall(call) && jdk(call, "valueOf", "long")
                && arg(call, 0) instanceof NumberLiteral number && number.resolveConstantExpressionValue() instanceof Long l)
            return BigInteger.valueOf(l);
        if (expr instanceof MethodInvocation call && jdk(call, "subtract", BIG)) {
            var one = site.checkedVariable(arg(call, 0));
            require(big(one.getDeclaringClass()) && one.getName().equals("ONE"));
            return literal(call.getExpression()).subtract(BigInteger.ONE);
        }
        if (expr instanceof ClassInstanceCreation creation && big(creation.resolveTypeBinding())
                && creation.getAnonymousClassDeclaration() == null && creation.arguments().size() == 2
                && creation.arguments().getFirst() instanceof StringLiteral hex && hex.getLiteralValue().length() <= 1024
                && integer((Expression)creation.arguments().get(1)) == 16) return new BigInteger(hex.getLiteralValue(), 16);
        Ref ref = site.constants.get(key(site.checkedVariable(expr))); require(ref != null);
        return ref.value();
    }

    private void checkBindings(IfStatement fast) {
        fast.accept(new ASTVisitor() {
            @Override public void preVisit(ASTNode node) { site.work.charge(1); }
            @Override public boolean visit(SimpleName n) { name(n); return true; }
            @Override public boolean visit(QualifiedName n) { name(n); return true; }
            private void name(Name n) {
                String id = external.get(n.getFullyQualifiedName());
                if (id != null) require(id.equals(key(n.resolveBinding())));
            }
            @Override public boolean visit(TypeLiteral literal) { require(big(literal.getType().resolveBinding())); return true; }
            @Override public boolean visit(MethodInvocation call) {
                var binding = call.resolveMethodBinding();
                require(binding != null && !binding.isRecovered() && (big(binding.getDeclaringClass())
                        || binding.getDeclaringClass().getQualifiedName().equals("java.lang.Object") && binding.getName().equals("getClass")
                        || binding.getMethodDeclaration().getDeclaringClass().getQualifiedName().equals("java.lang.Class") && binding.getName().equals("isInstance")
                        || binding.isEqualTo(site.sink.resolveMethodBinding()) && staticCall(call)));
                return true;
            }
        });
    }
    private String text(ASTNode node) { return site.source.substring(node.getStartPosition(), node.getStartPosition() + node.getLength()); }
    private void modulus(Expression expr) { require(modulusKey.equals(key(variable(expr)))); }
    private static int register(Map<String,Integer> registers, Expression expr) {
        Integer result = registers.get(key(variable(expr))); require(result != null); return result;
    }
}
