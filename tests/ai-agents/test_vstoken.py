"""Unit tests for scripts/vstoken (run: python3 -m unittest tests/ai-agents/test_vstoken.py)."""
import importlib.machinery, importlib.util, json, os, subprocess, sys, tempfile, unittest

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
loader = importlib.machinery.SourceFileLoader("vstoken", os.path.join(ROOT, "scripts", "vstoken"))
spec = importlib.util.spec_from_loader("vstoken", loader); vt = importlib.util.module_from_spec(spec); loader.exec_module(vt)

class Classify(unittest.TestCase):
    def t(self, task, tier):
        self.assertEqual(vt.classify(task)[0], tier, task)
    def test_trivial(self):
        self.t("rename foo to bar", "trivial"); self.t("what is a PendingIntent?", "trivial")
        self.t("format this json", "trivial")
    def test_routine(self):
        self.t("add a unit test for TermuxConstants path handling", "routine")
        self.t("fix the null check in FileUrl.kt", "routine")
    def test_hard(self):
        self.t("refactor the remote control envelope across modules", "hard")
        self.t("debug why the terminal crashes on rotation", "hard")
        self.t("update A.java, B.kt and C.gradle together", "hard")
    def test_escalation_order(self):
        self.assertEqual(vt.next_tier("trivial"), "routine"); self.assertEqual(vt.next_tier("routine"), "hard")
        self.assertIsNone(vt.next_tier("hard"))

class Trunc(unittest.TestCase):
    def test_keeps_errors_and_tail(self):
        log = "\n".join([f"line {i}" for i in range(2000)] + ["BUILD FAILED"])
        log = log.replace("line 1000", "error: cannot find symbol")
        out = vt.truncate(log, max_lines=60)
        self.assertIn("error: cannot find symbol", out); self.assertIn("BUILD FAILED", out)
        self.assertLess(len(out.splitlines()), 80)
    def test_collapse_repeats(self):
        self.assertIn("repeated 99x", vt.truncate("x\n" * 100))

class Context(unittest.TestCase):
    def test_aiignore_and_gitignore(self):
        d = tempfile.mkdtemp()
        subprocess.run(["git", "init", "-q", d], check=True)
        open(os.path.join(d, ".gitignore"), "w").write("build/\n")
        open(os.path.join(d, ".aiignore"), "w").write("secret/\n")
        for p in ["src/Gate.java", "build/out.java", "secret/notes.txt", "keystore.jks"]:
            os.makedirs(os.path.dirname(os.path.join(d, p)) or d, exist_ok=True)
            open(os.path.join(d, p), "w").write("gate gate gate")
        files = vt.repo_files(d)
        self.assertIn("src/Gate.java", files)
        for bad in ["build/out.java", "secret/notes.txt", "keystore.jks"]:
            self.assertNotIn(bad, files)
        text, inc = vt.build_context("fix gate", d, 2000, None)
        self.assertEqual(inc, ["src/Gate.java"])

class Router(unittest.TestCase):
    def tearDown(self):
        vt.run_tier, vt.load_cfg = self._orig
    def setUp(self):
        self._orig = (vt.run_tier, vt.load_cfg)
        self.d = tempfile.mkdtemp(); vt.STATE = self.d
        vt.CFG_PATH = os.path.join(self.d, "cfg.json")
        json.dump({"router_enabled": False, "tiers": {"trivial": {"provider": "ollama", "model": "m-small"},
                   "routine": {"provider": "claude", "model": "m-mid"}, "hard": {"provider": "claude", "model": "m-top"}},
                   "prices_per_mtok": {"m-mid": {"input": 1, "output": 2}}, "daily_budget_usd": 0.000001},
                  open(vt.CFG_PATH, "w"))
    def test_opt_in(self):
        with self.assertRaises(SystemExit):
            vt.main(["route", "rename x"])
    def test_dry_run_logs_without_prompt_text(self):
        vt.main(["route", "rename secretword to y", "--dry-run"])
        rec = json.loads(open(os.path.join(self.d, "vstoken-routes.jsonl")).read().splitlines()[-1])
        self.assertEqual(rec["tier"], "trivial"); self.assertEqual(rec["model"], "m-small")
        self.assertNotIn("secretword", json.dumps(rec))
    def test_override(self):
        vt.main(["route", "rename x", "--dry-run", "--tier", "hard"])
        rec = json.loads(open(os.path.join(self.d, "vstoken-routes.jsonl")).read().splitlines()[-1])
        self.assertEqual(rec["model"], "m-top")
    def test_escalates_on_failed_check(self):
        calls = []
        def fake(cfg, tier, prompt, mo, dry):
            calls.append(tier); return {"provider": "x", "model": tier, "text": "", "in": 1, "out": 1}
        vt.run_tier = fake
        class A: task = "rename x"; tier = None; model = None; check = "false"; dry_run = False
        vt.load_cfg = lambda: {"router_enabled": True}
        self.assertEqual(vt.route(A), 1)
        self.assertEqual(calls, ["trivial", "routine", "hard"])
    def test_budget_and_unpriced(self):
        vt.load_cfg = lambda: json.load(open(vt.CFG_PATH))
        vt.track_usage("claude", "m-mid", 1000, 1000)
        vt.track_usage("codex", "unpriced", 10, 10)
        rows = vt.read_usage(1)
        self.assertAlmostEqual(rows[0]["cost_usd"], 0.003); self.assertIsNone(rows[1]["cost_usd"])
        self.assertEqual(oct(os.stat(os.path.join(self.d, "vstoken-usage.jsonl")).st_mode & 0o777), "0o600")

if __name__ == "__main__":
    unittest.main()
