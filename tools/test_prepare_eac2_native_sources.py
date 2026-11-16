import hashlib
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
from prepare_eac2_native_sources import git_blob_sha, verify_and_install, ROOT, MANIFEST, OVERRIDES
import json


class NativeOverlayTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = Path(self.tmp.name)
        (self.path / "CMakeLists.txt").write_text("project(AusweisApp)\n", encoding="utf-8")
        self.manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))

    def test_guard_and_all_overrides_are_present(self):
        for rel in self.manifest["git_blob_shas"]:
            override = (OVERRIDES / rel)
            self.assertTrue(override.is_file())
        cpp = (OVERRIDES / "src/card/base/command/DidAuthenticateEAC2Command.cpp").read_text()
        self.assertIn("phase=ta_first result=accepted_by_chip", cpp)
        self.assertIn("phase=post_ta result=fault_injected_before_ca", cpp)
        cpp = (OVERRIDES / "src/workflows/base/states/StateDidAuthenticateEac2.cpp").read_text()
        self.assertIn("test.governikus-eid.de", cpp)
        self.assertIn("eidelweiss_eac2_post_ta_once", cpp)
        self.assertIn("EIDELWEISS_EAC2_LAB", cpp)

    def test_fails_closed_for_unknown_sources_before_modifying_anything(self):
        rel = next(iter(self.manifest["git_blob_shas"]))
        src = self.path / rel
        src.parent.mkdir(parents=True)
        src.write_bytes(b"WRONG_REVISION")
        with self.assertRaises(RuntimeError):
            verify_and_install(self.path)
        self.assertEqual(b"WRONG_REVISION", src.read_bytes())

    def test_accepts_already_patched_sources_idempotently(self):
        for rel in self.manifest["git_blob_shas"]:
            dst = self.path / rel
            dst.parent.mkdir(parents=True, exist_ok=True)
            dst.write_bytes((OVERRIDES / rel).read_bytes())
        result = verify_and_install(self.path, dry_run=True)
        self.assertEqual(9, result["checked"])
        result = verify_and_install(self.path)
        self.assertEqual(9, result["installed"])

    def test_git_blob_hash_uses_git_blob_header(self):
        self.assertEqual("ce013625030ba8dba906f756967f9e9ca394464a",
            git_blob_sha(b"hello\n"))


if __name__ == "__main__":
    unittest.main()
