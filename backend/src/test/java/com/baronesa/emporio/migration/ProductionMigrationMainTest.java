package com.baronesa.emporio.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.api.CoreErrorCode;
import org.flywaydb.core.api.ErrorCode;
import org.flywaydb.core.api.ErrorDetails;
import org.flywaydb.core.api.output.ValidateOutput;
import org.junit.jupiter.api.Test;

class ProductionMigrationMainTest {

    private static final String URL = "jdbc:postgresql://database.invalid/erp";
    private static final String USERNAME = "migration_user";
    private static final String PASSWORD = "fictional_password";

    @Test
    void probeReportsAppliedWithoutMigrating() {
        FakeOperations operations = new FakeOperations(0);
        Invocation result = invoke(new String[] {"probe"}, environment(), operations);

        assertEquals(ProductionMigrationMain.APPLIED_EXIT, result.exitCode());
        assertEquals(ProductionMigrationMain.APPLIED_MARKER + System.lineSeparator(), result.output());
        assertEquals(List.of("validate:true", "pending"), operations.calls);
    }

    @Test
    void probeReportsPendingWithoutMigrating() {
        FakeOperations operations = new FakeOperations(2);
        Invocation result = invoke(new String[] {"probe"}, environment(), operations);

        assertEquals(ProductionMigrationMain.PENDING_EXIT, result.exitCode());
        assertEquals(ProductionMigrationMain.PENDING_MARKER + System.lineSeparator(), result.output());
        assertEquals(List.of("validate:true", "pending"), operations.calls);
    }

    @Test
    void migrateAppliesValidatesAndRequiresNoRemainingPendingMigration() {
        FakeOperations operations = new FakeOperations(0);
        Invocation result = invoke(new String[] {"migrate"}, environment(), operations);

        assertEquals(ProductionMigrationMain.APPLIED_EXIT, result.exitCode());
        assertEquals(ProductionMigrationMain.APPLIED_MARKER + System.lineSeparator(), result.output());
        assertEquals(List.of("migrate", "validate:false", "pending"), operations.calls);
    }

    @Test
    void migrateFailsClosedWhenPendingMigrationRemains() {
        FakeOperations operations = new FakeOperations(1);
        Invocation result = invoke(new String[] {"migrate"}, environment(), operations);

        assertEquals(ProductionMigrationMain.FAILED_EXIT, result.exitCode());
        assertEquals(ProductionMigrationMain.FAILED_MARKER + System.lineSeparator(), result.output());
        assertEquals(List.of("migrate", "validate:false", "pending"), operations.calls);
    }

    @Test
    void invalidModeAndMissingConfigurationFailWithoutCreatingFlyway() {
        RecordingFactory invalidModeFactory = new RecordingFactory(new FakeOperations(0));
        Invocation invalidMode = invoke(
                new String[] {"repair"},
                environment(),
                invalidModeFactory);
        assertEquals(ProductionMigrationMain.FAILED_EXIT, invalidMode.exitCode());
        assertFalse(invalidModeFactory.created);

        Map<String, String> missingPassword = environment();
        missingPassword.remove("SPRING_DATASOURCE_PASSWORD");
        RecordingFactory missingFactory = new RecordingFactory(new FakeOperations(0));
        Invocation missing = invoke(new String[] {"probe"}, missingPassword, missingFactory);
        assertEquals(ProductionMigrationMain.FAILED_EXIT, missing.exitCode());
        assertFalse(missingFactory.created);
    }

    @Test
    void flywayFailureIsSanitizedAndSecretsAreNeverPrinted() {
        FakeOperations operations = new FakeOperations(0);
        operations.failure = new IllegalStateException(
                URL + " " + USERNAME + " " + PASSWORD + " SELECT secret");

        Invocation result = invoke(new String[] {"probe"}, environment(), operations);

        assertEquals(ProductionMigrationMain.FAILED_EXIT, result.exitCode());
        assertEquals(ProductionMigrationMain.FAILED_MARKER + System.lineSeparator(), result.output());
        assertFalse(result.output().contains(URL));
        assertFalse(result.output().contains(USERNAME));
        assertFalse(result.output().contains(PASSWORD));
        assertFalse(result.output().contains("SELECT"));
    }

    @Test
    void datasourceValuesArePassedExactlyToTheFactory() {
        RecordingFactory factory = new RecordingFactory(new FakeOperations(0));
        Invocation result = invoke(new String[] {"probe"}, environment(), factory);

        assertEquals(ProductionMigrationMain.APPLIED_EXIT, result.exitCode());
        assertTrue(factory.created);
        assertEquals(URL, factory.url);
        assertEquals(USERNAME, factory.username);
        assertEquals(PASSWORD, factory.password);
    }

    @Test
    void initializerErrorIsConvertedToSanitizedFailure() {
        Invocation result = invoke(
                new String[] {"probe"},
                environment(),
                (url, username, password) -> {
                    throw new ExceptionInInitializerError("fictional secret");
                });

        assertEquals(ProductionMigrationMain.FAILED_EXIT, result.exitCode());
        assertEquals(
                ProductionMigrationMain.FAILED_MARKER + System.lineSeparator(),
                result.output());
        assertFalse(result.output().contains("fictional secret"));
    }

    @Test
    void pendingClassifierAcceptsOnlyResolvedNotAppliedCodes() {
        assertTrue(ProductionMigrationMain.isPending(
                validation(CoreErrorCode.RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED)));
        assertTrue(ProductionMigrationMain.isPending(
                validation(CoreErrorCode.RESOLVED_REPEATABLE_MIGRATION_NOT_APPLIED)));
    }

    @Test
    void pendingClassifierRejectsEveryOtherFlywayErrorCode() {
        assertFalse(ProductionMigrationMain.isPending(
                validation(CoreErrorCode.APPLIED_VERSIONED_MIGRATION_NOT_RESOLVED)));
        assertFalse(ProductionMigrationMain.isPending(
                validation(CoreErrorCode.APPLIED_REPEATABLE_MIGRATION_NOT_RESOLVED)));
    }

    private static ValidateOutput validation(ErrorCode code) {
        return new ValidateOutput(
                "1",
                "baseline",
                "db/migration/V1__baseline.sql",
                new ErrorDetails(code, "fictional validation detail"));
    }

    private static Invocation invoke(
            String[] args,
            Map<String, String> environment,
            ProductionMigrationMain.MigrationOperations operations) {
        return invoke(args, environment, (url, username, password) -> operations);
    }

    private static Invocation invoke(
            String[] args,
            Map<String, String> environment,
            ProductionMigrationMain.MigrationOperationsFactory factory) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int exitCode;
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            exitCode = ProductionMigrationMain.run(args, environment::get, factory, output);
        }
        return new Invocation(exitCode, bytes.toString(StandardCharsets.UTF_8));
    }

    private static Map<String, String> environment() {
        Map<String, String> environment = new HashMap<>();
        environment.put("SPRING_DATASOURCE_URL", URL);
        environment.put("SPRING_DATASOURCE_USERNAME", USERNAME);
        environment.put("SPRING_DATASOURCE_PASSWORD", PASSWORD);
        return environment;
    }

    private record Invocation(int exitCode, String output) {
    }

    private static final class RecordingFactory
            implements ProductionMigrationMain.MigrationOperationsFactory {
        private final ProductionMigrationMain.MigrationOperations operations;
        private boolean created;
        private String url;
        private String username;
        private String password;

        private RecordingFactory(ProductionMigrationMain.MigrationOperations operations) {
            this.operations = operations;
        }

        @Override
        public ProductionMigrationMain.MigrationOperations create(
                String url,
                String username,
                String password) {
            this.created = true;
            this.url = url;
            this.username = username;
            this.password = password;
            return operations;
        }
    }

    private static final class FakeOperations
            implements ProductionMigrationMain.MigrationOperations {
        private final int pending;
        private final List<String> calls = new ArrayList<>();
        private RuntimeException failure;

        private FakeOperations(int pending) {
            this.pending = pending;
        }

        @Override
        public void validate(boolean allowPending) {
            calls.add("validate:" + allowPending);
            if (failure != null) {
                throw failure;
            }
        }

        @Override
        public int pendingCount() {
            calls.add("pending");
            return pending;
        }

        @Override
        public void migrate() {
            calls.add("migrate");
            if (failure != null) {
                throw failure;
            }
        }
    }
}
