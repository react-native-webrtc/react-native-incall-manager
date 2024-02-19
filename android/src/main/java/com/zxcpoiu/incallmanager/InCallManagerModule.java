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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.PowerManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import androidx.annotation.Nullable;
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

import java.lang.Runnable;
import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import com.zxcpoiu.incallmanager.AppRTC.AppRTCBluetoothManager;

public class InCallManagerModule extends ReactContextBaseJavaModule implements LifecycleEventListener, AudioManager.OnAudioFocusChangeListener {
    private static final String REACT_NATIVE_MODULE_NAME = "InCallManager";
    private static final String TAG = REACT_NATIVE_MODULE_NAME;
    private String mPackageName = "com.zxcpoiu.incallmanager";

    // --- Screen Manager
    private PowerManager mPowerManager;
    private WindowManager.LayoutParams lastLayoutParams;
    private WindowManager mWindowManager;

    // --- AudioRouteManager
    private AudioManager audioManager;
    private boolean audioManagerActivated = false;
    private boolean isAudioFocused = false;
    //private final Object mAudioFocusLock = new Object();
    private boolean isOrigAudioSetupStored = false;
    private boolean origIsSpeakerPhoneOn = false;
    private boolean origIsMicrophoneMute = false;
    private int origAudioMode = AudioManager.MODE_INVALID;
    private boolean defaultSpeakerOn = false;
    private int defaultAudioMode = AudioManager.MODE_IN_COMMUNICATION;
    private int forceSpeakerOn = 0;
    private boolean automatic = true;
    private boolean isProximityRegistered = false;
    private boolean proximityIsNear = false;
    private static final String ACTION_HEADSET_PLUG = (android.os.Build.VERSION.SDK_INT >= 21) ? AudioManager.ACTION_HEADSET_PLUG : Intent.ACTION_HEADSET_PLUG;
    private BroadcastReceiver wiredHeadsetReceiver;
    private BroadcastReceiver noisyAudioReceiver;
    private BroadcastReceiver mediaButtonReceiver;
    private AudioAttributes mAudioAttributes;
    private AudioFocusRequest mAudioFocusRequest;

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
    private Handler mRingtoneCountDownHandler;
    private String media = "audio";

    private static final String SPEAKERPHONE_AUTO = "auto";
    private static final String SPEAKERPHONE_TRUE = "true";
    private static final String SPEAKERPHONE_FALSE = "false";

    /**
     * AudioDevice is the names of possible audio devices that we currently
     * support.
     */
    public enum AudioDevice { SPEAKER_PHONE, WIRED_HEADSET, EARPIECE, BLUETOOTH, NONE }

    /** AudioManager state. */
    public enum AudioManagerState {
        UNINITIALIZED,
        PREINITIALIZED,
        RUNNING,
    }

    private int savedAudioMode = AudioManager.MODE_INVALID;
    private boolean savedIsSpeakerPhoneOn = false;
    private boolean savedIsMicrophoneMute = false;
    private boolean hasWiredHeadset = false;

    // Default audio device; speaker phone for video calls or earpiece for audio
    // only calls.
    private AudioDevice defaultAudioDevice = AudioDevice.NONE;

    // Contains the currently selected audio device.
    // This device is changed automatically using a certain scheme where e.g.
    // a wired headset "wins" over speaker phone. It is also possible for a
    // user to explicitly select a device (and overrid any predefined scheme).
    // See |userSelectedAudioDevice| for details.
    private AudioDevice selectedAudioDevice;

    // Contains the user-selected audio device which overrides the predefined
    // selection scheme.
    // TODO(henrika): always set to AudioDevice.NONE today. Add support for
    // explicit selection based on choice by userSelectedAudioDevice.
    private AudioDevice userSelectedAudioDevice;

    // Contains speakerphone setting: auto, true or false
    private final String useSpeakerphone = SPEAKERPHONE_AUTO;

    // Handles all tasks related to Bluetooth headset devices.
    private AppRTCBluetoothManager bluetoothManager = null;

    private final InCallProximityManager proximityManager;

    private final InCallWakeLockUtils wakeLockUtils;

    // Contains a list of available audio devices. A Set collection is used to
    // avoid duplicate elements.
    private Set<AudioDevice> audioDevices = new HashSet<>();

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
        audioManager = ((AudioManager) reactContext.getSystemService(Context.AUDIO_SERVICE));
        audioUriMap = new HashMap<String, Uri>();
        audioUriMap.put("defaultRingtoneUri", defaultRingtoneUri);
        audioUriMap.put("defaultRingbackUri", defaultRingbackUri);
        audioUriMap.put("defaultBusytoneUri", defaultBusytoneUri);
        audioUriMap.put("bundleRingtoneUri", bundleRingtoneUri);
        audioUriMap.put("bundleRingbackUri", bundleRingbackUri);
        audioUriMap.put("bundleBusytoneUri", bundleBusytoneUri);
        wakeLockUtils = new InCallWakeLockUtils(reactContext);
        proximityManager = InCallProximityManager.create(reactContext, this);

        UiThreadUtil.runOnUiThread(() -> {
            bluetoothManager = AppRTCBluetoothManager.create(reactContext, this);
        });

        Log.d(TAG, "InCallManager initialized");
    }

    private void manualTurnScreenOff() {
        Log.d(TAG, "manualTurnScreenOff()");
        Activity mCurrentActivity = getCurrentActivity();

        if (mCurrentActivity == null) {
            Log.d(TAG, "ReactContext doesn't have any Activity attached.");
            return;
        }

        UiThreadUtil.runOnUiThread(new Runnable() {
            public void run() {
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
        Activity mCurrentActivity = getCurrentActivity();

        if (mCurrentActivity == null) {
            Log.d(TAG, "ReactContext doesn't have any Activity attached.");
            return;
        }

        UiThreadUtil.runOnUiThread(new Runnable() {
            public void run() {
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
                        hasWiredHeadset = intent.getIntExtra("state", 0) == 1;
                        updateAudioRoute();
                        String deviceName = intent.getStringExtra("name");
                        if (deviceName == null) {
                            deviceName = "";
                        }
                        WritableMap data = Arguments.createMap();
                        data.putBoolean("isPlugged", (intent.getIntExtra("state", 0) == 1) ? true : false);
                        data.putBoolean("hasMic", (intent.getIntExtra("microphone", 0) == 1) ? true : false);
                        data.putString("deviceName", deviceName);
                        sendEvent("WiredHeadset", data);
                    } else {
                        hasWiredHeadset = false;
                    }
                }
            };
            this.registerReceiver(wiredHeadsetReceiver, filter);
        }
    }

    private void stopWiredHeadsetEvent() {
        if (wiredHeadsetReceiver != null) {
            Log.d(TAG, "stopWiredHeadsetEvent()");
            this.unregisterReceiver(this.wiredHeadsetReceiver);
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
                        updateAudioRoute();
                        sendEvent("NoisyAudio", null);
                    }
                }
            };
            this.registerReceiver(noisyAudioReceiver, filter);
        }
    }

    private void stopNoisyAudioEvent() {
        if (noisyAudioReceiver != null) {
            Log.d(TAG, "stopNoisyAudioEvent()");
            this.unregisterReceiver(this.noisyAudioReceiver);
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

            this.registerReceiver(mediaButtonReceiver, filter);
        }
    }

    private void stopMediaButtonEvent() {
        if (mediaButtonReceiver != null) {
            Log.d(TAG, "stopMediaButtonEvent()");
            this.unregisterReceiver(this.mediaButtonReceiver);
            mediaButtonReceiver = null;
        }
    }

    public void onProximitySensorChangedState(boolean isNear) {
        if (automatic && getSelectedAudioDevice() == AudioDevice.EARPIECE) {
            if (isNear) {
                turnScreenOff();
            } else {
                turnScreenOn();
            }
            updateAudioRoute();
        }
        WritableMap data = Arguments.createMap();
        data.putBoolean("isNear", isNear);
        sendEvent("Proximity", data);
    }

    @ReactMethod
    public void startProximitySensor() {
        if (!proximityManager.isProximitySupported()) {
            Log.d(TAG, "Proximity Sensor is not supported.");
            return;
        }
        if (isProximityRegistered) {
            Log.d(TAG, "Proximity Sensor is already registered.");
            return;
        }
        // --- SENSOR_DELAY_FASTEST(0 milisecs), SENSOR_DELAY_GAME(20 milisecs), SENSOR_DELAY_UI(60 milisecs), SENSOR_DELAY_NORMAL(200 milisecs)
        if (!proximityManager.start()) {
            Log.d(TAG, "proximityManager.start() failed. return false");
            return;
        }
        Log.d(TAG, "startProximitySensor()");
        isProximityRegistered = true;
    }

    @ReactMethod
    public void stopProximitySensor() {
        if (!proximityManager.isProximitySupported()) {
            Log.d(TAG, "Proximity Sensor is not supported.");
            return;
        }
        if (!isProximityRegistered) {
            Log.d(TAG, "Proximity Sensor is not registered.");
            return;
        }
        Log.d(TAG, "stopProximitySensor()");
        proximityManager.stop();
        isProximityRegistered = false;
    }

    // --- see: https://developer.android.com/reference/android/media/AudioManager
    @Override
    public void onAudioFocusChange(int focusChange) {
        String focusChangeStr;
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                focusChangeStr = "AUDIOFOCUS_GAIN";
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                focusChangeStr = "AUDIOFOCUS_GAIN_TRANSIENT";
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:
                focusChangeStr = "AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE";
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                focusChangeStr = "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK";
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
            case AudioManager.AUDIOFOCUS_NONE:
                focusChangeStr = "AUDIOFOCUS_NONE";
                break;
            default:
                focusChangeStr = "AUDIOFOCUS_UNKNOW";
                break;
        }

        Log.d(TAG, "onAudioFocusChange(): " + focusChange + " - " + focusChangeStr);

        WritableMap data = Arguments.createMap();
        data.putString("eventText", focusChangeStr);
        data.putInt("eventCode", focusChange);
        sendEvent("onAudioFocusChange", data);
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
        if (!audioManagerActivated) {
            audioManagerActivated = true;

            Log.d(TAG, "start audioRouteManager");
            wakeLockUtils.acquirePartialWakeLock();
            if (mRingtone != null && mRingtone.isPlaying()) {
                Log.d(TAG, "stop ringtone");
                stopRingtone(); // --- use brandnew instance
            }
            storeOriginalAudioSetup();
            requestAudioFocus();
            startEvents();
            UiThreadUtil.runOnUiThread(() -> {
                bluetoothManager.start();
            });
            // TODO: even if not acquired focus, we can still play sounds. but need figure out which is better.
            //getCurrentActivity().setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
            audioManager.setMode(defaultAudioMode);
            setSpeakerphoneOn(defaultSpeakerOn);
            setMicrophoneMute(false);
            forceSpeakerOn = 0;
            hasWiredHeadset = hasWiredHeadset();
            defaultAudioDevice = (defaultSpeakerOn) ? AudioDevice.SPEAKER_PHONE : (hasEarpiece()) ? AudioDevice.EARPIECE : AudioDevice.SPEAKER_PHONE;
            userSelectedAudioDevice = AudioDevice.NONE;
            selectedAudioDevice = AudioDevice.NONE;
            audioDevices.clear();
            updateAudioRoute();

            if (!ringbackUriType.isEmpty()) {
                startRingback(ringbackUriType);
            }
        }
    }

    public void stop() {
        stop("");
    }

    @ReactMethod
    public void stop(final String busytoneUriType) {
        if (audioManagerActivated) {
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
                UiThreadUtil.runOnUiThread(() -> {
                    bluetoothManager.stop();
                });
                restoreOriginalAudioSetup();
                abandonAudioFocus();
                audioManagerActivated = false;
            }
            wakeLockUtils.releasePartialWakeLock();
        }
    }

    private void startEvents() {
        startWiredHeadsetEvent();
        startNoisyAudioEvent();
        startMediaButtonEvent();
        startProximitySensor(); // --- proximity event always enable, but only turn screen off when audio is routing to earpiece.
        setKeepScreenOn(true);
    }

    private void stopEvents() {
        stopWiredHeadsetEvent();
        stopNoisyAudioEvent();
        stopMediaButtonEvent();
        stopProximitySensor();
        setKeepScreenOn(false);
        turnScreenOn();
    }

    @ReactMethod
    public void requestAudioFocusJS(Promise promise) {
        promise.resolve(requestAudioFocus());
    }

    private String requestAudioFocus() {
        String requestAudioFocusResStr = (android.os.Build.VERSION.SDK_INT >= 26)
                ? requestAudioFocusV26()
                : requestAudioFocusOld();
        Log.d(TAG, "requestAudioFocus(): res = " + requestAudioFocusResStr);
        return requestAudioFocusResStr;
    }

    private String requestAudioFocusV26() {
        if (isAudioFocused) {
            return "";
        }

        if (mAudioAttributes == null) {
            mAudioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
        }

        if (mAudioFocusRequest == null) {
            mAudioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(mAudioAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener(this)
                    .build();
        }

        int requestAudioFocusRes = audioManager.requestAudioFocus(mAudioFocusRequest);

        String requestAudioFocusResStr;
        switch (requestAudioFocusRes) {
            case AudioManager.AUDIOFOCUS_REQUEST_FAILED:
                requestAudioFocusResStr = "AUDIOFOCUS_REQUEST_FAILED";
                break;
            case AudioManager.AUDIOFOCUS_REQUEST_GRANTED:
                isAudioFocused = true;
                requestAudioFocusResStr = "AUDIOFOCUS_REQUEST_GRANTED";
                break;
            case AudioManager.AUDIOFOCUS_REQUEST_DELAYED:
                requestAudioFocusResStr = "AUDIOFOCUS_REQUEST_DELAYED";
                break;
            default:
                requestAudioFocusResStr = "AUDIOFOCUS_REQUEST_UNKNOWN";
                break;
        }

        return requestAudioFocusResStr;
    }

    private String requestAudioFocusOld() {
        if (isAudioFocused) {
            return "";
        }

        int requestAudioFocusRes = audioManager.requestAudioFocus(this, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);

        String requestAudioFocusResStr;
        switch (requestAudioFocusRes) {
            case AudioManager.AUDIOFOCUS_REQUEST_FAILED:
                requestAudioFocusResStr = "AUDIOFOCUS_REQUEST_FAILED";
                break;
            case AudioManager.AUDIOFOCUS_REQUEST_GRANTED:
                isAudioFocused = true;
                requestAudioFocusResStr = "AUDIOFOCUS_REQUEST_GRANTED";
                break;
            default:
                requestAudioFocusResStr = "AUDIOFOCUS_REQUEST_UNKNOWN";
                break;
        }

        return requestAudioFocusResStr;
    }

    @ReactMethod
    public void abandonAudioFocusJS(Promise promise) {
        promise.resolve(abandonAudioFocus());
    }

    private String abandonAudioFocus() {
        String abandonAudioFocusResStr = (android.os.Build.VERSION.SDK_INT >= 26)
                ? abandonAudioFocusV26()
                : abandonAudioFocusOld();
        Log.d(TAG, "abandonAudioFocus(): res = " + abandonAudioFocusResStr);
        return abandonAudioFocusResStr;
    }

    private String abandonAudioFocusV26() {
        if (!isAudioFocused || mAudioFocusRequest == null) {
            return "";
        }

        int abandonAudioFocusRes = audioManager.abandonAudioFocusRequest(mAudioFocusRequest);
        String abandonAudioFocusResStr;
        switch (abandonAudioFocusRes) {
            case AudioManager.AUDIOFOCUS_REQUEST_FAILED:
                abandonAudioFocusResStr = "AUDIOFOCUS_REQUEST_FAILED";
                break;
            case AudioManager.AUDIOFOCUS_REQUEST_GRANTED:
                isAudioFocused = false;
                abandonAudioFocusResStr = "AUDIOFOCUS_REQUEST_GRANTED";
                break;
            default:
                abandonAudioFocusResStr = "AUDIOFOCUS_REQUEST_UNKNOWN";
                break;
        }

        return abandonAudioFocusResStr;
    }

    private String abandonAudioFocusOld() {
        if (!isAudioFocused) {
            return "";
        }

        int abandonAudioFocusRes = audioManager.abandonAudioFocus(this);

        String abandonAudioFocusResStr;
        switch (abandonAudioFocusRes) {
            case AudioManager.AUDIOFOCUS_REQUEST_FAILED:
                abandonAudioFocusResStr = "AUDIOFOCUS_REQUEST_FAILED";
                break;
            case AudioManager.AUDIOFOCUS_REQUEST_GRANTED:
                isAudioFocused = false;
                abandonAudioFocusResStr = "AUDIOFOCUS_REQUEST_GRANTED";
                break;
            default:
                abandonAudioFocusResStr = "AUDIOFOCUS_REQUEST_UNKNOWN";
                break;
        }

        return abandonAudioFocusResStr;
    }

    @ReactMethod
    public void pokeScreen(int timeout) {
        Log.d(TAG, "pokeScreen()");
        wakeLockUtils.acquirePokeFullWakeLockReleaseAfter(timeout); // --- default 3000 ms
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
        if (proximityManager.isProximityWakeLockSupported()) {
            Log.d(TAG, "turnScreenOn(): use proximity lock.");
            proximityManager.releaseProximityWakeLock(true);
        } else {
            Log.d(TAG, "turnScreenOn(): proximity lock is not supported. try manually.");
            manualTurnScreenOn();
        }
    }

    @ReactMethod
    public void turnScreenOff() {
        if (proximityManager.isProximityWakeLockSupported()) {
            Log.d(TAG, "turnScreenOff(): use proximity lock.");
            proximityManager.acquireProximityWakeLock();
        } else {
            Log.d(TAG, "turnScreenOff(): proximity lock is not supported. try manually.");
            manualTurnScreenOff();
        }
    }

    @ReactMethod
    public void setKeepScreenOn(final boolean enable) {
        Log.d(TAG, "setKeepScreenOn() " + enable);

        Activity mCurrentActivity = getCurrentActivity();

        if (mCurrentActivity == null) {
            Log.d(TAG, "ReactContext doesn't have any Activity attached.");
            return;
        }

        UiThreadUtil.runOnUiThread(new Runnable() {
            public void run() {
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
	    audioManager.setMode(defaultAudioMode);
            audioManager.setSpeakerphoneOn(enable);
        }
    }

    // --- TODO (zxcpoiu): These two api name is really confusing. should be changed.
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

        // --- will call updateAudioDeviceState()
        // --- Note: in some devices, it may not contains specified route thus will not be effected.
        if (flag == 1) {
            selectAudioDevice(AudioDevice.SPEAKER_PHONE);
        } else if (flag == -1) {
            selectAudioDevice(AudioDevice.EARPIECE); // --- use the most common earpiece to force `speaker off`
        } else {
            selectAudioDevice(AudioDevice.NONE); // --- NONE will follow default route, the default route of `video` call is speaker.
        }
    }

    // --- TODO (zxcpoiu): Implement api to let user choose audio devices

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
    @ReactMethod
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
                }

                stopRingback(); // --- use brandnew instance
            }

            Uri ringbackUri;
            Map data = new HashMap<String, Object>();
            data.put("name", "mRingback");

            // --- use ToneGenerator instead file uri
            if (ringbackUriType.equals("_DTMF_")) {
                mRingback = new myToneGenerator(myToneGenerator.RINGBACK);
                mRingback.startPlay(data);
                return;
            }

            ringbackUri = getRingbackUri(ringbackUriType);
            if (ringbackUri == null) {
                Log.d(TAG, "startRingback(): no available media");
                return;    
            }

            mRingback = new myMediaPlayer();
            data.put("sourceUri", ringbackUri);
            data.put("setLooping", true);

            //data.put("audioStream", AudioManager.STREAM_VOICE_CALL); // --- lagacy
            // --- The ringback doesn't have to be a DTMF.
            // --- Should use VOICE_COMMUNICATION for sound during call or it may be silenced.
            data.put("audioUsage", AudioAttributes.USAGE_VOICE_COMMUNICATION);
            data.put("audioContentType", AudioAttributes.CONTENT_TYPE_MUSIC);

            setMediaPlayerEvents((MediaPlayer)mRingback, "mRingback");

            mRingback.startPlay(data);
        } catch(Exception e) {
            Log.d(TAG, "startRingback() failed", e);
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
                }

                stopBusytone(); // --- use brandnew instance
            }

            Uri busytoneUri;
            Map data = new HashMap<String, Object>();
            data.put("name", "mBusytone");

            // --- use ToneGenerator instead file uri
            if (busytoneUriType.equals("_DTMF_")) {
                mBusytone = new myToneGenerator(myToneGenerator.BUSY);
                mBusytone.startPlay(data);
                return true;
            }

            busytoneUri = getBusytoneUri(busytoneUriType);
            if (busytoneUri == null) {
                Log.d(TAG, "startBusytone(): no available media");
                return false;    
            }

            mBusytone = new myMediaPlayer();

            data.put("sourceUri", busytoneUri);
            data.put("setLooping", false);
            //data.put("audioStream", AudioManager.STREAM_VOICE_CALL); // --- lagacy
            // --- Should use VOICE_COMMUNICATION for sound during a call or it may be silenced.
            data.put("audioUsage", AudioAttributes.USAGE_VOICE_COMMUNICATION);
            data.put("audioContentType", AudioAttributes.CONTENT_TYPE_SONIFICATION); // --- CONTENT_TYPE_MUSIC?

            setMediaPlayerEvents((MediaPlayer)mBusytone, "mBusytone");
            mBusytone.startPlay(data);
            return true;
        } catch(Exception e) {
            Log.d(TAG, "startBusytone() failed", e);
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
    public void startRingtone(final String ringtoneUriType, final int seconds) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    Looper.prepare();

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

                    if (audioManagerActivated) {
                        InCallManagerModule.this.stop();
                    }

                    wakeLockUtils.acquirePartialWakeLock();

                    storeOriginalAudioSetup();
                    Map data = new HashMap<String, Object>();
                    mRingtone = new myMediaPlayer();

                    data.put("name", "mRingtone");
                    data.put("sourceUri", ringtoneUri);
                    data.put("setLooping", true);

                    //data.put("audioStream", AudioManager.STREAM_RING); // --- lagacy
                    data.put("audioUsage", AudioAttributes.USAGE_NOTIFICATION_RINGTONE); // --- USAGE_NOTIFICATION_COMMUNICATION_REQUEST?
                    data.put("audioContentType", AudioAttributes.CONTENT_TYPE_MUSIC);

                    setMediaPlayerEvents((MediaPlayer) mRingtone, "mRingtone");

                    mRingtone.startPlay(data);

                    if (seconds > 0) {
                        mRingtoneCountDownHandler = new Handler();
                        mRingtoneCountDownHandler.postDelayed(new Runnable() {
                            public void run() {
                                try {
                                    Log.d(TAG, String.format("mRingtoneCountDownHandler.stopRingtone() timeout after %d seconds", seconds));
                                    stopRingtone();
                                } catch(Exception e) {
                                    Log.d(TAG, "mRingtoneCountDownHandler.stopRingtone() failed.");
                                }
                            }
                        }, seconds * 1000);
                    }

                    Looper.loop();
                } catch(Exception e) {
                    wakeLockUtils.releasePartialWakeLock();
                    Log.e(TAG, "startRingtone() failed", e);
                }
            }
        };

        thread.start();
    }

    @ReactMethod
    public void stopRingtone() {
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    if (mRingtone != null) {
                        mRingtone.stopPlay();
                        mRingtone = null;
                        restoreOriginalAudioSetup();
                    }
                    if (mRingtoneCountDownHandler != null) {
                        mRingtoneCountDownHandler.removeCallbacksAndMessages(null);
                        mRingtoneCountDownHandler = null;
                    }
                } catch (Exception e) {
                    Log.d(TAG, "stopRingtone() failed");
                }
                wakeLockUtils.releasePartialWakeLock();
            }
        };

        thread.start();
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
                    stop();
                }
            }
        });

    }


// ===== File Uri Start =====
    @ReactMethod
    public void getAudioUriJS(String audioType, String fileType, Promise promise) {
        Uri result = null;
        if (audioType.equals("ringback")) {
            result = getRingbackUri(fileType);
        } else if (audioType.equals("busytone")) {
            result = getBusytoneUri(fileType);
        } else if (audioType.equals("ringtone")) {
            result = getRingtoneUri(fileType);
        }
        try {
            if (result != null) {
                promise.resolve(result.toString());
            } else {
                promise.reject("failed");
            }
        } catch (Exception e) {
            promise.reject("failed");
        }
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
// ===== File Uri End =====


// ===== Internal Classes Start =====
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
                InCallManagerModule.this.stop();
            }
        }
    }

    private class myMediaPlayer extends MediaPlayer implements MyPlayerInterface {

        @Override
        public void stopPlay() {
            stop();
            reset();
            release();
        }

        @Override
        public void startPlay(final Map data) {
            try {
                ReactContext reactContext = getReactApplicationContext();

                setDataSource(reactContext, (Uri) data.get("sourceUri"));
                setLooping((Boolean) data.get("setLooping"));

                // --- the `minSdkVersion` is 21 since RN 64,
                // --- if you want to suuport api < 21, comment out `setAudioAttributes` and use `setAudioStreamType((Integer) data.get("audioStream"))` instead
                setAudioAttributes(
                    new AudioAttributes.Builder()
                        .setUsage((Integer) data.get("audioUsage"))
                        .setContentType((Integer) data.get("audioContentType"))
                        .build()
                );

                // -- will start at onPrepared() event
                prepareAsync();
            } catch (Exception e) {
                Log.d(TAG, "startPlay() failed", e);
            }
        }

        @Override
        public boolean isPlaying() {
            return super.isPlaying();
        }
    }
// ===== Internal Classes End =====

    @ReactMethod
    public void chooseAudioRoute(String audioRoute, Promise promise) {
        Log.d(TAG, "RNInCallManager.chooseAudioRoute(): user choose audioDevice = " + audioRoute);

        if (audioRoute.equals(AudioDevice.EARPIECE.name())) {
            selectAudioDevice(AudioDevice.EARPIECE);
        } else if (audioRoute.equals(AudioDevice.SPEAKER_PHONE.name())) {
            selectAudioDevice(AudioDevice.SPEAKER_PHONE);
        } else if (audioRoute.equals(AudioDevice.WIRED_HEADSET.name())) {
            selectAudioDevice(AudioDevice.WIRED_HEADSET);
        } else if (audioRoute.equals(AudioDevice.BLUETOOTH.name())) {
            selectAudioDevice(AudioDevice.BLUETOOTH);
        }
        promise.resolve(getAudioDeviceStatusMap());
    }

    private static int getRandomInteger(int min, int max) {
        if (min >= max) {
            throw new IllegalArgumentException("max must be greater than min");
        }
        Random random = new Random();
        return random.nextInt((max - min) + 1) + min;
    }

    private void pause() {
        if (audioManagerActivated) {
            Log.d(TAG, "pause audioRouteManager");
            stopEvents();
        }
    }

    private void resume() {
        if (audioManagerActivated) {
            Log.d(TAG, "resume audioRouteManager");
            startEvents();
        }
    }

    @Override
    public void onHostResume() {
        Log.d(TAG, "onResume()");
        //resume();
    }

    @Override
    public void onHostPause() {
        Log.d(TAG, "onPause()");
        //pause();
    }

    @Override
    public void onHostDestroy() {
        Log.d(TAG, "onDestroy()");
        stopRingtone();
        stopRingback();
        stopBusytone();
        stop();
    }

    private void updateAudioRoute() {
        if (!automatic) {
            return;
        }
        updateAudioDeviceState();
    }

// ===== NOTE: below functions is based on appRTC DEMO M64 ===== //
  /** Changes selection of the currently active audio device. */
    private void setAudioDeviceInternal(AudioDevice device) {
        Log.d(TAG, "setAudioDeviceInternal(device=" + device + ")");
        if (!audioDevices.contains(device)) {
            Log.e(TAG, "specified audio device does not exist");
            return;
        }

        switch (device) {
            case SPEAKER_PHONE:
                setSpeakerphoneOn(true);
                break;
            case EARPIECE:
                setSpeakerphoneOn(false);
                break;
            case WIRED_HEADSET:
                setSpeakerphoneOn(false);
                break;
            case BLUETOOTH:
                setSpeakerphoneOn(false);
                break;
            default:
                Log.e(TAG, "Invalid audio device selection");
                break;
        }
        selectedAudioDevice = device;
    }



    /**
     * Changes default audio device.
     * TODO(henrika): add usage of this method in the AppRTCMobile client.
     */
    public void setDefaultAudioDevice(AudioDevice defaultDevice) {
        switch (defaultDevice) {
            case SPEAKER_PHONE:
                defaultAudioDevice = defaultDevice;
                break;
            case EARPIECE:
                if (hasEarpiece()) {
                    defaultAudioDevice = defaultDevice;
                } else {
                    defaultAudioDevice = AudioDevice.SPEAKER_PHONE;
                }
                break;
            default:
                Log.e(TAG, "Invalid default audio device selection");
                break;
        }
        Log.d(TAG, "setDefaultAudioDevice(device=" + defaultAudioDevice + ")");
        updateAudioDeviceState();
    }

    /** Changes selection of the currently active audio device. */
    public void selectAudioDevice(AudioDevice device) {
        if (device != AudioDevice.NONE && !audioDevices.contains(device)) {
            Log.e(TAG, "selectAudioDevice() Can not select " + device + " from available " + audioDevices);
            return;
        }
        userSelectedAudioDevice = device;
        updateAudioDeviceState();
    }

    /** Returns current set of available/selectable audio devices. */
    public Set<AudioDevice> getAudioDevices() {
        return Collections.unmodifiableSet(new HashSet<>(audioDevices));
    }

    /** Returns the currently selected audio device. */
    public AudioDevice getSelectedAudioDevice() {
        return selectedAudioDevice;
    }

    /** Helper method for receiver registration. */
    private void registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        final ReactContext reactContext = getReactApplicationContext();
        if (reactContext != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                reactContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                reactContext.registerReceiver(receiver, filter);
            }
        }  else {
            Log.d(TAG, "registerReceiver() reactContext is null");
        }
    }

    /** Helper method for unregistration of an existing receiver. */
    private void unregisterReceiver(final BroadcastReceiver receiver) {
        final ReactContext reactContext = this.getReactApplicationContext();
        if (reactContext != null) {
            try {
                reactContext.unregisterReceiver(receiver);
            } catch (final Exception e) {
                Log.d(TAG, "unregisterReceiver() failed");
            }
        } else {
            Log.d(TAG, "unregisterReceiver() reactContext is null");
        }
    }

    /** Sets the speaker phone mode. */
    /*
    private void setSpeakerphoneOn(boolean on) {
        boolean wasOn = audioManager.isSpeakerphoneOn();
        if (wasOn == on) {
            return;
        }
        audioManager.setSpeakerphoneOn(on);
    }
    */

    /** Sets the microphone mute state. */
    /*
    private void setMicrophoneMute(boolean on) {
        boolean wasMuted = audioManager.isMicrophoneMute();
        if (wasMuted == on) {
            return;
        }
        audioManager.setMicrophoneMute(on);
    }
    */

    /** Gets the current earpiece state. */
    private boolean hasEarpiece() {
        return getReactApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }

    /**
     * Checks whether a wired headset is connected or not.
     * This is not a valid indication that audio playback is actually over
     * the wired headset as audio routing depends on other conditions. We
     * only use it as an early indicator (during initialization) of an attached
     * wired headset.
     */
    @Deprecated
    private boolean hasWiredHeadset() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return audioManager.isWiredHeadsetOn();
        } else {
            final AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL);
            for (AudioDeviceInfo device : devices) {
                final int type = device.getType();
                if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
                    Log.d(TAG, "hasWiredHeadset: found wired headset");
                    return true;
                } else if (type == AudioDeviceInfo.TYPE_USB_DEVICE) {
                    Log.d(TAG, "hasWiredHeadset: found USB audio device");
                    return true;
                } else if (type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES) {
                    Log.d(TAG, "hasWiredHeadset: found wired headphones");
                    return true;
                }
            }
            return false;
        }
    }

    @ReactMethod
    public void getIsWiredHeadsetPluggedIn(Promise promise) {
        promise.resolve(this.hasWiredHeadset());
    }

    /**
     * Updates list of possible audio devices and make new device selection.
     */
    public void updateAudioDeviceState() {
        UiThreadUtil.runOnUiThread(() -> {
            Log.d(TAG, "--- updateAudioDeviceState: "
                            + "wired headset=" + hasWiredHeadset + ", "
                            + "BT state=" + bluetoothManager.getState());
            Log.d(TAG, "Device status: "
                            + "available=" + audioDevices + ", "
                            + "selected=" + selectedAudioDevice + ", "
                            + "user selected=" + userSelectedAudioDevice);

            // Check if any Bluetooth headset is connected. The internal BT state will
            // change accordingly.
            // TODO(henrika): perhaps wrap required state into BT manager.
            if (bluetoothManager.getState() == AppRTCBluetoothManager.State.HEADSET_AVAILABLE
                    || bluetoothManager.getState() == AppRTCBluetoothManager.State.HEADSET_UNAVAILABLE
                    || bluetoothManager.getState() == AppRTCBluetoothManager.State.SCO_DISCONNECTING) {
                bluetoothManager.updateDevice();
            }

            // Update the set of available audio devices.
            Set<AudioDevice> newAudioDevices = new HashSet<>();

            // always assume device has speaker phone
            newAudioDevices.add(AudioDevice.SPEAKER_PHONE);

            if (bluetoothManager.getState() == AppRTCBluetoothManager.State.SCO_CONNECTED
                    || bluetoothManager.getState() == AppRTCBluetoothManager.State.SCO_CONNECTING
                    || bluetoothManager.getState() == AppRTCBluetoothManager.State.HEADSET_AVAILABLE) {
                newAudioDevices.add(AudioDevice.BLUETOOTH);
            }

            if (hasWiredHeadset) {
                newAudioDevices.add(AudioDevice.WIRED_HEADSET);
            }

            if (hasEarpiece()) {
                newAudioDevices.add(AudioDevice.EARPIECE);
            }

            // --- check whether user selected audio device is available
            if (userSelectedAudioDevice != null
                    && userSelectedAudioDevice != AudioDevice.NONE
                    && !newAudioDevices.contains(userSelectedAudioDevice)) {
                userSelectedAudioDevice = AudioDevice.NONE;
            }

            // Store state which is set to true if the device list has changed.
            boolean audioDeviceSetUpdated = !audioDevices.equals(newAudioDevices);
            // Update the existing audio device set.
            audioDevices = newAudioDevices;

            AudioDevice newAudioDevice = getPreferredAudioDevice();

            // --- stop bluetooth if needed
            if (selectedAudioDevice == AudioDevice.BLUETOOTH
                    && newAudioDevice != AudioDevice.BLUETOOTH
                    && (bluetoothManager.getState() == AppRTCBluetoothManager.State.SCO_CONNECTED
                        || bluetoothManager.getState() == AppRTCBluetoothManager.State.SCO_CONNECTING)
                    ) {
                bluetoothManager.stopScoAudio();
                bluetoothManager.updateDevice();
            }

            // --- start bluetooth if needed
            if (selectedAudioDevice != AudioDevice.BLUETOOTH
                    && newAudioDevice == AudioDevice.BLUETOOTH
                    && bluetoothManager.getState() == AppRTCBluetoothManager.State.HEADSET_AVAILABLE) {
                // Attempt to start Bluetooth SCO audio (takes a few second to start).
                if (!bluetoothManager.startScoAudio()) {
                    // Remove BLUETOOTH from list of available devices since SCO failed.
                    audioDevices.remove(AudioDevice.BLUETOOTH);
                    audioDeviceSetUpdated = true;
                    if (userSelectedAudioDevice == AudioDevice.BLUETOOTH) {
                        userSelectedAudioDevice = AudioDevice.NONE;
                    }
                    newAudioDevice = getPreferredAudioDevice();
                }
            }
            
            if (newAudioDevice == AudioDevice.BLUETOOTH
                    && bluetoothManager.getState() != AppRTCBluetoothManager.State.SCO_CONNECTED) {
                newAudioDevice = getPreferredAudioDevice(true); // --- skip bluetooth
            }

            // Switch to new device but only if there has been any changes.
            if (newAudioDevice != selectedAudioDevice || audioDeviceSetUpdated) {

                // Do the required device switch.
                setAudioDeviceInternal(newAudioDevice);
                Log.d(TAG, "New device status: "
                                + "available=" + audioDevices + ", "
                                + "selected=" + newAudioDevice);
                /*
                if (audioManagerEvents != null) {
                    // Notify a listening client that audio device has been changed.
                    audioManagerEvents.onAudioDeviceChanged(selectedAudioDevice, audioDevices);
                }
                */
                sendEvent("onAudioDeviceChanged", getAudioDeviceStatusMap());
            }
            Log.d(TAG, "--- updateAudioDeviceState done");
        });
    }

    private WritableMap getAudioDeviceStatusMap() {
        WritableMap data = Arguments.createMap();
        String audioDevicesJson = "[";
        for (AudioDevice s: audioDevices) {
            audioDevicesJson += "\"" + s.name() + "\",";
        }

        // --- strip the last `,`
        if (audioDevicesJson.length() > 1) {
            audioDevicesJson = audioDevicesJson.substring(0, audioDevicesJson.length() - 1);
        }
        audioDevicesJson += "]";

        data.putString("availableAudioDeviceList", audioDevicesJson);
        data.putString("selectedAudioDevice", (selectedAudioDevice == null) ? "" : selectedAudioDevice.name());

        return data;
    }

    private AudioDevice getPreferredAudioDevice() {
        return getPreferredAudioDevice(false);
    }

    private AudioDevice getPreferredAudioDevice(boolean skipBluetooth) {
        final AudioDevice newAudioDevice;

        if (userSelectedAudioDevice != null && userSelectedAudioDevice != AudioDevice.NONE) {
            newAudioDevice = userSelectedAudioDevice;
        } else if (!skipBluetooth && audioDevices.contains(AudioDevice.BLUETOOTH)) {
            // If a Bluetooth is connected, then it should be used as output audio
            // device. Note that it is not sufficient that a headset is available;
            // an active SCO channel must also be up and running.
            newAudioDevice = AudioDevice.BLUETOOTH;
        } else if (audioDevices.contains(AudioDevice.WIRED_HEADSET)) {
            // If a wired headset is connected, but Bluetooth is not, then wired headset is used as
            // audio device.
            newAudioDevice = AudioDevice.WIRED_HEADSET;
        } else if (audioDevices.contains(defaultAudioDevice)) {
            newAudioDevice = defaultAudioDevice;
        } else {
            newAudioDevice = AudioDevice.SPEAKER_PHONE;
        }

        return newAudioDevice;
    }
}

