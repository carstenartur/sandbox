/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
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

import org.eclipse.jdt.core.dom.*;
import org.sandbox.functional.core.builder.LoopModelBuilder;
import org.sandbox.functional.core.model.*;
import org.sandbox.functional.core.model.SourceDescriptor.SourceType;

/**
 * Extracts a LoopModel from JDT AST nodes.
 * This bridges the JDT world with the abstract ULR model.
 */
public class JdtLoopExtractor {
    
    /**
     * Extracts a LoopModel from an EnhancedForStatement.
     */
    /**
     * Enhanced wrapper that includes both the abstract LoopModel and the original AST nodes.
     * This allows the renderer to use the actual AST nodes instead of parsing strings.
     */
    public static class ExtractedLoop {
        public final LoopModel model;
        public final Statement originalBody;
        
        public ExtractedLoop(LoopModel model, Statement originalBody) {
            this.model = model;
            this.originalBody = originalBody;
        }
    }
    
    public ExtractedLoop extract(EnhancedForStatement forStatement) {
        return extract(forStatement, null);
    }
    
    public ExtractedLoop extract(EnhancedForStatement forStatement, CompilationUnit compilationUnit) {
        Expression iterable = forStatement.getExpression();
        SingleVariableDeclaration parameter = forStatement.getParameter();
        Statement body = forStatement.getBody();
        
        // Determine source type
        SourceType sourceType = determineSourceType(iterable);
        String sourceExpression = iterable.toString();
        String elementType = parameter.getType().toString();
        
        // Element info
        String varName = parameter.getName().getIdentifier();
        boolean isFinal = Modifier.isFinal(parameter.getModifiers());
        
        // Analyze metadata — track truly unconvertible control flow and patterns.
        LoopBodyAnalyzer analyzer = new LoopBodyAnalyzer();
        // Issue #670: Enable collection modification detection
        if (iterable instanceof SimpleName) {
            analyzer.setIteratedCollectionName(((SimpleName) iterable).getIdentifier());
        }
        body.accept(analyzer);
        
        // If the body contains unconvertible patterns, don't even try to build operations
        // Note: external variable modifications (for reduce) and if-statements (for filter/match)
        // are handled by analyzeAndAddOperations() and should NOT be blocked here
        boolean hasUnconvertiblePatterns = analyzer.hasBreak() 
                || analyzer.hasLabeledContinue()
                || analyzer.hasTryCatch()
                || analyzer.hasSynchronized()
                || analyzer.hasNestedLoop()
                || analyzer.hasVoidReturn()
                || analyzer.modifiesIteratedCollection();
        
        // Build model — mark as unconvertible if any patterns detected
        LoopModelBuilder builder = new LoopModelBuilder()
            .source(sourceType, sourceExpression, elementType)
            .element(varName, elementType, isFinal)
            .metadata(analyzer.hasBreak(), analyzer.hasLabeledContinue(), 
                      false, analyzer.modifiesIteratedCollection(), true);
        
        // Only analyze body and add operations/terminal if no unconvertible patterns
        if (!hasUnconvertiblePatterns) {
            analyzeAndAddOperations(body, builder, varName, compilationUnit);
        }
        // If hasUnconvertiblePatterns, no terminal is set → NOT_CONVERTIBLE
        
        LoopModel model = builder.build();
        return new ExtractedLoop(model, body);
    }
    
    private SourceType determineSourceType(Expression iterable) {
        ITypeBinding binding = iterable.resolveTypeBinding();
        if (binding != null) {
            if (binding.isArray()) {
                return SourceType.ARRAY;
            }
            // Check for Collection
            if (isCollection(binding)) {
                return SourceType.COLLECTION;
            }
        }
        return SourceType.ITERABLE;
    }
    
    private boolean isCollection(ITypeBinding binding) {
        if (binding == null) {
            return false;
        }
        
        ITypeBinding erasure = binding.getErasure();
        if (erasure == null) {
            return false;
        }
        
        String qualifiedName = erasure.getQualifiedName();
        if ("java.util.Collection".equals(qualifiedName)
                || "java.util.List".equals(qualifiedName)
                || "java.util.Set".equals(qualifiedName)
                || "java.util.Queue".equals(qualifiedName)
                || "java.util.Deque".equals(qualifiedName)) {
            return true;
        }
        
        // Check interfaces
        for (ITypeBinding iface : erasure.getInterfaces()) {
            if (isCollection(iface)) {
                return true;
            }
        }
        
        // Check superclass
        ITypeBinding superclass = erasure.getSuperclass();
        if (superclass != null && isCollection(superclass)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Analyzes the loop body and populates the builder with operations and terminal.
     * 
     * <p>Detects the following patterns (conservatively — if no pattern matches,
     * the builder gets no terminal, and the loop will NOT be converted):</p>
     * <ul>
     *   <li>Simple body (single method call, no control flow) → ForEachTerminal</li>
     *   <li>IF with continue + remaining body → FilterOp + remaining patterns</li>
     *   <li>IF guard (no else, last statement) → FilterOp + nested body</li>
     *   <li>Variable declaration + remaining → MapOp + remaining patterns</li>
     *   <li>collection.add(expr) → CollectTerminal</li>
     *   <li>Accumulator patterns (+=, ++, etc.) → ReduceTerminal</li>
     *   <li>if (cond) return true/false (last) → MatchTerminal</li>
     * </ul>
     * 
     * <p>If the body contains unconvertible patterns (bare return, throw, 
     * assignments to external variables, etc.), no terminal is set and the
     * loop is left unchanged.</p>
     */
    private void analyzeAndAddOperations(Statement body, LoopModelBuilder builder, String varName, CompilationUnit compilationUnit) {
        java.util.List<Statement> statements = new java.util.ArrayList<>();
        
        if (body instanceof Block block) {
            for (Object stmt : block.statements()) {
                statements.add((Statement) stmt);
            }
        } else {
            statements.add(body);
        }
        
        // Guard: empty body → no conversion
        if (statements.isEmpty()) {
            return; // No terminal → NOT_CONVERTIBLE
        }
        
        // Guard: reject bodies with throw statements or bare returns (not boolean returns)
        if (containsUnconvertibleStatements(statements)) {
            return; // No terminal → NOT_CONVERTIBLE
        }
        
        // Guard: if body has multiple variable declarations, use simple forEach
        // instead of trying to decompose into map chains (which can be incorrect)
        int varDeclCount = countVariableDeclarations(statements);
        if (varDeclCount > 1) {
            // Convert directly to simple forEach with body as-is
            addSimpleForEachTerminal(statements, builder);
            return;
        }
        
        // Guard: if body has multiple side-effect statements (e.g., multiple println),
        // use simple forEach instead of trying to chain as map operations
        if (statements.size() > 1 && allSideEffects(statements)) {
            addSimpleForEachTerminal(statements, builder);
            return;
        }
        
        // Try to analyze the statements into operations + terminal
        analyzeStatements(statements, builder, varName, compilationUnit, false);
    }
    
    /**
     * Checks if all statements are simple side effects (method calls).
     */
    private boolean allSideEffects(java.util.List<Statement> statements) {
        for (Statement stmt : statements) {
            if (!isSimpleSideEffect(stmt)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Counts the number of variable declaration statements in the list.
     */
    private int countVariableDeclarations(java.util.List<Statement> statements) {
        int count = 0;
        for (Statement stmt : statements) {
            if (stmt instanceof VariableDeclarationStatement) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Adds a simple forEach terminal with all statements in the body.
     * Used when the body is too complex to decompose into map/filter chains.
     */
    private void addSimpleForEachTerminal(java.util.List<Statement> statements, LoopModelBuilder builder) {
        java.util.List<String> bodyStmts = new java.util.ArrayList<>();
        for (Statement stmt : statements) {
            String stmtStr = stmt.toString();
            // Don't remove trailing semicolons for block statements
            bodyStmts.add(stmtStr);
        }
        builder.forEach(bodyStmts, false);
    }
    
    /**
     * Recursively analyzes a list of statements, building operations and setting the terminal.
     * Implements V1-equivalent pattern detection for all supported transformations.
     * 
     * @param statements     the statements to analyze
     * @param builder        the model builder
     * @param varName        the current loop/pipeline variable name
     * @param compilationUnit the compilation unit (for type resolution)
     * @param hasOperations  whether any intermediate operations (filter/map) have been added
     */
    private void analyzeStatements(java.util.List<Statement> statements, LoopModelBuilder builder, 
                                    String varName, CompilationUnit compilationUnit, boolean hasOperations) {
        String currentVarName = varName;
        
        for (int i = 0; i < statements.size(); i++) {
            Statement stmt = statements.get(i);
            boolean isLast = (i == statements.size() - 1);
            
            // === IF STATEMENT patterns ===
            if (stmt instanceof IfStatement ifStmt && ifStmt.getElseStatement() == null) {
                Statement thenStmt = ifStmt.getThenStatement();
                
                // Pattern: if (cond) continue; → filter(x -> !(cond))
                if (isContinueStatement(thenStmt)) {
                    String condition = ifStmt.getExpression().toString();
                    builder.filter("!(" + condition + ")");
                    attachComments(builder.getLastOperation(), stmt, compilationUnit);
                    hasOperations = true;
                    continue;
                }
                
                // Pattern: if (cond) return true/false; (last stmt) → MatchTerminal
                if (isLast && isEarlyReturnIf(ifStmt, statements)) {
                    addMatchTerminal(ifStmt, builder, currentVarName);
                    return; // terminal set
                }
                
                // Pattern: if (cond) { body } (last stmt) → filter(cond) + body operations
                if (isLast) {
                    String condition = ifStmt.getExpression().toString();
                    builder.filter("(" + condition + ")");
                    attachComments(builder.getLastOperation(), stmt, compilationUnit);
                    hasOperations = true;
                    analyzeAndAddOperations(ifStmt.getThenStatement(), builder, currentVarName, compilationUnit);
                    return; // terminal set by recursion
                }
                
                // Pattern: if (cond) { ... } (NOT last) → side-effect MAP that wraps the if
                // V1 NON_TERMINAL: wrap intermediate if-statements as map(var -> { if(...){...} return var; })
                builder.sideEffectMap(stmt.toString(), currentVarName);
                attachComments(builder.getLastOperation(), stmt, compilationUnit);
                hasOperations = true;
                continue;
            }
            
            // === VARIABLE DECLARATION pattern ===
            // Type x = expr; → map(var -> expr) with renamed pipeline variable
            if (stmt instanceof VariableDeclarationStatement varDecl && !isLast) {
                @SuppressWarnings("unchecked")
                java.util.List<VariableDeclarationFragment> fragments = varDecl.fragments();
                if (fragments.size() == 1) {
                    VariableDeclarationFragment frag = fragments.get(0);
                    Expression init = frag.getInitializer();
                    if (init != null) {
                        String newVarName = frag.getName().getIdentifier();
                        String mapExpr = init.toString();
                        
                        // Check if remaining non-terminal statements need wrapping
                        // (V1 VARIABLE_DECLARATION.shouldWrapRemaining pattern)
                        if (shouldWrapRemainingInMap(statements, i, newVarName)) {
                            builder.map(mapExpr, varDecl.getType().toString(), newVarName);
                            attachComments(builder.getLastOperation(), stmt, compilationUnit);
                            currentVarName = newVarName;
                            hasOperations = true;
                            // Wrap remaining non-terminal statements as a single side-effect MAP
                            i = wrapRemainingNonTerminals(statements, i + 1, builder, currentVarName);
                            // Now i points to the last statement (terminal) — continue loop to process it
                            continue;
                        }
                        
                        builder.map(mapExpr, varDecl.getType().toString(), newVarName);
                        attachComments(builder.getLastOperation(), stmt, compilationUnit);
                        currentVarName = newVarName;
                        hasOperations = true;
                        continue;
                    }
                }
            }
            
            // === ASSIGNMENT MAP pattern ===
            // x = expr; where x is the current pipeline variable → map(x -> expr)
            if (stmt instanceof ExpressionStatement assignExprStmt && !isLast) {
                Expression assignExpr = assignExprStmt.getExpression();
                if (assignExpr instanceof Assignment assign
                        && assign.getOperator() == Assignment.Operator.ASSIGN) {
                    Expression lhs = assign.getLeftHandSide();
                    if (lhs instanceof SimpleName name 
                            && name.getIdentifier().equals(currentVarName)) {
                        String mapExpr = assign.getRightHandSide().toString();
                        builder.map(mapExpr, null, currentVarName);
                        attachComments(builder.getLastOperation(), stmt, compilationUnit);
                        hasOperations = true;
                        continue;
                    }
                }
            }
            
            // === TERMINAL patterns (last statement only) ===
            if (isLast) {
                // Collect: collection.add(expr)
                if (isCollectPattern(stmt)) {
                    addCollectTerminal(stmt, builder, currentVarName);
                    return; // terminal set
                }
                
                // Reduce: += , ++, *=, count = count + 1, etc.
                if (isReducePattern(stmt)) {
                    addReduceTerminal(stmt, builder, currentVarName);
                    return; // terminal set
                }
                
                // ForEach: everything else at the end
                java.util.List<String> bodyStmts = new java.util.ArrayList<>();
                String stmtStr = stmt.toString();
                if (stmtStr.endsWith(";\n") || stmtStr.endsWith(";")) {
                    stmtStr = stmtStr.replaceAll(";\\s*$", "").trim();
                }
                bodyStmts.add(stmtStr);
                builder.forEach(bodyStmts, hasOperations || builder.hasOperations());
                return; // terminal set
            }
            
            // === NON-TERMINAL side-effect pattern ===
            // Intermediate statements that don't match any pattern above are wrapped as 
            // side-effect MAP: map(var -> { stmt; return var; })
            // This is the V1 NON_TERMINAL handler equivalent
            if (isCollectPattern(stmt) || isReducePattern(stmt) || isSimpleSideEffect(stmt)) {
                builder.sideEffectMap(stmt.toString(), currentVarName);
                attachComments(builder.getLastOperation(), stmt, compilationUnit);
                hasOperations = true;
                continue;
            }
            
            // Unknown intermediate statement → cannot convert
            return; // No terminal → NOT_CONVERTIBLE
        }
    }
    
    /**
     * Checks if remaining non-terminal statements after a variable declaration
     * should be wrapped in a single MAP block.
     * 
     * <p>V1 wraps remaining statements when they contain intermediate if-statements
     * or other side-effect patterns that need to be combined into a single map block.</p>
     */
    private boolean shouldWrapRemainingInMap(java.util.List<Statement> statements, int currentIndex, String newVarName) {
        // Look through remaining non-terminal statements
        for (int j = currentIndex + 1; j < statements.size() - 1; j++) {
            Statement stmt = statements.get(j);
            
            // If there's an intermediate if-statement (not continue, not return), wrap
            if (stmt instanceof IfStatement ifStmt && ifStmt.getElseStatement() == null) {
                Statement thenStmt = ifStmt.getThenStatement();
                if (!isContinueStatement(thenStmt) && !isEarlyReturnIf(ifStmt, statements)) {
                    return true;
                }
            }
            
            // Don't wrap if the statement is an assignment to the produced variable 
            // (handled by ASSIGNMENT_MAP pattern)
            if (stmt instanceof ExpressionStatement es) {
                Expression expr = es.getExpression();
                if (expr instanceof Assignment assign 
                        && assign.getOperator() == Assignment.Operator.ASSIGN
                        && assign.getLeftHandSide() instanceof SimpleName sn
                        && sn.getIdentifier().equals(newVarName)) {
                    return false;
                }
            }
        }
        return false;
    }
    
    /**
     * Wraps remaining non-terminal statements (from startIndex to size-2) in a single
     * side-effect MAP operation, then returns the index of the last statement (terminal).
     * 
     * @return the index of the last statement (for the caller to process as terminal)
     */
    private int wrapRemainingNonTerminals(java.util.List<Statement> statements, int startIndex, 
                                           LoopModelBuilder builder, String currentVarName) {
        int lastIndex = statements.size() - 1;
        
        // Collect non-terminal statements into a block string
        StringBuilder blockStr = new StringBuilder();
        for (int j = startIndex; j < lastIndex; j++) {
            blockStr.append(statements.get(j).toString());
        }
        
        if (blockStr.length() > 0) {
            builder.sideEffectMap(blockStr.toString(), currentVarName);
        }
        
        // Return the index right before the last statement so the loop will process it next
        return lastIndex - 1;
    }
    
    /**
     * Checks if the statements contain patterns that are always unconvertible.
     */
    private boolean containsUnconvertibleStatements(java.util.List<Statement> statements) {
        for (Statement stmt : statements) {
            // Bare return (not inside an if-return-boolean pattern) → unconvertible
            if (stmt instanceof ReturnStatement returnStmt) {
                Expression expr = returnStmt.getExpression();
                // return; (void) → unconvertible
                if (expr == null) return true;
                // return someExpr; (not boolean literal) → unconvertible
                // Note: if-return-boolean inside an IfStatement is handled as a match pattern
                if (!(expr instanceof BooleanLiteral)) return true;
            }
            // Throw → unconvertible
            if (stmt instanceof ThrowStatement) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Checks if a statement is a simple side effect (method call, etc.)
     * that can be safely included in a forEach body.
     */
    private boolean isSimpleSideEffect(Statement stmt) {
        if (stmt instanceof ExpressionStatement exprStmt) {
            Expression expr = exprStmt.getExpression();
            // Method invocations like System.out.println(x) are simple side effects
            if (expr instanceof MethodInvocation) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isContinueStatement(Statement stmt) {
        if (stmt instanceof ContinueStatement) {
            return true;
        }
        if (stmt instanceof Block block) {
            @SuppressWarnings("unchecked")
            java.util.List<Statement> stmts = block.statements();
            return stmts.size() == 1 && stmts.get(0) instanceof ContinueStatement;
        }
        return false;
    }
    
    /**
     * Detects early return patterns for anyMatch/noneMatch/allMatch.
     * Pattern: if (condition) return true/false; (as last statement)
     */
    private boolean isEarlyReturnIf(IfStatement ifStmt, java.util.List<Statement> statements) {
        Statement thenStmt = ifStmt.getThenStatement();
        if (thenStmt instanceof ReturnStatement returnStmt) {
            return returnStmt.getExpression() instanceof BooleanLiteral;
        }
        if (thenStmt instanceof Block block) {
            @SuppressWarnings("unchecked")
            java.util.List<Statement> stmts = block.statements();
            if (stmts.size() == 1 && stmts.get(0) instanceof ReturnStatement returnStmt) {
                return returnStmt.getExpression() instanceof BooleanLiteral;
            }
        }
        return false;
    }
    
    private void addMatchTerminal(IfStatement ifStmt, LoopModelBuilder builder, String varName) {
        String condition = ifStmt.getExpression().toString();
        
        // Determine match type from the return value
        Statement thenStmt = ifStmt.getThenStatement();
        ReturnStatement returnStmt;
        if (thenStmt instanceof ReturnStatement rs) {
            returnStmt = rs;
        } else {
            @SuppressWarnings("unchecked")
            java.util.List<Statement> stmts = ((Block) thenStmt).statements();
            returnStmt = (ReturnStatement) stmts.get(0);
        }
        
        boolean returnsTrue = ((BooleanLiteral) returnStmt.getExpression()).booleanValue();
        
        if (returnsTrue) {
            // if (cond) return true; → anyMatch(cond)
            builder.anyMatch(condition);
        } else {
            // if (cond) return false; 
            // Check if the condition is already negated → allMatch with inner condition
            Expression condExpr = ifStmt.getExpression();
            if (condExpr instanceof PrefixExpression prefix 
                    && prefix.getOperator() == PrefixExpression.Operator.NOT) {
                // if (!innerCond) return false; → allMatch(innerCond)
                String innerCondition = prefix.getOperand().toString();
                builder.allMatch(innerCondition);
            } else if (condition.startsWith("!(") && condition.endsWith(")")) {
                // if (!(innerCond)) return false; → allMatch(innerCond)
                String innerCondition = condition.substring(2, condition.length() - 1);
                builder.allMatch(innerCondition);
            } else {
                // if (cond) return false; → noneMatch(cond)
                builder.noneMatch(condition);
            }
        }
    }
    
    /**
     * Detects collection.add(expr) patterns.
     */
    private boolean isCollectPattern(Statement stmt) {
        if (stmt instanceof ExpressionStatement exprStmt) {
            Expression expr = exprStmt.getExpression();
            if (expr instanceof MethodInvocation mi) {
                String methodName = mi.getName().getIdentifier();
                return "add".equals(methodName) && mi.arguments().size() == 1;
            }
        }
        return false;
    }
    
    private void addCollectTerminal(Statement stmt, LoopModelBuilder builder, String varName) {
        ExpressionStatement exprStmt = (ExpressionStatement) stmt;
        MethodInvocation mi = (MethodInvocation) exprStmt.getExpression();
        
        // Determine collector type from the target collection type
        Expression target = mi.getExpression();
        ITypeBinding targetType = target != null ? target.resolveTypeBinding() : null;
        
        org.sandbox.functional.core.terminal.CollectTerminal.CollectorType collectorType =
            org.sandbox.functional.core.terminal.CollectTerminal.CollectorType.TO_LIST;
        
        if (targetType != null) {
            String typeName = targetType.getErasure() != null ? targetType.getErasure().getQualifiedName() : "";
            if (typeName.contains("Set")) {
                collectorType = org.sandbox.functional.core.terminal.CollectTerminal.CollectorType.TO_SET;
            }
        }
        
        String targetVar = target != null ? target.toString() : "result";
        
        // Check if the added expression is not identity (needs a map before collect)
        Expression addedExpr = (Expression) mi.arguments().get(0);
        if (addedExpr instanceof SimpleName sn && sn.getIdentifier().equals(varName)) {
            // Identity mapping - just collect
            builder.collect(collectorType, targetVar);
        } else {
            // Non-identity: add map before collect, infer type from expression
            String mapTargetType = null;
            ITypeBinding exprType = addedExpr.resolveTypeBinding();
            if (exprType != null) {
                mapTargetType = exprType.getName();
            }
            builder.map(addedExpr.toString(), mapTargetType);
            builder.collect(collectorType, targetVar);
        }
    }
    
    /**
     * Detects accumulator patterns like sum += x, count++, count = count + 1,
     * max = Math.max(max, x), etc.
     */
    private boolean isReducePattern(Statement stmt) {
        if (stmt instanceof ExpressionStatement exprStmt) {
            Expression expr = exprStmt.getExpression();
            // Compound assignment: sum += x, product *= x
            if (expr instanceof Assignment assign) {
                Assignment.Operator op = assign.getOperator();
                if (op == Assignment.Operator.PLUS_ASSIGN 
                    || op == Assignment.Operator.MINUS_ASSIGN
                    || op == Assignment.Operator.TIMES_ASSIGN) {
                    return true;
                }
                // Plain assignment with infix accumulator: count = count + 1
                if (op == Assignment.Operator.ASSIGN && assign.getLeftHandSide() instanceof SimpleName) {
                    if (isInfixAccumulator(assign)) return true;
                    if (isMathMaxMinPattern(assign)) return true;
                }
            }
            // Postfix: count++, count--
            if (expr instanceof PostfixExpression) {
                return true;
            }
            // Prefix: ++count, --count
            if (expr instanceof PrefixExpression prefix) {
                PrefixExpression.Operator op = prefix.getOperator();
                return op == PrefixExpression.Operator.INCREMENT 
                    || op == PrefixExpression.Operator.DECREMENT;
            }
        }
        return false;
    }
    
    /**
     * Checks if a plain assignment is an infix accumulator pattern:
     * count = count + 1, result = result * value, etc.
     */
    private boolean isInfixAccumulator(Assignment assign) {
        String varName = ((SimpleName) assign.getLeftHandSide()).getIdentifier();
        Expression rhs = assign.getRightHandSide();
        if (rhs instanceof InfixExpression infix) {
            Expression left = infix.getLeftOperand();
            if (left instanceof SimpleName sn && sn.getIdentifier().equals(varName)) {
                InfixExpression.Operator op = infix.getOperator();
                return op == InfixExpression.Operator.PLUS
                    || op == InfixExpression.Operator.MINUS
                    || op == InfixExpression.Operator.TIMES;
            }
        }
        return false;
    }
    
    private void addReduceTerminal(Statement stmt, LoopModelBuilder builder, String varName) {
        ExpressionStatement exprStmt = (ExpressionStatement) stmt;
        Expression expr = exprStmt.getExpression();
        
        if (expr instanceof Assignment assign) {
            Assignment.Operator op = assign.getOperator();
            String accumVar = assign.getLeftHandSide().toString();
            String valueExpr = assign.getRightHandSide().toString();
            String accumType = resolveReducerType(assign.getLeftHandSide());
            
            if (op == Assignment.Operator.PLUS_ASSIGN) {
                // sum += value → .map(var -> value).reduce(sum, Integer/Long/Double::sum)
                if (!valueExpr.equals(varName)) {
                    builder.map(valueExpr, null);
                }
                builder.terminal(new org.sandbox.functional.core.terminal.ReduceTerminal(
                    accumVar, accumType + "::sum", null,
                    org.sandbox.functional.core.terminal.ReduceTerminal.ReduceType.SUM, accumVar));
            } else if (op == Assignment.Operator.TIMES_ASSIGN) {
                if (!valueExpr.equals(varName)) {
                    builder.map(valueExpr, null);
                }
                builder.terminal(new org.sandbox.functional.core.terminal.ReduceTerminal(
                    accumVar, "(a, b) -> a * b", null,
                    org.sandbox.functional.core.terminal.ReduceTerminal.ReduceType.PRODUCT, accumVar));
            } else if (op == Assignment.Operator.MINUS_ASSIGN) {
                if (!valueExpr.equals(varName)) {
                    builder.map(valueExpr, null);
                }
                builder.terminal(new org.sandbox.functional.core.terminal.ReduceTerminal(
                    accumVar, "(a, b) -> a - b", null,
                    org.sandbox.functional.core.terminal.ReduceTerminal.ReduceType.CUSTOM, accumVar));
            } else if (op == Assignment.Operator.ASSIGN) {
                if (assign.getRightHandSide() instanceof InfixExpression infix) {
                    addInfixReduceTerminal(infix, builder, varName, accumVar);
                } else if (isMathMaxMinPattern(assign)) {
                    addMathMaxMinReduce(assign, builder, accumVar);
                }
            }
        } else if (expr instanceof PostfixExpression postfix) {
            // i++ → .map(var -> 1).reduce(i, Integer::sum)
            String accumVar = postfix.getOperand().toString();
            String accumType = resolveReducerType(postfix.getOperand());
            String mapValue = isLongType(accumType) ? "1L" : "1";
            builder.map(mapValue, null);
            builder.terminal(new org.sandbox.functional.core.terminal.ReduceTerminal(
                accumVar, accumType + "::sum", null,
                org.sandbox.functional.core.terminal.ReduceTerminal.ReduceType.COUNT, accumVar));
        } else if (expr instanceof PrefixExpression prefix) {
            String accumVar = prefix.getOperand().toString();
            String accumType = resolveReducerType(prefix.getOperand());
            String mapValue = isLongType(accumType) ? "1L" : "1";
            if (prefix.getOperator() == PrefixExpression.Operator.INCREMENT) {
                builder.map(mapValue, null);
                builder.terminal(new org.sandbox.functional.core.terminal.ReduceTerminal(
                    accumVar, accumType + "::sum", null,
                    org.sandbox.functional.core.terminal.ReduceTerminal.ReduceType.COUNT, accumVar));
            } else {
                builder.map(mapValue, null);
                builder.terminal(new org.sandbox.functional.core.terminal.ReduceTerminal(
                    accumVar, "(a, b) -> a - b", null,
                    org.sandbox.functional.core.terminal.ReduceTerminal.ReduceType.CUSTOM, accumVar));
            }
        }
    }
    
    /**
     * Resolves the boxed type name for a reducer variable (Integer, Long, Double, etc.)
     * Falls back to "Integer" if type cannot be resolved.
     */
    private String resolveReducerType(Expression operand) {
        ITypeBinding binding = operand.resolveTypeBinding();
        if (binding != null) {
            String name = binding.isPrimitive() ? getBoxedTypeName(binding.getName()) : binding.getName();
            if (name != null) return name;
        }
        return "Integer";
    }
    
    private String getBoxedTypeName(String primitiveName) {
        return switch (primitiveName) {
            case "int" -> "Integer";
            case "long" -> "Long";
            case "double" -> "Double";
            case "float" -> "Float";
            default -> "Integer";
        };
    }
    
    private boolean isLongType(String typeName) {
        return "Long".equals(typeName) || "long".equals(typeName);
    }
    
    /**
     * Checks if an assignment is a Math.max or Math.min pattern.
     * Pattern: max = Math.max(max, x) or min = Math.min(min, x)
     */
    private boolean isMathMaxMinPattern(Assignment assign) {
        Expression rhs = assign.getRightHandSide();
        if (rhs instanceof MethodInvocation mi) {
            String methodName = mi.getName().getIdentifier();
            Expression expr = mi.getExpression();
            if (expr instanceof SimpleName sn && "Math".equals(sn.getIdentifier())) {
                return ("max".equals(methodName) || "min".equals(methodName)) && mi.arguments().size() == 2;
            }
        }
        return false;
    }
    
    /**
     * Adds Math.max/min reduce terminal.
     * Pattern: max = Math.max(max, num) → .reduce(max, Math::max)
     */
    private void addMathMaxMinReduce(Assignment assign, LoopModelBuilder builder, String accumVar) {
        MethodInvocation mi = (MethodInvocation) assign.getRightHandSide();
        String methodName = mi.getName().getIdentifier();
        
        org.sandbox.functional.core.terminal.ReduceTerminal.ReduceType type = 
            "max".equals(methodName) ? org.sandbox.functional.core.terminal.ReduceTerminal.ReduceType.MAX
                                     : org.sandbox.functional.core.terminal.ReduceTerminal.ReduceType.MIN;
        
        builder.terminal(new org.sandbox.functional.core.terminal.ReduceTerminal(
            accumVar, "Math::" + methodName, null, type, accumVar));
    }
    
    private void addInfixReduceTerminal(InfixExpression infix, LoopModelBuilder builder, 
                                         String varName, String accumVar) {
        String rightOperand = infix.getRightOperand().toString();
        InfixExpression.Operator op = infix.getOperator();
        String accumType = resolveReducerType(infix.getLeftOperand());
        
        // Only add a MAP operation if the right operand is NOT the identity (loop variable)
        // e.g., count = count + 1 → .map(item -> 1).reduce(count, Integer::sum)
        // but result = result + item → .reduce(result, (a, b) -> a + b) [no map needed]
        if (!rightOperand.equals(varName)) {
            builder.map(rightOperand, null);
        }
        
        if (op == InfixExpression.Operator.PLUS) {
            // For String types, check @NotNull annotation to decide between String::concat and lambda
            if ("String".equals(accumType)) {
                // Check if the accumulator variable has @NotNull annotation
                boolean isNullSafe = org.sandbox.jdt.internal.corext.util.VariableResolver
                    .hasNotNullAnnotation(infix, accumVar);
                if (isNullSafe) {
                    // @NotNull present: safe to use String::concat method reference
                    builder.terminal(new org.sandbox.functional.core.terminal.ReduceTerminal(
                        accumVar, "String::concat", null,
                        org.sandbox.functional.core.terminal.ReduceTerminal.ReduceType.STRING_CONCAT, accumVar));
                } else {
                    // No @NotNull: use null-safe lambda (a, b) -> a + b
                    builder.terminal(new org.sandbox.functional.core.terminal.ReduceTerminal(
                        accumVar, "(a, b) -> a + b", null,
                        org.sandbox.functional.core.terminal.ReduceTerminal.ReduceType.CUSTOM, accumVar));
                }
            } else {
                builder.terminal(new org.sandbox.functional.core.terminal.ReduceTerminal(
                    accumVar, accumType + "::sum", null,
                    org.sandbox.functional.core.terminal.ReduceTerminal.ReduceType.SUM, accumVar));
            }
        } else if (op == InfixExpression.Operator.TIMES) {
            builder.terminal(new org.sandbox.functional.core.terminal.ReduceTerminal(
                accumVar, "(a, b) -> a * b", null,
                org.sandbox.functional.core.terminal.ReduceTerminal.ReduceType.PRODUCT, accumVar));
        } else {
            builder.terminal(new org.sandbox.functional.core.terminal.ReduceTerminal(
                accumVar, "(a, b) -> a - b", null,
                org.sandbox.functional.core.terminal.ReduceTerminal.ReduceType.CUSTOM, accumVar));
        }
    }
    
    /**
     * Attaches extracted comments from an AST node to an Operation.
     * If the operation is a FilterOp or MapOp and has associated comments
     * in the source, they are stored in the operation for preservation
     * during rendering.
     * 
     * @param operation the operation to attach comments to (can be null)
     * @param node the AST node to extract comments from
     * @param cu the compilation unit (can be null)
     */
    private void attachComments(org.sandbox.functional.core.operation.Operation operation, 
                                 ASTNode node, CompilationUnit cu) {
        if (operation == null || cu == null) {
            return;
        }
        java.util.List<String> comments = extractComments(node, cu);
        if (comments.isEmpty()) {
            return;
        }
        if (operation instanceof org.sandbox.functional.core.operation.FilterOp filterOp) {
            filterOp.addComments(comments);
        } else if (operation instanceof org.sandbox.functional.core.operation.MapOp mapOp) {
            mapOp.addComments(comments);
        }
    }
    
    /**
     * Extracts comments associated with an AST node.
     * 
     * @param node the AST node to extract comments from
     * @param cu the compilation unit (can be null)
     * @return list of comment strings, never null
     */
    @SuppressWarnings("unchecked")
    private java.util.List<String> extractComments(ASTNode node, CompilationUnit cu) {
        java.util.List<String> comments = new java.util.ArrayList<>();
        
        if (cu == null || node == null) {
            return comments;
        }
        
        java.util.List<Comment> commentList = cu.getCommentList();
        if (commentList == null || commentList.isEmpty()) {
            return comments;
        }
        
        int nodeStart = node.getStartPosition();
        int nodeEnd = nodeStart + node.getLength();
        int nodeStartLine = cu.getLineNumber(nodeStart);
        
        for (Comment comment : commentList) {
            int commentStart = comment.getStartPosition();
            int commentEnd = commentStart + comment.getLength();
            int commentEndLine = cu.getLineNumber(commentEnd);
            
            // Associate comments that are:
            // 1. On the line immediately before the node, OR
            // 2. On the same line as the node (trailing comment), OR
            // 3. Within the node's span (embedded comment)
            boolean isLeadingComment = commentEndLine == nodeStartLine - 1 || 
                                       (commentEndLine == nodeStartLine && commentEnd <= nodeStart);
            boolean isEmbeddedComment = commentStart >= nodeStart && commentEnd <= nodeEnd;
            
            if (isLeadingComment || isEmbeddedComment) {
                String commentText = extractCommentText(comment, cu);
                if (commentText != null && !commentText.isEmpty()) {
                    comments.add(commentText);
                }
            }
        }
        
        return comments;
    }
    
    /**
     * Extracts the text content from a Comment node.
     * 
     * @param comment the comment node
     * @param cu the compilation unit
     * @return the comment text without delimiters, or null if not available
     */
    private String extractCommentText(Comment comment, CompilationUnit cu) {
        try {
            // Get the original source from the compilation unit's type root
            org.eclipse.jdt.core.ICompilationUnit javaElement = 
                (org.eclipse.jdt.core.ICompilationUnit) cu.getJavaElement();
            if (javaElement == null) {
                return null;
            }
            
            String source = javaElement.getSource();
            if (source == null) {
                return null;
            }
            
            int start = comment.getStartPosition();
            int length = comment.getLength();
            
            if (start < 0 || start + length > source.length()) {
                return null;
            }
            
            String commentStr = source.substring(start, start + length);
            
            // Remove comment delimiters
            if (comment.isLineComment()) {
                // Remove leading //
                commentStr = commentStr.replaceFirst("^//\\s*", "");
            } else if (comment.isBlockComment()) {
                // Remove /* and */
                commentStr = commentStr.replaceFirst("^/\\*\\s*", "");
                commentStr = commentStr.replaceFirst("\\s*\\*/$", "");
            } else if (comment instanceof Javadoc) {
                // Remove /** and */
                commentStr = commentStr.replaceFirst("^/\\*\\*\\s*", "");
                commentStr = commentStr.replaceFirst("\\s*\\*/$", "");
            }
            
            return commentStr.trim();
        } catch (Exception e) {
            // If extraction fails, return null
            return null;
        }
    }
    
    /**
     * Visitor to analyze loop body for truly unconvertible control flow.
     * 
     * <p>Tracks patterns that CANNOT be converted to stream operations:
     * <ul>
     *   <li>break statements</li>
     *   <li>labeled continue statements</li>
     *   <li>try-catch statements (checked exceptions require Try-with-resources or explicit handling)</li>
     *   <li>synchronized statements (synchronization semantics differ in streams)</li>
     *   <li>traditional for loops (complex control flow)</li>
     *   <li>while loops (complex control flow)</li>
     *   <li>do-while loops (complex control flow)</li>
     * </ul>
     * </p>
     * 
     * <p>Note: unlabeled continue, return, and add() calls are potentially
     * convertible as filter, match, and collect patterns respectively.
     * Those are handled by analyzeAndAddOperations().</p>
     */
    private static class LoopBodyAnalyzer extends ASTVisitor {
        private boolean hasBreak = false;
        private boolean hasLabeledContinue = false;
        private boolean hasTryCatch = false;
        private boolean hasSynchronized = false;
        private boolean hasNestedLoop = false;
        private boolean hasVoidReturn = false;
        private boolean hasIfElse = false;
        private boolean modifiesIteratedCollection = false;
        private String iteratedCollectionName;
        
        @Override
        public boolean visit(BreakStatement node) {
            hasBreak = true;
            return false;
        }
        
        @Override
        public boolean visit(ContinueStatement node) {
            // Only labeled continue is truly unconvertible
            // Unlabeled continue can be converted to a negated filter
            if (node.getLabel() != null) {
                hasLabeledContinue = true;
            }
            return false;
        }
        
        @Override
        public boolean visit(IfStatement node) {
            // If-else patterns don't map cleanly to stream operations
            // e.g., if (cond) list1.add(x) else list2.add(x)
            if (node.getElseStatement() != null) {
                hasIfElse = true;
            }
            return true; // Continue visiting to detect nested patterns
        }
        
        @Override
        public boolean visit(ReturnStatement node) {
            // Void return exits the enclosing method, not the loop
            // This cannot be converted to stream operations
            if (node.getExpression() == null) {
                hasVoidReturn = true;
            }
            // Note: boolean returns (return true/false) are potentially convertible
            // as match patterns — handled separately in analyzeStatements()
            return false;
        }
        
        @Override
        public boolean visit(TryStatement node) {
            // Try-catch cannot be easily converted to stream operations
            // due to checked exception handling requirements
            hasTryCatch = true;
            return false;
        }
        
        @Override
        public boolean visit(SynchronizedStatement node) {
            // Synchronized blocks have different semantics in streams
            // (parallelStream vs sequential processing)
            hasSynchronized = true;
            return false;
        }
        
        @Override
        public boolean visit(ForStatement node) {
            // Traditional for loops inside the body → unconvertible
            hasNestedLoop = true;
            return false;
        }
        
        @Override
        public boolean visit(WhileStatement node) {
            // While loops inside the body → unconvertible
            hasNestedLoop = true;
            return false;
        }
        
        @Override
        public boolean visit(DoStatement node) {
            // Do-while loops inside the body → unconvertible
            hasNestedLoop = true;
            return false;
        }
        
        @Override
        public boolean visit(MethodInvocation node) {
            // Issue #670: Detect structural modifications on the iterated collection
            if (iteratedCollectionName != null) {
                Expression receiver = node.getExpression();
                if (receiver instanceof SimpleName receiverName
                        && iteratedCollectionName.equals(receiverName.getIdentifier())) {
                    String methodName = node.getName().getIdentifier();
                    if (java.util.Set.of("remove", "add", "put", "clear", "set", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                            "addAll", "removeAll", "retainAll").contains(methodName)) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        modifiesIteratedCollection = true;
                    }
                }
            }
            return true;
        }
        
        public boolean hasBreak() { return hasBreak; }
        public boolean hasLabeledContinue() { return hasLabeledContinue; }
        public boolean hasTryCatch() { return hasTryCatch; }
        public boolean hasSynchronized() { return hasSynchronized; }
        public boolean hasNestedLoop() { return hasNestedLoop; }
        public boolean hasVoidReturn() { return hasVoidReturn; }
        public boolean hasIfElse() { return hasIfElse; }
        public boolean modifiesIteratedCollection() { return modifiesIteratedCollection; }
        
        /**
         * Sets the name of the iterated collection for modification detection.
         * Must be called before accept() to enable collection modification checking.
         * 
         * @see <a href="https://github.com/carstenartur/sandbox/issues/670">Issue #670</a>
         */
        public void setIteratedCollectionName(String name) {
            this.iteratedCollectionName = name;
        }
    }
}
