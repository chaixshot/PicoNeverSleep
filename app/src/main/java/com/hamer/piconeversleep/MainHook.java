package com.hamer.piconeversleep;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Implementation of a "Never Sleep" quick setting button for Pico VR.
 */
public final class MainHook implements IXposedHookLoadPackage {
    private static final String TAG = "PicoNeverSleep";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) {
        VolumeShortcutHook.init(lp);
        SettingsMenuHook.init(lp);
    }
}
