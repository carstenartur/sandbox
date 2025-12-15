/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

/**
 * Builder class for constructing stream pipelines from enhanced for-loops.
 * 
 * <p>This class analyzes the body of an enhanced for-loop and determines if it can be
 * converted into a stream pipeline. It handles various patterns including:
 * <ul>
 * <li>Simple forEach operations</li>
 * <li>MAP operations (variable declarations with initializers)</li>
 * <li>FILTER operations (IF statements)</li>
 * <li>REDUCE operations (accumulator patterns)</li>
 * <li>ANYMATCH/NONEMATCH operations (early returns)</li>
 * </ul>
 * 
 * <p>Based on the NetBeans mapreduce hints implementation:
 * https://github.com/apache/netbeans/tree/master/java/java.hints/src/org/netbeans/modules/java/hints/jdk/mapreduce
 * 
 * @see ProspectiveOperation
 * @see PreconditionsChecker
 */
public class StreamPipelineBuilder {
    private final EnhancedForStatement forLoop;
    private final PreconditionsChecker preconditions;
    private final AST ast;
    
    private List<ProspectiveOperation> operations;
    private String loopVariableName;
    private boolean analyzed = false;
    private boolean convertible = false;
    private String accumulatorVariable = null;
    private String accumulatorType = null;

    /**
     * Creates a new StreamPipelineBuilder for the given for-loop.
     * 
     * @param forLoop the enhanced for-loop to analyze
     * @param preconditions the preconditions checker for the loop
     */
    public StreamPipelineBuilder(EnhancedForStatement forLoop, PreconditionsChecker preconditions) {
        this.forLoop = forLoop;
        this.preconditions = preconditions;
        this.ast = forLoop.getAST();
        this.loopVariableName = forLoop.getParameter().getName().getIdentifier();
        this.operations = new ArrayList<>();
    }

    /**
     * Analyzes the loop body to determine if it can be converted to a stream pipeline.
     * 
     * <p>This method should be called before attempting to build the pipeline.
     * It inspects the loop body and extracts a sequence of {@link ProspectiveOperation}s
     * that represent the transformation.
     * 
     * @return true if the loop can be converted to a stream pipeline, false otherwise
     */
    public boolean analyze() {
        if (analyzed) {
            return convertible;
        }
        
        analyzed = true;
        
        // Check basic preconditions
        if (!preconditions.isSafeToRefactor() || !preconditions.iteratesOverIterable()) {
            convertible = false;
            return false;
        }
        
        // Parse the loop body into operations
        Statement loopBody = forLoop.getBody();
        operations = parseLoopBody(loopBody, loopVariableName);
        
        // Check if we have any operations
        convertible = !operations.isEmpty();
        return convertible;
    }

    /**
     * Builds the stream pipeline from the analyzed operations.
     * 
     * <p>This method should be called after {@link #analyze()} returns true.
     * It constructs a {@link MethodInvocation} representing the complete stream pipeline.
     * 
     * @return a MethodInvocation representing the stream pipeline, or null if the loop cannot be converted
     */
    public MethodInvocation buildPipeline() {
        if (!analyzed || !convertible) {
            return null;
        }
        
        // Check if we need .stream() or can use direct .forEach()
        boolean needsStream = requiresStreamPrefix();
        
        MethodInvocation pipeline;
        if (needsStream) {
            // Start with .stream()
            pipeline = ast.newMethodInvocation();
            pipeline.setExpression((Expression) ASTNode.copySubtree(ast, forLoop.getExpression()));
            pipeline.setName(ast.newSimpleName("stream"));
            
            // Chain each operation
            for (int i = 0; i < operations.size(); i++) {
                ProspectiveOperation op = operations.get(i);
                MethodInvocation next = ast.newMethodInvocation();
                next.setExpression(pipeline);
                next.setName(ast.newSimpleName(op.getSuitableMethod()));

                // Get the current parameter name for this operation
                String paramName = getVariableNameFromPreviousOp(operations, i, loopVariableName);
                
                // Use the current paramName for this operation
                List<Expression> args = op.getArguments(ast, paramName);
                for (Expression arg : args) {
                    next.arguments().add(arg);
                }
                pipeline = next;
            }
        } else {
            // Simple forEach without stream()
            ProspectiveOperation op = operations.get(0);
            pipeline = ast.newMethodInvocation();
            pipeline.setExpression((Expression) ASTNode.copySubtree(ast, forLoop.getExpression()));
            pipeline.setName(ast.newSimpleName("forEach"));
            List<Expression> args = op.getArguments(ast, loopVariableName);
            for (Expression arg : args) {
                pipeline.arguments().add(arg);
            }
        }
        
        return pipeline;
    }

    /**
     * Wraps the pipeline in an appropriate statement.
     * 
     * <p>Wraps the method invocation in an ExpressionStatement. For REDUCE operations
     * with a tracked accumulator variable, wraps the pipeline in an assignment statement
     * (e.g., "i = stream.reduce(...)") instead of a plain expression statement.
     * 
     * @param pipeline the pipeline method invocation
     * @return a Statement wrapping the pipeline
     */
    public Statement wrapPipeline(MethodInvocation pipeline) {
        if (pipeline == null) {
            return null;
        }
        
        // Check if we have a REDUCE operation
        boolean hasReduce = operations.stream()
            .anyMatch(op -> op.getOperationType() == ProspectiveOperation.OperationType.REDUCE);
        
        if (hasReduce && accumulatorVariable != null) {
            // Wrap in assignment: variable = pipeline
            Assignment assignment = ast.newAssignment();
            assignment.setLeftHandSide(ast.newSimpleName(accumulatorVariable));
            assignment.setOperator(Assignment.Operator.ASSIGN);
            assignment.setRightHandSide(pipeline);
            
            ExpressionStatement exprStmt = ast.newExpressionStatement(assignment);
            return exprStmt;
        } else {
            // Wrap in an ExpressionStatement for FOREACH and other operations
            ExpressionStatement exprStmt = ast.newExpressionStatement(pipeline);
            return exprStmt;
        }
    }

    /**
     * Returns the list of operations extracted from the loop body.
     * 
     * @return the list of prospective operations
     */
    public List<ProspectiveOperation> getOperations() {
        return operations;
    }
    
    /**
     * Extracts the expression from a REDUCE operation's right-hand side.
     * For example, in "i += foo(l)", extracts "foo(l)".
     * 
     * @param stmt the statement containing the reduce operation
     * @return the expression to be mapped, or null if none
     */
    private Expression extractReduceExpression(Statement stmt) {
        if (!(stmt instanceof ExpressionStatement)) {
            return null;
        }
        
        ExpressionStatement exprStmt = (ExpressionStatement) stmt;
        Expression expr = exprStmt.getExpression();
        
        if (expr instanceof Assignment) {
            Assignment assignment = (Assignment) expr;
            // Return the right-hand side expression for compound assignments
            if (assignment.getOperator() != Assignment.Operator.ASSIGN) {
                return assignment.getRightHandSide();
            }
        }
        
        return null;
    }
    
    /**
     * Adds a MAP operation before a REDUCE operation based on the reducer type.
     * For INCREMENT/DECREMENT: maps to 1 (or 1.0 for double types)
     * For SUM/PRODUCT/STRING_CONCAT: extracts and maps the RHS expression
     * 
     * @param ops the list to add the MAP operation to
     * @param reduceOp the REDUCE operation
     * @param stmt the statement containing the reduce operation
     * @param currentVarName the current variable name in the pipeline
     */
    private void addMapBeforeReduce(List<ProspectiveOperation> ops, ProspectiveOperation reduceOp, 
                                     Statement stmt, String currentVarName) {
        if (reduceOp.getReducerType() == ProspectiveOperation.ReducerType.INCREMENT ||
            reduceOp.getReducerType() == ProspectiveOperation.ReducerType.DECREMENT) {
            // Create a MAP operation that maps each item to 1 (or 1.0 for double types)
            Expression mapExpr;
            if (accumulatorType != null && accumulatorType.equals("double")) {
                mapExpr = ast.newNumberLiteral("1.0");
            } else if (accumulatorType != null && accumulatorType.equals("float")) {
                mapExpr = ast.newNumberLiteral("1.0f");
            } else if (accumulatorType != null && accumulatorType.equals("long")) {
                mapExpr = ast.newNumberLiteral("1L");
            } else if (accumulatorType != null && accumulatorType.equals("byte")) {
                // (byte) 1
                org.eclipse.jdt.core.dom.CastExpression cast = ast.newCastExpression();
                cast.setType(ast.newPrimitiveType(org.eclipse.jdt.core.dom.PrimitiveType.BYTE));
                cast.setExpression(ast.newNumberLiteral("1"));
                mapExpr = cast;
            } else if (accumulatorType != null && accumulatorType.equals("short")) {
                // (short) 1
                org.eclipse.jdt.core.dom.CastExpression cast = ast.newCastExpression();
                cast.setType(ast.newPrimitiveType(org.eclipse.jdt.core.dom.PrimitiveType.SHORT));
                cast.setExpression(ast.newNumberLiteral("1"));
                mapExpr = cast;
            } else if (accumulatorType != null && accumulatorType.equals("char")) {
                // (char) 1
                org.eclipse.jdt.core.dom.CastExpression cast = ast.newCastExpression();
                cast.setType(ast.newPrimitiveType(org.eclipse.jdt.core.dom.PrimitiveType.CHAR));
                cast.setExpression(ast.newNumberLiteral("1"));
                mapExpr = cast;
            } else {
                mapExpr = ast.newNumberLiteral("1");
            }
            
            ProspectiveOperation mapOp = new ProspectiveOperation(
                mapExpr,
                ProspectiveOperation.OperationType.MAP,
                "_item");
            ops.add(mapOp);
        } else if (reduceOp.getReducerType() == ProspectiveOperation.ReducerType.SUM ||
                   reduceOp.getReducerType() == ProspectiveOperation.ReducerType.PRODUCT ||
                   reduceOp.getReducerType() == ProspectiveOperation.ReducerType.STRING_CONCAT) {
            // For SUM/PRODUCT/STRING_CONCAT with expressions (e.g., i += foo(l)),
            // extract the right-hand side as a MAP operation
            Expression mapExpression = extractReduceExpression(stmt);
            if (mapExpression != null) {
                ProspectiveOperation mapOp = new ProspectiveOperation(
                    mapExpression,
                    ProspectiveOperation.OperationType.MAP,
                    currentVarName);
                ops.add(mapOp);
            }
        }
    }
    
    /**
     * Detects if a statement contains a REDUCE pattern (i++, sum += x, etc.).
     * 
     * @param stmt the statement to check
     * @return a ProspectiveOperation for REDUCE if detected, null otherwise
     */
    private ProspectiveOperation detectReduceOperation(Statement stmt) {
        if (!(stmt instanceof ExpressionStatement)) {
            return null;
        }
        
        ExpressionStatement exprStmt = (ExpressionStatement) stmt;
        Expression expr = exprStmt.getExpression();
        
        // Check for postfix increment/decrement: i++, i--
        if (expr instanceof PostfixExpression) {
            PostfixExpression postfix = (PostfixExpression) expr;
            if (postfix.getOperand() instanceof SimpleName) {
                String varName = ((SimpleName) postfix.getOperand()).getIdentifier();
                ProspectiveOperation.ReducerType reducerType;
                
                if (postfix.getOperator() == PostfixExpression.Operator.INCREMENT) {
                    reducerType = ProspectiveOperation.ReducerType.INCREMENT;
                } else if (postfix.getOperator() == PostfixExpression.Operator.DECREMENT) {
                    reducerType = ProspectiveOperation.ReducerType.DECREMENT;
                } else {
                    return null;
                }
                
                accumulatorVariable = varName;
                accumulatorType = getVariableType(varName);
                return new ProspectiveOperation(stmt, varName, reducerType);
            }
        }
        
        // Check for prefix increment/decrement: ++i, --i
        if (expr instanceof PrefixExpression) {
            PrefixExpression prefix = (PrefixExpression) expr;
            if (prefix.getOperand() instanceof SimpleName) {
                String varName = ((SimpleName) prefix.getOperand()).getIdentifier();
                ProspectiveOperation.ReducerType reducerType;
                
                if (prefix.getOperator() == PrefixExpression.Operator.INCREMENT) {
                    reducerType = ProspectiveOperation.ReducerType.INCREMENT;
                } else if (prefix.getOperator() == PrefixExpression.Operator.DECREMENT) {
                    reducerType = ProspectiveOperation.ReducerType.DECREMENT;
                } else {
                    return null;
                }
                
                accumulatorVariable = varName;
                accumulatorType = getVariableType(varName);
                return new ProspectiveOperation(stmt, varName, reducerType);
            }
        }
        
        // Check for compound assignments: +=, -=, *=, etc.
        if (expr instanceof Assignment) {
            Assignment assignment = (Assignment) expr;
            if (assignment.getLeftHandSide() instanceof SimpleName &&
                assignment.getOperator() != Assignment.Operator.ASSIGN) {
                
                String varName = ((SimpleName) assignment.getLeftHandSide()).getIdentifier();
                ProspectiveOperation.ReducerType reducerType;
                
                if (assignment.getOperator() == Assignment.Operator.PLUS_ASSIGN) {
                    reducerType = ProspectiveOperation.ReducerType.SUM;
                } else if (assignment.getOperator() == Assignment.Operator.TIMES_ASSIGN) {
                    reducerType = ProspectiveOperation.ReducerType.PRODUCT;
                } else if (assignment.getOperator() == Assignment.Operator.MINUS_ASSIGN) {
                    reducerType = ProspectiveOperation.ReducerType.DECREMENT;
                } else {
                    // Other assignment operators not yet supported
                    return null;
                }
                
                accumulatorVariable = varName;
                accumulatorType = getVariableType(varName);
                return new ProspectiveOperation(stmt, varName, reducerType);
            }
        }
        
        return null;
    }

    /**
     * Analyzes the body of an enhanced for-loop and extracts a list of {@link ProspectiveOperation}
     * objects representing the operations that can be mapped to stream operations.
     * 
     * <p>This method inspects the statements within the loop body to identify possible
     * stream operations such as {@code map} (for variable declarations with initializers),
     * {@code filter} (for IF statements), and {@code forEach} (for the final or sole statement).
     * For block bodies, it processes each statement in order, treating:
     * - IF statements with single block body as FILTER operations
     * - Variable declarations with initializers as MAP operations
     * - The last statement as a FOREACH operation
     *
     * @param body the {@link Statement} representing the loop body; may be a {@link Block} or a single statement
     * @param loopVarName the name of the loop variable currently in scope; may be updated if a map operation is found
     * @return a list of {@link ProspectiveOperation} objects, in the order they should be applied,
     *         representing the sequence of stream operations inferred from the loop body
     * @see ProspectiveOperation
     */
    private List<ProspectiveOperation> parseLoopBody(Statement body, String loopVarName) {
        List<ProspectiveOperation> ops = new ArrayList<>();
        String currentVarName = loopVarName; // Track the current variable name through the pipeline
        
        if (body instanceof Block) {
            Block block = (Block) body;
            List<Statement> statements = block.statements();
            
            for (int i = 0; i < statements.size(); i++) {
                Statement stmt = statements.get(i);
                boolean isLast = (i == statements.size() - 1);
                
                if (stmt instanceof VariableDeclarationStatement) {
                    // Variable declaration → MAP operation
                    VariableDeclarationStatement varDecl = (VariableDeclarationStatement) stmt;
                    List<VariableDeclarationFragment> fragments = varDecl.fragments();
                    if (!fragments.isEmpty()) {
                        VariableDeclarationFragment frag = fragments.get(0);
                        if (frag.getInitializer() != null) {
                            String newVarName = frag.getName().getIdentifier();
                            ProspectiveOperation mapOp = new ProspectiveOperation(
                                frag.getInitializer(),
                                ProspectiveOperation.OperationType.MAP,
                                newVarName);
                            ops.add(mapOp);
                            
                            // Update current var name for subsequent operations
                            currentVarName = newVarName;
                        }
                    }
                } else if (stmt instanceof IfStatement && !isLast) {
                    // IF statement (not the last statement) → potential FILTER or nested processing
                    IfStatement ifStmt = (IfStatement) stmt;
                    
                    // Check if this is a filtering IF (simple condition with block body)
                    if (ifStmt.getElseStatement() == null) {
                        Statement thenStmt = ifStmt.getThenStatement();
                        
                        // Check if this is an "if (condition) continue;" pattern
                        if (isIfWithContinue(ifStmt)) {
                            // Convert "if (condition) continue;" to ".filter(x -> !(condition))"
                            Expression negatedCondition = createNegatedExpression(ast, ifStmt.getExpression());
                            ProspectiveOperation filterOp = new ProspectiveOperation(
                                negatedCondition,
                                ProspectiveOperation.OperationType.FILTER);
                            ops.add(filterOp);
                            // Don't process the body since it's just a continue statement
                        } else {
                            // Regular filter with nested processing
                            // Add FILTER operation for the condition
                            ProspectiveOperation filterOp = new ProspectiveOperation(
                                ifStmt.getExpression(),
                                ProspectiveOperation.OperationType.FILTER);
                            ops.add(filterOp);
                            
                            // Process the body of the IF statement recursively
                            List<ProspectiveOperation> nestedOps = parseLoopBody(thenStmt, currentVarName);
                            ops.addAll(nestedOps);
                            
                            // Update current var name if the nested operations produced a new variable
                            if (!nestedOps.isEmpty()) {
                                ProspectiveOperation lastNested = nestedOps.get(nestedOps.size() - 1);
                                if (lastNested.getProducedVariableName() != null) {
                                    currentVarName = lastNested.getProducedVariableName();
                                }
                            }
                        }
                    }
                } else if (!isLast) {
                    // Non-last statement that's not a variable declaration or IF
                    // This is a side-effect statement like foo(l) - wrap it in a MAP that returns the current variable
                    // Only do this if it's not a REDUCE operation (which should only be the last statement)
                    ProspectiveOperation reduceCheck = detectReduceOperation(stmt);
                    if (reduceCheck == null) {
                        // Create a MAP operation with side effect that returns the current variable
                        // Note: For side-effect MAPs, the third parameter is the variable to return, not the loop variable
                        ProspectiveOperation mapOp = new ProspectiveOperation(
                            stmt,
                            ProspectiveOperation.OperationType.MAP,
                            currentVarName);
                        ops.add(mapOp);
                    }
                } else if (isLast) {
                    // Last statement → Check for REDUCE first, otherwise FOREACH
                    ProspectiveOperation reduceOp = detectReduceOperation(stmt);
                    if (reduceOp != null) {
                        // Add MAP operation before REDUCE based on reducer type
                        addMapBeforeReduce(ops, reduceOp, stmt, currentVarName);
                        ops.add(reduceOp);
                    } else {
                        // Regular FOREACH operation
                        ProspectiveOperation forEachOp = new ProspectiveOperation(
                            stmt,
                            ProspectiveOperation.OperationType.FOREACH,
                            currentVarName);
                        ops.add(forEachOp);
                    }
                }
            }
        } else if (body instanceof IfStatement) {
            // Single IF statement → process as filter with nested body
            IfStatement ifStmt = (IfStatement) body;
            if (ifStmt.getElseStatement() == null) {
                // Add FILTER operation
                ProspectiveOperation filterOp = new ProspectiveOperation(
                    ifStmt.getExpression(),
                    ProspectiveOperation.OperationType.FILTER);
                ops.add(filterOp);
                
                // Process the then statement
                List<ProspectiveOperation> nestedOps = parseLoopBody(ifStmt.getThenStatement(), currentVarName);
                ops.addAll(nestedOps);
            }
        } else {
            // Single statement → Check for REDUCE first, otherwise FOREACH
            ProspectiveOperation reduceOp = detectReduceOperation(body);
            if (reduceOp != null) {
                // Add MAP operation before REDUCE based on reducer type
                addMapBeforeReduce(ops, reduceOp, body, currentVarName);
                ops.add(reduceOp);
            } else {
                // Regular FOREACH operation
                ProspectiveOperation forEachOp = new ProspectiveOperation(
                    body,
                    ProspectiveOperation.OperationType.FOREACH,
                    currentVarName);
                ops.add(forEachOp);
            }
        }
        
        return ops;
    }
    
    /**
     * Determines the variable name to use for the current operation in a chain of stream operations.
     * 
     * <p>This method inspects the list of {@link ProspectiveOperation}s up to {@code currentIndex - 1}
     * to find if a previous MAP operation exists. If so, it returns the produced variable name
     * from that MAP operation. Otherwise, it returns the loop variable name.
     * </p>
     *
     * @param operations   the list of prospective operations representing the loop body transformation
     * @param currentIndex the index of the current operation in the list; operations before this index are considered
     * @param loopVarName  the original loop variable name
     * @return the variable name produced by the most recent MAP operation, or the loop variable name if none found
     */
    private String getVariableNameFromPreviousOp(List<ProspectiveOperation> operations, int currentIndex, String loopVarName) {
        // Look back to find the most recent operation that produces a variable
        for (int i = currentIndex - 1; i >= 0; i--) {
            ProspectiveOperation op = operations.get(i);
            if (op.getProducedVariableName() != null) {
                return op.getProducedVariableName();
            }
        }
        return loopVarName;
    }
    
    /**
     * Determines whether the stream pipeline requires an explicit .stream() prefix.
     * 
     * <p>Returns false if the pipeline consists of a single FOREACH operation,
     * which can be called directly on the collection. Returns true for all other cases,
     * including multiple operations or non-FOREACH terminal operations.
     * 
     * @return true if .stream() is required, false if direct collection method can be used
     */
    private boolean requiresStreamPrefix() {
        if (operations.isEmpty()) {
            return true;
        }
        return operations.size() > 1 || 
               operations.get(0).getOperationType() != ProspectiveOperation.OperationType.FOREACH;
    }
    
    /**
     * Checks if an IF statement contains a continue statement.
     * This pattern should be converted to a negated filter.
     * 
     * @param ifStatement the IF statement to check
     * @return true if the then branch contains a continue statement
     */
    private boolean isIfWithContinue(IfStatement ifStatement) {
        Statement thenStatement = ifStatement.getThenStatement();
        if (thenStatement instanceof ContinueStatement) {
            return true;
        }
        if (thenStatement instanceof Block) {
            Block block = (Block) thenStatement;
            if (block.statements().size() == 1 && block.statements().get(0) instanceof ContinueStatement) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Creates a negated expression for filter operations.
     * Used when converting "if (condition) continue;" to ".filter(x -> !(condition))".
     * 
     * @param ast the AST to create nodes in
     * @param condition the condition to negate
     * @return a negated expression
     */
    private Expression createNegatedExpression(AST ast, Expression condition) {
        PrefixExpression negation = ast.newPrefixExpression();
        negation.setOperator(PrefixExpression.Operator.NOT);
        negation.setOperand((Expression) ASTNode.copySubtree(ast, condition));
        return negation;
    }
    
    /**
     * Attempts to determine the type name of a variable by searching for its declaration
     * in the method containing the for-loop.
     * 
     * @param varName the variable name to look up
     * @return the simple type name (e.g., "double", "int") or null if not found
     */
    private String getVariableType(String varName) {
        // Walk up the AST to find the containing method or block
        ASTNode parent = forLoop.getParent();
        while (parent != null && !(parent instanceof org.eclipse.jdt.core.dom.MethodDeclaration) &&
               !(parent instanceof org.eclipse.jdt.core.dom.Block)) {
            parent = parent.getParent();
        }
        
        if (parent == null) {
            return null;
        }
        
        // Search for variable declarations in the method/block
        if (parent instanceof org.eclipse.jdt.core.dom.MethodDeclaration) {
            org.eclipse.jdt.core.dom.MethodDeclaration method = (org.eclipse.jdt.core.dom.MethodDeclaration) parent;
            return searchBlockForVariableType(method.getBody(), varName);
        } else if (parent instanceof org.eclipse.jdt.core.dom.Block) {
            return searchBlockForVariableType((org.eclipse.jdt.core.dom.Block) parent, varName);
        }
        
        return null;
    }
    
    /**
     * Searches a block for a variable declaration and returns its type.
     * 
     * @param block the block to search
     * @param varName the variable name to find
     * @return the simple type name or null if not found
     */
    private String searchBlockForVariableType(org.eclipse.jdt.core.dom.Block block, String varName) {
        if (block == null) {
            return null;
        }
        
        for (Object stmtObj : block.statements()) {
            if (stmtObj instanceof VariableDeclarationStatement) {
                VariableDeclarationStatement varDecl = (VariableDeclarationStatement) stmtObj;
                for (Object fragObj : varDecl.fragments()) {
                    if (fragObj instanceof VariableDeclarationFragment) {
                        VariableDeclarationFragment frag = (VariableDeclarationFragment) fragObj;
                        if (frag.getName().getIdentifier().equals(varName)) {
                            org.eclipse.jdt.core.dom.Type type = varDecl.getType();
                            // Robustly extract the simple type name
                            if (type.isPrimitiveType()) {
                                // For primitive types: int, double, etc.
                                return ((org.eclipse.jdt.core.dom.PrimitiveType) type).getPrimitiveTypeCode().toString();
                            } else if (type.isSimpleType()) {
                                // For reference types: get the simple name
                                org.eclipse.jdt.core.dom.SimpleType simpleType = (org.eclipse.jdt.core.dom.SimpleType) type;
                                // Try to use binding if available
                                org.eclipse.jdt.core.dom.ITypeBinding binding = simpleType.resolveBinding();
                                if (binding != null) {
                                    return binding.getName();
                                } else {
                                    return simpleType.getName().getFullyQualifiedName();
                                }
                            } else if (type.isArrayType()) {
                                // For array types, get the element type recursively and append "[]"
                                org.eclipse.jdt.core.dom.ArrayType arrayType = (org.eclipse.jdt.core.dom.ArrayType) type;
                                org.eclipse.jdt.core.dom.Type elementType = arrayType.getElementType();
                                String elementTypeName;
                                if (elementType.isPrimitiveType()) {
                                    elementTypeName = ((org.eclipse.jdt.core.dom.PrimitiveType) elementType).getPrimitiveTypeCode().toString();
                                } else if (elementType.isSimpleType()) {
                                    org.eclipse.jdt.core.dom.SimpleType simpleType = (org.eclipse.jdt.core.dom.SimpleType) elementType;
                                    org.eclipse.jdt.core.dom.ITypeBinding binding = simpleType.resolveBinding();
                                    if (binding != null) {
                                        elementTypeName = binding.getName();
                                    } else {
                                        elementTypeName = simpleType.getName().getFullyQualifiedName();
                                    }
                                } else {
                                    // Fallback for other types
                                    elementTypeName = elementType.toString();
                                }
                                return elementTypeName + "[]";
                            } else {
                                // Fallback for other types (e.g., parameterized, qualified, etc.)
                                return type.toString();
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
}
