# AMAMO Controller R8 规则（release 瘦身）
# release 开启 minify + shrinkResources，本文件保证反射路径不被裁剪。

# ---- kotlinx.serialization ----
# 保留 @Serializable 数据类与其生成的 serializer（字段名必须保留，报文解析依赖）
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.gree.remote.**$$serializer { *; }
-keepclassmembers class com.gree.remote.** {
    *** Companion;
}
-keepclasseswithmembers class com.gree.remote.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 协议模型整体保留（体积小，稳妥优先）
-keep class com.gree.remote.data.model.** { *; }

# ---- Retrofit ----
# 官方规则：接口方法注解与泛型签名（反射构建请求依赖）
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault, EnclosingMethod
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# ---- OkHttp / Okio（自带 consumer 规则，这里只补 dontwarn）----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.codehaus.mojo.animal_sniffer.*
