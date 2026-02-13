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
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.*;

/*-
 * #%L
 * Sandbox junit cleanup
 * %%
 * Copyright (C) 2026 hammer
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.util.AnnotationUtils;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractTool;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;

/**
 * Plugin to migrate JUnit 4 @RunWith(Categories.class) to JUnit 5 @Suite with @IncludeTags/@ExcludeTags.
 */
public class RunWithCategoriesJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	private static class CategoriesData {
		Annotation runWithAnnotation;
		TypeDeclaration typeDeclaration;
		List<Annotation> includeCategories = new ArrayList<>();
		List<Annotation> excludeCategories = new ArrayList<>();
		Annotation suiteClasses;
	}

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataHolder= ReferenceHolder.createIndexed();
		
		// Find @RunWith(Categories.class) annotations
		HelperVisitorFactory.forAnnotation(ORG_JUNIT_RUNWITH)
			.in(compilationUnit)
			.excluding(nodesprocessed)
			.processEach(dataHolder, (visited, aholder) -> {
				if (visited instanceof SingleMemberAnnotation) {
					return processFoundNode(fixcore, operations, (Annotation) visited, aholder, nodesprocessed);
				}
				return true;
			});
	}

	private boolean processFoundNode(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Annotation node,
			ReferenceHolder<Integer, JunitHolder> dataHolder, Set<ASTNode> nodesprocessed) {
		
		if (!(node instanceof SingleMemberAnnotation mynode)) {
			return true;
		}
		
		Expression value= mynode.getValue();
		if (!(value instanceof TypeLiteral myvalue)) {
			return true;
		}
		
		// Check if it's Categories.class
		String runnerQualifiedName = getRunnerQualifiedName(myvalue);
		
		// Only handle Categories runner
		if (!ORG_JUNIT_EXPERIMENTAL_CATEGORIES_CATEGORIES.equals(runnerQualifiedName)) {
			return true;
		}
		
		// Find the enclosing TypeDeclaration
		TypeDeclaration typeDecl = null;
		ASTNode parent = node.getParent();
		while (parent != null) {
			if (parent instanceof TypeDeclaration) {
				typeDecl = (TypeDeclaration) parent;
				break;
			}
			parent = parent.getParent();
		}
		
		if (typeDecl == null) {
			return true;
		}
		
		// Find @IncludeCategory, @ExcludeCategory, and @SuiteClasses annotations
		CategoriesData categoriesData = new CategoriesData();
		categoriesData.runWithAnnotation = node;
		categoriesData.typeDeclaration = typeDecl;
		findCategoryAnnotations(typeDecl, categoriesData);
		
		// Mark for transformation
		nodesprocessed.add(node);
		for (Annotation annotation : categoriesData.includeCategories) {
			nodesprocessed.add(annotation);
		}
		for (Annotation annotation : categoriesData.excludeCategories) {
			nodesprocessed.add(annotation);
		}
		if (categoriesData.suiteClasses != null) {
			nodesprocessed.add(categoriesData.suiteClasses);
		}
		
		JunitHolder mh= new JunitHolder();
		mh.setMinv(node);
		mh.setMinvname(node.getTypeName().getFullyQualifiedName());
		mh.setValue(ORG_JUNIT_EXPERIMENTAL_CATEGORIES_CATEGORIES);
		mh.setAdditionalInfo(categoriesData);
		dataHolder.put(dataHolder.size(), mh);
		operations.add(fixcore.rewrite(dataHolder));
		
		return true;
	}

	private String getRunnerQualifiedName(TypeLiteral myvalue) {
		ITypeBinding classBinding= myvalue.resolveTypeBinding();
		String runnerQualifiedName = null;
		
		if (classBinding != null) {
			Type type = myvalue.getType();
			if (type != null) {
				ITypeBinding typeBinding = type.resolveBinding();
				if (typeBinding != null) {
					runnerQualifiedName = typeBinding.getQualifiedName();
				}
			}
		}
		
		// Fallback to AST name if binding resolution failed
		if (runnerQualifiedName == null || runnerQualifiedName.isEmpty()) {
			Type runnerType = myvalue.getType();
			if (runnerType != null) {
				String typeName = runnerType.toString();
				if ("Categories".equals(typeName)) {
					runnerQualifiedName = ORG_JUNIT_EXPERIMENTAL_CATEGORIES_CATEGORIES;
				}
			}
		}
		
		return runnerQualifiedName;
	}

	private void findCategoryAnnotations(TypeDeclaration typeDecl, CategoriesData data) {
		List<?> modifiers = typeDecl.modifiers();
		for (Object modifier : modifiers) {
			if (modifier instanceof Annotation annotation) {
				String annotationName = annotation.getTypeName().getFullyQualifiedName();
				
				// Check for @IncludeCategory
				if ("IncludeCategory".equals(annotationName) || 
						ORG_JUNIT_EXPERIMENTAL_CATEGORIES_INCLUDE_CATEGORY.equals(annotationName) ||
						"Categories.IncludeCategory".equals(annotationName)) {
					data.includeCategories.add(annotation);
				}
				// Check for @ExcludeCategory
				else if ("ExcludeCategory".equals(annotationName) || 
						ORG_JUNIT_EXPERIMENTAL_CATEGORIES_EXCLUDE_CATEGORY.equals(annotationName) ||
						"Categories.ExcludeCategory".equals(annotationName)) {
					data.excludeCategories.add(annotation);
				}
				// Check for @SuiteClasses
				else if ("SuiteClasses".equals(annotationName) || 
						ORG_JUNIT_SUITE_SUITECLASSES.equals(annotationName) ||
						"Suite.SuiteClasses".equals(annotationName)) {
					data.suiteClasses = annotation;
				}
			}
		}
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		CategoriesData categoriesData = (CategoriesData) junitHolder.getAdditionalInfo();
		TypeDeclaration typeDecl = categoriesData.typeDeclaration;
		ListRewrite modifiersRewrite = rewriter.getListRewrite(typeDecl, TypeDeclaration.MODIFIERS2_PROPERTY);
		
		// Replace @RunWith(Categories.class) with @Suite
		MarkerAnnotation suiteAnnotation= AnnotationUtils.createMarkerAnnotation(ast, ANNOTATION_SUITE);
		modifiersRewrite.replace(categoriesData.runWithAnnotation, suiteAnnotation, group);
		
		// Transform @IncludeCategory annotations
		for (Annotation includeCategory : categoriesData.includeCategories) {
			SingleMemberAnnotation includeTagsAnnotation = createIncludeTagsAnnotation(ast, includeCategory);
			modifiersRewrite.replace(includeCategory, includeTagsAnnotation, group);
		}
		
		// Transform @ExcludeCategory annotations
		for (Annotation excludeCategory : categoriesData.excludeCategories) {
			SingleMemberAnnotation excludeTagsAnnotation = createExcludeTagsAnnotation(ast, excludeCategory);
			modifiersRewrite.replace(excludeCategory, excludeTagsAnnotation, group);
		}
		
		// Transform @SuiteClasses to @SelectClasses
		if (categoriesData.suiteClasses != null && categoriesData.suiteClasses instanceof SingleMemberAnnotation suiteClassesAnnotation) {
			SingleMemberAnnotation selectClassesAnnotation = ast.newSingleMemberAnnotation();
			selectClassesAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_SELECT_CLASSES));
			selectClassesAnnotation.setValue(ASTNodes.createMoveTarget(rewriter, suiteClassesAnnotation.getValue()));
			modifiersRewrite.replace(categoriesData.suiteClasses, selectClassesAnnotation, group);
		}
		
		// Update imports
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

	private SingleMemberAnnotation createIncludeTagsAnnotation(AST ast, Annotation includeCategory) {
		SingleMemberAnnotation includeTagsAnnotation = ast.newSingleMemberAnnotation();
		includeTagsAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_INCLUDE_TAGS));
		
		// Extract category class name and convert to string tag
		if (includeCategory instanceof SingleMemberAnnotation categoryAnnotation) {
			Expression value = categoryAnnotation.getValue();
			String tagName = extractTagNameFromValue(value);
			StringLiteral tagLiteral = ast.newStringLiteral();
			tagLiteral.setLiteralValue(tagName);
			includeTagsAnnotation.setValue(tagLiteral);
		}
		
		return includeTagsAnnotation;
	}

	private SingleMemberAnnotation createExcludeTagsAnnotation(AST ast, Annotation excludeCategory) {
		SingleMemberAnnotation excludeTagsAnnotation = ast.newSingleMemberAnnotation();
		excludeTagsAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_EXCLUDE_TAGS));
		
		// Extract category class name and convert to string tag
		if (excludeCategory instanceof SingleMemberAnnotation categoryAnnotation) {
			Expression value = categoryAnnotation.getValue();
			String tagName = extractTagNameFromValue(value);
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
		return "Unknown";
	}
	
	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					@Suite
					@IncludeTags("FastTests")
					@ExcludeTags("SlowTests")
					@SelectClasses({TestA.class, TestB.class})
					public class FastTestSuite {
					}
					"""; //$NON-NLS-1$
		}
		return """
				@RunWith(Categories.class)
				@IncludeCategory(FastTests.class)
				@ExcludeCategory(SlowTests.class)
				@SuiteClasses({TestA.class, TestB.class})
				public class FastTestSuite {
				}
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "RunWithCategories"; //$NON-NLS-1$
	}
}
