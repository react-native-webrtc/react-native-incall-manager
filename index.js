'use strict';
var _InCallManager = require('react-native').NativeModules.InCallManager;

var InCallManager = {
    start: function(setup) {
        let auto = (setup.auto === false) ? false : true;
        let media = (setup.media === 'video') ? 'video' : 'audio';
        _InCallManager.start(media, auto);
    },
    stop: function() {
        _InCallManager.stop();
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
};

module.exports = InCallManager;
