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
 * <p>
 * If the value is {@code "*"}, a wildcard is sent. Otherwise the request's
 * {@code Origin} header is echoed back only when it matches one of the
 * comma-separated entries in the allowed-origins list.
 * </p>
 */
public class CorsFilter implements Filter {

	private final String[] allowedOrigins;

	private final boolean wildcard;

	/**
	 * Create a CORS filter.
	 *
	 * @param allowedOrigins
	 *            comma-separated list of allowed origins, or {@code "*"}
	 */
	public CorsFilter(String allowedOrigins) {
		String trimmed = allowedOrigins == null ? "" : allowedOrigins.trim(); //$NON-NLS-1$
		if ("*".equals(trimmed)) { //$NON-NLS-1$
			this.wildcard = true;
			this.allowedOrigins = new String[0];
		} else {
			this.wildcard = false;
			this.allowedOrigins = trimmed.split("\\s*,\\s*"); //$NON-NLS-1$
		}
	}

	@Override
	public void init(FilterConfig filterConfig) {
		// nothing to do
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response,
			FilterChain chain) throws IOException, ServletException {
		if (response instanceof HttpServletResponse httpResp
				&& request instanceof HttpServletRequest httpReq) {
			String rawOrigin = httpReq.getHeader("Origin"); //$NON-NLS-1$
			// Strip CR/LF to prevent header injection before echoing back
			String origin = rawOrigin != null
					? rawOrigin.replace("\r", "").replace("\n", "") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					: null;
			boolean originAllowed = origin != null && isAllowed(origin);
			if (wildcard) {
				httpResp.setHeader("Access-Control-Allow-Origin", "*"); //$NON-NLS-1$ //$NON-NLS-2$
			} else if (originAllowed) {
				httpResp.setHeader("Access-Control-Allow-Origin", origin); //$NON-NLS-1$
				httpResp.setHeader("Vary", "Origin"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			if (wildcard || originAllowed) {
				httpResp.setHeader("Access-Control-Allow-Methods", //$NON-NLS-1$
						"GET, POST, OPTIONS"); //$NON-NLS-1$
				httpResp.setHeader("Access-Control-Allow-Headers", //$NON-NLS-1$
						"Content-Type, Authorization"); //$NON-NLS-1$
				httpResp.setHeader("Access-Control-Max-Age", "3600"); //$NON-NLS-1$ //$NON-NLS-2$
			}

			if ("OPTIONS".equals(httpReq.getMethod())) { //$NON-NLS-1$
				httpResp.setStatus(HttpServletResponse.SC_OK);
				return;
			}
		}
		chain.doFilter(request, response);
	}

	private boolean isAllowed(String origin) {
		for (String allowed : allowedOrigins) {
			if (allowed.equals(origin)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void destroy() {
		// nothing to do
	}
}
