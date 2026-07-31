package com.baronesa.website.migration;

import java.io.PrintStream;
import java.util.Objects;
import java.util.function.Function;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.ErrorCode;
import org.flywaydb.core.api.logging.Log;
import org.flywaydb.core.api.logging.LogCreator;
import org.flywaydb.core.api.output.ValidateOutput;
import org.flywaydb.core.api.output.ValidateResult;

public final class ProductionMigrationMain {

    static final int APPLIED_EXIT = 0;
    static final int PENDING_EXIT = 10;
    static final int FAILED_EXIT = 20;

    static final String APPLIED_MARKER = "MIGRATIONS_APPLIED";
    static final String PENDING_MARKER = "MIGRATIONS_PENDING";
    static final String FAILED_MARKER = "MIGRATIONS_FAILED";

    private ProductionMigrationMain() {
    }

    public static void main(String[] args) {
        int exitCode = run(
                args,
                System::getenv,
                ProductionMigrationMain::createFlywayOperations,
                System.out);
        System.exit(exitCode);
    }

    static int run(
            String[] args,
            Function<String, String> environment,
            MigrationOperationsFactory factory,
            PrintStream output) {
        Objects.requireNonNull(environment);
        Objects.requireNonNull(factory);
        Objects.requireNonNull(output);

        String mode = args != null && args.length == 1 ? args[0] : null;
        if (!"probe".equals(mode) && !"migrate".equals(mode)) {
            return finish(output, FAILED_EXIT, FAILED_MARKER);
        }

        try {
            String url = required(environment, "SPRING_DATASOURCE_URL");
            String username = required(environment, "SPRING_DATASOURCE_USERNAME");
            String password = required(environment, "SPRING_DATASOURCE_PASSWORD");
            MigrationOperations migrations = factory.create(url, username, password);

            if ("migrate".equals(mode)) {
                migrations.migrate();
            }
            migrations.validate("probe".equals(mode));
            boolean pending = migrations.pendingCount() != 0;
            if ("probe".equals(mode) && pending) {
                return finish(output, PENDING_EXIT, PENDING_MARKER);
            }
            if (pending) {
                return finish(output, FAILED_EXIT, FAILED_MARKER);
            }
            return finish(output, APPLIED_EXIT, APPLIED_MARKER);
        } catch (Throwable ignored) {
            return finish(output, FAILED_EXIT, FAILED_MARKER);
        }
    }

    private static String required(
            Function<String, String> environment,
            String name) {
        String value = environment.apply(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing configuration");
        }
        return value;
    }

    private static int finish(PrintStream output, int exitCode, String marker) {
        output.println(marker);
        return exitCode;
    }

    private static MigrationOperations createFlywayOperations(
            String url,
            String username,
            String password) {
        Flyway flyway = Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .loggers(NoOpLogCreator.class.getName())
                .load();
        return new FlywayOperations(flyway);
    }

    public static final class NoOpLogCreator implements LogCreator {
        @Override
        public Log createLogger(Class<?> clazz) {
            return NoOpLog.INSTANCE;
        }
    }

    public static final class NoOpLog implements Log {
        private static final NoOpLog INSTANCE = new NoOpLog();

        private NoOpLog() {
        }

        @Override
        public boolean isDebugEnabled() {
            return false;
        }

        @Override
        public void debug(String message) {
        }

        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message) {
        }

        @Override
        public void error(String message) {
        }

        @Override
        public void error(String message, Exception exception) {
        }

        @Override
        public void notice(String message) {
        }
    }

    interface MigrationOperationsFactory {
        MigrationOperations create(String url, String username, String password);
    }

    interface MigrationOperations {
        void validate(boolean allowPending);

        int pendingCount();

        void migrate();
    }

    private static final class FlywayOperations implements MigrationOperations {
        private final Flyway flyway;

        private FlywayOperations(Flyway flyway) {
            this.flyway = flyway;
        }

        @Override
        public void validate(boolean allowPending) {
            ValidateResult result = flyway.validateWithResult();
            if (result.validationSuccessful) {
                return;
            }
            if (allowPending
                    && !result.invalidMigrations.isEmpty()
                    && result.invalidMigrations.stream()
                            .allMatch(FlywayOperations::isPending)) {
                return;
            }
            throw new IllegalStateException("invalid migrations");
        }

        @Override
        public int pendingCount() {
            return flyway.info().pending().length;
        }

        @Override
        public void migrate() {
            flyway.migrate();
        }

        private static boolean isPending(ValidateOutput migration) {
            ErrorCode code = migration.errorDetails.errorCode;
            return code == ErrorCode.RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED
                    || code == ErrorCode.RESOLVED_REPEATABLE_MIGRATION_NOT_APPLIED;
        }
    }
}
