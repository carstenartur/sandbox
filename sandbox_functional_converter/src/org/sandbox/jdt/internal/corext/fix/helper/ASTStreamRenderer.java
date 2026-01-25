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

import java.util.List;
import java.util.function.Supplier;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.sandbox.functional.core.model.SourceDescriptor;
import org.sandbox.functional.core.renderer.ASTAwareRenderer;
import org.sandbox.functional.core.renderer.StreamPipelineRenderer;
import org.sandbox.functional.core.terminal.*;

/**
 * JDT AST-based renderer for stream pipeline generation.
 * 
 * <p>This renderer generates JDT AST nodes instead of strings,
 * allowing direct integration with Eclipse refactoring infrastructure.</p>
 * 
 * @see StreamPipelineRenderer
 * @see ASTAwareRenderer
 */
public class ASTStreamRenderer implements ASTAwareRenderer<Expression, Statement, Expression> {
    
    private final AST ast;
    private final CompilationUnit compilationUnit;
    private final Statement originalBody;
    
    public ASTStreamRenderer(AST ast, ASTRewrite rewrite, CompilationUnit compilationUnit, Statement originalBody) {
        this.ast = ast;
        this.compilationUnit = compilationUnit;
        this.originalBody = originalBody;
        // Note: rewrite parameter reserved for future use in complex AST transformations
    }
    
    @Override
    public Expression renderSource(SourceDescriptor source) {
        // Create: collection.stream() or Arrays.stream(array)
        switch (source.type()) {
            case COLLECTION:
                // collection.stream()
                MethodInvocation streamCall = ast.newMethodInvocation();
                streamCall.setExpression(createExpression(source.expression()));
                streamCall.setName(ast.newSimpleName("stream"));
                return streamCall;
                
            case ARRAY:
                // Arrays.stream(array)
                MethodInvocation arraysStream = ast.newMethodInvocation();
                arraysStream.setExpression(ast.newSimpleName("Arrays"));
                arraysStream.setName(ast.newSimpleName("stream"));
                arraysStream.arguments().add(createExpression(source.expression()));
                return arraysStream;
                
            case ITERABLE:
                // StreamSupport.stream(iterable.spliterator(), false)
                MethodInvocation streamSupport = ast.newMethodInvocation();
                streamSupport.setExpression(ast.newSimpleName("StreamSupport"));
                streamSupport.setName(ast.newSimpleName("stream"));
                
                MethodInvocation spliterator = ast.newMethodInvocation();
                spliterator.setExpression(createExpression(source.expression()));
                spliterator.setName(ast.newSimpleName("spliterator"));
                
                streamSupport.arguments().add(spliterator);
                streamSupport.arguments().add(ast.newBooleanLiteral(false));
                return streamSupport;
                
            case INT_RANGE:
                // IntStream.range(0, end) - for simple INT_RANGE with just end value
                MethodInvocation intStreamSimple = ast.newMethodInvocation();
                intStreamSimple.setExpression(ast.newSimpleName("IntStream"));
                intStreamSimple.setName(ast.newSimpleName("range"));
                intStreamSimple.arguments().add(ast.newNumberLiteral("0"));
                intStreamSimple.arguments().add(createExpression(source.expression()));
                return intStreamSimple;
                
            case EXPLICIT_RANGE:
                // IntStream.range(start, end) - for explicit start and end
                MethodInvocation intStream = ast.newMethodInvocation();
                intStream.setExpression(ast.newSimpleName("IntStream"));
                intStream.setName(ast.newSimpleName("range"));
                // Parse start and end from expression (format: "start,end")
                String[] parts = source.expression().split(",");
                if (parts.length != 2 || parts[0].trim().isEmpty() || parts[1].trim().isEmpty()) {
                    throw new IllegalArgumentException("Invalid EXPLICIT_RANGE expression: '" + source.expression()
                            + "'. Expected format 'start,end' with non-empty expressions.");
                }
                intStream.arguments().add(createExpression(parts[0].trim()));
                intStream.arguments().add(createExpression(parts[1].trim()));
                return intStream;
                
            case STREAM:
            default:
                // Already a Stream
                return createExpression(source.expression());
        }
    }
    
    @Override
    public Expression renderFilter(Expression pipeline, String expression, String variableName) {
        // pipeline.filter(var -> expression)
        MethodInvocation filterCall = ast.newMethodInvocation();
        filterCall.setExpression(pipeline);
        filterCall.setName(ast.newSimpleName("filter"));
        filterCall.arguments().add(createLambda(variableName, expression));
        return filterCall;
    }
    
    @Override
    public Expression renderMap(Expression pipeline, String expression, String variableName, String targetType) {
        // pipeline.map(var -> expression)
        MethodInvocation mapCall = ast.newMethodInvocation();
        mapCall.setExpression(pipeline);
        mapCall.setName(ast.newSimpleName("map"));
        mapCall.arguments().add(createLambda(variableName, expression));
        return mapCall;
    }
    
    @Override
    public Expression renderFlatMap(Expression pipeline, String expression, String variableName) {
        // pipeline.flatMap(var -> expression)
        MethodInvocation flatMapCall = ast.newMethodInvocation();
        flatMapCall.setExpression(pipeline);
        flatMapCall.setName(ast.newSimpleName("flatMap"));
        flatMapCall.arguments().add(createLambda(variableName, expression));
        return flatMapCall;
    }
    
    @Override
    public Expression renderPeek(Expression pipeline, String expression, String variableName) {
        // pipeline.peek(var -> expression)
        MethodInvocation peekCall = ast.newMethodInvocation();
        peekCall.setExpression(pipeline);
        peekCall.setName(ast.newSimpleName("peek"));
        peekCall.arguments().add(createLambda(variableName, expression));
        return peekCall;
    }
    
    @Override
    public Expression renderDistinct(Expression pipeline) {
        // pipeline.distinct()
        MethodInvocation distinctCall = ast.newMethodInvocation();
        distinctCall.setExpression(pipeline);
        distinctCall.setName(ast.newSimpleName("distinct"));
        return distinctCall;
    }
    
    @Override
    public Expression renderSorted(Expression pipeline, String comparatorExpression) {
        // pipeline.sorted() oder pipeline.sorted(comparator)
        MethodInvocation sortedCall = ast.newMethodInvocation();
        sortedCall.setExpression(pipeline);
        sortedCall.setName(ast.newSimpleName("sorted"));
        if (comparatorExpression != null && !comparatorExpression.isEmpty()) {
            sortedCall.arguments().add(createExpression(comparatorExpression));
        }
        return sortedCall;
    }
    
    @Override
    public Expression renderLimit(Expression pipeline, long maxSize) {
        // pipeline.limit(maxSize)
        MethodInvocation limitCall = ast.newMethodInvocation();
        limitCall.setExpression(pipeline);
        limitCall.setName(ast.newSimpleName("limit"));
        limitCall.arguments().add(ast.newNumberLiteral(String.valueOf(maxSize)));
        return limitCall;
    }
    
    @Override
    public Expression renderSkip(Expression pipeline, long count) {
        // pipeline.skip(count)
        MethodInvocation skipCall = ast.newMethodInvocation();
        skipCall.setExpression(pipeline);
        skipCall.setName(ast.newSimpleName("skip"));
        skipCall.arguments().add(ast.newNumberLiteral(String.valueOf(count)));
        return skipCall;
    }
    
    @Override
    public Expression renderForEach(Expression pipeline, List<String> bodyStatements, 
                                     String variableName, boolean ordered) {
        // pipeline.forEach(var -> { statements }) oder forEachOrdered
        MethodInvocation forEachCall = ast.newMethodInvocation();
        forEachCall.setExpression(pipeline);
        forEachCall.setName(ast.newSimpleName(ordered ? "forEachOrdered" : "forEach"));
        
        LambdaExpression lambda = ast.newLambdaExpression();
        VariableDeclarationFragment param = ast.newVariableDeclarationFragment();
        param.setName(ast.newSimpleName(variableName));
        lambda.parameters().add(param);
        
        // For single parameter without type annotation, don't use parentheses
        lambda.setParentheses(false);
        
        // Use original body if available (production), otherwise fall back to string parsing (tests)
        if (originalBody != null) {
            // Production path: Use copySubtree from original body to preserve binding information
            if (originalBody instanceof Block) {
                Block block = (Block) originalBody;
                if (block.statements().size() == 1) {
                    // Single statement - extract as expression
                    Statement stmt = (Statement) block.statements().get(0);
                    if (stmt instanceof ExpressionStatement) {
                        ExpressionStatement exprStmt = (ExpressionStatement) stmt;
                        lambda.setBody((Expression) ASTNode.copySubtree(ast, exprStmt.getExpression()));
                    } else {
                        // Not an expression statement, copy the whole statement as block
                        Block lambdaBlock = ast.newBlock();
                        lambdaBlock.statements().add(ASTNode.copySubtree(ast, stmt));
                        lambda.setBody(lambdaBlock);
                    }
                } else {
                    // Multiple statements - copy all into a block
                    Block lambdaBlock = ast.newBlock();
                    for (Object stmt : block.statements()) {
                        lambdaBlock.statements().add(ASTNode.copySubtree(ast, (Statement) stmt));
                    }
                    lambda.setBody(lambdaBlock);
                }
            } else {
                // Body is a single statement (not a block)
                if (originalBody instanceof ExpressionStatement) {
                    ExpressionStatement exprStmt = (ExpressionStatement) originalBody;
                    lambda.setBody((Expression) ASTNode.copySubtree(ast, exprStmt.getExpression()));
                } else {
                    // Not an expression statement, wrap in block
                    Block lambdaBlock = ast.newBlock();
                    lambdaBlock.statements().add(ASTNode.copySubtree(ast, originalBody));
                    lambda.setBody(lambdaBlock);
                }
            }
        } else {
            // Test/fallback path: Use bodyStatements strings (for unit tests)
            if (bodyStatements.size() == 1) {
                Expression bodyExpr = createExpression(bodyStatements.get(0));
                lambda.setBody(bodyExpr);
            } else {
                Block lambdaBlock = ast.newBlock();
                for (String stmt : bodyStatements) {
                    lambdaBlock.statements().add(createStatement(stmt));
                }
                lambda.setBody(lambdaBlock);
            }
        }
        
        forEachCall.arguments().add(lambda);
        return forEachCall;
    }
    
    @Override
    public Expression renderForEachWithBody(Expression pipeline, Supplier<Expression> bodySupplier, 
                                             String variableName, boolean ordered) {
        // Similar to renderForEach but uses the supplier to get the body directly
        MethodInvocation forEachCall = ast.newMethodInvocation();
        forEachCall.setExpression(pipeline);
        forEachCall.setName(ast.newSimpleName(ordered ? "forEachOrdered" : "forEach"));
        
        LambdaExpression lambda = ast.newLambdaExpression();
        VariableDeclarationFragment param = ast.newVariableDeclarationFragment();
        param.setName(ast.newSimpleName(variableName));
        lambda.parameters().add(param);
        lambda.setParentheses(false);
        
        // Get the body from the supplier (AST-aware)
        Expression body = bodySupplier.get();
        if (body != null) {
            lambda.setBody((Expression) ASTNode.copySubtree(ast, body));
        } else {
            // Fallback to an empty block (no-op) if no body is provided
            Block emptyBody = ast.newBlock();
            lambda.setBody(emptyBody);
        }
        
        forEachCall.arguments().add(lambda);
        return forEachCall;
    }
    
    @Override
    public Expression renderFilterWithPredicate(Expression pipeline, Supplier<Expression> predicateSupplier,
                                                 String variableName) {
        // Similar to renderFilter but uses the supplier
        MethodInvocation filterCall = ast.newMethodInvocation();
        filterCall.setExpression(pipeline);
        filterCall.setName(ast.newSimpleName("filter"));
        
        LambdaExpression lambda = ast.newLambdaExpression();
        VariableDeclarationFragment param = ast.newVariableDeclarationFragment();
        param.setName(ast.newSimpleName(variableName));
        lambda.parameters().add(param);
        lambda.setParentheses(false);
        
        // Get the predicate from the supplier (AST-aware)
        Expression predicate = predicateSupplier.get();
        if (predicate != null) {
            lambda.setBody((Expression) ASTNode.copySubtree(ast, predicate));
        } else {
            // Fallback to true literal
            lambda.setBody(ast.newBooleanLiteral(true));
        }
        
        filterCall.arguments().add(lambda);
        return filterCall;
    }
    
    @Override
    public Expression renderMapWithMapper(Expression pipeline, Supplier<Expression> mapperSupplier,
                                           String variableName, String targetType) {
        // Similar to renderMap but uses the supplier
        MethodInvocation mapCall = ast.newMethodInvocation();
        mapCall.setExpression(pipeline);
        mapCall.setName(ast.newSimpleName("map"));
        
        LambdaExpression lambda = ast.newLambdaExpression();
        VariableDeclarationFragment param = ast.newVariableDeclarationFragment();
        param.setName(ast.newSimpleName(variableName));
        lambda.parameters().add(param);
        lambda.setParentheses(false);
        
        // Get the mapper from the supplier (AST-aware)
        Expression mapper = mapperSupplier.get();
        if (mapper != null) {
            lambda.setBody((Expression) ASTNode.copySubtree(ast, mapper));
        } else {
            // Fallback to the variable itself (identity mapper)
            lambda.setBody(ast.newSimpleName(variableName));
        }
        
        mapCall.arguments().add(lambda);
        return mapCall;
    }
    
    
    @Override
    public Expression renderCollect(Expression pipeline, CollectTerminal terminal, String variableName) {
        // pipeline.collect(Collectors.toList()) etc.
        MethodInvocation collectCall = ast.newMethodInvocation();
        collectCall.setExpression(pipeline);
        collectCall.setName(ast.newSimpleName("collect"));
        
        MethodInvocation collector = ast.newMethodInvocation();
        collector.setExpression(ast.newSimpleName("Collectors"));
        
        switch (terminal.collectorType()) {
            case TO_LIST:
                collector.setName(ast.newSimpleName("toList"));
                break;
            case TO_SET:
                collector.setName(ast.newSimpleName("toSet"));
                break;
            case TO_MAP:
                collector.setName(ast.newSimpleName("toMap"));
                // Key and value mappers würden hier hinzugefügt
                break;
            case JOINING:
                collector.setName(ast.newSimpleName("joining"));
                break;
            case GROUPING_BY:
                collector.setName(ast.newSimpleName("groupingBy"));
                break;
            case CUSTOM:
            default:
                collector.setName(ast.newSimpleName("toList"));
        }
        
        collectCall.arguments().add(collector);
        return collectCall;
    }
    
    @Override
    public Expression renderReduce(Expression pipeline, ReduceTerminal terminal, String variableName) {
        // pipeline.reduce(identity, accumulator) etc.
        MethodInvocation reduceCall = ast.newMethodInvocation();
        reduceCall.setExpression(pipeline);
        reduceCall.setName(ast.newSimpleName("reduce"));
        
        if (terminal.identity() != null) {
            reduceCall.arguments().add(createExpression(terminal.identity()));
        }
        reduceCall.arguments().add(createExpression(terminal.accumulator()));
        
        return reduceCall;
    }
    
    @Override
    public Expression renderCount(Expression pipeline) {
        // pipeline.count()
        MethodInvocation countCall = ast.newMethodInvocation();
        countCall.setExpression(pipeline);
        countCall.setName(ast.newSimpleName("count"));
        return countCall;
    }
    
    @Override
    public Expression renderFind(Expression pipeline, boolean findFirst) {
        // pipeline.findFirst() oder pipeline.findAny()
        MethodInvocation findCall = ast.newMethodInvocation();
        findCall.setExpression(pipeline);
        findCall.setName(ast.newSimpleName(findFirst ? "findFirst" : "findAny"));
        return findCall;
    }
    
    @Override
    public Expression renderMatch(Expression pipeline, MatchTerminal terminal, String variableName) {
        // pipeline.anyMatch/allMatch/noneMatch(var -> predicate)
        MethodInvocation matchCall = ast.newMethodInvocation();
        matchCall.setExpression(pipeline);
        
        switch (terminal.matchType()) {
            case ANY_MATCH:
                matchCall.setName(ast.newSimpleName("anyMatch"));
                break;
            case ALL_MATCH:
                matchCall.setName(ast.newSimpleName("allMatch"));
                break;
            case NONE_MATCH:
                matchCall.setName(ast.newSimpleName("noneMatch"));
                break;
            default:
                throw new IllegalArgumentException("Unknown match type: " + terminal.matchType());
        }
        
        matchCall.arguments().add(createLambda(variableName, terminal.predicate()));
        return matchCall;
    }
    
    // Helper methods
    
    private LambdaExpression createLambda(String paramName, String bodyExpression) {
        LambdaExpression lambda = ast.newLambdaExpression();
        VariableDeclarationFragment param = ast.newVariableDeclarationFragment();
        param.setName(ast.newSimpleName(paramName));
        lambda.parameters().add(param);
        lambda.setBody(createExpression(bodyExpression));
        return lambda;
    }
    
    private Expression createExpression(String expressionText) {
        // Parse expression string to AST node
        if (expressionText == null || expressionText.isEmpty()) {
            return ast.newNullLiteral();
        }
        
        // Check for simple identifiers using Java's identifier validation
        if (isValidJavaIdentifier(expressionText)) {
            return ast.newSimpleName(expressionText);
        }
        
        // For complex expressions: use ASTParser with binding resolution
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_EXPRESSION);
        parser.setSource(expressionText.toCharArray());
        parser.setResolveBindings(true);  // Enable binding resolution
        parser.setBindingsRecovery(true);  // Recover from binding errors
        
        // Set up the environment from our compilation unit
        if (compilationUnit != null && compilationUnit.getJavaElement() != null) {
            parser.setProject(compilationUnit.getJavaElement().getJavaProject());
            parser.setUnitName("__expression.java");  // Virtual file name
        }
        
        ASTNode result = parser.createAST(null);
        
        if (result instanceof Expression) {
            return (Expression) ASTNode.copySubtree(ast, result);
        }
        
        // Expression could not be parsed; fail fast instead of silently mangling it
        throw new IllegalArgumentException("Unable to parse expression: " + expressionText);
    }
    
    /**
     * Validates if a string is a valid Java identifier.
     * Supports Unicode identifiers, underscores, and dollar signs.
     */
    private boolean isValidJavaIdentifier(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        if (!Character.isJavaIdentifierStart(str.charAt(0))) {
            return false;
        }
        for (int i = 1; i < str.length(); i++) {
            if (!Character.isJavaIdentifierPart(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }
    
    private Statement createStatement(String statementText) {
        // Ensure statement ends with semicolon for proper parsing
        String normalizedStatement = statementText.trim();
        if (!normalizedStatement.endsWith(";")) {
            normalizedStatement += ";";
        }
        
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_STATEMENTS);
        parser.setSource(normalizedStatement.toCharArray());
        parser.setResolveBindings(true);  // Enable binding resolution
        parser.setBindingsRecovery(true);  // Recover from binding errors
        
        // Set up the environment from our compilation unit
        if (compilationUnit != null && compilationUnit.getJavaElement() != null) {
            parser.setProject(compilationUnit.getJavaElement().getJavaProject());
            parser.setUnitName("__statement.java");  // Virtual file name
        }
        
        ASTNode result = parser.createAST(null);
        
        if (result instanceof Block) {
            Block block = (Block) result;
            if (!block.statements().isEmpty()) {
                return (Statement) ASTNode.copySubtree(ast, (Statement) block.statements().get(0));
            }
        }
        
        // Statement could not be parsed; fail fast
        throw new IllegalArgumentException("Unable to parse statement: " + statementText);
    }
    
    /**
     * Returns the AST used by this renderer.
     */
    public AST getAST() {
        return ast;
    }
}
