package com.hamer.piconeversleep;

import android.os.IBinder;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Method;

public class GuardianDaemon {
    private static final String TAG = "PicoNeverSleepDaemon";

    private static volatile boolean sDisplayPowerOn = true;
    private static Method sSystemPropGetMethod;
    private static IBinder sDisplayToken;
    private static Method sSetDisplayPowerModeMethod;

    static {
        try {
            Class<?> spClazz = Class.forName("android.os.SystemProperties");
            sSystemPropGetMethod = spClazz.getMethod("get", String.class, String.class);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to resolve SystemProperties.get: " + t);
        }

        try {
            Class<?> scClazz = Class.forName("android.view.SurfaceControl");
            try {
                Method getBuiltInDisplay = scClazz.getMethod("getBuiltInDisplay", int.class);
                sDisplayToken = (IBinder) getBuiltInDisplay.invoke(null, 0);
            } catch (Throwable ignored) {
            }
            if (sDisplayToken == null) {
                try {
                    Method getInternalToken = scClazz.getMethod("getInternalDisplayToken");
                    sDisplayToken = (IBinder) getInternalToken.invoke(null);
                } catch (Throwable ignored) {
                }
            }
            sSetDisplayPowerModeMethod = scClazz.getMethod("setDisplayPowerMode", IBinder.class, int.class);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to resolve SurfaceControl methods: " + t);
        }
    }

    public static void main(String[] args) {
        try {
            long sensorTime = 0;
            int myPid = android.os.Process.myPid();
            Log.i(TAG, "GuardianDaemon starting (PID " + myPid + ")...");

            // Kill any stale GuardianDaemon instances
            execCmd("for p in $(pgrep -f 'com.hamer.piconeversleep.GuardianDaemon'); do if [ \"$p\" != \"" + myPid + "\" ]; then kill -9 $p; fi; done || true");

            // Assign daemon to background cpuset to conserve CPU performance cores for VR apps
            setupGuardianDaemonCpuset(myPid);

            while (true) {
                try {
                    boolean sensorDetected = "0".equals(getSystemProperty("sys.pxr.psensor.status", "1"));

                    if (!sensorDetected) {
                        if (sensorTime == 0) {
                            sensorTime = System.currentTimeMillis();
                        }

                        String delayStr = getSystemProperty("persist.psensor.screenoff.delay", "0");
                        long delayMs = Long.parseLong(delayStr) * 1000L;
                        if (delayMs < 0) delayMs = 0;

                        boolean shouldTurnOff = "65535".equals(delayStr) || (System.currentTimeMillis() - sensorTime >= delayMs);
                        if (sDisplayPowerOn && shouldTurnOff) {
                            if (!"65535".equals(delayStr)) {
                                setDisplayPowerState(false);
                            }
                        }
                    } else {
                        sensorTime = 0;
                        if (!sDisplayPowerOn) {
                            setDisplayPowerState(true);
                        }
                    }

                    Thread.sleep(500L);
                } catch (InterruptedException e) {
                    break;
                } catch (Throwable t) {
                    Log.e(TAG, "Error in GuardianDaemon loop: " + t);
                    try {
                        Thread.sleep(2000L);
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            try {
                File f = new File("/data/local/tmp/daemon_error.txt");
                FileWriter fw = new FileWriter(f, true);
                t.printStackTrace(new java.io.PrintWriter(fw));
                fw.close();
            } catch (Throwable ignored) {
            }
            t.printStackTrace();
        }
        Log.i(TAG, "GuardianDaemon stopped");
    }

    private static void setupGuardianDaemonCpuset(int myPid) {
        try {
            File bgCgroup = new File("/dev/cpuset/background/cgroup.procs");
            if (bgCgroup.exists()) {
                try (FileWriter writer = new FileWriter(bgCgroup)) {
                    writer.write(String.valueOf(myPid));
                }
                Log.i(TAG, "Assigned GuardianDaemon to /dev/cpuset/background (PID " + myPid + ")");
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to assign to background cpuset: " + t);
        }
    }

    private static String getSystemProperty(String key, String def) {
        if (sSystemPropGetMethod != null) {
            try {
                return (String) sSystemPropGetMethod.invoke(null, key, def);
            } catch (Throwable ignored) {
            }
        }
        return def;
    }

    private static void setDisplayPowerState(boolean on) {
        if (sSetDisplayPowerModeMethod != null && sDisplayToken != null) {
            try {
                int mode = on ? 2 : 0; // 2 = NORMAL, 0 = OFF
                sSetDisplayPowerModeMethod.invoke(null, sDisplayToken, mode);
                sDisplayPowerOn = on;
                Log.i(TAG, "setDisplayPowerMode: " + mode);
            } catch (Throwable t) {
                Log.e(TAG, "setDisplayPowerState failed: " + t);
            }
        }
    }

    private static void execCmd(String command) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            process.waitFor();
        } catch (Throwable ignored) {
        }
    }
}
