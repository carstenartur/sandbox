/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.sandbox.jdt.internal.corext.fix.math.JavaGuardedFieldSource.*;
import static org.sandbox.jdt.internal.corext.fix.math.JavaFieldCallSite.text;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.dom.*;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.*;
import de.regelsuche.math.algorithms.modular.QuadraticRootProgram;

/** Specializes a checked call. The original remains the immediate fallback for every unsupported input. */
final class JavaFieldCallOptimizer {
    record Context(JavaFieldCallSite call, JavaFieldGuardFacts facts) {}
    private JavaFieldCallOptimizer() {}

    static boolean propose(CompilationUnit ast, String source, MethodDeclaration root, int selection, int length,
            MathCleanUpOptions options, Map<String,String> compiler, SourceEnvironment env, IProgressMonitor monitor,
            List<Replacement> edits, List<VerifiedRegion> evidence, List<Diagnostic> diagnostics, JavaValueSourceSummary.Work work) {
        var type = root.resolveBinding().getReturnType();
        if (!Modifier.isStatic(root.getModifiers()) || Modifier.isSynchronized(root.getModifiers()) || type.isPrimitive() || type.isParameterizedType() || type.isTypeVariable()
                || type.getQualifiedName().isEmpty() || selection >= 0 && JavaFieldCallSite.overlaps(root, selection, length)) return false;
        var locations = JavaFieldCallSite.locate(root, selection, length, work);
        if (locations.size() > 8) return false;
        for (var location : locations) {
            if (edits.stream().anyMatch(edit -> JavaFieldCallSite.overlaps(location.caller(), edit.offset(), edit.length()))) continue;
            for (boolean swapped : List.of(false, true)) {
                try (var local = JavaSourceContext.captureLocalSources(ast)) {
                    var site = new JavaGuardedFieldSource(ast, source, root, false, swapped, work);
                    if (!JavaFieldCallSite.nonnullFactory(site)) return false;
                    var call = JavaFieldCallSite.bind(site, location.caller(), location.statement(), location.call());
                    var facts = JavaFieldGuardFacts.inspect(site, call, call.statement());
                    if (JavaGuardedFieldOptimizer.propose(site, local, options, compiler, env, monitor, edits, evidence,
                            diagnostics, new Context(call, facts))) return true;
                } catch (JavaValueSourceSummary.DeadlineExceeded expired) { throw expired; }
                catch (IllegalArgumentException | ArithmeticException rejected) {
                    diagnostics.add(new Diagnostic("FIELD_CALL_UNAVAILABLE", rejected.getMessage(), location.call().getStartPosition(),
                            location.call().getLength(), Stage.SOURCE));
                }
            }
        }
        return false;
    }

    static void emit(JavaGuardedFieldSource site, Context context, FieldRootEvidence base, String prefix,
            MathCleanUpOptions options, Map<String,String> compiler, SourceEnvironment env, IProgressMonitor monitor,
            List<Replacement> edits, List<VerifiedRegion> evidence, List<Diagnostic> diagnostics) {
        var call = context.call();
        var facts = context.facts();
        String helper = prefix + "Candidate", temp = prefix + "Result";
        String type = site.method.resolveBinding().getReturnType().getQualifiedName();
        String witness = facts.witnessKey().isEmpty() ? "" : prefix + "Norm";
        String indent = "        ";
        var lowering = new JavaFieldProgramLowering(site, prefix, indent, facts, witness, site.inputs, site.modulus.key());
        String parameters = site.inputs.stream().map(input -> BIG + " " + input.spelling()).collect(Collectors.joining(", "));
        String arguments = call.arguments().stream().map(SimpleName::getIdentifier).collect(Collectors.joining(", "));
        if (!witness.isEmpty()) { parameters += ", boolean " + witness; arguments += ", " + facts.witnessName(); }
        String member = "\n\n    private static " + type + " " + helper + "(" + parameters + ") {\n"
                + indent + BIG + " " + site.modulus.spelling() + " = " + site.modulusField.spelling() + ";\n"
                + indent + lowering.emit(base.certificate().program()) + "return null;\n    }";
        int start = call.caller().getStartPosition(), end = start + call.caller().getLength();
        int stmt = call.statement().getStartPosition();
        String pad = site.source.substring(site.source.lastIndexOf('\n', stmt - 1) + 1, stmt);
        if (!pad.isBlank()) pad = "";
        String tempDecl = type + " " + temp + " = " + helper + "(" + arguments + ");\n" + pad;
        var changes = List.of(insert(stmt, tempDecl), insert(call.call().getStartPosition(), temp + " != null ? " + temp + " : "),
                insert(end, member));
        var anchor = JavaSourceRewritePlan.captureNode(call.caller(), site.source);
        var plan = new JavaSourceRewritePlan("Source.java", MathematicalAnalysis.digest(site.source), site.source.length(), start,
                end - start, List.of(anchor), List.of(), Map.of("field-call", List.of(anchor.id())), changes);
        plan.resolve(site.unit, site.source);
        String original = text(site.source, call.caller());
        StringBuilder replacement = new StringBuilder(original);
        for (var change : changes.reversed()) replacement.insert(change.offset() - start, change.replacement());
        var receipt = new FieldCallEvidence(key(call.caller().resolveBinding()), MathematicalAnalysis.digest(original), helper, temp,
                call.arguments().stream().map(n -> key(n.resolveBinding())).toList(), facts.inputs(), facts.witnessKey());
        var proof = new FieldRootEvidence(base.certificate(), base.methodKey(), base.sinkKey(), prefix, indent, base.swappedInputs(),
                base.methodHash(), base.bindings(), base.localDefinitions(), base.dependencies(), base.searchWork(), base.candidates(), receipt);
        var region = new VerifiedRegion(start, original.length(), null, null, original, replacement.toString(), null, plan,
                null, null, null, null, null, null, null, null, proof);
        String output = site.source.substring(0, start) + replacement + site.source.substring(end);
        site.work.charge(output.length());
        var after = MathematicalAnalysis.checkGeneratedSource(site.unit, site.source, output, compiler, options.targetJava(), monitor, env,
                GeneratedSourceDiagnostics.edits(plan));
        JavaGuardedFieldOptimizer.verify(after, output, region, List.of(), options, env, monitor, site.work);
        String message = "Verified exact ordered field coordinates using the call's reaching values and the original fallback.";
        edits.add(new Replacement(start, original.length(), replacement.toString(), message));
        evidence.add(region);
        diagnostics.add(new Diagnostic("VERIFIED_QUADRATIC_ROOT_CALL", message, start, original.length(), Stage.PROOF));
    }

    private static JavaSourceRewritePlan.Change insert(int offset, String text) {
        return new JavaSourceRewritePlan.Change(offset, 0, MathematicalAnalysis.digest(""), text);
    }

    /** The nullable call shape can also be a handwritten cache. Rebind the existing guard and compare its
     * decoded schedule with the SDK's selected exact-pair program before reserving this scalar root. */
    static boolean isEmittedCandidate(CompilationUnit ast, String source, MethodDeclaration root, MethodDeclaration caller,
            VariableDeclarationStatement declaration, Statement statement, MethodInvocation attempt, MethodInvocation fallback,
            MathCleanUpOptions options, JavaValueSourceSummary.Work work) {
        if (Modifier.isSynchronized(root.getModifiers()) || attempt.arguments().size() < 3 || attempt.arguments().size() > 4
                || !attempt.resolveTypeBinding().isEqualTo(root.resolveBinding().getReturnType())
                || !(ast.findDeclaringNode(attempt.resolveMethodBinding()) instanceof MethodDeclaration helper)
                || helper.getParent() != caller.getParent() || helper.getModifiers() != (Modifier.PRIVATE | Modifier.STATIC)
                || helper.getBody() == null || helper.getBody().statements().size() != 3
                || !(helper.getBody().statements().get(1) instanceof IfStatement fast)
                || !(fast.getThenStatement() instanceof Block block) || block.statements().isEmpty()
                || !(block.statements().getFirst() instanceof VariableDeclarationStatement first) || first.fragments().size() != 1)
            return false;
        String register = ((VariableDeclarationFragment)first.fragments().getFirst()).getName().getIdentifier();
        if (register.length() < 2 || !register.endsWith("3")) return false;
        String prefix = register.substring(0, register.length() - 1);
        int start = fast.getStartPosition();
        String indent = source.substring(source.lastIndexOf('\n', start - 1) + 1, start);
        if (!indent.isBlank()) return false;
        for (boolean swapped : List.of(false, true)) {
            try {
                var site = new JavaGuardedFieldSource(ast, source, root, false, swapped, work);
                if (!JavaFieldCallSite.nonnullFactory(site)) return false;
                var call = JavaFieldCallSite.bind(site, caller, statement, fallback);
                var facts = JavaFieldGuardFacts.inspect(site, call, declaration);
                var decoded = decodeHelper(site, helper, call, attempt, facts, prefix, indent);
                var expected = JavaGuardedFieldOptimizer.contract(site, MathematicalAnalysis.digest(key(root.resolveBinding())));
                var found = JavaGuardedFieldOptimizer.search(expected, options, work);
                if (found.certificate().isPresent() && decoded.equals(found.certificate().get().program())) return true;
            } catch (JavaValueSourceSummary.DeadlineExceeded expired) { throw expired; }
            catch (IllegalArgumentException | ArithmeticException mismatch) { /* Try the other scalar-role assignment. */ }
        }
        return false;
    }

    static QuadraticRootProgram replay(JavaGuardedFieldSource site, VerifiedRegion region) {
        var proof = region.fieldRoot();
        var receipt = proof.callSite();
        require(!Modifier.isSynchronized(site.method.getModifiers()));
        var caller = requireType(site.unit.findDeclaringNode(receipt.callerKey()), MethodDeclaration.class);
        require(MathematicalAnalysis.digest(region.original()).equals(receipt.callerHash()) && JavaFieldCallSite.nonnullFactory(site));
        List<?> statements = caller.getBody().statements();
        VariableDeclarationStatement declaration = null;
        for (Object item : statements)
            if (item instanceof VariableDeclarationStatement decl && decl.fragments().size() == 1
                    && ((VariableDeclarationFragment)decl.fragments().getFirst()).getName().getIdentifier().equals(receipt.temporaryName())) declaration = decl;
        if (declaration == null) throw unavailable();
        int nextStatement = statements.indexOf(declaration) + 1;
        require(nextStatement < statements.size());
        var temp = (VariableDeclarationFragment)declaration.fragments().getFirst();
        var tryCall = requireType(temp.getInitializer(), MethodInvocation.class);
        require(staticCall(tryCall));
        var helper = requireType(site.unit.findDeclaringNode(tryCall.resolveMethodBinding()), MethodDeclaration.class);
        require(helper.getParent() == caller.getParent() && helper.getName().getIdentifier().equals(receipt.helperName())
                && helper.getModifiers() == (Modifier.PRIVATE | Modifier.STATIC) && helper.getBody() != null);
        var statement = (Statement)statements.get(nextStatement);
        var choose = requireType(JavaFieldCallSite.destination(statement), ConditionalExpression.class);
        var test = requireType(choose.getExpression(), InfixExpression.class);
        require(test.getOperator() == InfixExpression.Operator.NOT_EQUALS && !test.hasExtendedOperands()
                && test.getRightOperand() instanceof NullLiteral && variable(test.getLeftOperand()).isEqualTo(temp.resolveBinding())
                && variable(choose.getThenExpression()).isEqualTo(temp.resolveBinding()));
        var call = JavaFieldCallSite.bind(site, caller, statement, requireType(choose.getElseExpression(), MethodInvocation.class));
        require(call.arguments().stream().map(n -> key(n.resolveBinding())).toList().equals(receipt.arguments()));
        int callerStart = caller.getStartPosition(), callerEnd = callerStart + caller.getLength();
        require(callerEnd <= helper.getStartPosition() && site.source.substring(callerEnd, helper.getStartPosition()).isBlank()
                && helper.getStartPosition() + helper.getLength() == callerStart + region.replacement().length());
        String original = site.source.substring(callerStart, declaration.getStartPosition())
                + site.source.substring(statement.getStartPosition(), choose.getStartPosition()) + text(site.source, call.call())
                + site.source.substring(choose.getStartPosition() + choose.getLength(), callerEnd);
        require(MathematicalAnalysis.digest(original).equals(receipt.callerHash()));
        var facts = JavaFieldGuardFacts.inspect(site, call, declaration);
        require(facts.inputs().equals(receipt.inputs()) && facts.witnessKey().equals(receipt.normWitnessKey()));
        return decodeHelper(site, helper, call, tryCall, facts, proof.prefix(), proof.indent());
    }

    private static QuadraticRootProgram decodeHelper(JavaGuardedFieldSource site, MethodDeclaration helper, JavaFieldCallSite call,
            MethodInvocation tryCall, JavaFieldGuardFacts facts, String prefix, String indent) {
        int count = facts.witnessKey().isEmpty() ? 3 : 4;
        require(helper.parameters().size() == count && tryCall.arguments().size() == count
                && helper.resolveBinding().getReturnType().isEqualTo(site.method.resolveBinding().getReturnType()));
        var roles = new ArrayList<Ref>();
        for (int i = 0; i < 3; i++) {
            require(variable(arg(tryCall, i)).isEqualTo(call.arguments().get(i).resolveBinding()));
            var formal = (SingleVariableDeclaration)helper.parameters().get(i);
            require(big(formal.resolveBinding().getType()) && formal.getName().getIdentifier().equals(site.inputs.get(i).spelling()));
            roles.add(new Ref(formal.getName().getIdentifier(), key(formal.resolveBinding()), null));
        }
        String witness = "";
        if (count == 4) {
            var formal = (SingleVariableDeclaration)helper.parameters().get(3);
            require(key(variable(arg(tryCall, 3))).equals(facts.witnessKey())
                    && formal.resolveBinding().getType().getQualifiedName().equals("boolean"));
            witness = formal.getName().getIdentifier();
        }
        var body = helper.getBody().statements();
        require(body.size() == 3 && body.get(2) instanceof ReturnStatement ret && ret.getExpression() instanceof NullLiteral);
        var p = requireType(body.getFirst(), VariableDeclarationStatement.class);
        require(p.fragments().size() == 1);
        var modulus = (VariableDeclarationFragment)p.fragments().getFirst();
        require(modulus.getName().getIdentifier().equals(site.modulus.spelling())
                && key(site.checkedVariable(modulus.getInitializer())).equals(site.modulusField.key()));
        var fast = requireType(body.get(1), IfStatement.class);
        if (count == 4) {
            String id = key(((SingleVariableDeclaration)helper.parameters().get(3)).resolveBinding());
            String name = witness;
            fast.getExpression().accept(new ASTVisitor() {
                @Override public boolean visit(SimpleName n) {
                    site.work.charge(1);
                    if (n.getIdentifier().equals(name)) require(key(n.resolveBinding()).equals(id));
                    return true;
                }
            });
        }
        return new JavaFieldProgramLowering(site, prefix, indent, facts, witness, roles, key(modulus.resolveBinding())).decode(fast);
    }
}
