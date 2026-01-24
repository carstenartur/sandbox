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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;

/**
 * Unit tests for {@link HelperVisitor} fluent API.
 * 
 * <p>These tests verify the fluent builder API for HelperVisitor operations using
 * Java built-in annotations and classes to avoid external dependencies.</p>
 * 
 * <h2>Key Features Tested:</h2>
 * <ul>
 * <li>Factory methods: {@code forAnnotation()}, {@code forMethodCalls()}, etc.</li>
 * <li>Fluent API: Method chaining for visitor configuration</li>
 * <li>Import handling: {@code andImports()}, {@code andStaticImports()}</li>
 * <li>Terminal operations: {@code processEach()}, {@code collect()}</li>
 * <li>Error handling: State validation</li>
 * </ul>
 *
 * @since 1.0
 */
@DisplayName("HelperVisitor Fluent API Tests")
public class HelperVisitorBuilderTest {

private static CompilationUnit parseSource(String source) {
ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
parser.setSource(source.toCharArray());
parser.setKind(ASTParser.K_COMPILATION_UNIT);
@SuppressWarnings("unchecked")
Map<String, String> options = JavaCore.getOptions();
JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
parser.setCompilerOptions(options);
parser.setEnvironment(new String[] {}, new String[] {}, null, true);
parser.setBindingsRecovery(true);
parser.setResolveBindings(true);
parser.setUnitName("Test.java"); //$NON-NLS-1$
return (CompilationUnit) parser.createAST(null);
}

/**
 * Tests for {@link org.sandbox.jdt.internal.common.AnnotationVisitorBuilder}.
 * Uses Java built-in annotations like @Deprecated, @Override, @SuppressWarnings.
 */
@Nested
@DisplayName("AnnotationVisitorBuilder Tests")
class AnnotationVisitorBuilderTests {

@Test
@DisplayName("Find @Deprecated annotations by fully qualified name")
void testFindDeprecatedAnnotations() {
String source = """
public class TestClass {
@Deprecated
public void oldMethod() {
}

public void newMethod() {
}
}
""";

CompilationUnit cu = parseSource(source);
List<ASTNode> nodes = new ArrayList<>();

HelperVisitor.forAnnotation("java.lang.Deprecated") //$NON-NLS-1$
.in(cu)
.excluding(new HashSet<>())
.processEach((node, holder) -> {
nodes.add(node);
return true;
});

assertEquals(1, nodes.size(), "Should find one @Deprecated annotation"); //$NON-NLS-1$
assertTrue(nodes.get(0) instanceof MarkerAnnotation);
}

@Test
@DisplayName("Find @Override annotations")
void testFindOverrideAnnotations() {
String source = """
public class TestClass {
@Override
public String toString() {
return "test";
}
}
""";

CompilationUnit cu = parseSource(source);
List<ASTNode> nodes = new ArrayList<>();

HelperVisitor.forAnnotation("java.lang.Override") //$NON-NLS-1$
.in(cu)
.excluding(new HashSet<>())
.processEach((node, holder) -> {
nodes.add(node);
return true;
});

assertEquals(1, nodes.size(), "Should find one @Override annotation"); //$NON-NLS-1$
}

@Test
@DisplayName("Find @SuppressWarnings annotations (SingleMemberAnnotation)")
void testFindSuppressWarningsAnnotations() {
String source = """
public class TestClass {
@SuppressWarnings("unchecked")
public void test() {
}
}
""";

CompilationUnit cu = parseSource(source);
List<ASTNode> nodes = new ArrayList<>();

HelperVisitor.forAnnotation("java.lang.SuppressWarnings") //$NON-NLS-1$
.in(cu)
.excluding(new HashSet<>())
.processEach((node, holder) -> {
nodes.add(node);
return true;
});

assertEquals(1, nodes.size(), "Should find one @SuppressWarnings annotation"); //$NON-NLS-1$
}

@Test
@DisplayName("Collect annotations using collect() method")
void testCollectAnnotations() {
String source = """
public class TestClass {
@Deprecated
public void oldMethod1() {
}

@Deprecated
public void oldMethod2() {
}
}
""";

CompilationUnit cu = parseSource(source);

List<ASTNode> nodes = HelperVisitor.forAnnotation("java.lang.Deprecated") //$NON-NLS-1$
.in(cu)
.excluding(new HashSet<>())
.collect();

assertEquals(2, nodes.size(), "Should collect two @Deprecated annotations"); //$NON-NLS-1$
}

@Test
@DisplayName("Validation: processEach without compilationUnit should throw")
void testValidationWithoutCompilationUnit() {
assertThrows(IllegalStateException.class, () -> {
HelperVisitor.forAnnotation("java.lang.Deprecated") //$NON-NLS-1$
.excluding(new HashSet<>())
.processEach((node, holder) -> true);
});
}
}

/**
 * Tests for {@link org.sandbox.jdt.internal.common.MethodCallVisitorBuilder}.
 * Uses String methods which are always available.
 */
@Nested
@DisplayName("MethodCallVisitorBuilder Tests")
class MethodCallVisitorBuilderTests {

@Test
@DisplayName("Find single method call")
void testFindSingleMethodCall() {
String source = """
public class TestClass {
public void test() {
String s = "hello";
int len = s.length();
}
}
""";

CompilationUnit cu = parseSource(source);
List<ASTNode> nodes = new ArrayList<>();

HelperVisitor.forMethodCall("java.lang.String", "length") //$NON-NLS-1$ //$NON-NLS-2$
.in(cu)
.excluding(new HashSet<>())
.processEach((node, holder) -> {
nodes.add(node);
return true;
});

assertEquals(1, nodes.size(), "Should find one length() call"); //$NON-NLS-1$
assertTrue(nodes.get(0) instanceof MethodInvocation);
}

@Test
@DisplayName("Find multiple method calls")
void testFindMultipleMethodCalls() {
String source = """
public class TestClass {
public void test() {
String s = "hello";
int len = s.length();
String upper = s.toUpperCase();
String lower = s.toLowerCase();
}
}
""";

CompilationUnit cu = parseSource(source);
List<ASTNode> nodes = new ArrayList<>();

Set<String> methods = Set.of("length", "toUpperCase", "toLowerCase"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
HelperVisitor.forMethodCalls("java.lang.String", methods) //$NON-NLS-1$
.in(cu)
.excluding(new HashSet<>())
.processEach((node, holder) -> {
nodes.add(node);
return true;
});

assertEquals(3, nodes.size(), "Should find three method calls"); //$NON-NLS-1$
}

@Test
@DisplayName("Collect method calls using collect()")
void testCollectMethodCalls() {
String source = """
public class TestClass {
public void test() {
String s = "hello";
s.trim();
s.trim();
}
}
""";

CompilationUnit cu = parseSource(source);

List<ASTNode> nodes = HelperVisitor.forMethodCall("java.lang.String", "trim") //$NON-NLS-1$ //$NON-NLS-2$
.in(cu)
.excluding(new HashSet<>())
.collect();

assertEquals(2, nodes.size(), "Should collect two trim() calls"); //$NON-NLS-1$
}
}

/**
 * Tests for {@link org.sandbox.jdt.internal.common.FieldVisitorBuilder}.
 */
@Nested
@DisplayName("FieldVisitorBuilder Tests")
class FieldVisitorBuilderTests {

@Test
@DisplayName("Validation: FieldVisitorBuilder requires both annotation and type")
void testValidationRequiresAnnotationAndType() {
String source = """
public class TestClass {
@Deprecated
public String field;
}
""";

CompilationUnit cu = parseSource(source);

// Missing type - should throw
assertThrows(IllegalStateException.class, () -> {
HelperVisitor.forField()
.withAnnotation("java.lang.Deprecated") //$NON-NLS-1$
.in(cu)
.excluding(new HashSet<>())
.processEach((node, holder) -> true);
});

// Missing annotation - should throw
assertThrows(IllegalStateException.class, () -> {
HelperVisitor.forField()
.ofType("java.lang.String") //$NON-NLS-1$
.in(cu)
.excluding(new HashSet<>())
.processEach((node, holder) -> true);
});
}
}

/**
 * Tests for {@link org.sandbox.jdt.internal.common.ImportVisitorBuilder}.
 */
@Nested
@DisplayName("ImportVisitorBuilder Tests")
class ImportVisitorBuilderTests {

@Test
@DisplayName("Find regular imports")
void testFindRegularImports() {
String source = """
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

public class TestClass {
private List<String> list = new ArrayList<>();
}
""";

CompilationUnit cu = parseSource(source);
List<ASTNode> nodes = new ArrayList<>();

HelperVisitor.forImport("java.util.List") //$NON-NLS-1$
.in(cu)
.excluding(new HashSet<>())
.processEach((node, holder) -> {
nodes.add(node);
return true;
});

assertEquals(1, nodes.size(), "Should find one import for java.util.List"); //$NON-NLS-1$
assertTrue(nodes.get(0) instanceof ImportDeclaration);
}

@Test
@DisplayName("Find static imports")
void testFindStaticImports() {
String source = """
import static java.lang.Math.PI;
import static java.lang.Math.sqrt;

public class TestClass {
double radius = PI;
}
""";

CompilationUnit cu = parseSource(source);
List<ASTNode> nodes = new ArrayList<>();

HelperVisitor.forImport("java.lang.Math.PI") //$NON-NLS-1$
.in(cu)
.excluding(new HashSet<>())
.processEach((node, holder) -> {
nodes.add(node);
return true;
});

assertEquals(1, nodes.size(), "Should find one static import for Math.PI"); //$NON-NLS-1$
assertTrue(nodes.get(0) instanceof ImportDeclaration);
assertTrue(((ImportDeclaration) nodes.get(0)).isStatic());
}
}

/**
 * Tests for early termination and processing control.
 */
@Nested
@DisplayName("Processing Control Tests")
class ProcessingControlTests {

@Test
@DisplayName("Early termination when processor returns false")
void testEarlyTermination() {
String source = """
public class TestClass {
@Deprecated
public void method1() {}

@Deprecated
public void method2() {}

@Deprecated
public void method3() {}
}
""";

CompilationUnit cu = parseSource(source);
List<ASTNode> nodes = new ArrayList<>();

HelperVisitor.forAnnotation("java.lang.Deprecated") //$NON-NLS-1$
.in(cu)
.excluding(new HashSet<>())
.processEach((node, holder) -> {
nodes.add(node);
return nodes.size() < 2; // Stop after 2 nodes
});

assertEquals(2, nodes.size(), "Should stop after processing 2 nodes"); //$NON-NLS-1$
}

@Test
@DisplayName("Excluding processed nodes")
void testExcludingProcessedNodes() {
String source = """
public class TestClass {
@Deprecated
public void method1() {}

@Deprecated
public void method2() {}
}
""";

CompilationUnit cu = parseSource(source);
Set<ASTNode> processed = new HashSet<>();

// First pass - find all
HelperVisitor.forAnnotation("java.lang.Deprecated") //$NON-NLS-1$
.in(cu)
.excluding(new HashSet<>())
.processEach((node, holder) -> {
processed.add(node);
return true;
});

assertEquals(2, processed.size());

// Second pass - should find none (all excluded)
List<ASTNode> secondPass = new ArrayList<>();
HelperVisitor.forAnnotation("java.lang.Deprecated") //$NON-NLS-1$
.in(cu)
.excluding(processed)
.processEach((node, holder) -> {
secondPass.add(node);
return true;
});

assertEquals(0, secondPass.size(), "Should find no nodes when all are excluded"); //$NON-NLS-1$
}
}

/**
 * Tests for ReferenceHolder integration.
 */
@Nested
@DisplayName("ReferenceHolder Integration Tests")
class ReferenceHolderTests {

@Test
@DisplayName("ReferenceHolder can be used to collect data")
void testReferenceHolderDataCollection() {
String source = """
public class TestClass {
@Deprecated
public void method1() {}

@Deprecated
public void method2() {}
}
""";

CompilationUnit cu = parseSource(source);
ReferenceHolder<Integer, List<String>> holder = new ReferenceHolder<>();
holder.put(0, new ArrayList<>());

HelperVisitor.forAnnotation("java.lang.Deprecated") //$NON-NLS-1$
.in(cu)
.excluding(new HashSet<>())
.processEach(holder, (node, h) -> {
@SuppressWarnings("unchecked")
List<String> data = (List<String>) h.get(0);
data.add(node.toString());
return true;
});

@SuppressWarnings("unchecked")
List<String> collected = (List<String>) holder.get(0);
assertEquals(2, collected.size(), "Should have collected 2 annotations"); //$NON-NLS-1$
}
}
}
