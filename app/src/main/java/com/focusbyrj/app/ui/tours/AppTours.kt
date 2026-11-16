package com.focusbyrj.app.ui.tours

internal data class TourStep(val target: String, val title: String, val explanation: String)

internal data class AppTour(
    val id: String,
    val title: String,
    val route: String,
    val steps: List<TourStep>,
)

internal object AppTours {
    val navigation =
        AppTour(
            "navigation",
            "Getting around",
            "dashboard",
            listOf(
                TourStep(
                    "tab_todos",
                    "Tasks & reminders",
                    "Add tasks, set reminders and see what is due today.",
                ),
                TourStep(
                    "tab_empty",
                    "Your notes",
                    "Write notes, add attachments and find your archive here.",
                ),
                TourStep(
                    "settings",
                    "Settings & backups",
                    "Tap your avatar for settings, backups and the optional Ayva bubble.",
                ),
            ),
        )
    val focus =
        AppTour(
            "focus",
            "Focus & app blocking",
            "dashboard",
            listOf(
                TourStep(
                    "add_boundary",
                    "Choose distracting apps",
                    "Tap + to choose apps and set how they are blocked.",
                )
            ),
        )
    val blocking =
        AppTour(
            "blocking",
            "Choosing apps",
            "add_restriction",
            listOf(
                TourStep(
                    "app_search",
                    "Find an app",
                    "Search and select the apps you want to block. Then tap Configure Shield.",
                )
            ),
        )
    val habits =
        AppTour(
            "habits",
            "Habits",
            "habits",
            listOf(
                TourStep(
                    "habit_add",
                    "Build a habit",
                    "Tap + to choose a goal, how often it repeats and whether you want a reminder.",
                )
            ),
        )
    val routines =
        AppTour(
            "routines",
            "Routines",
            "schedules",
            listOf(
                TourStep(
                    "routine_add",
                    "Schedule your focus time",
                    "Tap + to choose days, times and apps to block automatically.",
                )
            ),
        )
    val blockingModes =
        AppTour(
            "blocking_modes",
            "Blocking options",
            "add_restriction",
            listOf(
                TourStep(
                    "block_options",
                    "Hard or soft blocking?",
                    "Tap an app to move it between modes. Hard mode prevents bypassing; Soft mode allows a bypass after a wait.",
                )
            ),
        )
    val tasks =
        AppTour(
            "tasks",
            "Tasks & reminders",
            "todos",
            listOf(
                TourStep(
                    "task_tabs",
                    "Find the right tasks",
                    "Today shows due and overdue tasks. Upcoming shows what is next; All shows your task list.",
                ),
                TourStep(
                    "task_add",
                    "Create a task",
                    "Tap +, then Create task. Include dates such as tomorrow at 6 pm in the title.",
                ),
            ),
        )
    val taskDates =
        AppTour(
            "task_dates",
            "Dates in tasks",
            "todos",
            listOf(
                TourStep(
                    "task_title",
                    "Dates, in your own words",
                    "Try typing: Buy milk tomorrow at 6 pm. Check the detected date and time before saving.",
                )
            ),
        )
    val notes =
        AppTour(
            "notes",
            "Notes & archive",
            "empty",
            listOf(
                TourStep(
                    "notes_menu",
                    "Find your archive",
                    "Open this menu for Archive, Trash and labels. Archive keeps notes out of your main list.",
                )
            ),
        )
    val profile =
        AppTour(
            "profile",
            "Profile & rewards",
            "account",
            listOf(
                TourStep("profile_stats", "Your progress", "See your gold and level here."),
                TourStep(
                    "profile_tabs",
                    "Rewards & achievements",
                    "Overview shows rewards when they are ready to claim. Achievements shows your milestones.",
                ),
            ),
        )
    val backups =
        AppTour(
            "backups",
            "Backups & recovery",
            "settings_data",
            listOf(
                TourStep(
                    "manual_backup",
                    "Save a full backup",
                    "Manual backups include records and attachments. Keep the file and its password safe.",
                ),
                TourStep(
                    "automatic_backup",
                    "Back up on a schedule",
                    "Save encrypted backups automatically. Keep the recovery file safe; Android may delay a run.",
                ),
                TourStep(
                    "recovery_tools",
                    "Snapshots stay on this phone",
                    "Recovery tools include safety snapshots. They recover records, but do not contain attachment files.",
                ),
            ),
        )
    val automatic =
        AppTour(
            "automatic",
            "Automatic backup setup",
            "settings_automatic_backups",
            listOf(
                TourStep(
                    "automatic_setup",
                    "Follow the setup steps",
                    "Choose a folder, save your recovery file and turn backups on. Your recovery file is different from your vault PIN.",
                )
            ),
        )
    val replayable =
        listOf(navigation, focus, tasks, notes, profile, habits, routines, backups, automatic)
}
