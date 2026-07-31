#!/usr/bin/env python3
import json
import re
import os
import sys
import urllib.request
import zipfile
from pathlib import Path


def fail(message):
    print(f"ERROR: {message}", file=sys.stderr)
    sys.exit(1)


def fetch_json(url, token=None):
    req = urllib.request.Request(url)
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode("utf-8"))


def download_file(url, dest, token=None):
    req = urllib.request.Request(url)
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(req) as resp, open(dest, "wb") as f:
        f.write(resp.read())


def normalize_entries(entries, strip_first):
    normalized = set()
    for entry in entries:
        if strip_first:
            parts = entry.split("/", 1)
            if len(parts) == 2:
                normalized.add(parts[1])
                continue
        normalized.add(entry)
    return normalized


def validate_icons_zip(zip_path):
    with zipfile.ZipFile(zip_path) as zf:
        entries = [e for e in zf.namelist() if not e.endswith("/")]

    if not entries:
        return False

    required_xml = {
        "mipmap-anydpi-v26/ic_launcher.xml",
        "mipmap-anydpi-v26/ic_launcher_round.xml",
        "drawable/ic_launcher_background.xml",
        "drawable-v24/ic_launcher_foreground.xml",
    }

    def has_icons(entries_set):
        densities = ["mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"]
        for density in densities:
            base = f"mipmap-{density}/"
            for name in ("ic_launcher", "ic_launcher_round"):
                png = base + name + ".png"
                webp = base + name + ".webp"
                if png not in entries_set and webp not in entries_set:
                    return False
        return True

    normalized = normalize_entries(entries, False)
    normalized_strip = normalize_entries(entries, True)

    has_xml = required_xml.issubset(normalized) or required_xml.issubset(normalized_strip)
    has_pngs = has_icons(normalized) or has_icons(normalized_strip)
    return has_xml and has_pngs


def safe_extract_icons(zip_path, out_res_dir):
    allowed_prefixes = (
        "drawable/",
        "drawable-v24/",
        "mipmap-",
        "mipmap-anydpi-v26/",
    )

    with zipfile.ZipFile(zip_path) as zf:
        for entry in zf.infolist():
            if entry.is_dir():
                continue

            name = entry.filename
            if name.startswith("res/"):
                name = name[len("res/") :]
            elif "/" in name and not name.startswith(("drawable", "mipmap")):
                # strip top-level folder if present
                parts = name.split("/", 1)
                if len(parts) == 2:
                    name = parts[1]

            if not name.startswith(allowed_prefixes):
                continue

            dest_path = out_res_dir / name
            dest_path.parent.mkdir(parents=True, exist_ok=True)
            with zf.open(entry) as src, open(dest_path, "wb") as dst:
                dst.write(src.read())


def write_strings_xml(out_res_dir, values):
    values_dir = out_res_dir / "values"
    values_dir.mkdir(parents=True, exist_ok=True)
    content = (
        "<?xml version='1.0' encoding='utf-8'?>\n"
        "<resources>\n"
        f"    <string name=\"app_name\">{values['appName']}</string>\n"
        f"    <string name=\"title_activity_main\">{values['titleActivityMain']}</string>\n"
        f"    <string name=\"package_name\">{values['packageName']}</string>\n"
        f"    <string name=\"custom_url_scheme\">{values['customUrlScheme']}</string>\n"
        "</resources>\n"
    )
    (values_dir / "strings.xml").write_text(content, encoding="utf-8")


def update_main_activity(app_dir, package_name):
    java_root = app_dir / "src" / "main" / "java"
    if not java_root.exists():
        fail(f"Diretorio Java Android nao encontrado: {java_root}")

    candidates = list(java_root.rglob("MainActivity.java"))
    if not candidates:
        fail("MainActivity.java nao encontrado.")

    # Prefer the file that declares BridgeActivity
    main_file = None
    for candidate in candidates:
        text = candidate.read_text(encoding="utf-8")
        if "BridgeActivity" in text:
            main_file = candidate
            break
    if main_file is None:
        main_file = candidates[0]

    text = main_file.read_text(encoding="utf-8")
    lines = text.splitlines()
    if not lines:
        fail("MainActivity.java vazio.")

    lines[0] = f"package {package_name};"
    updated = "\n".join(lines) + "\n"

    target_dir = java_root / Path(package_name.replace(".", "/"))
    target_dir.mkdir(parents=True, exist_ok=True)
    target_file = target_dir / "MainActivity.java"
    target_file.write_text(updated, encoding="utf-8")

    if target_file.resolve() != main_file.resolve():
        main_file.unlink()


def update_capacitor_config(base_dir, package_name):
    config_path = base_dir / "capacitor.config.ts"
    if not config_path.exists():
        fail(f"capacitor.config.ts nao encontrado: {config_path}")

    text = config_path.read_text(encoding="utf-8")
    new_text = re.sub(
        r"appId\s*:\s*['\"][^'\"]+['\"]",
        f"appId: '{package_name}'",
        text,
        count=1,
    )
    if new_text == text:
        if f"appId: '{package_name}'" in text or f"\"appId\": \"{package_name}\"" in text:
            return
        fail("Nao foi possivel atualizar appId em capacitor.config.ts")
    config_path.write_text(new_text, encoding="utf-8")

    assets_config = base_dir / "android" / "app" / "src" / "main" / "assets" / "capacitor.config.json"
    if assets_config.exists():
        data = json.loads(assets_config.read_text())
        data["appId"] = package_name
        assets_config.write_text(json.dumps(data, indent=2, ensure_ascii=True) + "\n")

def main():
    base_url = os.getenv("VILLA_API_URL", "http://localhost:8085")
    theme_id = os.getenv("THEME_ID")
    tenant_id = os.getenv("TENANT_ID", "espresso")
    token = os.getenv("AUTH_TOKEN")
    base_dir = Path(__file__).resolve().parents[1]
    output_dir = Path(os.getenv("OUTPUT_DIR", str(base_dir / "android" / "theme_generated")))

    if theme_id:
        theme_url = f"{base_url}/api/themes/{theme_id}"
    else:
        theme_url = f"{base_url}/api/themes/public/theme/active?tenantId={tenant_id}"

    theme = fetch_json(theme_url, token=token)

    content = theme.get("content") or {}
    assets = theme.get("assets") or {}
    android_content = content.get("android") or {}
    android_assets = assets.get("android") or {}

    required_content = [
        "appName",
        "titleActivityMain",
        "packageName",
        "customUrlScheme",
        "applicationId",
        "namespace",
        "versionName",
        "versionCode",
    ]

    missing = [key for key in required_content if not android_content.get(key)]
    if missing:
        fail(f"Campos android ausentes no tema: {', '.join(missing)}")

    google_services_url = android_assets.get("googleServicesJsonUrl")
    icons_zip_url = android_assets.get("iconsZipUrl")

    if not google_services_url:
        fail("assets.android.googleServicesJsonUrl ausente.")
    if not icons_zip_url:
        fail("assets.android.iconsZipUrl ausente.")

    output_dir.mkdir(parents=True, exist_ok=True)
    res_dir = output_dir / "res"
    if res_dir.exists():
        for path in res_dir.rglob("*"):
            if path.is_file():
                path.unlink()
    res_dir.mkdir(parents=True, exist_ok=True)

    google_services_path = output_dir / "google-services.json"
    icons_zip_path = output_dir / "icons.zip"

    download_file(google_services_url, google_services_path, token=token)
    download_file(icons_zip_url, icons_zip_path, token=token)

    if not validate_icons_zip(icons_zip_path):
        fail("icons.zip invalido. Estrutura esperada nao encontrada.")

    safe_extract_icons(icons_zip_path, res_dir)
    write_strings_xml(res_dir, android_content)
    update_capacitor_config(base_dir, android_content["applicationId"])

    app_dir = base_dir / "android" / "app"
    if not app_dir.exists():
        fail(f"Diretorio Android nao encontrado: {app_dir}")

    target_google = app_dir / "google-services.json"
    target_res = app_dir / "src" / "main" / "res"

    if not target_res.exists():
        fail(f"Diretorio de resources Android nao encontrado: {target_res}")

    target_google.write_bytes(google_services_path.read_bytes())

    if target_res.exists():
        for path in target_res.rglob("*"):
            if path.is_file():
                rel = path.relative_to(target_res)
                if str(rel).startswith(("mipmap-", "drawable", "drawable-v24", "mipmap-anydpi-v26")):
                    if rel.name.startswith("ic_launcher") or rel.name in {
                        "ic_launcher_background.xml",
                        "ic_launcher_foreground.xml",
                    }:
                        path.unlink()

    for path in res_dir.rglob("*"):
        if path.is_file():
            rel = path.relative_to(res_dir)
            dest = target_res / rel
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(path.read_bytes())

    update_main_activity(app_dir, android_content["applicationId"])

    config = {
        "applicationId": android_content["applicationId"],
        "namespace": android_content["namespace"],
        "versionName": android_content["versionName"],
        "versionCode": android_content["versionCode"],
        "appName": android_content["appName"],
    }
    (output_dir / "android_config.json").write_text(
        json.dumps(config, indent=2, ensure_ascii=True),
        encoding="utf-8",
    )

    print(f"OK: arquivos gerados em {output_dir} e sincronizados em {app_dir}")


if __name__ == "__main__":
    main()
