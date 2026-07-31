package com.baronesa.emporio.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Pattern;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Rescue strategy for cases where the database schema already exists but the Flyway history was wiped.
 *
 * This is intentionally guarded by a property because it can hide real migration problems if enabled by default.
 *
 * Enable with: APP_FLYWAY_BASELINE_TO_LATEST_ON_EMPTY=true
 * Property key: app.flyway.baseline-to-latest-on-empty
 */
@Configuration
@ConditionalOnProperty(name = "app.flyway.baseline-to-latest-on-empty", havingValue = "true")
public class FlywayRescueConfig {
	private static final Logger log = LoggerFactory.getLogger(FlywayRescueConfig.class);
	private static final Pattern SAFE_SCHEMA = Pattern.compile("^[a-zA-Z0-9_]+$");

	@Bean
	public FlywayMigrationStrategy flywayMigrationStrategy() {
		return flyway -> {
			try (Connection conn = flyway.getConfiguration().getDataSource().getConnection()) {
				String schema = resolvePrimarySchema(flyway);
				boolean schemaHasTables = schemaHasTables(conn, schema);
				boolean historyExists = flywayHistoryTableExists(conn, schema);
				long historyRowCount = historyExists ? flywayHistoryRowCount(conn, schema) : 0;
				int pendingVersionedMigrations = countPendingVersionedMigrations(flyway);

				if (!schemaHasTables) {
					flyway.migrate();
					return;
				}

				// If schema is non-empty but Flyway history is missing/empty OR inconsistent (pending migrations that likely
				// were already applied), baseline to latest resolved migration in the codebase.
				//
				// This is a pragmatic recovery path for "history was wiped/partially wiped by external tooling".
				boolean historyMissingOrEmpty = !historyExists || historyRowCount == 0;
				boolean historyInconsistent = historyExists && historyRowCount > 0 && pendingVersionedMigrations > 0;
				if (historyMissingOrEmpty || historyInconsistent) {
					MigrationVersion latest = latestResolvedMigrationVersion(flyway);
					if (latest == null) {
						log.warn(
							"Flyway rescue enabled but no versioned migrations were resolved. Running regular migrate. schema={}",
							schema
						);
						flyway.migrate();
						return;
					}

					log.warn(
						"Flyway rescue enabled (schema non-empty). historyExists={}, historyRowCount={}, pendingVersionedMigrations={}. " +
							"Baselines will be created at latest resolved version {} (schema={}).",
						historyExists,
						historyRowCount,
						pendingVersionedMigrations,
						latest,
						schema
					);

					if (historyExists) {
						// Flyway baseline() refuses to run when the history table exists (even if empty).
						// The safest recovery is to drop only Flyway metadata and recreate it via baseline.
						dropFlywayHistoryTable(conn, schema);
					}

					Flyway rescued = Flyway.configure()
						.configuration(flyway.getConfiguration())
						.baselineVersion(latest)
						.baselineDescription("rescue baseline (history wiped)")
						.load();

					rescued.baseline();
					rescued.migrate();
					return;
				}

				// Normal path
				flyway.migrate();
			} catch (SQLException e) {
				throw new IllegalStateException("Failed to run Flyway rescue strategy", e);
			}
		};
	}

	private static String resolvePrimarySchema(Flyway flyway) {
		String[] schemas = flyway.getConfiguration().getSchemas();
		String schema = (schemas != null && schemas.length > 0) ? schemas[0] : null;
		if (schema == null || schema.isBlank()) {
			return "public";
		}
		if (!SAFE_SCHEMA.matcher(schema).matches()) {
			log.warn("Ignoring unexpected Flyway schema value '{}'; falling back to 'public'.", schema);
			return "public";
		}
		return schema;
	}

	private static boolean flywayHistoryTableExists(Connection conn, String schema) throws SQLException {
		String sql = "select to_regclass(?)";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, schema + ".flyway_schema_history");
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					return false;
				}
				return rs.getString(1) != null;
			}
		}
	}

	private static long flywayHistoryRowCount(Connection conn, String schema) throws SQLException {
		String sql = "select count(*) from " + schema + ".flyway_schema_history";
		try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
			rs.next();
			return rs.getLong(1);
		}
	}

	private static void dropFlywayHistoryTable(Connection conn, String schema) throws SQLException {
		log.warn("Dropping {}.flyway_schema_history as part of Flyway rescue.", schema);
		String sql = "drop table if exists " + schema + ".flyway_schema_history cascade";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.executeUpdate();
		}
	}

	private static boolean schemaHasTables(Connection conn, String schema) throws SQLException {
		String sql =
			"select count(*) from information_schema.tables " +
			"where table_schema = ? and table_type = 'BASE TABLE'";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, schema);
			try (ResultSet rs = ps.executeQuery()) {
				rs.next();
				return rs.getLong(1) > 0;
			}
		}
	}

	private static MigrationVersion latestResolvedMigrationVersion(Flyway flyway) {
		MigrationVersion max = null;
		MigrationInfo[] infos = flyway.info().all();
		if (infos == null) {
			return null;
		}
		for (MigrationInfo info : infos) {
			MigrationVersion v = info.getVersion();
			if (v == null) {
				continue;
			}
			if (max == null || v.compareTo(max) > 0) {
				max = v;
			}
		}
		return max;
	}

	private static int countPendingVersionedMigrations(Flyway flyway) {
		MigrationInfo[] pending = flyway.info().pending();
		if (pending == null) {
			return 0;
		}
		int count = 0;
		for (MigrationInfo info : pending) {
			if (info.getVersion() != null) {
				count++;
			}
		}
		return count;
	}
}
