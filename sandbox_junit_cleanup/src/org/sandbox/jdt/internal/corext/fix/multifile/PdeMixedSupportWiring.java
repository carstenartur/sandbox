/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.multifile;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.osgi.service.resolver.BaseDescription;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.ExportPackageDescription;
import org.eclipse.osgi.service.resolver.PlatformAdmin;
import org.eclipse.pde.core.plugin.PluginRegistry;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.sandbox.jdt.internal.common.MigrationSupportProvider;
import org.sandbox.jdt.internal.common.PdeManifestImportUpdater.Exporter;

/** The type identities used by the generated factory call in a PDE consumer. */
public final class PdeMixedSupportWiring {
	private static final String API= "org.junit.jupiter.api"; //$NON-NLS-1$
	private static final String EXTENSION= API + ".extension"; //$NON-NLS-1$
	private static final String FUNCTION= API + ".function"; //$NON-NLS-1$
	private static final String PARALLEL= API + ".parallel"; //$NON-NLS-1$
	private static final List<String> PLATFORM= List.of("org.junit.platform.engine", "org.junit.platform.launcher"); //$NON-NLS-1$ //$NON-NLS-2$
	private static final List<String> TYPES= List.of(API + ".TestInstance", EXTENSION + ".Extension", //$NON-NLS-1$ //$NON-NLS-2$
			FUNCTION + ".ThrowingConsumer", PARALLEL + ".Isolated", MigrationSupportProvider.FACTORY); //$NON-NLS-1$ //$NON-NLS-2$

	private PdeMixedSupportWiring() {}

	/** Identify the consumer's current Jupiter API when the native preview adds a package. */
	public static Exporter jupiterExporter(IJavaProject java, String pkg) throws JavaModelException {
		var model= PluginRegistry.findModel(java.getProject());
		if (model == null || model.getBundleDescription() == null) return null;
		for (String type : List.of(API + ".Test", EXTENSION + ".Extension")) { //$NON-NLS-1$ //$NON-NLS-2$
			var binary= MigrationSupportProvider.binaryLocation(java, type);
			if (binary == null) continue;
			var supplier= targetBundle(model.getBundleDescription(), binary);
			if (supplier == null) continue;
			if (Arrays.stream(supplier.getExportPackages()).noneMatch(cap -> pkg.equals(cap.getName()))) {
				throw new IllegalStateException("Selected Jupiter provider does not export " + pkg); //$NON-NLS-1$
			}
			return new Exporter(supplier.getSymbolicName(), supplier.getVersion().toString());
		}
		return null;
	}

	static boolean available(IJavaProject java) throws JavaModelException {
		var model= PluginRegistry.findModel(java.getProject());
		if (model == null || model.getBundleDescription() == null) return false;
		var consumer= model.getBundleDescription();
		var apiJar= MigrationSupportProvider.binaryLocation(java, TYPES.getFirst());
		var supportJar= MigrationSupportProvider.binaryLocation(java, MigrationSupportProvider.FACTORY);
		if (apiJar == null || supportJar == null) return false;
		for (String type : TYPES) {
			var expected= type.equals(MigrationSupportProvider.FACTORY) ? supportJar : apiJar;
			if (!expected.equals(MigrationSupportProvider.binaryLocation(java, type))) return false;
		}
		var api= targetBundle(consumer, apiJar);
		var support= targetBundle(consumer, supportJar);
		if (api == null || support == null || !MigrationSupportProvider.BUNDLE.equals(support.getSymbolicName())) return false;
		boolean constrainsCallbacks= Arrays.stream(support.getExportPackages())
				.filter(pkg -> MigrationSupportProvider.PACKAGE.equals(pkg.getName()))
				.anyMatch(pkg -> pkg.getDirective(Constants.USES_DIRECTIVE) instanceof String[] uses
						&& Arrays.asList(uses).containsAll(List.of(EXTENSION, FUNCTION)));
		if (!constrainsCallbacks) return false;

		// PDE development resolution deliberately skips OSGi uses constraints. Resolve a private
		// copy of the selected target, including the real workspace manifests and host fragments.
		var live= consumer.getContainingState();
		var state= live.getFactory().createState(live);
		Dictionary<?, ?>[] original= live.getPlatformProperties();
		var properties= new Dictionary<?, ?>[original.length];
		for (int i= 0; i < original.length; i++) {
			var copy= new Hashtable<Object, Object>();
			for (var keys= original[i].keys(); keys.hasMoreElements();) {
				var key= keys.nextElement();
				copy.put(key, original[i].get(key));
			}
			copy.remove("osgi.resolverMode"); //$NON-NLS-1$
			copy.put("osgi.resolver.usesMode", "aggressive"); //$NON-NLS-1$ //$NON-NLS-2$
			properties[i]= copy;
		}
		state.setPlatformProperties(properties);
		var context= FrameworkUtil.getBundle(PdeMixedSupportWiring.class).getBundleContext();
		var reference= context.getServiceReference(PlatformAdmin.class);
		if (reference == null) return false;
		try {
			var admin= context.getService(reference);
			if (admin == null) return false;
			var resolver= admin.createResolver();
			// A Jupiter API bundle imports its own exported packages. When a new import
			// requires this precise exporter, keep its export ahead of substitutes.
			boolean canSubstitute= Arrays.stream(live.getBundles(api.getSymbolicName())).anyMatch(other ->
					other.getVersion().getMajor() == api.getVersion().getMajor() && other.getVersion().compareTo(api.getVersion()) > 0);
			if (canSubstitute && Arrays.stream(consumer.getImportPackages()).anyMatch(pkg -> (pkg.getName().equals(API) || pkg.getName().startsWith(API + '.'))
					&& api.getSymbolicName().equals(pkg.getBundleSymbolicName())
					&& pkg.getBundleVersionRange().isExact() && pkg.getBundleVersionRange().includes(api.getVersion()))) {
				var preferred= new HashSet<Long>();
				preferred.add(api.getBundleId());
				for (String pkg : PLATFORM) {
					var supplier= exporter(consumer, pkg);
					if (supplier != null) preferred.add(supplier.getBundleId());
				}
				resolver.setSelectionPolicy(Comparator.comparing((BaseDescription cap) -> !preferred.contains(cap.getSupplier().getBundleId()))
						.thenComparing(BaseDescription::getVersion, Comparator.reverseOrder())
						.thenComparingLong(cap -> cap.getSupplier().getBundleId()));
			}
			state.setResolver(resolver);
			state.resolve(false);
			var resolved= state.getBundle(consumer.getBundleId());
			var selectedApi= state.getBundle(api.getBundleId());
			var selectedSupport= state.getBundle(support.getBundleId());
			return coherent(resolved, selectedApi, selectedSupport) && samePlatformSuppliers(consumer, resolved);
		} finally {
			context.ungetService(reference);
		}
	}

	private static BundleDescription targetBundle(BundleDescription consumer, Path jar) {
		for (var target : PluginRegistry.getActiveModels()) {
			if (target.getInstallLocation() != null && jar.equals(Path.of(target.getInstallLocation()))) {
				var installed= target.getBundleDescription();
				if (installed == null) return null;
				var candidates= Arrays.stream(consumer.getContainingState().getBundles(installed.getSymbolicName()))
						.filter(bundle -> installed.getVersion().equals(bundle.getVersion())).toList();
				return candidates.size() == 1 ? candidates.getFirst() : null;
			}
		}
		return null;
	}

	private static boolean coherent(BundleDescription consumer, BundleDescription api, BundleDescription support) {
		if (!consumer.isResolved() || !support.isResolved() || !api.isResolved()
				|| exporter(consumer, MigrationSupportProvider.PACKAGE) != support) return false;
		for (String pkg : List.of(API, EXTENSION, FUNCTION, PARALLEL)) if (exporter(consumer, pkg) != api) return false;
		return exporter(support, EXTENSION) == api && exporter(support, FUNCTION) == api && samePlatformSuppliers(consumer, support);
	}

	private static boolean samePlatformSuppliers(BundleDescription consumer, BundleDescription other) {
		for (String pkg : PLATFORM) {
			var expected= exporter(consumer, pkg);
			var actual= exporter(other, pkg);
			if (expected != null && (actual == null || expected.getBundleId() != actual.getBundleId())) return false;
		}
		return true;
	}

	private static BundleDescription exporter(BundleDescription bundle, String pkg) {
		var direct= Arrays.stream(bundle.getResolvedImports()).filter(wire -> pkg.equals(wire.getName())).findFirst();
		if (direct.isPresent()) return direct.get().getExporter();
		var visible= bundle.getContainingState().getStateHelper().getVisiblePackages(bundle);
		var suppliers= Arrays.stream(visible).filter(wire -> pkg.equals(wire.getName()))
				.map(ExportPackageDescription::getExporter).distinct().toList();
		return suppliers.size() == 1 ? suppliers.getFirst() : null;
	}
}
