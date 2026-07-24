/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;

import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/** Regression tests for accumulator type and multi-loop sequencing safety. */
public class AccumulatorAndSequenceSafetyTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava22();

	@Test
	void enhancedForPreservesConcreteArrayListAccumulator() throws CoreException {
		assertExpected("""
				package test;
				import java.util.*;
				class E {
					ArrayList<String> copy(List<String> source) {
						ArrayList<String> result = new ArrayList<>();
						for (String item : source) {
							result.add(item);
						}
						return result;
					}
				}
				""", """
				package test;
				import java.util.*;
				import java.util.stream.Collectors;
				class E {
					ArrayList<String> copy(List<String> source) {
						ArrayList<String> result = source.stream().collect(Collectors.toCollection(java.util.ArrayList::new));
						return result;
					}
				}
				""");
	}

	@Test
	void enhancedForPreservesConcreteLinkedListAccumulator() throws CoreException {
		assertExpected("""
				package test;
				import java.util.*;
				class E {
					LinkedList<String> copy(List<String> source) {
						LinkedList<String> result = new LinkedList<>();
						for (String item : source) {
							result.add(item);
						}
						return result;
					}
				}
				""", """
				package test;
				import java.util.*;
				import java.util.stream.Collectors;
				class E {
					LinkedList<String> copy(List<String> source) {
						LinkedList<String> result = source.stream().collect(Collectors.toCollection(java.util.LinkedList::new));
						return result;
					}
				}
				""");
	}

	@Test
	void enhancedForPreservesConcreteHashSetAccumulator() throws CoreException {
		assertExpected("""
				package test;
				import java.util.*;
				class E {
					HashSet<String> copy(List<String> source) {
						HashSet<String> result = new HashSet<>();
						for (String item : source) {
							result.add(item);
						}
						return result;
					}
				}
				""", """
				package test;
				import java.util.*;
				import java.util.stream.Collectors;
				class E {
					HashSet<String> copy(List<String> source) {
						HashSet<String> result = source.stream().collect(Collectors.toCollection(java.util.HashSet::new));
						return result;
					}
				}
				""");
	}

	@Test
	void enhancedForPreservesConcreteLinkedHashSetAccumulator() throws CoreException {
		assertExpected("""
				package test;
				import java.util.*;
				class E {
					LinkedHashSet<String> copy(List<String> source) {
						LinkedHashSet<String> result = new LinkedHashSet<>();
						for (String item : source) {
							result.add(item);
						}
						return result;
					}
				}
				""", """
				package test;
				import java.util.*;
				import java.util.stream.Collectors;
				class E {
					LinkedHashSet<String> copy(List<String> source) {
						LinkedHashSet<String> result = source.stream().collect(Collectors.toCollection(java.util.LinkedHashSet::new));
						return result;
					}
				}
				""");
	}

	@Test
	void enhancedForPreservesNaturalOrderTreeSetAccumulator() throws CoreException {
		assertExpected("""
				package test;
				import java.util.*;
				class E {
					TreeSet<String> copy(List<String> source) {
						TreeSet<String> result = new TreeSet<>();
						for (String item : source) {
							result.add(item);
						}
						return result;
					}
				}
				""", """
				package test;
				import java.util.*;
				import java.util.stream.Collectors;
				class E {
					TreeSet<String> copy(List<String> source) {
						TreeSet<String> result = source.stream().collect(Collectors.toCollection(java.util.TreeSet::new));
						return result;
					}
				}
				""");
	}

	@Test
	void interfaceDeclarationStillPreservesChosenImplementation() throws CoreException {
		assertExpected("""
				package test;
				import java.util.*;
				class E {
					List<String> copy(List<String> source) {
						List<String> result = new ArrayList<>();
						for (String item : source) {
							result.add(item);
						}
						return result;
					}
				}
				""", """
				package test;
				import java.util.*;
				import java.util.stream.Collectors;
				class E {
					List<String> copy(List<String> source) {
						List<String> result = source.stream().collect(Collectors.toCollection(java.util.ArrayList::new));
						return result;
					}
				}
				""");
	}

	@Test
	void arraySourceUsesTheSameConcreteFactoryModel() throws CoreException {
		assertExpected("""
				package test;
				import java.util.*;
				class E {
					ArrayList<String> copy(String[] source) {
						ArrayList<String> result = new ArrayList<>();
						for (String item : source) {
							result.add(item);
						}
						return result;
					}
				}
				""", """
				package test;
				import java.util.*;
				import java.util.stream.Collectors;
				class E {
					ArrayList<String> copy(String[] source) {
						ArrayList<String> result = Arrays.stream(source).collect(Collectors.toCollection(java.util.ArrayList::new));
						return result;
					}
				}
				""");
	}

	@Test
	void iteratorPreservesConcreteArrayListAccumulator() throws CoreException {
		assertExpected("""
				package test;
				import java.util.*;
				class E {
					ArrayList<String> copy(List<String> source) {
						ArrayList<String> result = new ArrayList<>();
						Iterator<String> iterator = source.iterator();
						while (iterator.hasNext()) {
							String item = iterator.next();
							result.add(item);
						}
						return result;
					}
				}
				""", """
				package test;
				import java.util.*;
				import java.util.stream.Collectors;
				class E {
					ArrayList<String> copy(List<String> source) {
						ArrayList<String> result = source.stream().collect(Collectors.toCollection(java.util.ArrayList::new));
						return result;
					}
				}
				""");
	}

	@Test
	void iteratorPreservesNaturalOrderTreeSetAccumulator() throws CoreException {
		assertExpected("""
				package test;
				import java.util.*;
				class E {
					TreeSet<String> copy(List<String> source) {
						TreeSet<String> result = new TreeSet<>();
						Iterator<String> iterator = source.iterator();
						while (iterator.hasNext()) {
							String item = iterator.next();
							result.add(item);
						}
						return result;
					}
				}
				""", """
				package test;
				import java.util.*;
				import java.util.stream.Collectors;
				class E {
					TreeSet<String> copy(List<String> source) {
						TreeSet<String> result = source.stream().collect(Collectors.toCollection(java.util.TreeSet::new));
						return result;
					}
				}
				""");
	}

	@Test
	void capacityConstructorPreservesDeclarationAndConvertsLoop() throws CoreException {
		assertExpected("""
				package test;
				import java.util.*;
				class E {
					ArrayList<String> copy(List<String> source) {
						ArrayList<String> result = new ArrayList<>(16);
						for (String item : source) {
							result.add(item);
						}
						return result;
					}
				}
				""", """
				package test;
				import java.util.*;
				class E {
					ArrayList<String> copy(List<String> source) {
						ArrayList<String> result = new ArrayList<>(16);
						source.forEach(item -> result.add(item));
						return result;
					}
				}
				""");
	}

	@Test
	void comparatorConstructorPreservesDeclarationAndConvertsLoop() throws CoreException {
		assertExpected("""
				package test;
				import java.util.*;
				class E {
					TreeSet<String> copy(List<String> source) {
						TreeSet<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
						for (String item : source) {
							result.add(item);
						}
						return result;
					}
				}
				""", """
				package test;
				import java.util.*;
				class E {
					TreeSet<String> copy(List<String> source) {
						TreeSet<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
						source.forEach(item -> result.add(item));
						return result;
					}
				}
				""");
	}

	@Test
	void unsupportedCustomImplementationFailsClosed() throws CoreException {
		assertNoChange("""
				package test;
				import java.util.*;
				class E {
					static class CustomList<T> extends ArrayList<T> {}
					CustomList<String> copy(List<String> source) {
						CustomList<String> result = new CustomList<>();
						for (String item : source) {
							result.add(item);
						}
						return result;
					}
				}
				""");
	}

	@Test
	void anonymousCollectionImplementationFailsClosed() throws CoreException {
		assertNoChange("""
				package test;
				import java.util.*;
				class E {
					ArrayList<String> copy(List<String> source) {
						ArrayList<String> result = new ArrayList<>() {
							private static final long serialVersionUID = 1L;
						};
						for (String item : source) {
							result.add(item);
						}
						return result;
					}
				}
				""");
	}

	@Test
	void consecutiveLoopsRemainSequentialAfterFreshAccumulatorMerge() throws CoreException {
		assertExpected("""
				package test;
				import java.util.*;
				class E {
					List<String> copy(List<String> first, List<String> second) {
						List<String> result = new ArrayList<>();
						for (String item : first) {
							result.add(item);
						}
						for (String item : second) {
							result.add(item);
						}
						return result;
					}
				}
				""", """
				package test;
				import java.util.*;
				import java.util.stream.Collectors;
				class E {
					List<String> copy(List<String> first, List<String> second) {
						List<String> result = first.stream().collect(Collectors.toCollection(java.util.ArrayList::new));
						second.forEach(item -> result.add(item));
						return result;
					}
				}
				""");
	}

	@Test
	void consecutiveLoopsPreserveExistingTargetIdentity() throws CoreException {
		assertExpected("""
				package test;
				import java.util.*;
				class E {
					void append(List<String> target, List<String> first, List<String> second) {
						for (String item : first) {
							target.add(item);
						}
						for (String item : second) {
							target.add(item);
						}
					}
				}
				""", """
				package test;
				import java.util.*;
				class E {
					void append(List<String> target, List<String> first, List<String> second) {
						first.forEach(item -> target.add(item));
						second.forEach(item -> target.add(item));
					}
				}
				""");
	}

	private void assertNoChange(String source) throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("E.java", source, false, null); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { unit });
	}

	private void assertExpected(String source, String expected) throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("E.java", source, false, null); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { unit }, new String[] { expected }, null);
	}
}
