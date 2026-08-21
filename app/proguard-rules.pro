# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Preservation of native method names for JNI
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep native-facing classes and all their members to ensure JNI and service calls work
-keep class com.example.nozapret.core.ByeDpiProxy { *; }
-keep class com.example.nozapret.core.HevSocks5Tunnel { *; }
-keep class com.example.nozapret.services.DpiVpnService { *; }

# Keep VpnService and its protect method (used via JNI)
-keep class android.net.VpnService {
    boolean protect(int);
}

# Aggressive Size Reduction Optimizations

# Remove all Log.d, Log.i, Log.v calls to save space and improve privacy
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Remove unnecessary metadata and attributes
-keepattributes !SourceFile,!LineNumberTable,!Signature,!AnnotationDefault,!EnclosingMethod,!InnerClasses

# Optimization settings for R8
-optimizationpasses 5
-allowaccessmodification
-mergeinterfacesaggressively
-repackageclasses ''
