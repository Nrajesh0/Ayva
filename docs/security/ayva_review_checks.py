"""Source contracts only. Native encryption and lifecycle tests run separately in CI."""
from pathlib import Path
import re
ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "app/src/main/java/com/focusbyrj/app"
def read(path): return (BASE / path).read_text()
for path in ["data/note/DatabaseKeyProvider.kt", "data/note/ArchiveVaultSecurity.kt", "util/crypto/EncryptedMediaStorage.kt", "util/backup/SnapshotCryptoEngine.kt"]:
    source = read(path)
    assert "software_seed_v1" not in source, path
    assert "getLegacyFallbackKey" not in source, path
vault = read("data/note/ArchiveVaultSecurity.kt")
assert "encryptWithMasterKey(derivedHash)" not in vault
assert "pinVerifier" in vault and "argon2id-verifier-v2" in vault
for path in ["data/chat/AyvaChatDatabase.kt", "data/drill/DrillDatabase.kt", "data/VocabDatabase.kt"]:
    assert "SecureRoomFactory.create(context)" in read(path), path
assert "cleanPlaceholderData" not in read("data/HabitRepository.kt")
assert "MediaRetirement.candidates" in read("data/note/NoteMediaManager.kt")
assert "withMediaReferenceSnapshot" in read("data/note/NoteMediaManager.kt")
assert "pin_verifier_v2" in vault
assert "sanitize" in read("util/diagnostics/AppLogger.kt")
assert "allowDataLossOnRecovery(false)" in read("util/backup/MaintenanceOpenHelperFactory.kt")
assert "override fun onCorruption" in read("util/backup/MaintenanceOpenHelperFactory.kt")
assert "DatabaseErrorHandler" in read("data/PreservingSqlCipherFactory.kt")
for path in ["FocusApplication.kt", "data/note/NoteDatabase.kt", "data/SecureRoomFactory.kt"]:
    assert "PreservingSqlCipherFactory" in read(path), path
    assert "SupportFactory(" not in read(path), path
assert "BuildConfig.DEBUG &&" in read("util/crypto/TestRuntimeKeys.kt")
assert "abortOnError = true" in (ROOT / "app/build.gradle.kts").read_text()
print("Pre-release source security contracts passed (not runtime verification).")
