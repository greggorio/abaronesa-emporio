#!/usr/bin/env python3
"""Validador fail-closed do bootstrap administrativo e seeds sensiveis."""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


@dataclass(frozen=True)
class ContractPaths:
    initializer: Path
    seeder: Path
    properties: tuple[Path, ...]
    env_examples: tuple[Path, ...]
    documentation: tuple[Path, ...]


def default_paths(root: Path = ROOT) -> ContractPaths:
    return ContractPaths(
        initializer=root
        / "backend/src/main/java/com/baronesa/emporio/config/RootUserInitializer.java",
        seeder=root / "backend/src/main/java/com/baronesa/emporio/ConfigSeeder.java",
        properties=(
            root / "backend/src/main/resources/application.properties",
            root / "backend/src/main/resources/application-dev.properties",
            root / "backend/src/main/resources/application-prod.properties",
            root / "backend/src/test/resources/application-test.properties",
        ),
        env_examples=(root / ".env.example", root / "backend/.env.example"),
        documentation=(
            root / "docs/development/CONFIGURACAO_LOCAL_SEGREDOS.md",
            root / "docs/development/README.md",
            root
            / "docs/infrastructure/deployment/BOOTSTRAP_ADMIN_E_CONFIGURACOES_SENSIVEIS.md",
        ),
    )


ROOT_PROPERTIES = {
    "app.bootstrap.root.enabled": "${ROOT_BOOTSTRAP_ENABLED:false}",
    "app.bootstrap.root.name": "${ROOT_BOOTSTRAP_NAME:Root}",
    "app.bootstrap.root.email": "${ROOT_BOOTSTRAP_EMAIL:}",
    "app.bootstrap.root.password": "${ROOT_BOOTSTRAP_PASSWORD:}",
}
SENSITIVE_SEEDS = {
    "nfe_certificado_path",
    "nfe_certificado_senha",
    "nfe_id_csc",
    "nfe_token_csc",
    "nfce_id_csc",
    "nfce_token_csc",
    "mercadopago_access_token_production",
    "mercadopago_public_key_production",
    "mercadopago_access_token",
    "mercadopago_public_key",
    "mercadopago_notification_url",
    "mercadopago_access_token_sandbox",
    "mercadopago_public_key_sandbox",
    "mercadopago_webhook_secret",
    "mercadopago_webhook_url",
    "pagseguro_email",
    "pagseguro_token",
    "pagseguro_public_key",
    "pagseguro_notification_url",
    "uber_client_id",
    "uber_client_secret",
    "uber_customer_id",
    "uber_access_token",
    "uber_pickup_address",
    "uber_pickup_name",
    "uber_pickup_phone",
    "uber_pickup_notes",
    "whatsapp_service_url",
    "print_agent_url",
    "print_agent_agent_id",
    "print_agent_erp_url",
}
TENANT_SEEDS = {
    "app_name",
    "segmento",
    "nfe_cnpj",
    "nfe_razao_social",
    "nfe_nome_fantasia",
    "nfe_inscricao_estadual",
    "nfe_inscricao_municipal",
    "nfe_logradouro",
    "nfe_numero",
    "nfe_bairro",
    "nfe_municipio",
    "nfe_cod_municipio",
    "nfe_uf",
    "nfe_cep",
    "nfe_complemento",
    "nfe_telefone",
    "nfe_email",
    "nfe_email_contabilidade",
    "nfe_logo_path",
    "nfe_logo_base64",
    "nfe_consumidor_cpf",
}
SENSITIVE_ENV = {
    "ROOT_BOOTSTRAP_EMAIL",
    "ROOT_BOOTSTRAP_PASSWORD",
    "INTEGRATION_SYSTEM_TOKEN_SECRET",
    "DB_PASSWORD",
    "GOOGLE_CLIENT_SECRET",
    "UBER_CLIENT_SECRET",
    "UBER_ACCESS_TOKEN",
    "ESPRESSO_SYNC_API_KEY",
    "WEBSITE_ERP_SYNC_KEY",
    "OPENAI_API_KEY",
}


def _read(path: Path, code: str, errors: list[str]) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except (OSError, UnicodeError):
        errors.append(f"{code}:{path}")
        return ""


def _properties(text: str) -> dict[str, str]:
    result: dict[str, str] = {}
    for raw in text.splitlines():
        line = raw.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            result[key.strip()] = value.strip()
    return result


def _seed_literals(source: str) -> dict[str, str]:
    return {
        key: value
        for key, value in re.findall(
            r'seedConfig\(\s*"([^"]+)"\s*,\s*"([^"]*)"', source
        )
    }


def validate(paths: ContractPaths | None = None) -> list[str]:
    if paths is None:
        paths = default_paths()
    if not isinstance(paths, ContractPaths):
        return ["INPUT_INVALID"]

    errors: list[str] = []
    initializer = _read(paths.initializer, "FILE_MISSING_INITIALIZER", errors)
    seeder = _read(paths.seeder, "FILE_MISSING_SEEDER", errors)
    property_texts = [
        _read(path, "FILE_MISSING_PROPERTIES", errors) for path in paths.properties
    ]
    env_texts = [
        (path, _read(path, "FILE_MISSING_ENV_EXAMPLE", errors))
        for path in paths.env_examples
    ]
    for path in paths.documentation:
        _read(path, "DOCUMENTATION_MISSING", errors)
    if errors:
        return errors

    base_properties = _properties(property_texts[0])
    for key, expected in ROOT_PROPERTIES.items():
        if base_properties.get(key) != expected:
            errors.append(f"ROOT_PROPERTY_INVALID:{key}")

    if re.search(r"ROOT_(?:EMAIL|SENHA|PASSWORD)\s*=", initializer):
        errors.append(f"ROOT_LITERAL_CONSTANT:{paths.initializer}")
    if "MINIMUM_PASSWORD_LENGTH = 16" not in initializer:
        errors.append(f"ROOT_PASSWORD_MINIMUM_INVALID:{paths.initializer}")
    if "if (!enabled)" not in initializer:
        errors.append(f"ROOT_NOT_OPT_IN:{paths.initializer}")
    if (
        "implements ApplicationRunner" not in initializer
        or not re.search(
            r"@Transactional\s+public void run\(ApplicationArguments\s+\w+\)",
            initializer,
        )
        or "@PostConstruct" in initializer
    ):
        errors.append(f"ROOT_TRANSACTION_TRIGGER_INVALID:{paths.initializer}")
    if "catch (" in initializer:
        errors.append(f"ROOT_VALIDATION_MAY_BE_SWALLOWED:{paths.initializer}")
    for log_call in re.findall(r"log\.(?:info|warn|error|debug)\s*\((.*?)\);", initializer, re.S):
        if re.search(r"\b(?:email|password|senha)\b", log_call, re.I):
            errors.append(f"ROOT_LOG_SENSITIVE:{paths.initializer}")
            break

    seeds = _seed_literals(seeder)
    for key in sorted(SENSITIVE_SEEDS):
        if key not in seeds or seeds[key] != "":
            errors.append(f"SENSITIVE_SEED_INVALID:{key}")
    for key in sorted(TENANT_SEEDS):
        if key not in seeds or seeds[key] != "":
            errors.append(f"TENANT_SEED_INVALID:{key}")
    if (
        '@Value("${app.fiscal.nfe-schema-path}")' not in seeder
        or '@Value("${app.fiscal.nfe-xml-path}")' not in seeder
        or not re.search(r'seedConfig\("nfe_schema_path",\s*nfeSchemaPath,', seeder)
        or not re.search(r'seedConfig\("nfe_xml_path",\s*nfeXmlPath,', seeder)
        or "/app/" in seeder
        or "/home/" in seeder
    ):
        errors.append(f"SEEDER_FISCAL_PATH_BINDING_INVALID:{paths.seeder}")
    if re.search(r"default[-_ ]key|dev[-_ ]key|change[-_ ]me", seeder, re.I):
        errors.append(f"KNOWN_FALLBACK_PRESENT:{paths.seeder}")
    for log_call in re.findall(r"log\.(?:info|warn|error|debug)\s*\((.*?)\);", seeder, re.S):
        if "valorPadrao" in log_call:
            errors.append(f"SEED_LOG_VALUE:{paths.seeder}")
            break
    if "existente.setValor" in seeder:
        errors.append(f"EXISTING_VALUE_OVERWRITE:{paths.seeder}")

    inspected_sources = [initializer, seeder, *property_texts, *(text for _, text in env_texts)]
    if any("/home/gregorio" in text for text in inspected_sources):
        errors.append("HOST_PATH_PRESENT")

    base, dev, prod, test = map(_properties, property_texts)
    for profile_name, profile in (("base", base), ("dev", dev)):
        scoped = {
            key: value
            for key, value in profile.items()
            if key.startswith("store.upload.") and key.endswith(("-dir", ".dir"))
        }
        if not scoped or any(
            "/app/" in value
            or "/home/" in value
            or not re.fullmatch(r"\$\{[A-Z0-9_]+:[^/][^}]*\}", value)
            for value in scoped.values()
        ):
            errors.append(f"LOCAL_PATH_PROFILE_INVALID:{profile_name}")
    if base.get("app.fiscal.nfe-schema-path") != "${NFE_SCHEMA_PATH:nfe/schemas}":
        errors.append("LOCAL_FISCAL_PATH_INVALID:base:schema")
    if base.get("app.fiscal.nfe-xml-path") != "${NFE_XML_PATH:nfe/xmls}":
        errors.append("LOCAL_FISCAL_PATH_INVALID:base:xml")

    expected_prod = {
        "store.upload.produto-dir": "${STORE_UPLOAD_PRODUTO_DIR:/app/uploads/produtos}",
        "store.upload.certificado-dir": "${STORE_UPLOAD_CERTIFICADO_DIR:/app/uploads/certificados}",
        "store.upload.signage-ai-dir": "${STORE_UPLOAD_SIGNAGE_AI_DIR:/app/uploads/signage/ai}",
        "app.fiscal.nfe-schema-path": "${NFE_SCHEMA_PATH:/app/nfe/schemas}",
        "app.fiscal.nfe-xml-path": "${NFE_XML_PATH:/app/nfe/xmls}",
    }
    if any(prod.get(key) != value for key, value in expected_prod.items()):
        errors.append("PROD_PATH_PROFILE_INVALID")

    expected_test = {
        "store.upload.dir": "${java.io.tmpdir}/emporio-s07-test/uploads",
        "store.upload.produto-dir": "${java.io.tmpdir}/emporio-s07-test/uploads/produtos",
        "store.upload.certificado-dir": "${java.io.tmpdir}/emporio-s07-test/uploads/certificados",
        "store.upload.signage-ai-dir": "${java.io.tmpdir}/emporio-s07-test/uploads/signage-ai",
        "app.fiscal.nfe-schema-path": "${java.io.tmpdir}/emporio-s07-test/nfe/schemas",
        "app.fiscal.nfe-xml-path": "${java.io.tmpdir}/emporio-s07-test/nfe/xmls",
    }
    if any(test.get(key) != value for key, value in expected_test.items()):
        errors.append("TEST_PATH_PROFILE_INVALID")

    for path, text in env_texts:
        values = _properties(text)
        if values.get("ROOT_BOOTSTRAP_ENABLED") != "false":
            errors.append(f"ENV_BOOTSTRAP_DEFAULT_INVALID:{path}")
        if values.get("ROOT_BOOTSTRAP_NAME") != "Root":
            errors.append(f"ENV_BOOTSTRAP_NAME_INVALID:{path}")
        for key in sorted(SENSITIVE_ENV):
            if key in values and values[key] != "":
                errors.append(f"ENV_SENSITIVE_VALUE:{path}:{key}")

    return errors


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["validate"])
    try:
        parser.parse_args(argv)
        errors = validate()
    except (OSError, ValueError, TypeError):
        print("bootstrap-contract:invalid:INPUT_INVALID", file=sys.stderr)
        return 2
    if errors:
        for error in errors:
            print(f"bootstrap-contract:invalid:{error}", file=sys.stderr)
        return 2
    print("bootstrap-contract:valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
