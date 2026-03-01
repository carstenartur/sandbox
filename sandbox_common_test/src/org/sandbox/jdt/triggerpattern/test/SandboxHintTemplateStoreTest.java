/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
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
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jface.text.templates.Template;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.editor.SandboxHintTemplateStore;

/**
 * Tests for {@link SandboxHintTemplateStore}.
 *
 * <p>Verifies that templates are correctly loaded and contain the expected
 * guard, fix, rule, and metadata templates.</p>
 *
 * @since 1.5.0
 */
public class SandboxHintTemplateStoreTest {

	@Test
	public void testTemplatesNotNull() {
		Template[] templates = SandboxHintTemplateStore.getTemplates();
		assertNotNull(templates, "Templates array should not be null"); //$NON-NLS-1$
	}

	@Test
	public void testFourTemplatesExist() {
		Template[] templates = SandboxHintTemplateStore.getTemplates();
		assertEquals(4, templates.length, "Should have 4 templates"); //$NON-NLS-1$
	}

	@Test
	public void testTemplateNamesPresent() {
		Template[] templates = SandboxHintTemplateStore.getTemplates();
		Set<String> names = Arrays.stream(templates)
				.map(Template::getName)
				.collect(Collectors.toSet());

		assertTrue(names.contains("guard"), "Should contain 'guard' template"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(names.contains("fix"), "Should contain 'fix' template"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(names.contains("rule"), "Should contain 'rule' template"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(names.contains("meta"), "Should contain 'meta' template"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void testGuardTemplateContainsExpectedContent() {
		Template[] templates = SandboxHintTemplateStore.getTemplates();
		Template guard = Arrays.stream(templates)
				.filter(t -> "guard".equals(t.getName())) //$NON-NLS-1$
				.findFirst()
				.orElse(null);

		assertNotNull(guard);
		assertTrue(guard.getPattern().contains("public boolean"), //$NON-NLS-1$
				"Guard template should contain 'public boolean'"); //$NON-NLS-1$
		assertTrue(guard.getPattern().contains("<?"), //$NON-NLS-1$
				"Guard template should contain '<?'"); //$NON-NLS-1$
	}

	@Test
	public void testFixTemplateContainsFixFunctionAnnotation() {
		Template[] templates = SandboxHintTemplateStore.getTemplates();
		Template fix = Arrays.stream(templates)
				.filter(t -> "fix".equals(t.getName())) //$NON-NLS-1$
				.findFirst()
				.orElse(null);

		assertNotNull(fix);
		assertTrue(fix.getPattern().contains("@FixFunction"), //$NON-NLS-1$
				"Fix template should contain '@FixFunction'"); //$NON-NLS-1$
		assertTrue(fix.getPattern().contains("public void"), //$NON-NLS-1$
				"Fix template should contain 'public void'"); //$NON-NLS-1$
	}

	@Test
	public void testContextTypeIdNotEmpty() {
		assertNotNull(SandboxHintTemplateStore.CONTEXT_TYPE_ID);
		assertTrue(!SandboxHintTemplateStore.CONTEXT_TYPE_ID.isEmpty(),
				"Context type ID should not be empty"); //$NON-NLS-1$
	}

	@Test
	public void testGetTemplatesReturnsCopy() {
		Template[] first = SandboxHintTemplateStore.getTemplates();
		Template[] second = SandboxHintTemplateStore.getTemplates();
		assertTrue(first != second, "getTemplates() should return a copy"); //$NON-NLS-1$
		assertEquals(first.length, second.length);
	}
}
