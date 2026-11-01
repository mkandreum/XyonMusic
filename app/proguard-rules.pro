# R8 completo activado en release.
# Spotify App Remote SDK (reglas del propio SDK + Gson)
-keep class com.spotify.protocol.types.** { *; }
-keep class com.spotify.protocol.mappers.** { *; }
-keep class com.spotify.android.appremote.** { *; }
-dontwarn com.spotify.**
-dontwarn com.fasterxml.jackson.**
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**
