from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from dataclasses import replace
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
MODULE_PATH = ROOT / "tools/docker/java_images_contract.py"
SPEC = importlib.util.spec_from_file_location("java_images_contract", MODULE_PATH)
contract = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
sys.modules[SPEC.name] = contract
SPEC.loader.exec_module(contract)


class JavaImagesContractTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        temp = Path(self.temporary.name)
        real = contract.default_files()
        values = {}
        for index, source in enumerate(real.__dict__.values()):
            target = temp / f"{index}-{source.name}"
            target.write_text(source.read_text(encoding="utf-8"), encoding="utf-8")
            values[source] = target
        self.files = contract.ContractFiles(
            *(values[source] for source in real.__dict__.values())
        )

    def mutate(self, path: Path, old: str, new: str):
        text = path.read_text(encoding="utf-8")
        self.assertIn(old, text)
        path.write_text(text.replace(old, new, 1), encoding="utf-8")

    def assert_invalid(self):
        self.assertTrue(contract.validate(self.files))

    def test_01_real_contract_is_valid(self):
        self.assertEqual([], contract.validate())

    def test_02_missing_file_fails(self):
        self.files.backend_dockerfile.unlink()
        self.assert_invalid()

    def test_03_exact_stages_are_required(self):
        self.mutate(self.files.backend_dockerfile, " AS build", " AS builder")
        self.assert_invalid()

    def test_04_java_21_maven_39_base_is_required(self):
        self.mutate(self.files.backend_dockerfile, contract.BUILD_TAG, "maven:3.8-eclipse-temurin-17")
        self.assert_invalid()

    def test_05_complete_runtime_tag_is_required(self):
        self.mutate(self.files.website_dockerfile, contract.RUNTIME_TAG, "eclipse-temurin:21-jre-alpine")
        self.assert_invalid()

    def test_06_digest_is_required(self):
        self.mutate(self.files.backend_dockerfile, "@sha256:", "@missing:")
        self.assert_invalid()

    def test_07_latest_is_forbidden(self):
        self.mutate(self.files.backend_dockerfile, "ARG VCS_REF=unknown", "ARG VCS_REF=latest")
        self.assert_invalid()

    def test_08_runtime_maven_is_forbidden(self):
        self.mutate(self.files.website_dockerfile, "RUN apk add --no-cache", "RUN apk add --no-cache maven && apk add --no-cache")
        self.assert_invalid()

    def test_09_non_root_numeric_user_is_required(self):
        self.mutate(self.files.backend_dockerfile, "USER 10001:10001", "USER 0:0")
        self.assert_invalid()

    def test_10_entrypoint_exec_form_is_required(self):
        self.mutate(self.files.backend_dockerfile, 'ENTRYPOINT ["java", "-jar", "/app/app.jar"]', "ENTRYPOINT java -jar /app/app.jar")
        self.assert_invalid()

    def test_11_java_tool_options_is_required(self):
        self.mutate(self.files.website_dockerfile, 'ENV JAVA_TOOL_OPTIONS=""', 'ENV OTHER=""')
        self.assert_invalid()

    def test_12_prod_profile_is_required(self):
        self.mutate(self.files.website_dockerfile, "SPRING_PROFILES_ACTIVE=prod", "SPRING_PROFILES_ACTIVE=dev")
        self.assert_invalid()

    def test_13_backend_port_is_exact(self):
        self.mutate(self.files.backend_dockerfile, "EXPOSE 8080", "EXPOSE 8081")
        self.assert_invalid()

    def test_14_health_path_is_exact(self):
        self.mutate(self.files.website_dockerfile, "/actuator/health", "/health")
        self.assert_invalid()

    def test_15_health_must_use_loopback(self):
        self.mutate(self.files.backend_dockerfile, "127.0.0.1", "backend.example")
        self.assert_invalid()

    def test_16_health_options_are_required(self):
        self.mutate(self.files.backend_dockerfile, "--start-period=60s ", "")
        self.assert_invalid()

    def test_17_common_runtime_package_is_required(self):
        self.mutate(self.files.website_dockerfile, "ca-certificates ", "")
        self.assert_invalid()

    def test_18_backend_ffmpeg_is_required(self):
        self.mutate(self.files.backend_dockerfile, "ffmpeg ", "")
        self.assert_invalid()

    def test_19_backend_fonts_are_required(self):
        self.mutate(self.files.backend_dockerfile, "ttf-dejavu", "font-missing")
        self.assert_invalid()

    def test_20_schema_copy_is_required(self):
        self.mutate(self.files.backend_dockerfile, "nfe/schemas/ /app/nfe/schemas/", "nfe/schemas/ /tmp/schemas/")
        self.assert_invalid()

    def test_21_personal_path_is_forbidden(self):
        self.mutate(self.files.backend_dockerfile, "WORKDIR /workspace", "WORKDIR /home/gregorio")
        self.assert_invalid()

    def test_22_sensitive_arg_is_forbidden(self):
        self.mutate(self.files.website_dockerfile, "ARG VCS_REF=unknown", "ARG PASSWORD=unknown")
        self.assert_invalid()

    def test_23_extra_arg_is_forbidden(self):
        self.mutate(self.files.website_dockerfile, "ARG IMAGE_VERSION=local", "ARG IMAGE_VERSION=local\nARG EXTRA=value")
        self.assert_invalid()

    def test_24_dockerignore_item_is_required(self):
        self.mutate(self.files.backend_ignore, "*.hprof\n", "")
        self.assert_invalid()

    def test_25_dockerignore_cannot_hide_source(self):
        with self.files.website_ignore.open("a", encoding="utf-8") as stream:
            stream.write("\nsrc/**\n")
        self.assert_invalid()

    def test_26_actuator_dependency_is_required(self):
        self.mutate(self.files.website_pom, "spring-boot-starter-actuator", "actuator-removed")
        self.assert_invalid()

    def test_27_prod_must_expose_only_health(self):
        self.mutate(self.files.website_prod_properties, "exposure.include=health", "exposure.include=*")
        self.assert_invalid()

    def test_28_health_details_must_be_hidden(self):
        self.mutate(self.files.website_prod_properties, "show-details=never", "show-details=always")
        self.assert_invalid()

    def test_29_security_must_allow_exact_health(self):
        self.mutate(self.files.website_security, '"/actuator/health"', '"/health"')
        self.assert_invalid()

    def test_30_writable_directory_contract_is_required(self):
        self.mutate(self.files.website_dockerfile, "/app/uploads/android-private", "/tmp/android-private")
        self.assert_invalid()

    def test_31_volume_is_forbidden(self):
        self.mutate(self.files.backend_dockerfile, "EXPOSE 8080", "VOLUME /app/uploads\nEXPOSE 8080")
        self.assert_invalid()

    def test_32_docker_socket_is_forbidden(self):
        self.mutate(self.files.backend_dockerfile, "WORKDIR /app", "RUN echo /var/run/docker.sock\nWORKDIR /app")
        self.assert_invalid()

    def test_33_documentation_is_required(self):
        self.files.documentation.unlink()
        self.assert_invalid()

    def test_34_invalid_input_fails_closed(self):
        self.assertEqual(["INPUT_INVALID"], contract.validate("invalid"))

    def test_35_error_output_does_not_include_mutated_value(self):
        marker = "private-marker-not-for-output"
        self.mutate(self.files.website_dockerfile, "ARG VCS_REF=unknown", f"ARG SECRET={marker}")
        self.assertNotIn(marker, "\n".join(contract.validate(self.files)))

    def test_36_backend_schema_must_be_read_only(self):
        self.mutate(self.files.backend_dockerfile, "chmod -R a-w /app/nfe/schemas", "chmod -R u+w /app/nfe/schemas")
        self.assert_invalid()

    def test_37_previous_base_reference_is_rejected(self):
        previous = (
            "maven:3.9.11-eclipse-temurin-21-alpine@"
            "sha256:922927df2c662cdd47ddb116443d6bec4696cfae3de1a0ddac8fcc7b87ce61ae"
        )
        self.mutate(self.files.backend_dockerfile, contract.BUILD_BASE, previous)
        self.assertIn("BASE_TAG_INVALID:backend", contract.validate(self.files))

    def assert_error(self, error: str):
        self.assertIn(error, contract.validate(self.files))

    def dockerfile(self, component: str) -> Path:
        return (
            self.files.backend_dockerfile
            if component == "backend"
            else self.files.website_dockerfile
        )

    def pom(self, component: str) -> Path:
        return (
            self.files.backend_pom
            if component == "backend"
            else self.files.website_pom
        )

    def migration(self, component: str) -> Path:
        return (
            self.files.backend_migration
            if component == "backend"
            else self.files.website_migration
        )

    def test_38_floating_frontend_is_rejected(self):
        for component in ("backend", "website_back"):
            with self.subTest(component=component):
                self.setUp()
                self.mutate(
                    self.dockerfile(component),
                    contract.FRONTEND_DIRECTIVE,
                    "# syntax=docker/dockerfile:1.7",
                )
                self.assert_error(f"DOCKERFILE_FRONTEND_INVALID:{component}")

    def test_39_frontend_without_digest_is_rejected(self):
        self.mutate(
            self.files.backend_dockerfile,
            contract.FRONTEND_DIRECTIVE,
            "# syntax=docker.io/docker/dockerfile:1.7",
        )
        self.assert_error("DOCKERFILE_FRONTEND_INVALID:backend")

    def test_40_spring_boot_baseline_is_exact(self):
        for component in ("backend", "website_back"):
            with self.subTest(component=component):
                self.setUp()
                self.mutate(
                    self.pom(component),
                    f"<version>{contract.SPRING_BOOT_BASELINE}</version>",
                    "<version>3.3.13</version>",
                )
                self.assert_error(f"SPRING_BOOT_BASELINE_INVALID:{component}")

    def test_41_springdoc_baseline_is_exact(self):
        for component in ("backend", "website_back"):
            with self.subTest(component=component):
                self.setUp()
                self.mutate(
                    self.pom(component),
                    f"<springdoc.version>{contract.SPRINGDOC_BASELINE}</springdoc.version>",
                    "<springdoc.version>2.6.0</springdoc.version>",
                )
                self.assert_error(f"SPRINGDOC_BASELINE_INVALID:{component}")

    def test_42_tomcat_override_is_forbidden(self):
        for component in ("backend", "website_back"):
            with self.subTest(component=component):
                self.setUp()
                self.mutate(
                    self.pom(component),
                    "        <springdoc.version>",
                    "        <tomcat.version>10.1.55</tomcat.version>\n        <springdoc.version>",
                )
                self.assert_error(f"SPRING_BOM_OVERRIDE_FORBIDDEN:{component}")

    def test_43_jackson_override_is_forbidden(self):
        for component in ("backend", "website_back"):
            with self.subTest(component=component):
                self.setUp()
                self.mutate(
                    self.pom(component),
                    "        <springdoc.version>",
                    "        <jackson-bom.version>2.18.8</jackson-bom.version>\n        <springdoc.version>",
                )
                self.assert_error(f"SPRING_BOM_OVERRIDE_FORBIDDEN:{component}")

    def test_44_protective_property_removal_is_rejected(self):
        cases = (
            ("backend", "postgresql.version", "42.7.12"),
            ("backend", "thymeleaf.version", "3.1.5.RELEASE"),
            ("website_back", "postgresql.version", "42.7.12"),
            ("website_back", "netty.version", "4.1.136.Final"),
        )
        for component, prop, version in cases:
            with self.subTest(component=component, property=prop):
                self.setUp()
                self.mutate(self.pom(component), f"<{prop}>{version}</{prop}>", "")
                self.assert_error(
                    f"PROTECTIVE_OVERRIDE_MISSING:{component}:{prop}"
                )

    def test_45_protective_managed_downgrade_is_rejected(self):
        cases = (
            ("backend", "commons-beanutils", "1.11.0", "1.9.4"),
            ("backend", "neethi", "3.2.2", "3.1.1"),
            ("website_back", "protobuf-java", "3.25.5", "3.25.1"),
            ("website_back", "grpc-netty-shaded", "1.75.0", "1.68.0"),
        )
        for component, artifact, version, downgrade in cases:
            with self.subTest(component=component, artifact=artifact):
                self.setUp()
                self.mutate(
                    self.pom(component),
                    f"<artifactId>{artifact}</artifactId>\n"
                    f"                <version>{version}</version>",
                    f"<artifactId>{artifact}</artifactId>\n"
                    f"                <version>{downgrade}</version>",
                )
                self.assert_error(
                    f"PROTECTIVE_OVERRIDE_MISSING:{component}:{artifact}"
                )

    def test_46_missing_core_error_code_is_rejected(self):
        for component in ("backend", "website_back"):
            with self.subTest(component=component):
                self.setUp()
                self.mutate(
                    self.migration(component),
                    "CoreErrorCode.RESOLVED_REPEATABLE_MIGRATION_NOT_APPLIED",
                    "CoreErrorCode.NULL_VARIABLE",
                )
                self.assert_error(f"FLYWAY_ERROR_CODE_INVALID:{component}")

    def test_47_obsolete_error_code_form_is_rejected(self):
        for component in ("backend", "website_back"):
            with self.subTest(component=component):
                self.setUp()
                self.mutate(
                    self.migration(component),
                    "CoreErrorCode.RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED",
                    "ErrorCode.RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED",
                )
                self.assert_error(f"FLYWAY_ERROR_CODE_INVALID:{component}")

    def test_49_okhttp_property_value_is_exact(self):
        self.mutate(
            self.files.backend_pom,
            "<okhttp.version>4.12.0</okhttp.version>",
            "<okhttp.version>3.14.9</okhttp.version>",
        )
        self.assert_error("PROTECTIVE_OVERRIDE_MISSING:backend:okhttp.version")

    def test_50_okhttp_property_removal_is_rejected(self):
        self.mutate(
            self.files.backend_pom,
            "<okhttp.version>4.12.0</okhttp.version>",
            "",
        )
        self.assert_error("PROTECTIVE_OVERRIDE_MISSING:backend:okhttp.version")

    def test_51_okhttp_bom_import_is_required(self):
        text = self.files.backend_pom.read_text(encoding="utf-8")
        block = (
            "<dependency>\n"
            "                <groupId>com.squareup.okhttp3</groupId>\n"
            "                <artifactId>okhttp-bom</artifactId>\n"
            "                <version>${okhttp.version}</version>\n"
            "                <type>pom</type>\n"
            "                <scope>import</scope>\n"
            "            </dependency>\n            "
        )
        self.assertIn(block, text)
        self.files.backend_pom.write_text(text.replace(block, "", 1), encoding="utf-8")
        self.assert_error("OKHTTP_BOM_IMPORT_REQUIRED:backend")

    def test_52_okhttp_bom_literal_version_is_rejected(self):
        self.mutate(
            self.files.backend_pom,
            "<version>${okhttp.version}</version>",
            "<version>4.12.0</version>",
        )
        self.assert_error("OKHTTP_BOM_IMPORT_REQUIRED:backend")

    def test_53_okhttp_bom_without_pom_type_is_rejected(self):
        self.mutate(self.files.backend_pom, "<type>pom</type>\n", "")
        self.assert_error("OKHTTP_BOM_IMPORT_REQUIRED:backend")

    def test_54_okhttp_bom_without_import_scope_is_rejected(self):
        self.mutate(self.files.backend_pom, "<scope>import</scope>\n", "")
        self.assert_error("OKHTTP_BOM_IMPORT_REQUIRED:backend")

    def test_55_isolated_okhttp_property_is_not_enough(self):
        text = self.files.backend_pom.read_text(encoding="utf-8")
        block = (
            "<dependency>\n"
            "                <groupId>com.squareup.okhttp3</groupId>\n"
            "                <artifactId>okhttp-bom</artifactId>\n"
            "                <version>${okhttp.version}</version>\n"
            "                <type>pom</type>\n"
            "                <scope>import</scope>\n"
            "            </dependency>\n            "
        )
        self.files.backend_pom.write_text(text.replace(block, "", 1), encoding="utf-8")
        errors = contract.validate(self.files)
        self.assertEqual(["OKHTTP_BOM_IMPORT_REQUIRED:backend"], errors)

    def test_56_java_danfe_property_reintroduction_is_rejected(self):
        self.mutate(
            self.files.backend_pom,
            "        <java-nfe.version>4.00.42</java-nfe.version>",
            "        <java-nfe.version>4.00.42</java-nfe.version>\n"
            "        <java-danfe.version>1.8</java-danfe.version>",
        )
        self.assert_error("UNUSED_JAVA_DANFE_FORBIDDEN:backend")

    def test_57_java_danfe_dependency_reintroduction_is_rejected(self):
        self.mutate(
            self.files.backend_pom,
            "        <dependency>\n            <groupId>com.google.zxing</groupId>",
            "        <dependency>\n"
            "            <groupId>br.com.swconsultoria</groupId>\n"
            "            <artifactId>java-danfe</artifactId>\n"
            "            <version>1.8</version>\n"
            "        </dependency>\n"
            "        <dependency>\n            <groupId>com.google.zxing</groupId>",
        )
        self.assert_error("UNUSED_JAVA_DANFE_FORBIDDEN:backend")

    def test_58_direct_jasperreports_dependency_is_rejected(self):
        self.mutate(
            self.files.backend_pom,
            "        <dependency>\n            <groupId>com.google.zxing</groupId>",
            "        <dependency>\n"
            "            <groupId>net.sf.jasperreports</groupId>\n"
            "            <artifactId>jasperreports</artifactId>\n"
            "            <version>7.0.4</version>\n"
            "        </dependency>\n"
            "        <dependency>\n            <groupId>com.google.zxing</groupId>",
        )
        self.assert_error("JASPERREPORTS_FORBIDDEN:backend")

    def test_59_jasperreports_fonts_dependency_is_rejected(self):
        self.mutate(
            self.files.backend_pom,
            "        <dependency>\n            <groupId>com.google.zxing</groupId>",
            "        <dependency>\n"
            "            <groupId>net.sf.jasperreports</groupId>\n"
            "            <artifactId>jasperreports-fonts</artifactId>\n"
            "            <version>6.20.6</version>\n"
            "        </dependency>\n"
            "        <dependency>\n            <groupId>com.google.zxing</groupId>",
        )
        self.assert_error("JASPERREPORTS_FORBIDDEN:backend")

    def test_60_jasperreports_property_reintroduction_is_rejected(self):
        self.mutate(
            self.files.backend_pom,
            "        <java-nfe.version>4.00.42</java-nfe.version>",
            "        <java-nfe.version>4.00.42</java-nfe.version>\n"
            "        <jasperreports.version>6.20.6</jasperreports.version>",
        )
        self.assert_error("JASPERREPORTS_FORBIDDEN:backend")

    def test_61_thymeleaf_starter_removal_is_rejected(self):
        self.mutate(
            self.files.backend_pom,
            "<artifactId>spring-boot-starter-thymeleaf</artifactId>",
            "<artifactId>spring-boot-starter-web</artifactId>",
        )
        self.assert_error("DANFE_RENDERER_REQUIRED:backend")

    def test_62_flying_saucer_removal_is_rejected(self):
        self.mutate(
            self.files.backend_pom,
            "<artifactId>flying-saucer-pdf-openpdf</artifactId>",
            "<artifactId>flying-saucer-core</artifactId>",
        )
        self.assert_error("DANFE_RENDERER_REQUIRED:backend")

    def test_63_flying_saucer_downgrade_is_rejected(self):
        self.mutate(
            self.files.backend_pom,
            "<artifactId>flying-saucer-pdf-openpdf</artifactId>\n"
            "            <version>9.1.22</version>",
            "<artifactId>flying-saucer-pdf-openpdf</artifactId>\n"
            "            <version>9.1.20</version>",
        )
        self.assert_error("DANFE_RENDERER_REQUIRED:backend")

    def test_64_zxing_core_downgrade_is_rejected(self):
        self.mutate(
            self.files.backend_pom,
            "<artifactId>core</artifactId>\n            <version>3.5.3</version>",
            "<artifactId>core</artifactId>\n            <version>3.4.1</version>",
        )
        self.assert_error("DANFE_RENDERER_REQUIRED:backend")

    def test_65_zxing_javase_downgrade_is_rejected(self):
        self.mutate(
            self.files.backend_pom,
            "<artifactId>javase</artifactId>\n            <version>3.5.3</version>",
            "<artifactId>javase</artifactId>\n            <version>3.4.1</version>",
        )
        self.assert_error("DANFE_RENDERER_REQUIRED:backend")

    def test_66_zxing_javase_removal_is_rejected(self):
        self.mutate(
            self.files.backend_pom,
            "<artifactId>javase</artifactId>",
            "<artifactId>javase-removed</artifactId>",
        )
        self.assert_error("DANFE_RENDERER_REQUIRED:backend")

    def test_67_java_nfe_is_not_confused_with_java_danfe(self):
        text = self.files.backend_pom.read_text(encoding="utf-8")
        self.assertIn("<artifactId>java-nfe</artifactId>", text)
        self.assertIn("<groupId>br.com.swconsultoria</groupId>", text)
        self.assertEqual([], contract.validate(self.files))

    def test_48_broadened_tolerated_error_list_is_rejected(self):
        self.mutate(
            self.files.backend_migration,
            "CoreErrorCode.RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED",
            "ErrorCode.RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED",
        )
        errors = contract.validate(self.files)
        self.assertEqual(["FLYWAY_ERROR_CODE_INVALID:backend"], errors)


if __name__ == "__main__":
    unittest.main()
