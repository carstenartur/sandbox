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
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sandbox.functional.core.model.SourceDescriptor;
import org.sandbox.functional.core.model.SourceDescriptor.SourceType;
import org.sandbox.functional.core.terminal.CollectTerminal;
import org.sandbox.functional.core.terminal.MatchTerminal;
import org.sandbox.jdt.internal.corext.fix.helper.ASTStreamRenderer;

/**
 * Tests for ASTStreamRenderer.
 * 
 * <p>This test class validates that ASTStreamRenderer correctly generates
 * JDT AST nodes for stream pipeline operations.</p>
 */
public class ASTStreamRendererTest {
    
    private AST ast;
    private ASTRewrite rewrite;
    private ASTStreamRenderer renderer;
    
    @BeforeEach
    void setUp() {
        ast = AST.newAST(AST.getJLSLatest(), false);
        rewrite = ASTRewrite.create(ast);
        // Pass null for CompilationUnit since these tests don't require binding resolution
        renderer = new ASTStreamRenderer(ast, rewrite, null);
    }
    
    @Test
    void testRenderSource_Collection() {
        SourceDescriptor source = new SourceDescriptor(
            SourceType.COLLECTION, "items", "String");
        
        Expression result = renderer.renderSource(source);
        
        assertNotNull(result);
        assertTrue(result instanceof MethodInvocation);
        MethodInvocation mi = (MethodInvocation) result;
        assertEquals("stream", mi.getName().getIdentifier());
        assertNotNull(mi.getExpression());
        assertTrue(mi.getExpression() instanceof SimpleName);
        assertEquals("items", ((SimpleName) mi.getExpression()).getIdentifier());
    }
    
    @Test
    void testRenderSource_Array() {
        SourceDescriptor source = new SourceDescriptor(
            SourceType.ARRAY, "arr", "int");
        
        Expression result = renderer.renderSource(source);
        
        assertNotNull(result);
        assertTrue(result instanceof MethodInvocation);
        MethodInvocation mi = (MethodInvocation) result;
        assertEquals("stream", mi.getName().getIdentifier());
        assertNotNull(mi.getExpression());
        assertTrue(mi.getExpression() instanceof SimpleName);
        assertEquals("Arrays", ((SimpleName) mi.getExpression()).getIdentifier());
        assertEquals(1, mi.arguments().size());
    }
    
    @Test
    void testRenderSource_Iterable() {
        SourceDescriptor source = new SourceDescriptor(
            SourceType.ITERABLE, "items", "String");
        
        Expression result = renderer.renderSource(source);
        
        assertNotNull(result);
        assertTrue(result instanceof MethodInvocation);
        MethodInvocation mi = (MethodInvocation) result;
        assertEquals("stream", mi.getName().getIdentifier());
        assertNotNull(mi.getExpression());
        assertTrue(mi.getExpression() instanceof SimpleName);
        assertEquals("StreamSupport", ((SimpleName) mi.getExpression()).getIdentifier());
        assertEquals(2, mi.arguments().size()); // spliterator() and false
        assertTrue(mi.arguments().get(0) instanceof MethodInvocation);
        MethodInvocation spliterator = (MethodInvocation) mi.arguments().get(0);
        assertEquals("spliterator", spliterator.getName().getIdentifier());
    }
    
    @Test
    void testRenderSource_IntRange() {
        SourceDescriptor source = new SourceDescriptor(
            SourceType.INT_RANGE, "0,10", "int");
        
        Expression result = renderer.renderSource(source);
        
        assertNotNull(result);
        assertTrue(result instanceof MethodInvocation);
        MethodInvocation mi = (MethodInvocation) result;
        assertEquals("range", mi.getName().getIdentifier());
        assertNotNull(mi.getExpression());
        assertTrue(mi.getExpression() instanceof SimpleName);
        assertEquals("IntStream", ((SimpleName) mi.getExpression()).getIdentifier());
        assertEquals(2, mi.arguments().size()); // start and end
    }
    
    @Test
    void testRenderFilter() {
        Expression pipeline = ast.newSimpleName("stream");
        
        Expression result = renderer.renderFilter(pipeline, "x != null", "x");
        
        assertNotNull(result);
        assertTrue(result instanceof MethodInvocation);
        MethodInvocation mi = (MethodInvocation) result;
        assertEquals("filter", mi.getName().getIdentifier());
        assertTrue(result.toString().contains("filter"));
    }
    
    @Test
    void testRenderMap() {
        Expression pipeline = ast.newSimpleName("stream");
        
        Expression result = renderer.renderMap(pipeline, "x.toUpperCase()", "x", "String");
        
        assertNotNull(result);
        assertTrue(result instanceof MethodInvocation);
        MethodInvocation mi = (MethodInvocation) result;
        assertEquals("map", mi.getName().getIdentifier());
    }
    
    @Test
    void testRenderFlatMap() {
        Expression pipeline = ast.newSimpleName("stream");
        
        Expression result = renderer.renderFlatMap(pipeline, "x.stream()", "x");
        
        assertNotNull(result);
        assertTrue(result instanceof MethodInvocation);
        MethodInvocation mi = (MethodInvocation) result;
        assertEquals("flatMap", mi.getName().getIdentifier());
    }
    
    @Test
    void testRenderPeek() {
        Expression pipeline = ast.newSimpleName("stream");
        
        Expression result = renderer.renderPeek(pipeline, "System.out.println(x)", "x");
        
        assertNotNull(result);
        assertTrue(result instanceof MethodInvocation);
        MethodInvocation mi = (MethodInvocation) result;
        assertEquals("peek", mi.getName().getIdentifier());
    }
    
    @Test
    void testRenderDistinct() {
        Expression pipeline = ast.newSimpleName("stream");
        
        Expression result = renderer.renderDistinct(pipeline);
        
        assertNotNull(result);
        assertTrue(result instanceof MethodInvocation);
        MethodInvocation mi = (MethodInvocation) result;
        assertEquals("distinct", mi.getName().getIdentifier());
    }
    
    @Test
    void testRenderSorted_NoComparator() {
        Expression pipeline = ast.newSimpleName("stream");
        
        Expression result = renderer.renderSorted(pipeline, null);
        
        assertNotNull(result);
        assertTrue(result instanceof MethodInvocation);
        MethodInvocation mi = (MethodInvocation) result;
        assertEquals("sorted", mi.getName().getIdentifier());
        assertTrue(mi.arguments().isEmpty());
    }
    
    @Test
    void testRenderSorted_WithComparator() {
        Expression pipeline = ast.newSimpleName("stream");
        
        Expression result = renderer.renderSorted(pipeline, "Comparator.naturalOrder()");
        
        assertNotNull(result);
        assertTrue(result instanceof MethodInvocation);
        MethodInvocation mi = (MethodInvocation) result;
        assertEquals("sorted", mi.getName().getIdentifier());
        assertFalse(mi.arguments().isEmpty());
    }
    
    @Test
    void testRenderLimit() {
        Expression pipeline = ast.newSimpleName("stream");
        
        Expression result = renderer.renderLimit(pipeline, 10);
        
        assertNotNull(result);
        assertTrue(result instanceof MethodInvocation);
        MethodInvocation mi = (MethodInvocation) result;
        assertEquals("limit", mi.getName().getIdentifier());
        assertEquals("stream.limit(10)", result.toString());
    }
    
    @Test
    void testRenderSkip() {
        Expression pipeline = ast.newSimpleName("stream");
        
        Expression result = renderer.renderSkip(pipeline, 5);
        
        assertNotNull(result);
        assertTrue(result instanceof MethodInvocation);
        MethodInvocation mi = (MethodInvocation) result;
        assertEquals("skip", mi.getName().getIdentifier());
        assertEquals("stream.skip(5)", result.toString());
    }
    
    @Test
    void testRenderForEach_SingleStatement() {
        Expression pipeline = ast.newSimpleName("stream");
        
        Expression result = renderer.renderForEach(pipeline, 
            java.util.List.of("System.out.println(x)"), "x", false);
        
        assertNotNull(result);
        assertTrue(result instanceof MethodInvocation);
        MethodInvocation mi = (MethodInvocation) result;
        assertEquals("forEach", mi.getName().getIdentifier());
    }
    
    @Test
    void testRenderForEach_MultipleStatements() {
        Expression pipeline = ast.newSimpleName("stream");
        
        Expression result = renderer.renderForEach(pipeline, 
            java.util.List.of("int y = x * 2", "System.out.println(y)"), "x", false);
        
        assertNotNull(result);
        assertTrue(result instanceof MethodInvocation);
        MethodInvocation mi = (MethodInvocation) result;
        assertEquals("forEach", mi.getName().getIdentifier());
        // Lambda should have a block body for multiple statements
        assertEquals(1, mi.arguments().size());
        assertTrue(mi.arguments().get(0) instanceof LambdaExpression);
    }
    
    @Test
    void testRenderForEach_Ordered() {
        Expression pipeline = ast.newSimpleName("stream");
        
        Expression result = renderer.renderForEach(pipeline, 
            java.util.List.of("System.out.println(x)"), "x", true);
        
        assertNotNull(result);
        assertTrue(result instanceof MethodInvocation);
        MethodInvocation mi = (MethodInvocation) result;
        assertEquals("forEachOrdered", mi.getName().getIdentifier());
    }
    
    @Test
    void testRenderCollect_ToList() {
        Expression pipeline = ast.newSimpleName("stream");
        CollectTerminal terminal = new CollectTerminal(
            CollectTerminal.CollectorType.TO_LIST, "result");
        
        Expression result = renderer.renderCollect(pipeline, terminal, "x");
        
        assertNotNull(result);
        assertTrue(result instanceof MethodInvocation);
        MethodInvocation collectCall = (MethodInvocation) result;
        assertEquals("collect", collectCall.getName().getIdentifier());
        assertEquals(1, collectCall.arguments().size());
        assertTrue(collectCall.arguments().get(0) instanceof MethodInvocation);
        
        MethodInvocation collector = (MethodInvocation) collectCall.arguments().get(0);
        assertEquals("toList", collector.getName().getIdentifier());
        assertNotNull(collector.getExpression());
        assertTrue(collector.getExpression() instanceof SimpleName);
        assertEquals("Collectors", ((SimpleName) collector.getExpression()).getIdentifier());
    }
    
    @Test
    void testRenderCollect_ToSet() {
        Expression pipeline = ast.newSimpleName("stream");
        CollectTerminal terminal = new CollectTerminal(
            CollectTerminal.CollectorType.TO_SET, "result");
        
        Expression result = renderer.renderCollect(pipeline, terminal, "x");
        
        assertNotNull(result);
        assertTrue(result instanceof MethodInvocation);
        MethodInvocation collectCall = (MethodInvocation) result;
        assertEquals("collect", collectCall.getName().getIdentifier());
        assertEquals(1, collectCall.arguments().size());
        assertTrue(collectCall.arguments().get(0) instanceof MethodInvocation);
        
        MethodInvocation collector = (MethodInvocation) collectCall.arguments().get(0);
        assertEquals("toSet", collector.getName().getIdentifier());
    }
    
    @Test
    void testRenderReduce_WithIdentity() {
        Expression pipeline = ast.newSimpleName("stream");
        org.sandbox.functional.core.terminal.ReduceTerminal terminal = 
            new org.sandbox.functional.core.terminal.ReduceTerminal(
                "0", "(a, b) -> a + b", null, 
                org.sandbox.functional.core.terminal.ReduceTerminal.ReduceType.SUM);
        
        Expression result = renderer.renderReduce(pipeline, terminal, "x");
        
        assertNotNull(result);
        assertTrue(result instanceof MethodInvocation);
        MethodInvocation mi = (MethodInvocation) result;
        assertEquals("reduce", mi.getName().getIdentifier());
        assertEquals(2, mi.arguments().size()); // identity + accumulator
    }
    
    @Test
    void testRenderReduce_WithoutIdentity() {
        Expression pipeline = ast.newSimpleName("stream");
        org.sandbox.functional.core.terminal.ReduceTerminal terminal = 
            new org.sandbox.functional.core.terminal.ReduceTerminal(
                null, "(a, b) -> a + b", null, 
                org.sandbox.functional.core.terminal.ReduceTerminal.ReduceType.CUSTOM);
        
        Expression result = renderer.renderReduce(pipeline, terminal, "x");
        
        assertNotNull(result);
        assertTrue(result instanceof MethodInvocation);
        MethodInvocation mi = (MethodInvocation) result;
        assertEquals("reduce", mi.getName().getIdentifier());
        assertEquals(1, mi.arguments().size()); // only accumulator
    }
    
    @Test
    void testRenderCount() {
        Expression pipeline = ast.newSimpleName("stream");
        
        Expression result = renderer.renderCount(pipeline);
        
        assertNotNull(result);
        assertTrue(result instanceof MethodInvocation);
        MethodInvocation mi = (MethodInvocation) result;
        assertEquals("count", mi.getName().getIdentifier());
        assertNotNull(mi.getExpression());
        assertTrue(mi.getExpression() instanceof SimpleName);
        assertEquals("stream", ((SimpleName) mi.getExpression()).getIdentifier());
    }
    
    @Test
    void testRenderFind_First() {
        Expression pipeline = ast.newSimpleName("stream");
        
        Expression result = renderer.renderFind(pipeline, true);
        
        assertNotNull(result);
        assertTrue(result instanceof MethodInvocation);
        MethodInvocation mi = (MethodInvocation) result;
        assertEquals("findFirst", mi.getName().getIdentifier());
    }
    
    @Test
    void testRenderFind_Any() {
        Expression pipeline = ast.newSimpleName("stream");
        
        Expression result = renderer.renderFind(pipeline, false);
        
        assertNotNull(result);
        assertTrue(result instanceof MethodInvocation);
        MethodInvocation mi = (MethodInvocation) result;
        assertEquals("findAny", mi.getName().getIdentifier());
    }
    
    @Test
    void testRenderMatch_AnyMatch() {
        Expression pipeline = ast.newSimpleName("stream");
        MatchTerminal terminal = new MatchTerminal(
            MatchTerminal.MatchType.ANY_MATCH, "x > 0");
        
        Expression result = renderer.renderMatch(pipeline, terminal, "x");
        
        assertNotNull(result);
        assertTrue(result instanceof MethodInvocation);
        MethodInvocation mi = (MethodInvocation) result;
        assertEquals("anyMatch", mi.getName().getIdentifier());
        assertEquals(1, mi.arguments().size());
        assertTrue(mi.arguments().get(0) instanceof LambdaExpression);
    }
    
    @Test
    void testRenderMatch_AllMatch() {
        Expression pipeline = ast.newSimpleName("stream");
        MatchTerminal terminal = new MatchTerminal(
            MatchTerminal.MatchType.ALL_MATCH, "x > 0");
        
        Expression result = renderer.renderMatch(pipeline, terminal, "x");
        
        assertNotNull(result);
        assertTrue(result instanceof MethodInvocation);
        MethodInvocation mi = (MethodInvocation) result;
        assertEquals("allMatch", mi.getName().getIdentifier());
        assertEquals(1, mi.arguments().size());
        assertTrue(mi.arguments().get(0) instanceof LambdaExpression);
    }
    
    @Test
    void testRenderMatch_NoneMatch() {
        Expression pipeline = ast.newSimpleName("stream");
        MatchTerminal terminal = new MatchTerminal(
            MatchTerminal.MatchType.NONE_MATCH, "x < 0");
        
        Expression result = renderer.renderMatch(pipeline, terminal, "x");
        
        assertNotNull(result);
        assertTrue(result instanceof MethodInvocation);
        MethodInvocation mi = (MethodInvocation) result;
        assertEquals("noneMatch", mi.getName().getIdentifier());
        assertEquals(1, mi.arguments().size());
        assertTrue(mi.arguments().get(0) instanceof LambdaExpression);
    }
    
    @Test
    void testComplexPipeline() {
        // items.stream().filter(x -> x != null).map(x -> x.toUpperCase()).forEach(x -> System.out.println(x))
        SourceDescriptor source = new SourceDescriptor(
            SourceType.COLLECTION, "items", "String");
        
        Expression pipeline = renderer.renderSource(source);
        pipeline = renderer.renderFilter(pipeline, "x != null", "x");
        pipeline = renderer.renderMap(pipeline, "x.toUpperCase()", "x", "String");
        pipeline = renderer.renderForEach(pipeline, 
            java.util.List.of("System.out.println(x)"), "x", false);
        
        assertNotNull(pipeline);
        assertTrue(pipeline instanceof MethodInvocation, "Final result should be a MethodInvocation");
        
        // Verify the pipeline structure by checking the nested method invocations
        MethodInvocation forEach = (MethodInvocation) pipeline;
        assertEquals("forEach", forEach.getName().getIdentifier());
        assertNotNull(forEach.getExpression(), "forEach should have an expression (the map call)");
        
        assertTrue(forEach.getExpression() instanceof MethodInvocation, "forEach expression should be map");
        MethodInvocation map = (MethodInvocation) forEach.getExpression();
        assertEquals("map", map.getName().getIdentifier());
        
        assertTrue(map.getExpression() instanceof MethodInvocation, "map expression should be filter");
        MethodInvocation filter = (MethodInvocation) map.getExpression();
        assertEquals("filter", filter.getName().getIdentifier());
        
        assertTrue(filter.getExpression() instanceof MethodInvocation, "filter expression should be stream");
        MethodInvocation stream = (MethodInvocation) filter.getExpression();
        assertEquals("stream", stream.getName().getIdentifier());
    }
    
    @Test
    void testGetAST() {
        assertNotNull(renderer.getAST());
        assertSame(ast, renderer.getAST());
    }
}
