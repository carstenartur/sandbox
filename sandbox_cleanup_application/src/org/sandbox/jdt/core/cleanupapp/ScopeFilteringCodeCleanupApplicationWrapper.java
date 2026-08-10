/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.core.cleanupapp;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.internal.core.util.Util;
import org.osgi.framework.Bundle;

/**
 * Public application wrapper that resolves {@code main}/{@code test} scope to
 * concrete Java files before delegating to the cleanup engine.
 */
public final class ScopeFilteringCodeCleanupApplicationWrapper extends CodeCleanupApplicationWrapper {

	private static final String ARG_CONFIG= "-config"; //$NON-NLS-1$
	private static final String ARG_CONFIG_LONG= "--config"; //$NON-NLS-1$
	private static final String ARG_IMPORT_PROJECT= "--import-project"; //$NON-NLS-1$
	private static final String ARG_MODE= "--mode"; //$NON-NLS-1$
	private static final String ARG_PATCH= "--patch"; //$NON-NLS-1$
	private static final String ARG_REPORT= "--report"; //$NON-NLS-1$
	private static final String ARG_SCOPE= "--scope"; //$NON-NLS-1$
	private static final String ARG_SOURCE= "--source"; //$NON-NLS-1$

	private enum RequestedScope {
		MAIN, TEST, BOTH, INVALID
	}

	private record FilteredArguments(String[] arguments, boolean hasCleanupInput) {
		FilteredArguments {
			arguments= arguments.clone();
		}

		@Override
		public String[] arguments() {
			return arguments.clone();
		}
	}

	private static final class ArgumentContext implements IApplicationContext {
		private final IApplicationContext delegate;
		private final String[] applicationArguments;

		ArgumentContext(IApplicationContext delegate, String[] applicationArguments) {
			this.delegate= delegate;
			this.applicationArguments= applicationArguments.clone();
		}

		@Override
		@SuppressWarnings({ "rawtypes", "unchecked" })
		public Map getArguments() {
			Map forwarded= new HashMap(delegate.getArguments());
			forwarded.put(APPLICATION_ARGS, applicationArguments.clone());
			return forwarded;
		}

		@Override
		public void applicationRunning() {
			delegate.applicationRunning();
		}

		@Override
		public String getBrandingApplication() {
			return delegate.getBrandingApplication();
		}

		@Override
		public Bundle getBrandingBundle() {
			return delegate.getBrandingBundle();
		}

		@Override
		public String getBrandingDescription() {
			return delegate.getBrandingDescription();
		}

		@Override
		public String getBrandingId() {
			return delegate.getBrandingId();
		}

		@Override
		public String getBrandingName() {
			return delegate.getBrandingName();
		}

		@Override
		public String getBrandingProperty(String key) {
			return delegate.getBrandingProperty(key);
		}

		@Override
		public void setResult(Object result, IApplication application) {
			delegate.setResult(result, application);
		}
	}

	@Override
	public Object start(IApplicationContext context) throws NoClassDefFoundError, Exception {
		String[] arguments= (String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
		RequestedScope scope= requestedScope(arguments);
		if (scope == RequestedScope.BOTH || scope == RequestedScope.INVALID) {
			return super.start(context);
		}

		Path emptyInput= null;
		IOException deletionFailure= null;
		Object result;
		try {
			FilteredArguments filtered= filterArguments(arguments, scope);
			String[] forwarded= filtered.arguments();
			if (!filtered.hasCleanupInput()) {
				emptyInput= Files.createTempDirectory("sandbox-cleanup-empty-scope-"); //$NON-NLS-1$
				forwarded= Arrays.copyOf(forwarded, forwarded.length + 1);
				forwarded[forwarded.length - 1]= emptyInput.toString();
			}
			result= super.start(new ArgumentContext(context, forwarded));
		} catch (IOException e) {
			System.err.println("Cannot enumerate cleanup source scope: " + e.getMessage()); //$NON-NLS-1$
			return Integer.valueOf(CodeCleanupApplication.EXIT_ERROR);
		} finally {
			if (emptyInput != null) {
				try {
					Files.deleteIfExists(emptyInput);
				} catch (IOException e) {
					deletionFailure= e;
				}
			}
		}
		if (deletionFailure != null) {
			System.err.println("Cannot remove temporary cleanup scope directory: " //$NON-NLS-1$
					+ deletionFailure.getMessage());
			return Integer.valueOf(CodeCleanupApplication.EXIT_ERROR);
		}
		return result;
	}

	static String[] filterArgumentsForScope(String[] arguments) throws IOException {
		RequestedScope scope= requestedScope(arguments);
		if (scope == RequestedScope.BOTH || scope == RequestedScope.INVALID) {
			return arguments.clone();
		}
		return filterArguments(arguments, scope).arguments();
	}

	private static FilteredArguments filterArguments(String[] arguments, RequestedScope scope) throws IOException {
		List<String> result= new ArrayList<>();
		boolean hasCleanupInput= false;
		for (int index= 0; index < arguments.length; index++) {
			String argument= arguments[index];
			if (ARG_SOURCE.equals(argument)) {
				result.add(argument);
				if (++index >= arguments.length) {
					// Keep the incomplete option intact so the delegate reports a syntax
					// error instead of receiving the empty-scope fallback as its value.
					hasCleanupInput= true;
					break;
				}
				List<String> selected= expandRoot(arguments[index], scope);
				if (selected.isEmpty()) {
					result.remove(result.size() - 1);
				} else {
					result.add(selected.getFirst());
					for (int selectedIndex= 1; selectedIndex < selected.size(); selectedIndex++) {
						result.add(ARG_SOURCE);
						result.add(selected.get(selectedIndex));
					}
					hasCleanupInput= true;
				}
				continue;
			}
			if (takesValue(argument)) {
				result.add(argument);
				if (++index < arguments.length) {
					result.add(arguments[index]);
				}
				continue;
			}
			if (argument.startsWith("-")) { //$NON-NLS-1$
				result.add(argument);
				continue;
			}

			List<String> selected= expandRoot(argument, scope);
			result.addAll(selected);
			hasCleanupInput|= !selected.isEmpty();
		}
		return new FilteredArguments(result.toArray(String[]::new), hasCleanupInput);
	}

	private static boolean takesValue(String argument) {
		return ARG_CONFIG.equals(argument)
				|| ARG_CONFIG_LONG.equals(argument)
				|| ARG_IMPORT_PROJECT.equals(argument)
				|| ARG_MODE.equals(argument)
				|| ARG_PATCH.equals(argument)
				|| ARG_REPORT.equals(argument)
				|| ARG_SCOPE.equals(argument);
	}

	private static List<String> expandRoot(String value, RequestedScope scope) throws IOException {
		File root= new File(value);
		if (!root.exists()) {
			return List.of(value);
		}
		Path inputPath= root.toPath();
		Path classificationRoot= root.isDirectory()
				? inputPath
				: inputPath.toAbsolutePath().normalize().getParent();
		CleanupSourceSetClassifier classifier= CleanupSourceSetClassifier.create(
				classificationRoot == null ? inputPath : classificationRoot);
		if (root.isFile()) {
			if (!Util.isJavaLikeFileName(root.getPath()) || accepts(classifier, inputPath, scope)) {
				return List.of(value);
			}
			return List.of();
		}

		try (Stream<Path> paths= Files.walk(inputPath)) {
			return paths
					.filter(Files::isRegularFile)
					.filter(path -> Util.isJavaLikeFileName(path.toString()))
					.filter(path -> accepts(classifier, path, scope))
					.map(path -> path.toFile().getPath())
					.sorted()
					.toList();
		}
	}

	private static boolean accepts(CleanupSourceSetClassifier classifier, Path path, RequestedScope scope) {
		boolean testSource= classifier.isTestSource(path);
		return scope == RequestedScope.TEST ? testSource : !testSource;
	}

	private static RequestedScope requestedScope(String[] arguments) {
		if (arguments == null) {
			return RequestedScope.INVALID;
		}
		RequestedScope result= RequestedScope.BOTH;
		for (int index= 0; index < arguments.length; index++) {
			if (!ARG_SCOPE.equals(arguments[index])) {
				continue;
			}
			if (++index >= arguments.length) {
				return RequestedScope.INVALID;
			}
			try {
				result= RequestedScope.valueOf(arguments[index].toUpperCase(Locale.ROOT));
			} catch (@SuppressWarnings("unused") IllegalArgumentException e) {
				return RequestedScope.INVALID;
			}
		}
		return result;
	}
}
