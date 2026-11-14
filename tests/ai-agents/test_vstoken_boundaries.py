"""Context privacy and output-budget regression tests; fixtures never contact a model."""
import importlib.machinery
import importlib.util
import io
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import patch

sys.dont_write_bytecode = True
SCRIPT = Path(__file__).resolve().parents[2] / "scripts" / "vstoken"
loader = importlib.machinery.SourceFileLoader("vstoken_boundaries", str(SCRIPT))
spec = importlib.util.spec_from_loader(loader.name, loader)
vt = importlib.util.module_from_spec(spec)
loader.exec_module(vt)


class ContextBoundaries(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.repo = self.root / "repo"
        self.repo.mkdir()

    def write(self, path, text):
        target = self.repo / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text)
        return target

    def context(self, budget=6000):
        return vt.build_context("review database password", str(self.repo), budget, None)

    def test_dotenv_variants_never_enter_context(self):
        subprocess.run(["git", "init", "-q", str(self.repo)], check=True)
        for name in [".env", ".env.local", ".env.production", "service/.env.development",
                     "service/database.env.prod", "service/.env.example"]:
            self.write(name, "database password QA_PRIVATE_VALUE\n")
        self.write("database.py", "database = 'public example'\n")
        context, included = self.context()
        self.assertNotIn("QA_PRIVATE_VALUE", context)
        self.assertEqual(["database.py"], included)

    def test_external_file_symlink_is_excluded(self):
        secret = self.root / "secret.env"
        secret.write_text("database password QA_EXTERNAL_SECRET\n")
        self.write("database.py", "database = 'public'\n")
        (self.repo / "notes.txt").symlink_to(secret)
        context, included = self.context()
        self.assertNotIn("QA_EXTERNAL_SECRET", context)
        self.assertNotIn("notes.txt", included)

    def test_symlink_to_internal_ignored_file_is_excluded(self):
        secret = self.write(".env", "database password QA_INTERNAL_SECRET\n")
        (self.repo / "notes.txt").symlink_to(secret)
        context, included = self.context()
        self.assertNotIn("QA_INTERNAL_SECRET", context)
        self.assertNotIn("notes.txt", included)

    def test_external_directory_symlink_is_not_traversed(self):
        outside = self.root / "outside"
        outside.mkdir()
        (outside / "password.txt").write_text("database password QA_DIRECTORY_SECRET\n")
        (self.repo / "linked").symlink_to(outside, target_is_directory=True)
        context, _ = self.context()
        self.assertNotIn("QA_DIRECTORY_SECRET", context)

    def test_non_git_directory_honors_gitignore_and_aiignore(self):
        self.write(".gitignore", "private/\n")
        self.write(".aiignore", "notes/\n")
        self.write("private/password.txt", "QA_IGNORED_SECRET database password\n")
        self.write("notes/password.txt", "QA_AI_SECRET database password\n")
        self.write("database.py", "database = 'public'\n")
        context, included = self.context()
        self.assertNotIn("QA_IGNORED_SECRET", context)
        self.assertNotIn("QA_AI_SECRET", context)
        self.assertIn("database.py", included)

    def test_gitignore_also_protects_already_tracked_files(self):
        subprocess.run(["git", "init", "-q", str(self.repo)], check=True)
        self.write("private.txt", "database password QA_TRACKED_IGNORE\n")
        subprocess.run(["git", "-C", str(self.repo), "add", "private.txt"], check=True)
        self.write(".gitignore", "private.txt\n")
        context, included = self.context()
        self.assertNotIn("QA_TRACKED_IGNORE", context)
        self.assertNotIn("private.txt", included)

    def test_aiignore_directory_pattern_applies_at_any_depth(self):
        self.write(".aiignore", "private/\n")
        self.write("one/private/database.txt", "database password QA_NESTED_IGNORE\n")
        context, included = self.context()
        self.assertNotIn("QA_NESTED_IGNORE", context)
        self.assertNotIn("one/private/database.txt", included)

    def test_unreadable_gitignore_fails_closed(self):
        rule = self.write(".gitignore", "private.txt\n")
        self.write("private.txt", "database password QA_UNREADABLE_IGNORE\n")
        rule.chmod(0)
        self.addCleanup(rule.chmod, 0o600)
        if os.access(rule, os.R_OK):
            self.skipTest("requires an account that cannot read mode-000 files")
        with self.assertRaises(ValueError):
            self.context()

    def test_nested_symlink_gitignore_fails_closed(self):
        rules = self.write("rules.txt", "private.txt\n")
        self.write("nested/private.txt", "database password QA_LINKED_IGNORE\n")
        (self.repo / "nested/.gitignore").symlink_to(rules)
        with self.assertRaises(ValueError):
            self.context()

    def test_non_utf8_filename_does_not_hide_valid_context(self):
        subprocess.run(["git", "init", "-q", str(self.repo)], check=True)
        path = os.fsencode(self.repo) + b"/data_\xff.txt"
        fd = os.open(path, os.O_WRONLY | os.O_CREAT, 0o600)
        os.close(fd)
        self.write("database.py", "database = 'public'\n")
        for search_path in [os.environ.get("PATH", ""), ""]:
            with self.subTest(git_available=bool(search_path)), patch.dict(os.environ, {"PATH": search_path}):
                context, included = self.context()
                self.assertIn("database.py", included)
                self.assertNotIn("\udcff", context)

    def test_broken_symlink_does_not_crash(self):
        (self.repo / "database.txt").symlink_to(self.root / "missing")
        self.assertEqual([], self.context()[1])

    def test_repository_map_is_inside_token_budget(self):
        for i in range(100):
            self.write(f"source_{i:03}.py", "database = 'public'\n")
        context, _ = self.context(50)
        self.assertLessEqual(vt.count_tokens(context)[0], 50)
        self.assertIn("# Task\nreview database password", context)

    def test_task_that_cannot_fit_is_rejected(self):
        with self.assertRaises(ValueError):
            vt.build_context("database " * 100, str(self.repo), 5, None)

    def test_roadmap_is_inside_token_budget(self):
        roadmap = self.root / "ROADMAP.md"
        roadmap.write_text("# " + "x" * 10000)
        context, _ = vt.build_context("review database", str(self.repo), 40, str(roadmap))
        self.assertLessEqual(vt.count_tokens(context)[0], 40)


class TruncationBudgets(unittest.TestCase):
    def test_router_retains_task_prefix_when_clipping_verbose_context(self):
        task = "Fix the database login error and preserve authorization."
        outgoing = []

        def provider(cfg, tier, prompt, model_override, dry):
            outgoing.append(prompt)
            return {"provider": "fixture", "model": "fixture", "text": "done", "in": 1, "out": 1}

        args = SimpleNamespace(task=task + "\n" + "verbose log text " * 1000,
                               tier=None, model=None, check=None, dry_run=False)
        with tempfile.TemporaryDirectory() as state, \
             patch.object(vt, "STATE", state), \
             patch.object(vt, "load_cfg", return_value={"router_enabled": True, "max_prompt_tokens": 40}), \
             patch.object(vt, "run_tier", side_effect=provider), \
             patch("sys.stdout", new_callable=io.StringIO), \
             patch("sys.stderr", new_callable=io.StringIO):
            self.assertEqual(0, vt.route(args))
        self.assertTrue(outgoing[0].startswith(task))
        self.assertLessEqual(vt.count_tokens(outgoing[0])[0], 40)

    def test_small_line_limits_include_markers(self):
        text = "\n".join(f"error: row {i}" for i in range(200))
        for limit in [1, 2, 3, 4, 60, 120]:
            with self.subTest(limit=limit):
                output = vt.truncate(text, max_lines=limit)
                self.assertLessEqual(len(output.splitlines()), limit)
                self.assertIn("row 199", output)

    def test_tiny_token_limits_have_no_minimum_output_floor(self):
        for limit in [1, 2, 5, 10, 30]:
            with self.subTest(limit=limit):
                output = vt.truncate("a " * 1000, max_tokens=limit)
                self.assertLessEqual(vt.count_tokens(output)[0], limit)

    def test_unicode_output_respects_both_limits(self):
        text = "\n".join(f"警告 🚀 {i}" for i in range(100))
        output = vt.truncate(text, max_lines=3, max_tokens=10)
        self.assertLessEqual(vt.count_tokens(output)[0], 10)
        self.assertLessEqual(len(output.splitlines()), 3)

    def test_zero_or_negative_limits_are_rejected(self):
        for kwargs in [{"max_lines": 0}, {"max_lines": -1}, {"max_tokens": 0}, {"max_tokens": -1}]:
            with self.subTest(kwargs=kwargs), self.assertRaises(ValueError):
                vt.truncate("hello", **kwargs)

    def test_cli_rejects_invalid_limits_without_traceback(self):
        for args in [["trunc", "--max-lines", "0"], ["context", "task", "--max-tokens", "0"]]:
            p = subprocess.run([sys.executable, str(SCRIPT), *args], input="hello", text=True,
                               capture_output=True)
            self.assertNotEqual(0, p.returncode)
            self.assertNotIn("Traceback", p.stderr)

    def test_cli_output_including_newline_fits_budget(self):
        p = subprocess.run([sys.executable, str(SCRIPT), "trunc", "--max-tokens", "1"],
                           input="a " * 1000, text=True, capture_output=True)
        self.assertEqual(0, p.returncode)
        self.assertLessEqual(vt.count_tokens(p.stdout)[0], 1)


if __name__ == "__main__":
    unittest.main()
