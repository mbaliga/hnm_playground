#!/usr/bin/env python3
"""Port guards (plan X2): keeps the default build free of platform-port leakage.

Checks (each selectable; none selected means all but --projects needs Gradle, so it is opt-in):
  --paths     tracked paths must be safe on Windows (reserved names, case collisions, trailing dot/space,
              illegal characters, over-long paths).
  --purity    core/src/commonMain and ui/src/commonMain must not import java./javax./android. nor call
              System. (comments and string literals are ignored).
  --projects  `gradlew projects` must list no ios/wasm/Ubuntu Touch module while the matching ENABLE_*
              variable is unset. Needs Gradle plugin resolution (network), so CI is the evidence.

Exit status 0 when clean, 1 on any violation, 2 on a tool error.
"""
import argparse
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PURE_DIRS = ["core/src/commonMain", "ui/src/commonMain"]
MAX_PATH_LEN = 180  # leaves room for a checkout prefix under Windows' 260-char limit
RESERVED = {"CON", "PRN", "AUX", "NUL"} | {f"COM{i}" for i in range(1, 10)} | {f"LPT{i}" for i in range(1, 10)}
ILLEGAL = set('<>:"|?*') | {chr(c) for c in range(32)}
# project-name pattern -> the variable that must be set for it to appear
GATED = [
    (re.compile(r"ios", re.I), "ENABLE_IOS"),
    (re.compile(r"wasm", re.I), "ENABLE_WASM"),
    (re.compile(r"^(web|ubuntu-touch)$|ubuntu|click", re.I), "ENABLE_UT"),
]


def tracked_paths():
    out = subprocess.run(["git", "ls-files", "-z"], cwd=ROOT, check=True, capture_output=True).stdout
    return [p for p in out.decode("utf-8").split("\0") if p]


def lint_paths(paths):
    problems = []
    seen = {}
    for p in paths:
        if len(p) > MAX_PATH_LEN:
            problems.append(f"{p}: path is {len(p)} chars (limit {MAX_PATH_LEN})")
        low = p.lower()
        if low in seen and seen[low] != p:
            problems.append(f"{p}: case collision with {seen[low]}")
        seen.setdefault(low, p)
        for part in p.split("/"):
            stem = part.split(".")[0].upper()
            if stem in RESERVED:
                problems.append(f"{p}: reserved Windows name '{part}'")
            if part.endswith(".") or part.endswith(" "):
                problems.append(f"{p}: component '{part}' ends in a dot or space")
            bad = sorted(c for c in set(part) if c in ILLEGAL)
            if bad:
                problems.append(f"{p}: component '{part}' has illegal characters {bad!r}")
    return problems


def strip_code(text):
    """Remove comments and string literals, keeping line numbers."""
    out = []
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        two = text[i:i + 2]
        if two == "//":
            while i < n and text[i] != "\n":
                i += 1
        elif two == "/*":
            depth = 0  # Kotlin block comments nest
            while i < n:
                if text[i:i + 2] == "/*":
                    depth += 1
                    i += 2
                elif text[i:i + 2] == "*/":
                    depth -= 1
                    i += 2
                    if depth == 0:
                        break
                else:
                    if text[i] == "\n":
                        out.append("\n")
                    i += 1
        elif text[i:i + 3] == '"""':
            i += 3
            while i < n and text[i:i + 3] != '"""':
                if text[i] == "\n":
                    out.append("\n")
                i += 1
            i += 3
        elif c == '"':
            i += 1
            while i < n and text[i] != '"' and text[i] != "\n":
                i += 2 if text[i] == "\\" else 1
            i += 1
        elif c == "'":
            i += 1
            while i < n and text[i] != "'" and text[i] != "\n":
                i += 2 if text[i] == "\\" else 1
            i += 1
        else:
            out.append(c)
            i += 1
    return "".join(out)


IMPORT_RE = re.compile(r"^\s*import\s+(java|javax|android)\.")
SYSTEM_RE = re.compile(r"(?<![\w.])System\s*\.")


def scan_source(text):
    """Return (line_no, reason) for each purity violation in one Kotlin source."""
    hits = []
    for no, line in enumerate(strip_code(text).split("\n"), 1):
        m = IMPORT_RE.match(line)
        if m:
            hits.append((no, f"imports {m.group(1)}.*"))
        if SYSTEM_RE.search(line):
            hits.append((no, "calls System."))
    return hits


def lint_purity(root=ROOT, dirs=PURE_DIRS):
    problems = []
    for d in dirs:
        for dirpath, _, files in os.walk(os.path.join(root, d)):
            for f in sorted(files):
                if not f.endswith(".kt"):
                    continue
                full = os.path.join(dirpath, f)
                with open(full, encoding="utf-8") as fh:
                    for no, why in scan_source(fh.read()):
                        problems.append(f"{os.path.relpath(full, root)}:{no}: {why}")
    return problems


def project_names(listing):
    return re.findall(r"Project '(:[^']+)'", listing)


def lint_projects(listing, env):
    problems = []
    for name in project_names(listing):
        leaf = name.lstrip(":").split(":")[-1]
        for pat, var in GATED:
            if pat.search(leaf) and env.get(var) != "1":
                problems.append(f"project {name} is present but {var} is not set")
    return problems


def run_projects():
    env = {k: v for k, v in os.environ.items() if not k.startswith("ENABLE_")}
    res = subprocess.run([os.path.join(ROOT, "gradlew"), "projects", "-q", "--console=plain"],
                         cwd=ROOT, env=env, capture_output=True, text=True)
    if res.returncode != 0:
        print(res.stdout + res.stderr, file=sys.stderr)
        raise SystemExit(2)
    return lint_projects(res.stdout, env)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--paths", action="store_true")
    ap.add_argument("--purity", action="store_true")
    ap.add_argument("--projects", action="store_true")
    a = ap.parse_args(argv)
    if not (a.paths or a.purity or a.projects):
        a.paths = a.purity = True
    problems = []
    if a.paths:
        paths = tracked_paths()
        print(f"paths: {len(paths)} tracked")
        problems += lint_paths(paths)
    if a.purity:
        problems += lint_purity()
    if a.projects:
        problems += run_projects()
    for p in problems:
        print("VIOLATION", p)
    print(f"{len(problems)} violations")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
