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
        renderer = new ASTStreamRenderer(ast, rewrite);
    }
    
    @Test
    void testRenderSource_Collection() {
        SourceDescriptor source = new SourceDescriptor(
            SourceType.COLLECTION, "items", "String");
        
        Expression result = renderer.renderSource(source);
        
        assertNotNull(result);
        assertTrue(result instanceof MethodInvocation);
        assertEquals("items.stream()", result.toString());
    }
    
    @Test
    void testRenderSource_Array() {
        SourceDescriptor source = new SourceDescriptor(
            SourceType.ARRAY, "arr", "int");
        
        Expression result = renderer.renderSource(source);
        
        assertNotNull(result);
        assertTrue(result instanceof MethodInvocation);
        assertEquals("Arrays.stream(arr)", result.toString());
    }
    
    @Test
    void testRenderSource_Iterable() {
        SourceDescriptor source = new SourceDescriptor(
            SourceType.ITERABLE, "items", "String");
        
        Expression result = renderer.renderSource(source);
        
        assertNotNull(result);
        assertTrue(result instanceof MethodInvocation);
        assertTrue(result.toString().contains("StreamSupport.stream"));
        assertTrue(result.toString().contains("spliterator"));
    }
    
    @Test
    void testRenderSource_IntRange() {
        SourceDescriptor source = new SourceDescriptor(
            SourceType.INT_RANGE, "0,10", "int");
        
        Expression result = renderer.renderSource(source);
        
        assertNotNull(result);
        assertTrue(result instanceof MethodInvocation);
        assertEquals("IntStream.range(0,10)", result.toString());
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
        assertTrue(result.toString().contains("collect"));
        assertTrue(result.toString().contains("Collectors.toList"));
    }
    
    @Test
    void testRenderCollect_ToSet() {
        Expression pipeline = ast.newSimpleName("stream");
        CollectTerminal terminal = new CollectTerminal(
            CollectTerminal.CollectorType.TO_SET, "result");
        
        Expression result = renderer.renderCollect(pipeline, terminal, "x");
        
        assertNotNull(result);
        assertTrue(result.toString().contains("toSet"));
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
        assertEquals("stream.count()", result.toString());
    }
    
    @Test
    void testRenderFind_First() {
        Expression pipeline = ast.newSimpleName("stream");
        
        Expression result = renderer.renderFind(pipeline, true);
        
        assertNotNull(result);
        assertTrue(result.toString().contains("findFirst"));
    }
    
    @Test
    void testRenderFind_Any() {
        Expression pipeline = ast.newSimpleName("stream");
        
        Expression result = renderer.renderFind(pipeline, false);
        
        assertNotNull(result);
        assertTrue(result.toString().contains("findAny"));
    }
    
    @Test
    void testRenderMatch_AnyMatch() {
        Expression pipeline = ast.newSimpleName("stream");
        MatchTerminal terminal = new MatchTerminal(
            MatchTerminal.MatchType.ANY_MATCH, "x > 0");
        
        Expression result = renderer.renderMatch(pipeline, terminal, "x");
        
        assertNotNull(result);
        assertTrue(result.toString().contains("anyMatch"));
    }
    
    @Test
    void testRenderMatch_AllMatch() {
        Expression pipeline = ast.newSimpleName("stream");
        MatchTerminal terminal = new MatchTerminal(
            MatchTerminal.MatchType.ALL_MATCH, "x > 0");
        
        Expression result = renderer.renderMatch(pipeline, terminal, "x");
        
        assertNotNull(result);
        assertTrue(result.toString().contains("allMatch"));
    }
    
    @Test
    void testRenderMatch_NoneMatch() {
        Expression pipeline = ast.newSimpleName("stream");
        MatchTerminal terminal = new MatchTerminal(
            MatchTerminal.MatchType.NONE_MATCH, "x < 0");
        
        Expression result = renderer.renderMatch(pipeline, terminal, "x");
        
        assertNotNull(result);
        assertTrue(result.toString().contains("noneMatch"));
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
        String code = pipeline.toString();
        assertTrue(code.contains("stream()"));
        assertTrue(code.contains("filter"));
        assertTrue(code.contains("map"));
        assertTrue(code.contains("forEach"));
    }
    
    @Test
    void testGetAST() {
        assertNotNull(renderer.getAST());
        assertSame(ast, renderer.getAST());
    }
}
