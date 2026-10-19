#!/usr/bin/env python3
"""Reproduce bundled Wordset text shards; build-time tool, never executed by the app.

git clone https://github.com/wordset/wordset-dictionary.git
git -C wordset-dictionary checkout 4503bfafbe4556361a296b9d368c18168dfb56c3
python3 tools/import_dictionary.py --source wordset-dictionary
"""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import unicodedata

COMMIT = "4503bfafbe4556361a296b9d368c18168dfb56c3"
SOURCE_SHA256 = "ed4403906e2ba3afd5bc075611999b44afc513546e160f1fe4391e80f6ecc14c"


def build(source, output):
    files = sorted((source / "data").glob("*.json"))
    digest = hashlib.sha256()
    for path in files:
        digest.update(path.name.encode())
        digest.update(b"\0")
        digest.update(path.read_bytes())
    digest.update((source / "LICENSE").read_bytes())
    if digest.hexdigest() != SOURCE_SHA256:
        raise ValueError("Dictionary source/license differs from the reviewed version")
    entries = {}
    for path in files:
        for word, entry in json.loads(path.read_text()).items():
            key = unicodedata.normalize("NFKC", word).lower().strip()
            if key and len(key) <= 120 and entry.get("meanings"):
                entries.setdefault(key, []).append(entry)
    shards = {f"{i:02x}": {} for i in range(256)}
    for key, records in sorted(entries.items()):
        shards[hashlib.sha256(key.encode()).hexdigest()[:2]][key] = records
    output.mkdir(parents=True, exist_ok=True)
    hashes = {}
    compressed_size = max_expanded = 0
    for key, records in shards.items():
        raw = json.dumps(records, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()
        data = gzip.compress(raw, mtime=0)
        (output / (key + ".dict")).write_bytes(data)
        (output / (key + ".json.gz")).unlink(missing_ok=True)
        hashes[key] = hashlib.sha256(data).hexdigest()
        compressed_size += len(data)
        max_expanded = max(max_expanded, len(raw))
    if compressed_size > 10_000_000 or max_expanded > 262_144:
        raise ValueError("Dictionary exceeds the reviewed storage/lookup budget")
    editorial = Path(__file__).resolve().parent / "data/dictionary_plain_english.json"
    plain = editorial.read_bytes()
    # Editorial definitions/examples are sense-specific adaptations, licensed with the data.
    for sense_id in json.loads(plain):
        if not any(sense_id == meaning.get("id") for records in entries.values() for entry in records for meaning in entry["meanings"]):
            raise ValueError("Plain-English adaptation refers to an unknown source sense")
    (output / "plain_english.json").write_bytes(plain)
    manifest = dict(plain_english_sha256=hashlib.sha256(plain).hexdigest(), format_version=1, source="https://github.com/wordset/wordset-dictionary", commit=COMMIT,
                    source_sha256=SOURCE_SHA256, license="CC-BY-SA-4.0; underlying WordNet notice retained",
                    headwords=len(entries), entries=sum(map(len, entries.values())),
                    senses=sum(len(e["meanings"]) for records in entries.values() for e in records),
                    compressed_bytes=compressed_size, max_expanded_shard=max_expanded, shards=hashes)
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n")
    notice = ("Ayva bundled dictionary: Wordset by Wordset, Inc. and the contributors credited in each entry.\n"
              "Source: https://github.com/wordset/wordset-dictionary/tree/" + COMMIT + "\n"
              "Converted to normalized, hash-sharded JSON by Ayva. Definitions, examples and contributor credits retained.\n"
              "This adapted dictionary DATA is distributed under CC BY-SA 4.0: https://creativecommons.org/licenses/by-sa/4.0/\n"
              "License legal text: https://creativecommons.org/licenses/by-sa/4.0/legalcode\n"
              "These data licenses do not relicense Ayva's independent application code.\n\n")
    (output / "LICENSE.txt").write_text(notice + (source / "LICENSE").read_text())
    print(json.dumps({k: v for k, v in manifest.items() if k != "shards"}, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=Path("app/src/main/assets/dictionary"))
    args = parser.parse_args()
    build(args.source, args.output)
