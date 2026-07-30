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
package org.eclipse.jgit.server.rest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jgit.storage.hibernate.config.HibernateSessionFactoryProvider;
import org.hibernate.SessionFactory;
import org.junit.Test;

/** Lifecycle contract for the native Search compatibility adapter. */
public class NativeSearchResourceTest {

	@Test
	public void compatibilityProviderDoesNotOwnNativeFactory() throws Exception {
		AtomicBoolean closed= new AtomicBoolean();
		SessionFactory sessionFactory= proxySessionFactory(closed);
		NativeSearchResource resource= new NativeSearchResource(sessionFactory);

		Field providerField= SearchResource.class.getDeclaredField("provider"); //$NON-NLS-1$
		providerField.setAccessible(true);
		HibernateSessionFactoryProvider provider=
				(HibernateSessionFactoryProvider) providerField.get(resource);

		assertSame(sessionFactory, provider.getSessionFactory());
		provider.close();
		assertFalse("Search compatibility must not close the application-owned factory", closed.get()); //$NON-NLS-1$
	}

	private static SessionFactory proxySessionFactory(AtomicBoolean closed) {
		return (SessionFactory) Proxy.newProxyInstance(SessionFactory.class.getClassLoader(),
				new Class<?>[] { SessionFactory.class }, (proxy, method, arguments) -> {
					switch (method.getName()) {
					case "close": //$NON-NLS-1$
						closed.set(true);
						return null;
					case "isClosed": //$NON-NLS-1$
						return Boolean.valueOf(closed.get());
					case "equals": //$NON-NLS-1$
						return Boolean.valueOf(proxy == argument(arguments, 0));
					case "hashCode": //$NON-NLS-1$
						return Integer.valueOf(System.identityHashCode(proxy));
					case "toString": //$NON-NLS-1$
						return "TestSessionFactory"; //$NON-NLS-1$
					default:
						return defaultValue(method.getReturnType());
					}
				});
	}

	private static Object argument(Object[] arguments, int index) {
		return arguments != null && index >= 0 && index < arguments.length ? arguments[index] : null;
	}

	private static Object defaultValue(Class<?> type) {
		if (!type.isPrimitive() || type == void.class) {
			return null;
		}
		if (type == boolean.class) {
			return Boolean.FALSE;
		}
		if (type == char.class) {
			return Character.valueOf('\0');
		}
		if (type == byte.class) {
			return Byte.valueOf((byte) 0);
		}
		if (type == short.class) {
			return Short.valueOf((short) 0);
		}
		if (type == int.class) {
			return Integer.valueOf(0);
		}
		if (type == long.class) {
			return Long.valueOf(0L);
		}
		if (type == float.class) {
			return Float.valueOf(0.0F);
		}
		return Double.valueOf(0.0D);
	}
}
