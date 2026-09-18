# ── Stumpd ProGuard / R8 Rules ──

# Preserve line numbers for crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep Kotlin metadata for reflection
-keep class kotlin.Metadata { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ── Gson serialization ──
# Keep all domain/data model classes that Gson serialises via reflection
-keep class com.oreki.stumpd.domain.model.** { *; }
-keep class com.oreki.stumpd.data.models.** { *; }
-keep class com.oreki.stumpd.data.local.entity.** { *; }
-keep class com.oreki.stumpd.data.sync.SyncState { *; }
-keep class com.oreki.stumpd.data.sync.SyncState$* { *; }
-keep class com.oreki.stumpd.data.sync.SyncResult { *; }
-keep class com.oreki.stumpd.data.sync.SyncResult$* { *; }
-keep class com.oreki.stumpd.data.sync.SyncMetadata { *; }

# Gson generic type handling
-keepattributes Signature
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# ── Firebase / Firestore ──
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ── Room ──
# Room generates code at compile time; entities are also kept above via entity package.
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ── Compose ──
-dontwarn androidx.compose.**

# ── Coroutines ──
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ── Biometric ──
-dontwarn androidx.biometric.**

# ── Play Services Auth ──
-keep class com.google.android.gms.auth.** { *; }
