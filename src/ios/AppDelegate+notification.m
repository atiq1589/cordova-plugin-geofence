//
//  AppDelegate+notification.m
//  geofencetest
//
//  Created by Robert Easterday on 10/26/12.
//
//

#import "AppDelegate+notification.h"
#import "GeofencePlugin.h"
#import <objc/runtime.h>

static char launchNotificationKey;
static char coldstartKey;

@implementation AppDelegate (notification)

- (id) getCommandInstance:(NSString*)className
{
    return [self.viewController getCommandInstance:className];
}

// its dangerous to override a method from within a category.
// Instead we will use method swizzling. we set this up in the load call.
+ (void)load
{
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        Class class = [self class];

        SEL originalSelector = @selector(init);
        SEL swizzledSelector = @selector(geofencePluginSwizzledInit);

        Method original = class_getInstanceMethod(class, originalSelector);
        Method swizzled = class_getInstanceMethod(class, swizzledSelector);

        BOOL didAddMethod = class_addMethod(class, originalSelector, method_getImplementation(swizzled), method_getTypeEncoding(swizzled));

        if (didAddMethod) {
            class_replaceMethod(class, swizzledSelector, method_getImplementation(original), method_getTypeEncoding(original));
        } else {
            method_exchangeImplementations(original, swizzled);
        }
    });
}

- (AppDelegate *)geofencePluginSwizzledInit
{
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(createNotificationChecker:) name:UIApplicationDidFinishLaunchingNotification object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(geofencePluginOnApplicationDidBecomeActive:) name:UIApplicationDidBecomeActiveNotification object:nil];

    // This actually calls the original init method over in AppDelegate. Equivilent to calling super
    // on an overrided method, this is not recursive, although it appears that way. neat huh?
    return [self geofencePluginSwizzledInit];
}

// This code will be called immediately after application:didFinishLaunchingWithOptions:. We need to process notifications in cold-start situations
- (void)createNotificationChecker:(NSNotification *)notification
{
    NSLog(@"createNotificationChecker");
    if (notification)
    {
        NSDictionary *launchOptions = [notification userInfo];
        if (launchOptions) {
            NSLog(@"coldstart");
            self.launchNotification = [launchOptions objectForKey: @"UIApplicationLaunchOptionsRemoteNotificationKey"];
            self.coldstart = [NSNumber numberWithBool:YES];
        } else {
            NSLog(@"not coldstart");
            self.coldstart = [NSNumber numberWithBool:NO];
        }
    }
}

- (void)application:(UIApplication *)application didRegisterForRemoteNotificationsWithDeviceToken:(NSData *)deviceToken {
    GeofencePlugin *geofenceHandler = [self getCommandInstance:@"GeofenceNotification"];
    [geofenceHandler didRegisterForRemoteNotificationsWithDeviceToken:deviceToken];
}

- (void)application:(UIApplication *)application didFailToRegisterForRemoteNotificationsWithError:(NSError *)error {
    GeofencePlugin *geofenceHandler = [self getCommandInstance:@"GeofenceNotification"];
    [geofenceHandler didFailToRegisterForRemoteNotificationsWithError:error];
}

- (void) application:(UIApplication *)application didReceiveRemoteNotification:(NSDictionary *)userInfo {
    NSLog(@"clicked on the shade");
    GeofencePlugin *geofenceHandler = [self getCommandInstance:@"GeofenceNotification"];
    geofenceHandler.notificationMessage = userInfo;
    geofenceHandler.isInline = NO;
    [geofenceHandler notificationReceived];
}

- (void)application:(UIApplication *)application didReceiveRemoteNotification:(NSDictionary *)userInfo fetchCompletionHandler:(void (^)(UIBackgroundFetchResult))completionHandler {
    NSLog(@"didReceiveNotification with fetchCompletionHandler");

    // app is in the foreground so call notification callback
    if (application.applicationState == UIApplicationStateActive) {
        NSLog(@"app active");
        GeofencePlugin *geofenceHandler = [self getCommandInstance:@"GeofenceNotification"];
        geofenceHandler.notificationMessage = userInfo;
        geofenceHandler.isInline = YES;
        [geofenceHandler notificationReceived];

        completionHandler(UIBackgroundFetchResultNewData);
    }
    // app is in background or in stand by
    else {
        NSLog(@"app in-active");

        // do some convoluted logic to find out if this should be a silent geofence.
        long silent = 0;
        id aps = [userInfo objectForKey:@"aps"];
        id contentAvailable = [aps objectForKey:@"content-available"];
        if ([contentAvailable isKindOfClass:[NSString class]] && [contentAvailable isEqualToString:@"1"]) {
            silent = 1;
        } else if ([contentAvailable isKindOfClass:[NSNumber class]]) {
            silent = [contentAvailable integerValue];
        }

        if (silent == 1) {
            NSLog(@"this should be a silent geofence");
            void (^safeHandler)(UIBackgroundFetchResult) = ^(UIBackgroundFetchResult result){
                dispatch_async(dispatch_get_main_queue(), ^{
                    completionHandler(result);
                });
            };

            GeofencePlugin *geofenceHandler = [self getCommandInstance:@"GeofenceNotification"];

            if (geofenceHandler.handlerObj == nil) {
                geofenceHandler.handlerObj = [NSMutableDictionary dictionaryWithCapacity:2];
            }

            id notId = [userInfo objectForKey:@"notId"];
            if (notId != nil) {
                NSLog(@"Geofence Plugin notId %@", notId);
                [geofenceHandler.handlerObj setObject:safeHandler forKey:notId];
            } else {
                NSLog(@"Geofence Plugin notId handler");
                [geofenceHandler.handlerObj setObject:safeHandler forKey:@"handler"];
            }

            geofenceHandler.notificationMessage = userInfo;
            geofenceHandler.isInline = NO;
            [geofenceHandler notificationReceived];
        } else {
            NSLog(@"just put it in the shade");
            //save it for later
            self.launchNotification = userInfo;

            completionHandler(UIBackgroundFetchResultNewData);
        }
    }
}

- (BOOL) hasPermissionToScheduleLocalNotifications
{
    if ([[UIApplication sharedApplication] respondsToSelector:@selector(registerUserNotificationSettings:)])
    {
        UIUserNotificationType types;
        UIUserNotificationSettings *settings;

        settings = [[UIApplication sharedApplication] currentUserNotificationSettings];

        types = UIUserNotificationTypeAlert|UIUserNotificationTypeBadge|UIUserNotificationTypeSound;

        return (settings.types & types);
    } else {
        return YES;
    }
}

- (void) registerPermissionToScheduleLocalNotifications
{
    if ([[UIApplication sharedApplication] respondsToSelector:@selector(registerUserNotificationSettings:)])
    {
        UIUserNotificationType types;
        UIUserNotificationSettings *settings;

        settings = [[UIApplication sharedApplication] currentUserNotificationSettings];

        types = settings.types|UIUserNotificationTypeAlert|UIUserNotificationTypeBadge|UIUserNotificationTypeSound;

        settings = [UIUserNotificationSettings settingsForTypes:types categories:nil];

        [[UIApplication sharedApplication] registerUserNotificationSettings:settings];
    }
}

- (void)geofencePluginOnApplicationDidBecomeActive:(NSNotification *)notification {

    NSLog(@"active");

    UIApplication *application = notification.object;

    GeofencePlugin *geofenceHandler = [self getCommandInstance:@"GeofenceNotification"];
    if (geofenceHandler.clearBadge) {
        NSLog(@"GeofencePlugin clearing badge");
        //zero badge
        application.applicationIconBadgeNumber = 0;
    } else {
        NSLog(@"GeofencePlugin skip clear badge");
    }

    if (self.launchNotification) {
        geofenceHandler.isInline = NO;
        geofenceHandler.coldstart = [self.coldstart boolValue];
        geofenceHandler.notificationMessage = self.launchNotification;
        self.launchNotification = nil;
        self.coldstart = [NSNumber numberWithBool:NO];
        [geofenceHandler performSelectorOnMainThread:@selector(notificationReceived) withObject:geofenceHandler waitUntilDone:NO];
    }
}


- (void)application:(UIApplication *) application handleActionWithIdentifier: (NSString *) identifier
forRemoteNotification: (NSDictionary *) notification completionHandler: (void (^)()) completionHandler {

    NSLog(@"Geofence Plugin handleActionWithIdentifier %@", identifier);
    NSMutableDictionary *userInfo = [notification mutableCopy];
    [userInfo setObject:identifier forKey:@"actionCallback"];
    NSLog(@"Geofence Plugin userInfo %@", userInfo);

    if (application.applicationState == UIApplicationStateActive) {
        GeofencePlugin *geofenceHandler = [self getCommandInstance:@"GeofenceNotification"];
        geofenceHandler.notificationMessage = userInfo;
        geofenceHandler.isInline = NO;
        [geofenceHandler notificationReceived];
    } else {
        void (^safeHandler)() = ^(void){
            dispatch_async(dispatch_get_main_queue(), ^{
                completionHandler();
            });
        };

        GeofencePlugin *geofenceHandler = [self getCommandInstance:@"GeofenceNotification"];

        if (geofenceHandler.handlerObj == nil) {
            geofenceHandler.handlerObj = [NSMutableDictionary dictionaryWithCapacity:2];
        }

        id notId = [userInfo objectForKey:@"notId"];
        if (notId != nil) {
            NSLog(@"Geofence Plugin notId %@", notId);
            [geofenceHandler.handlerObj setObject:safeHandler forKey:notId];
        } else {
            NSLog(@"Geofence Plugin notId handler");
            [geofenceHandler.handlerObj setObject:safeHandler forKey:@"handler"];
        }

        geofenceHandler.notificationMessage = userInfo;
        geofenceHandler.isInline = NO;

        [geofenceHandler performSelectorOnMainThread:@selector(notificationReceived) withObject:geofenceHandler waitUntilDone:NO];
    }
}

// The accessors use an Associative Reference since you can't define a iVar in a category
// http://developer.apple.com/library/ios/#documentation/cocoa/conceptual/objectivec/Chapters/ocAssociativeReferences.html
- (NSMutableArray *)launchNotification
{
    return objc_getAssociatedObject(self, &launchNotificationKey);
}

- (void)setLaunchNotification:(NSDictionary *)aDictionary
{
    objc_setAssociatedObject(self, &launchNotificationKey, aDictionary, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

- (NSNumber *)coldstart
{
    return objc_getAssociatedObject(self, &coldstartKey);
}

- (void)setColdstart:(NSNumber *)aNumber
{
    objc_setAssociatedObject(self, &coldstartKey, aNumber, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

- (void)dealloc
{
    self.launchNotification = nil; // clear the association and release the object
    self.coldstart = nil;
}

@end