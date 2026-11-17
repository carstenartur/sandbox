/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;

import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

/** Replaces a private growing byte prefix with one bounded result array. */
final class JavaByteArrayPrefixOptimizer {
    private static final int MAX_OUTPUT_LENGTH = 65535;
    private static final String DESCRIPTION = "Preallocate the byte-array prefix and retain each block evaluation"; //$NON-NLS-1$

    private JavaByteArrayPrefixOptimizer() { }

    private record Prefix(VariableDeclarationStatement seed, Statement append, ReturnStatement result,
            SimpleName accumulator, SimpleName requested, MethodInvocation cloneCall,
            MethodInvocation appendCall, MethodInvocation sliceCall) { }

    static void optimize(CompilationUnit ast, String source, MathCleanUpOptions options, IProgressMonitor monitor,
            int selection, int selectionLength, Map<String, String> compiler, MathematicalAnalysis.SourceEnvironment environment,
            List<MathematicalAnalysis.Replacement> replacements, List<MathematicalAnalysis.VerifiedRegion> evidence,
            JavaValueSourceSummary.Work work) {
        if (options.safety() != SafetyProfile.PRESERVE_JAVA || options.goal() == OptimizationGoal.READABILITY
                || !source.contains("concatenate") || !source.contains("copyOfRange") || work.remaining() < source.length()) return; //$NON-NLS-1$ //$NON-NLS-2$
        var scopes = new ArrayList<Block>();
        var names = new HashSet<String>();
        JavaSourceContext.valueWork(ast, work);
        try {
            ast.accept(new ASTVisitor() {
                @Override public boolean preVisit2(ASTNode node) { work.charge(1); return true; }
                @Override public boolean visit(SimpleName name) { names.add(name.getIdentifier()); return true; }
                @Override public boolean visit(Block scope) {
                    if (scope.statements().size() >= 3 && scope.statements().getLast() instanceof ReturnStatement) scopes.add(scope);
                    return true;
                }
            });
            var contract = new ByteArrayUtilityContract();
            for (Block scope : scopes) {
                work.charge(1);
                var statements = scope.statements();
                if (!(statements.get(statements.size() - 3) instanceof VariableDeclarationStatement seed)) continue;
                var result = (ReturnStatement) statements.getLast();
                int start = seed.getStartPosition(), length = end(result) - start;
                if (selection >= 0 && !overlaps(start, length, selection, Math.max(1, selectionLength))) continue;
                work.charge(replacements.size());
                if (replacements.stream().anyMatch(edit -> overlaps(start, length, edit.offset(), edit.length()))) continue;
                var prefix = admit(ast, statements, seed, result, work);
                if (prefix == null || !contract.verify(ast, prefix.cloneCall(), prefix.appendCall(), prefix.sliceCall())) continue;
                work.charge(4L * source.length());
                var edits = emit(source, prefix, names);
                var plan = JavaSourceRewritePlan.replaceStatements(ast, source, List.of(seed, prefix.append(), result), edits);
                var document = new Document(source);
                try {
                    plan.newEdit(source).apply(document);
                    String generated = document.get();
                    var parsed = MathematicalAnalysis.checkGeneratedSource(ast, source, generated, compiler,
                            options.targetJava(), monitor, environment, GeneratedSourceDiagnostics.edits(plan));
                    int seedShift = edits.get(0).length() - seed.getLength();
                    int appendShift = edits.get(1).length() - prefix.append().getLength();
                    int appendStart = prefix.append().getStartPosition() + seedShift;
                    int resultStart = result.getStartPosition() + seedShift + appendShift;
                    if (!verifiedResult(parsed, resultStart, edits.get(2).length(), prefix.sliceCall().resolveMethodBinding())
                            || !verifiedPlatformCalls(parsed, start, edits.get(0).length(), appendStart, edits.get(1).length())) continue;
                    String replacement = generated.substring(start, start + length + generated.length() - source.length());
                    replacements.add(new MathematicalAnalysis.Replacement(start, length, replacement, DESCRIPTION));
                    evidence.add(new MathematicalAnalysis.VerifiedRegion(start, length, null, null,
                            source.substring(start, start + length), replacement, null, plan));
                } catch (IllegalArgumentException | BadLocationException invalid) {
                    // A broken binding, edit, or newly diagnosed program never reaches Apply.
                }
            }
        } catch (JavaValueSourceSummary.DeadlineExceeded expired) {
            throw expired;
        } catch (IllegalArgumentException exhausted) {
            // Discovery and candidate replay share the request budget.
        } finally {
            JavaSourceContext.valueWork(ast, null);
        }
    }

    private static Prefix admit(CompilationUnit ast, List<?> scope, VariableDeclarationStatement seed, ReturnStatement result,
            JavaValueSourceSummary.Work work) {
        if (seed.fragments().size() != 1 || !seed.modifiers().isEmpty()
                || !(seed.fragments().getFirst() instanceof VariableDeclarationFragment variable)
                || !byteArray(variable.resolveBinding() == null ? null : variable.resolveBinding().getType())
                || !(variable.getInitializer() instanceof MethodInvocation clone) || !named(clone, "clone", 1) //$NON-NLS-1$
                || !(result.getExpression() instanceof MethodInvocation slice) || !named(slice, "copyOfRange", 3) //$NON-NLS-1$
                || !(slice.arguments().get(1) instanceof NumberLiteral start) || !Integer.valueOf(0).equals(start.resolveConstantExpressionValue())
                || !(slice.arguments().get(2) instanceof SimpleName requested)
                || !(requested.resolveBinding() instanceof IVariableBinding limit) || limit.isField() || limit.isRecovered()
                || !limit.isEffectivelyFinal() && !Modifier.isFinal(limit.getModifiers()) || !primitive(limit.getType(), "int")) return null; //$NON-NLS-1$
        Statement loop = (Statement) scope.get(scope.size() - 2);
        Statement body = switch (loop) {
            case ForStatement counted -> counted.getBody();
            case EnhancedForStatement each -> each.getBody();
            case WhileStatement repeated -> repeated.getBody();
            case DoStatement repeated -> repeated.getBody();
            default -> null;
        };
        if (!(body instanceof Block block) || block.statements().isEmpty()
                || !(block.statements().getLast() instanceof ExpressionStatement append)
                || !(append.getExpression() instanceof Assignment update) || update.getOperator() != Assignment.Operator.ASSIGN
                || !(update.getRightHandSide() instanceof MethodInvocation call) || !named(call, "concatenate", 2)) return null; //$NON-NLS-1$
        var binding = variable.resolveBinding();
        if (!sameLocal(update.getLeftHandSide(), binding) || !sameLocal(call.arguments().getFirst(), binding)
                || !sameLocal(slice.arguments().getFirst(), binding)
                || !bounded(scope.subList(0, scope.size() - 3), limit, work)) return null;
        var writesAndRead = Set.of(update.getLeftHandSide(), call.arguments().getFirst());
        boolean[] observed = {false};
        loop.accept(new ASTVisitor() {
            @Override public boolean preVisit2(ASTNode node) { work.charge(1); return !observed[0]; }
            @Override public boolean visit(SimpleName name) {
                observed[0] = binding.isEqualTo(name.resolveBinding()) && !writesAndRead.contains(name);
                return !observed[0];
            }
        });
        if (observed[0]) return null;
        for (Object item : ast.getCommentList()) {
            work.charge(1);
            var comment = (Comment) item;
            if (intersects(comment, seed) || intersects(comment, append) || intersects(comment, result)) return null;
        }
        return new Prefix(seed, append, result, variable.getName(), requested, clone, call, slice);
    }

    /** A stable local makes this earlier, bounded allocation independent of the block producers. */
    private static boolean bounded(List<?> preceding, IVariableBinding binding, JavaValueSourceSummary.Work work) {
        for (Object statement : preceding) {
            work.charge(1);
            if (!(statement instanceof IfStatement guard) || guard.getElseStatement() != null
                    || !(guard.getExpression() instanceof InfixExpression relation) || relation.hasExtendedOperands()
                    || relation.getOperator() != InfixExpression.Operator.GREATER || !sameLocal(relation.getLeftOperand(), binding)
                    || !(relation.getRightOperand() instanceof NumberLiteral bound)
                    || !(bound.resolveConstantExpressionValue() instanceof Integer upper) || upper > MAX_OUTPUT_LENGTH) continue;
            Statement failure = guard.getThenStatement();
            if (failure instanceof ThrowStatement || failure instanceof Block block && block.statements().size() == 1
                    && block.statements().getFirst() instanceof ThrowStatement) return true;
        }
        return false;
    }

    private static List<String> emit(String source, Prefix prefix, Set<String> names) {
        String output = text(source, prefix.accumulator());
        String requested = text(source, prefix.requested());
        String first = fresh(names, "firstBlock"); //$NON-NLS-1$
        String next = fresh(names, "nextBlock"); //$NON-NLS-1$
        String accumulated = fresh(names, "prefixLength"); //$NON-NLS-1$
        String previous = fresh(names, "previousLength"); //$NON-NLS-1$
        boolean nextLineBrace = margin(source, prefix.append().getParent().getStartPosition()).isBlank();
        String seed = """
                final byte[] %1$s = %2$s;
                final byte[] %3$s = new byte[java.lang.Math.max(0, %4$s)];
                int %5$s = -1;
                if (%1$s != null) {
                    %5$s = %1$s.length;
                    java.lang.System.arraycopy(%1$s, 0, %3$s, 0, java.lang.Math.min(%5$s, %3$s.length));
                }
                """; //$NON-NLS-1$
        seed = indent(seed, source, prefix.seed(), nextLineBrace).formatted(first,
                text(source, (ASTNode) prefix.cloneCall().arguments().getFirst()), output, requested, accumulated);
        String append = """
                final byte[] %1$s = %2$s;
                if (%1$s != null) {
                    final int %3$s = java.lang.Math.max(0, %4$s);
                    %4$s = %3$s + %1$s.length;
                    if (%4$s < 0) {
                        // The growing concatenation would throw on this negative array length.
                        return new byte[%4$s];
                    }
                    if (%3$s < %5$s.length) {
                        java.lang.System.arraycopy(%1$s, 0, %5$s, %3$s,
                            java.lang.Math.min(%1$s.length, %5$s.length - %3$s));
                    }
                }
                """; //$NON-NLS-1$
        append = indent(append, source, prefix.append(), nextLineBrace).formatted(next,
                text(source, (ASTNode) prefix.appendCall().arguments().get(1)), previous, accumulated, output);
        String result = "return %s < 0 || %s < 0 ? %s.%s((byte[]) null, 0, %s) : %s;".formatted(requested, accumulated, //$NON-NLS-1$
                text(source, prefix.sliceCall().getExpression()), text(source, prefix.sliceCall().getName()), requested, output);
        return List.of(seed, append, result);
    }

    private static boolean verifiedResult(CompilationUnit ast, int start, int length, IMethodBinding original) {
        return NodeFinder.perform(ast, start, length) instanceof ReturnStatement result
                && result.getExpression() instanceof ConditionalExpression conditional
                && conditional.getThenExpression() instanceof MethodInvocation fallback
                && original.getMethodDeclaration().isEqualTo(fallback.resolveMethodBinding());
    }

    private static boolean verifiedPlatformCalls(CompilationUnit ast, int seedStart, int seedLength, int appendStart, int appendLength) {
        boolean[] valid = {true};
        ast.accept(new ASTVisitor() {
            @Override public boolean preVisit2(ASTNode node) {
                return valid[0] && (overlaps(node.getStartPosition(), node.getLength(), seedStart, seedLength)
                        || overlaps(node.getStartPosition(), node.getLength(), appendStart, appendLength));
            }
            @Override public boolean visit(MethodInvocation call) {
                if (call.getExpression() instanceof Name qualifier) {
                    String type = qualifier.getFullyQualifiedName();
                    if ("java.lang.Math".equals(type) || "java.lang.System".equals(type)) //$NON-NLS-1$ //$NON-NLS-2$
                        valid[0] = ByteArrayUtilityContract.libraryCall(call, type, call.getName().getIdentifier());
                }
                return valid[0];
            }
        });
        return valid[0];
    }

    private static String indent(String code, String source, Statement anchor, boolean nextLineBrace) {
        String margin = margin(source, anchor.getStartPosition());
        margin = margin.substring(0, margin.length() - margin.stripLeading().length());
        String formatted = nextLineBrace ? code.replaceAll("(?m)^(\\h*)(.*\\)) \\{$", "$1$2\n$1{") : code; //$NON-NLS-1$ //$NON-NLS-2$
        // A moved argument retains its exact source, including text-block whitespace and line delimiters.
        return formatted.stripTrailing().replace("\n", (source.contains("\r\n") ? "\r\n" : "\n") + margin); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    }

    private static String margin(String source, int start) {
        return source.substring(source.lastIndexOf('\n', start - 1) + 1, start);
    }

    private static String fresh(Set<String> names, String preferred) {
        String name = preferred;
        for (int i = 1; !names.add(name); i++) name = preferred + i;
        return name;
    }

    static boolean byteArray(ITypeBinding type) {
        return type != null && !type.isRecovered() && type.isArray() && type.getDimensions() == 1 && primitive(type.getElementType(), "byte"); //$NON-NLS-1$
    }

    static boolean primitive(ITypeBinding type, String name) {
        return type != null && type.isPrimitive() && name.equals(type.getName());
    }

    private static boolean sameLocal(Object occurrence, IVariableBinding variable) {
        return occurrence instanceof SimpleName name && variable.isEqualTo(name.resolveBinding());
    }

    private static boolean named(MethodInvocation call, String name, int arguments) {
        return name.equals(call.getName().getIdentifier()) && call.arguments().size() == arguments;
    }

    private static boolean intersects(ASTNode left, ASTNode right) {
        return overlaps(left.getStartPosition(), left.getLength(), right.getStartPosition(), right.getLength());
    }

    private static boolean overlaps(int start, int length, int other, int otherLength) {
        return start < (long) other + otherLength && other < (long) start + length;
    }

    private static int end(ASTNode node) { return node.getStartPosition() + node.getLength(); }
    private static String text(String source, ASTNode node) { return source.substring(node.getStartPosition(), end(node)); }
}
