package com.zxcpoiu.incallmanager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Build;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;

import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.UiThreadUtil;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

public class InCallManagerModule extends ReactContextBaseJavaModule implements LifecycleEventListener {
    private static final String REACT_NATIVE_MODULE_NAME = "InCallManager";
    private static final String TAG = REACT_NATIVE_MODULE_NAME;
    private static ReactApplicationContext reactContext;

    // --- Screen Manager
    private PowerManager mPowerManager;
    private WakeLock mPartialLock = null;
    private WakeLock mProximityLock = null;
    private Method mPowerManagerRelease;
    private WindowManager.LayoutParams lastLayoutParams;

    // --- AudioRouteManager
    private AudioManager audioManager;
    private boolean audioManagerInitialized = false;
    private boolean isAudioFocused = false;
    private boolean origIsSpeakerPhoneOn = false;
    private boolean origIsMicrophoneMute = false;
    private int origAudioMode = AudioManager.MODE_INVALID;
    private boolean defaultSpeakerOn = false;
    private int defaultAudioMode = AudioManager.MODE_IN_COMMUNICATION;
    private boolean forceSpeakerOn = false;
    private boolean automatic = true;
    private boolean isProximitySupported = false;
    private boolean isProximityRegistered = false;
    private boolean proximityIsNear = false;
    private static final String ACTION_HEADSET_PLUG = (android.os.Build.VERSION.SDK_INT >= 21) ? AudioManager.ACTION_HEADSET_PLUG : Intent.ACTION_HEADSET_PLUG;
    private BroadcastReceiver wiredHeadsetReceiver;
    private BroadcastReceiver noisyAudioReceiver;
    private BroadcastReceiver mediaButtonReceiver;
    private SensorManager mSensorManager;
    private Sensor proximitySensor;
    private SensorEventListener proximitySensorEventListener;

    @Override
    public String getName() {
        return REACT_NATIVE_MODULE_NAME;
    }

    public InCallManagerModule(ReactApplicationContext _reactContext) {
        super(_reactContext);
        reactContext = _reactContext;
        reactContext.addLifecycleEventListener(this);
        mPowerManager = (PowerManager) reactContext.getSystemService(Context.POWER_SERVICE);
        mPartialLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        mPartialLock.setReferenceCounted(false);
        audioManager = ((AudioManager) reactContext.getSystemService(Context.AUDIO_SERVICE));
        mSensorManager = (SensorManager)reactContext.getSystemService(Context.SENSOR_SERVICE);
        proximitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        checkProximitySupport();

        Log.d(TAG, "InCallManager initialized");
    }

    private void checkProximitySupport() {
        if (proximitySensor != null) {
            isProximitySupported = true;
            initProximitySensorEventListener();
        }

        // --- Check if PROXIMITY_SCREEN_OFF_WAKE_LOCK is implemented.
        try {
            boolean _isProximitySupported = false;
            Field field = PowerManager.class.getDeclaredField("PROXIMITY_SCREEN_OFF_WAKE_LOCK");
            int proximityScreenOffWakeLock = (Integer) field.get(null);
            if (android.os.Build.VERSION.SDK_INT < 17) {
                Method method = mPowerManager.getClass().getDeclaredMethod("getSupportedWakeLockFlags");
                int powerManagerSupportedFlags = (Integer) method.invoke(mPowerManager);
                _isProximitySupported = ((powerManagerSupportedFlags & proximityScreenOffWakeLock) != 0x0);
            } else {
                // --- android 4.2+
                Method method = mPowerManager.getClass().getDeclaredMethod("isWakeLockLevelSupported", int.class);
                _isProximitySupported = (Boolean) method.invoke(mPowerManager, proximityScreenOffWakeLock);
            }
            if (_isProximitySupported) {
                mProximityLock = mPowerManager.newWakeLock(proximityScreenOffWakeLock, TAG);
                mProximityLock.setReferenceCounted(false);
            }
        } catch (Exception e) {
            Log.d(TAG, "Failed to get proximity screen locker.");
        }
        if (mProximityLock != null) {
            Log.d(TAG, "Using native screen locker...");
            try {
                mPowerManagerRelease = mProximityLock.getClass().getDeclaredMethod("release", int.class);
            } catch (Exception e) {
                Log.d(TAG, "Failed to get proximity screen locker release().");
            }
        } else {
            Log.d(TAG, "fallback to old school screen locker...");
        }
    }

    private boolean isProximityWakeLockSupported() {
        return mProximityLock != null;
    }

    private boolean getProximityIsNear() {
        return proximityIsNear;
    }

    private void acquireProximityWakeLock() {
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

    private void releaseProximityWakeLock(final boolean waitForNoProximity) {
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
                    Log.e(TAG, "failed to release proximity lock");
                }
            }
        }
    }

    private boolean acquirePartialWakeLock() {
        synchronized (mPartialLock) {
            if (!mPartialLock.isHeld()) {
                Log.d(TAG, "acquirePartialWakeLock()");
                mPartialLock.acquire();
                return true;
            }
        }
        return false;
    }

    private boolean releasePartialWakeLock() {
        synchronized (mPartialLock) {
            if (mPartialLock.isHeld()) {
                Log.d(TAG, "releasePartialWakeLock()");
                mPartialLock.release();
                return true;
            }
        }
        return false;
    }

    private void manualTurnScreenOff() {
        Log.d(TAG, "manualTurnScreenOff()");
        if (!acquirePartialWakeLock()) {
            return;
        }
        UiThreadUtil.runOnUiThread(new Runnable() {
            public void run() {
                Activity mCurrentActivity = getCurrentActivity();
                if (mCurrentActivity == null) {
                    Log.d(TAG, "ReactContext doesn't hava any Activity attached.");
                    return;
                }
                Window window = mCurrentActivity.getWindow();
                WindowManager.LayoutParams params = window.getAttributes();
                lastLayoutParams = params; // --- store last param
                params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF; // --- Dim as dark as possible. see BRIGHTNESS_OVERRIDE_OFF
                window.setAttributes(params);
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
    }

    private void manualTurnScreenOn() {
        Log.d(TAG, "manualTurnScreenOn()");
        if (!releasePartialWakeLock()) {
            return;
        }
        UiThreadUtil.runOnUiThread(new Runnable() {
            public void run() {
                Activity mCurrentActivity = getCurrentActivity();
                if (mCurrentActivity == null) {
                    Log.d(TAG, "ReactContext doesn't hava any Activity attached.");
                    return;
                }
                Window window = mCurrentActivity.getWindow();
                if (lastLayoutParams != null) {
                    window.setAttributes(lastLayoutParams);
                } else {
                    WindowManager.LayoutParams params = window.getAttributes();
                    params.screenBrightness = -1; // --- Dim to preferable one
                    window.setAttributes(params);
                }
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
    }

    private void storeOriginalAudioSetup() {
        Log.d(TAG, "storeOriginalAudioSetup()");
        origAudioMode = audioManager.getMode();
        origIsSpeakerPhoneOn = audioManager.isSpeakerphoneOn();
        origIsMicrophoneMute = audioManager.isMicrophoneMute();
    }

    private void restoreOriginalAudioSetup() {
        Log.d(TAG, "restoreOriginalAudioSetup()");
        setSpeakerphoneOn(origIsSpeakerPhoneOn);
        setMicrophoneMute(origIsMicrophoneMute);
        audioManager.setMode(origAudioMode);
    }

    private void startWiredHeadsetEvent() {
        if (wiredHeadsetReceiver == null) {
            Log.d(TAG, "startWiredHeadsetEvent()");
            IntentFilter filter = new IntentFilter(ACTION_HEADSET_PLUG);
            wiredHeadsetReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (ACTION_HEADSET_PLUG.equals(intent.getAction())) {
                        if (automatic) {
                            updateAudioRoute();
                        }
                        String deviceName = intent.getStringExtra("name");
                        if (deviceName == null) {
                            deviceName = "";
                        }
                        WritableMap data = Arguments.createMap();
                        data.putBoolean("isPlugged", (intent.getIntExtra("state", 0) == 1) ? true : false);
                        data.putBoolean("hasMic", (intent.getIntExtra("microphone", 0) == 1) ? true : false);
                        data.putString("deviceName", deviceName);
                        sendEvent("WiredHeadset", data);
                    }
                }
            };
            reactContext.registerReceiver(wiredHeadsetReceiver, filter);
        }
    }

    private void stopWiredHeadsetEvent() {
        if (wiredHeadsetReceiver != null) {
            Log.d(TAG, "stopWiredHeadsetEvent()");
            reactContext.unregisterReceiver(wiredHeadsetReceiver);
            wiredHeadsetReceiver = null;
        }
    }

    private void startNoisyAudioEvent() {
        if (noisyAudioReceiver == null) {
            Log.d(TAG, "startNoisyAudioEvent()");
            IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
            noisyAudioReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                        if (automatic) {
                            updateAudioRoute();
                        }
                        sendEvent("NoisyAudio", null);
                    }
                }
            };
            reactContext.registerReceiver(noisyAudioReceiver, filter);
        }
    }

    private void stopNoisyAudioEvent() {
        if (noisyAudioReceiver != null) {
            Log.d(TAG, "stopNoisyAudioEvent()");
            reactContext.unregisterReceiver(noisyAudioReceiver);
            noisyAudioReceiver = null;
        }
    }

    private void startMediaButtonEvent() {
        if (mediaButtonReceiver == null) {
            Log.d(TAG, "startMediaButtonEvent()");
            IntentFilter filter = new IntentFilter(Intent.ACTION_MEDIA_BUTTON);
            mediaButtonReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
                        KeyEvent event = (KeyEvent)intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                        int keyCode = event.getKeyCode();
                        String keyText = "";
                        switch (keyCode) {
                            case KeyEvent.KEYCODE_MEDIA_PLAY:
                                keyText = "KEYCODE_MEDIA_PLAY";
                                break;
                            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                                keyText = "KEYCODE_MEDIA_PAUSE";
                                break;
                            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                                keyText = "KEYCODE_MEDIA_PLAY_PAUSE";
                                break;
                            case KeyEvent.KEYCODE_MEDIA_NEXT:
                                keyText = "KEYCODE_MEDIA_NEXT";
                                break;
                            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                                keyText = "KEYCODE_MEDIA_PREVIOUS";
                                break;
                            case KeyEvent.KEYCODE_MEDIA_CLOSE:
                                keyText = "KEYCODE_MEDIA_CLOSE";
                                break;
                            case KeyEvent.KEYCODE_MEDIA_EJECT:
                                keyText = "KEYCODE_MEDIA_EJECT";
                                break;
                            case KeyEvent.KEYCODE_MEDIA_RECORD:
                                keyText = "KEYCODE_MEDIA_RECORD";
                                break;
                            case KeyEvent.KEYCODE_MEDIA_STOP:
                                keyText = "KEYCODE_MEDIA_STOP";
                                break;
                            default:
                                keyText = "KEYCODE_UNKNOW";
                                break;
                        }
                        WritableMap data = Arguments.createMap();
                        data.putString("eventText", keyText);
                        data.putInt("eventCode", keyCode);
                        sendEvent("MediaButton", data);
                    }
                }
            };
            reactContext.registerReceiver(mediaButtonReceiver, filter);
        }
    }

    private void stopMediaButtonEvent() {
        if (mediaButtonReceiver != null) {
            Log.d(TAG, "stopMediaButtonEvent()");
            reactContext.unregisterReceiver(mediaButtonReceiver);
            mediaButtonReceiver = null;
        }
    }

    private void initProximitySensorEventListener() {
        if (proximitySensorEventListener == null) {
            Log.d(TAG, "initProximitySensorEventListener()");
            proximitySensorEventListener = new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent sensorEvent) {
                    if (sensorEvent.sensor.getType() == Sensor.TYPE_PROXIMITY) {
                        boolean isNear = false;
                        WritableMap data = Arguments.createMap();
                        if (sensorEvent.values[0] < proximitySensor.getMaximumRange()) {
                            isNear = true;
                        }
                        proximityIsNear = isNear;
                        if (automatic) {
                            updateAudioRoute();
                            if (isNear) {
                                turnScreenOff();
                            } else {
                                turnScreenOn();
                            }
                        }
                        data.putBoolean("isNear", isNear);
                        sendEvent("Proximity", data);
                    }
                }

                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {
                }
            };
        }
    }

    private void startProximitySensor() {
        if (!isProximitySupported) {
            Log.d(TAG, "Proximity Sensor is not supported.");
            return;
        }
        if (!isProximityRegistered) {
            Log.d(TAG, "startProximitySensor()");
            //SENSOR_DELAY_FASTEST(0 milisecs), SENSOR_DELAY_GAME(20 milisecs), SENSOR_DELAY_UI(60 milisecs), SENSOR_DELAY_NORMAL(200 milisecs)
            mSensorManager.registerListener(proximitySensorEventListener, proximitySensor, SensorManager.SENSOR_DELAY_UI);
            isProximityRegistered = true;
        }
    }

    private void stopProximitySensor() {
        if (!isProximitySupported) {
            Log.d(TAG, "Proximity Sensor is not supported.");
            return;
        }
        if (isProximityRegistered) {
            Log.d(TAG, "stopProximitySensor()");
            mSensorManager.unregisterListener(proximitySensorEventListener);
            isProximityRegistered = false;
        }
    }

    private static final class OnFocusChangeListener implements AudioManager.OnAudioFocusChangeListener {

        private static OnFocusChangeListener instance;

        protected static OnFocusChangeListener getInstance() {
            if (instance == null) {
                instance = new OnFocusChangeListener();
            }
            return instance;
        }

        @Override
        public void onAudioFocusChange(final int focusChange) {
            String focusChangeStr;
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    focusChangeStr = "AUDIOFOCUS_GAIN";
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    focusChangeStr = "AUDIOFOCUS_LOSS";
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    focusChangeStr = "AUDIOFOCUS_LOSS_TRANSIENT";
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    focusChangeStr = "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK";
                    break;
                default:
                    focusChangeStr = "AUDIOFOCUS_UNKNOW";
                    break;
            }
            Log.d(TAG, "onAudioFocusChange: " + focusChange + " - " + focusChangeStr);

            WritableMap data = Arguments.createMap();
            data.putString("eventText", focusChangeStr);
            data.putInt("eventCode", focusChange);
            sendEvent("onAudioFocusChange", data);
        }
    }

    /*
        // --- TODO: AudioDeviceCallBack android sdk 23+
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            private class MyAudioDeviceCallback extends AudioDeviceCallback {
                public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                    mAddCallbackCalled = true;
                }
                public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                    mRemoveCallbackCalled = true;
                }
            }

            // --- Specifies the Handler object for the thread on which to execute the callback. If null, the Handler associated with the main Looper will be used.
            public void test_deviceCallback() {
                AudioDeviceCallback callback =  new EmptyDeviceCallback();
                mAudioManager.registerAudioDeviceCallback(callback, null);
            }

            // --- get all audio devices by flags
            //public AudioDeviceInfo[] getDevices (int flags)
            //Returns an array of AudioDeviceInfo objects corresponding to the audio devices currently connected to the system and meeting the criteria specified in the flags parameter.
            //flags    int: A set of bitflags specifying the criteria to test.
        }

        // --- TODO: adjust valume if needed.
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            isVolumeFixed ()

            // The following APIs have no effect when volume is fixed:
            adjustVolume(int, int)
            adjustSuggestedStreamVolume(int, int, int)
            adjustStreamVolume(int, int, int)
            setStreamVolume(int, int, int)
            setRingerMode(int)
            setStreamSolo(int, boolean)
            setStreamMute(int, boolean)
        }

        // -- TODO: bluetooth support
    */

    private static void sendEvent(final String eventName, @Nullable WritableMap params) {
        try {
            reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
        } catch (RuntimeException e) {
            Log.e(TAG, "sendEvent(): java.lang.RuntimeException: Trying to invoke JS before CatalystInstance has been set!");
        }
    }

    @ReactMethod
    public void start(final String media, final boolean auto) {
        if (media.equals("video")) {
            defaultSpeakerOn = true;
        } else {
            defaultSpeakerOn = false;
        }
        automatic = auto;
        if (!audioManagerInitialized) {
            Log.d(TAG, "start audioRouteManager");
            storeOriginalAudioSetup();
            startEvents();
            // TODO: even if not acquired focus, we can still play sounds. but need figure out which is better.
            setMicrophoneMute(false);
            audioManager.setMode(defaultAudioMode);
            updateAudioRoute();
            audioManagerInitialized = true;
        }
    }

    @ReactMethod
    public void stop() {
        if (audioManagerInitialized) {
            Log.d(TAG, "stop audioRouteManager");
            stopEvents();
            restoreOriginalAudioSetup();
            audioManagerInitialized = false;
        }
    }

    private void pause() {
        if (audioManagerInitialized) {
            Log.d(TAG, "pause audioRouteManager");
            stopEvents();
        }
    }

    private void resume() {
        if (audioManagerInitialized) {
            Log.d(TAG, "resume audioRouteManager");
            startEvents();
        }
    }

    private void startEvents() {
        requestAudioFocus();
        startWiredHeadsetEvent();
        startNoisyAudioEvent();
        startMediaButtonEvent();
        if (!defaultSpeakerOn) {
            // video, default disable proximity
            startProximitySensor();
        }
        setKeepScreenOn(true);
    }

    private void stopEvents() {
        stopWiredHeadsetEvent();
        stopNoisyAudioEvent();
        stopMediaButtonEvent();
        stopProximitySensor();
        setKeepScreenOn(false);
        releaseAudioFocus();
    }

    private void requestAudioFocus() {
        if (!isAudioFocused) {
            int result = audioManager.requestAudioFocus(OnFocusChangeListener.getInstance(), AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.d(TAG, "AudioFocus granted");
                isAudioFocused = true;
            } else if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
                Log.d(TAG, "AudioFocus failed");
                isAudioFocused = false;
            }
        }
    }

    private void releaseAudioFocus() {
        if (isAudioFocused) {
            audioManager.abandonAudioFocus(null);
            isAudioFocused = false;
        }
    }

    @ReactMethod
    public void turnScreenOn() {
        if (isProximityWakeLockSupported()) {
            Log.d(TAG, "turnScreenOn(): use proximity lock.");
            releaseProximityWakeLock(true);
        } else {
            Log.d(TAG, "turnScreenOn(): proximity lock is not supported. try manually.");
            manualTurnScreenOn();
        }
    }

    @ReactMethod
    public void turnScreenOff() {
        if (isProximityWakeLockSupported()) {
            Log.d(TAG, "turnScreenOff(): use proximity lock.");
            acquireProximityWakeLock();
        } else {
            Log.d(TAG, "turnScreenOff(): proximity lock is not supported. try manually.");
            manualTurnScreenOff();
        }
    }

    private void updateAudioRoute() {
        if (forceSpeakerOn) {
            Log.d(TAG, "updateAudioRoute() forceSpeakerOn. speaker: true");
            setSpeakerphoneOn(true);
        } else if (audioManager.isWiredHeadsetOn() || audioManager.isBluetoothA2dpOn() || audioManager.isBluetoothScoOn()) {
            Log.d(TAG, "updateAudioRoute() has headphone plugged. speaker: false");
            setSpeakerphoneOn(false);
        } else {
            if (getProximityIsNear()) {
                Log.d(TAG, "updateAudioRoute() proximity is near. speaker: false");
                setSpeakerphoneOn(false);
            } else {
                Log.d(TAG, "updateAudioRoute() default audio route. speaker: " + defaultSpeakerOn);
                setSpeakerphoneOn(defaultSpeakerOn);
            }
        }
    }

    @ReactMethod
    public void setKeepScreenOn(final boolean enable) {
        Log.d(TAG, "setKeepScreenOn() " + enable);
        UiThreadUtil.runOnUiThread(new Runnable() {
            public void run() {
                Activity mCurrentActivity = getCurrentActivity();
                if (mCurrentActivity == null) {
                    Log.d(TAG, "ReactContext doesn't hava any Activity attached.");
                    return;
                }
                Window window = mCurrentActivity.getWindow();
                if (enable) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            }
        });
    }

    @ReactMethod
    public void setSpeakerphoneOn(final boolean enable) {
        if (enable != audioManager.isSpeakerphoneOn())  {
            Log.d(TAG, "setSpeakerphoneOn(): " + enable);
            audioManager.setSpeakerphoneOn(enable);
        }
    }

    @ReactMethod
    public void setForceSpeakerphoneOn(final boolean enable) {
        forceSpeakerOn = enable;
        if (forceSpeakerOn) {
            Log.d(TAG, "setForceSpeakerphoneOn()");
            setSpeakerphoneOn(true);
        }
    }

    @ReactMethod
    public void setMicrophoneMute(final boolean enable) {
        if (enable != audioManager.isMicrophoneMute())  {
            Log.d(TAG, "setMicrophoneMute(): " + enable);
            audioManager.setMicrophoneMute(enable);
        }
    }

    @Override
    public void onHostResume() {
        Log.d(TAG, "onResume()");
        resume();
    }

    @Override
    public void onHostPause() {
        Log.d(TAG, "onPause()");
        pause();
    }

    @Override
    public void onHostDestroy() {
        Log.d(TAG, "onDestroy()");
        stop();
    }
}
