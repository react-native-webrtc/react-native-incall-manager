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
  
}

+ (BOOL)requiresMainQueueSetup
{
    return NO;
}

// This is where the module is being exported
RCT_EXPORT_MODULE(InCallManager)

- (instancetype)init
{
    if (self = [super init]) {
     
    }
    return self;
}

- (void)dealloc
{
    [[NSNotificationCenter defaultCenter] removeObserver:self];
    [self stop:@""];
}




// START METHOD *STUDY
RCT_EXPORT_METHOD(start:(NSString *)mediaType
      
}

// STOP METHOD *STUDY
RCT_EXPORT_METHOD(stop:(NSString *)busytoneUriType)
{
  
}

