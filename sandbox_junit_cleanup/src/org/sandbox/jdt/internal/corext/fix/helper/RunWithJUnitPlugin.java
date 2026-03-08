/*******************************************************************************
 * Copyright (c) 2021 Carsten Hammer.
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
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.util.AnnotationUtils;
import org.sandbox.jdt.internal.common.HelperVisitorFactory;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;
import org.sandbox.jdt.internal.corext.fix.helper.lib.TriggerPatternCleanupPlugin;
import org.sandbox.jdt.triggerpattern.api.CleanupPattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;

/**
 * Consolidated plugin to migrate ALL JUnit 4 {@code @RunWith(...)} variants
 * and {@code @Suite.SuiteClasses} to JUnit 5 equivalents.
 *
 * <p>This single plugin handles all runner types via internal dispatch:
 * <ul>
 *   <li>{@code @RunWith(Suite.class)} → {@code @Suite}</li>
 *   <li>{@code @RunWith(Enclosed.class)} → {@code @Nested} inner classes</li>
 *   <li>{@code @RunWith(Theories.class)} → {@code @ParameterizedTest} + {@code @ValueSource}</li>
 *   <li>{@code @RunWith(Categories.class)} → {@code @Suite} + {@code @IncludeTags/@ExcludeTags}</li>
 *   <li>{@code @RunWith(MockitoJUnitRunner.class)} → {@code @ExtendWith(MockitoExtension.class)}</li>
 *   <li>{@code @RunWith(SpringRunner.class)} → {@code @ExtendWith(SpringExtension.class)}</li>
 * </ul>
 *
 * <p>Previously these were four separate plugins (RunWithJUnitPlugin,
 * RunWithEnclosedJUnitPlugin, RunWithTheoriesJUnitPlugin,
 * RunWithCategoriesJUnitPlugin) which all matched on {@code @RunWith} and shared
 * the {@code nodesprocessed} set. This caused interference: when one plugin
 * marked a node as processed but could not handle that runner type, the correct
 * plugin never got a chance to process it. Consolidating into a single plugin
 * eliminates this conflict.</p>
 *
 * <p>This plugin overrides {@code find()} because it needs to search for BOTH
 * {@code @RunWith} and {@code @Suite.SuiteClasses} annotations — the base
 * TriggerPatternCleanupPlugin only searches for the pattern in @CleanupPattern.</p>
 *
 * @since 1.3.0
 */
@CleanupPattern(value = "@RunWith($runner)", kind = PatternKind.ANNOTATION, qualifiedType = ORG_JUNIT_RUNWITH, cleanupId = "cleanup.junit.runwith", description = "Migrate @RunWith to JUnit 5 equivalents", displayName = "JUnit 4 @RunWith → JUnit 5 @ExtendWith/@Suite")
public class RunWithJUnitPlugin extends TriggerPatternCleanupPlugin {

	// ---- Data classes for complex runner transformations ----

	private static class TheoriesData {
		Annotation runWithAnnotation;
		FieldDeclaration dataPointsField;
		MethodDeclaration theoryMethod;
		ArrayInitializer dataPointsArray;
	}

	private static class CategoriesData {
		Annotation runWithAnnotation;
		TypeDeclaration typeDeclaration;
		List<Annotation> includeCategories = new ArrayList<>();
		List<Annotation> excludeCategories = new ArrayList<>();
		Annotation suiteClasses;
	}

	// ---- find() — scan for @RunWith and @Suite.SuiteClasses ----

	/**
	 * Override find() because we need to search for TWO annotation types:
	 * {@code @RunWith} and {@code @Suite.SuiteClasses}.
	 */
	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataHolder = ReferenceHolder.createIndexed();

		// Find @RunWith annotations
		HelperVisitorFactory.forAnnotation(ORG_JUNIT_RUNWITH).in(compilationUnit).excluding(nodesprocessed)
				.processEach(dataHolder, (visited, aholder) -> {
					if (visited instanceof SingleMemberAnnotation) {
						return processFoundNodeRunWith(fixcore, operations, (Annotation) visited, aholder);
					}
					return true;
				});

		// Find @Suite.SuiteClasses annotations
		HelperVisitorFactory.forAnnotation(ORG_JUNIT_SUITE_SUITECLASSES).in(compilationUnit).excluding(nodesprocessed)
				.processEach(dataHolder, (visited, aholder) -> {
					if (visited instanceof SingleMemberAnnotation) {
						return processFoundNodeSuite(fixcore, operations, (Annotation) visited, aholder);
					}
					return true;
				});
	}

	// ---- Runner type resolution ----

	/**
	 * Resolves the fully qualified runner class name from a TypeLiteral
	 * (e.g., {@code Suite.class} → {@code "org.junit.runners.Suite"}).
	 */
	private String resolveRunnerQualifiedName(TypeLiteral myvalue) {
		String runnerQualifiedName = null;

		// Try to get qualified name from binding
		ITypeBinding classBinding = myvalue.resolveTypeBinding();
		if (classBinding != null) {
			Type type = myvalue.getType();
			if (type != null) {
				ITypeBinding typeBinding = type.resolveBinding();
				if (typeBinding != null) {
					runnerQualifiedName = typeBinding.getQualifiedName();
				}
			}
		}

		// If binding resolution failed, try to get fully qualified name from the AST
		if (runnerQualifiedName == null || runnerQualifiedName.isEmpty()) {
			Type runnerType = myvalue.getType();
			if (runnerType != null) {
				String typeName = runnerType.toString();
				// Only use it if it's a fully qualified name (contains a dot)
				if (typeName.contains(".")) { //$NON-NLS-1$
					runnerQualifiedName = typeName;
				}
				// Fallback: resolve well-known JUnit simple names
				else {
					runnerQualifiedName = resolveSimpleName(typeName);
				}
			}
		}

		return runnerQualifiedName;
	}

	/**
	 * Maps well-known JUnit simple class names to their qualified names
	 * when binding resolution fails.
	 */
	private String resolveSimpleName(String typeName) {
		return switch (typeName) {
		case "Suite" -> ORG_JUNIT_SUITE; //$NON-NLS-1$
		case "Enclosed" -> ORG_JUNIT_EXPERIMENTAL_RUNNERS_ENCLOSED; //$NON-NLS-1$
		case "Theories" -> ORG_JUNIT_EXPERIMENTAL_THEORIES_THEORIES; //$NON-NLS-1$
		case "Categories" -> ORG_JUNIT_EXPERIMENTAL_CATEGORIES_CATEGORIES; //$NON-NLS-1$
		default -> null;
		};
	}

	// ---- find() node processing ----

	private boolean processFoundNodeRunWith(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Annotation node,
			ReferenceHolder<Integer, JunitHolder> dataHolder) {
		if (!(node instanceof SingleMemberAnnotation mynode)) {
			return true;
		}
		Expression value = mynode.getValue();
		if (!(value instanceof TypeLiteral myvalue)) {
			return true;
		}

		String runnerQualifiedName = resolveRunnerQualifiedName(myvalue);
		if (runnerQualifiedName == null) {
			return true;
		}

		JunitHolder mh = new JunitHolder();
		mh.setMinv(node);
		mh.setMinvname(node.getTypeName().getFullyQualifiedName());

		// --- Suite ---
		if (ORG_JUNIT_SUITE.equals(runnerQualifiedName)) {
			mh.setValue(ORG_JUNIT_RUNWITH);
			dataHolder.put(dataHolder.size(), mh);
			operations.add(fixcore.rewrite(dataHolder));
			return true;
		}

		// --- Enclosed ---
		if (ORG_JUNIT_EXPERIMENTAL_RUNNERS_ENCLOSED.equals(runnerQualifiedName)) {
			mh.setValue(ORG_JUNIT_EXPERIMENTAL_RUNNERS_ENCLOSED);
			dataHolder.put(dataHolder.size(), mh);
			operations.add(fixcore.rewrite(dataHolder));
			return true;
		}

		// --- Theories ---
		if (ORG_JUNIT_EXPERIMENTAL_THEORIES_THEORIES.equals(runnerQualifiedName)) {
			TheoriesData data = buildTheoriesData(node);
			if (data != null) {
				mh.setValue(ORG_JUNIT_EXPERIMENTAL_THEORIES_THEORIES);
				mh.setAdditionalInfo(data);
				dataHolder.put(dataHolder.size(), mh);
				operations.add(fixcore.rewrite(dataHolder));
			}
			return true;
		}

		// --- Categories ---
		if (ORG_JUNIT_EXPERIMENTAL_CATEGORIES_CATEGORIES.equals(runnerQualifiedName)) {
			CategoriesData data = buildCategoriesData(node);
			if (data != null) {
				mh.setValue(ORG_JUNIT_EXPERIMENTAL_CATEGORIES_CATEGORIES);
				mh.setAdditionalInfo(data);
				dataHolder.put(dataHolder.size(), mh);
				operations.add(fixcore.rewrite(dataHolder));
			}
			return true;
		}

		// --- Mockito ---
		if (ORG_MOCKITO_JUNIT_MOCKITO_JUNIT_RUNNER.equals(runnerQualifiedName)
				|| ORG_MOCKITO_RUNNERS_MOCKITO_JUNIT_RUNNER.equals(runnerQualifiedName)) {
			mh.setValue(ORG_MOCKITO_JUNIT_MOCKITO_JUNIT_RUNNER);
			dataHolder.put(dataHolder.size(), mh);
			operations.add(fixcore.rewrite(dataHolder));
			return true;
		}

		// --- Spring ---
		if (ORG_SPRINGFRAMEWORK_TEST_CONTEXT_JUNIT4_SPRING_RUNNER.equals(runnerQualifiedName)
				|| ORG_SPRINGFRAMEWORK_TEST_CONTEXT_JUNIT4_SPRING_JUNIT4_CLASS_RUNNER.equals(runnerQualifiedName)) {
			mh.setValue(ORG_SPRINGFRAMEWORK_TEST_CONTEXT_JUNIT4_SPRING_RUNNER);
			dataHolder.put(dataHolder.size(), mh);
			operations.add(fixcore.rewrite(dataHolder));
			return true;
		}

		return true;
	}

	private boolean processFoundNodeSuite(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Annotation node,
			ReferenceHolder<Integer, JunitHolder> dataHolder) {
		JunitHolder mh = new JunitHolder();
		mh.setMinv(node);
		mh.setMinvname(node.getTypeName().getFullyQualifiedName());
		mh.setValue(ORG_JUNIT_SUITE_SUITECLASSES);
		dataHolder.put(dataHolder.size(), mh);
		operations.add(fixcore.rewrite(dataHolder));
		return true;
	}

	// ---- Rewrite dispatch ----

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		String runnerValue = junitHolder.getValue();

		if (ORG_JUNIT_SUITE_SUITECLASSES.equals(runnerValue)) {
			rewriteSuiteClasses(group, rewriter, ast, importRewriter, junitHolder);
		} else if (ORG_JUNIT_RUNWITH.equals(runnerValue)) {
			rewriteSuiteRunner(group, rewriter, ast, importRewriter, junitHolder);
		} else if (ORG_JUNIT_EXPERIMENTAL_RUNNERS_ENCLOSED.equals(runnerValue)) {
			rewriteEnclosed(group, rewriter, ast, importRewriter, junitHolder);
		} else if (ORG_JUNIT_EXPERIMENTAL_THEORIES_THEORIES.equals(runnerValue)) {
			rewriteTheories(group, rewriter, ast, importRewriter, junitHolder);
		} else if (ORG_JUNIT_EXPERIMENTAL_CATEGORIES_CATEGORIES.equals(runnerValue)) {
			rewriteCategories(group, rewriter, ast, importRewriter, junitHolder);
		} else if (ORG_MOCKITO_JUNIT_MOCKITO_JUNIT_RUNNER.equals(runnerValue)) {
			rewriteMockito(group, rewriter, ast, importRewriter, junitHolder);
		} else if (ORG_SPRINGFRAMEWORK_TEST_CONTEXT_JUNIT4_SPRING_RUNNER.equals(runnerValue)) {
			rewriteSpring(group, rewriter, ast, importRewriter, junitHolder);
		}
	}

	// ---- @Suite.SuiteClasses → @SelectClasses ----

	private void rewriteSuiteClasses(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, JunitHolder junitHolder) {
		Annotation minv = junitHolder.getAnnotation();
		SingleMemberAnnotation mynode = (SingleMemberAnnotation) minv;
		SingleMemberAnnotation newAnnotation = ast.newSingleMemberAnnotation();
		newAnnotation.setValue(ASTNodes.createMoveTarget(rewriter, mynode.getValue()));
		newAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_SELECT_CLASSES));
		importRewriter.addImport(ORG_JUNIT_PLATFORM_SUITE_API_SELECT_CLASSES);
		importRewriter.removeImport(ORG_JUNIT_SUITE_SUITECLASSES);
		importRewriter.removeImport(ORG_JUNIT_SUITE);
		ASTNodes.replaceButKeepComment(rewriter, minv, newAnnotation, group);
		importRewriter.removeImport(ORG_JUNIT_RUNWITH);
	}

	// ---- @RunWith(Suite.class) → @Suite ----

	private void rewriteSuiteRunner(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, JunitHolder junitHolder) {
		Annotation minv = junitHolder.getAnnotation();
		Annotation newAnnotation = AnnotationUtils.createMarkerAnnotation(ast, ANNOTATION_SUITE);
		importRewriter.addImport(ORG_JUNIT_JUPITER_SUITE);
		importRewriter.removeImport(ORG_JUNIT_SUITE);
		importRewriter.removeImport(ORG_JUNIT_RUNWITH);
		ASTNodes.replaceButKeepComment(rewriter, minv, newAnnotation, group);
		importRewriter.removeImport(ORG_JUNIT_RUNWITH);
	}

	// ---- @RunWith(Enclosed.class) → @Nested inner classes ----

	private void rewriteEnclosed(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, JunitHolder junitHolder) {
		Annotation runWithAnnotation = junitHolder.getAnnotation();

		// Remove @RunWith(Enclosed.class) annotation
		rewriter.remove(runWithAnnotation, group);

		// Find the enclosing TypeDeclaration
		TypeDeclaration outerClass = findEnclosingType(runWithAnnotation);
		if (outerClass == null) {
			return;
		}

		// Transform inner static classes to @Nested classes
		for (TypeDeclaration innerType : outerClass.getTypes()) {
			if (Modifier.isStatic(innerType.getModifiers()) && hasTestMethods(innerType)) {
				ListRewrite modifiersRewrite = rewriter.getListRewrite(innerType,
						TypeDeclaration.MODIFIERS2_PROPERTY);
				for (Object modifier : innerType.modifiers()) {
					if (modifier instanceof Modifier mod && mod.isStatic()) {
						modifiersRewrite.remove(mod, group);
					}
					if (modifier instanceof Modifier mod && mod.isPublic()) {
						modifiersRewrite.remove(mod, group);
					}
				}
				MarkerAnnotation nestedAnnotation = AnnotationUtils.createMarkerAnnotation(ast, ANNOTATION_NESTED);
				modifiersRewrite.insertFirst(nestedAnnotation, group);
			}
		}

		importRewriter.addImport(ORG_JUNIT_JUPITER_API_NESTED);
		importRewriter.removeImport(ORG_JUNIT_EXPERIMENTAL_RUNNERS_ENCLOSED);
		importRewriter.removeImport(ORG_JUNIT_RUNWITH);
	}

	// ---- @RunWith(Theories.class) → @ParameterizedTest + @ValueSource ----

	private TheoriesData buildTheoriesData(Annotation node) {
		TypeDeclaration typeDecl = findEnclosingType(node);
		if (typeDecl == null) {
			return null;
		}
		TheoriesData data = new TheoriesData();
		data.runWithAnnotation = node;
		findTheoriesComponents(typeDecl, data);
		if (data.dataPointsField == null || data.theoryMethod == null) {
			return null;
		}
		return data;
	}

	private void rewriteTheories(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, JunitHolder junitHolder) {
		TheoriesData theoriesData = (TheoriesData) junitHolder.getAdditionalInfo();

		// Remove @RunWith(Theories.class)
		rewriter.remove(theoriesData.runWithAnnotation, group);
		// Remove @DataPoints field
		rewriter.remove(theoriesData.dataPointsField, group);
		// Transform @Theory method
		transformTheoryMethod(theoriesData, rewriter, ast, group);

		importRewriter.addImport(ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_TEST);
		importRewriter.addImport(ORG_JUNIT_JUPITER_PARAMS_PROVIDER_VALUE_SOURCE);
		importRewriter.removeImport(ORG_JUNIT_EXPERIMENTAL_THEORIES_THEORIES);
		importRewriter.removeImport(ORG_JUNIT_EXPERIMENTAL_THEORIES_THEORY);
		importRewriter.removeImport(ORG_JUNIT_EXPERIMENTAL_THEORIES_DATAPOINTS);
		importRewriter.removeImport(ORG_JUNIT_RUNWITH);
	}

	// ---- @RunWith(Categories.class) → @Suite + @IncludeTags/@ExcludeTags ----

	private CategoriesData buildCategoriesData(Annotation node) {
		TypeDeclaration typeDecl = findEnclosingType(node);
		if (typeDecl == null) {
			return null;
		}
		CategoriesData data = new CategoriesData();
		data.runWithAnnotation = node;
		data.typeDeclaration = typeDecl;
		findCategoryAnnotations(typeDecl, data);
		return data;
	}

	private void rewriteCategories(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, JunitHolder junitHolder) {
		CategoriesData categoriesData = (CategoriesData) junitHolder.getAdditionalInfo();
		TypeDeclaration typeDecl = categoriesData.typeDeclaration;
		ListRewrite modifiersRewrite = rewriter.getListRewrite(typeDecl, TypeDeclaration.MODIFIERS2_PROPERTY);

		// Replace @RunWith(Categories.class) with @Suite
		MarkerAnnotation suiteAnnotation = AnnotationUtils.createMarkerAnnotation(ast, ANNOTATION_SUITE);
		modifiersRewrite.replace(categoriesData.runWithAnnotation, suiteAnnotation, group);

		for (Annotation includeCategory : categoriesData.includeCategories) {
			modifiersRewrite.replace(includeCategory, createIncludeTagsAnnotation(ast, includeCategory), group);
		}
		for (Annotation excludeCategory : categoriesData.excludeCategories) {
			modifiersRewrite.replace(excludeCategory, createExcludeTagsAnnotation(ast, excludeCategory), group);
		}

		if (categoriesData.suiteClasses instanceof SingleMemberAnnotation suiteClassesAnnotation) {
			SingleMemberAnnotation selectClassesAnnotation = ast.newSingleMemberAnnotation();
			selectClassesAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_SELECT_CLASSES));
			selectClassesAnnotation.setValue(ASTNodes.createMoveTarget(rewriter, suiteClassesAnnotation.getValue()));
			modifiersRewrite.replace(categoriesData.suiteClasses, selectClassesAnnotation, group);
		}

		importRewriter.addImport(ORG_JUNIT_JUPITER_SUITE);
		importRewriter.addImport(ORG_JUNIT_PLATFORM_SUITE_API_INCLUDE_TAGS);
		importRewriter.addImport(ORG_JUNIT_PLATFORM_SUITE_API_EXCLUDE_TAGS);
		importRewriter.addImport(ORG_JUNIT_PLATFORM_SUITE_API_SELECT_CLASSES);
		importRewriter.removeImport(ORG_JUNIT_EXPERIMENTAL_CATEGORIES_CATEGORIES);
		importRewriter.removeImport(ORG_JUNIT_EXPERIMENTAL_CATEGORIES_INCLUDE_CATEGORY);
		importRewriter.removeImport(ORG_JUNIT_EXPERIMENTAL_CATEGORIES_EXCLUDE_CATEGORY);
		importRewriter.removeImport(ORG_JUNIT_SUITE_SUITECLASSES);
		importRewriter.removeImport(ORG_JUNIT_RUNWITH);
	}

	// ---- @RunWith(MockitoJUnitRunner.class) → @ExtendWith(MockitoExtension.class) ----

	private void rewriteMockito(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, JunitHolder junitHolder) {
		Annotation minv = junitHolder.getAnnotation();
		SingleMemberAnnotation extendWithAnnotation = ast.newSingleMemberAnnotation();
		extendWithAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_EXTEND_WITH));
		TypeLiteral typeLiteral = ast.newTypeLiteral();
		typeLiteral.setType(ast.newSimpleType(ast.newName(MOCKITO_EXTENSION)));
		extendWithAnnotation.setValue(typeLiteral);
		importRewriter.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_EXTEND_WITH);
		importRewriter.addImport(ORG_MOCKITO_JUNIT_JUPITER_MOCKITO_EXTENSION);
		importRewriter.removeImport(ORG_MOCKITO_JUNIT_MOCKITO_JUNIT_RUNNER);
		importRewriter.removeImport(ORG_MOCKITO_RUNNERS_MOCKITO_JUNIT_RUNNER);
		ASTNodes.replaceButKeepComment(rewriter, minv, extendWithAnnotation, group);
		importRewriter.removeImport(ORG_JUNIT_RUNWITH);
	}

	// ---- @RunWith(SpringRunner.class) → @ExtendWith(SpringExtension.class) ----

	private void rewriteSpring(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, JunitHolder junitHolder) {
		Annotation minv = junitHolder.getAnnotation();
		SingleMemberAnnotation extendWithAnnotation = ast.newSingleMemberAnnotation();
		extendWithAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_EXTEND_WITH));
		TypeLiteral typeLiteral = ast.newTypeLiteral();
		typeLiteral.setType(ast.newSimpleType(ast.newName(SPRING_EXTENSION)));
		extendWithAnnotation.setValue(typeLiteral);
		importRewriter.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_EXTEND_WITH);
		importRewriter.addImport(ORG_SPRINGFRAMEWORK_TEST_CONTEXT_JUNIT_JUPITER_SPRING_EXTENSION);
		importRewriter.removeImport(ORG_SPRINGFRAMEWORK_TEST_CONTEXT_JUNIT4_SPRING_RUNNER);
		importRewriter.removeImport(ORG_SPRINGFRAMEWORK_TEST_CONTEXT_JUNIT4_SPRING_JUNIT4_CLASS_RUNNER);
		ASTNodes.replaceButKeepComment(rewriter, minv, extendWithAnnotation, group);
		importRewriter.removeImport(ORG_JUNIT_RUNWITH);
	}

	// ---- Helper methods (from former Enclosed, Theories, Categories plugins) ----

	private TypeDeclaration findEnclosingType(ASTNode node) {
		ASTNode parent = node.getParent();
		while (parent != null) {
			if (parent instanceof TypeDeclaration td) {
				return td;
			}
			parent = parent.getParent();
		}
		return null;
	}

	private boolean hasTestMethods(TypeDeclaration typeDecl) {
		for (MethodDeclaration method : typeDecl.getMethods()) {
			for (Object modifier : method.modifiers()) {
				if (modifier instanceof Annotation annotation) {
					String annotationName = annotation.getTypeName().getFullyQualifiedName();
					if ("Test".equals(annotationName) || ORG_JUNIT_TEST.equals(annotationName) //$NON-NLS-1$
							|| ORG_JUNIT_JUPITER_TEST.equals(annotationName)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private void findTheoriesComponents(TypeDeclaration typeDecl, TheoriesData data) {
		for (FieldDeclaration field : typeDecl.getFields()) {
			for (Object modifier : field.modifiers()) {
				if (modifier instanceof Annotation annotation) {
					String annotationName = annotation.getTypeName().getFullyQualifiedName();
					if ("DataPoints".equals(annotationName) //$NON-NLS-1$
							|| ORG_JUNIT_EXPERIMENTAL_THEORIES_DATAPOINTS.equals(annotationName)) {
						data.dataPointsField = field;
						List<?> fragments = field.fragments();
						if (!fragments.isEmpty()
								&& fragments.get(0) instanceof VariableDeclarationFragment fragment) {
							Expression initializer = fragment.getInitializer();
							if (initializer instanceof ArrayInitializer arrayInit) {
								data.dataPointsArray = arrayInit;
							}
						}
						break;
					}
				}
			}
		}

		for (MethodDeclaration method : typeDecl.getMethods()) {
			for (Object modifier : method.modifiers()) {
				if (modifier instanceof Annotation annotation) {
					String annotationName = annotation.getTypeName().getFullyQualifiedName();
					if ("Theory".equals(annotationName) //$NON-NLS-1$
							|| ORG_JUNIT_EXPERIMENTAL_THEORIES_THEORY.equals(annotationName)) {
						data.theoryMethod = method;
						break;
					}
				}
			}
		}
	}

	private void transformTheoryMethod(TheoriesData data, ASTRewrite rewriter, AST ast, TextEditGroup group) {
		MethodDeclaration theoryMethod = data.theoryMethod;
		ListRewrite modifiersRewrite = rewriter.getListRewrite(theoryMethod, MethodDeclaration.MODIFIERS2_PROPERTY);

		for (Object modifier : theoryMethod.modifiers()) {
			if (modifier instanceof Annotation annotation) {
				String annotationName = annotation.getTypeName().getFullyQualifiedName();
				if ("Theory".equals(annotationName) //$NON-NLS-1$
						|| ORG_JUNIT_EXPERIMENTAL_THEORIES_THEORY.equals(annotationName)) {
					Annotation parameterizedTest = AnnotationUtils.createMarkerAnnotation(ast,
							ANNOTATION_PARAMETERIZED_TEST);
					NormalAnnotation valueSource = createValueSourceAnnotation(ast, data);
					modifiersRewrite.replace(annotation, parameterizedTest, group);
					modifiersRewrite.insertAfter(valueSource, parameterizedTest, group);
					break;
				}
			}
		}
	}

	private NormalAnnotation createValueSourceAnnotation(AST ast, TheoriesData data) {
		NormalAnnotation valueSource = ast.newNormalAnnotation();
		valueSource.setTypeName(ast.newSimpleName(ANNOTATION_VALUE_SOURCE));

		String memberName = determineValueSourceMember(data.dataPointsField);
		MemberValuePair valuePair = ast.newMemberValuePair();
		valuePair.setName(ast.newSimpleName(memberName));

		if (data.dataPointsArray != null) {
			ArrayInitializer newArray = (ArrayInitializer) ASTNode.copySubtree(ast, data.dataPointsArray);
			valuePair.setValue(newArray);
		}

		valueSource.values().add(valuePair);
		return valueSource;
	}

	private String determineValueSourceMember(FieldDeclaration field) {
		if (field == null) {
			return "ints"; //$NON-NLS-1$
		}
		Type fieldType = field.getType();
		if (fieldType == null) {
			return "ints"; //$NON-NLS-1$
		}
		String typeName = fieldType.toString();
		if (typeName.endsWith("[]")) { //$NON-NLS-1$
			String baseType = typeName.substring(0, typeName.length() - 2);
			return switch (baseType) {
			case "int" -> "ints"; //$NON-NLS-1$ //$NON-NLS-2$
			case "String" -> "strings"; //$NON-NLS-1$ //$NON-NLS-2$
			case "double" -> "doubles"; //$NON-NLS-1$ //$NON-NLS-2$
			case "long" -> "longs"; //$NON-NLS-1$ //$NON-NLS-2$
			case "short" -> "shorts"; //$NON-NLS-1$ //$NON-NLS-2$
			case "byte" -> "bytes"; //$NON-NLS-1$ //$NON-NLS-2$
			case "float" -> "floats"; //$NON-NLS-1$ //$NON-NLS-2$
			case "char" -> "chars"; //$NON-NLS-1$ //$NON-NLS-2$
			case "boolean" -> "booleans"; //$NON-NLS-1$ //$NON-NLS-2$
			case "Class" -> "classes"; //$NON-NLS-1$ //$NON-NLS-2$
			default -> "ints"; //$NON-NLS-1$
			};
		}
		return "ints"; //$NON-NLS-1$
	}

	private void findCategoryAnnotations(TypeDeclaration typeDecl, CategoriesData data) {
		for (Object modifier : typeDecl.modifiers()) {
			if (modifier instanceof Annotation annotation) {
				String annotationName = annotation.getTypeName().getFullyQualifiedName();
				if ("IncludeCategory".equals(annotationName) //$NON-NLS-1$
						|| ORG_JUNIT_EXPERIMENTAL_CATEGORIES_INCLUDE_CATEGORY.equals(annotationName)
						|| "Categories.IncludeCategory".equals(annotationName)) { //$NON-NLS-1$
					data.includeCategories.add(annotation);
				} else if ("ExcludeCategory".equals(annotationName) //$NON-NLS-1$
						|| ORG_JUNIT_EXPERIMENTAL_CATEGORIES_EXCLUDE_CATEGORY.equals(annotationName)
						|| "Categories.ExcludeCategory".equals(annotationName)) { //$NON-NLS-1$
					data.excludeCategories.add(annotation);
				} else if ("SuiteClasses".equals(annotationName) //$NON-NLS-1$
						|| ORG_JUNIT_SUITE_SUITECLASSES.equals(annotationName)
						|| "Suite.SuiteClasses".equals(annotationName)) { //$NON-NLS-1$
					data.suiteClasses = annotation;
				}
			}
		}
	}

	private SingleMemberAnnotation createIncludeTagsAnnotation(AST ast, Annotation includeCategory) {
		SingleMemberAnnotation includeTagsAnnotation = ast.newSingleMemberAnnotation();
		includeTagsAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_INCLUDE_TAGS));
		if (includeCategory instanceof SingleMemberAnnotation categoryAnnotation) {
			String tagName = extractTagNameFromValue(categoryAnnotation.getValue());
			StringLiteral tagLiteral = ast.newStringLiteral();
			tagLiteral.setLiteralValue(tagName);
			includeTagsAnnotation.setValue(tagLiteral);
		}
		return includeTagsAnnotation;
	}

	private SingleMemberAnnotation createExcludeTagsAnnotation(AST ast, Annotation excludeCategory) {
		SingleMemberAnnotation excludeTagsAnnotation = ast.newSingleMemberAnnotation();
		excludeTagsAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_EXCLUDE_TAGS));
		if (excludeCategory instanceof SingleMemberAnnotation categoryAnnotation) {
			String tagName = extractTagNameFromValue(categoryAnnotation.getValue());
			StringLiteral tagLiteral = ast.newStringLiteral();
			tagLiteral.setLiteralValue(tagName);
			excludeTagsAnnotation.setValue(tagLiteral);
		}
		return excludeTagsAnnotation;
	}

	private String extractTagNameFromValue(Expression value) {
		if (value instanceof TypeLiteral typeLiteral) {
			Type type = typeLiteral.getType();
			if (type != null) {
				return type.toString();
			}
		}
		return "Unknown"; //$NON-NLS-1$
	}

	// ---- Preview and identity ----

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					@Suite
					@SelectClasses({
						MyTest.class
					})
					"""; //$NON-NLS-1$
		}
		return """
				@RunWith(Suite.class)
				@Suite.SuiteClasses({
					MyTest.class
				})
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "RunWith"; //$NON-NLS-1$
	}
}
