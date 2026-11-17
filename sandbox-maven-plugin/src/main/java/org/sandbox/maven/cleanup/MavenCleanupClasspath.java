/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.maven.cleanup;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.util.filter.DependencyFilterUtils;

/** Resolves the Maven classpath only for a synthesized Java project and the selected source sets. */
final class MavenCleanupClasspath {
	private MavenCleanupClasspath() {}

	static MavenProject resolve(MavenProject project, RepositorySystemSession session,
			ProjectDependenciesResolver resolver, boolean includeTests) throws IOException {
		var request = request(project, session, includeTests);
		MavenProject scoped = request.getMavenProject();
		try {
			var graph = resolver.resolve(request).getDependencyGraph();
			Set<Artifact> artifacts = new LinkedHashSet<>();
			if (graph != null && !graph.getChildren().isEmpty()) {
				RepositoryUtils.toArtifacts(artifacts, graph.getChildren(), List.of(project.getArtifact().getId()), request.getResolutionFilter());
			}
			scoped.setArtifacts(artifacts);
			return scoped;
		} catch (DependencyResolutionException e) {
			throw new IOException("Cannot resolve the Maven " + (includeTests ? "test" : "compile") + " classpath for " + project.getId() + ": " + e.getMessage(), e);
		}
	}

	static DependencyResolutionRequest request(MavenProject project, RepositorySystemSession session, boolean includeTests) {
		MavenProject scoped = project.clone();
		String scope = includeTests ? Artifact.SCOPE_TEST : Artifact.SCOPE_COMPILE;
		if (!includeTests) {
			// The resolution filter controls artifact downloads, but collection also reads POMs. Exclude
			// irrelevant direct roots before collection, including reactor test-JARs that do not yet exist.
			scoped.getDependencies().removeIf(dep -> !onCompileClasspath(dep.getScope()));
			if (scoped.getDependencyArtifacts() != null) {
				var direct = new LinkedHashSet<>(scoped.getDependencyArtifacts());
				direct.removeIf(artifact -> !onCompileClasspath(artifact.getScope()));
				scoped.setDependencyArtifacts(direct);
			}
		}
		var filter = DependencyFilterUtils.classpathFilter(scope);
		return new DefaultDependencyResolutionRequest(scoped, session).setResolutionFilter(filter);
	}

	private static boolean onCompileClasspath(String scope) {
		return scope == null || Artifact.SCOPE_COMPILE.equals(scope) || Artifact.SCOPE_PROVIDED.equals(scope)
				|| Artifact.SCOPE_SYSTEM.equals(scope);
	}
}
