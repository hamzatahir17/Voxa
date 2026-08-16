# Voxa AI ProGuard Rules

# Room Database
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>(...);
}
-keep class * extends androidx.room.RoomDatabase
-keep class androidx.room.paging.LimitOffsetDataSource

# Gemini AI (Generative AI SDK)
-keep class com.google.ai.client.generativeai.** { *; }

# Serialization/JSON rules for AI extraction
-keepclassmembers class com.voxa.app.ui.viewmodel.ItineraryItem { *; }
-keepclassmembers class com.voxa.app.data.local.entity.ItineraryEntity { *; }

# General safety for data classes
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
