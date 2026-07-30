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
package org.eclipse.jgit.server.rest;

import org.eclipse.jgit.server.internal.NonOwningHibernateSessionFactoryProvider;
import org.hibernate.SessionFactory;

/**
 * Native-factory entry point for the legacy copied Search projection endpoint.
 *
 * <p>The superclass still accepts the copied provider type. This adapter keeps
 * that compatibility detail inside the Search boundary and deliberately does
 * not own or close the application-owned {@link SessionFactory}.</p>
 */
public final class NativeSearchResource extends SearchResource {

	private static final long serialVersionUID = 1L;

	/** Creates a Search endpoint backed by an application-owned native factory. */
	public NativeSearchResource(SessionFactory sessionFactory) {
		super(NonOwningHibernateSessionFactoryProvider.view(sessionFactory));
	}
}
