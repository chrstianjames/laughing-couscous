# Add project specific ProGuard rules here.
-keep class com.shortly.app.data.model.** { *; }
-keepattributes *Annotation*, InnerClasses
-dontwarn okhttp3.**
-dontwarn kotlinx.serialization.**
-dontwarn org.slf4j.**
