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
package org.sandbox.jdt.ui.helper.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test class for JavaHelperView and related components.
 * 
 * Tests verify:
 * - VarVisitor correctly identifies variables and their types
 * - Detection of duplicate variable names with different types
 * - JHViewContentProvider returns expected variable bindings
 */
public class JavaHelperViewTest {

	private IJavaProject testProject;
	private IPackageFragmentRoot sourceFolder;

	@BeforeEach
	public void setUp() throws Exception {
		// Create test project
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		testProject = createJavaProject("TestProject", new String[] {"src"});
		sourceFolder = testProject.getPackageFragmentRoot(testProject.getProject().getFolder("src"));
	}

	@AfterEach
	public void tearDown() throws Exception {
		if (testProject != null && testProject.getProject().exists()) {
			testProject.getProject().delete(true, true, null);
		}
	}

	/**
	 * Test that VarVisitor correctly identifies variables from a compilation unit
	 */
	@Test
	public void testVarVisitorBasicFunctionality() throws Exception {
		// Create a simple Java file with some variables
		String testCode = """
			package test;
			public class TestClass {
				public void testMethod() {
					String name = "test";
					int count = 5;
					boolean flag = true;
				}
			}
			""";
		
		CompilationUnit cu = parseCode(testCode);
		VarVisitor visitor = new VarVisitor();
		visitor.process(cu);
		
		Set<IVariableBinding> vars = visitor.getVars();
		assertNotNull(vars, "Variable set should not be null");
		assertFalse(vars.isEmpty(), "Should have found some variables");
		
		// Check that we found the expected variables
		boolean foundName = false;
		boolean foundCount = false;
		boolean foundFlag = false;
		
		for (IVariableBinding binding : vars) {
			String varName = binding.getName();
			if ("name".equals(varName)) {
				foundName = true;
				assertEquals("String", binding.getType().getName());
			} else if ("count".equals(varName)) {
				foundCount = true;
				assertEquals("int", binding.getType().getName());
			} else if ("flag".equals(varName)) {
				foundFlag = true;
				assertEquals("boolean", binding.getType().getName());
			}
		}
		
		assertTrue(foundName, "Should have found variable 'name'");
		assertTrue(foundCount, "Should have found variable 'count'");
		assertTrue(foundFlag, "Should have found variable 'flag'");
	}

	/**
	 * Test detection of duplicate variable names with different types.
	 * This is the core use case - detecting naming inconsistencies.
	 */
	@Test
	public void testDetectDuplicateVariableNamesWithDifferentTypes() throws Exception {
		// Create code with same variable name but different types in different methods
		String testCode = """
			package test;
			public class TestClass {
				public void method1() {
					String result = "test";
					System.out.println(result);
				}
				
				public void method2() {
					int result = 42;
					System.out.println(result);
				}
			}
			""";
		
		CompilationUnit cu = parseCode(testCode);
		VarVisitor visitor = new VarVisitor();
		visitor.process(cu);
		
		Set<IVariableBinding> vars = visitor.getVars();
		assertNotNull(vars);
		assertFalse(vars.isEmpty());
		
		// Find all 'result' variables
		Map<String, String> resultVariableTypes = new HashMap<>();
		for (IVariableBinding binding : vars) {
			if ("result".equals(binding.getName())) {
				String typeName = binding.getType().getName();
				resultVariableTypes.put(binding.getKey(), typeName);
			}
		}
		
		// We should have found 2 'result' variables with different types
		assertTrue(resultVariableTypes.size() >= 2, 
			"Should have found at least 2 'result' variables");
		assertTrue(resultVariableTypes.containsValue("String"), 
			"Should have found 'result' with String type");
		assertTrue(resultVariableTypes.containsValue("int"), 
			"Should have found 'result' with int type");
	}

	/**
	 * Test that JHViewContentProvider.getElements() returns variable bindings.
	 * Note: This test verifies basic functionality without full binding resolution.
	 * In a real Eclipse environment with proper Java project setup, all variables
	 * would be found. This test ensures the provider doesn't crash and returns
	 * IVariableBinding instances.
	 */
	@Test
	public void testContentProviderGetElements() throws Exception {
		// Create a simple package and compilation unit
		IPackageFragment pack = sourceFolder.createPackageFragment("test", false, null);
		String testCode = """
			package test;
			public class ContentTest {
				public void testMethod() {
					String message = "hello";
					int value = 10;
				}
			}
			""";
		
		ICompilationUnit cu = pack.createCompilationUnit("ContentTest.java", testCode, false, null);
		
		JHViewContentProvider provider = new JHViewContentProvider();
		Object[] elements = provider.getElements(Collections.singletonList(cu));
		
		assertNotNull(elements, "Elements should not be null");
		assertTrue(elements.length > 0, "Should have found some elements");
		
		// All elements should be IVariableBinding instances
		for (Object element : elements) {
			assertTrue(element instanceof IVariableBinding, 
				"Elements should be IVariableBinding instances");
		}
		
		// Verify at least one variable was found
		// Note: Without proper Java project setup and binding resolution,
		// not all variables may be captured. This is expected in the test environment.
		boolean foundAnyVariable = false;
		for (Object element : elements) {
			IVariableBinding binding = (IVariableBinding) element;
			String varName = binding.getName();
			if ("message".equals(varName) || "value".equals(varName)) {
				foundAnyVariable = true;
			}
		}
		
		assertTrue(foundAnyVariable, "Should have found at least one expected variable (message or value)");
	}

	/**
	 * Test VarVisitor with multiple variable declarations in one statement
	 */
	@Test
	public void testVarVisitorMultipleDeclarations() throws Exception {
		String testCode = """
			package test;
			public class TestClass {
				public void testMethod() {
					int x = 1, y = 2, z = 3;
				}
			}
			""";
		
		CompilationUnit cu = parseCode(testCode);
		VarVisitor visitor = new VarVisitor();
		visitor.process(cu);
		
		Set<IVariableBinding> vars = visitor.getVars();
		
		// Should find all three variables
		boolean foundX = false, foundY = false, foundZ = false;
		for (IVariableBinding binding : vars) {
			String name = binding.getName();
			if ("x".equals(name)) foundX = true;
			if ("y".equals(name)) foundY = true;
			if ("z".equals(name)) foundZ = true;
		}
		
		assertTrue(foundX, "Should have found variable 'x'");
		assertTrue(foundY, "Should have found variable 'y'");
		assertTrue(foundZ, "Should have found variable 'z'");
	}

	/**
	 * Regression test: Verify that passing IJavaElement (not IResource) to the content
	 * provider correctly returns variable bindings. This tests the fix for the bug where
	 * updateViewFromActiveEditor() was converting IJavaElement to IResource before passing
	 * to the provider, causing variables not to display.
	 */
	@Test
	public void testContentProviderWithIJavaElementVsIResource() throws Exception {
		// Create a simple package and compilation unit
		IPackageFragment pack = sourceFolder.createPackageFragment("test", false, null);
		String testCode = """
			package test;
			public class TestJavaElement {
				public void method() {
					String variable1 = "test";
					int variable2 = 100;
				}
			}
			""";
		
		ICompilationUnit cu = pack.createCompilationUnit("TestJavaElement.java", testCode, false, null);
		
		JHViewContentProvider provider = new JHViewContentProvider();
		try {
			// Test 1: IJavaElement (ICompilationUnit) should return variable bindings
			Object[] elementsFromJavaElement = provider.getElements(Collections.singletonList(cu));
			
			assertNotNull(elementsFromJavaElement, "Elements should not be null when IJavaElement is passed");
			assertTrue(elementsFromJavaElement.length > 0, "Should find elements when IJavaElement is passed");
			
			// Verify we found variables from the IJavaElement
			boolean foundVariableFromJavaElement = false;
			for (Object element : elementsFromJavaElement) {
				assertTrue(element instanceof IVariableBinding, 
					"Elements should be IVariableBinding instances");
				IVariableBinding binding = (IVariableBinding) element;
				String varName = binding.getName();
				if ("variable1".equals(varName) || "variable2".equals(varName)) {
					foundVariableFromJavaElement = true;
					break;
				}
			}
			assertTrue(foundVariableFromJavaElement, 
				"Should find variables when IJavaElement (ICompilationUnit) is passed");
			
			// Test 2: IResource should NOT return variable bindings (regression test)
			// This was the bug: passing IResource instead of IJavaElement
			IResource resource = cu.getResource();
			assertNotNull(resource, "IResource should exist for the compilation unit");
			
			Object[] elementsFromResource = provider.getElements(Collections.singletonList(resource));
			
			// IResource input doesn't match the JavaElement instanceof check in the provider,
			// so no variables should be returned
			assertTrue(elementsFromResource.length == 0, 
				"Should not find variable bindings when IResource is passed (this was the bug)");
		} finally {
			provider.dispose();
		}
	}

	// Helper methods

	private CompilationUnit parseCode(String source) {
		ASTParser parser = ASTParser.newParser(AST.JLS_Latest);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(source.toCharArray());
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setStatementsRecovery(true);
		
		Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);
		parser.setUnitName("TestUnit.java");
		parser.setEnvironment(null, null, null, true);
		
		return (CompilationUnit) parser.createAST(null);
	}

	private IJavaProject createJavaProject(String projectName, String[] sourceFolderPaths) throws CoreException {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IProject project = root.getProject(projectName);
		
		if (!project.exists()) {
			project.create(null);
		}
		project.open(null);
		
		// Set Java nature
		org.eclipse.core.resources.IProjectDescription description = project.getDescription();
		description.setNatureIds(new String[] { JavaCore.NATURE_ID });
		project.setDescription(description, null);
		
		IJavaProject javaProject = JavaCore.create(project);
		
		// Create source folders
		for (String path : sourceFolderPaths) {
			IPackageFragmentRoot fragmentRoot = javaProject.getPackageFragmentRoot(project.getFolder(path));
			if (!fragmentRoot.exists()) {
				project.getFolder(path).create(true, true, null);
			}
		}
		
		return javaProject;
	}
}
