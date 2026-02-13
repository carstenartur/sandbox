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
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.util.AnnotationUtils;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractTool;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;

/**
 * Plugin to migrate JUnit 4 @RunWith(Theories.class) to JUnit 5 @ParameterizedTest.
 */
public class RunWithTheoriesJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	private static class TheoriesData {
		Annotation runWithAnnotation;
		FieldDeclaration dataPointsField;
		MethodDeclaration theoryMethod;
		ArrayInitializer dataPointsArray;
	}

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataHolder= ReferenceHolder.createIndexed();
		
		// Find @RunWith(Theories.class) annotations
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
		
		// Check if it's Theories.class
		String runnerQualifiedName = getRunnerQualifiedName(myvalue);
		
		// Only handle Theories runner
		if (!ORG_JUNIT_EXPERIMENTAL_THEORIES_THEORIES.equals(runnerQualifiedName)) {
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
		
		// Find @DataPoints field and @Theory methods
		TheoriesData theoriesData = new TheoriesData();
		theoriesData.runWithAnnotation = node;
		findTheoriesComponents(typeDecl, theoriesData);
		
		// Only process if we found both @DataPoints and @Theory
		if (theoriesData.dataPointsField == null || theoriesData.theoryMethod == null) {
			return true;
		}
		
		// Mark for transformation
		nodesprocessed.add(node);
		nodesprocessed.add(theoriesData.dataPointsField);
		nodesprocessed.add(theoriesData.theoryMethod);
		
		JunitHolder mh= new JunitHolder();
		mh.setMinv(node);
		mh.setMinvname(node.getTypeName().getFullyQualifiedName());
		mh.setValue(ORG_JUNIT_EXPERIMENTAL_THEORIES_THEORIES);
		mh.setAdditionalInfo(theoriesData);
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
				if ("Theories".equals(typeName)) {
					runnerQualifiedName = ORG_JUNIT_EXPERIMENTAL_THEORIES_THEORIES;
				}
			}
		}
		
		return runnerQualifiedName;
	}

	private void findTheoriesComponents(TypeDeclaration typeDecl, TheoriesData data) {
		// Find @DataPoints field
		for (FieldDeclaration field : typeDecl.getFields()) {
			List<?> modifiers = field.modifiers();
			for (Object modifier : modifiers) {
				if (modifier instanceof Annotation annotation) {
					String annotationName = annotation.getTypeName().getFullyQualifiedName();
					if ("DataPoints".equals(annotationName) || ORG_JUNIT_EXPERIMENTAL_THEORIES_DATAPOINTS.equals(annotationName)) {
						data.dataPointsField = field;
						// Extract array initializer
						List<?> fragments = field.fragments();
						if (!fragments.isEmpty() && fragments.get(0) instanceof VariableDeclarationFragment fragment) {
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
		
		// Find @Theory method
		for (MethodDeclaration method : typeDecl.getMethods()) {
			List<?> modifiers = method.modifiers();
			for (Object modifier : modifiers) {
				if (modifier instanceof Annotation annotation) {
					String annotationName = annotation.getTypeName().getFullyQualifiedName();
					if ("Theory".equals(annotationName) || ORG_JUNIT_EXPERIMENTAL_THEORIES_THEORY.equals(annotationName)) {
						data.theoryMethod = method;
						break;
					}
				}
			}
		}
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		TheoriesData theoriesData = (TheoriesData) junitHolder.getAdditionalInfo();
		
		// Remove @RunWith(Theories.class) annotation
		rewriter.remove(theoriesData.runWithAnnotation, group);
		
		// Remove @DataPoints field
		rewriter.remove(theoriesData.dataPointsField, group);
		
		// Transform @Theory method
		transformTheoryMethod(theoriesData, rewriter, ast, group);
		
		// Update imports
		importRewriter.addImport(ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_TEST);
		importRewriter.addImport(ORG_JUNIT_JUPITER_PARAMS_PROVIDER_VALUE_SOURCE);
		importRewriter.removeImport(ORG_JUNIT_EXPERIMENTAL_THEORIES_THEORIES);
		importRewriter.removeImport(ORG_JUNIT_EXPERIMENTAL_THEORIES_THEORY);
		importRewriter.removeImport(ORG_JUNIT_EXPERIMENTAL_THEORIES_DATAPOINTS);
		importRewriter.removeImport(ORG_JUNIT_RUNWITH);
	}

	private void transformTheoryMethod(TheoriesData theoriesData, ASTRewrite rewriter, AST ast, TextEditGroup group) {
		MethodDeclaration theoryMethod = theoriesData.theoryMethod;
		ListRewrite modifiersRewrite = rewriter.getListRewrite(theoryMethod, MethodDeclaration.MODIFIERS2_PROPERTY);
		
		// Replace @Theory with @ParameterizedTest
		List<?> modifiers = theoryMethod.modifiers();
		for (Object modifier : modifiers) {
			if (modifier instanceof Annotation annotation) {
				String annotationName = annotation.getTypeName().getFullyQualifiedName();
				if ("Theory".equals(annotationName) || ORG_JUNIT_EXPERIMENTAL_THEORIES_THEORY.equals(annotationName)) {
					// Create @ParameterizedTest annotation
					Annotation parameterizedTest= AnnotationUtils.createMarkerAnnotation(ast, ANNOTATION_PARAMETERIZED_TEST);
					
					// Create @ValueSource annotation with data from @DataPoints
					NormalAnnotation valueSource = createValueSourceAnnotation(ast, theoriesData);
					
					// Replace @Theory with @ParameterizedTest
					modifiersRewrite.replace(annotation, parameterizedTest, group);
					// Add @ValueSource after @ParameterizedTest
					modifiersRewrite.insertAfter(valueSource, parameterizedTest, group);
					break;
				}
			}
		}
	}

	private NormalAnnotation createValueSourceAnnotation(AST ast, TheoriesData theoriesData) {
		NormalAnnotation valueSource = ast.newNormalAnnotation();
		valueSource.setTypeName(ast.newSimpleName(ANNOTATION_VALUE_SOURCE));
		
		// Determine the array type and use appropriate member name
		String memberName = determineValueSourceMember(theoriesData.dataPointsField);
		MemberValuePair valuePair = ast.newMemberValuePair();
		valuePair.setName(ast.newSimpleName(memberName));
		
		// Copy the array initializer from @DataPoints
		if (theoriesData.dataPointsArray != null) {
			ArrayInitializer newArray = (ArrayInitializer) ASTNode.copySubtree(ast, theoriesData.dataPointsArray);
			valuePair.setValue(newArray);
		}
		
		valueSource.values().add(valuePair);
		return valueSource;
	}
	
	/**
	 * Determine the appropriate @ValueSource member name based on the array type.
	 */
	private String determineValueSourceMember(FieldDeclaration field) {
		if (field == null) {
			return "ints"; // default fallback
		}
		
		Type fieldType = field.getType();
		if (fieldType == null) {
			return "ints";
		}
		
		String typeName = fieldType.toString();
		
		// Handle array types
		if (typeName.endsWith("[]")) {
			String baseType = typeName.substring(0, typeName.length() - 2);
			switch (baseType) {
				case "int":
					return "ints";
				case "String":
					return "strings";
				case "double":
					return "doubles";
				case "long":
					return "longs";
				case "short":
					return "shorts";
				case "byte":
					return "bytes";
				case "float":
					return "floats";
				case "char":
					return "chars";
				case "boolean":
					return "booleans";
				case "Class":
					return "classes";
				default:
					return "ints"; // fallback
			}
		}
		
		return "ints"; // default fallback
	}
	
	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					public class TheoriesTest {
					    @ParameterizedTest
					    @ValueSource(ints = {1, 2, 3, 4, 5})
					    void testPositiveNumbers(int value) {
					        assertTrue(value > 0);
					    }
					}
					"""; //$NON-NLS-1$
		}
		return """
				@RunWith(Theories.class)
				public class TheoriesTest {
				    @DataPoints
				    public static int[] values = {1, 2, 3, 4, 5};
				    
				    @Theory
				    public void testPositiveNumbers(int value) {
				        assertTrue(value > 0);
				    }
				}
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "RunWithTheories"; //$NON-NLS-1$
	}
}
