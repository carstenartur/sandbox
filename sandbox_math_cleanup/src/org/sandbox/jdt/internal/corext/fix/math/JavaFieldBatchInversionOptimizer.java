/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.dom.*;
import org.sandbox.jdt.internal.corext.fix.math.JavaValueSourceSummary.Work;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.Diagnostic;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.Replacement;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.SourceEnvironment;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.Stage;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.VerifiedRegion;
import de.regelsuche.ast.iteration.FieldInversionBatch;
import de.regelsuche.math.algorithms.iteration.FieldBatchInversionPlan;
import de.regelsuche.math.algorithms.iteration.FieldBatchInversionPlan.Invert;
import de.regelsuche.math.algorithms.iteration.FieldBatchInversionPlan.Multiply;
import de.regelsuche.sdk.optimization.iteration.FieldInversionSdk;

/** Proves the Java input domain for a checked pair of modular inverses. */
final class JavaFieldBatchInversionOptimizer {
    private static final String BIG = "java.math.BigInteger"; //$NON-NLS-1$
    private static final String ARITHMETIC_EXCEPTION = "java.lang.ArithmeticException"; //$NON-NLS-1$
    private static final String OBJECT = "java.lang.Object"; //$NON-NLS-1$
    private static final String INVERSE = "modInverse"; //$NON-NLS-1$
    private static final String MULTIPLY = "multiply"; //$NON-NLS-1$
    private static final String MOD = "mod"; //$NON-NLS-1$
    private static final int MINIMUM_MODULUS_BITS = 256;
    private static final int MAXIMUM_INPUT_BITS = 4096;
    // Even a balanced multiply tree at this depth stays far below BigInteger's range.
    private static final int MAXIMUM_INITIALIZER_DEPTH = 8;
    private static final int PREFIX_STATEMENTS = 3;
    private static final int MAXIMUM_SITES = 8;

    private record Read(String source, String key) { }

    private static final class PureInitializer {
        final Map<String, String> reads = new LinkedHashMap<>();
        final Set<String> moduli = new HashSet<>();
        MethodInvocation inverse;
        Read operand, modulus;
    }

    private record Site(VariableDeclarationStatement first, VariableDeclarationStatement second,
            PureInitializer left, PureInitializer right, Map<String, String> reads) {
        int start() { return first.getStartPosition(); }
        int end() { return JavaFieldBatchInversionOptimizer.end(second); }
        List<ASTNode> statements() { return List.of(first, second); }
    }

    private JavaFieldBatchInversionOptimizer() { }

    static void optimize(CompilationUnit ast, String source, MathCleanUpOptions options, IProgressMonitor monitor,
            int selectionOffset, int selectionLength, Map<String, String> compiler, SourceEnvironment environment,
            List<Replacement> replacements, List<VerifiedRegion> evidence, List<Diagnostic> diagnostics, Work work) {
        var blocks = new ArrayList<Block>();
        var names = new HashSet<String>();
        try {
            ast.accept(new ASTVisitor() {
                @Override public void preVisit(ASTNode node) { work.charge(1); }
                @Override public boolean visit(Block node) {
                    if (node.statements().size() >= 2) blocks.add(node);
                    return true;
                }
                @Override public boolean visit(SimpleName node) { names.add(node.getIdentifier()); return true; }
            });
            var request = new FieldInversionBatch(2);
            int accepted = 0;
            for (var block : blocks) {
                List<?> siblings = block.statements();
                for (int i = 1; i < siblings.size(); i++) {
                    work.charge(1);
                    if (accepted == MAXIMUM_SITES || work.remaining() < 256) return;
                    if (!(siblings.get(i - 1) instanceof VariableDeclarationStatement first)
                            || !(siblings.get(i) instanceof VariableDeclarationStatement second)) continue;
                    int start = first.getStartPosition(), length = end(second) - start;
                    if (selectionOffset >= 0 && !overlaps(start, length, selectionOffset, Math.max(1, selectionLength))) continue;
                    if (replacements.stream().anyMatch(edit -> overlaps(start, length, edit.offset(), edit.length()))) continue;
                    Site site = match(ast, block, first, second, work);
                    if (site == null || containsComment(ast, start, length, work)) continue;
                    try {
                        var searched = FieldInversionSdk.searchBatch(request, work.allocate(128, options), work.token());
                        if (searched.verified().isEmpty()) continue;
                        var planned = searched.verified().orElseThrow().plan();
                        var checked = FieldInversionSdk.verifyBatch(request, planned, work.allocate(32, options), work.token())
                                .verified().orElse(null);
                        if (checked == null) continue;
                        String replacement = emit(source, site, checked.plan(), names);
                        work.charge(source.length() + (long) replacement.length());
                        String generated = source.substring(0, start) + replacement + source.substring(site.end());
                        var after = MathematicalAnalysis.checkGeneratedSource(ast, source, generated, compiler, options.targetJava(),
                                monitor, environment, start, length, replacement.length());
                        var emitted = envelope(after, generated, start, replacement, work);
                        checkPrefix(after, emitted, site.reads(), work);
                        if (!bindings(ast, site.statements(), true, work).equals(bindings(after,
                                emitted.subList(PREFIX_STATEMENTS, emitted.size()), true, work))) throw invalid();
                        var proof = new FieldBatchInversionEvidence(checked.source(), checked.plan(), searched.witness(),
                                site.reads(), bindings(after, emitted, false, work));
                        String original = source.substring(start, site.end());
                        var sourcePlan = new JavaSourceRewritePlan(environment == null ? "Source.java" : environment.unitName(), //$NON-NLS-1$
                                JavaComputationTask.hash(source), source.length(), start, length, List.of(), List.of(), Map.of(),
                                List.of(new JavaSourceRewritePlan.Change(start, length, JavaComputationTask.hash(original), replacement)));
                        replacements.add(new Replacement(start, length, replacement, proof.description()));
                        evidence.add(new VerifiedRegion(start, length, null, null, original, replacement, null, sourcePlan,
                                null, null, null, null, null, null, null, proof));
                        diagnostics.add(new Diagnostic("VERIFIED_FIELD_BATCH_INVERSIONS", proof.description(), start, length, Stage.PROOF)); //$NON-NLS-1$
                        accepted++;
                    } catch (JavaValueSourceSummary.DeadlineExceeded cancelled) {
                        throw cancelled;
                    } catch (IllegalArgumentException unavailable) {
                        diagnostics.add(new Diagnostic("FIELD_BATCH_INVERSIONS_UNAVAILABLE", unavailable.getMessage(), start, length, Stage.SOURCE)); //$NON-NLS-1$
                    }
                }
            }
        } catch (JavaValueSourceSummary.DeadlineExceeded cancelled) {
            throw cancelled;
        } catch (IllegalArgumentException unavailable) {
            diagnostics.add(new Diagnostic("FIELD_BATCH_INVERSIONS_UNAVAILABLE", unavailable.getMessage(), 0, source.length(), Stage.SOURCE)); //$NON-NLS-1$
        }
    }

    private static Site match(CompilationUnit ast, Block block, VariableDeclarationStatement first,
            VariableDeclarationStatement second, Work work) {
        var owner = owner(block, work);
        if (owner == null) return null;
        int start = first.getStartPosition();
        var left = initializer(ast, owner, start, first, work);
        var right = initializer(ast, owner, start, second, work);
        if (left == null || right == null || !left.modulus.key().equals(right.modulus.key())
                || left.operand.key().equals(right.operand.key())) return null;
        var reads = new LinkedHashMap<>(left.reads);
        right.reads.forEach(reads::putIfAbsent);
        return new Site(first, second, left, right, reads);
    }

    private static PureInitializer initializer(CompilationUnit ast, ITypeBinding owner, int start,
            VariableDeclarationStatement declaration, Work work) {
        if (declaration.fragments().size() != 1) return null;
        var fragment = (VariableDeclarationFragment) declaration.fragments().get(0);
        var variable = fragment.resolveBinding();
        if (!valid(variable) || !big(variable.getType())) return null;
        var result = new PureInitializer();
        if (!readInitializer(ast, owner, start, fragment.getInitializer(), result, 0, work)
                || result.inverse == null || result.moduli.size() != 1) return null;
        return result;
    }

    private static boolean readInitializer(CompilationUnit ast, ITypeBinding owner, int start, Expression expression,
            PureInitializer result, int depth, Work work) {
        work.charge(1);
        if (depth > MAXIMUM_INITIALIZER_DEPTH || expression == null
                || (expression.getFlags() & (ASTNode.MALFORMED | ASTNode.RECOVERED)) != 0) return false;
        if (expression instanceof ParenthesizedExpression parens)
            return readInitializer(ast, owner, start, parens.getExpression(), result, depth + 1, work);
        if (!(expression instanceof MethodInvocation call))
            return addRead(result, stableRead(ast, owner, start, expression, work)) != null;
        var method = call.resolveMethodBinding();
        if (!arithmetic(method) || call.getExpression() == null || !call.typeArguments().isEmpty() || call.arguments().size() != 1) return false;
        var argument = (Expression) call.arguments().get(0);
        if (INVERSE.equals(method.getName())) {
            if (result.inverse != null) return false;
            result.inverse = call;
            result.operand = addRead(result, stableRead(ast, owner, start, call.getExpression(), work));
            result.modulus = addRead(result, stableRead(ast, owner, start, argument, work));
            if (result.operand == null || result.modulus == null) return false;
            result.moduli.add(result.modulus.key());
            return true;
        }
        if (!readInitializer(ast, owner, start, call.getExpression(), result, depth + 1, work)) return false;
        if (MOD.equals(method.getName())) {
            Read modulus = addRead(result, stableRead(ast, owner, start, argument, work));
            if (modulus == null) return false;
            result.moduli.add(modulus.key());
            return true;
        }
        return readInitializer(ast, owner, start, argument, result, depth + 1, work);
    }

    private static Read addRead(PureInitializer initializer, Read read) {
        if (read == null) return null;
        String previous = initializer.reads.putIfAbsent(read.source(), read.key());
        return previous == null || previous.equals(read.key()) ? read : null;
    }

    private static Read stableRead(CompilationUnit ast, ITypeBinding owner, int start, Expression expression, Work work) {
        while (expression instanceof ParenthesizedExpression parens) {
            work.charge(1);
            expression = parens.getExpression();
        }
        IBinding binding;
        String spelling;
        if (expression instanceof SimpleName name) {
            binding = name.resolveBinding();
            spelling = name.getIdentifier();
        } else if (expression instanceof FieldAccess field && field.getExpression() instanceof ThisExpression self
                && self.getQualifier() == null) {
            binding = field.resolveFieldBinding();
            spelling = "this." + field.getName().getIdentifier(); //$NON-NLS-1$
        } else return null;
        if (!(binding instanceof IVariableBinding read) || !valid(read) || !big(read.getType())) return null;
        var variable = read.getVariableDeclaration();
        ASTNode declaration = ast.findDeclaringNode(variable);
        if (!(declaration instanceof VariableDeclaration)) return null;
        if (variable.isField()) {
            // Own fields cannot run a different class's initialization or dereference another object.
            if (!Modifier.isFinal(variable.getModifiers()) || owner == null || !owner.isEqualTo(variable.getDeclaringClass())) return null;
        } else if (declaration.getStartPosition() >= start
                || !(Modifier.isFinal(variable.getModifiers()) || variable.isEffectivelyFinal())) return null;
        // In particular, a second initializer cannot consume the first declaration.
        return new Read(spelling, variable.getKey());
    }

    private static ITypeBinding owner(ASTNode node, Work work) {
        for (ASTNode parent = node; parent != null; parent = parent.getParent()) {
            work.charge(1);
            if (parent instanceof MethodDeclaration method) {
                var binding = method.resolveBinding();
                return method.isConstructor() || !valid(binding) ? null : binding.getDeclaringClass();
            }
            if (parent instanceof Initializer || parent instanceof AbstractTypeDeclaration
                    || parent instanceof AnonymousClassDeclaration) break;
        }
        return null;
    }

    private static String emit(String source, Site site, FieldBatchInversionPlan plan, Set<String> names) {
        String modulus = site.left().modulus.source();
        List<String> slots = new ArrayList<>(List.of(site.left().operand.source(), site.right().operand.source()));
        String left = fresh(names), right = fresh(names);
        String base = indentation(source, site.start());
        String unit = base.indexOf('\t') >= 0 ? "\t" : "    "; //$NON-NLS-1$ //$NON-NLS-2$
        String nl = delimiter(source);
        var lines = new ArrayList<String>();
        lines.add(BIG + " " + left + " = null;"); //$NON-NLS-1$ //$NON-NLS-2$
        lines.add(BIG + " " + right + " = null;"); //$NON-NLS-1$ //$NON-NLS-2$
        var conditions = new ArrayList<String>();
        var guarded = new HashSet<String>();
        // Every original leaf must pass before the inversion can move ahead of either initializer.
        site.reads().forEach((read, key) -> {
            if (guarded.add(key)) {
                conditions.add(read + " != null && " + read + ".getClass() == " + BIG + ".class"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                conditions.add(read + ".bitLength() <= " + MAXIMUM_INPUT_BITS); //$NON-NLS-1$
            }
        });
        conditions.add(modulus + ".signum() > 0 && " + modulus + ".bitLength() >= " + MINIMUM_MODULUS_BITS); //$NON-NLS-1$ //$NON-NLS-2$
        lines.add("if (" + String.join(nl + base + unit + "&& ", conditions) + ") {"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        String indent = unit;
        for (var step : plan.steps()) {
            String next = fresh(names);
            switch (step) {
                case Multiply m -> lines.add(indent + "final " + BIG + " " + next + " = " + slots.get(m.left()) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        + ".multiply(" + slots.get(m.right()) + ").mod(" + modulus + ");"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                case Invert i -> {
                    lines.add(indent + BIG + " " + next + " = null;"); //$NON-NLS-1$ //$NON-NLS-2$
                    lines.add(indent + "try {"); //$NON-NLS-1$
                    lines.add(indent + unit + next + " = " + slots.get(i.operand()) + ".modInverse(" + modulus + ");"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    lines.add(indent + "} catch (" + ARITHMETIC_EXCEPTION + " " + fresh(names) + ") {"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    lines.add(indent + unit + "// The original calls report failure in source order."); //$NON-NLS-1$
                    lines.add(indent + "}"); //$NON-NLS-1$
                    lines.add(indent + "if (" + next + " != null) {"); //$NON-NLS-1$ //$NON-NLS-2$
                    indent += unit;
                }
            }
            slots.add(next);
        }
        lines.add(indent + left + " = " + slots.get(plan.outputs().get(0)) + ";"); //$NON-NLS-1$ //$NON-NLS-2$
        lines.add(indent + right + " = " + slots.get(plan.outputs().get(1)) + ";"); //$NON-NLS-1$ //$NON-NLS-2$
        lines.add(unit + "}"); //$NON-NLS-1$
        lines.add("}"); //$NON-NLS-1$
        MethodInvocation first = site.left().inverse, second = site.right().inverse;
        return String.join(nl + base, lines) + nl + base + source.substring(site.start(), first.getStartPosition())
                + fallback(source, first, left) + source.substring(end(first), second.getStartPosition())
                + fallback(source, second, right) + source.substring(end(second), site.end());
    }

    private static String fallback(String source, MethodInvocation inverse, String result) {
        return "(" + result + " == null ? " + source.substring(inverse.getStartPosition(), end(inverse)) //$NON-NLS-1$ //$NON-NLS-2$
                + " : " + result + ")"; //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static String fresh(Set<String> names) {
        for (int i = names.size(); ; i++) {
            String name = "_mathBatch" + i; //$NON-NLS-1$
            if (names.add(name)) return name;
        }
    }

    static void verifyCombined(CompilationUnit ast, String generated, VerifiedRegion region, List<Replacement> edits, Work work) {
        int start = region.offset();
        for (var edit : edits) {
            work.charge(1);
            if (edit.offset() < region.offset() && edit.offset() + edit.length() <= region.offset())
                start += edit.replacement().length() - edit.length();
        }
        var proof = region.fieldBatchInversion();
        var emitted = envelope(ast, generated, start, region.replacement(), work);
        checkPrefix(ast, emitted, proof.inputs(), work);
        if (!proof.bindings().equals(bindings(ast, emitted, false, work))) throw invalid();
    }

    private static List<ASTNode> envelope(CompilationUnit ast, String source, int start, String replacement, Work work) {
        work.charge(replacement.length());
        if (!source.regionMatches(start, replacement, 0, replacement.length())) throw invalid();
        ASTNode node = NodeFinder.perform(ast, start, 1);
        while (node != null && !(node instanceof VariableDeclarationStatement)) {
            work.charge(1);
            node = node.getParent();
        }
        if (node == null || node.getStartPosition() != start || !(node.getParent() instanceof Block block)) throw invalid();
        var statements = new ArrayList<ASTNode>();
        int stop = start + replacement.length();
        for (Object entry : block.statements()) {
            work.charge(1);
            var statement = (ASTNode) entry;
            if (statement.getStartPosition() >= start && end(statement) <= stop) statements.add(statement);
        }
        if (statements.size() != PREFIX_STATEMENTS + 2 || end(statements.getLast()) != stop
                || !(statements.get(2) instanceof IfStatement)
                || !(statements.get(3) instanceof VariableDeclarationStatement)
                || !(statements.get(4) instanceof VariableDeclarationStatement)) throw invalid();
        return statements;
    }

    private static void checkPrefix(CompilationUnit ast, List<ASTNode> statements, Map<String, String> inputs, Work work) {
        int start = statements.getFirst().getStartPosition(), stop = end(statements.get(2));
        var owner = owner(statements.getFirst(), work);
        var readKeys = new HashSet<String>();
        for (int index = 0; index < PREFIX_STATEMENTS; index++) {
            statements.get(index).accept(new ASTVisitor() {
                @Override public void preVisit(ASTNode node) { work.charge(1); }
                @Override public boolean visit(SimpleName node) {
                    if (node.isDeclaration() || !(node.resolveBinding() instanceof IVariableBinding variable)) return true;
                    ASTNode declaration = ast.findDeclaringNode(variable.getVariableDeclaration());
                    if (declaration != null && declaration.getStartPosition() >= start && end(declaration) <= stop) return true;
                    var expression = node.getParent() instanceof FieldAccess field && field.getName() == node ? field : node;
                    var read = stableRead(ast, owner, start, expression, work);
                    if (read == null || !read.key().equals(inputs.get(read.source()))) throw invalid();
                    readKeys.add(read.key());
                    return true;
                }
            });
        }
        if (!readKeys.equals(new HashSet<>(inputs.values()))) throw invalid();
    }

    private static List<String> bindings(CompilationUnit ast, List<ASTNode> statements, boolean originalOnly, Work work) {
        int start = statements.getFirst().getStartPosition(), stop = end(statements.getLast());
        var result = new ArrayList<String>();
        var visitor = new ASTVisitor() {
            @Override public void preVisit(ASTNode node) { work.charge(1); }
            @Override public boolean visit(SimpleName node) {
                var binding = node.resolveBinding();
                if (binding instanceof IVariableBinding variable) {
                    ASTNode declaration = ast.findDeclaringNode(variable.getVariableDeclaration());
                    if (!valid(binding)) throw invalid();
                    result.add(declaration != null && declaration.getStartPosition() >= start && end(declaration) <= stop
                            ? "local:" + variable.getName() : variable.getVariableDeclaration().getKey()); //$NON-NLS-1$
                } else if (binding instanceof ITypeBinding) {
                    if (!valid(binding)) throw invalid();
                    result.add(binding.getKey());
                }
                return true;
            }
            @Override public boolean visit(MethodInvocation node) {
                var method = node.resolveMethodBinding();
                if (!arithmetic(method) && (originalOnly || !guardMethod(method))) throw invalid();
                result.add(method.getMethodDeclaration().getKey());
                return true;
            }
            @Override public boolean visit(TypeLiteral node) {
                if (!big(node.getType().resolveBinding())) throw invalid();
                return true;
            }
            @Override public boolean visit(CatchClause node) {
                if (!jdk(node.getException().getType().resolveBinding(), ARITHMETIC_EXCEPTION)) throw invalid();
                return true;
            }
            @Override public boolean visit(ConditionalExpression node) {
                if (!originalOnly) return true;
                // In the two unchanged initializers, replay the original inverse branches.
                if (!(node.getThenExpression() instanceof MethodInvocation original)
                        || !INVERSE.equals(original.getName().getIdentifier())) throw invalid();
                original.accept(this);
                return false;
            }
        };
        statements.forEach(statement -> statement.accept(visitor));
        return result;
    }

    private static boolean arithmetic(IMethodBinding method) {
        if (!valid(method) || Modifier.isStatic(method.getModifiers()) || !big(method.getDeclaringClass())
                || method.getParameterTypes().length != 1 || !big(method.getParameterTypes()[0]) || !big(method.getReturnType()))
            return false;
        return MULTIPLY.equals(method.getName()) || MOD.equals(method.getName()) || INVERSE.equals(method.getName());
    }

    private static boolean guardMethod(IMethodBinding method) {
        if (!valid(method) || Modifier.isStatic(method.getModifiers()) || method.getParameterTypes().length != 0) return false;
        if (jdk(method.getDeclaringClass(), OBJECT))
            return Modifier.isFinal(method.getModifiers()) && "getClass".equals(method.getName()); //$NON-NLS-1$
        return big(method.getDeclaringClass()) && ("bitLength".equals(method.getName()) || "signum".equals(method.getName())); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static boolean valid(IBinding binding) {
        return binding != null && !binding.isRecovered() && binding.getKey() != null;
    }

    private static boolean big(ITypeBinding type) { return jdk(type, BIG); }

    private static boolean jdk(ITypeBinding type, String name) {
        return valid(type) && !type.isFromSource() && name.equals(type.getQualifiedName())
                && type.getModule() != null && "java.base".equals(type.getModule().getName()); //$NON-NLS-1$
    }

    private static boolean containsComment(CompilationUnit ast, int start, int length, Work work) {
        if (ast.getCommentList() == null) return true;
        for (Object entry : ast.getCommentList()) {
            work.charge(1);
            var comment = (Comment) entry;
            if (overlaps(start, length, comment.getStartPosition(), comment.getLength())) return true;
        }
        return false;
    }

    private static String indentation(String source, int offset) {
        int start = Math.max(source.lastIndexOf('\n', offset - 1), source.lastIndexOf('\r', offset - 1)) + 1;
        int i = start;
        while (i < offset && (source.charAt(i) == ' ' || source.charAt(i) == '\t')) i++;
        return source.substring(start, i);
    }

    private static String delimiter(String source) {
        int line = source.indexOf('\n');
        return line > 0 && source.charAt(line - 1) == '\r' ? "\r\n" : line < 0 && source.indexOf('\r') >= 0 ? "\r" : "\n"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    private static boolean overlaps(int a, int length, int b, int otherLength) {
        return a < (long) b + otherLength && b < (long) a + length;
    }

    private static int end(ASTNode node) { return node.getStartPosition() + node.getLength(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("FIELD_BATCH_INVERSION_EMISSION_DIFFERS"); } //$NON-NLS-1$
}
