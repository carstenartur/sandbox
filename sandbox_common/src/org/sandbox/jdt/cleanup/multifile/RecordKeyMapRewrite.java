/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.cleanup.multifile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

import org.sandbox.jdt.container.analysis.RecordKeyLookupAnalyzer;
import org.sandbox.jdt.container.api.ContainerUsageProfile;

/** Converts a private first-match index whose complete content is determined during construction. */
final class RecordKeyMapRewrite extends CompilationUnitRewriteOperationWithSourceRange {
	private record Insertion(MethodInvocation add, Expression key) {}
	private record Scan(EnhancedForStatement loop, SimpleName query) {}
	private static final String DESCRIPTION= "Index a closed record sequence by its immutable String key"; //$NON-NLS-1$
	private final FieldDeclaration field;
	private final VariableDeclarationFragment fragment;
	private final List<Insertion> insertions;
	private final List<Scan> scans;

	private RecordKeyMapRewrite(FieldDeclaration field, VariableDeclarationFragment fragment, List<Insertion> insertions, List<Scan> scans) {
		this.field= field;
		this.fragment= fragment;
		this.insertions= List.copyOf(insertions);
		this.scans= List.copyOf(scans);
	}

	static List<RecordKeyMapRewrite> find(CompilationUnit root) {
		if (java.util.Arrays.stream(root.getProblems()).anyMatch(problem -> problem.isError())) {
			return List.of();
		}
		List<RecordKeyMapRewrite> result= new ArrayList<>();
		for (var profile : RecordKeyLookupAnalyzer.analyze(root, () -> {})) {
			var operation= plan(root, profile);
			if (operation != null) {
				result.add(operation);
			}
		}
		return result;
	}

	private static RecordKeyMapRewrite plan(CompilationUnit root, ContainerUsageProfile profile) {
		if (!(root.findDeclaringNode(profile.identity().bindingKey()) instanceof VariableDeclarationFragment fragment)
				|| !(fragment.getParent() instanceof FieldDeclaration field) || field.fragments().size() != 1
				|| !field.modifiers().stream().allMatch(Modifier.class::isInstance)
				|| !(field.getParent() instanceof TypeDeclaration owner) || owner.getParent() != root || !Modifier.isFinal(owner.getModifiers())
				|| owner.getSuperclassType() != null || !owner.superInterfaceTypes().isEmpty() || !owner.typeParameters().isEmpty()
				|| !(field.getType() instanceof ParameterizedType type) || type.typeArguments().size() != 1 || fragment.resolveBinding() == null
				|| !Modifier.isPrivate(field.getModifiers()) || !Modifier.isFinal(field.getModifiers()) || Modifier.isStatic(field.getModifiers())
				|| fragment.resolveBinding().getAnnotations().length != 0 || owner.resolveBinding().getAnnotations().length != 0
				|| owner.getFields().length != 1 || owner.bodyDeclarations().stream().anyMatch(Initializer.class::isInstance)
				|| type.resolveBinding().getTypeAnnotations().length != 0
				|| !(fragment.getInitializer() instanceof ClassInstanceCreation empty) || !empty.arguments().isEmpty() || empty.getAnonymousClassDeclaration() != null
				|| empty.resolveTypeBinding() == null || !"java.util.ArrayList".equals(empty.resolveTypeBinding().getErasure().getQualifiedName())) { //$NON-NLS-1$
			return null;
		}
		var element= fragment.resolveBinding().getType().getTypeArguments()[0];
		if (!element.isRecord() || element.isParameterizedType() || element.getTypeAnnotations().length != 0 || !(root.findDeclaringNode(element) instanceof RecordDeclaration record)
				|| record.getParent() != owner || !record.bodyDeclarations().isEmpty() || !record.typeParameters().isEmpty()
				|| !record.superInterfaceTypes().isEmpty() || record.resolveBinding().getAnnotations().length != 0) {
			return null;
		}
		Set<ASTNode> admitted= new HashSet<>();
		admitted.add(fragment.getName());
		List<Scan> scans= new ArrayList<>();
		int keyIndex= -1;
		for (var observation : profile.evidence()) {
			if (!(NodeFinder.perform(root, observation.sourceStart(), observation.sourceLength()) instanceof EnhancedForStatement loop)
					|| !(loop.getParent() instanceof Block body) || !(body.getParent() instanceof MethodDeclaration method)
					|| method.getParent() != owner || method.getBody() != body || body.statements().size() != 2 || body.statements().getFirst() != loop
					|| !(body.statements().getLast() instanceof ReturnStatement fallback) || !(fallback.getExpression() instanceof NullLiteral)
					|| !(single(loop.getBody()) instanceof IfStatement branch) || !(branch.getExpression() instanceof MethodInvocation equals)
					|| !(equals.getExpression() instanceof SimpleName query) || !(equals.arguments().getFirst() instanceof MethodInvocation accessor)
					|| method.parameters().size() != 1 || !(method.parameters().getFirst() instanceof SingleVariableDeclaration parameter)
					|| parameter.resolveBinding() == null || !parameter.resolveBinding().isEqualTo(query.resolveBinding())
					|| !"java.lang.String".equals(parameter.resolveBinding().getType().getQualifiedName())) { //$NON-NLS-1$
				return null;
			}
			int component= componentIndex(record, accessor.getName().getIdentifier());
			if (component < 0 || (keyIndex >= 0 && keyIndex != component)) {
				return null;
			}
			keyIndex= component;
			if (includesComments(root, loop)) {
				return null;
			}
			admitted.add(referenceName(loop.getExpression()));
			scans.add(new Scan(loop, query));
		}
		if (keyIndex < 0) {
			return null;
		}
		var inserts= construction(owner, fragment.resolveBinding(), element, keyIndex, admitted);
		if (inserts == null || inserts.isEmpty() || !allUsesAdmitted(root, fragment.resolveBinding(), admitted)
				|| includesComments(root, field.getType()) || includesComments(root, empty)) {
			return null;
		}
		return new RecordKeyMapRewrite(field, fragment, inserts, scans);
	}

	private static List<Insertion> construction(TypeDeclaration owner, IVariableBinding field,
			ITypeBinding record, int keyIndex, Set<ASTNode> admitted) {
		List<Insertion> insertions= new ArrayList<>();
		for (MethodDeclaration method : owner.getMethods()) {
			if (method.isConstructor() && (method.getBody() == null || !constructionStatements(method.getBody(), field, record, keyIndex, admitted, insertions))) {
				return null;
			}
		}
		return insertions;
	}

	/** Before publication only conditional inserts of fresh records with inert constructor arguments are allowed. */
	private static boolean constructionStatements(Statement statement, IVariableBinding field, ITypeBinding record,
			int keyIndex, Set<ASTNode> admitted, List<Insertion> insertions) {
		if (statement instanceof Block block) {
			for (Object child : block.statements()) {
				if (!constructionStatements((Statement) child, field, record, keyIndex, admitted, insertions)) {
					return false;
				}
			}
			return true;
		}
		if (statement instanceof IfStatement branch && branch.getExpression() instanceof SimpleName name
				&& name.resolveBinding() instanceof IVariableBinding flag && flag.isParameter() && flag.getType().isPrimitive()) {
			return constructionStatements(branch.getThenStatement(), field, record, keyIndex, admitted, insertions)
					&& (branch.getElseStatement() == null || constructionStatements(branch.getElseStatement(), field, record, keyIndex, admitted, insertions));
		}
		if (!(statement instanceof ExpressionStatement expr) || !(expr.getExpression() instanceof MethodInvocation add)
				|| !(referenceName(add.getExpression()) instanceof SimpleName name) || !field.isEqualTo(name.resolveBinding())
				|| add.resolveMethodBinding() == null || !"add".equals(add.getName().getIdentifier()) || add.arguments().size() != 1 //$NON-NLS-1$
				|| !(add.arguments().getFirst() instanceof ClassInstanceCreation row) || row.getAnonymousClassDeclaration() != null
				|| !record.isEqualTo(row.resolveTypeBinding()) || row.resolveConstructorBinding() == null || row.arguments().size() <= keyIndex) {
			return false;
		}
		for (Object value : row.arguments()) {
			if (!(value instanceof Expression argument) || !inertValue(argument)) {
				return false;
			}
		}
		admitted.add(name);
		insertions.add(new Insertion(add, (Expression) row.arguments().get(keyIndex)));
		return true;
	}

	private static boolean inertValue(Expression value) {
		return switch (value.getNodeType()) {
			case ASTNode.NULL_LITERAL, ASTNode.STRING_LITERAL, ASTNode.NUMBER_LITERAL,
					ASTNode.BOOLEAN_LITERAL, ASTNode.CHARACTER_LITERAL -> true;
			case ASTNode.SIMPLE_NAME -> ((SimpleName) value).resolveBinding() instanceof IVariableBinding binding && binding.isParameter();
			default -> false;
		};
	}

	private static boolean allUsesAdmitted(CompilationUnit root, IVariableBinding field, Set<ASTNode> admitted) {
		boolean[] safe= { true };
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration method) {
				if (Modifier.isNative(method.getModifiers())) {
					safe[0]= false;
				}
				return safe[0];
			}

			@Override
			public boolean visit(SimpleName name) {
				var binding= name.resolveBinding();
				if (binding instanceof IVariableBinding var && field.isEqualTo(var) && !admitted.contains(name)) {
					safe[0]= false;
				}
				if (binding instanceof IMethodBinding method && method.getDeclaringClass() != null) {
					String type= method.getDeclaringClass().getErasure().getQualifiedName();
					if ("java.lang.Class".equals(type) || type.startsWith("java.lang.reflect.") || type.startsWith("java.lang.invoke.")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						safe[0]= false;
					}
				}
				return safe[0];
			}
		});
		return safe[0];
	}

	private static boolean includesComments(CompilationUnit root, ASTNode node) {
		int start= root.getExtendedStartPosition(node);
		int end= start + root.getExtendedLength(node);
		for (Object entry : root.getCommentList()) {
			var comment= (ASTNode) entry;
			if (comment.getStartPosition() < end && start < comment.getStartPosition() + comment.getLength()) {
				return true;
			}
		}
		return false;
	}

	private static ASTNode referenceName(Expression expression) {
		if (expression instanceof FieldAccess field) {
			return field.getName();
		}
		return expression;
	}

	private static int componentIndex(RecordDeclaration record, String name) {
		for (int i= 0; i < record.recordComponents().size(); i++) {
			if (((SingleVariableDeclaration) record.recordComponents().get(i)).getName().getIdentifier().equals(name)) {
				return i;
			}
		}
		return -1;
	}

	private static Statement single(Statement statement) {
		return statement instanceof Block block && block.statements().size() == 1 ? (Statement) block.statements().getFirst() : statement;
	}

	@Override
	public void rewriteASTInternal(CompilationUnitRewrite cu, LinkedProposalModelCore model) {
		var ast= cu.getRoot().getAST();
		var rewrite= cu.getASTRewrite();
		var group= createTextEditGroup(DESCRIPTION, cu);
		var imports= cu.getImportRewrite();
		var context= new ContextSensitiveImportRewriteContext(field, imports);
		var map= ast.newParameterizedType(ast.newSimpleType(ast.newName(imports.addImport("java.util.Map", context)))); //$NON-NLS-1$
		map.typeArguments().add(ast.newSimpleType(ast.newName(imports.addImport("java.lang.String", context)))); //$NON-NLS-1$
		map.typeArguments().add(ASTNode.copySubtree(ast, (Type) ((ParameterizedType) field.getType()).typeArguments().getFirst()));
		rewrite.replace(field.getType(), map, group);
		var creation= ast.newClassInstanceCreation();
		creation.setType(ast.newParameterizedType(ast.newSimpleType(ast.newName(imports.addImport("java.util.LinkedHashMap", context))))); //$NON-NLS-1$
		rewrite.replace(fragment.getInitializer(), creation, group);
		for (var insertion : insertions) {
			var call= insertion.add();
			rewrite.set(call, MethodInvocation.NAME_PROPERTY, ast.newSimpleName("putIfAbsent"), group); //$NON-NLS-1$
			rewrite.getListRewrite(call, MethodInvocation.ARGUMENTS_PROPERTY).insertFirst(ASTNode.copySubtree(ast, insertion.key()), group);
		}
		for (var scan : scans) {
			var fieldRead= scan.loop().getExpression();
			var lookup= ast.newMethodInvocation();
			lookup.setExpression((Expression) ASTNode.copySubtree(ast, fieldRead));
			lookup.setName(ast.newSimpleName("get")); //$NON-NLS-1$
			var checked= ast.newMethodInvocation();
			checked.setExpression(ast.newName(imports.addImport("java.util.Objects", new ContextSensitiveImportRewriteContext(scan.loop(), imports)))); //$NON-NLS-1$
			checked.setName(ast.newSimpleName("requireNonNull")); //$NON-NLS-1$
			checked.arguments().add(ASTNode.copySubtree(ast, scan.query()));
			lookup.arguments().add(checked);
			var empty= ast.newMethodInvocation();
			empty.setExpression((Expression) ASTNode.copySubtree(ast, fieldRead));
			empty.setName(ast.newSimpleName("isEmpty")); //$NON-NLS-1$
			var guard= ast.newIfStatement();
			guard.setExpression(empty);
			var whenEmpty= ast.newReturnStatement();
			whenEmpty.setExpression(ast.newNullLiteral());
			guard.setThenStatement(whenEmpty);
			rewrite.replace(scan.loop(), guard, group);
			var body= (Block) scan.loop().getParent();
			rewrite.replace(((ReturnStatement) body.statements().getLast()).getExpression(), lookup, group);
		}
	}

	@Override
	public String getAdditionalInfo() {
		return DESCRIPTION;
	}
}
