SUMO PERSISTENT TRACKING FIX

Goal:
Location sharing has NO 30-minute, 1-hour, or other intentional time limit.
Once the user starts sharing, the foreground location service is intended to
continue until the user explicitly chooses Stop Sharing.

Changes made:
- Kept high-accuracy location requests (5-second target, 2-second minimum).
- Added a watchdog that checks whether live location callbacks have gone silent.
- If no callback arrives for 90 seconds while sharing is still enabled, the app
  safely removes and re-registers the fused-location request.
- Prevented duplicate callback registration during recovery.
- Kept the foreground service START_STICKY.
- Explicitly keeps sharing enabled when the Activity is swiped from Recent Apps.
- Preserved Stop Sharing behaviour: TRACKING=false stops the service and the
  service will not intentionally restart while sharing is disabled.
- Improved service lifecycle cleanup.
- Fixed the known MainActivity Kotlin issue: setSingleLine(true).

Important Android limitations:
- Force Stop can stop the app and its services.
- Revoking location permission stops tracking.
- A phone reboot may require Android-permitted recovery/reopening the app,
  depending on Android version and OEM restrictions.
- Samsung/Xiaomi battery-management settings can still restrict background
  work. This update does not use deceptive methods to bypass Android.

Testing:
The ZIP integrity and Kotlin source structure were checked here, but a real
Android Gradle build and multi-hour physical-device test were not performed.
Test for several hours on both the Samsung A55 and Redmi 13, including after
swiping SUMO away from Recents and locking the screen.
