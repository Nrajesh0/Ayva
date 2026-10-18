package com.focusbyrj.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.provider.Settings
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

enum class AppCategory(val title: String) {
    ALL("All"), SOCIAL("Social"), PAYMENT("Finance"), SHOPPING("Shopping"), GAMES("Games"), UTILITY("Utility"), OTHERS("Others")
}
data class InstalledApp(val packageName: String, val appName: String, val category: AppCategory)

/** One eligible-app catalogue and taxonomy for chat, manual shields, routines and custom categories. */
object AppCatalog {
    private val brands = linkedMapOf(
        AppCategory.SOCIAL to listOf("com.whatsapp", "com.instagram", "com.facebook", "com.twitter", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.snapchat.android", "com.reddit", "org.telegram", "org.thoughtcrime.securesms", "com.discord", "com.linkedin", "com.pinterest", "com.google.android.youtube", "tv.twitch.android.app", "com.sharechat", "in.mohalla", "com.tencent.mm", "com.viber", "xyz.bluesky", "org.joinmastodon"),
        AppCategory.PAYMENT to listOf("com.google.android.apps.nbu.paisa", "com.phonepe", "net.one97.paytm", "com.paytm", "com.paypal", "com.venmo", "com.dreamplug", "com.bhim", "in.org.npci.upiapp", "com.sbi", "com.csam.icici", "com.axis", "com.hdfc", "com.revolut", "com.nu.production", "com.blockchain", "com.coinbase"),
        AppCategory.SHOPPING to listOf("com.amazon.mShop", "com.flipkart", "com.myntra", "com.ebay", "com.alibaba", "com.aliexpress", "com.etsy", "com.shopify", "com.meesho", "com.nykaa", "com.snapdeal", "com.einnovation.temu"),
        AppCategory.GAMES to listOf("com.ea", "com.king", "com.supercell", "com.roblox", "com.mojang", "com.epicgames", "com.riotgames", "com.activision", "com.dts.freefireth", "com.pubg", "com.tencent.ig"),
        AppCategory.UTILITY to listOf("com.google.android.gm", "com.google.android.apps.docs", "com.google.android.calendar", "com.google.android.deskclock", "com.google.android.apps.maps", "com.google.android.keep", "com.google.android.calculator", "com.microsoft.office", "com.microsoft.emmx", "com.sec.android.app.camera")
    )
    private fun namespace(pkg: String, root: String) = pkg.equals(root, true) || pkg.startsWith("$root.", true)
    fun category(info: ApplicationInfo, packageName: String): AppCategory {
        // Known namespaces precede inconsistent OEM/productivity metadata. Never match fragments such as “ea” or “king”.
        brands.entries.firstOrNull { (_, names) -> names.any { namespace(packageName, it) } }?.let { return it.key }
        if (Build.VERSION.SDK_INT >= 26) when (info.category) {
            ApplicationInfo.CATEGORY_SOCIAL -> return AppCategory.SOCIAL
            ApplicationInfo.CATEGORY_GAME -> return AppCategory.GAMES
        }
        val segments = packageName.lowercase(Locale.ROOT).split('.', '_')
        return when {
            segments.any { it in setOf("bank", "banking", "payments", "wallet", "finance") } -> AppCategory.PAYMENT
            segments.any { it in setOf("shopping", "shop", "marketplace", "retail") } -> AppCategory.SHOPPING
            segments.any { it in setOf("game", "games") } -> AppCategory.GAMES
            segments.any { it in setOf("calculator", "calendar", "clock", "camera", "weather", "notes", "files", "filemanager", "tools", "utilities", "email") } -> AppCategory.UTILITY
            Build.VERSION.SDK_INT >= 26 && info.category in setOf(ApplicationInfo.CATEGORY_PRODUCTIVITY, ApplicationInfo.CATEGORY_MAPS) -> AppCategory.UTILITY
            else -> AppCategory.OTHERS
        }
    }
    fun protectedPackages(context: Context): Set<String> = buildSet {
        addAll(listOf(context.packageName, "android", "com.android.settings", "com.android.systemui", "com.android.phone", "com.android.permissioncontroller", "com.google.android.permissioncontroller", "com.android.packageinstaller", "com.google.android.packageinstaller", "com.android.emergency", "com.android.dialer", "com.google.android.dialer", "com.samsung.android.dialer"))
        val pm = context.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0).mapNotNullTo(this) { it.activityInfo?.packageName }
        pm.queryIntentActivities(Intent(Intent.ACTION_DIAL).setData(android.net.Uri.parse("tel:")), 0).mapNotNullTo(this) { it.activityInfo?.packageName }
        runCatching { (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)?.enabledInputMethodList?.mapTo(this) { it.packageName } }
        runCatching { Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')?.takeIf(String::isNotBlank)?.let(::add) }
    }
    fun requireEligible(context: Context, pkg: String, protected: Set<String> = protectedPackages(context)) {
        require(pkg.matches(Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+"))) { "Choose an installed application" }
        require(pkg !in protected) { "Ayva, launcher, keyboard, phone and system controls are protected" }
        @Suppress("DEPRECATION") val info = context.packageManager.getApplicationInfo(pkg, 0)
        require(info.enabled) { "Choose an enabled application" }
    }
    fun load(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val protected = protectedPackages(context)
        val rows = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).mapNotNull { resolved ->
            val activity = resolved.activityInfo ?: return@mapNotNull null
            val info = activity.applicationInfo ?: return@mapNotNull null
            val pkg = activity.packageName ?: return@mapNotNull null
            if (!activity.enabled || !info.enabled || pkg in protected) return@mapNotNull null
            runCatching { InstalledApp(pkg, pm.getApplicationLabel(info).toString().ifBlank { pkg }, category(info, pkg)) }.getOrNull()
        }
        return filter(rows)
    }
    fun normalize(text: String) = Normalizer.normalize(text, Normalizer.Form.NFKD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex("\\s+"), " ")
    fun filter(apps: List<InstalledApp>, category: AppCategory = AppCategory.ALL, query: String = "", packages: Set<String>? = null): List<InstalledApp> {
        val asked = normalize(query); val words = asked.split(' ').filter(String::isNotBlank)
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        fun rank(app: InstalledApp): Int {
            val label = normalize(app.appName)
            return when { asked.isBlank() || label == asked -> 0; label.startsWith(asked) -> 1; label.split(' ').any { it.startsWith(asked) } -> 2; label.contains(asked) -> 3; else -> 4 }
        }
        return apps.filter { it.packageName.isNotBlank() && (category == AppCategory.ALL || it.category == category) && (packages == null || it.packageName in packages) }
            .distinctBy { it.packageName }.filter { app -> val haystack = normalize(app.appName + " " + app.packageName); words.all(haystack::contains) }
            .sortedWith { a, b -> val relevance = rank(a).compareTo(rank(b)); if (relevance != 0) relevance else collator.compare(a.appName.trim(), b.appName.trim()).takeIf { it != 0 } ?: a.packageName.compareTo(b.packageName) }
    }
}
