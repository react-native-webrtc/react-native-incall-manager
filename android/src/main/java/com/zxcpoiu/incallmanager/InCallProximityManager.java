/*
 * Copyright (c) 2017 Henry Lin @zxcpoiu
 * 
 * Permission to use, copy, modify, and distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package com.zxcpoiu.incallmanager;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.Runnable;

import com.facebook.react.bridge.UiThreadUtil;

import com.zxcpoiu.incallmanager.AppRTC.AppRTCProximitySensor;

public class InCallProximityManager {
    private static final String TAG = "InCallProximityManager";

    private WakeLock mProximityLock = null;
    private Method mPowerManagerRelease;
    private boolean proximitySupported = false;
    private AppRTCProximitySensor proximitySensor = null;

    /** Construction */
    static InCallProximityManager create(Context context, final InCallManagerModule inCallManager) {
        return new InCallProximityManager(context, inCallManager);
    }

    private InCallProximityManager(Context context, final InCallManagerModule inCallManager) {
        Log.d(TAG, "InCallProximityManager");
        checkProximitySupport(context);
        if (proximitySupported) {
            UiThreadUtil.runOnUiThread(() -> {
                proximitySensor = AppRTCProximitySensor.create(context, () -> {
                    inCallManager.onProximitySensorChangedState(proximitySensor.sensorReportsNearState());               
                });
            });
        }
    }

    private void checkProximitySupport(Context context) {
        SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY) == null) {
            proximitySupported = false;
            return;
        }

        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

        proximitySupported = true;

        // --- Check if PROXIMITY_SCREEN_OFF_WAKE_LOCK is implemented.
        try {
            boolean _proximitySupported = false;
            Field field = PowerManager.class.getDeclaredField("PROXIMITY_SCREEN_OFF_WAKE_LOCK");
            int proximityScreenOffWakeLock = (Integer) field.get(null);

            if (android.os.Build.VERSION.SDK_INT < 17) {
                Method method = powerManager.getClass().getDeclaredMethod("getSupportedWakeLockFlags");
                int powerManagerSupportedFlags = (Integer) method.invoke(powerManager);
                _proximitySupported = ((powerManagerSupportedFlags & proximityScreenOffWakeLock) != 0x0);
            } else {
                // --- android 4.2+
                Method method = powerManager.getClass().getDeclaredMethod("isWakeLockLevelSupported", int.class);
                _proximitySupported = (Boolean) method.invoke(powerManager, proximityScreenOffWakeLock);
            }

            if (_proximitySupported) {
                mProximityLock = powerManager.newWakeLock(proximityScreenOffWakeLock, TAG);
                mProximityLock.setReferenceCounted(false);
            }
        } catch (Exception e) {
            Log.d(TAG, "Failed to get proximity screen locker. exception: ", e);
        }

        if (mProximityLock != null) {
            Log.d(TAG, "use native screen locker...");
            try {
                mPowerManagerRelease = mProximityLock.getClass().getDeclaredMethod("release", int.class);
            } catch (Exception e) {
                Log.d(TAG, "failed to get proximity screen locker: `release()`. exception: ", e);
            }
        } else {
            Log.d(TAG, "fallback to old school screen locker...");
        }
    }

    public boolean start() {
        if (!proximitySupported) {
            return false;
        }
        UiThreadUtil.runOnUiThread(() -> {
            proximitySensor.start();
        });
        return true;
    }

    public void stop() {
        UiThreadUtil.runOnUiThread(() -> {
            proximitySensor.stop();
        });
    }

    public boolean isProximitySupported() {
        return proximitySupported;
    }

    public boolean isProximityWakeLockSupported() {
        return mProximityLock != null;
    }

    public boolean getProximityIsNear() {
        return (proximitySupported) ? proximitySensor.sensorReportsNearState() : false;
    }

    public void acquireProximityWakeLock() {
        if (!isProximityWakeLockSupported()) {
            return;
        }
        synchronized (mProximityLock) {
            if (!mProximityLock.isHeld()) {
                Log.d(TAG, "acquireProximityWakeLock()");
                mProximityLock.acquire();
            }
        }
    }

    public void releaseProximityWakeLock(final boolean waitForNoProximity) {
        if (!isProximityWakeLockSupported()) {
            return;
        }
        synchronized (mProximityLock) {
            if (mProximityLock.isHeld()) {
                try {
                    int flags = waitForNoProximity ? PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY : 0;
                    mPowerManagerRelease.invoke(mProximityLock, flags);
                    Log.d(TAG, "releaseProximityWakeLock()");
                } catch (Exception e) {
                    Log.e(TAG, "failed to release proximity lock. e: ", e);
                }
            }
        }
    }
}
