#import <Foundation/Foundation.h>
#import <Cordova/CDV.h>
#import <Cordova/CDVPlugin.h>

@interface GeofencePlugin : CDVPlugin
{
    NSDictionary *notificationMessage;
    BOOL    isInline;
    NSString *notificationCallbackId;
    NSString *callback;
    BOOL    clearBadge;

    NSMutableDictionary *handlerObj;
    void (^completionHandler)(UIBackgroundFetchResult);

    BOOL ready;
}

@property (nonatomic, copy) NSString *callbackId;
@property (nonatomic, copy) NSString *notificationCallbackId;
@property (nonatomic, copy) NSString *callback;

@property (nonatomic, strong) NSDictionary *notificationMessage;
@property BOOL isInline;
@property BOOL coldstart;
@property BOOL clearBadge;
@property (nonatomic, strong) NSMutableDictionary *handlerObj;

- (void)init:(CDVInvokedUrlCommand*)command;
- (void)addFences:(CDVInvokedUrlCommand*)command;
- (void)removeFences:(CDVInvokedUrlCommand*)command;
- (void)removeAllFences:(CDVInvokedUrlCommand*)command;
- (void)clearAllNotifications:(CDVInvokedUrlCommand*)command;
- (void)getFence:(CDVInvokedUrlCommand*)command;
- (void)getFences:(CDVInvokedUrlCommand*)command;
- (void)finish:(CDVInvokedUrlCommand*)command;
- (void)hasPermission:(CDVInvokedUrlCommand*)command;

- (void)didRegisterForRemoteNotificationsWithDeviceToken:(NSData *)deviceToken;
- (void)didFailToRegisterForRemoteNotificationsWithError:(NSError *)error;

- (void)setNotificationMessage:(NSDictionary *)notification;
- (void)notificationReceived;

- (void)willSendDataMessageWithID:(NSString *)messageID error:(NSError *)error;
- (void)didSendDataMessageWithID:(NSString *)messageID;

@end