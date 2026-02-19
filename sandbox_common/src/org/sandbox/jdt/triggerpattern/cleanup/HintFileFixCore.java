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
package org.sandbox.jdt.triggerpattern.cleanup;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor.TransformationResult;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.ImportDirective;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;
import org.sandbox.jdt.triggerpattern.internal.GuardRegistry;
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
	 * @param enabledBundles set of enabled bundled hint file IDs; project-level
	 *        hint files (with {@code "project:"} prefix) are always included
	 *        regardless of this parameter
	 */
	public static void findOperations(CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations,
			Set<String> enabledBundles) {

		// Ensure built-in guard functions (sourceVersionGE, etc.) are registered
		GuardRegistry.getInstance();

		HintFileRegistry registry = HintFileRegistry.getInstance();
		// Ensure bundled libraries are loaded
		registry.loadBundledLibraries(HintFileFixCore.class.getClassLoader());
		// Load extension-point contributed hint files (encoding, junit5, etc.)
		registry.loadFromExtensions();

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
	 * Finds hint-file-based cleanup operations for a specific bundle,
	 * tracking processed nodes to avoid duplicate processing with imperative helpers.
	 *
	 * <p>This method loads a single hint file bundle by ID from the
	 * {@link HintFileRegistry} and creates rewrite operations for matches.
	 * Matched nodes are added to {@code nodesprocessed} so that imperative
	 * helpers can skip them.</p>
	 *
	 * @param compilationUnit the compilation unit to search
	 * @param bundleId the hint file bundle ID to load (e.g., {@code "encoding"})
	 * @param operations the set to add found operations to
	 * @param nodesprocessed set of already-processed AST nodes; matched nodes
	 *        are added to this set to prevent double-processing
	 * @since 1.3.7
	 */
	public static void findOperationsForBundle(CompilationUnit compilationUnit,
			String bundleId, Set<CompilationUnitRewriteOperation> operations,
			Set<ASTNode> nodesprocessed) {
		findOperationsForBundle(compilationUnit, bundleId, operations, nodesprocessed, null);
	}

	/**
	 * Finds hint-file-based cleanup operations for a specific bundle,
	 * tracking processed nodes and passing compiler options for guard evaluation.
	 *
	 * <p>This overload accepts compiler options that are passed to guard
	 * evaluation. This enables mode-dependent DSL rules via the
	 * {@code sandbox.cleanup.mode} option.</p>
	 *
	 * @param compilationUnit the compilation unit to search
	 * @param bundleId the hint file bundle ID to load (e.g., {@code "encoding"})
	 * @param operations the set to add found operations to
	 * @param nodesprocessed set of already-processed AST nodes; matched nodes
	 *        are added to this set to prevent double-processing
	 * @param compilerOptions compiler options for guard context; may contain
	 *        {@code sandbox.cleanup.mode} for mode-dependent rules (may be {@code null})
	 * @since 1.3.8
	 */
	public static void findOperationsForBundle(CompilationUnit compilationUnit,
			String bundleId, Set<CompilationUnitRewriteOperation> operations,
			Set<ASTNode> nodesprocessed, Map<String, String> compilerOptions) {

		// Ensure built-in guard functions (sourceVersionGE, etc.) are registered
		GuardRegistry.getInstance();

		HintFileRegistry registry = HintFileRegistry.getInstance();
		// Ensure bundled libraries are loaded
		registry.loadBundledLibraries(HintFileFixCore.class.getClassLoader());
		// Load extension-point contributed hint files (encoding, junit5, etc.)
		registry.loadFromExtensions();

		// Load project-level .sandbox-hint files if available
		if (compilationUnit.getJavaElement() != null
				&& compilationUnit.getJavaElement().getJavaProject() != null) {
			org.eclipse.core.resources.IProject project = compilationUnit.getJavaElement()
					.getJavaProject().getProject();
			registry.loadProjectHintFiles(project);
		}

		HintFile hintFile = registry.getHintFile(bundleId);
		if (hintFile == null) {
			return;
		}

		List<TransformationRule> resolvedRules = registry.resolveIncludes(hintFile);
		BatchTransformationProcessor processor = new BatchTransformationProcessor(hintFile, resolvedRules);
		List<TransformationResult> results = processor.process(compilationUnit, compilerOptions);

		for (TransformationResult result : results) {
			if (result.hasReplacement()) {
				ASTNode matchedNode = result.match().getMatchedNode();
				if (matchedNode != null) {
					nodesprocessed.add(matchedNode);
				}
				operations.add(new HintFileRewriteOperation(result));
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
		// Ensure built-in guard functions (sourceVersionGE, etc.) are registered
		GuardRegistry.getInstance();
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

				// Handle replaceStaticImport directives
				// Note: This may execute multiple times for the same imports when multiple
				// rules match in the same file. ImportRewrite handles duplicate add/remove
				// operations gracefully, so this is an acceptable tradeoff for simplicity.
				for (Map.Entry<String, String> entry : imports.getReplaceStaticImports().entrySet()) {
					String oldType = entry.getKey();
					String newType = entry.getValue();
					// Find existing static imports from oldType and replace with newType
					CompilationUnit cu = cuRewrite.getRoot();
					for (Object importObj : cu.imports()) {
						org.eclipse.jdt.core.dom.ImportDeclaration importDecl =
								(org.eclipse.jdt.core.dom.ImportDeclaration) importObj;
						if (importDecl.isStatic()) {
							String importName = importDecl.getName().getFullyQualifiedName();
							if (importDecl.isOnDemand() && importName.equals(oldType)) {
								// Wildcard: import static org.junit.Assert.* → import static org.junit.jupiter.api.Assertions.*
								importRewrite.removeStaticImport(oldType + ".*"); //$NON-NLS-1$
								importRewrite.addStaticImport(newType, "*", false); //$NON-NLS-1$
							} else if (importName.startsWith(oldType + ".")) { //$NON-NLS-1$
								// Specific: import static org.junit.Assert.assertEquals → ...Assertions.assertEquals
								String member = importName.substring(oldType.length() + 1);
								importRewrite.removeStaticImport(importName);
								importRewrite.addStaticImport(newType, member, false);
							}
						}
					}
				}
			}

			// Parse replacement as an expression and replace the matched node
			if (matchedNode instanceof Expression) {
				// Shorten FQNs to simple names (imports are already added above)
				String shortenedReplacement = shortenFqns(replacement, result);

				// Try to parse the replacement as an expression
				org.eclipse.jdt.core.dom.ASTParser parser = org.eclipse.jdt.core.dom.ASTParser.newParser(AST.getJLSLatest());
				parser.setKind(org.eclipse.jdt.core.dom.ASTParser.K_EXPRESSION);
				parser.setSource(shortenedReplacement.toCharArray());
				ASTNode newNode = parser.createAST(null);
				if (newNode instanceof Expression) {
					ASTNode copy = ASTNode.copySubtree(ast, newNode);
					// Use replaceAndRemoveNLS for StringLiteral nodes to clean up //$NON-NLS-n$ comments
					if (matchedNode instanceof StringLiteral) {
						try {
							ASTNodes.replaceAndRemoveNLS(rewrite, matchedNode, copy, group, cuRewrite);
						} catch (CoreException e) {
							// Fall back to plain replace if NLS removal fails
							rewrite.replace(matchedNode, copy, group);
						}
					} else {
						rewrite.replace(matchedNode, copy, group);
					}

					// Auto-detect: did we change a String charset argument to a Charset type?
					TypeChangeInfo typeChange = TypeChangeDetector.detectCharsetTypeChange(
							matchedNode, replacement);
					if (typeChange != null) {
						ImportRewrite importRewrite = cuRewrite.getImportRewrite();
						ExceptionCleanupHelper.removeCheckedException(
								matchedNode,
								typeChange.exceptionFQN(),
								typeChange.exceptionSimpleName(),
								group, rewrite, importRewrite);
					}
				}
			} else if (matchedNode instanceof Annotation) {
				// Handle annotation replacement (e.g., @Before → @BeforeEach)
				String annotationName = replacement.trim();
				if (annotationName.startsWith("@")) { //$NON-NLS-1$
					annotationName = annotationName.substring(1);
				}
				Annotation newAnnotation;
				if (matchedNode instanceof SingleMemberAnnotation oldSingleMember) {
					// Preserve the value: @Ignore("reason") → @Disabled("reason")
					SingleMemberAnnotation newSingleMember = ast.newSingleMemberAnnotation();
					newSingleMember.setTypeName(ast.newName(annotationName));
					newSingleMember.setValue((Expression) ASTNode.copySubtree(ast, oldSingleMember.getValue()));
					newAnnotation = newSingleMember;
				} else if (matchedNode instanceof NormalAnnotation oldNormal) {
					// Preserve member-value pairs: @Test(expected=X.class) → @Test(expected=X.class)
					NormalAnnotation newNormal = ast.newNormalAnnotation();
					newNormal.setTypeName(ast.newName(annotationName));
					for (Object obj : oldNormal.values()) {
						MemberValuePair oldPair = (MemberValuePair) obj;
						MemberValuePair newPair = ast.newMemberValuePair();
						newPair.setName(ast.newSimpleName(oldPair.getName().getIdentifier()));
						newPair.setValue((Expression) ASTNode.copySubtree(ast, oldPair.getValue()));
						newNormal.values().add(newPair);
					}
					newAnnotation = newNormal;
				} else {
					// MarkerAnnotation: @Before → @BeforeEach
					MarkerAnnotation newMarker = ast.newMarkerAnnotation();
					newMarker.setTypeName(ast.newName(annotationName));
					newAnnotation = newMarker;
				}
				rewrite.replace(matchedNode, newAnnotation, group);
			}
		}

		/**
		 * Shortens fully qualified names in the replacement text to simple names.
		 *
		 * <p>Uses the already-inferred {@link ImportDirective#getAddImports()} to know
		 * which FQNs to shorten. For example, {@code java.nio.charset.StandardCharsets.UTF_8}
		 * becomes {@code StandardCharsets.UTF_8} since the import for
		 * {@code java.nio.charset.StandardCharsets} is already being added.</p>
		 *
		 * <p>FQNs are processed longest-first to avoid partial-match issues.</p>
		 *
		 * @param replacement the raw replacement text containing FQNs
		 * @param result the transformation result with import directives
		 * @return the replacement text with FQNs shortened to simple names
		 */
		private static String shortenFqns(String replacement, TransformationResult result) {
			if (!result.hasImportDirective()) {
				return replacement;
			}
			List<String> addImports = result.importDirective().getAddImports();
			if (addImports.isEmpty()) {
				return replacement;
			}
			// Sort by length (longest first) to avoid partial replacement issues
			List<String> sorted = new java.util.ArrayList<>(addImports);
			sorted.sort((a, b) -> Integer.compare(b.length(), a.length()));

			String shortened = replacement;
			for (String fqn : sorted) {
				int lastDot = fqn.lastIndexOf('.');
				if (lastDot > 0) {
					String simpleName = fqn.substring(lastDot + 1);
					shortened = shortened.replace(fqn, simpleName);
				}
			}
			return shortened;
		}
	}
}
