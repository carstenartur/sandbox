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

import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.util.AnnotationUtils;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.HelperVisitorFactory;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractTool;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;

/**
 * Plugin to migrate JUnit 4 @RunWith(Enclosed.class) to JUnit 5 @Nested classes.
 */
public class RunWithEnclosedJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataHolder= ReferenceHolder.createIndexed();
		
		// Find @RunWith annotations
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
		
		// Check if it's Enclosed.class
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
				if ("Enclosed".equals(typeName)) {
					runnerQualifiedName = ORG_JUNIT_EXPERIMENTAL_RUNNERS_ENCLOSED;
				}
			}
		}
		
		// Only handle Enclosed runner
		if (!ORG_JUNIT_EXPERIMENTAL_RUNNERS_ENCLOSED.equals(runnerQualifiedName)) {
			return true;
		}
		
		// Found @RunWith(Enclosed.class), mark it for transformation
		nodesprocessed.add(node);
		JunitHolder mh= new JunitHolder();
		mh.setMinv(node);
		mh.setMinvname(node.getTypeName().getFullyQualifiedName());
		mh.setValue(ORG_JUNIT_EXPERIMENTAL_RUNNERS_ENCLOSED);
		dataHolder.put(dataHolder.size(), mh);
		operations.add(fixcore.rewrite(dataHolder));
		
		return true;
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		Annotation runWithAnnotation= junitHolder.getAnnotation();
		
		// Remove @RunWith(Enclosed.class) annotation
		rewriter.remove(runWithAnnotation, group);
		
		// Find the enclosing TypeDeclaration
		TypeDeclaration outerClass = null;
		ASTNode parent = runWithAnnotation.getParent();
		while (parent != null) {
			if (parent instanceof TypeDeclaration) {
				outerClass = (TypeDeclaration) parent;
				break;
			}
			parent = parent.getParent();
		}
		
		if (outerClass == null) {
			return;
		}
		
		// Transform inner static classes to @Nested classes
		TypeDeclaration[] innerTypes = outerClass.getTypes();
		for (TypeDeclaration innerType : innerTypes) {
			// Check if it's a static class and contains test methods
			if (Modifier.isStatic(innerType.getModifiers()) && hasTestMethods(innerType)) {
				// Remove static modifier
				ListRewrite modifiersRewrite = rewriter.getListRewrite(innerType, TypeDeclaration.MODIFIERS2_PROPERTY);
				List<?> modifiers = innerType.modifiers();
				for (Object modifier : modifiers) {
					if (modifier instanceof Modifier mod && mod.isStatic()) {
						modifiersRewrite.remove(mod, group);
					}
					// Optionally remove public modifier
					if (modifier instanceof Modifier mod && mod.isPublic()) {
						modifiersRewrite.remove(mod, group);
					}
				}
				
				// Add @Nested annotation
				MarkerAnnotation nestedAnnotation= AnnotationUtils.createMarkerAnnotation(ast, ANNOTATION_NESTED);
				modifiersRewrite.insertFirst(nestedAnnotation, group);
			}
		}
		
		// Update imports
		importRewriter.addImport(ORG_JUNIT_JUPITER_API_NESTED);
		importRewriter.removeImport(ORG_JUNIT_EXPERIMENTAL_RUNNERS_ENCLOSED);
		importRewriter.removeImport(ORG_JUNIT_RUNWITH);
	}
	
	/**
	 * Check if a type declaration contains methods annotated with @Test.
	 */
	private boolean hasTestMethods(TypeDeclaration typeDecl) {
		for (MethodDeclaration method : typeDecl.getMethods()) {
			List<?> modifiers = method.modifiers();
			for (Object modifier : modifiers) {
				if (modifier instanceof Annotation annotation) {
					String annotationName = annotation.getTypeName().getFullyQualifiedName();
					if ("Test".equals(annotationName) || 
							ORG_JUNIT_TEST.equals(annotationName) || 
							ORG_JUNIT_JUPITER_TEST.equals(annotationName)) {
						return true;
					}
				}
			}
		}
		return false;
	}
	
	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					public class EnclosedTest {
					    @Nested
					    class WhenConditionA {
					        @Test
					        void shouldDoSomething() {
					        }
					    }
					}
					"""; //$NON-NLS-1$
		}
		return """
				@RunWith(Enclosed.class)
				public class EnclosedTest {
				    public static class WhenConditionA {
				        @Test
				        public void shouldDoSomething() {
				        }
				    }
				}
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "RunWithEnclosed"; //$NON-NLS-1$
	}
}
