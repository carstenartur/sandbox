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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.ListenerList;
import org.eclipse.jdt.core.IJavaElement;
//import org.eclipse.jdt.jeview.views.JEAttribute;
//import org.eclipse.jdt.jeview.views.JEResource;
//import org.eclipse.jdt.jeview.views.JavaElement;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;

class JHViewSelectionProvider implements ISelectionProvider {
	private final TableViewer fViewer;

	ListenerList<ISelectionChangedListener> fSelectionChangedListeners= new ListenerList<>();
	private IStructuredSelection fLastSelection;

	public JHViewSelectionProvider(TableViewer viewer) {
		fViewer= viewer;
		fViewer.addSelectionChangedListener(event -> fireSelectionChanged());
	}

	void fireSelectionChanged() {
		if (fSelectionChangedListeners != null) {
			IStructuredSelection selection= getSelection();

			if (fLastSelection != null) {
				List<?> newSelection= selection.toList();
				List<?> oldSelection= fLastSelection.toList();
				int size= newSelection.size();
				if (size == oldSelection.size()) {
					for (int i= 0; i < size; i++) {
						Object newElement= newSelection.get(i);
						Object oldElement= oldSelection.get(i);
						if (newElement != oldElement && newElement.equals(oldElement)
								&& newElement instanceof IJavaElement) {
							// send out a fake selection event to make the Properties view update getKey():
							SelectionChangedEvent event= new SelectionChangedEvent(this, StructuredSelection.EMPTY);
							for (ISelectionChangedListener listener : fSelectionChangedListeners) {
								listener.selectionChanged(event);
							}
							break;
						}
					}
				}
			}
			fLastSelection= selection;

			SelectionChangedEvent event= new SelectionChangedEvent(this, selection);

			for (ISelectionChangedListener listener : fSelectionChangedListeners) {
				listener.selectionChanged(event);
			}
		}
	}

	@Override
	public void addSelectionChangedListener(ISelectionChangedListener listener) {
		fSelectionChangedListeners.add(listener);
	}

	@Override
	public IStructuredSelection getSelection() {
		IStructuredSelection selection= (IStructuredSelection) fViewer.getSelection();
		System.out.println("out:" + selection);
		ArrayList<Object> externalSelection= new ArrayList<>();
		for (Object element : selection) {
			//			if (element instanceof JavaElement) {
			//				IJavaElement javaElement = ((JavaElement) element).getJavaElement();
			//				if (javaElement != null && !(javaElement instanceof IJavaModel)) {
			//					// assume getJavaProject() is
			//					// non-null
			//					externalSelection.add(javaElement);
			//				}
			//			} else if (element instanceof JEResource) {
			//				IResource resource = ((JEResource) element).getResource();
			//				if (resource != null && !(resource instanceof IWorkspaceRoot)) {
			//					// getProject() is non-null
			//					externalSelection.add(resource);
			//				}
			//			} else if (element instanceof JEAttribute) {
			//				Object wrappedObject = ((JEAttribute) element).getWrappedObject();
			//				if (wrappedObject != null) {
			//					externalSelection.add(wrappedObject);
			//				}
			//			}
		}
		return new StructuredSelection(externalSelection);
	}

	@Override
	public void removeSelectionChangedListener(ISelectionChangedListener listener) {
		fSelectionChangedListeners.remove(listener);
	}

	@Override
	public void setSelection(ISelection selection) {
		// not supported
	}
}