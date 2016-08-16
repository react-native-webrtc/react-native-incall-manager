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
class RNInCallManager: NSObject, AVAudioPlayerDelegate {
    var bridge: RCTBridge!  // this is synthesized
    var currentDevice: UIDevice!
    var audioSession: AVAudioSession!
    var mRingtone: AVAudioPlayer!
    var mRingback: AVAudioPlayer!
    var mBusytone: AVAudioPlayer!

    var defaultRingtoneUri: NSURL!
    var defaultRingbackUri: NSURL!
    var defaultBusytoneUri: NSURL!
    var bundleRingtoneUri: NSURL!
    var bundleRingbackUri: NSURL!
    var bundleBusytoneUri: NSURL!

    var isProximitySupported: Bool = false
    var proximityIsNear: Bool = false

    // --- tags to indicating which observer has added
    var isProximityRegistered: Bool = false
    var isAudioSessionInterruptionRegistered: Bool = false
    var isAudioSessionRouteChangeRegistered: Bool = false
    var isAudioSessionMediaServicesWereLostRegistered: Bool = false
    var isAudioSessionMediaServicesWereResetRegistered: Bool = false
    var isAudioSessionSilenceSecondaryAudioHintRegistered: Bool = false

    // -- notification observers
    var proximityObserver: NSObjectProtocol?
    var audioSessionInterruptionObserver: NSObjectProtocol?
    var audioSessionRouteChangeObserver: NSObjectProtocol?
    var audioSessionMediaServicesWereLostObserver: NSObjectProtocol?
    var audioSessionMediaServicesWereResetObserver: NSObjectProtocol?
    var audioSessionSilenceSecondaryAudioHintObserver: NSObjectProtocol?

    var incallAudioMode: String = AVAudioSessionModeVoiceChat
    var incallAudioCategory: String = AVAudioSessionCategoryPlayAndRecord
    var origAudioCategory: String!
    var origAudioMode: String!
    var audioSessionInitialized: Bool = false
    let automatic: Bool = true
    var forceSpeakerOn: Int = 0 //UInt8?
    var recordPermission: String!
    var cameraPermission: String!
    var media: String = "audio"

    // --- AVAudioSessionCategoryOptionAllowBluetooth:
    // --- Valid only if the audio session category is AVAudioSessionCategoryPlayAndRecord or AVAudioSessionCategoryRecord.
    // --- Using VoiceChat/VideoChat mode has the side effect of enabling the AVAudioSessionCategoryOptionAllowBluetooth category option. 
    // --- So basically, we don't have to add AllowBluetooth options by hand.

    //@objc func initWithBridge(_bridge: RCTBridge) {
    //self.bridge = _bridge
    override init() {
        super.init()
        self.currentDevice = UIDevice.currentDevice()
        self.audioSession = AVAudioSession.sharedInstance()
        self.checkProximitySupport()
        NSLog("RNInCallManager.init(): initialized")
    }

    deinit {
        self.stop("")
    }

    @objc func start(media: String, auto: Bool, ringbackUriType: String) -> Void {
        guard !self.audioSessionInitialized else { return }
        guard self.recordPermission == "granted" else {
            NSLog("RNInCallManager.start(): recordPermission should be granted. state: \(self.recordPermission)")
            return
        }
        self.media = media

        // --- auto is always true on ios
        if self.media == "video" {
            self.incallAudioMode = AVAudioSessionModeVideoChat
        } else {
            self.incallAudioMode = AVAudioSessionModeVoiceChat
        }
        NSLog("RNInCallManager.start() start InCallManager. media=\(self.media), type=\(self.media), mode=\(self.incallAudioMode)")
        self.storeOriginalAudioSetup()
        self.forceSpeakerOn = 0;
        self.startAudioSessionNotification()
        //self.audioSessionSetCategory(self.incallAudioCategory, [.DefaultToSpeaker, .AllowBluetooth], #function)
        self.audioSessionSetCategory(self.incallAudioCategory, nil, #function)
        self.audioSessionSetMode(self.incallAudioMode, #function)
        self.audioSessionSetActive(true, nil, #function)
        if !(ringbackUriType ?? "").isEmpty {
            NSLog("RNInCallManager.start() play ringback first. type=\(ringbackUriType)")
            self.startRingback(ringbackUriType)
        }

        if self.media == "audio" {
            self.startProximitySensor()
        }
        self.setKeepScreenOn(true)
        self.audioSessionInitialized = true
        //self.debugAudioSession()
    }

    @objc func stop(busytoneUriType: String) -> Void {
        guard self.audioSessionInitialized else { return }

        self.stopRingback()
        if !(busytoneUriType ?? "").isEmpty && self.startBusytone(busytoneUriType) {
            // play busytone first, and call this func again when finish
            NSLog("RNInCallManager.stop(): play busytone before stop")
            return
        } else {
            NSLog("RNInCallManager.stop(): stop InCallManager")
            self.restoreOriginalAudioSetup()
            self.stopBusytone()
            self.stopProximitySensor()
            self.audioSessionSetActive(false, .NotifyOthersOnDeactivation, #function)
            self.setKeepScreenOn(false)
            self.stopAudioSessionNotification()
            NSNotificationCenter.defaultCenter().removeObserver(self)
            self.forceSpeakerOn = 0;
            self.audioSessionInitialized = false
        }
    }

    @objc func turnScreenOn() -> Void {
        NSLog("RNInCallManager.turnScreenOn(): ios doesn't support turnScreenOn()")
    }

    @objc func turnScreenOff() -> Void {
        NSLog("RNInCallManager.turnScreenOff(): ios doesn't support turnScreenOff()")
    }

    func updateAudioRoute() -> Void {
        NSLog("RNInCallManager.updateAudioRoute(): [Enter] forceSpeakerOn flag=\(self.forceSpeakerOn) media=\(self.media) category=\(self.audioSession.category) mode=\(self.audioSession.mode)")
        //self.debugAudioSession()
        var overrideAudioPort: AVAudioSessionPortOverride
        var overrideAudioPortString: String = ""
        var audioMode: String = ""

        // --- WebRTC native code will change audio mode automatically when established.
        // --- It would have some race condition if we change audio mode with webrtc at the same time.
        // --- So we should not change audio mode as possible as we can. Only when default video call which wants to force speaker off.
        // --- audio: only override speaker on/off; video: should change category if needed and handle proximity sensor. ( because default proximity is off when video call )
        if self.forceSpeakerOn == 1 {
            // --- force ON, override speaker only, keep audio mode remain.
            overrideAudioPort = .Speaker
            overrideAudioPortString = ".Speaker"
            if self.media == "video" {
                audioMode = AVAudioSessionModeVideoChat
                self.stopProximitySensor()
            }
        } else if self.forceSpeakerOn == -1 {
            // --- force off
            overrideAudioPort = .None
            overrideAudioPortString = ".None"
            if self.media == "video" {
                audioMode = AVAudioSessionModeVoiceChat
                self.startProximitySensor()
            }
        } else { // use default behavior
            overrideAudioPort = .None
            overrideAudioPortString = ".None"
            if self.media == "video" {
                audioMode = AVAudioSessionModeVideoChat
                self.stopProximitySensor()
            }
        }

        let isCurrentRouteToSpeaker: Bool = self.checkAudioRoute([AVAudioSessionPortBuiltInSpeaker], "output")
        if (overrideAudioPort == .Speaker && !isCurrentRouteToSpeaker) || (overrideAudioPort == .None && isCurrentRouteToSpeaker) {
            do {
                try self.audioSession.overrideOutputAudioPort(overrideAudioPort)
                NSLog("RNInCallManager.updateAudioRoute(): audioSession.overrideOutputAudioPort(\(overrideAudioPortString)) success")
            } catch let err {
                NSLog("RNInCallManager.updateAudioRoute(): audioSession.overrideOutputAudioPort(\(overrideAudioPortString)) failed: \(err)")
            }
        } else {
            NSLog("RNInCallManager.updateAudioRoute(): did NOT overrideOutputAudioPort()")
        }

        if !audioMode.isEmpty && self.audioSession.mode != audioMode {
            self.audioSessionSetMode(audioMode, #function)
            NSLog("RNInCallManager.updateAudioRoute() audio mode has changed to \(audioMode)")
        } else {
            NSLog("RNInCallManager.updateAudioRoute() did NOT change audio mode")
        }
        //self.debugAudioSession()
    }

    func checkAudioRoute(targetPortTypeArray: [String], _ routeType: String) -> Bool {
        if let currentRoute: AVAudioSessionRouteDescription = self.audioSession.currentRoute {
            let routes: [AVAudioSessionPortDescription] = (routeType == "input" ? currentRoute.inputs : currentRoute.outputs)
            for _portDescription in routes {
                let portDescription: AVAudioSessionPortDescription = _portDescription as AVAudioSessionPortDescription
                if targetPortTypeArray.contains(portDescription.portType) {
                    return true
                }
            }
        }
        return false
    }

    func isWiredHeadsetPluggedIn() -> Bool {
        // --- only check for a audio device plugged into headset port instead bluetooth/usb/hdmi
        return self.checkAudioRoute([AVAudioSessionPortHeadphones], "output") || self.checkAudioRoute([AVAudioSessionPortHeadsetMic], "input")
    }

    func audioSessionSetCategory(audioCategory: String, _ options: AVAudioSessionCategoryOptions?, _ callerMemo: String) -> Void {
        do {
            if let withOptions = options {
                try self.audioSession.setCategory(audioCategory, withOptions: withOptions)
            } else {
                try self.audioSession.setCategory(audioCategory)
            }
            NSLog("RNInCallManager.\(callerMemo): audioSession.setCategory(\(audioCategory), withOptions: \(options)) success")
        } catch let err {
            NSLog("RNInCallManager.\(callerMemo): audioSession.setCategory(\(audioCategory), withOptions: \(options)) failed: \(err)")
        }
    }

    func audioSessionSetMode(audioMode: String, _ callerMemo: String) -> Void {
        do {
            try self.audioSession.setMode(audioMode)
            NSLog("RNInCallManager.\(callerMemo): audioSession.setMode(\(audioMode)) success")
        } catch let err {
            NSLog("RNInCallManager.\(callerMemo): audioSession.setMode(\(audioMode)) failed: \(err)")
        }
    }

    func audioSessionSetActive(audioActive: Bool, _ options:AVAudioSessionSetActiveOptions?, _ callerMemo: String) -> Void {
        do {
            if let withOptions = options {
                try self.audioSession.setActive(audioActive, withOptions: withOptions)
            } else {
                try self.audioSession.setActive(audioActive)
            }
            NSLog("RNInCallManager.\(callerMemo): audioSession.setActive(\(audioActive), withOptions: \(options)) success")
        } catch let err {
            NSLog("RNInCallManager.\(callerMemo): audioSession.setActive(\(audioActive), withOptions: \(options)) failed: \(err)")
        }
    }

    @objc func setKeepScreenOn(enable: Bool) -> Void {
        NSLog("RNInCallManager.setKeepScreenOn(): enable: \(enable)")
        UIApplication.sharedApplication().idleTimerDisabled = enable
    }

    @objc func setSpeakerphoneOn(enable: Bool) -> Void {
        NSLog("RNInCallManager.setSpeakerphoneOn(): ios doesn't support setSpeakerphoneOn()")
    }

    @objc func setForceSpeakerphoneOn(flag: Int) -> Void {
        self.forceSpeakerOn = flag
        NSLog("RNInCallManager.setForceSpeakerphoneOn(): flag=\(flag)")
        self.updateAudioRoute()
    }

    @objc func setMicrophoneMute(enable: Bool) -> Void {
        NSLog("RNInCallManager.setMicrophoneMute(): ios doesn't support setMicrophoneMute()")
    }

    func storeOriginalAudioSetup() -> Void {
        NSLog("RNInCallManager.storeOriginalAudioSetup(): origAudioCategory=\(self.audioSession.category), origAudioMode=\(self.audioSession.mode)")
        self.origAudioCategory = self.audioSession.category 
        self.origAudioMode = self.audioSession.mode
    }

    func restoreOriginalAudioSetup() -> Void {
        NSLog("RNInCallManager.restoreOriginalAudioSetup(): origAudioCategory=\(self.audioSession.category), origAudioMode=\(self.audioSession.mode)")
        self.audioSessionSetCategory(self.origAudioCategory, nil, #function)
        self.audioSessionSetMode(self.origAudioMode, #function)
    }

    func checkProximitySupport() -> Void {
        self.currentDevice.proximityMonitoringEnabled = true
        self.isProximitySupported = self.currentDevice.proximityMonitoringEnabled
        self.currentDevice.proximityMonitoringEnabled = false
        NSLog("RNInCallManager.checkProximitySupport(): isProximitySupported=\(self.isProximitySupported)")
    }

    func startProximitySensor() -> Void {
        guard !self.isProximityRegistered else { return }

        NSLog("RNInCallManager.startProximitySensor()")
        self.currentDevice.proximityMonitoringEnabled = true

        self.stopObserve(self.proximityObserver, name: UIDeviceProximityStateDidChangeNotification, object: nil) // --- in case it didn't deallocate when ViewDidUnload
        self.proximityObserver = self.startObserve(UIDeviceProximityStateDidChangeNotification, object: self.currentDevice, queue: nil) { notification in
            let state: Bool = self.currentDevice.proximityState
            if state != self.proximityIsNear {
                NSLog("RNInCallManager.UIDeviceProximityStateDidChangeNotification(): isNear: \(state)")
                self.proximityIsNear = state
                self.bridge.eventDispatcher().sendDeviceEventWithName("Proximity", body: ["isNear": state])
            }
        }
        
        self.isProximityRegistered = true
    }

    func stopProximitySensor() -> Void {
        guard self.isProximityRegistered else { return }

        NSLog("RNInCallManager.stopProximitySensor()")
        self.currentDevice.proximityMonitoringEnabled = false
        self.stopObserve(self.proximityObserver, name: UIDeviceProximityStateDidChangeNotification, object: nil) // --- remove all no matter what object
        self.isProximityRegistered = false
    }

    func startAudioSessionNotification() -> Void {
        NSLog("RNInCallManager.startAudioSessionNotification() starting...")
        self.startAudioSessionInterruptionNotification()
        self.startAudioSessionRouteChangeNotification()
        self.startAudioSessionMediaServicesWereLostNotification()
        self.startAudioSessionMediaServicesWereResetNotification()
        self.startAudioSessionSilenceSecondaryAudioHintNotification()
    }

    func stopAudioSessionNotification() -> Void {
        NSLog("RNInCallManager.startAudioSessionNotification() stopping...")
        self.stopAudioSessionInterruptionNotification()
        self.stopAudioSessionRouteChangeNotification()
        self.stopAudioSessionMediaServicesWereLostNotification()
        self.stopAudioSessionMediaServicesWereResetNotification()
        self.stopAudioSessionSilenceSecondaryAudioHintNotification()
    }

    func startAudioSessionInterruptionNotification() -> Void {
        guard !self.isAudioSessionInterruptionRegistered else { return }
        NSLog("RNInCallManager.startAudioSessionInterruptionNotification()")

        self.stopObserve(self.audioSessionInterruptionObserver, name: AVAudioSessionInterruptionNotification, object: nil) // --- in case it didn't deallocate when ViewDidUnload
        self.audioSessionInterruptionObserver = self.startObserve(AVAudioSessionInterruptionNotification, object: nil, queue: nil) { notification in
            guard notification.name == AVAudioSessionInterruptionNotification && notification.userInfo != nil else { return }

            if let rawValue = notification.userInfo?[AVAudioSessionInterruptionTypeKey]?.unsignedIntegerValue {
                //if let type = AVAudioSessionInterruptionType.fromRaw(rawValue) {
                if let type = AVAudioSessionInterruptionType(rawValue: rawValue) {
                    switch type {
                        case .Began:
                            NSLog("RNInCallManager.AudioSessionInterruptionNotification: Began")
                        case .Ended:
                            NSLog("RNInCallManager.AudioSessionInterruptionNotification: Ended")
                        default:
                            NSLog("RNInCallManager.AudioSessionInterruptionNotification: Unknow Value")
                    }
                    return
                }
            }
            NSLog("RNInCallManager.AudioSessionInterruptionNotification: could not resolve notification")
        }
        self.isAudioSessionInterruptionRegistered = true
    }

    func stopAudioSessionInterruptionNotification() -> Void {
        guard self.isAudioSessionInterruptionRegistered else { return }

        NSLog("RNInCallManager.stopAudioSessionInterruptionNotification()")
        self.stopObserve(self.audioSessionInterruptionObserver, name: AVAudioSessionInterruptionNotification, object: nil) // --- remove all no matter what object
        self.isAudioSessionInterruptionRegistered = false
    }

    func startAudioSessionRouteChangeNotification() -> Void {
        guard !self.isAudioSessionRouteChangeRegistered else { return }

        NSLog("RNInCallManager.startAudioSessionRouteChangeNotification()")
        self.stopObserve(self.audioSessionRouteChangeObserver, name: AVAudioSessionRouteChangeNotification, object: nil) // --- in case it didn't deallocate when ViewDidUnload
        self.audioSessionRouteChangeObserver = self.startObserve(AVAudioSessionRouteChangeNotification, object: nil, queue: nil) { notification in
            guard notification.name == AVAudioSessionRouteChangeNotification && notification.userInfo != nil else { return }

            if let rawValue = notification.userInfo?[AVAudioSessionRouteChangeReasonKey]?.unsignedIntegerValue {
                if let type = AVAudioSessionRouteChangeReason(rawValue: rawValue) {
                    switch type {
                        case .Unknown:
                            NSLog("RNInCallManager.AudioRouteChange.Reason: Unknown")
                        case .NewDeviceAvailable:
                            NSLog("RNInCallManager.AudioRouteChange.Reason: NewDeviceAvailable")
                            if self.checkAudioRoute([AVAudioSessionPortHeadsetMic], "input") {
                                self.bridge.eventDispatcher().sendDeviceEventWithName("WiredHeadset", body: ["isPlugged": true, "hasMic": true, "deviceName": AVAudioSessionPortHeadsetMic])
                            } else if self.checkAudioRoute([AVAudioSessionPortHeadphones], "output") {
                                self.bridge.eventDispatcher().sendDeviceEventWithName("WiredHeadset", body: ["isPlugged": true, "hasMic": false, "deviceName": AVAudioSessionPortHeadphones])
                            }
                        case .OldDeviceUnavailable:
                            NSLog("RNInCallManager.AudioRouteChange.Reason: OldDeviceUnavailable")
                            if !self.isWiredHeadsetPluggedIn() {
                                self.bridge.eventDispatcher().sendDeviceEventWithName("WiredHeadset", body: ["isPlugged": false, "hasMic": false, "deviceName": ""])
                            }
                        case .CategoryChange:
                            NSLog("RNInCallManager.AudioRouteChange.Reason: CategoryChange. category=\(self.audioSession.category) mode=\(self.audioSession.mode)")
                            self.updateAudioRoute()
                        case .Override:
                            NSLog("RNInCallManager.AudioRouteChange.Reason: Override")
                        case .WakeFromSleep:
                            NSLog("RNInCallManager.AudioRouteChange.Reason: WakeFromSleep")
                        case .NoSuitableRouteForCategory:
                            NSLog("RNInCallManager.AudioRouteChange.Reason: NoSuitableRouteForCategory")
                        case .RouteConfigurationChange:
                            NSLog("RNInCallManager.AudioRouteChange.Reason: RouteConfigurationChange. category=\(self.audioSession.category) mode=\(self.audioSession.mode)")
                        default:
                            NSLog("RNInCallManager.AudioRouteChange.Reason: Unknow Value")
                    }
                } else {
                    NSLog("RNInCallManager.AudioRouteChange.Reason: cound not resolve notification")
                }
            } else {
                NSLog("RNInCallManager.AudioRouteChange.Reason: cound not resolve notification")
            }
            if #available(iOS 8, *) {
                if let rawValue = notification.userInfo?[AVAudioSessionSilenceSecondaryAudioHintTypeKey]?.unsignedIntegerValue {
                    if let type = AVAudioSessionSilenceSecondaryAudioHintType(rawValue: rawValue) {
                        switch type {
                            case .Begin:
                                NSLog("RNInCallManager.AudioRouteChange.SilenceSecondaryAudioHint: Begin")
                            case .End:
                                NSLog("RNInCallManager.AudioRouteChange.SilenceSecondaryAudioHint: End")
                            default:
                                NSLog("RNInCallManager.AudioRouteChange.SilenceSecondaryAudioHint: Unknow Value")
                        }
                    } else {
                        NSLog("RNInCallManager.AudioRouteChange.SilenceSecondaryAudioHint: cound not resolve notification")
                    }
                } else {
                    NSLog("RNInCallManager.AudioRouteChange.SilenceSecondaryAudioHint: cound not resolve notification")
                }
            }
        }
        self.isAudioSessionRouteChangeRegistered = true
    }

    func stopAudioSessionRouteChangeNotification() -> Void {
        guard self.isAudioSessionRouteChangeRegistered else { return }

        NSLog("RNInCallManager.stopAudioSessionRouteChangeNotification()")
        self.stopObserve(self.audioSessionRouteChangeObserver, name: AVAudioSessionRouteChangeNotification, object: nil) // --- remove all no matter what object
        self.isAudioSessionRouteChangeRegistered = false
    }

    func startAudioSessionMediaServicesWereLostNotification() -> Void {
        guard !self.isAudioSessionMediaServicesWereLostRegistered else { return }

        NSLog("RNInCallManager.startAudioSessionMediaServicesWereLostNotification()")
        self.stopObserve(self.audioSessionMediaServicesWereLostObserver, name: AVAudioSessionMediaServicesWereLostNotification, object: nil) // --- in case it didn't deallocate when ViewDidUnload
        self.audioSessionMediaServicesWereLostObserver = self.startObserve(AVAudioSessionMediaServicesWereLostNotification, object: nil, queue: nil) { notification in
            // --- This notification has no userInfo dictionary.
            NSLog("RNInCallManager.AudioSessionMediaServicesWereLostNotification: Media Services Were Lost")
        }
        self.isAudioSessionMediaServicesWereLostRegistered = true
    }

    func stopAudioSessionMediaServicesWereLostNotification() -> Void {
        guard self.isAudioSessionMediaServicesWereLostRegistered else { return }

        NSLog("RNInCallManager.stopAudioSessionMediaServicesWereLostNotification()")
        self.stopObserve(self.audioSessionMediaServicesWereLostObserver, name: AVAudioSessionMediaServicesWereLostNotification, object: nil) // --- remove all no matter what object
        self.isAudioSessionMediaServicesWereLostRegistered = false
    }

    func startAudioSessionMediaServicesWereResetNotification() -> Void {
        guard !self.isAudioSessionMediaServicesWereResetRegistered else { return }

        NSLog("RNInCallManager.startAudioSessionMediaServicesWereResetNotification()")
        self.stopObserve(self.audioSessionMediaServicesWereResetObserver, name: AVAudioSessionMediaServicesWereResetNotification, object: nil) // --- in case it didn't deallocate when ViewDidUnload
        self.audioSessionMediaServicesWereResetObserver = self.startObserve(AVAudioSessionMediaServicesWereResetNotification, object: nil, queue: nil) { notification in
            // --- This notification has no userInfo dictionary.
            NSLog("RNInCallManager.AudioSessionMediaServicesWereResetNotification: Media Services Were Reset")
        }
        self.isAudioSessionMediaServicesWereResetRegistered = true
    }

    func stopAudioSessionMediaServicesWereResetNotification() -> Void {
        guard self.isAudioSessionMediaServicesWereResetRegistered else { return }

        NSLog("RNInCallManager.stopAudioSessionMediaServicesWereResetNotification()")
        self.stopObserve(self.audioSessionMediaServicesWereResetObserver, name: AVAudioSessionMediaServicesWereResetNotification, object: nil) // --- remove all no matter what object
        self.isAudioSessionMediaServicesWereResetRegistered = false
    }

    func startAudioSessionSilenceSecondaryAudioHintNotification() -> Void {
        guard #available(iOS 8, *) else { return }
        guard !self.isAudioSessionSilenceSecondaryAudioHintRegistered else { return }

        NSLog("RNInCallManager.startAudioSessionSilenceSecondaryAudioHintNotification()")
        self.stopObserve(self.audioSessionSilenceSecondaryAudioHintObserver, name: AVAudioSessionSilenceSecondaryAudioHintNotification, object: nil) // --- in case it didn't deallocate when ViewDidUnload
        self.audioSessionSilenceSecondaryAudioHintObserver = self.startObserve(AVAudioSessionSilenceSecondaryAudioHintNotification, object: nil, queue: nil) { notification in
            guard notification.name == AVAudioSessionSilenceSecondaryAudioHintNotification && notification.userInfo != nil else { return }

            if let rawValue = notification.userInfo?[AVAudioSessionSilenceSecondaryAudioHintTypeKey]?.unsignedIntegerValue {
                if let type = AVAudioSessionSilenceSecondaryAudioHintType(rawValue: rawValue) {
                    switch type {
                        case .Begin:
                            NSLog("RNInCallManager.AVAudioSessionSilenceSecondaryAudioHintNotification: Begin")
                        case .End:
                            NSLog("RNInCallManager.AVAudioSessionSilenceSecondaryAudioHintNotification: End")
                        default:
                            NSLog("RNInCallManager.AVAudioSessionSilenceSecondaryAudioHintNotification: Unknow Value")
                    }
                    return
                }
            }
            NSLog("RNInCallManager.AVAudioSessionSilenceSecondaryAudioHintNotification: could not resolve notification")
        }
        self.isAudioSessionSilenceSecondaryAudioHintRegistered = true
    }

    func stopAudioSessionSilenceSecondaryAudioHintNotification() -> Void {
        guard #available(iOS 8, *) else { return }
        guard self.isAudioSessionSilenceSecondaryAudioHintRegistered else { return }

        NSLog("RNInCallManager.stopAudioSessionSilenceSecondaryAudioHintNotification()")
        self.stopObserve(self.audioSessionSilenceSecondaryAudioHintObserver, name: AVAudioSessionSilenceSecondaryAudioHintNotification, object: nil) // --- remove all no matter what object
        self.isAudioSessionSilenceSecondaryAudioHintRegistered = false
    }

    func startObserve(name: String, object: AnyObject?, queue: NSOperationQueue?, block: (NSNotification) -> ()) -> NSObjectProtocol {
        return NSNotificationCenter.defaultCenter().addObserverForName(name, object: object, queue: queue, usingBlock: block)
    }

    func stopObserve(_observer: AnyObject?, name: String?, object: AnyObject?) -> Void {
        if let observer = _observer {
            NSNotificationCenter.defaultCenter().removeObserver(observer, name: name, object: object)
        }
    }

    // --- _ringbackUriType: never go here with  be empty string.
    func startRingback(_ringbackUriType: String) -> Void {
        // you may rejected by apple when publish app if you use system sound instead of bundled sound.
        NSLog("RNInCallManager.startRingback(): type=\(_ringbackUriType)")
        do {
            if self.mRingback != nil {
                if self.mRingback.playing {
                    NSLog("RNInCallManager.startRingback(): is already playing")
                    return
                } else {
                    self.stopRingback()
                }
            }
            // ios don't have embedded DTMF tone generator. use system dtmf sound files.
            let ringbackUriType: String = (_ringbackUriType == "_DTMF_" ? "_DEFAULT_" : _ringbackUriType)
            let ringbackUri: NSURL? = getRingbackUri(ringbackUriType)
            if ringbackUri == nil {
                NSLog("RNInCallManager.startRingback(): no available media")
                return
            }
            //self.storeOriginalAudioSetup()
            self.mRingback = try AVAudioPlayer(contentsOfURL: ringbackUri!)
            self.mRingback.delegate = self
            self.mRingback.numberOfLoops = -1 // you need to stop it explicitly
            self.mRingback.prepareToPlay()

            //self.audioSessionSetCategory(self.incallAudioCategory, [.DefaultToSpeaker, .AllowBluetooth], #function)
            self.audioSessionSetCategory(self.incallAudioCategory, nil, #function)
            self.audioSessionSetMode(self.incallAudioMode, #function)
            self.mRingback.play()
        } catch let err {
            NSLog("RNInCallManager.startRingback(): caught error=\(err)")
        }    
    }

    @objc func stopRingback() -> Void {
        if self.mRingback != nil {
            NSLog("RNInCallManager.stopRingback()")
            self.mRingback.stop()
            self.mRingback = nil
            // --- need to reset route based on config because WebRTC seems will switch audio mode automatically when call established.
            //self.updateAudioRoute()
        }
    }

    // --- _busytoneUriType: never go here with  be empty string.
    func startBusytone(_busytoneUriType: String) -> Bool {
        // you may rejected by apple when publish app if you use system sound instead of bundled sound.
        NSLog("RNInCallManager.startBusytone(): type=\(_busytoneUriType)")
        do {
            if self.mBusytone != nil {
                if self.mBusytone.playing {
                    NSLog("RNInCallManager.startBusytone(): is already playing")
                    return false
                } else {
                    self.stopBusytone()
                }
            }

            // ios don't have embedded DTMF tone generator. use system dtmf sound files.
            let busytoneUriType: String = (_busytoneUriType == "_DTMF_" ? "_DEFAULT_" : _busytoneUriType)
            let busytoneUri: NSURL? = getBusytoneUri(busytoneUriType)
            if busytoneUri == nil {
                NSLog("RNInCallManager.startBusytone(): no available media")
                return false
            }
            //self.storeOriginalAudioSetup()
            self.mBusytone = try AVAudioPlayer(contentsOfURL: busytoneUri!)
            self.mBusytone.delegate = self
            self.mBusytone.numberOfLoops = 0 // it's part of start(), will stop at stop() 
            self.mBusytone.prepareToPlay()

            //self.audioSessionSetCategory(self.incallAudioCategory, [.DefaultToSpeaker, .AllowBluetooth], #function)
            self.audioSessionSetCategory(self.incallAudioCategory, nil, #function)
            self.audioSessionSetMode(self.incallAudioMode, #function)
            self.mBusytone.play()
        } catch let err {
            NSLog("RNInCallManager.startBusytone(): caught error=\(err)")
            return false
        }    
        return true
    }
    
    func stopBusytone() -> Void {
        if self.mBusytone != nil {
            NSLog("RNInCallManager.stopBusytone()")
            self.mBusytone.stop()
            self.mBusytone = nil
        }
    }

    // --- ringtoneUriType May be empty
    @objc func startRingtone(ringtoneUriType: String, ringtoneCategory: String) -> Void {
        // you may rejected by apple when publish app if you use system sound instead of bundled sound.
        NSLog("RNInCallManager.startRingtone(): type=\(ringtoneUriType)")
        do {
            if self.mRingtone != nil {
                if self.mRingtone.playing {
                    NSLog("RNInCallManager.startRingtone(): is already playing.")
                    return
                } else {
                    self.stopRingtone()
                }
            }
            let ringtoneUri: NSURL? = getRingtoneUri(ringtoneUriType)
            if ringtoneUri == nil {
                NSLog("RNInCallManager.startRingtone(): no available media")
                return
            }
            
            // --- ios has Ringer/Silent switch, so just play without check ringer volume.
            self.storeOriginalAudioSetup()
            self.mRingtone = try AVAudioPlayer(contentsOfURL: ringtoneUri!)
            self.mRingtone.delegate = self
            self.mRingtone.numberOfLoops = -1 // you need to stop it explicitly
            self.mRingtone.prepareToPlay()

            // --- 1. if we use Playback, it can supports background playing (starting from foreground), but it would not obey Ring/Silent switch.
            // ---    make sure you have enabled 'audio' tag ( or 'voip' tag ) at XCode -> Capabilities -> BackgroundMode
            // --- 2. if we use SoloAmbient, it would obey Ring/Silent switch in the foreground, but does not support background playing, 
            // ---    thus, then you should play ringtone again via local notification after back to home during a ring session.

            // we prefer 2. by default, since most of users doesn't want to interrupted by a ringtone if Silent mode is on.

            //self.audioSessionSetCategory(AVAudioSessionCategoryPlayback, [.DuckOthers], #function)
            if ringtoneCategory == "playback" {
                self.audioSessionSetCategory(AVAudioSessionCategoryPlayback, nil, #function)
            } else {
                self.audioSessionSetCategory(AVAudioSessionCategorySoloAmbient, nil, #function)
            }
            self.audioSessionSetMode(AVAudioSessionModeDefault, #function)
            //self.audioSessionSetActive(true, nil, #function)
            self.mRingtone.play()
        } catch let err {
            NSLog("RNInCallManager.startRingtone(): caught error=\(err)")
        }    
    }

    @objc func stopRingtone() -> Void {
        if self.mRingtone != nil {
            NSLog("RNInCallManager.stopRingtone()")
            self.mRingtone.stop()
            self.mRingtone = nil
            self.restoreOriginalAudioSetup()
            self.audioSessionSetActive(false, .NotifyOthersOnDeactivation, #function)
        }
    }

    @objc func getAudioUriJS(audioType: String, fileType: String, resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        var _result: NSURL? = nil
        if audioType == "ringback" {
            _result = getRingbackUri(fileType)
        } else if audioType == "busytone" {
            _result = getBusytoneUri(fileType)
        } else if audioType == "ringtone" {
            _result = getRingtoneUri(fileType)
        }
        if let result: NSURL? = _result {
            if let urlString = result?.absoluteString {
                resolve(urlString)
                return
            }
        }
        reject("error_code", "getAudioUriJS() failed", NSError(domain:"getAudioUriJS", code: 0, userInfo: nil))
    }

    func getRingbackUri(_type: String) -> NSURL? {
        let fileBundle: String = "incallmanager_ringback"
        let fileBundleExt: String = "mp3"
        //let fileSysWithExt: String = "vc~ringing.caf" // --- ringtone of facetine, but can't play it.
        //let fileSysPath: String = "/System/Library/Audio/UISounds"
        let fileSysWithExt: String = "Marimba.m4r"
        let fileSysPath: String = "/Library/Ringtones"
        let type = (_type == "" || _type == "_DEFAULT_" ? fileSysWithExt : _type) // --- you can't get default user perfrence sound in ios
        return self.getAudioUri(type, fileBundle, fileBundleExt, fileSysWithExt, fileSysPath, &self.bundleRingbackUri, &self.defaultRingbackUri)
    }

    func getBusytoneUri(_type: String) -> NSURL? {
        let fileBundle: String = "incallmanager_busytone"
        let fileBundleExt: String = "mp3"
        let fileSysWithExt: String = "ct-busy.caf" //ct-congestion.caf
        let fileSysPath: String = "/System/Library/Audio/UISounds"
        let type = (_type == "" || _type == "_DEFAULT_" ? fileSysWithExt : _type) // --- you can't get default user perfrence sound in ios
        return self.getAudioUri(type, fileBundle, fileBundleExt, fileSysWithExt, fileSysPath, &self.bundleBusytoneUri, &self.defaultBusytoneUri)
    }

    func getRingtoneUri(_type: String) -> NSURL? {
        let fileBundle: String = "incallmanager_ringtone"
        let fileBundleExt: String = "mp3"
        let fileSysWithExt: String = "Opening.m4r" //Marimba.m4r
        let fileSysPath: String = "/Library/Ringtones"
        let type = (_type == "" || _type == "_DEFAULT_" ? fileSysWithExt : _type) // --- you can't get default user perfrence sound in ios
        return self.getAudioUri(type, fileBundle, fileBundleExt, fileSysWithExt, fileSysPath, &self.bundleRingtoneUri, &self.defaultRingtoneUri)
    }

    func getAudioUri(_type: String, _ fileBundle: String, _ fileBundleExt: String, _ fileSysWithExt: String, _ fileSysPath: String, inout _ uriBundle: NSURL!, inout _ uriDefault: NSURL!) -> NSURL? {
        var type = _type
        if type == "_BUNDLE_" {
            if uriBundle == nil {
                uriBundle = NSBundle.mainBundle().URLForResource(fileBundle, withExtension: fileBundleExt)
                if uriBundle == nil {
                    NSLog("RNInCallManager.getAudioUri(): \(fileBundle).\(fileBundleExt) not found in bundle.")
                    type = fileSysWithExt
                } else {
                    return uriBundle
                }
            } else {
                return uriBundle
            }
        }
        
        if uriDefault == nil {
            let target: String = "\(fileSysPath)/\(type)"
            uriDefault = self.getSysFileUri(target)
        }
        return uriDefault
    }

    func getSysFileUri(target: String) -> NSURL? {
        if let url: NSURL? = NSURL(fileURLWithPath: target, isDirectory: false) {
            if let path = url?.path {
                let fileManager: NSFileManager = NSFileManager()
                var isTargetDirectory: ObjCBool = ObjCBool(false)
                if fileManager.fileExistsAtPath(path, isDirectory: &isTargetDirectory) {
                    if !isTargetDirectory {
                        return url
                    }
                }
            }
        }
        NSLog("RNInCallManager.getSysFileUri(): can not get url for \(target)")
        return nil
    }

    func audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully flag: Bool) -> Void {
        // --- this only called when all loop played. it means, an infinite (numberOfLoops = -1) loop will never into here.
        //if player.url!.isFileReferenceURL() {
        let filename = player.url?.URLByDeletingPathExtension?.lastPathComponent
        NSLog("RNInCallManager.audioPlayerDidFinishPlaying(): finished playing: \(filename)")
        if filename == self.bundleBusytoneUri?.URLByDeletingPathExtension?.lastPathComponent
            || filename == self.defaultBusytoneUri?.URLByDeletingPathExtension?.lastPathComponent {
            //self.stopBusytone()
            NSLog("RNInCallManager.audioPlayerDidFinishPlaying(): busytone finished, invoke stop()")
            self.stop("")
        }
    }

    func audioPlayerDecodeErrorDidOccur(player: AVAudioPlayer, error: NSError?) -> Void {
        let filename = player.url?.URLByDeletingPathExtension?.lastPathComponent
        NSLog("RNInCallManager.audioPlayerDecodeErrorDidOccur(): player=\(filename), error=\(error?.localizedDescription)")
    }

    // --- Deprecated in iOS 8.0.
    func audioPlayerBeginInterruption(player: AVAudioPlayer) -> Void {
        let filename = player.url?.URLByDeletingPathExtension?.lastPathComponent
        NSLog("RNInCallManager.audioPlayerBeginInterruption(): player=\(filename)")
    }

    // --- Deprecated in iOS 8.0.
    func audioPlayerEndInterruption(player: AVAudioPlayer) -> Void {
        let filename = player.url?.URLByDeletingPathExtension?.lastPathComponent
        NSLog("RNInCallManager.audioPlayerEndInterruption(): player=\(filename)")
    }

    func debugAudioSession() -> Void {
        let currentRoute: Dictionary <String,String> = ["input": self.audioSession.currentRoute.inputs[0].UID, "output": self.audioSession.currentRoute.outputs[0].UID]
        var categoryOptions = ""
        switch self.audioSession.categoryOptions {
            case AVAudioSessionCategoryOptions.MixWithOthers:
                categoryOptions = "MixWithOthers"
            case AVAudioSessionCategoryOptions.DuckOthers:
                categoryOptions = "DuckOthers"
            case AVAudioSessionCategoryOptions.AllowBluetooth:
                categoryOptions = "AllowBluetooth"
            case AVAudioSessionCategoryOptions.DefaultToSpeaker:
                categoryOptions = "DefaultToSpeaker"
            default:
                categoryOptions = "unknow"
        }
        if #available(iOS 9, *) {
            if categoryOptions == "unknow" && self.audioSession.categoryOptions == AVAudioSessionCategoryOptions.InterruptSpokenAudioAndMixWithOthers {
                categoryOptions = "InterruptSpokenAudioAndMixWithOthers"
            }
        }
        self._checkRecordPermission()
        var audioSessionProperties: Dictionary <String,Any> = [
            "category": self.audioSession.category,
            "categoryOptions": categoryOptions,
            "mode": self.audioSession.mode,
            //"inputAvailable": self.audioSession.inputAvailable,
            "otherAudioPlaying": self.audioSession.otherAudioPlaying,
            "recordPermission" : self.recordPermission,
            //"availableInputs": self.audioSession.availableInputs,
            //"preferredInput": self.audioSession.preferredInput,
            //"inputDataSources": self.audioSession.inputDataSources,
            //"inputDataSource": self.audioSession.inputDataSource,
            //"outputDataSources": self.audioSession.outputDataSources,
            //"outputDataSource": self.audioSession.outputDataSource,
            "currentRoute": currentRoute,
            "outputVolume": self.audioSession.outputVolume,
            "inputGain": self.audioSession.inputGain,
            "inputGainSettable": self.audioSession.inputGainSettable,
            "inputLatency": self.audioSession.inputLatency,
            "outputLatency": self.audioSession.outputLatency,
            "sampleRate": self.audioSession.sampleRate,
            "preferredSampleRate": self.audioSession.preferredSampleRate,
            "IOBufferDuration": self.audioSession.IOBufferDuration,
            "preferredIOBufferDuration": self.audioSession.preferredIOBufferDuration,
            "inputNumberOfChannels": self.audioSession.inputNumberOfChannels,
            "maximumInputNumberOfChannels": self.audioSession.maximumInputNumberOfChannels,
            "preferredInputNumberOfChannels": self.audioSession.preferredInputNumberOfChannels,
            "outputNumberOfChannels": self.audioSession.outputNumberOfChannels,
            "maximumOutputNumberOfChannels": self.audioSession.maximumOutputNumberOfChannels,
            "preferredOutputNumberOfChannels": self.audioSession.preferredOutputNumberOfChannels
        ]
        /*
        // --- Too noisy
        if #available(iOS 8, *) {
            //audioSessionProperties["secondaryAudioShouldBeSilencedHint"] = self.audioSession.secondaryAudioShouldBeSilencedHint
        } else {
            //audioSessionProperties["secondaryAudioShouldBeSilencedHint"] = "unknow"
        }
        if #available(iOS 9, *) {
            //audioSessionProperties["availableCategories"] = self.audioSession.availableCategories
            //audioSessionProperties["availableModes"] = self.audioSession.availableModes
        }
        */
        NSLog("RNInCallManager.debugAudioSession(): ==========BEGIN==========")
        // iterate over all keys
        for (key, value) in audioSessionProperties {
            NSLog("\(key) = \(value)")
        }
        NSLog("RNInCallManager.debugAudioSession(): ==========END==========")
    }

    @objc func checkRecordPermission(resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        self._checkRecordPermission()
        if self.recordPermission != nil {
            resolve(self.recordPermission)
        } else {
            reject("error_code", "error message", NSError(domain:"checkRecordPermission", code: 0, userInfo: nil))
        }
    }

    func _checkRecordPermission() {
        var recordPermission: String = "unsupported"
        var usingApi: String = ""
        if #available(iOS 8, *) {
            usingApi = "iOS8+"
            switch self.audioSession.recordPermission() {
                case AVAudioSessionRecordPermission.Granted:
                    recordPermission = "granted"
                case AVAudioSessionRecordPermission.Denied:
                    recordPermission = "denied"
                case AVAudioSessionRecordPermission.Undetermined:
                    recordPermission = "undetermined"
                default:
                    recordPermission = "unknow"
            }
        } else {
            // --- target api at least iOS7+
            usingApi = "iOS7"
            recordPermission = self._checkMediaPermission(AVMediaTypeAudio)
        }
        self.recordPermission = recordPermission
        NSLog("RNInCallManager._checkRecordPermission(): using \(usingApi) api. recordPermission=\(self.recordPermission)")
    }

    @objc func requestRecordPermission(resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        NSLog("RNInCallManager.requestRecordPermission(): waiting for user confirmation...")
        self.audioSession.requestRecordPermission({(granted: Bool) -> Void in
            if granted {
                self.recordPermission = "granted"
            } else {
                self.recordPermission = "denied"
            }
            NSLog("RNInCallManager.requestRecordPermission(): \(self.recordPermission)")
            resolve(self.recordPermission)
        })
    }

    @objc func checkCameraPermission(resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        self._checkCameraPermission()
        if self.cameraPermission != nil {
            resolve(self.cameraPermission)
        } else {
            reject("error_code", "error message", NSError(domain:"checkCameraPermission", code: 0, userInfo: nil))
        }
    }

    func _checkCameraPermission() -> Void {
        self.cameraPermission = self._checkMediaPermission(AVMediaTypeVideo)
        NSLog("RNInCallManager._checkCameraPermission(): using iOS7 api. cameraPermission=\(self.cameraPermission)")
    }

    @objc func requestCameraPermission(resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        NSLog("RNInCallManager.requestCameraPermission(): waiting for user confirmation...")
        AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo, completionHandler: {(granted: Bool) -> Void in
            if granted {
                self.cameraPermission = "granted"
            } else {
                self.cameraPermission = "denied"
            }
            NSLog("RNInCallManager.requestCameraPermission(): \(self.cameraPermission)")
            resolve(self.cameraPermission)
        })
    }

    func _checkMediaPermission(targetMediaType: String) -> String {
        switch AVCaptureDevice.authorizationStatusForMediaType(targetMediaType) {
            case AVAuthorizationStatus.Authorized:
                return "granted"
            case AVAuthorizationStatus.Denied:
                return "denied"
            case AVAuthorizationStatus.NotDetermined:
                return "undetermined"
            case AVAuthorizationStatus.Restricted:
                return "restricted"
            default:
                return "unknow"
        }
    }

    func debugApplicationState() -> Void {
        var appState = "unknow"
        switch UIApplication.sharedApplication().applicationState {
            case UIApplicationState.Active:
                appState = "Active"
            case UIApplicationState.Inactive:
                appState = "Inactive"
            case UIApplicationState.Background:
                appState = "Background"
        }

        NSLog("RNInCallManage ZXCPOIU: appState: \(appState)")
    }
}
