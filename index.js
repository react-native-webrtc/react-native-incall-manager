'use strict';
var _InCallManager = require('react-native').NativeModules.InCallManager;
import {
    DeviceEventEmitter,
    NativeEventEmitter,
    Platform,
    Vibration,
} from 'react-native';

// --- the events the iOS module sends: its supportedEvents, which it exports as a constant, so
// --- this file keeps no copy of the list. Without the constant (a mocked module), every event is
// --- passed on and the native side decides.
function iosSupportsEvent(eventName) {
    const events = _InCallManager && _InCallManager.supportedEvents;
    return !Array.isArray(events) || events.indexOf(eventName) !== -1;
}

class InCallManager {
    constructor() {
        this.vibrate = false;
        this.audioUriMap = {
            ringtone: { _BUNDLE_: null, _DEFAULT_: null},
            ringback: { _BUNDLE_: null, _DEFAULT_: null},
            busytone: { _BUNDLE_: null, _DEFAULT_: null},
        };
    }

    start(setup) {
        setup = (setup === undefined) ? {} : setup;
        let auto = (setup.auto === false) ? false : true;
        let media = (setup.media === 'video') ? 'video' : 'audio';
        let ringback = (!!setup.ringback) ? (typeof setup.ringback === 'string') ? setup.ringback : "" : "";
        _InCallManager.start(media, auto, ringback);
    }

    stop(setup) {
        setup = (setup === undefined) ? {} : setup;
        let busytone = (!!setup.busytone) ? (typeof setup.busytone === 'string') ? setup.busytone : "" : "";
        _InCallManager.stop(busytone);
    }

    turnScreenOff() {
        _InCallManager.turnScreenOff();
    }

    turnScreenOn() {
        _InCallManager.turnScreenOn();
    }

    async getIsWiredHeadsetPluggedIn() {
        let isPluggedIn = await _InCallManager.getIsWiredHeadsetPluggedIn();
        return { isWiredHeadsetPluggedIn: isPluggedIn };
    }

    setFlashOn(enable, brightness) {
        if (Platform.OS === 'ios') {
            enable = (enable === true) ? true : false;
            brightness = (typeof brightness === 'number') ? brightness : 0;
            _InCallManager.setFlashOn(enable, brightness);
        } else {
            console.log("Android doesn't support setFlashOn(enable, brightness)");
        }
    }


    setKeepScreenOn(enable) {
        enable = (enable === true) ? true : false;
        _InCallManager.setKeepScreenOn(enable);
    }

    setSpeakerphoneOn(enable) {
        enable = (enable === true) ? true : false;
        _InCallManager.setSpeakerphoneOn(enable);
    }

    setForceSpeakerphoneOn(_flag) {
        let flag = (typeof _flag === "boolean") ? (_flag) ? 1 : -1 : 0;
        _InCallManager.setForceSpeakerphoneOn(flag);
    }

    setMicrophoneMute(enable) {
        enable = (enable === true) ? true : false;
        _InCallManager.setMicrophoneMute(enable);
    }

    startRingtone(ringtone, vibrate_pattern, ios_category, seconds) {
        ringtone = (typeof ringtone === 'string') ? ringtone : "_DEFAULT_";
        this.vibrate = (Array.isArray(vibrate_pattern)) ? true : false;
        ios_category = (ios_category === 'playback') ? 'playback' : "default";
        seconds = (typeof seconds === 'number' && seconds > 0) ? parseInt(seconds) : -1; // --- android only, default looping

        if (Platform.OS === 'android') {
            if (typeof _InCallManager.startRingtoneWithVibration === 'function') {
                _InCallManager.startRingtoneWithVibration(
                    ringtone,
                    seconds,
                    this.vibrate ? vibrate_pattern : null
                );
            } else {
                _InCallManager.startRingtone(ringtone, seconds);
                if (this.vibrate) {
                    Vibration.vibrate(vibrate_pattern, false);
                }
            }
        } else {
            _InCallManager.startRingtone(ringtone, ios_category);
            if (this.vibrate) {
                // --- should not use repeat, it may cause infinite loop in some cases.
                Vibration.vibrate(vibrate_pattern, false); // --- ios needs RN 0.34 to support vibration pattern
            }
        }
    }

    stopRingtone() {
        if (this.vibrate && (Platform.OS !== 'android'
                || typeof _InCallManager.startRingtoneWithVibration !== 'function')) {
            Vibration.cancel();
        }
        _InCallManager.stopRingtone();
    }

    startProximitySensor() {
        _InCallManager.startProximitySensor();
    }
  
    stopProximitySensor() {
        _InCallManager.stopProximitySensor();
    }

    startRingback(ringback) {
        ringback = (typeof ringback === 'string') ? ringback : "_DTMF_";
        _InCallManager.startRingback(ringback);
    }

    stopRingback() {
        _InCallManager.stopRingback();
    }

    pokeScreen(_timeout) {
        if (Platform.OS === 'android') {
            let timeout = (typeof _timeout === "number" && _timeout > 0) ? _timeout : 3000; // --- default 3000 ms
            _InCallManager.pokeScreen(timeout);
        } else {
            console.log("ios doesn't support pokeScreen()");
        }
    }

    async getAudioUri(audioType, fileType) {
        if (typeof this.audioUriMap[audioType] === "undefined") {
            return null;
        }
        if (this.audioUriMap[audioType][fileType]) {
            return this.audioUriMap[audioType][fileType];
        } else {
            try {
                let result = await _InCallManager.getAudioUriJS(audioType, fileType);
                if (typeof result === 'string' && result.length > 0) {
                    this.audioUriMap[audioType][fileType] = result;
                    return result
                } else {
                    return null;
                }
            } catch (err) {
                return null;
            }
        }
    }

    async getAudioDeviceStatus() {
        if (Platform.OS === 'ios') {
            return await _InCallManager.getAudioDeviceStatus();
        } else {
            throw new Error("android doesn't support getAudioDeviceStatus(), listen for 'onAudioDeviceChanged' instead");
        }
    }

    // Subscribes to one of this module's events ('Proximity', 'WiredHeadset',
    // 'onAudioDeviceChanged', ...). Returns the subscription; call remove() on it.
    // On iOS the native side sends events only while a NativeEventEmitter built on
    // this module has listeners, so DeviceEventEmitter.addListener alone hears
    // nothing there. Android sends through DeviceEventEmitter.
    // The iOS module sends only its supportedEvents; for the Android-only events a no-op
    // subscription is returned, since the native addListener: reports any other
    // name as an error (a RedBox in a debug build).
    addListener(eventName, listener) {
        if (Platform.OS === 'ios') {
            if (!iosSupportsEvent(eventName)) {
                return { remove() {} };
            }
            this.emitter = this.emitter || new NativeEventEmitter(_InCallManager);
            return this.emitter.addListener(eventName, listener);
        }
        return DeviceEventEmitter.addListener(eventName, listener);
    }

    async chooseAudioRoute(route) {
        let result = await _InCallManager.chooseAudioRoute(route);
        return result;
    }

    async requestAudioFocus() {
        if (Platform.OS === 'android') {
            return await _InCallManager.requestAudioFocusJS();
        } else {
            console.log("ios doesn't support requestAudioFocus()");
        }
    }

    async abandonAudioFocus() {
        if (Platform.OS === 'android') {
            return await _InCallManager.abandonAudioFocusJS();
        } else {
            console.log("ios doesn't support requestAudioFocus()");
        }
    }
}

export default new InCallManager();
