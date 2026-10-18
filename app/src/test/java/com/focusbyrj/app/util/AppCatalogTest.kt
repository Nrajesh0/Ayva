package com.focusbyrj.app.util

import android.content.pm.ApplicationInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppCatalogTest {
    @Test fun knownAppsOverrideMisleadingMetadataWithoutSubstringFalsePositives() {
        val productivity = ApplicationInfo().apply { category = ApplicationInfo.CATEGORY_PRODUCTIVITY }
        for (pkg in listOf("com.whatsapp.w4b", "com.instagram.barcelona", "org.telegram.messenger", "org.thoughtcrime.securesms")) assertEquals(pkg, AppCategory.SOCIAL, AppCatalog.category(productivity, pkg))
        val unknown = ApplicationInfo().apply { category = ApplicationInfo.CATEGORY_UNDEFINED }
        val cases = mapOf("com.google.android.apps.nbu.paisa.user" to AppCategory.PAYMENT, "com.phonepe.app" to AppCategory.PAYMENT, "com.amazon.mShop.android.shopping" to AppCategory.SHOPPING, "com.supercell.clashofclans" to AppCategory.GAMES, "com.google.android.apps.docs" to AppCategory.UTILITY, "com.example.reader" to AppCategory.OTHERS, "com.example.bankingnotes" to AppCategory.OTHERS, "com.amazon.avod.thirdpartyclient" to AppCategory.OTHERS)
        cases.forEach { (pkg, category) -> assertEquals(pkg, category, AppCatalog.category(unknown, pkg)) }
    }
    @Test fun filteringCombinesCategoryAndPackageSearchAndRanksNamesConsistently() {
        val a = InstalledApp("org.example.alpha", "Álpha", AppCategory.SOCIAL)
        val b = InstalledApp("org.example.alphabet", "alphabet", AppCategory.SOCIAL)
        val c = InstalledApp("org.example.utility", "Alpha utility", AppCategory.UTILITY)
        val d = InstalledApp("org.alpha.hidden", "Zulu", AppCategory.SOCIAL)
        val apps = listOf(d, c, b, a, a.copy(appName = "duplicate"))
        assertEquals(listOf(a, b, d), AppCatalog.filter(apps, AppCategory.SOCIAL, " alpha "))
        assertEquals(listOf(c), AppCatalog.filter(apps, query = "alpha utility"))
        assertEquals(listOf(d), AppCatalog.filter(apps, query = "org.alpha.hidden"))
        assertEquals(listOf(a), AppCatalog.filter(apps, packages = setOf(a.packageName), query = "alpha"))
    }
}
