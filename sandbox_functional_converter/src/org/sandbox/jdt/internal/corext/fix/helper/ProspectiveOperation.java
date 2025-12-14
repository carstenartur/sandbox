/*******************************************************************************
 * Copyright (c) 2021 Alexandru Gyori and others.
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
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeMethodReference;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

public class ProspectiveOperation {
    /**
     * The original expression being analyzed or transformed.
     * <p>
     * This is set directly when the {@link ProspectiveOperation} is constructed with an {@link Expression}.
     * If constructed with a {@link org.eclipse.jdt.core.dom.Statement}, this is set to the expression
     * contained within the statement (if applicable, e.g., for {@link org.eclipse.jdt.core.dom.ExpressionStatement}).
     */
    private Expression originalExpression;

    /**
     * The original statement being analyzed or transformed.
     * <p>
     * This is set when the {@link ProspectiveOperation} is constructed with a {@link org.eclipse.jdt.core.dom.Statement}.
     * If the statement is an {@link org.eclipse.jdt.core.dom.ExpressionStatement}, its expression is also
     * extracted and stored in {@link #originalExpression}.
     * Otherwise, {@link #originalExpression} may be null.
     */
    private org.eclipse.jdt.core.dom.Statement originalStatement;

    private OperationType operationType;
    private Set<String> neededVariables = new HashSet<>();
    private Expression reducingVariable;
    private boolean eager = false;

    /**
     * The name of the loop variable associated with this operation, if applicable.
     * <p>
     * This is set when the {@link ProspectiveOperation} is constructed with a statement and a loop variable name.
     * It is used to track the variable iterated over in enhanced for-loops or similar constructs.
     */
    private String loopVariableName;
    
    /**
     * The name of the variable produced by this operation (for MAP operations).
     * This is used to track variable names through the stream pipeline.
     */
    private String producedVariableName;
    
    /**
     * The name of the accumulator variable for REDUCE operations.
     * Used to track which variable is being accumulated (e.g., "i" in "i++").
     */
    private String accumulatorVariableName;
    
    /**
     * The reducer type for REDUCE operations (INCREMENT, DECREMENT, SUM, etc.).
     */
    private ReducerType reducerType;

    // Sammelt alle verwendeten Variablen
    private void collectNeededVariables(Expression expression) {
        if (expression == null) return;
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
    
    public ProspectiveOperation(org.eclipse.jdt.core.dom.Statement statement, OperationType operationType, String loopVarName) {
        this.originalStatement = statement;
        this.operationType = operationType;
        this.loopVariableName = loopVarName;
        if (statement instanceof org.eclipse.jdt.core.dom.ExpressionStatement) {
            this.originalExpression = ((org.eclipse.jdt.core.dom.ExpressionStatement) statement).getExpression();
            collectNeededVariables(this.originalExpression);
        }
    }
    
    /**
     * Constructor for MAP operations with a produced variable name.
     * Used when a variable declaration creates a new variable in the stream pipeline.
     */
    public ProspectiveOperation(Expression expression, OperationType operationType, String producedVarName) {
        this.originalExpression = expression;
        this.operationType = operationType;
        this.producedVariableName = producedVarName;
        collectNeededVariables(expression);
    }
    
    /**
     * Constructor for REDUCE operations with accumulator variable and reducer type.
     * Used when a reducer pattern (i++, sum += x, etc.) is detected.
     * 
     * @param statement the statement containing the reducer
     * @param accumulatorVarName the name of the accumulator variable (e.g., "i", "sum")
     * @param reducerType the type of reducer (INCREMENT, SUM, etc.)
     */
    public ProspectiveOperation(org.eclipse.jdt.core.dom.Statement statement, String accumulatorVarName, ReducerType reducerType) {
        this.originalStatement = statement;
        this.operationType = OperationType.REDUCE;
        this.accumulatorVariableName = accumulatorVarName;
        this.reducerType = reducerType;
        if (statement instanceof org.eclipse.jdt.core.dom.ExpressionStatement) {
            this.originalExpression = ((org.eclipse.jdt.core.dom.ExpressionStatement) statement).getExpression();
            collectNeededVariables(this.originalExpression);
        }
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
    
    /** Returns the suitable stream method name for this operation type */
    public String getSuitableMethod() {
        switch (operationType) {
            case MAP:
                return "map";
            case FILTER:
                return "filter";
            case FOREACH:
                return "forEachOrdered";
            case REDUCE:
                return "reduce";
            case ANYMATCH:
                return "anyMatch";
            case NONEMATCH:
                return "noneMatch";
            default:
                return "unknown";
        }
    }
    
    /**
     * Generate the lambda arguments for this operation
     * Based on NetBeans ProspectiveOperation.getArguments()
     */
    public List<Expression> getArguments(AST ast, String paramName) {
        List<Expression> args = new ArrayList<>();
        
        if (operationType == OperationType.REDUCE) {
            return getArgumentsForReducer(ast);
        }
        
        // Create lambda expression for MAP, FILTER, FOREACH, ANYMATCH, NONEMATCH
        LambdaExpression lambda = ast.newLambdaExpression();
        
        // Create parameter
        VariableDeclarationFragment param = ast.newVariableDeclarationFragment();
        param.setName(ast.newSimpleName(paramName != null ? paramName : "item"));
        lambda.parameters().add(param);
        
        // Create lambda body based on operation type
        if (operationType == OperationType.MAP && originalExpression != null) {
            // For MAP: lambda body is the expression
            lambda.setBody(ASTNode.copySubtree(ast, originalExpression));
        } else if (operationType == OperationType.FILTER && originalExpression != null) {
            // For FILTER: wrap condition in parentheses
            ParenthesizedExpression parenExpr = ast.newParenthesizedExpression();
            parenExpr.setExpression((Expression) ASTNode.copySubtree(ast, originalExpression));
            lambda.setBody(parenExpr);
        } else if (operationType == OperationType.FOREACH && originalStatement != null) {
            // For FOREACH: lambda body is the statement (as block)
            if (originalStatement instanceof org.eclipse.jdt.core.dom.Block) {
                lambda.setBody(ASTNode.copySubtree(ast, originalStatement));
            } else {
                org.eclipse.jdt.core.dom.Block block = ast.newBlock();
                block.statements().add(ASTNode.copySubtree(ast, originalStatement));
                lambda.setBody(block);
            }
        } else if (originalExpression != null) {
            // Default: use expression as body
            lambda.setBody(ASTNode.copySubtree(ast, originalExpression));
        } else {
            // Defensive: neither originalExpression nor originalStatement is available
            throw new IllegalStateException("Cannot create lambda: both originalExpression and originalStatement are null for operationType " + operationType);
        }
        
        args.add(lambda);
        return args;
    }
    
    /**
     * Sets whether this operation should be executed eagerly (e.g., forEachOrdered vs forEach).
     * 
     * @param eager true if the operation should be executed in order, false otherwise
     */
    public void setEager(boolean eager) {
        this.eager = eager;
    }
    
    /**
     * Creates a lambda expression for this operation.
     * 
     * <p>Implementation details:
     * <ul>
     * <li>MAP: x -&gt; { &lt;stmt&gt;; return x; } or simpler form for expressions</li>
     * <li>FILTER: x -&gt; (&lt;condition&gt;) with optional negation</li>
     * <li>FOREACH: x -&gt; { &lt;stmt&gt; }</li>
     * <li>REDUCE: Create map to constant, then reduce with accumulator function</li>
     * <li>ANYMATCH: x -&gt; (&lt;condition&gt;)</li>
     * <li>NONEMATCH: x -&gt; (&lt;condition&gt;)</li>
     * </ul>
     * 
     * @param ast the AST to create nodes in
     * @param loopVarName the name of the loop variable to use as lambda parameter
     * @return a lambda expression representing this operation
     */
    public LambdaExpression createLambda(AST ast, String loopVarName) {
        LambdaExpression lambda = ast.newLambdaExpression();
        
        // Create parameter using SingleVariableDeclaration (as per existing code pattern)
        SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
        param.setName(ast.newSimpleName(loopVarName != null ? loopVarName : "x"));
        lambda.parameters().add(param);
        
        // Create lambda body based on operation type
        switch (operationType) {
            case MAP:
                if (originalExpression != null) {
                    // For MAP: use expression directly for simple cases
                    lambda.setBody(ASTNode.copySubtree(ast, originalExpression));
                } else if (originalStatement != null) {
                    // For MAP with statement: create block
                    org.eclipse.jdt.core.dom.Block block = ast.newBlock();
                    block.statements().add(ASTNode.copySubtree(ast, originalStatement));
                    lambda.setBody(block);
                }
                break;
                
            case FILTER:
            case ANYMATCH:
            case NONEMATCH:
                // For FILTER/ANYMATCH/NONEMATCH: x -> (<condition>)
                if (originalExpression != null) {
                    lambda.setBody(ASTNode.copySubtree(ast, originalExpression));
                }
                break;
                
            case FOREACH:
                // For FOREACH: x -> { <stmt> } (no return)
                if (originalStatement != null) {
                    if (originalStatement instanceof org.eclipse.jdt.core.dom.Block) {
                        lambda.setBody(ASTNode.copySubtree(ast, originalStatement));
                    } else {
                        org.eclipse.jdt.core.dom.Block block = ast.newBlock();
                        block.statements().add(ASTNode.copySubtree(ast, originalStatement));
                        lambda.setBody(block);
                    }
                } else if (originalExpression != null) {
                    org.eclipse.jdt.core.dom.Block block = ast.newBlock();
                    org.eclipse.jdt.core.dom.ExpressionStatement exprStmt = ast.newExpressionStatement(
                        (Expression) ASTNode.copySubtree(ast, originalExpression));
                    block.statements().add(exprStmt);
                    lambda.setBody(block);
                }
                break;
                
            case REDUCE:
                // For REDUCE: accumulator lambda is created separately
                // This returns the accumulator function, not a mapping function
                return createAccumulatorLambda(ast);
                
            default:
                throw new IllegalStateException("Unknown operation type: " + operationType);
        }
        
        return lambda;
    }
    
    /**
     * Returns the stream method name for this operation.
     * 
     * <p>Mapping:
     * <ul>
     * <li>MAP → "map"</li>
     * <li>FILTER → "filter"</li>
     * <li>FOREACH → "forEach" or "forEachOrdered" (depending on eager flag)</li>
     * <li>REDUCE → "reduce"</li>
     * <li>ANYMATCH → "anyMatch"</li>
     * <li>NONEMATCH → "noneMatch"</li>
     * </ul>
     * 
     * @return the stream API method name for this operation
     */
    public String getStreamMethod() {
        switch (operationType) {
            case MAP:
                return "map";
            case FILTER:
                return "filter";
            case FOREACH:
                return eager ? "forEachOrdered" : "forEach";
            case REDUCE:
                return "reduce";
            case ANYMATCH:
                return "anyMatch";
            case NONEMATCH:
                return "noneMatch";
            default:
                throw new IllegalStateException("Unknown operation type: " + operationType);
        }
    }
    
    /**
     * Returns the arguments for the stream method call.
     * 
     * <p>For most operations (MAP, FILTER, FOREACH, ANYMATCH, NONEMATCH), 
     * returns a list containing the lambda expression.</p>
     * 
     * <p>For REDUCE operations, returns a list with the identity value followed by 
     * the accumulator lambda, or just the lambda if no identity value can be determined.</p>
     * 
     * @param ast the AST to create nodes in
     * @param loopVarName the name of the loop variable to use as lambda parameter
     * @return a list of expressions to pass as arguments to the stream method
     */
    public List<Expression> getStreamArguments(AST ast, String loopVarName) {
        List<Expression> args = new ArrayList<>();
        
        if (operationType == OperationType.REDUCE) {
            return getArgumentsForReducer(ast);
        }
        
        // For other operations: create lambda
        LambdaExpression lambda = createLambda(ast, loopVarName);
        args.add(lambda);
        return args;
    }
    
    /**
     * Returns the reducing variable expression for REDUCE operations.
     * 
     * @return the expression representing the variable being reduced
     */
    public Expression getReducingVariable() {
        return reducingVariable;
    }
    
    /**
     * Returns the variable name produced by this operation (for MAP operations).
     * This is used to track variable names through the stream pipeline.
     */
    public String getProducedVariableName() {
        return producedVariableName;
    }
    
    /**
     * Returns the accumulator variable name for REDUCE operations.
     * @return the accumulator variable name, or null if not a REDUCE operation
     */
    public String getAccumulatorVariableName() {
        return accumulatorVariableName;
    }
    
    /**
     * Returns the reducer type for REDUCE operations.
     * @return the reducer type, or null if not a REDUCE operation
     */
    public ReducerType getReducerType() {
        return reducerType;
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
            // First argument: identity element (the accumulator variable reference)
            if (accumulatorVariableName != null) {
                arguments.add(ast.newSimpleName(accumulatorVariableName));
            } else {
                // Fallback to default identity
                Expression identity = getIdentityElement(ast);
                if (identity != null) arguments.add(identity);
            }
            
            // Second argument: accumulator function (method reference or lambda)
            Expression accumulator = createAccumulatorExpression(ast);
            if (accumulator != null) arguments.add(accumulator);
        }
        return arguments;
    }
    
    /**
     * Creates the accumulator expression for REDUCE operations.
     * Returns a method reference (e.g., Integer::sum) when possible, or a lambda otherwise.
     */
    private Expression createAccumulatorExpression(AST ast) {
        if (reducerType == null) {
            // Fallback to legacy behavior
            return createAccumulatorLambda(ast);
        }
        
        switch (reducerType) {
            case INCREMENT:
            case SUM:
                // Use Integer::sum method reference
                return createMethodReference(ast, "Integer", "sum");
            case DECREMENT:
                // Use (accumulator, _item) -> accumulator - _item lambda
                return createBinaryOperatorLambda(ast, InfixExpression.Operator.MINUS);
            case PRODUCT:
                // Use (accumulator, _item) -> accumulator * _item lambda
                return createBinaryOperatorLambda(ast, InfixExpression.Operator.TIMES);
            case STRING_CONCAT:
                // Use (accumulator, _item) -> accumulator + _item lambda
                return createBinaryOperatorLambda(ast, InfixExpression.Operator.PLUS);
            default:
                return createAccumulatorLambda(ast);
        }
    }
    
    /**
     * Creates a method reference like Integer::sum.
     */
    private TypeMethodReference createMethodReference(AST ast, String typeName, String methodName) {
        TypeMethodReference methodRef = ast.newTypeMethodReference();
        methodRef.setType(ast.newSimpleType(ast.newSimpleName(typeName)));
        methodRef.setName(ast.newSimpleName(methodName));
        return methodRef;
    }
    
    /**
     * Creates a binary operator lambda like (accumulator, _item) -> accumulator + _item.
     * Note: Lambda parameters must use SingleVariableDeclaration (not VariableDeclarationFragment)
     * as required by the Eclipse JDT AST specification for lambda expressions.
     */
    private LambdaExpression createBinaryOperatorLambda(AST ast, InfixExpression.Operator operator) {
        LambdaExpression lambda = ast.newLambdaExpression();
        
        // Parameters: (accumulator, _item)
        SingleVariableDeclaration param1 = ast.newSingleVariableDeclaration();
        param1.setName(ast.newSimpleName("accumulator"));
        SingleVariableDeclaration param2 = ast.newSingleVariableDeclaration();
        param2.setName(ast.newSimpleName("_item"));
        lambda.parameters().add(param1);
        lambda.parameters().add(param2);
        
        // Body: accumulator + _item (or other operator)
        InfixExpression operationExpr = ast.newInfixExpression();
        operationExpr.setLeftOperand(ast.newSimpleName("accumulator"));
        operationExpr.setRightOperand(ast.newSimpleName("_item"));
        operationExpr.setOperator(operator);
        lambda.setBody(operationExpr);
        
        return lambda;
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
    
    public enum ReducerType {
        INCREMENT,      // i++, ++i
        DECREMENT,      // i--, --i
        SUM,            // sum += x
        PRODUCT,        // product *= x
        STRING_CONCAT   // s += string
    }
}