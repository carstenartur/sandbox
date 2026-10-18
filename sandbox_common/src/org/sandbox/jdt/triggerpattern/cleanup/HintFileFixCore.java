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
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.ImportRemover;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.util.TypeWideningAnalyzer;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor.TransformationResult;
import org.sandbox.jdt.triggerpattern.api.EmbeddedJavaBlock;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.ImportDirective;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;
import org.sandbox.jdt.triggerpattern.eclipse.HintFinding;
import org.sandbox.jdt.triggerpattern.internal.EmbeddedFixExecutor;
import org.sandbox.jdt.triggerpattern.internal.EmbeddedGuardRegistrar;
import org.sandbox.jdt.triggerpattern.internal.EmbeddedJavaCompiler;
import org.sandbox.jdt.triggerpattern.internal.EmbeddedJavaCompiler.CompilationResult;
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
	 * and creates rewrite operations for each match with a replacement.
	 * Hint-only results (rules without replacement) are silently discarded.
	 * Use the four-parameter overload to collect hint-only findings.</p>
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
		findOperations(compilationUnit, operations, enabledBundles, null);
	}

	/**
	 * Finds hint-file-based cleanup operations for the given compilation unit,
	 * collecting hint-only results into the provided findings list.
	 *
	 * <p>Rules with a replacement produce rewrite operations. Rules without a
	 * replacement (hint-only) produce {@link HintFinding} entries that can be
	 * reported as problem markers.</p>
	 *
	 * @param compilationUnit the compilation unit to search
	 * @param operations the set to add found rewrite operations to
	 * @param enabledBundles set of enabled bundled hint file IDs
	 * @param findings list to collect hint-only findings into; may be {@code null}
	 *        to discard hint-only results (backward compatible)
	 */
	public static void findOperations(CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations,
			Set<String> enabledBundles,
			List<HintFinding> findings) {

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

			// Register guard and fix functions from <? ?> blocks
			registerEmbeddedFunctions(hintFile, hintFileId);

			List<TransformationRule> resolvedRules = registry.resolveIncludes(hintFile);
			BatchTransformationProcessor processor = new BatchTransformationProcessor(hintFile, resolvedRules);
			List<TransformationResult> results = processor.process(compilationUnit);

			for (TransformationResult result : results) {
				if (result.hasReplacement()) {
					if (isNonWidenableDeclaration(result, compilationUnit)) {
						continue;
					}
					addRewriteOperation(compilationUnit, result, operations);
				} else if (findings != null) {
					findings.add(HintFinding.fromTransformationResult(result, compilationUnit));
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

		// Register guard and fix functions from <? ?> blocks
		registerEmbeddedFunctions(hintFile, bundleId);

		List<TransformationRule> resolvedRules = registry.resolveIncludes(hintFile);
		BatchTransformationProcessor processor = new BatchTransformationProcessor(hintFile, resolvedRules);
		List<TransformationResult> results = processor.process(compilationUnit, compilerOptions);

		for (TransformationResult result : results) {
			if (result.hasReplacement()) {
				if (isNonWidenableDeclaration(result, compilationUnit)) {
					continue;
				}
				ASTNode matchedNode = result.match().getMatchedNode();
				if (addRewriteOperation(compilationUnit, result, operations) && matchedNode != null) {
					nodesprocessed.add(matchedNode);
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
		// Ensure built-in guard functions (sourceVersionGE, etc.) are registered
		GuardRegistry.getInstance();
		try {
			HintFileParser parser = new HintFileParser();
			HintFile hintFile = parser.parse(hintFileContent);
			findOperationsFromHint(compilationUnit, hintFile, operations);
		} catch (HintFileParser.HintParseException e) {
			// Skip invalid hint files
		}
	}

	/** Uses an already parsed draft and its explicit binding policy in the ordinary backend. */
	public static void findOperationsFromHint(CompilationUnit compilationUnit,
			HintFile hintFile, Set<CompilationUnitRewriteOperation> operations) {
		GuardRegistry.getInstance();
		for (TransformationResult result : new BatchTransformationProcessor(hintFile).process(compilationUnit)) {
			if (result.hasReplacement() && !isNonWidenableDeclaration(result, compilationUnit)) {
				addRewriteOperation(compilationUnit, result, operations);
			}
		}
	}

	/** Declines unsupported or recovered text before constructing an empty cleanup fix. */
	private static boolean addRewriteOperation(CompilationUnit root, TransformationResult result,
			Set<CompilationUnitRewriteOperation> operations) {
		ASTNode node= result.match().getMatchedNode();
		boolean expression= node instanceof Expression;
		boolean statement= node instanceof Statement
				&& !(node instanceof VariableDeclarationStatement && result.replacement().contains("$widestType")); //$NON-NLS-1$
		Map<String, String> options= root.getJavaElement() == null
				? org.sandbox.jdt.triggerpattern.api.SemanticRewritePlanContext.currentCompilerOptions()
				: root.getJavaElement().getJavaProject().getOptions(true);
		if ((expression || statement)
				&& (HintFileRewriteOperation.hasCommentInRange(root, result.match().getOffset(), result.match().getLength())
						|| expression && HintFileRewriteOperation.parseExpression(result.replacement(), options) == null
						|| statement && HintFileRewriteOperation.parseBody(result.replacement(), options) == null)) {
			return false;
		}
		if (node instanceof org.eclipse.jdt.core.dom.MethodDeclaration
				&& HintFileRewriteOperation.parseReplacementAsMethodDeclaration(result.replacement(), options) == null) {
			return false;
		}
		operations.add(new HintFileRewriteOperation(result));
		return true;
	}

	/**
	 * Registers guard and fix functions from embedded Java blocks in a hint file.
	 *
	 * <p>Compiles each non-blank {@code <? ?>} block and registers its guard
	 * methods in the {@link GuardRegistry} and fix methods in the
	 * {@link EmbeddedFixExecutor}.</p>
	 *
	 * @param hintFile the hint file containing embedded Java blocks
	 * @param hintFileId the hint file ID for tracking
	 * @since 1.5.0
	 */
	private static void registerEmbeddedFunctions(HintFile hintFile, String hintFileId) {
		// Unregister previously registered guards/fixes for this hint file
		// to avoid stale entries when a hint file is reloaded after editing
		EmbeddedGuardRegistrar.unregisterGuards(hintFileId);
		EmbeddedFixExecutor.unregisterFixes(hintFileId);

		for (EmbeddedJavaBlock block : hintFile.getEmbeddedJavaBlocks()) {
			if (block.getSource().isBlank()) {
				continue;
			}
			CompilationResult compResult = EmbeddedJavaCompiler.compile(block, hintFileId);
			if (!compResult.hasErrors()) {
				EmbeddedGuardRegistrar.registerGuards(compResult, hintFileId);
				EmbeddedFixExecutor.registerFixes(compResult, hintFileId);
			}
		}
	}

	/**
	 * Checks if a transformation result is a DECLARATION pattern with {@code $widestType}
	 * that cannot actually be widened. When widening IS possible, the widest type FQN
	 * is stored in the match's extra data for later retrieval by
	 * {@code handleDeclarationRewrite}.
	 *
	 * <p>This filtering ensures that rewrite operations are only created when a real
	 * type change will be produced — avoiding entering {@code rewriteAST} without a
	 * change to apply.</p>
	 *
	 * @param result the transformation result to check
	 * @param compilationUnit the compilation unit for type widening analysis
	 * @return {@code true} if this is a $widestType declaration that cannot be widened
	 *         (should be skipped); {@code false} otherwise (proceed normally)
	 * @since 1.3.12
	 */
	private static boolean isNonWidenableDeclaration(TransformationResult result,
			CompilationUnit compilationUnit) {
		String replacement = result.replacement();
		if (replacement == null || !replacement.contains("$widestType")) { //$NON-NLS-1$
			return false; // Not a $widestType rule — proceed normally
		}
		ASTNode matchedNode = result.match().getMatchedNode();
		if (!(matchedNode instanceof VariableDeclarationStatement declStmt)) {
			return false; // Not a declaration — proceed normally
		}
		if (declStmt.fragments().size() != 1) {
			return true; // Multi-fragment — cannot widen
		}
		VariableDeclarationFragment fragment =
				(VariableDeclarationFragment) declStmt.fragments().get(0);
		IVariableBinding varBinding = fragment.resolveBinding();
		if (varBinding == null) {
			return true; // No binding — cannot analyze
		}

		Map<String, TypeWideningAnalyzer.TypeWideningResult> analysisResults =
				TypeWideningAnalyzer.analyzeCompilationUnit(compilationUnit);
		TypeWideningAnalyzer.TypeWideningResult wideningResult =
				analysisResults.get(varBinding.getKey());
		if (wideningResult == null || !wideningResult.canWiden()) {
			return true; // No widening possible — skip
		}

		// Widening IS possible — store the FQN for handleDeclarationRewrite
		result.match().putExtraData("__widestType__", //$NON-NLS-1$
				wideningResult.getWidestType().getQualifiedName());
		return false; // Proceed — create the operation
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
		public void rewriteAST(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedModel) throws CoreException {
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
			boolean sequence= result.rule().sourcePattern().getKind() == PatternKind.STATEMENT_SEQUENCE;
			boolean ordinaryStatement= matchedNode instanceof Statement
					&& !(matchedNode instanceof VariableDeclarationStatement && replacement.contains("$widestType")); //$NON-NLS-1$
			if ((sequence || ordinaryStatement)
					&& (hasCommentInRange(cuRewrite.getRoot(), result.match().getOffset(), result.match().getLength())
							|| parseStatements(replacement, cuRewrite) == null)) {
				// Do not change imports or partially apply a recovered replacement.
				return;
			}
			if (matchedNode instanceof Expression
					&& (hasCommentInRange(cuRewrite.getRoot(), result.match().getOffset(), result.match().getLength())
							|| parseExpression(replacement, cuRewrite) == null)) {
				return;
			}

			// Check for embedded fix function reference: <?fixName?>
			// These are dispatched to EmbeddedFixExecutor instead of text-based replacement.
			// Currently fix functions are stubs (Phase 1.4 MVP) — log and skip rewrite.
			String fixName = extractEmbeddedFixName(replacement);
			if (fixName != null) {
				if (EmbeddedFixExecutor.hasFix(fixName)) {
					EmbeddedFixExecutor.execute(fixName);
				}
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
			if (sequence || ordinaryStatement && matchedNode.getParent() instanceof Block) {
				rewriteStatementSequence(cuRewrite, group);
			} else if (matchedNode instanceof Expression) {
				// Shorten FQNs to simple names (imports are already added above)
				String shortenedReplacement = shortenFqns(replacement, result,
						cuRewrite.getCu().getJavaProject().getOptions(true));

				// Try to parse the replacement as an expression
				Expression newNode = parseExpression(shortenedReplacement, cuRewrite);
				if (newNode != null) {
					ASTNode copy = ASTNode.copySubtree(ast, newNode);
					int oldCount = countStringLiterals(matchedNode);
					int newCount = countStringLiterals(copy);
                    if (CharsetConstructorMigration.rewriteExpression(matchedNode, copy, replacement, cuRewrite, group)) {
                        TypeChangeInfo change= TypeChangeDetector.detectCharsetTypeChange(matchedNode, newNode);
                        if (change != null) ExceptionCleanupHelper.removeCheckedException(matchedNode,
                                change.exceptionFQN(), change.exceptionSimpleName(), group, rewrite, cuRewrite.getImportRemover());
                        CharsetConstructorMigration.adapt(matchedNode, copy, replacement, cuRewrite, group);
                        return;
                    }


					// Auto-detect: did we change a String charset argument to a Charset type?
					TypeChangeInfo typeChange = TypeChangeDetector.detectCharsetTypeChange(
							matchedNode, newNode);

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
					CharsetConstructorMigration.adapt(matchedNode, copy, replacement, cuRewrite, group);
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
			} else if (matchedNode instanceof VariableDeclarationStatement declStmt
					&& replacement.contains("$widestType")) { //$NON-NLS-1$
				// Handle DECLARATION rewrite with $widestType support.
				// When the replacement contains $widestType($var), replace the
				// declaration type with the widest type computed by canWidenType guard.
				handleDeclarationRewrite(declStmt, replacement, ast, rewrite, cuRewrite, group);
			} else if (matchedNode instanceof Statement) {
				Block parsed= parseStatements(replacement, cuRewrite);
				if (parsed != null) {
					ASTNode node= parsed.statements().size() == 1 ? (ASTNode) parsed.statements().getFirst() : parsed;
					rewrite.replace(matchedNode, ASTNode.copySubtree(ast, node), group);
				}
			}
		}

		/** Rewrites only the matched original list window, preserving its siblings. */
		private void rewriteStatementSequence(CompilationUnitRewrite cuRewrite, TextEditGroup group) {
			ASTNode anchor= result.match().getMatchedNode();
			if (!(anchor instanceof Statement) || !(anchor.getParent() instanceof Block block)) {
				return;
			}
			int start= result.match().getOffset();
			int end= start + result.match().getLength();
			List<Statement> originals= new java.util.ArrayList<>();
			for (Object value : block.statements()) {
				Statement statement= (Statement) value;
				if (statement.getStartPosition() >= start
						&& statement.getStartPosition() + statement.getLength() <= end) {
					originals.add(statement);
				}
			}
			if (originals.isEmpty() || originals.getFirst() != anchor
					|| originals.getLast().getStartPosition() + originals.getLast().getLength() != end) {
				return;
			}
			Block parsed= parseStatements(shortenFqns(result.replacement(), result,
					cuRewrite.getCu().getJavaProject().getOptions(true)), cuRewrite);
			if (parsed == null) {
				return;
			}
			ASTRewrite rewrite= cuRewrite.getASTRewrite();
			ListRewrite list= rewrite.getListRewrite(block, Block.STATEMENTS_PROPERTY);
			for (Object value : parsed.statements()) {
				list.insertBefore(ASTNode.copySubtree(block.getAST(), (ASTNode) value), anchor, group);
			}
			for (Statement original : originals) {
				list.remove(original, group);
			}
		}

		private static Block parseStatements(String source, CompilationUnitRewrite cuRewrite) {
			return parseBody(source, cuRewrite);
		}

		private static Expression parseExpression(String source, CompilationUnitRewrite cuRewrite) {
			return parseExpression(source, cuRewrite.getCu().getJavaProject().getOptions(true));
		}

		private static Expression parseExpression(String source, Map<String, String> options) {
			Block body= parseBody("return " + source + ";", options); //$NON-NLS-1$ //$NON-NLS-2$
			if (body != null && body.statements().size() == 1
					&& body.statements().getFirst() instanceof org.eclipse.jdt.core.dom.ReturnStatement statement) {
				return statement.getExpression();
			}
			return null;
		}

		private static Block parseBody(String source, CompilationUnitRewrite cuRewrite) {
			return parseBody(source, cuRewrite.getCu().getJavaProject().getOptions(true));
		}

		private static Block parseBody(String source, Map<String, String> options) {
			org.eclipse.jdt.core.dom.ASTParser parser= org.eclipse.jdt.core.dom.ASTParser.newParser(AST.getJLSLatest());
			parser.setKind(org.eclipse.jdt.core.dom.ASTParser.K_COMPILATION_UNIT);
			parser.setSource(("class __Hint { void __hint() {\n" + source + "\n} }").toCharArray()); //$NON-NLS-1$ //$NON-NLS-2$
			parser.setCompilerOptions(options);
			CompilationUnit parsed= (CompilationUnit) parser.createAST(null);
			for (var problem : parsed.getProblems()) {
				if (problem.isError()) {
					return null;
				}
			}
			boolean[] recovered= { false };
			parsed.accept(new org.eclipse.jdt.core.dom.ASTVisitor() {
				@Override
				public void preVisit(ASTNode node) {
					recovered[0] |= (node.getFlags() & (ASTNode.MALFORMED | ASTNode.RECOVERED)) != 0;
				}
			});
			if (recovered[0] || !parsed.getCommentList().isEmpty() || parsed.types().size() != 1
					|| !(parsed.types().getFirst() instanceof org.eclipse.jdt.core.dom.TypeDeclaration type)
					|| type.bodyDeclarations().size() != 1 || type.getMethods().length != 1) {
				return null;
			}
			return type.getMethods()[0].getBody();
		}

		private static boolean hasCommentInRange(CompilationUnit root, int offset, int length) {
			return root.getCommentList().stream().anyMatch(value -> {
				ASTNode comment= (ASTNode) value;
				return comment.getStartPosition() < offset + length
						&& comment.getStartPosition() + comment.getLength() > offset;
			});
		}

		/**
		 * Returns an annotation's parsed name, retaining any qualification.
		 */
		private static String getAnnotationName(Annotation annotation) {
			return annotation.getTypeName().getFullyQualifiedName();
		}

		/**
		 * Handles DECLARATION rewrite with {@code $widestType} support.
		 *
		 * <p>The widest type FQN was pre-computed by {@link #isNonWidenableDeclaration}
		 * and stored in the match's extra data under {@code "__widestType__"}.
		 * This method is only called when widening IS possible, so the FQN is
		 * guaranteed to be present.</p>
		 *
		 * @param declStmt the matched VariableDeclarationStatement
		 * @param replacement the replacement text
		 * @param ast the AST factory
		 * @param rewrite the AST rewriter
		 * @param cuRewrite the compilation unit rewrite context
		 * @param group the text edit group
		 * @since 1.3.12
		 */
		private void handleDeclarationRewrite(VariableDeclarationStatement declStmt,
				String replacement, AST ast, ASTRewrite rewrite,
				CompilationUnitRewrite cuRewrite, TextEditGroup group) {
			// Check if replacement uses $widestType function
			if (replacement == null || !replacement.contains("$widestType")) { //$NON-NLS-1$
				return;
			}

			// Retrieve the widest type FQN pre-computed by isNonWidenableDeclaration
			Object widestTypeFqn = result.match().getExtraData("__widestType__"); //$NON-NLS-1$
			if (!(widestTypeFqn instanceof String fqn) || fqn.isEmpty()) {
				return;
			}

			// Add import for the FQN if available
			String simpleName = cuRewrite.getImportRewrite().addImport(fqn);

			// Create the new type node
			Type newType;
			if (simpleName.contains(".")) { //$NON-NLS-1$
				// Qualified name: split and create QualifiedName
				newType = ast.newSimpleType(ast.newName(simpleName.split("\\."))); //$NON-NLS-1$
			} else {
				newType = ast.newSimpleType(ast.newSimpleName(simpleName));
			}

			// Replace the type in the variable declaration statement
			rewrite.replace(declStmt.getType(), newType, group);
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
					parseReplacementAsMethodDeclaration(replacement,
							cuRewrite.getCu().getJavaProject().getOptions(true));
			if (replacementMethod == null) {
				return;
			}

			// Keep qualified annotation identity until ImportRewrite chooses the rendered name.
			java.util.Set<String> replacementAnnotationNames = new java.util.LinkedHashSet<>();
			java.util.Map<String, String> annotationFqnMap = new java.util.LinkedHashMap<>();
			for (Object modifier : replacementMethod.modifiers()) {
				if (modifier instanceof Annotation ann) {
					String annotationName = getAnnotationName(ann);
					replacementAnnotationNames.add(annotationName);
					if (annotationName.indexOf('.') >= 0) {
						annotationFqnMap.put(annotationName, annotationName);
					}
				}
			}

			// Collect annotation simple names from the source pattern (pattern text, not actual code)
			String sourcePatternText = result.rule() != null
					? result.rule().sourcePattern().getValue() : ""; //$NON-NLS-1$
			java.util.Set<String> sourceAnnotationNames = new java.util.LinkedHashSet<>();
			org.eclipse.jdt.core.dom.MethodDeclaration sourceMethod =
					parseReplacementAsMethodDeclaration(sourcePatternText,
							cuRewrite.getCu().getJavaProject().getOptions(true));
			if (sourceMethod != null) {
				for (Object modifier : sourceMethod.modifiers()) {
					if (modifier instanceof Annotation ann) {
						sourceAnnotationNames.add(getAnnotationName(ann));
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
			java.util.Set<String> existingAnnotationTypes = new java.util.LinkedHashSet<>();
			for (Object modifier : methodDecl.modifiers()) {
				if (modifier instanceof Annotation ann) {
					existingAnnotationNames.add(getAnnotationName(ann));
					var binding= ann.resolveTypeBinding();
					if (binding != null && !binding.isRecovered()) {
						existingAnnotationTypes.add(binding.getQualifiedName());
					}
				}
			}

			// Add each missing annotation (idempotent: skip if already present)
			ListRewrite modifiersRewrite = rewrite.getListRewrite(
					methodDecl, org.eclipse.jdt.core.dom.MethodDeclaration.MODIFIERS2_PROPERTY);
			ImportRewrite importRewrite = cuRewrite.getImportRewrite();
			boolean changed = false;

			for (String annotationName : annotationsToAdd) {
				String fqn = annotationFqnMap.get(annotationName);
				if (fqn != null
						? existingAnnotationTypes.contains(fqn) || existingAnnotationNames.contains(annotationName)
						: existingAnnotationNames.contains(annotationName)) {
					continue; // Already present on the actual method — skip
				}
				// Use createStringPlaceholder so the annotation appears on its own
				// line above the method declaration (proper formatting).
				ASTNode newAnnotation = rewrite.createStringPlaceholder(
						"@" + (fqn == null ? annotationName : importRewrite.addImport(fqn)) + "\n", ASTNode.MARKER_ANNOTATION); //$NON-NLS-1$ //$NON-NLS-2$
				modifiersRewrite.insertFirst(newAnnotation, group);
				changed = true;

				// Add import for the FQN if available
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
				String methodSnippet, Map<String, String> options) {
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
			parser.setCompilerOptions(options);
			CompilationUnit cu = (CompilationUnit) parser.createAST(null);
			for (var problem : cu.getProblems()) {
				if (problem.isError()) {
					return null;
				}
			}
			boolean[] recovered= { false };
			cu.accept(new org.eclipse.jdt.core.dom.ASTVisitor() {
				@Override
				public void preVisit(ASTNode node) {
					recovered[0] |= (node.getFlags() & (ASTNode.MALFORMED | ASTNode.RECOVERED)) != 0;
				}
			});
			if (recovered[0] || cu.types().size() != 1
					|| !(cu.types().get(0) instanceof org.eclipse.jdt.core.dom.TypeDeclaration typeDecl)
					|| typeDecl.bodyDeclarations().size() != 1 || typeDecl.getMethods().length != 1) {
				return null;
			}
			return typeDecl.getMethods()[0];
		}

		/**
		 * Shortens fully qualified names in the replacement text to simple names.
		 *
		 * <p>Uses the already-inferred {@link ImportDirective#getAddImports()} to know
		 * which FQNs to shorten. For example, {@code java.nio.charset.StandardCharsets.UTF_8}
		 * becomes {@code StandardCharsets.UTF_8} since the import for
		 * {@code java.nio.charset.StandardCharsets} is already being added.</p>
		 *
		 * <p>Only parsed Java names are shortened; literal contents remain untouched.</p>
		 *
		 * @param replacement the raw replacement text containing FQNs
		 * @param result the transformation result with import directives
		 * @return the replacement text with imported Java names shortened
		 */
		private static String shortenFqns(String replacement, TransformationResult result,
				Map<String, String> options) {
			if (!result.hasImportDirective()) {
				return replacement;
			}
			List<String> addImports = result.importDirective().getAddImports();
			if (addImports.isEmpty()) {
				return replacement;
			}
			String prefix= "class __Hint { void __hint() {\n"; //$NON-NLS-1$
			String wrapped= prefix + replacement + "\n} }"; //$NON-NLS-1$
			org.eclipse.jdt.core.dom.ASTParser parser= org.eclipse.jdt.core.dom.ASTParser.newParser(AST.getJLSLatest());
			parser.setKind(org.eclipse.jdt.core.dom.ASTParser.K_COMPILATION_UNIT);
			parser.setSource(wrapped.toCharArray());
			parser.setCompilerOptions(options);
			CompilationUnit parsed= (CompilationUnit) parser.createAST(null);
			java.util.Map<Integer, String> replacements= new java.util.LinkedHashMap<>();
			parsed.accept(new org.eclipse.jdt.core.dom.ASTVisitor() {
				@Override
				public void preVisit(ASTNode node) {
					if (!(node instanceof org.eclipse.jdt.core.dom.Name name)) {
						return;
					}
					int sourceOffset= name.getStartPosition() - prefix.length();
					if (sourceOffset < 0 || sourceOffset >= replacement.length()) {
						return;
					}
					for (String fqn : addImports) {
						if (name.getFullyQualifiedName().startsWith(fqn + ".") //$NON-NLS-1$
								|| name.getFullyQualifiedName().equals(fqn)) {
							String current= replacements.get(sourceOffset);
							if (current == null || fqn.length() > current.length()) {
								replacements.put(sourceOffset, fqn);
							}
						}
					}
				}
			});
			if (replacements.isEmpty()) {
				return replacement;
			}
			List<Map.Entry<Integer, String>> edits= new java.util.ArrayList<>(replacements.entrySet());
			edits.sort((first, second) -> Integer.compare(second.getKey(), first.getKey()));
			StringBuilder shortened= new StringBuilder(replacement);
			for (Map.Entry<Integer, String> edit : edits) {
				int start= edit.getKey();
				String fqn= edit.getValue();
				if (start + fqn.length() <= shortened.length()
						&& shortened.substring(start, start + fqn.length()).equals(fqn)) {
					shortened.replace(start, start + fqn.length(),
							fqn.substring(fqn.lastIndexOf('.') + 1));
				}
			}
			return shortened.toString();
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
		 * Extracts the embedded fix function name from a replacement text ({@code <?fixName?>}).
		 *
		 * <p>A valid embedded fix reference has the form {@code <?identifier?>} where
		 * the identifier has no leading/trailing whitespace and is a valid Java identifier.</p>
		 *
		 * @return the fix function name, or {@code null} if the replacement is not a valid fix reference
		 */
		private static String extractEmbeddedFixName(String replacement) {
			if (replacement == null || !replacement.startsWith("<?") || !replacement.endsWith("?>")) { //$NON-NLS-1$ //$NON-NLS-2$
				return null;
			}
			String inner = replacement.substring(2, replacement.length() - 2);
			String trimmed = inner.trim();
			if (trimmed.isEmpty() || !inner.equals(trimmed)) {
				return null;
			}
			for (int i = 0; i < trimmed.length(); i++) {
				char ch = trimmed.charAt(i);
				if (i == 0) {
					if (!Character.isJavaIdentifierStart(ch)) {
						return null;
					}
				} else if (!Character.isJavaIdentifierPart(ch)) {
					return null;
				}
			}
			return trimmed;
		}

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
		 * removal, registers the replacement before simplifying the proven obsolete handler.
		 * The shared helper retains pending statement edits during unwrapping.
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
			if (!(tryParent instanceof Block)) {
				return false;
			}
			if (!ExceptionCleanupHelper.canRemoveException(tryStatement, matchedNode,
					typeChange.exceptionFQN(), rewrite)) {
				return false;
			}
			try {
				ASTNodes.replaceAndRemoveNLS(rewrite, matchedNode, copy, group, cuRewrite);
			} catch (CoreException exception) {
				rewrite.replace(matchedNode, copy, group);
			}
			ExceptionCleanupHelper.removeCheckedException(matchedNode, typeChange.exceptionFQN(),
					typeChange.exceptionSimpleName(), group, rewrite, cuRewrite.getImportRemover());
			return true;
		}
	}
}
