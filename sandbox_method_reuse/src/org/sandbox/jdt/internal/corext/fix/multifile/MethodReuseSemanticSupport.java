/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.multifile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;

/** Binding-aware analysis shared by planning and stale-plan resolution. */
final class MethodReuseSemanticSupport {

	/** A value read from outside the selected sequence and passed to the helper. */
	record InputDescriptor(String bindingKey, String sourceName, String typeKey,
			String typeQualifiedName, ITypeBinding typeBinding) {
		InputDescriptor {
			if (bindingKey == null || sourceName == null || typeKey == null
					|| typeQualifiedName == null || typeBinding == null) {
				throw new IllegalArgumentException();
			}
		}
	}

	/** Optional local value returned by the extracted helper to its caller. */
	record OutputDescriptor(String bindingKey, String sourceName, String typeKey,
			String typeQualifiedName, ITypeBinding typeBinding,
			int declarationStatementIndexInSequence) {
		OutputDescriptor {
			if (bindingKey == null || sourceName == null || typeKey == null
					|| typeQualifiedName == null || typeBinding == null
					|| declarationStatementIndexInSequence < 0) {
				throw new IllegalArgumentException();
			}
		}
	}

	/** One safe top-level statement window in a current AST. */
	record SequenceDescriptor(String compilationUnitHandle, String sourceRootHandle,
			String packageName, String declaringTypeBindingKey,
			String declaringTypeQualifiedName, String methodBindingKey, String methodName,
			int startStatementIndex, int statementCount, String fingerprint,
			String inputTypeSignature, String outputTypeKey, List<InputDescriptor> inputs,
			OutputDescriptor output, boolean targetTypeEligible) {

		SequenceDescriptor {
			inputs= List.copyOf(inputs);
			outputTypeKey= outputTypeKey == null ? "" : outputTypeKey; //$NON-NLS-1$
		}

		String groupKey() {
			return sourceRootHandle + '\n' + packageName + '\n' + statementCount + '\n'
					+ inputTypeSignature + '\n' + outputTypeKey + '\n' + fingerprint;
		}

		String occurrenceId() {
			return compilationUnitHandle + '#' + methodBindingKey + ':' + startStatementIndex
					+ '+' + statementCount;
		}

		String rangeOwner() {
			return compilationUnitHandle + '\n' + methodBindingKey;
		}

		int endStatementIndexExclusive() {
			return startStatementIndex + statementCount;
		}
	}

	private record LocalDescriptor(String bindingKey, String sourceName, String typeKey,
			String typeQualifiedName, ITypeBinding typeBinding,
			int declarationStatementIndexInSequence, int normalizedIndex) {
	}

	private MethodReuseSemanticSupport() {
	}

	static SequenceDescriptor describeSequence(ICompilationUnit unit, CompilationUnit root,
			MethodDeclaration method, int startStatementIndex, int statementCount) {
		if (unit == null || root == null || !isEligibleContainingMethod(method)
				|| startStatementIndex < 0 || statementCount < 2) {
			return null;
		}
		@SuppressWarnings("unchecked")
		List<Statement> methodStatements= method.getBody().statements();
		if (startStatementIndex + statementCount > methodStatements.size()) {
			return null;
		}
		if (!(method.getParent() instanceof TypeDeclaration type)
				|| !(type.getParent() instanceof CompilationUnit) || type.isInterface()
				|| !type.typeParameters().isEmpty()) {
			return null;
		}
		IMethodBinding methodBinding= method.resolveBinding();
		ITypeBinding typeBinding= type.resolveBinding();
		if (methodBinding == null || typeBinding == null) {
			return null;
		}
		methodBinding= methodBinding.getMethodDeclaration();
		typeBinding= typeBinding.getTypeDeclaration();
		if (methodBinding.getKey() == null || typeBinding.getKey() == null
				|| typeBinding.getQualifiedName() == null
				|| typeBinding.getQualifiedName().isBlank()) {
			return null;
		}

		List<Statement> sequence= List.copyOf(
				methodStatements.subList(startStatementIndex, startStatementIndex + statementCount));
		if (!isStraightLineSequence(sequence) || hasCommentInside(root, sequence)) {
			return null;
		}

		LinkedHashMap<String, LocalDescriptor> internalVariables=
				collectInternalVariables(sequence, packageName(typeBinding));
		if (internalVariables == null) {
			return null;
		}
		Set<String> escapingVariables= internalVariablesReferencedAfter(methodStatements,
				startStatementIndex + statementCount, internalVariables.keySet());
		if (escapingVariables.size() > 1) {
			return null;
		}
		OutputDescriptor output= null;
		if (!escapingVariables.isEmpty()) {
			LocalDescriptor local= internalVariables.get(escapingVariables.iterator().next());
			if (local == null || !parameterTypeSupported(local.typeBinding(), packageName(typeBinding))) {
				return null;
			}
			output= new OutputDescriptor(local.bindingKey(), local.sourceName(), local.typeKey(),
					local.typeQualifiedName(), local.typeBinding(),
					local.declarationStatementIndexInSequence());
		}

		Map<String, Integer> normalizedInternalVariables= new LinkedHashMap<>();
		for (LocalDescriptor local : internalVariables.values()) {
			normalizedInternalVariables.put(local.bindingKey(),
					Integer.valueOf(local.normalizedIndex()));
		}
		SequenceAnalyzer analyzer= new SequenceAnalyzer(methodBinding, packageName(typeBinding),
				normalizedInternalVariables);
		for (Statement statement : sequence) {
			statement.accept(analyzer);
			if (!analyzer.safe()) {
				return null;
			}
		}
		if (analyzer.inputs().isEmpty()) {
			// Avoid adding a new target-class initialization edge for a no-input block.
			return null;
		}

		String fingerprint= fingerprint(sequence, normalizedInternalVariables,
				analyzer.inputIndexes());
		String inputTypeSignature= inputTypeSignature(analyzer.inputs());
		if (fingerprint == null || inputTypeSignature == null) {
			return null;
		}

		IJavaElement sourceRootElement= unit.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
		if (!(sourceRootElement instanceof IPackageFragmentRoot sourceRoot)) {
			return null;
		}
		return new SequenceDescriptor(unit.getPrimary().getHandleIdentifier(),
				sourceRoot.getHandleIdentifier(), packageName(typeBinding), typeBinding.getKey(),
				typeBinding.getQualifiedName(), methodBinding.getKey(),
				method.getName().getIdentifier(), startStatementIndex, statementCount, fingerprint,
				inputTypeSignature, output == null ? "" : output.typeKey(), analyzer.inputs(), //$NON-NLS-1$
				output, safeTargetType(type));
	}

	static MethodDeclaration findMethod(CompilationUnit root, String methodBindingKey) {
		if (root == null || methodBindingKey == null) {
			return null;
		}
		MethodDeclaration[] result= new MethodDeclaration[1];
		boolean[] duplicate= { false };
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration method) {
				IMethodBinding binding= method.resolveBinding();
				if (binding != null
						&& methodBindingKey.equals(binding.getMethodDeclaration().getKey())) {
					if (result[0] != null) {
						duplicate[0]= true;
					} else {
						result[0]= method;
					}
					return false;
				}
				return !duplicate[0];
			}
		});
		return duplicate[0] ? null : result[0];
	}

	static TypeDeclaration findType(CompilationUnit root, String typeBindingKey) {
		if (root == null || typeBindingKey == null) {
			return null;
		}
		TypeDeclaration[] result= new TypeDeclaration[1];
		boolean[] duplicate= { false };
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(TypeDeclaration type) {
				ITypeBinding binding= type.resolveBinding();
				if (binding != null
						&& typeBindingKey.equals(binding.getTypeDeclaration().getKey())) {
					if (result[0] != null) {
						duplicate[0]= true;
					} else {
						result[0]= type;
					}
					return false;
				}
				return !duplicate[0];
			}
		});
		return duplicate[0] ? null : result[0];
	}

	static List<Statement> statementWindow(MethodDeclaration method, int start, int count) {
		if (method == null || method.getBody() == null || start < 0 || count < 1) {
			return List.of();
		}
		@SuppressWarnings("unchecked")
		List<Statement> statements= method.getBody().statements();
		if (start + count > statements.size()) {
			return List.of();
		}
		return List.copyOf(statements.subList(start, start + count));
	}

	static CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setSource(unit);
		parser.setProject(unit.getJavaProject());
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(IASTSharedValues.SHARED_BINDING_RECOVERY);
		parser.setStatementsRecovery(IASTSharedValues.SHARED_AST_STATEMENT_RECOVERY);
		parser.setCompilerOptions(RefactoringASTParser.getCompilerOptions(unit.getJavaProject()));
		return (CompilationUnit) parser.createAST(null);
	}

	static boolean methodNameAvailable(TypeDeclaration type, String methodName) {
		if (type == null || methodName == null || methodName.isBlank()) {
			return false;
		}
		for (MethodDeclaration method : type.getMethods()) {
			if (methodName.equals(method.getName().getIdentifier())) {
				return false;
			}
		}
		return true;
	}

	private static boolean isEligibleContainingMethod(MethodDeclaration method) {
		return method != null && !method.isConstructor() && method.getBody() != null
				&& Modifier.isStatic(method.getModifiers())
				&& !Modifier.isAbstract(method.getModifiers())
				&& !Modifier.isNative(method.getModifiers()) && !method.isVarargs()
				&& method.typeParameters().isEmpty();
	}

	private static boolean isStraightLineSequence(List<Statement> sequence) {
		for (Statement statement : sequence) {
			if (!(statement instanceof VariableDeclarationStatement)
					&& !(statement instanceof ExpressionStatement)) {
				return false;
			}
			if (statement instanceof VariableDeclarationStatement declaration
					&& declaration.fragments().size() != 1) {
				return false;
			}
		}
		return true;
	}

	private static LinkedHashMap<String, LocalDescriptor> collectInternalVariables(
			List<Statement> sequence, String packageName) {
		LinkedHashMap<String, LocalDescriptor> result= new LinkedHashMap<>();
		for (int statementIndex= 0; statementIndex < sequence.size(); statementIndex++) {
			Statement statement= sequence.get(statementIndex);
			if (!(statement instanceof VariableDeclarationStatement declaration)
					|| declaration.fragments().size() != 1) {
				continue;
			}
			VariableDeclarationFragment fragment=
					(VariableDeclarationFragment) declaration.fragments().get(0);
			IVariableBinding binding= fragment.resolveBinding();
			ITypeBinding type= binding == null ? null : binding.getType();
			String key= binding == null ? null : binding.getVariableDeclaration().getKey();
			String typeKey= type == null ? null : type.getKey();
			String qualifiedName= type == null ? null : type.getQualifiedName();
			if (key == null || typeKey == null || qualifiedName == null
					|| qualifiedName.isBlank() || !copySafeBodyType(type, packageName)
					|| result.containsKey(key)) {
				return null;
			}
			result.put(key, new LocalDescriptor(key, fragment.getName().getIdentifier(),
					typeKey, qualifiedName, type, statementIndex, result.size()));
		}
		return result;
	}

	private static Set<String> internalVariablesReferencedAfter(List<Statement> methodStatements,
			int firstFollowingStatement, Set<String> internalKeys) {
		if (internalKeys.isEmpty()) {
			return Set.of();
		}
		Set<String> result= new LinkedHashSet<>();
		ASTVisitor finder= new ASTVisitor() {
			@Override
			public boolean visit(SimpleName name) {
				IBinding binding= name.resolveBinding();
				if (binding instanceof IVariableBinding variable) {
					String key= variable.getVariableDeclaration().getKey();
					if (key != null && internalKeys.contains(key)) {
						result.add(key);
					}
				}
				return true;
			}
		};
		for (int index= firstFollowingStatement; index < methodStatements.size(); index++) {
			methodStatements.get(index).accept(finder);
		}
		return Set.copyOf(result);
	}

	private static final class SequenceAnalyzer extends ASTVisitor {
		private final IMethodBinding owner;
		private final String packageName;
		private final Map<String, Integer> internalVariables;
		private final LinkedHashMap<String, Integer> inputIndexes= new LinkedHashMap<>();
		private final List<InputDescriptor> inputs= new ArrayList<>();
		private boolean safe= true;

		SequenceAnalyzer(IMethodBinding owner, String packageName,
				Map<String, Integer> internalVariables) {
			this.owner= owner;
			this.packageName= packageName;
			this.internalVariables= internalVariables;
		}

		boolean safe() {
			return safe;
		}

		Map<String, Integer> inputIndexes() {
			return Map.copyOf(inputIndexes);
		}

		List<InputDescriptor> inputs() {
			return List.copyOf(inputs);
		}

		@Override
		public void preVisit(ASTNode node) {
			if (!safe) {
				return;
			}
			switch (node.getNodeType()) {
				case ASTNode.RETURN_STATEMENT, ASTNode.BREAK_STATEMENT,
						ASTNode.CONTINUE_STATEMENT, ASTNode.THROW_STATEMENT,
						ASTNode.YIELD_STATEMENT, ASTNode.LABELED_STATEMENT,
						ASTNode.SYNCHRONIZED_STATEMENT, ASTNode.TRY_STATEMENT,
						ASTNode.THIS_EXPRESSION, ASTNode.SUPER_METHOD_INVOCATION,
						ASTNode.SUPER_FIELD_ACCESS, ASTNode.SUPER_CONSTRUCTOR_INVOCATION,
						ASTNode.CONSTRUCTOR_INVOCATION, ASTNode.LAMBDA_EXPRESSION,
						ASTNode.CREATION_REFERENCE, ASTNode.EXPRESSION_METHOD_REFERENCE,
						ASTNode.SUPER_METHOD_REFERENCE, ASTNode.TYPE_METHOD_REFERENCE,
						ASTNode.ANONYMOUS_CLASS_DECLARATION,
						ASTNode.TYPE_DECLARATION_STATEMENT,
						ASTNode.CLASS_INSTANCE_CREATION, ASTNode.ARRAY_CREATION -> safe= false;
				default -> {
					if (node instanceof Type type
							&& !copySafeBodyType(type.resolveBinding(), packageName)) {
						safe= false;
					}
				}
			}
		}

		@Override
		public boolean visit(MethodInvocation invocation) {
			if (!safe) {
				return false;
			}
			IMethodBinding binding= invocation.resolveMethodBinding();
			if (invocation.getExpression() == null || binding == null
					|| declaresCheckedException(binding)
					|| !methodAccessibleFromPackage(binding, packageName)) {
				safe= false;
				return false;
			}
			IMethodBinding declaration= binding.getMethodDeclaration();
			if (owner.getKey() != null && declaration.getKey() != null
					&& owner.getKey().equals(declaration.getKey())) {
				safe= false;
				return false;
			}
			return true;
		}

		@Override
		public boolean visit(SimpleName name) {
			if (!safe) {
				return false;
			}
			IBinding binding= name.resolveBinding();
			if (binding == null) {
				safe= false;
				return false;
			}
			if (binding instanceof ITypeBinding type) {
				if (!copySafeBodyType(type, packageName)) {
					safe= false;
				}
				return safe;
			}
			if (!(binding instanceof IVariableBinding variable)) {
				return true;
			}
			IVariableBinding declaration= variable.getVariableDeclaration();
			String key= declaration.getKey();
			if (key == null) {
				safe= false;
				return false;
			}
			if (internalVariables.containsKey(key)) {
				return true;
			}
			if (declaration.isField() || declaration.isEnumConstant()) {
				if (!isQualifiedFieldName(name)
						|| !fieldAccessibleFromPackage(declaration, packageName)) {
					safe= false;
				}
				return safe;
			}
			IMethodBinding declaringMethod= declaration.getDeclaringMethod();
			if (declaringMethod == null || owner.getKey() == null
					|| !owner.getKey().equals(declaringMethod.getMethodDeclaration().getKey())
					|| isDirectWrite(name)) {
				safe= false;
				return false;
			}
			if (!inputIndexes.containsKey(key)) {
				ITypeBinding type= declaration.getType();
				String typeKey= type == null ? null : type.getKey();
				String qualifiedName= type == null ? null : type.getQualifiedName();
				if (!parameterTypeSupported(type, packageName) || typeKey == null
						|| qualifiedName == null || qualifiedName.isBlank()) {
					safe= false;
					return false;
				}
				inputIndexes.put(key, Integer.valueOf(inputIndexes.size()));
				inputs.add(new InputDescriptor(key, name.getIdentifier(), typeKey, qualifiedName,
						type));
			}
			return true;
		}
	}

	private static boolean isQualifiedFieldName(SimpleName name) {
		ASTNode parent= name.getParent();
		return (parent instanceof QualifiedName qualified && qualified.getName() == name)
				|| (parent instanceof FieldAccess access && access.getName() == name);
	}

	private static boolean fieldAccessibleFromPackage(IVariableBinding field,
			String targetPackage) {
		if (Modifier.isPublic(field.getModifiers())) {
			return typeAccessibleFromPackage(field.getDeclaringClass(), targetPackage);
		}
		if (Modifier.isPrivate(field.getModifiers())) {
			return false;
		}
		ITypeBinding declaringClass= field.getDeclaringClass();
		return declaringClass != null
				&& targetPackage.equals(packageName(declaringClass.getTypeDeclaration()));
	}

	private static boolean methodAccessibleFromPackage(IMethodBinding method,
			String targetPackage) {
		IMethodBinding declaration= method.getMethodDeclaration();
		if (Modifier.isPrivate(declaration.getModifiers())) {
			return false;
		}
		ITypeBinding declaringClass= declaration.getDeclaringClass();
		if (declaringClass == null) {
			return false;
		}
		if (Modifier.isPublic(declaration.getModifiers())) {
			return typeAccessibleFromPackage(declaringClass, targetPackage);
		}
		return targetPackage.equals(packageName(declaringClass.getTypeDeclaration()));
	}

	private static boolean typeAccessibleFromPackage(ITypeBinding type,
			String targetPackage) {
		ITypeBinding declaration= type == null ? null : type.getTypeDeclaration();
		if (declaration == null || Modifier.isPrivate(declaration.getModifiers())) {
			return false;
		}
		return Modifier.isPublic(declaration.getModifiers())
				|| targetPackage.equals(packageName(declaration));
	}

	private static boolean isDirectWrite(SimpleName name) {
		ASTNode current= name;
		ASTNode parent= current.getParent();
		while (parent instanceof ParenthesizedExpression) {
			current= parent;
			parent= current.getParent();
		}
		if (parent instanceof Assignment assignment
				&& assignment.getLeftHandSide() == current) {
			return true;
		}
		if (parent instanceof PrefixExpression prefix && prefix.getOperand() == current) {
			return prefix.getOperator() == PrefixExpression.Operator.INCREMENT
					|| prefix.getOperator() == PrefixExpression.Operator.DECREMENT;
		}
		return parent instanceof PostfixExpression postfix
				&& postfix.getOperand() == current;
	}

	private static boolean declaresCheckedException(IMethodBinding method) {
		for (ITypeBinding exception : method.getExceptionTypes()) {
			if (!isUnchecked(exception)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isUnchecked(ITypeBinding exception) {
		for (ITypeBinding current= exception; current != null;
				current= current.getSuperclass()) {
			String qualifiedName= current.getErasure().getQualifiedName();
			if ("java.lang.RuntimeException".equals(qualifiedName) //$NON-NLS-1$
					|| "java.lang.Error".equals(qualifiedName)) { //$NON-NLS-1$
				return true;
			}
		}
		return false;
	}

	private static boolean parameterTypeSupported(ITypeBinding type, String targetPackage) {
		if (type == null || type.isNullType() || type.isWildcardType()
				|| type.isCapture() || type.isIntersectionType() || type.isTypeVariable()) {
			return false;
		}
		if (type.isArray()) {
			return parameterTypeSupported(type.getElementType(), targetPackage);
		}
		if (type.isPrimitive()) {
			return true;
		}
		return typeAccessibleFromPackage(type, targetPackage);
	}

	private static boolean copySafeBodyType(ITypeBinding type, String targetPackage) {
		if (!parameterTypeSupported(type, targetPackage)) {
			return false;
		}
		if (type.isArray()) {
			return copySafeBodyType(type.getElementType(), targetPackage);
		}
		if (type.isPrimitive()) {
			return true;
		}
		String packageName= packageName(type.getTypeDeclaration());
		return "java.lang".equals(packageName) || targetPackage.equals(packageName); //$NON-NLS-1$
	}

	private static boolean safeTargetType(TypeDeclaration type) {
		for (FieldDeclaration field : type.getFields()) {
			if (Modifier.isStatic(field.getModifiers())) {
				return false;
			}
		}
		for (Object declaration : type.bodyDeclarations()) {
			if (declaration instanceof Initializer initializer
					&& Modifier.isStatic(initializer.getModifiers())) {
				return false;
			}
		}
		return true;
	}

	private static String packageName(ITypeBinding type) {
		IPackageBinding packageBinding= type == null ? null : type.getPackage();
		return packageBinding == null ? "" : packageBinding.getName(); //$NON-NLS-1$
	}

	private static boolean hasCommentInside(CompilationUnit root,
			List<Statement> sequence) {
		if (sequence.isEmpty()) {
			return false;
		}
		int start= sequence.get(0).getStartPosition();
		Statement last= sequence.get(sequence.size() - 1);
		int end= last.getStartPosition() + last.getLength();
		for (Object rawComment : root.getCommentList()) {
			Comment comment= (Comment) rawComment;
			if (comment.getStartPosition() >= start && comment.getStartPosition() < end) {
				return true;
			}
		}
		return false;
	}

	private static String inputTypeSignature(List<InputDescriptor> inputs) {
		StringBuilder result= new StringBuilder();
		for (InputDescriptor input : inputs) {
			if (input.typeKey() == null) {
				return null;
			}
			appendToken(result, input.typeKey());
		}
		return result.toString();
	}

	private static String fingerprint(List<Statement> sequence,
			Map<String, Integer> internalVariables, Map<String, Integer> inputs) {
		StringBuilder result= new StringBuilder(512);
		appendToken(result, Integer.toString(sequence.size()));
		for (Statement statement : sequence) {
			if (!appendNode(result, statement, internalVariables, inputs)) {
				return null;
			}
		}
		return result.toString();
	}

	private static boolean appendNode(StringBuilder result, ASTNode node,
			Map<String, Integer> internalVariables, Map<String, Integer> inputs) {
		appendToken(result, Integer.toString(node.getNodeType()));
		if (node instanceof SimpleName name) {
			String semantic= semanticBinding(name.resolveBinding(), internalVariables, inputs);
			if (semantic == null) {
				return false;
			}
			appendToken(result, semantic);
			return true;
		}
		for (Object rawDescriptor : node.structuralPropertiesForType()) {
			StructuralPropertyDescriptor descriptor= (StructuralPropertyDescriptor) rawDescriptor;
			appendToken(result, descriptor.getId());
			Object value= node.getStructuralProperty(descriptor);
			if (descriptor.isChildProperty()) {
				if (value == null) {
					appendToken(result, "null"); //$NON-NLS-1$
				} else if (!appendNode(result, (ASTNode) value, internalVariables, inputs)) {
					return false;
				}
			} else if (descriptor.isChildListProperty()) {
				List<?> children= (List<?>) value;
				appendToken(result, Integer.toString(children.size()));
				for (Object child : children) {
					if (!appendNode(result, (ASTNode) child, internalVariables, inputs)) {
						return false;
					}
				}
			} else {
				appendToken(result, String.valueOf(value));
			}
		}
		return true;
	}

	private static String semanticBinding(IBinding binding,
			Map<String, Integer> internalVariables, Map<String, Integer> inputs) {
		if (binding == null) {
			return null;
		}
		if (binding instanceof IVariableBinding variable) {
			IVariableBinding declaration= variable.getVariableDeclaration();
			String key= declaration.getKey();
			Integer internalIndex= internalVariables.get(key);
			if (internalIndex != null) {
				return "$local" + internalIndex; //$NON-NLS-1$
			}
			Integer inputIndex= inputs.get(key);
			if (inputIndex != null) {
				return "$input" + inputIndex; //$NON-NLS-1$
			}
			if (declaration.isField() || declaration.isEnumConstant()) {
				return "F:" + key; //$NON-NLS-1$
			}
			return null;
		}
		if (binding instanceof IMethodBinding method) {
			String key= method.getMethodDeclaration().getKey();
			return key == null ? null : "M:" + key; //$NON-NLS-1$
		}
		if (binding instanceof ITypeBinding type) {
			String key= type.getTypeDeclaration().getKey();
			return key == null ? null : "T:" + key; //$NON-NLS-1$
		}
		if (binding instanceof IPackageBinding packageBinding) {
			return "P:" + packageBinding.getName(); //$NON-NLS-1$
		}
		return binding.getKey() == null ? null
				: binding.getKind() + ":" + binding.getKey(); //$NON-NLS-1$
	}

	private static void appendToken(StringBuilder result, String value) {
		String normalized= value == null ? "null" : value; //$NON-NLS-1$
		result.append(normalized.length()).append(':').append(normalized).append(';');
	}
}
