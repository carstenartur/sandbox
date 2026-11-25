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

import static org.sandbox.jdt.internal.corext.fix.helper.StreamConstants.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.manipulation.ImportReferencesCollector;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.LinkedNodeFinder;
import org.eclipse.jdt.internal.corext.dom.ScopeAnalyzer;
import org.eclipse.jdt.internal.corext.refactoring.structure.ImportRemover;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.sandbox.functional.core.model.SourceDescriptor;
import org.sandbox.functional.core.model.FunctionalExpression;
import org.sandbox.functional.core.operation.FilterOp;
import org.sandbox.functional.core.operation.MapOp;
import org.sandbox.functional.core.operation.StreamTypeConversionOp;
import org.sandbox.functional.core.renderer.ASTAwareRenderer;
import org.sandbox.functional.core.renderer.StreamPipelineRenderer;
import org.sandbox.functional.core.terminal.*;
import org.sandbox.jdt.internal.corext.util.NamingUtils;

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
    private final ASTRewrite rewrite;
    private String primitiveElement;
    private Type emittedElementType;
    private final Set<Type> retainedMappedTypes = new HashSet<>();
    private final Set<MethodInvocation> iterableStreams = new HashSet<>();
    
    public ASTStreamRenderer(AST ast, ASTRewrite rewrite, CompilationUnit compilationUnit, Statement originalBody) {
        this.ast = ast;
        this.compilationUnit = compilationUnit;
        this.originalBody = originalBody;
        this.rewrite = rewrite;
    }
    
    @Override
    public Expression renderSource(SourceDescriptor source) {
        Expression pipeline = renderStreamSource(source);
        PrimitiveElementAdapter adapter = primitiveElementAdapter();
        if (adapter == null || primitiveElement != null) return pipeline;
        // An enhanced-for primitive is unboxed once before its body. Keep that
        // type in every following operation, including overload resolution.
        String parameter = LoopVariableNames.fresh("boxedValue", LoopVariableNames.usedNames(originalBody)); //$NON-NLS-1$
        return renderMap(pipeline, parameter + "." + adapter.method() + "()", parameter, adapter.type()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private Expression renderStreamSource(SourceDescriptor source) {
        primitiveElement = switch (source.type()) {
            case INT_RANGE, EXPLICIT_RANGE -> "int"; //$NON-NLS-1$
            case ARRAY -> numericStreamType(source.elementTypeName());
            default -> null;
        };
        if (source.streamExpression() != null) return createExpression(source.streamExpression());
        // Create: collection.stream() or Arrays.stream(array)
        switch (source.type()) {
            case COLLECTION:
                // collection.stream()
                MethodInvocation streamCall = ast.newMethodInvocation();
                streamCall.setExpression(createReceiverExpression(source.expression()));
                streamCall.setName(ast.newSimpleName(STREAM_METHOD));
                return streamCall;
                
            case ARRAY:
                // Arrays.stream(array)
                MethodInvocation arraysStream = ast.newMethodInvocation();
                arraysStream.setExpression(ast.newSimpleName(ARRAYS_CLASS_NAME));
                arraysStream.setName(ast.newSimpleName(STREAM_METHOD));
                arraysStream.arguments().add(createExpression(source.expression()));
                return arraysStream;
                
            case ITERABLE:
                // StreamSupport.stream(iterable.spliterator(), false)
                MethodInvocation streamSupport = ast.newMethodInvocation();
                streamSupport.setExpression(ast.newSimpleName(STREAM_SUPPORT_CLASS_NAME));
                streamSupport.setName(ast.newSimpleName(STREAM_METHOD));
                
                MethodInvocation spliterator = ast.newMethodInvocation();
                spliterator.setExpression(createReceiverExpression(source.expression()));
                spliterator.setName(ast.newSimpleName(SPLITERATOR_METHOD));
                
                streamSupport.arguments().add(spliterator);
                streamSupport.arguments().add(ast.newBooleanLiteral(false));
                iterableStreams.add(streamSupport);
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

    private record PrimitiveElementAdapter(String type, String method) { }

    private PrimitiveElementAdapter primitiveElementAdapter() {
        if (originalBody == null || !(originalBody.getParent() instanceof EnhancedForStatement loop)) return null;
        IVariableBinding variable = loop.getParameter().resolveBinding();
        String type = variable == null ? null : numericStreamType(variable.getType().getName());
        ITypeBinding source = loop.getExpression().resolveTypeBinding();
        if (type == null || source == null) return null;
        ITypeBinding element;
        if (source.isArray()) {
            element = source.getComponentType();
        } else {
            ITypeBinding iterable = Bindings.findTypeInHierarchy(source, "java.lang.Iterable"); //$NON-NLS-1$
            if (iterable == null || iterable.getTypeArguments().length != 1) return null;
            element = iterable.getTypeArguments()[0];
        }
        if (element.isPrimitive()) return null;
        String method = "java.lang.Character".equals(element.getErasure().getQualifiedName()) //$NON-NLS-1$
                ? "charValue" : type + "Value"; //$NON-NLS-1$ //$NON-NLS-2$
        return new PrimitiveElementAdapter(type, method);
    }

    @Override
    public Expression renderTypeConversion(Expression pipeline, StreamTypeConversionOp conversion) {
        MethodInvocation call = ast.newMethodInvocation();
        call.setExpression(pipeline);
        call.setName(ast.newSimpleName(conversion.operationType()));
        return call;
    }
    
    @Override
    public Expression renderFilter(Expression pipeline, String expression, String variableName) {
        // pipeline.filter(var -> expression)
        MethodInvocation filterCall = ast.newMethodInvocation();
        filterCall.setExpression(pipeline);
        filterCall.setName(ast.newSimpleName(FILTER_METHOD));
        filterCall.arguments().add(createLambda(variableName, expression));
        return filterCall;
    }
    
    @Override
    public Expression renderFilterOp(Expression pipeline, FilterOp filterOp, String variableName) {
        if (filterOp.function() != null) {
            return ASTAwareRenderer.super.renderFilterOp(pipeline, filterOp, variableName);
        }
        // Check if operation has comments - if so, use block lambda
        if (filterOp.hasComments()) {
            return renderFilterWithComments(pipeline, filterOp, variableName);
        }
        // Otherwise use simple expression lambda
        return renderFilter(pipeline, filterOp.expression(), variableName);
    }
    
    @Override
    public Expression renderMap(Expression pipeline, String expression, String variableName, String targetType) {
        // A primitive local stays primitive in downstream filters, overloads and reductions.
        if (primitiveElement != null && primitiveElement.equals(targetType) && variableName.equals(expression)) {
            return pipeline;
        }
        MethodInvocation mapCall = ast.newMethodInvocation();
        mapCall.setExpression(pipeline);
        mapCall.setName(ast.newSimpleName(mappingMethod(targetType)));
        mapCall.arguments().add(createLambda(variableName, expression));
        return mapCall;
    }
    
    private static String numericStreamType(String type) {
        return switch (type) {
            case "int", "long", "double" -> type; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            case null, default -> null;
        };
    }

    private String mappingMethod(String targetType) {
        String previous = primitiveElement;
        primitiveElement = numericStreamType(targetType);
        if (java.util.Objects.equals(previous, primitiveElement)) {
            return MAP_METHOD;
        }
        return switch (primitiveElement) {
            case "int" -> "mapToInt"; //$NON-NLS-1$ //$NON-NLS-2$
            case "long" -> "mapToLong"; //$NON-NLS-1$ //$NON-NLS-2$
            case "double" -> "mapToDouble"; //$NON-NLS-1$ //$NON-NLS-2$
            case null, default -> "mapToObj"; //$NON-NLS-1$
        };
    }

    protected final Expression collectionInput(Expression pipeline) {
        if (primitiveElement == null) {
            return pipeline;
        }
        MethodInvocation boxed = ast.newMethodInvocation();
        boxed.setExpression(pipeline);
        boxed.setName(ast.newSimpleName("boxed")); //$NON-NLS-1$
        return boxed;
    }

    @Override
    public Expression renderMapOp(Expression pipeline, MapOp mapOp, String variableName) {
        if (mapOp.function() != null) {
            return ASTAwareRenderer.super.renderMapOp(pipeline, mapOp, variableName);
        }
        // Side-effect maps: map(var -> { statements; return var; })
        if (mapOp.isSideEffect()) {
            return renderSideEffectMap(pipeline, mapOp.expression(), variableName);
        }
        VariableDeclarationStatement declaration = originalTargetTypedDeclaration(mapOp);
        if (declaration != null) return renderDeclaredMap(pipeline, mapOp, variableName, declaration);
        // Check if operation has comments - if so, use block lambda
        if (mapOp.hasComments()) {
            return renderMapWithComments(pipeline, mapOp, variableName);
        }
        // Otherwise use simple expression lambda
        return renderMap(pipeline, mapOp.expression(), variableName, mapOp.targetType());
    }

    private VariableDeclarationStatement originalTargetTypedDeclaration(MapOp map) {
        if (rewrite == null || originalBody == null || compilationUnit == null) return null;
        VariableDeclarationStatement[] found = { null };
        originalBody.accept(new ASTVisitor() {
            @Override public boolean visit(LambdaExpression node) { return false; }
            @Override public boolean visit(AnonymousClassDeclaration node) { return false; }
            @Override public boolean visit(TypeDeclaration node) { return false; }
            @Override public boolean visit(VariableDeclarationStatement node) {
                if (node.fragments().size() != 1) return true;
                VariableDeclarationFragment fragment = (VariableDeclarationFragment) node.fragments().get(0);
                Expression initializer = fragment.getInitializer();
                if (initializer == null || !fragment.getName().getIdentifier().equals(map.outputVariableName())
                        || !initializer.toString().equals(map.expression())) return true;
                Expression value = ASTNodes.getUnparenthesedExpression(initializer);
                if (!(value instanceof ArrayInitializer || value instanceof LambdaExpression
                        || value instanceof MethodReference
                        || value instanceof ClassInstanceCreation creation
                                && creation.getType() instanceof ParameterizedType type && type.typeArguments().isEmpty())) return false;
                // Iterator models contain detached copies with the original ranges.
                ASTNode original = node.getRoot() == compilationUnit ? node
                        : NodeFinder.perform(compilationUnit, node.getStartPosition(), node.getLength());
                if (original instanceof VariableDeclarationStatement declaration) found[0] = declaration;
                return false;
            }
        });
        return found[0];
    }

    @SuppressWarnings("unchecked")
    private Expression renderDeclaredMap(Expression pipeline, MapOp map, String variableName,
            VariableDeclarationStatement declaration) {
        MethodInvocation call = ast.newMethodInvocation();
        call.setExpression(pipeline);
        call.setName(ast.newSimpleName(mappingMethod(map.targetType())));
        LambdaExpression lambda = ast.newLambdaExpression();
        VariableDeclarationFragment parameter = ast.newVariableDeclarationFragment();
        lambda.parameters().add(parameter);
        lambda.setParentheses(false);
        setLambdaParameterName(parameter, effectiveParameterName(variableName, declaration), variableName);
        Block body = ast.newBlock();
        body.statements().add(rewrite.createCopyTarget(declaration));
        ReturnStatement result = ast.newReturnStatement();
        result.setExpression(ast.newSimpleName(map.outputVariableName()));
        body.statements().add(result);
        lambda.setBody(body);
        call.arguments().add(lambda);
        retainedMappedTypes.add(declaration.getType());
        return call;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Expression renderFunction(Expression pipeline, String operation, FunctionalExpression function) {
        MethodInvocation call = ast.newMethodInvocation();
        call.setExpression(pipeline);
        call.setName(ast.newSimpleName(operation));
        call.arguments().add(createExpression("(" + function.functionType() + ") (" + function.expression() + ")")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        return call;
    }
    
    /**
     * Renders a side-effect MAP operation as: pipeline.map(var -> { statements; return var; })
     */
    @SuppressWarnings("unchecked")
    private Expression renderSideEffectMap(Expression pipeline, String statementsText, String variableName) {
        MethodInvocation mapCall = ast.newMethodInvocation();
        mapCall.setExpression(pipeline);
        mapCall.setName(ast.newSimpleName(MAP_METHOD));
        
        // Create block lambda: var -> { statements; return var; }
        LambdaExpression lambda = ast.newLambdaExpression();
        VariableDeclarationFragment param = ast.newVariableDeclarationFragment();
        param.setName(ast.newSimpleName(variableName));
        lambda.parameters().add(param);
        
        Block block = ast.newBlock();
        
        // Parse the side-effect statements from text
        org.eclipse.jdt.core.dom.ASTParser parser = org.eclipse.jdt.core.dom.ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(org.eclipse.jdt.core.dom.ASTParser.K_STATEMENTS);
        parser.setSource(statementsText.toCharArray());
        ASTNode parsed = parser.createAST(null);
        if (parsed instanceof Block parsedBlock) {
            for (Object s : parsedBlock.statements()) {
                block.statements().add(ASTNode.copySubtree(ast, (ASTNode) s));
            }
        }
        
        // Add return statement: return var;
        ReturnStatement returnStmt = ast.newReturnStatement();
        returnStmt.setExpression(ast.newSimpleName(variableName));
        block.statements().add(returnStmt);
        
        lambda.setBody(block);
        lambda.setParentheses(false);
        setLambdaParameterName(param, variableName, variableName);
        
        mapCall.arguments().add(lambda);
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
        forEachCall.setName(ast.newSimpleName(ordered ? FOR_EACH_ORDERED_METHOD : FOR_EACH_METHOD));
        
        LambdaExpression lambda = ast.newLambdaExpression();
        VariableDeclarationFragment param = ast.newVariableDeclarationFragment();
        param.setName(ast.newSimpleName(variableName));
        lambda.parameters().add(param);
        
        // For single parameter without type annotation, don't use parentheses
        lambda.setParentheses(false);
        
        // Always use bodyStatements - this contains only the terminal body statements,
        // not the full original loop body (which would include statements already converted
        // to filter/map operations, causing duplication)
        String parameterName = effectiveParameterName(variableName, bodyStatements);
        setLambdaParameterName(param, parameterName, variableName);
        lambda.setBody(createLambdaBody(bodyStatements));
        
        forEachCall.arguments().add(lambda);
        return forEachCall;
    }
    
    @Override
    public Expression renderForEachWithBody(Expression pipeline, Supplier<Expression> bodySupplier, 
                                             String variableName, boolean ordered) {
        // Similar to renderForEach but uses the supplier to get the body directly
        MethodInvocation forEachCall = ast.newMethodInvocation();
        forEachCall.setExpression(pipeline);
        forEachCall.setName(ast.newSimpleName(ordered ? FOR_EACH_ORDERED_METHOD : FOR_EACH_METHOD));
        
        LambdaExpression lambda = ast.newLambdaExpression();
        VariableDeclarationFragment param = ast.newVariableDeclarationFragment();
        param.setName(ast.newSimpleName(variableName));
        lambda.parameters().add(param);
        lambda.setParentheses(false);
        
        // Get the body from the supplier (AST-aware)
        Expression body = bodySupplier.get();
        setLambdaParameterName(param, effectiveParameterName(variableName, body), variableName);
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
    
    /**
     * Renders a direct forEach call without stream prefix (e.g., list.forEach(...) instead of list.stream().forEach(...)).
     * 
     * <p>This method is used for simple forEach patterns with no intermediate operations.
     * It generates more idiomatic code by avoiding the unnecessary .stream() call.</p>
     * 
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>Collections: {@code list.forEach(item -> System.out.println(item))}</li>
     *   <li>Arrays: {@code Arrays.stream(array).forEach(item -> ...)} (arrays don't have forEach method)</li>
     * </ul>
     * 
     * <p><b>Immutability Considerations:</b></p>
     * <p>This method is safe for both mutable and immutable collections:
     * <ul>
     *   <li>Immutable collections (List.of, Collections.unmodifiableList, etc.) support forEach</li>
     *   <li>forEach only reads elements, doesn't modify the collection structure</li>
     *   <li>Side effects in the lambda body are the user's responsibility</li>
     * </ul>
     * </p>
     * 
     * @param source the source descriptor (must be COLLECTION or ITERABLE for direct forEach)
     * @param bodyStatements the body statements to execute for each element
     * @param variableName the loop variable name
     * @param ordered whether to use forEachOrdered (only relevant for streams)
     * @return the direct forEach invocation
     */
    public Expression renderDirectForEach(SourceDescriptor source, List<String> bodyStatements, 
                                           String variableName, boolean ordered) {
        // A direct fallback may replace a previously rendered collect pipeline.
        iterableStreams.clear();
        // Arrays need a stream receiver, but must retain the same original body
        // as collections when the caller requests a conservative direct fallback.
        boolean streamSource = source.type() == SourceDescriptor.SourceType.ARRAY || primitiveElementAdapter() != null;
        MethodInvocation forEachCall = ast.newMethodInvocation();
        forEachCall.setExpression(streamSource ? renderSource(source) : createReceiverExpression(source.expression()));
        forEachCall.setName(ast.newSimpleName(streamSource && ordered ? FOR_EACH_ORDERED_METHOD : FOR_EACH_METHOD));
        
        LambdaExpression lambda = ast.newLambdaExpression();
        VariableDeclarationFragment param = ast.newVariableDeclarationFragment();
        lambda.parameters().add(param);
        lambda.setParentheses(false);
        
        // Use original body if available (production), otherwise fall back to string parsing (tests)
        if (originalBody != null) {
            ASTNode sourceBody = originalBody;
            boolean preserveSource = rewrite != null && originalBody.getRoot() == compilationUnit;
            EnhancedForStatement loop = originalBody.getParent() instanceof EnhancedForStatement enhanced ? enhanced : null;
            IVariableBinding element = loop == null ? null : loop.getParameter().resolveBinding();
            boolean mutableElement = element != null && !Modifier.isFinal(element.getModifiers()) && !element.isEffectivelyFinal();
            if (!mutableElement && sourceBody instanceof Block block && block.statements().size() == 1 && !hasComments(block)) {
                sourceBody = (Statement) block.statements().get(0);
            }
            if (!mutableElement && sourceBody instanceof ExpressionStatement statement) {
                sourceBody = statement.getExpression();
            }
            ASTNode body = preserveSource ? rewrite.createCopyTarget(sourceBody) : ASTNode.copySubtree(ast, sourceBody);
            String parameterName = effectiveParameterName(variableName, sourceBody);
            if (mutableElement && preserveSource) {
                parameterName = LoopVariableNames.fresh(variableName, LoopVariableNames.usedNames(loop));
                VariableDeclarationFragment local = ast.newVariableDeclarationFragment();
                local.setName(ast.newSimpleName(variableName));
                local.extraDimensions().addAll(ASTNode.copySubtrees(ast, loop.getParameter().extraDimensions()));
                local.setInitializer(ast.newSimpleName(parameterName));
                VariableDeclarationStatement declaration = ast.newVariableDeclarationStatement(local);
                declaration.setType((Type) rewrite.createCopyTarget(loop.getParameter().getType()));
                declaration.modifiers().addAll(ASTNode.copySubtrees(ast, loop.getParameter().modifiers()));
                if (sourceBody instanceof Block block) {
                    rewrite.getListRewrite(block, Block.STATEMENTS_PROPERTY).insertFirst(declaration, null);
                } else {
                    Block block = ast.newBlock();
                    block.statements().add(declaration);
                    block.statements().add(body);
                    body = block;
                }
            } else if (loop != null && variableName.equals(parameterName)
                    && variableName.equals(loop.getParameter().getName().getIdentifier())) {
                parameterName = avoidHidingField(loop, sourceBody, body, variableName, preserveSource);
            }
            if (preserveSource) lowerContinues(originalBody);
            setLambdaParameterName(param, parameterName, variableName);
            if (body instanceof Statement statement && !(body instanceof Block)) {
                Block lambdaBlock = ast.newBlock();
                lambdaBlock.statements().add(statement);
                body = lambdaBlock;
            }
            lambda.setBody(body);
        } else {
            // Test/fallback path: Use bodyStatements strings (for unit tests)
            String parameterName = effectiveParameterName(variableName, bodyStatements);
            setLambdaParameterName(param, parameterName, variableName);
            lambda.setBody(createLambdaBody(bodyStatements));
        }
        
        forEachCall.arguments().add(lambda);
        return forEachCall;
    }

    private boolean hasComments(ASTNode node) {
        if (compilationUnit == null || compilationUnit.getCommentList() == null) return false;
        for (Object item : compilationUnit.getCommentList()) {
            Comment comment = (Comment) item;
            if (comment.getStartPosition() >= node.getStartPosition()
                    && comment.getStartPosition() < node.getStartPosition() + node.getLength()) return true;
        }
        return false;
    }

    private void lowerContinues(Statement body) {
        body.accept(new ASTVisitor() {
            @Override public boolean visit(LambdaExpression node) { return false; }
            @Override public boolean visit(AnonymousClassDeclaration node) { return false; }
            @Override public boolean visit(TypeDeclaration node) { return false; }
            @Override public boolean visit(EnhancedForStatement node) { return false; }
            @Override public boolean visit(ForStatement node) { return false; }
            @Override public boolean visit(WhileStatement node) { return false; }
            @Override public boolean visit(DoStatement node) { return false; }
            @Override public boolean visit(ContinueStatement node) {
                if (node.getLabel() == null) rewrite.replace(node, ast.newReturnStatement(), null);
                return false;
            }
        });
    }
    
    private String avoidHidingField(EnhancedForStatement loop, ASTNode source, ASTNode copy, String name, boolean preserveSource) {
        IVariableBinding element = loop.getParameter().resolveBinding();
        if (element == null || compilationUnit == null) {
            return name;
        }
        // At the loop boundary its element is not in scope yet, so fields remain visible.
        IBinding[] visible = new ScopeAnalyzer(compilationUnit).getDeclarationsInScope(loop.getStartPosition(),
                ScopeAnalyzer.VARIABLES | ScopeAnalyzer.CHECK_VISIBILITY);
        Set<String> fields = new HashSet<>();
        for (IBinding binding : visible) {
            if (binding instanceof IVariableBinding field && field.isField()) {
                fields.add(field.getName());
            }
        }
        if (!fields.contains(name)) {
            return name;
        }
        Set<String> reserved = new HashSet<>(LoopVariableNames.usedNames(loop));
        reserved.addAll(fields);
        String replacement = LoopVariableNames.fresh(name, reserved);
        // copySubtree retains source ranges but drops bindings. Match the original element uses by position.
        Set<Integer> uses = new HashSet<>();
        for (SimpleName use : LinkedNodeFinder.findByBinding(source, element)) {
            if (preserveSource) rewrite.replace(use, ast.newSimpleName(replacement), null);
            uses.add(use.getStartPosition());
        }
        if (preserveSource) return replacement;
        copy.accept(new ASTVisitor() {
            @Override
            public boolean visit(SimpleName identifier) {
                if (uses.contains(identifier.getStartPosition())) {
                    identifier.setIdentifier(replacement);
                }
                return true;
            }
        });
        return replacement;
    }

    @Override
    public Expression renderFilterWithPredicate(Expression pipeline, Supplier<Expression> predicateSupplier,
                                                 String variableName) {
        // Similar to renderFilter but uses the supplier
        MethodInvocation filterCall = ast.newMethodInvocation();
        filterCall.setExpression(pipeline);
        filterCall.setName(ast.newSimpleName(FILTER_METHOD));
        
        LambdaExpression lambda = ast.newLambdaExpression();
        VariableDeclarationFragment param = ast.newVariableDeclarationFragment();
        param.setName(ast.newSimpleName(variableName));
        lambda.parameters().add(param);
        lambda.setParentheses(false);
        
        // Get the predicate from the supplier (AST-aware)
        Expression predicate = predicateSupplier.get();
        if (predicate != null) {
            setLambdaParameterName(param, effectiveParameterName(variableName, predicate), variableName);
            lambda.setBody((Expression) ASTNode.copySubtree(ast, predicate));
        } else {
            // Fallback to true literal
            Expression body = ast.newBooleanLiteral(true);
            setLambdaParameterName(param, effectiveParameterName(variableName, body), variableName);
            lambda.setBody(body);
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
        mapCall.setName(ast.newSimpleName(mappingMethod(targetType)));
        
        LambdaExpression lambda = ast.newLambdaExpression();
        VariableDeclarationFragment param = ast.newVariableDeclarationFragment();
        lambda.parameters().add(param);
        lambda.setParentheses(false);
        
        // Get the mapper from the supplier (AST-aware)
        Expression mapper = mapperSupplier.get();
        if (mapper != null) {
            Expression body = (Expression) ASTNode.copySubtree(ast, mapper);
            setLambdaParameterName(param, effectiveParameterName(variableName, body), variableName);
            lambda.setBody(body);
        } else {
            // Fallback to the variable itself (identity mapper)
            setLambdaParameterName(param, variableName, variableName);
            lambda.setBody(ast.newSimpleName(variableName));
        }
        
        mapCall.arguments().add(lambda);
        return mapCall;
    }
    
    
    @Override
    public Expression renderCollect(Expression pipeline, CollectTerminal terminal, String variableName) {
        // pipeline.collect(Collectors.toList()) etc.
        MethodInvocation collectCall = ast.newMethodInvocation();
        collectCall.setExpression(collectionInput(pipeline));
        collectCall.setName(ast.newSimpleName(COLLECT_METHOD));
        
        MethodInvocation collector = ast.newMethodInvocation();
        collector.setExpression(ast.newSimpleName(COLLECTORS_CLASS_NAME));
        
        switch (terminal.collectorType()) {
            case TO_LIST:
                collector.setName(ast.newSimpleName(TO_LIST_METHOD));
                break;
            case TO_SET:
                collector.setName(ast.newSimpleName(TO_SET_METHOD));
                break;
            case TO_MAP:
                collector.setName(ast.newSimpleName(TO_MAP_METHOD));
                break;
            case JOINING:
                collector.setName(ast.newSimpleName(JOINING_METHOD));
                break;
            case GROUPING_BY:
                collector.setName(ast.newSimpleName(GROUPING_BY_METHOD));
                break;
            case CUSTOM:
            default:
                collector.setName(ast.newSimpleName(TO_LIST_METHOD));
        }
        
        collectCall.arguments().add(collector);
        return collectCall;
    }
    
    @Override
    public Expression renderReduce(Expression pipeline, ReduceTerminal terminal, String variableName) {
        // pipeline.reduce(identity, accumulator) etc.
        MethodInvocation reduceCall = ast.newMethodInvocation();
        reduceCall.setExpression(pipeline);
        reduceCall.setName(ast.newSimpleName(REDUCE_METHOD));
        
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
                matchCall.setName(ast.newSimpleName(ANY_MATCH_METHOD));
                break;
            case ALL_MATCH:
                matchCall.setName(ast.newSimpleName(ALL_MATCH_METHOD));
                break;
            case NONE_MATCH:
                matchCall.setName(ast.newSimpleName(NONE_MATCH_METHOD));
                break;
            default:
                throw new IllegalArgumentException("Unknown match type: " + terminal.matchType());
        }
        
        LambdaExpression lambda = createLambda(variableName, terminal.predicate());
        BooleanMatchDeclaration original = originalBooleanMatchDeclaration(terminal);
        if (original != null) {
            // Keep the original declaration's comments and literal lines without
            // introducing a boxed Boolean stream between the predicate and match.
            Block body = ast.newBlock();
            body.statements().add(rewrite.createCopyTarget(original.declaration()));
            ReturnStatement result = ast.newReturnStatement();
            String name = ((VariableDeclarationFragment) original.declaration().fragments().get(0)).getName().getIdentifier();
            result.setExpression(createExpression(original.negated() ? "!" + name : name)); //$NON-NLS-1$
            body.statements().add(result);
            lambda.setBody(body);
        } else {
            IfStatement match = originalCommentedMatch(terminal);
            if (match != null) {
                // Keep trailing compiler comments (including identity comparison
                // markers) on their original expression line and copy all trivia.
                Block body = ast.newBlock();
                body.statements().add(rewrite.createCopyTarget(match));
                Statement branch = match.getThenStatement();
                if (branch instanceof Block block) branch = (Statement) block.statements().get(0);
                ReturnStatement matched = (ReturnStatement) branch;
                if (!((BooleanLiteral) matched.getExpression()).booleanValue()) {
                    rewrite.replace(matched.getExpression(), ast.newBooleanLiteral(true), null);
                }
                ReturnStatement otherwise = ast.newReturnStatement();
                otherwise.setExpression(ast.newBooleanLiteral(false));
                body.statements().add(otherwise);
                lambda.setBody(body);
            }
        }
        matchCall.arguments().add(lambda);
        return matchCall;
    }

    private record BooleanMatchDeclaration(VariableDeclarationStatement declaration, boolean negated) { }

    private BooleanMatchDeclaration originalBooleanMatchDeclaration(MatchTerminal terminal) {
        if (rewrite == null || originalBody == null || originalBody.getRoot() != compilationUnit
                || terminal.matchType() == MatchTerminal.MatchType.ALL_MATCH || !hasComments(originalBody)) return null;
        BooleanMatchDeclaration[] found = { null };
        originalBody.accept(new ASTVisitor() {
            @Override public boolean visit(LambdaExpression node) { return false; }
            @Override public boolean visit(AnonymousClassDeclaration node) { return false; }
            @Override public boolean visit(TypeDeclaration node) { return false; }
            @Override public boolean visit(Block block) {
                if (found[0] != null) return false;
                List<?> statements = block.statements();
                if (statements.size() < 2
                        || !(statements.get(statements.size() - 2) instanceof VariableDeclarationStatement declaration)
                        || !(statements.get(statements.size() - 1) instanceof IfStatement match)
                        || match.getElseStatement() != null || declaration.fragments().size() != 1
                        || !(declaration.getType() instanceof PrimitiveType primitive)
                        || primitive.getPrimitiveTypeCode() != PrimitiveType.BOOLEAN) return true;
                VariableDeclarationFragment fragment = (VariableDeclarationFragment) declaration.fragments().get(0);
                int polarity = JdtLoopExtractor.booleanMatchPolarity(fragment.getName(), match.getExpression());
                if (fragment.getInitializer() == null || polarity == 0) return true;
                String predicate = polarity > 0 ? fragment.getInitializer().toString()
                        : "!(" + fragment.getInitializer() + ")"; //$NON-NLS-1$ //$NON-NLS-2$
                if (!terminal.predicate().equals(predicate)) return true;
                Statement branch = match.getThenStatement();
                if (branch instanceof Block body && body.statements().size() == 1) {
                    branch = (Statement) body.statements().get(0);
                }
                if (branch instanceof ReturnStatement result
                        && result.getExpression() instanceof BooleanLiteral value
                        && value.booleanValue() == (terminal.matchType() == MatchTerminal.MatchType.ANY_MATCH)) {
                    found[0] = new BooleanMatchDeclaration(declaration, polarity < 0);
                }
                return found[0] == null;
            }
        });
        return found[0];
    }

    private IfStatement originalCommentedMatch(MatchTerminal terminal) {
        if (rewrite == null || originalBody == null || originalBody.getRoot() != compilationUnit
                || terminal.matchType() == MatchTerminal.MatchType.ALL_MATCH || !hasComments(originalBody)) return null;
        IfStatement[] found = { null };
        originalBody.accept(new ASTVisitor() {
            @Override public boolean visit(LambdaExpression node) { return false; }
            @Override public boolean visit(AnonymousClassDeclaration node) { return false; }
            @Override public boolean visit(TypeDeclaration node) { return false; }
            @Override public boolean visit(IfStatement node) {
                if (node.getElseStatement() != null || !node.getExpression().toString().equals(terminal.predicate())
                        || !hasComments(node)) return true;
                Statement branch = node.getThenStatement();
                if (branch instanceof Block block && block.statements().size() == 1) branch = (Statement) block.statements().get(0);
                if (branch instanceof ReturnStatement result && result.getExpression() instanceof BooleanLiteral) found[0] = node;
                return found[0] == null;
            }
        });
        return found[0];
    }
    
    // Helper methods

    /** A single statement is not necessarily a Java expression (for example, if/else). */
    private ASTNode createLambdaBody(List<String> statements) {
        Block block = ast.newBlock();
        for (String statement : statements) block.statements().add(createStatement(statement));
        if (block.statements().size() == 1
                && block.statements().get(0) instanceof ExpressionStatement expression) {
            return ASTNode.copySubtree(ast, expression.getExpression());
        }
        return block;
    }

    /** Preserve the precedence of a cast, conditional or other compound receiver. */
    private Expression createReceiverExpression(String text) {
        Expression expression = createExpression(text);
        if (expression instanceof CastExpression || expression instanceof ConditionalExpression
                || expression instanceof Assignment || expression instanceof InfixExpression
                || expression instanceof LambdaExpression || expression instanceof MethodReference) {
            ParenthesizedExpression receiver = ast.newParenthesizedExpression();
            receiver.setExpression(expression);
            return receiver;
        }
        return expression;
    }
    
    private LambdaExpression createLambda(String paramName, String bodyExpression) {
        Expression body = createExpression(bodyExpression);
        LambdaExpression lambda = ast.newLambdaExpression();
        VariableDeclarationFragment param = ast.newVariableDeclarationFragment();
        lambda.parameters().add(param);
        // For single parameter without type annotation, don't use parentheses
        lambda.setParentheses(false);
        setLambdaParameterName(param, effectiveParameterName(paramName, body), paramName);
        lambda.setBody(body);
        return lambda;
    }

    private boolean referencesVariable(ASTNode node, String variableName) {
        if (node == null) {
            return false;
        }
        final boolean[] referenced = { false };
        node.accept(new ASTVisitor() {
            @Override
            public boolean visit(SimpleName name) {
                if (variableName.equals(name.getIdentifier())) {
                    referenced[0] = true;
                }
                return !referenced[0];
            }
        });
        return referenced[0];
    }

    private String effectiveParameterName(String requestedName, ASTNode body) {
        if (supportsUnnamedLambdaParameters() && !referencesVariable(body, requestedName)) {
            return "_"; //$NON-NLS-1$
        }
        return requestedName;
    }

    private String effectiveParameterName(String requestedName, List<String> bodyStatements) {
        if (supportsUnnamedLambdaParameters() && !referencesVariable(bodyStatements, requestedName)) {
            return "_"; //$NON-NLS-1$
        }
        return requestedName;
    }

    private boolean referencesVariable(List<String> bodyStatements, String variableName) {
        for (String bodyStatement : bodyStatements) {
            if (bodyStatement != null && referencesVariable(createStatement(bodyStatement), variableName)) {
                return true;
            }
        }
        return false;
    }

    private boolean supportsUnnamedLambdaParameters() {
        if (compilationUnit == null || compilationUnit.getJavaElement() == null) {
            return false;
        }
        String source = compilationUnit.getJavaElement().getJavaProject().getOption(JavaCore.COMPILER_SOURCE, true);
        return JavaCore.compareJavaVersions(source, JavaCore.VERSION_22) >= 0;
    }

    @SuppressWarnings("unchecked")
    private void setLambdaParameterName(VariableDeclarationFragment parameter, String parameterName,
            String sourceVariableName) {
        parameter.setName(ast.newSimpleName(parameterName));
        if (originalBody == null || !(originalBody.getParent() instanceof EnhancedForStatement loop)
                || !sourceVariableName.equals(loop.getParameter().getName().getIdentifier())
                || !requiresDeclaredElementType(loop)) return;
        // Inference can narrow a declared supertype to a subtype or capture, changing
        // overload resolution and the diagnostics of casts in the original body.
        SingleVariableDeclaration typed = ast.newSingleVariableDeclaration();
        typed.setName(ast.newSimpleName(parameterName));
        emittedElementType = (Type) ASTNode.copySubtree(ast, loop.getParameter().getType());
        typed.setType(emittedElementType);
        typed.extraDimensions().addAll(ASTNode.copySubtrees(ast, loop.getParameter().extraDimensions()));
        LambdaExpression lambda = (LambdaExpression) parameter.getParent();
        lambda.parameters().set(lambda.parameters().indexOf(parameter), typed);
        lambda.setParentheses(true);
    }

    private static boolean requiresDeclaredElementType(EnhancedForStatement loop) {
        IVariableBinding variable = loop.getParameter().resolveBinding();
        ITypeBinding declared = variable == null ? null : variable.getType();
        ITypeBinding source = loop.getExpression().resolveTypeBinding();
        if (declared == null || declared.isPrimitive() || source == null || loop.getParameter().getType().isVar()) return false;
        ITypeBinding inferred;
        if (source.isArray()) {
            inferred = source.getComponentType();
        } else {
            ITypeBinding iterable = Bindings.findTypeInHierarchy(source, "java.lang.Iterable"); //$NON-NLS-1$
            if (iterable == null || iterable.getTypeArguments().length != 1) return false;
            inferred = iterable.getTypeArguments()[0];
        }
        return !inferred.isPrimitive() && !declared.isEqualTo(inferred);
    }

    void preserveImports(CompilationUnitRewrite cuRewrite) {
        if (!iterableStreams.isEmpty()) {
            int position = originalBody == null ? 0 : originalBody.getStartPosition();
            if (position < 0 && originalBody instanceof Block block && !block.statements().isEmpty()) {
                position = ((Statement) block.statements().get(0)).getStartPosition();
            }
            var imports = cuRewrite.getImportRewrite();
            var context = new ContextSensitiveImportRewriteContext(cuRewrite.getRoot(), Math.max(0, position), imports);
            String support = imports.addImport("java.util.stream.StreamSupport", context); //$NON-NLS-1$
            for (MethodInvocation stream : iterableStreams) stream.setExpression(ast.newName(support));
        }
        ImportRemover importRemover = cuRewrite.getImportRemover();
        if (emittedElementType != null) importRemover.registerAddedImports(emittedElementType);
        List<SimpleName> references = new ArrayList<>();
        for (Type type : retainedMappedTypes) {
            ImportReferencesCollector.collect(type, null, null, references, null);
        }
        // The handler later marks the original declaration type removed. Register
        // copied uses independently, excluding the tail of fully qualified names.
        for (SimpleName reference : references) {
            if (reference.resolveBinding() instanceof ITypeBinding type && !type.isTypeVariable() && !type.isLocal()) {
                importRemover.registerAddedImport(reference.getIdentifier());
            }
        }
    }
    
    private Expression createExpression(String expressionText) {
        // Parse expression string to AST node
        if (expressionText == null || expressionText.isEmpty()) {
            return ast.newNullLiteral();
        }
        
        // Check for simple identifiers using Java's identifier validation
        if (NamingUtils.isValidJavaIdentifier(expressionText)) {
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
    
    /**
     * Renders a filter operation with comments using a block lambda.
     * Comments are inserted before the return statement in the block lambda body.
     * 
     * @param pipeline the current pipeline
     * @param filterOp the filter operation with comments
     * @param variableName the variable name for the lambda
     * @return the pipeline with filter appended
     */
    @SuppressWarnings("unchecked")
    private Expression renderFilterWithComments(Expression pipeline, FilterOp filterOp, String variableName) {
        MethodInvocation filterCall = ast.newMethodInvocation();
        filterCall.setExpression(pipeline);
        filterCall.setName(ast.newSimpleName(FILTER_METHOD));
        
        LambdaExpression lambda = ast.newLambdaExpression();
        VariableDeclarationFragment param = ast.newVariableDeclarationFragment();
        param.setName(ast.newSimpleName(variableName));
        lambda.parameters().add(param);
        lambda.setParentheses(false);
        
        setLambdaParameterName(param, effectiveParameterName(variableName, createExpression(filterOp.expression())), variableName);
        lambda.setBody(commentedReturnBody(filterOp.getComments(), filterOp.expression()));
        filterCall.arguments().add(lambda);
        
        return filterCall;
    }
    
    /**
     * Renders a map operation with comments using a block lambda.
     * Comments are inserted before the return statement in the block lambda body.
     * 
     * @param pipeline the current pipeline
     * @param mapOp the map operation with comments
     * @param variableName the variable name for the lambda
     * @return the pipeline with map appended
     */
    @SuppressWarnings("unchecked")
    private Expression renderMapWithComments(Expression pipeline, MapOp mapOp, String variableName) {
        MethodInvocation mapCall = ast.newMethodInvocation();
        mapCall.setExpression(pipeline);
        mapCall.setName(ast.newSimpleName(mappingMethod(mapOp.targetType())));
        
        LambdaExpression lambda = ast.newLambdaExpression();
        VariableDeclarationFragment param = ast.newVariableDeclarationFragment();
        param.setName(ast.newSimpleName(variableName));
        lambda.parameters().add(param);
        lambda.setParentheses(false);
        
        setLambdaParameterName(param, effectiveParameterName(variableName, createExpression(mapOp.expression())), variableName);
        lambda.setBody(commentedReturnBody(mapOp.getComments(), mapOp.expression()));
        mapCall.arguments().add(lambda);
        
        return mapCall;
    }

    @SuppressWarnings("unchecked")
    private Block commentedReturnBody(List<String> comments, String expression) {
        if (rewrite != null) {
            StringBuilder source = new StringBuilder("{\n"); //$NON-NLS-1$
            for (String comment : comments) {
                for (String line : comment.split("\\R", -1)) { //$NON-NLS-1$
                    source.append("// ").append(line).append('\n'); //$NON-NLS-1$
                }
            }
            source.append("return ").append(expression).append(";\n}"); //$NON-NLS-1$ //$NON-NLS-2$
            return (Block) rewrite.createStringPlaceholder(source.toString(), ASTNode.BLOCK);
        }
        Block body = ast.newBlock();
        ReturnStatement result = ast.newReturnStatement();
        result.setExpression(createExpression(expression));
        body.statements().add(result);
        return body;
    }
}
