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
import java.util.regex.Pattern;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.ImportRemover;
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
				// Use ImportRemover instead of directly calling importRewrite.removeImport().
				// Direct removal is unsafe because the same type may still be referenced
				// by other nodes in the compilation unit that are NOT being transformed.
				// ImportRemover scans the AST and only removes the import when ALL
				// references to the type are gone (see eclipse-jdt/eclipse.jdt.ui#121).
				if (!imports.getRemoveImports().isEmpty()) {
					ImportRemover remover = cuRewrite.getImportRemover();
					remover.registerRemovedNode(matchedNode);
					remover.applyRemoves(importRewrite);
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
					int oldCount = countStringLiterals(matchedNode);
					int newCount = countStringLiterals(copy);

					// Auto-detect: did we change a String charset argument to a Charset type?
					TypeChangeInfo typeChange = TypeChangeDetector.detectCharsetTypeChange(
							matchedNode, replacement);

					// Check if the matched node is inside a try body that will be unwrapped
					// after removing the checked exception. If so, we must handle the
					// replacement AND the try-unwrap atomically to avoid conflicts between
					// ASTNodes.replaceAndRemoveNLS (statement-level replacement) and
					// createMoveTarget (used by simplifyEmptyTryStatement).
					boolean tryAlreadyHandled = false;
					if (typeChange != null && oldCount > newCount) {
						tryAlreadyHandled = replaceAndUnwrapTryIfNeeded(
								matchedNode, copy, shortenedReplacement,
								typeChange, rewrite, cuRewrite, group);
					}

					if (!tryAlreadyHandled) {
						if (oldCount > newCount) {
							try {
								ASTNodes.replaceAndRemoveNLS(rewrite, matchedNode, copy, group, cuRewrite);
							} catch (CoreException e) {
								// Fall back to plain replace if NLS removal fails
								rewrite.replace(matchedNode, copy, group);
							}
						} else {
							rewrite.replace(matchedNode, copy, group);
						}

						if (typeChange != null) {
							ExceptionCleanupHelper.removeCheckedException(
									matchedNode,
									typeChange.exceptionFQN(),
									typeChange.exceptionSimpleName(),
									group, rewrite, cuRewrite.getImportRemover());
						}
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
			} else if (matchedNode instanceof org.eclipse.jdt.core.dom.MethodDeclaration) {
				// Handle METHOD_DECLARATION → METHOD_DECLARATION rewrite.
				// When the replacement is also a method declaration (e.g.,
				// "@Test void $name($params$)"), diff the annotations between
				// source and replacement patterns and add the missing ones.
				handleMethodDeclarationRewrite(matchedNode, replacement, ast,
						rewrite, cuRewrite, group);
			}
		}

		/**
		 * Returns the simple name of an annotation (without package prefix).
		 */
		private static String getAnnotationSimpleName(Annotation annotation) {
			if (annotation instanceof MarkerAnnotation marker) {
				return marker.getTypeName().toString();
			} else if (annotation instanceof SingleMemberAnnotation single) {
				return single.getTypeName().toString();
			} else if (annotation instanceof NormalAnnotation normal) {
				return normal.getTypeName().toString();
			}
			return ""; //$NON-NLS-1$
		}

		/**
		 * Handles METHOD_DECLARATION → METHOD_DECLARATION rewrite using natural syntax.
		 *
		 * <p>When the replacement is a method declaration with additional annotations
		 * (e.g., {@code @Test void $name($params$)}), this method diffs the annotations
		 * between the source pattern and the replacement pattern, then adds any
		 * annotations that are in the replacement but not in the source.</p>
		 *
		 * <p>This is <b>idempotent</b>: annotations already present on the actual
		 * matched method are not added again.</p>
		 *
		 * <p>This approach is <b>NetBeans-compatible</b>: the replacement is just
		 * valid Java code (a method declaration with annotations), not a custom directive.</p>
		 *
		 * @param matchedNode the matched MethodDeclaration AST node
		 * @param replacement the replacement text (e.g., {@code "@Test void $name($params$)"})
		 * @param ast the AST factory
		 * @param rewrite the AST rewriter
		 * @param cuRewrite the compilation unit rewrite context
		 * @param group the text edit group
		 * @since 1.3.9
		 */
		@SuppressWarnings("unchecked")
		private void handleMethodDeclarationRewrite(ASTNode matchedNode, String replacement,
				AST ast, ASTRewrite rewrite, CompilationUnitRewrite cuRewrite,
				TextEditGroup group) {
			if (!(matchedNode instanceof org.eclipse.jdt.core.dom.MethodDeclaration methodDecl)) {
				return;
			}

			// Parse the replacement as a method declaration to extract annotations
			org.eclipse.jdt.core.dom.MethodDeclaration replacementMethod =
					parseReplacementAsMethodDeclaration(replacement);
			if (replacementMethod == null) {
				return;
			}

			// Collect annotation simple names from the replacement method
			java.util.Set<String> replacementAnnotationNames = new java.util.LinkedHashSet<>();
			java.util.Map<String, String> annotationFqnMap = new java.util.LinkedHashMap<>();
			for (Object modifier : replacementMethod.modifiers()) {
				if (modifier instanceof Annotation ann) {
					String simpleName = getAnnotationSimpleName(ann);
					replacementAnnotationNames.add(simpleName);
					// Try to extract FQN from the replacement text for import management
					String fqn = extractAnnotationFqnFromText(replacement, simpleName);
					if (fqn != null) {
						annotationFqnMap.put(simpleName, fqn);
					}
				}
			}

			// Collect annotation simple names from the source pattern (pattern text, not actual code)
			String sourcePatternText = result.rule() != null
					? result.rule().sourcePattern().getValue() : ""; //$NON-NLS-1$
			java.util.Set<String> sourceAnnotationNames = new java.util.LinkedHashSet<>();
			org.eclipse.jdt.core.dom.MethodDeclaration sourceMethod =
					parseReplacementAsMethodDeclaration(sourcePatternText);
			if (sourceMethod != null) {
				for (Object modifier : sourceMethod.modifiers()) {
					if (modifier instanceof Annotation ann) {
						sourceAnnotationNames.add(getAnnotationSimpleName(ann));
					}
				}
			}

			// Annotations to add = in replacement but not in source pattern
			java.util.Set<String> annotationsToAdd = new java.util.LinkedHashSet<>(replacementAnnotationNames);
			annotationsToAdd.removeAll(sourceAnnotationNames);

			if (annotationsToAdd.isEmpty()) {
				return;
			}

			// Collect existing annotation simple names on the actual matched method
			java.util.Set<String> existingAnnotationNames = new java.util.LinkedHashSet<>();
			for (Object modifier : methodDecl.modifiers()) {
				if (modifier instanceof Annotation ann) {
					existingAnnotationNames.add(getAnnotationSimpleName(ann));
				}
			}

			// Add each missing annotation (idempotent: skip if already present)
			ListRewrite modifiersRewrite = rewrite.getListRewrite(
					methodDecl, org.eclipse.jdt.core.dom.MethodDeclaration.MODIFIERS2_PROPERTY);
			ImportRewrite importRewrite = cuRewrite.getImportRewrite();
			boolean changed = false;

			for (String annotationName : annotationsToAdd) {
				if (existingAnnotationNames.contains(annotationName)) {
					continue; // Already present on the actual method — skip
				}
				// Use createStringPlaceholder so the annotation appears on its own
				// line above the method declaration (proper formatting).
				ASTNode newAnnotation = rewrite.createStringPlaceholder(
						"@" + annotationName + "\n", ASTNode.MARKER_ANNOTATION); //$NON-NLS-1$ //$NON-NLS-2$
				modifiersRewrite.insertFirst(newAnnotation, group);
				changed = true;

				// Add import for the FQN if available
				String fqn = annotationFqnMap.get(annotationName);
				if (fqn != null) {
					importRewrite.addImport(fqn);
				}
			}

			// If nothing changed (all annotations already present), skip
			if (!changed) {
				return;
			}
		}

		/**
		 * Parses a string as a method declaration by wrapping it in a class context.
		 *
		 * @param methodSnippet the method snippet (e.g., {@code "@Test void $name($params$)"})
		 * @return the parsed MethodDeclaration, or {@code null} if parsing fails
		 */
		private static org.eclipse.jdt.core.dom.MethodDeclaration parseReplacementAsMethodDeclaration(
				String methodSnippet) {
			if (methodSnippet == null || methodSnippet.isBlank()) {
				return null;
			}
			String normalized = methodSnippet.trim();
			// Add empty body if not present
			if (!normalized.endsWith("}") && !normalized.endsWith(";")) { //$NON-NLS-1$ //$NON-NLS-2$
				normalized = normalized + " {}"; //$NON-NLS-1$
			}
			// Handle multi-placeholder parameters for valid Java syntax
			normalized = normalized.replaceAll(
					"\\(\\s*\\$([a-zA-Z_][a-zA-Z0-9_]*)\\$\\s*\\)", //$NON-NLS-1$
					"(Object... \\$$1\\$)"); //$NON-NLS-1$
			// Handle single placeholders as method names (replace $name with _name for parsing)
			// We just need the annotations, so the method body doesn't matter
			String source = "class _Pattern { " + normalized + " }"; //$NON-NLS-1$ //$NON-NLS-2$
			org.eclipse.jdt.core.dom.ASTParser parser = org.eclipse.jdt.core.dom.ASTParser
					.newParser(AST.getJLSLatest());
			parser.setSource(source.toCharArray());
			parser.setKind(org.eclipse.jdt.core.dom.ASTParser.K_COMPILATION_UNIT);
			parser.setCompilerOptions(org.eclipse.jdt.core.JavaCore.getOptions());
			CompilationUnit cu = (CompilationUnit) parser.createAST(null);
			if (cu.types().isEmpty()) {
				return null;
			}
			org.eclipse.jdt.core.dom.TypeDeclaration typeDecl =
					(org.eclipse.jdt.core.dom.TypeDeclaration) cu.types().get(0);
			if (typeDecl.getMethods().length == 0) {
				return null;
			}
			return typeDecl.getMethods()[0];
		}

		/**
		 * Extracts the FQN for an annotation from the replacement text.
		 * For example, from {@code "@org.junit.jupiter.api.Test void $name($params$)"},
		 * extracts {@code "org.junit.jupiter.api.Test"} for annotation simple name {@code "Test"}.
		 *
		 * @param replacementText the full replacement text
		 * @param simpleName the annotation simple name to look for
		 * @return the FQN (without {@code @}), or {@code null} if not found
		 */
		private static String extractAnnotationFqnFromText(String replacementText, String simpleName) {
			// Look for @pkg.sub.AnnotationName pattern in the replacement text
			java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
					"@((?:[a-z][a-z0-9_]*\\.)+)" + java.util.regex.Pattern.quote(simpleName) + "\\b") //$NON-NLS-1$ //$NON-NLS-2$
					.matcher(replacementText);
			if (matcher.find()) {
				// Return the full FQN without @
				return matcher.group(1) + simpleName;
			}
			return null;
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

		/**
		 * Counts the number of {@link StringLiteral} nodes in the given AST subtree.
		 *
		 * <p>This is used to determine whether {@code replaceAndRemoveNLS} should be
		 * used instead of a plain {@code rewrite.replace()}. When a DSL rule replaces
		 * a string literal with a non-string expression (e.g., {@code "UTF-8"} →
		 * {@code StandardCharsets.UTF_8}), the count decreases and the associated
		 * {@code //$NON-NLS-n$} comment must be removed. When the count stays the
		 * same or increases (e.g., zero-arg expansion adding a new argument),
		 * existing NLS comments must be preserved.</p>
		 *
		 * @param node the AST node to inspect
		 * @return the number of {@code StringLiteral} nodes found
		 */
		private static int countStringLiterals(ASTNode node) {
			int[] count = { 0 };
			node.accept(new org.eclipse.jdt.core.dom.ASTVisitor() {
				@Override
				public boolean visit(StringLiteral literal) {
					count[0]++;
					return false;
				}
			});
			return count[0];
		}

		/**
		 * Pattern matching the LAST NLS comment on a line.
		 * Adapted from {@code AbstractExplicitEncoding}.
		 */
		private static final Pattern LAST_NLS_COMMENT = Pattern.compile("[ ]*\\/\\/\\$NON-NLS-[0-9]+\\$(?!.*\\/\\/\\$NON-NLS-)"); //$NON-NLS-1$

		/**
		 * Checks whether the given statement is directly inside the body of a try statement
		 * that will be fully unwrapped after removing the target exception. This happens when:
		 * <ul>
		 *   <li>The statement is in the try body (not a catch/finally block)</li>
		 *   <li>The try has no resources (not try-with-resources)</li>
		 *   <li>The try has no finally block</li>
		 *   <li>The try has exactly one catch clause catching only the target exception</li>
		 * </ul>
		 */
		private static boolean isInsideTryBodyWithOnlyTargetExceptionCatch(ASTNode statement, String exceptionSimple) {
			ASTNode parent = statement.getParent();
			if (!(parent instanceof Block block)) {
				return false;
			}
			ASTNode grandParent = block.getParent();
			if (!(grandParent instanceof TryStatement tryStatement)) {
				return false;
			}
			if (tryStatement.getBody() != block) {
				return false;
			}
			if (!tryStatement.resources().isEmpty()) {
				return false;
			}
			if (tryStatement.getFinally() != null) {
				return false;
			}
			@SuppressWarnings("unchecked")
			List<CatchClause> catchClauses = tryStatement.catchClauses();
			if (catchClauses.size() != 1) {
				return false;
			}
			CatchClause catchClause = catchClauses.get(0);
			Type exType = catchClause.getException().getType();
			if (exType instanceof SimpleType simpleType) {
				return exceptionSimple.equals(simpleType.getName().toString());
			}
			return false;
		}

		/**
		 * If the matched node is inside a try body that will be unwrapped after exception
		 * removal, handles BOTH the replacement AND the try-catch unwrapping in a single
		 * text-based operation to avoid conflicts between {@code rewrite.replace()} on
		 * child nodes and {@code createMoveTarget()} on parent statements.
		 *
		 * @return {@code true} if the combined operation was performed (caller should skip
		 *         separate replacement and exception removal), {@code false} if not applicable
		 */
		private static boolean replaceAndUnwrapTryIfNeeded(
				ASTNode matchedNode, ASTNode copy, String shortenedReplacement,
				TypeChangeInfo typeChange, ASTRewrite rewrite,
				CompilationUnitRewrite cuRewrite, TextEditGroup group) {
			ASTNode st = ASTNodes.getFirstAncestorOrNull(matchedNode, Statement.class);
			if (st == null || !isInsideTryBodyWithOnlyTargetExceptionCatch(st, typeChange.exceptionSimpleName())) {
				return false;
			}
			Block block = (Block) st.getParent();
			TryStatement tryStatement = (TryStatement) block.getParent();
			ASTNode tryParent = tryStatement.getParent();
			if (!(tryParent instanceof Block parentBlock)) {
				return false;
			}
			try {
				String buffer = cuRewrite.getCu().getBuffer().getContents();
				CompilationUnit cu = (CompilationUnit) st.getRoot();
				String matchedSource = buffer.substring(matchedNode.getStartPosition(),
						matchedNode.getStartPosition() + matchedNode.getLength());

				ListRewrite parentListRewrite = rewrite.getListRewrite(parentBlock, Block.STATEMENTS_PROPERTY);
				List<?> tryStatements = block.statements();
				for (int i = tryStatements.size() - 1; i >= 0; i--) {
					ASTNode stmt = (ASTNode) tryStatements.get(i);
					int stmtStart = cu.getExtendedStartPosition(stmt);
					int stmtLength = cu.getExtendedLength(stmt);
					String stmtSource = buffer.substring(stmtStart, stmtStart + stmtLength);
					// Remove leading whitespace
					stmtSource = Pattern.compile("^[ \\t]*").matcher(stmtSource).replaceAll(""); //$NON-NLS-1$ //$NON-NLS-2$
					stmtSource = Pattern.compile("\n[ \\t]*").matcher(stmtSource).replaceAll("\n"); //$NON-NLS-1$ //$NON-NLS-2$
					if (stmt == st) {
						// Remove last NLS comment and apply the replacement
						stmtSource = LAST_NLS_COMMENT.matcher(stmtSource).replaceFirst(""); //$NON-NLS-1$
						stmtSource = stmtSource.replace(matchedSource, shortenedReplacement);
					}
					ASTNode placeholder = rewrite.createStringPlaceholder(stmtSource, stmt.getNodeType());
					parentListRewrite.insertAfter(placeholder, tryStatement, group);
				}
				rewrite.remove(tryStatement, group);
				// Register removed nodes for import removal
				cuRewrite.getImportRemover().registerRemovedNode(matchedNode);
				return true;
			} catch (JavaModelException e) {
				// Fall back to separate handling
				return false;
			}
		}
	}
}
