# Keep Room entities
-keep class com.abetworks.abetcrm.data.model.** { *; }
# Keep enums
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }
# WorkManager
-keep class androidx.work.** { *; }
