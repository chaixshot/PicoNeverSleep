package com.hamer.piconeversleep;

import android.content.ContentResolver;
import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Method;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public final class Utils {
    public static final String TAG = "PicoNeverSleep";
    public static final String PROP_NEVER_SLEEP_VOLATILE = "pvr.factorytest.never.sleep";
    public static final String SETTING_NEVER_SLEEP = "pvr_never_sleep_enabled";
    public static final String MODULE_PACKAGE = "com.hamer.piconeversleep";

    // Cached reflection references
    private static Method sGetPropMethod;
    private static Method sSetPropMethod;
    private static Method sGetIntMethod;
    private static Method sPutIntMethod;
    private static Method sGetStringMethod;
    private static Method sPutStringMethod;

    static {
        try {
            Class<?> systemPropertiesClass = XposedHelpers.findClass("android.os.SystemProperties", null);
            sGetPropMethod = systemPropertiesClass.getMethod("get", String.class, String.class);
            sSetPropMethod = systemPropertiesClass.getMethod("set", String.class, String.class);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Failed to cache SystemProperties reflection: " + t);
        }

        try {
            Class<?> settingsGlobalClass = XposedHelpers.findClass("android.provider.Settings$Global", null);
            sGetIntMethod = settingsGlobalClass.getMethod("getInt", ContentResolver.class, String.class, int.class);
            sPutIntMethod = settingsGlobalClass.getMethod("putInt", ContentResolver.class, String.class, int.class);
            sGetStringMethod = settingsGlobalClass.getMethod("getString", ContentResolver.class, String.class);
            sPutStringMethod = settingsGlobalClass.getMethod("putString", ContentResolver.class, String.class, String.class);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Failed to cache Settings.Global reflection: " + t);
        }
    }

    public static String GetProp(String key, String def) {
        try {
            if (sGetPropMethod != null) {
                return (String) sGetPropMethod.invoke(null, key, def);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": getProp failed: " + t);
        }
        return def;
    }

    public static void SetProp(String key, String val) {
        try {
            if (sSetPropMethod != null) {
                sSetPropMethod.invoke(null, key, val);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": setProp failed: " + t);
        }
    }

    public static int GetGlobalInt(Context c, String k, int d) {
        try {
            if (sGetIntMethod != null && c != null) {
                return (Integer) sGetIntMethod.invoke(null, c.getContentResolver(), k, d);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": getGlobalInt failed: " + t);
        }
        return d;
    }

    public static void PutGlobalInt(Context c, String k, int v) {
        try {
            if (sPutIntMethod != null && c != null) {
                sPutIntMethod.invoke(null, c.getContentResolver(), k, v);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": setting write failed " + k + ": " + t);
        }
    }

    public static String GetGlobalString(Context c, String k) {
        try {
            if (sGetStringMethod != null && c != null) {
                return (String) sGetStringMethod.invoke(null, c.getContentResolver(), k);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": getGlobalString failed: " + t);
        }
        return null;
    }

    public static void PutGlobalString(Context c, String k, String v) {
        try {
            if (sPutStringMethod != null && c != null) {
                sPutStringMethod.invoke(null, c.getContentResolver(), k, v);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": string write failed " + k + ": " + t);
        }
    }

    public static void StartGuardianDaemon(Context context) {
        try {
            Context moduleContext = context.createPackageContext(Utils.MODULE_PACKAGE, Context.CONTEXT_IGNORE_SECURITY);
            String settingsApk = context.getApplicationInfo().publicSourceDir;
            String moduleApk = moduleContext.getApplicationInfo().publicSourceDir;
            String classPath = settingsApk + ":" + moduleApk;

            String cmd = "pkill -f 'com.hamer.piconeversleep.GuardianDaemon' 2>/dev/null || true; sleep 0.3; " +
                    "exec app_process -Djava.class.path=\"" + classPath + "\" /system/bin com.hamer.piconeversleep.GuardianDaemon &";

            Process process = Runtime.getRuntime().exec("su");
            try (java.io.DataOutputStream os = new java.io.DataOutputStream(process.getOutputStream())) {
                os.writeBytes(cmd + "\n");
                os.writeBytes("exit\n");
                os.flush();
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String errorMsg = "Please grant <b>[SharedUID] Android System</b> access from the <b>Magisk</b> app in the <b>Superuser</b> tab.";
                XposedBridge.log(Utils.TAG + ": " + errorMsg);
                ShowToast(context, errorMsg);
            } else {
                XposedBridge.log(Utils.TAG + ": GuardianDaemon started via app_process using settings & module classpath");
            }
        } catch (Throwable t) {
            String errorMsg = "Never Sleep: " + t.getMessage();
            XposedBridge.log(Utils.TAG + ": " + errorMsg);
            ShowToast(context, errorMsg);
        }
    }

    public static void StopGuardianDaemon() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "pkill -f 'com.hamer.piconeversleep.GuardianDaemon'"});
            process.waitFor();
            XposedBridge.log(TAG + ": GuardianDaemon stopped");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Failed to stop GuardianDaemon: " + t);
        }
    }

    public static void ShowToast(final Context context, final String htmlMessage) {
        try {
            final Handler handler = new Handler(Looper.getMainLooper());
            for (int i = 0; i < 3; i++) {
                final long delay = i * 3500L;
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            CharSequence text = Build.VERSION.SDK_INT >= 24
                                    ? android.text.Html.fromHtml(htmlMessage, android.text.Html.FROM_HTML_MODE_LEGACY)
                                    : android.text.Html.fromHtml(htmlMessage);
                            android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_LONG).show();
                        } catch (Throwable ignored) {
                        }
                    }
                }, delay);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void PlayBeepSound(){
        // Play confirmation beep sound
        try {
            ToneGenerator toneGen = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 250);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Failed to play beep sound: " + t);
        }
    }
}
