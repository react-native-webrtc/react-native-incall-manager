//
//  RNInCallManager.h
//  RNInCallManager
//
//  Created by Ian Yu-Hsun Lin (@ianlin) on 05/12/2017.
//  Copyright © 2017 zxcpoiu. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <AVFoundation/AVFoundation.h>

#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface RNInCallManager : RCTEventEmitter <RCTBridgeModule, AVAudioPlayerDelegate>

@end
