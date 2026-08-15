package com.hamer.piconeversleep;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Implementation of a "Never Sleep" quick setting button for Pico VR.
 * https://github.com/hhhbwc/pico4-sleep-mode/blob/main/mod_vsleep/src/com/picoxr/vsleep/VSleepHook.java
 */
public final class MainHook implements IXposedHookLoadPackage {
    private static final String TAG = "PicoNeverSleep";
    private static final String SETTINGS_PACKAGE = "com.picovr.settings";
    private static final int NEVER_SLEEP_TILE = 9001;
    private static final String SETTING_NEVER_SLEEP = "pvr_never_sleep_enabled";
    private static final String PROP_NEVER_SLEEP_VOLATILE = "pvr.factorytest.never.sleep";
    private static final String MODULE_PACKAGE = "com.hamer.piconeversleep";
    
    private static volatile Object sButton;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) {
        if ("android".equals(lp.packageName)) {
            XposedBridge.log(TAG + ": Hooking android");
            hookSystemReady(lp);
        }

        if (!SETTINGS_PACKAGE.equals(lp.packageName)) return;
        
        XposedBridge.log(TAG + ": Hooking " + lp.packageName);

        try {
            final Class<?> adapterClass = XposedHelpers.findClass("com.picovr.quicksettings.ButtonListAdapter", lp.classLoader);

            // Hook getItemViewType to return 1 for our custom tile type
            XposedHelpers.findAndHookMethod(adapterClass, "getItemViewType", int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        List<?> data = (List<?>) XposedHelpers.getObjectField(p.thisObject, "a");
                        Object info = data.get((Integer) p.args[0]);
                        if ((Integer) XposedHelpers.callMethod(info, "f") == NEVER_SLEEP_TILE) {
                            p.setResult(1);
                        }
                    } catch (Throwable t) {
                        // Silently fail to avoid crashing the settings app
                    }
                }
            });

            // Hook onBindViewHolder to configure our custom tile
            XposedHelpers.findAndHookMethod(adapterClass, "onBindViewHolder", 
                "androidx.recyclerview.widget.RecyclerView$ViewHolder", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        List<?> data = (List<?>) XposedHelpers.getObjectField(p.thisObject, "a");
                        Object info = data.get((Integer) p.args[1]);
                        if ((Integer) XposedHelpers.callMethod(info, "f") == NEVER_SLEEP_TILE) {
                            // Map to internal type 1 for proper layout selection
                            XposedHelpers.callMethod(info, "m", 1);
                        }
                    } catch (Throwable t) {}
                }

                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    configureButton(p.args[0], (Integer) p.args[1]);
                }
            });

            // Hook the list update method to ensure no duplicates
            XposedHelpers.findAndHookMethod(adapterClass, "b", ArrayList.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    removeDuplicates((ArrayList<?>) p.args[0]);
                }
            });

            // Hook QuickSettingUtils to inject our tile into the loaded list
            final Class<?> utilsClass = XposedHelpers.findClass("com.picovr.quicksettings.utils.QuickSettingUtils", lp.classLoader);
            final Class<?> loadCallback = XposedHelpers.findClass("com.picovr.quicksettings.utils.QuickSettingUtils$LoadButtonsCallBack", lp.classLoader);
            XposedHelpers.findAndHookMethod(utilsClass, "b", Context.class, loadCallback, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    p.args[1] = quickPanelCallback(p.args[1], loadCallback, lp.classLoader);
                }
            });

            XposedBridge.log(TAG + ": Hooks installed successfully");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Failed to install hooks: " + t);
        }
    }

    private void hookSystemReady(XC_LoadPackage.LoadPackageParam lp) {
        try {
            XposedHelpers.findAndHookMethod("com.android.server.SystemServiceManager", lp.classLoader,
                    "startBootPhase", int.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            int phase = (int) param.args[0];
                            if (phase == 600 || phase == 1000) { // PHASE_BOOT_COMPLETED or PHASE_SYSTEM_SERVICES_READY
                                XposedBridge.log(TAG + ": Boot phase " + phase + " reached, syncing props");
                                final Context context;
                                Object mContext = XposedHelpers.getObjectField(param.thisObject, "mContext");
                                if (mContext instanceof Context) {
                                    context = (Context) mContext;
                                } else {
                                     context = null;
                                }

                                if (context != null) {
                                    syncProps(context);
                                    // Also sync after a short delay to override vendor resets
                                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            XposedBridge.log(TAG + ": Delayed sync after boot");
                                            syncProps(context);
                                        }
                                    }, 10000); // 10 seconds delay
                                }
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Failed to hook startBootPhase: " + t);
        }
    }

    private Object quickPanelCallback(final Object original, Class<?> callbackClass, final ClassLoader cl) {
        return Proxy.newProxyInstance(cl, new Class<?>[]{callbackClass}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if ("a".equals(method.getName()) && args != null && args.length == 1) {
                    ArrayList<Object> list = (ArrayList<Object>) args[0];
                    if (!hasTile(list, NEVER_SLEEP_TILE)) {
                        try {
                            Class<?> infoClass = XposedHelpers.findClass("com.picovr.quicksettings.button.QuickSettingButtonInfo", cl);
                            Object item = XposedHelpers.newInstance(infoClass);
                            XposedHelpers.callMethod(item, "m", NEVER_SLEEP_TILE);
                            list.add(0, item);
                        } catch (Throwable t) {
                            XposedBridge.log(TAG + ": Tile injection failed: " + t);
                        }
                    }
                }
                return method.invoke(original, args);
            }
        });
    }

    private boolean hasTile(List<?> list, int type) {
        try {
            for (Object item : list) {
                if ((Integer) XposedHelpers.callMethod(item, "f") == type) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private void removeDuplicates(ArrayList<?> list) {
        boolean seen = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            try {
                if ((Integer) XposedHelpers.callMethod(list.get(i), "f") == NEVER_SLEEP_TILE) {
                    if (seen) list.remove(i);
                    else seen = true;
                }
            } catch (Throwable ignored) {}
        }
    }

    private void configureButton(Object holder, int position) {
        try {
            // Check if this is our tile based on position 0 where we injected it
            if (position != 0) return;
            
            final View button = (View) XposedHelpers.getObjectField(holder, "a");
            final Context context = button.getContext();
            
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleNeverSleep(context);
                    refreshTile(context);
                }
            });
            
            sButton = button;
            syncProps(context);
            button.post(new Runnable() {
                @Override
                public void run() {
                    refreshTile(context);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": configureButton failed: " + t);
        }
    }

    private void toggleNeverSleep(Context context) {
        boolean enabled = isNeverSleepEnabled(context);
        String val = enabled ? "0" : "1";
        putGlobalInt(context, SETTING_NEVER_SLEEP, enabled ? 0 : 1);
        setProp(PROP_NEVER_SLEEP_VOLATILE, val);
        XposedBridge.log(TAG + ": Never Sleep toggled to " + (!enabled));
    }

    private void syncProps(Context context) {
        try {
            int enabled = getGlobalInt(context, SETTING_NEVER_SLEEP, 0);
            String val = String.valueOf(enabled);
            String volatileVal = getProp(PROP_NEVER_SLEEP_VOLATILE, "0");
            if (!val.equals(volatileVal)) {
                setProp(PROP_NEVER_SLEEP_VOLATILE, val);
                XposedBridge.log(TAG + ": Synced volatile prop to " + val);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": syncProps failed: " + t);
        }
    }

    private boolean isNeverSleepEnabled(Context context) {
        return getGlobalInt(context, SETTING_NEVER_SLEEP, 0) == 1;
    }

    private void refreshTile(Context context) {
        try {
            Object button = sButton;
            if (button == null) return;

            boolean enabled = isNeverSleepEnabled(context);
            // 'h' likely sets the active/checked state of the button
            XposedHelpers.callMethod(button, "h", enabled);
            XposedHelpers.callMethod(button, "setTipText", getModuleString(context, "never_sleep"));

            ImageView iconView = findImageView((View) button);
            if (iconView != null) {
                Drawable drawable = getModuleDrawable(context);
                if (drawable != null) {
                    iconView.setBackground(null);
                    iconView.setImageDrawable(drawable);
                    
                    ViewGroup.LayoutParams lp = iconView.getLayoutParams();
                    if (lp != null) {
                        float density = context.getResources().getDisplayMetrics().density;
                        lp.width = (int) (33 * density);
                        lp.height = (int) (33 * density);
                        iconView.setLayoutParams(lp);
                    }
                    iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": refreshTile failed: " + t);
        }
    }

    private ImageView findImageView(View view) {
        if (view instanceof ImageView) return (ImageView) view;
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                ImageView found = findImageView(vg.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private Drawable getModuleDrawable(Context context) {
        try {
            Context moduleContext = context.createPackageContext(MODULE_PACKAGE, Context.CONTEXT_IGNORE_SECURITY);
            int id = moduleContext.getResources().getIdentifier("ic_launcher_foreground", "mipmap", MODULE_PACKAGE);
            if (id == 0) id = moduleContext.getResources().getIdentifier("ic_launcher", "mipmap", MODULE_PACKAGE);
            if (id == 0) id = moduleContext.getResources().getIdentifier("ic_launcher", "drawable", MODULE_PACKAGE);
            if (id != 0) return moduleContext.getResources().getDrawable(id, null);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Failed to load module icon: " + t);
        }
        return null;
    }

    private String getModuleString(Context context, String name) {
        try {
            Context moduleContext = context.createPackageContext(MODULE_PACKAGE, Context.CONTEXT_IGNORE_SECURITY);
            int id = moduleContext.getResources().getIdentifier(name, "string", MODULE_PACKAGE);
            if (id != 0) return moduleContext.getString(id);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Failed to load module string: " + t);
        }
        return "Never Sleep";
    }

    private String getProp(String key, String def) {
        try {
            return (String) XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("android.os.SystemProperties", null), "get", key, def);
        } catch (Throwable t) {
            return def;
        }
    }

    private void setProp(String key, String val) {
        try {
            XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("android.os.SystemProperties", null), "set", key, val);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": setProp failed: " + t);
        }
    }

    private int getGlobalInt(Context c, String k, int d) {
        try {
            return (Integer) XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("android.provider.Settings$Global", null),
                "getInt", c.getContentResolver(), k, d);
        } catch (Throwable t) {
            return d;
        }
    }

    private void putGlobalInt(Context c, String k, int v) {
        try {
            XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("android.provider.Settings$Global", null),
                "putInt", c.getContentResolver(), k, v);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": setting write failed " + k + ": " + t);
        }
    }
}
