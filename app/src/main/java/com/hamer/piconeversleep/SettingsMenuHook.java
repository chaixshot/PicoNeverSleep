package com.hamer.piconeversleep;

import static com.hamer.piconeversleep.Utils.PlayBeepSound;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class SettingsMenuHook {
    private static final String SETTINGS_PACKAGE = "com.picovr.settings";
    private static final int NEVER_SLEEP_TILE = 9002;
    private static final String TILE_ADDED_KEY = "pico_neversleep_quick_added";
    private static final String TILE_INDEX_KEY = "pico_neversleep_quick_index";
    private static final String VSLEEP_ENABLED_KEY = "pico_vsleep_enabled";
    private static final String COORD_REQUEST = "pico_power_coord_v2_request";
    private static final String COORD_ACK = "pico_power_coord_v2_ack";
    private static final String COORD_OWNER = "pico_power_coord_v2_effective_owner";
    private static final String COORD_PHASE = "pico_power_coord_v2_phase";
    private static final String VSLEEP_WAS_ENABLED_KEY = "pico_neversleep_vsleep_was_enabled";
    private static volatile Object sButton;

    public static void init(XC_LoadPackage.LoadPackageParam lp) {
        if (!SETTINGS_PACKAGE.equals(lp.packageName)) return;
        XposedBridge.log(Utils.TAG + ": Hooking " + lp.packageName + " quick setting");
        hookSystemReady(lp);

        try {
            final Class<?> adapterClass = XposedHelpers.findClass("com.picovr.quicksettings.ButtonListAdapter", lp.classLoader);

            // Hook onBindViewHolder to configure our custom tile
            XposedHelpers.findAndHookMethod(adapterClass, "onBindViewHolder",
                    "androidx.recyclerview.widget.RecyclerView$ViewHolder", int.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam p) {
                            try {
                                List<?> data = (List<?>) XposedHelpers.getObjectField(p.thisObject, "a");
                                int position = (Integer) p.args[1];
                                if (position >= 0 && position < data.size()) {
                                    Object item = data.get(position);
                                    if (buttonType(item) == NEVER_SLEEP_TILE) {
                                        configureButton(p.thisObject, p.args[0], position);
                                        p.setResult(null);
                                    }
                                }
                            } catch (Throwable t) {
                                XposedBridge.log(Utils.TAG + ": onBindViewHolder hook error: " + t);
                            }
                        }
                    });

            // Normalize the input before the adapter publishes it to RecyclerView.
            XposedHelpers.findAndHookMethod(adapterClass, "b", ArrayList.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    normalizeRuntimeTiles((ArrayList) p.args[0]);
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
            installEditorHooks(lp.classLoader);

            XposedBridge.log(Utils.TAG + ": Hooks installed successfully");
        } catch (Throwable t) {
            XposedBridge.log(Utils.TAG + ": Failed to install hooks: " + t);
        }
    }

    private static void installEditorHooks(final ClassLoader cl) {
        try {
            final Class<?> fragment = XposedHelpers.findClass("com.picovr.fragments.QuickPanelFragment", cl);
            final Class<?> manager = XposedHelpers.findClass("com.picovr.database.quickpanel.QuickPanelManager", cl);
            final Class<?> callback = XposedHelpers.findClass("com.picovr.listener.ResultCallback", cl);
            final Class<?> added = XposedHelpers.findClass("com.picovr.adapters.QuickPanelAddedAdapter", cl);
            final Class<?> more = XposedHelpers.findClass("com.picovr.adapters.QuickPanelMoreAdapter", cl);
            XposedHelpers.findAndHookMethod(manager, "A", List.class, callback, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    p.args[0] = saveEditorTile((List<?>) p.args[0]);
                }
            });
            XposedHelpers.findAndHookMethod(fragment, "I", List.class, List.class, boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    addEditorTile((List<?>) p.args[0], (List<?>) p.args[1], cl);
                }
            });
            XC_MethodHook bind = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    bindEditorTile(p.thisObject, p.args[0], (Integer) p.args[1]);
                }
            };
            XposedHelpers.findAndHookMethod(added, "m", XposedHelpers.findClass("com.picovr.adapters.QuickPanelAddedAdapter$AddedHolder", cl), int.class, bind);
            XposedHelpers.findAndHookMethod(more, "c", XposedHelpers.findClass("com.picovr.adapters.QuickPanelMoreAdapter$MoreHolder", cl), int.class, bind);
            XposedBridge.log(Utils.TAG + ": editor hooks installed");
        } catch (Throwable t) {
            XposedBridge.log(Utils.TAG + ": editor hooks unavailable " + t);
        }
    }

    private static Object currentApplication() throws Exception {
        return Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
    }

    private static int panelType(Object item) {
        try {
            return ((Integer) item.getClass().getMethod("f").invoke(item)).intValue();
        } catch (Throwable t) {
            return -1;
        }
    }

    private static int panelIndex(Object item) {
        try {
            return ((Integer) item.getClass().getMethod("d").invoke(item)).intValue();
        } catch (Throwable t) {
            return -1;
        }
    }

    private static boolean panelAdded(Object item) {
        try {
            return ((Boolean) item.getClass().getMethod("g").invoke(item)).booleanValue();
        } catch (Throwable t) {
            return false;
        }
    }

    private static int panelName(Object item) {
        try {
            return ((Integer) item.getClass().getMethod("e").invoke(item)).intValue();
        } catch (Throwable t) {
            return 0;
        }
    }

    private static int panelIcon(Object item) {
        try {
            return ((Integer) item.getClass().getMethod("b").invoke(item)).intValue();
        } catch (Throwable t) {
            return 0;
        }
    }

    private static void addEditorTile(List<?> added, List<?> more, ClassLoader cl) {
        try {
            Object context = currentApplication();
            int state = Utils.GetGlobalInt((Context) context, TILE_ADDED_KEY, 1);
            List target = state == 1 ? added : more;
            for (Object item : added) if (panelType(item) == NEVER_SLEEP_TILE) return;
            for (Object item : more) if (panelType(item) == NEVER_SLEEP_TILE) return;
            Object template = !added.isEmpty() ? added.get(0) : (more.isEmpty() ? null : more.get(0));
            if (template == null) return;
            Class<?> itemClass = XposedHelpers.findClass("com.picovr.database.quickpanel.QuickPanelItem", cl);
            Constructor<?> c = itemClass.getConstructor(int.class, int.class, int.class, int.class, int.class, String.class);
            int index = clampIndex(Utils.GetGlobalInt((Context) context, TILE_INDEX_KEY, target.size()), target.size());
            target.add(index, c.newInstance(NEVER_SLEEP_TILE, index, state, panelName(template), panelIcon(template), "neversleep"));
            XposedBridge.log(Utils.TAG + ": editor tile added state=" + state + " index=" + index + " addedSize=" + added.size() + " moreSize=" + more.size());
        } catch (Throwable t) {
            XposedBridge.log(Utils.TAG + ": editor tile injection failed " + t);
        }
    }

    private static List<?> saveEditorTile(List<?> list) {
        try {
            Object context = currentApplication();
            ArrayList copy = new ArrayList();
            boolean isAdded = false;
            if (list != null) {
                for (Object item : list) {
                    if (panelType(item) == NEVER_SLEEP_TILE) {
                        isAdded = panelAdded(item);
                        Utils.PutGlobalInt((Context) context, TILE_ADDED_KEY, isAdded ? 1 : 0);
                        int savedIndex = list.indexOf(item);
                        if (savedIndex < 0) savedIndex = panelIndex(item);
                        if (savedIndex < 0) savedIndex = 0;
                        Utils.PutGlobalInt((Context) context, TILE_INDEX_KEY, savedIndex);
                        XposedBridge.log(Utils.TAG + ": saved editable tile state added=" + (isAdded ? 1 : 0) + " index=" + savedIndex);
                    } else {
                        copy.add(item);
                    }
                }
            }
            if (!isAdded && context != null) {
                Utils.PutGlobalInt((Context) context, TILE_ADDED_KEY, 0);
                XposedBridge.log(Utils.TAG + ": saved editable tile state added=0");
            }
            return copy;
        } catch (Throwable t) {
            ArrayList filtered = new ArrayList();
            if (list != null) {
                for (Object item : list) {
                    if (panelType(item) != NEVER_SLEEP_TILE) filtered.add(item);
                }
            }
            XposedBridge.log(Utils.TAG + ": editable tile save failed; filtered virtual tile: " + t);
            return filtered;
        }
    }

    private static void bindEditorTile(Object adapter, Object holder, int position) {
        try {
            Field data = adapter.getClass().getDeclaredField("b");
            data.setAccessible(true);
            List<?> list = (List<?>) data.get(adapter);
            if (position < 0 || position >= list.size() || panelType(list.get(position)) != NEVER_SLEEP_TILE)
                return;
            boolean isAdded = holder.getClass().getName().contains("Added");
            Field text = holder.getClass().getDeclaredField(isAdded ? "d" : "c");
            text.setAccessible(true);
            Object label = text.get(holder);
            label.getClass().getMethod("setText", CharSequence.class).invoke(label, "Never Sleep");
            Field image = holder.getClass().getDeclaredField(isAdded ? "c" : "b");
            image.setAccessible(true);
            Object imageView = image.get(holder);
            Drawable drawable = getEditorModuleDrawable((Context) currentApplication());
            if (drawable != null) {
                imageView.getClass().getMethod("setImageDrawable", Drawable.class).invoke(imageView, drawable);
                imageView.getClass().getMethod("setScaleType", ImageView.ScaleType.class).invoke(imageView, ImageView.ScaleType.CENTER_INSIDE);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void hookSystemReady(XC_LoadPackage.LoadPackageParam lp) {
        try {
            Class<?> appClass = XposedHelpers.findClassIfExists("com.picovr.settings.SettingApplication", lp.classLoader);
            if (appClass == null) {
                appClass = XposedHelpers.findClass("android.app.Application", lp.classLoader);
            }
            XposedHelpers.findAndHookMethod(appClass, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    final Context context = (Context) param.thisObject;
                    XposedBridge.log(Utils.TAG + ": SettingApplication onCreate reached, syncing props");
                    syncProps(context);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(Utils.TAG + ": Failed to hook Application onCreate: " + t);
        }
    }

    private static Object quickPanelCallback(final Object original, Class<?> callbackClass, final ClassLoader cl) {
        return Proxy.newProxyInstance(cl, new Class<?>[]{callbackClass}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if ("a".equals(method.getName()) && args != null && args.length == 1) {
                    ArrayList<Object> list = (ArrayList<Object>) args[0];
                    Context context = null;
                    try {
                        context = (Context) currentApplication();
                    } catch (Throwable ignored) {
                    }
                    if (context != null) {
                        if (list != null) {
                            boolean seen = false;
                            for (int i = list.size() - 1; i >= 0; i--) {
                                Object item = list.get(i);
                                if (item != null && buttonType(item) == NEVER_SLEEP_TILE) {
                                    if (!seen) seen = true;
                                    else list.remove(i);
                                }
                            }
                        }

                        if (Utils.GetGlobalInt(context, TILE_ADDED_KEY, 1) == 1 && !hasTile(list, NEVER_SLEEP_TILE)) {
                            try {
                                Class<?> infoClass = XposedHelpers.findClass("com.picovr.quicksettings.button.QuickSettingButtonInfo", cl);
                                Object item = XposedHelpers.newInstance(infoClass);
                                XposedHelpers.callMethod(item, "m", NEVER_SLEEP_TILE);
                                int index = clampIndex(Utils.GetGlobalInt(context, TILE_INDEX_KEY, 0), list.size());
                                list.add(index, item);
                            } catch (Throwable t) {
                                XposedBridge.log(Utils.TAG + ": Tile injection failed: " + t);
                            }
                        }
                    }
                }
                return method.invoke(original, args);
            }
        });
    }

    private static boolean hasTile(List<?> list, int type) {
        if (list == null) return false;
        for (Object item : list) {
            if (item == null) continue;
            try {
                Object res = XposedHelpers.callMethod(item, "f");
                if (res instanceof Integer && ((Integer) res) == type) return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static void normalizeRuntimeTiles(ArrayList list) {
        if (list == null) return;
        try {
            Object tile = null;
            for (int i = list.size() - 1; i >= 0; i--) {
                if (buttonType(list.get(i)) == NEVER_SLEEP_TILE) {
                    if (tile == null) tile = list.get(i);
                    else list.remove(i);
                }
            }
            if (tile == null) return;
            Context context = (Context) currentApplication();
            int current = list.indexOf(tile);
            int wanted = TilePositionPolicy.normalizedIndex(
                    current,
                    Utils.GetGlobalInt(context, TILE_INDEX_KEY, 0),
                    list.size());
            if (current != wanted) {
                list.remove(tile);
                list.add(wanted, tile);
            }
            XposedBridge.log(Utils.TAG + ": runtime tile normalized=" + list.indexOf(tile));
        } catch (Throwable t) {
            XposedBridge.log(Utils.TAG + ": runtime tile normalization failed: " + t);
        }
    }

    private static int clampIndex(int index, int size) {
        return Math.max(0, Math.min(index, Math.max(0, size)));
    }

    private static int buttonType(Object info) {
        try {
            return ((Integer) XposedHelpers.callMethod(info, "f")).intValue();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static void configureButton(Object adapter, Object holder, int position) {
        try {
            List<?> data = (List<?>) XposedHelpers.getObjectField(adapter, "a");
            if (position < 0 || position >= data.size()) return;
            Object item = data.get(position);
            if (buttonType(item) != NEVER_SLEEP_TILE) return;
            XposedBridge.log(Utils.TAG + ": binding quick tile position=" + position + " type=" + buttonType(item));

            final View button = (View) XposedHelpers.getObjectField(holder, "a");
            final Context context = button.getContext();

            try {
                Object mode = XposedHelpers.callMethod(adapter, "a");
                XposedHelpers.callMethod(button, "i", mode);
            } catch (Throwable ignored) {
            }

            View.OnClickListener listener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final boolean nextState = !isNeverSleepEnabled(context);
                    refreshTile(context, nextState);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            toggleNeverSleep(context, nextState);
                        }
                    }).start();
                }
            };
            button.setOnClickListener(listener);
            button.setClickable(true);
            View itemView = (View) XposedHelpers.getObjectField(holder, "itemView");
            itemView.setOnClickListener(listener);
            itemView.setClickable(true);
            sButton = button;
            XposedBridge.log(Utils.TAG + ": quick tile configured position=" + position);
            button.post(new Runnable() {
                @Override
                public void run() {
                    refreshTile(context, null);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(Utils.TAG + ": configureButton failed: " + t);
        }
    }

    private static void toggleNeverSleep(Context context, boolean targetEnabled) {
        if (targetEnabled) {
            boolean vsleepActive = Utils.GetGlobalInt(context, VSLEEP_ENABLED_KEY, 0) == 1
                    && "vsleep".equals(Utils.GetGlobalString(context, COORD_OWNER))
                    && "active".equals(Utils.GetGlobalString(context, COORD_PHASE));
            Utils.PutGlobalInt(context, VSLEEP_WAS_ENABLED_KEY, vsleepActive ? 1 : 0);
            requestPowerOwner(context, "enable");
            Utils.PutGlobalInt(context, Utils.SETTING_NEVER_SLEEP, 1);
            Utils.SetProp(Utils.PROP_NEVER_SLEEP_VOLATILE, "1");
            XposedBridge.log(Utils.TAG + ": Never Sleep enabled; previous V-Sleep=" + vsleepActive);
            Utils.StartGuardianDaemon(context);
        } else {
            Utils.PutGlobalInt(context, Utils.SETTING_NEVER_SLEEP, 0);
            Utils.SetProp(Utils.PROP_NEVER_SLEEP_VOLATILE, "0");
            requestPowerOwner(context, "disable");
            XposedBridge.log(Utils.TAG + ": Never Sleep disabled; released power owner");
            Utils.StopGuardianDaemon();
            setDisplayPowerState(true);
        }
        PlayBeepSound();
    }

    private static void setDisplayPowerState(boolean on) {
        if (Build.VERSION.SDK_INT != 29) {
            XposedBridge.log(Utils.TAG + ": setDisplayPowerState skipped, not Android 10 (SDK 29)");
            return;
        }
        try {
            Class<?> surfaceControl = Class.forName("android.view.SurfaceControl");
            IBinder token = null;

            try {
                Method getBuiltInDisplay = surfaceControl.getMethod("getBuiltInDisplay", int.class);
                token = (IBinder) getBuiltInDisplay.invoke(null, 0);
            } catch (Throwable ignored) {
            }

            if (token == null) {
                try {
                    Method getInternalToken = surfaceControl.getMethod("getInternalDisplayToken");
                    token = (IBinder) getInternalToken.invoke(null);
                } catch (Throwable ignored) {
                }
            }

            if (token != null) {
                Method setDisplayPowerMode = surfaceControl.getMethod("setDisplayPowerMode", IBinder.class, int.class);
                int mode = on ? 2 : 0;
                setDisplayPowerMode.invoke(null, token, mode);
                XposedBridge.log(Utils.TAG + ": setDisplayPowerMode invoked successfully, mode=" + mode);
            } else {
                XposedBridge.log(Utils.TAG + ": Could not obtain display token via reflection");
            }
        } catch (Throwable t) {
            XposedBridge.log(Utils.TAG + ": setDisplayPowerState failed: " + t);
        }
    }

    private static void requestPowerOwner(Context context, String payload) {
        String request = "2|" + UUID.randomUUID().toString() + "|power|" + payload;
        Utils.PutGlobalString(context, COORD_REQUEST, request);
        long deadline = System.currentTimeMillis() + 1500L;
        while (System.currentTimeMillis() < deadline) {
            if (request.equals(Utils.GetGlobalString(context, COORD_ACK))) return;
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        XposedBridge.log(Utils.TAG + ": power request not acknowledged immediately: " + payload);
    }

    private static void syncProps(Context context) {
        try {
            int enabled = Utils.GetGlobalInt(context, Utils.SETTING_NEVER_SLEEP, 0);
            String val = String.valueOf(enabled);
            String volatileVal = Utils.GetProp(Utils.PROP_NEVER_SLEEP_VOLATILE, "0");
            if (!val.equals(volatileVal)) {
                Utils.SetProp(Utils.PROP_NEVER_SLEEP_VOLATILE, val);
                XposedBridge.log(Utils.TAG + ": Synced volatile prop to " + val);
            }
            if (isNeverSleepEnabled(context)) {
                Utils.StartGuardianDaemon(context);
            } else {
                Utils.StopGuardianDaemon();
            }
        } catch (Throwable t) {
            XposedBridge.log(Utils.TAG + ": syncProps failed: " + t);
        }
    }
    
    private static boolean isNeverSleepEnabled(Context context) {
        return Utils.GetGlobalInt(context, Utils.SETTING_NEVER_SLEEP, 0) == 1;
    }

    private static void refreshTile(final Context context, final Boolean forcedState) {
        try {
            final Object button = sButton;
            if (button == null) return;

            final boolean enabled = (forcedState != null) ? forcedState : isNeverSleepEnabled(context);

            Runnable updateTask = new Runnable() {
                @Override
                public void run() {
                    try {
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
                                    lp.width = (int) (55 * density);
                                    lp.height = (int) (55 * density);
                                    iconView.setLayoutParams(lp);
                                }
                                iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                            }
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(Utils.TAG + ": refreshTile UI update failed: " + t);
                    }
                }
            };

            if (Looper.myLooper() == Looper.getMainLooper()) {
                updateTask.run();
            } else {
                ((View) button).post(updateTask);
            }
        } catch (Throwable t) {
            XposedBridge.log(Utils.TAG + ": refreshTile failed: " + t);
        }
    }

    private static ImageView findImageView(View view) {
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

    private static Drawable getModuleDrawable(Context context) {
        try {
            Context moduleContext = context.createPackageContext(Utils.MODULE_PACKAGE, Context.CONTEXT_IGNORE_SECURITY);
            int id = moduleContext.getResources().getIdentifier("ic_launcher_foreground", "mipmap", Utils.MODULE_PACKAGE);
            if (id == 0)
                id = moduleContext.getResources().getIdentifier("ic_launcher", "mipmap", Utils.MODULE_PACKAGE);
            if (id == 0)
                id = moduleContext.getResources().getIdentifier("ic_launcher", "drawable", Utils.MODULE_PACKAGE);
            if (id != 0) return moduleContext.getResources().getDrawable(id, null);
        } catch (Throwable t) {
            XposedBridge.log(Utils.TAG + ": Failed to load module icon: " + t);
        }
        return null;
    }

    private static Drawable getEditorModuleDrawable(Context context) {
        Drawable drawable = getModuleDrawable(context);
        if (drawable != null && Build.VERSION.SDK_INT >= 21) {
            return new android.graphics.drawable.InsetDrawable(drawable, -0.25f, -0.25f, -0.25f, -0.25f);
        }
        return drawable;
    }

    private static String getModuleString(Context context, String name) {
        try {
            Context moduleContext = context.createPackageContext(Utils.MODULE_PACKAGE, Context.CONTEXT_IGNORE_SECURITY);
            int id = moduleContext.getResources().getIdentifier(name, "string", Utils.MODULE_PACKAGE);
            if (id != 0) return moduleContext.getString(id);
        } catch (Throwable t) {
            XposedBridge.log(Utils.TAG + ": Failed to load module string: " + t);
        }
        return "Never Sleep";
    }
}
