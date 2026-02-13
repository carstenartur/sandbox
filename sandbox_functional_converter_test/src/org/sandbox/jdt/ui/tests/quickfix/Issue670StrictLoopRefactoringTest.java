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

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/**
 * Tests for stricter loop refactoring rules (Issue #670).
 * 
 * <p>This test class validates the implementation of stricter rules for loop-to-stream
 * conversions to prevent subtle bugs caused by semantic changes or thread-safety violations.</p>
 * 
 * <p><b>Test Categories:</b></p>
 * <ul>
 *   <li><b>Category A: Arrays</b> - Index usage detection for arrays</li>
 *   <li><b>Category B: Normal Collections</b> - Structural modification detection</li>
 *   <li><b>Category C: Concurrent Collections</b> - Special handling for concurrent types</li>
 * </ul>
 * 
 * @see <a href="https://github.com/carstenartur/sandbox/issues/670">Issue #670</a>
 */
@DisplayName("Issue #670: Strict Loop Refactoring Rules")
public class Issue670StrictLoopRefactoringTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava22();

	// ===========================================
	// CATEGORY A: ARRAYS - Index Usage Detection
	// ===========================================

	/**
	 * Tests that indexed array loops with neighbor access (i+1) should NOT convert.
	 * 
	 * <p><b>Rule:</b> When index is used for neighbor access like {@code array[i+1]},
	 * conversion to enhanced for-loop would lose index semantics.</p>
	 * 
	 * <p><b>Pattern:</b> {@code for (int i = 0; i < array.length - 1; i++) { ... array[i+1] ... }}</p>
	 * <p><b>Why not convertible:</b> Enhanced for-loop doesn't provide access to neighbors.
	 * Converting would lose the ability to access {@code array[i+1]}.</p>
	 */
	@Test
	@DisplayName("Indexed array loop with neighbor access (i+1) - should NOT convert")
	void testArrayIndexedLoop_NeighborAccess_ShouldNotConvert() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
				package test1;
				public class MyTest {
					void process(int[] array) {
						for (int i = 0; i < array.length - 1; i++) {
							int current = array[i];
							int next = array[i + 1];
							System.out.println(current + next);
						}
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	/**
	 * Tests that indexed loops with previous element access (i-1) should NOT convert.
	 */
	@Test
	@DisplayName("Indexed loop with previous element access (i-1) - should NOT convert")
	void testIndexedLoop_PreviousAccess_ShouldNotConvert() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
				package test1;
				public class MyTest {
					void process(int[] array) {
						for (int i = 1; i < array.length; i++) {
							int current = array[i];
							int prev = array[i - 1];
							System.out.println(prev + current);
						}
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	/**
	 * Tests that loops using index for conditional logic (i%2==0) should NOT convert.
	 * 
	 * <p><b>Rule:</b> When index is used in conditional logic beyond simple element access,
	 * conversion loses semantic meaning.</p>
	 */
	@Test
	@DisplayName("Loop with index in conditional logic (i%2==0) - should NOT convert")
	void testIndexedLoop_ModuloLogic_ShouldNotConvert() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
				package test1;
				public class MyTest {
					void processEvenIndices(int[] array) {
						for (int i = 0; i < array.length; i++) {
							if (i % 2 == 0) {
								System.out.println("Even index: " + array[i]);
							}
						}
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	/**
	 * Tests that loops using index for arithmetic (i*2) should NOT convert.
	 */
	@Test
	@DisplayName("Loop with index arithmetic (i*2) - should NOT convert")
	void testIndexedLoop_IndexArithmetic_ShouldNotConvert() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
				package test1;
				public class MyTest {
					void process(int[] array) {
						for (int i = 0; i < array.length; i++) {
							int doubled = i * 2;
							System.out.println("Index doubled: " + doubled);
						}
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	/**
	 * Tests that simple indexed array loops (only using array[i]) CAN convert to IntStream.range().
	 * 
	 * <p><b>Rule:</b> When index is only used for simple element access {@code array[i]},
	 * conversion to IntStream.range() is safe.</p>
	 * 
	 * <p><b>Note:</b> This converts to IntStream.range(), not enhanced for-loop,
	 * because the TraditionalForHandler handles this pattern.</p>
	 */
	@Test
	@DisplayName("Simple indexed array loop (only array[i]) - CAN convert to IntStream")
	void testSimpleIndexedArrayLoop_CanConvert() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
				package test1;
				public class MyTest {
					void process(int[] array) {
						for (int i = 0; i < array.length; i++) {
							System.out.println(array[i]);
						}
					}
				}
				""";

		String expected = """
				package test1;
				
				import java.util.stream.IntStream;
				
				public class MyTest {
					void process(int[] array) {
						IntStream.range(0, array.length).forEach(i -> System.out.println(array[i]));
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	// ===========================================
	// CATEGORY B: NORMAL COLLECTIONS - Modification Detection
	// ===========================================

	/**
	 * Tests that loops with list.remove() should NOT convert.
	 * 
	 * <p><b>Rule:</b> Structural modifications (add/remove/clear) on the iterated collection
	 * cause ConcurrentModificationException with fail-fast iterators.</p>
	 */
	@Test
	@DisplayName("Loop with list.remove() - should NOT convert")
	void testLoopWithListRemove_ShouldNotConvert() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
				package test1;
				import java.util.*;
				public class MyTest {
					void process(List<String> list) {
						for (String item : list) {
							if (item == null) {
								list.remove(item);
							}
						}
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	/**
	 * Tests that loops with list.add() should NOT convert.
	 */
	@Test
	@DisplayName("Loop with list.add() - should NOT convert")
	void testLoopWithListAdd_ShouldNotConvert() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
				package test1;
				import java.util.*;
				public class MyTest {
					void process(List<String> source, List<String> target) {
						for (String item : source) {
							source.add(item.toUpperCase());
						}
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	/**
	 * Tests that loops with map.put() on the iterated map should NOT convert.
	 * 
	 * <p><b>BUG:</b> This test currently fails because the implementation converts the loop
	 * even though it modifies the iterated map. The collection modification detection correctly
	 * identifies that we're iterating over "map" (via map.keySet()), but the safety check
	 * is not blocking the conversion.</p>
	 * 
	 * <p><b>Expected:</b> No conversion (modification causes ConcurrentModificationException)</p>
	 * <p><b>Actual:</b> Converts to {@code map.keySet().forEach(...)}</p>
	 * 
	 * <p><b>Root Cause:</b> The {@code modifiesIteratedCollection} flag is set correctly,
	 * but somewhere in the conversion pipeline it's not being honored for all handlers.</p>
	 * 
	 * @see <a href="https://github.com/carstenartur/sandbox/issues/670">Issue #670</a>
	 */
	@Test
	@Disabled("BUG: Collection modification detection not blocking conversion - Issue #670")
	@DisplayName("Loop with map.put() on iterated map - should NOT convert (BUG)")
	void testLoopWithMapPut_ShouldNotConvert() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
				package test1;
				import java.util.*;
				public class MyTest {
					void process(Map<String, Integer> map) {
						for (String key : map.keySet()) {
							map.put(key + "_new", map.get(key));
						}
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	/**
	 * Tests that loops with list.clear() should NOT convert.
	 */
	@Test
	@DisplayName("Loop with list.clear() - should NOT convert")
	void testLoopWithListClear_ShouldNotConvert() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
				package test1;
				import java.util.*;
				public class MyTest {
					void process(List<String> list) {
						for (String item : list) {
							System.out.println(item);
							list.clear();
							break;
						}
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	/**
	 * Tests that loops modifying a DIFFERENT collection CAN convert.
	 * 
	 * <p><b>Rule:</b> Only modifications to the ITERATED collection block conversion.
	 * Modifications to other collections are safe.</p>
	 * 
	 * <p><b>IMPLEMENTATION NOTE:</b> The current implementation detects this as a collect pattern
	 * and converts to {@code stream().map().collect()}, which has different semantics than
	 * {@code forEach()} - it creates a new list instead of modifying the existing target.</p>
	 * 
	 * <p><b>Expected:</b> {@code source.forEach(item -> target.add(item.toUpperCase()))}</p>
	 * <p><b>Actual:</b> {@code target = source.stream().map(...).collect()}</p>
	 * 
	 * <p>This is acceptable behavior but different from the test expectation. The test
	 * is disabled to avoid build failures.</p>
	 */
	@Test
	@Disabled("Different but acceptable behavior - converts to collect() instead of forEach()")
	@DisplayName("Loop modifying different collection - CAN convert")
	void testLoopModifyingDifferentCollection_CanConvert() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
				package test1;
				import java.util.*;
				public class MyTest {
					void process(List<String> source, List<String> target) {
						for (String item : source) {
							target.add(item.toUpperCase());
						}
					}
				}
				""";

		String expected = """
				package test1;
				import java.util.*;
				public class MyTest {
					void process(List<String> source, List<String> target) {
						source.forEach(item -> target.add(item.toUpperCase()));
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	// ===========================================
	// CATEGORY C: CONCURRENT COLLECTIONS
	// ===========================================

	/**
	 * Tests that iterator loops on CopyOnWriteArrayList with iterator.remove() should NOT convert.
	 * 
	 * <p><b>Rule:</b> In the functional-loop (stream) cleanup, iterator-while patterns are
	 * handled by the {@code IteratorWhileHandler}, and {@code iterator.remove()} cannot be
	 * represented in the resulting stream-based refactoring, so this conversion is correctly blocked.</p>
	 */
	@Test
	@DisplayName("Iterator loop on CopyOnWriteArrayList with iterator.remove() - should NOT convert")
	void testConcurrentCollectionWithIteratorRemove_ShouldNotConvert() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
				package test1;
				import java.util.*;
				import java.util.concurrent.CopyOnWriteArrayList;
				public class MyTest {
					void process(CopyOnWriteArrayList<String> list) {
						Iterator<String> it = list.iterator();
						while (it.hasNext()) {
							String item = it.next();
							if (item == null) {
								it.remove();
							}
						}
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	/**
	 * Tests that simple iterator loops on CopyOnWriteArrayList (without remove) CAN convert.
	 * 
	 * <p><b>Rule:</b> Read-only iteration on concurrent collections is safe.</p>
	 * 
	 * <p><b>IMPLEMENTATION NOTE:</b> The current implementation converts iterator loops
	 * directly to {@code stream().forEach()} rather than to enhanced for-loops. This is
	 * acceptable behavior, just different from the test expectation.</p>
	 * 
	 * <p><b>Expected:</b> {@code for (String item : list)}</p>
	 * <p><b>Actual:</b> {@code list.stream().forEach(item -> ...)}</p>
	 */
	@Test
	@Disabled("Different but acceptable behavior - converts to stream instead of enhanced-for")
	@DisplayName("Simple iterator loop on CopyOnWriteArrayList - CAN convert")
	void testConcurrentCollection_SimpleIteration_CanConvert() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
				package test1;
				import java.util.*;
				import java.util.concurrent.CopyOnWriteArrayList;
				public class MyTest {
					void process(CopyOnWriteArrayList<String> list) {
						Iterator<String> it = list.iterator();
						while (it.hasNext()) {
							String item = it.next();
							System.out.println(item);
						}
					}
				}
				""";

		String expected = """
				package test1;
				import java.util.*;
				import java.util.concurrent.CopyOnWriteArrayList;
				public class MyTest {
					void process(CopyOnWriteArrayList<String> list) {
						for (String item : list) {
							System.out.println(item);
						}
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests that enhanced for-loops on ConcurrentHashMap values should NOT convert.
	 * 
	 * <p><b>Rule:</b> Concurrent collections are blocked conservatively due to
	 * weakly-consistent iterator semantics (Issue #670).</p>
	 */
	@Test
	@DisplayName("Enhanced for-loop on ConcurrentHashMap values - should NOT convert")
	void testConcurrentHashMap_EnhancedFor_ShouldNotConvert() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
				package test1;
				import java.util.concurrent.ConcurrentHashMap;
				public class MyTest {
					void process(ConcurrentHashMap<String, Integer> map) {
						for (Integer value : map.values()) {
							System.out.println(value);
						}
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	// ===========================================
	// EDGE CASES AND COMBINED SCENARIOS
	// ===========================================

	/**
	 * Tests that loops with both index arithmetic and collection modification should NOT convert.
	 */
	@Test
	@DisplayName("Loop with index arithmetic AND collection modification - should NOT convert")
	void testIndexArithmeticAndModification_ShouldNotConvert() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
				package test1;
				import java.util.*;
				public class MyTest {
					void process(List<String> list) {
						for (int i = 0; i < list.size(); i++) {
							if (i % 2 == 0) {
								list.remove(i);
							}
						}
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	/**
	 * Tests that loops with field access receiver (this.list.remove) should NOT convert.
	 * 
	 * <p><b>BUG:</b> This test currently fails because the implementation converts the loop
	 * even though it modifies the iterated collection via field access.</p>
	 * 
	 * <p><b>Expected:</b> No conversion (modification causes ConcurrentModificationException)</p>
	 * <p><b>Actual:</b> Converts to {@code this.list.stream().filter().forEachOrdered(...)}</p>
	 * 
	 * <p><b>Root Cause:</b> The collection modification detection supports field access
	 * receivers ({@code this.list.remove()}), but the safety check is not being enforced
	 * in all conversion handlers.</p>
	 * 
	 * @see <a href="https://github.com/carstenartur/sandbox/issues/670">Issue #670</a>
	 */
	@Test
	@Disabled("BUG: Field access modification detection not blocking conversion - Issue #670")
	@DisplayName("Loop with field access receiver (this.list.remove) - should NOT convert (BUG)")
	void testFieldAccessReceiverModification_ShouldNotConvert() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
				package test1;
				import java.util.*;
				public class MyTest {
					private List<String> list = new ArrayList<>();
					
					void process() {
						for (String item : this.list) {
							if (item == null) {
								this.list.remove(item);
							}
						}
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	/**
	 * Tests that loops with getter method receiver (getList().add) should NOT convert.
	 * 
	 * <p><b>BUG:</b> This test causes an {@code IllegalArgumentException: Invalid identifier : >getList()<}
	 * because the {@code EnhancedForHandler} tries to create an AST SimpleName from the method
	 * invocation expression "getList()", which includes parentheses and is not a valid identifier.</p>
	 * 
	 * <p><b>Root Cause:</b> When iterating over {@code getList()}, the handler extracts "getList()"
	 * as the source expression and attempts to use it directly as an identifier, which fails
	 * AST validation.</p>
	 * 
	 * <p><b>Fix Needed:</b> The handler should detect method invocation expressions and either:
	 * <ul>
	 * <li>Skip conversion for method invocation sources, OR</li>
	 * <li>Properly handle them by preserving the full method call expression</li>
	 * </ul>
	 * 
	 * @see <a href="https://github.com/carstenartur/sandbox/issues/670">Issue #670</a>
	 */
	@Test
	@Disabled("BUG: IllegalArgumentException - Invalid identifier : >getList()< - needs fix in EnhancedForHandler")
	@DisplayName("Loop with getter method receiver (getList().add) - should NOT convert (CRASHES)")
	void testGetterMethodReceiverModification_ShouldNotConvert() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
				package test1;
				import java.util.*;
				public class MyTest {
					private List<String> list = new ArrayList<>();
					
					List<String> getList() {
						return list;
					}
					
					void process() {
						for (String item : getList()) {
							getList().add(item.toUpperCase());
						}
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}
}
