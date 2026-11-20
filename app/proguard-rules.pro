# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class app.reelify.** {
    *** Companion;
}
-keepclasseswithmembers class app.reelify.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.reelify.**$$serializer { *; }
# Media3 effect reflection (used for optional components)
-keep class androidx.media3.effect.** { *; }
-keep class androidx.media3.transformer.** { *; }
-dontwarn org.slf4j.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
