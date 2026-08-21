# Pomodoro Timer — Android

The Android port of `D:\claude\Pomodoro timer` (whose notes are in `pomodoro-timer.md`).
Same timer, same three themes, same end-of-session prompts; Kotlin and Jetpack Compose
instead of Python and Tkinter.

## The APK

```
dist\PomodoroTimer-debug.apk        8.4 MB
```

Built and signed with the standard Android debug key, which is what emulators accept.
`com.pomodoro.timer`, `versionName` **1.1.0** / `versionCode` **10100** (both read
from `VERSION` — see *Version control*), Android 8.0 (API 26) and newer.

To run it:

- **MuMu, LDPlayer, BlueStacks** — drag the `.apk` onto the emulator window.
- **An AVD or anything with adb** —
  `adb install -r "dist\PomodoroTimer-debug.apk"`.

It is a *debug* build, so Android may warn that it comes from an unknown developer; allow
it. A release build would need a signing key, which is only worth setting up if the app
is going somewhere other than your own emulator.

## Rebuilding it

### With Android Studio

1. Install [Android Studio](https://developer.android.com/studio) if you don't have it. It
   brings its own JDK and Android SDK, so nothing else is needed.
2. **File → Open** and pick this folder (`D:\claude\Pomodoro timer Android`).
3. Wait for the first Gradle sync. It downloads Gradle 8.7 and the Android build tools, so
   the first one takes a few minutes; later ones are quick.
   - The Gradle wrapper JAR is not in this folder (it's a binary that has to come from a
     Gradle release). Android Studio recreates it during the first sync. If it asks, let it
     use the Gradle wrapper at the version in `gradle/wrapper/gradle-wrapper.properties`.
4. Start your emulator, then press **Run ▶**. Studio installs and launches the app.

To get an APK you can install anywhere: **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
It lands in `app\build\outputs\apk\debug\app-debug.apk`.

### From the command line, with the toolchain already here

The APK in `dist\` was built this way. A self-contained toolchain — JDK 17, the Android
SDK (platform 34, build-tools 34.0.0, platform-tools), Gradle 8.7, and Gradle's dependency
cache — lives in `D:\claude\android-build`, along with the three scripts that put it there:

```powershell
powershell -ExecutionPolicy Bypass -File "D:\claude\android-build\build-apk.ps1"
```

That rebuilds `app\build\outputs\apk\debug\app-debug.apk`; copy it over `dist\` to publish
it. The full Gradle output goes to `D:\claude\android-build\build.log`.

**The toolchain takes 2.2 GB.** Nothing outside that one folder was touched — no installer
ran, no `PATH` or registry entry changed, and `JAVA_HOME` is set only inside the build
script. Delete `D:\claude\android-build` and every trace of it is gone (you'd then need
Android Studio, or to re-run `fetch-toolchain.ps1` and `setup-toolchain.ps1`, to build
again). `local.properties` points at that SDK and is machine-specific, which is why it's
in `.gitignore`.

Requires Android 8.0 (API 26) or newer; built against API 34.

## What carried over, and what couldn't

| Desktop feature | Android |
| --- | --- |
| Work presets 15 / 30 / 45 | Same |
| Break presets 5 / 10 / 15 | Same |
| Custom length, 1–180 min, per row | Same, as a dialog |
| Depleting ring, `MM:SS`, `WORK` / `BREAK`, `PAUSED` | Same, sized to the screen. Tall screens stack the ring above the controls; wide or short ones put it alongside, so nothing needs scrolling |
| Start / Pause / Reset | Same |
| Completed-session counter | Same, and it survives the app being killed |
| Three themes: Black, Paper, Mist | Same palettes, same hex values |
| Three 880 Hz beeps at the end | Same tone, synthesised sample-by-sample |
| End-of-session prompt with the next duration | Same wording, same choices |
| No clock drift | Same approach, `elapsedRealtime()` instead of `time.monotonic()` |
| Remaining time in the title bar | Remaining time in a notification that counts down |
| Raises the window at the end | Notification + sound + vibration |
| **Always on top** | **No equivalent** — Android has no window stack to sit on top of. The nearest useful thing, a *Keep screen on* switch, is in its place |
| **Window scaling 50–250%, and the size lock** | **Not applicable** — those exist because a desktop window has a size you can drag. An Android app is given its window by the system, so the layout adapts to whatever it gets instead |

The two dropped features are both window management, which is the one part of the desktop
app that has no counterpart here. Everything the *timer* does came across.

Two things the Android version does that the desktop one can't:

- **It keeps time while it isn't running.** Leave the app, lock the phone, or let Android
  kill the process — an exact alarm still fires at the deadline, plays the tone and posts a
  notification, and the session is still counted. Reopen the app and the usual prompt is
  waiting.
- **The countdown ticks in the notification shade** without anything running in the
  background: the platform's own chronometer does it.

## Layout

```
Pomodoro timer Android\
├── Pomodoro timer Android.md     this file
├── VERSION                       current version number — see Version control
├── .gitignore                    keeps dist\, build\, .gradle\, local.properties out of git
├── .gitattributes                stores every file byte for byte
├── dist\
│   └── PomodoroTimer-debug.apk   the built app, 8.4 MB — not in git, rebuild it
├── make_launcher_icons.py        redraws the launcher icon (needs Pillow)
├── settings.gradle.kts           }
├── build.gradle.kts              } Gradle build, versions pinned as a known-good set
├── gradle.properties             }
├── gradle\wrapper\...properties  Gradle 8.7
└── app\
    ├── build.gradle.kts
    └── src\main\
        ├── AndroidManifest.xml
        ├── java\com\pomodoro\timer\
        │   ├── MainActivity.kt   window, system bars, keep-screen-on, permissions
        │   ├── TimerViewModel.kt the state machine — the port of PomodoroTimer
        │   ├── Ui.kt             the whole screen, ring included
        │   ├── Theme.kt          hands the palette to Material
        │   ├── Palette.kt        the three themes
        │   ├── Model.kt          Mode, presets, TimerState, mm:ss
        │   ├── Prefs.kt          the counterpart of settings.json
        │   ├── Alerts.kt         tone, vibration, notifications
        │   └── Sessions.kt       exact alarm, and the one path that ends a session
        └── res\                  strings, icons, theme
```

## Implementation notes

- **The countdown is a deadline, not a counter**, exactly as on the desktop. Two clocks are
  kept because they answer different questions: `elapsedRealtime()` is monotonic and
  unaffected by the user changing the time, so it drives the countdown, while the wall clock
  is the only thing an alarm or a saved file can be expressed in, so it is what gets
  scheduled and persisted.
- **A session can only end once.** Two things race to end it — the in-app ticker reaching
  zero and the alarm going off — so both go through `Sessions.completeOnce()`, which records
  the deadline it handled and ignores a second attempt at the same one. Without that, a
  session finishing while the app is open would be counted twice and beep twice.
- **No foreground service.** A timer has no `foregroundServiceType` that fits, and it
  doesn't need one: an exact alarm handles the deadline, and an ongoing notification with
  `setChronometerCountDown` displays the countdown without anything staying awake to update
  it. `USE_EXACT_ALARM` is the permission meant for alarm and timer apps and needs no
  runtime prompt.
- **The beeps are synthesised.** Android has no stock tone at 880 Hz, so `Alerts` builds the
  same three 180 ms beeps as a PCM buffer, with a few milliseconds of fade at each end
  because a square-edged tone clicks.
- **Material needs the palette too.** Anything not coloured by hand — dialog surfaces, the
  text field, ripples — reads `MaterialTheme.colorScheme`, which defaults to a *light*
  scheme. Left alone it puts dark text on the Black theme's dark dialogs, so `Theme.kt`
  builds a scheme from the active palette.
- **The window is painted before the first frame.** `MainActivity.onCreate` sets the window
  background from the saved theme, otherwise a light theme starts with a dark flash.
- **The launcher icon is the desktop icon.** `make_launcher_icons.py` runs the same drawing
  code as `Pomodoro timer\make_icon.py` at 1024 px and downsamples it into every density
  bucket, plus an adaptive-icon foreground inset to the 66% safe zone.

### Redrawing the icon

```bash
py make_launcher_icons.py
```

Needs Pillow (`py -m pip install pillow`); the app itself doesn't.

## Version control

This project is its own Git repository, with two remotes:

| Remote | Points at |
|---|---|
| `origin` | `https://github.com/Micheal-Jiaming/Pomodoro-timer-Android` — private |
| `mirror` | `D:\claude\repos\Pomodoro timer Android.git` — local bare copy |

GitHub disallows spaces in repository names, hence `Pomodoro-timer-Android`.
The desktop original is a **separate** repository
(`github.com/Micheal-Jiaming/pomodoro-timer`) and the two version
independently. Authentication is the GitHub CLI acting as git's credential
helper (`gh auth setup-git`), so pushes need no interactive prompt.

Tracked: the Gradle build files, everything under `app\src\`, the launcher-icon
PNGs, `make_launcher_icons.py`, `gradle\wrapper\gradle-wrapper.properties`, and
this document. Ignored: `dist\`, `build\` (which covers `app\build\`),
`.gradle\`, `local.properties` — the APK is rebuilt from source as described
above, an old debug APK cannot do what newer source does, and `local.properties`
is machine-specific. The Gradle wrapper JAR is absent by design; Android Studio
recreates it. `.gitattributes` sets `* -text` so every file is stored and checked
out byte for byte; Git for Windows is configured `core.autocrlf=true` system-wide
and would otherwise rewrite these LF files to CRLF.

**Versioning.** `VERSION` holds the current number; every release is tagged
`v<number>`. The baseline was **1.0.0**; **1.0.1** recorded the move to GitHub
and **1.0.2** reconciled this document with the files.

`VERSION` is the *only* place the version is written. `app\build.gradle.kts`
reads the file and sets `versionName` from it verbatim, deriving `versionCode`
arithmetically (`1.1.0` → `10100`) so it always rises with it, as `adb install -r`
and Google Play require. Nothing has to be kept in step by hand — up to **1.1.0**
the Gradle file carried its own hard-coded `versionName = "1.0"`, which had
already fallen a version behind. If `VERSION` is missing or unparsable the build
still succeeds but reports `0.0.0`, which is meant to be conspicuous.

| Update | Bump | Example |
|---|---|---|
| Major — new or changed functionality | +0.1 | 1.0.0 → 1.1.0 |
| Minor — fixes, docs, small tweaks | +0.0.1 | 1.0.0 → 1.0.1 |

**One bump per task, however many commits it takes** — not one per commit. A
task that edits code and then updates this document is a single version; write
the new number into `VERSION` in the task's last commit and tag there. Documented
changes are never exempt: this file is what a later session works from, so a
wrong line in it is a defect like any other. The rule is deliberately mechanical,
because a rule needing judgement gets applied differently by every session.

Then tag and push both remotes:

```powershell
git -C "D:\claude\Pomodoro timer Android" commit -am "..."
git -C "D:\claude\Pomodoro timer Android" tag -a v1.0.2 -m "..."
git -C "D:\claude\Pomodoro timer Android" push origin main --tags
git -C "D:\claude\Pomodoro timer Android" push mirror main --tags
```

## Status

Built, installed and driven on MuMu Player 12 (Android 12, x86_64, 720×1280 @ 240 dpi).

Verified working on the device:

- presets and selection highlighting; the ring, `MM:SS`, `WORK`/`BREAK` and `PAUSED`
- Start counting down, Pause, Reset
- **Custom** dialog — validation and a 1-minute session accepted
- the end-of-session prompt, with the desktop's exact wording, green break buttons and red
  work buttons, and the session counter incrementing to 1
- picking *5 min* from the prompt starting a green break immediately
- all three themes cycling, and Paper/Mist rendering with their own deeper accents
- the ongoing notification posting on the `running` channel as an ongoing, low-importance
  entry
- theme and session count surviving a force-stop and relaunch (`shared_prefs/pomodoro.xml`)
- no entries in the crash buffer at any point

Three defects were found and fixed in the process:

- `setKeepScreenOn` clashed with the JVM setter the `keepScreenOn` property generates for
  itself — identical signature, so the two could not coexist. Now `updateKeepScreenOn`.
- On a short landscape screen the bottom row — the theme button and *Keep screen on* — sat
  below the fold, reachable only by a scroll nobody would think to try. Wide-and-short
  screens now put the ring beside the controls instead of above them, and everything fits.
- `Row` and `Column` carry the same `DslMarker` as `BoxWithConstraints`, so its
  `maxWidth`/`maxHeight` are invisible inside the nested lambdas; they are copied into
  locals first.

The list above was verified on the **1.0.0** build. **1.1.0** changed only where the
version number comes from — no app code — and was checked by rebuilding and reading
the APK back with `aapt2 dump badging` (`versionName='1.1.0' versionCode='10100'`),
plus a configuration run with `VERSION` deliberately absent to confirm a missing
file cannot break the build. The 1.1.0 APK has **not** been re-run on an emulator;
nothing in it should behave differently.

Not verified: **portrait**. MuMu pins its display to landscape and neither `user_rotation`
nor a `wm size` override moves it, so the tall layout has only been seen rendering in a
window too short for it. It is the simpler of the two branches and has more room to work
with on a phone, but it has not been looked at on a genuinely tall screen. Also unverified:
the alarm firing after the process is killed, and how the tone actually sounds.
