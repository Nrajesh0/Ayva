# Kotlin-only general dialogue

ChatScript is removed from the application. The existing Kotlin conversation engine remains the owner of responses, context and memory. Native app actions, planning, dictionary lookup, reminders and their authorization paths are unchanged.

Removed the public dictionary/topic archive (16,841,776 bytes), its manifest/digest, all four worker binaries, the process/extraction/cache adapter, obsolete native tests, duplicate native license bundle and experimental build/package tools. Historical research and emulator reports remain historical evidence.

`GeneralDialoguePhrases` preserves the former twelve fallback meanings with bounded Kotlin phrase matching. It only runs when the regular Kotlin engine returns no answer or an unknown rule. Original input remains the source of conversational facts. Negation, uncertainty, quoted/multi-line text, compound wording and unrelated commands are not flattened into a meaning token.

An idempotent background startup cleanup removes the retired `general-native-assets` extraction directory and `general-native-turn` scratch directory on existing installations. It does not traverse symlinks or delete chat history, saved memories, task data, dictionaries or other caches.

Release packaging no longer requires the retired archive. It rejects APKs containing the old archive path or worker library, while still checking all four architectures for the app's remaining native dependencies. Native-library packaging settings are preserved for those dependencies.

Validation:
- Debug and optimized release builds passed, using one Gradle worker and a 5-GiB Kotlin compiler heap in this workspace. No project JVM settings were changed.
- Full suite: 1,441 of 1,442 passed. The remaining drill test failed before its body due to leaked background receiver `PendingResult.finish()` exceptions from the test harness. The affected receiver code was not changed. An isolated rerun of the entire DrillCrashTest class and both new test classes passed all 13 tests, with no failures, errors or skipped tests. The full-suite run must not be represented as entirely green.
- Kotlin tests cover all twelve fallback meanings and rejection of negation, uncertainty, quotations, compounds and commands. Cleanup tests cover idempotence, symlink handling and retention of user files/other caches.
- Chat data-boundary guard, dictionary integrity verification and whitespace checks passed.
- Actual debug and release APKs contain no retired archive, workers or duplicate license bundle. Release packaging validation passed with all four remaining dependency ABIs.
- Debug APK: 57.36 MiB. This is not a same-version before/after size comparison; the previous local APK predates other pulled changes.
- Physical-device verification was not performed. Release version: 2.0.39 (139). Post-push verification is recorded separately.
