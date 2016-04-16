//  RNInCallManager.swift
//  RNInCallManager
//
//  Created by zxcpoiu, Henry Hung-Hsien Lin on 2016-04-10
//  Copyright 2016 Facebook. All rights reserved.
//

import Foundation
import UIKit
import NotificationCenter
import AVFoundation

@objc(RNInCallManager)
class RNInCallManager: NSObject {
    var bridge: RCTBridge!  // this is synthesized
    var currentDevice: UIDevice!
    var audioSession: AVAudioSession!
    var isProximitySupported: Bool = false
    var isProximityRegistered: Bool = false
    var proximityIsNear: Bool = false
    var defaultAudioMode: String = AVAudioSessionModeVoiceChat
    var defaultAudioCategory: String = AVAudioSessionCategoryPlayAndRecord
    var origAudioCategory: String!
    var origAudioMode: String!
    var audioSessionInitialized: Bool = false
    let automatic: Bool = true
    var forceSpeakerOn: Bool = false
  
    //@objc func initWithBridge(_bridge: RCTBridge) {
        //self.bridge = _bridge
    override init() {
        super.init()
        self.currentDevice = UIDevice.currentDevice()
        self.audioSession = AVAudioSession.sharedInstance()
        self.checkProximitySupport()
        print("InCallManager initialized")
    }

    deinit {
        self.stop()
    }

    @objc func start(media: String, auto: Bool) -> Void {
        guard !self.audioSessionInitialized else { return }

        // --- audo is always true on ios
        if media == "video" {
            self.defaultAudioMode = AVAudioSessionModeVideoChat
        } else {
            self.defaultAudioMode = AVAudioSessionModeVoiceChat
        }
        print("start InCallManager")
        self.storeOriginalAudioSetup()
        //self.audioSession.setCategory(defaultAudioCategory, options: [.DefaultToSpeaker, .AllowBluetooth])
        _ = try? self.audioSession.setCategory(self.defaultAudioCategory)
        _ = try? self.audioSession.setMode(self.defaultAudioMode)
        if media == "audio" {
            self.startProximitySensor()
        }
        self.setKeepScreenOn(true)
        self.audioSessionInitialized = true
    }

    @objc func stop() -> Void {
        guard self.audioSessionInitialized else { return }

        print("stop InCallManager")
        self.restoreOriginalAudioSetup()
        self.stopProximitySensor()
        self.setKeepScreenOn(false)
        NSNotificationCenter.defaultCenter().removeObserver(self)
        self.audioSessionInitialized = false
    }

    @objc func turnScreenOn() -> Void {
        print("ios doesn't support turnScreenOn()")
    }

    @objc func turnScreenOff() -> Void {
        print("ios doesn't support turnScreenOn()")
    }

    func updateAudioRoute() -> Void {
        print("ios doesn't support updateAudioRoute()")
    }

    @objc func setKeepScreenOn(enable: Bool) -> Void {
        UIApplication.sharedApplication().idleTimerDisabled = enable
    }

    @objc func setSpeakerphoneOn(enable: Bool) -> Void {
        print("ios doesn't support setSpeakerphoneOn()")
    }

    @objc func setForceSpeakerphoneOn(enable: Bool) -> Void {
        self.forceSpeakerOn = enable;
        print("setForceSpeakerphoneOn(\(enable))");
        if self.forceSpeakerOn {
            _ = try? self.audioSession.overrideOutputAudioPort(AVAudioSessionPortOverride.Speaker)
        } else {
            _ = try? self.audioSession.overrideOutputAudioPort(AVAudioSessionPortOverride.None)
        }
    }

    @objc func setMicrophoneMute(enable: Bool) -> Void {
        print("ios doesn't support setMicrophoneMute()")
    }

    func storeOriginalAudioSetup() -> Void {
        print("storeOriginalAudioSetup()")
        self.origAudioCategory = self.audioSession.category 
        self.origAudioMode = self.audioSession.mode
    }

    func restoreOriginalAudioSetup() -> Void {
        print("restoreOriginalAudioSetup()")
        _ = try? self.audioSession.setCategory(self.origAudioCategory)
        _ = try? self.audioSession.setMode(self.origAudioMode)
    }

    func checkProximitySupport() -> Void {
        self.currentDevice.proximityMonitoringEnabled = true
        self.isProximitySupported = self.currentDevice.proximityMonitoringEnabled
        self.currentDevice.proximityMonitoringEnabled = false
    }

    func startProximitySensor() -> Void {
        guard !self.isProximityRegistered else { return }
        print("startProximitySensor()")
        self.currentDevice.proximityMonitoringEnabled = true

        self.startObserve(UIDeviceProximityStateDidChangeNotification, object: self.currentDevice, queue: nil) { notification in
            let state: Bool = self.currentDevice.proximityState
            print("Proximity Changed. isNear: \(state)")
            self.bridge.eventDispatcher.sendDeviceEventWithName("Proximity", body: ["isNear": state])
        }
        
        self.isProximityRegistered = true
    }

    func stopProximitySensor() -> Void {
        guard self.isProximityRegistered else { return }

        print("stopProximitySensor()")
        self.currentDevice.proximityMonitoringEnabled = false
        self.stopObserve(self, name: UIDeviceProximityStateDidChangeNotification, object: self.currentDevice)
        self.isProximityRegistered = false
    }

    func startObserve(name: String, object: AnyObject?, queue: NSOperationQueue?, block: (NSNotification) -> ()) {
        NSNotificationCenter.defaultCenter().addObserverForName(name, object: object, queue: queue, usingBlock: block)
    }

    func stopObserve(observer: AnyObject, name: String?, object: AnyObject?) {
        NSNotificationCenter.defaultCenter().removeObserver(observer, name: name, object: object)
    }
}
