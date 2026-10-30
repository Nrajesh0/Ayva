# Screen protection and legacy migration decisions

## Current change

The existing `focus_prefs/secure_recents` preference now applies before content is displayed in BubbleChatActivity, ShareToNoteActivity, and NoteWidgetConfigureActivity. The shared policy listens to preference changes while a window is open, refreshes on resume and unregisters on destruction. It preserves any stronger pre-existing secure-window flag. Compose dialogs inherit the hosting window policy by default.

Android launcher-hosted RemoteViews widgets are not Ayva-owned windows. Ayva cannot set FLAG_SECURE on the launcher. The widget configuration activity can be protected, but the widget itself needs privacy-conscious content selection (for example, hiding previews) rather than a promise that screenshots are blocked. No widget rendering or speech-provider changes are included.

## Chat data decision

App-layer chat/drill database encryption is optional hardening for this use case, not a prerequisite imposed by this review. Android's private app sandbox and device encryption remain the baseline. Task titles and ordinary questions can still be personal data.

Current expiry: viewed messages are eligible after ten minutes; unread messages, welcome messages and active drills have exceptions. Cleanup runs through application code, so a stopped app cannot promise an exact wall-clock deletion deadline. SQL DELETE is logical deletion, not guaranteed forensic erasure. Backup archives can retain earlier messages. Saved tasks, habits and learning progress have separate lifetimes. If stronger ephemerality is desired, first define retention for unread/active messages and whether conversational text should enter backups.

## Proposed migration approach (not implemented by this change)

Keep an upgrade reader for old formats. A forced export/uninstall/reinstall is not appropriate: skipped versions, incomplete old backups and unavailable Keystore keys can make that path lose data. Normal updates should preserve app data and existing key access.

1. Inventory format versions without changing data. Check free space and key availability. Offer a modern password-encrypted portable backup where the installed version can read all content; explicitly identify anything omitted. Preserve the user's existing backup.
2. Establish a durable migration journal and coordinate writes. Record per-item format, source hash, target and phase. Retain old key envelopes until all dependent records are converted and verified. Use the existing maintenance/recovery infrastructure where its contract applies, extending it explicitly for key migration rather than assuming restore recovery covers keys automatically.
3. Decrypt through the compatibility reader, write a separate current-format encrypted file/envelope with fresh IV, sync it, decrypt and verify complete content/hash, then atomically install. For databases, verify transactional migration and record contents. Never treat an unreadable original as empty data.
4. Commit a durable completion marker, then retire the superseded live copy only after successful verification. Resume or roll back after interruption. If keys or storage are unavailable, defer migration and retain the original; communicate what remains unconverted.
5. Gate release on synthetic fixtures from each supported old format, direct upgrades skipping intermediate versions, interrupted migration, wrong PIN/key, low storage and backup restore on a fresh installation. Test real Keystore/native cryptography as well as JVM logic.

Existing database-passphrase code already attempts to rewrap legacy credentials under Keystore. Media still supports legacy readers; there is no blanket assertion that all installed old files have been converted. Keep compatibility while supported users can upgrade from those versions. An old reader's presence alone does not mean new writes use the old key. No user data was inspected or migrated in this task.
