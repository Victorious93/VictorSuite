#!/usr/bin/env python3
"""Fail unless JUnit XML results prove tests actually ran and passed.

Rejects: no result files, zero tests, any failure/error, a result file that cannot be parsed
(truncated by a crashed runner), and fewer tests than --min-tests. Skipped tests are reported
but only fail the gate if every test was skipped.

Usage: verify_test_results.py <results_dir> [--min-tests N]
"""
import argparse
import glob
import os
import sys
import xml.etree.ElementTree as ET


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("results_dir")
    ap.add_argument("--min-tests", type=int, default=1)
    args = ap.parse_args()

    files = sorted(glob.glob(os.path.join(args.results_dir, "**", "TEST-*.xml"), recursive=True))
    if not files:
        print(f"FAIL: no TEST-*.xml under {args.results_dir} (runner crashed or never ran)")
        return 1
    tests = failures = errors = skipped = 0
    bad = []
    for f in files:
        try:
            root = ET.parse(f).getroot()
        except ET.ParseError as e:
            bad.append(f"{f}: unparseable ({e})")
            continue
        suites = [root] if root.tag == "testsuite" else list(root.iter("testsuite"))
        for s in suites:
            tests += int(s.get("tests", 0))
            failures += int(s.get("failures", 0))
            errors += int(s.get("errors", 0))
            skipped += int(s.get("skipped", 0))
    print(f"files={len(files)} tests={tests} failures={failures} errors={errors} skipped={skipped}")
    for b in bad:
        print("FAIL:", b)
    if bad:
        return 1
    if tests < args.min_tests:
        print(f"FAIL: {tests} tests, need at least {args.min_tests}")
        return 1
    if failures or errors:
        print("FAIL: test failures/errors present")
        return 1
    if skipped >= tests:
        print("FAIL: every test was skipped")
        return 1
    print("PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
