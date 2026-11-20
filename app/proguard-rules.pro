# Keep all classes in this Xposed module package.
# GuardianDaemon is started out-of-process via app_process using its fully qualified class name:
# exec app_process -Djava.class.path="..." /system/bin com.hamer.piconeversleep.GuardianDaemon
# R8 static analysis cannot detect dynamic app_process invocation and shrinks/obfuscates GuardianDaemon and its main() method.
-keep class com.hamer.piconeversleep.** { *; }

# Keep Xposed API classes (provided by framework)
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**
