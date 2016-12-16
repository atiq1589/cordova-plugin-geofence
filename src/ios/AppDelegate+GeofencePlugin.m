#import "AppDelegate+GeofencePlugin.h"
#import "GeofencePlugin.h"
#import <objc/runtime.h>

static char fencesKey;
static char locationManagerKey;
static char notificationCenterKey;
static char transitionEventKey;
static char coldStartKey;
static char locationPermissionKey;
static char notificationPermissionKey;

@implementation AppDelegate (GeofencePlugin)

#pragma mark -
#pragma mark Helpers

- (id)getCommandInstance:(NSString*)className {
    return [self.viewController getCommandInstance:className];
}

#pragma mark -
#pragma mark Initialization

+ (void)load {
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

- (AppDelegate*)geofencePluginSwizzledInit {
    NSLog(@"geofencePluginSwizzledInit");
    
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(geofencePluginDidFinishLaunching:) name:UIApplicationDidFinishLaunchingNotification object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(geofencePluginDidBecomeActive:) name:UIApplicationDidBecomeActiveNotification object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(geofencePluginDidEnterBackground:) name:UIApplicationDidEnterBackgroundNotification object:nil];

    return [self geofencePluginSwizzledInit];
}

#pragma mark -
#pragma mark Methods

- (void)addFences:(NSArray*)fences {
    if (fences == nil) {
        NSLog(@"restoring %ld fences", (unsigned long)[self.fences count]);
        
        for(int i = 0; i < [self.fences count]; i++) {
            NSDictionary *fence = [self.fences objectAtIndex:i];
            NSString *fenceId = [fence objectForKey:@"id"];

            BOOL restore = YES;
            for(CLRegion *region in self.locationManager.monitoredRegions) {
                if ([region.identifier isEqualToString:fenceId]) {
                    restore = NO;
                    break;
                }
            }
            
            if (restore) {
                NSLog(@"Fence %@ not found, adding to locationManager", fenceId);
                
                double latitude = [[fence objectForKey:@"latitude"] doubleValue];
                double longitude = [[fence objectForKey:@"longitude"] doubleValue];
                float radius = [[fence objectForKey:@"radius"] floatValue];
                
                CLLocationCoordinate2D coords = CLLocationCoordinate2DMake(latitude, longitude);
                CLRegion* region = [[CLCircularRegion alloc] initWithCenter:coords radius:radius identifier:fenceId];
                
                if (SYSTEM_VERSION_GREATER_THAN_OR_EQUAL_TO(@"7.0")) {
                    int transitionType = [[fence objectForKey:@"transitionType"] intValue];
                    region.notifyOnEntry = (transitionType & 1) == 1 || (transitionType & 4) == 4;
                    region.notifyOnExit = (transitionType & 2) == 2;
                }
                
                [self.locationManager startMonitoringForRegion:region];
            }
        }
    } else {
        NSLog(@"addFences called with %ld fences", (unsigned long)[fences count]);
        for (NSDictionary* fence in fences) {
            NSString *fenceId = [fence objectForKey:@"id"];
            if (fenceId == nil) {
                continue;
            }
            
            BOOL add = YES;
            for (int i = 0; i < [self.fences count]; i++) {
                NSString *existingId = [[self.fences objectAtIndex:i] objectForKey:@"id"];
                
                if ([fenceId isEqualToString:existingId]) {
                    add = NO;
                    [self.fences replaceObjectAtIndex:i withObject:fence];
                }
            }
            
            if (add == YES) {
                [self.fences addObject:fence];
            }
            
            if ([CLLocationManager authorizationStatus] == kCLAuthorizationStatusAuthorizedAlways) {
                double latitude = [[fence objectForKey:@"latitude"] doubleValue];
                double longitude = [[fence objectForKey:@"longitude"] doubleValue];
                float radius = [[fence objectForKey:@"radius"] floatValue];
                
                CLLocationCoordinate2D coords = CLLocationCoordinate2DMake(latitude, longitude);
                CLRegion* region = [[CLCircularRegion alloc] initWithCenter:coords radius:radius identifier:fenceId];
                
                if (SYSTEM_VERSION_GREATER_THAN_OR_EQUAL_TO(@"7.0")) {
                    int transitionType = [[fence objectForKey:@"transitionType"] intValue];
                    region.notifyOnEntry = (transitionType & 1) == 1 || (transitionType & 4) == 4;
                    region.notifyOnExit = (transitionType & 2) == 2;
                }
                
                [self.locationManager startMonitoringForRegion:region];
            } else {
                NSLog(@"startMonitoringForRegion skipped, authorization not granted");
            }
        }
    }
}

- (void)removeFences:(NSArray*)ids {
    NSMutableArray *remove = [NSMutableArray array];

    for (NSString* removeId in ids) {
        for (int i = 0; i < [self.fences count]; i++) {
            NSString *fenceId = [[self.fences objectAtIndex:i] objectForKey:@"id"];
            
            if ([fenceId isEqualToString:removeId]) {
                [remove addObject:[self.fences objectAtIndex:i]];
                break;
            }
        }

        NSSet* regions = [self.locationManager monitoredRegions];
        for(CLRegion *region in regions) {
            if ([region.identifier isEqualToString:removeId]) {
                [self.locationManager stopMonitoringForRegion:region];
                break;
            }
        }
    }
    
    [self.fences removeObjectsInArray:remove];
}

- (void)removeAllFences {
    NSSet* regions = [self.locationManager monitoredRegions];
    for(CLRegion *region in regions) {
        [self.locationManager stopMonitoringForRegion:region];
    }
    
    [self.fences removeAllObjects];
}

- (NSDictionary*)getFence:(NSString*)id {
    for (NSDictionary* fence in self.fences) {
        NSString *fenceId = [fence objectForKey:@"id"];
        
        if ([fenceId isEqualToString:id]) {
            return [fence copy];
        }
    }
    
    return nil;
}

- (NSArray*)getFences {
    return self.fences;
}

- (void)checkLocationPermissions {
    if ([CLLocationManager authorizationStatus] != kCLAuthorizationStatusAuthorizedAlways) {
        [self.locationManager requestAlwaysAuthorization];
    }
}

- (void)checkPermissions:(NSNumber*)types {
    if ([CLLocationManager authorizationStatus] != kCLAuthorizationStatusAuthorizedAlways) {
        [self.locationManager requestAlwaysAuthorization];
    }

    [self.notificationCenter getNotificationSettingsWithCompletionHandler:^(UNNotificationSettings * _Nonnull settings) {
        if (settings.authorizationStatus != UNAuthorizationStatusAuthorized) {
            [self.notificationCenter requestAuthorizationWithOptions:[types intValue] completionHandler:^(BOOL granted, NSError * _Nullable error) {
                if (error != nil) {
                    NSLog(@"GeofencePlugin localNotification permission error: %@", [error localizedDescription]);
                    self.notificationPermission = [NSNumber numberWithBool:NO];
                } else if (!granted) {
                    NSLog(@"GeofencePlugin localNotification permission not granted");
                    self.notificationPermission = [NSNumber numberWithBool:NO];
                }
            }];
        }
    }];
}

- (NSNumber*)hasPermission {
    if (self.notificationPermission != nil && [self.notificationPermission boolValue] && self.locationPermission != nil && [self.locationPermission boolValue]) {
        return [NSNumber numberWithBool:YES];
    }

    return [NSNumber numberWithBool:NO];
}

#pragma mark -
#pragma mark Delegates

- (void)geofencePluginDidFinishLaunching:(NSNotification*)notification {
    NSLog(@"geofencePluginDidFinishLaunching");
    
    self.locationManager = [[CLLocationManager alloc] init];
    self.locationManager.delegate = self;
    // self.locationmanager.desiredAccuracy = kCLLocationAccuracyBest;

    if (SYSTEM_VERSION_GREATER_THAN_OR_EQUAL_TO(@"10.0")) {
        self.notificationCenter = [UNUserNotificationCenter currentNotificationCenter];
        self.notificationCenter.delegate = self;
    }

    NSString *path = NSSearchPathForDirectoriesInDomains(NSLibraryDirectory, NSUserDomainMask, YES)[0];
    NSString *filename = [path stringByAppendingString:@"/fences.dat"];
    
    if ([[NSFileManager defaultManager] fileExistsAtPath:filename]) {
        NSLog(@"Restoring previous fences");
        self.fences = [NSMutableArray arrayWithContentsOfFile:filename];
    }
    
    if (self.fences == nil) {
        self.fences = [NSMutableArray array];
    }
    
    if (notification) {
        NSDictionary *launchOptions = [notification userInfo];
        self.transitionEvent = [launchOptions objectForKey:UIApplicationLaunchOptionsRemoteNotificationKey];
        self.coldstart = [NSNumber numberWithBool:YES];
    } else {
        self.coldstart = [NSNumber numberWithBool:NO];
    }
    
    self.locationPermission = [NSNumber numberWithBool:([CLLocationManager authorizationStatus] == kCLAuthorizationStatusAuthorizedAlways)];
}

- (void)geofencePluginDidBecomeActive:(NSNotification*)notification {
    NSLog(@"geofencePluginDidBecomeActive");

    [self.locationManager stopMonitoringSignificantLocationChanges];
    
    UIApplication *application = notification.object;
    
    GeofencePlugin *geofenceHandler = [self getCommandInstance:@"Geofence"];
    if (geofenceHandler.clearBadge) {
        application.applicationIconBadgeNumber = 0;
    }
    
    [geofenceHandler load];
    
    if (self.transitionEvent) {
        NSMutableDictionary *transitionEvent = [self.transitionEvent mutableCopy];
        [transitionEvent setValue:[NSNumber numberWithBool:NO] forKey:@"foreground"];
        [transitionEvent setValue:self.coldstart forKey:@"coldstart"];
        self.transitionEvent = nil;
        self.coldstart = [NSNumber numberWithBool:NO];
        
        dispatch_async(dispatch_get_main_queue(), ^{
            [geofenceHandler sendEvent:transitionEvent];
        });
    }
}

- (void)geofencePluginDidEnterBackground:(NSNotification*)notification {
    [self.locationManager startMonitoringSignificantLocationChanges];
    
    NSLog(@"geofencePluginDidEnterBackground");
    __block UIBackgroundTaskIdentifier identifier = [[UIApplication sharedApplication] beginBackgroundTaskWithExpirationHandler:^{
        if (identifier != UIBackgroundTaskInvalid) {
            NSLog(@"Expired");
            [[UIApplication sharedApplication] endBackgroundTask:identifier];
            identifier = UIBackgroundTaskInvalid;
        }
    }];

    GeofencePlugin *geofenceHandler = [self getCommandInstance:@"Geofence"];
    dispatch_async(dispatch_get_main_queue(), ^{
        NSString *path = NSSearchPathForDirectoriesInDomains(NSLibraryDirectory, NSUserDomainMask, YES)[0];
        NSString *filename = [path stringByAppendingString:@"/fences.dat"];
        
        NSLog(@"Saving fences to %@", filename);
        [self.fences writeToFile:filename atomically:YES];

        NSLog(@"Telling handler to save pending data");
        [geofenceHandler save];
        
        if (identifier != UIBackgroundTaskInvalid) {
            NSLog(@"Finishing");
            [[UIApplication sharedApplication] endBackgroundTask:identifier];
            identifier = UIBackgroundTaskInvalid;
        }
    });
}

- (void)application:(UIApplication *)application didReceiveLocalNotification:(UILocalNotification *)notification {
    if (SYSTEM_VERSION_LESS_THAN(@"10.0")) {
        NSDictionary *transitionEvent = [notification userInfo];

        if (transitionEvent) {
            GeofencePlugin *geofenceHandler = [self getCommandInstance:@"Geofence"];
            dispatch_async(dispatch_get_main_queue(), ^{
                [geofenceHandler sendEvent:transitionEvent];
            });
        }
    }
}

- (void)application:(UIApplication *)application handleActionWithIdentifier:(NSString *)identifier forLocalNotification:(UILocalNotification *)notification completionHandler:(void (^)())completionHandler {
    NSMutableDictionary *transitionEvent = [[notification userInfo] mutableCopy];
    [transitionEvent setObject:identifier forKey:@"callback"];
    
    GeofencePlugin *geofenceHandler = [self getCommandInstance:@"Geofence"];

    if (application.applicationState == UIApplicationStateActive) {
        dispatch_async(dispatch_get_main_queue(), ^{
            [geofenceHandler sendEvent:transitionEvent];
        });
    } else {
        void (^safeHandler)() = ^(void) {
            dispatch_async(dispatch_get_main_queue(), ^{
                completionHandler();
            });
        };

        if (geofenceHandler.tasks == nil) {
            geofenceHandler.tasks = [NSMutableDictionary dictionary];
        }

        [geofenceHandler.tasks setObject:safeHandler forKey:@"handler"];
        [transitionEvent setObject:@"handler" forKey:@"task"];

        dispatch_async(dispatch_get_main_queue(), ^{
            [geofenceHandler sendEvent:transitionEvent];
        });
    }
}

- (void)userNotificationCenter:(UNUserNotificationCenter *)center willPresentNotification:(UNNotification *)notification withCompletionHandler:(void (^)(UNNotificationPresentationOptions))completionHandler {
    GeofencePlugin *geofenceHandler = [self getCommandInstance:@"Geofence"];
    
    if ([[UIApplication sharedApplication] applicationState] == UIApplicationStateActive && !geofenceHandler.forceShow) {
        completionHandler(UNNotificationPresentationOptionNone);
    }
    
    completionHandler(UNNotificationPresentationOptionAlert);
}

- (void)userNotificationCenter:(UNUserNotificationCenter *)center didReceiveNotificationResponse:(UNNotificationResponse *)response withCompletionHandler:(void (^)())completionHandler {
    if (response.actionIdentifier != UNNotificationDismissActionIdentifier) {
        NSDictionary *userInfo = response.notification.request.content.userInfo;
    
        if (userInfo != nil) {
            NSMutableDictionary *transitionEvent = [userInfo mutableCopy];
        
            if (response.actionIdentifier != UNNotificationDefaultActionIdentifier) {
                [transitionEvent setObject:response.actionIdentifier forKey:@"callback"];
            }
        
            GeofencePlugin *geofenceHandler = [self getCommandInstance:@"Geofence"];
            if ([[UIApplication sharedApplication] applicationState] == UIApplicationStateActive) {
                dispatch_async(dispatch_get_main_queue(), ^{
                    [geofenceHandler sendEvent:transitionEvent];
                });
            } else {
                void (^safeHandler)() = ^(void) {
                    dispatch_async(dispatch_get_main_queue(), ^{
                        completionHandler();
                    });
                };
            
                if (geofenceHandler.tasks == nil) {
                    geofenceHandler.tasks = [NSMutableDictionary dictionary];
                }
            
                [geofenceHandler.tasks setObject:safeHandler forKey:@"handler"];
                [transitionEvent setObject:@"handler" forKey:@"task"];

                dispatch_async(dispatch_get_main_queue(), ^{
                    [geofenceHandler sendEvent:transitionEvent];
                });
            }
        }
    }
}

- (void)application:(UIApplication *)application didRegisterUserNotificationSettings:(UIUserNotificationSettings *)notificationSettings {
    if ([notificationSettings types] == UIUserNotificationTypeNone) {
        self.notificationPermission = [NSNumber numberWithBool:NO];
    }
}

- (void)locationManager:(CLLocationManager *)manager didChangeAuthorizationStatus:(CLAuthorizationStatus)status {
    if (status != kCLAuthorizationStatusAuthorizedAlways) {
        self.locationPermission = [NSNumber numberWithBool:NO];
    } else {
        self.locationPermission = [NSNumber numberWithBool:YES];
        [self addFences:nil];
    }
}

- (void)locationManager:(CLLocationManager *)manager didStartMonitoringForRegion:(CLRegion *)region {
    NSLog(@"didStartMonitoringForRegion %@", region.identifier);
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 200000000), dispatch_get_main_queue(), ^{
        [manager requestStateForRegion:region];
    });
}

- (void)locationManager:(CLLocationManager *)manager monitoringDidFailForRegion:(CLRegion *)region withError:(NSError *)error {
    NSLog(@"monitoringDidFailForRegion %@ - %@", region.identifier, [error localizedDescription]);

    if (error.domain == kCLErrorDomain && error.code == 5) {
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 200000000), dispatch_get_main_queue(), ^{
            [manager requestStateForRegion:region];
        });
    } else {
        GeofencePlugin *geofenceHandler = [self getCommandInstance:@"Geofence"];
        dispatch_async(dispatch_get_main_queue(), ^{
            [geofenceHandler sendError:@"failed" withMessage:[error localizedDescription]];
        });
    }
}

- (void)locationManager:(CLLocationManager *)manager didDetermineState:(CLRegionState)state forRegion:(CLRegion *)region {
    NSLog(@"didDetermineState - %@", region.identifier);
    if (state == CLRegionStateInside) {
        [self handleTransition:1 forRegion:region];
    } else if (state == CLRegionStateOutside) {
        [self handleTransition:2 forRegion:region];
    }
}

- (void)handleTransition:(int)transitionType forRegion:(CLRegion*)region {
    BOOL foreground = [[UIApplication sharedApplication] applicationState] == UIApplicationStateActive;
    __block UIBackgroundTaskIdentifier identifier = UIBackgroundTaskInvalid;
    if (!foreground) {
        identifier = [[UIApplication sharedApplication] beginBackgroundTaskWithExpirationHandler:^{
            if (identifier != UIBackgroundTaskInvalid) {
                NSLog(@"Expired");
                [[UIApplication sharedApplication] endBackgroundTask:identifier];
                identifier = UIBackgroundTaskInvalid;
            }
        }];
    }

    GeofencePlugin *geofenceHandler = [self getCommandInstance:@"Geofence"];

    NSLog(@"handleTransition: %d, %@", transitionType, region.identifier);
    
    NSLog(@"geofenceHandler: %@", geofenceHandler);
    
    for(NSDictionary *fence in self.fences) {
        NSString *fenceId = [fence objectForKey:@"id"];
        
        if ([fenceId isEqualToString:region.identifier]) {
            id transitionTypeField = [fence objectForKey:@"transitionType"];
            if (transitionTypeField == nil || ![transitionTypeField isKindOfClass:[NSNumber class]] || ([transitionTypeField intValue] & transitionType) != transitionType) {
                continue;
            }
            
            double timestamp = [self.locationManager.location.timestamp timeIntervalSince1970];
            if (timestamp == 0) {
                timestamp = [[NSDate dateWithTimeIntervalSinceNow:0] timeIntervalSince1970];
            }
            
            timestamp = timestamp * 1000;
            
            NSMutableDictionary *transitionLocation = [NSMutableDictionary dictionary];
            [transitionLocation setValue:[NSNumber numberWithDouble:self.locationManager.location.coordinate.latitude] forKey:@"latitude"];
            [transitionLocation setValue:[NSNumber numberWithDouble:self.locationManager.location.coordinate.longitude] forKey:@"longitude"];
            [transitionLocation setValue:[NSNumber numberWithFloat:self.locationManager.location.horizontalAccuracy] forKey:@"accuracy"];
            [transitionLocation setValue:[NSNumber numberWithDouble:self.locationManager.location.altitude] forKey:@"altitude"];
            [transitionLocation setValue:[NSNumber numberWithFloat:self.locationManager.location.course] forKey:@"bearing"];
            [transitionLocation setValue:[NSNumber numberWithFloat:self.locationManager.location.speed] forKey:@"speed"];
            [transitionLocation setValue:[NSNumber numberWithLong:timestamp] forKey:@"time"];
            [transitionLocation setValue:[NSNumber numberWithBool:YES] forKey:@"hasAccuracy"];
            [transitionLocation setValue:[NSNumber numberWithBool:YES] forKey:@"hasAltitude"];
            [transitionLocation setValue:[NSNumber numberWithBool:(self.locationManager.location.course > 0)] forKey:@"hasBearing"];
            [transitionLocation setValue:[NSNumber numberWithBool:(self.locationManager.location.speed > 0)] forKey:@"hasSpeed"];
            
            NSMutableDictionary *transitionEvent = [NSMutableDictionary dictionary];
            [transitionEvent setValue:[NSArray arrayWithObject:region.identifier] forKey:@"ids"];
            [transitionEvent setValue:[NSNumber numberWithInt:transitionType] forKey:@"transitionType"];
            [transitionEvent setValue:transitionLocation forKey:@"location"];
            
            NSDictionary *notification = [fence objectForKey:@"notification"];
            NSString *message;
            
            if (notification != nil) {
                if (transitionType == 1) {
                    message = [notification objectForKey:@"enterMessage"];
                } else if (transitionType == 2) {
                    message = [notification objectForKey:@"exitMessage"];
                }
                
                if (message == nil) {
                    message = [notification objectForKey:@"message"];
                }
            }

            BOOL coldstart = YES;
            if (self.coldstart != nil) {
                coldstart = [self.coldstart boolValue];
            }

            [transitionEvent setValue:[NSNumber numberWithBool:foreground] forKey:@"foreground"];
            [transitionEvent setValue:[NSNumber numberWithBool:coldstart] forKey:@"coldStart"];

            if (foreground && (SYSTEM_VERSION_LESS_THAN(@"10.0") || !geofenceHandler.forceShow)) {
                //The app is in foreground and runs on either iOS < 10 or forceShow is disabled
                dispatch_async(dispatch_get_main_queue(), ^{
                    NSLog(@"foreground && !forceShow || iOS < 10: Send to js");
                    [geofenceHandler sendEvent:transitionEvent];
                });
            } else {
                NSNumber* background = [fence objectForKey:@"forceStart"];

                int notificationId = -1;
                if (notification != nil) {
                    id notificationIdField = [notification objectForKey:@"id"];
                    if (notificationIdField != nil && [notificationIdField isKindOfClass:[NSNumber class]]) {
                        notificationId = [[notification objectForKey:@"id"] intValue];
                    }
                }

                NSMutableDictionary *userInfo = [transitionEvent mutableCopy];
                [userInfo setValue:region.identifier forKey:@"id"];
                [userInfo setValue:[NSNumber numberWithInt:notificationId] forKey:@"notificationId"];

                if (message != nil) {
                    NSLog(@"Sending notification with message %@", message);
                    if (SYSTEM_VERSION_GREATER_THAN_OR_EQUAL_TO(@"10.0")) {
                        UNMutableNotificationContent *content = [[UNMutableNotificationContent alloc] init];
                        content.title = [notification objectForKey:@"title"];
                        content.body = message;

                        NSString *sound = [notification objectForKey:@"sound"];
                        if (sound != nil && ![sound isEqualToString:@"default"]) {
                            content.sound = [UNNotificationSound soundNamed:sound];
                        } else {
                            content.sound = [UNNotificationSound defaultSound];
                        }

                        //TODO: attachments: @see https://developer.apple.com/reference/usernotifications/unnotificationattachment/1649987-attachmentwithidentifier?language=objc
                        //TODO: buttons: @see https://developer.apple.com/reference/usernotifications/unnotificationcategory/2196944-categorywithidentifier?language=objc
                        //TODO: text input action: @see https://developer.apple.com/reference/usernotifications/untextinputnotificationaction?language=objc
                        //content.category = [notification objectForKey:category];

                        content.badge = [[NSNumber alloc] initWithLong:[[UIApplication sharedApplication] applicationIconBadgeNumber] + 1];
                        content.userInfo = userInfo;
                            
                        UNNotificationRequest *request = [UNNotificationRequest requestWithIdentifier:[[NSString alloc] initWithFormat:@"%d", notificationId] content:content trigger:nil];
                            
                        [self.notificationCenter addNotificationRequest:request withCompletionHandler:^(NSError * _Nullable error) {
                            if (error != nil) {
                                NSLog(@"GeofencePlugin addNotificationRequest error: %@", [error localizedDescription]);
                            }
                        }];
                    } else {
                        UILocalNotification *localNotification = [[UILocalNotification alloc] init];
                        localNotification.alertBody = message;
                        localNotification.applicationIconBadgeNumber = [[UIApplication sharedApplication] applicationIconBadgeNumber] + 1;
                        
                        if (SYSTEM_VERSION_GREATER_THAN_OR_EQUAL_TO(@"8.2")) {
                            localNotification.alertTitle = [notification objectForKey:@"title"];
                        }
                        
                        NSString *sound = [notification objectForKey:@"sound"];
                        if (sound != nil && ![sound isEqualToString:@"default"]) {
                            localNotification.soundName = sound;
                        } else {
                            localNotification.soundName = UILocalNotificationDefaultSoundName;
                        }
                        
                        if (SYSTEM_VERSION_GREATER_THAN_OR_EQUAL_TO(@"8.0")) {
                            localNotification.category = [notification objectForKey:@"category"];
                        }
                        
                        [[UIApplication sharedApplication] scheduleLocalNotification:localNotification];
                    }
                } else {
                    NSLog(@"No message body, skipping notification");
                }

                if (message == nil || (background != nil && [background boolValue])) {
                    NSMutableDictionary *backgroundEvent = [userInfo mutableCopy];
                    [backgroundEvent setValue:[NSNumber numberWithBool:YES] forKey:@"background"];

                    dispatch_async(dispatch_get_main_queue(), ^{
                        NSLog(@"Sending to js");
                        if (identifier != UIBackgroundTaskInvalid) {
                            if (geofenceHandler.tasks == nil) {
                                geofenceHandler.tasks = [NSMutableDictionary dictionary];
                            }

                            [geofenceHandler.tasks setObject:^void (){
                                dispatch_async(dispatch_get_main_queue(), ^{
                                    if (identifier != UIBackgroundTaskInvalid) {
                                        [[UIApplication sharedApplication] endBackgroundTask:identifier];
                                        identifier = UIBackgroundTaskInvalid;
                                    }
                                });
                            } forKey:[NSNumber numberWithLong:identifier]];
                            [backgroundEvent setValue:[NSNumber numberWithLong:identifier] forKey:@"task"];
                        }

                        [geofenceHandler sendEvent:backgroundEvent];
                    });
                } else if (!foreground) {
                    dispatch_async(dispatch_get_main_queue(), ^{
                        if (identifier != UIBackgroundTaskInvalid) {
                            [[UIApplication sharedApplication] endBackgroundTask:identifier];
                            identifier = UIBackgroundTaskInvalid;
                        }
                    });
                }
            }
        }
    }
}

- (NSDictionary*)transitionEvent {
    return objc_getAssociatedObject(self, &transitionEventKey);
}

- (void)setTransitionEvent:(NSDictionary*)transitionEvent {
    objc_setAssociatedObject(self, &transitionEventKey, transitionEvent, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

- (NSNumber*)coldstart {
    return objc_getAssociatedObject(self, &coldStartKey);
}

- (void)setColdstart:(NSNumber *)coldstart {
    objc_setAssociatedObject(self, &coldStartKey, coldstart, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

- (NSNumber*)locationPermission {
    return objc_getAssociatedObject(self, &locationPermissionKey);
}

- (void)setLocationPermission:(NSNumber *)locationPermission {
    objc_setAssociatedObject(self, &locationPermissionKey, locationPermission, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

- (NSNumber*)notificationPermission {
    return objc_getAssociatedObject(self, &notificationPermissionKey);
}

- (void)setNotificationPermission:(NSNumber *)notificationPermission {
    objc_setAssociatedObject(self, &notificationPermissionKey, notificationPermission, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

- (NSMutableArray*)fences {
    return objc_getAssociatedObject(self, &fencesKey);
}

- (void)setFences:(NSMutableArray*)fences {
    objc_setAssociatedObject(self, &fencesKey, fences, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

- (CLLocationManager*)locationManager {
    return objc_getAssociatedObject(self, &locationManagerKey);
}

- (void)setLocationManager:(CLLocationManager*)locationManager {
    objc_setAssociatedObject(self, &locationManagerKey, locationManager, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}


- (UNUserNotificationCenter*)notificationCenter {
    return objc_getAssociatedObject(self, &notificationCenterKey);
}

- (void)setNotificationCenter:(UNUserNotificationCenter*)notificationCenter {
    objc_setAssociatedObject(self, &notificationCenterKey, notificationCenter, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

- (void)dealloc {
    self.locationManager = nil;
    self.fences = nil;
    self.transitionEvent = nil;
    self.coldstart = nil;
}

@end
