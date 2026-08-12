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
package org.sandbox.jdt.cleanup.multifile.ui;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.ViewForm;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.CompareUI;
import org.eclipse.compare.CompareViewerSwitchingPane;
import org.eclipse.compare.IEncodedStreamContentAccessor;
import org.eclipse.compare.IResourceProvider;
import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.structuremergeviewer.DiffNode;
import org.eclipse.compare.structuremergeviewer.ICompareInput;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.TextEditBasedChange;
import org.eclipse.ltk.ui.refactoring.ChangePreviewViewerInput;
import org.eclipse.ltk.ui.refactoring.IChangePreviewViewer;

/**
 * Presents one coordinated multi-file Cleanup candidate without exposing its
 * necessary per-file changes as independently selectable preview entries.
 *
 * <p>The candidate itself remains selectable through the ordinary checkbox in
 * the LTK change tree. This viewer explains the atomic selection contract,
 * lists the affected source files, and provides a read-only diff for each file.
 * It deliberately depends only on the public LTK/Compare APIs. Access to the
 * optional patched-JDT change metadata is reflective so the ordinary stock-JDT
 * product can still resolve this bundle.</p>
 */
public final class CoordinatedCleanUpPreviewViewer implements IChangePreviewViewer {

	private static final String CHANGE_CLASS=
			"org.eclipse.jdt.internal.corext.fix.CoordinatedCleanUpChange"; //$NON-NLS-1$

	private static final String ATOMIC_SELECTION=
			"Selection is atomic: use the single candidate checkbox in the Changes tree to include or exclude every required file."; //$NON-NLS-1$

	private AdaptableViewForm root;
	private Label title;
	private Text description;
	private TableViewer files;
	private Text details;
	private ComparePreviewer comparePreviewer;

	@Override
	public void createControl(Composite parent) {
		CompareConfiguration configuration= new CompareConfiguration();
		configuration.setLeftEditable(false);
		configuration.setRightEditable(false);
		configuration.setLeftLabel("Refactored source"); //$NON-NLS-1$
		configuration.setRightLabel("Original source"); //$NON-NLS-1$

		root= new AdaptableViewForm(parent, configuration);
		Composite body= new Composite(root, SWT.NONE);
		body.setLayout(new GridLayout(1, false));

		title= new Label(body, SWT.WRAP);
		title.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		description= new Text(body, SWT.READ_ONLY | SWT.WRAP | SWT.MULTI);
		GridData descriptionData= new GridData(SWT.FILL, SWT.CENTER, true, false);
		descriptionData.heightHint= 42;
		description.setLayoutData(descriptionData);

		SashForm sash= new SashForm(body, SWT.HORIZONTAL);
		sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		Composite overview= new Composite(sash, SWT.NONE);
		overview.setLayout(new GridLayout(1, false));

		Label filesLabel= new Label(overview, SWT.NONE);
		filesLabel.setText("Affected source files"); //$NON-NLS-1$

		files= new TableViewer(overview, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL | SWT.H_SCROLL);
		files.setContentProvider(ArrayContentProvider.getInstance());
		files.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				return element instanceof FilePreview preview ? preview.label() : super.getText(element);
			}
		});
		GridData filesData= new GridData(SWT.FILL, SWT.FILL, true, true);
		filesData.heightHint= 110;
		files.getControl().setLayoutData(filesData);
		files.addSelectionChangedListener(event -> {
			IStructuredSelection selection= event.getStructuredSelection();
			showFile(selection.getFirstElement() instanceof FilePreview preview ? preview : null);
		});

		Label detailsLabel= new Label(overview, SWT.NONE);
		detailsLabel.setText("Safety and scope evidence"); //$NON-NLS-1$

		details= new Text(overview, SWT.READ_ONLY | SWT.WRAP | SWT.MULTI | SWT.V_SCROLL);
		GridData detailsData= new GridData(SWT.FILL, SWT.FILL, true, true);
		detailsData.heightHint= 90;
		details.setLayoutData(detailsData);

		comparePreviewer= new ComparePreviewer(sash, configuration);
		sash.setWeights(35, 65);

		root.setContent(body);
		Dialog.applyDialogFont(root);
		clear("Select a coordinated Cleanup candidate to inspect its affected files."); //$NON-NLS-1$
	}

	@Override
	public Control getControl() {
		return root;
	}

	@Override
	public void setInput(ChangePreviewViewerInput input) {
		if (root == null || root.isDisposed()) {
			return;
		}
		Change change= input == null ? null : input.getChange();
		if (change == null || !CHANGE_CLASS.equals(change.getClass().getName())) {
			clear("No coordinated Cleanup candidate is selected."); //$NON-NLS-1$
			return;
		}
		try {
			CandidatePreview candidate= readCandidate(change);
			title.setText(candidate.name());
			description.setText(candidate.description().isBlank()
					? ATOMIC_SELECTION
					: candidate.description() + System.lineSeparator() + ATOMIC_SELECTION);
			details.setText(String.join(System.lineSeparator(), candidate.details()));
			files.setInput(candidate.files());
			if (candidate.files().isEmpty()) {
				showFile(null);
			} else {
				files.setSelection(new org.eclipse.jface.viewers.StructuredSelection(candidate.files().get(0)), true);
			}
			root.layout(true, true);
		} catch (CoreException | ReflectiveOperationException | RuntimeException exception) {
			clear("The coordinated Cleanup preview could not be created: " + safeMessage(exception)); //$NON-NLS-1$
		}
	}

	private CandidatePreview readCandidate(Change change)
			throws ReflectiveOperationException, CoreException {
		String candidateDescription= invoke(change, "getDescription", String.class); //$NON-NLS-1$
		List<String> safetyDetails= stringList(invoke(change, "getSafetyDetails", List.class)); //$NON-NLS-1$
		Change[] changes= invoke(change, "getChanges", Change[].class); //$NON-NLS-1$
		List<FilePreview> filePreviews= new ArrayList<>();
		for (Change child : changes) {
			collectFilePreviews(child, filePreviews);
		}
		List<String> allDetails= new ArrayList<>();
		allDetails.add(ATOMIC_SELECTION);
		allDetails.addAll(safetyDetails);
		if (filePreviews.isEmpty()) {
			allDetails.add("No textual source diff is available for this candidate."); //$NON-NLS-1$
		}
		return new CandidatePreview(change.getName(), candidateDescription, List.copyOf(allDetails),
				List.copyOf(filePreviews));
	}

	private static void collectFilePreviews(Change change, List<FilePreview> result) throws CoreException {
		if (change instanceof TextEditBasedChange textChange) {
			NullProgressMonitor monitor= new NullProgressMonitor();
			String current= textChange.getCurrentContent(monitor);
			String preview= textChange.getPreviewContent(new NullProgressMonitor());
			IResource resource= resource(change);
			String label= resource == null
					? change.getName()
					: resource.getProjectRelativePath().toPortableString();
			String type= textChange.getTextType();
			result.add(new FilePreview(label, current, preview,
					type == null || type.isBlank() ? "txt" : type, resource)); //$NON-NLS-1$
			return;
		}
		if (change instanceof CompositeChange composite) {
			for (Change child : composite.getChildren()) {
				collectFilePreviews(child, result);
			}
		}
	}

	private static IResource resource(Change change) {
		Object modified= change.getModifiedElement();
		if (modified instanceof IResource resource) {
			return resource;
		}
		if (modified instanceof IAdaptable adaptable) {
			return adaptable.getAdapter(IResource.class);
		}
		return null;
	}

	private void showFile(FilePreview preview) {
		if (comparePreviewer != null && !comparePreviewer.isDisposed()) {
			comparePreviewer.setPreview(preview);
		}
	}

	private void clear(String message) {
		if (title != null && !title.isDisposed()) {
			title.setText("Coordinated Cleanup preview"); //$NON-NLS-1$
		}
		if (description != null && !description.isDisposed()) {
			description.setText(message == null ? "" : message); //$NON-NLS-1$
		}
		if (details != null && !details.isDisposed()) {
			details.setText(ATOMIC_SELECTION);
		}
		if (files != null && files.getControl() != null && !files.getControl().isDisposed()) {
			files.setInput(List.of());
		}
		showFile(null);
	}

	private static String safeMessage(Throwable throwable) {
		Throwable current= throwable;
		if (throwable instanceof InvocationTargetException invocation && invocation.getCause() != null) {
			current= invocation.getCause();
		}
		String message= current.getMessage();
		return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
	}

	private static List<String> stringList(Object value) {
		if (!(value instanceof List<?> list)) {
			return List.of();
		}
		return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
	}

	private static <T> T invoke(Change change, String methodName, Class<T> expectedType)
			throws ReflectiveOperationException {
		Method method= change.getClass().getMethod(methodName);
		Object value= method.invoke(change);
		return expectedType.cast(value);
	}

	private record CandidatePreview(String name, String description, List<String> details,
			List<FilePreview> files) {
	}

	private record FilePreview(String label, String originalContent, String refactoredContent,
			String type, IResource resource) {
	}

	private static final class AdaptableViewForm extends ViewForm implements IAdaptable {
		private final CompareConfiguration configuration;

		AdaptableViewForm(Composite parent, CompareConfiguration configuration) {
			super(parent, SWT.NONE);
			this.configuration= configuration;
		}

		@Override
		public <T> T getAdapter(Class<T> adapter) {
			if (CompareConfiguration.class.equals(adapter)) {
				return adapter.cast(configuration);
			}
			return null;
		}
	}

	private static final class ComparePreviewer extends CompareViewerSwitchingPane {
		private final CompareConfiguration configuration;

		ComparePreviewer(Composite parent, CompareConfiguration configuration) {
			super(parent, SWT.BORDER | SWT.FLAT, true);
			this.configuration= configuration;
		}

		@Override
		protected Viewer getViewer(Viewer oldViewer, Object input) {
			return CompareUI.findContentViewer(oldViewer, (ICompareInput) input, this, configuration);
		}

		void setPreview(FilePreview preview) {
			if (preview == null) {
				setInput(null);
				return;
			}
			setInput(new DiffNode(
					new CompareElement(preview.refactoredContent(), preview.type(), preview.resource()),
					new CompareElement(preview.originalContent(), preview.type(), preview.resource())));
		}
	}

	private static final class CompareElement
			implements ITypedElement, IEncodedStreamContentAccessor, IResourceProvider {
		private final String content;
		private final String type;
		private final IResource resource;

		CompareElement(String content, String type, IResource resource) {
			this.content= content;
			this.type= type;
			this.resource= resource;
		}

		@Override
		public String getName() {
			return "Source"; //$NON-NLS-1$
		}

		@Override
		public Image getImage() {
			return null;
		}

		@Override
		public String getType() {
			return type;
		}

		@Override
		public InputStream getContents() {
			return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
		}

		@Override
		public String getCharset() {
			return StandardCharsets.UTF_8.name();
		}

		@Override
		public IResource getResource() {
			return resource;
		}
	}
}
