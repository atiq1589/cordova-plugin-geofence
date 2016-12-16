#import "GeofencePlugin.h"
#import "AppDelegate+GeofencePlugin.h"

@implementation GeofencePlugin

@synthesize events;
@synthesize callbackId;
@synthesize clearBadge;
@synthesize forceShow;
@synthesize tasks;

- (void)init:(CDVInvokedUrlCommand*)command;
{
    [self.commandDelegate runInBackground:^ {
        NSLog(@"Geofence Plugin register called");
        self.callbackId = command.callbackId;

        NSMutableDictionary* options = [command.arguments objectAtIndex:0];
        NSMutableDictionary* iosOptions = [options objectForKey:@"ios"];

        id clearBadgeArg = [iosOptions objectForKey:@"clearBadge"];
        if (clearBadgeArg == nil || ([clearBadgeArg isKindOfClass:[NSString class]] && [clearBadgeArg isEqualToString:@"false"]) || ![clearBadgeArg boolValue]) {
            NSLog(@"GeofencePlugin.init: setting clear badge to false");
            clearBadge = NO;
        } else {
            NSLog(@"GeofencePlugin.init: setting clear badge to true");
            clearBadge = YES;
            [[UIApplication sharedApplication] setApplicationIconBadgeNumber:0];
        }
        NSLog(@"GeofencePlugin.init: clear badge is set to %d", clearBadge);

        id forceShowArg = [iosOptions objectForKey:@"forceShow"];
        if (forceShowArg == nil || ([forceShowArg isKindOfClass:[NSString class]] && [forceShowArg isEqualToString:@"false"]) || ![forceShowArg boolValue]) {
            NSLog(@"GeofencePlugin.init: setting force show to false");
            forceShow = NO;
        } else {
            NSLog(@"GeofencePlugin.init: setting force show to true");
            forceShow = YES;
        }
        NSLog(@"GeofencePlugin.init: force show is set to %d", forceShow);

        id badgeArg = [iosOptions objectForKey:@"badge"];
        id soundArg = [iosOptions objectForKey:@"sound"];
        id alertArg = [iosOptions objectForKey:@"alert"];
        
        if (SYSTEM_VERSION_GREATER_THAN_OR_EQUAL_TO(@"10.0")) {
            UNAuthorizationOptions notificationTypes = UNAuthorizationOptionNone;
            
            if (([badgeArg isKindOfClass:[NSString class]] && [badgeArg isEqualToString:@"true"]) || [badgeArg boolValue]) {
                notificationTypes |= UNAuthorizationOptionBadge;
            }
            
            if (([soundArg isKindOfClass:[NSString class]] && [soundArg isEqualToString:@"true"]) || [soundArg boolValue]) {
                notificationTypes |= UNAuthorizationOptionSound;
            }
            
            if (([alertArg isKindOfClass:[NSString class]] && [alertArg isEqualToString:@"true"]) || [alertArg boolValue]) {
                notificationTypes |= UNAuthorizationOptionAlert;
            }
            
            if (notificationTypes == UNAuthorizationOptionNone) {
                NSLog(@"GeofencePlugin.init: Notification type is set to none");
            }
            
            id<UIApplicationDelegate> delegate = [[UIApplication sharedApplication] delegate];
            if ([delegate respondsToSelector:@selector(checkPermissions:)]) {
                [delegate performSelector:@selector(checkPermissions:) withObject:[NSNumber numberWithInt:notificationTypes]];
            }
            
            //TODO create & register action buttons
        } else {
            UIUserNotificationType notificationTypes = UIUserNotificationTypeNone;
            
            if (([badgeArg isKindOfClass:[NSString class]] && [badgeArg isEqualToString:@"true"]) || [badgeArg boolValue]) {
                notificationTypes |= UIUserNotificationTypeBadge;
            }
            
            if (([soundArg isKindOfClass:[NSString class]] && [soundArg isEqualToString:@"true"]) || [soundArg boolValue]) {
                notificationTypes |= UIUserNotificationTypeSound;
            }
            
            if (([alertArg isKindOfClass:[NSString class]] && [alertArg isEqualToString:@"true"]) || [alertArg boolValue]) {
                notificationTypes |= UIUserNotificationTypeAlert;
            }
            
            if (notificationTypes == UIUserNotificationTypeNone) {
                NSLog(@"GeofencePlugin.init: Notification type is set to none");
            } else {
                notificationTypes |= UIUserNotificationActivationModeBackground;
            }
            
            NSMutableSet *categories = [[NSMutableSet alloc] init];
            id categoryOptions = [iosOptions objectForKey:@"categories"];
            if (categoryOptions != nil && [categoryOptions isKindOfClass:[NSDictionary class]]) {
                for (id key in categoryOptions) {
                    NSLog(@"categories: key %@", key);
                    id category = [categoryOptions objectForKey:key];
                    
                    id yesButton = [category objectForKey:@"yes"];
                    UIMutableUserNotificationAction *yesAction;
                    if (yesButton != nil && [yesButton isKindOfClass:[NSDictionary class]]) {
                        yesAction = [self createAction: yesButton];
                    }
                    
                    id noButton = [category objectForKey:@"no"];
                    UIMutableUserNotificationAction *noAction;
                    if (noButton != nil && [noButton isKindOfClass:[NSDictionary class]]) {
                        noAction = [self createAction: noButton];
                    }
                    
                    id maybeButton = [category objectForKey:@"maybe"];
                    UIMutableUserNotificationAction *maybeAction;
                    if (maybeButton != nil && [maybeButton isKindOfClass:[NSDictionary class]]) {
                        maybeAction = [self createAction: maybeButton];
                    }
                    
                    // First create the category
                    UIMutableUserNotificationCategory *notificationCategory = [[UIMutableUserNotificationCategory alloc] init];
                    
                    // Identifier to include in your push payload and local notification
                    notificationCategory.identifier = key;
                    
                    NSMutableArray *categoryArray = [[NSMutableArray alloc] init];
                    NSMutableArray *minimalCategoryArray = [[NSMutableArray alloc] init];
                    if (yesButton != nil) {
                        [categoryArray addObject:yesAction];
                        [minimalCategoryArray addObject:yesAction];
                    }
                    
                    if (noButton != nil) {
                        [categoryArray addObject:noAction];
                        [minimalCategoryArray addObject:noAction];
                    }
                    
                    if (maybeButton != nil) {
                        [categoryArray addObject:maybeAction];
                    }
                    
                    // Add the actions to the category and set the action context
                    [notificationCategory setActions:categoryArray forContext:UIUserNotificationActionContextDefault];
                    
                    // Set the actions to present in a minimal context
                    [notificationCategory setActions:minimalCategoryArray forContext:UIUserNotificationActionContextMinimal];
                    
                    NSLog(@"Adding category %@", key);
                    [categories addObject:notificationCategory];
                }
            }

            id<UIApplicationDelegate> delegate = [[UIApplication sharedApplication] delegate];
            if ([delegate respondsToSelector:@selector(checkLocationPermissions)]) {
                [delegate performSelector:@selector(checkLocationPermissions)];
            }

            [[UIApplication sharedApplication] registerUserNotificationSettings:[UIUserNotificationSettings settingsForTypes:notificationTypes categories:categories]];
        }

        NSLog(@"GeofencePlugin.init: better button setup");

        if ([self.events count] > 0) {
            dispatch_async(dispatch_get_main_queue(), ^{
                for(NSDictionary *event in self.events) {
                    [self sendEvent:event];
                }
                [self.events removeAllObjects];
            });
        }
    }];
}

- (void)addFences:(CDVInvokedUrlCommand *)command {
    [self.commandDelegate runInBackground:^{
        NSArray *fences = [command.arguments objectAtIndex:0];

        id<UIApplicationDelegate> delegate = [[UIApplication sharedApplication] delegate];
        if ([delegate respondsToSelector:@selector(addFences:)]) {
            [delegate performSelector:@selector(addFences:) withObject:fences];
        }

        CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }];
}

- (void)removeFences:(CDVInvokedUrlCommand *)command {
    [self.commandDelegate runInBackground:^{
        NSArray *ids = [command.arguments objectAtIndex:0];

        id<UIApplicationDelegate> delegate = [[UIApplication sharedApplication] delegate];
        if ([delegate respondsToSelector:@selector(removeFences:)]) {
            [delegate performSelector:@selector(removeFences:) withObject:ids];
        }
        
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }];
}

- (void)removeAllFences:(CDVInvokedUrlCommand *)command {
    [self.commandDelegate runInBackground:^{
        id<UIApplicationDelegate> delegate = [[UIApplication sharedApplication] delegate];
        if ([delegate respondsToSelector:@selector(removeAllFences)]) {
            [delegate performSelector:@selector(removeAllFences)];
        }

        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }];
}

- (void)getFence:(CDVInvokedUrlCommand *)command {
    [self.commandDelegate runInBackground:^{
        id<UIApplicationDelegate> delegate = [[UIApplication sharedApplication] delegate];
        NSString *id = [command.arguments objectAtIndex:0];
        NSDictionary *fence;

        if ([delegate respondsToSelector:@selector(getFence:)]) {
             fence = [delegate performSelector:@selector(getFence:) withObject:id];
        }

        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:fence];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }];
}

- (void)getFences:(CDVInvokedUrlCommand *)command {
    [self.commandDelegate runInBackground:^{
        NSArray *fences;

        id<UIApplicationDelegate> delegate = [[UIApplication sharedApplication] delegate];
        if ([delegate respondsToSelector:@selector(getFences)]) {
            fences = [delegate performSelector:@selector(getFences)];
        }

        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsArray:fences];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }];
}

- (void)clearAllNotifications:(CDVInvokedUrlCommand *)command
{
    [[UIApplication sharedApplication] setApplicationIconBadgeNumber:0];

    NSString* message = [NSString stringWithFormat:@"cleared all notifications"];
    CDVPluginResult *commandResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:message];
    [self.commandDelegate sendPluginResult:commandResult callbackId:command.callbackId];
}

- (void)hasPermission:(CDVInvokedUrlCommand *)command
{
    [self.commandDelegate runInBackground:^{
        BOOL permission = NO;
        
        id<UIApplicationDelegate> delegate = [[UIApplication sharedApplication] delegate];
        if ([delegate respondsToSelector:@selector(hasPermission)]) {
            permission = [[delegate performSelector:@selector(hasPermission)] boolValue];
        }
        
        CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsInt:(permission ? 1 : 0)];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }];
}

-(void) finish:(CDVInvokedUrlCommand*)command
{
    NSLog(@"Geofence Plugin finish called");

    [self.commandDelegate runInBackground:^ {
        id task = [command.arguments objectAtIndex:0];

        if (task != nil && [task isKindOfClass:[NSNumber class]]) {
            dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 100000000), dispatch_get_main_queue(), ^{
                [self stopBackgroundTask:task];
            });
        }

        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
}

#pragma mark -
#pragma mark Life Cycle

- (void)load {
    NSString *path = NSSearchPathForDirectoriesInDomains(NSLibraryDirectory, NSUserDomainMask, YES)[0];
    NSString *filename = [path stringByAppendingString:@"pending.dat"];

    if ([[NSFileManager defaultManager] fileExistsAtPath:filename]) {
        self.events = [NSMutableArray arrayWithContentsOfFile:filename];
    }

    if (self.events == nil) {
        self.events = [NSMutableArray array];
    }

    if (self.tasks == nil) {
        self.tasks = [NSMutableDictionary dictionary];
    }
}

- (void)save {
    NSString *path = NSSearchPathForDirectoriesInDomains(NSLibraryDirectory, NSUserDomainMask, YES)[0];
    NSString *filename = [path stringByAppendingString:@"pending.dat"];

    if ([[NSFileManager defaultManager] fileExistsAtPath:filename]) {
        self.events = [NSMutableArray arrayWithContentsOfFile:filename];
    }

    if (self.events == nil) {
        self.events = [NSMutableArray array];
    }
}

#pragma mark -
#pragma mark Helper

- (void)sendEvent:(NSDictionary*)event {
    NSLog(@"sendEvent");
    if (self.callbackId == nil) {
        NSLog(@"callback not available adding to queue");
        [self.events addObject:event];
    } else {
        NSLog(@"callback available sending to js");
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:event];
        result.keepCallback = [NSNumber numberWithBool:YES];
        [self.commandDelegate sendPluginResult:result callbackId:self.callbackId];
    }
}

- (void)sendError:(NSString*)code withMessage:(NSString*)message {
    NSMutableDictionary *data = [NSMutableDictionary dictionary];
    [data setValue:code forKey:@"code"];
    [data setValue:message forKey:@"message"];
    
    if (self.callbackId != nil) {
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:data];
        result.keepCallback = [NSNumber numberWithBool:YES];
        [self.commandDelegate sendPluginResult:result callbackId:self.callbackId];
    }
}

- (UIMutableUserNotificationAction *)createAction:(NSDictionary *)dictionary {
    UIMutableUserNotificationAction *myAction = [[UIMutableUserNotificationAction alloc] init];
    
    myAction = [[UIMutableUserNotificationAction alloc] init];
    myAction.identifier = [dictionary objectForKey:@"callback"];
    myAction.title = [dictionary objectForKey:@"title"];
    
    id mode =[dictionary objectForKey:@"foreground"];
    if (mode == nil || ([mode isKindOfClass:[NSString class]] && [mode isEqualToString:@"false"]) || ![mode boolValue]) {
        myAction.activationMode = UIUserNotificationActivationModeBackground;
    } else {
        myAction.activationMode = UIUserNotificationActivationModeForeground;
    }
    
    id destructive = [dictionary objectForKey:@"destructive"];
    if (destructive == nil || ([destructive isKindOfClass:[NSString class]] && [destructive isEqualToString:@"false"]) || ![destructive boolValue]) {
        myAction.destructive = NO;
    } else {
        myAction.destructive = YES;
    }
    
    myAction.authenticationRequired = NO;
    
    return myAction;
}

-(void)stopBackgroundTask:(NSNumber*)task
{
    NSLog(@"Geofence Plugin stopBackgroundTask called");

    id handler = [self.tasks objectForKey:task];
    if (handler != nil) {
        [self.tasks removeObjectForKey:task];
        ((void(^)()) handler)();
    }
}

@end
