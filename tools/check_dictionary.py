#!/usr/bin/env python3
"""Verify source attribution and integrity/size of the bundled dictionary artifacts."""
import gzip
import hashlib
import json
from pathlib import Path
import unicodedata

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "app/src/main/assets/dictionary"
manifest = json.loads((DATA / "manifest.json").read_text())
assert manifest["commit"] == "4503bfafbe4556361a296b9d368c18168dfb56c3"
assert manifest["source_sha256"] == "ed4403906e2ba3afd5bc075611999b44afc513546e160f1fe4391e80f6ecc14c"
assert manifest["format_version"] == 1
assert set(manifest["shards"]) == {f"{i:02x}" for i in range(256)}
assert {p.name for p in DATA.glob("*.dict")} == {f"{i:02x}.dict" for i in range(256)}
assert not list(DATA.glob("*.gz")), "Android would expand .gz source assets"
headwords = entries = senses = size = maximum = 0
sense_ids = set()
for shard, expected_hash in manifest["shards"].items():
    raw = (DATA / (shard + ".dict")).read_bytes()
    assert hashlib.sha256(raw).hexdigest() == expected_hash
    size += len(raw)
    expanded = gzip.decompress(raw)
    assert len(expanded) <= 262_144
    maximum = max(maximum, len(expanded))
    data = json.loads(expanded)
    for word, records in data.items():
        assert unicodedata.normalize("NFKC", word).lower().strip() == word
        assert hashlib.sha256(word.encode()).hexdigest()[:2] == shard
        headwords += 1
        entries += len(records)
        for entry in records:
            assert entry.get("word") and entry.get("wordset_id")
            for meaning in entry["meanings"]:
                assert meaning.get("id") and meaning.get("def")
                sense_ids.add(meaning["id"])
                senses += 1
assert (headwords, entries, senses, size, maximum) == tuple(manifest[key] for key in ("headwords", "entries", "senses", "compressed_bytes", "max_expanded_shard"))
assert size <= 10_000_000
plain = (DATA / "plain_english.json").read_bytes()
assert hashlib.sha256(plain).hexdigest() == manifest["plain_english_sha256"]
assert plain == (ROOT / "tools/data/dictionary_plain_english.json").read_bytes()
for sense, editorial in json.loads(plain).items():
    assert sense in sense_ids and editorial["simple"] and editorial["examples"]
notice = (DATA / "LICENSE.txt").read_text()
assert "CC BY-SA 4.0" in notice and "WordNet 3.0 Copyright 2006" in notice and "contributors" in notice
assert manifest["commit"] in notice
print(f"Dictionary verified: {headwords:,} headwords, {entries:,} source entries, {senses:,} senses; {size:,} compressed bytes, {maximum:,} largest expanded shard.")
