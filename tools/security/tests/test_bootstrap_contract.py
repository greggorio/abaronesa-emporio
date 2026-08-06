from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from dataclasses import replace
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
MODULE_PATH = ROOT / "tools/security/bootstrap_contract.py"
SPEC = importlib.util.spec_from_file_location("bootstrap_contract", MODULE_PATH)
contract = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
sys.modules[SPEC.name] = contract
SPEC.loader.exec_module(contract)


class BootstrapContractTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.temp_root = Path(self.temporary.name)
        real = contract.default_paths()
        all_files = (
            (real.initializer, real.seeder)
            + real.properties
            + real.env_examples
            + real.documentation
        )
        copies = {}
        for index, source in enumerate(all_files):
            target = self.temp_root / f"{index}-{source.name}"
            target.write_text(source.read_text(encoding="utf-8"), encoding="utf-8")
            copies[source] = target
        self.paths = contract.ContractPaths(
            initializer=copies[real.initializer],
            seeder=copies[real.seeder],
            properties=tuple(copies[path] for path in real.properties),
            env_examples=tuple(copies[path] for path in real.env_examples),
            documentation=tuple(copies[path] for path in real.documentation),
        )

    def mutate(self, path: Path, old: str, new: str):
        text = path.read_text(encoding="utf-8")
        self.assertIn(old, text)
        path.write_text(text.replace(old, new, 1), encoding="utf-8")

    def assert_invalid(self):
        self.assertTrue(contract.validate(self.paths))

    def test_01_real_contract_is_valid(self):
        self.assertEqual([], contract.validate())

    def test_02_bootstrap_enabled_by_default_fails(self):
        self.mutate(self.paths.properties[0], "ROOT_BOOTSTRAP_ENABLED:false", "ROOT_BOOTSTRAP_ENABLED:true")
        self.assert_invalid()

    def test_03_root_email_fallback_fails(self):
        self.mutate(self.paths.properties[0], "ROOT_BOOTSTRAP_EMAIL:}", "ROOT_BOOTSTRAP_EMAIL:fixture}")
        self.assert_invalid()

    def test_04_root_password_fallback_fails(self):
        self.mutate(self.paths.properties[0], "ROOT_BOOTSTRAP_PASSWORD:}", "ROOT_BOOTSTRAP_PASSWORD:fixture}")
        self.assert_invalid()

    def test_05_java_root_constant_fails(self):
        self.mutate(self.paths.initializer, "private static final int MINIMUM", "private static final String ROOT_PASSWORD = \"fixture\"; private static final int MINIMUM")
        self.assert_invalid()

    def test_06_shorter_password_rule_fails(self):
        self.mutate(self.paths.initializer, "MINIMUM_PASSWORD_LENGTH = 6", "MINIMUM_PASSWORD_LENGTH = 4")
        self.assert_invalid()

    def test_07_sensitive_root_log_fails(self):
        self.mutate(self.paths.initializer, 'log.info("Bootstrap root criou usuario SYSTEM");', 'log.info("Bootstrap root email {}", email);')
        self.assert_invalid()

    def test_08_sensitive_seed_literal_fails(self):
        self.mutate(self.paths.seeder, 'seedConfig("nfe_token_csc", ""', 'seedConfig("nfe_token_csc", "fixture"')
        self.assert_invalid()

    def test_09_account_identifier_literal_fails(self):
        self.mutate(self.paths.seeder, 'seedConfig("uber_customer_id", ""', 'seedConfig("uber_customer_id", "fixture"')
        self.assert_invalid()

    def test_10_tenant_identity_literal_fails(self):
        self.mutate(self.paths.seeder, 'seedConfig("nfe_cnpj", ""', 'seedConfig("nfe_cnpj", "fixture"')
        self.assert_invalid()

    def test_11_seed_value_log_fails(self):
        self.mutate(
            self.paths.seeder,
            "log.info(\"SEED: Criando configuracao ausente '{}'\", chave);",
            'log.info("SEED: valor {}", valorPadrao);',
        )
        self.assert_invalid()

    def test_12_known_fallback_fails(self):
        self.mutate(self.paths.seeder, '${website.sync.api-key:}', '${website.sync.api-key:dev-key}')
        self.assert_invalid()

    def test_13_host_path_fails(self):
        self.mutate(
            self.paths.seeder,
            '@Value("${app.fiscal.nfe-xml-path}")',
            '@Value("/home/gregorio/xmls")',
        )
        self.assert_invalid()

    def test_14_fiscal_path_drift_fails(self):
        self.mutate(
            self.paths.properties[0],
            "${NFE_SCHEMA_PATH:nfe/schemas}",
            "${NFE_SCHEMA_PATH:/tmp/schemas}",
        )
        self.assert_invalid()

    def test_15_env_sensitive_value_fails_without_leaking_value(self):
        marker = "private-fixture-marker"
        with self.paths.env_examples[0].open("a", encoding="utf-8") as stream:
            stream.write(f"\nROOT_BOOTSTRAP_PASSWORD={marker}\n")
        errors = contract.validate(self.paths)
        self.assertTrue(errors)
        self.assertNotIn(marker, "\n".join(errors))

    def test_16_missing_required_file_fails(self):
        self.paths.documentation[0].unlink()
        self.assert_invalid()

    def test_17_missing_input_path_returns_invalid(self):
        missing = replace(self.paths, initializer=self.temp_root / "missing.java")
        self.assertTrue(contract.validate(missing))

    def test_18_invalid_input_fails_closed(self):
        self.assertEqual(["INPUT_INVALID"], contract.validate("invalid"))

    def test_19_existing_database_value_overwrite_fails(self):
        self.mutate(self.paths.seeder, "} else {", "} else { existente.setValor(valorPadrao);")
        self.assert_invalid()

    def test_20_upload_path_drift_fails(self):
        self.mutate(
            self.paths.properties[0],
            "store.upload.dir=${STORE_UPLOAD_DIR:uploads}",
            "store.upload.dir=${STORE_UPLOAD_DIR:/app/uploads}",
        )
        self.assert_invalid()

    def test_21_post_construct_transaction_regression_fails(self):
        self.mutate(
            self.paths.initializer,
            "implements ApplicationRunner",
            "",
        )
        self.mutate(
            self.paths.initializer,
            "@Override\n    @Transactional",
            "@PostConstruct\n    @Transactional",
        )
        self.assert_invalid()

    def test_22_development_container_path_fails(self):
        self.mutate(
            self.paths.properties[1],
            "${STORE_UPLOAD_PRODUTO_DIR:uploads/produtos}",
            "${STORE_UPLOAD_PRODUTO_DIR:/app/uploads/produtos}",
        )
        self.assert_invalid()

    def test_23_development_host_path_fails(self):
        self.mutate(
            self.paths.properties[1],
            "${STORE_UPLOAD_CERTIFICADO_DIR:uploads/certificados}",
            "${STORE_UPLOAD_CERTIFICADO_DIR:/home/gregorio/uploads}",
        )
        self.assert_invalid()

    def test_24_production_container_default_is_required(self):
        self.mutate(
            self.paths.properties[2],
            "${NFE_SCHEMA_PATH:/app/nfe/schemas}",
            "${NFE_SCHEMA_PATH:nfe/schemas}",
        )
        self.assert_invalid()

    def test_25_test_profile_must_use_temporary_directory(self):
        self.mutate(
            self.paths.properties[3],
            "${java.io.tmpdir}/emporio-s07-test/nfe/xmls",
            "/app/nfe/xmls",
        )
        self.assert_invalid()

    def test_26_seeder_cannot_hardcode_container_path(self):
        self.mutate(
            self.paths.seeder,
            'seedConfig("nfe_schema_path", nfeSchemaPath,',
            'seedConfig("nfe_schema_path", "/app/nfe/schemas",',
        )
        self.assert_invalid()


if __name__ == "__main__":
    unittest.main()
