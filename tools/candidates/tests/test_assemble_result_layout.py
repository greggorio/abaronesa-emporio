from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location(
    "assemble_candidate", ROOT / "tools/candidates/assemble_candidate.py"
)
assert SPEC and SPEC.loader
assemble = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = assemble
SPEC.loader.exec_module(assemble)


class ResultLayoutTest(unittest.TestCase):
    """actions/download-artifact only creates a directory per artifact when the
    pattern matches more than one. A commit touching a single component produced
    the flat layout, the assembler globbed only the nested one, and a build of one
    component was read as a build of none — surfacing as an opaque
    `assemble:invalid:result partition` two steps later. Every earlier release
    changed either zero components or several, so the defect stayed latent."""

    def layout(self, *relative: str) -> Path:
        base = Path(tempfile.mkdtemp())
        self.addCleanup(lambda: __import__("shutil").rmtree(base, ignore_errors=True))
        for item in relative:
            path = base / item
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("{}", encoding="utf-8")
        return base

    def test_single_component_lands_flat_and_is_found(self) -> None:
        base = self.layout("component-result.json")
        self.assertEqual(
            [base / "component-result.json"], assemble.result_paths(base)
        )

    def test_several_components_land_nested_and_are_found(self) -> None:
        base = self.layout(
            "candidate-component-backend/component-result.json",
            "candidate-component-gateway/component-result.json",
        )
        self.assertEqual(
            [
                base / "candidate-component-backend/component-result.json",
                base / "candidate-component-gateway/component-result.json",
            ],
            assemble.result_paths(base),
        )

    def test_no_builds_yields_nothing(self) -> None:
        self.assertEqual([], assemble.result_paths(self.layout()))

    def test_unrelated_shapes_are_ignored(self) -> None:
        base = self.layout(
            "candidate-component-backend/component-result.json",
            "component-result.json.sha256",
            "candidate-component-backend/metadata.json",
            "deeper/nested/candidate-component-x/component-result.json",
        )
        self.assertEqual(
            [base / "candidate-component-backend/component-result.json"],
            assemble.result_paths(base),
        )


if __name__ == "__main__":
    unittest.main()
