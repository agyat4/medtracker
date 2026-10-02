# MedTracker

A one-tap medicine dose log with a reminder alarm, for Android.

- The screen is a web page: `app/src/main/assets/www/index.html`
- The Java code in `app/src/main/java/com/medtracker/app/` sets real phone alarms.
- Every push to `main` builds the APK on GitHub (see the Actions tab). The APK is attached to a new entry under **Releases**.

The signing key (`app/medtracker.keystore`) is kept in the repo so every build can update the installed app without losing data. Keep this repo private.
