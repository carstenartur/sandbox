/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.core.dom.*;
import org.sandbox.functional.core.model.StateIteration;
import org.sandbox.functional.core.model.UlrExpression;
import org.sandbox.functional.core.model.UlrExpression.Type;

/** Checks the descriptor against original source, then independently replays emitted Java. */
final class JavaTailRecursionReplay {
    private JavaTailRecursionReplay() { }

    static boolean verify(MethodDeclaration original, MethodDeclaration target, StateIteration model, Runnable checkpoint) {
        checkpoint.run();
        if (!sameSignature(original, target) || original.getBody() == null || target.getBody() == null
                || model.resultType() != JavaPrimitiveStateExpressions.type(original.resolveBinding().getReturnType())
                || model.updateMode() != StateIteration.UpdateMode.SIMULTANEOUS
                || model.evaluationOrder() != StateIteration.EvaluationOrder.LEFT_TO_RIGHT) return false;
        int count = model.states().size();
        var body = target.getBody().statements();
        if (body.size() != count + 2 || original.parameters().size() != count || target.parameters().size() != count) return false;
        var bindings = new HashMap<String, String>();
        var stateKeys = new ArrayList<String>(); var nextKeys = new ArrayList<String>();
        boolean headerInitialized=false;
        for (int i = 0; i < count; i++) {
            checkpoint.run();
            var declared = model.states().get(i);
            var originalParameter = ((SingleVariableDeclaration) original.parameters().get(i)).resolveBinding();
            if (!declared.bindingKey().equals(key(originalParameter))
                    || declared.type() != JavaPrimitiveStateExpressions.type(originalParameter.getType())
                    || !declared.bindingKey().equals(model.updates().get(i).targetBinding())) return false;
            if (!(body.get(i) instanceof VariableDeclarationStatement declaration)
                    || declaration.getModifiers() != 0) return false;
            VariableDeclarationFragment fragment = declaration(declaration, declared.type());
            var parameter = ((SingleVariableDeclaration) target.parameters().get(i)).resolveBinding();
            String parameterKey = key(parameter);
            if (fragment == null || parameterKey == null) return false;
            String state = key(fragment.resolveBinding());
            if (state == null || stateKeys.contains(state)) return false;
            if(fragment.getInitializer()==null) {
                if(!declared.bindingKey().equals(model.termination().counterBinding())
                        || !(body.get(count) instanceof ForStatement loop) || !parameterInitializer(loop,state,parameterKey)) return false;
                headerInitialized=true;
            } else if(!parameterKey.equals(key(fragment.getInitializer()))) return false;
            stateKeys.add(state); bindings.put(declared.bindingKey(), state);
        }
        var sourceBody = original.getBody().statements();
        if (sourceBody.size() != 2 || !(sourceBody.getFirst() instanceof IfStatement exit) || exit.getElseStatement() != null
                || !(sourceBody.get(1) instanceof ReturnStatement tail)
                || !(JavaPrimitiveStateExpressions.unwrap(tail.getExpression()) instanceof MethodInvocation call)
                || call.arguments().size() != count) return false;
        Statement then = exit.getThenStatement();
        if (then instanceof Block block && block.statements().size() == 1) then = (Statement) block.statements().getFirst();
        Set<String> originalBindings = bindings.keySet();
        if (!(then instanceof ReturnStatement base)
                || !matches(model.stopCondition(), exit.getExpression(), originalBindings, checkpoint)
                || !matches(model.resultExpression(), base.getExpression(), originalBindings, checkpoint)) return false;
        for (int i = 0; i < count; i++) {
            if (!matches(model.updates().get(i).expression(), (Expression) call.arguments().get(i), originalBindings, checkpoint)) return false;
        }

        Block block;int counter=-1;
        if (body.get(count) instanceof WhileStatement loop && loop.getBody() instanceof Block loopBody
                && loop.getExpression() instanceof PrefixExpression condition && condition.getOperator()==PrefixExpression.Operator.NOT) {
            if (!expression(exit.getExpression(),condition.getOperand(),bindings,checkpoint,0)) return false;
            block=loopBody;
        } else if (body.get(count) instanceof ForStatement loop && loop.getBody() instanceof Block loopBody) {
            for(int i=0;i<count;i++) if(model.states().get(i).bindingKey().equals(model.termination().counterBinding())) counter=i;
            if(counter<0 || model.termination().decrement()!=1 || model.termination().stopAtOrBelow()!=0
                    || !countdown(exit.getExpression(),model.states().get(counter).bindingKey(),InfixExpression.Operator.LESS_EQUALS)
                    || !countdown(loop.getExpression(),stateKeys.get(counter),InfixExpression.Operator.GREATER)
                    || (!headerInitialized && !counterInitializer(loop,stateKeys.get(counter))) || loop.updaters().size()!=1
                    || !counterUpdate((Expression)call.arguments().get(counter),(Expression)loop.updaters().getFirst(),
                        model.states().get(counter).bindingKey(),stateKeys.get(counter),bindings,checkpoint)) return false;
            // The pure, nonthrowing decrement can be committed after the other states.
            // Their next values still read the previous counter and all previous state.
            block=loopBody;
        } else return false;
        var transfers=new ArrayList<Integer>();
        for(int i=0;i<count;i++) if(i!=counter) transfers.add(i);
        if(block.statements().size()!=2*transfers.size()) return false;
        for (int slot = 0; slot < transfers.size(); slot++) {
            int i=transfers.get(slot);
            checkpoint.run();
            if (!(block.statements().get(slot) instanceof VariableDeclarationStatement declaration)
                    || declaration.getModifiers() != Modifier.FINAL) return false;
            VariableDeclarationFragment fragment = declaration(declaration, model.states().get(i).type());
            if (fragment == null || !expression((Expression) call.arguments().get(i), fragment.getInitializer(), bindings, checkpoint, 0)) return false;
            String next = key(fragment.resolveBinding());
            if (next == null || nextKeys.contains(next) || stateKeys.contains(next)) return false;
            nextKeys.add(next);
        }
        for (int slot = 0; slot < transfers.size(); slot++) {
            int i=transfers.get(slot);
            checkpoint.run();
            if (!(block.statements().get(transfers.size() + slot) instanceof ExpressionStatement statement)
                    || !(statement.getExpression() instanceof Assignment assignment)
                    || assignment.getOperator() != Assignment.Operator.ASSIGN
                    || !stateKeys.get(i).equals(key(assignment.getLeftHandSide()))
                    || !nextKeys.get(slot).equals(key(assignment.getRightHandSide()))) return false;
        }
        return body.get(count + 1) instanceof ReturnStatement result
                && expression(base.getExpression(), result.getExpression(), bindings, checkpoint, 0);
    }

    private record ExpressionPair(UlrExpression expected, UlrExpression actual) { }

    private static boolean matches(UlrExpression expected, Expression original, Set<String> bindings, Runnable checkpoint) {
        var actual = JavaPrimitiveStateExpressions.lower(original, bindings, checkpoint);
        if (actual == null) return false;
        // Record equality would recurse down arbitrarily long left-associated infix chains.
        var pending = new ArrayDeque<ExpressionPair>();
        pending.push(new ExpressionPair(expected, actual));
        while (!pending.isEmpty()) {
            checkpoint.run();
            var pair = pending.pop();
            var observed = pair.actual();
            if (pair.expected().type() != observed.type()) return false;
            switch (pair.expected()) {
                case UlrExpression.Binary binary -> {
                    if (!(observed instanceof UlrExpression.Binary found) || binary.operator() != found.operator()
                            || binary.overflow() != found.overflow()) return false;
                    pending.push(new ExpressionPair(binary.right(), found.right()));
                    pending.push(new ExpressionPair(binary.left(), found.left()));
                }
                case UlrExpression.Unary unary -> {
                    if (!(observed instanceof UlrExpression.Unary found) || unary.operator() != found.operator()
                            || unary.overflow() != found.overflow()) return false;
                    pending.push(new ExpressionPair(unary.operand(), found.operand()));
                }
                default -> { if (!pair.expected().equals(observed)) return false; }
            }
        }
        return true;
    }

    private static boolean counterInitializer(ForStatement loop,String counter) {
        return loop.initializers().isEmpty() || loop.initializers().size()==1
                && loop.initializers().getFirst() instanceof Assignment init && init.getOperator()==Assignment.Operator.ASSIGN
                && counter.equals(key(init.getLeftHandSide())) && counter.equals(key(init.getRightHandSide()));
    }

    private static boolean parameterInitializer(ForStatement loop,String counter,String parameter) {
        return loop.initializers().size()==1 && loop.initializers().getFirst() instanceof Assignment init
                && init.getOperator()==Assignment.Operator.ASSIGN
                && counter.equals(key(init.getLeftHandSide())) && parameter.equals(key(init.getRightHandSide()));
    }

    private static boolean countdown(Expression value,String binding,InfixExpression.Operator operator) {
        value=JavaPrimitiveStateExpressions.unwrap(value);
        return value instanceof InfixExpression comparison && comparison.getOperator()==operator
                && !comparison.hasExtendedOperands() && binding.equals(key(JavaPrimitiveStateExpressions.unwrap(comparison.getLeftOperand())))
                && JavaPrimitiveStateExpressions.literal(comparison.getRightOperand(),0);
    }

    private static boolean counterUpdate(Expression original,Expression emitted,String before,String after,
            Map<String,String> bindings,Runnable checkpoint) {
        checkpoint.run();
        if(emitted instanceof Assignment update && update.getOperator()==Assignment.Operator.ASSIGN
                && after.equals(key(update.getLeftHandSide()))) return expression(original,update.getRightHandSide(),bindings,checkpoint,0);
        original=JavaPrimitiveStateExpressions.unwrap(original);
        if(!(original instanceof InfixExpression step) || step.getOperator()!=InfixExpression.Operator.MINUS || step.hasExtendedOperands()
                || !before.equals(key(JavaPrimitiveStateExpressions.unwrap(step.getLeftOperand())))
                || !JavaPrimitiveStateExpressions.literal(step.getRightOperand(),1)
                || !(emitted instanceof PostfixExpression decrement) || decrement.getOperator()!=PostfixExpression.Operator.DECREMENT
                || !after.equals(key(decrement.getOperand()))) return false;
        var from=original.resolveTypeBinding();var to=emitted.resolveTypeBinding();
        return from!=null && to!=null && !from.isRecovered() && !to.isRecovered() && from.isPrimitive() && from.isEqualTo(to);
    }

    private static boolean sameSignature(MethodDeclaration original, MethodDeclaration target) {
        IMethodBinding before = original.resolveBinding(), after = target.resolveBinding();
        return before != null && after != null && !before.isRecovered() && !after.isRecovered()
                && original.getModifiers() == target.getModifiers()
                && before.getMethodDeclaration().isEqualTo(after.getMethodDeclaration())
                && before.getReturnType().isEqualTo(after.getReturnType());
    }

    private static VariableDeclarationFragment declaration(VariableDeclarationStatement node, Type type) {
        if (JavaPrimitiveStateExpressions.type(node.getType().resolveBinding()) != type
                || node.fragments().size() != 1) return null;
        var fragment = (VariableDeclarationFragment) node.fragments().getFirst();
        return fragment.extraDimensions().isEmpty() ? fragment : null;
    }
    private static String key(IVariableBinding binding) {
        return binding == null || binding.isRecovered() ? null : binding.getVariableDeclaration().getKey();
    }
    private static String key(Expression expression) {
        return expression instanceof SimpleName name && name.resolveBinding() instanceof IVariableBinding variable ? key(variable) : null;
    }
    private static boolean expression(Expression a, Expression b, Map<String, String> names, Runnable checkpoint, int depth) {
        checkpoint.run();
        if (a == null || b == null || depth > 64) return false;
        a = JavaPrimitiveStateExpressions.unwrap(a); b = JavaPrimitiveStateExpressions.unwrap(b);
        ITypeBinding at = a.resolveTypeBinding(), bt = b.resolveTypeBinding();
        if (at == null || bt == null || at.isRecovered() || bt.isRecovered() || !at.isPrimitive() || !bt.isPrimitive()
                || !at.getName().equals(bt.getName())) return false;
        if (a instanceof SimpleName) {
            String target = names.get(key(a)); return target != null && target.equals(key(b));
        }
        if (a instanceof NumberLiteral left && b instanceof NumberLiteral right) return left.getToken().equals(right.getToken());
        if (a instanceof PrefixExpression left && b instanceof PrefixExpression right) return left.getOperator() == right.getOperator()
                && expression(left.getOperand(), right.getOperand(), names, checkpoint, depth + 1);
        if (!(a instanceof InfixExpression left) || !(b instanceof InfixExpression right)
                || left.getOperator() != right.getOperator() || left.extendedOperands().size() != right.extendedOperands().size()) return false;
        if (!expression(left.getLeftOperand(), right.getLeftOperand(), names, checkpoint, depth + 1)
                || !expression(left.getRightOperand(), right.getRightOperand(), names, checkpoint, depth + 1)) return false;
        for (int i = 0; i < left.extendedOperands().size(); i++) {
            if (!expression((Expression) left.extendedOperands().get(i), (Expression) right.extendedOperands().get(i), names, checkpoint, depth + 1)) return false;
        }
        return true;
    }
}
