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
        
        // Analyze metadata — only track truly unconvertible control flow.
        // continue/return/add() are potentially convertible as filter/match/collect
        // patterns and are handled by analyzeAndAddOperations().
        LoopBodyAnalyzer analyzer = new LoopBodyAnalyzer();
        body.accept(analyzer);
        
        // Build model — only hasBreak and hasLabeledContinue are always unconvertible
        LoopModelBuilder builder = new LoopModelBuilder()
            .source(sourceType, sourceExpression, elementType)
            .element(varName, elementType, isFinal)
            .metadata(analyzer.hasBreak(), analyzer.hasLabeledContinue(), 
                      false, false, true);
        
        // Analyze body and add operations/terminal
        analyzeAndAddOperations(body, builder, varName, compilationUnit);
        
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
        
        // Try to analyze the statements into operations + terminal
        analyzeStatements(statements, builder, varName, compilationUnit, false);
    }
    
    /**
     * Recursively analyzes a list of statements, building operations and setting the terminal.
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
            
            // Pattern 1: if (cond) continue; → filter(x -> !(cond))
            if (stmt instanceof IfStatement ifStmt && ifStmt.getElseStatement() == null) {
                Statement thenStmt = ifStmt.getThenStatement();
                if (isContinueStatement(thenStmt)) {
                    String condition = ifStmt.getExpression().toString();
                    builder.filter("!(" + condition + ")");
                    hasOperations = true;
                    continue;
                }
                
                // Pattern 2: Early return match patterns: if (cond) return true/false;
                if (isLast && isEarlyReturnIf(ifStmt, statements)) {
                    addMatchTerminal(ifStmt, builder, currentVarName);
                    return; // terminal set
                }
                
                // Pattern 3: if (cond) { body } → filter(x -> cond) + body operations
                // Only at the last position and only if no else branch
                if (isLast) {
                    String condition = ifStmt.getExpression().toString();
                    builder.filter("(" + condition + ")");
                    hasOperations = true;
                    // Recurse into then-block
                    analyzeAndAddOperations(ifStmt.getThenStatement(), builder, currentVarName, compilationUnit);
                    return; // terminal set by recursion (or not, if body is unconvertible)
                }
            }
            
            // Pattern 4: Variable declaration: Type x = expr; → map(var -> expr)
            // Only if not the last statement (there must be a consumer after)
            if (stmt instanceof VariableDeclarationStatement varDecl && !isLast) {
                @SuppressWarnings("unchecked")
                java.util.List<VariableDeclarationFragment> fragments = varDecl.fragments();
                if (fragments.size() == 1) {
                    VariableDeclarationFragment frag = fragments.get(0);
                    Expression init = frag.getInitializer();
                    if (init != null) {
                        String newVarName = frag.getName().getIdentifier();
                        String mapExpr = init.toString();
                        // Pass outputVariableName so chained maps use the correct lambda parameter
                        builder.map(mapExpr, varDecl.getType().toString(), newVarName);
                        currentVarName = newVarName;
                        hasOperations = true;
                        continue;
                    }
                }
            }
            
            // Pattern 5: Reassignment of pipeline variable: x = expr; → map(x -> expr)
            // Only matches when the assigned variable is the CURRENT pipeline variable
            // (i.e., was introduced by a previous variable declaration/map or is the loop variable).
            // Does NOT match external variable assignments (e.g., lastItem = item).
            if (stmt instanceof ExpressionStatement exprStmt5 && !isLast) {
                Expression expr5 = exprStmt5.getExpression();
                if (expr5 instanceof Assignment assign5
                        && assign5.getOperator() == Assignment.Operator.ASSIGN) {
                    Expression lhs5 = assign5.getLeftHandSide();
                    if (lhs5 instanceof SimpleName name5 
                            && name5.getIdentifier().equals(currentVarName)) {
                        String mapExpr = assign5.getRightHandSide().toString();
                        // Reassignment keeps the same variable name
                        builder.map(mapExpr, null, currentVarName);
                        hasOperations = true;
                        continue;
                    }
                }
            }
            
            // Pattern 5: collection.add(expr) → CollectTerminal (only at the last position)
            if (isLast && isCollectPattern(stmt)) {
                addCollectTerminal(stmt, builder, currentVarName);
                return; // terminal set
            }
            
            // Pattern 6: Accumulator pattern (+=, ++, etc.) → ReduceTerminal (only at the last position)
            if (isLast && isReducePattern(stmt)) {
                addReduceTerminal(stmt, builder, currentVarName);
                return; // terminal set
            }
            
            // Pattern 7: Simple forEach — all remaining statements become the body
            // This matches when current stmt is the start of a "side-effect only" tail
            if (isLast || isSimpleSideEffect(stmt)) {
                // Collect all remaining statements as forEach body
                java.util.List<String> bodyStmts = new java.util.ArrayList<>();
                for (int j = i; j < statements.size(); j++) {
                    String stmtStr = statements.get(j).toString();
                    if (stmtStr.endsWith(";\n") || stmtStr.endsWith(";")) {
                        stmtStr = stmtStr.replaceAll(";\\s*$", "").trim();
                    }
                    bodyStmts.add(stmtStr);
                }
                // Use forEachOrdered when there are ANY operations in the pipeline
                // (not just local hasOperations, since operations may have been added in outer scope)
                builder.forEach(bodyStmts, hasOperations || builder.hasOperations());
                return; // terminal set
            }
            
            // If we reach here, the statement doesn't match any pattern
            // and is not a simple side-effect → give up (no terminal)
            return;
        }
        
        // Fallback: treat entire body as forEach if we somehow get here
        java.util.List<String> bodyStmts = new java.util.ArrayList<>();
        for (Statement stmt : statements) {
            String stmtStr = stmt.toString();
            if (stmtStr.endsWith(";\n") || stmtStr.endsWith(";")) {
                stmtStr = stmtStr.replaceAll(";\\s*$", "").trim();
            }
            bodyStmts.add(stmtStr);
        }
        builder.forEach(bodyStmts, hasOperations);
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
            // if (cond) return false; → noneMatch(cond) or allMatch(!cond)
            builder.noneMatch(condition);
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
     * Detects accumulator patterns like sum += x, count++, etc.
     */
    private boolean isReducePattern(Statement stmt) {
        if (stmt instanceof ExpressionStatement exprStmt) {
            Expression expr = exprStmt.getExpression();
            // Compound assignment: sum += x, product *= x
            if (expr instanceof Assignment assign) {
                Assignment.Operator op = assign.getOperator();
                return op == Assignment.Operator.PLUS_ASSIGN 
                    || op == Assignment.Operator.MINUS_ASSIGN
                    || op == Assignment.Operator.TIMES_ASSIGN;
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
    
    private void addReduceTerminal(Statement stmt, LoopModelBuilder builder, String varName) {
        ExpressionStatement exprStmt = (ExpressionStatement) stmt;
        Expression expr = exprStmt.getExpression();
        
        if (expr instanceof Assignment assign) {
            Assignment.Operator op = assign.getOperator();
            String accumVar = assign.getLeftHandSide().toString();
            String valueExpr = assign.getRightHandSide().toString();
            
            if (op == Assignment.Operator.PLUS_ASSIGN) {
                builder.terminal(new org.sandbox.functional.core.terminal.ReduceTerminal(
                    "0", "(" + accumVar + ", " + varName + ") -> " + accumVar + " + " + valueExpr, null,
                    org.sandbox.functional.core.terminal.ReduceTerminal.ReduceType.SUM, accumVar));
            } else if (op == Assignment.Operator.TIMES_ASSIGN) {
                builder.terminal(new org.sandbox.functional.core.terminal.ReduceTerminal(
                    "1", "(" + accumVar + ", " + varName + ") -> " + accumVar + " * " + valueExpr, null,
                    org.sandbox.functional.core.terminal.ReduceTerminal.ReduceType.PRODUCT, accumVar));
            } else {
                builder.terminal(new org.sandbox.functional.core.terminal.ReduceTerminal(
                    "0", "(" + accumVar + ", " + varName + ") -> " + accumVar + " - " + valueExpr, null,
                    org.sandbox.functional.core.terminal.ReduceTerminal.ReduceType.CUSTOM, accumVar));
            }
        } else if (expr instanceof PostfixExpression postfix) {
            String accumVar = postfix.getOperand().toString();
            builder.terminal(new org.sandbox.functional.core.terminal.ReduceTerminal(
                "0", "(" + accumVar + ", " + varName + ") -> " + accumVar + " + 1", null,
                org.sandbox.functional.core.terminal.ReduceTerminal.ReduceType.COUNT, accumVar));
        } else if (expr instanceof PrefixExpression prefix) {
            String accumVar = prefix.getOperand().toString();
            if (prefix.getOperator() == PrefixExpression.Operator.INCREMENT) {
                builder.terminal(new org.sandbox.functional.core.terminal.ReduceTerminal(
                    "0", "(" + accumVar + ", " + varName + ") -> " + accumVar + " + 1", null,
                    org.sandbox.functional.core.terminal.ReduceTerminal.ReduceType.COUNT, accumVar));
            } else {
                builder.terminal(new org.sandbox.functional.core.terminal.ReduceTerminal(
                    "0", "(" + accumVar + ", " + varName + ") -> " + accumVar + " - 1", null,
                    org.sandbox.functional.core.terminal.ReduceTerminal.ReduceType.CUSTOM, accumVar));
            }
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
     * <p>Only tracks patterns that CANNOT be converted to stream operations:
     * break statements and labeled continue statements.</p>
     * 
     * <p>Note: unlabeled continue, return, and add() calls are potentially
     * convertible as filter, match, and collect patterns respectively.
     * Those are handled by analyzeAndAddOperations().</p>
     */
    private static class LoopBodyAnalyzer extends ASTVisitor {
        private boolean hasBreak = false;
        private boolean hasLabeledContinue = false;
        
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
        
        public boolean hasBreak() { return hasBreak; }
        public boolean hasLabeledContinue() { return hasLabeledContinue; }
    }
}
