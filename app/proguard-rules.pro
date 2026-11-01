# Add project specific ProGuard rules here.
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class **$$serializer {
    *** getInstance();
}

# OkHttp / Retrofit
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep GREE protocol data classes referenced by reflection-free serialization.
-keep class com.gree.remote.data.model.** { *; }