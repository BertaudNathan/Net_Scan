# Room
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-keepclassmembers class * { @androidx.room.* <methods>; }

# ARCore (code natif + réflexion)
-keep class com.google.ar.core.** { *; }
-dontwarn com.google.ar.core.**

# Garder les noms des data classes sérialisées si besoin
-keepattributes *Annotation*, Signature, InnerClasses