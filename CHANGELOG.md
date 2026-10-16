# Changelog

## [1.0.1] - 2026-10-16
### Fixed
- The warning "You are using a fake version of the app", which Commons showed now and then, no longer shows up
- The selected dialog button is much easier to see
- On phones with an on-screen navigation bar the last rows of the settings and About stay above it

## [1.0.0] - 2026-10-10
First release, based on Fossify Clock 1.6.0.

### Added
- A home list instead of bottom tabs: Clock, Alarm, Stopwatch, Timer and Settings, with the time, the next alarm and
  a running stopwatch or timer next to them; 1–5 open the screens, Back returns to the list
- Keypad control on every screen: a clear focus frame, the Menu key for each screen's actions, "+" rows at the end of
  the lists instead of floating buttons
- Alarms: OK opens them, ← / → switch them off / on (the switch shows an arrow), hold OK to switch or delete, the days
  to repeat picked from a checklist
- Times typed with the digits (0730 = 07:30), ↑ / ↓ and ← / → in the columns, Cancel / Save rows in the dialogs
- Ringing alarm screen for the keys: ←, OK or Back snooze, → turns the alarm off; Back in the snooze picker snoozes
  instead of dismissing
- Full-screen "Time expired" for a finished timer: ← starts it again, → / OK / Back stop it
- On DumberOS, which shows no pop-up notifications, the alarm and the finished timer open over other apps
  (*Display over other apps*, asked once)
- Stopwatch: OK starts / pauses, → adds a lap, ← resets
- Clock: your own time zone under the date, hold OK on an added zone to rename or delete it, the list of zones starts
  at yours
- Keypad-friendly settings, color picker and dialogs, an About screen with all the keys
- App icon with an alarm clock pictogram, purple by default; any other color can be picked and a theme keeps it

### Changed
- Smaller app (APK 9.2 → 5.0 MB): unused resources are left out and only languages with a real translation are
  included

### Fixed
- Crashes of the alarm list and the alarm editor with newer Kotlin versions
