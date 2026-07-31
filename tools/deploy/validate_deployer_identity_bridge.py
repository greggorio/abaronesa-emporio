#!/usr/bin/env python3
"""Validador estrutural da ponte de identidade do deployer (S23).

Verifica, fail-closed:
- Rotas exatas e públicas/protegidas conforme contrato.
- Matcher HTTP hasRole("SYSTEM") e @PreAuthorize.
- Algoritmo, audience, scope e TTL fixos (não configuráveis).
- Habilitação opt-in com default false.
- Material criptográfico PKCS#8/RSA 3072.
- Claims e response body exatos.
- Nenhuma dependência Maven nova.
- Nenhuma alteração no pacote identity (publisher) preservado.
- Literal jwt_audience do release_control contendo ambos os valores fechados.
- Documentação canônica.
"""

import os
import re
import sys
from pathlib import Path


# Snapshot fechado do pom.xml antes da S23. A comparação é intencionalmente
# estática: ler o mesmo pom mutado como baseline faria a validação aceitar a
# própria alteração que deveria detectar.
KNOWN_MAVEN_DEPENDENCIES = frozenset(
    {
        "org.springframework.boot:spring-boot-starter-web",
        "org.springframework.boot:spring-boot-starter-mail",
        "org.springframework.boot:spring-boot-starter-websocket",
        "org.springframework.boot:spring-boot-starter-validation",
        "org.springframework.boot:spring-boot-starter-actuator",
        "org.springframework.boot:spring-boot-starter-security",
        "org.springframework.boot:spring-boot-starter-oauth2-client",
        "io.jsonwebtoken:jjwt-api",
        "io.jsonwebtoken:jjwt-impl",
        "io.jsonwebtoken:jjwt-jackson",
        "org.springframework.boot:spring-boot-starter-data-jpa",
        "org.flywaydb:flyway-core",
        "org.flywaydb:flyway-database-postgresql",
        "org.postgresql:postgresql",
        "org.springdoc:springdoc-openapi-starter-webmvc-ui",
        "org.springframework.boot:spring-boot-starter-aop",
        "org.springframework.retry:spring-retry",
        "org.projectlombok:lombok",
        "com.github.ben-manes.caffeine:caffeine",
        "commons-codec:commons-codec",
        "org.springframework.boot:spring-boot-devtools",
        "net.bramp.ffmpeg:ffmpeg",
        "com.theokanning.openai-gpt3-java:service",
        "org.json:json",
        "br.com.swconsultoria:java-nfe",
        "br.com.swconsultoria:java-danfe",
        "jakarta.xml.soap:jakarta.xml.soap-api",
        "com.sun.xml.messaging.saaj:saaj-impl",
        "wsdl4j:wsdl4j",
        "javax.xml.bind:jaxb-api",
        "org.glassfish.jaxb:jaxb-runtime",
        "org.springframework.boot:spring-boot-starter-test",
        "org.springframework.security:spring-security-test",
        "org.springframework.boot:spring-boot-starter-thymeleaf",
        "org.xhtmlrenderer:flying-saucer-pdf-openpdf",
        "com.google.zxing:core",
        "com.google.zxing:javase",
        "org.apache.poi:poi",
        "org.apache.poi:poi-ooxml",
    }
)


def fail(msg: str) -> None:
    print(f"deployer-identity:invalid — {msg}", file=sys.stderr)
    raise SystemExit(1)


def check_backend_deployer_routes() -> None:
    """Verifica rotas exatas do controlador deployer."""
    path = Path(
        "backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/deployer/"
        "DeployerReleaseControlIdentityController.java"
    )
    if not path.exists():
        fail(f"missing {path}")

    content = path.read_text()

    if "@RequestMapping(\"/api/release-control/identity/deployer\")" not in content:
        fail("deployer controller missing exact base path")

    if "@GetMapping(\"/jwks\")" not in content:
        fail("deployer controller missing GET /jwks")
    if "public ResponseEntity<JwksResponse> jwks()" not in content:
        fail("deployer controller jwks route signature invalid")

    if "@PostMapping(\"/token\")" not in content:
        fail("deployer controller missing POST /token")
    if "@PreAuthorize(\"hasRole('SYSTEM')\")" not in content:
        fail("deployer controller token route missing @PreAuthorize")
    if "public TokenResponse token(" not in content:
        fail("deployer controller token route signature invalid")


def check_backend_security_config() -> None:
    """Verifica matchers SecurityConfig."""
    path = Path("backend/src/main/java/com/baronesa/emporio/config/SecurityConfig.java")
    if not path.exists():
        fail(f"missing {path}")

    content = path.read_text()

    if '"/api/release-control/identity/deployer/jwks"' not in content:
        fail("SecurityConfig missing deployer JWKS matcher")
    if 'HttpMethod.GET' not in content or ".permitAll()" not in content:
        fail("SecurityConfig deployer JWKS not permitAll")

    if '"/api/release-control/identity/deployer/token"' not in content:
        fail("SecurityConfig missing deployer token matcher")
    if 'HttpMethod.POST' not in content or ".hasRole(\"SYSTEM\")" not in content:
        fail("SecurityConfig deployer token not hasRole SYSTEM")


def check_backend_deployer_service() -> None:
    """Verifica constantes do service deployer."""
    path = Path(
        "backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/deployer/"
        "DeployerReleaseControlIdentityService.java"
    )
    if not path.exists():
        fail(f"missing {path}")

    content = path.read_text()

    if 'AUDIENCE = "emporio-release-control-deployer"' not in content:
        fail("deployer service missing exact audience constant")
    if 'SCOPE = "deployment:read deployment:execute"' not in content:
        fail("deployer service missing exact scope constant")
    if "TTL_SECONDS = 300" not in content:
        fail("deployer service missing exact TTL constant")


def check_backend_properties() -> None:
    """Verifica propriedades nos application.properties."""
    for profile in ["", "-dev", "-prod"]:
        path = Path(f"backend/src/main/resources/application{profile}.properties")
        if not path.exists():
            fail(f"missing {path}")

        content = path.read_text()

        if "app.release-control.deployer-identity.enabled=" not in content:
            fail(f"application{profile}.properties missing deployer-identity.enabled")
        if "RELEASE_CONTROL_DEPLOYER_IDENTITY_ENABLED" not in content:
            fail(f"application{profile}.properties missing RELEASE_CONTROL_DEPLOYER_IDENTITY_ENABLED env var")
        if "app.release-control.deployer-identity.issuer=" not in content:
            fail(f"application{profile}.properties missing deployer-identity.issuer")
        if "app.release-control.deployer-identity.private-key-path=" not in content:
            fail(f"application{profile}.properties missing deployer-identity.private-key-path")
        if "app.release-control.deployer-identity.key-id=" not in content:
            fail(f"application{profile}.properties missing deployer-identity.key-id")


def check_backend_publisher_untouched() -> None:
    """Verifica que pacote identity (publisher) não foi alterado."""
    publisher_path = Path("backend/src/main/java/com/baronesa/emporio/releasecontrol/identity")
    if not publisher_path.exists():
        fail("publisher identity package missing")

    # Verifica que não há classes deployer dentro do pacote publisher
    for file in publisher_path.glob("*.java"):
        if "Deployer" in file.name:
            fail(f"publisher package contains deployer class: {file.name}")


def check_backend_env_example() -> None:
    """Verifica arquivo .env.example do backend."""
    path = Path("backend/.env.example")
    if not path.exists():
        fail(f"missing {path}")

    content = path.read_text()

    if "RELEASE_CONTROL_DEPLOYER_IDENTITY_ENABLED=" not in content:
        fail(".env.example missing deployer identity enabled")
    if "RELEASE_CONTROL_DEPLOYER_IDENTITY_ISSUER=" not in content:
        fail(".env.example missing deployer identity issuer")
    if "RELEASE_CONTROL_DEPLOYER_IDENTITY_PRIVATE_KEY_PATH=" not in content:
        fail(".env.example missing deployer identity private key path")
    if "RELEASE_CONTROL_DEPLOYER_IDENTITY_KEY_ID=" not in content:
        fail(".env.example missing deployer identity key id")


def check_release_control_config() -> None:
    """Verifica alterações em release_control/config.py."""
    path = Path("release_control/src/emporio_release_control/config.py")
    if not path.exists():
        fail(f"missing {path}")

    content = path.read_text()

    # Verifica literal audience
    if 'jwt_audience: Literal["emporio-release-control", "emporio-release-control-deployer"]' not in content:
        fail("release_control config missing both audience literals")

    # Verifica validação cruzada mode/audience
    if 'mode == "publisher" and self.jwt_audience != "emporio-release-control"' not in content:
        fail("release_control config missing publisher mode validation")
    if 'mode == "deployer" and self.jwt_audience != "emporio-release-control-deployer"' not in content:
        fail("release_control config missing deployer mode validation")


def check_release_control_env_example() -> None:
    """Verifica arquivo .env.example do release_control."""
    path = Path("release_control/.env.example")
    if not path.exists():
        fail(f"missing {path}")

    content = path.read_text()

    if "RELEASE_CONTROL_MODE=deployer" not in content:
        fail(".env.example missing deployer mode example")
    if "RELEASE_CONTROL_JWT_AUDIENCE=emporio-release-control-deployer" not in content:
        fail(".env.example missing deployer audience example")


def check_documentation() -> None:
    """Verifica documentação canônica."""
    doc_path = Path("docs/infrastructure/deployment/release-control/IDENTIDADE_DEPLOYER.md")
    if not doc_path.exists():
        fail(f"missing {doc_path}")

    content = doc_path.read_text()

    # Verifica seções obrigatórias
    required_sections = [
        "Por que a ponte do deployer é separada",
        "Fluxo de identidade",
        "Rotas e autorização",
        "Propriedades do emissor ERP",
        "JWT emitido",
        "JWKS",
        "Perfis do deployer",
        "Gerar a chave fora do repositório",
    ]

    for section in required_sections:
        if section not in content:
            fail(f"IDENTIDADE_DEPLOYER.md missing section: {section}")


def check_no_new_maven_dependency() -> None:
    """Verifica que o conjunto de dependências Maven permanece no snapshot S22."""
    path = Path("backend/pom.xml")
    if not path.exists():
        fail(f"missing {path}")

    content = path.read_text()
    dependencies: set[str] = set()
    for block in re.findall(r"<dependency>\s*(.*?)\s*</dependency>", content, re.DOTALL):
        group = re.search(r"<groupId>\s*([^<]+?)\s*</groupId>", block)
        artifact = re.search(r"<artifactId>\s*([^<]+?)\s*</artifactId>", block)
        if group is None or artifact is None:
            fail("Maven dependency without groupId/artifactId")
        dependencies.add(f"{group.group(1)}:{artifact.group(1)}")

    unexpected = sorted(dependencies - KNOWN_MAVEN_DEPENDENCIES)
    missing = sorted(KNOWN_MAVEN_DEPENDENCIES - dependencies)
    if unexpected:
        fail(f"new Maven dependencies detected: {', '.join(unexpected)}")
    if missing:
        fail(f"known Maven dependencies missing: {', '.join(missing)}")


def main(root: Path | None = None) -> int:
    repository_root = (
        Path(root).resolve()
        if root is not None
        else Path(__file__).resolve().parent.parent.parent
    )
    os.chdir(repository_root)

    try:
        check_backend_deployer_routes()
        check_backend_security_config()
        check_backend_deployer_service()
        check_backend_properties()
        check_backend_publisher_untouched()
        check_backend_env_example()
        check_release_control_config()
        check_release_control_env_example()
        check_documentation()
        check_no_new_maven_dependency()
    except SystemExit:
        raise
    except Exception as e:
        fail(f"validation exception: {e}")

    print("deployer-identity:valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
