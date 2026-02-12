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
package org.eclipse.jdt.ui.tests.quickfix;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava8;

/**
 * Tests for Use General Type cleanup - widens variable declarations to more general types
 * (e.g., ArrayList -> List, HashMap -> Map).
 */
public class UseGeneralTypeCleanUpTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava8();

	// Positive test cases - cleanup SHOULD change the code

	@Test
	public void testHashMapToMap() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("Test.java", //$NON-NLS-1$
				"""
				package test;
				import java.util.HashMap;
				
				public class Test {
				    public void method() {
				        HashMap<String, Integer> map = new HashMap<>();
				        map.put("a", 1);
				        map.get("a");
				    }
				}
				""",
				false, null);
		context.enable(MYCleanUpConstants.USE_GENERAL_TYPE_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] {cu}, new String[] {
				"""
				package test;
				import java.util.HashMap;
				import java.util.Map;
				
				public class Test {
				    public void method() {
				        Map<String, Integer> map = new HashMap<>();
				        map.put("a", 1);
				        map.get("a");
				    }
				}
				"""
		}, null);
	}

	@Test
	public void testArrayListToList() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("Test.java", //$NON-NLS-1$
				"""
				package test;
				import java.util.ArrayList;
				
				public class Test {
				    public void method() {
				        ArrayList<String> list = new ArrayList<>();
				        list.add("a");
				        list.size();
				    }
				}
				""",
				false, null);
		context.enable(MYCleanUpConstants.USE_GENERAL_TYPE_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] {cu}, new String[] {
				"""
				package test;
				import java.util.ArrayList;
				import java.util.List;
				
				public class Test {
				    public void method() {
				        List<String> list = new ArrayList<>();
				        list.add("a");
				        list.size();
				    }
				}
				"""
		}, null);
	}

	@Test
	public void testLinkedListToList() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("Test.java", //$NON-NLS-1$
				"""
				package test;
				import java.util.LinkedList;
				
				public class Test {
				    public void method() {
				        LinkedList<String> list = new LinkedList<>();
				        list.add("a");
				        list.size();
				    }
				}
				""",
				false, null);
		context.enable(MYCleanUpConstants.USE_GENERAL_TYPE_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] {cu}, new String[] {
				"""
				package test;
				import java.util.LinkedList;
				import java.util.List;
				
				public class Test {
				    public void method() {
				        List<String> list = new LinkedList<>();
				        list.add("a");
				        list.size();
				    }
				}
				"""
		}, null);
	}

	@Test
	public void testHashSetToSet() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("Test.java", //$NON-NLS-1$
				"""
				package test;
				import java.util.HashSet;
				
				public class Test {
				    public void method() {
				        HashSet<String> set = new HashSet<>();
				        set.add("a");
				        set.contains("a");
				    }
				}
				""",
				false, null);
		context.enable(MYCleanUpConstants.USE_GENERAL_TYPE_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] {cu}, new String[] {
				"""
				package test;
				import java.util.HashSet;
				import java.util.Set;
				
				public class Test {
				    public void method() {
				        Set<String> set = new HashSet<>();
				        set.add("a");
				        set.contains("a");
				    }
				}
				"""
		}, null);
	}

	@Test
	public void testLinkedHashMapToMap() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("Test.java", //$NON-NLS-1$
				"""
				package test;
				import java.util.LinkedHashMap;
				
				public class Test {
				    public void method() {
				        LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
				        map.put("a", 1);
				    }
				}
				""",
				false, null);
		context.enable(MYCleanUpConstants.USE_GENERAL_TYPE_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] {cu}, new String[] {
				"""
				package test;
				import java.util.LinkedHashMap;
				import java.util.Map;
				
				public class Test {
				    public void method() {
				        Map<String, Integer> map = new LinkedHashMap<>();
				        map.put("a", 1);
				    }
				}
				"""
		}, null);
	}

	@Test
	public void testTreeSetToSet() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("Test.java", //$NON-NLS-1$
				"""
				package test;
				import java.util.TreeSet;
				
				public class Test {
				    public void method() {
				        TreeSet<String> set = new TreeSet<>();
				        set.add("a");
				    }
				}
				""",
				false, null);
		context.enable(MYCleanUpConstants.USE_GENERAL_TYPE_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] {cu}, new String[] {
				"""
				package test;
				import java.util.Set;
				import java.util.TreeSet;
				
				public class Test {
				    public void method() {
				        Set<String> set = new TreeSet<>();
				        set.add("a");
				    }
				}
				"""
		}, null);
	}

	// Negative test cases - cleanup should NOT change the code

	@Test
	public void testNoChangeWithCast() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("Test.java", //$NON-NLS-1$
				"""
				package test;
				import java.util.ArrayList;
				
				public class Test {
				    public void method() {
				        ArrayList<String> list = new ArrayList<>();
				        list.add("a");
				        Object obj = (ArrayList<String>) list;
				    }
				}
				""",
				false, null);
		context.enable(MYCleanUpConstants.USE_GENERAL_TYPE_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testNoChangeWithInstanceof() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("Test.java", //$NON-NLS-1$
				"""
				package test;
				import java.util.ArrayList;
				
				public class Test {
				    public void method() {
				        ArrayList<String> list = new ArrayList<>();
				        list.add("a");
				        if (list instanceof ArrayList) {
				            System.out.println("ArrayList");
				        }
				    }
				}
				""",
				false, null);
		context.enable(MYCleanUpConstants.USE_GENERAL_TYPE_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testNoChangeWithSpecificMethod() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("Test.java", //$NON-NLS-1$
				"""
				package test;
				import java.util.LinkedList;
				
				public class Test {
				    public void method() {
				        LinkedList<String> list = new LinkedList<>();
				        list.addFirst("a");
				    }
				}
				""",
				false, null);
		context.enable(MYCleanUpConstants.USE_GENERAL_TYPE_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testNoChangeWithReturnStatement() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("Test.java", //$NON-NLS-1$
				"""
				package test;
				import java.util.ArrayList;
				
				public class Test {
				    public ArrayList<String> method() {
				        ArrayList<String> list = new ArrayList<>();
				        list.add("a");
				        return list;
				    }
				}
				""",
				false, null);
		context.enable(MYCleanUpConstants.USE_GENERAL_TYPE_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testNoChangeWithAssignment() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("Test.java", //$NON-NLS-1$
				"""
				package test;
				import java.util.ArrayList;
				
				public class Test {
				    public void method() {
				        ArrayList<String> list1 = new ArrayList<>();
				        list1.add("a");
				        ArrayList<String> list2;
				        list2 = list1;
				    }
				}
				""",
				false, null);
		context.enable(MYCleanUpConstants.USE_GENERAL_TYPE_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testNoChangeWithMethodArgument() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("Test.java", //$NON-NLS-1$
				"""
				package test;
				import java.util.ArrayList;
				
				public class Test {
				    public void method() {
				        ArrayList<String> list = new ArrayList<>();
				        list.add("a");
				        process(list);
				    }
				    
				    public void process(ArrayList<String> list) {
				    }
				}
				""",
				false, null);
		context.enable(MYCleanUpConstants.USE_GENERAL_TYPE_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testNoChangeWithPrimitive() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("Test.java", //$NON-NLS-1$
				"""
				package test;
				
				public class Test {
				    public void method() {
				        int x = 5;
				        System.out.println(x);
				    }
				}
				""",
				false, null);
		context.enable(MYCleanUpConstants.USE_GENERAL_TYPE_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testNoChangeWithVar() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("Test.java", //$NON-NLS-1$
				"""
				package test;
				import java.util.ArrayList;
				
				public class Test {
				    public void method() {
				        var list = new ArrayList<String>();
				        list.add("a");
				    }
				}
				""",
				false, null);
		context.enable(MYCleanUpConstants.USE_GENERAL_TYPE_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testNoChangeAlreadyInterface() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("Test.java", //$NON-NLS-1$
				"""
				package test;
				import java.util.ArrayList;
				import java.util.List;
				
				public class Test {
				    public void method() {
				        List<String> list = new ArrayList<>();
				        list.add("a");
				    }
				}
				""",
				false, null);
		context.enable(MYCleanUpConstants.USE_GENERAL_TYPE_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}
}
