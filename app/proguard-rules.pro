# Keep kotlinx.serialization generated serializers for our models.
-keepclassmembers class com.jadroid.launcher.** {
    *** Companion;
}
-keepclasseswithmembers class com.jadroid.launcher.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.jadroid.launcher.**$$serializer { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
