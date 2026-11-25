/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.jar.JarFile;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.util.IClassFileReader;
import org.eclipse.jdt.core.ToolFactory;

/** The packaged, deferred-supplier API required by automatically generated registrations. */
public final class MigrationSupportProvider {
	public static final String PACKAGE= "org.sandbox.junit.jupiter"; //$NON-NLS-1$
	public static final String FACTORY= PACKAGE + ".ExternalResourceExtension"; //$NON-NLS-1$
	public static final String BUNDLE= "org.sandbox.junit.migration.support"; //$NON-NLS-1$
	private static final String SIGNATURE= "(Ljava/util/function/Supplier;Lorg/junit/jupiter/api/function/ThrowingConsumer;Ljava/util/function/Consumer;)Lorg/junit/jupiter/api/extension/Extension;"; //$NON-NLS-1$

	private MigrationSupportProvider() {}

	public static boolean available(IJavaProject project) throws JavaModelException {
		var type= project.findType(FACTORY);
		if (type == null || !type.isBinary()) return false;
		var root= type.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
		var resource= root.getResource();
		var location= resource == null ? root.getPath() : resource.getLocation();
		return location != null && compatible(location.toFile().toPath());
	}

	public static boolean compatible(Path jar) {
		if (!Files.isRegularFile(jar)) return false;
		try (var archive= new JarFile(jar.toFile())) {
			var manifest= archive.getManifest();
			if (manifest == null) return false;
			var attributes= manifest.getMainAttributes();
			String name= attributes.getValue("Bundle-SymbolicName"); //$NON-NLS-1$
			if (name == null || !BUNDLE.equals(name.split(";", 2)[0].strip())) return false; //$NON-NLS-1$
			var entry= archive.getJarEntry(FACTORY.replace('.', '/') + ".class"); //$NON-NLS-1$
			if (entry == null) return false;
			try (var input= archive.getInputStream(entry)) {
				var reader= ToolFactory.createDefaultClassFileReader(input, IClassFileReader.METHOD_INFOS);
				if (reader == null || !Flags.isPublic(reader.getAccessFlags())) return false;
				return Arrays.stream(reader.getMethodInfos())
						.filter(method -> Flags.isPublic(method.getAccessFlags()) && Flags.isStatic(method.getAccessFlags())
								&& SIGNATURE.equals(new String(method.getDescriptor())))
						.map(method -> new String(method.getName())).collect(java.util.stream.Collectors.toSet())
						.containsAll(Set.of("perTest", "perClass")); //$NON-NLS-1$ //$NON-NLS-2$
			}
		} catch (IOException e) {
			return false;
		}
	}
}
