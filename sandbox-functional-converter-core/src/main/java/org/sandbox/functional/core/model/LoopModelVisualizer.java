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

import java.util.List;

import org.sandbox.functional.core.operation.FilterOp;
import org.sandbox.functional.core.operation.MapOp;
import org.sandbox.functional.core.operation.Operation;
import org.sandbox.functional.core.terminal.CollectTerminal;
import org.sandbox.functional.core.terminal.FindTerminal;
import org.sandbox.functional.core.terminal.ForEachTerminal;
import org.sandbox.functional.core.terminal.MatchTerminal;
import org.sandbox.functional.core.terminal.ReduceTerminal;
import org.sandbox.functional.core.terminal.TerminalOperation;
import org.sandbox.functional.core.tree.LoopTree;
import org.sandbox.functional.core.tree.LoopTreeNode;

/**
 * Diagnostic visualization utility for ULR (Unified Loop Representation) models.
 * 
 * <p>Provides human-readable ASCII representations of {@link LoopModel} pipelines
 * and {@link LoopTree} structures for debugging and testing purposes.</p>
 * 
 * <h2>Usage in Tests</h2>
 * <p>Enable debug output by setting the system property {@code ulr.debug=true}
 * or by calling the methods directly:</p>
 * <pre>{@code
 * // Quick pipeline diagram
 * System.out.println(LoopModelVisualizer.toAsciiPipeline(model));
 * 
 * // Detailed dump of all components
 * System.out.println(LoopModelVisualizer.toDetailedDump(model));
 * 
 * // Tree structure visualization
 * System.out.println(LoopModelVisualizer.toTreeDiagram(tree));
 * }</pre>
 * 
 * @see LoopModel
 * @see LoopTree
 * @since 1.0.0
 */
public final class LoopModelVisualizer {

	private LoopModelVisualizer() {
	}

	/**
	 * Renders a compact ASCII pipeline diagram of the loop model.
	 * 
	 * <p>Example output:</p>
	 * <pre>
	 * ╔══════════════════════════════════════════╗
	 * ║         ULR Pipeline Diagram             ║
	 * ╠══════════════════════════════════════════╣
	 * ║ Source: COLLECTION "myList" (String)      ║
	 * ║ Element: item : String                    ║
	 * ╠══════════════════════════════════════════╣
	 * ║  myList.stream()                          ║
	 * ║    │                                      ║
	 * ║    ├── .filter(x -&gt; x &gt; 0)               ║
	 * ║    │                                      ║
	 * ║    ├── .map(x -&gt; x.toString())            ║
	 * ║    │                                      ║
	 * ║    └── .forEach(...)                       ║
	 * ╚══════════════════════════════════════════╝
	 * </pre>
	 * 
	 * @param model the loop model to visualize
	 * @return the ASCII pipeline diagram
	 */
	public static String toAsciiPipeline(LoopModel model) {
		if (model == null) {
			return "[null model]"; //$NON-NLS-1$
		}

		StringBuilder sb = new StringBuilder();
		sb.append("╔══════════════════════════════════════════╗\n"); //$NON-NLS-1$
		sb.append("║         ULR Pipeline Diagram             ║\n"); //$NON-NLS-1$
		sb.append("╠══════════════════════════════════════════╣\n"); //$NON-NLS-1$

		// Source
		if (model.getSource() != null) {
			SourceDescriptor src = model.getSource();
			sb.append("║ Source: ").append(src.type()) //$NON-NLS-1$
				.append(" \"").append(src.expression()).append("\"") //$NON-NLS-1$ //$NON-NLS-2$
				.append(" (").append(src.elementTypeName()).append(")\n"); //$NON-NLS-1$ //$NON-NLS-2$
		} else {
			sb.append("║ Source: [not set]\n"); //$NON-NLS-1$
		}

		// Element
		if (model.getElement() != null) {
			ElementDescriptor elem = model.getElement();
			sb.append("║ Element: ").append(elem.variableName()) //$NON-NLS-1$
				.append(" : ").append(elem.typeName()); //$NON-NLS-1$
			if (elem.isFinal()) {
				sb.append(" (final)"); //$NON-NLS-1$
			}
			sb.append('\n');
		} else {
			sb.append("║ Element: [not set]\n"); //$NON-NLS-1$
		}

		sb.append("╠══════════════════════════════════════════╣\n"); //$NON-NLS-1$

		// Source expression
		String sourceExpr = renderSourceExpression(model);
		sb.append("║  ").append(sourceExpr).append('\n'); //$NON-NLS-1$

		// Operations
		List<Operation> ops = model.getOperations();
		boolean hasTerminal = model.getTerminal() != null;

		for (int i = 0; i < ops.size(); i++) {
			Operation op = ops.get(i);
			boolean isLast = (i == ops.size() - 1) && !hasTerminal;
			sb.append("║    │\n"); //$NON-NLS-1$
			String connector = isLast ? "└── " : "├── "; //$NON-NLS-1$ //$NON-NLS-2$
			sb.append("║    ").append(connector) //$NON-NLS-1$
				.append('.').append(renderOperation(op)).append('\n');

			// Show comments if present
			List<String> comments = getComments(op);
			if (!comments.isEmpty()) {
				String commentIndent = isLast ? "        " : "│       "; //$NON-NLS-1$ //$NON-NLS-2$
				for (String comment : comments) {
					sb.append("║    ").append(commentIndent) //$NON-NLS-1$
						.append("// ").append(comment).append('\n'); //$NON-NLS-1$
				}
			}
		}

		// Terminal
		if (hasTerminal) {
			sb.append("║    │\n"); //$NON-NLS-1$
			sb.append("║    └── .").append(renderTerminal(model.getTerminal())).append('\n'); //$NON-NLS-1$
		}

		sb.append("╚══════════════════════════════════════════╝"); //$NON-NLS-1$
		return sb.toString();
	}

	/**
	 * Renders a detailed dump of all components in the loop model.
	 * 
	 * <p>Includes source, element, metadata flags, operations with
	 * comments, and terminal operation details.</p>
	 * 
	 * @param model the loop model to dump
	 * @return the detailed text dump
	 */
	public static String toDetailedDump(LoopModel model) {
		if (model == null) {
			return "[null model]"; //$NON-NLS-1$
		}

		StringBuilder sb = new StringBuilder();
		sb.append("=== ULR Model Detailed Dump ===\n"); //$NON-NLS-1$

		// Source
		sb.append("\n[Source]\n"); //$NON-NLS-1$
		if (model.getSource() != null) {
			SourceDescriptor src = model.getSource();
			sb.append("  type           = ").append(src.type()).append('\n'); //$NON-NLS-1$
			sb.append("  expression     = ").append(src.expression()).append('\n'); //$NON-NLS-1$
			sb.append("  elementType    = ").append(src.elementTypeName()).append('\n'); //$NON-NLS-1$
		} else {
			sb.append("  (not set)\n"); //$NON-NLS-1$
		}

		// Element
		sb.append("\n[Element]\n"); //$NON-NLS-1$
		if (model.getElement() != null) {
			ElementDescriptor elem = model.getElement();
			sb.append("  variableName   = ").append(elem.variableName()).append('\n'); //$NON-NLS-1$
			sb.append("  typeName       = ").append(elem.typeName()).append('\n'); //$NON-NLS-1$
			sb.append("  isFinal        = ").append(elem.isFinal()).append('\n'); //$NON-NLS-1$
		} else {
			sb.append("  (not set)\n"); //$NON-NLS-1$
		}

		// Metadata
		sb.append("\n[Metadata]\n"); //$NON-NLS-1$
		if (model.getMetadata() != null) {
			LoopMetadata meta = model.getMetadata();
			sb.append("  hasBreak             = ").append(meta.hasBreak()).append('\n'); //$NON-NLS-1$
			sb.append("  hasContinue          = ").append(meta.hasContinue()).append('\n'); //$NON-NLS-1$
			sb.append("  hasReturn            = ").append(meta.hasReturn()).append('\n'); //$NON-NLS-1$
			sb.append("  modifiesCollection   = ").append(meta.modifiesCollection()).append('\n'); //$NON-NLS-1$
			sb.append("  requiresOrdering     = ").append(meta.requiresOrdering()).append('\n'); //$NON-NLS-1$
			sb.append("  hasIteratorRemove    = ").append(meta.hasIteratorRemove()).append('\n'); //$NON-NLS-1$
			sb.append("  usesIndexBeyondGet   = ").append(meta.usesIndexBeyondGet()).append('\n'); //$NON-NLS-1$
			sb.append("  isConvertible        = ").append(meta.isConvertible()).append('\n'); //$NON-NLS-1$
		} else {
			sb.append("  (not set)\n"); //$NON-NLS-1$
		}

		// Operations
		sb.append("\n[Operations] (").append(model.getOperations().size()).append(")\n"); //$NON-NLS-1$ //$NON-NLS-2$
		for (int i = 0; i < model.getOperations().size(); i++) {
			Operation op = model.getOperations().get(i);
			sb.append("  [").append(i).append("] ").append(op.operationType()) //$NON-NLS-1$ //$NON-NLS-2$
				.append("(").append(op.expression()).append(")\n"); //$NON-NLS-1$ //$NON-NLS-2$

			List<String> comments = getComments(op);
			if (!comments.isEmpty()) {
				sb.append("       comments: ").append(comments).append('\n'); //$NON-NLS-1$
			}

			if (op instanceof MapOp mapOp) {
				if (mapOp.targetType() != null) {
					sb.append("       targetType: ").append(mapOp.targetType()).append('\n'); //$NON-NLS-1$
				}
				if (mapOp.isSideEffect()) {
					sb.append("       sideEffect: true\n"); //$NON-NLS-1$
				}
			}
		}

		// Terminal
		sb.append("\n[Terminal]\n"); //$NON-NLS-1$
		if (model.getTerminal() != null) {
			TerminalOperation term = model.getTerminal();
			sb.append("  type = ").append(term.operationType()).append('\n'); //$NON-NLS-1$
			appendTerminalDetails(sb, term);
		} else {
			sb.append("  (not set)\n"); //$NON-NLS-1$
		}

		// Convertibility summary
		sb.append("\n[Convertibility]\n"); //$NON-NLS-1$
		sb.append("  isConvertible = ").append(model.isConvertible()).append('\n'); //$NON-NLS-1$

		return sb.toString();
	}

	/**
	 * Renders a tree diagram of a {@link LoopTree}.
	 * 
	 * <p>Example output:</p>
	 * <pre>
	 * LoopTree
	 * ├── ENHANCED_FOR [CONVERTIBLE]
	 * │   └── TRADITIONAL_FOR [NOT_CONVERTIBLE]
	 * └── ITERATOR_WHILE [PENDING]
	 * </pre>
	 * 
	 * @param tree the loop tree to visualize
	 * @return the ASCII tree diagram
	 */
	public static String toTreeDiagram(LoopTree tree) {
		if (tree == null) {
			return "[null tree]"; //$NON-NLS-1$
		}

		StringBuilder sb = new StringBuilder();
		sb.append("LoopTree\n"); //$NON-NLS-1$

		List<LoopTreeNode> roots = tree.getRoots();
		for (int i = 0; i < roots.size(); i++) {
			boolean isLast = (i == roots.size() - 1);
			appendTreeNode(sb, roots.get(i), "", isLast); //$NON-NLS-1$
		}

		return sb.toString();
	}

	private static void appendTreeNode(StringBuilder sb, LoopTreeNode node, String prefix, boolean isLast) {
		String connector = isLast ? "└── " : "├── "; //$NON-NLS-1$ //$NON-NLS-2$
		sb.append(prefix).append(connector)
			.append(node.getKind())
			.append(" [").append(node.getDecision()).append("]"); //$NON-NLS-1$ //$NON-NLS-2$

		if (node.getLoopModel() != null) {
			LoopModel m = node.getLoopModel();
			sb.append(" ops=").append(m.getOperations().size()); //$NON-NLS-1$
			if (m.getTerminal() != null) {
				sb.append(" -> ").append(m.getTerminal().operationType()); //$NON-NLS-1$
			}
		}
		sb.append('\n');

		String childPrefix = prefix + (isLast ? "    " : "│   "); //$NON-NLS-1$ //$NON-NLS-2$
		List<LoopTreeNode> children = node.getChildren();
		for (int i = 0; i < children.size(); i++) {
			appendTreeNode(sb, children.get(i), childPrefix, i == children.size() - 1);
		}
	}

	private static String renderSourceExpression(LoopModel model) {
		if (model.getSource() == null) {
			return "[no source]"; //$NON-NLS-1$
		}
		SourceDescriptor src = model.getSource();
		return switch (src.type()) {
			case COLLECTION -> src.expression() + ".stream()"; //$NON-NLS-1$
			case ARRAY -> "Arrays.stream(" + src.expression() + ")"; //$NON-NLS-1$ //$NON-NLS-2$
			case ITERABLE -> "StreamSupport.stream(" + src.expression() + ".spliterator(), false)"; //$NON-NLS-1$ //$NON-NLS-2$
			case STREAM -> src.expression();
			case ITERATOR -> src.expression() + ".stream()"; //$NON-NLS-1$
			case INT_RANGE -> "IntStream.range(0, " + src.expression() + ")"; //$NON-NLS-1$ //$NON-NLS-2$
			case EXPLICIT_RANGE -> {
				String[] parts = src.expression().split(","); //$NON-NLS-1$
				if (parts.length == 2) {
					yield "IntStream.range(" + parts[0].trim() + ", " + parts[1].trim() + ")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				}
				yield "IntStream.range(" + src.expression() + ")"; //$NON-NLS-1$ //$NON-NLS-2$
			}
		};
	}

	private static String renderOperation(Operation op) {
		return switch (op) {
			case FilterOp f -> "filter(" + f.expression() + ")"; //$NON-NLS-1$ //$NON-NLS-2$
			case MapOp m -> renderMapOp(m);
			default -> op.operationType() + "(" + //$NON-NLS-1$
				(op.expression() != null ? op.expression() : "") + ")"; //$NON-NLS-1$ //$NON-NLS-2$
		};
	}

	private static String renderMapOp(MapOp m) {
		StringBuilder sb = new StringBuilder();
		sb.append("map(").append(m.expression()); //$NON-NLS-1$
		if (m.targetType() != null) {
			sb.append(" -> ").append(m.targetType()); //$NON-NLS-1$
		}
		sb.append(')');
		if (m.isSideEffect()) {
			sb.append(" [side-effect]"); //$NON-NLS-1$
		}
		return sb.toString();
	}

	private static List<String> getComments(Operation op) {
		if (op instanceof FilterOp f) {
			return f.getComments();
		}
		if (op instanceof MapOp m) {
			return m.getComments();
		}
		return List.of();
	}

	private static String renderTerminal(TerminalOperation term) {
		return switch (term) {
			case ForEachTerminal fe -> fe.ordered() ? "forEachOrdered(...)" : "forEach(...)"; //$NON-NLS-1$ //$NON-NLS-2$
			case CollectTerminal ct -> "collect(" + ct.collectorType() + ")"; //$NON-NLS-1$ //$NON-NLS-2$
			case ReduceTerminal rt -> "reduce(" + rt.reduceType() + ")"; //$NON-NLS-1$ //$NON-NLS-2$
			case MatchTerminal mt -> mt.operationType() + "(" + mt.predicate() + ")"; //$NON-NLS-1$ //$NON-NLS-2$
			case FindTerminal ft -> ft.operationType() + "()"; //$NON-NLS-1$
			default -> term.operationType() + "()"; //$NON-NLS-1$
		};
	}

	private static void appendTerminalDetails(StringBuilder sb, TerminalOperation term) {
		switch (term) {
			case ForEachTerminal fe -> {
				sb.append("  ordered = ").append(fe.ordered()).append('\n'); //$NON-NLS-1$
				if (!fe.bodyStatements().isEmpty()) {
					sb.append("  bodyStatements = ").append(fe.bodyStatements()).append('\n'); //$NON-NLS-1$
				}
			}
			case CollectTerminal ct -> {
				sb.append("  collectorType = ").append(ct.collectorType()).append('\n'); //$NON-NLS-1$
				if (ct.targetVariable() != null) {
					sb.append("  targetVariable = ").append(ct.targetVariable()).append('\n'); //$NON-NLS-1$
				}
			}
			case ReduceTerminal rt -> {
				sb.append("  reduceType = ").append(rt.reduceType()).append('\n'); //$NON-NLS-1$
				sb.append("  identity = ").append(rt.identity()).append('\n'); //$NON-NLS-1$
				sb.append("  accumulator = ").append(rt.accumulator()).append('\n'); //$NON-NLS-1$
				if (rt.combiner() != null) {
					sb.append("  combiner = ").append(rt.combiner()).append('\n'); //$NON-NLS-1$
				}
			}
			case MatchTerminal mt ->
				sb.append("  predicate = ").append(mt.predicate()).append('\n'); //$NON-NLS-1$
			default -> { /* no extra details */ }
		}
	}
}
