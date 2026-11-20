# M-CHAT-R/F Clinician — Production R8 / ProGuard Hardening Rules

# Keep Kotlinx Serialization models & generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.mchat.**$$serializer { *; }
-keepclassmembers class com.mchat.** {
    *** Companion;
}
-keepclasseswithmembers class com.mchat.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.navigation.**$$serializer { *; }
-keepclassmembers class com.example.navigation.** {
    *** Companion;
}

# Keep SQLCipher native bindings
-keep class net.zetetic.** { *; }
-keep class net.sqlcipher.** { *; }

# Keep Room database & generated implementations
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep Retrofit & Drive API interfaces
-keepattributes Signature, Exceptions, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep interface com.mchat.data.sync.GoogleDriveApi { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep BouncyCastle Argon2 / crypto primitives
-keep class org.bouncycastle.crypto.generators.Argon2BytesGenerator { *; }
-keep class org.bouncycastle.crypto.params.Argon2Parameters** { *; }
-dontwarn org.bouncycastle.**

# Strip verbose / debug logging in release builds to guarantee zero PHI leakage
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
