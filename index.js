'use strict';
var _InCallManager = require('react-native').NativeModules.InCallManager;
import {
    Platform,
    Vibration,
} from 'react-native';

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
        // --- every element must be a finite number; native would throw on anything else
        this.vibrate = Array.isArray(vibrate_pattern)
            && vibrate_pattern.every((ms) => typeof ms === 'number' && isFinite(ms));
        ios_category = (ios_category === 'playback') ? 'playback' : "default";
        // --- android only, default looping. Round up: a fraction must not become 0 (= no timeout).
        seconds = (typeof seconds === 'number' && seconds > 0 && isFinite(seconds)) ? Math.ceil(seconds) : -1;

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
        if (typeof fileType !== 'string' || fileType.length === 0) {
            fileType = '_DEFAULT_';
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

    async chooseAudioRoute(route) {
        if (typeof route !== 'string') {
            throw new TypeError('chooseAudioRoute(): route must be a string, got ' + typeof route);
        }
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
