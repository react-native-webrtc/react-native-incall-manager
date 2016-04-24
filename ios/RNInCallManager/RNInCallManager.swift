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
        print("start() InCallManager")
        self.storeOriginalAudioSetup()
        _ = try? self.audioSession.setActive(false)
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
            print("play busytone before stop InCallManager")
            return
        } else {
            print("stop() InCallManager")
            self.restoreOriginalAudioSetup()
            self.stopBusytone()
            self.stopProximitySensor()
            _ = try? self.audioSession.setActive(false, withOptions: .NotifyOthersOnDeactivation)
            self.setKeepScreenOn(false)
            NSNotificationCenter.defaultCenter().removeObserver(self)
            self.audioSessionInitialized = false
        }
    }

    @objc func turnScreenOn() -> Void {
        print("ios doesn't support turnScreenOn()")
    }

    @objc func turnScreenOff() -> Void {
        print("ios doesn't support turnScreenOff()")
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
        self.forceSpeakerOn = enable
        print("setForceSpeakerphoneOn(\(enable))")
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

    func getRingbackUri(_type: String) -> NSURL? {
        let fileBundle: String = "incallmanager_ringback"
        let fileBundleExt: String = "mp3"
        let fileSysWithExt: String = "vc~ringing.caf"
        let fileSysPath: String = "/System/Library/Audio/UISounds"
        let type = (_type == "" || _type == "_DEFAULT_" ? fileSysWithExt : _type)
        return self.getAudioUri(type, fileBundle, fileBundleExt, fileSysWithExt, fileSysPath, &self.bundleRingbackUri, &self.defaultRingbackUri)
    }

    func startRingback(ringbackUriType: String) -> Void {
        // you may rejected by apple when publish app if you use system sound instead of bundled sound.
        print("startRingback()")
        do {
            if self.mRingback != nil {
                if self.mRingback.playing {
                    return
                } else {
                    self.stopRingback()
                }
            }
            let ringbackUri: NSURL? = getRingbackUri(ringbackUriType)
            if ringbackUri == nil {
                print("no available ringback")
                return
            }
            //self.storeOriginalAudioSetup()
            self.mRingback = try AVAudioPlayer(contentsOfURL: ringbackUri!)
            self.mRingback.delegate = self
            self.mRingback.numberOfLoops = -1 // you need to stop it explicitly
            self.mRingback.prepareToPlay()
            //self.audioSession.setCategory(defaultAudioCategory, options: [.DefaultToSpeaker, .AllowBluetooth])

            _ = try? self.audioSession.setActive(false)
            _ = try? self.audioSession.setCategory(self.defaultAudioCategory)
            _ = try? self.audioSession.setMode(self.defaultAudioMode)
            _ = try? self.audioSession.setActive(true)
            self.mRingback.play()
        } catch {
            print("startRingtone() failed")
        }    
    }

    @objc func stopRingback() -> Void {
        if self.mRingback != nil {
            print("stopRingback()")
            self.mRingback.stop()
            self.mRingback = nil
            //self.restoreOriginalAudioSetup()
            //_ = try? self.audioSession.setActive(false, withOptions: .NotifyOthersOnDeactivation)
        }
    }

    func getBusytoneUri(_type: String) -> NSURL? {
        let fileBundle: String = "incallmanager_busytone"
        let fileBundleExt: String = "mp3"
        let fileSysWithExt: String = "ct-busy.caf" //ct-congestion.caf
        let fileSysPath: String = "/System/Library/Audio/UISounds"
        let type = (_type == "" || _type == "_DEFAULT_" ? fileSysWithExt : _type)
        return self.getAudioUri(type, fileBundle, fileBundleExt, fileSysWithExt, fileSysPath, &self.bundleBusytoneUri, &self.defaultBusytoneUri)
    }

    func startBusytone(busytoneUriType: String) -> Bool {
        // you may rejected by apple when publish app if you use system sound instead of bundled sound.
        print("startBusytone()")
        do {
            if self.mBusytone != nil {
                if self.mBusytone.playing {
                    return false
                } else {
                    self.stopBusytone()
                }
            }
            let busytoneUri: NSURL? = getBusytoneUri(busytoneUriType)
            if busytoneUri == nil {
                print("no available busytone")
                return false
            }
            //self.storeOriginalAudioSetup()
            self.mBusytone = try AVAudioPlayer(contentsOfURL: busytoneUri!)
            self.mBusytone.delegate = self
            self.mBusytone.numberOfLoops = 0 // it's part of start(), will stop at stop() 
            self.mBusytone.prepareToPlay()
            //self.audioSession.setCategory(defaultAudioCategory, options: [.DefaultToSpeaker, .AllowBluetooth])

            _ = try? self.audioSession.setActive(false)
            _ = try? self.audioSession.setCategory(self.defaultAudioCategory)
            _ = try? self.audioSession.setMode(self.defaultAudioMode)
            _ = try? self.audioSession.setActive(true)
            self.mBusytone.play()
        } catch {
            print("startRingtone() failed")
            return false
        }    
        return true
    }
    
    func stopBusytone() -> Void {
        if self.mBusytone != nil {
            print("stopBusytone()")
            self.mBusytone.stop()
            self.mBusytone = nil
            //self.restoreOriginalAudioSetup()
            //_ = try? self.audioSession.setActive(false, withOptions: .NotifyOthersOnDeactivation)
        }
    }

    func getRingtoneUri(_type: String) -> NSURL? {
        let fileBundle: String = "incallmanager_ringtone"
        let fileBundleExt: String = "mp3"
        let fileSysWithExt: String = "Opening.m4r" //Marimba.m4r
        let fileSysPath: String = "/Library/Ringtones"
        let type = (_type == "" || _type == "_DEFAULT_" ? fileSysWithExt : _type)
        return self.getAudioUri(type, fileBundle, fileBundleExt, fileSysWithExt, fileSysPath, &self.bundleRingtoneUri, &self.defaultRingtoneUri)
    }

    @objc func startRingtone(ringtoneUriType: String) -> Void {
        // you may rejected by apple when publish app if you use system sound instead of bundled sound.
        print("startRingtone()");
        do {
            if self.mRingtone != nil {
                if self.mRingtone.playing {
                    return
                } else {
                    self.stopRingtone()
                }
            }
            let ringtoneUri: NSURL? = getRingtoneUri(ringtoneUriType)
            if ringtoneUri == nil {
                print("no available ringtone")
                return
            }
            self.storeOriginalAudioSetup()
            self.mRingtone = try AVAudioPlayer(contentsOfURL: ringtoneUri!)
            self.mRingtone.delegate = self
            self.mRingtone.numberOfLoops = -1 // you need to stop it explicitly
            self.mRingtone.prepareToPlay()
            //self.audioSession.setCategory(defaultAudioCategory, options: [.DefaultToSpeaker, .AllowBluetooth])

            _ = try? self.audioSession.setActive(false)
            _ = try? self.audioSession.setCategory(AVAudioSessionCategorySoloAmbient)
            _ = try? self.audioSession.setMode(AVAudioSessionModeDefault)
            _ = try? self.audioSession.setActive(true)
            self.mRingtone.play()
        } catch {
            print("startRingtone() failed")
        }    
    }

    @objc func stopRingtone() -> Void {
        if self.mRingtone != nil {
            print("stopRingtone()")
            self.mRingtone.stop()
            self.mRingtone = nil
            self.restoreOriginalAudioSetup()
            _ = try? self.audioSession.setActive(false, withOptions: .NotifyOthersOnDeactivation)
        }
    }

    func getAudioUri(_type: String, _ fileBundle: String, _ fileBundleExt: String, _ fileSysWithExt: String, _ fileSysPath: String, inout _ uriBundle: NSURL!, inout _ uriDefault: NSURL!) -> NSURL? {
        var type = _type
        if type == "_BUNDLE_" {
            if uriBundle == nil {
                uriBundle = NSBundle.mainBundle().URLForResource(fileBundle, withExtension: fileBundleExt)
                if uriBundle == nil {
                    print("getAudioUri() \(fileBundle).\(fileBundleExt) not found in bundle.")
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
            if uriDefault == nil {
                return nil
            }
        }
        return uriDefault;
    }

    func getSysFileUri(target: String) -> NSURL? {
        let fileManager: NSFileManager = NSFileManager()
        let url: NSURL = NSURL(fileURLWithPath: target, isDirectory: false)
        var isTargetDirectory: ObjCBool = ObjCBool(false)
        if fileManager.fileExistsAtPath(url.path!, isDirectory: &isTargetDirectory) {
            if !isTargetDirectory {
                //print("\(url.URLByDeletingPathExtension?.lastPathComponent)")
                return url
            }
        }
        return nil
    }

    func audioPlayerDidFinishPlaying(player: AVAudioPlayer!, successfully flag: Bool) {
        // --- this only called when all loop played. it means, an infinite (numberOfLoops = -1) loop will never into here.
        //if player.url!.isFileReferenceURL() {
        let filename = player.url?.URLByDeletingPathExtension?.lastPathComponent
        if filename == self.bundleBusytoneUri?.URLByDeletingPathExtension?.lastPathComponent
            || filename == self.defaultBusytoneUri?.URLByDeletingPathExtension?.lastPathComponent {
            self.stopBusytone()
            self.stop("")
        }
        print("finished playing: \(filename)")
    }

    func audioPlayerDecodeErrorDidOccur(player: AVAudioPlayer!, error: NSError!) {
        print("\(error.localizedDescription)")
    }

    // --- Deprecated in iOS 8.0.
    func audioPlayerBeginInterruption(player: AVAudioPlayer!) {
    }

    // --- Deprecated in iOS 8.0.
    func audioPlayerEndInterruption(player: AVAudioPlayer!) {
    }

}


