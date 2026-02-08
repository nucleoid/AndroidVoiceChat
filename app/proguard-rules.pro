# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class ai.openclaw.android.**$$serializer { *; }
-keepclassmembers class ai.openclaw.android.** {
    *** Companion;
}
-keepclasseswithmembers class ai.openclaw.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}
