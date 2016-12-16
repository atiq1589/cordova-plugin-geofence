#import <Cordova/CDV.h>
#import <Cordova/CDVPlugin.h>

@import Foundation;

@interface GeofencePlugin : CDVPlugin
{
    NSMutableDictionary *handlerObj;
    void (^completionHandler)(UIBackgroundFetchResult);
}

@property (nonatomic, strong) NSMutableArray* events;
@property (nonatomic, strong) NSMutableDictionary* tasks;

@property (nonatomic, copy) NSString *callbackId;

@property BOOL clearBadge;
@property BOOL forceShow;

- (void)init:(CDVInvokedUrlCommand*)command;
- (void)addFences:(CDVInvokedUrlCommand*)command;
- (void)removeFences:(CDVInvokedUrlCommand*)command;
- (void)removeAllFences:(CDVInvokedUrlCommand*)command;
- (void)clearAllNotifications:(CDVInvokedUrlCommand*)command;
- (void)getFence:(CDVInvokedUrlCommand*)command;
- (void)getFences:(CDVInvokedUrlCommand*)command;
- (void)finish:(CDVInvokedUrlCommand*)command;
- (void)hasPermission:(CDVInvokedUrlCommand*)command;
- (void)sendEvent:(NSDictionary*)event;
- (void)sendError:(NSString*)code withMessage:(NSString*)message;

- (void)load;
- (void)save;

@end
