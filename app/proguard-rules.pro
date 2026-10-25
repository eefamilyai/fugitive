# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class app.fugitive.**$$serializer { *; }
-keepclassmembers class app.fugitive.** { *** Companion; }
-keepclasseswithmembers class app.fugitive.** { kotlinx.serialization.KSerializer serializer(...); }

# MapLibre
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**

# Firebase Realtime Database reflects on nothing of ours (we convert via kotlinx.serialization),
# but keep its own model classes.
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
