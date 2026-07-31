#!/usr/bin/env python3
"""
Extrai o relatório de produto acabado (PDF) para CSV, preservando
campos como grupo/subgrupo/setor para posterior carga no ERP.
"""

import argparse
import csv
import re
import subprocess
import tempfile
from pathlib import Path
from typing import Dict, List, Sequence

CODE_PATTERN = re.compile(r"^\s*(\d+)\b")
NUM_PATTERN = re.compile(r"-?\d+[\d\.]*,\d{2,3}$")
UNITS = {"UN", "KG", "L", "ML", "LT", "CX"}
DESC_COMMA_FIX = re.compile(r",\s+(\d{1,3})\b")
SKIP_MARKERS = [
    "Bitbyte Informática",
    "Bitbyte Informatica",
    "Relatório de Produto",
    "Relatorio de Produto",
    "Agrupado por",
    "Data:",
    "Hora:",
    "Pág.:",
    "Pag.:",
    "VILLA CUSTOM",
]
FIELDS = ["codigo", "descricao", "unid", "estoque", "grupo", "subgrupo", "setor", "vr_venda", "vr_custo"]


def split_tokens(line: str) -> List[str]:
    """Quebra uma linha por 2+ espaços, descartando vazios."""
    return [p for p in re.split(r"\s{2,}", line.strip()) if p]


def normalize_number(value: str) -> str:
    """Remove separador de milhar e converte vírgula para ponto."""
    if not value:
        return ""
    return value.replace(".", "").replace(",", ".")


def extract_unit_from_desc(record: Dict[str, str]) -> None:
    """Se a unidade veio colada na descrição (ex.: '... 129, UN'), move para o campo correto."""
    if record["unid"]:
        return
    parts = record["descricao"].split()
    if not parts:
        return
    last = parts[-1]
    if last in UNITS:
        record["unid"] = last
        record["descricao"] = " ".join(parts[:-1]).strip()


def clean_description(text: str) -> str:
    """Remove espaços duplicados e corrige ', 00' -> ',00' etc."""
    text = " ".join(text.split())
    return DESC_COMMA_FIX.sub(r",\1", text)


def parse_line(parts: Sequence[str]) -> Dict[str, str]:
    """Converte uma linha com código em um registro bruto."""
    record = {k: "" for k in FIELDS}
    record["codigo"] = parts[0]
    record["descricao"] = parts[1]

    idx = 2
    if len(parts) >= 3:
        third = parts[2]
        if " " in third:
            record["unid"], record["estoque"] = third.split(None, 1)
            idx = 3
        elif NUM_PATTERN.match(third):
            record["estoque"] = third
            idx = 3
        else:
            record["unid"] = third
            if len(parts) > 3 and NUM_PATTERN.match(parts[3]):
                record["estoque"] = parts[3]
                idx = 4
            else:
                idx = 3

    rest = list(parts[idx:])
    if rest and NUM_PATTERN.match(rest[-1]):
        record["vr_custo"] = rest.pop()
    if rest and NUM_PATTERN.match(rest[-1]):
        record["vr_venda"] = rest.pop()

    for key, value in zip(["grupo", "subgrupo", "setor"], rest):
        record[key] = value

    extract_unit_from_desc(record)
    record["descricao"] = clean_description(record["descricao"])
    return record


def fill_continuation(record: Dict[str, str], tokens: Sequence[str]) -> None:
    """Preenche dados faltantes a partir de linhas de continuação."""
    for token in tokens:
        if any(marker in token for marker in ["Data:", "Hora:", "Pág", "Pag:"]):
            continue
        if NUM_PATTERN.match(token) and not record["vr_venda"]:
            record["vr_venda"] = token
            continue
        if NUM_PATTERN.match(token) and not record["vr_custo"]:
            record["vr_custo"] = token
            continue
        filled = False
        for key in ["grupo", "subgrupo", "setor"]:
            if not record[key]:
                record[key] = token
                filled = True
                break
        if not filled:
            record["descricao"] = (record["descricao"] + " " + token).strip()
    extract_unit_from_desc(record)
    record["descricao"] = clean_description(record["descricao"])


def parse_records(lines: Sequence[str]) -> List[Dict[str, str]]:
    """Percorre as linhas do relatório e monta a lista de produtos."""
    records: List[Dict[str, str]] = []
    current: Dict[str, str] | None = None

    for raw_line in lines:
        line = raw_line.replace("\x0c", "")
        if not line.strip() or any(marker in line for marker in SKIP_MARKERS):
            continue
        if "Código Descrição" in line or "Codigo Descricao" in line:
            continue

        parts = split_tokens(line)
        has_code = CODE_PATTERN.match(line) and len(parts) >= 2 and parts[0].isdigit()

        if has_code:
            if current:
                records.append(current)
            current = parse_line(parts)
            continue

        if current:
            fill_continuation(current, parts)

    if current:
        records.append(current)
    return records


def load_lines(pdf_path: Path, txt_path: Path | None) -> List[str]:
    """Carrega o texto do PDF (via pdftotext -layout) ou de um TXT já extraído."""
    if txt_path:
        return txt_path.read_text(encoding="utf-8", errors="ignore").splitlines()

    with tempfile.TemporaryDirectory() as tmpdir:
        tmp_txt = Path(tmpdir) / "relatorio.txt"
        subprocess.run(["pdftotext", "-layout", str(pdf_path), str(tmp_txt)], check=True)
        return tmp_txt.read_text(encoding="utf-8", errors="ignore").splitlines()


def write_csv(records: Sequence[Dict[str, str]], out_dir: Path) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    products_path = out_dir / "relatorio_produtos.csv"
    with products_path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=FIELDS)
        writer.writeheader()
        for rec in records:
            row = rec.copy()
            row["estoque"] = normalize_number(row["estoque"])
            row["vr_venda"] = normalize_number(row["vr_venda"])
            row["vr_custo"] = normalize_number(row["vr_custo"])
            writer.writerow(row)

    categorias = sorted({r["grupo"] for r in records if r["grupo"]})
    cat_path = out_dir / "categorias.csv"
    with cat_path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["categoria"])
        writer.writerows([[c] for c in categorias])

    subcategorias = sorted(
        {(r["grupo"], r["subgrupo"]) for r in records if r["grupo"] and r["subgrupo"]}
    )
    sub_path = out_dir / "subcategorias.csv"
    with sub_path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["categoria", "subcategoria"])
        writer.writerows(subcategorias)

    print(f"Produtos salvos em: {products_path}")
    print(f"Categorias únicas: {cat_path}")
    print(f"Subcategorias únicas: {sub_path}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Converte relatório de produto acabado para CSV.")
    parser.add_argument("--pdf", default="relatorio_produtos.pdf", help="Caminho do PDF de entrada.")
    parser.add_argument(
        "--txt", default=None, help="Opcional: usar TXT já extraído (formato -layout) em vez do PDF."
    )
    parser.add_argument("--out-dir", default="outputs", help="Diretório onde os CSVs serão gravados.")
    args = parser.parse_args()

    pdf_path = Path(args.pdf)
    txt_path = Path(args.txt) if args.txt else None
    if txt_path and not txt_path.exists():
        raise SystemExit(f"TXT não encontrado: {txt_path}")
    if not txt_path and not pdf_path.exists():
        raise SystemExit(f"PDF não encontrado: {pdf_path}")

    lines = load_lines(pdf_path, txt_path)
    records = parse_records(lines)
    if not records:
        raise SystemExit("Nenhum produto encontrado no relatório.")
    write_csv(records, Path(args.out_dir))
    print(f"Total de produtos extraídos: {len(records)}")


if __name__ == "__main__":
    main()
