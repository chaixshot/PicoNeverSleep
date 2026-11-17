package com.hamer.piconeversleep;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static com.hamer.piconeversleep.Utils.*;

public final class VolumeShortcutHook {
    private static final String SYSTEM_PACKAGE = "android";
    private static boolean sVolumeUpPressed = false;
    private static boolean sVolumeDownPressed = false;

    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    private static Object sPwmInstance;
    private static boolean sIsScheduled = false;

    private static final Runnable sTriggerRunnable = new Runnable() {
        @Override
        public void run() {
            if (sVolumeUpPressed && sVolumeDownPressed) {
                checkAndToggle(sPwmInstance);
            }
            sIsScheduled = false;
        }
    };

    public static void init(XC_LoadPackage.LoadPackageParam lp) {
        if (!SYSTEM_PACKAGE.equals(lp.packageName)) return;
        XposedBridge.log(TAG + ": Hooking " + lp.packageName + " for volume shortcuts");

        try {
            Class<?> pwmClass = XposedHelpers.findClass(
                    "com.android.server.policy.PhoneWindowManager",
                    lp.classLoader
            );

            XposedBridge.hookAllMethods(pwmClass, "interceptKeyBeforeQueueing", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args != null && param.args.length > 0 && param.args[0] instanceof KeyEvent) {
                        KeyEvent event = (KeyEvent) param.args[0];
                        int keyCode = event.getKeyCode();
                        int action = event.getAction();

                        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                            if (action == KeyEvent.ACTION_DOWN) {
                                sVolumeUpPressed = true;
                            } else if (action == KeyEvent.ACTION_UP) {
                                sVolumeUpPressed = false;
                            }
                        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                            if (action == KeyEvent.ACTION_DOWN) {
                                sVolumeDownPressed = true;
                            } else if (action == KeyEvent.ACTION_UP) {
                                sVolumeDownPressed = false;
                            }
                        }

                        if (sVolumeUpPressed && sVolumeDownPressed) {
                            if (!sIsScheduled) {
                                sPwmInstance = param.thisObject;
                                sIsScheduled = true;
                                sHandler.postDelayed(sTriggerRunnable, 2000L); // Hold for 2 seconds
                                XposedBridge.log(TAG + ": Volume Up + Down held, starting 2s timer...");
                            }
                        } else {
                            if (sIsScheduled) {
                                sHandler.removeCallbacks(sTriggerRunnable);
                                sIsScheduled = false;
                                XposedBridge.log(TAG + ": Volume shortcut released before 2s, timer canceled.");
                            }
                        }
                    }
                }
            });

            XposedBridge.log(TAG + ": Successfully hooked PhoneWindowManager for volume combination shortcut");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": PhoneWindowManager volume hook error: " + t);
        }
    }

    private static void restoreVolume(Context context) {
        try {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 100, 0);
                XposedBridge.log(TAG + ": Set volume to " + 100 + ")");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Failed to set volume: " + t);
        }
    }

    private static void checkAndToggle(Object pwmInstance) {
        try {
            Context context = null;
            try {
                context = (Context) XposedHelpers.getObjectField(pwmInstance, "mContext");
            } catch (Throwable ignored) {
                try {
                    context = (Context) XposedHelpers.callMethod(pwmInstance, "getContext");
                } catch (Throwable ignored2) {
                }
            }

            if (context != null) {
                boolean currentState = GetGlobalInt(context, SETTING_NEVER_SLEEP, 0) == 1;
                boolean targetState = !currentState;
                XposedBridge.log(TAG + ": Volume shortcut toggling Never Sleep from " + currentState + " to " + targetState);
                restoreVolume(context);
                PlayBeepSound(targetState);
                ToggleNeverSleep(context, targetState);
            }

            sVolumeUpPressed = false;
            sVolumeDownPressed = false;
            sIsScheduled = false;
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": checkAndTriggerStop failed: " + t);
        }
    }
}
