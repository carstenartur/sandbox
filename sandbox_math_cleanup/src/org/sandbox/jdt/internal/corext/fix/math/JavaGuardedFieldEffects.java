/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.sandbox.jdt.internal.corext.fix.math.JavaGuardedFieldSource.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jdt.core.dom.*;

/** Positive summary for the complete two-coordinate factory, including uses after coordinate erasure.
 * The summary tracks ownership at each actual argument. Only private storage allocated by this call
 * can be written. A factory cannot observe an enclosing frame or invoke an unchecked callback.
 * The source-refactoring contract covers values, exception types/messages and relevant ordered
 * effects. Diagnostic source/bytecode positions and external inspection of those positions are
 * outside that contract; a propagated exception can expose a relocated caller line. */
final class JavaGuardedFieldEffects {
    private enum Value { PRIMITIVE, BIG, OWNED_ARRAY, READ_ARRAY, PRODUCT, STRING, NIL, VOID }
    private record Call(String binding, List<Value> arguments) {}
    private final JavaGuardedFieldSource site;
    private final Map<Call,Value> summaries = new HashMap<>();
    private final Set<Call> active = new HashSet<>();
    private final Set<String> initialized;
    private final Map<String,Optional<Object>> primitiveFields = new HashMap<>();

    JavaGuardedFieldEffects(JavaGuardedFieldSource site) {
        this.site = site;
        // Non-null pinned constants have completed their admitted initializer/alias chains.
        initialized = new HashSet<>(site.constantOwners);
        initialized.add(key(site.method.resolveBinding().getDeclaringClass()));
    }

    void check(IMethodBinding factory) { invoke(factory, List.of(Value.BIG, Value.BIG)); }

    private Value invoke(IMethodBinding target, List<Value> arguments) {
        check(target != null && !target.isRecovered() && !target.isVarargs(), "unresolved call");
        var method = target.getMethodDeclaration();
        var owner = method.getDeclaringClass();
        check(owner.isFromSource() && (method.isConstructor() || Modifier.isStatic(method.getModifiers()))
                && !Modifier.isSynchronized(method.getModifiers()), "unverified dispatch");
        initialize(owner);
        var call = new Call(key(method), List.copyOf(arguments));
        if (summaries.containsKey(call)) return summaries.get(call);
        check(active.size() < 16 && summaries.size() < 96 && active.add(call), "call graph bound");
        var declaration = declaration(method, MethodDeclaration.class);
        check(declaration.getBody() != null && declaration.parameters().size() == arguments.size(), "missing body");
        var scope = new Scope(method.isConstructor() ? owner : null);
        for (int i = 0; i < arguments.size(); i++) {
            var parameter = (SingleVariableDeclaration)declaration.parameters().get(i);
            scope.locals.put(key(parameter.resolveBinding()), arguments.get(i));
        }
        if (method.isConstructor()) {
            var type = declaration(owner, TypeDeclaration.class);
            check(Modifier.isFinal(owner.getModifiers()) && plainClass(owner), "unverified product type");
            for (Object member : type.bodyDeclarations()) {
                if (member instanceof Initializer init) check(Modifier.isStatic(init.getModifiers()), "instance initializer");
                if (member instanceof FieldDeclaration field && !Modifier.isStatic(field.getModifiers())) {
                    for (Object item : field.fragments()) check(((VariableDeclarationFragment)item).getInitializer() == null, "instance field initializer");
                }
            }
        }
        scope.statement(declaration.getBody());
        Value result = method.isConstructor() ? Value.PRODUCT : scope.result == null ? Value.VOID : scope.result;
        summaries.put(call, result);
        active.remove(call);
        return result;
    }

    private static boolean plainClass(ITypeBinding owner) {
        return owner.isClass() && (!owner.isNested() || Modifier.isStatic(owner.getModifiers()))
                && owner.getSuperclass() != null && owner.getSuperclass().getQualifiedName().equals("java.lang.Object")
                && owner.getInterfaces().length == 0;
    }

    private void initialize(ITypeBinding owner) {
        if (!initialized.add(key(owner))) return;
        check(initialized.size() < 64 && plainClass(owner), "unverified class initializer");
        var type = declaration(owner, TypeDeclaration.class);
        var scope = new Scope(null);
        for (Object member : type.bodyDeclarations()) {
            if (member instanceof Initializer init && Modifier.isStatic(init.getModifiers())) scope.statement(init.getBody());
            if (member instanceof FieldDeclaration field && Modifier.isStatic(field.getModifiers())) {
                for (Object item : field.fragments()) {
                    var var = (VariableDeclarationFragment)item;
                    if (var.getInitializer() != null) scope.expression(var.getInitializer());
                }
            }
        }
    }

    private <T> T declaration(IBinding binding, Class<T> type) {
        site.work.charge(1);
        site.definitions.add(key(binding));
        var definition = site.definition(binding);
        check(definition != null && type.isInstance(definition.declaration()), "source unavailable");
        return type.cast(definition.declaration());
    }

    /** A constant read can skip class initialization only while its complete source expression stays constant. */
    private boolean primitiveConstant(Name use, IVariableBinding field, int depth) {
        check(field.isField() && field.getType().isPrimitive() && Modifier.isStatic(field.getModifiers())
                && Modifier.isFinal(field.getModifiers()) && field.getDeclaringClass().isFromSource(), "unbound primitive field");
        check(site.directOwner(use, use instanceof QualifiedName q ? q.getQualifier() : null, field.getDeclaringClass(), site.normalized), "indirect primitive field lookup");
        String id = key(field);
        Object value = field.getConstantValue();
        var cached = primitiveFields.get(id);
        if (cached != null) {
            check(Objects.equals(cached.orElse(null), value), "inconsistent primitive field");
            return cached.isPresent();
        }
        check(depth < 16 && primitiveFields.size() < 128, "primitive constant bound");
        var fragment = declaration(field, VariableDeclarationFragment.class);
        var current = fragment.resolveBinding();
        check(current != null && id.equals(key(current)) && current.getType().isEqualTo(field.getType())
                && Modifier.isStatic(current.getModifiers()) && Modifier.isFinal(current.getModifiers())
                && Objects.equals(current.getConstantValue(), value), "changed primitive field");
        if (value != null) {
            var initializer = fragment.getInitializer();
            if (initializer == null) throw unsupported("missing constant initializer");
            initializer.accept(new ASTVisitor() {
                int nesting;
                @Override public void preVisit(ASTNode node) { site.work.charge(1); check(++nesting <= 64, "constant syntax depth"); }
                @Override public void postVisit(ASTNode node) { nesting--; }
                @Override public boolean visit(SimpleName name) {
                    if (name.resolveBinding() instanceof IVariableBinding dependency) {
                        Name reference = name.getParent() instanceof QualifiedName q && q.getName() == name ? q : name;
                        check(primitiveConstant(reference, dependency, depth + 1), "nonconstant initializer reference");
                    }
                    return true;
                }
            });
        }
        primitiveFields.put(id, Optional.ofNullable(value));
        return value != null;
    }

    private final class Scope {
        final Map<String,Value> locals = new HashMap<>();
        final ITypeBinding constructor;
        Value result;
        int depth;
        Scope(ITypeBinding constructor) { this.constructor = constructor; }

        void statement(Statement statement) {
            enter();
            try { checkStatement(statement); }
            finally { depth--; }
        }
        private void enter() {
            site.work.charge(1);
            check(++depth <= 64, "syntax depth");
        }
        private void checkStatement(Statement statement) {
            if (statement instanceof Block block) {
                for (Object item : block.statements()) statement((Statement)item);
            } else if (statement instanceof VariableDeclarationStatement vars) {
                declare(vars.fragments());
            } else if (statement instanceof ReturnStatement ret) {
                Value next = ret.getExpression() == null ? Value.VOID : expression(ret.getExpression());
                check(result == null || next == result, "incompatible exits");
                result = next;
            } else if (statement instanceof IfStatement choice) {
                primitive(choice.getExpression());
                statement(choice.getThenStatement());
                if (choice.getElseStatement() != null) statement(choice.getElseStatement());
            } else if (statement instanceof ForStatement loop) {
                for (Object init : loop.initializers()) expression((Expression)init);
                primitive(loop.getExpression());
                statement(loop.getBody());
                for (Object update : loop.updaters()) expression((Expression)update);
            } else if (statement instanceof ExpressionStatement expr) {
                expression(expr.getExpression());
            } else if (statement instanceof ThrowStatement thrown) {
                var error = requireType(thrown.getExpression(), ClassInstanceCreation.class);
                check(error.getAnonymousClassDeclaration() == null && (!site.normalized || JavaSourceTypeNames.closedType(error.getType(), site.work::charge))
                        && error.resolveTypeBinding() != null
                        && !error.resolveTypeBinding().isFromSource() && error.resolveTypeBinding().getQualifiedName().equals("java.lang.IllegalArgumentException")
                        && (error.arguments().isEmpty() || error.arguments().size() == 1 && error.arguments().getFirst() instanceof StringLiteral), "unverified throw");
            } else throw unsupported("statement " + statement.getClass().getSimpleName());
        }

        void declare(List<?> fragments) {
            for (Object item : fragments) {
                var var = (VariableDeclarationFragment)item;
                locals.put(key(var.resolveBinding()), expression(var.getInitializer()));
            }
        }
        void primitive(Expression expr) { check(expression(expr) == Value.PRIMITIVE, "primitive operand"); }

        Value expression(Expression original) {
            enter();
            try { return checkExpression(unwrap(original)); }
            finally { depth--; }
        }
        private Value checkExpression(Expression e) {
            if (e instanceof NumberLiteral || e instanceof BooleanLiteral || e instanceof CharacterLiteral) return Value.PRIMITIVE;
            if (e instanceof StringLiteral) return Value.STRING;
            if (e instanceof NullLiteral) return Value.NIL;
            if (e instanceof QualifiedName n && n.getName().getIdentifier().equals("length")
                    && n.getQualifier().resolveTypeBinding() != null && n.getQualifier().resolveTypeBinding().isArray()) {
                array(n.getQualifier());
                return Value.PRIMITIVE;
            }
            if (e instanceof Name name) return read(name);
            if (e instanceof ArrayAccess access) {
                array(access.getArray()); primitive(access.getIndex()); return Value.PRIMITIVE;
            }
            if (e instanceof ArrayCreation creation) {
                check(primitiveArray(creation.resolveTypeBinding()), "array component");
                for (Object size : creation.dimensions()) primitive((Expression)size);
                if (creation.getInitializer() != null) expression(creation.getInitializer());
                return Value.OWNED_ARRAY;
            }
            if (e instanceof ArrayInitializer init) {
                for (Object element : init.expressions()) primitive((Expression)element);
                return Value.OWNED_ARRAY;
            }
            if (e instanceof MethodInvocation call) return call(call);
            if (e instanceof ClassInstanceCreation creation) {
                check(creation.getExpression() == null && creation.getAnonymousClassDeclaration() == null
                        && (!site.normalized || JavaSourceTypeNames.closedType(creation.getType(), site.work::charge)), "constructor dispatch");
                var args = arguments(creation.arguments());
                if (big(creation.resolveTypeBinding())) {
                    check(args.equals(List.of(Value.STRING, Value.PRIMITIVE)) || args.equals(List.of(Value.STRING)), "integer constructor");
                    return Value.BIG;
                }
                if (site.normalized) site.checkSourceMethod(creation.resolveConstructorBinding());
                return invoke(creation.resolveConstructorBinding(), args);
            }
            if (e instanceof InfixExpression binary) {
                var operands = new ArrayList<Value>();
                operands.add(expression(binary.getLeftOperand())); operands.add(expression(binary.getRightOperand()));
                for (Object operand : binary.extendedOperands()) operands.add(expression((Expression)operand));
                if (operands.stream().allMatch(v -> v == Value.PRIMITIVE)) return Value.PRIMITIVE;
                if (operands.stream().allMatch(v -> v == Value.STRING) && binary.resolveConstantExpressionValue() instanceof String) return Value.STRING;
                check(operands.size() == 2 && operands.contains(Value.NIL) && (binary.getOperator() == InfixExpression.Operator.EQUALS
                        || binary.getOperator() == InfixExpression.Operator.NOT_EQUALS), "reference observation");
                return Value.PRIMITIVE;
            }
            if (e instanceof CastExpression cast) {
                check(cast.getType().isPrimitiveType(), "reference cast"); primitive(cast.getExpression()); return Value.PRIMITIVE;
            }
            if (e instanceof ConditionalExpression conditional) {
                primitive(conditional.getExpression());
                Value yes = expression(conditional.getThenExpression());
                check(yes == expression(conditional.getElseExpression()), "conditional ownership"); return yes;
            }
            if (e instanceof PrefixExpression prefix) {
                primitive(prefix.getOperand());
                if (prefix.getOperator() == PrefixExpression.Operator.INCREMENT || prefix.getOperator() == PrefixExpression.Operator.DECREMENT)
                    write(prefix.getOperand(), Value.PRIMITIVE);
                return Value.PRIMITIVE;
            }
            if (e instanceof PostfixExpression post) {
                primitive(post.getOperand()); return write(post.getOperand(), Value.PRIMITIVE);
            }
            if (e instanceof Assignment assignment) {
                Value value = expression(assignment.getRightHandSide());
                check(assignment.getOperator() == Assignment.Operator.ASSIGN || value == Value.PRIMITIVE, "reference update");
                return write(assignment.getLeftHandSide(), value);
            }
            if (e instanceof VariableDeclarationExpression vars) { declare(vars.fragments()); return Value.VOID; }
            throw unsupported("expression " + (e == null ? "null" : e.getClass().getSimpleName()));
        }

        private Value read(Name name) {
            var binding = site.checkedVariable(name);
            var value = locals.get(key(binding));
            if (value != null) return value;
            check(binding.isField() && Modifier.isStatic(binding.getModifiers()) && Modifier.isFinal(binding.getModifiers()), "nonlocal mutable read");
            if (binding.getType().isPrimitive() && primitiveConstant(name, binding, 0)) return Value.PRIMITIVE;
            if (big(binding.getDeclaringClass())) {
                site.constant(binding, 0);
                return Value.BIG;
            }
            initialize(binding.getDeclaringClass());
            if (binding.getType().isPrimitive()) return Value.PRIMITIVE;
            if (big(binding.getType())) { site.constant(binding, 0); return Value.BIG; }
            check(primitiveArray(binding.getType()) && Modifier.isPrivate(binding.getModifiers()), "unverified array field");
            return Value.READ_ARRAY;
        }
        private void array(Expression expression) {
            Value kind = expression(expression);
            check(kind == Value.OWNED_ARRAY || kind == Value.READ_ARRAY, "array operand");
        }
        private Value write(Expression target, Value value) {
            if (target instanceof SimpleName name) {
                check(locals.get(key(name.resolveBinding())) == value, "nonlocal write or ownership change");
            } else if (target instanceof ArrayAccess access) {
                check(expression(access.getArray()) == Value.OWNED_ARRAY && value == Value.PRIMITIVE, "write to shared storage");
                primitive(access.getIndex());
            } else if (target instanceof FieldAccess field && constructor != null && field.getExpression() instanceof ThisExpression self
                    && self.getQualifier() == null) {
                var binding = field.resolveFieldBinding();
                check(binding != null && !Modifier.isStatic(binding.getModifiers()) && Modifier.isFinal(binding.getModifiers())
                        && constructor.isEqualTo(binding.getDeclaringClass()) && (value == Value.OWNED_ARRAY || value == Value.PRIMITIVE),
                        "product initialization");
            } else throw unsupported("nonlocal write");
            return value;
        }
        private List<Value> arguments(List<?> arguments) {
            var result = new ArrayList<Value>();
            for (Object arg : arguments) result.add(expression((Expression)arg));
            return result;
        }
        private Value call(MethodInvocation call) {
            var method = call.resolveMethodBinding();
            check(method != null && !method.isRecovered(), "unresolved call");
            if (Modifier.isStatic(method.getModifiers())) {
                check(site.checkedStaticCall(call), "effectful qualifier");
                var args = arguments(call.arguments());
                if (method.getDeclaringClass().isFromSource()) return invoke(method, args);
                String name = method.getDeclaringClass().getQualifiedName() + "." + method.getName();
                if (jdk(call, "valueOf", "long") && args.equals(List.of(Value.PRIMITIVE))) return Value.BIG;
                if (name.equals("java.lang.System.arraycopy") && args.size() == 5 && args.get(2) == Value.OWNED_ARRAY
                        && (args.get(0) == Value.OWNED_ARRAY || args.get(0) == Value.READ_ARRAY)
                        && args.get(1) == Value.PRIMITIVE && args.get(3) == Value.PRIMITIVE && args.get(4) == Value.PRIMITIVE) return Value.VOID;
                if (name.equals("java.util.Arrays.fill") && args.equals(List.of(Value.OWNED_ARRAY, Value.PRIMITIVE))
                        && primitiveArray(method.getParameterTypes()[0])) return Value.VOID;
            } else {
                check(expression(call.getExpression()) == Value.BIG, "unverified receiver");
                var args = arguments(call.arguments());
                if (jdk(call, "toByteArray")) return Value.OWNED_ARRAY;
                if (jdk(call, "mod", BIG) && args.equals(List.of(Value.BIG))
                        || jdk(call, "shiftLeft", "int") && args.equals(List.of(Value.PRIMITIVE))
                        || jdk(call, "shiftRight", "int") && args.equals(List.of(Value.PRIMITIVE))) return Value.BIG;
                if (jdk(call, "intValue") || jdk(call, "longValue") || jdk(call, "longValueExact") || jdk(call, "intValueExact")
                        || jdk(call, "signum") || jdk(call, "bitLength")) return Value.PRIMITIVE;
            }
            throw unsupported("call " + method.getName());
        }
    }
    private static boolean primitiveArray(ITypeBinding type) {
        return type != null && type.isArray() && type.getComponentType().isPrimitive();
    }
    private static void check(boolean condition, String reason) { if (!condition) throw unsupported(reason); }
    private static IllegalArgumentException unsupported(String reason) { return new IllegalArgumentException("Unproved factory effect: " + reason); }
}
