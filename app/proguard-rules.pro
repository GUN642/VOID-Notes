# kotlinx.serialization: keep generated serializers of the data model.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class app.voidnotes.data.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.voidnotes.data.**$$serializer { *; }
