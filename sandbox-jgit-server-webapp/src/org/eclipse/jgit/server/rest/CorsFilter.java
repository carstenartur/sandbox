/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.eclipse.jgit.server.rest;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Simple CORS filter for the REST API.
 * <p>
 * When configured, adds {@code Access-Control-Allow-*} headers to responses.
 * The allowed origins are passed at construction time (typically from the
 * {@code JGIT_CORS_ORIGINS} environment variable).
 * </p>
 */
public class CorsFilter implements Filter {

	private final String allowedOrigins;

	/**
	 * Create a CORS filter.
	 *
	 * @param allowedOrigins
	 *            comma-separated list of allowed origins, or {@code "*"}
	 */
	public CorsFilter(String allowedOrigins) {
		this.allowedOrigins = allowedOrigins;
	}

	@Override
	public void init(FilterConfig filterConfig) {
		// nothing to do
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response,
			FilterChain chain) throws IOException, ServletException {
		if (response instanceof HttpServletResponse httpResp) {
			httpResp.setHeader("Access-Control-Allow-Origin", //$NON-NLS-1$
					allowedOrigins);
			httpResp.setHeader("Access-Control-Allow-Methods", //$NON-NLS-1$
					"GET, POST, OPTIONS"); //$NON-NLS-1$
			httpResp.setHeader("Access-Control-Allow-Headers", //$NON-NLS-1$
					"Content-Type, Authorization"); //$NON-NLS-1$
			httpResp.setHeader("Access-Control-Max-Age", "3600"); //$NON-NLS-1$ //$NON-NLS-2$

			if (request instanceof HttpServletRequest httpReq
					&& "OPTIONS".equals(httpReq.getMethod())) { //$NON-NLS-1$
				httpResp.setStatus(HttpServletResponse.SC_OK);
				return;
			}
		}
		chain.doFilter(request, response);
	}

	@Override
	public void destroy() {
		// nothing to do
	}
}
