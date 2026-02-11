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
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.helper.ConcurrentCollectionDetector;

/**
 * Unit tests for {@link ConcurrentCollectionDetector}.
 * 
 * <p>Tests detection of concurrent collection types that require special handling.</p>
 * 
 * @see ConcurrentCollectionDetector
 * @see <a href="https://github.com/carstenartur/sandbox/issues/670">Issue #670 - Point 2.4</a>
 */
@DisplayName("ConcurrentCollectionDetector Tests")
public class ConcurrentCollectionDetectorTest {

	/**
	 * Parses a Java code snippet and extracts the type binding of a field.
	 */
	private ITypeBinding parseFieldTypeBinding(String code) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);
		parser.setSource(code.toCharArray());
		parser.setUnitName("Test.java");
		parser.setResolveBindings(true);
		parser.setEnvironment(null, null, null, true);
		
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		TypeDeclaration type = (TypeDeclaration) cu.types().get(0);
		FieldDeclaration field = type.getFields()[0];
		VariableDeclarationFragment fragment = (VariableDeclarationFragment) field.fragments().get(0);
		return fragment.resolveBinding().getType();
	}

	@Test
	@DisplayName("Should detect CopyOnWriteArrayList as concurrent")
	void testCopyOnWriteArrayList() {
		String code = """
				import java.util.concurrent.CopyOnWriteArrayList;
				class Test {
					CopyOnWriteArrayList<String> list;
				}
				""";
		ITypeBinding binding = parseFieldTypeBinding(code);
		assertTrue(ConcurrentCollectionDetector.isConcurrentCollection(binding));
	}

	@Test
	@DisplayName("Should detect CopyOnWriteArraySet as concurrent")
	void testCopyOnWriteArraySet() {
		String code = """
				import java.util.concurrent.CopyOnWriteArraySet;
				class Test {
					CopyOnWriteArraySet<String> set;
				}
				""";
		ITypeBinding binding = parseFieldTypeBinding(code);
		assertTrue(ConcurrentCollectionDetector.isConcurrentCollection(binding));
	}

	@Test
	@DisplayName("Should detect ConcurrentHashMap as concurrent")
	void testConcurrentHashMap() {
		String code = """
				import java.util.concurrent.ConcurrentHashMap;
				class Test {
					ConcurrentHashMap<String, String> map;
				}
				""";
		ITypeBinding binding = parseFieldTypeBinding(code);
		assertTrue(ConcurrentCollectionDetector.isConcurrentCollection(binding));
	}

	@Test
	@DisplayName("Should detect ConcurrentSkipListMap as concurrent")
	void testConcurrentSkipListMap() {
		String code = """
				import java.util.concurrent.ConcurrentSkipListMap;
				class Test {
					ConcurrentSkipListMap<String, String> map;
				}
				""";
		ITypeBinding binding = parseFieldTypeBinding(code);
		assertTrue(ConcurrentCollectionDetector.isConcurrentCollection(binding));
	}

	@Test
	@DisplayName("Should detect ConcurrentLinkedQueue as concurrent")
	void testConcurrentLinkedQueue() {
		String code = """
				import java.util.concurrent.ConcurrentLinkedQueue;
				class Test {
					ConcurrentLinkedQueue<String> queue;
				}
				""";
		ITypeBinding binding = parseFieldTypeBinding(code);
		assertTrue(ConcurrentCollectionDetector.isConcurrentCollection(binding));
	}

	@Test
	@DisplayName("Should detect LinkedBlockingQueue as concurrent")
	void testLinkedBlockingQueue() {
		String code = """
				import java.util.concurrent.LinkedBlockingQueue;
				class Test {
					LinkedBlockingQueue<String> queue;
				}
				""";
		ITypeBinding binding = parseFieldTypeBinding(code);
		assertTrue(ConcurrentCollectionDetector.isConcurrentCollection(binding));
	}

	@Test
	@DisplayName("Should NOT detect ArrayList as concurrent")
	void testArrayListNotConcurrent() {
		String code = """
				import java.util.ArrayList;
				class Test {
					ArrayList<String> list;
				}
				""";
		ITypeBinding binding = parseFieldTypeBinding(code);
		assertFalse(ConcurrentCollectionDetector.isConcurrentCollection(binding));
	}

	@Test
	@DisplayName("Should NOT detect HashMap as concurrent")
	void testHashMapNotConcurrent() {
		String code = """
				import java.util.HashMap;
				class Test {
					HashMap<String, String> map;
				}
				""";
		ITypeBinding binding = parseFieldTypeBinding(code);
		assertFalse(ConcurrentCollectionDetector.isConcurrentCollection(binding));
	}

	@Test
	@DisplayName("Should NOT detect LinkedList as concurrent")
	void testLinkedListNotConcurrent() {
		String code = """
				import java.util.LinkedList;
				class Test {
					LinkedList<String> list;
				}
				""";
		ITypeBinding binding = parseFieldTypeBinding(code);
		assertFalse(ConcurrentCollectionDetector.isConcurrentCollection(binding));
	}

	@Test
	@DisplayName("Should detect by qualified name - CopyOnWriteArrayList")
	void testByQualifiedNameCopyOnWrite() {
		assertTrue(ConcurrentCollectionDetector.isConcurrentCollection(
			"java.util.concurrent.CopyOnWriteArrayList"));
	}

	@Test
	@DisplayName("Should detect by qualified name - ConcurrentHashMap")
	void testByQualifiedNameConcurrentHashMap() {
		assertTrue(ConcurrentCollectionDetector.isConcurrentCollection(
			"java.util.concurrent.ConcurrentHashMap"));
	}

	@Test
	@DisplayName("Should NOT detect by qualified name - ArrayList")
	void testByQualifiedNameArrayList() {
		assertFalse(ConcurrentCollectionDetector.isConcurrentCollection(
			"java.util.ArrayList"));
	}

	@Test
	@DisplayName("Should handle null type binding")
	void testNullTypeBinding() {
		assertFalse(ConcurrentCollectionDetector.isConcurrentCollection((ITypeBinding) null));
	}

	@Test
	@DisplayName("Should handle null qualified name")
	void testNullQualifiedName() {
		assertFalse(ConcurrentCollectionDetector.isConcurrentCollection((String) null));
	}
}
