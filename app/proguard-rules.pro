# ==============================================================================
# Ayva / Focus by Rj — Production-Grade ProGuard / R8 Configuration
# ==============================================================================

# ------------------------------------------------------------------------------
# 1. Bytecode Optimization, Shrinking & Obfuscation
# ------------------------------------------------------------------------------
# Renames source file attributes to disguise internal directory structure
# while retaining LineNumberTable for accurate stack trace line numbers.
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable,InnerClasses,EnclosingMethod,Signature,*Annotation*

# Broadens method and field visibility modifiers to unlock maximum R8 inlining
-allowaccessmodification

# Strip debug and verbose Log calls in release builds to prevent string leakage
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ------------------------------------------------------------------------------
# 2. Application Core & Architectural Invariants
# ------------------------------------------------------------------------------
# Main activity entry point
-keep class com.focusbyrj.app.MainActivity { *; }

# CRITICAL SYSTEM INVARIANT: App Blocking Service & Direct WindowManager Overlay
# Retain core blocking components to guarantee Android 14+ BAL compatibility
-keep class com.focusbyrj.app.service.FocusBlockerService { *; }
-keep class com.focusbyrj.app.service.BlockOverlayManager { *; }

# Dynamic Profile Avatar App Icon Aliases (invoked via PackageManager component name)
-keep class com.focusbyrj.app.MainActivityAlias* { *; }

# WorkManager Workers (instantiated dynamically by WorkManager reflection)
-keep public class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ViewModels (instantiated via ViewModelProvider reflection factory)
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Preserve classes/members explicitly annotated with @Keep
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# ------------------------------------------------------------------------------
# 3. Room Database & Persisted Entities
# ------------------------------------------------------------------------------
# Retain Room database, DAOs, and entities without blanket-preserving data classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Entity class * { *; }
-keepclassmembers @androidx.room.Entity class * {
    <fields>;
    <init>(...);
}
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <methods>;
}
-keep class * extends androidx.room.migration.Migration {
    <init>(...);
}
-dontwarn androidx.room.paging.**

# ------------------------------------------------------------------------------
# 4. Native Cryptography & Storage Libraries
# ------------------------------------------------------------------------------
# SQLCipher & SQLite JNI
-keep class net.sqlcipher.** { *; }
-keep interface net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# Argon2kt Native JNI bindings
-keep class com.lambdapioneer.argon2kt.** { *; }
-dontwarn com.lambdapioneer.argon2kt.**

# ------------------------------------------------------------------------------
# 5. UI, Animations, Media & Hardware
# ------------------------------------------------------------------------------
# Rive Runtime Native Objects
-keep class app.rive.runtime.kotlin.** { *; }
-keep interface app.rive.runtime.kotlin.** { *; }
-keepclassmembers class app.rive.runtime.kotlin.** { *; }
-keep class * extends app.rive.runtime.kotlin.core.NativeObject { *; }
-dontwarn app.rive.runtime.kotlin.**

# Lottie Animation
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# Coil Image Loader
-keep class coil.** { *; }
-dontwarn coil.**

# ZXing & Google Play Services Barcode Scanner
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# ------------------------------------------------------------------------------
# 6. Jetpack Compose, Coroutines & Framework Warnings
# ------------------------------------------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keep class androidx.compose.material.icons.** { *; }
-dontwarn androidx.compose.**

-dontwarn javax.annotation.**
-dontwarn okio.**
-dontwarn okhttp3.**
-dontwarn org.checkerframework.**
