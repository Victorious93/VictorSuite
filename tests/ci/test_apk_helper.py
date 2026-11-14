"""Exercise the standalone packaging probe with real, small ZIP fixtures."""
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile

PROBE = Path(__file__).with_name("check_apk_helper.py")


class ApkHelperProbeTests(unittest.TestCase):
    def run_probe(self, entries):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "fixture.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                for name, content in entries.items():
                    archive.writestr(name, content)
            return subprocess.run([sys.executable, str(PROBE), str(apk)],
                                  capture_output=True, text=True)

    def test_apk_without_native_libraries_is_rejected(self):
        self.assertNotEqual(0, self.run_probe({"AndroidManifest.xml": "fixture"}).returncode)

    def test_missing_helper_is_rejected(self):
        self.assertNotEqual(0, self.run_probe({"lib/arm64-v8a/libtermux.so": b"fixture"}).returncode)

    def test_empty_helper_is_rejected(self):
        self.assertNotEqual(0, self.run_probe({"lib/arm64-v8a/libtermux-api-helper.so": b""}).returncode)

    def test_helper_is_required_in_every_present_abi(self):
        result = self.run_probe({"lib/arm64-v8a/libtermux-api-helper.so": b"\x7fELFfixture",
                                 "lib/x86_64/libtermux.so": b"fixture"})
        self.assertNotEqual(0, result.returncode)
        self.assertIn("lib/x86_64/libtermux-api-helper.so", result.stdout)

    def test_present_helpers_pass(self):
        self.assertEqual(0, self.run_probe({"lib/arm64-v8a/libtermux-api-helper.so": b"\x7fELFfixture",
                                           "lib/x86_64/libtermux-api-helper.so": b"\x7fELFfixture"}).returncode)


if __name__ == "__main__":
    unittest.main()
