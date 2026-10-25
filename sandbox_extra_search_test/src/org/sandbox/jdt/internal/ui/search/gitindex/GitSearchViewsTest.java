/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.search.gitindex;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.stream.Stream;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.Test;

class GitSearchViewsTest {

	@Test void searchAsksForRepository() {
		var shell= new Shell();
		try {
			new GitSearchView().createPartControl(shell);
			assertTrue(widgets(shell).filter(Label.class::isInstance).map(Label.class::cast)
					.anyMatch(label -> label.getText().equals("Repository:")));
		} finally { shell.dispose(); }
	}

	@Test void historyAsksForAnExactPathAndRepository() {
		var shell= new Shell();
		try {
			new JavaTypeHistoryView().createPartControl(shell);
			var labels= widgets(shell).filter(Label.class::isInstance).map(Label.class::cast).map(Label::getText).toList();
			assertTrue(labels.contains("Repository:"));
			assertTrue(labels.contains("File path:"));
		} finally { shell.dispose(); }
	}

	@Test void analyticsRepositoryCanBeEntered() {
		var shell= new Shell();
		try {
			new CommitAnalyticsView().createPartControl(shell);
			Combo input= widgets(shell).filter(Combo.class::isInstance).map(Combo.class::cast).findFirst().orElseThrow();
			assertEquals(0, input.getStyle() & SWT.READ_ONLY);
		} finally { shell.dispose(); }
	}

	static Stream<Control> widgets(Composite parent) {
		return Arrays.stream(parent.getChildren()).flatMap(control -> control instanceof Composite composite
				? Stream.concat(Stream.of(control), widgets(composite)) : Stream.of(control));
	}
}
