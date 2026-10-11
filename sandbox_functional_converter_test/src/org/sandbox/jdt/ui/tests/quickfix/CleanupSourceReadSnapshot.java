/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix;

import java.nio.charset.Charset;
import java.time.Instant;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.internal.core.BufferManager;
import org.eclipse.jface.text.IDocumentExtension4;

/** Test-only retained observations; no buffer opening, synchronization or repair. */
final class CleanupSourceReadSnapshot {
	private final String before;
	private final Instant sourceReadStart;
	private final long sourceReadStartNanos;
	private String after;

	CleanupSourceReadSnapshot(ICompilationUnit unit) {
		before = state(unit);
		sourceReadStart = Instant.now();
		sourceReadStartNanos = System.nanoTime();
	}

	void sourceReadFinished(ICompilationUnit unit) {
		long sourceReadEndNanos = System.nanoTime();
		Instant sourceReadEnd = Instant.now();
		StringBuilder result = new StringBuilder("\nSource read end: ").append(sourceReadEnd)
				.append("; duration ns: ").append(sourceReadEndNanos - sourceReadStartNanos);
		if (unit.getResource() instanceof IFile file) {
			try {
				long stampBefore = file.getModificationStamp();
				Instant fileReadStart = Instant.now();
				long fileReadStartNanos = System.nanoTime();
				String contents;
				try (var stream = file.getContents()) {
					contents = new String(stream.readAllBytes(), Charset.forName(file.getCharset()));
				}
				long fileReadEndNanos = System.nanoTime();
				result.append("\nRetained file read: ").append(fileReadStart).append(" to ").append(Instant.now())
						.append("; start offset ns: ").append(fileReadStartNanos - sourceReadStartNanos)
						.append("; end offset ns: ").append(fileReadEndNanos - sourceReadStartNanos)
						.append("; stamps: ").append(stampBefore).append(" -> ").append(file.getModificationStamp())
						.append("\nRetained file source immediately after getSource:\n").append(contents);
			} catch (Exception failure) {
				result.append("\nRetained file read failed: ").append(failure);
			}
		}
		after = result.append("\nAfter source/file reads: ").append(state(unit)).toString();
	}

	String describe() {
		return "\nSource-read observation (diagnosis, not repair):\nBefore getSource: " + before
				+ "\nSource read start: " + sourceReadStart + "; monotonic ns: " + sourceReadStartNanos + after;
	}

	private static String state(ICompilationUnit unit) {
		var buffer = BufferManager.getDefaultBufferManager().getBuffer(unit); // Only an existing buffer.
		var fileBuffer = FileBuffers.getTextFileBufferManager().getTextFileBuffer(unit.getPath(), LocationKind.IFILE);
		StringBuilder result = new StringBuilder(identity(unit)).append("; working copy: ").append(unit.isWorkingCopy())
				.append("; open: ").append(unit.isOpen()).append("; CU buffer: ").append(identity(buffer))
				.append("; file buffer: ").append(identity(fileBuffer))
				.append("; model stamp: not captured (no non-opening API)");
		if (fileBuffer != null && fileBuffer.getDocument() instanceof IDocumentExtension4 document) {
			result.append("; document stamp: ").append(document.getModificationStamp());
		}
		for (Job job : Job.getJobManager().find(null)) {
			boolean autoBuild = job.belongsTo(ResourcesPlugin.FAMILY_AUTO_BUILD);
			boolean manualBuild = job.belongsTo(ResourcesPlugin.FAMILY_MANUAL_BUILD);
			if (job.getState() != Job.NONE && (autoBuild || manualBuild || job.getClass().getName().contains(".jdt."))) {
				result.append("\nJob: ").append(identity(job)).append("; name: ").append(job.getName())
						.append("; state: ").append(job.getState()).append("; auto/manual build: ")
						.append(autoBuild).append('/').append(manualBuild);
			}
		}
		return result.toString();
	}

	private static String identity(Object object) {
		return object == null ? "none" : object.getClass().getName() + '@' + Integer.toHexString(System.identityHashCode(object));
	}
}
