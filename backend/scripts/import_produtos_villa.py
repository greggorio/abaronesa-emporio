#!/usr/bin/env python3
"""
Importa produtos do relatorio PDF da Villa Custom, garantindo categorias e
subcategorias e depois criando os produtos via API.
"""

from __future__ import annotations

import argparse
import csv
import json
import sys
import unicodedata
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Tuple
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from export_produtos_csv import load_lines, parse_records, write_csv  # noqa: E402


def normalize(text: str) -> str:
    if text is None:
        return ""
    value = unicodedata.normalize("NFD", text)
    value = "".join(ch for ch in value if ord(ch) < 128)
    return value.lower().strip()


def to_float(value: str) -> Optional[float]:
    if not value:
        return None
    try:
        cleaned = value.strip().replace(".", "").replace(",", ".")
        return float(Decimal(cleaned))
    except (InvalidOperation, ValueError):
        return None


def request_json(method: str, url: str, token: Optional[str] = None, payload: Optional[dict] = None):
    data = None
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
    req = Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urlopen(req) as resp:
            body = resp.read().decode("utf-8")
            if not body:
                return None
            return json.loads(body)
    except HTTPError as exc:
        body = exc.read().decode("utf-8")
        raise RuntimeError(f"{method} {url} failed: {exc.code} {body}") from exc
    except URLError as exc:
        raise RuntimeError(f"{method} {url} failed: {exc}") from exc


def login(base_url: str, email: str, password: str) -> str:
    payload = {"email": email, "password": password}
    data = request_json("POST", f"{base_url}/api/auth/login", payload=payload)
    token = data.get("accessToken") if isinstance(data, dict) else None
    if not token:
        raise RuntimeError("Falha ao obter token de acesso.")
    return token


def fetch_categorias(base_url: str, token: str) -> Dict[str, int]:
    data = request_json("GET", f"{base_url}/api/categorias/all", token=token)
    categorias = {}
    for item in data or []:
        nome = item.get("nome")
        if nome:
            categorias[normalize(nome)] = item.get("id")
    return categorias


def fetch_subcategorias(base_url: str, token: str, categoria_id: int) -> Dict[str, int]:
    data = request_json(
        "GET", f"{base_url}/api/subcategorias/categoria/{categoria_id}", token=token
    )
    subcategorias = {}
    for item in data or []:
        nome = item.get("nome")
        if nome:
            subcategorias[normalize(nome)] = item.get("id")
    return subcategorias


def ensure_categorias(
    base_url: str, token: str, grupos: Iterable[str]
) -> Dict[str, int]:
    categorias = fetch_categorias(base_url, token)
    for grupo in sorted({g for g in grupos if g}):
        if normalize(grupo) in categorias:
            continue
        request_json("POST", f"{base_url}/api/categorias", token=token, payload={"nome": grupo})
    return fetch_categorias(base_url, token)


def ensure_subcategorias(
    base_url: str,
    token: str,
    categoria_nome_para_id: Dict[str, int],
    pares: Iterable[Tuple[str, str]],
) -> Dict[Tuple[int, str], int]:
    subcategoria_map: Dict[Tuple[int, str], int] = {}
    for categoria_nome, sub_nome in pares:
        if not categoria_nome or not sub_nome:
            continue
        categoria_id = categoria_nome_para_id.get(normalize(categoria_nome))
        if not categoria_id:
            continue
        if (categoria_id, normalize(sub_nome)) in subcategoria_map:
            continue

        existentes = fetch_subcategorias(base_url, token, categoria_id)
        if normalize(sub_nome) not in existentes:
            payload = {"nome": sub_nome, "categoriaId": categoria_id}
            request_json("POST", f"{base_url}/api/subcategorias", token=token, payload=payload)
            existentes = fetch_subcategorias(base_url, token, categoria_id)

        for nome_norm, sub_id in existentes.items():
            subcategoria_map[(categoria_id, nome_norm)] = sub_id
    return subcategoria_map


def map_unidade_medida(unid: str) -> str:
    if not unid:
        return "UN"
    value = unid.strip().upper()
    if value == "LT":
        return "L"
    if value in {"UN", "KG", "G", "L", "ML", "CX"}:
        return value
    return "UN"


def map_unidade_base(unid: str) -> str:
    if not unid:
        return "UNIDADE"
    value = unid.strip().upper()
    if value in {"ML", "L", "LT"}:
        return "MILILITRO"
    if value in {"G", "KG"}:
        return "GRAMA"
    return "UNIDADE"


def map_setor(setor: str) -> Optional[str]:
    if not setor:
        return None
    value = setor.strip().upper()
    if value in {"BAR", "CAIXA", "COZINHA", "IMPORTADOS", "TABACARIA", "VINHOS"}:
        return value
    return None


def write_extra_csvs(records: List[Dict[str, str]], out_dir: Path, ids_map: Dict[str, Tuple[int, int]]):
    out_dir.mkdir(parents=True, exist_ok=True)
    ids_path = out_dir / "relatorio_produtos_ids.csv"
    with ids_path.open("w", newline="", encoding="utf-8") as f:
        fieldnames = [
            "codigo",
            "descricao",
            "unid",
            "estoque",
            "grupo",
            "subgrupo",
            "setor",
            "vr_venda",
            "vr_custo",
            "categoria_id",
            "subcategoria_id",
        ]
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for rec in records:
            cat_id, sub_id = ids_map.get(rec["codigo"], (None, None))
            row = rec.copy()
            row["categoria_id"] = cat_id
            row["subcategoria_id"] = sub_id
            writer.writerow(row)

    map_path = out_dir / "produtos_categoria_map.csv"
    with map_path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["codigo_interno", "categoria_id", "subcategoria_id"])
        for codigo, (cat_id, sub_id) in ids_map.items():
            writer.writerow([codigo, cat_id, sub_id])

    nome_preco_path = out_dir / "produtos_nome_preco.csv"
    with nome_preco_path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["codigo_interno", "nome", "vr_venda"])
        for rec in records:
            writer.writerow([rec["codigo"], rec["descricao"], rec["vr_venda"]])


def build_product_payload(
    rec: Dict[str, str],
    categoria_id: Optional[int],
    subcategoria_id: Optional[int],
) -> Dict[str, object]:
    unidade = map_unidade_medida(rec.get("unid"))
    payload: Dict[str, object] = {
        "nome": rec.get("descricao"),
        "descricao": rec.get("descricao"),
        "codigoInterno": rec.get("codigo"),
        "tipoPrecificacao": "SIMPLES",
        "unidadeMedida": unidade,
        "unidadeBase": map_unidade_base(unidade),
        "precoVenda": to_float(rec.get("vr_venda")) or 0,
    }

    preco_custo = to_float(rec.get("vr_custo"))
    if preco_custo is not None:
        payload["precoCusto"] = preco_custo

    if categoria_id:
        payload["categoriaId"] = categoria_id
    if subcategoria_id:
        payload["subcategoriaId"] = subcategoria_id

    setor = map_setor(rec.get("setor"))
    if setor:
        payload["setor"] = setor

    return payload


def import_produtos(
    base_url: str,
    token: str,
    records: List[Dict[str, str]],
    categoria_ids: Dict[str, int],
    subcategoria_ids: Dict[Tuple[int, str], int],
    dry_run: bool,
) -> Tuple[int, int]:
    ok = 0
    fail = 0
    for rec in records:
        grupo = rec.get("grupo")
        subgrupo = rec.get("subgrupo")
        categoria_id = categoria_ids.get(normalize(grupo)) if grupo else None
        subcategoria_id = None
        if categoria_id and subgrupo:
            subcategoria_id = subcategoria_ids.get((categoria_id, normalize(subgrupo)))

        payload = build_product_payload(rec, categoria_id, subcategoria_id)
        if dry_run:
            ok += 1
            continue
        try:
            request_json("POST", f"{base_url}/api/produtos", token=token, payload=payload)
            ok += 1
        except RuntimeError:
            fail += 1
    return ok, fail


def main() -> None:
    parser = argparse.ArgumentParser(description="Importa produtos do PDF da Villa Custom.")
    parser.add_argument("--pdf", default="relatorio_produtos.pdf", help="Caminho do PDF de entrada.")
    parser.add_argument("--txt", default=None, help="TXT extraido do PDF (opcional).")
    parser.add_argument("--out-dir", default="outputs", help="Diretorio para CSVs gerados.")
    parser.add_argument("--base-url", default="http://localhost:8080", help="URL base da API.")
    parser.add_argument("--email", default="root@localhost", help="Email para login.")
    parser.add_argument("--password", default="123456", help="Senha para login.")
    parser.add_argument("--dry-run", action="store_true", help="Nao envia dados para a API.")
    args = parser.parse_args()

    pdf_path = Path(args.pdf)
    txt_path = Path(args.txt) if args.txt else None
    out_dir = Path(args.out_dir)

    if txt_path and not txt_path.exists():
        raise SystemExit(f"TXT não encontrado: {txt_path}")
    if not txt_path and not pdf_path.exists():
        raise SystemExit(f"PDF não encontrado: {pdf_path}")

    lines = load_lines(pdf_path, txt_path)
    records = parse_records(lines)
    if not records:
        raise SystemExit("Nenhum produto encontrado no relatorio.")

    write_csv(records, out_dir)

    token = login(args.base_url, args.email, args.password)

    grupos = [r.get("grupo") for r in records]
    categoria_ids = ensure_categorias(args.base_url, token, grupos)

    pares = {(r.get("grupo"), r.get("subgrupo")) for r in records if r.get("grupo") and r.get("subgrupo")}
    subcategoria_ids = ensure_subcategorias(args.base_url, token, categoria_ids, pares)

    ids_map: Dict[str, Tuple[int, int]] = {}
    for rec in records:
        grupo = rec.get("grupo")
        subgrupo = rec.get("subgrupo")
        cat_id = categoria_ids.get(normalize(grupo)) if grupo else None
        sub_id = None
        if cat_id and subgrupo:
            sub_id = subcategoria_ids.get((cat_id, normalize(subgrupo)))
        ids_map[rec["codigo"]] = (cat_id, sub_id)

    write_extra_csvs(records, out_dir, ids_map)

    ok, fail = import_produtos(
        args.base_url, token, records, categoria_ids, subcategoria_ids, args.dry_run
    )

    print(f"Total de produtos: {len(records)}")
    print(f"Importados com sucesso: {ok}")
    print(f"Falhas: {fail}")
    if args.dry_run:
        print("Dry-run ativo: nenhuma chamada POST para /api/produtos foi executada.")


if __name__ == "__main__":
    main()
