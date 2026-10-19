// Test stub: RCTEventEmitter's interface as RNInCallManager.m uses it (implemented in RouteTests.m,
// which records the events sent).
#import <React/RCTBridgeModule.h>
@interface RCTEventEmitter : NSObject <RCTBridgeModule>
- (NSArray<NSString *> *)supportedEvents;
- (void)sendEventWithName:(NSString *)name body:(id)body;
- (void)startObserving;
- (void)stopObserving;
- (void)invalidate;
@end
