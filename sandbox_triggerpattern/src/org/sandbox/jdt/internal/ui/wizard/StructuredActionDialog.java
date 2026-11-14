/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.wizard;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Stream;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.sandbox.jdt.triggerpattern.api.RewriteActionCatalog;
import org.sandbox.jdt.triggerpattern.api.RewriteActionSchema;

/** Builds one action from the same schemas used to validate the hint document. */
final class StructuredActionDialog extends TitleAreaDialog {

	private final RewriteActionCatalog catalog= RewriteActionCatalog.standard();
	private final Map<String, Text> fields= new LinkedHashMap<>();
	private Combo actions;
	private Composite arguments;
	private Text preview;
	private RewriteActionSchema schema;
	private String action;

	StructuredActionDialog(Shell parent) {
		super(parent);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite area= (Composite) super.createDialogArea(parent);
		setTitle("Build a structured action"); //$NON-NLS-1$
		Composite form= new Composite(area, SWT.NONE);
		form.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		form.setLayout(new GridLayout(2, false));
		new Label(form, SWT.NONE).setText("Action"); //$NON-NLS-1$
		actions= new Combo(form, SWT.READ_ONLY | SWT.DROP_DOWN);
		actions.setItems(catalog.names().stream().sorted().toArray(String[]::new));
		actions.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		arguments= new Composite(form, SWT.NONE);
		arguments.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
		arguments.setLayout(new GridLayout(2, false));
		new Label(form, SWT.NONE).setText("Insert"); //$NON-NLS-1$
		preview= new Text(form, SWT.READ_ONLY | SWT.BORDER | SWT.SINGLE);
		preview.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		actions.addListener(SWT.Selection, event -> showArguments());
		actions.select(0);
		showArguments();
		return area;
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.OK_ID, "Insert", true); //$NON-NLS-1$
		createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
		updatePreview();
	}

	private void showArguments() {
		schema= catalog.schema(actions.getText()).orElseThrow();
		setMessage(schema.description());
		for (Control old : arguments.getChildren()) {
			old.dispose();
		}
		fields.clear();
		Stream.concat(schema.requiredArguments().stream().sorted(), schema.optionalArguments().stream().sorted())
				.forEach(name -> {
					new Label(arguments, SWT.NONE).setText(schema.requiredArguments().contains(name)
							? name : name + " (optional)"); //$NON-NLS-1$
					Text field= new Text(arguments, SWT.SINGLE | SWT.BORDER);
					field.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
					fields.put(name, field);
					field.addModifyListener(event -> updatePreview());
				});
		arguments.requestLayout();
		updatePreview();
		if (getShell().isVisible()) {
			getShell().pack();
		}
	}

	private void updatePreview() {
		var expression= new StringJoiner(", ", schema.name() + "(", ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		String error= null;
		for (var entry : fields.entrySet()) {
			String name= entry.getKey();
			String value= entry.getValue().getText().strip();
			if (value.isEmpty()) {
				if (schema.requiredArguments().contains(name) && error == null) {
					error= "Enter " + name + "."; //$NON-NLS-1$ //$NON-NLS-2$
				}
				continue;
			}
			if (name.equals("target") || value.startsWith("$")) { //$NON-NLS-1$ //$NON-NLS-2$
				if (!value.matches("\\$\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*")) { //$NON-NLS-1$
					error= "Enter a binding such as $method."; //$NON-NLS-1$
				}
			} else if (name.equals("index")) { //$NON-NLS-1$
				try {
					int index= Integer.parseInt(value);
					if (index < 0) {
						error= "Enter a nonnegative integer index."; //$NON-NLS-1$
					} else {
						value= Integer.toString(index);
					}
				} catch (NumberFormatException invalid) {
					error= "Enter a nonnegative integer index."; //$NON-NLS-1$
				}
			} else {
				value= "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
			}
			expression.add(name + "=" + value); //$NON-NLS-1$
		}
		if (schema.optionalArguments().contains("name") && schema.optionalArguments().contains("index") //$NON-NLS-1$ //$NON-NLS-2$
				&& fields.get("name").getText().isBlank() == fields.get("index").getText().isBlank()) { //$NON-NLS-1$ //$NON-NLS-2$
			error= "Select one parameter using either its name or its index."; //$NON-NLS-1$
		}
		action= error == null ? expression.toString() : null;
		setErrorMessage(error);
		preview.setText(action == null ? "" : action); //$NON-NLS-1$
		if (getButton(IDialogConstants.OK_ID) != null) {
			getButton(IDialogConstants.OK_ID).setEnabled(action != null);
		}
	}

	String action() {
		return action;
	}
}
