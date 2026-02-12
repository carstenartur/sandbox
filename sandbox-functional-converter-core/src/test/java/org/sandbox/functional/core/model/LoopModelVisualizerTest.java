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
package org.sandbox.functional.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sandbox.functional.core.builder.LoopModelBuilder;
import org.sandbox.functional.core.operation.FilterOp;
import org.sandbox.functional.core.operation.MapOp;
import org.sandbox.functional.core.terminal.CollectTerminal;
import org.sandbox.functional.core.terminal.ForEachTerminal;
import org.sandbox.functional.core.terminal.ReduceTerminal;
import org.sandbox.functional.core.tree.ConversionDecision;
import org.sandbox.functional.core.tree.LoopKind;
import org.sandbox.functional.core.tree.LoopTree;
import org.sandbox.functional.core.tree.LoopTreeNode;

/**
 * Tests for {@link LoopModelVisualizer}.
 */
class LoopModelVisualizerTest {

	@Nested
	@DisplayName("ASCII Pipeline Diagram")
	class AsciiPipelineTests {

		@Test
		@DisplayName("null model returns placeholder")
		void testNullModel() {
			assertThat(LoopModelVisualizer.toAsciiPipeline(null))
				.isEqualTo("[null model]");
		}

		@Test
		@DisplayName("empty model shows unset components")
		void testEmptyModel() {
			String result = LoopModelVisualizer.toAsciiPipeline(new LoopModel());
			assertThat(result)
				.contains("ULR Pipeline Diagram")
				.contains("[not set]")
				.contains("[no source]");
		}

		@Test
		@DisplayName("simple filter + forEach pipeline")
		void testSimpleFilterForEach() {
			LoopModel model = new LoopModelBuilder()
				.source(SourceDescriptor.SourceType.COLLECTION, "items", "String")
				.element("item", "String", false)
				.filter("item.length() > 3")
				.forEach(List.of("System.out.println(item)"), false)
				.build();

			String result = LoopModelVisualizer.toAsciiPipeline(model);
			assertThat(result)
				.contains("COLLECTION")
				.contains("items")
				.contains("items.stream()")
				.contains("├── .filter(item.length() > 3)")
				.contains("└── .forEach(...)");
		}

		@Test
		@DisplayName("filter + map + collect pipeline")
		void testFilterMapCollect() {
			LoopModel model = new LoopModelBuilder()
				.source(SourceDescriptor.SourceType.COLLECTION, "numbers", "Integer")
				.element("n", "Integer", false)
				.filter("n > 0")
				.map("n.toString()", "String")
				.collect(CollectTerminal.CollectorType.TO_LIST, "result")
				.build();

			String result = LoopModelVisualizer.toAsciiPipeline(model);
			assertThat(result)
				.contains("├── .filter(n > 0)")
				.contains("├── .map(n.toString()")
				.contains("└── .collect(TO_LIST)");
		}

		@Test
		@DisplayName("operations with comments shown")
		void testOperationsWithComments() {
			FilterOp filter = new FilterOp("x > 0");
			filter.addComment("Only positive values");

			LoopModel model = new LoopModel();
			model.setSource(new SourceDescriptor(SourceDescriptor.SourceType.COLLECTION, "list", "Integer"));
			model.setElement(new ElementDescriptor("x", "Integer", false));
			model.addOperation(filter);
			model.setTerminal(new ForEachTerminal(List.of(), false));

			String result = LoopModelVisualizer.toAsciiPipeline(model);
			assertThat(result)
				.contains("// Only positive values");
		}

		@Test
		@DisplayName("array source renders Arrays.stream()")
		void testArraySource() {
			LoopModel model = new LoopModelBuilder()
				.source(SourceDescriptor.SourceType.ARRAY, "arr", "int")
				.element("x", "int", false)
				.forEach(List.of(), false)
				.build();

			String result = LoopModelVisualizer.toAsciiPipeline(model);
			assertThat(result).contains("Arrays.stream(arr)");
		}

		@Test
		@DisplayName("IntStream.range source")
		void testIntRangeSource() {
			LoopModel model = new LoopModelBuilder()
				.source(SourceDescriptor.SourceType.EXPLICIT_RANGE, "0,10", "int")
				.element("i", "int", false)
				.forEach(List.of(), false)
				.build();

			String result = LoopModelVisualizer.toAsciiPipeline(model);
			assertThat(result).contains("IntStream.range(0, 10)");
		}

		@Test
		@DisplayName("final element is noted")
		void testFinalElement() {
			LoopModel model = new LoopModelBuilder()
				.source(SourceDescriptor.SourceType.COLLECTION, "items", "String")
				.element("item", "String", true)
				.forEach(List.of(), false)
				.build();

			String result = LoopModelVisualizer.toAsciiPipeline(model);
			assertThat(result).contains("(final)");
		}
	}

	@Nested
	@DisplayName("Detailed Dump")
	class DetailedDumpTests {

		@Test
		@DisplayName("null model returns placeholder")
		void testNullModel() {
			assertThat(LoopModelVisualizer.toDetailedDump(null))
				.isEqualTo("[null model]");
		}

		@Test
		@DisplayName("full model with all sections")
		void testFullModel() {
			LoopModel model = new LoopModelBuilder()
				.source(SourceDescriptor.SourceType.COLLECTION, "items", "String")
				.element("item", "String", false)
				.metadata(false, false, false, false, true, false, false)
				.filter("item != null")
				.map("item.toUpperCase()", "String")
				.forEach(List.of("System.out.println(item)"), false)
				.build();

			String result = LoopModelVisualizer.toDetailedDump(model);
			assertThat(result)
				.contains("ULR Model Detailed Dump")
				.contains("[Source]")
				.contains("type           = COLLECTION")
				.contains("expression     = items")
				.contains("[Element]")
				.contains("variableName   = item")
				.contains("[Metadata]")
				.contains("requiresOrdering     = true")
				.contains("[Operations] (2)")
				.contains("[0] filter(item != null)")
				.contains("[1] map(item.toUpperCase())")
				.contains("[Terminal]")
				.contains("type = forEach")
				.contains("[Convertibility]");
		}

		@Test
		@DisplayName("operation comments shown in dump")
		void testOperationComments() {
			FilterOp filter = new FilterOp("x > 0");
			filter.addComment("Positive only");

			LoopModel model = new LoopModel();
			model.setSource(new SourceDescriptor(SourceDescriptor.SourceType.COLLECTION, "list", "Integer"));
			model.setElement(new ElementDescriptor("x", "Integer", false));
			model.addOperation(filter);

			String result = LoopModelVisualizer.toDetailedDump(model);
			assertThat(result)
				.contains("comments: [Positive only]");
		}

		@Test
		@DisplayName("map with side effect flagged")
		void testMapSideEffect() {
			MapOp mapOp = new MapOp("sideEffectExpr", "void", "x", true);

			LoopModel model = new LoopModel();
			model.setSource(new SourceDescriptor(SourceDescriptor.SourceType.COLLECTION, "list", "String"));
			model.setElement(new ElementDescriptor("x", "String", false));
			model.addOperation(mapOp);

			String result = LoopModelVisualizer.toDetailedDump(model);
			assertThat(result).contains("sideEffect: true");
		}

		@Test
		@DisplayName("reduce terminal with details")
		void testReduceTerminal() {
			LoopModel model = new LoopModelBuilder()
				.source(SourceDescriptor.SourceType.COLLECTION, "numbers", "Integer")
				.element("n", "Integer", false)
				.reduce("0", "Integer::sum", null, ReduceTerminal.ReduceType.SUM)
				.build();

			String result = LoopModelVisualizer.toDetailedDump(model);
			assertThat(result)
				.contains("reduceType = SUM")
				.contains("identity = 0")
				.contains("accumulator = Integer::sum");
		}
	}

	@Nested
	@DisplayName("Tree Diagram")
	class TreeDiagramTests {

		@Test
		@DisplayName("null tree returns placeholder")
		void testNullTree() {
			assertThat(LoopModelVisualizer.toTreeDiagram(null))
				.isEqualTo("[null tree]");
		}

		@Test
		@DisplayName("single root node")
		void testSingleRoot() {
			LoopTree tree = new LoopTree();
			LoopTreeNode node = tree.pushLoop(LoopKind.ENHANCED_FOR);
			node.setDecision(ConversionDecision.CONVERTIBLE);
			tree.popLoop();

			String result = LoopModelVisualizer.toTreeDiagram(tree);
			assertThat(result)
				.contains("LoopTree")
				.contains("└── ENHANCED_FOR [CONVERTIBLE]");
		}

		@Test
		@DisplayName("nested loops with decisions")
		void testNestedLoops() {
			LoopTree tree = new LoopTree();

			// Outer loop
			tree.pushLoop(LoopKind.ENHANCED_FOR);

			// Inner loop
			LoopTreeNode inner = tree.pushLoop(LoopKind.TRADITIONAL_FOR);
			inner.setDecision(ConversionDecision.NOT_CONVERTIBLE);
			tree.popLoop();

			LoopTreeNode outer = tree.popLoop();
			outer.setDecision(ConversionDecision.CONVERTIBLE);

			String result = LoopModelVisualizer.toTreeDiagram(tree);
			assertThat(result)
				.contains("└── ENHANCED_FOR [CONVERTIBLE]")
				.contains("    └── TRADITIONAL_FOR [NOT_CONVERTIBLE]");
		}

		@Test
		@DisplayName("multiple roots with children")
		void testMultipleRoots() {
			LoopTree tree = new LoopTree();

			// First root
			LoopTreeNode root1 = tree.pushLoop(LoopKind.ENHANCED_FOR);
			root1.setDecision(ConversionDecision.CONVERTIBLE);
			tree.popLoop();

			// Second root
			LoopTreeNode root2 = tree.pushLoop(LoopKind.ITERATOR_WHILE);
			root2.setDecision(ConversionDecision.PENDING);
			tree.popLoop();

			String result = LoopModelVisualizer.toTreeDiagram(tree);
			assertThat(result)
				.contains("├── ENHANCED_FOR [CONVERTIBLE]")
				.contains("└── ITERATOR_WHILE [PENDING]");
		}

		@Test
		@DisplayName("node with loop model shows operation count")
		void testNodeWithLoopModel() {
			LoopTree tree = new LoopTree();
			LoopTreeNode node = tree.pushLoop(LoopKind.ENHANCED_FOR);

			LoopModel model = new LoopModelBuilder()
				.source(SourceDescriptor.SourceType.COLLECTION, "items", "String")
				.element("item", "String", false)
				.filter("item != null")
				.map("item.toUpperCase()", "String")
				.forEach(List.of(), false)
				.build();

			node.setLoopModel(model);
			node.setDecision(ConversionDecision.CONVERTIBLE);
			tree.popLoop();

			String result = LoopModelVisualizer.toTreeDiagram(tree);
			assertThat(result)
				.contains("ops=2")
				.contains("-> forEach");
		}
	}
}
