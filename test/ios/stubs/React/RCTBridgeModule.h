// Test stub: just enough of React Native's module macros to compile RNInCallManager.m outside an app.
#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>
typedef void (^RCTPromiseResolveBlock)(id result);
typedef void (^RCTPromiseRejectBlock)(NSString *code, NSString *message, NSError *error);
@protocol RCTBridgeModule <NSObject>
@optional
@property (nonatomic, strong, readonly) dispatch_queue_t methodQueue;
- (NSDictionary *)constantsToExport;
@end
#define RCT_EXPORT_MODULE(js_name) + (NSString *)moduleName { return @#js_name; }
#define RCT_EXPORT_METHOD(method) - (void)method
