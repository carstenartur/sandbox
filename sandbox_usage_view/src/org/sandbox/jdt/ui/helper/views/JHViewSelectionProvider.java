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
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;

/**
 * Selection provider for the variable table viewer that bridges the internal
 * table selection to the workbench selection mechanism.
 */
class JHViewSelectionProvider implements ISelectionProvider {
	private final TableViewer tableViewer;

	ListenerList<ISelectionChangedListener> selectionChangedListeners = new ListenerList<>();
	private IStructuredSelection lastSelection;

	public JHViewSelectionProvider(TableViewer tableViewer) {
		this.tableViewer = tableViewer;
		this.tableViewer.addSelectionChangedListener(event -> fireSelectionChanged());
	}

	void fireSelectionChanged() {
		if (selectionChangedListeners != null) {
			IStructuredSelection selection = getSelection();

			if (lastSelection != null) {
				List<?> newSelection = selection.toList();
				List<?> oldSelection = lastSelection.toList();
				int size = newSelection.size();
				if (size == oldSelection.size()) {
					for (int i = 0; i < size; i++) {
						Object newElement = newSelection.get(i);
						Object oldElement = oldSelection.get(i);
						if (newElement != oldElement && newElement.equals(oldElement)
								&& newElement instanceof IJavaElement) {
							// send out a fake selection event to make the Properties view update getKey():
							SelectionChangedEvent event = new SelectionChangedEvent(this, StructuredSelection.EMPTY);
							for (ISelectionChangedListener listener : selectionChangedListeners) {
								listener.selectionChanged(event);
							}
							break;
						}
					}
				}
			}
			lastSelection = selection;

			SelectionChangedEvent event = new SelectionChangedEvent(this, selection);

			for (ISelectionChangedListener listener : selectionChangedListeners) {
				listener.selectionChanged(event);
			}
		}
	}

	@Override
	public void addSelectionChangedListener(ISelectionChangedListener listener) {
		selectionChangedListeners.add(listener);
	}

	@Override
	public IStructuredSelection getSelection() {
		IStructuredSelection selection = (IStructuredSelection) tableViewer.getSelection();
		// Debug logging removed - uncomment if needed: logger.log(new Status(Status.INFO, UsageViewPlugin.PLUGIN_ID, "Selection: " + selection));
		ArrayList<Object> externalSelection = new ArrayList<>();
		// Variable bindings from the table can be added to external selection
		// if needed for integration with other views
		// Currently returns empty selection - extend if needed
		return new StructuredSelection(externalSelection);
	}

	@Override
	public void removeSelectionChangedListener(ISelectionChangedListener listener) {
		selectionChangedListeners.remove(listener);
	}

	@Override
	public void setSelection(ISelection selection) {
		// not supported
	}
}