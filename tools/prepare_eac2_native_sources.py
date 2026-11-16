#!/usr/bin/env python3
"""
Verify and install the Eidelweiss native C++ overlays into a separate local
checkout of mtschirs/eidolon/AusweisApp.

Refuses to patch unknown upstream revisions; copies are transactional at the
validation level. Never alters the remote eidolon repository.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "instrumentation" / "base-manifest.json"
OVERRIDES = ROOT / "instrumentation" / "ausweisapp-overrides"


def git_blob_sha(data: bytes) -> str:
    return hashlib.sha1(b"blob " + str(len(data)).encode("ascii") + b"\0" + data).hexdigest()


def verify_and_install(target: Path, dry_run: bool = False) -> dict:
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    files = manifest["git_blob_shas"]
    target = target.resolve()
    if not (target / "CMakeLists.txt").is_file():
        raise RuntimeError("Expected the root directory of the vendored AusweisApp")
    if len(files) < 5:
        raise RuntimeError("Refusing to install incomplete instrumentation")
    plans = []
    for relpath, expected_sha in sorted(files.items()):
        rel = Path(relpath)
        if rel.is_absolute() or ".." in rel.parts or rel.suffix not in (".cpp", ".h"):
            raise RuntimeError("Unexpected path in source manifest")
        original = target / rel
        override = OVERRIDES / rel
        if not original.is_file() or not override.is_file():
            raise RuntimeError(f"Missing original or override: {relpath}")
        before = original.read_bytes()
        after = override.read_bytes()
        if git_blob_sha(before) != expected_sha and before != after:
            raise RuntimeError(f"Unrecognized upstream revision: {relpath}")
        if b"EIDELWEISS_EAC2_LAB" not in after and rel.suffix == ".cpp" and (
            rel.name in ("DidAuthenticateEAC2Command.cpp", "StateDidAuthenticateEac2.cpp")
        ):
            raise RuntimeError(f"Instrumentation guard missing: {relpath}")
        plans.append((original, after))
    if not dry_run:
        for original, after in plans:
            if original.read_bytes() == after:
                continue
            with tempfile.NamedTemporaryFile(dir=original.parent, delete=False) as tmp:
                tmp.write(after)
                temp = Path(tmp.name)
            try:
                os.replace(temp, original)
            finally:
                temp.unlink(missing_ok=True)
    return {"checked": len(plans), "installed": 0 if dry_run else len(plans),
            "guard": manifest["compile_define"], "target": str(target)}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="Local path to eidolon/AusweisApp")
    parser.add_argument("--check-only", action="store_true")
    args = parser.parse_args()
    try:
        result = verify_and_install(args.source, args.check_only)
    except (RuntimeError, OSError, ValueError) as exc:
        print("EAC2 source patch rejected:", str(exc), file=sys.stderr)
        return 2
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
