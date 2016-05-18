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
    var isProximityRegistered: Bool = false
    var proximityIsNear: Bool = false
    var proximityObserver: NSObjectProtocol?
    var defaultAudioMode: String = AVAudioSessionModeVoiceChat
    var defaultAudioCategory: String = AVAudioSessionCategoryPlayAndRecord
    var origAudioCategory: String!
    var origAudioMode: String!
    var audioSessionInitialized: Bool = false
    let automatic: Bool = true
    var forceSpeakerOn: Int = 0 //UInt8?
  
    //@objc func initWithBridge(_bridge: RCTBridge) {
        //self.bridge = _bridge
    override init() {
        super.init()
        self.currentDevice = UIDevice.currentDevice()
        self.audioSession = AVAudioSession.sharedInstance()
        self.checkProximitySupport()
        NSLog("InCallManager initialized")
    }

    deinit {
        self.stop("")
    }

    @objc func start(media: String, auto: Bool, ringbackUriType: String) -> Void {
        guard !self.audioSessionInitialized else { return }

        // --- audo is always true on ios
        if media == "video" {
            self.defaultAudioMode = AVAudioSessionModeVideoChat
        } else {
            self.defaultAudioMode = AVAudioSessionModeVoiceChat
        }
        NSLog("start() InCallManager")
        self.storeOriginalAudioSetup()
        self.forceSpeakerOn = 0;
        //self.audioSession.setCategory(defaultAudioCategory, options: [.DefaultToSpeaker, .AllowBluetooth])
        _ = try? self.audioSession.setCategory(self.defaultAudioCategory)
        _ = try? self.audioSession.setMode(self.defaultAudioMode)
        _ = try? self.audioSession.setActive(true)
        if !(ringbackUriType ?? "").isEmpty {
            self.startRingback(ringbackUriType)
        }

        if media == "audio" {
            self.startProximitySensor()
        }
        self.setKeepScreenOn(true)
        self.audioSessionInitialized = true
    }

    @objc func stop(busytoneUriType: String) -> Void {
        guard self.audioSessionInitialized else { return }

        self.stopRingback()
        if !(busytoneUriType ?? "").isEmpty && self.startBusytone(busytoneUriType) {
            // play busytone first, and call this func again when finish
            NSLog("play busytone before stop InCallManager")
            return
        } else {
            NSLog("stop() InCallManager")
            self.restoreOriginalAudioSetup()
            self.stopBusytone()
            self.stopProximitySensor()
            _ = try? self.audioSession.setActive(false, withOptions: .NotifyOthersOnDeactivation)
            self.setKeepScreenOn(false)
            NSNotificationCenter.defaultCenter().removeObserver(self)
            self.forceSpeakerOn = 0;
            self.audioSessionInitialized = false
        }
    }

    @objc func turnScreenOn() -> Void {
        NSLog("ios doesn't support turnScreenOn()")
    }

    @objc func turnScreenOff() -> Void {
        NSLog("ios doesn't support turnScreenOff()")
    }

    func updateAudioRoute() -> Void {
        NSLog("ios doesn't support updateAudioRoute()")
    }

    @objc func setKeepScreenOn(enable: Bool) -> Void {
        UIApplication.sharedApplication().idleTimerDisabled = enable
    }

    @objc func setSpeakerphoneOn(enable: Bool) -> Void {
        NSLog("ios doesn't support setSpeakerphoneOn()")
    }

    @objc func setForceSpeakerphoneOn(flag: Int) -> Void {
        self.forceSpeakerOn = flag
        NSLog("setForceSpeakerphoneOn(\(flag))")
        if self.forceSpeakerOn == 1 { // force on
            _ = try? self.audioSession.overrideOutputAudioPort(AVAudioSessionPortOverride.Speaker)
            _ = try? self.audioSession.setMode(AVAudioSessionModeVideoChat)
        } else if self.forceSpeakerOn == -1 { //force off
            _ = try? self.audioSession.overrideOutputAudioPort(AVAudioSessionPortOverride.None)
            _ = try? self.audioSession.setMode(AVAudioSessionModeVoiceChat)
        } else { // use default behavior
            _ = try? self.audioSession.overrideOutputAudioPort(AVAudioSessionPortOverride.None)
            _ = try? self.audioSession.setMode(self.defaultAudioMode)
        }
    }

    @objc func setMicrophoneMute(enable: Bool) -> Void {
        NSLog("ios doesn't support setMicrophoneMute()")
    }

    func storeOriginalAudioSetup() -> Void {
        NSLog("storeOriginalAudioSetup()")
        self.origAudioCategory = self.audioSession.category 
        self.origAudioMode = self.audioSession.mode
    }

    func restoreOriginalAudioSetup() -> Void {
        NSLog("restoreOriginalAudioSetup()")
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
        NSLog("startProximitySensor()")
        self.currentDevice.proximityMonitoringEnabled = true

        self.stopObserve(self.proximityObserver, name: UIDeviceProximityStateDidChangeNotification, object: nil) // --- in case it didn't deallocate when ViewDidUnload
        self.proximityObserver = self.startObserve(UIDeviceProximityStateDidChangeNotification, object: self.currentDevice, queue: nil) { notification in
            let state: Bool = self.currentDevice.proximityState
            if state != self.proximityIsNear {
                NSLog("Proximity Changed. isNear: \(state)")
                self.proximityIsNear = state
                self.bridge.eventDispatcher.sendDeviceEventWithName("Proximity", body: ["isNear": state])
            }
        }
        
        self.isProximityRegistered = true
    }

    func stopProximitySensor() -> Void {
        guard self.isProximityRegistered else { return }

        NSLog("stopProximitySensor()")
        self.currentDevice.proximityMonitoringEnabled = false
        self.stopObserve(self.proximityObserver, name: UIDeviceProximityStateDidChangeNotification, object: nil) // --- remove all no matter what object
        self.isProximityRegistered = false
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
        NSLog("startRingback(): UriType=\(_ringbackUriType)")
        do {
            if self.mRingback != nil {
                if self.mRingback.playing {
                    NSLog("startRingback(): is already playing")
                    return
                } else {
                    self.stopRingback()
                }
            }
            // ios don't have embedded DTMF tone generator. use system dtmf sound files.
            let ringbackUriType: String = (_ringbackUriType == "_DTMF_" ? "_DEFAULT_" : _ringbackUriType)
            let ringbackUri: NSURL? = getRingbackUri(ringbackUriType)
            if ringbackUri == nil {
                NSLog("startRingback(): no available ringback")
                return
            }
            //self.storeOriginalAudioSetup()
            self.mRingback = try AVAudioPlayer(contentsOfURL: ringbackUri!)
            self.mRingback.delegate = self
            self.mRingback.numberOfLoops = -1 // you need to stop it explicitly
            self.mRingback.prepareToPlay()
            //self.audioSession.setCategory(defaultAudioCategory, options: [.DefaultToSpeaker, .AllowBluetooth])

            _ = try? self.audioSession.setCategory(self.defaultAudioCategory)
            _ = try? self.audioSession.setMode(self.defaultAudioMode)
            self.mRingback.play()
        } catch {
            NSLog("startRingtone() failed")
        }    
    }

    @objc func stopRingback() -> Void {
        if self.mRingback != nil {
            NSLog("stopRingback()")
            self.mRingback.stop()
            self.mRingback = nil
            //self.restoreOriginalAudioSetup()
            //_ = try? self.audioSession.setActive(false, withOptions: .NotifyOthersOnDeactivation)
        }
    }

    // --- _busytoneUriType: never go here with  be empty string.
    func startBusytone(_busytoneUriType: String) -> Bool {
        // you may rejected by apple when publish app if you use system sound instead of bundled sound.
        NSLog("startBusytone(): UriType=\(_busytoneUriType)")
        do {
            if self.mBusytone != nil {
                if self.mBusytone.playing {
                    NSLog("startBusytone(): is already playing")
                    return false
                } else {
                    self.stopBusytone()
                }
            }

            // ios don't have embedded DTMF tone generator. use system dtmf sound files.
            let busytoneUriType: String = (_busytoneUriType == "_DTMF_" ? "_DEFAULT_" : _busytoneUriType)
            let busytoneUri: NSURL? = getBusytoneUri(busytoneUriType)
            if busytoneUri == nil {
                NSLog("startBusytone(): no available media")
                return false
            }
            //self.storeOriginalAudioSetup()
            self.mBusytone = try AVAudioPlayer(contentsOfURL: busytoneUri!)
            self.mBusytone.delegate = self
            self.mBusytone.numberOfLoops = 0 // it's part of start(), will stop at stop() 
            self.mBusytone.prepareToPlay()
            //self.audioSession.setCategory(defaultAudioCategory, options: [.DefaultToSpeaker, .AllowBluetooth])

            _ = try? self.audioSession.setCategory(self.defaultAudioCategory)
            _ = try? self.audioSession.setMode(self.defaultAudioMode)
            self.mBusytone.play()
        } catch {
            NSLog("startRingtone() failed")
            return false
        }    
        return true
    }
    
    func stopBusytone() -> Void {
        if self.mBusytone != nil {
            NSLog("stopBusytone()")
            self.mBusytone.stop()
            self.mBusytone = nil
            //self.restoreOriginalAudioSetup()
            //_ = try? self.audioSession.setActive(false, withOptions: .NotifyOthersOnDeactivation)
        }
    }

    // --- ringtoneUriType May be empty
    @objc func startRingtone(ringtoneUriType: String) -> Void {
        // you may rejected by apple when publish app if you use system sound instead of bundled sound.
        NSLog("startRingtone(): UriType=\(ringtoneUriType)")
        do {
            if self.mRingtone != nil {
                if self.mRingtone.playing {
                    NSLog("startRingtone(): is already playing.")
                    return
                } else {
                    self.stopRingtone()
                }
            }
            let ringtoneUri: NSURL? = getRingtoneUri(ringtoneUriType)
            if ringtoneUri == nil {
                NSLog("startRingtone(): no available media")
                return
            }
            
            // --- ios has Ringer/Silent switch, so just play without check ringer volume.
            self.storeOriginalAudioSetup()
            self.mRingtone = try AVAudioPlayer(contentsOfURL: ringtoneUri!)
            self.mRingtone.delegate = self
            self.mRingtone.numberOfLoops = -1 // you need to stop it explicitly
            self.mRingtone.prepareToPlay()
            //self.audioSession.setCategory(defaultAudioCategory, options: [.DefaultToSpeaker, .AllowBluetooth])

            _ = try? self.audioSession.setCategory(AVAudioSessionCategorySoloAmbient)
            _ = try? self.audioSession.setMode(AVAudioSessionModeDefault)
            self.mRingtone.play()
        } catch {
            NSLog("startRingtone() failed")
        }    
    }

    @objc func stopRingtone() -> Void {
        if self.mRingtone != nil {
            NSLog("stopRingtone()")
            self.mRingtone.stop()
            self.mRingtone = nil
            self.restoreOriginalAudioSetup()
            _ = try? self.audioSession.setActive(false, withOptions: .NotifyOthersOnDeactivation)
        }
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
                    NSLog("getAudioUri() \(fileBundle).\(fileBundleExt) not found in bundle.")
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
        NSLog("can not get url for \(target)")
        return nil
    }

    func audioPlayerDidFinishPlaying(player: AVAudioPlayer!, successfully flag: Bool) {
        // --- this only called when all loop played. it means, an infinite (numberOfLoops = -1) loop will never into here.
        //if player.url!.isFileReferenceURL() {
        let filename = player.url?.URLByDeletingPathExtension?.lastPathComponent
        NSLog("finished playing: \(filename)")
        if filename == self.bundleBusytoneUri?.URLByDeletingPathExtension?.lastPathComponent
            || filename == self.defaultBusytoneUri?.URLByDeletingPathExtension?.lastPathComponent {
            //self.stopBusytone()
            NSLog("busytone finished, invoke stop()")
            self.stop("")
        }
    }

    func audioPlayerDecodeErrorDidOccur(player: AVAudioPlayer!, error: NSError!) {
        NSLog("\(error.localizedDescription)")
    }

    // --- Deprecated in iOS 8.0.
    func audioPlayerBeginInterruption(player: AVAudioPlayer!) {
    }

    // --- Deprecated in iOS 8.0.
    func audioPlayerEndInterruption(player: AVAudioPlayer!) {
    }

}
