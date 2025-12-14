/*******************************************************************************
 * Copyright (c) 2025 Alexandru Gyori and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Alexandru Gyori original code
 *     Carsten Hammer initial port to Eclipse
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

public class ProspectiveOperation {
    private Expression originalExpression;
    private OperationType operationType;
    private Set<String> neededVariables = new HashSet<>();
    private Expression reducingVariable;

    // Sammelt alle verwendeten Variablen
    private void collectNeededVariables(Expression expression) {
        expression.accept(new ASTVisitor() {
            @Override
            public boolean visit(SimpleName node) {
                neededVariables.add(node.getIdentifier());
                return super.visit(node);
            }
        });
    }
    public ProspectiveOperation(Expression expression, OperationType operationType) {
        this.originalExpression = expression;
        this.operationType = operationType;
        collectNeededVariables(expression);
    }

    /** (1) Gibt den ursprünglichen Ausdruck zurück */
    public Expression getExpression() {
        return this.originalExpression;
    }

    /** (2) Gibt den Typ der Operation zurück */
    public OperationType getOperationType() {
        return this.operationType;
    }

    /** (3) Gibt die passende Stream-API Methode zurück */
    public String getStreamOperation() {
        return operationType == OperationType.MAP ? "map" :
               operationType == OperationType.REDUCE ? "reduce" : "unknown";
    }

    /** (4) Erstellt eine Lambda-Expression für Streams */
    public LambdaExpression getLambdaExpression(AST ast) {
        LambdaExpression lambda = ast.newLambdaExpression();
        SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
        param.setName(ast.newSimpleName("x"));
        lambda.parameters().add(param);
        lambda.setBody(ASTNode.copySubtree(ast, originalExpression));
        return lambda;
    }

    /** (5) Ermittelt die Argumente für `reduce()` */
    private List<Expression> getArgumentsForReducer(AST ast) {
        List<Expression> arguments = new ArrayList<>();
        if (operationType == OperationType.REDUCE) {
            Expression identity = getIdentityElement(ast);
            if (identity != null) arguments.add(identity);
            LambdaExpression accumulator = createAccumulatorLambda(ast);
            if (accumulator != null) arguments.add(accumulator);
        }
        return arguments;
    }

    /** (6) Ermittelt das Identitätselement (`0`, `1`) für `reduce()` */
    private Expression getIdentityElement(AST ast) {
        if (operationType == OperationType.REDUCE && originalExpression instanceof Assignment) {
            Assignment assignment = (Assignment) originalExpression;
            if (assignment.getOperator() == Assignment.Operator.PLUS_ASSIGN) {
                return ast.newNumberLiteral("0");
            } else if (assignment.getOperator() == Assignment.Operator.TIMES_ASSIGN) {
                return ast.newNumberLiteral("1");
            }
        }
        return null;
    }

    /** (7) Erstellt eine Akkumulator-Funktion für `reduce()` */
    private LambdaExpression createAccumulatorLambda(AST ast) {
        LambdaExpression lambda = ast.newLambdaExpression();
        SingleVariableDeclaration paramA = ast.newSingleVariableDeclaration();
        paramA.setName(ast.newSimpleName("a"));
        SingleVariableDeclaration paramB = ast.newSingleVariableDeclaration();
        paramB.setName(ast.newSimpleName("b"));
        lambda.parameters().add(paramA);
        lambda.parameters().add(paramB);

        InfixExpression operationExpr = ast.newInfixExpression();
        operationExpr.setLeftOperand(ast.newSimpleName("a"));
        operationExpr.setRightOperand(ast.newSimpleName("b"));
        operationExpr.setOperator(InfixExpression.Operator.PLUS);
        lambda.setBody(operationExpr);

        return lambda;
    }

    /** (8) Erstellt eine Lambda-Funktion für `map()` */
    private LambdaExpression createLambdaExpression(AST ast) {
        LambdaExpression lambda = ast.newLambdaExpression();
        SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
        param.setName(ast.newSimpleName("x"));
        lambda.parameters().add(param);
        lambda.setBody(ASTNode.copySubtree(ast, originalExpression));
        return lambda;
    }

    /** (9) Prüft, ob eine bestimmte Variable verwendet wird */
    public boolean usesVariable(String variableName) {
        return neededVariables.contains(variableName);
    }

    /** (10) Prüft, ob zwei `ProspectiveOperation`-Objekte kombinierbar sind */
    public static boolean areComposable(ProspectiveOperation op1, ProspectiveOperation op2) {
        return op1.getOperationType() == op2.getOperationType() &&
               op1.getStreamOperation().equals(op2.getStreamOperation()) &&
               Collections.disjoint(op1.getNeededVariables(), op2.getNeededVariables());
    }

    /** (11) Kombiniert zwei `ProspectiveOperation`-Objekte */
    public static ProspectiveOperation merge(ProspectiveOperation op1, ProspectiveOperation op2, AST ast) {
        if (!areComposable(op1, op2)) return null;
        LambdaExpression mergedLambda = ast.newLambdaExpression();
        mergedLambda.parameters().addAll(op1.getLambdaExpression(ast).parameters());

        InfixExpression combinedExpr = ast.newInfixExpression();
        combinedExpr.setLeftOperand((Expression) ASTNode.copySubtree(ast, op1.getLambdaExpression(ast).getBody()));
        combinedExpr.setRightOperand((Expression) ASTNode.copySubtree(ast, op2.getLambdaExpression(ast).getBody()));
        combinedExpr.setOperator(InfixExpression.Operator.PLUS);

        mergedLambda.setBody(combinedExpr);
        return new ProspectiveOperation(mergedLambda, op1.getOperationType());
    }
    
    public static List<ProspectiveOperation> mergeRecursivelyIntoComposableOperations(List<ProspectiveOperation> operations) {
        List<ProspectiveOperation> mergedOperations = new ArrayList<>();
        
        for (ProspectiveOperation op : operations) {
            boolean merged = false;

            // Prüfen, ob die aktuelle Operation mit einer bestehenden verschmolzen werden kann
            for (ProspectiveOperation existingOp : mergedOperations) {
                if (canBeMerged(existingOp, op)) {
                    mergeOperations(existingOp, op);
                    merged = true;
                    break;
                }
            }

            // Falls keine bestehende Operation kombiniert werden konnte, zur Liste hinzufügen
            if (!merged) {
                mergedOperations.add(op);
            }
        }

        return mergedOperations;
    }

    // Prüft, ob zwei Operationen verschmolzen werden können
    private static boolean canBeMerged(ProspectiveOperation op1, ProspectiveOperation op2) {
        return op1.getOperationType() == op2.getOperationType()
                && op1.getStreamOperation().equals(op2.getStreamOperation());
    }

    // Kombiniert zwei `map()`- oder `reduce()`-Operationen zu einer einzigen
    private static void mergeOperations(ProspectiveOperation target, ProspectiveOperation source) {
        AST ast = target.getExpression().getAST();

        LambdaExpression mergedLambda = ast.newLambdaExpression();
        mergedLambda.parameters().addAll(target.getLambdaExpression(ast).parameters());

        InfixExpression combinedExpression = ast.newInfixExpression();
        combinedExpression.setLeftOperand((Expression) ASTNode.copySubtree(ast, target.getLambdaExpression(ast).getBody()));
        combinedExpression.setRightOperand((Expression) ASTNode.copySubtree(ast, source.getLambdaExpression(ast).getBody()));

        if (source.getOperationType() == ProspectiveOperation.OperationType.MAP) {
            combinedExpression.setOperator(InfixExpression.Operator.PLUS);
        } else if (source.getOperationType() == ProspectiveOperation.OperationType.REDUCE) {
            combinedExpression.setOperator(InfixExpression.Operator.TIMES);
        }

        mergedLambda.setBody(combinedExpression);
        target.replaceReducingVariable(mergedLambda);
    }
    
    public void replaceReducingVariable(LambdaExpression lambda) {
        lambda.accept(new ASTVisitor() {
            @Override
            public boolean visit(SimpleName node) {
                if (node.getIdentifier().equals(reducingVariable.toString())) {
                    node.setIdentifier("acc");
                }
                return super.visit(node);
            }
        });
    }

    /** (14) Gibt die Menge der benötigten Variablen zurück */
    public Set<String> getNeededVariables() {
        return neededVariables;
    }

    // Sammelt alle verfügbaren Variablen im aktuellen Scope
    private Set<String> getAvailableVariables(ASTNode node) {
        Set<String> variables = new HashSet<>();
        node.accept(new ASTVisitor() {
            @Override
            public boolean visit(VariableDeclarationFragment fragment) {
                variables.add(fragment.getName().getIdentifier());
                return super.visit(fragment);
            }

            @Override
            public boolean visit(SingleVariableDeclaration param) {
                variables.add(param.getName().getIdentifier());
                return super.visit(param);
            }
        });
        return variables;
    }
    
    /** (15) Prüft, ob alle benötigten Variablen existieren */
    public boolean areAvailableVariables(Set<String> needed, ASTNode node) {
        return getAvailableVariables(node).containsAll(needed);
    }

    /** (16) Gibt ein beliebiges Element aus einem Set zurück */
    public static <T> T getOneFromSet(Set<T> set) {
        return set.isEmpty() ? null : set.iterator().next();
    }

    /** (17) Optimiert redundanten Code */
    public void beautify(AST ast, ASTRewrite rewrite, Expression expression) {
        if (expression instanceof ParenthesizedExpression) {
            // Entferne überflüssige Klammern: ((x + y)) → x + y
            ParenthesizedExpression parenExpr = (ParenthesizedExpression) expression;
            rewrite.replace(parenExpr, ASTNode.copySubtree(ast, parenExpr.getExpression()), null);
        } else if (expression instanceof Assignment) {
            // Optimierung von Zuweisungen: x = x + y → x += y
            Assignment assignment = (Assignment) expression;
            beautifyAssignment(ast, rewrite, assignment);
        } else if (expression instanceof InfixExpression) {
            // Optimierung mathematischer Ausdrücke
            optimizeInfixExpression(ast, rewrite, (InfixExpression) expression);
        }
    }
    
    public void beautifyAssignment(AST ast, ASTRewrite rewrite, Assignment assignment) {
        InfixExpression infixExpr = ast.newInfixExpression();
        infixExpr.setLeftOperand((Expression) ASTNode.copySubtree(ast, assignment.getLeftHandSide()));
        infixExpr.setRightOperand((Expression) ASTNode.copySubtree(ast, assignment.getRightHandSide()));
        infixExpr.setOperator(InfixExpression.Operator.PLUS);

        rewrite.replace(assignment, infixExpr, null);
    }
    // Methode zur Optimierung mathematischer Ausdrücke
    private void optimizeInfixExpression(AST ast, ASTRewrite rewrite, InfixExpression infixExpr) {
        Expression left = infixExpr.getLeftOperand();
        Expression right = infixExpr.getRightOperand();

        if (isZero(left) && infixExpr.getOperator() == InfixExpression.Operator.PLUS) {
            // 0 + x → x
            rewrite.replace(infixExpr, ASTNode.copySubtree(ast, right), null);
        } else if (isZero(right) && infixExpr.getOperator() == InfixExpression.Operator.PLUS) {
            // x + 0 → x
            rewrite.replace(infixExpr, ASTNode.copySubtree(ast, left), null);
        } else if (isOne(right) && infixExpr.getOperator() == InfixExpression.Operator.TIMES) {
            // x * 1 → x
            rewrite.replace(infixExpr, ASTNode.copySubtree(ast, left), null);
        } else if (isOne(left) && infixExpr.getOperator() == InfixExpression.Operator.TIMES) {
            // 1 * x → x
            rewrite.replace(infixExpr, ASTNode.copySubtree(ast, right), null);
        }
    }

    // Prüft, ob eine Expression die Zahl 1 ist
    private boolean isOne(Expression expr) {
        return expr instanceof NumberLiteral && ((NumberLiteral) expr).getToken().equals("1");
    }

    /** (18) Führt risikoarme Code-Optimierungen durch */
    public void beautifyLazy(AST ast, ASTRewrite rewrite, Expression expression) {
        if (expression instanceof ParenthesizedExpression) {
            // Entferne überflüssige Klammern NUR, wenn sie direkt einen Ausdruck umschließen: ((x)) → x
            ParenthesizedExpression parenExpr = (ParenthesizedExpression) expression;
            if (parenExpr.getExpression() instanceof SimpleName || parenExpr.getExpression() instanceof NumberLiteral) {
                rewrite.replace(parenExpr, ASTNode.copySubtree(ast, parenExpr.getExpression()), null);
            }
        } else if (expression instanceof InfixExpression) {
            // Nur einfache mathematische Optimierungen durchführen
            optimizeLazyInfixExpression(ast, rewrite, (InfixExpression) expression);
        }
    }
    
 // Vereinfachungen nur bei einfachen mathematischen Ausdrücken
    private void optimizeLazyInfixExpression(AST ast, ASTRewrite rewrite, InfixExpression infixExpr) {
        Expression left = infixExpr.getLeftOperand();
        Expression right = infixExpr.getRightOperand();

        if (isZero(left) && infixExpr.getOperator() == InfixExpression.Operator.PLUS) {
            // 0 + x → x (nur einfache Zahlenoperationen)
            rewrite.replace(infixExpr, ASTNode.copySubtree(ast, right), null);
        } else if (isZero(right) && infixExpr.getOperator() == InfixExpression.Operator.PLUS) {
            // x + 0 → x
            rewrite.replace(infixExpr, ASTNode.copySubtree(ast, left), null);
        }
    }

    // Prüft, ob eine Expression die Zahl 0 ist
    private boolean isZero(Expression expr) {
        return expr instanceof NumberLiteral && ((NumberLiteral) expr).getToken().equals("0");
    }

    /** (19) Wandelt einen Ausdruck in eine `return`-Anweisung um */
    public void addReturn(AST ast, ASTRewrite rewrite, Expression expression) {
        ReturnStatement returnStatement = ast.newReturnStatement();
        returnStatement.setExpression((Expression) ASTNode.copySubtree(ast, expression));
        rewrite.replace(expression, returnStatement, null);
    }
    
    @Override
    public String toString() {
        return "ProspectiveOperation{" +
               "expression=" + originalExpression +
               ", operationType=" + operationType +
               ", neededVariables=" + neededVariables +
               '}';
    }

    public enum OperationType {
        MAP, FOREACH, FILTER, REDUCE, ANYMATCH, NONEMATCH
    }
}