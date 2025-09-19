# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Aggressive optimization for smaller APK
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Remove debug information
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Keep WebView JavaScript interface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep Room database classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# Keep Firebase classes
-keep class com.google.firebase.** { *; }

# Keep Material Design components
-keep class com.google.android.material.** { *; }

# Remove unused code
-assumenosideeffects class java.lang.System {
    public static long currentTimeMillis();
    static java.lang.Class getCallerClass();
}

# Optimize string operations
-assumenosideeffects class java.lang.String {
    public java.lang.String(...);
    public static java.lang.String valueOf(...);
}