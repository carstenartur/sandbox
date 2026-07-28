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
package org.eclipse.jgit.server.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

import com.google.gson.Gson;

import io.github.carstenartur.jgit.storage.hibernate.schema.LegacyCoreSchemaAdoption;
import io.github.carstenartur.jgit.storage.hibernate.schema.LegacyCoreSchemaAdoption.DuplicatePackIdentity;
import io.github.carstenartur.jgit.storage.hibernate.schema.LegacyCoreSchemaAdoption.LegacySchemaReport;

/**
 * Runs the released Core legacy-schema validator without starting Hibernate or
 * executing DDL.
 *
 * <p>The command uses the same database environment variables as the server and
 * prints one JSON report to standard output. Any unsafe or unsupported schema
 * causes a non-zero process exit through the validator exception.</p>
 */
public final class LegacyCoreSchemaPreflight {

	private static final String CONNECTION_URL= "hibernate.connection.url"; //$NON-NLS-1$
	private static final String CONNECTION_USER= "hibernate.connection.username"; //$NON-NLS-1$
	private static final String CONNECTION_PASSWORD= "hibernate.connection.password"; //$NON-NLS-1$
	private static final String CONNECTION_DRIVER= "hibernate.connection.driver_class"; //$NON-NLS-1$
	private static final String POSTGRESQL_URL_PREFIX= "jdbc:postgresql:"; //$NON-NLS-1$
	private static final String HSQLDB_URL_PREFIX= "jdbc:hsqldb:"; //$NON-NLS-1$
	private static final String SQL_SERVER_URL_PREFIX= "jdbc:sqlserver:"; //$NON-NLS-1$
	private static final String HSQLDB_IF_EXISTS= ";ifexists=true"; //$NON-NLS-1$

	private LegacyCoreSchemaPreflight() {
	}

	/**
	 * Execute the read-only preflight from the server environment.
	 *
	 * @param args
	 *            ignored
	 * @throws Exception
	 *             if the driver cannot be loaded, the database cannot be read or
	 *             the schema is unsafe to adopt
	 */
	public static void main(String[] args) throws Exception {
		PreflightReport report= inspect(HibernateConfig.buildProperties());
		System.out.println(toJson(report));
	}

	/**
	 * Open a JDBC connection from Hibernate properties and inspect the schema.
	 *
	 * @param properties
	 *            database properties; no Hibernate bootstrap is performed
	 * @return validated report safe to use for the next adoption decision
	 * @throws ClassNotFoundException
	 *             if an explicitly configured JDBC driver cannot be loaded
	 * @throws SQLException
	 *             if the database connection fails
	 */
	static PreflightReport inspect(Properties properties)
			throws ClassNotFoundException, SQLException {
		Objects.requireNonNull(properties, "properties"); //$NON-NLS-1$
		String url= requireProperty(properties, CONNECTION_URL);
		requirePublishedLegacyAdoptionPath(url);
		String driver= properties.getProperty(CONNECTION_DRIVER);
		if (driver != null && !driver.isBlank()) {
			Class.forName(driver);
		}
		String user= properties.getProperty(CONNECTION_USER, ""); //$NON-NLS-1$
		String password= properties.getProperty(CONNECTION_PASSWORD, ""); //$NON-NLS-1$
		try (Connection connection= DriverManager.getConnection(url, user, password)) {
			connection.setReadOnly(true);
			return inspect(connection);
		}
	}

	/** Validate one existing JDBC connection without modifying its schema. */
	static PreflightReport inspect(Connection connection) {
		LegacySchemaReport report= LegacyCoreSchemaAdoption
				.requireSafeToAdopt(connection);
		Comparator<DuplicatePackIdentity> duplicateOrder= Comparator
				.comparing(DuplicatePackIdentity::repositoryName)
				.thenComparing(DuplicatePackIdentity::packName)
				.thenComparing(DuplicatePackIdentity::packExtension)
				.thenComparingLong(DuplicatePackIdentity::rowCount);
		List<DuplicatePackIdentity> duplicatePackIdentities= report
				.duplicatePackIdentities().stream().sorted(duplicateOrder).toList();
		return new PreflightReport(report.columns().stream().sorted().toList(),
				report.missingRequiredColumns().stream().sorted().toList(),
				report.packRows(), report.incompletePackRows(),
				duplicatePackIdentities, report.hasCommittedColumn(),
				report.hasCommittedAtColumn(), report.requiresAdoption());
	}

	/** Serialize the bounded report without exposing connection credentials. */
	static String toJson(PreflightReport report) {
		return new Gson().toJson(report);
	}

	/** Require a JDBC family with a published pre-library adoption migration. */
	static void requirePublishedLegacyAdoptionPath(String url) {
		String normalizedUrl= Objects.requireNonNull(url, "url") //$NON-NLS-1$
				.toLowerCase(Locale.ROOT);
		if (!normalizedUrl.startsWith(POSTGRESQL_URL_PREFIX)
				&& !normalizedUrl.startsWith(HSQLDB_URL_PREFIX)
				&& !normalizedUrl.startsWith(SQL_SERVER_URL_PREFIX)) {
			throw new IllegalArgumentException(
					"No published pre-library legacy-adoption migration exists " //$NON-NLS-1$
							+ "for JDBC family " + databaseFamily(normalizedUrl) //$NON-NLS-1$
							+ ". Use PostgreSQL, HSQLDB or SQL Server, or add " //$NON-NLS-1$
							+ "and release generic support upstream first."); //$NON-NLS-1$
		}
		if (normalizedUrl.startsWith(HSQLDB_URL_PREFIX)
				&& !normalizedUrl.contains(HSQLDB_IF_EXISTS)) {
			throw new IllegalArgumentException(
					"HSQLDB legacy-schema preflight requires ;ifexists=true " //$NON-NLS-1$
							+ "so a mistyped path cannot create a database."); //$NON-NLS-1$
		}
	}

	private static String databaseFamily(String url) {
		int separator= url.indexOf(':', "jdbc:".length()); //$NON-NLS-1$
		return separator > 0 ? url.substring(0, separator + 1) : "unknown"; //$NON-NLS-1$
	}

	private static String requireProperty(Properties properties, String name) {
		String value= properties.getProperty(name);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(
					"Missing required database property: " + name); //$NON-NLS-1$
		}
		return value;
	}

	/** Machine-readable result of the released read-only legacy-schema check. */
	record PreflightReport(List<String> columns,
			List<String> missingRequiredColumns, long packRows,
			long incompletePackRows,
			List<DuplicatePackIdentity> duplicatePackIdentities,
			boolean hasCommittedColumn, boolean hasCommittedAtColumn,
			boolean requiresAdoption) {
	}
}
