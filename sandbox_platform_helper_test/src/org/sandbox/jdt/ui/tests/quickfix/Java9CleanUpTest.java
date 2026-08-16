package org.sandbox.jdt.ui.tests.quickfix;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava9;

public class Java9CleanUpTest {

	@RegisterExtension
	EclipseJava9 context= new EclipseJava9();

	enum PlatformStatusPatterns {

		STATUS_WARNING_WITH_THROWABLE("""
			package test1;
			import org.eclipse.core.runtime.IStatus;
			import org.eclipse.core.runtime.Status;
			public class E1 {
				void method(Throwable e) {
					IStatus status = new Status(IStatus.WARNING, "plugin.id", IStatus.OK, "important message", e);
				}
			}""", //$NON-NLS-1$

				"""
					package test1;
					import org.eclipse.core.runtime.IStatus;
					import org.eclipse.core.runtime.Status;
					public class E1 {
						void method(Throwable e) {
							IStatus status = new Status(IStatus.WARNING, "plugin.id", "important message", e);
						}
					}"""), //$NON-NLS-1$

		STATUS_ERROR_WITH_THROWABLE("""
			package test1;
			import org.eclipse.core.runtime.IStatus;
			import org.eclipse.core.runtime.Status;
			public class E1 {
				void method(Throwable e) {
					IStatus status = new Status(IStatus.ERROR, "plugin.id", IStatus.OK, "important message", e);
				}
			}""", //$NON-NLS-1$

				"""
					package test1;
					import org.eclipse.core.runtime.IStatus;
					import org.eclipse.core.runtime.Status;
					public class E1 {
						void method(Throwable e) {
							IStatus status = new Status(IStatus.ERROR, "plugin.id", "important message", e);
						}
					}"""), //$NON-NLS-1$

		STATUS_INFO_WITH_THROWABLE("""
			package test1;
			import org.eclipse.core.runtime.IStatus;
			import org.eclipse.core.runtime.Status;
			public class E1 {
				void method(Throwable e) {
					IStatus status = new Status(IStatus.INFO, "plugin.id", IStatus.OK, "important message", e);
				}
			}""", //$NON-NLS-1$

				"""
					package test1;
					import org.eclipse.core.runtime.IStatus;
					import org.eclipse.core.runtime.Status;
					public class E1 {
						void method(Throwable e) {
							IStatus status = new Status(IStatus.INFO, "plugin.id", "important message", e);
						}
					}"""), //$NON-NLS-1$

		STATUS_WARNING_WITH_NULL("""
			package test1;
			import org.eclipse.core.runtime.IStatus;
			import org.eclipse.core.runtime.Status;
			public class E1 {
				IStatus status = new Status(IStatus.WARNING, "plugin.id", IStatus.OK, "important message", null);
			}""", //$NON-NLS-1$

				"""
					package test1;
					import org.eclipse.core.runtime.IStatus;
					import org.eclipse.core.runtime.Status;
					public class E1 {
						IStatus status = new Status(IStatus.WARNING, "plugin.id", "important message", null);
					}"""), //$NON-NLS-1$

		STATUS_WITH_NUMERIC_ZERO("""
			package test1;
			import org.eclipse.core.runtime.IStatus;
			import org.eclipse.core.runtime.Status;
			public class E1 {
				IStatus status = new Status(IStatus.WARNING, "plugin.id", 0, "important message", null);
			}""", //$NON-NLS-1$

				"""
					package test1;
					import org.eclipse.core.runtime.IStatus;
					import org.eclipse.core.runtime.Status;
					public class E1 {
						IStatus status = new Status(IStatus.WARNING, "plugin.id", "important message", null);
					}"""), //$NON-NLS-1$

		STATUS_WITH_ZERO_CONSTANT("""
			package test1;
			import org.eclipse.core.runtime.IStatus;
			import org.eclipse.core.runtime.Status;
			public class E1 {
				private static final int OK_CODE = 0;
				IStatus status = new Status(IStatus.ERROR, "plugin.id", OK_CODE, "important message", null);
			}""", //$NON-NLS-1$

				"""
					package test1;
					import org.eclipse.core.runtime.IStatus;
					import org.eclipse.core.runtime.Status;
					public class E1 {
						private static final int OK_CODE = 0;
						IStatus status = new Status(IStatus.ERROR, "plugin.id", "important message", null);
					}"""), //$NON-NLS-1$

		STATUS_WITH_CLASS_IDENTITY("""
			package test1;
			import org.eclipse.core.runtime.IStatus;
			import org.eclipse.core.runtime.Status;
			public class E1 {
				IStatus status = new Status(IStatus.INFO, E1.class, IStatus.OK, "important message", null);
			}""", //$NON-NLS-1$

				"""
					package test1;
					import org.eclipse.core.runtime.IStatus;
					import org.eclipse.core.runtime.Status;
					public class E1 {
						IStatus status = new Status(IStatus.INFO, E1.class, "important message", null);
					}"""); //$NON-NLS-1$

		final String given;
		final String expected;

		PlatformStatusPatterns(String given, String expected) {
			this.given= given;
			this.expected= expected;
		}
	}

	@ParameterizedTest
	@EnumSource(PlatformStatusPatterns.class)
	public void testPlatformStatusParametrized(PlatformStatusPatterns test) throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test1", false, null); //$NON-NLS-1$
		ICompilationUnit cu= pack.createCompilationUnit("E1.java", test.given, false, null); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.SIMPLIFY_STATUS_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { test.expected }, null);
	}

	enum PlatformStatusPatternsDontTouch {

		NON_ZERO_LITERAL("""
			package test1;
			import org.eclipse.core.runtime.IStatus;
			import org.eclipse.core.runtime.Status;
			public class E1 {
				IStatus status = new Status(IStatus.WARNING, "plugin.id", 17, "important message", null);
			}"""), //$NON-NLS-1$

		NON_ZERO_CONSTANT("""
			package test1;
			import org.eclipse.core.runtime.IStatus;
			import org.eclipse.core.runtime.Status;
			public class E1 {
				private static final int APPLICATION_CODE = 100;
				IStatus status = new Status(IStatus.ERROR, "plugin.id", APPLICATION_CODE, "important message", null);
			}"""), //$NON-NLS-1$

		NON_CONSTANT_CODE("""
			package test1;
			import org.eclipse.core.runtime.IStatus;
			import org.eclipse.core.runtime.Status;
			public class E1 {
				int code() { return 0; }
				IStatus status = new Status(IStatus.INFO, "plugin.id", code(), "important message", null);
			}"""), //$NON-NLS-1$

		UNSUPPORTED_SEVERITY("""
			package test1;
			import org.eclipse.core.runtime.IStatus;
			import org.eclipse.core.runtime.Status;
			public class E1 {
				IStatus status = new Status(IStatus.CANCEL, "plugin.id", IStatus.OK, "important message", null);
			}"""), //$NON-NLS-1$

		ALREADY_SIMPLIFIED("""
			package test1;
			import org.eclipse.core.runtime.IStatus;
			import org.eclipse.core.runtime.Status;
			public class E1 {
				IStatus status = new Status(IStatus.WARNING, "plugin.id", "important message", null);
			}"""); //$NON-NLS-1$

		final String given;

		PlatformStatusPatternsDontTouch(String given) {
			this.given= given;
		}
	}

	@ParameterizedTest
	@EnumSource(PlatformStatusPatternsDontTouch.class)
	public void testPlatformStatusDontTouch(PlatformStatusPatternsDontTouch test) throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test1", false, null); //$NON-NLS-1$
		ICompilationUnit cu= pack.createCompilationUnit("E1.java", test.given, false, null); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.SIMPLIFY_STATUS_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	enum MultiStatusPatterns {

		ZERO_LITERAL("""
			package test1;
			import org.eclipse.core.runtime.MultiStatus;
			public class E1 {
				void method(Throwable e) {
					MultiStatus status = new MultiStatus("plugin.id", 0, "important message", e);
				}
			}""", //$NON-NLS-1$

				"""
					package test1;
					import org.eclipse.core.runtime.IStatus;
					import org.eclipse.core.runtime.MultiStatus;
					public class E1 {
						void method(Throwable e) {
							MultiStatus status = new MultiStatus("plugin.id", IStatus.OK, "important message", e);
						}
					}"""), //$NON-NLS-1$

		ZERO_CONSTANT("""
			package test1;
			import org.eclipse.core.runtime.MultiStatus;
			public class E1 {
				private static final int OK_CODE = 0;
				void method(Throwable e) {
					MultiStatus status = new MultiStatus("plugin.id", OK_CODE, "important message", e);
				}
			}""", //$NON-NLS-1$

				"""
					package test1;
					import org.eclipse.core.runtime.IStatus;
					import org.eclipse.core.runtime.MultiStatus;
					public class E1 {
						private static final int OK_CODE = 0;
						void method(Throwable e) {
							MultiStatus status = new MultiStatus("plugin.id", IStatus.OK, "important message", e);
						}
					}"""); //$NON-NLS-1$

		final String given;
		final String expected;

		MultiStatusPatterns(String given, String expected) {
			this.given= given;
			this.expected= expected;
		}
	}

	@ParameterizedTest
	@EnumSource(MultiStatusPatterns.class)
	public void testMultiStatusParametrized(MultiStatusPatterns test) throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test1", false, null); //$NON-NLS-1$
		ICompilationUnit cu= pack.createCompilationUnit("E1.java", test.given, false, null); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.SIMPLIFY_STATUS_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { test.expected }, null);
	}

	enum MultiStatusPatternsDontTouch {

		ALREADY_OK("""
			package test1;
			import org.eclipse.core.runtime.IStatus;
			import org.eclipse.core.runtime.MultiStatus;
			public class E1 {
				MultiStatus status = new MultiStatus("plugin.id", IStatus.OK, "important message", null);
			}"""), //$NON-NLS-1$

		NON_ZERO_LITERAL("""
			package test1;
			import org.eclipse.core.runtime.MultiStatus;
			public class E1 {
				MultiStatus status = new MultiStatus("plugin.id", 1, "important message", null);
			}"""), //$NON-NLS-1$

		NON_ZERO_CONSTANT("""
			package test1;
			import org.eclipse.core.runtime.MultiStatus;
			public class E1 {
				private static final int APPLICATION_CODE = 100;
				MultiStatus status = new MultiStatus("plugin.id", APPLICATION_CODE, "important message", null);
			}"""), //$NON-NLS-1$

		NON_CONSTANT_CODE("""
			package test1;
			import org.eclipse.core.runtime.MultiStatus;
			public class E1 {
				int code() { return 0; }
				MultiStatus status = new MultiStatus("plugin.id", code(), "important message", null);
			}"""); //$NON-NLS-1$

		final String given;

		MultiStatusPatternsDontTouch(String given) {
			this.given= given;
		}
	}

	@ParameterizedTest
	@EnumSource(MultiStatusPatternsDontTouch.class)
	public void testMultiStatusDontTouch(MultiStatusPatternsDontTouch test) throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test1", false, null); //$NON-NLS-1$
		ICompilationUnit cu= pack.createCompilationUnit("E1.java", test.given, false, null); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.SIMPLIFY_STATUS_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}
}
