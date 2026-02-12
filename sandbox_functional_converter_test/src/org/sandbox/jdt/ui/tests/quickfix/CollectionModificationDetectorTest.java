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

import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.helper.CollectionModificationDetector;

/**
 * Unit tests for {@link CollectionModificationDetector}.
 * 
 * <p>Tests detection of collection modification methods including:</p>
 * <ul>
 * <li>Collection methods (add, remove, clear, etc.)</li>
 * <li>Map methods (put, putIfAbsent, compute, etc.)</li>
 * <li>Field access receivers (this.list.remove(x))</li>
 * </ul>
 * 
 * @see CollectionModificationDetector
 * @see <a href="https://github.com/carstenartur/sandbox/issues/670">Issue #670</a>
 */
@DisplayName("CollectionModificationDetector Tests")
public class CollectionModificationDetectorTest {

	/**
	 * Parses a Java code snippet and extracts the first MethodInvocation.
	 */
	private MethodInvocation parseMethodInvocation(String code) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);
		parser.setSource(code.toCharArray());
		parser.setUnitName("Test.java");
		
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		TypeDeclaration type = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = type.getMethods()[0];
		Block body = method.getBody();
		ExpressionStatement stmt = (ExpressionStatement) body.statements().get(0);
		return (MethodInvocation) stmt.getExpression();
	}

	@Test
	@DisplayName("Should detect list.remove() modification")
	void testListRemove() {
		String code = """
				import java.util.List;
				class Test {
					void test(List<String> list) {
						list.remove(0);
					}
				}
				""";
		MethodInvocation inv = parseMethodInvocation(code);
		assertTrue(CollectionModificationDetector.isModification(inv, "list"));
	}

	@Test
	@DisplayName("Should detect list.add() modification")
	void testListAdd() {
		String code = """
				import java.util.List;
				class Test {
					void test(List<String> list) {
						list.add("item");
					}
				}
				""";
		MethodInvocation inv = parseMethodInvocation(code);
		assertTrue(CollectionModificationDetector.isModification(inv, "list"));
	}

	@Test
	@DisplayName("Should detect list.clear() modification")
	void testListClear() {
		String code = """
				import java.util.List;
				class Test {
					void test(List<String> list) {
						list.clear();
					}
				}
				""";
		MethodInvocation inv = parseMethodInvocation(code);
		assertTrue(CollectionModificationDetector.isModification(inv, "list"));
	}

	@Test
	@DisplayName("Should detect list.addAll() modification")
	void testListAddAll() {
		String code = """
				import java.util.List;
				class Test {
					void test(List<String> list, List<String> other) {
						list.addAll(other);
					}
				}
				""";
		MethodInvocation inv = parseMethodInvocation(code);
		assertTrue(CollectionModificationDetector.isModification(inv, "list"));
	}

	@Test
	@DisplayName("Should detect list.removeIf() modification")
	void testListRemoveIf() {
		String code = """
				import java.util.List;
				class Test {
					void test(List<String> list) {
						list.removeIf(s -> s.isEmpty());
					}
				}
				""";
		MethodInvocation inv = parseMethodInvocation(code);
		assertTrue(CollectionModificationDetector.isModification(inv, "list"));
	}

	@Test
	@DisplayName("Should detect list.replaceAll() modification")
	void testListReplaceAll() {
		String code = """
				import java.util.List;
				class Test {
					void test(List<String> list) {
						list.replaceAll(String::toUpperCase);
					}
				}
				""";
		MethodInvocation inv = parseMethodInvocation(code);
		assertTrue(CollectionModificationDetector.isModification(inv, "list"));
	}

	@Test
	@DisplayName("Should detect list.sort() modification")
	void testListSort() {
		String code = """
				import java.util.List;
				class Test {
					void test(List<String> list) {
						list.sort(null);
					}
				}
				""";
		MethodInvocation inv = parseMethodInvocation(code);
		assertTrue(CollectionModificationDetector.isModification(inv, "list"));
	}

	@Test
	@DisplayName("Should detect map.put() modification")
	void testMapPut() {
		String code = """
				import java.util.Map;
				class Test {
					void test(Map<String, String> map) {
						map.put("key", "value");
					}
				}
				""";
		MethodInvocation inv = parseMethodInvocation(code);
		assertTrue(CollectionModificationDetector.isModification(inv, "map"));
	}

	@Test
	@DisplayName("Should detect map.putIfAbsent() modification")
	void testMapPutIfAbsent() {
		String code = """
				import java.util.Map;
				class Test {
					void test(Map<String, String> map) {
						map.putIfAbsent("key", "value");
					}
				}
				""";
		MethodInvocation inv = parseMethodInvocation(code);
		assertTrue(CollectionModificationDetector.isModification(inv, "map"));
	}

	@Test
	@DisplayName("Should detect map.compute() modification")
	void testMapCompute() {
		String code = """
				import java.util.Map;
				class Test {
					void test(Map<String, Integer> map) {
						map.compute("key", (k, v) -> v == null ? 1 : v + 1);
					}
				}
				""";
		MethodInvocation inv = parseMethodInvocation(code);
		assertTrue(CollectionModificationDetector.isModification(inv, "map"));
	}

	@Test
	@DisplayName("Should detect map.computeIfAbsent() modification")
	void testMapComputeIfAbsent() {
		String code = """
				import java.util.Map;
				class Test {
					void test(Map<String, String> map) {
						map.computeIfAbsent("key", k -> "value");
					}
				}
				""";
		MethodInvocation inv = parseMethodInvocation(code);
		assertTrue(CollectionModificationDetector.isModification(inv, "map"));
	}

	@Test
	@DisplayName("Should detect map.computeIfPresent() modification")
	void testMapComputeIfPresent() {
		String code = """
				import java.util.Map;
				class Test {
					void test(Map<String, Integer> map) {
						map.computeIfPresent("key", (k, v) -> v + 1);
					}
				}
				""";
		MethodInvocation inv = parseMethodInvocation(code);
		assertTrue(CollectionModificationDetector.isModification(inv, "map"));
	}

	@Test
	@DisplayName("Should detect map.merge() modification")
	void testMapMerge() {
		String code = """
				import java.util.Map;
				class Test {
					void test(Map<String, Integer> map) {
						map.merge("key", 1, Integer::sum);
					}
				}
				""";
		MethodInvocation inv = parseMethodInvocation(code);
		assertTrue(CollectionModificationDetector.isModification(inv, "map"));
	}

	@Test
	@DisplayName("Should detect map.replace() modification")
	void testMapReplace() {
		String code = """
				import java.util.Map;
				class Test {
					void test(Map<String, String> map) {
						map.replace("key", "newValue");
					}
				}
				""";
		MethodInvocation inv = parseMethodInvocation(code);
		assertTrue(CollectionModificationDetector.isModification(inv, "map"));
	}

	@Test
	@DisplayName("Should detect this.list.remove() modification")
	void testFieldAccessModification() {
		String code = """
				import java.util.List;
				class Test {
					List<String> list;
					void test() {
						this.list.remove(0);
					}
				}
				""";
		MethodInvocation inv = parseMethodInvocation(code);
		assertTrue(CollectionModificationDetector.isModification(inv, "list"));
	}

	@Test
	@DisplayName("Should NOT detect non-modifying methods")
	void testNonModifyingMethods() {
		String code = """
				import java.util.List;
				class Test {
					void test(List<String> list) {
						list.get(0);
					}
				}
				""";
		MethodInvocation inv = parseMethodInvocation(code);
		assertFalse(CollectionModificationDetector.isModification(inv, "list"));
	}

	@Test
	@DisplayName("Should NOT detect modification on different collection")
	void testDifferentCollection() {
		String code = """
				import java.util.List;
				class Test {
					void test(List<String> list, List<String> other) {
						other.remove(0);
					}
				}
				""";
		MethodInvocation inv = parseMethodInvocation(code);
		assertFalse(CollectionModificationDetector.isModification(inv, "list"));
	}
	
	@Test
	@DisplayName("Should detect getList().remove() modification")
	void testGetterMethodInvocationRemove() {
		String code = """
				import java.util.List;
				class Test {
					List<String> getList() { return null; }
					void test() {
						getList().remove(0);
					}
				}
				""";
		MethodInvocation inv = parseMethodInvocation(code);
		assertTrue(CollectionModificationDetector.isModification(inv, "list"));
	}
	
	@Test
	@DisplayName("Should detect getItems().add() modification")
	void testGetterMethodInvocationAdd() {
		String code = """
				import java.util.List;
				class Test {
					List<String> getItems() { return null; }
					void test() {
						getItems().add("item");
					}
				}
				""";
		MethodInvocation inv = parseMethodInvocation(code);
		assertTrue(CollectionModificationDetector.isModification(inv, "items"));
	}
	
	@Test
	@DisplayName("Should detect fetchMap().put() modification")
	void testFetchMapPut() {
		String code = """
				import java.util.Map;
				class Test {
					Map<String, String> fetchMap() { return null; }
					void test() {
						fetchMap().put("key", "value");
					}
				}
				""";
		MethodInvocation inv = parseMethodInvocation(code);
		assertTrue(CollectionModificationDetector.isModification(inv, "map"));
	}
	
	@Test
	@DisplayName("Should detect retrieveData().clear() modification")
	void testRetrieveDataClear() {
		String code = """
				import java.util.List;
				class Test {
					List<String> retrieveData() { return null; }
					void test() {
						retrieveData().clear();
					}
				}
				""";
		MethodInvocation inv = parseMethodInvocation(code);
		assertTrue(CollectionModificationDetector.isModification(inv, "data"));
	}
	
	@Test
	@DisplayName("Should NOT detect getList().get() - non-modifying method")
	void testGetterNonModifyingMethod() {
		String code = """
				import java.util.List;
				class Test {
					List<String> getList() { return null; }
					void test() {
						getList().get(0);
					}
				}
				""";
		MethodInvocation inv = parseMethodInvocation(code);
		assertFalse(CollectionModificationDetector.isModification(inv, "list"));
	}
	
	@Test
	@DisplayName("Should NOT detect getter with arguments")
	void testGetterWithArguments() {
		String code = """
				import java.util.List;
				class Test {
					List<String> getList(int index) { return null; }
					void test() {
						getList(0).remove(0);
					}
				}
				""";
		MethodInvocation inv = parseMethodInvocation(code);
		assertFalse(CollectionModificationDetector.isModification(inv, "list"));
	}
	
	@Test
	@DisplayName("Should NOT detect non-matching getter name")
	void testNonMatchingGetterName() {
		String code = """
				import java.util.List;
				class Test {
					List<String> getItems() { return null; }
					void test() {
						getItems().remove(0);
					}
				}
				""";
		MethodInvocation inv = parseMethodInvocation(code);
		// Looking for "list" but getter is "getItems()"
		assertFalse(CollectionModificationDetector.isModification(inv, "list"));
	}
}
