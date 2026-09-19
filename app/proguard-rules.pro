# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

-keepattributes SourceFile,LineNumberTable,InnerClasses,EnclosingMethod,Signature,*Annotation*

# Rive Runtime
-keep class app.rive.runtime.kotlin.** { *; }
-keep interface app.rive.runtime.kotlin.** { *; }
-keepclassmembers class app.rive.runtime.kotlin.** { *; }
-keep class * extends app.rive.runtime.kotlin.core.NativeObject { *; }
-dontwarn app.rive.runtime.kotlin.**

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Entity class * { *; }
-keep class com.focusbyrj.app.data.** { *; }
-dontwarn androidx.room.paging.**

# Lottie Animation
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Android & Compose Jetpack
-keep class androidx.compose.material.icons.** { *; }
-dontwarn androidx.compose.**

# Okio & OkHttp & javax.annotation
-dontwarn javax.annotation.**
-dontwarn okio.**
-dontwarn okhttp3.**
-dontwarn org.checkerframework.**

# SQLCipher
-keep class net.sqlcipher.** { *; }
-keep interface net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

-keep class com.focusbyrj.app.service.** { *; }
-keep class com.focusbyrj.app.receiver.** { *; }
-keep class com.focusbyrj.app.model.** { *; }
-keep class com.focusbyrj.app.ui.components.** { *; }
-keep class com.focusbyrj.app.overlay.** { *; }
-keep class com.focusbyrj.app.util.** { *; }

