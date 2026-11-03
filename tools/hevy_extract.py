#!/usr/bin/env python3
"""Builds Platewise's exercise catalog and thumbnails from a local Hevy.apk.

Usage:  py tools/hevy_extract.py Hevy.apk decompiled.js

decompiled.js is the Hermes bundle decompiled with hermes-dec (Hevy is React Native):
    unzip -p Hevy.apk assets/index.android.bundle > index.android.bundle
    pip install hermes-dec
    hbc-decompiler index.android.bundle decompiled.js

Writes (relative to the repo root):
  app/src/main/assets/ejercicios.json              catalog: ids, names (en/es), type, muscle, equipment
  app/src/main/hevy-assets/ejercicios/<id>_m.jpg   thumbnails (male / female variant), gitignored
"""
import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STR = re.compile(r"'((?:[^'\\]|\\.)*)'|\"((?:[^\"\\]|\\.)*)\"")
ENTRY = re.compile(r"^\s*r\d+ = \{'id': '")


def unescape(s):
    return re.sub(r"\\(u[0-9a-fA-F]{4}|x[0-9a-fA-F]{2}|.)", lambda m: (
        chr(int(m.group(1)[1:], 16)) if m.group(1)[0] in "ux" and len(m.group(1)) > 1 else
        {"n": "\n", "t": "\t", "r": "\r"}.get(m.group(1), m.group(1))), s)


def parse_object(line):
    """Parses the flat `{'key': value, ...}` literal hermes-dec prints for each catalog entry."""
    body = line[line.index("{"):line.rindex("}") + 1]
    out, pos = {}, 1
    while pos < len(body) - 1:
        m = STR.match(body, pos)
        if not m:
            break
        key = m.group(1) if m.group(1) is not None else m.group(2)
        pos = m.end()
        assert body[pos:pos + 2] == ": ", body[pos:pos + 20]
        pos += 2
        m = STR.match(body, pos)
        if m:
            out[key] = unescape(m.group(1) if m.group(1) is not None else m.group(2))
            pos = m.end()
        else:
            m = re.compile(r"-?\d+(?:\.\d+)?|null|true|false").match(body, pos)
            if not m:
                raise ValueError(f"unexpected value for {key!r}: {body[pos:pos + 60]!r}")
            raw = m.group(0)
            literals = {"null": None, "true": True, "false": False}
            out[key] = literals[raw] if raw in literals else (float(raw) if "." in raw else int(raw))
            pos = m.end()
        pos += 2 if body[pos:pos + 2] == ", " else 0
    return out


def thumb_key(url):
    """cloud filename -> the resource name Android gave it inside the APK."""
    stem = url.rsplit("/", 1)[-1].rsplit(".", 1)[0].replace("@3x", "")
    return "src_exercises_assets_" + re.sub(r"[^a-z0-9_]", "", stem.lower())


def main():
    apk, js = Path(sys.argv[1]), Path(sys.argv[2])
    exercises = {}
    with js.open(encoding="utf-8", errors="replace") as f:
        for line in f:
            if ENTRY.match(line) and "'exercise_type'" in line:
                e = parse_object(line)
                exercises.setdefault(e["id"], e)

    with zipfile.ZipFile(apk) as z:
        thumbs = {Path(n).stem: n for n in z.namelist() if "src_exercises_assets_" in n}
        out_dir = ROOT / "app/src/main/hevy-assets/ejercicios"
        out_dir.mkdir(parents=True, exist_ok=True)

        catalog, copied, missing = [], 0, 0
        for e in sorted(exercises.values(), key=lambda e: (-(e.get("priority") or 0), e["title"])):
            imgs = {}
            for suffix, field in (("m", "thumbnail_url"), ("f", "thumbnail_url_female")):
                name = thumbs.get(thumb_key(e[field])) if e.get(field) else None
                if name:
                    (out_dir / f"{e['id']}_{suffix}.jpg").write_bytes(z.read(name))
                    imgs[suffix] = True
                    copied += 1
                else:
                    missing += 1
            catalog.append({
                "id": e["id"],
                "en": e["title"],
                "es": e.get("es_title") or e["title"],
                "type": e["exercise_type"],
                "muscle": e["muscle_group"],
                "equipment": e["equipment_category"],
                "m": "m" in imgs,
                "f": "f" in imgs,
            })

    dest = ROOT / "app/src/main/assets/ejercicios.json"
    dest.write_text(json.dumps(catalog, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"{len(catalog)} exercises, {copied} thumbnails copied, {missing} without a local thumbnail")


if __name__ == "__main__":
    main()
