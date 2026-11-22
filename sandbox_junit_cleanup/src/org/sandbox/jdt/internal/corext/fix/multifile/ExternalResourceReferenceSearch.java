/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.multifile;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.ChildPropertyDescriptor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.search.FieldReferenceMatch;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.core.util.IClassFileReader;
import org.eclipse.jdt.core.util.IConstantPoolConstant;

/** Proves the selected resource contract without relying on a previous build. */
final class ExternalResourceReferenceSearch {

	private final Set<String> projectHandles= new LinkedHashSet<>();
	private final Set<String> selectedHandles= new LinkedHashSet<>();
	private final Map<String, CompilationUnit> candidateRoots= new LinkedHashMap<>();
	private final IJavaSearchScope sourceScope;
	private final boolean validSelection;

	ExternalResourceReferenceSearch(List<IJavaProject> projects, List<ICompilationUnit> selected) throws JavaModelException {
		for (IJavaProject project : projects) {
			if (project != null && project.exists()) projectHandles.add(project.getHandleIdentifier());
		}
		boolean valid= !projects.isEmpty() && !selected.isEmpty();
		for (ICompilationUnit unit : selected) {
			if (unit == null || !unit.exists()
					|| !projectHandles.contains(unit.getJavaProject().getHandleIdentifier())) {
				valid= false;
			} else {
				selectedHandles.add(unit.getPrimary().getHandleIdentifier());
			}
		}
		validSelection= valid;
		sourceScope= SearchEngine.createJavaSearchScope(
				JavaCore.create(ResourcesPlugin.getWorkspace().getRoot()).getJavaProjects(), IJavaSearchScope.SOURCES);
	}

	boolean allReferencesSelected(IJavaElement target, IProgressMonitor monitor) throws CoreException {
		checkCanceled(monitor);
		if (!validSelection || target == null || !target.exists() || !isSelected(target)) return false;
		SearchPattern exact= SearchPattern.createPattern(target, IJavaSearchConstants.REFERENCES);
		if (exact == null || !matchesSelected(exact, SearchEngine.createWorkspaceScope(), target, false, monitor)) {
			return false;
		}
		// Element-focused JDT searches can omit an indexed caller whose last builder
		// state predates a new reference. String patterns do not use that prefilter.
		List<SearchPattern> patterns= candidatePatterns(target);
		if (patterns == null) return false;
		for (SearchPattern pattern : patterns) {
			if (pattern == null || !matchesSelected(pattern, sourceScope, target, true, monitor)) return false;
		}
		checkCanceled(monitor);
		return true;
	}

	private boolean matchesSelected(SearchPattern pattern, IJavaSearchScope scope, IJavaElement target,
			boolean refineCandidates, IProgressMonitor monitor) throws CoreException {
		boolean[] complete= { true };
		new SearchEngine().search(pattern,
					new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
					scope, new SearchRequestor() {
						@Override
						public void acceptSearchMatch(SearchMatch match) throws CoreException {
							if (!complete[0]) return;
							if (isBinaryLambdaParameter(match, target, monitor)) return;
							if (match.getAccuracy() != SearchMatch.A_ACCURATE
									|| !(match.getElement() instanceof IJavaElement element)
									|| !(element.getAncestor(IJavaElement.COMPILATION_UNIT) instanceof ICompilationUnit unit)
									|| !unit.exists()) {
								complete[0]= false;
								return;
							}
							if (!isSelected(unit) && (!refineCandidates
									|| !isOtherDeclaration(unit.getPrimary(), match, target, monitor))) {
								complete[0]= false;
							}
						}
					}, monitor);
		checkCanceled(monitor);
		return complete[0];
	}

	private boolean isSelected(IJavaElement element) {
		return element.getAncestor(IJavaElement.COMPILATION_UNIT) instanceof ICompilationUnit unit && unit.exists()
				&& projectHandles.contains(unit.getJavaProject().getHandleIdentifier())
				&& selectedHandles.contains(unit.getPrimary().getHandleIdentifier());
	}

	private boolean isBinaryLambdaParameter(SearchMatch match, IJavaElement target, IProgressMonitor monitor)
			throws JavaModelException {
		if (!(target instanceof IField field) || !(match instanceof FieldReferenceMatch)
				|| !(match.getElement() instanceof IJavaElement element)
				|| !(element.getAncestor(IJavaElement.CLASS_FILE) instanceof IClassFile binary)
				|| !binary.exists() || match.getOffset() < 0 || match.getLength() <= 0 || binary.getBuffer() == null) {
			return false;
		}
		// Attached JDK source can report a lambda parameter as an inaccurate field
		// match. Require both the actual bytecode and its lexical declaration to
		// disprove that reference; an unknown or real binary caller still blocks.
		var reader= ToolFactory.createDefaultClassFileReader(new ByteArrayInputStream(binary.getBytes()),
				IClassFileReader.CONSTANT_POOL);
		if (reader == null) return false;
		var pool= reader.getConstantPool();
		for (int i= 1; i < pool.getConstantPoolCount(); i++) {
			if (pool.getEntryKind(i) == IConstantPoolConstant.CONSTANT_Fieldref
					&& field.getElementName().equals(new String(pool.decodeEntry(i).getFieldName()))) return false;
		}
		CompilationUnit root= candidateRoots.get(binary.getHandleIdentifier());
		if (root == null) {
			ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
			parser.setSource(binary);
			root= (CompilationUnit) parser.createAST(monitor);
			candidateRoots.put(binary.getHandleIdentifier(), root);
		}
		var node= NodeFinder.perform(root, match.getOffset(), match.getLength());
		if (!(node instanceof SimpleName name) || name.isDeclaration()
				|| name.getStartPosition() != match.getOffset() || name.getLength() != match.getLength()
				|| !field.getElementName().equals(name.getIdentifier())) return false;
		return isLambdaParameterExpression(name);
	}

	private static boolean isLambdaParameterExpression(SimpleName name) {
		// A parameter may be the leftmost qualifier in parameter.member, never a
		// field tail, method name, type name, declaration, or statement label.
		ASTNode expression= name;
		while (expression.getParent() instanceof QualifiedName qualified && qualified.getQualifier() == expression) {
			expression= qualified;
		}
		var location= expression.getLocationInParent();
		boolean expressionRole= location instanceof ChildPropertyDescriptor child && child.getChildType() == Expression.class
				|| location instanceof ChildListPropertyDescriptor list && list.getElementType() == Expression.class
				|| expression.getParent() instanceof LambdaExpression lambda && lambda.getBody() == expression;
		if (!expressionRole || (name.getFlags() & (ASTNode.RECOVERED | ASTNode.MALFORMED)) != 0) return false;
		for (ASTNode child= name, parent= child.getParent(); parent != null; child= parent, parent= parent.getParent()) {
			if ((parent.getFlags() & (ASTNode.RECOVERED | ASTNode.MALFORMED)) != 0) return false;
			if (parent instanceof AbstractTypeDeclaration || parent instanceof AnonymousClassDeclaration
					|| parent instanceof MethodDeclaration) return false;
			if (parent instanceof LambdaExpression lambda && lambda.getBody() == child) {
				for (Object parameter : lambda.parameters()) {
					if (parameter instanceof VariableDeclaration variable
							&& (variable.getFlags() & (ASTNode.RECOVERED | ASTNode.MALFORMED)) == 0
							&& name.getIdentifier().equals(variable.getName().getIdentifier())) return true;
				}
			}
		}
		return false;
	}

	private static void checkCanceled(IProgressMonitor monitor) {
		if (monitor != null && monitor.isCanceled()) throw new OperationCanceledException();
	}

	private static List<SearchPattern> candidatePatterns(IJavaElement target) throws JavaModelException {
		List<SearchPattern> patterns= new ArrayList<>();
		if (target instanceof IType type) {
			patterns.add(pattern(type.getFullyQualifiedName('.'), IJavaSearchConstants.TYPE));
		} else if (target instanceof IMember member && member.getDeclaringType() != null) {
			String name= member.getDeclaringType().getFullyQualifiedName('.') + '.' + member.getElementName();
			if (member instanceof IField field) {
				patterns.add(pattern(name, IJavaSearchConstants.FIELD));
				if (field.isRecordComponent()) patterns.add(pattern(name + "()", IJavaSearchConstants.METHOD)); //$NON-NLS-1$
			} else if (member instanceof IMethod) {
				// Bindings below distinguish overloads and equal names in other projects.
				patterns.add(pattern(name, IJavaSearchConstants.METHOD));
			} else {
				return null;
			}
		} else {
			return null;
		}
		return patterns;
	}

	private static SearchPattern pattern(String name, int kind) {
		return SearchPattern.createPattern(name, kind, IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH | SearchPattern.R_CASE_SENSITIVE);
	}

	private boolean isOtherDeclaration(ICompilationUnit unit, SearchMatch match, IJavaElement target,
			IProgressMonitor monitor) throws JavaModelException {
		String handle= unit.getHandleIdentifier();
		CompilationUnit root= candidateRoots.get(handle);
		if (root == null) {
			ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
			parser.setSource(unit);
			parser.setResolveBindings(true);
			root= (CompilationUnit) parser.createAST(monitor);
			candidateRoots.put(handle, root);
		}
		var node= NodeFinder.perform(root, match.getOffset(), match.getLength());
		IBinding binding= node instanceof Name name ? name.resolveBinding()
				: node instanceof Type type ? type.resolveBinding() : null;
		if (binding == null || binding.isRecovered()) return false;
		IJavaElement declaration;
		if (target instanceof IType && binding instanceof ITypeBinding type) {
			declaration= type.getTypeDeclaration().getJavaElement();
		} else if (target instanceof IField field && field.isRecordComponent()
				&& binding instanceof IMethodBinding method && method.getParameterTypes().length == 0
				&& field.getElementName().equals(method.getName())) {
			return differentOwner(method.getDeclaringClass().getTypeDeclaration().getJavaElement(), field.getDeclaringType());
		} else if (target instanceof IField && binding instanceof IVariableBinding variable && variable.isField()) {
			declaration= variable.getVariableDeclaration().getJavaElement();
		} else if (target instanceof IMethod && binding instanceof IMethodBinding method) {
			declaration= method.getMethodDeclaration().getJavaElement();
		} else {
			return false;
		}
		if (declaration == null || !declaration.exists()) return false;
		if (!(target instanceof IType) && target instanceof IMember expected && declaration instanceof IMember actual) {
			IType expectedOwner= expected.getDeclaringType();
			IType actualOwner= actual.getDeclaringType();
			if (actualOwner == null || expectedOwner == null
					|| !actualOwner.getFullyQualifiedName('.').equals(expectedOwner.getFullyQualifiedName('.'))) {
				return false;
			}
		}
		return !declaration.getHandleIdentifier().equals(target.getHandleIdentifier());
	}

	private static boolean differentOwner(IJavaElement actual, IType expected) {
		return actual instanceof IType type && type.exists()
				&& type.getFullyQualifiedName('.').equals(expected.getFullyQualifiedName('.'))
				&& !type.getHandleIdentifier().equals(expected.getHandleIdentifier());
	}
}
