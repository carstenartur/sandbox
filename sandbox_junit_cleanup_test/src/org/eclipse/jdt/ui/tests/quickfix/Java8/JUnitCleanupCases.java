package org.eclipse.jdt.ui.tests.quickfix.Java8;

/*-
 * #%L
 * Sandbox junit cleanup test
 * %%
 * Copyright (C) 2024 hammer
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

enum JUnitCleanupCases{
		RuleAnonymousExternalResource(
"""
package test;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.ExternalResource;
/**
 * 
 */
public class MyTest {

	@Rule
	public ExternalResource er= new ExternalResource() {
	@Override
	protected void before() throws Throwable {
	};

	@Override
	protected void after() {
	};
	};

	@Test
	public void test3() {
	}
}
""", //$NON-NLS-1$
"""
package test;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
/**
 *
 */
public class MyTest {

	@RegisterExtension
	public Er_5b8b4 er= new Er_5b8b4();

	@Test
	public void test3() {
	}

	class Er_5b8b4 implements BeforeEachCallback, AfterEachCallback {
		public void beforeEach(ExtensionContext context) {
		}

		public void afterEach(ExtensionContext context) {
		}
	}
}
"""),
RuleNestedExternalResource(
"""
package test;

import org.junit.Test;
import org.junit.Rule;
import org.junit.ClassRule;
import org.junit.rules.ExternalResource;

public class MyTest {

    // Final abgeleitete Klasse
    final class MyExternalResource extends ExternalResource {
        @Override
        protected void before() throws Throwable {
            super.before();
            int i = 4;
        }

        @Override
        protected void after() {
            super.after();
        }
    }

	@Rule
    public ExternalResource er = new MyExternalResource();

    // Anonyme Klasse als ExternalResource
	@Rule
    public ExternalResource anonymousRule = new ExternalResource() {
        @Override
        protected void before() throws Throwable {
            System.out.println("Anonymous rule before");
        }

        @Override
        protected void after() {
            System.out.println("Anonymous rule after");
        }
    };

    // Statische Klasse für ClassRule
    static class StaticExternalResource extends ExternalResource {
        @Override
        protected void before() throws Throwable {
            System.out.println("Static resource before");
        }

        @Override
        protected void after() {
            System.out.println("Static resource after");
        }
    }

	@ClassRule
	public static ExternalResource staticResource = new StaticExternalResource();

    // Klasse mit Konstruktor
    final class ConstructedExternalResource extends ExternalResource {
        private final String resourceName;

        public ConstructedExternalResource(String resourceName) {
            this.resourceName = resourceName;
        }

        @Override
        protected void before() throws Throwable {
            System.out.println("Resource setup: " + resourceName);
        }

        @Override
        protected void after() {
            System.out.println("Resource cleanup: " + resourceName);
        }
    }

	@Rule
    public ExternalResource constructedResource = new ConstructedExternalResource("TestResource");

    // Zweite Regel
	@Rule
    public ExternalResource secondRule = new ExternalResource() {
        @Override
        protected void before() throws Throwable {
            System.out.println("Second rule before");
        }

        @Override
        protected void after() {
            System.out.println("Second rule after");
        }
    };

    // Testfälle
    @Test
    public void testWithExternalResource() {
        System.out.println("Test with external resource");
    }

    @Test
    public void testWithMultipleResources() {
        System.out.println("Test with multiple resources");
    }
}
""", //$NON-NLS-1$
"""
package test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;

public class MyTest {

    // Final abgeleitete Klasse
    final class MyExternalResource implements BeforeEachCallback, AfterEachCallback {
        @Override
        public void beforeEach(ExtensionContext context) {
            super.beforeEach(context);
            int i = 4;
        }

        @Override
        public void afterEach(ExtensionContext context) {
            super.afterEach(context);
        }
    }

	@RegisterExtension
	public ExternalResource er = new MyExternalResource();

    // Anonyme Klasse als ExternalResource
	@RegisterExtension
	public AnonymousRule_9ea4e anonymousRule = new AnonymousRule_9ea4e();

    // Statische Klasse für ClassRule
    static class StaticExternalResource implements BeforeAllCallback, AfterAllCallback {
        @Override
        public void beforeAll(ExtensionContext context) {
            System.out.println("Static resource before");
        }

        @Override
        public void afterAll(ExtensionContext context) {
            System.out.println("Static resource after");
        }
    }

	@RegisterExtension
	public static ExternalResource staticResource = new StaticExternalResource();

    // Klasse mit Konstruktor
    final class ConstructedExternalResource implements BeforeEachCallback, AfterEachCallback {
        private final String resourceName;

        public ConstructedExternalResource(String resourceName) {
            this.resourceName = resourceName;
        }

        @Override
        public void beforeEach(ExtensionContext context) {
            System.out.println("Resource setup: " + resourceName);
        }

        @Override
        public void afterEach(ExtensionContext context) {
            System.out.println("Resource cleanup: " + resourceName);
        }
    }

	@RegisterExtension
	public ExternalResource constructedResource = new ConstructedExternalResource("TestResource");

    // Zweite Regel
	@RegisterExtension
	public SecondRule_c4213 secondRule = new SecondRule_c4213();

    // Testfälle
    @Test
    public void testWithExternalResource() {
        System.out.println("Test with external resource");
    }

    @Test
    public void testWithMultipleResources() {
        System.out.println("Test with multiple resources");
    }

	class SecondRule_c4213 implements BeforeEachCallback, AfterEachCallback {
		public void beforeEach(ExtensionContext context) {
			System.out.println("Second rule before");
		}

		public void afterEach(ExtensionContext context) {
			System.out.println("Second rule after");
		}
	}

	class AnonymousRule_9ea4e implements BeforeEachCallback, AfterEachCallback {
		public void beforeEach(ExtensionContext context) {
			System.out.println("Anonymous rule before");
		}

		public void afterEach(ExtensionContext context) {
			System.out.println("Anonymous rule after");
		}
	}
}
"""); //$NON-NLS-1$

		String given;
		String expected;

		JUnitCleanupCases(String given, String expected) {
			this.given=given;
			this.expected=expected;
		}
	}
