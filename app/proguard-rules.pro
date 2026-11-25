# ---------- General ----------
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod, SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# ---------- Kotlin / Coroutines ----------
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.debug.**

# ---------- kotlinx.serialization ----------
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.kirimly.app.**$$serializer { *; }
-keepclassmembers class com.kirimly.app.** { *** Companion; }
-keepclasseswithmembers class com.kirimly.app.** { kotlinx.serialization.KSerializer serializer(...); }

# ---------- Ktor (server + client) ----------
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { volatile <fields>; }
-dontwarn io.ktor.**
-dontwarn java.lang.management.**
-dontwarn javax.naming.**
-dontwarn org.slf4j.**
-dontwarn org.fusesource.jansi.**
-dontwarn reactor.blockhound.**
-dontwarn com.typesafe.config.**

# ---------- Room ----------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ---------- ZXing ----------
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
