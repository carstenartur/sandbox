/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.dom.*;
import org.sandbox.functional.core.model.UlrExpression;
import org.sandbox.functional.core.model.UlrExpression.Type;

import de.regelsuche.ast.iteration.TableXorReduction.Axis;
import de.regelsuche.ast.iteration.TableXorReduction.Storage;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.iteration.TableXorReductionSdk;

/** A source-bound description of a byte table contraction. Every row load remains an observable access. */
public record JavaByteTableAccumulation(String sourceSha256, int sourceStart, int sourceLength,
        List<Axis> loopOrder, Storage storage, List<Loop> loops, Map<ArrayRole, String> arrayBindings,
        List<Access> accessSchedule) {
    public enum ArrayRole { TABLE, COEFFICIENT, INPUT, OUTPUT }
    public enum AccessKind { ARRAY_READ, ARRAY_WRITE }
    public enum Element { BYTE_ROW, BYTE }
    public enum Aliasing { MAY_ALIAS }
    /** Each loop has a Java int counter, strict upper bound and unit increment. */
    public record Loop(String counterBinding, UlrExpression start, UlrExpression endExclusive, int sourceStart, int sourceLength) { }
    /** The schedule is in lexical execution order within the indicated loop nests. */
    public record Access(AccessKind kind, ArrayRole arrayRole, Element element, int loopDepth, int sourceStart, int sourceLength) { }
    public JavaByteTableAccumulation {
        loopOrder = List.copyOf(loopOrder);
        loops = List.copyOf(loops);
        arrayBindings = Map.copyOf(arrayBindings);
        accessSchedule = List.copyOf(accessSchedule);
    }

    /** Distinct bindings can denote the same matrix or share any of their mutable rows. */
    @com.fasterxml.jackson.annotation.JsonProperty public Aliasing aliasing() { return Aliasing.MAY_ALIAS; }
    @com.fasterxml.jackson.annotation.JsonProperty public String fieldClassification() { return "UNVERIFIED_TABLE"; } //$NON-NLS-1$
    @com.fasterxml.jackson.annotation.JsonProperty public boolean editAuthorized() { return false; }

    /** Route plausible nests during the normal stale-source walk. Binding and expression work is charged below. */
    static final class Candidates {
        private final Set<ForStatement> roots = new LinkedHashSet<>();
        private final long limit;

        Candidates(MathCleanUpOptions options) {
            limit = options.kinds().containsAll(Set.of(NumericKind.BYTE, NumericKind.INT)) ? options.workBudget() / 64 : 0;
        }
        void consider(VariableDeclarationFragment declaration) {
            if (roots.size() >= limit || !(declaration.getInitializer() instanceof NumberLiteral)
                    || !(declaration.getParent() instanceof VariableDeclarationStatement statement)
                    || !(statement.getParent() instanceof Block body) || !(body.getParent() instanceof ForStatement middle)
                    || !(middle.getParent() instanceof Block outerBody) || !(outerBody.getParent() instanceof ForStatement outer)) return;
            roots.add(outer);
        }
        List<JavaByteTableAccumulation> find(CompilationUnit unit, String digest, int selection, int length,
                List<? extends ISourceRange> protectedRanges, JavaValueSourceSummary.Work work) {
            if (roots.isEmpty()) return List.of();
            for (var problem : unit.getProblems()) if (problem.isError()) return List.of();
            var results = new ArrayList<JavaByteTableAccumulation>();
            for (var root : roots) {
                if (selection >= 0 && !overlaps(root, selection, Math.max(1, length))) continue;
                if (protectedRanges.stream().anyMatch(p -> overlaps(root, p.getOffset(), p.getLength()))) continue;
                try {
                    results.add(new Projection(work).project(root, digest));
                } catch (Unsupported ignored) { /* Outside the checked fragment. */ }
                catch (JavaValueSourceSummary.DeadlineExceeded expired) { throw expired; }
                catch (IllegalArgumentException exhausted) { break; }
            }
            return List.copyOf(results);
        }
    }

    private static boolean overlaps(ASTNode node, int start, int length) {
        return node.getStartPosition() < (long) start + length && start < (long) node.getStartPosition() + node.getLength();
    }

    private sealed interface Term permits Ref, At, UnsignedByte { }
    private record Ref(String binding) implements Term { }
    private record At(Term array, Term index) implements Term { }
    private record UnsignedByte(Term value) implements Term { }
    private record Event(At access, ArrayAccess source, int depth, AccessKind kind) { }

    private static final class Projection {
        private final JavaValueSourceSummary.Work work;
        private final List<Loop> loops = new ArrayList<>();
        private final Map<String, Term> aliases = new HashMap<>();
        private final Set<String> referencedAliases = new HashSet<>();
        private final List<Event> events = new ArrayList<>();
        private String accumulator;
        private Term term, destination;

        Projection(JavaValueSourceSummary.Work work) { this.work = work; }

        JavaByteTableAccumulation project(ForStatement root, String digest) {
            loop(root, 1);
            require(aliases.keySet().equals(referencedAliases));
            // dest[o][p] = low8(XOR_i T[u8(C[o][i])][u8(X[i][p])]).
            At outputByte = at(destination), outputRow = at(outputByte.array());
            String output = ref(outputRow.index()), position = ref(outputByte.index()), input = loops.get(2).counterBinding();
            At tableByte = at(term), tableRow = at(tableByte.array());
            At coefficient = at(unsigned(tableRow.index())), coefficientRow = at(coefficient.array());
            At inputByte = at(unsigned(tableByte.index())), inputRow = at(inputByte.array());
            require(ref(coefficientRow.index()).equals(output) && ref(coefficient.index()).equals(input)
                    && ref(inputRow.index()).equals(input) && ref(inputByte.index()).equals(position));
            require(!output.equals(position) && !output.equals(input) && !position.equals(input));
            var roles = Map.of(ArrayRole.TABLE, ref(tableRow.array()), ArrayRole.COEFFICIENT, ref(coefficientRow.array()),
                    ArrayRole.INPUT, ref(inputRow.array()), ArrayRole.OUTPUT, ref(outputRow.array()));
            var byBinding = new HashMap<String, ArrayRole>();
            roles.forEach((role, binding) -> require(byBinding.put(binding, role) == null));
            var axisBindings = Map.of(output, Axis.OUTPUT, position, Axis.POSITION, input, Axis.INPUT);
            var order = new ArrayList<Axis>();
            for (var loop : loops) {
                Axis axis = axisBindings.get(loop.counterBinding());
                require(axis != null && (axis == Axis.POSITION || zero(loop.start())));
                order.add(axis);
            }
            var reduction = TableXorReductionSdk.describe(order, Storage.ZERO_XOR_REDUCE_THEN_STORE);
            var schedule = new ArrayList<Access>();
            for (var event : events) {
                Term base = event.access().array();
                while (base instanceof At access) base = access.array();
                ArrayRole role = byBinding.get(ref(base));
                require(role != null);
                var type = event.source().resolveTypeBinding();
                if (type == null || type.isRecovered()) throw UNSUPPORTED;
                Element element = type.isArray() ? Element.BYTE_ROW : Element.BYTE;
                schedule.add(new Access(event.kind(), role, element, event.depth(), event.source().getStartPosition(), event.source().getLength()));
            }
            return new JavaByteTableAccumulation(digest, root.getStartPosition(), root.getLength(), reduction.order(),
                    reduction.storage(), loops, roles, schedule);
        }

        private void loop(ForStatement node, int depth) {
            work.charge(1);
            require(node.initializers().size() == 1 && node.updaters().size() == 1
                    && node.initializers().getFirst() instanceof VariableDeclarationExpression && node.getBody() instanceof Block);
            var init = (VariableDeclarationExpression) node.initializers().getFirst();
            var counter = fragment(init.fragments());
            var binding = variable(counter.getName());
            requireType(binding.getType(), "int"); //$NON-NLS-1$
            String key = binding.getKey();
            var test = binary(node.getExpression(), InfixExpression.Operator.LESS);
            require(variable(test.getLeftOperand()).getKey().equals(key));
            Expression update = (Expression) node.updaters().getFirst();
            Expression updated;
            if (update instanceof PostfixExpression post && post.getOperator() == PostfixExpression.Operator.INCREMENT) updated = post.getOperand();
            else if (update instanceof PrefixExpression pre && pre.getOperator() == PrefixExpression.Operator.INCREMENT) updated = pre.getOperand();
            else throw UNSUPPORTED;
            require(variable(updated).getKey().equals(key));
            loops.add(new Loop(key, bound(counter.getInitializer(), false), bound(test.getRightOperand(), true),
                    node.getStartPosition(), node.getLength()));
            boolean nested = false, operation = false;
            for (Object entry : ((Block) node.getBody()).statements()) {
                work.charge(1);
                if (entry instanceof VariableDeclarationStatement declaration && !nested && !operation) {
                    var local = fragment(declaration.fragments());
                    var localBinding = variable(local.getName());
                    if (localBinding.getType().isArray()) {
                        requireType(localBinding.getType(), "byte[]"); //$NON-NLS-1$
                        Term alias = expression(local.getInitializer(), depth, 0);
                        require(alias instanceof At);
                        aliases.put(localBinding.getKey(), alias);
                    } else {
                        require(depth == 2 && accumulator == null && integer(local.getInitializer(), 0));
                        requireType(localBinding.getType(), "int"); //$NON-NLS-1$
                        accumulator = localBinding.getKey();
                    }
                } else if (entry instanceof ForStatement child && depth < 3 && !nested && !operation) {
                    require(depth == 1 || accumulator != null);
                    loop(child, depth + 1);
                    nested = true;
                } else if (entry instanceof ExpressionStatement statement && statement.getExpression() instanceof Assignment assign && !operation) {
                    if (depth == 3 && assign.getOperator() == Assignment.Operator.BIT_XOR_ASSIGN) {
                        require(variable(assign.getLeftHandSide()).getKey().equals(accumulator));
                        requireType(assign.getRightHandSide().resolveTypeBinding(), "byte"); //$NON-NLS-1$
                        term = expression(assign.getRightHandSide(), depth, 0);
                    } else if (depth == 2 && nested && assign.getOperator() == Assignment.Operator.ASSIGN) {
                        require(assign.getLeftHandSide() instanceof ArrayAccess && assign.getRightHandSide() instanceof CastExpression);
                        var cast = (CastExpression) assign.getRightHandSide();
                        requireType(cast.getType().resolveBinding(), "byte"); //$NON-NLS-1$
                        require(variable(cast.getExpression()).getKey().equals(accumulator));
                        var target = (ArrayAccess) assign.getLeftHandSide();
                        requireType(target.resolveTypeBinding(), "byte"); //$NON-NLS-1$
                        destination = access(target, depth, AccessKind.ARRAY_WRITE, 0);
                    } else throw UNSUPPORTED;
                    operation = true;
                } else throw UNSUPPORTED;
            }
            require(nested == (depth < 3) && operation == (depth > 1));
        }

        private UlrExpression bound(Expression value, boolean allowSum) {
            if (integer(value, 0)) return new UlrExpression.Constant(BigInteger.ZERO, Type.INT32);
            if (allowSum && value instanceof InfixExpression) {
                var sum = binary(value, InfixExpression.Operator.PLUS);
                return new UlrExpression.Binary(UlrExpression.BinaryOperator.ADD, bound(sum.getLeftOperand(), false),
                        bound(sum.getRightOperand(), false), Type.INT32, UlrExpression.Overflow.WRAP);
            }
            var parameter = variable(value);
            require(parameter.isParameter());
            requireType(parameter.getType(), "int"); //$NON-NLS-1$
            return new UlrExpression.Read(parameter.getKey(), Type.INT32);
        }

        private Term expression(Expression expression, int depth, int nesting) {
            work.charge(1);
            require(nesting <= 16);
            if (expression instanceof ParenthesizedExpression parenthesized) return expression(parenthesized.getExpression(), depth, nesting + 1);
            if (expression instanceof ArrayAccess array) return access(array, depth, AccessKind.ARRAY_READ, nesting);
            if (expression instanceof InfixExpression) {
                var mask = binary(expression, InfixExpression.Operator.AND);
                require(integer(mask.getRightOperand(), 255));
                requireType(mask.getLeftOperand().resolveTypeBinding(), "byte"); //$NON-NLS-1$
                return new UnsignedByte(expression(mask.getLeftOperand(), depth, nesting + 1));
            }
            var binding = variable(expression);
            String key = binding.getKey();
            Term alias = aliases.get(key);
            if (alias != null) {
                referencedAliases.add(key);
                return alias;
            }
            // Leaf matrices and counters are Java local variables or parameters. Instance/static fields
            // (including volatile field reads) are outside this region; captured matrix handles are allowed.
            String type = binding.getType().getQualifiedName();
            require(type.equals("byte[][]") || type.equals("int")); //$NON-NLS-1$ //$NON-NLS-2$
            return new Ref(key);
        }

        private At access(ArrayAccess array, int depth, AccessKind kind, int nesting) {
            var address = new At(expression(array.getArray(), depth, nesting + 1), expression(array.getIndex(), depth, nesting + 1));
            events.add(new Event(address, array, depth, kind));
            return address;
        }
    }

    private static IVariableBinding variable(Expression expression) {
        if (!(expression instanceof SimpleName name) || !(name.resolveBinding() instanceof IVariableBinding binding)
                || binding.isRecovered() || binding.isField() || binding.getKey() == null) throw UNSUPPORTED;
        return binding.getVariableDeclaration();
    }
    private static VariableDeclarationFragment fragment(List<?> fragments) {
        if (fragments.size() != 1 || !(fragments.getFirst() instanceof VariableDeclarationFragment value)) throw UNSUPPORTED;
        return value;
    }
    private static InfixExpression binary(Expression expression, InfixExpression.Operator operator) {
        if (!(expression instanceof InfixExpression binary) || binary.getOperator() != operator || binary.hasExtendedOperands()) throw UNSUPPORTED;
        return binary;
    }
    private static boolean integer(Expression expression, int value) {
        return expression instanceof NumberLiteral literal && Integer.valueOf(value).equals(literal.resolveConstantExpressionValue());
    }
    private static boolean zero(UlrExpression expression) {
        return expression instanceof UlrExpression.Constant c && c.value().signum() == 0;
    }
    private static String ref(Term value) { if (value instanceof Ref ref) return ref.binding(); throw UNSUPPORTED; }
    private static Term unsigned(Term value) { if (value instanceof UnsignedByte cast) return cast.value(); throw UNSUPPORTED; }
    private static At at(Term value) { if (value instanceof At at) return at; throw UNSUPPORTED; }
    private static void requireType(ITypeBinding binding, String type) {
        require(binding != null && !binding.isRecovered() && binding.getQualifiedName().equals(type));
    }
    private static void require(boolean condition) { if (!condition) throw UNSUPPORTED; }
    private static final Unsupported UNSUPPORTED = new Unsupported();
    private static final class Unsupported extends RuntimeException {
        private static final long serialVersionUID = 1L;
        Unsupported() { super(null, null, false, false); }
    }
}
