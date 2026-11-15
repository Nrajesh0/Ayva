# Historical audit coverage

This is the compact input for `scripts/check_audit_coverage.ps1`. File states
preserve the former tracker's classifications; they are historical coverage,
not proof that the current file or working tree has been re-audited. Current
follow-ups live in [open findings](docs/security/OPEN_FINDINGS.md), and release
evidence belongs to the actual revision. Detailed old findings remain in Git
history and the pre-consolidation documentation archive.

## Critical architectural invariants

Preserve the direct WindowManager blocking mechanism in `AGENTS.md`. Never
delete/reset unreadable user stores, regenerate missing read keys or weaken
authentication to resolve a test/build failure.

## File Coverage Map

| File | Batch | Status | Last Audit |
|:---|:---|:---|:---|
| `AccountScreen.kt` | Historical | Historical record | Prior tracker |
| `ActiveDrillStateEntity.kt` | Historical | Historical record | Prior tracker |
| `AddRestrictionScreen.kt` | Historical | Historical record | Prior tracker |
| `AnalyticsComponents.kt` | Historical | Historical record | Prior tracker |
| `AppIconManager.kt` | Historical | Historical record | Prior tracker |
| `AppLogger.kt` | Historical | Historical record | Prior tracker |
| `AppModeSection.kt` | Historical | Historical record | Prior tracker |
| `AppRepository.kt` | Historical | Historical record | Prior tracker |
| `AppRestriction.kt` | Historical | Historical record | Prior tracker |
| `AppRestrictionDao.kt` | Historical | Historical record | Prior tracker |
| `AppThemeManager.kt` | Historical | Historical record | Prior tracker |
| `AptitudeCardHelper.kt` | Historical | Historical record | Prior tracker |
| `AptitudeManager.kt` | Historical | Historical record | Prior tracker |
| `AptitudeProfileCard.kt` | Historical | Historical record | Prior tracker |
| `AptitudeReminderHelper.kt` | Historical | Historical record | Prior tracker |
| `AptitudeReminderReceiver.kt` | Historical | Historical record | Prior tracker |
| `ArchiveVaultDialogs.kt` | Historical | Historical record | Prior tracker |
| `ArchiveVaultSecurity.kt` | Historical | Historical record | Prior tracker |
| `Argon2idKdf.kt` | Historical | Historical record | Prior tracker |
| `ArithmeticCard.kt` | Historical | Historical record | Prior tracker |
| `ArithmeticEngine.kt` | Historical | Historical record | Prior tracker |
| `ArticleDocxGenerator.kt` | Historical | Historical record | Prior tracker |
| `ArticleEditorialBottomSheet.kt` | Historical | Historical record | Prior tracker |
| `ArticleExportBottomSheet.kt` | Historical | Historical record | Prior tracker |
| `ArticleExporter.kt` | Historical | Historical record | Prior tracker |
| `ArticleExporterHelper.kt` | Historical | Historical record | Prior tracker |
| `ArticlePdfGenerator.kt` | Historical | Historical record | Prior tracker |
| `ArticleTocHelper.kt` | Historical | Historical record | Prior tracker |
| `AudioMemoManager.kt` | Historical | Historical record | Prior tracker |
| `AudioPlayerWidget.kt` | Historical | Historical record | Prior tracker |
| `AutoBackupScheduler.kt` | Historical | Historical record | Prior tracker |
| `AutoBackupWorker.kt` | Historical | Historical record | Prior tracker |
| `AutoSyncManager.kt` | Historical | Historical record | Prior tracker |
| `AutoSyncWorker.kt` | Historical | Historical record | Prior tracker |
| `AyvaAlertTheme.kt` | Historical | Historical record | Prior tracker |
| `AyvaCatPeekingHost.kt` | Historical | Historical record | Prior tracker |
| `AyvaChatDao.kt` | Historical | Historical record | Prior tracker |
| `AyvaChatDatabase.kt` | Historical | Historical record | Prior tracker |
| `AyvaChatMessageEntity.kt` | Historical | Historical record | Prior tracker |
| `AyvaChatTimeline.kt` | Historical | Historical record | Prior tracker |
| `AyvaComposerBar.kt` | Historical | Historical record | Prior tracker |
| `AyvaCompoundCommandHandler.kt` | Historical | Historical record | Prior tracker |
| `AyvaContextEngine.kt` | Historical | Historical record | Prior tracker |
| `AyvaDialogueEngine.kt` | Historical | Historical record | Prior tracker |
| `AyvaIcon.kt` | Historical | Historical record | Prior tracker |
| `AyvaIntentRouter.kt` | Historical | Historical record | Prior tracker |
| `AyvaNotificationColorsDialog.kt` | Historical | Historical record | Prior tracker |
| `AyvaTalkEngine.kt` | Historical | Historical record | Prior tracker |
| `BackupDialogs.kt` | Historical | Historical record | Prior tracker |
| `BackupRestoreManager.kt` | Historical | Historical record | Prior tracker |
| `Batch10SecurityAuditTest.kt` | Historical | Historical record | Prior tracker |
| `Batch11SecurityAuditTest.kt` | Historical | Historical record | Prior tracker |
| `Batch12SecurityAuditTest.kt` | Historical | Historical record | Prior tracker |
| `Batch13SecurityAuditTest.kt` | Historical | Historical record | Prior tracker |
| `Batch1SecurityAuditTest.kt` | Historical | Historical record | Prior tracker |
| `Batch2Pass3SecurityTest.kt` | Historical | Historical record | Prior tracker |
| `Batch2SecurityAuditTest.kt` | Historical | Historical record | Prior tracker |
| `Batch3SecurityAuditTest.kt` | Historical | Historical record | Prior tracker |
| `Batch4SecurityAuditTest.kt` | Historical | Historical record | Prior tracker |
| `Batch5SecurityAuditTest.kt` | Historical | Historical record | Prior tracker |
| `Batch6SecurityAuditTest.kt` | Historical | Historical record | Prior tracker |
| `Batch7SecurityAuditTest.kt` | Historical | Historical record | Prior tracker |
| `Batch8SecurityAuditTest.kt` | Historical | Historical record | Prior tracker |
| `Batch9SecurityAuditTest.kt` | Historical | Historical record | Prior tracker |
| `BlockActivity.kt` | Historical | Historical record | Prior tracker |
| `BlockOverlayManager.kt` | Historical | Historical record | Prior tracker |
| `BootReceiver.kt` | Historical | Historical record | Prior tracker |
| `BubbleChatActivity.kt` | Historical | Historical record | Prior tracker |
| `BubbleChatManager.kt` | Historical | Historical record | Prior tracker |
| `BubbleService.kt` | Historical | Historical record | Prior tracker |
| `BubbleSettingsHelper.kt` | Historical | Historical record | Prior tracker |
| `BubbleSettingsScreen.kt` | Historical | Historical record | Prior tracker |
| `CameraQrScannerDialog.kt` | Historical | Historical record | Prior tracker |
| `CategoryEditorHelper.kt` | Historical | Historical record | Prior tracker |
| `CatMagicRiveView.kt` | Historical | Historical record | Prior tracker |
| `ChatTextSizeDialog.kt` | Historical | Historical record | Prior tracker |
| `Color.kt` | Historical | Historical record | Prior tracker |
| `CompletedTaskHistoryManager.kt` | Historical | Historical record | Prior tracker |
| `CrashHandler.kt` | Historical | Historical record | Prior tracker |
| `CryptoBackupEngine.kt` | Historical | Historical record | Prior tracker |
| `CustomCategoryEditor.kt` | Historical | Historical record | Prior tracker |
| `CustomCategoryManager.kt` | Historical | Historical record | Prior tracker |
| `CustomRestrictionSection.kt` | Historical | Historical record | Prior tracker |
| `DailyQuestManager.kt` | Historical | Historical record | Prior tracker |
| `DailyQuestsCard.kt` | Historical | Historical record | Prior tracker |
| `DailySummaryReceiver.kt` | Historical | Historical record | Prior tracker |
| `DashboardScreen.kt` | Historical | Historical record | Prior tracker |
| `DatabaseKeyProvider.kt` | Historical | Historical record | Prior tracker |
| `DataIntegrityPhase3Test.kt` | Historical | Historical record | Prior tracker |
| `DataSafetyManager.kt` | Historical | Historical record | Prior tracker |
| `DeviceStatsHelper.kt` | Historical | Historical record | Prior tracker |
| `DeviceSyncScreen.kt` | Historical | Historical record | Prior tracker |
| `DiagnosticManager.kt` | Historical | Historical record | Prior tracker |
| `DndHelper.kt` | Historical | Historical record | Prior tracker |
| `DocumentStatsDialog.kt` | Historical | Historical record | Prior tracker |
| `DrillBottomActionRow.kt` | Historical | Historical record | Prior tracker |
| `DrillDatabase.kt` | Historical | Historical record | Prior tracker |
| `DrillModels.kt` | Historical | Historical record | Prior tracker |
| `DrillPaletteHelper.kt` | Historical | Historical record | Prior tracker |
| `DrillQuestionPaletteModal.kt` | Historical | Historical record | Prior tracker |
| `DrillStreakHelper.kt` | Historical | Historical record | Prior tracker |
| `DrillSummaryCard.kt` | Historical | Historical record | Prior tracker |
| `DrillSummaryHelper.kt` | Historical | Historical record | Prior tracker |
| `DrillSummaryMathHelper.kt` | Historical | Historical record | Prior tracker |
| `DrillTopBar.kt` | Historical | Historical record | Prior tracker |
| `DrillTopBarHelper.kt` | Historical | Historical record | Prior tracker |
| `DuolingoComponents.kt` | Historical | Historical record | Prior tracker |
| `DuolingoMysteryChestDialog.kt` | Historical | Historical record | Prior tracker |
| `EditLabelsDialog.kt` | Historical | Historical record | Prior tracker |
| `EncryptedMediaFetcher.kt` | Historical | Historical record | Prior tracker |
| `EncryptedMediaStorage.kt` | Historical | Historical record | Prior tracker |
| `ExplanationSection.kt` | Historical | Historical record | Prior tracker |
| `FocusApplication.kt` | Historical | Historical record | Prior tracker |
| `FocusBlockerService.kt` | Historical | Historical record | Prior tracker |
| `FocusBottomBar.kt` | Historical | Historical record | Prior tracker |
| `FocusDatabase.kt` | Historical | Historical record | Prior tracker |
| `FocusDatabaseMigrationHelper.kt` | Historical | Historical record | Prior tracker |
| `FocusDeviceAdminReceiver.kt` | Historical | Historical record | Prior tracker |
| `FocusEconomyManager.kt` | Historical | Historical record | Prior tracker |
| `FocusExitTracker.kt` | Historical | Historical record | Prior tracker |
| `FocusQuotes.kt` | Historical | Historical record | Prior tracker |
| `FocusSchedule.kt` | Historical | Historical record | Prior tracker |
| `FocusStatsManager.kt` | Historical | Historical record | Prior tracker |
| `FocusViewModel.kt` | Historical | Historical record | Prior tracker |
| `FullscreenDrillHelper.kt` | Historical | Historical record | Prior tracker |
| `FullscreenDrillSummaryView.kt` | Historical | Historical record | Prior tracker |
| `FullscreenDrillView.kt` | Historical | Historical record | Prior tracker |
| `FullscreenSolutionsView.kt` | Historical | Historical record | Prior tracker |
| `GamificationHaptics.kt` | Historical | Historical record | Prior tracker |
| `Habit.kt` | Historical | Historical record | Prior tracker |
| `HabitActionReceiver.kt` | Historical | Historical record | Prior tracker |
| `HabitAlarmScheduler.kt` | Historical | Historical record | Prior tracker |
| `HabitDao.kt` | Historical | Historical record | Prior tracker |
| `HabitFloatingOverlayManager.kt` | Historical | Historical record | Prior tracker |
| `HabitMicroCopyProvider.kt` | Historical | Historical record | Prior tracker |
| `HabitReceiver.kt` | Historical | Historical record | Prior tracker |
| `HabitRepository.kt` | Historical | Historical record | Prior tracker |
| `HabitsChatCard.kt` | Historical | Historical record | Prior tracker |
| `HabitsScreen.kt` | Historical | Historical record | Prior tracker |
| `HabitViewModel.kt` | Historical | Historical record | Prior tracker |
| `HkdfUtil.kt` | Historical | Historical record | Prior tracker |
| `ImageUtils.kt` | Historical | Historical record | Prior tracker |
| `KeepColorDialog.kt` | Historical | Historical record | Prior tracker |
| `KeepColorPalette.kt` | Historical | Historical record | Prior tracker |
| `KeepFontPalette.kt` | Historical | Historical record | Prior tracker |
| `KeepImageCollage.kt` | Historical | Historical record | Prior tracker |
| `KeepNoteCard.kt` | Historical | Historical record | Prior tracker |
| `KeepNoteEditor.kt` | Historical | Historical record | Prior tracker |
| `KeepNoteShareParser.kt` | Historical | Historical record | Prior tracker |
| `KeepSketchDialog.kt` | Historical | Historical record | Prior tracker |
| `KeepThemeIllustration.kt` | Historical | Historical record | Prior tracker |
| `LicenseManager.kt` | Historical | Historical record | Prior tracker |
| `MainActivity.kt` | Historical | Historical record | Prior tracker |
| `McqTextParser.kt` | Historical | Historical record | Prior tracker |
| `MysteryBoxChatCard.kt` | Historical | Historical record | Prior tracker |
| `NoteDao.kt` | Historical | Historical record | Prior tracker |
| `NoteDatabase.kt` | Historical | Historical record | Prior tracker |
| `NoteDatabaseMigrationHelper.kt` | Historical | Historical record | Prior tracker |
| `NoteEntity.kt` | Historical | Historical record | Prior tracker |
| `NoteImageHelper.kt` | Historical | Historical record | Prior tracker |
| `NoteLabelsDialog.kt` | Historical | Historical record | Prior tracker |
| `NoteMediaManager.kt` | Historical | Historical record | Prior tracker |
| `NoteRepository.kt` | Historical | Historical record | Prior tracker |
| `NotesnookBlockModel.kt` | Historical | Historical record | Prior tracker |
| `NotesnookBlockWidgets.kt` | Historical | Historical record | Prior tracker |
| `NotesnookEditorDrawer.kt` | Historical | Historical record | Prior tracker |
| `NotesnookFormattingHelper.kt` | Historical | Historical record | Prior tracker |
| `NotesnookInsertBottomSheet.kt` | Historical | Historical record | Prior tracker |
| `NotesnookTableWidget.kt` | Historical | Historical record | Prior tracker |
| `NotesScreen.kt` | Historical | Historical record | Prior tracker |
| `NotesSpeedDialFab.kt` | Historical | Historical record | Prior tracker |
| `NotesViewModel.kt` | Historical | Historical record | Prior tracker |
| `NoteWidgetActionReceiver.kt` | Historical | Historical record | Prior tracker |
| `NoteWidgetConfig.kt` | Historical | Historical record | Prior tracker |
| `NoteWidgetConfigureActivity.kt` | Historical | Historical record | Prior tracker |
| `NoteWidgetDrawableGenerator.kt` | Historical | Historical record | Prior tracker |
| `NoteWidgetProvider.kt` | Historical | Historical record | Prior tracker |
| `NoteWidgetService.kt` | Historical | Historical record | Prior tracker |
| `OfflineNluEngine.kt` | Historical | Historical record | Prior tracker |
| `PackageChangeReceiver.kt` | Historical | Historical record | Prior tracker |
| `PermissionsDialog.kt` | Historical | Historical record | Prior tracker |
| `PermissionUtils.kt` | Historical | Historical record | Prior tracker |
| `PreferencesHubHelper.kt` | Historical | Historical record | Prior tracker |
| `PreferencesHubScreen.kt` | Historical | Historical record | Prior tracker |
| `ProfessionalSlider.kt` | Historical | Historical record | Prior tracker |
| `ProfileAvatarManager.kt` | Historical | Historical record | Prior tracker |
| `QuickAddNoteItemActivity.kt` | Historical | Historical record | Prior tracker |
| `QuickAddTaskActivity.kt` | Historical | Historical record | Prior tracker |
| `QuickEditNoteActivity.kt` | Historical | Historical record | Prior tracker |
| `RichTextEngine.kt` | Historical | Historical record | Prior tracker |
| `SafePrefsExtensions.kt` | Historical | Historical record | Prior tracker |
| `ScheduleDao.kt` | Historical | Historical record | Prior tracker |
| `SchedulesScreen.kt` | Historical | Historical record | Prior tracker |
| `SecurityScreen.kt` | Historical | Historical record | Prior tracker |
| `SettingsScreen.kt` | Historical | Historical record | Prior tracker |
| `SettingsScreenHelper.kt` | Historical | Historical record | Prior tracker |
| `ShareToNoteActivity.kt` | Historical | Historical record | Prior tracker |
| `SmartDateParser.kt` | Historical | Historical record | Prior tracker |
| `SolutionCardItem.kt` | Historical | Historical record | Prior tracker |
| `SolutionsTopBar.kt` | Historical | Historical record | Prior tracker |
| `SolutionsViewHelper.kt` | Historical | Historical record | Prior tracker |
| `StreakManager.kt` | Historical | Historical record | Prior tracker |
| `SubscriptionScreen.kt` | Historical | Historical record | Prior tracker |
| `SummaryQuotes.kt` | Historical | Historical record | Prior tracker |
| `SupabaseAuthManager.kt` | Historical | Historical record | Prior tracker |
| `SupabaseAuthScreen.kt` | Historical | Historical record | Prior tracker |
| `SupabaseConfig.kt` | Historical | Historical record | Prior tracker |
| `SupabaseKeyManager.kt` | Historical | Historical record | Prior tracker |
| `SupabaseStorageEngine.kt` | Historical | Historical record | Prior tracker |
| `SupabaseSyncEngine.kt` | Historical | Historical record | Prior tracker |
| `SyncAndConflictResolutionTest.kt` | Historical | Historical record | Prior tracker |
| `TalkAction.kt` | Historical | Historical record | Prior tracker |
| `TalkActionChips.kt` | Historical | Historical record | Prior tracker |
| `Task.kt` | Historical | Historical record | Prior tracker |
| `TaskActionReceiver.kt` | Historical | Historical record | Prior tracker |
| `TaskDao.kt` | Historical | Historical record | Prior tracker |
| `TaskReminderHelper.kt` | Historical | Historical record | Prior tracker |
| `TaskReminderOverlayManager.kt` | Historical | Historical record | Prior tracker |
| `TaskReminderPopupActivity.kt` | Historical | Historical record | Prior tracker |
| `TaskReminderReceiver.kt` | Historical | Historical record | Prior tracker |
| `TaskRepository.kt` | Historical | Historical record | Prior tracker |
| `TaskViewModel.kt` | Historical | Historical record | Prior tracker |
| `TemporaryUnlockManager.kt` | Historical | Historical record | Prior tracker |
| `Theme.kt` | Historical | Historical record | Prior tracker |
| `TimeScreen.kt` | Historical | Historical record | Prior tracker |
| `TodosScreen.kt` | Historical | Historical record | Prior tracker |
| `TodoWidgetActionReceiver.kt` | Historical | Historical record | Prior tracker |
| `TodoWidgetConfigureActivity.kt` | Historical | Historical record | Prior tracker |
| `TodoWidgetProvider.kt` | Historical | Historical record | Prior tracker |
| `TodoWidgetService.kt` | Historical | Historical record | Prior tracker |
| `Type.kt` | Historical | Historical record | Prior tracker |
| `UnifiedOverlayCoordinator.kt` | Historical | Historical record | Prior tracker |
| `UsageBreakTracker.kt` | Historical | Historical record | Prior tracker |
| `UsageStatsHelper.kt` | Historical | Historical record | Prior tracker |
| `VaultCryptoEngine.kt` | Historical | Historical record | Prior tracker |
| `VaultPayloadEncryptor.kt` | Historical | Historical record | Prior tracker |
| `VaultSyncManager.kt` | Historical | Historical record | Prior tracker |
| `VocabBriefContent.kt` | Historical | Historical record | Prior tracker |
| `VocabDao.kt` | Historical | Historical record | Prior tracker |
| `VocabDatabase.kt` | Historical | Historical record | Prior tracker |
| `VocabEntities.kt` | Historical | Historical record | Prior tracker |
| `VocabRepository.kt` | Historical | Historical record | Prior tracker |
| `VocabRetentionHubChatCard.kt` | Historical | Historical record | Prior tracker |
| `VoiceRecordDialog.kt` | Historical | Historical record | Prior tracker |
| `WidgetConfig.kt` | Historical | Historical record | Prior tracker |
| `WidgetDrawableGenerator.kt` | Historical | Historical record | Prior tracker |

## Pending Audit Queue

Use the helper for changed/missing files. An empty queue is not current acceptance.

| File | Reason | Changed In |
|:---|:---|:---|
| _None queued here_ | See current findings and actual Git diff | Historical |

## Manual Verification Register

| Area | Required evidence |
| --- | --- |
| Key transitions and recovery | Native Keystore, PIN/key changes, low storage and process/power interruption; old dates are not a fresh pass |
| Android services, overlays and permissions | Supported modern APIs/OEMs, BAL, alarm access, screen lock, notifications and foreground limits |
| UI and widgets | Native task/reward actions, Back/IME/rotation, usage-access refresh, widget taps and launcher rendering |

The prior register recorded checks in September 2026, including a retired
PBKDF2-to-Argon2 upgrade scenario. It does not authorize reintroducing unsupported
development-format readers. Current device gates and verification limits are in
[security](docs/security/README.md) and [open findings](docs/security/OPEN_FINDINGS.md).
