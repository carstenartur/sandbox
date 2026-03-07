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

import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.fix.helper.lib.ExternalResourceRefactorer;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;
import org.sandbox.jdt.internal.corext.fix.helper.lib.TriggerPatternCleanupPlugin;
import org.sandbox.jdt.triggerpattern.api.CleanupPattern;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;

/**
 * Plugin to migrate JUnit 4 ExternalResource rules to JUnit 5 extensions.
 *
 * @since 1.3.0
 */
@CleanupPattern(value = "@Rule public $type $name", kind = PatternKind.FIELD, qualifiedType = ORG_JUNIT_RULES_EXTERNAL_RESOURCE, cleanupId = "cleanup.junit.ruleexternalresource", description = "Migrate @Rule ExternalResource to JUnit 5 extension", displayName = "JUnit 4 @Rule ExternalResource \u2192 JUnit 5 Extension")
public class RuleExternalResourceJUnitPlugin extends TriggerPatternCleanupPlugin {

	/**
	 * Override getPatterns() to match both @Rule and @ClassRule variants.
	 */
	@Override
	protected List<Pattern> getPatterns() {
		return List.of(
				new Pattern("@Rule public $type $name", PatternKind.FIELD, null, null, ORG_JUNIT_RULES_EXTERNAL_RESOURCE, null, null),
				new Pattern("@ClassRule public $type $name", PatternKind.FIELD, null, null, ORG_JUNIT_RULES_EXTERNAL_RESOURCE, null, null),
				new Pattern("@ClassRule public static $type $name", PatternKind.FIELD, null, null, ORG_JUNIT_RULES_EXTERNAL_RESOURCE, null, null));
	}

	@Override
	protected JunitHolder createHolder(Match match) {
		FieldDeclaration fieldDecl = (FieldDeclaration) match.getMatchedNode();
		VariableDeclarationFragment fragment = (VariableDeclarationFragment) fieldDecl.fragments().get(0);
		if (fragment.resolveBinding() == null) {
			return null;
		}
		ITypeBinding binding = fragment.resolveBinding().getType();
		if (binding == null) {
			return null;
		}
		// Exclude specific rule types handled by dedicated plugins
		String qualifiedName = binding.getQualifiedName();
		if (ORG_JUNIT_RULES_TEST_NAME.equals(qualifiedName)
				|| ORG_JUNIT_RULES_TEMPORARY_FOLDER.equals(qualifiedName)) {
			return null;
		}
		JunitHolder holder = new JunitHolder();
		holder.setMinv(fieldDecl);
		return holder;
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		FieldDeclaration fieldDeclaration = junitHolder.getFieldDeclaration();
		boolean fieldStatic = isFieldAnnotatedWith(fieldDeclaration, ORG_JUNIT_CLASS_RULE);
		CompilationUnit cu = (CompilationUnit) fieldDeclaration.getRoot();

		ASTNode node2 = ExternalResourceRefactorer.getTypeDefinitionForField(fieldDeclaration, cu);

		if (node2 instanceof TypeDeclaration) {
			ExternalResourceRefactorer.modifyExternalResourceClass((TypeDeclaration) node2, fieldDeclaration,
					fieldStatic, rewriter, ast, group, importRewriter);
		} else if (node2 instanceof AnonymousClassDeclaration typeNode) {
			ExternalResourceRefactorer.refactorAnonymousClassToImplementCallbacks(typeNode, fieldDeclaration,
					fieldStatic, rewriter, ast, group, importRewriter);
		}
		// If no matching type definition found, no action needed
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					ExtendWith(MyTest.MyExternalResource.class)
					public class MyTest {

						final class MyExternalResource implements BeforeEachCallback, AfterEachCallback {
							@Override
							protected void beforeEach() throws Throwable {
							}

							@Override
							protected void afterEach() {
							}
						}
					"""; //$NON-NLS-1$
		}
		return """
				public class MyTest {

					final class MyExternalResource extends ExternalResource {
						@Override
						protected void before() throws Throwable {
						}

						@Override
						protected void after() {
						}
					}

					@Rule
					public ExternalResource er= new MyExternalResource();
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "RuleExternalResource"; //$NON-NLS-1$
	}
}
