# Sumo — Name-only live location sharing

Independent Android app and Flask backend. It does not connect to the reference website.

## User flow
1. User enters a display name and taps Let's Go.
2. Android requests location permission.
3. The app registers the device anonymously with the Sumo backend.
4. A foreground location service uploads location updates.
5. The map reads `/api/people` and shows all participating devices that have uploaded a location.

No phone number, password, OTP, or family code is requested or displayed.

## Privacy warning
This implementation makes precise locations visible to every person who can reach the public Sumo map. Only install/use it with people who knowingly agree to share their location. There is a stop-sharing control in the app. This is not suitable for tracking strangers or people without their consent.

## Deployment
The backend is in `backend/`. Deploy it as a Python web service. Configure the public HTTPS URL in `app/src/main/java/com/kinly/familytracker/Config.kt`, then rebuild the APK.

**Persistent database storage is required on the host.** The current backend uses SQLite; on Render configure a persistent disk and set `KINLY_DB_PATH` to a file path on that disk, otherwise registrations and locations may be lost when the service restarts/redeploys.

## Build / verification status
The ZIP archive is checked for integrity. This environment has not run an Android Gradle build, installed the APK, deployed this updated backend, or tested GPS on physical devices. The updated backend must be pushed to GitHub and redeployed on Render before the updated APK can use `/api/people`. Background location remains subject to Android permissions, notifications, battery restrictions, force-stop, and connectivity.
