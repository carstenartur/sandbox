/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.preferences.cleanup;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.internal.ui.preferences.cleanup.AbstractCleanUpTabPage;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.ui.fix.MathematicalCleanUp;

import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

/** One profile section with independent numerical, safety, objective and resource choices. */
public final class MathematicalCleanUpTabPage extends AbstractCleanUpTabPage {
	private static final String[] FALSE_TRUE= { CleanUpOptions.FALSE, CleanUpOptions.TRUE };
	private final Map<NumericKind, Button> kindButtons= new EnumMap<>(NumericKind.class);
	private final Map<String, Text> textValues= new HashMap<>();
	private Combo kindPreset;
	private Composite settingsPane;
	private boolean customSelection;
	private boolean statusReady;
	private IStatus fieldStatus;
	private Runnable refresh= () -> { };

	@Override
	public Composite createContents(Composite parent) {
		Composite result= super.createContents(parent);
		Composite content= settingsPane.getParent();
		if (content.getParent() instanceof ScrolledComposite scroll) {
			// JDT's default page layout measures an unconstrained preferred width and
			// can turn horizontal expansion off. Wrapped math descriptions must instead
			// use the actual viewport, including the space occupied by native scrollbars.
			content.setLayout(new Layout() {
				@Override
				protected Point computeSize(Composite composite, int widthHint, int heightHint, boolean flushCache) {
					int width= widthHint == SWT.DEFAULT ? Math.max(1, scroll.getClientArea().width) : widthHint;
					return settingsPane.computeSize(width, heightHint, flushCache);
				}

				@Override
				protected void layout(Composite composite, boolean flushCache) {
					settingsPane.setBounds(composite.getClientArea());
				}
			});
			scroll.setExpandHorizontal(true);
			scroll.setExpandVertical(true);
			scroll.setMinWidth(0);
			scroll.addListener(SWT.Resize, event -> updateScrollMinimum(scroll));
			content.addListener(SWT.Resize, event -> updateScrollMinimum(scroll));
			updateScrollMinimum(scroll);
			content.layout(true, true);
		}
		return result;
	}

	private static void updateScrollMinimum(ScrolledComposite scroll) {
		if (scroll.isDisposed() || scroll.getContent() == null || scroll.getClientArea().width <= 0) { return; }
		int height= scroll.getContent().computeSize(scroll.getClientArea().width, SWT.DEFAULT, true).y;
		if (scroll.getMinHeight() != height) { scroll.setMinHeight(height); }
	}

	@Override
	public void setWorkingValues(Map<String, String> values) {
		MathCleanUpOptions.defaults(25).toMap().forEach(values::putIfAbsent);
		customSelection= !java.util.Set.of("BIG_INTEGER", "INT,LONG", "FLOAT,DOUBLE").contains(values.get(MathCleanUpOptions.KINDS)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		super.setWorkingValues(values);
	}

	@Override
	protected AbstractCleanUp[] createPreviewCleanUps(Map<String, String> values) {
		return new AbstractCleanUp[] { new MathematicalCleanUp(values) };
	}

	@Override
	protected void doCreatePreferences(Composite composite, int columns) {
		settingsPane= composite;
		if (PlatformUI.isWorkbenchRunning()) {
			PlatformUI.getWorkbench().getHelpSystem().setHelp(composite,
					"sandbox_math_cleanup.cleanup_configuration"); //$NON-NLS-1$
		}
		Composite group= MathematicalPreferenceGroup.createBody(
				createGroup(columns, composite, "Verified mathematical calculations")); //$NON-NLS-1$
		CheckboxPreference enabled= createCheckboxPref(group, columns, "Enable explicit mathematics cleanup", //$NON-NLS-1$
				MathCleanUpOptions.CLEANUP, FALSE_TRUE);
		createLabel(columns, group, "Runs locally during explicit cleanup or Quick Assist. Never runs when saving."); //$NON-NLS-1$
		createLabel(columns, group, "Numeric type profile:"); //$NON-NLS-1$
		kindPreset= new Combo(group, SWT.READ_ONLY);
		kindPreset.setItems("Only BigInteger", "int and long", "float and double", "Custom selection"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		kindPreset.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, columns, 1));
		Composite kinds= new Composite(group, SWT.NONE);
		kinds.setLayout(new GridLayout(4, true));
		kinds.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, columns, 1));
		for (NumericKind kind : NumericKind.values()) {
			Button button= new Button(kinds, SWT.CHECK);
			button.setText(kind == NumericKind.BIG_INTEGER ? "BigInteger" : kind.name().toLowerCase(java.util.Locale.ROOT)); //$NON-NLS-1$
			button.addListener(SWT.Selection, event -> {
				customSelection= true;
				fWorkingValues.put(MathCleanUpOptions.KINDS, kindButtons.entrySet().stream()
						.filter(entry -> entry.getValue().getSelection()).map(entry -> entry.getKey().name())
						.collect(Collectors.joining(","))); //$NON-NLS-1$
				changed();
			});
			kindButtons.put(kind, button);
		}
		kindPreset.addListener(SWT.Selection, event -> {
			customSelection= kindPreset.getSelectionIndex() == 3;
			String value= switch (kindPreset.getSelectionIndex()) {
			case 0 -> "BIG_INTEGER"; //$NON-NLS-1$
			case 1 -> "INT,LONG"; //$NON-NLS-1$
			case 2 -> "FLOAT,DOUBLE"; //$NON-NLS-1$
			default -> fWorkingValues.get(MathCleanUpOptions.KINDS);
			};
			fWorkingValues.put(MathCleanUpOptions.KINDS, value);
			changed();
		});
		createLabel(columns, group, "Both declared and promoted operation types must be selected. BigInteger never widens primitive arithmetic."); //$NON-NLS-1$
		ComboPreference safety= createComboPref(group, columns, "Safety profile:", MathCleanUpOptions.SAFETY, //$NON-NLS-1$
				Arrays.stream(SafetyProfile.values()).map(Enum::name).toArray(String[]::new),
				new String[] { "Preserve Java behavior", "Guard with unchanged original fallback", //$NON-NLS-1$ //$NON-NLS-2$
						"Checked: throw ArithmeticException" }); //$NON-NLS-1$
		CheckboxPreference optIn= createCheckboxPref(group, columns,
				"I explicitly accept new ArithmeticException behavior", MathCleanUpOptions.CHECKED_OPT_IN, FALSE_TRUE); //$NON-NLS-1$
		Label warning= wrappedLabel(columns, group, MathCleanUpOptions.CHECKED_WARNING);
		ComboPreference goal= createComboPref(group, columns, "Optimization goal:", MathCleanUpOptions.GOAL, //$NON-NLS-1$
				Arrays.stream(OptimizationGoal.values()).map(Enum::name).toArray(String[]::new),
				new String[] { "Lower estimated runtime", "Lower allocation", "Readability" }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		NumberPreference budget= createNumberPref(group, columns, "Search work budget:", MathCleanUpOptions.WORK_BUDGET, 1, 100_000_000); //$NON-NLS-1$
		NumberPreference states= createNumberPref(group, columns, "Maximum search states:", MathCleanUpOptions.MAX_STATES, 1, 1_000_000); //$NON-NLS-1$
		StringPreference exclusions= createStringPref(group, columns, "Exclude paths (semicolon-separated globs):", //$NON-NLS-1$
				MathCleanUpOptions.EXCLUSIONS, value -> validateExclusions(value));
		textValues.put(MathCleanUpOptions.WORK_BUDGET, (Text) budget.getControl());
		textValues.put(MathCleanUpOptions.MAX_STATES, (Text) states.getControl());
		textValues.put(MathCleanUpOptions.EXCLUSIONS, (Text) exclusions.getControl());
		createLabel(columns, group, "Example exclusions: **/generated/**;org/example/Secret.java"); //$NON-NLS-1$
		Button underflow= new Button(group, SWT.CHECK);
		underflow.setText("Detect tiny-and-inexact underflow (unsupported)"); //$NON-NLS-1$
		underflow.setEnabled(false);
		underflow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, columns, 1));
		wrappedLabel(columns, group, "Unsupported numerical combinations are diagnosed and left unchanged. Estimates are not benchmark measurements."); //$NON-NLS-1$
		// JDT's preference factories put labels and fixed-width inputs side by side.
		// Their shared column minima can exceed the viewport even when the outer
		// content fits. Give each label and input its own full-width, shrinkable row.
		for (Control child : group.getChildren()) {
			GridData data= (GridData) child.getLayoutData();
			data.horizontalSpan= columns;
			data.horizontalAlignment= SWT.FILL;
			data.grabExcessHorizontalSpace= true;
		}
		refresh= () -> {
			boolean active= enabled.getChecked();
			boolean checked= safety.hasValue(SafetyProfile.CHECKED_THROW.name());
			kindPreset.setEnabled(active);
			kindButtons.values().forEach(button -> button.setEnabled(active));
			setEnabledIfChanged(safety, active);
			setEnabledIfChanged(optIn, active && checked);
			warning.setVisible(checked);
			((GridData) warning.getLayoutData()).exclude= !checked;
			setEnabledIfChanged(goal, active);
			setEnabledIfChanged(budget, active);
			setEnabledIfChanged(states, active);
			setEnabledIfChanged(exclusions, active);
			syncKinds();
			group.layout(true, true);
			for (Composite parent= group.getParent(); parent != null; parent= parent.getParent()) {
				parent.layout(true, true);
				if (parent instanceof ScrolledComposite scroll) { updateScrollMinimum(scroll); }
			}
			publishStatus();
		};
		enabled.addObserver((source, value) -> refresh.run());
		safety.addObserver((source, value) -> refresh.run());
		optIn.addObserver((source, value) -> refresh.run());
		// Only the master participates in Select All. Contract-changing consent stays explicit.
		registerPreference(enabled);
		// JDT registers contributed pages only after createContents has returned.
		composite.getShell().addListener(SWT.Show, event -> {
			statusReady= true;
			refresh.run();
		});
		refresh.run();
	}

	private static Label wrappedLabel(int columns, Composite parent, String text) {
		Label label= new Label(parent, SWT.WRAP);
		label.setText(text);
		GridData data= new GridData(SWT.FILL, SWT.CENTER, true, false, columns, 1);
		data.widthHint= 440;
		label.setLayoutData(data);
		return label;
	}

	private static void setEnabledIfChanged(Preference preference, boolean enabled) {
		// JDT setEnabled also restores widget text from the last saved value.
		if (preference.getEnabled() != enabled) { preference.setEnabled(enabled); }
	}

	@Override
	protected void notifyValuesModified() {
		super.notifyValuesModified();
		// The host revalidates profile names on modification, so publish our status last.
		publishStatus();
	}

	@Override
	protected void updateStatus(IStatus status) {
		fieldStatus= status;
		publishStatus();
	}

	private void publishStatus() {
		if (!statusReady) { return; }
		IStatus status= fieldStatus;
		if (status == null || status.isOK()) {
			try {
				Map<String, String> displayedValues= new HashMap<>(fWorkingValues);
				textValues.forEach((key, control) -> displayedValues.put(key, control.getText().trim()));
				MathCleanUpOptions.parse(displayedValues, 25);
				status= null;
			} catch (IllegalArgumentException e) {
				status= Status.error(e.getMessage());
			}
		}
		// null preserves the host's profile-name and built-in-profile validation.
		super.updateStatus(status);
	}

	@Override
	public void resetValues() {
		super.resetValues();
		refresh.run();
	}

	@Override
	public void setDefaults() {
		super.setDefaults();
		refresh.run();
	}

	private void changed() {
		refresh.run();
		doUpdatePreview();
		notifyValuesModified();
	}

	private void syncKinds() {
		String value= fWorkingValues.get(MathCleanUpOptions.KINDS);
		var selected= Arrays.stream(value.split(",")).map(String::trim).toList(); //$NON-NLS-1$
		kindButtons.forEach((kind, button) -> button.setSelection(selected.contains(kind.name())));
		kindPreset.select(customSelection ? 3 : switch (value) {
		case "BIG_INTEGER" -> 0; //$NON-NLS-1$
		case "INT,LONG" -> 1; //$NON-NLS-1$
		case "FLOAT,DOUBLE" -> 2; //$NON-NLS-1$
		default -> 3;
		});
	}

	private String validateExclusions(String value) {
		try {
			Map<String, String> values= new HashMap<>(MathCleanUpOptions.defaults(25).toMap());
			values.put(MathCleanUpOptions.EXCLUSIONS, value);
			MathCleanUpOptions.parse(values, 25);
			return null;
		} catch (IllegalArgumentException e) {
			return e.getMessage();
		}
	}
}
