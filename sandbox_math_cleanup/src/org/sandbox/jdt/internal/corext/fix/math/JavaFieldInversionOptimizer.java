/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.sandbox.jdt.internal.corext.fix.math.JavaByteFieldContract.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.dom.*;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.Diagnostic;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.Replacement;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.SourceEnvironment;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.Stage;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.VerifiedRegion;
import de.regelsuche.ast.iteration.FieldInversionReduction;
import de.regelsuche.ast.iteration.FieldInversionReduction.Factor;
import de.regelsuche.ast.iteration.FieldInversionReduction.FixedPower;
import de.regelsuche.ast.iteration.FieldInversionReduction.Kind;
import de.regelsuche.math.algorithms.iteration.FieldInversionPlan;
import de.regelsuche.sdk.optimization.iteration.FieldInversionSdk;

/** Source and effect checks around the SDK's checked byte-field schedules. */
final class JavaFieldInversionOptimizer {
    private static final String DIVIDE = """
            for (int index = 0; index < bytes.length; index++) {
                bytes[index] = receiver.divide(bytes[index] & 255, divisor);
            }
            """;
    private static final String PRODUCT = """
            int cursor = 0;
            for (int index = 0; index < bound; index++) {
                if (index != outer) {
                    scratch[cursor++] = receiver.divide(shares[index].coordinate, shares[outer].coordinate ^ shares[index].coordinate);
                }
            }
            accumulator = 1;
            for (int reduce = 0; reduce != scratch.length; reduce++) {
                accumulator = receiver.multiply(accumulator & 255, scratch[reduce] & 255);
            }
            """;
    private static final String OUTER = "for (int outer = 0; outer < bound; outer++) {}"; //$NON-NLS-1$
    private static final String SCRATCH = "byte[] scratch = new byte[bound - 1];"; //$NON-NLS-1$
    private static final String FALLBACK = JavaComputationExtractor.ORIGINAL_FALLBACK_MARKER;
    private static final int MINIMUM_ROWS = 2;

    private record Site(Kind kind, List<ASTNode> statements, ExpressionStatement store, MethodInvocation divide, Match bindings) {
        int start() { return statements.getFirst().getStartPosition(); }
        int end() { return JavaFieldInversionOptimizer.end(statements.getLast()); }
        String name(String key) { return bindings.get(key).getName(); }
    }

    private JavaFieldInversionOptimizer() { }

    static void optimize(CompilationUnit ast, String source, MathCleanUpOptions options, IProgressMonitor monitor,
            int selectionOffset, int selectionLength, Map<String, String> compiler, SourceEnvironment environment,
            List<Replacement> replacements, List<VerifiedRegion> evidence, List<Diagnostic> diagnostics,
            JavaValueSourceSummary.Work work) {
        var loops = new ArrayList<ForStatement>();
        var names = new HashSet<String>();
        JavaSourceContext.valueWork(ast, work);
        try {
            ast.accept(new ASTVisitor() {
                @Override public void preVisit(ASTNode node) { work.charge(1); }
                @Override public boolean visit(ForStatement node) { loops.add(node); return true; }
                @Override public boolean visit(SimpleName name) { names.add(name.getIdentifier()); return true; }
            });
            if (loops.isEmpty()) return;
            var contracts = new JavaByteFieldContract();
            ASTNode dividePattern = (ASTNode) pattern(DIVIDE).statements().getFirst();
            List<?> productPattern = pattern(PRODUCT).statements();
            ForStatement outerPattern = (ForStatement) pattern(OUTER).statements().getFirst();
            ASTNode scratchPattern = (ASTNode) pattern(SCRATCH).statements().getFirst();
            int accepted = 0;
            for (var loop : loops) {
                work.charge(1);
                if (accepted == 8 || work.remaining() < 16) break;
                if (!(loop.getParent() instanceof Block block) || fallback(block, source, work)) continue;
                Site site = divide(loop, dividePattern, work);
                if (site == null) site = product(ast, loop, block, productPattern, outerPattern, scratchPattern, work);
                if (site == null) continue;
                int start = site.start(), length = site.end() - start;
                if (selectionOffset >= 0 && !overlaps(start, length, selectionOffset, Math.max(1, selectionLength))) continue;
                if (replacements.stream().anyMatch(edit -> overlaps(start, length, edit.offset(), edit.length()))
                        || containsComment(ast, start, length, work)) continue;
                try {
                    var receiver = (IVariableBinding) site.bindings().get("receiver"); //$NON-NLS-1$
                    var contract = contracts.resolve(ast, receiver, site.divide().resolveMethodBinding(), work);
                    if (contract == null || site.kind() == Kind.PRODUCT
                            && !same(contract.multiply(), site.bindings().get("multiply"))) continue; //$NON-NLS-1$
                    var request = new FieldInversionReduction(site.kind(), new FixedPower(254),
                            List.of(new Factor(0, false, false), new Factor(1, site.kind() == Kind.PER_ELEMENT, true)));
                    var searched = FieldInversionSdk.search(request, work.allocate(Math.min(2048, work.remaining() / 8), options), work.token());
                    var checked = searched.verified().orElse(null);
                    if (checked == null || !checked.source().equals(request) || !canEmit(site.kind(), checked.plan())) continue;
                    String replacement = emit(source, site, contract, names);
                    work.charge(source.length() + (long) replacement.length());
                    String generated = source.substring(0, start) + replacement + source.substring(site.end());
                    var after = MathematicalAnalysis.checkGeneratedSource(ast, source, generated, compiler, options.targetJava(),
                            monitor, environment, start, length, replacement.length());
                    var definitions = new HashMap<String, String>();
                    contract.localDefinitions().forEach((key, node) -> definitions.put(key, JavaComputationTask.hash(node.toString())));
                    var proof = new FieldInversionEvidence(checked.source(), checked.plan(), checked.cost(MINIMUM_ROWS), searched.witness(),
                            receiver.getKey(), contract.owner().getKey(), contract.divide().getKey(), contract.multiply().getKey(),
                            contract.power().getKey(), JavaComputationTask.hash(site.statements().toString()), definitions);
                    requireGenerated(after, generated, start, replacement, proof, work);
                    String original = source.substring(start, site.end());
                    var sourcePlan = new JavaSourceRewritePlan("Source.java", JavaComputationTask.hash(source), source.length(), //$NON-NLS-1$
                            start, length, List.of(), List.of(), Map.of(),
                            List.of(new JavaSourceRewritePlan.Change(start, length, JavaComputationTask.hash(original), replacement)));
                    replacements.add(new Replacement(start, length, replacement, proof.description()));
                    evidence.add(new VerifiedRegion(start, length, null, null, original, replacement, null, sourcePlan,
                            null, null, null, null, null, null, proof));
                    diagnostics.add(new Diagnostic("VERIFIED_FIELD_INVERSIONS", proof.description(), start, length, Stage.PROOF)); //$NON-NLS-1$
                    accepted++;
                } catch (JavaValueSourceSummary.DeadlineExceeded cancelled) {
                    throw cancelled;
                } catch (IllegalArgumentException unavailable) {
                    diagnostics.add(new Diagnostic("FIELD_INVERSIONS_UNAVAILABLE", unavailable.getMessage(), start, length, Stage.SOURCE)); //$NON-NLS-1$
                }
            }
        } catch (JavaValueSourceSummary.DeadlineExceeded cancelled) {
            throw cancelled;
        } catch (IllegalArgumentException unavailable) {
            diagnostics.add(new Diagnostic("FIELD_INVERSIONS_UNAVAILABLE", unavailable.getMessage(), 0, source.length(), Stage.SOURCE)); //$NON-NLS-1$
        } finally {
            JavaSourceContext.valueWork(ast, null);
        }
    }

    private static Site divide(ForStatement loop, ASTNode template, JavaValueSourceSummary.Work work) {
        var match = new Match(work);
        if (!loop.subtreeMatch(match, template) || !(match.get("bytes") instanceof IVariableBinding bytes) //$NON-NLS-1$
                || bytes.isField() || !byteArray(bytes) || !(match.get("divisor") instanceof IVariableBinding divisor) //$NON-NLS-1$
                || !divisor.isParameter() || !primitive(divisor.getType(), "int") //$NON-NLS-1$
                || !(match.get("receiver") instanceof IVariableBinding)) return null; //$NON-NLS-1$
        var store = (ExpressionStatement) ((Block) loop.getBody()).statements().getFirst();
        return new Site(Kind.PER_ELEMENT, List.of(loop), store, (MethodInvocation) ((Assignment) store.getExpression()).getRightHandSide(), match);
    }

    private static Site product(CompilationUnit ast, ForStatement loop, Block block, List<?> template, ForStatement outerPattern,
            ASTNode scratchPattern, JavaValueSourceSummary.Work work) {
        if (!(block.getParent() instanceof ForStatement outer) || outer.getBody() != block) return null;
        List<?> siblings = block.statements();
        work.charge(siblings.size());
        int position = siblings.indexOf(loop);
        if (position < 1 || position + 2 >= siblings.size()) return null;
        var match = new Match(work);
        for (int i = 0; i < template.size(); i++)
            if (!((ASTNode) siblings.get(position + i - 1)).subtreeMatch(match, template.get(i))) return null;
        if (!outerHeader(outer, outerPattern, match)
                || !(match.get("outer") instanceof IVariableBinding index) || writes(block, index, work) //$NON-NLS-1$
                || !(match.get("bound") instanceof IVariableBinding bound) || bound.isField() //$NON-NLS-1$
                || !primitive(bound.getType(), "int") || !(Modifier.isFinal(bound.getModifiers()) || bound.isEffectivelyFinal()) //$NON-NLS-1$
                || !(match.get("scratch") instanceof IVariableBinding scratch) || scratch.isField() || !byteArray(scratch) //$NON-NLS-1$
                || !(match.get("cursor") instanceof IVariableBinding cursor) || cursor.isField() //$NON-NLS-1$
                || !(match.get("accumulator") instanceof IVariableBinding accumulator) || accumulator.isField() //$NON-NLS-1$
                || !primitive(accumulator.getType(), "byte") || !(match.get("receiver") instanceof IVariableBinding) //$NON-NLS-1$ //$NON-NLS-2$
                || !(match.get("coordinate") instanceof IVariableBinding coordinate) || !coordinate.isField() //$NON-NLS-1$
                || Modifier.isStatic(coordinate.getModifiers()) || !primitive(coordinate.getType(), "int")) return null; //$NON-NLS-1$
        if (!(ast.findDeclaringNode(scratch.getKey()) instanceof VariableDeclarationFragment declaration)
                || !declaration.getParent().subtreeMatch(match, scratchPattern)) return null;
        ASTNode reduce = (ASTNode) siblings.get(position + 2);
        ASTNode scope = executableScope(block, work);
        if (scope == null || scope != executableScope(declaration, work)
                || hasOutsideUse(scope, scratch, loop.getStartPosition(), end(reduce), work)
                || hasOutsideUse(scope, cursor, loop.getStartPosition(), end(reduce), work)) return null;
        var condition = (IfStatement) ((Block) loop.getBody()).statements().getFirst();
        var store = (ExpressionStatement) ((Block) condition.getThenStatement()).statements().getFirst();
        return new Site(Kind.PRODUCT, List.of(loop, (ASTNode) siblings.get(position + 1), reduce), store,
                (MethodInvocation) ((Assignment) store.getExpression()).getRightHandSide(), match);
    }

    private static boolean outerHeader(ForStatement loop, ForStatement template, Match match) {
        return loop.initializers().size() == 1 && loop.updaters().size() == 1 && loop.getExpression() != null
                && ((ASTNode) loop.initializers().getFirst()).subtreeMatch(match, template.initializers().getFirst())
                && loop.getExpression().subtreeMatch(match, template.getExpression())
                && ((ASTNode) loop.updaters().getFirst()).subtreeMatch(match, template.updaters().getFirst());
    }

    private static ASTNode executableScope(ASTNode node, JavaValueSourceSummary.Work work) {
        while (node != null && !(node instanceof MethodDeclaration || node instanceof LambdaExpression || node instanceof Initializer)) {
            work.charge(1);
            node = node.getParent();
        }
        return node;
    }

    private static boolean writes(Block body, IVariableBinding index, JavaValueSourceSummary.Work work) {
        boolean[] written = {false};
        body.accept(new ASTVisitor() {
            @Override public void preVisit(ASTNode node) { work.charge(1); }
            @Override public boolean visit(Assignment node) { written[0] |= same(index, variable(node.getLeftHandSide())); return true; }
            @Override public boolean visit(PostfixExpression node) { written[0] |= same(index, variable(node.getOperand())); return true; }
            @Override public boolean visit(PrefixExpression node) {
                if (node.getOperator() == PrefixExpression.Operator.INCREMENT || node.getOperator() == PrefixExpression.Operator.DECREMENT)
                    written[0] |= same(index, variable(node.getOperand()));
                return true;
            }
        });
        return written[0];
    }

    private static boolean hasOutsideUse(ASTNode scope, IVariableBinding binding, int start, int stop, JavaValueSourceSummary.Work work) {
        boolean[] outside = {false};
        scope.accept(new ASTVisitor() {
            @Override public void preVisit(ASTNode node) { work.charge(1); }
            @Override public boolean visit(SimpleName name) {
                if (!name.isDeclaration() && (name.getStartPosition() < start || end(name) > stop))
                    outside[0] |= same(binding, name.resolveBinding());
                return true;
            }
        });
        return outside[0];
    }

    private static boolean byteArray(IVariableBinding variable) {
        return variable.getType().isArray() && variable.getType().getDimensions() == 1
                && primitive(variable.getType().getElementType(), "byte"); //$NON-NLS-1$
    }

    private static boolean canEmit(Kind kind, FieldInversionPlan plan) {
        return plan.kind() == kind && plan.requiresNonempty() && plan.ordinaryFactors().equals(List.of(0))
                && plan.groupedInverses().equals(List.of(1)) && plan.rowInverses().isEmpty();
    }

    private static String emit(String source, Site site, Contract contract, Set<String> names) {
        String original = source.substring(site.start(), site.end());
        String base = indent(source, site.start()), unit = base.contains("\t") ? "\t" : "    ", nl = delimiter(source); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        String receiver = site.name("receiver"), multiply = receiver + "." + contract.multiply().getName(); //$NON-NLS-1$ //$NON-NLS-2$
        String power = receiver + "." + contract.power().getName(); //$NON-NLS-1$
        String guard = receiver + " != null && " + receiver + ".getClass() == " + contract.owner().getQualifiedName() + ".class"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        String fast;
        String x = text(source, (ASTNode) site.divide().arguments().get(0));
        String y = text(source, (ASTNode) site.divide().arguments().get(1));
        if (site.kind() == Kind.PER_ELEMENT) {
            String bytes = site.name("bytes"); //$NON-NLS-1$
            guard = bytes + " != null && " + bytes + ".length >= " + MINIMUM_ROWS + " && " + guard; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            String inverse = fresh(names, "_mathFieldInverse"); //$NON-NLS-1$
            String call = multiply + "(" + x + ", " + inverse + ")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            fast = "final int " + inverse + " = " + power + "(" + y + ", (byte) 254) & 0xff;" + nl + base //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    + replace(source, site.statements().getFirst(), site.divide(), call);
        } else {
            guard = site.name("scratch") + ".length >= " + MINIMUM_ROWS + " && " + guard; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            String numerator = fresh(names, "_mathFieldNumerator"), denominator = fresh(names, "_mathFieldDenominator"); //$NON-NLS-1$ //$NON-NLS-2$
            String left = fresh(names, "_mathFieldLeft"), right = fresh(names, "_mathFieldRight"); //$NON-NLS-1$ //$NON-NLS-2$
            String entry = String.join(nl + indent(source, site.store().getStartPosition()),
                    "final int " + left + " = " + x + ";", "final int " + right + " = " + y + ";", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                    accumulate(numerator, multiply, left), accumulate(denominator, multiply, right));
            fast = String.join(nl + base, "int " + numerator + " = 1;", "int " + denominator + " = 1;", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    replace(source, site.statements().getFirst(), site.store(), entry),
                    site.name("accumulator") + " = " + multiply + "(" + numerator + ", " + power + "(" + denominator //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                            + ", (byte) 254) & 0xff);"); //$NON-NLS-1$
        }
        return "if (" + guard + ") {" + nl + shifted(fast, base, unit, nl) + nl + base + "} else {" + nl //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                + base + unit + "// " + FALLBACK + nl + shifted(original, base, unit, nl) + nl + base + "}"; //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static String accumulate(String target, String multiply, String operand) {
        return target + " = " + multiply + "(" + target + ", " + operand + " & 0xff) & 0xff;"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    }

    private static String replace(String source, ASTNode outer, ASTNode inner, String replacement) {
        return source.substring(outer.getStartPosition(), inner.getStartPosition()) + replacement + source.substring(end(inner), end(outer));
    }

    private static String fresh(Set<String> names, String prefix) {
        for (int i = 0; ; i++) {
            String name = prefix + i;
            if (names.add(name)) return name;
        }
    }

    private static String shifted(String text, String base, String unit, String nl) {
        return base + unit + text.replace("\r\n", "\n").replace('\r', '\n').replace("\n", nl + unit); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    private static String delimiter(String source) {
        int line = source.indexOf('\n');
        return line > 0 && source.charAt(line - 1) == '\r' ? "\r\n" : line < 0 && source.indexOf('\r') >= 0 ? "\r" : "\n"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    private static String indent(String source, int offset) {
        int start = Math.max(source.lastIndexOf('\n', offset - 1), source.lastIndexOf('\r', offset - 1)) + 1;
        int i = start;
        while (i < offset && (source.charAt(i) == ' ' || source.charAt(i) == '\t')) i++;
        return source.substring(start, i);
    }

    private static boolean containsComment(CompilationUnit ast, int start, int length, JavaValueSourceSummary.Work work) {
        if (ast.getCommentList() == null) return true;
        for (Object item : ast.getCommentList()) {
            work.charge(1);
            Comment comment = (Comment) item;
            if (overlaps(start, length, comment.getStartPosition(), comment.getLength())) return true;
        }
        return false;
    }

    private static boolean fallback(Block block, String source, JavaValueSourceSummary.Work work) {
        if (block.statements().isEmpty()) return false;
        int start = block.getStartPosition(), stop = ((ASTNode) block.statements().getFirst()).getStartPosition();
        work.charge(stop - (long) start);
        return source.substring(start, stop).contains(FALLBACK);
    }

    static void verifyCombined(CompilationUnit ast, String generated, VerifiedRegion region, List<Replacement> edits,
            JavaValueSourceSummary.Work work) {
        int start = region.offset();
        for (var edit : edits) {
            work.charge(1);
            if (edit.offset() < region.offset() && edit.offset() + edit.length() <= region.offset())
                start += edit.replacement().length() - edit.length();
        }
        requireGenerated(ast, generated, start, region.replacement(), region.fieldInversion(), work);
    }

    private static void requireGenerated(CompilationUnit ast, String source, int start, String replacement,
            FieldInversionEvidence proof, JavaValueSourceSummary.Work work) {
        work.charge(replacement.length());
        if (!source.regionMatches(start, replacement, 0, replacement.length())
                || !(NodeFinder.perform(ast, start, replacement.length()) instanceof IfStatement guard)
                || guard.getStartPosition() != start || guard.getLength() != replacement.length()
                || !(guard.getThenStatement() instanceof Block fast) || !(guard.getElseStatement() instanceof Block slow)
                || !fallback(slow, source, work)
                || !proof.originalStatementsHash().equals(JavaComputationTask.hash(slow.statements().toString()))) throw invalid();
        var condition = new ArrayList<String>();
        guard.getExpression().accept(new ASTVisitor() {
            @Override public boolean visit(TypeLiteral node) {
                var type = node.getType().resolveBinding();
                if (!valid(type) || !type.getKey().equals(proof.fieldTypeBinding())) throw invalid();
                condition.add(type.getKey());
                return true;
            }
            @Override public boolean visit(MethodInvocation node) {
                var method = node.resolveMethodBinding();
                if (!valid(method) || !"getClass".equals(method.getName()) || !Modifier.isFinal(method.getModifiers()) //$NON-NLS-1$
                        || !"java.lang.Object".equals(method.getDeclaringClass().getQualifiedName())) throw invalid(); //$NON-NLS-1$
                requireReceiver(node, proof);
                condition.add(method.getKey());
                return true;
            }
        });
        boolean product = proof.plan().kind() == Kind.PRODUCT;
        String multiply = proof.multiplicationBinding(), power = proof.powerBinding(), divide = proof.divisionBinding();
        if (condition.size() != 2 || !calls(fast, proof).equals(product ? List.of(multiply, multiply, multiply, power) : List.of(power, multiply))
                || !calls(slow, proof).equals(product ? List.of(divide, multiply) : List.of(divide))) throw invalid();
        proof.localDefinitionHashes().forEach((key, hash) -> {
            ASTNode declaration = ast.findDeclaringNode(key);
            if (declaration == null || !hash.equals(JavaComputationTask.hash(declaration.toString()))) throw invalid();
        });
    }

    private static List<String> calls(Block block, FieldInversionEvidence proof) {
        var methods = new ArrayList<String>();
        block.accept(new ASTVisitor() {
            @Override public boolean visit(MethodInvocation node) {
                requireReceiver(node, proof);
                if (!valid(node.resolveMethodBinding())) throw invalid();
                methods.add(node.resolveMethodBinding().getMethodDeclaration().getKey());
                return true;
            }
        });
        return methods;
    }

    private static void requireReceiver(MethodInvocation call, FieldInversionEvidence proof) {
        var variable = variable(call.getExpression());
        if (!stableReceiver(variable) || !proof.receiverBinding().equals(variable.getKey()) || !valid(variable.getType())
                || !proof.fieldTypeBinding().equals(variable.getType().getKey())) throw invalid();
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("FIELD_INVERSION_EMISSION_DIFFERS"); } //$NON-NLS-1$
    private static String text(String source, ASTNode node) { return source.substring(node.getStartPosition(), end(node)); }
    private static int end(ASTNode node) { return node.getStartPosition() + node.getLength(); }
    private static boolean overlaps(int a, int length, int b, int otherLength) {
        return a < (long) b + otherLength && b < (long) a + length;
    }
}
