package com.zxcpoiu.incallmanager;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.Manifest.permission;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.UiThreadUtil;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.File;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;

public class InCallManagerModule extends ReactContextBaseJavaModule implements LifecycleEventListener {
    private static final String REACT_NATIVE_MODULE_NAME = "InCallManager";
    private static final String TAG = REACT_NATIVE_MODULE_NAME;
    private static SparseArray<Promise> mRequestPermissionCodePromises;
    private static SparseArray<String> mRequestPermissionCodeTargetPermission;
    private String mPackageName = "com.zxcpoiu.incallmanager";

    // --- Screen Manager
    private PowerManager mPowerManager;
    private WakeLock mFullLock = null;
    private WakeLock mPokeFullLock = null;
    private WakeLock mPartialLock = null;
    private WakeLock mProximityLock = null;
    private Method mPowerManagerRelease;
    private WindowManager.LayoutParams lastLayoutParams;
    private WindowManager mWindowManager;

    // --- AudioRouteManager
    private AudioManager audioManager;
    private boolean audioManagerInitialized = false;
    private boolean isAudioFocused = false;
    private boolean isOrigAudioSetupStored = false;
    private boolean origIsSpeakerPhoneOn = false;
    private boolean origIsMicrophoneMute = false;
    private int origAudioMode = AudioManager.MODE_INVALID;
    private int origRingerMode = AudioManager.RINGER_MODE_NORMAL;
    private boolean defaultSpeakerOn = false;
    private int defaultAudioMode = AudioManager.MODE_IN_COMMUNICATION;
    private int forceSpeakerOn = 0;
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
    private OnFocusChangeListener mOnFocusChangeListener;

    // --- same as: RingtoneManager.getActualDefaultRingtoneUri(reactContext, RingtoneManager.TYPE_RINGTONE);
    private Uri defaultRingtoneUri = Settings.System.DEFAULT_RINGTONE_URI;
    private Uri defaultRingbackUri = Settings.System.DEFAULT_RINGTONE_URI;
    private Uri defaultBusytoneUri = Settings.System.DEFAULT_NOTIFICATION_URI;
    //private Uri defaultAlarmAlertUri = Settings.System.DEFAULT_ALARM_ALERT_URI; // --- too annoying
    private Uri bundleRingtoneUri;
    private Uri bundleRingbackUri;
    private Uri bundleBusytoneUri;
    private Map<String, Uri> audioUriMap;
    private MyPlayerInterface mRingtone;
    private MyPlayerInterface mRingback;
    private MyPlayerInterface mBusytone;
    private String media = "audio";
    private static String recordPermission = "unknow";
    private static String cameraPermission = "unknow";

    interface MyPlayerInterface {
        public boolean isPlaying();
        public void startPlay(Map<String, Object> data);
        public void stopPlay();
    }

    @Override
    public String getName() {
        return REACT_NATIVE_MODULE_NAME;
    }

    public InCallManagerModule(ReactApplicationContext reactContext) {
        super(reactContext);
        mPackageName = reactContext.getPackageName();
        reactContext.addLifecycleEventListener(this);
        mWindowManager = (WindowManager) reactContext.getSystemService(Context.WINDOW_SERVICE);
        mPowerManager = (PowerManager) reactContext.getSystemService(Context.POWER_SERVICE);
        mPokeFullLock = mPowerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, TAG);
        mPokeFullLock.setReferenceCounted(false);
        mFullLock = mPowerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, TAG);
        mFullLock.setReferenceCounted(false);
        mPartialLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        mPartialLock.setReferenceCounted(false);
        audioManager = ((AudioManager) reactContext.getSystemService(Context.AUDIO_SERVICE));
        mSensorManager = (SensorManager) reactContext.getSystemService(Context.SENSOR_SERVICE);
        proximitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        checkProximitySupport();
        audioUriMap = new HashMap<String, Uri>();
        audioUriMap.put("defaultRingtoneUri", defaultRingtoneUri);
        audioUriMap.put("defaultRingbackUri", defaultRingbackUri);
        audioUriMap.put("defaultBusytoneUri", defaultBusytoneUri);
        audioUriMap.put("bundleRingtoneUri", bundleRingtoneUri);
        audioUriMap.put("bundleRingbackUri", bundleRingbackUri);
        audioUriMap.put("bundleBusytoneUri", bundleBusytoneUri);
        mRequestPermissionCodePromises = new SparseArray<Promise>();
        mRequestPermissionCodeTargetPermission = new SparseArray<String>();
        mOnFocusChangeListener = new OnFocusChangeListener();
        Log.d(TAG, "InCallManager initialized");
    }

    private void checkProximitySupport() {
        if (proximitySensor != null) {
            isProximitySupported = true;
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

    private boolean acquireFullWakeLock() {
        synchronized (mFullLock) {
            if (!mFullLock.isHeld()) {
                Log.d(TAG, "acquireFullWakeLock()");
                mFullLock.acquire();
                return true;
            }
        }
        return false;
    }

    private boolean releaseFullWakeLock() {
        synchronized (mFullLock) {
            if (mFullLock.isHeld()) {
                Log.d(TAG, "releaseFullWakeLock()");
                mFullLock.release();
                return true;
            }
        }
        return false;
    }

    private boolean acquirePokeFullWakeLockReleaseAfter(long timeout) {
        synchronized (mPokeFullLock) {
            if (!mPokeFullLock.isHeld()) {
                mPokeFullLock.acquire(timeout);
                Log.d(TAG, String.format("acquirePokeFullWakeLockReleaseAfter(%s)", timeout));
                return true;
            }
        }
        return false;
    }

    private boolean acquirePokeFullWakeLock() {
        synchronized (mPokeFullLock) {
            if (!mPokeFullLock.isHeld()) {
                Log.d(TAG, "acquirePokeFullWakeLock()");
                mPokeFullLock.acquire();
                return true;
            }
        }
        return false;
    }

    private boolean releasePokeFullWakeLock() {
        synchronized (mPokeFullLock) {
            if (mPokeFullLock.isHeld()) {
                Log.d(TAG, "releasePokeFullWakeLock()");
                mPokeFullLock.release();
                return true;
            }
        }
        return false;
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
        if (!isOrigAudioSetupStored) {
            origRingerMode = audioManager.getRingerMode();
            origAudioMode = audioManager.getMode();
            origIsSpeakerPhoneOn = audioManager.isSpeakerphoneOn();
            origIsMicrophoneMute = audioManager.isMicrophoneMute();
            isOrigAudioSetupStored = true;
        }
    }

    private void restoreOriginalAudioSetup() {
        Log.d(TAG, "restoreOriginalAudioSetup()");
        if (isOrigAudioSetupStored) {
            setSpeakerphoneOn(origIsSpeakerPhoneOn);
            setMicrophoneMute(origIsMicrophoneMute);
            audioManager.setMode(origAudioMode);
            audioManager.setRingerMode(origRingerMode);
            if (getCurrentActivity() != null) {
                getCurrentActivity().setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE);
            }
            isOrigAudioSetupStored = false;
        }
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
            ReactContext reactContext = getReactApplicationContext();
            if (reactContext != null) {
                reactContext.registerReceiver(wiredHeadsetReceiver, filter);
            } else {
                Log.d(TAG, "startWiredHeadsetEvent() reactContext is null");
            }
        }
    }

    private void stopWiredHeadsetEvent() {
        if (wiredHeadsetReceiver != null) {
            Log.d(TAG, "stopWiredHeadsetEvent()");
            ReactContext reactContext = getReactApplicationContext();
            if (reactContext != null) {
                reactContext.unregisterReceiver(wiredHeadsetReceiver);
            } else {
                Log.d(TAG, "stopWiredHeadsetEvent() reactContext is null");
            }
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
            ReactContext reactContext = getReactApplicationContext();
            if (reactContext != null) {
                reactContext.registerReceiver(noisyAudioReceiver, filter);
            } else {
                Log.d(TAG, "startNoisyAudioEvent() reactContext is null");
            }
        }
    }

    private void stopNoisyAudioEvent() {
        if (noisyAudioReceiver != null) {
            Log.d(TAG, "stopNoisyAudioEvent()");
            ReactContext reactContext = getReactApplicationContext();
            if (reactContext != null) {
                reactContext.unregisterReceiver(noisyAudioReceiver);
            } else {
                Log.d(TAG, "stopNoisyAudioEvent() reactContext is null");
            }
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
            ReactContext reactContext = getReactApplicationContext();
            if (reactContext != null) {
                reactContext.registerReceiver(mediaButtonReceiver, filter);
            } else {
                Log.d(TAG, "startMediaButtonEvent() reactContext is null");
            }
        }
    }

    private void stopMediaButtonEvent() {
        if (mediaButtonReceiver != null) {
            Log.d(TAG, "stopMediaButtonEvent()");
            ReactContext reactContext = getReactApplicationContext();
            if (reactContext != null) {
                reactContext.unregisterReceiver(mediaButtonReceiver);
            } else {
                Log.d(TAG, "stopMediaButtonEvent() reactContext is null");
            }
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
        if (proximitySensorEventListener == null) {
            initProximitySensorEventListener();
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
        if (proximitySensorEventListener != null) {
            proximitySensorEventListener = null;
        }
    }

    private class OnFocusChangeListener implements AudioManager.OnAudioFocusChangeListener {

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

    private void sendEvent(final String eventName, @Nullable WritableMap params) {
        try {
            ReactContext reactContext = getReactApplicationContext();
            if (reactContext != null && reactContext.hasActiveCatalystInstance()) {
                reactContext
                        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                        .emit(eventName, params);
            } else {
                Log.e(TAG, "sendEvent(): reactContext is null or not having CatalystInstance yet.");
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "sendEvent(): java.lang.RuntimeException: Trying to invoke JS before CatalystInstance has been set!");
        }
    }

    @ReactMethod
    public void start(final String _media, final boolean auto, final String ringbackUriType) {
        media = _media;
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
            //getCurrentActivity().setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
            audioManager.setMode(defaultAudioMode);
            setSpeakerphoneOn(defaultSpeakerOn);
            setMicrophoneMute(false);
            forceSpeakerOn = 0;
            if (!ringbackUriType.isEmpty()) {
                startRingback(ringbackUriType);
            }
            updateAudioRoute();
            audioManagerInitialized = true;
        }
    }

    @ReactMethod
    public void stop(final String busytoneUriType) {
        if (audioManagerInitialized) {
            stopRingback();
            if (!busytoneUriType.isEmpty() && startBusytone(busytoneUriType)) {
                // play busytone first, and call this func again when finish
                Log.d(TAG, "play busytone before stop InCallManager");
                return;
            } else {
                Log.d(TAG, "stop() InCallManager");
                stopBusytone();
                stopEvents();
                setSpeakerphoneOn(false);
                setMicrophoneMute(false);
                forceSpeakerOn = 0;
                restoreOriginalAudioSetup();
                audioManagerInitialized = false;
            }
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
        turnScreenOn();
        releaseAudioFocus();
    }

    private void requestAudioFocus() {
        if (!isAudioFocused) {
            int result = audioManager.requestAudioFocus(mOnFocusChangeListener, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN);
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
    public void pokeScreen(int timeout) {
        //debugScreenPowerState();
        //if (!mPowerManager.isInteractive() && mWindowManager.getDefaultDisplay().getState() != Display.STATE_ON) {
        if (!mPokeFullLock.isHeld()) {
            Log.d(TAG, "pokeScreen()");
            if (timeout > 0) {
                acquirePokeFullWakeLockReleaseAfter(timeout); // --- ms
            } else {
                acquirePokeFullWakeLock();
                releasePokeFullWakeLock();
            }
        }
    }

    private void debugScreenPowerState() {
        String isDeviceIdleMode = "unknow"; // --- API 23
        String isIgnoringBatteryOptimizations = "unknow"; // --- API 23
        String isPowerSaveMode = "unknow"; // --- API 21
        String isInteractive = "unknow"; // --- API 20 ( before since API 7 is: isScreenOn())
        String screenState = "unknow"; // --- API 20

        if (android.os.Build.VERSION.SDK_INT >= 23) {
            isDeviceIdleMode = String.format("%s", mPowerManager.isDeviceIdleMode());
            isIgnoringBatteryOptimizations = String.format("%s", mPowerManager.isIgnoringBatteryOptimizations(mPackageName));
        }
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            isPowerSaveMode = String.format("%s", mPowerManager.isPowerSaveMode());
        }
        if (android.os.Build.VERSION.SDK_INT >= 20) {
            isInteractive = String.format("%s", mPowerManager.isInteractive());
            Display display = mWindowManager.getDefaultDisplay();
            switch (display.getState()) {
                case Display.STATE_OFF:
                    screenState = "STATE_OFF";
                    break;
                case Display.STATE_ON:
                    screenState = "STATE_ON";
                    break;
                case Display.STATE_DOZE:
                    screenState = "STATE_DOZE";
                    break;
                case Display.STATE_DOZE_SUSPEND:
                    screenState = "STATE_DOZE_SUSPEND";
                    break;
                default:
                    break;
            }
        } else {
            isInteractive = String.format("%s", mPowerManager.isScreenOn());
        }
        Log.d(TAG, String.format("debugScreenPowerState(): screenState='%s', isInteractive='%s', isPowerSaveMode='%s', isDeviceIdleMode='%s', isIgnoringBatteryOptimizations='%s'", screenState, isInteractive, isPowerSaveMode, isDeviceIdleMode, isIgnoringBatteryOptimizations));
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
        if (forceSpeakerOn != 0) { // 0 - default action
            if (forceSpeakerOn == 1) { // 1 - on
                Log.d(TAG, "updateAudioRoute() forceSpeakerOn. speaker: true");
                setSpeakerphoneOn(true);
            } else { // -1 - off
                Log.d(TAG, "updateAudioRoute() forceSpeakerOn. speaker: false");
                setSpeakerphoneOn(false);
            }
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

    /**
     * flag: Int
     * 0: use default action
     * 1: force speaker on
     * -1: force speaker off
     */
    @ReactMethod
    public void setForceSpeakerphoneOn(final int flag) {
        if (flag < -1 || flag > 1) {
            return;
        }
        Log.d(TAG, "setForceSpeakerphoneOn() flag: " + flag);
        forceSpeakerOn = flag;
        updateAudioRoute();
    }

    @ReactMethod
    public void setMicrophoneMute(final boolean enable) {
        if (enable != audioManager.isMicrophoneMute())  {
            Log.d(TAG, "setMicrophoneMute(): " + enable);
            audioManager.setMicrophoneMute(enable);
        }
    }

    /** 
     * This is part of start() process. 
     * ringbackUriType must not empty. empty means do not play.
     */
    public void startRingback(final String ringbackUriType) {
        if (ringbackUriType.isEmpty()) {
            return;
        }
        try {
            Log.d(TAG, "startRingback(): UriType=" + ringbackUriType);
            if (mRingback != null) {
                if (mRingback.isPlaying()) {
                    Log.d(TAG, "startRingback(): is already playing");
                    return;
                } else {
                    stopRingback(); // --- use brandnew instance
                }
            }

            Uri ringbackUri;
            Map data = new HashMap<String, Object>();
            data.put("name", "mRingback");
            if (ringbackUriType.equals("_DTMF_")) {
                mRingback = new myToneGenerator(myToneGenerator.RINGBACK);
                mRingback.startPlay(data);
                return;
            } else {
                ringbackUri = getRingbackUri(ringbackUriType);
                if (ringbackUri == null) {
                    Log.d(TAG, "startRingback(): no available media");
                    return;    
                }
            }

            mRingback = new myMediaPlayer();
            data.put("sourceUri", ringbackUri);
            data.put("setLooping", true);
            data.put("audioStream", AudioManager.STREAM_VOICE_CALL);
            /*
            TODO: for API 21
            data.put("audioFlag", AudioAttributes.FLAG_AUDIBILITY_ENFORCED);
            data.put("audioUsage", AudioAttributes.USAGE_VOICE_COMMUNICATION); // USAGE_VOICE_COMMUNICATION_SIGNALLING ?
            data.put("audioContentType", AudioAttributes.CONTENT_TYPE_SPEECH); // CONTENT_TYPE_MUSIC ?
            */
            setMediaPlayerEvents((MediaPlayer)mRingback, "mRingback");
            mRingback.startPlay(data);
        } catch(Exception e) {
            Log.d(TAG, "startRingback() failed");
        }   
    }

    @ReactMethod
    public void stopRingback() {
        try {
            if (mRingback != null) {
                mRingback.stopPlay();
                mRingback = null;
            }
        } catch(Exception e) {
            Log.d(TAG, "stopRingback() failed");
        }   
    }

    /** 
     * This is part of start() process. 
     * busytoneUriType must not empty. empty means do not play.
     * return false to indicate play tone failed and should be stop() immediately
     * otherwise, it will stop() after a tone completed.
     */
    public boolean startBusytone(final String busytoneUriType) {
        if (busytoneUriType.isEmpty()) {
            return false;
        }
        try {
            Log.d(TAG, "startBusytone(): UriType=" + busytoneUriType);
            if (mBusytone != null) {
                if (mBusytone.isPlaying()) {
                    Log.d(TAG, "startBusytone(): is already playing");
                    return false;
                } else {
                    stopBusytone(); // --- use brandnew instance
                }
            }

            Uri busytoneUri;
            Map data = new HashMap<String, Object>();
            data.put("name", "mBusytone");
            if (busytoneUriType.equals("_DTMF_")) {
                mBusytone = new myToneGenerator(myToneGenerator.BUSY);
                mBusytone.startPlay(data);
                return true;
            } else {
                busytoneUri = getBusytoneUri(busytoneUriType);
                if (busytoneUri == null) {
                    Log.d(TAG, "startBusytone(): no available media");
                    return false;    
                }
            }

            mBusytone = new myMediaPlayer();
            data.put("sourceUri", busytoneUri);
            data.put("setLooping", false);
            data.put("audioStream", AudioManager.STREAM_VOICE_CALL);
            /*
            TODO: for API 21
            data.put("audioFlag", AudioAttributes.FLAG_AUDIBILITY_ENFORCED);
            data.put("audioUsage", AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING); // USAGE_VOICE_COMMUNICATION ?
            data.put("audioContentType", AudioAttributes.CONTENT_TYPE_SPEECH);
            */
            setMediaPlayerEvents((MediaPlayer)mBusytone, "mBusytone");
            mBusytone.startPlay(data);
            return true;
        } catch(Exception e) {
            Log.d(TAG, "startBusytone() failed");
            Log.d(TAG, e.getMessage());
            return false;
        }   
    }

    public void stopBusytone() {
        try {
            if (mBusytone != null) {
                mBusytone.stopPlay();
                mBusytone = null;
            }
        } catch(Exception e) {
            Log.d(TAG, "stopBusytone() failed");
        }   
    }

    @ReactMethod
    public void startRingtone(final String ringtoneUriType) {
        try {
            Log.d(TAG, "startRingtone(): UriType=" + ringtoneUriType);
            if (mRingtone != null) {
                if (mRingtone.isPlaying()) {
                    Log.d(TAG, "startRingtone(): is already playing");
                    return;
                } else {
                    stopRingtone(); // --- use brandnew instance
                }
            }

            //if (!audioManager.isStreamMute(AudioManager.STREAM_RING)) {
            //if (origRingerMode == AudioManager.RINGER_MODE_NORMAL) {
            if (audioManager.getStreamVolume(AudioManager.STREAM_RING) == 0) {
                Log.d(TAG, "startRingtone(): ringer is silent. leave without play.");
                return;
            }

            // --- there is no _DTMF_ option in startRingtone()
            Uri ringtoneUri = getRingtoneUri(ringtoneUriType);
            if (ringtoneUri == null) {
                Log.d(TAG, "startRingtone(): no available media");
                return;    
            }

            storeOriginalAudioSetup();
            Map data = new HashMap<String, Object>();
            mRingtone = new myMediaPlayer();
            data.put("name", "mRingtone");
            data.put("sourceUri", ringtoneUri);
            data.put("setLooping", true);
            data.put("audioStream", AudioManager.STREAM_RING);
            /*
            TODO: for API 21
            data.put("audioFlag", 0);
            data.put("audioUsage", AudioAttributes.USAGE_NOTIFICATION_RINGTONE); // USAGE_NOTIFICATION_COMMUNICATION_REQUEST ?
            data.put("audioContentType", AudioAttributes.CONTENT_TYPE_MUSIC);
            */
            setMediaPlayerEvents((MediaPlayer) mRingtone, "mRingtone");
            releasePokeFullWakeLock();
            acquireFullWakeLock();
            mRingtone.startPlay(data);
        } catch(Exception e) {
            releaseFullWakeLock();
            Log.d(TAG, "startRingtone() failed");
        }   
    }

    @ReactMethod
    public void stopRingtone() {
        try {
            if (mRingtone != null) {
                mRingtone.stopPlay();
                mRingtone = null;
                restoreOriginalAudioSetup();
            }
            releaseFullWakeLock();
        } catch(Exception e) {
            Log.d(TAG, "stopRingtone() failed");
        }   
    }

    private void setMediaPlayerEvents(MediaPlayer mp, final String name) {

        mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            //http://developer.android.com/reference/android/media/MediaPlayer.OnErrorListener.html
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Log.d(TAG, String.format("MediaPlayer %s onError(). what: %d, extra: %d", name, what, extra));
                //return True if the method handled the error
                //return False, or not having an OnErrorListener at all, will cause the OnCompletionListener to be called. Get news & tips 
                return true;
            }
        });

        mp.setOnInfoListener(new MediaPlayer.OnInfoListener() {
            //http://developer.android.com/reference/android/media/MediaPlayer.OnInfoListener.html
            @Override
            public boolean onInfo(MediaPlayer mp, int what, int extra) {
                Log.d(TAG, String.format("MediaPlayer %s onInfo(). what: %d, extra: %d", name, what, extra));
                //return True if the method handled the info
                //return False, or not having an OnInfoListener at all, will cause the info to be discarded.
                return true;
            }
        });

        mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                Log.d(TAG, String.format("MediaPlayer %s onPrepared(), start play, isSpeakerPhoneOn %b", name, audioManager.isSpeakerphoneOn()));
                if (name.equals("mBusytone")) {
                    audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                } else if (name.equals("mRingback")) {
                    audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                } else if (name.equals("mRingtone")) {
                    audioManager.setMode(AudioManager.MODE_RINGTONE);
                } 
                updateAudioRoute();
                mp.start();
            }
        });

        mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                Log.d(TAG, String.format("MediaPlayer %s onCompletion()", name));
                if (name.equals("mBusytone")) {
                    Log.d(TAG, "MyMediaPlayer(): invoke stop()");
                    stop("");
                }
            }
        });

    }

    private Uri getRingtoneUri(final String _type) {
        final String fileBundle = "incallmanager_ringtone";
        final String fileBundleExt = "mp3";
        final String fileSysWithExt = "media_volume.ogg";
        final String fileSysPath = "/system/media/audio/ui"; // --- every devices all ships with different in ringtone. maybe ui sounds are more "stock"
        String type;
        // --- _type MAY be empty
        if (_type.equals("_DEFAULT_") ||  _type.isEmpty()) {
            //type = fileSysWithExt;
            return getDefaultUserUri("defaultRingtoneUri");
        } else {
            type = _type;
        }
        return getAudioUri(type, fileBundle, fileBundleExt, fileSysWithExt, fileSysPath, "bundleRingtoneUri", "defaultRingtoneUri");
    }

    private Uri getRingbackUri(final String _type) {
        final String fileBundle = "incallmanager_ringback";
        final String fileBundleExt = "mp3";
        final String fileSysWithExt = "media_volume.ogg";
        final String fileSysPath = "/system/media/audio/ui"; // --- every devices all ships with different in ringtone. maybe ui sounds are more "stock"
        String type;
        // --- _type would never be empty here. just in case.
        if (_type.equals("_DEFAULT_") ||  _type.isEmpty()) {
            //type = fileSysWithExt;
            return getDefaultUserUri("defaultRingbackUri");
        } else {
            type = _type;
        }
        return getAudioUri(type, fileBundle, fileBundleExt, fileSysWithExt, fileSysPath, "bundleRingbackUri", "defaultRingbackUri");
    }

    private Uri getBusytoneUri(final String _type) {
        final String fileBundle = "incallmanager_busytone";
        final String fileBundleExt = "mp3";
        final String fileSysWithExt = "LowBattery.ogg";
        final String fileSysPath = "/system/media/audio/ui"; // --- every devices all ships with different in ringtone. maybe ui sounds are more "stock"
        String type;
        // --- _type would never be empty here. just in case.
        if (_type.equals("_DEFAULT_") ||  _type.isEmpty()) {
            //type = fileSysWithExt; // --- 
            return getDefaultUserUri("defaultBusytoneUri");
        } else {
            type = _type;
        }
        return getAudioUri(type, fileBundle, fileBundleExt, fileSysWithExt, fileSysPath, "bundleBusytoneUri", "defaultBusytoneUri");
    }

    private Uri getAudioUri(final String _type, final String fileBundle, final String fileBundleExt, final String fileSysWithExt, final String fileSysPath, final String uriBundle, final String uriDefault) {
        String type = _type;
        if (type.equals("_BUNDLE_")) {
            if (audioUriMap.get(uriBundle) == null) {
                int res = 0;
                ReactContext reactContext = getReactApplicationContext();
                if (reactContext != null) {
                    res = reactContext.getResources().getIdentifier(fileBundle, "raw", mPackageName);
                } else {
                    Log.d(TAG, "getAudioUri() reactContext is null");
                }
                if (res <= 0) {
                    Log.d(TAG, String.format("getAudioUri() %s.%s not found in bundle.", fileBundle, fileBundleExt));
                    audioUriMap.put(uriBundle, null);
                    //type = fileSysWithExt;
                    return getDefaultUserUri(uriDefault); // --- if specified bundle but not found, use default directlly
                } else {
                    audioUriMap.put(uriBundle, Uri.parse("android.resource://" + mPackageName + "/" + Integer.toString(res)));
                    //bundleRingtoneUri = Uri.parse("android.resource://" + reactContext.getPackageName() + "/" + R.raw.incallmanager_ringtone);
                    //bundleRingtoneUri = Uri.parse("android.resource://" + reactContext.getPackageName() + "/raw/incallmanager_ringtone");
                    Log.d(TAG, "getAudioUri() using: " + type);
                    return audioUriMap.get(uriBundle);
                }
            } else {
                Log.d(TAG, "getAudioUri() using: " + type);
                return audioUriMap.get(uriBundle);
            }
        }

        // --- Check file every time in case user deleted.
        final String target = fileSysPath + "/" + type;
        Uri _uri = getSysFileUri(target);
        if (_uri == null) {
            Log.d(TAG, "getAudioUri() using user default");
            return getDefaultUserUri(uriDefault);
        } else {
            Log.d(TAG, "getAudioUri() using internal: " + target);
            audioUriMap.put(uriDefault, _uri);
            return _uri;
        }
    }

    private Uri getSysFileUri(final String target) {
        File file = new File(target);
        if (file.isFile()) {
            return Uri.fromFile(file);
        }
        return null;
    }

    private Uri getDefaultUserUri(final String type) {
        // except ringtone, it doesn't suppose to be go here. and every android has different files unlike apple;
        if (type.equals("defaultRingtoneUri")) {
            return Settings.System.DEFAULT_RINGTONE_URI;
        } else if (type.equals("defaultRingbackUri")) {
            return Settings.System.DEFAULT_RINGTONE_URI;
        } else if (type.equals("defaultBusytoneUri")) {
            return Settings.System.DEFAULT_NOTIFICATION_URI; // --- DEFAULT_ALARM_ALERT_URI
        } else {
            return Settings.System.DEFAULT_NOTIFICATION_URI;
        }
    }

    private class myToneGenerator extends Thread implements MyPlayerInterface {
        private int toneType;
        private int toneCategory;
        private boolean playing = false;
        private static final int maxWaitTimeMs = 3600000; // 1 hour fairly enough
        private static final int loadBufferWaitTimeMs = 20;
        private static final int toneVolume = 100; // The volume of the tone, given in percentage of maximum volume (from 0-100).
        // --- constant in ToneGenerator all below 100
        public static final int BEEP = 101;
        public static final int BUSY = 102;
        public static final int CALLEND = 103;
        public static final int CALLWAITING = 104;
        public static final int RINGBACK = 105;
        public static final int SILENT = 106;
        public int customWaitTimeMs = maxWaitTimeMs;
        public String caller;

        myToneGenerator(final int t) {
            super();
            toneCategory = t;
        }

        public void setCustomWaitTime(final int ms) {
            customWaitTimeMs = ms;
        }

        @Override
        public void startPlay(final Map data) {
            String name = (String) data.get("name");
            caller = name;
            start();
        }

        @Override
        public boolean isPlaying() {
            return playing;
        }

        @Override
        public void stopPlay() {
            synchronized (this) {
                if (playing) {
                    notify();
                }
                playing = false;
            }
        }

        @Override
        public void run() {
            int toneWaitTimeMs;
            switch (toneCategory) {
                case SILENT:
                    //toneType = ToneGenerator.TONE_CDMA_SIGNAL_OFF;
                    toneType = ToneGenerator.TONE_CDMA_ANSWER;
                    toneWaitTimeMs = 1000;
                    break;
                case BUSY:
                    //toneType = ToneGenerator.TONE_SUP_BUSY;
                    //toneType = ToneGenerator.TONE_SUP_CONGESTION;
                    //toneType = ToneGenerator.TONE_SUP_CONGESTION_ABBREV;
                    //toneType = ToneGenerator.TONE_CDMA_NETWORK_BUSY;
                    //toneType = ToneGenerator.TONE_CDMA_NETWORK_BUSY_ONE_SHOT;
                    toneType = ToneGenerator.TONE_SUP_RADIO_NOTAVAIL;
                    toneWaitTimeMs = 4000;
                    break;
                case RINGBACK:
                    //toneType = ToneGenerator.TONE_SUP_RINGTONE;
                    toneType = ToneGenerator.TONE_CDMA_NETWORK_USA_RINGBACK;
                    toneWaitTimeMs = maxWaitTimeMs; // [STOP MANUALLY]
                    break;
                case CALLEND:
                    toneType = ToneGenerator.TONE_PROP_PROMPT;
                    toneWaitTimeMs = 200; // plays when call ended
                    break;
                case CALLWAITING:
                    //toneType = ToneGenerator.TONE_CDMA_NETWORK_CALLWAITING;
                    toneType = ToneGenerator.TONE_SUP_CALL_WAITING;
                    toneWaitTimeMs = maxWaitTimeMs; // [STOP MANUALLY]
                    break;
                case BEEP:
                    //toneType = ToneGenerator.TONE_SUP_PIP;
                    //toneType = ToneGenerator.TONE_CDMA_PIP;
                    //toneType = ToneGenerator.TONE_SUP_RADIO_ACK;
                    //toneType = ToneGenerator.TONE_PROP_BEEP;
                    toneType = ToneGenerator.TONE_PROP_BEEP2;
                    toneWaitTimeMs = 1000; // plays when call ended
                    break;
                default:
                    // --- use ToneGenerator internal type.
                    Log.d(TAG, "myToneGenerator: use internal tone type: " + toneCategory);
                    toneType = toneCategory;
                    toneWaitTimeMs = customWaitTimeMs;
            }
            Log.d(TAG, String.format("myToneGenerator: toneCategory: %d ,toneType: %d, toneWaitTimeMs: %d", toneCategory, toneType, toneWaitTimeMs));

            ToneGenerator tg;
            try {
                tg = new ToneGenerator(AudioManager.STREAM_VOICE_CALL, toneVolume);
            } catch (RuntimeException e) {
                Log.d(TAG, "myToneGenerator: Exception caught while creating ToneGenerator: " + e);
                tg = null;
            }

            if (tg != null) {
                synchronized (this) {
                    if (!playing) {
                        playing = true;

                        // --- make sure audio routing, or it will be wired when switch suddenly
                        if (caller.equals("mBusytone")) {
                            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                        } else if (caller.equals("mRingback")) {
                            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                        } else if (caller.equals("mRingtone")) {
                            audioManager.setMode(AudioManager.MODE_RINGTONE);
                        } 
                        InCallManagerModule.this.updateAudioRoute();

                        tg.startTone(toneType);
                        try {
                            wait(toneWaitTimeMs + loadBufferWaitTimeMs);
                        } catch  (InterruptedException e) {
                            Log.d(TAG, "myToneGenerator stopped. toneType: " + toneType);
                        }
                        tg.stopTone();
                    }
                    playing = false;
                    tg.release();
                }
            }
            Log.d(TAG, "MyToneGenerator(): play finished. caller=" + caller);
            if (caller.equals("mBusytone")) {
                Log.d(TAG, "MyToneGenerator(): invoke stop()");
                InCallManagerModule.this.stop("");
            }
        }
    }

    private class myMediaPlayer extends MediaPlayer implements MyPlayerInterface {

        //myMediaPlayer() {
        //    super();
        //}

        @Override
        public void stopPlay() {
            stop();
            reset();
            release();
        }

        @Override
        public void startPlay(final Map data) {
            try {
                Uri sourceUri = (Uri) data.get("sourceUri");
                boolean setLooping = (Boolean) data.get("setLooping");
                int stream = (Integer) data.get("audioStream");
                String name = (String) data.get("name");

                ReactContext reactContext = getReactApplicationContext();
                setDataSource(reactContext, sourceUri);
                setLooping(setLooping);
                setAudioStreamType(stream); // is better using STREAM_DTMF for ToneGenerator?

                /*
                // TODO: use modern and more explicit audio stream api
                if (android.os.Build.VERSION.SDK_INT >= 21) {
                    int audioFlag = (Integer) data.get("audioFlag");
                    int audioUsage = (Integer) data.get("audioUsage");
                    int audioContentType = (Integer) data.get("audioContentType");

                    setAudioAttributes(
                        new AudioAttributes.Builder()
                            .setFlags(audioFlag)
                            .setLegacyStreamType(stream)
                            .setUsage(audioUsage)
                            .setContentType(audioContentType)
                            .build()
                    );
                }
                */

                // -- will start at onPrepared() event
                prepareAsync();
            } catch (Exception e) {
                Log.d(TAG, "startPlay() failed");
            }
        }

        @Override
        public boolean isPlaying() {
            return super.isPlaying();
        }
    }

    @ReactMethod
    public void checkRecordPermission(Promise promise) {
        Log.d(TAG, "RNInCallManager.checkRecordPermission(): enter");
        _checkRecordPermission();
        if (recordPermission.equals("unknow")) {
            Log.d(TAG, "RNInCallManager.checkRecordPermission(): failed");
            promise.reject(new Exception("checkRecordPermission failed"));
        } else {
            promise.resolve(recordPermission);
        }
    }

    @ReactMethod
    public void checkCameraPermission(Promise promise) {
        Log.d(TAG, "RNInCallManager.checkCameraPermission(): enter");
        _checkCameraPermission();
        if (cameraPermission.equals("unknow")) {
            Log.d(TAG, "RNInCallManager.checkCameraPermission(): failed");
            promise.reject(new Exception("checkCameraPermission failed"));
        } else {
            promise.resolve(cameraPermission);
        }
    }

    private void _checkRecordPermission() {
        recordPermission = _checkPermission(permission.RECORD_AUDIO);
        Log.d(TAG, String.format("RNInCallManager.checkRecordPermission(): recordPermission=%s", recordPermission));
    }

    private void _checkCameraPermission() {
        cameraPermission = _checkPermission(permission.CAMERA);
        Log.d(TAG, String.format("RNInCallManager.checkCameraPermission(): cameraPermission=%s", cameraPermission));
    }

    private String _checkPermission(String targetPermission) {
        try {
            ReactContext reactContext = getReactApplicationContext();
            if (ContextCompat.checkSelfPermission(reactContext, targetPermission) == PackageManager.PERMISSION_GRANTED) {
                return "granted";
            } else {
                return "denied";
            }
        } catch (Exception e) {
            Log.d(TAG, "_checkPermission() catch");
            return "denied";
        }
    }

    @ReactMethod
    public void requestRecordPermission(Promise promise) {
        Log.d(TAG, "RNInCallManager.requestRecordPermission(): enter");
        _checkRecordPermission();
        if (!recordPermission.equals("granted")) {
            _requestPermission(permission.RECORD_AUDIO, promise);
        } else {
            // --- already granted
            promise.resolve(recordPermission);
        }
    }

    @ReactMethod
    public void requestCameraPermission(Promise promise) {
        Log.d(TAG, "RNInCallManager.requestCameraPermission(): enter");
        _checkCameraPermission();
        if (!cameraPermission.equals("granted")) {
            _requestPermission(permission.CAMERA, promise);
        } else {
            // --- already granted
            promise.resolve(cameraPermission);
        }
    }

    private void _requestPermission(String targetPermission, Promise promise) {
        Activity currentActivity = getCurrentActivity();
        if (currentActivity == null) {
            Log.d(TAG, String.format("RNInCallManager._requestPermission(): ReactContext doesn't hava any Activity attached when requesting %s", targetPermission));
            promise.reject(new Exception("_requestPermission(): currentActivity is not attached"));
            return;
        }
        int requestPermissionCode = getRandomInteger(1, 99999999);
        while (mRequestPermissionCodePromises.get(requestPermissionCode, null) != null) {
            requestPermissionCode = getRandomInteger(1, 99999999);
        }
        mRequestPermissionCodePromises.put(requestPermissionCode, promise);
        mRequestPermissionCodeTargetPermission.put(requestPermissionCode, targetPermission);
        /*
        if (ActivityCompat.shouldShowRequestPermissionRationale(currentActivity, permission.RECORD_AUDIO)) {
            showMessageOKCancel("You need to allow access to microphone for making call", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    ActivityCompat.requestPermissions(currentActivity, new String[] {permission.RECORD_AUDIO}, requestPermissionCode);
                }
            });
            return;
        }
        */
        ActivityCompat.requestPermissions(currentActivity, new String[] {targetPermission}, requestPermissionCode);
    }

    private static int getRandomInteger(int min, int max) {
        if (min >= max) {
            throw new IllegalArgumentException("max must be greater than min");
        }
        Random random = new Random();
        return random.nextInt((max - min) + 1) + min;
    }

    protected static void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        Log.d(TAG, "RNInCallManager.onRequestPermissionsResult(): enter");
        Promise promise = mRequestPermissionCodePromises.get(requestCode, null);
        String targetPermission = mRequestPermissionCodeTargetPermission.get(requestCode, null);
        mRequestPermissionCodePromises.delete(requestCode);
        mRequestPermissionCodeTargetPermission.delete(requestCode);
        if (promise != null && targetPermission != null) {

            Map<String, Integer> permissionResultMap = new HashMap<String, Integer>();

            for (int i = 0; i < permissions.length; i++) {
                permissionResultMap.put(permissions[i], grantResults[i]);
            }

            if (!permissionResultMap.containsKey(targetPermission)) {
                Log.wtf(TAG, String.format("RNInCallManager.onRequestPermissionsResult(): requested permission %s but did not appear", targetPermission));
                promise.reject(String.format("%s_PERMISSION_NOT_FOUND", targetPermission), String.format("requested permission %s but did not appear", targetPermission));
                return;
            }

            String _requestPermissionResult = "unknow";
            if (permissionResultMap.get(targetPermission) == PackageManager.PERMISSION_GRANTED) {
                _requestPermissionResult = "granted";
            } else {
                _requestPermissionResult = "denied";
            }

            if (targetPermission.equals(permission.RECORD_AUDIO)) {
                recordPermission = _requestPermissionResult;
            } else if (targetPermission.equals(permission.CAMERA)) {
                cameraPermission = _requestPermissionResult;
            }
            promise.resolve(_requestPermissionResult);
        } else {
            //super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            Log.wtf(TAG, "RNInCallManager.onRequestPermissionsResult(): request code not found");
            promise.reject("PERMISSION_REQUEST_CODE_NOT_FOUND", "request code not found");
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
        stopRingtone();
        stopRingback();
        stopBusytone();
        stop("");
    }
}
