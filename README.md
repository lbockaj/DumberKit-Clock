# DumberKit: Clock
<img alt="Logo" src="graphics/icon.png" width="120" />

A clock, alarm, stopwatch and timer for **keypad phones** – the Qin F21, Dumber Mini, Doov R77 Pro and other small
Android phones with a D-pad and a T9 keypad. Its core is [Fossify Clock](https://github.com/FossifyOrg/Clock).
Every screen, menu and dialog works with the keys alone: no floating buttons, no swiping, sized for a 480×640 screen.

## How it works

The app opens on a list: **Clock, Alarm, Stopwatch, Timer** and, under a divider, **Settings**. ↑ / ↓ and OK open a
screen (or press 1–5), Back returns to the list.

| Screen | Keys |
| --- | --- |
| **Alarm** | OK edits the alarm · ← / → switch it off / on · hold OK: on/off, delete · *+ Add alarm* row at the end |
| **Editing an alarm** | time, *Repeat* (a checklist of days), vibration, sound, label; *Cancel* / *Save* rows at the bottom |
| **Picking a time** | type it (0730 = 07:30) or ↑ / ↓ · OK or → moves to the next column, from the last one to *Save* |
| **Ringing alarm** | ← or OK snoozes · → dismisses · Back snoozes too, so a half-asleep key press never turns it off |
| **Stopwatch** | OK starts / pauses · → adds a lap while running · ← resets while paused |
| **Timer** | → starts · ← pauses (silences a finished one) · OK: edit, reset, delete · *+ New timer* row |
| **Clock** | your time zone under the date · OK renames an added zone · *+ Add time zone* row |

The **Menu** key shows the screen's other actions (delete, sort…). Long lists in dialogs (time zones, days, alarm
sounds) have a green check and a red cross on the right: → jumps there from any row.

## Features

- **Alarms you can stop with the keys** – the ringing screen shows snooze ← alarm → dismiss, with short labels.
- **Works on DumberOS** – DumberOS shows no pop-up notifications, so on an unlocked phone a ringing alarm would only
  sound. The app asks once for *Display over other apps* and then opens the alarm screen itself (*Settings → Alarm
  over other apps* shows whether it's allowed; the system page itself needs one touch).
- **Keys only** – touches are ignored in the app, *Settings → Allow touch input* turns them back on.
- **Colors and themes** – System (follows Android), light, dark and custom themes; purple app icon by default, other
  colors in *Settings → Customize colors*.
- **Private** – no ads, no tracking, no internet access.

## Install

1. Download `dumberkit-clock.apk` from the [latest release](../../releases/latest).
2. Open it on the phone and allow installing apps from that source when Android asks.
3. Start **Clock** with the purple icon and allow the permissions it asks for (notifications, full-screen alarms,
   display over other apps).

Requires Android 8.0 or newer.

## Build

```bash
./gradlew assembleFossRelease
```

The release build is signed with the key from `keystore.properties` (see `keystore.properties_sample`), the APK ends up
in `app/build/outputs/apk/foss/release/`.

## License

DumberKit: Clock is free software under the [GNU General Public License v3.0](LICENSE).

## Credits

The core of the app is [Fossify Clock](https://github.com/FossifyOrg/Clock) and its
[Commons](https://github.com/FossifyOrg/Commons) library (GPL-3.0) – alarms, timers, the stopwatch, themes and
translations come from there. DumberKit: Clock is a modified version, reworked in 2026 for keypad navigation and small
screens. Thanks to the Fossify team and its translators.
