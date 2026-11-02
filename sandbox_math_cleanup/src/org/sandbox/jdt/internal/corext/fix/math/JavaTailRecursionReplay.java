/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jdt.core.dom.*;
import org.sandbox.functional.core.model.StateIteration;

/** Independent typed structural replay; does not call the source renderer. */
final class JavaTailRecursionReplay {
    private JavaTailRecursionReplay() { }

    static boolean verify(MethodDeclaration original, MethodDeclaration target, StateIteration model, Runnable checkpoint) {
        checkpoint.run();
        if (!sameSignature(original, target) || target.getBody() == null || model.updateMode() != StateIteration.UpdateMode.SIMULTANEOUS
                || model.evaluationOrder() != StateIteration.EvaluationOrder.LEFT_TO_RIGHT) return false;
        int count = model.states().size();
        var body = target.getBody().statements();
        if (body.size() != count + 2 || target.parameters().size() != count) return false;
        var bindings = new HashMap<String, String>();
        var stateKeys = new ArrayList<String>(); var nextKeys = new ArrayList<String>();
        for (int i = 0; i < count; i++) {
            checkpoint.run();
            if (!(body.get(i) instanceof VariableDeclarationStatement declaration)
                    || declaration.getModifiers() != 0) return false;
            VariableDeclarationFragment fragment = declaration(declaration, model.states().get(i).type());
            var parameter = ((SingleVariableDeclaration) target.parameters().get(i)).resolveBinding();
            String parameterKey = key(parameter);
            if (fragment == null || parameterKey == null || !parameterKey.equals(key(fragment.getInitializer()))) return false;
            String state = key(fragment.resolveBinding());
            if (state == null || stateKeys.contains(state)) return false;
            stateKeys.add(state); bindings.put(model.states().get(i).bindingKey(), state);
        }
        var exit = (IfStatement) original.getBody().statements().getFirst();
        var tail = (ReturnStatement) original.getBody().statements().get(1);
        var call = (MethodInvocation) JavaPrimitiveStateExpressions.unwrap(tail.getExpression());
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
                    || loop.initializers().size()!=1 || loop.updaters().size()!=1
                    || !(loop.initializers().getFirst() instanceof Assignment init) || init.getOperator()!=Assignment.Operator.ASSIGN
                    || !stateKeys.get(counter).equals(key(init.getLeftHandSide())) || !stateKeys.get(counter).equals(key(init.getRightHandSide()))
                    || !(loop.updaters().getFirst() instanceof Assignment update) || update.getOperator()!=Assignment.Operator.ASSIGN
                    || !stateKeys.get(counter).equals(key(update.getLeftHandSide()))
                    || !expression((Expression)call.arguments().get(counter),update.getRightHandSide(),bindings,checkpoint,0)) return false;
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
        var base = exit.getThenStatement() instanceof ReturnStatement r ? r
                : (ReturnStatement) ((Block) exit.getThenStatement()).statements().getFirst();
        return body.get(count + 1) instanceof ReturnStatement result
                && expression(base.getExpression(), result.getExpression(), bindings, checkpoint, 0);
    }

    private static boolean countdown(Expression value,String binding,InfixExpression.Operator operator) {
        value=JavaPrimitiveStateExpressions.unwrap(value);
        return value instanceof InfixExpression comparison && comparison.getOperator()==operator
                && !comparison.hasExtendedOperands() && binding.equals(key(JavaPrimitiveStateExpressions.unwrap(comparison.getLeftOperand())))
                && JavaPrimitiveStateExpressions.literal(comparison.getRightOperand(),0);
    }

    private static boolean sameSignature(MethodDeclaration original, MethodDeclaration target) {
        IMethodBinding before = original.resolveBinding(), after = target.resolveBinding();
        return before != null && after != null && !before.isRecovered() && !after.isRecovered()
                && original.getModifiers() == target.getModifiers()
                && before.getMethodDeclaration().isEqualTo(after.getMethodDeclaration())
                && before.getReturnType().isEqualTo(after.getReturnType());
    }

    private static VariableDeclarationFragment declaration(VariableDeclarationStatement node, String type) {
        ITypeBinding binding = node.getType().resolveBinding();
        if (binding == null || binding.isRecovered() || !binding.isPrimitive() || !type.equals(binding.getName())
                || node.fragments().size() != 1) return null;
        var fragment = (VariableDeclarationFragment) node.fragments().getFirst();
        return fragment.extraDimensions().isEmpty() && fragment.getInitializer() != null ? fragment : null;
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
