# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Gson
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepattributes Signature
-keepattributes *Annotation*
-keep class sun.misc.Unsafe { *; }

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.** class * { *; }
-keep @dagger.** class * { *; }

# Models / Entities
-keep class com.mediavault.core.storage.entity.** { *; }
-keep class com.mediavault.core.downloader.model.** { *; }
-keep class com.mediavault.core.upscale.model.** { *; }
