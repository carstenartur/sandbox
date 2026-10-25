/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.repository;

import java.io.IOException;

/** Physical repository inventory; object counts include stored unreachable objects. */
public interface SandboxStorageStatistics {
	ObjectCounts objects(String repository) throws IOException;
	PackCounts packs(String repository) throws IOException;

	record ObjectCounts(long commits, long trees, long blobs, long tags) {}
	record PackCounts(long packCount, long totalSizeBytes) {}
}
