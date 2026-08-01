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


if __name__ == "__main__":
    unittest.main()
