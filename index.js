'use strict';
var _InCallManager = require('react-native').NativeModules.InCallManager;
import {
    Platform,
    Vibration,
} from 'react-native';

class InCallManager {
    constructor() {
        this.vibrate = false;
        this.recordPermission = 'unknow';
        this.cameraPermission = 'unknow';
        this.audioUriMap = {
            ringtone: { _BUNDLE_: null, _DEFAULT_: null},
            ringback: { _BUNDLE_: null, _DEFAULT_: null},
            busytone: { _BUNDLE_: null, _DEFAULT_: null},
        };
        this.checkRecordPermission = this.checkRecordPermission.bind(this);
        this.requestRecordPermission = this.requestRecordPermission.bind(this);
        this.checkCameraPermission = this.checkCameraPermission.bind(this);
        this.requestCameraPermission = this.requestCameraPermission.bind(this);
        this.checkRecordPermission();
        this.checkCameraPermission();
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
        if (Platform.OS === 'ios') {
            return await _InCallManager.getIsWiredHeadsetPluggedIn();
        } else {
            console.log("Android doesn't support getIsWiredHeadsetPluggedIn() yet.");
            return null;
        }
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
            _InCallManager.startRingtone(ringtone, seconds);
        } else {
            _InCallManager.startRingtone(ringtone, ios_category);
        }

        // --- should not use repeat, it may cause infinite loop in some cases.
        if (this.vibrate) {
            Vibration.vibrate(vibrate_pattern, false); // --- ios needs RN 0.34 to support vibration pattern
        }
    }

    stopRingtone() {
        if (this.vibrate) {
            Vibration.cancel();
        }
        _InCallManager.stopRingtone();
    }

    stopRingback() {
        _InCallManager.stopRingback();
    }

    async checkRecordPermission() {
        // --- on android which api < 23, it will always be "granted"
        let result = await _InCallManager.checkRecordPermission();
        this.recordPermission = result;
        return result;
    }

    async requestRecordPermission() {
        // --- on android which api < 23, it will always be "granted"
        let result = await _InCallManager.requestRecordPermission();
        this.recordPermission = result;
        return result;
    }

    async checkCameraPermission() {
        // --- on android which api < 23, it will always be "granted"
        let result = await _InCallManager.checkCameraPermission();
        this.cameraPermission = result;
        return result;
    }

    async requestCameraPermission() {
        // --- on android which api < 23, it will always be "granted"
        let result = await _InCallManager.requestCameraPermission();
        this.cameraPermission = result;
        return result;
    }

    pokeScreen(_timeout) {
        if (Platform.OS === 'android') {
            let timeout = (typeof _timeout === "number" && _timeout > 0) ? _timeout : 0;
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
}

export default new InCallManager();
