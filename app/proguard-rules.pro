# Disable obfuscation (we use Proguard exclusively for optimization)
-dontobfuscate

# Keep all navigation route classes and their serializers
-keep class com.vishruth.key1.app.Routes** { *; }
-keep class com.vishruth.key1.app.Routes$** { *; }

# Keep context configuration classes to prevent navigation issues
-keep class com.vishruth.key1.app.settings.context.** { *; }

# Keep navigation composable functions
-keep class com.vishruth.key1.app.onboarding.** { *; }

# Keep `Companion` object fields of serializable classes.
# This avoids serializer lookup through `getDeclaredClasses` as done for named companion objects.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (both default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# @Serializable and @Polymorphic are used at runtime for polymorphic serialization.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# Keep Compose Navigation classes and serialization
-keep class androidx.navigation.** { *; }
-keep class * extends androidx.navigation.NavDestination { *; }

# Keep all Deeplink annotation classes
-keep @com.vishruth.key1.app.Deeplink class * { *; }
-keepclasseswithmembers class * {
    @com.vishruth.key1.app.Deeplink <methods>;
}

# Ensure Compose functions are not stripped
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
