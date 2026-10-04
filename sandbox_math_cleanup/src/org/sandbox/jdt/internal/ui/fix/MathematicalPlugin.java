package org.sandbox.jdt.internal.ui.fix;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/** Owns the bounded workers and the Java model listener lifetime. */
public final class MathematicalPlugin implements BundleActivator {
 @Override public void start(BundleContext context) { MathematicalEnvironment.start(); }
 @Override public void stop(BundleContext context) { MathematicalAnalysisJob.cancelAll();MathematicalEnvironment.stop(); }
}
