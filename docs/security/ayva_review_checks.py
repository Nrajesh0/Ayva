"""Source checks and minimal models for Ayva review. Not Android runtime tests."""
from pathlib import Path
import hashlib, re, sqlite3, json
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'app/src/main/java/com/focusbyrj/app'
def read(path): return (BASE / path).read_text(encoding='utf-8-sig')
results = []
def record(name, evidence): results.append({'check': name, 'evidence': evidence})

# Reproduce the key construction exactly from the three production fallback branches.
for path in ['data/note/DatabaseKeyProvider.kt', 'data/note/ArchiveVaultSecurity.kt', 'util/crypto/EncryptedMediaStorage.kt']:
    source = read(path)
    seed = re.search(r'\.digest\("([^"]*software_seed_v1)"\.toByteArray', source).group(1)
    device_a_key = hashlib.sha256(seed.encode()).digest()
    device_b_key = hashlib.sha256(seed.encode()).digest()
    nonce = bytes(range(12))
    sample = b'Synthetic confidential note or encryption key'
    ciphertext = AESGCM(device_a_key).encrypt(nonce, sample, None)
    assert AESGCM(device_b_key).decrypt(nonce, ciphertext, None) == sample
    record('Known fallback key: ' + path, 'Independent key reconstruction decrypts synthetic ciphertext without any user secret')

# Source-level guard and ordering checks; no claim of executing Android code.
payload = read('util/crypto/VaultPayloadEncryptor.kt')
assert '|| vaultSubKey == null' in payload
lock_body = read('ui/screens/notes/NotesViewModel.kt').split('fun lockVault() {', 1)[1].split('\n    }', 1)[0]
assert '_editingState' not in lock_body and 'autoSaveJob' not in lock_body
record('Lock/save guard', 'Lock leaves editor and autosave intact; payload encryptor returns original entity when subkey is null')

vault = read('data/note/ArchiveVaultSecurity.kt')
setup = vault.split('fun setPasscode(', 1)[1].split('fun verifyPasscode(', 1)[0]
assert setup.index('noteDb.withTransaction') < setup.index('val committed = editor.commit()')
old_key, new_key, nonce = bytes([1])*32, bytes([2])*32, bytes(range(12))
committed_note = AESGCM(new_key).encrypt(nonce, b'Synthetic archived note', None)
try:
    AESGCM(old_key).decrypt(nonce, committed_note, None)
    raise AssertionError('Old credential unexpectedly opened new ciphertext')
except Exception as exc:
    assert type(exc).__name__ == 'InvalidTag'
record('Rekey crash-window model', 'DB committed under new key cannot open using old persisted credentials')

migration = read('data/note/NoteDatabaseMigrationHelper.kt')
assert 'insertSuccessCount++' in migration and 'secureWipeAndDelete(dbFile)' in migration
wipe_tail = migration.split('// Securely wipe and delete legacy plaintext db files after migration',1)[1]
assert 'insertSuccessCount' not in wipe_tail
record('Legacy migration cleanup', 'Source deletion is not conditional on complete successful copy')

# SQL-level lost-update schedule corresponding to HabitRepository read/copy/write.
db = sqlite3.connect(':memory:')
db.execute('CREATE TABLE habit_logs (id INTEGER PRIMARY KEY, habitId INTEGER, date TEXT, completedCount INTEGER, UNIQUE(habitId,date))')
db.execute("INSERT INTO habit_logs VALUES (1,42,'2026-10-06',0)")
a = db.execute('SELECT completedCount FROM habit_logs WHERE id=1').fetchone()[0]
b = db.execute('SELECT completedCount FROM habit_logs WHERE id=1').fetchone()[0]
for count in (a+1,b+1):
    db.execute("INSERT OR REPLACE INTO habit_logs VALUES (1,42,'2026-10-06',?)", (count,))
assert db.execute('SELECT completedCount FROM habit_logs WHERE id=1').fetchone()[0] == 1
record('Habit read/replace interleaving model', 'Two increments leave count 1 rather than 2')

main_sources = list((ROOT / 'app/src/main').rglob('*.kt'))
assert not any('class SupabaseSyncEngine' in p.read_text(encoding='utf-8-sig') or 'object SupabaseSyncEngine' in p.read_text(encoding='utf-8-sig') for p in main_sources)
test = (ROOT / 'app/src/test/java/com/focusbyrj/app/SyncAndConflictResolutionTest.kt').read_text()
assert 'import com.focusbyrj.app.util.sync.supabase.SupabaseSyncEngine' in test
record('Missing test dependency', 'Test imports SupabaseSyncEngine; corresponding production definition absent')

out = {'scope': 'source assertions plus synthetic crypto/SQLite models; not an Android test run', 'checks': results}
Path(__file__).with_name('ayva_review_checks.json').write_text(json.dumps(out, indent=2))
print(json.dumps(out, indent=2))
