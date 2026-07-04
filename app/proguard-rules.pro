# Fallen ProGuard rules

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.fallen.studio.**$$serializer { *; }
-keepclassmembers class com.fallen.studio.** {
    *** Companion;
}
-keepclasseswithmembers class com.fallen.studio.** {
    kotlinx.serialization.KSerializer serializer(...);
}
