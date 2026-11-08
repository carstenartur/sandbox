/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.eclipse.jdt.core.dom.*;

/** Binds one implementation of byte-field arithmetic by its source, independent of names. */
final class JavaByteFieldContract {
    private static final String DIVISION = "return multiply(x, power(y, (byte) 254) & 255);"; //$NON-NLS-1$
    private static final String MULTIPLICATION = """
            int result = 0;
            for (int i = 0; i < 8; i++) {
                result ^= x & -(y & 1);
                y >>>= 1;
                int carry = (x >>> 7) & 1;
                x = ((x << 1) ^ (reduction & -carry)) & 255;
            }
            return (byte) result;
            """;
    private static final String POWER = """
            int result = 1;
            for (int i = 0; i < 8; i++) {
                if ((y & (1 << i)) != 0) {
                    result = multiply(result & 255, x & 255);
                }
                x = multiply(x & 255, x & 255);
            }
            return (byte) result;
            """;

    record Contract(ITypeBinding owner, IMethodBinding divide, IMethodBinding multiply, IMethodBinding power,
            Map<String, ASTNode> localDefinitions) { }

    private final Block division = pattern(DIVISION);
    private final Block multiplication = pattern(MULTIPLICATION);
    private final Block power = pattern(POWER);

    Contract resolve(CompilationUnit ast, IVariableBinding receiver, IMethodBinding divide, JavaValueSourceSummary.Work work) {
        if (!stableReceiver(receiver) || !method(divide, "int", "int")) return null; //$NON-NLS-1$ //$NON-NLS-2$
        // Do not infer finality from a caller AST's potentially stale inherited field binding.
        ASTNode receiverDeclaration = ast.findDeclaringNode(receiver.getVariableDeclaration().getKey());
        if (!(receiverDeclaration instanceof VariableDeclarationFragment fragment)
                || !stableReceiver(fragment.resolveBinding()) || !same(receiver, fragment.resolveBinding())) return null;
        ITypeBinding type = divide.getDeclaringClass();
        if (!same(receiver.getType(), type) || !type.isClass() || !type.isFromSource() || type.getQualifiedName().isEmpty()) return null;
        MethodDeclaration divisionSource = freshMethod(ast, divide, "int", "int"); //$NON-NLS-1$ //$NON-NLS-2$
        Match divided = methodBody(divisionSource, division, work);
        if (divided == null || !(divided.get("multiply") instanceof IMethodBinding multiply) //$NON-NLS-1$
                || !(divided.get("power") instanceof IMethodBinding exponentiation) //$NON-NLS-1$
                || !method(multiply, "int", "int") || !method(exponentiation, "int", "byte") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                || !same(type, multiply.getDeclaringClass()) || !same(type, exponentiation.getDeclaringClass())) return null;
        MethodDeclaration powerSource = freshMethod(ast, exponentiation, "int", "byte"); //$NON-NLS-1$ //$NON-NLS-2$
        Match powered = methodBody(powerSource, power, work);
        if (powered == null || !same(multiply, powered.get("multiply"))) return null; //$NON-NLS-1$
        MethodDeclaration multiplicationSource = freshMethod(ast, multiply, "int", "int"); //$NON-NLS-1$ //$NON-NLS-2$
        Match multiplied = methodBody(multiplicationSource, multiplication, work);
        if (multiplied == null || !(multiplied.get("reduction") instanceof IVariableBinding reduction) //$NON-NLS-1$
                || !same(type, reduction.getDeclaringClass()) || !reduction.isField() || !Modifier.isFinal(reduction.getModifiers())
                || !primitive(reduction.getType(), "int") || !(multiplicationSource.getParent() instanceof TypeDeclaration owner)) return null; //$NON-NLS-1$
        var definitions = new LinkedHashMap<String, ASTNode>();
        if (!reduction(owner, reduction, definitions, work)) return null;
        definitions.put(divide.getKey(), divisionSource);
        definitions.put(multiply.getKey(), multiplicationSource);
        definitions.put(exponentiation.getKey(), powerSource);
        definitions.put(receiver.getKey(), receiverDeclaration);
        definitions.entrySet().removeIf(entry -> entry.getValue().getRoot() != ast);
        return new Contract(type, divide, multiply, exponentiation, Map.copyOf(definitions));
    }

    static boolean stableReceiver(IVariableBinding variable) {
        return valid(variable) && variable.isField() && Modifier.isFinal(variable.getModifiers())
                && !Modifier.isStatic(variable.getModifiers());
    }

    private static MethodDeclaration freshMethod(CompilationUnit ast, IMethodBinding binding, String first, String second) {
        MethodDeclaration declaration = JavaSourceContext.method(ast, binding);
        IMethodBinding fresh = declaration == null ? null : declaration.resolveBinding();
        return same(binding, fresh) && same(binding.getDeclaringClass(), fresh.getDeclaringClass())
                && method(fresh, first, second) ? declaration : null;
    }

    private static boolean method(IMethodBinding binding, String first, String second) {
        if (!valid(binding) || binding.isConstructor() || binding.isVarargs() || binding.isGenericMethod()
                || binding.getParameterTypes().length != 2 || !primitive(binding.getReturnType(), "byte") //$NON-NLS-1$
                || binding.getExceptionTypes().length != 0) return false;
        int modifiers = binding.getModifiers();
        return !Modifier.isStatic(modifiers) && !Modifier.isSynchronized(modifiers)
                && !Modifier.isNative(modifiers) && !Modifier.isAbstract(modifiers)
                && primitive(binding.getParameterTypes()[0], first) && primitive(binding.getParameterTypes()[1], second);
    }

    private static Match methodBody(MethodDeclaration declaration, Block template, JavaValueSourceSummary.Work work) {
        if (declaration == null || declaration.getBody() == null || declaration.parameters().size() != 2) return null;
        var match = new Match(work);
        var first = (SingleVariableDeclaration) declaration.parameters().get(0);
        var second = (SingleVariableDeclaration) declaration.parameters().get(1);
        return match.bind("x", first.resolveBinding()) && match.bind("y", second.resolveBinding()) //$NON-NLS-1$ //$NON-NLS-2$
                && declaration.getBody().subtreeMatch(match, template) ? match : null;
    }

    private static boolean reduction(TypeDeclaration owner, IVariableBinding binding, Map<String, ASTNode> definitions,
            JavaValueSourceSummary.Work work) {
        if (!(owner.getRoot() instanceof CompilationUnit unit)
                || !(unit.findDeclaringNode(binding.getKey()) instanceof VariableDeclarationFragment declaration)) return false;
        definitions.put(binding.getKey(), declaration);
        if (declaration.getInitializer() instanceof NumberLiteral literal
                && knownPolynomial(literal.resolveConstantExpressionValue())) return true;
        // A blank final has no usable value on an escaped, partially initialized instance.
        // Admit a complete direct-to-Object constructor with no initializer calls or writes other than this selection.
        ITypeBinding ownerBinding = owner.resolveBinding();
        if (!valid(ownerBinding)) return false;
        ITypeBinding superclass = ownerBinding.getSuperclass();
        if (declaration.getInitializer() != null || Modifier.isStatic(binding.getModifiers()) || !valid(superclass)
                || !"java.lang.Object".equals(superclass.getQualifiedName()) || owner.getFields().length != 1 //$NON-NLS-1$
                || owner.getFields()[0].fragments().size() != 1 || !usesOrdinaryConstruction(ownerBinding, work)) return false;
        work.charge(owner.bodyDeclarations().size());
        for (Object member : owner.bodyDeclarations()) if (member instanceof Initializer) return false;
        List<MethodDeclaration> constructors = Arrays.stream(owner.getMethods()).filter(MethodDeclaration::isConstructor).toList();
        if (constructors.size() != 1) return false;
        MethodDeclaration ctor = constructors.get(0);
        if (!valid(ctor.resolveBinding()) || ctor.parameters().size() != 1 || ctor.getBody() == null
                || ctor.getBody().statements().size() != 1
                || !(ctor.getBody().statements().getFirst() instanceof SwitchStatement choice)) return false;
        var parameter = ((SingleVariableDeclaration) ctor.parameters().getFirst()).resolveBinding();
        if (!valid(parameter) || !parameter.getType().isEnum() || !same(variable(choice.getExpression()), parameter)) return false;
        List<?> statements = choice.statements();
        // Two constant cases and a throwing default: the enum's names carry no mathematical meaning.
        if (statements.size() != 8 || !constructorCase(statements, 0, binding, parameter.getType(), work)
                || !constructorCase(statements, 3, binding, parameter.getType(), work)
                || !(statements.get(6) instanceof SwitchCase last) || !last.isDefault()
                || !(statements.get(7) instanceof ThrowStatement thrown)
                || !(thrown.getExpression() instanceof ClassInstanceCreation exception) || exception.getExpression() != null
                || exception.getAnonymousClassDeclaration() != null || !valid(exception.resolveTypeBinding())
                || !"java.lang.IllegalArgumentException".equals(exception.resolveTypeBinding().getQualifiedName()) //$NON-NLS-1$
                || exception.arguments().size() != 1 || !(exception.arguments().getFirst() instanceof StringLiteral)) return false;
        definitions.put(ctor.resolveBinding().getKey(), ctor);
        return true;
    }

    private static boolean constructorCase(List<?> statements, int offset, IVariableBinding binding, ITypeBinding selector,
            JavaValueSourceSummary.Work work) {
        return statements.get(offset) instanceof SwitchCase branch && !branch.isDefault() && enumLabels(branch, selector, work)
                && statements.get(offset + 1) instanceof ExpressionStatement line && line.getExpression() instanceof Assignment assigned
                && assigned.getOperator() == Assignment.Operator.ASSIGN && same(variable(assigned.getLeftHandSide()), binding)
                && assigned.getRightHandSide() instanceof NumberLiteral literal && knownPolynomial(literal.resolveConstantExpressionValue())
                && statements.get(offset + 2) instanceof BreakStatement stop && stop.getLabel() == null;
    }

    @SuppressWarnings("deprecation") // The single-label API is required for pre-JLS14 ASTs.
    private static boolean enumLabels(SwitchCase branch, ITypeBinding selector, JavaValueSourceSummary.Work work) {
        if (branch.getAST().apiLevel() < AST.JLS14) return enumConstant(branch.getExpression(), selector);
        // A guarded pattern can call code and publish this before the reduction assignment.
        List<?> labels = branch.expressions();
        work.charge(labels.size());
        return !labels.isEmpty() && labels.stream().allMatch(label -> enumConstant(label, selector));
    }

    private static boolean enumConstant(Object label, ITypeBinding selector) {
        return label instanceof Name name && name.resolveBinding() instanceof IVariableBinding constant
                && valid(constant) && constant.isEnumConstant() && same(constant.getDeclaringClass(), selector);
    }

    /** The caller first proves that the class extends Object directly. */
    static boolean usesOrdinaryConstruction(ITypeBinding owner, JavaValueSourceSummary.Work work) {
        var types = new ArrayList<ITypeBinding>();
        var seen = new HashSet<String>();
        types.add(owner);
        for (int i = 0; i < types.size(); i++) {
            work.charge(1);
            var type = types.get(i);
            // Deserialization can restore final instance fields without a constructor freeze.
            if (!valid(type) || "java.io.Serializable".equals(type.getQualifiedName())) return false; //$NON-NLS-1$
            if (!seen.add(type.getKey())) continue;
            for (var parent : type.getInterfaces()) types.add(parent);
        }
        return true;
    }

    private static boolean knownPolynomial(Object value) {
        return value instanceof Integer number && (number == 0x11b || number == 0x11d);
    }

    static Block pattern(String source) {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_STATEMENTS);
        parser.setSource(source.toCharArray());
        return (Block) parser.createAST(null);
    }

    static boolean valid(IBinding binding) {
        return binding != null && !binding.isRecovered() && binding.getKey() != null;
    }

    static boolean same(IBinding first, IBinding second) {
        return valid(first) && valid(second) && first.getKey().equals(second.getKey());
    }

    static boolean primitive(ITypeBinding binding, String name) {
        return valid(binding) && binding.isPrimitive() && binding.getName().equals(name);
    }

    static IVariableBinding variable(Expression expression) {
        while (expression instanceof ParenthesizedExpression wrapped) expression = wrapped.getExpression();
        return expression instanceof SimpleName name && name.resolveBinding() instanceof IVariableBinding binding ? binding : null;
    }

    /** Match syntax and all occurrences of each placeholder to one real binding. */
    static final class Match extends ASTMatcher {
        private final Map<String, IBinding> bindings = new LinkedHashMap<>();
        private final JavaValueSourceSummary.Work work;

        Match(JavaValueSourceSummary.Work work) { this.work = work; }
        IBinding get(String key) { return bindings.get(key); }

        boolean bind(String key, IBinding binding) {
            work.charge(1);
            return valid(binding) && same(binding, bindings.computeIfAbsent(key, ignored -> binding));
        }

        @Override public boolean match(SimpleName node, Object other) {
            if (!(other instanceof SimpleName name)) return false;
            if ("length".equals(name.getIdentifier()) && name.getParent() instanceof QualifiedName arrayLength //$NON-NLS-1$
                    && arrayLength.getName() == name) {
                return "length".equals(node.getIdentifier()) && node.getParent() instanceof QualifiedName candidate //$NON-NLS-1$
                        && candidate.getQualifier().resolveTypeBinding() != null && candidate.getQualifier().resolveTypeBinding().isArray();
            }
            return bind(name.getIdentifier(), node.resolveBinding());
        }

        @Override public boolean match(NumberLiteral node, Object other) {
            return other instanceof NumberLiteral expected && node.resolveConstantExpressionValue() instanceof Integer value
                    && Objects.equals(value, decode(expected.getToken()));
        }

        @Override public boolean match(PrefixExpression node, Object other) {
            if (node.getOperator() == PrefixExpression.Operator.INCREMENT && other instanceof PostfixExpression expected
                    && expected.getOperator() == PostfixExpression.Operator.INCREMENT && expected.getParent() instanceof ForStatement loop
                    && loop.updaters().contains(expected)) return node.getOperand().subtreeMatch(this, expected.getOperand());
            return super.match(node, other);
        }

        private static Integer decode(String token) {
            try { return Integer.decode(token); }
            catch (NumberFormatException unsupported) { return null; }
        }
    }
}
