# Keep calculator engine model classes used with Gson
-keep class com.gpa.calculator.data.remote.model.** { *; }
-keepclassmembers class com.gpa.calculator.data.remote.model.** { *; }
-keepattributes Signature, *Annotation*

# Gson
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Meta Audience Network
-keep class com.facebook.ads.** { *; }
-dontwarn com.facebook.ads.**

# AppLovin
-keep class com.applovin.** { *; }
-dontwarn com.applovin.**

# Google Mobile Ads
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**
