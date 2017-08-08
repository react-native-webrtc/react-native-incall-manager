//  RNInCallManagerBridge.m
//  RNInCallManager
//
//  Created by zxcpoiu, Henry Hung-Hsien Lin on 2016-04-10
//  Copyright 2016 Facebook. All rights reserved.
//

#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_REMAP_MODULE(InCallManager, RNInCallManager, NSObject)

RCT_EXTERN_METHOD(start:(NSString *)mediaType auto:(BOOL)auto ringbackUriType:(NSString *)ringbackUriType)
RCT_EXTERN_METHOD(stop:(NSString *)busytone)
RCT_EXTERN_METHOD(turnScreenOn)
RCT_EXTERN_METHOD(turnScreenOff)
RCT_EXTERN_METHOD(setFlashOn:(BOOL)enable brightness:(nonnull NSNumber *)brightness)
RCT_EXTERN_METHOD(setKeepScreenOn:(BOOL)enable)
RCT_EXTERN_METHOD(setSpeakerphoneOn:(BOOL)enable)
RCT_EXTERN_METHOD(setForceSpeakerphoneOn:(int)flag)
RCT_EXTERN_METHOD(setMicrophoneMute:(BOOL)enable)
RCT_EXTERN_METHOD(stopRingback)
RCT_EXTERN_METHOD(startRingtone:(NSString *)ringtoneUriType ringtoneCategory:(NSString *)ringtoneCategory)
RCT_EXTERN_METHOD(stopRingtone)
RCT_EXTERN_METHOD(checkRecordPermission:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(requestRecordPermission:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(checkCameraPermission:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(requestCameraPermission:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(getAudioUriJS:(NSString *)audioType fileType:(NSString *)fileType resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(getIsWiredHeadsetPluggedIn:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
@end
