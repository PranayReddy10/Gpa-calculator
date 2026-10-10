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

# OneSignal ships its own consumer rules; keep notification payload models used via reflection
-dontwarn com.onesignal.**

# --- Data stored as JSON with Gson: field names must survive obfuscation, or saved data
# --- (saved semesters, history results) would become unreadable after an update.
-keep class com.gpa.calculator.data.Semesters$Semester { *; }
-keep class com.gpa.calculator.core.CalcResult { *; }
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# --- Release builds: drop verbose/debug/info logging (warnings and errors are kept)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
