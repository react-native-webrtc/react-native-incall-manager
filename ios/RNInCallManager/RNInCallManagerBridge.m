//  RNInCallManagerBridge.m
//  RNInCallManager
//
//  Created by zxcpoiu, Henry Hung-Hsien Lin on 2016-04-10
//  Copyright 2016 Facebook. All rights reserved.
//

#import "RCTBridge.h"

@interface RCT_EXTERN_REMAP_MODULE(InCallManager, RNInCallManager, NSObject)

RCT_EXTERN_METHOD(start:(NSString *)mediaType auto:(BOOL)auto)
RCT_EXTERN_METHOD(stop)
RCT_EXTERN_METHOD(turnScreenOn)
RCT_EXTERN_METHOD(turnScreenOff)
RCT_EXTERN_METHOD(setKeepScreenOn:(BOOL)enable)
RCT_EXTERN_METHOD(setSpeakerphoneOn:(BOOL)enable)
RCT_EXTERN_METHOD(setForceSpeakerphoneOn:(BOOL)enable)
RCT_EXTERN_METHOD(setMicrophoneMute:(BOOL)enable)

@end
