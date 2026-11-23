package com.focusbyrj.app.ui.navigation

import android.content.Intent

/** The launch picker, dock and external entry points share the same destinations. */
internal object MainNavigation {
    val tabs = listOf(Screen.Dashboard, Screen.Todos, Screen.Empty, Screen.Time, Screen.Account)
    private val routes =
        tabs.map { it.route }.toSet() +
            SettingsRoutes.all +
            setOf(Screen.Schedules.route, Screen.AddRestriction.route, Screen.Habits.route)

    fun destination(route: String?): String? {
        val resolved = if (route == Screen.EmptyTab.route) Screen.Time.route else route
        return resolved?.takeIf { it in routes }
    }

    fun launchTab(saved: String?): String =
        destination(saved)?.takeIf { route -> tabs.any { it.route == route } }
            ?: Screen.Dashboard.route

    fun isSensitive(route: String?): Boolean =
        route == Screen.Empty.route || route in SettingsRoutes.sensitive
}

/** One user request, consumed before dispatch so recreation cannot repeat an editor action. */
internal data class MainLaunchRequest(
    val route: String,
    val openEditor: Boolean = false,
    val noteId: Long? = null,
    val newNote: Boolean? = null,
    val voiceNote: Boolean = false,
    val invalidDestination: Boolean = false,
) {
    companion object {
        private val extras =
            listOf(
                "navigate_to",
                "NAV_DESTINATION",
                "open_add_dialog",
                "open_note_id",
                "open_new_note",
                "as_checklist",
                "open_voice_note",
            )

        fun consume(intent: Intent?): MainLaunchRequest? {
            if (intent == null || extras.none(intent::hasExtra)) return null
            val rawRoute =
                runCatching {
                        intent.getStringExtra("navigate_to")
                            ?: intent.getStringExtra("NAV_DESTINATION")
                    }
                    .getOrNull()
            fun flag(key: String) =
                runCatching { intent.getBooleanExtra(key, false) }.getOrDefault(false)
            val id =
                runCatching { intent.getLongExtra("open_note_id", -1) }
                    .getOrDefault(-1)
                    .takeIf { it > 0 }
            val newNote = flag("open_new_note")
            val checklist = flag("as_checklist")
            val voice = flag("open_voice_note")
            val add = flag("open_add_dialog")
            extras.forEach(intent::removeExtra)
            val route = MainNavigation.destination(rawRoute)
            return when {
                id != null -> MainLaunchRequest(Screen.Empty.route, noteId = id)
                newNote -> MainLaunchRequest(Screen.Empty.route, newNote = checklist)
                voice -> MainLaunchRequest(Screen.Empty.route, voiceNote = true)
                add ->
                    MainLaunchRequest(
                        if (route == Screen.Habits.route) route else Screen.Todos.route,
                        openEditor = true,
                    )
                else ->
                    MainLaunchRequest(
                        route ?: Screen.Dashboard.route,
                        invalidDestination = rawRoute != null && route == null,
                    )
            }
        }
    }
}
