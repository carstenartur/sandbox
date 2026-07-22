/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix.Java22;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;

import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.eclipse.jdt.internal.corext.fix.CleanUpPostSaveListener;
import org.eclipse.jdt.internal.corext.fix.CleanUpPreferenceUtil;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;

import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;

import org.sandbox.jdt.internal.corext.fix.IntToEnumCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/** End-to-end QA for single-file save-action isolation. */
@SuppressWarnings("restriction")
public class IntToEnumSaveActionIsolationTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava22();

	private final List<IEditorPart> openedEditors= new ArrayList<>();

	@AfterEach
	void restoreSaveParticipantPreferencesAndCloseEditors() throws Exception {
		if (PlatformUI.isWorkbenchRunning() && PlatformUI.getWorkbench().getActiveWorkbenchWindow() != null) {
			var page= PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
			if (page != null) {
				for (IEditorPart editor : openedEditors) {
					page.closeEditor(editor, false);
				}
			}
		}
		IEclipsePreferences node= InstanceScope.INSTANCE.getNode(JavaUI.ID_PLUGIN);
		node.remove("editor_save_participant_" + CleanUpPostSaveListener.POSTSAVELISTENER_ID); //$NON-NLS-1$
		node.remove(CleanUpPreferenceUtil.SAVE_PARTICIPANT_KEY_PREFIX
				+ CleanUpConstants.CLEANUP_ON_SAVE_ADDITIONAL_OPTIONS);
		node.flush();
	}

	@Test
	void savingOneEditorRunsLocalCleanupWithoutExpandingProjectScope() throws Exception {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		String localOnDisk= """
				package test;

				public class LocalState {
					private static final int STATUS_PENDING = 0;
					private static final int STATUS_APPROVED = 1;

					void run() {
						process(STATUS_PENDING);
					}

					private void process(int status) {
						if (status == STATUS_PENDING) {
							System.out.println("pending");
						} else if (status == STATUS_APPROVED) {
							System.out.println("approved");
						}
					}
				}
				""";
		String localInEditor= localOnDisk.replace("void run() {", "// edited before save\n\tvoid run() {"); //$NON-NLS-1$ //$NON-NLS-2$
		String processorSource= """
				package test;

				public class SharedProcessor {
					static final int STATUS_PENDING = 0;
					static final int STATUS_APPROVED = 1;

					void process(int status) {
						if (status == STATUS_PENDING) {
							System.out.println("pending");
						} else if (status == STATUS_APPROVED) {
							System.out.println("approved");
						}
					}
				}
				""";
		String callerOnDisk= """
				package test;

				public class SharedCaller {
					void run(SharedProcessor processor) {
						processor.process(SharedProcessor.STATUS_PENDING);
					}
				}
				""";
		String callerDirtyBuffer= callerOnDisk.replace("void run", "// unsaved editor marker\n\tvoid run"); //$NON-NLS-1$ //$NON-NLS-2$

		ICompilationUnit local= pack.createCompilationUnit("LocalState.java", localOnDisk, false, null); //$NON-NLS-1$
		ICompilationUnit processor= pack.createCompilationUnit("SharedProcessor.java", processorSource, false, null); //$NON-NLS-1$
		ICompilationUnit caller= pack.createCompilationUnit("SharedCaller.java", callerOnDisk, false, null); //$NON-NLS-1$

		context.enable(MYCleanUpConstants.INT_TO_ENUM_CLEANUP);
		context.disable(IntToEnumCleanUpOptions.PROJECT_WIDE);
		enableSaveParticipant();

		JavaEditor callerEditor= openEditor(caller);
		caller.getBuffer().setContents(callerDirtyBuffer);
		assertTrue(caller.hasUnsavedChanges(), "The unrelated caller editor must be dirty before saving the local file"); //$NON-NLS-1$
		assertTrue(callerEditor.isDirty(), "The unrelated caller editor must report its dirty state"); //$NON-NLS-1$

		JavaEditor localEditor= openEditor(local);
		local.getBuffer().setContents(localInEditor);
		localEditor.doSave(null);

		String savedLocal= Files.readString(local.getResource().getLocation().toFile().toPath(), StandardCharsets.UTF_8);
		assertTrue(savedLocal.contains("enum Status"), "The proven local cleanup must run during save"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(savedLocal.contains("process(Status.PENDING)"), "The local call site must use the generated enum"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(savedLocal.contains("process(Status status)"), "The private local signature must be migrated"); //$NON-NLS-1$ //$NON-NLS-2$
		assertFalse(savedLocal.contains("STATUS_PENDING = 0"), "The local integer constants must be removed"); //$NON-NLS-1$ //$NON-NLS-2$

		assertEquals(processorSource, processor.getBuffer().getContents(),
				"Saving another editor must not migrate the project-wide API owner"); //$NON-NLS-1$
		assertEquals(processorSource,
				Files.readString(processor.getResource().getLocation().toFile().toPath(), StandardCharsets.UTF_8),
				"The API owner on disk must remain unchanged"); //$NON-NLS-1$
		assertEquals(callerDirtyBuffer, caller.getBuffer().getContents(),
				"The unrelated dirty editor buffer must remain untouched"); //$NON-NLS-1$
		assertTrue(caller.hasUnsavedChanges(), "Saving the local editor must not save the unrelated caller editor"); //$NON-NLS-1$
		assertTrue(callerEditor.isDirty(), "Saving the local editor must leave the unrelated editor dirty"); //$NON-NLS-1$
		assertEquals(callerOnDisk,
				Files.readString(caller.getResource().getLocation().toFile().toPath(), StandardCharsets.UTF_8),
				"The unrelated caller resource on disk must remain untouched"); //$NON-NLS-1$
	}

	private void enableSaveParticipant() throws Exception {
		IEclipsePreferences node= InstanceScope.INSTANCE.getNode(JavaUI.ID_PLUGIN);
		node.putBoolean("editor_save_participant_" + CleanUpPostSaveListener.POSTSAVELISTENER_ID, true); //$NON-NLS-1$
		node.put(CleanUpPreferenceUtil.SAVE_PARTICIPANT_KEY_PREFIX
				+ CleanUpConstants.CLEANUP_ON_SAVE_ADDITIONAL_OPTIONS, CleanUpOptions.TRUE);
		node.flush();
	}

	private JavaEditor openEditor(ICompilationUnit unit) throws Exception {
		JavaEditor editor= (JavaEditor) EditorUtility.openInEditor(unit);
		openedEditors.add(editor);
		return editor;
	}
}
