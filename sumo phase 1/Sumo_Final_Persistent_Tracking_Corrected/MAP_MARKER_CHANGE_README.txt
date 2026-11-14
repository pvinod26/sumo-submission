SUMO MAP MARKER UPDATE

This update changes the Leaflet map marker HTML/CSS in backend/app.py:
- Each participant marker displays their first initial in a circular badge.
- The participant's name is displayed beneath the badge.
- The existing marker coordinates, popup, and focusMember behaviour are retained.
- Config.kt now points to https://sumo-abap.onrender.com.

IMPORTANT:
The marker HTML is served by the backend. To see this map change in the app,
commit/upload the updated backend/app.py to the GitHub repository connected
to the Render service sumo-abap, then deploy the latest commit in Render.
The Android app loads the map from that backend URL, so the marker change
does not require an Android APK rebuild by itself.

If you also use the updated Config.kt, rebuild the Android APK and install it
on the devices. This archive has been integrity-checked, but an Android build
and two-device live test have not been performed here.
