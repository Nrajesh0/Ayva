package com.focusbyrj.app.util.assistant

/** Inspectable authored packages and structural diagnostics; never stores the text of a diagnostic query. */
internal object ConversationRegistry {
    data class Package(val id: String, val version: Int, val language: String, val provenance: String, val scope: String)
    val packages = listOf(
        Package("concepts", 2, "en", "Ayva authored guidance", "Study, travel preparation, projects, decisions and routines; no live facts"),
        Package("planning-methods", 2, "en", "Ayva authored methods", "Bounded outlines and smaller-action methods; estimates and dates require user input"),
        Package("reasoning", 1, "en", "User-supplied premises and rules", "24 premises, 12 rules and eight inference passes; no independent factual verification"),
        Package("everyday-topics", 1, "en", "Ayva authored conversation", "Reading, music, cooking, films, coding and walking"),
        Package("creative", 1, "en", "Ayva authored story and game structures", "Small continuing stories and existing finite games"))
    fun answer(state: AssistantSession, input: String): String? = when (ConversationLanguage.normalize(input).lowercase().trimEnd('.', '!', '?')) {
        "conversation capabilities", "what conversation topics do you support", "show conversation packages" -> packages.joinToString("\n") { "${it.id} v${it.version} (${it.language}): ${it.scope}. ${it.provenance}." }
        "explain the last conversation route", "conversation routing details" -> state.featureState?.optString("lastConversationRoute")?.takeIf { it.isNotBlank() }
            ?.let { "The prior structural routing choice was $it. This diagnostic retains an owner and reason, not the message text." } ?: "No structural routing choice is recorded yet."
        else -> null
    }
}
