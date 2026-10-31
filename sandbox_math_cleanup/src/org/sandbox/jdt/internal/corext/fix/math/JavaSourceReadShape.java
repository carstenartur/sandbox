/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.math;

import de.regelsuche.sdk.optimization.NumericKind;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.ThisExpression;

/**
 * Source-shape admission only. Every admitted occurrence still executes once,
 * in order, even if its scalar value becomes dead. Neither final fields nor
 * virtual buffer calls are assumed pure, equal, non-null or in bounds.
 */
final class JavaSourceReadShape {
    private static final Set<String> INTEGRAL = Set.of("byte", "short", "char", "int", "long"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
    private static final Set<String> BUFFERS = Set.of("java.nio.ByteBuffer", "java.nio.ShortBuffer", //$NON-NLS-1$ //$NON-NLS-2$
            "java.nio.CharBuffer", "java.nio.IntBuffer", "java.nio.LongBuffer"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    private static final Set<String> EXCLUDED_NUMERIC_RECEIVERS = Set.of("java.lang.Number", "java.lang.Float", //$NON-NLS-1$ //$NON-NLS-2$
            "java.lang.Double", "java.math.BigInteger", "java.math.BigDecimal"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    private static final Set<InfixExpression.Operator> TOTAL_INDEX_OPERATORS = Set.of(
            InfixExpression.Operator.PLUS, InfixExpression.Operator.MINUS, InfixExpression.Operator.TIMES,
            InfixExpression.Operator.AND, InfixExpression.Operator.OR, InfixExpression.Operator.XOR,
            InfixExpression.Operator.LEFT_SHIFT, InfixExpression.Operator.RIGHT_SHIFT_SIGNED,
            InfixExpression.Operator.RIGHT_SHIFT_UNSIGNED);

    private JavaSourceReadShape() { }

    static boolean additionalRead(Expression expression) {
        return bufferMethod(expression) != null || numericField(expression) != null || unboxingRead(expression);
    }

    static boolean read(Expression expression) {
        return expression instanceof ArrayAccess || additionalRead(expression) || opaqueAccessor(expression);
    }

    static boolean supported(Expression expression, Set<String> regionalLocals) {
        if (!JavaArrayReadInputs.integral(kind(expression).orElse(null))) return false;
        expression = unparenthesized(expression);
        if (expression instanceof ArrayAccess array) {
            return arrayReceiver(array.getArray(), regionalLocals, 0)
                    && stableIndex(array.getIndex(), regionalLocals, 0);
        }
        if (bufferMethod(expression) != null && expression instanceof MethodInvocation call) {
            return localReceiver(call.getExpression(), regionalLocals)
                    && stableIndex((Expression) call.arguments().getFirst(), regionalLocals, 0);
        }
        if (opaqueAccessor(expression) && expression instanceof MethodInvocation call)
            return localReceiver(call.getExpression(), regionalLocals);
        if (expression instanceof SimpleName name && name.resolveBinding() instanceof IVariableBinding local && !local.isField())
            return localReceiver(expression, regionalLocals) && wrapper(local.getType()).isPresent();
        IVariableBinding field = field(expression);
        if (field == null || field.getConstantValue() != null) return false;
        if (expression instanceof SimpleName) return true; // Original lexical field lookup is retained verbatim.
        Expression receiver = expression instanceof FieldAccess access ? access.getExpression()
                : ((QualifiedName) expression).getQualifier();
        receiver = unparenthesized(receiver);
        if (receiver instanceof Name name && name.resolveBinding() instanceof ITypeBinding)
            return Modifier.isStatic(field.getModifiers());
        return receiver(receiver, regionalLocals, 0);
    }

    /** Original admission for the first search pass; richer reads receive only its remainder. */
    static boolean established(Expression expression, Set<String> regionalLocals) {
        if (!JavaArrayReadInputs.integral(JavaOperationRegistry.kind(expression.resolveTypeBinding()).orElse(null))) return false;
        if (expression instanceof ArrayAccess array) {
            Expression receiver=unparenthesized(array.getArray());
            IVariableBinding binding=receiver instanceof Name name && name.resolveBinding() instanceof IVariableBinding variable
                    ? variable : receiver instanceof FieldAccess access ? access.resolveFieldBinding() : null;
            if (binding==null || binding.isRecovered() || binding.getType()==null || binding.getType().isRecovered()
                    || !binding.getType().isArray() || binding.getType().getDimensions()!=1 || !integral(binding.getType().getElementType())
                    || !stableIndex(array.getIndex(),regionalLocals,0)) return false;
            if (!binding.isField()) return receiver instanceof SimpleName && !regionalLocals.contains(binding.getKey());
            if (Modifier.isVolatile(binding.getModifiers())) return false;
            if (receiver instanceof SimpleName) return true;
            if (receiver instanceof FieldAccess access)
                return unparenthesized(access.getExpression()) instanceof ThisExpression self && self.getQualifier()==null;
            return receiver instanceof QualifiedName name && name.getQualifier().resolveBinding() instanceof ITypeBinding
                    && Modifier.isStatic(binding.getModifiers());
        }
        if (bufferMethod(expression)!=null) return supported(expression,regionalLocals);
        IVariableBinding field=numericField(expression);
        if (field==null || !Modifier.isFinal(field.getModifiers())) return false;
        if (expression instanceof SimpleName) return true;
        Expression receiver=unparenthesized(expression instanceof FieldAccess access ? access.getExpression() : ((QualifiedName)expression).getQualifier());
        return receiver instanceof Name name && name.resolveBinding() instanceof ITypeBinding && Modifier.isStatic(field.getModifiers())
                || receiver instanceof ThisExpression self && self.getQualifier()==null || localReceiver(receiver,regionalLocals);
    }

    static boolean hasExtendedReads(org.eclipse.jdt.core.dom.CompilationUnit unit, JavaComputationRegion region) {
        return region.arrayReads().stream().anyMatch(read -> {
            ASTNode node=org.eclipse.jdt.core.dom.NodeFinder.perform(unit,read.start(),read.length());
            return node instanceof Expression expression && !established(expression,Set.of());
        });
    }

    /** The entire outer occurrence owns all inner dereferences and checks. */
    private static boolean arrayReceiver(Expression expression, Set<String> regionalLocals, int depth) {
        ITypeBinding type = expression.resolveTypeBinding();
        return type != null && !type.isRecovered() && type.isArray()
                && (integral(type.getElementType()) || wrapper(type.getElementType()).isPresent())
                && receiver(expression, regionalLocals, depth + 1);
    }

    /** Reread each nonvolatile link verbatim; never assert receiver identity or purity. */
    private static boolean receiver(Expression expression, Set<String> regionalLocals, int depth) {
        if (depth > 64) return false;
        expression = unparenthesized(expression);
        if (expression instanceof ThisExpression self) return self.getQualifier() == null;
        if (expression instanceof ArrayAccess array)
            return arrayReceiver(array.getArray(), regionalLocals, depth + 1)
                    && stableIndex(array.getIndex(), regionalLocals, depth + 1);
        if (expression instanceof SimpleName name && name.resolveBinding() instanceof IVariableBinding binding)
            return !binding.isRecovered() && !regionalLocals.contains(binding.getKey())
                    && (!binding.isField() || !Modifier.isVolatile(binding.getModifiers()));
        IVariableBinding binding = field(expression);
        if (binding == null) return false;
        Expression qualifier = expression instanceof FieldAccess access ? access.getExpression()
                : ((QualifiedName)expression).getQualifier();
        if (qualifier instanceof Name name && name.resolveBinding() instanceof ITypeBinding)
            return Modifier.isStatic(binding.getModifiers());
        return receiver(qualifier, regionalLocals, depth + 1);
    }

    /** Primitive result before numeric promotion, including only a resolved Java unboxing conversion. */
    static Optional<NumericKind> kind(Expression expression) {
        ITypeBinding type = expression.resolveTypeBinding();
        var primitive = JavaOperationRegistry.kind(type);
        return primitive.isPresent() ? primitive : expression.resolveUnboxing() ? wrapper(type) : Optional.empty();
    }

    private static Optional<NumericKind> wrapper(ITypeBinding type) {
        if (type == null || type.isRecovered()) return Optional.empty();
        return switch (type.getQualifiedName()) {
            case "java.lang.Byte" -> Optional.of(NumericKind.BYTE); //$NON-NLS-1$
            case "java.lang.Short" -> Optional.of(NumericKind.SHORT); //$NON-NLS-1$
            case "java.lang.Character" -> Optional.of(NumericKind.CHAR); //$NON-NLS-1$
            case "java.lang.Integer" -> Optional.of(NumericKind.INT); //$NON-NLS-1$
            case "java.lang.Long" -> Optional.of(NumericKind.LONG); //$NON-NLS-1$
            default -> Optional.empty();
        };
    }

    private static boolean unboxingRead(Expression expression) {
        return expression.resolveUnboxing() && wrapper(expression.resolveTypeBinding()).isPresent();
    }

    /** This is a late fallback: existing scalar/SAM/known-operation summaries take precedence. */
    static boolean opaqueAccessor(Expression expression) {
        if (!(expression instanceof MethodInvocation call) || !call.arguments().isEmpty() || !call.typeArguments().isEmpty()
                || !JavaArrayReadInputs.integral(kind(expression).orElse(null))) return false;
        IMethodBinding method = call.resolveMethodBinding();
        if (method == null || method.isRecovered() || method.isConstructor() || Modifier.isStatic(method.getModifiers())
                || method.isVarargs() || method.isGenericMethod() || method.isParameterizedMethod()
                || method.getDeclaringClass() == null || method.getDeclaringClass().isRecovered()
                || method.getParameterTypes().length != 0 || call.getExpression() == null) return false;
        // Relative buffer reads keep their established boundary; the buffer model
        // admits only the explicitly indexed get signature above.
        if (BUFFERS.contains(method.getDeclaringClass().getErasure().getQualifiedName())) return false;
        ITypeBinding receiverType = call.getExpression().resolveTypeBinding();
        if (receiverType == null || receiverType.isRecovered() || EXCLUDED_NUMERIC_RECEIVERS.contains(receiverType.getErasure().getQualifiedName())
                || EXCLUDED_NUMERIC_RECEIVERS.contains(method.getDeclaringClass().getErasure().getQualifiedName())) return false;
        return localReceiver(call.getExpression(), Set.of());
    }

    private static IVariableBinding field(Expression expression) {
        IVariableBinding binding = expression instanceof Name name && name.resolveBinding() instanceof IVariableBinding variable
                ? variable : expression instanceof FieldAccess access ? access.resolveFieldBinding() : null;
        return binding != null && !binding.isRecovered() && binding.isField() && !Modifier.isVolatile(binding.getModifiers()) ? binding : null;
    }

    private static IVariableBinding numericField(Expression expression) {
        IVariableBinding binding = field(expression);
        return binding != null && binding.getConstantValue() == null && integral(binding.getType()) ? binding : null;
    }

    /** Produced locals and throwing prefixes may not move after a following source read. */
    static boolean requiresBoundary(Statement statement, Set<String> regionalLocals, boolean totalPrefix) {
        return requiresBoundary(statement,regionalLocals,totalPrefix,true);
    }

    static boolean requiresBoundary(Statement statement, Set<String> regionalLocals, boolean totalPrefix, boolean extendedReads) {
        boolean[] boundary = {false};
        statement.accept(new ASTVisitor() {
            @Override public boolean preVisit2(ASTNode node) {
                if (boundary[0]) return false;
                if (!(node instanceof Expression expression)) return true;
                IVariableBinding field=numericField(expression);
                boolean sourceRead=extendedReads ? read(expression) : expression instanceof ArrayAccess
                        || bufferMethod(expression)!=null || field!=null && Modifier.isFinal(field.getModifiers());
                if (!sourceRead) return true;
                if (!totalPrefix) { boundary[0] = true; return false; }
                expression.accept(new ASTVisitor() {
                    @Override public boolean visit(SimpleName name) {
                        if (name.resolveBinding() instanceof IVariableBinding binding
                                && regionalLocals.contains(binding.getKey())) boundary[0] = true;
                        return !boundary[0];
                    }
                });
                return false;
            }
        });
        return boundary[0];
    }

    private static IMethodBinding bufferMethod(Expression expression) {
        if (!(expression instanceof MethodInvocation call) || call.arguments().size() != 1
                || !call.typeArguments().isEmpty() || !"get".equals(call.getName().getIdentifier())) return null; //$NON-NLS-1$
        IMethodBinding method = call.resolveMethodBinding();
        if (method == null || method.isRecovered() || Modifier.isStatic(method.getModifiers())
                || method.isVarargs() || method.getDeclaringClass() == null
                || !BUFFERS.contains(method.getDeclaringClass().getErasure().getQualifiedName())
                || method.getParameterTypes().length != 1 || !integral(method.getReturnType())
                || !"int".equals(method.getParameterTypes()[0].getQualifiedName())) return null; //$NON-NLS-1$
        return method;
    }

    private static boolean localReceiver(Expression receiver, Set<String> regionalLocals) {
        receiver = unparenthesized(receiver);
        return receiver instanceof SimpleName name && name.resolveBinding() instanceof IVariableBinding binding
                && !binding.isRecovered() && !binding.isField() && !regionalLocals.contains(binding.getKey());
    }

    private static Expression unparenthesized(Expression expression) {
        while (expression instanceof ParenthesizedExpression parenthesized) expression = parenthesized.getExpression();
        return expression;
    }

    private static boolean integral(ITypeBinding binding) {
        return binding != null && !binding.isRecovered() && binding.isPrimitive() && INTEGRAL.contains(binding.getName());
    }

    private static boolean stableIndex(Expression index, Set<String> regionalLocals, int depth) {
        if (depth > 64 || !integral(index.resolveTypeBinding())) return false;
        if (index instanceof NumberLiteral || index instanceof CharacterLiteral) return true;
        if (index instanceof Name name && name.resolveBinding() instanceof IVariableBinding binding) {
            if (binding.isRecovered() || regionalLocals.contains(binding.getKey())) return false;
            if (!binding.isField()) return name instanceof SimpleName;
            // Constant variables cannot trigger class initialization. An object-qualified
            // constant is deliberately excluded: do not silently omit its receiver evaluation.
            return binding.getConstantValue() != null && (name instanceof SimpleName
                    || name instanceof QualifiedName qualified && qualified.getQualifier().resolveBinding() instanceof ITypeBinding);
        }
        if (index instanceof ParenthesizedExpression parentheses)
            return stableIndex(parentheses.getExpression(), regionalLocals, depth + 1);
        if (index instanceof CastExpression cast)
            return stableIndex(cast.getExpression(), regionalLocals, depth + 1);
        if (index instanceof PrefixExpression prefix)
            return (prefix.getOperator() == PrefixExpression.Operator.PLUS || prefix.getOperator() == PrefixExpression.Operator.MINUS
                    || prefix.getOperator() == PrefixExpression.Operator.COMPLEMENT)
                    && stableIndex(prefix.getOperand(), regionalLocals, depth + 1);
        if (index instanceof InfixExpression infix) {
            if (!TOTAL_INDEX_OPERATORS.contains(infix.getOperator())
                    || !stableIndex(infix.getLeftOperand(), regionalLocals, depth + 1)
                    || !stableIndex(infix.getRightOperand(), regionalLocals, depth + 1)) return false;
            for (Object operand : infix.extendedOperands())
                if (!stableIndex((Expression) operand, regionalLocals, depth + 1)) return false;
            return true;
        }
        return false;
    }
}
