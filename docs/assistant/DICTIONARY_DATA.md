# Bundled dictionary: source, licensing and measurements

The dictionary is packaged text data, not a language model. No runtime download, model, embedding service or inference request was added.

## Source and obligations

- Source: [Wordset Dictionary](https://github.com/wordset/wordset-dictionary), commit `4503bfafbe4556361a296b9d368c18168dfb56c3`.
- Reviewed actual upstream `LICENSE`: Wordset by Wordset, Inc. is **CC BY-SA 4.0**, with the underlying Princeton WordNet 3.0 notice/disclaimer retained. This is not an MIT-licensed dataset.
- Pinned digest of the sorted `data/*.json` filenames/content plus LICENSE: `ed4403906e2ba3afd5bc075611999b44afc513546e160f1fe4391e80f6ecc14c`.
- Source entry contributor/editor credits, definitions, senses, examples and labels remain in the adapted shards. Wordset/WordNet attribution, license links, modification notice and the original upstream notice are bundled in `assets/dictionary/LICENSE.txt`, accessible through `dictionary credits` in chat.
- The normalized/sharded dictionary data and the plain-English sense adaptations/examples are distributed under CC BY-SA 4.0. This does not change the license of independent application code. The JSON adaptation file credits Ayva separately; its examples are labelled Ayva examples, not attributed to the source.
- Upstream describes a historical, discontinued collaborative dictionary. Coverage and sense order are not claims of contemporary usage frequency. Source labels are shown where supplied; the app does not infer missing usage labels.

The existing vocabulary asset has 1,773 idioms and 2,027 OWS entries. No separate provenance/license documentation for that historical bundled asset was found during this inventory. Its redistribution provenance remains an existing documentation gap; this new source's license is not asserted to cover the old asset. Original rows are preserved. Native vocabulary exact lookups retain priority for the existing `look up` command; a matching existing OWS definition can be reused on import rather than duplicated.

## Reproduce and verify

Clone the upstream repository at the pinned commit, then run:

```sh
python3 tools/import_dictionary.py --source /path/to/wordset-dictionary
```

The importer verifies the reviewed source/license digest, normalizes keys using Unicode NFKC/lowercase, preserves colliding source entries/senses, and assigns a SHA-256 two-hex-digit bucket. It writes 256 deterministic gzip-compressed `.dict` assets, a manifest with hashes/counts and the attribution notice. It also validates/copies the sense-specific editorial data from `tools/data/dictionary_plain_english.json`.

The `.dict` extension is intentional: Android's asset merger automatically expands source `.gz` files and strips their suffix. An initial integration run caught that behavior. The renamed opaque assets retain gzip contents through packaging, and the runtime uses `GZIPInputStream`. There is no installed unpacked dictionary database or duplicated extracted copy.

`tools/check_dictionary.py` verifies the packaged-source manifest, every compressed shard, sense references, counts, expansion bounds and notices. The importer uses only Python's standard library and is a build/development tool; no network call or importer executes in the app.

## Storage comparison and chosen budget

Measured locally from the same pinned source:

| Candidate | Normalized headwords | Source entries | Senses | Compressed shards | Largest expanded shard |
| --- | ---: | ---: | ---: | ---: | ---: |
| Compact prototype: ASCII keys of at most six characters | 14,618 | 14,713 | 40,430 | 2,210,053 bytes | 40,654 bytes |
| Chosen broader library | 107,945 | 108,128 | 163,274 | 9,513,126 bytes | 142,361 bytes |

The compact selection is a reproducible size comparison, not a curated common-word list; length is not word frequency. The broader candidate retains useful longer words and multiword entries without a frequency dataset of uncertain license.

Import budgets: compressed shards at most **10,000,000 bytes**; any expanded shard at most **262,144 bytes**. Editorial text, notice and manifest add a small amount beyond the shard total. The runtime keeps at most four parsed shards, reads at most 262,145 bytes before rejecting an oversized asset and never loads the complete dictionary. Approximate parsed-object memory must still be measured on Android; serialized byte counts are not heap measurements.

The host prototype's first object parse for a sample lookup was about 0.4 ms for the compact dataset and 6.9 ms for the broader candidate. These Python measurements have uncontrolled OS caching and are not Android cold-start latency guarantees. Physical-device cold/warm lookup latency, heap and battery impact remain unmeasured. See STATUS.md for actual final APK sizes and comparison against the B2 artifacts; do not substitute gzip size for APK measurement.

## Meaning and sentence limits

Senses use stable upstream IDs. Several meanings are shown without pretending source order is frequency ranking. Follow-ups select the actual current sense. Examples come from that sense or separately labelled editorial examples. Nineteen senses currently have separately written simple-English wording; other entries use the source definition and explicitly acknowledge when a simpler alternative/example is unavailable.

Inflection suggestions are bounded, exact-checked dictionary entries and require the user to choose a form. Unknown words never become task creation. No arbitrary synonym replacement is presented as an accurate sentence paraphrase. Supported whole-sentence patterns and counterexamples are listed in [OFFLINE_ASSISTANT_IMPLEMENTATION.md](OFFLINE_ASSISTANT_IMPLEMENTATION.md).

Saving a word imports/links the selected sense in the existing native vocabulary bookmark/learning store. It does not save to Notes or mark the word learned. The source link survives unbookmarking, and the bundled credits remain accessible. Native quizzes can explicitly filter bookmarked words or a selected saved OWS entry; they keep their existing test window.
