# Keep Kotlinx Serialization generated classes and serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ainews.globalbrief.data.model.**$$serializer { *; }
-keepclassmembers class com.ainews.globalbrief.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.ainews.globalbrief.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
