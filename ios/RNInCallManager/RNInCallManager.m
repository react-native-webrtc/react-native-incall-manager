//
//  RNInCallManager.m
//  RNInCallManager
//
//  Created by Ian Yu-Hsun Lin (@ianlin) on 05/12/2017.
//  Copyright © 2017 zxcpoiu. All rights reserved.
//

#import "RNInCallManager.h"

#import <React/RCTBridge.h>
#import <React/RCTConvert.h>
#import <React/RCTEventDispatcher.h>
#import <React/RCTUtils.h>

//static BOOL const automatic = YES;

@implementation RNInCallManager
{
    UIDevice *_currentDevice;

    AVAudioSession *_audioSession;
    AVAudioPlayer *_ringtone;
    AVAudioPlayer *_ringback;
    AVAudioPlayer *_busytone;

    NSURL *_defaultRingtoneUri;
    NSURL *_defaultRingbackUri;
    NSURL *_defaultBusytoneUri;
    NSURL *_bundleRingtoneUri;
    NSURL *_bundleRingbackUri;
    NSURL *_bundleBusytoneUri;

    //BOOL isProximitySupported;
    BOOL _proximityIsNear;

    // --- tags to indicating which observer has added
    BOOL _isProximityRegistered;
    BOOL _isAudioSessionInterruptionRegistered;
    BOOL _isAudioSessionRouteChangeRegistered;
    BOOL _isAudioSessionMediaServicesWereLostRegistered;
    BOOL _isAudioSessionMediaServicesWereResetRegistered;
    BOOL _isAudioSessionSilenceSecondaryAudioHintRegistered;

    // -- notification observers
    id _proximityObserver;
    id _audioSessionInterruptionObserver;
    id _audioSessionRouteChangeObserver;
    id _audioSessionMediaServicesWereLostObserver;
    id _audioSessionMediaServicesWereResetObserver;
    id _audioSessionSilenceSecondaryAudioHintObserver;

    NSString *_incallAudioMode;
    NSString *_incallAudioCategory;
    NSString *_origAudioCategory;
    NSString *_origAudioMode;
    BOOL _audioSessionInitialized;
    int _forceSpeakerOn;
    NSString *_media;
}

+ (BOOL)requiresMainQueueSetup
{
    return NO;
}

RCT_EXPORT_MODULE(InCallManager)

- (instancetype)init
{
    if (self = [super init]) {
        _currentDevice = [UIDevice currentDevice];
        _audioSession = [AVAudioSession sharedInstance];
        _ringtone = nil;
        _ringback = nil;
        _busytone = nil;

        _defaultRingtoneUri = nil;
        _defaultRingbackUri = nil;
        _defaultBusytoneUri = nil;
        _bundleRingtoneUri = nil;
        _bundleRingbackUri = nil;
        _bundleBusytoneUri = nil;

        _proximityIsNear = NO;

        _isProximityRegistered = NO;
        _isAudioSessionInterruptionRegistered = NO;
        _isAudioSessionRouteChangeRegistered = NO;
        _isAudioSessionMediaServicesWereLostRegistered = NO;
        _isAudioSessionMediaServicesWereResetRegistered = NO;
        _isAudioSessionSilenceSecondaryAudioHintRegistered = NO;

        _proximityObserver = nil;
        _audioSessionInterruptionObserver = nil;
        _audioSessionRouteChangeObserver = nil;
        _audioSessionMediaServicesWereLostObserver = nil;
        _audioSessionMediaServicesWereResetObserver = nil;
        _audioSessionSilenceSecondaryAudioHintObserver = nil;

        _incallAudioMode = AVAudioSessionModeVoiceChat;
        _incallAudioCategory = AVAudioSessionCategoryPlayAndRecord;
        _origAudioCategory = nil;
        _origAudioMode = nil;
        _audioSessionInitialized = NO;
        _forceSpeakerOn = 0;
        _media = @"audio";

        NSLog(@"RNInCallManager.init(): initialized");
    }
    return self;
}

- (void)dealloc
{
    [[NSNotificationCenter defaultCenter] removeObserver:self];
    [self stop:@""];
}

- (NSArray<NSString *> *)supportedEvents
{
    return @[@"Proximity",
             @"WiredHeadset"];
}

RCT_EXPORT_METHOD(start:(NSString *)mediaType
                   auto:(BOOL)_auto
        ringbackUriType:(NSString *)ringbackUriType)
{
    if (_audioSessionInitialized) {
        return;
    }
    _media = mediaType;

    // --- auto is always true on ios
    if ([_media isEqualToString:@"video"]) {
        _incallAudioMode = AVAudioSessionModeVideoChat;
    } else {
        _incallAudioMode = AVAudioSessionModeVoiceChat;
    }
    NSLog(@"RNInCallManager.start() start InCallManager. media=%@, type=%@, mode=%@", _media, _media, _incallAudioMode);
    [self storeOriginalAudioSetup];
    _forceSpeakerOn = 0;
    [self startAudioSessionNotification];
    [self audioSessionSetCategory:_incallAudioCategory
                          options:0
                       callerMemo:NSStringFromSelector(_cmd)];
    [self audioSessionSetMode:_incallAudioMode
                   callerMemo:NSStringFromSelector(_cmd)];
    [self audioSessionSetActive:YES
                        options:0
                     callerMemo:NSStringFromSelector(_cmd)];

    if (ringbackUriType.length > 0) {
        NSLog(@"RNInCallManager.start() play ringback first. type=%@", ringbackUriType);
        [self startRingback:ringbackUriType];
    }

    if ([_media isEqualToString:@"audio"]) {
        [self startProximitySensor];
    }
    [self setKeepScreenOn:YES];
    _audioSessionInitialized = YES;
    //self.debugAudioSession()
}

RCT_EXPORT_METHOD(stop:(NSString *)busytoneUriType)
{
    if (!_audioSessionInitialized) {
        return;
    }

    [self stopRingback];

    if (busytoneUriType.length > 0 && [self startBusytone:busytoneUriType]) {
        // play busytone first, and call this func again when finish
        NSLog(@"RNInCallManager.stop(): play busytone before stop");
        return;
    } else {
        NSLog(@"RNInCallManager.stop(): stop InCallManager");
        [self restoreOriginalAudioSetup];
        [self stopBusytone];
        [self stopProximitySensor];
        [self audioSessionSetActive:NO
                            options:AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
                         callerMemo:NSStringFromSelector(_cmd)];
        [self setKeepScreenOn:NO];
        [self stopAudioSessionNotification];
        [[NSNotificationCenter defaultCenter] removeObserver:self];
        _forceSpeakerOn = 0;
        _audioSessionInitialized = NO;
    }
}

RCT_EXPORT_METHOD(turnScreenOn)
{
    NSLog(@"RNInCallManager.turnScreenOn(): ios doesn't support turnScreenOn()");
}

RCT_EXPORT_METHOD(turnScreenOff)
{
    NSLog(@"RNInCallManager.turnScreenOff(): ios doesn't support turnScreenOff()");
}

RCT_EXPORT_METHOD(setFlashOn:(BOOL)enable
                  brightness:(nonnull NSNumber *)brightness)
{
    if ([AVCaptureDevice class]) {
        AVCaptureDevice *device = [AVCaptureDevice defaultDeviceWithMediaType:AVMediaTypeVideo];
        if (device.hasTorch && device.position == AVCaptureDevicePositionBack) {
            @try {
                [device lockForConfiguration:nil];

                if (enable) {
                    [device setTorchMode:AVCaptureTorchModeOn];
                } else {
                    [device setTorchMode:AVCaptureTorchModeOff];
                }

                [device unlockForConfiguration];
            } @catch (NSException *e) {}
        }
    }
}

RCT_EXPORT_METHOD(setKeepScreenOn:(BOOL)enable)
{
    NSLog(@"RNInCallManager.setKeepScreenOn(): enable: %@", enable ? @"YES" : @"NO");
    dispatch_async(dispatch_get_main_queue(), ^{
        [[UIApplication sharedApplication] setIdleTimerDisabled:enable];
    });
}

RCT_EXPORT_METHOD(setSpeakerphoneOn:(BOOL)enable)
{
    BOOL success;
    NSError *error = nil;
    NSArray* routes = [_audioSession availableInputs];

    if(!enable){
        NSLog(@"Routing audio via Earpiece");
        @try {
            success = [_audioSession setCategory:AVAudioSessionCategoryPlayAndRecord error:&error];
            if (!success)  NSLog(@"Cannot set category due to error: %@", error);
            success = [_audioSession setMode:AVAudioSessionModeVoiceChat error:&error];
            if (!success)  NSLog(@"Cannot set mode due to error: %@", error);
            [_audioSession setPreferredOutputNumberOfChannels:0 error:nil];
            if (!success)  NSLog(@"Port override failed due to: %@", error);
            [_audioSession overrideOutputAudioPort:[AVAudioSessionPortBuiltInReceiver intValue] error:&error];
            success = [_audioSession setActive:YES error:&error];
            if (!success) NSLog(@"Audio session override failed: %@", error);
            else NSLog(@"AudioSession override is successful ");

        } @catch (NSException *e) {
            NSLog(@"Error occurred while routing audio via Earpiece: %@", e.reason);
        }
    } else {
        NSLog(@"Routing audio via Loudspeaker");
        @try {
            NSLog(@"Available routes: %@", routes[0]);
            success = [_audioSession setCategory:AVAudioSessionCategoryPlayAndRecord
                        withOptions:AVAudioSessionCategoryOptionDefaultToSpeaker
                        error:nil];
            if (!success)  NSLog(@"Cannot set category due to error: %@", error);
            success = [_audioSession setMode:AVAudioSessionModeVoiceChat error: &error];
            if (!success)  NSLog(@"Cannot set mode due to error: %@", error);
            [_audioSession setPreferredOutputNumberOfChannels:0 error:nil];
            [_audioSession overrideOutputAudioPort:[AVAudioSessionPortBuiltInSpeaker intValue] error: &error];
            if (!success)  NSLog(@"Port override failed due to: %@", error);
            success = [_audioSession setActive:YES error:&error];
            if (!success) NSLog(@"Audio session override failed: %@", error);
            else NSLog(@"AudioSession override is successful ");
        } @catch (NSException *e) {
            NSLog(@"Error occurred while routing audio via Loudspeaker: %@", e.reason);
        }
    }
}

RCT_EXPORT_METHOD(setForceSpeakerphoneOn:(int)flag)
{
    _forceSpeakerOn = flag;
    NSLog(@"RNInCallManager.setForceSpeakerphoneOn(): flag: %d", flag);
    [self updateAudioRoute];
}

RCT_EXPORT_METHOD(setMicrophoneMute:(BOOL)enable)
{
    NSLog(@"RNInCallManager.setMicrophoneMute(): ios doesn't support setMicrophoneMute()");
}

RCT_EXPORT_METHOD(startRingback:(NSString *)_ringbackUriType)
{
    // you may rejected by apple when publish app if you use system sound instead of bundled sound.
    NSLog(@"RNInCallManager.startRingback(): type=%@", _ringbackUriType);

    @try {
        if (_ringback != nil) {
            if ([_ringback isPlaying]) {
                NSLog(@"RNInCallManager.startRingback(): is already playing");
                return;
            } else {
                [self stopRingback];
            }
        }
        // ios don't have embedded DTMF tone generator. use system dtmf sound files.
        NSString *ringbackUriType = [_ringbackUriType isEqualToString:@"_DTMF_"]
            ? @"_DEFAULT_"
            : _ringbackUriType;
        NSURL *ringbackUri = [self getRingbackUri:ringbackUriType];
        if (ringbackUri == nil) {
            NSLog(@"RNInCallManager.startRingback(): no available media");
            return;
        }
        //self.storeOriginalAudioSetup()
        _ringback = [[AVAudioPlayer alloc] initWithContentsOfURL:ringbackUri error:nil];
        _ringback.delegate = self;
        _ringback.numberOfLoops = -1; // you need to stop it explicitly
        [_ringback prepareToPlay];

        //self.audioSessionSetCategory(self.incallAudioCategory, [.DefaultToSpeaker, .AllowBluetooth], #function)
        [self audioSessionSetCategory:_incallAudioCategory
                              options:0
                           callerMemo:NSStringFromSelector(_cmd)];
        [self audioSessionSetMode:_incallAudioMode
                       callerMemo:NSStringFromSelector(_cmd)];
        [_ringback play];
    } @catch (NSException *e) {
        NSLog(@"RNInCallManager.startRingback(): caught error=%@", e.reason);
    }
}

RCT_EXPORT_METHOD(stopRingback)
{
    if (_ringback != nil) {
        NSLog(@"RNInCallManager.stopRingback()");
        [_ringback stop];
        _ringback = nil;
        // --- need to reset route based on config because WebRTC seems will switch audio mode automatically when call established.
        //[self updateAudioRoute];
    }
}

RCT_EXPORT_METHOD(startRingtone:(NSString *)ringtoneUriType
               ringtoneCategory:(NSString *)ringtoneCategory)
{
    // you may rejected by apple when publish app if you use system sound instead of bundled sound.
    NSLog(@"RNInCallManager.startRingtone(): type: %@", ringtoneUriType);
    @try {
        if (_ringtone != nil) {
            if ([_ringtone isPlaying]) {
                NSLog(@"RNInCallManager.startRingtone(): is already playing.");
                return;
            } else {
                [self stopRingtone];
            }
        }
        NSURL *ringtoneUri = [self getRingtoneUri:ringtoneUriType];
        if (ringtoneUri == nil) {
            NSLog(@"RNInCallManager.startRingtone(): no available media");
            return;
        }

        // --- ios has Ringer/Silent switch, so just play without check ringer volume.
        [self storeOriginalAudioSetup];
        _ringtone = [[AVAudioPlayer alloc] initWithContentsOfURL:ringtoneUri error:nil];
        _ringtone.delegate = self;
        _ringtone.numberOfLoops = -1; // you need to stop it explicitly
        [_ringtone prepareToPlay];

        // --- 1. if we use Playback, it can supports background playing (starting from foreground), but it would not obey Ring/Silent switch.
        // ---    make sure you have enabled 'audio' tag ( or 'voip' tag ) at XCode -> Capabilities -> BackgroundMode
        // --- 2. if we use SoloAmbient, it would obey Ring/Silent switch in the foreground, but does not support background playing,
        // ---    thus, then you should play ringtone again via local notification after back to home during a ring session.

        // we prefer 2. by default, since most of users doesn't want to interrupted by a ringtone if Silent mode is on.

        //self.audioSessionSetCategory(AVAudioSessionCategoryPlayback, [.DuckOthers], #function)
        if ([ringtoneCategory isEqualToString:@"playback"]) {
            [self audioSessionSetCategory:AVAudioSessionCategoryPlayback
                                  options:0
                               callerMemo:NSStringFromSelector(_cmd)];
        } else {
            [self audioSessionSetCategory:AVAudioSessionCategorySoloAmbient
                                  options:0
                               callerMemo:NSStringFromSelector(_cmd)];
        }
        [self audioSessionSetMode:AVAudioSessionModeDefault
                       callerMemo:NSStringFromSelector(_cmd)];
        //[self audioSessionSetActive:YES
        //                    options:nil
        //                 callerMemo:NSStringFromSelector(_cmd)];
        [_ringtone play];
    } @catch (NSException *e) {
        NSLog(@"RNInCallManager.startRingtone(): caught error = %@", e.reason);
    }
}

RCT_EXPORT_METHOD(stopRingtone)
{
    if (_ringtone != nil) {
        NSLog(@"RNInCallManager.stopRingtone()");
        [_ringtone stop];
        _ringtone = nil;
        [self restoreOriginalAudioSetup];
        [self audioSessionSetActive:NO
                            options:AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
                         callerMemo:NSStringFromSelector(_cmd)];
    }
}

RCT_EXPORT_METHOD(getAudioUriJS:(NSString *)audioType
                       fileType:(NSString *)fileType
                        resolve:(RCTPromiseResolveBlock)resolve
                         reject:(RCTPromiseRejectBlock)reject)
{
    NSURL *result = nil;
    if ([audioType isEqualToString:@"ringback"]) {
        result = [self getRingbackUri:fileType];
    } else if ([audioType isEqualToString:@"busytone"]) {
        result = [self getBusytoneUri:fileType];
    } else if ([audioType isEqualToString:@"ringtone"]) {
        result = [self getRingtoneUri:fileType];
    }
    if (result != nil) {
        if (result.absoluteString.length > 0) {
            resolve(result.absoluteString);
            return;
        }
    }
    reject(@"error_code", @"getAudioUriJS() failed", RCTErrorWithMessage(@"getAudioUriJS() failed"));
}

RCT_EXPORT_METHOD(getIsWiredHeadsetPluggedIn:(RCTPromiseResolveBlock)resolve
                                      reject:(RCTPromiseRejectBlock)reject)
{
    BOOL wiredHeadsetPluggedIn = [self isWiredHeadsetPluggedIn];
    resolve(@{
        @"isWiredHeadsetPluggedIn": wiredHeadsetPluggedIn ? @YES : @NO,
    });
}

- (void)updateAudioRoute
{
    NSLog(@"RNInCallManager.updateAudioRoute(): [Enter] forceSpeakerOn flag=%d media=%@ category=%@ mode=%@", _forceSpeakerOn, _media, _audioSession.category, _audioSession.mode);
    //self.debugAudioSession()

    //AVAudioSessionPortOverride overrideAudioPort;
    int overrideAudioPort;
    NSString *overrideAudioPortString = @"";
    NSString *audioMode = @"";

    // --- WebRTC native code will change audio mode automatically when established.
    // --- It would have some race condition if we change audio mode with webrtc at the same time.
    // --- So we should not change audio mode as possible as we can. Only when default video call which wants to force speaker off.
    // --- audio: only override speaker on/off; video: should change category if needed and handle proximity sensor. ( because default proximity is off when video call )
    if (_forceSpeakerOn == 1) {
        // --- force ON, override speaker only, keep audio mode remain.
        overrideAudioPort = AVAudioSessionPortOverrideSpeaker;
        overrideAudioPortString = @".Speaker";
        if ([_media isEqualToString:@"video"]) {
            audioMode = AVAudioSessionModeVideoChat;
            [self stopProximitySensor];
        }
    } else if (_forceSpeakerOn == -1) {
        // --- force off
        overrideAudioPort = AVAudioSessionPortOverrideNone;
        overrideAudioPortString = @".None";
        if ([_media isEqualToString:@"video"]) {
            audioMode = AVAudioSessionModeVoiceChat;
            [self startProximitySensor];
        }
    } else { // use default behavior
        overrideAudioPort = AVAudioSessionPortOverrideNone;
        overrideAudioPortString = @".None";
        if ([_media isEqualToString:@"video"]) {
            audioMode = AVAudioSessionModeVideoChat;
            [self stopProximitySensor];
        }
    }

    BOOL isCurrentRouteToSpeaker;
    isCurrentRouteToSpeaker = [self checkAudioRoute:@[AVAudioSessionPortBuiltInSpeaker]
                                               routeType:@"output"];
    if ((overrideAudioPort == AVAudioSessionPortOverrideSpeaker && !isCurrentRouteToSpeaker)
            || (overrideAudioPort == AVAudioSessionPortOverrideNone && isCurrentRouteToSpeaker)) {
        @try {
            [_audioSession overrideOutputAudioPort:overrideAudioPort error:nil];
            NSLog(@"RNInCallManager.updateAudioRoute(): audioSession.overrideOutputAudioPort(%@) success", overrideAudioPortString);
        } @catch (NSException *e) {
            NSLog(@"RNInCallManager.updateAudioRoute(): audioSession.overrideOutputAudioPort(%@) fail: %@", overrideAudioPortString, e.reason);
        }
    } else {
        NSLog(@"RNInCallManager.updateAudioRoute(): did NOT overrideOutputAudioPort()");
    }

    if (audioMode.length > 0 && ![_audioSession.mode isEqualToString:audioMode]) {
        [self audioSessionSetMode:audioMode
                       callerMemo:NSStringFromSelector(_cmd)];
        NSLog(@"RNInCallManager.updateAudioRoute() audio mode has changed to %@", audioMode);
    } else {
        NSLog(@"RNInCallManager.updateAudioRoute() did NOT change audio mode");
    }
    //self.debugAudioSession()
}

- (BOOL)checkAudioRoute:(NSArray<NSString *> *)targetPortTypeArray
              routeType:(NSString *)routeType
{
    AVAudioSessionRouteDescription *currentRoute = _audioSession.currentRoute;

    if (currentRoute != nil) {
        NSArray<AVAudioSessionPortDescription *> *routes = [routeType isEqualToString:@"input"]
            ? currentRoute.inputs
            : currentRoute.outputs;
        for (AVAudioSessionPortDescription *portDescription in routes) {
            if ([targetPortTypeArray containsObject:portDescription.portType]) {
                return YES;
            }
        }
    }
    return NO;
}

- (BOOL)startBusytone:(NSString *)_busytoneUriType
{
    // you may rejected by apple when publish app if you use system sound instead of bundled sound.
    NSLog(@"RNInCallManager.startBusytone(): type: %@", _busytoneUriType);
    @try {
        if (_busytone != nil) {
            if ([_busytone isPlaying]) {
                NSLog(@"RNInCallManager.startBusytone(): is already playing");
                return NO;
            } else {
                [self stopBusytone];
            }
        }

        // ios don't have embedded DTMF tone generator. use system dtmf sound files.
        NSString *busytoneUriType = [_busytoneUriType isEqualToString:@"_DTMF_"]
            ? @"_DEFAULT_"
            : _busytoneUriType;
        NSURL *busytoneUri = [self getBusytoneUri:busytoneUriType];
        if (busytoneUri == nil) {
            NSLog(@"RNInCallManager.startBusytone(): no available media");
            return NO;
        }
        //[self storeOriginalAudioSetup];
        _busytone = [[AVAudioPlayer alloc] initWithContentsOfURL:busytoneUri error:nil];
        _busytone.delegate = self;
        _busytone.numberOfLoops = 0; // it's part of start(), will stop at stop()
        [_busytone prepareToPlay];

        //self.audioSessionSetCategory(self.incallAudioCategory, [.DefaultToSpeaker, .AllowBluetooth], #function)
        [self audioSessionSetCategory:_incallAudioCategory
                              options:0
                           callerMemo:NSStringFromSelector(_cmd)];
        [self audioSessionSetMode:_incallAudioMode
                       callerMemo:NSStringFromSelector(_cmd)];
        [_busytone play];
    } @catch (NSException *e) {
        NSLog(@"RNInCallManager.startBusytone(): caught error = %@", e.reason);
        return NO;
    }
    return YES;
}

- (void)stopBusytone
{
    if (_busytone != nil) {
        NSLog(@"RNInCallManager.stopBusytone()");
        [_busytone stop];
        _busytone = nil;
    }
}

- (BOOL)isWiredHeadsetPluggedIn
{
    // --- only check for a audio device plugged into headset port instead bluetooth/usb/hdmi
    return [self checkAudioRoute:@[AVAudioSessionPortHeadphones]
                       routeType:@"output"]
        || [self checkAudioRoute:@[AVAudioSessionPortHeadsetMic]
                       routeType:@"input"];
}

- (void)audioSessionSetCategory:(NSString *)audioCategory
                        options:(AVAudioSessionCategoryOptions)options
                     callerMemo:(NSString *)callerMemo
{
    @try {
        if (options != 0) {
            [_audioSession setCategory:audioCategory
                           withOptions:options
                                 error:nil];
        } else {
            [_audioSession setCategory:audioCategory
                                 error:nil];
        }
        NSLog(@"RNInCallManager.%@: audioSession.setCategory: %@, withOptions: %lu success", callerMemo, audioCategory, (unsigned long)options);
    } @catch (NSException *e) {
        NSLog(@"RNInCallManager.%@: audioSession.setCategory: %@, withOptions: %lu fail: %@", callerMemo, audioCategory, (unsigned long)options, e.reason);
    }
}

- (void)audioSessionSetMode:(NSString *)audioMode
                 callerMemo:(NSString *)callerMemo
{
    @try {
        [_audioSession setMode:audioMode error:nil];
        NSLog(@"RNInCallManager.%@: audioSession.setMode(%@) success", callerMemo, audioMode);
    } @catch (NSException *e) {
        NSLog(@"RNInCallManager.%@: audioSession.setMode(%@) fail: %@", callerMemo, audioMode, e.reason);
    }
}

- (void)audioSessionSetActive:(BOOL)audioActive
                   options:(AVAudioSessionSetActiveOptions)options
                   callerMemo:(NSString *)callerMemo
{
    @try {
        if (options != 0) {
            [_audioSession setActive:audioActive
                         withOptions:options
                               error:nil];
        } else {
            [_audioSession setActive:audioActive
                               error:nil];
        }
        NSLog(@"RNInCallManager.%@: audioSession.setActive(%@), withOptions: %lu success", callerMemo, audioActive ? @"YES" : @"NO", (unsigned long)options);
    } @catch (NSException *e) {
        NSLog(@"RNInCallManager.%@: audioSession.setActive(%@), withOptions: %lu fail: %@", callerMemo, audioActive ? @"YES" : @"NO", (unsigned long)options, e.reason);
    }
}

- (void)storeOriginalAudioSetup
{
    NSLog(@"RNInCallManager.storeOriginalAudioSetup(): origAudioCategory=%@, origAudioMode=%@", _audioSession.category, _audioSession.mode);
    _origAudioCategory = _audioSession.category;
    _origAudioMode = _audioSession.mode;
}

- (void)restoreOriginalAudioSetup
{
    NSLog(@"RNInCallManager.restoreOriginalAudioSetup(): origAudioCategory=%@, origAudioMode=%@", _audioSession.category, _audioSession.mode);
    [self audioSessionSetCategory:_origAudioCategory
                          options:0
                       callerMemo:NSStringFromSelector(_cmd)];
    [self audioSessionSetMode:_origAudioMode
                   callerMemo:NSStringFromSelector(_cmd)];
}

- (void)startProximitySensor
{
    if (_isProximityRegistered) {
        return;
    }

    NSLog(@"RNInCallManager.startProximitySensor()");
    dispatch_async(dispatch_get_main_queue(), ^{
        self->_currentDevice.proximityMonitoringEnabled = YES;
    });

    // --- in case it didn't deallocate when ViewDidUnload
    [self stopObserve:_proximityObserver
                 name:UIDeviceProximityStateDidChangeNotification
               object:nil];

    _proximityObserver = [self startObserve:UIDeviceProximityStateDidChangeNotification
                                     object:_currentDevice
                                      queue: nil
                                      block:^(NSNotification *notification) {
        BOOL state = self->_currentDevice.proximityState;
        if (state != self->_proximityIsNear) {
            NSLog(@"RNInCallManager.UIDeviceProximityStateDidChangeNotification(): isNear: %@", state ? @"YES" : @"NO");
            self->_proximityIsNear = state;
            [self sendEventWithName:@"Proximity" body:@{@"isNear": state ? @YES : @NO}];
        }
    }];

    _isProximityRegistered = YES;
}

- (void)stopProximitySensor
{
    if (!_isProximityRegistered) {
        return;
    }

    NSLog(@"RNInCallManager.stopProximitySensor()");
    dispatch_async(dispatch_get_main_queue(), ^{
        self->_currentDevice.proximityMonitoringEnabled = NO;
    });

    // --- remove all no matter what object
    [self stopObserve:_proximityObserver
                 name:UIDeviceProximityStateDidChangeNotification
               object:nil];

    _isProximityRegistered = NO;
}

- (void)startAudioSessionNotification
{
    NSLog(@"RNInCallManager.startAudioSessionNotification() starting...");
    [self startAudioSessionInterruptionNotification];
    [self startAudioSessionRouteChangeNotification];
    [self startAudioSessionMediaServicesWereLostNotification];
    [self startAudioSessionMediaServicesWereResetNotification];
    [self startAudioSessionSilenceSecondaryAudioHintNotification];
}

- (void)stopAudioSessionNotification
{
    NSLog(@"RNInCallManager.startAudioSessionNotification() stopping...");
    [self stopAudioSessionInterruptionNotification];
    [self stopAudioSessionRouteChangeNotification];
    [self stopAudioSessionMediaServicesWereLostNotification];
    [self stopAudioSessionMediaServicesWereResetNotification];
    [self stopAudioSessionSilenceSecondaryAudioHintNotification];
}

- (void)startAudioSessionInterruptionNotification
{
    if (_isAudioSessionInterruptionRegistered) {
        return;
    }
    NSLog(@"RNInCallManager.startAudioSessionInterruptionNotification()");

    // --- in case it didn't deallocate when ViewDidUnload
    [self stopObserve:_audioSessionInterruptionObserver
                 name:AVAudioSessionInterruptionNotification
               object:nil];

    _audioSessionInterruptionObserver = [self startObserve:AVAudioSessionInterruptionNotification
                                                    object:nil
                                                     queue:nil
                                                     block:^(NSNotification *notification) {
        if (notification.userInfo == nil
                || ![notification.name isEqualToString:AVAudioSessionInterruptionNotification]) {
            return;
        }

        //NSUInteger rawValue = notification.userInfo[AVAudioSessionInterruptionTypeKey].unsignedIntegerValue;
        NSNumber *interruptType = [notification.userInfo objectForKey:@"AVAudioSessionInterruptionTypeKey"];
        if ([interruptType unsignedIntegerValue] == AVAudioSessionInterruptionTypeBegan) {
            NSLog(@"RNInCallManager.AudioSessionInterruptionNotification: Began");
        } else if ([interruptType unsignedIntegerValue] == AVAudioSessionInterruptionTypeEnded) {
            NSLog(@"RNInCallManager.AudioSessionInterruptionNotification: Ended");
        } else {
            NSLog(@"RNInCallManager.AudioSessionInterruptionNotification: Unknow Value");
        }
        //NSLog(@"RNInCallManager.AudioSessionInterruptionNotification: could not resolve notification");
    }];

    _isAudioSessionInterruptionRegistered = YES;
}

- (void)stopAudioSessionInterruptionNotification
{
    if (!_isAudioSessionInterruptionRegistered) {
        return;
    }
    NSLog(@"RNInCallManager.stopAudioSessionInterruptionNotification()");
    // --- remove all no matter what object
    [self stopObserve:_audioSessionInterruptionObserver
                 name:AVAudioSessionInterruptionNotification
               object: nil];
    _isAudioSessionInterruptionRegistered = NO;
}

- (void)startAudioSessionRouteChangeNotification
{
        if (_isAudioSessionRouteChangeRegistered) {
            return;
        }

        NSLog(@"RNInCallManager.startAudioSessionRouteChangeNotification()");

        // --- in case it didn't deallocate when ViewDidUnload
        [self stopObserve:_audioSessionRouteChangeObserver
                     name: AVAudioSessionRouteChangeNotification
                   object: nil];

        _audioSessionRouteChangeObserver = [self startObserve:AVAudioSessionRouteChangeNotification
                                                       object: nil
                                                        queue: nil
                                                        block:^(NSNotification *notification) {
            if (notification.userInfo == nil
                    || ![notification.name isEqualToString:AVAudioSessionRouteChangeNotification]) {
                return;
            }

            NSNumber *routeChangeType = [notification.userInfo objectForKey:@"AVAudioSessionRouteChangeReasonKey"];
            NSUInteger routeChangeTypeValue = [routeChangeType unsignedIntegerValue];

            switch (routeChangeTypeValue) {
                case AVAudioSessionRouteChangeReasonUnknown:
                    NSLog(@"RNInCallManager.AudioRouteChange.Reason: Unknown");
                    break;
                case AVAudioSessionRouteChangeReasonNewDeviceAvailable:
                    NSLog(@"RNInCallManager.AudioRouteChange.Reason: NewDeviceAvailable");
                    if ([self checkAudioRoute:@[AVAudioSessionPortHeadsetMic]
                                    routeType:@"input"]) {
                        [self sendEventWithName:@"WiredHeadset"
                                           body:@{
                                               @"isPlugged": @YES,
                                               @"hasMic": @YES,
                                               @"deviceName": AVAudioSessionPortHeadsetMic,
                                           }];
                    } else if ([self checkAudioRoute:@[AVAudioSessionPortHeadphones]
                                           routeType:@"output"]) {
                        [self sendEventWithName:@"WiredHeadset"
                                           body:@{
                                               @"isPlugged": @YES,
                                               @"hasMic": @NO,
                                               @"deviceName": AVAudioSessionPortHeadphones,
                                           }];
                    }
                    break;
                case AVAudioSessionRouteChangeReasonOldDeviceUnavailable:
                    NSLog(@"RNInCallManager.AudioRouteChange.Reason: OldDeviceUnavailable");
                    if (![self isWiredHeadsetPluggedIn]) {
                        [self sendEventWithName:@"WiredHeadset"
                                           body:@{
                                               @"isPlugged": @NO,
                                               @"hasMic": @NO,
                                               @"deviceName": @"",
                                           }];
                    }
                    break;
                case AVAudioSessionRouteChangeReasonCategoryChange:
                    NSLog(@"RNInCallManager.AudioRouteChange.Reason: CategoryChange. category=%@ mode=%@", self->_audioSession.category, self->_audioSession.mode);
                    [self updateAudioRoute];
                    break;
                case AVAudioSessionRouteChangeReasonOverride:
                    NSLog(@"RNInCallManager.AudioRouteChange.Reason: Override");
                    break;
                case AVAudioSessionRouteChangeReasonWakeFromSleep:
                    NSLog(@"RNInCallManager.AudioRouteChange.Reason: WakeFromSleep");
                    break;
                case AVAudioSessionRouteChangeReasonNoSuitableRouteForCategory:
                    NSLog(@"RNInCallManager.AudioRouteChange.Reason: NoSuitableRouteForCategory");
                    break;
                case AVAudioSessionRouteChangeReasonRouteConfigurationChange:
                    NSLog(@"RNInCallManager.AudioRouteChange.Reason: RouteConfigurationChange. category=%@ mode=%@", self->_audioSession.category, self->_audioSession.mode);
                    break;
                default:
                    NSLog(@"RNInCallManager.AudioRouteChange.Reason: Unknow Value");
                    break;
            }

            NSNumber *silenceSecondaryAudioHintType = [notification.userInfo objectForKey:@"AVAudioSessionSilenceSecondaryAudioHintTypeKey"];
            NSUInteger silenceSecondaryAudioHintTypeValue = [silenceSecondaryAudioHintType unsignedIntegerValue];
            switch (silenceSecondaryAudioHintTypeValue) {
                case AVAudioSessionSilenceSecondaryAudioHintTypeBegin:
                    NSLog(@"RNInCallManager.AudioRouteChange.SilenceSecondaryAudioHint: Begin");
                case AVAudioSessionSilenceSecondaryAudioHintTypeEnd:
                    NSLog(@"RNInCallManager.AudioRouteChange.SilenceSecondaryAudioHint: End");
                default:
                    NSLog(@"RNInCallManager.AudioRouteChange.SilenceSecondaryAudioHint: Unknow Value");
            }
        }];

        _isAudioSessionRouteChangeRegistered = YES;
}

- (void)stopAudioSessionRouteChangeNotification
{
    if (!_isAudioSessionRouteChangeRegistered) {
        return;
    }

    NSLog(@"RNInCallManager.stopAudioSessionRouteChangeNotification()");
    // --- remove all no matter what object
    [self stopObserve:_audioSessionRouteChangeObserver
                 name:AVAudioSessionRouteChangeNotification
               object:nil];
    _isAudioSessionRouteChangeRegistered = NO;
}

- (void)startAudioSessionMediaServicesWereLostNotification
{
    if (_isAudioSessionMediaServicesWereLostRegistered) {
        return;
    }

    NSLog(@"RNInCallManager.startAudioSessionMediaServicesWereLostNotification()");

    // --- in case it didn't deallocate when ViewDidUnload
    [self stopObserve:_audioSessionMediaServicesWereLostObserver
                 name:AVAudioSessionMediaServicesWereLostNotification
               object:nil];

    _audioSessionMediaServicesWereLostObserver = [self startObserve:AVAudioSessionMediaServicesWereLostNotification
                                                             object:nil
                                                              queue:nil
                                                              block:^(NSNotification *notification) {
        // --- This notification has no userInfo dictionary.
        NSLog(@"RNInCallManager.AudioSessionMediaServicesWereLostNotification: Media Services Were Lost");
    }];

    _isAudioSessionMediaServicesWereLostRegistered = YES;
}

- (void)stopAudioSessionMediaServicesWereLostNotification
{
    if (!_isAudioSessionMediaServicesWereLostRegistered) {
        return;
    }

    NSLog(@"RNInCallManager.stopAudioSessionMediaServicesWereLostNotification()");

    // --- remove all no matter what object
    [self stopObserve:_audioSessionMediaServicesWereLostObserver
                 name:AVAudioSessionMediaServicesWereLostNotification
               object:nil];

    _isAudioSessionMediaServicesWereLostRegistered = NO;
}

- (void)startAudioSessionMediaServicesWereResetNotification
{
    if (_isAudioSessionMediaServicesWereResetRegistered) {
        return;
    }

    NSLog(@"RNInCallManager.startAudioSessionMediaServicesWereResetNotification()");

    // --- in case it didn't deallocate when ViewDidUnload
    [self stopObserve:_audioSessionMediaServicesWereResetObserver
                 name:AVAudioSessionMediaServicesWereResetNotification
               object:nil];

    _audioSessionMediaServicesWereResetObserver = [self startObserve:AVAudioSessionMediaServicesWereResetNotification
                                                              object:nil
                                                               queue:nil
                                                               block:^(NSNotification *notification) {
        // --- This notification has no userInfo dictionary.
        NSLog(@"RNInCallManager.AudioSessionMediaServicesWereResetNotification: Media Services Were Reset");
    }];

    _isAudioSessionMediaServicesWereResetRegistered = YES;
}

- (void)stopAudioSessionMediaServicesWereResetNotification
{
    if (!_isAudioSessionMediaServicesWereResetRegistered) {
        return;
    }

    NSLog(@"RNInCallManager.stopAudioSessionMediaServicesWereResetNotification()");

    // --- remove all no matter what object
    [self stopObserve:_audioSessionMediaServicesWereResetObserver
                 name:AVAudioSessionMediaServicesWereResetNotification
               object:nil];

    _isAudioSessionMediaServicesWereResetRegistered = NO;
}

- (void)startAudioSessionSilenceSecondaryAudioHintNotification
{
    if (_isAudioSessionSilenceSecondaryAudioHintRegistered) {
        return;
    }

    NSLog(@"RNInCallManager.startAudioSessionSilenceSecondaryAudioHintNotification()");

    // --- in case it didn't deallocate when ViewDidUnload
    [self stopObserve:_audioSessionSilenceSecondaryAudioHintObserver
                 name:AVAudioSessionSilenceSecondaryAudioHintNotification
               object:nil];

    _audioSessionSilenceSecondaryAudioHintObserver = [self startObserve:AVAudioSessionSilenceSecondaryAudioHintNotification
                                                                 object:nil
                                                                  queue:nil
                                                                  block:^(NSNotification *notification) {
        if (notification.userInfo == nil
                || ![notification.name isEqualToString:AVAudioSessionSilenceSecondaryAudioHintNotification]) {
            return;
        }

        NSNumber *silenceSecondaryAudioHintType = [notification.userInfo objectForKey:@"AVAudioSessionSilenceSecondaryAudioHintTypeKey"];
        NSUInteger silenceSecondaryAudioHintTypeValue = [silenceSecondaryAudioHintType unsignedIntegerValue];
        switch (silenceSecondaryAudioHintTypeValue) {
            case AVAudioSessionSilenceSecondaryAudioHintTypeBegin:
                NSLog(@"RNInCallManager.AVAudioSessionSilenceSecondaryAudioHintNotification: Begin");
                break;
            case AVAudioSessionSilenceSecondaryAudioHintTypeEnd:
                NSLog(@"RNInCallManager.AVAudioSessionSilenceSecondaryAudioHintNotification: End");
                break;
            default:
                NSLog(@"RNInCallManager.AVAudioSessionSilenceSecondaryAudioHintNotification: Unknow Value");
                break;
        }
    }];
    _isAudioSessionSilenceSecondaryAudioHintRegistered = YES;
}

- (void)stopAudioSessionSilenceSecondaryAudioHintNotification
{
    if (!_isAudioSessionSilenceSecondaryAudioHintRegistered) {
        return;
    }

    NSLog(@"RNInCallManager.stopAudioSessionSilenceSecondaryAudioHintNotification()");
    // --- remove all no matter what object
    [self stopObserve:_audioSessionSilenceSecondaryAudioHintObserver
                 name:AVAudioSessionSilenceSecondaryAudioHintNotification
               object:nil];

    _isAudioSessionSilenceSecondaryAudioHintRegistered = NO;
}

- (id)startObserve:(NSString *)name
            object:(id)object
             queue:(NSOperationQueue *)queue
             block:(void (^)(NSNotification *))block
{
    return [[NSNotificationCenter defaultCenter] addObserverForName:name
                                               object:object
                                                queue:queue
                                           usingBlock:block];
}

- (void)stopObserve:(id)observer
             name:(NSString *)name
           object:(id)object
{
    if (observer == nil) return;
    [[NSNotificationCenter defaultCenter] removeObserver:observer
                                                    name:name
                                                  object:object];
}

- (NSURL *)getRingbackUri:(NSString *)_type
{
    NSString *fileBundle = @"incallmanager_ringback";
    NSString *fileBundleExt = @"mp3";
    //NSString *fileSysWithExt = @"vc~ringing.caf"; // --- ringtone of facetime, but can't play it.
    //NSString *fileSysPath = @"/System/Library/Audio/UISounds";
    NSString *fileSysWithExt = @"Marimba.m4r";
    NSString *fileSysPath = @"/Library/Ringtones";

    // --- you can't get default user perfrence sound in ios
    NSString *type = [_type isEqualToString:@""] || [_type isEqualToString:@"_DEFAULT_"]
        ? fileSysWithExt
        : _type;

    NSURL *bundleUri = _bundleRingbackUri;
    NSURL *defaultUri = _defaultRingbackUri;

    NSURL *uri = [self getAudioUri:type
                        fileBundle:fileBundle
                     fileBundleExt:fileBundleExt
                    fileSysWithExt:fileSysWithExt
                       fileSysPath:fileSysPath
                         uriBundle:&bundleUri
                        uriDefault:&defaultUri];

    _bundleRingbackUri = bundleUri;
    _defaultRingbackUri = defaultUri;

    return uri;
}

- (NSURL *)getBusytoneUri:(NSString *)_type
{
    NSString *fileBundle = @"incallmanager_busytone";
    NSString *fileBundleExt = @"mp3";
    NSString *fileSysWithExt = @"ct-busy.caf"; //ct-congestion.caf
    NSString *fileSysPath = @"/System/Library/Audio/UISounds";
    // --- you can't get default user perfrence sound in ios
    NSString *type = [_type isEqualToString:@""] || [_type isEqualToString:@"_DEFAULT_"]
        ? fileSysWithExt
        : _type;

    NSURL *bundleUri = _bundleBusytoneUri;
    NSURL *defaultUri = _defaultBusytoneUri;

    NSURL *uri = [self getAudioUri:type
                        fileBundle:fileBundle
                     fileBundleExt:fileBundleExt
                    fileSysWithExt:fileSysWithExt
                       fileSysPath:fileSysPath
                         uriBundle:&bundleUri
                        uriDefault:&defaultUri];

    _bundleBusytoneUri = bundleUri;
    _defaultBusytoneUri = defaultUri;

    return uri;
}

- (NSURL *)getRingtoneUri:(NSString *)_type
{
    NSString *fileBundle = @"incallmanager_ringtone";
    NSString *fileBundleExt = @"mp3";
    NSString *fileSysWithExt = @"Opening.m4r"; //Marimba.m4r
    NSString *fileSysPath = @"/Library/Ringtones";
    // --- you can't get default user perfrence sound in ios
    NSString *type = [_type isEqualToString:@""] || [_type isEqualToString:@"_DEFAULT_"]
        ? fileSysWithExt
        : _type;

    NSURL *bundleUri = _bundleRingtoneUri;
    NSURL *defaultUri = _defaultRingtoneUri;

    NSURL *uri = [self getAudioUri:type
                        fileBundle:fileBundle
                     fileBundleExt:fileBundleExt
                    fileSysWithExt:fileSysWithExt
                       fileSysPath:fileSysPath
                         uriBundle:&bundleUri
                        uriDefault:&defaultUri];

    _bundleRingtoneUri = bundleUri;
    _defaultRingtoneUri = defaultUri;

    return uri;
}

- (NSURL *)getAudioUri:(NSString *)_type
            fileBundle:(NSString *)fileBundle
         fileBundleExt:(NSString *)fileBundleExt
        fileSysWithExt:(NSString *)fileSysWithExt
           fileSysPath:(NSString *)fileSysPath
             uriBundle:(NSURL **)uriBundle
            uriDefault:(NSURL **)uriDefault
{
    NSString *type = _type;
    if ([type isEqualToString:@"_BUNDLE_"]) {
        if (*uriBundle == nil) {
            *uriBundle = [[NSBundle mainBundle] URLForResource:fileBundle withExtension:fileBundleExt];
            if (*uriBundle == nil) {
                NSLog(@"RNInCallManager.getAudioUri(): %@.%@ not found in bundle.", fileBundle, fileBundleExt);
                type = fileSysWithExt;
            } else {
                return *uriBundle;
            }
        } else {
            return *uriBundle;
        }
    }

    if (*uriDefault == nil) {
        NSString *target = [NSString stringWithFormat:@"%@/%@", fileSysPath, type];
        *uriDefault = [self getSysFileUri:target];
    }
    return *uriDefault;
}

- (NSURL *)getSysFileUri:(NSString *)target
{
    NSURL *url = [[NSURL alloc] initFileURLWithPath:target isDirectory:NO];

    if (url != nil) {
        NSString *path = url.path;
        if (path != nil) {
            NSFileManager *fileManager = [[NSFileManager alloc] init];
            BOOL isTargetDirectory;
            if ([fileManager fileExistsAtPath:path isDirectory:&isTargetDirectory]) {
                if (!isTargetDirectory) {
                    return url;
                }
            }
        }
    }
    NSLog(@"RNInCallManager.getSysFileUri(): can not get url for %@", target);
    return nil;
}

#pragma mark - AVAudioPlayerDelegate

// --- this only called when all loop played. it means, an infinite (numberOfLoops = -1) loop will never into here.
- (void)audioPlayerDidFinishPlaying:(AVAudioPlayer *)player
                       successfully:(BOOL)flag
{
    NSString *filename = player.url.URLByDeletingPathExtension.lastPathComponent;
    NSLog(@"RNInCallManager.audioPlayerDidFinishPlaying(): finished playing: %@", filename);
    if ([filename isEqualToString:_bundleBusytoneUri.URLByDeletingPathExtension.lastPathComponent]
            || [filename isEqualToString:_defaultBusytoneUri.URLByDeletingPathExtension.lastPathComponent]) {
        //[self stopBusytone];
        NSLog(@"RNInCallManager.audioPlayerDidFinishPlaying(): busytone finished, invoke stop()");
        [self stop:@""];
    }
}

- (void)audioPlayerDecodeErrorDidOccur:(AVAudioPlayer *)player
                                 error:(NSError *)error
{
    NSString *filename = player.url.URLByDeletingPathExtension.lastPathComponent;
    NSLog(@"RNInCallManager.audioPlayerDecodeErrorDidOccur(): player=%@, error=%@", filename, error.localizedDescription);
}

// --- Deprecated in iOS 8.0.
//- (void)audioPlayerBeginInterruption:(AVAudioPlayer *)player
//{
//}

// --- Deprecated in iOS 8.0.
//- (void)audioPlayerEndInterruption:(AVAudioPlayer *)player
//{
//}

//- (void)debugAudioSession
//{
//    let currentRoute: Dictionary <String,String> = ["input": self.audioSession.currentRoute.inputs[0].uid, "output": self.audioSession.currentRoute.outputs[0].uid]
//    var categoryOptions = ""
//    switch self.audioSession.categoryOptions {
//        case AVAudioSessionCategoryOptions.mixWithOthers:
//            categoryOptions = "MixWithOthers"
//        case AVAudioSessionCategoryOptions.duckOthers:
//            categoryOptions = "DuckOthers"
//        case AVAudioSessionCategoryOptions.allowBluetooth:
//            categoryOptions = "AllowBluetooth"
//        case AVAudioSessionCategoryOptions.defaultToSpeaker:
//            categoryOptions = "DefaultToSpeaker"
//        default:
//            categoryOptions = "unknow"
//    }
//    if #available(iOS 9, *) {
//        if categoryOptions == "unknow" && self.audioSession.categoryOptions == AVAudioSessionCategoryOptions.interruptSpokenAudioAndMixWithOthers {
//            categoryOptions = "InterruptSpokenAudioAndMixWithOthers"
//        }
//    }
//    self._checkRecordPermission()
//    let audioSessionProperties: Dictionary <String,Any> = [
//        "category": self.audioSession.category,
//        "categoryOptions": categoryOptions,
//        "mode": self.audioSession.mode,
//        //"inputAvailable": self.audioSession.inputAvailable,
//        "otherAudioPlaying": self.audioSession.isOtherAudioPlaying,
//        "recordPermission" : self.recordPermission,
//        //"availableInputs": self.audioSession.availableInputs,
//        //"preferredInput": self.audioSession.preferredInput,
//        //"inputDataSources": self.audioSession.inputDataSources,
//        //"inputDataSource": self.audioSession.inputDataSource,
//        //"outputDataSources": self.audioSession.outputDataSources,
//        //"outputDataSource": self.audioSession.outputDataSource,
//        "currentRoute": currentRoute,
//        "outputVolume": self.audioSession.outputVolume,
//        "inputGain": self.audioSession.inputGain,
//        "inputGainSettable": self.audioSession.isInputGainSettable,
//        "inputLatency": self.audioSession.inputLatency,
//        "outputLatency": self.audioSession.outputLatency,
//        "sampleRate": self.audioSession.sampleRate,
//        "preferredSampleRate": self.audioSession.preferredSampleRate,
//        "IOBufferDuration": self.audioSession.ioBufferDuration,
//        "preferredIOBufferDuration": self.audioSession.preferredIOBufferDuration,
//        "inputNumberOfChannels": self.audioSession.inputNumberOfChannels,
//        "maximumInputNumberOfChannels": self.audioSession.maximumInputNumberOfChannels,
//        "preferredInputNumberOfChannels": self.audioSession.preferredInputNumberOfChannels,
//        "outputNumberOfChannels": self.audioSession.outputNumberOfChannels,
//        "maximumOutputNumberOfChannels": self.audioSession.maximumOutputNumberOfChannels,
//        "preferredOutputNumberOfChannels": self.audioSession.preferredOutputNumberOfChannels
//    ]
//    /*
//    // --- Too noisy
//    if #available(iOS 8, *) {
//        //audioSessionProperties["secondaryAudioShouldBeSilencedHint"] = self.audioSession.secondaryAudioShouldBeSilencedHint
//    } else {
//        //audioSessionProperties["secondaryAudioShouldBeSilencedHint"] = "unknow"
//    }
//    if #available(iOS 9, *) {
//        //audioSessionProperties["availableCategories"] = self.audioSession.availableCategories
//        //audioSessionProperties["availableModes"] = self.audioSession.availableModes
//    }
//    */
//    NSLog("RNInCallManager.debugAudioSession(): ==========BEGIN==========")
//    // iterate over all keys
//    for (key, value) in audioSessionProperties {
//        NSLog("\(key) = \(value)")
//    }
//    NSLog("RNInCallManager.debugAudioSession(): ==========END==========")
//}

@end
