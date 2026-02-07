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
 */
public class JHViewContentProviderWithProgress {

	private final IProgressMonitor monitor;

	/**
	 * Creates a new content provider with progress monitoring.
	 * 
	 * @param monitor the progress monitor to report progress to
	 */
	public JHViewContentProviderWithProgress(IProgressMonitor monitor) {
		this.monitor = monitor;
	}

	/**
	 * Gets the elements (variable bindings) from the input, reporting progress.
	 * 
	 * @param inputElement the input element (typically a List containing a Java element)
	 * @return array of IVariableBinding objects
	 */
	public Object[] getElements(Object inputElement) {
		VarVisitor visitor = new VarVisitor();
		
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
					processJavaElement(visitor, javaElement);
				}
			}
		}
		
		for (IVariableBinding binding : visitor.getVars()) {
			System.out.println("Var name: " + binding.getName() + " Return type: " + binding.toString()); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return visitor.getVars().toArray();
	}

	private void processJavaElement(VarVisitor visitor, IJavaElement javaElement) {
		if (monitor.isCanceled()) {
			return;
		}
		
		if (javaElement instanceof ICompilationUnit cu) {
			processCompilationUnit(visitor, cu);
		} else if (javaElement instanceof IJavaProject jproject) {
			try {
				for (IPackageFragmentRoot pfr : jproject.getAllPackageFragmentRoots()) {
					if (monitor.isCanceled()) {
						return;
					}
					if (pfr.getKind() == IPackageFragmentRoot.K_SOURCE) {
						processPackageFragmentRoot(visitor, pfr);
					}
				}
			} catch (JavaModelException e) {
				e.printStackTrace();
			}
		} else if (javaElement instanceof IPackageFragment pf) {
			processPackageFragment(visitor, pf);
		} else if (javaElement instanceof IPackageFragmentRoot pfr) {
			processPackageFragmentRoot(visitor, pfr);
		}
	}

	private void processPackageFragmentRoot(VarVisitor visitor, IPackageFragmentRoot pfr) {
		try {
			for (IJavaElement child : pfr.getChildren()) {
				if (monitor.isCanceled()) {
					return;
				}
				if (child instanceof IPackageFragment pf) {
					processPackageFragment(visitor, pf);
				}
			}
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
	}

	private void processPackageFragment(VarVisitor visitor, IPackageFragment pf) {
		try {
			if (!pf.containsJavaResources()) {
				return;
			}
			for (ICompilationUnit unit : pf.getCompilationUnits()) {
				if (monitor.isCanceled()) {
					return;
				}
				processCompilationUnit(visitor, unit);
			}
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
	}

	private void processCompilationUnit(VarVisitor visitor, ICompilationUnit unit) {
		monitor.subTask("Processing " + unit.getElementName()); //$NON-NLS-1$
		CompilationUnit parse = parse(unit);
		visitor.process(parse);
		monitor.worked(1);
	}

	private static CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser = ASTParser.newParser(AST.JLS_Latest);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(unit);
		parser.setResolveBindings(true);
		return (CompilationUnit) parser.createAST(null);
	}
}
