# Vocabulary and retired general dictionary

The general Wordset dictionary, its 256 shards/manifest, runtime lookup store and
import/check scripts were removed. Do not restore the deleted checker to CI or
promise arbitrary dictionary coverage. Native learning content remains in
`app/src/main/assets/vocab.db`: 1,773 idioms and 2,027 OWS seed entries at the
retirement checkpoint. These are lexical seed records, not user notes/progress.

- Native learning, mastery, bookmarks, missed-word flags and quizzes remain.
- Saved dictionary words are ordinary native OWS rows. Their state/source links
  remain in existing Room entities, structural migrations and portable backups.
  Word identity is matched and IDs remapped on restore; old saved rows are retained.
- Supported meaning aliases use native vocabulary; unknown words receive a
  bounded unavailable answer. Authored sentence patterns remain independent.
- Word-chain/anagram games use authored words plus native vocabulary; unknown
  words remain unscored and users can skip. Native queries suspend off the UI path.
- Retired dictionary controls/actions fail closed rather than modifying vocabulary
  or accessing deleted assets. Removing a dataset is not authorization to wipe
  users' saved words or to remove database schema history.

## Attribution

`assets/licenses/Wordset-NOTICE.txt` remains for previously saved Wordset entries;
`dictionary credits` can display it. Wordset uses CC BY-SA 4.0 with the underlying
WordNet notice. This neither licenses independent app code nor establishes the
redistribution provenance of the original `vocab.db`. That separate provenance
gap remains in [open findings](../security/OPEN_FINDINGS.md).

Current checks include `VocabQuizChoicesTest`, backup/vocabulary/assistant
regressions and the chat/security source guards. Historical dataset integrity
results refer to a retired asset set, not a current download or capability.
