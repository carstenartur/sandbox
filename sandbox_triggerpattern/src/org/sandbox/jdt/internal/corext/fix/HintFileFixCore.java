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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor.TransformationResult;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.ImportDirective;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;
import org.sandbox.jdt.triggerpattern.internal.HintFileRegistry;

/**
 * Fix core that creates rewrite operations from {@code .sandbox-hint} files.
 *
 * <p>This bridges the gap between the {@code .sandbox-hint} DSL and the Eclipse
 * CleanUp framework by using {@link BatchTransformationProcessor} to find matches
 * and creating {@link CompilationUnitRewriteOperation} instances for each match
 * that has a replacement.</p>
 *
 * @since 1.3.5
 */
public class HintFileFixCore {

	/**
	 * Finds all hint-file-based cleanup operations for the given compilation unit.
	 *
	 * <p>Loads all registered {@code .sandbox-hint} files from the
	 * {@link HintFileRegistry}, processes them with {@link BatchTransformationProcessor},
	 * and creates rewrite operations for each match with a replacement.</p>
	 *
	 * @param compilationUnit the compilation unit to search
	 * @param operations the set to add found operations to
	 * @param enabledBundles set of enabled bundled hint file IDs; if {@code null},
	 *        all bundles are processed
	 */
	public static void findOperations(CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations,
			Set<String> enabledBundles) {

		HintFileRegistry registry = HintFileRegistry.getInstance();
		// Ensure bundled libraries are loaded
		registry.loadBundledLibraries(HintFileFixCore.class.getClassLoader());

		// Load project-level .sandbox-hint files if available
		if (compilationUnit.getJavaElement() != null
				&& compilationUnit.getJavaElement().getJavaProject() != null) {
			org.eclipse.core.resources.IProject project = compilationUnit.getJavaElement()
					.getJavaProject().getProject();
			registry.loadProjectHintFiles(project);
		}

		for (Map.Entry<String, HintFile> entry : registry.getAllHintFiles().entrySet()) {
			String hintFileId = entry.getKey();
			HintFile hintFile = entry.getValue();

			// Filter by enabled bundles (project-level files are always included)
			if (enabledBundles != null && !hintFileId.startsWith("project:") //$NON-NLS-1$
					&& !enabledBundles.contains(hintFileId)) {
				continue;
			}

			List<TransformationRule> resolvedRules = registry.resolveIncludes(hintFile);
			BatchTransformationProcessor processor = new BatchTransformationProcessor(hintFile, resolvedRules);
			List<TransformationResult> results = processor.process(compilationUnit);

			for (TransformationResult result : results) {
				if (result.hasReplacement()) {
					operations.add(new HintFileRewriteOperation(result));
				}
			}
		}
	}

	/**
	 * Loads a hint file from a string and finds operations.
	 *
	 * @param compilationUnit the compilation unit to search
	 * @param hintFileContent the hint file content
	 * @param operations the set to add found operations to
	 */
	public static void findOperationsFromContent(CompilationUnit compilationUnit,
			String hintFileContent, Set<CompilationUnitRewriteOperation> operations) {
		try {
			HintFileParser parser = new HintFileParser();
			HintFile hintFile = parser.parse(hintFileContent);
			BatchTransformationProcessor processor = new BatchTransformationProcessor(hintFile);
			List<TransformationResult> results = processor.process(compilationUnit);

			for (TransformationResult result : results) {
				if (result.hasReplacement()) {
					operations.add(new HintFileRewriteOperation(result));
				}
			}
		} catch (HintFileParser.HintParseException e) {
			// Skip invalid hint files
		}
	}

	/**
	 * Generic rewrite operation that replaces matched AST nodes with the
	 * replacement text from a {@code .sandbox-hint} rule.
	 *
	 * <p>This operation handles text-based replacement by parsing the replacement
	 * text as an expression and replacing the matched AST node. It also handles
	 * import management via {@link ImportDirective}.</p>
	 */
	private static class HintFileRewriteOperation extends CompilationUnitRewriteOperation {

		private final TransformationResult result;

		public HintFileRewriteOperation(TransformationResult result) {
			this.result = result;
		}

		@Override
		public void rewriteAST(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedModel) {
			ASTRewrite rewrite = cuRewrite.getASTRewrite();
			AST ast = cuRewrite.getRoot().getAST();
			String description = result.description() != null
					? result.description()
					: "Apply hint file rule"; //$NON-NLS-1$
			TextEditGroup group = createTextEditGroup(description, cuRewrite);

			ASTNode matchedNode = result.match().getMatchedNode();
			String replacement = result.replacement();

			if (matchedNode == null || replacement == null) {
				return;
			}

			// Handle import directives
			if (result.hasImportDirective()) {
				ImportDirective imports = result.importDirective();
				ImportRewrite importRewrite = cuRewrite.getImportRewrite();

				for (String addImport : imports.getAddImports()) {
					importRewrite.addImport(addImport);
				}
				for (String removeImport : imports.getRemoveImports()) {
					importRewrite.removeImport(removeImport);
				}
				for (String addStatic : imports.getAddStaticImports()) {
					int lastDot = addStatic.lastIndexOf('.');
					if (lastDot > 0) {
						String type = addStatic.substring(0, lastDot);
						String member = addStatic.substring(lastDot + 1);
						importRewrite.addStaticImport(type, member, false);
					}
				}
				for (String removeStatic : imports.getRemoveStaticImports()) {
					int lastDot = removeStatic.lastIndexOf('.');
					if (lastDot > 0) {
						String type = removeStatic.substring(0, lastDot);
						String member = removeStatic.substring(lastDot + 1);
						importRewrite.removeStaticImport(type + "." + member); //$NON-NLS-1$
					}
				}
			}

			// Parse replacement as an expression and replace the matched node
			if (matchedNode instanceof Expression) {
				// Try to parse the replacement as an expression
				org.eclipse.jdt.core.dom.ASTParser parser = org.eclipse.jdt.core.dom.ASTParser.newParser(AST.getJLSLatest());
				parser.setKind(org.eclipse.jdt.core.dom.ASTParser.K_EXPRESSION);
				parser.setSource(replacement.toCharArray());
				ASTNode newNode = parser.createAST(null);
				if (newNode instanceof Expression) {
					ASTNode copy = ASTNode.copySubtree(ast, newNode);
					rewrite.replace(matchedNode, copy, group);
				}
			}
		}
	}
}
