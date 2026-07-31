from __future__ import annotations

import copy
import importlib.util
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
MODULE_PATH = ROOT / "tools/releases/catalog.py"
SPEC = importlib.util.spec_from_file_location("release_catalog", MODULE_PATH)
catalog_module = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
sys.modules[SPEC.name] = catalog_module
SPEC.loader.exec_module(catalog_module)


class CatalogContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.real = catalog_module.load_yaml()
        cls.schema = catalog_module.load_schema()
        cls.order = cls.real["canonical_order"]

    def mutated(self):
        return copy.deepcopy(self.real)

    def errors(self, value):
        return catalog_module.validate_catalog(value, self.schema)

    def assertInvalid(self, value):
        self.assertTrue(self.errors(value))

    def ready_component(self, value, component_id):
        component = value["components"][component_id]
        component["readiness"] = "ready"
        component["readiness_gates"] = []
        component["build"] = {
            **component["build"],
            "status": "confirmed",
            "command": component["build"]["command"] or "build-command",
        }
        component["test"] = {"status": "confirmed", "command": "test-command"}
        component["health_check"] = {"status": "confirmed", "path": "/health"}
        for persistence in component["persistence"]:
            persistence["status"] = "confirmed"
        return component

    def test_01_real_catalog_is_valid(self):
        self.assertEqual([], self.errors(self.real))

    def test_02_release_readiness_has_no_pending_gates_after_s10(self):
        gates = catalog_module.readiness_gates(self.real)
        self.assertEqual([], gates)

    def test_03_missing_component_fails(self):
        value = self.mutated()
        del value["components"]["gateway"]
        self.assertInvalid(value)

    def test_04_extra_component_fails(self):
        value = self.mutated()
        value["components"]["other"] = copy.deepcopy(value["components"]["gateway"])
        value["components"]["other"]["id"] = "other"
        self.assertInvalid(value)

    def test_05_unknown_dependency_fails(self):
        value = self.mutated()
        value["components"]["frontend"]["dependencies"] = ["missing"]
        self.assertInvalid(value)

    def test_06_cycle_fails(self):
        value = self.mutated()
        value["components"]["whatsapp_service"]["dependencies"] = ["backend"]
        self.assertInvalid(value)

    def test_07_duplicate_image_fails(self):
        value = self.mutated()
        value["components"]["frontend"]["image_repository"] = value["components"]["backend"][
            "image_repository"
        ]
        self.assertInvalid(value)

    def test_08_wrong_image_namespace_fails(self):
        value = self.mutated()
        value["components"]["frontend"]["image_repository"] = "ghcr.io/other/image"
        self.assertInvalid(value)

    def test_09_latest_tag_fails(self):
        value = self.mutated()
        value["components"]["frontend"]["image_repository"] += ":latest"
        self.assertInvalid(value)

    def test_10_digest_fails(self):
        value = self.mutated()
        value["components"]["frontend"]["image_repository"] += "@sha256:abc"
        self.assertInvalid(value)

    def test_11_blocked_without_gate_fails(self):
        value = self.mutated()
        value["components"]["website_back"]["readiness"] = "blocked"
        value["components"]["website_back"]["readiness_gates"] = []
        self.assertInvalid(value)

    def test_12_ready_with_gate_fails(self):
        value = self.mutated()
        value["components"]["website_back"]["readiness"] = "ready"
        value["components"]["website_back"]["readiness_gates"] = [
            {"code": "MUTANT_GATE", "description": "mutant", "status": "pending"}
        ]
        self.assertInvalid(value)

    def assertClosure(self, component, expected):
        result = catalog_module.resolve(
            self.real, [f"{component}/example.txt" if component != "gateway" else "ops/gateway/example"]
        )
        self.assertEqual([component], result["buildComponents"])
        self.assertEqual(expected, result["validationComponents"])

    def test_13_backend_closure(self):
        self.assertClosure("backend", catalog_module.EXPECTED_CLOSURES["backend"])

    def test_14_website_back_closure(self):
        self.assertClosure("website_back", catalog_module.EXPECTED_CLOSURES["website_back"])

    def test_15_frontend_closure(self):
        self.assertClosure("frontend", catalog_module.EXPECTED_CLOSURES["frontend"])

    def test_16_website_front_closure(self):
        self.assertClosure("website_front", catalog_module.EXPECTED_CLOSURES["website_front"])

    def test_17_whatsapp_closure(self):
        self.assertClosure(
            "whatsapp_service", catalog_module.EXPECTED_CLOSURES["whatsapp_service"]
        )

    def test_18_gateway_closure(self):
        self.assertClosure("gateway", catalog_module.EXPECTED_CLOSURES["gateway"])

    def test_19_multiple_paths_are_deterministic(self):
        first = catalog_module.resolve(
            self.real, ["frontend/src/a.js", "backend/src/A.java"]
        )
        second = catalog_module.resolve(
            self.real, ["backend/src/A.java", "frontend/src/a.js"]
        )
        self.assertEqual(first, second)
        self.assertEqual(["backend", "frontend"], first["buildComponents"])

    def test_20_global_path_revalidates_all(self):
        result = catalog_module.resolve(self.real, ["ops/releases/components.yml"])
        self.assertEqual([], result["buildComponents"])
        self.assertEqual(self.order, result["validationComponents"])

    def test_21_docs_only_selects_nothing(self):
        result = catalog_module.resolve(self.real, ["docs/README.md"])
        self.assertEqual("documentation", result["classification"])
        self.assertEqual([], result["buildComponents"])
        self.assertEqual([], result["validationComponents"])

    def test_22_unknown_path_fails_closed(self):
        result = catalog_module.resolve(self.real, ["unknown/file.txt"])
        self.assertEqual("unknown", result["classification"])
        self.assertEqual(self.order, result["buildComponents"])
        self.assertEqual(self.order, result["validationComponents"])
        self.assertTrue(result["warnings"])

    def test_23_first_release_selects_all(self):
        result = catalog_module.resolve(self.real, first_release=True)
        self.assertEqual(self.order, result["buildComponents"])
        self.assertEqual(self.order, result["validationComponents"])
        self.assertEqual([], result["inheritedComponents"])

    def test_24_absolute_path_is_rejected(self):
        with self.assertRaises(catalog_module.CatalogError):
            catalog_module.resolve(self.real, ["/etc/passwd"])

    def test_25_parent_traversal_is_rejected(self):
        with self.assertRaises(catalog_module.CatalogError):
            catalog_module.resolve(self.real, ["backend/../secret"])

    def test_26_release_control_never_enters_bom(self):
        self.assertNotIn("release_control", self.order)
        result = catalog_module.resolve(self.real, ["release_control/app.py"])
        self.assertEqual("unknown", result["classification"])
        self.assertNotIn("release_control", result["buildComponents"])

    def test_27_source_path_collision_fails(self):
        value = self.mutated()
        value["components"]["frontend"]["source_paths"] = ["backend/**"]
        self.assertInvalid(value)

    def test_28_pending_command_must_be_null(self):
        value = self.mutated()
        value["components"]["gateway"]["build"]["status"] = "pending"
        value["components"]["gateway"]["build"]["command"] = "docker build"
        self.assertInvalid(value)

    def test_29_confirmed_command_must_exist(self):
        value = self.mutated()
        value["components"]["backend"]["test"]["command"] = None
        self.assertInvalid(value)

    def test_30_inherited_is_build_complement(self):
        result = catalog_module.resolve(self.real, ["frontend/src/a.js"])
        self.assertEqual(
            [item for item in self.order if item != "frontend"],
            result["inheritedComponents"],
        )

    def test_31_dot_github_path_is_global(self):
        result = catalog_module.resolve(self.real, [".github/workflows/ci.yml"])
        self.assertEqual("global", result["classification"])
        self.assertEqual([], result["buildComponents"])
        self.assertEqual(self.order, result["validationComponents"])

    def test_32_explicit_relative_dot_github_is_equivalent(self):
        direct = catalog_module.resolve(self.real, [".github/workflows/ci.yml"])
        explicit = catalog_module.resolve(self.real, ["./.github/workflows/ci.yml"])
        self.assertEqual(direct, explicit)

    def test_33_hidden_unknown_path_preserves_dot(self):
        result = catalog_module.resolve(self.real, [".hidden/unknown.txt"])
        self.assertEqual([".hidden/unknown.txt"], result["changedPaths"])
        self.assertEqual(
            ["FAIL_CLOSED_UNKNOWN_PATH:.hidden/unknown.txt"], result["warnings"]
        )

    def test_34_explicit_relative_component_path(self):
        result = catalog_module.resolve(self.real, ["./backend/src/A.java"])
        self.assertEqual(["backend/src/A.java"], result["changedPaths"])
        self.assertEqual(["backend"], result["buildComponents"])

    def test_35_ready_with_pending_test_fails(self):
        value = self.mutated()
        component = self.ready_component(value, "website_front")
        component["test"] = {"status": "pending", "command": None}
        self.assertInvalid(value)

    def test_36_ready_with_pending_health_fails(self):
        value = self.mutated()
        component = self.ready_component(value, "gateway")
        component["health_check"] = {"status": "pending", "path": None}
        self.assertInvalid(value)

    def test_37_ready_with_inferred_health_fails(self):
        value = self.mutated()
        component = self.ready_component(value, "frontend")
        component["health_check"] = {"status": "inferred", "path": "/"}
        self.assertInvalid(value)

    def test_38_ready_with_pending_persistence_fails(self):
        value = self.mutated()
        component = self.ready_component(value, "backend")
        component["persistence"][0]["status"] = "pending"
        self.assertInvalid(value)

    def test_39_global_paths_are_frozen(self):
        value = self.mutated()
        value["global_paths"].remove(".github/workflows/**")
        self.assertInvalid(value)

    def test_40_documentation_paths_are_frozen(self):
        value = self.mutated()
        value["documentation_paths"] = ["docs/**"]
        self.assertInvalid(value)

    def test_41_source_paths_are_frozen(self):
        value = self.mutated()
        value["components"]["backend"]["source_paths"] = ["backend/src/**"]
        self.assertInvalid(value)

    def test_42_ready_with_pending_build_fails(self):
        value = self.mutated()
        component = self.ready_component(value, "gateway")
        component["build"] = {
            **component["build"],
            "status": "pending",
            "command": None,
        }
        self.assertInvalid(value)

    def test_43_s10_confirms_website_upload_persistence(self):
        backend = self.real["components"]["backend"]
        website = self.real["components"]["website_back"]
        self.assertEqual("ready", backend["readiness"])
        self.assertEqual([], backend["readiness_gates"])
        self.assertEqual(
            {"status": "confirmed", "path": "/actuator/health"},
            website["health_check"],
        )
        self.assertEqual("ready", website["readiness"])
        self.assertEqual([], website["readiness_gates"])
        self.assertEqual("confirmed", website["persistence"][0]["status"])

    def test_44_s10_closes_exactly_the_five_previous_gates(self):
        self.assertEqual([], catalog_module.readiness_gates(self.real))
        gateway = self.real["components"]["gateway"]
        self.assertEqual("ready", gateway["readiness"])
        self.assertEqual({"status": "confirmed", "path": "/healthz"}, gateway["health_check"])
        self.assertEqual("confirmed", gateway["build"]["status"])
        self.assertEqual("confirmed", gateway["test"]["status"])

    def test_45_node_image_evidence_closes_only_s09_gates(self):
        for name, health in (
            ("frontend", "/healthz"),
            ("website_front", "/healthz"),
            ("whatsapp_service", "/health/live"),
        ):
            component = self.real["components"][name]
            self.assertEqual("ready", component["readiness"])
            self.assertEqual([], component["readiness_gates"])
            self.assertEqual(
                {"status": "confirmed", "path": health},
                component["health_check"],
            )
            self.assertEqual("confirmed", component["build"]["status"])
            self.assertEqual("confirmed", component["test"]["status"])
            self.assertTrue(component["test"]["command"])
        self.assertEqual([], catalog_module.readiness_gates(self.real))


if __name__ == "__main__":
    unittest.main()
