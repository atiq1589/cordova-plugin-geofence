#import "AppDelegate.h"

#define SYSTEM_VERSION_GREATER_THAN_OR_EQUAL_TO(v) ([[[UIDevice currentDevice] systemVersion] compare:v options:NSNumericSearch] != NSOrderedAscending)
#define SYSTEM_VERSION_LESS_THAN(v) ([[[UIDevice currentDevice] systemVersion] compare:v options:NSNumericSearch] == NSOrderedAscending)

@import CoreLocation;
@import UserNotifications;
@import UIKit;

@interface AppDelegate (GeofencePlugin) <CLLocationManagerDelegate, UNUserNotificationCenterDelegate>
@property (nonatomic, retain) CLLocationManager* locationManager;
@property (nonatomic, retain) UNUserNotificationCenter* notificationCenter;
@property (nonatomic, retain) NSMutableArray* fences;
@property (nonatomic, retain) NSDictionary *transitionEvent;
@property (nonatomic, retain) NSNumber *coldstart;
@property (nonatomic, retain) NSNumber *locationPermission;
@property (nonatomic, retain) NSNumber *notificationPermission;

- (void)addFences:(NSArray*)fences;
- (void)removeFences:(NSArray*)ids;
- (void)removeAllFences;
- (NSDictionary*)getFence:(NSString*)id;
- (NSArray*)getFences;
- (void)checkLocationPermissions;
- (void)checkPermissions:(NSNumber*)types;
- (NSNumber*)hasPermission;
@end
