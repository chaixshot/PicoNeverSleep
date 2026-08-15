# LSPosed module entry point
-keep class com.hamer.piconeversleep.MainHook { *; }

# Keep Xposed API classes (provided by framework)
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**

# Keep reflection targets if they were used directly, but we use strings, so R8 won't see them anyway.
# We just need to make sure our own classes aren't obfuscated if we need to access them via reflection,
# but here we only have MainHook.
