/*******************************************************************************
 * Copyright (c) 2020 Carsten Hammer.
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
package org.sandbox.jdt.ui.helper.views;

import java.util.List;

import org.eclipse.core.internal.resources.File;
import org.eclipse.core.internal.resources.WorkspaceRoot;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.internal.core.JavaElement;

/**
 * Content provider with progress monitoring support for processing large numbers
 * of compilation units in packages, source folders, or projects.
 * Extracts variable bindings from AST nodes.
 */
public class VariableBindingContentProviderWithProgress {

	private final IProgressMonitor progressMonitor;

	/**
	 * Creates a new content provider with progress monitoring.
	 * 
	 * @param progressMonitor the progress monitor to report progress to
	 */
	public VariableBindingContentProviderWithProgress(IProgressMonitor progressMonitor) {
		this.progressMonitor = progressMonitor;
	}

	/**
	 * Gets the elements (variable bindings) from the input, reporting progress.
	 * 
	 * @param inputElement the input element (typically a List containing a Java element)
	 * @return array of IVariableBinding objects
	 */
	public Object[] getElements(Object inputElement) {
		VariableBindingVisitor variableVisitor = new VariableBindingVisitor();
		
		if (inputElement instanceof List list) {
			if (list.size() == 1) {
				Object object = list.get(0);
				if (object == null) {
					return new Object[0];
				}
				if (object instanceof WorkspaceRoot root) {
					System.err.println(root.getName());
				} else if (object instanceof File file) {
					System.err.println(file.getName());
				} else if (object instanceof JavaElement) {
					IJavaElement javaElement = (IJavaElement) object;
					processJavaElement(variableVisitor, javaElement);
				}
			}
		}
		
		for (IVariableBinding binding : variableVisitor.getVariableBindings()) {
			System.out.println("Var name: " + binding.getName() + " Return type: " + binding.toString()); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return variableVisitor.getVariableBindings().toArray();
	}

	private void processJavaElement(VariableBindingVisitor variableVisitor, IJavaElement javaElement) {
		if (progressMonitor.isCanceled()) {
			return;
		}
		
		if (javaElement instanceof ICompilationUnit compilationUnit) {
			processCompilationUnit(variableVisitor, compilationUnit);
		} else if (javaElement instanceof IJavaProject javaProject) {
			try {
				for (IPackageFragmentRoot packageRoot : javaProject.getAllPackageFragmentRoots()) {
					if (progressMonitor.isCanceled()) {
						return;
					}
					if (packageRoot.getKind() == IPackageFragmentRoot.K_SOURCE) {
						processPackageFragmentRoot(variableVisitor, packageRoot);
					}
				}
			} catch (JavaModelException e) {
				e.printStackTrace();
			}
		} else if (javaElement instanceof IPackageFragment packageFragment) {
			processPackageFragment(variableVisitor, packageFragment);
		} else if (javaElement instanceof IPackageFragmentRoot packageRoot) {
			processPackageFragmentRoot(variableVisitor, packageRoot);
		}
	}

	private void processPackageFragmentRoot(VariableBindingVisitor variableVisitor, IPackageFragmentRoot packageRoot) {
		try {
			for (IJavaElement child : packageRoot.getChildren()) {
				if (progressMonitor.isCanceled()) {
					return;
				}
				if (child instanceof IPackageFragment packageFragment) {
					processPackageFragment(variableVisitor, packageFragment);
				}
			}
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
	}

	private void processPackageFragment(VariableBindingVisitor variableVisitor, IPackageFragment packageFragment) {
		try {
			if (!packageFragment.containsJavaResources()) {
				return;
			}
			for (ICompilationUnit compilationUnit : packageFragment.getCompilationUnits()) {
				if (progressMonitor.isCanceled()) {
					return;
				}
				processCompilationUnit(variableVisitor, compilationUnit);
			}
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
	}

	private void processCompilationUnit(VariableBindingVisitor variableVisitor, ICompilationUnit compilationUnit) {
		progressMonitor.subTask("Processing " + compilationUnit.getElementName()); //$NON-NLS-1$
		CompilationUnit astRoot = parseCompilationUnit(compilationUnit);
		variableVisitor.process(astRoot);
		progressMonitor.worked(1);
	}

	private static CompilationUnit parseCompilationUnit(ICompilationUnit compilationUnit) {
		ASTParser parser = ASTParser.newParser(AST.JLS_Latest);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(compilationUnit);
		parser.setResolveBindings(true);
		return (CompilationUnit) parser.createAST(null);
	}
}
