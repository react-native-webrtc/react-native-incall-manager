'use strict';
var _InCallManager = require('react-native').NativeModules.InCallManager;

var InCallManager = {
    start: function(setup) {
        let auto = (setup.auto === false) ? false : true;
        let media = (setup.media === 'video') ? 'video' : 'audio';
        let ringback = (!!setup.ringback) ? (typeof setup.ringback === 'string') ? setup.ringback : "" : "";
        _InCallManager.start(media, auto, ringback);
    },
    stop: function(setup) {
        let busytone = (!!setup.busytone) ? (typeof setup.busytone === 'string') ? setup.busytone : "" : "";
        _InCallManager.stop(busytone);
    },
    turnScreenOff: function() {
        _InCallManager.turnScreenOff();
    },
    turnScreenOn: function() {
        _InCallManager.turnScreenOn();
    },
    setKeepScreenOn: function(enable) {
        enable = (enable === true) ? true : false;
        _InCallManager.setKeepScreenOn(enable);
    },
    setSpeakerphoneOn: function(enable) {
        enable = (enable === true) ? true : false;
        _InCallManager.setSpeakerphoneOn(enable);
    },
    setForceSpeakerphoneOn: function(enable) {
        enable = (enable === true) ? true : false;
        _InCallManager.setForceSpeakerphoneOn(enable);
    },
    setMicrophoneMute: function(enable) {
        enable = (enable === true) ? true : false;
        _InCallManager.setMicrophoneMute(enable);
    },
    startRingtone: function(ringtone) {
        ringtone = (typeof ringtone === 'string') ? ringtone : "_DEFAULT_";
        _InCallManager.startRingtone(ringtone);
    },
    stopRingtone: function() {
        _InCallManager.stopRingtone();
    },
    stopRingback: function() {
        _InCallManager.stopRingback();
    },
};

module.exports = InCallManager;
