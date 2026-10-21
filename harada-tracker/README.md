# Harada Tracker

A small Android app for the Harada Method chart: check off daily actions, keep a streak, and get reminders.

- A day counts toward the streak when at least 80% of the items are checked.
- One rest day per week (Monday to Sunday) protects the streak.
- Reminders: morning nudge (always), evening warning and last call (only if today isn't safe yet). Times are set in the app.

GitHub Actions builds the APK on every push to `main` and attaches it to the `latest` release:
`https://github.com/<user>/harada-tracker/releases/latest/download/HaradaTracker.apk`
