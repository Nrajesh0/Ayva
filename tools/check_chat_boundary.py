#!/usr/bin/env python3
"""Architectural regression guard; behavioral tests also instrument the Notes database."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/com/focusbyrj/app'
paths = list((JAVA / 'util/assistant').glob('*.kt'))
paths += list((JAVA / 'util/router').glob('*.kt'))
paths += list((JAVA / 'util/speech').glob('*.kt'))
paths += list((JAVA / 'ui/screens/chat').glob('*.kt'))
paths += [JAVA / 'ui/screens/BubbleChatActivity.kt']
paths += [JAVA / 'ui/components/TalkActionChips.kt']
paths += [JAVA / 'util/AyvaTalkEngine.kt', JAVA / 'util/TalkAction.kt', JAVA / 'util/VocabQuizChoices.kt',
          JAVA / 'util/backup/TaskRecoveryStore.kt', JAVA / 'util/backup/EncryptedSnapshotFile.kt']
# A narrow source dependency guard, not a claim of complete transitive isolation.
forbidden = re.compile(r'\b(?:NoteDatabase|NoteDao|NoteEntity|ArchiveVaultSecurity|VaultPayloadEncryptor|VaultSyncManager|BackupRestoreManager|DataSafetyManager|EncryptedMediaStorage|NoteMediaManager|AudioMemoManager)\b|com\.focusbyrj\.app\.data\.note\.')
violations = []
preview_only = {JAVA / 'util/TalkAction.kt', JAVA / 'ui/components/TalkActionChips.kt',
                JAVA / 'ui/screens/chat/AyvaResponseControls.kt'}
direct_write = re.compile(r'\b(?:AssistantPreferences\.apply|AssistantActionExecutor\.execute(?:Async)?)\s*\(')
for path in paths:
    for line_number, line in enumerate(path.read_text().splitlines(), 1):
        if forbidden.search(line):
            violations.append(f'{path.relative_to(ROOT)}:{line_number}: forbidden Notes/all-data capability')
        if path in preview_only and direct_write.search(line):
            violations.append(f'{path.relative_to(ROOT)}:{line_number}: mutation chips must request a reviewed query')
if violations:
    print('\n'.join(violations), file=sys.stderr)
    sys.exit(1)
print(f'Chat data-boundary dependency guard passed ({len(paths)} source files).')
