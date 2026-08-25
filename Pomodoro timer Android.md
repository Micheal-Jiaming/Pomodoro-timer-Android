# Pomodoro Timer — Android

The Android port of `D:\claude\Pomodoro timer` (whose notes are in `pomodoro-timer.md`).
Same timer, same three themes, same end-of-session prompts; Kotlin and Jetpack Compose
instead of Python and Tkinter.

## The APK

Two builds, for two different purposes:

```
dist\PomodoroTimer-debug.apk                     8.4 MB   debug key
app\build\outputs\apk\release\app-release.apk    5.8 MB   release key
```

The **debug** build is signed with the standard Android debug key, which is what emulators
accept, and is the one kept in `dist\`. The **release** build is the one that gets
published: it carries the project's own signing key (see *Rebuilding it*) and is smaller,
having none of the debug tooling bundled in. Do not distribute the debug build — the
Android debug key is shared and publicly known, so anyone could forge an update against it.

Both report `io.github.michealjiaming.pomodoro`, `versionName` **1.1.2** / `versionCode`
**10102** (both read from `VERSION` — see *Version control*), Android 8.0 (API 26) and newer.

The application ID is `io.github.michealjiaming.pomodoro` and **must never change again**:
an app's ID is its permanent identity on every store, and altering it after publication
orphans every existing install. It was renamed from `com.pomodoro.timer` in 1.1.2, while
nothing was yet published — the old form implied ownership of `pomodoro.com`, whereas
`io.github.<user>` is the accepted convention for a developer without their own domain and
is common across F-Droid, which is where this app is headed (see *Publishing*).

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

For the **release** build — the one that gets published — use its sibling:

```powershell
powershell -ExecutionPolicy Bypass -File "D:\claude\android-build\build-release.ps1"
```

That produces `app\build\outputs\apk\release\app-release.apk` and then prints the signing
certificate, which is the whole reason it is a separate script: it makes shipping a
debug-signed APK by accident impossible to do quietly. A correct run reports
`CN=Micheal-Jiaming`. If the signing credentials are missing it exits non-zero and says so,
rather than handing over an unsigned file that looks fine until someone tries to install it.

The signing key itself is **not in this repository** and never can be: `app\release.keystore`
is gitignored, and its passwords live in `local.properties`, which is gitignored too. That
means a fresh clone builds debug fine but cannot produce a publishable release — which is
correct, and is also exactly how F-Droid's build server sees it, since F-Droid compiles from
source and signs with its own key. `app/build.gradle.kts` therefore treats all four signing
values as optional and falls back to an unsigned release rather than failing to configure.

> The keystore and its password must be backed up **outside** `D:\claude`. Android refuses
> an update signed by a different key, so losing either one means this app can never be
> updated again. There is no recovery path.

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

- **It keeps time while it isn't running.** Leave the app or lock the phone and an exact
  alarm still fires at the deadline, plays the tone and posts a notification, and the
  session is still counted; reopen the app and the usual prompt is waiting. This much was
  observed on a device in 1.1.2. The stronger claim — that it also survives Android killing
  the process outright — *follows from* the design, because the alarm is held by the system
  and not by the app, but it has still never actually been seen; see *Status*. A user
  **force-stopping** the app is a different case again, and a lost cause: Android cancels a
  force-stopped package's alarms itself, and no app can prevent that.
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
        ├── java\io\github\michealjiaming\pomodoro\
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
`v<number>`. The baseline was **1.0.0**. This document deliberately does not list
the releases since — `git tag` and `git log VERSION` are the record, and an
enumeration here would be one release stale the moment the next one ships.

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
git -C "D:\claude\Pomodoro timer Android" tag -a v$(cat VERSION) -m "..."
git -C "D:\claude\Pomodoro timer Android" push origin main --tags
git -C "D:\claude\Pomodoro timer Android" push mirror main --tags
```

## Publishing

The app is headed for **F-Droid**, and for nothing else. Nothing is published yet.

### Why F-Droid, and not the obvious two

Both mainstream stores were investigated and ruled out:

- **Google Play** requires a personal account created after November 2023 to run a closed
  test with **12 testers opted in for 14 continuous days** before it may even apply for
  production access. There is no way around it — public "open testing" only unlocks *after*
  production access is granted, so it cannot substitute. The owner cannot gather 12 people.
  Note the gate is per *account*, not per app: clearing it once would open Play permanently,
  so this is deferred rather than abandoned.
- **The Apple App Store** bars this app by category. App Review Guideline 4.3(b) names
  **simple timers** among categories too saturated for new entries, refusing them unless
  they offer a meaningfully different experience, and reserving the right to remove them
  later for failing to attract users. Separately, no free path to *any* iOS distribution
  exists — even the EU alternative marketplaces require the $99/year Apple Developer
  Program plus notarisation.
- **Amazon's Appstore** stopped accepting Android submissions on 20 August 2025.

F-Droid costs nothing, needs no developer account, no identity verification and no testers.
Its one real condition is that the app be open source with no proprietary dependencies, and
this app already qualifies untouched: Kotlin, AndroidX and Compose are all Apache-2.0, and
there is no Firebase, no Google Play Services, no analytics and no advertising. Nothing had
to be removed to become eligible.

**F-Droid imposes no target-API deadline.** The 31 August 2026 API 36 cutoff was Google
Play's and no longer applies, which is why `compileSdk`/`targetSdk` remain at 34. Upgrading
is worthwhile quality work but is not a blocker, and is deliberately deferred: it forces
AGP 8.9+, a newer Gradle than the pinned 8.7, a newer Compose than `compose-bom:2024.06.00`,
a re-fetch of the platform-34-only toolchain in `D:\claude\android-build`, and re-checking
both branches of `Ui.kt` against Android 16's enforced edge-to-edge display.

### What still has to happen

| Step | State |
|---|---|
| Application ID renamed to a namespace the author controls | **done** (1.1.2) |
| Security pass — no secrets in history, no `INTERNET`, immutable `PendingIntent`s | **done** (1.1.2) |
| `LICENSE` file, Apache-2.0, at the repo root | **done** (1.1.2) — `Copyright 2026 Micheal-Jiaming` |
| Release signing config and keystore | **done** (1.1.2) — 4096-bit RSA, alias `pomodoro`, valid to 2054 |
| `fastlane/metadata/android/en-US/` listing text, icon and screenshots | **done** (1.1.2) — F-Droid reads the listing from *this* repo, not a web console |
| GitHub repository made public | **done** (1.1.2) — F-Droid must be able to fetch and build the source |
| Signed GitHub release, APK attached | **done** (1.1.2) — [v1.1.2](https://github.com/Micheal-Jiaming/Pomodoro-timer-Android/releases/tag/v1.1.2) |
| IzzyOnDroid inclusion request | outstanding — needs a Codeberg account; request text drafted below |
| F-Droid merge request | outstanding — needs a GitLab account; recipe drafted below |

The listing screenshots are all **landscape**, because MuMu Player cannot be made to render
this app in portrait (see *Status*). They are accurate and cover all three themes, but
portrait shots taken on a real phone would suit F-Droid's phone slot better; replacing them
is a matter of dropping new files into `phoneScreenshots/`.

Then three releases, escalating, each a prerequisite for the next:

1. **A signed GitHub release** on the existing `v<VERSION>` tag — same day, and immediately
   installable by anyone. **Done:** `v1.1.2`, with `PomodoroTimer-1.1.2.apk` attached
   (6,069,210 bytes, SHA-256 `ebb238b7…cc7fe8`).
2. **IzzyOnDroid** — a third-party F-Droid repository that serves the author's *own* signed
   APK straight from GitHub releases, so it needs no build on their side. Days, not months.
   Submitted by filing an issue on their Codeberg maintenance repo.
3. **The official F-Droid repository** — a merge request against
   `gitlab.com/fdroid/fdroiddata` adding `metadata/io.github.michealjiaming.pomodoro.yml`.
   F-Droid builds and signs it themselves, so the release keystore is not involved. Volunteer
   review takes weeks to months.

The step-2 request, ready to file as an issue at
`codeberg.org/IzzyOnDroid/repodata/issues`. IzzyOnDroid serves the developer's own signed
APK from GitHub releases rather than building from source, which is why it asks for a
release URL instead of a build recipe:

> **App inclusion request: Pomodoro Timer**
>
> - **Name:** Pomodoro Timer
> - **Package ID:** `io.github.michealjiaming.pomodoro`
> - **Source:** https://github.com/Micheal-Jiaming/Pomodoro-timer-Android
> - **Licence:** Apache-2.0
> - **Releases:** https://github.com/Micheal-Jiaming/Pomodoro-timer-Android/releases —
>   signed APK attached to each tagged release, tags in the form `v1.1.2`
> - **Minimum Android:** 8.0 (API 26)
>
> A Pomodoro focus timer. Fully offline: the app declares **no `INTERNET` permission**, so
> it has no means of network access at all. No advertising, no analytics, no tracking
> libraries, no Google Play Services and no Firebase — the only dependencies are Kotlin,
> AndroidX and Compose, all Apache-2.0. Nothing is collected; the theme and session count
> are stored on the device with `SharedPreferences`.
>
> I am the author, the app is not yet listed in the repository, and I believe it meets the
> inclusion policy. Happy to supply anything further.

The step-3 recipe, drafted and ready to submit as
`metadata/io.github.michealjiaming.pomodoro.yml`:

```yaml
Categories:
  - Time
License: Apache-2.0
AuthorName: Micheal-Jiaming
SourceCode: https://github.com/Micheal-Jiaming/Pomodoro-timer-Android
IssueTracker: https://github.com/Micheal-Jiaming/Pomodoro-timer-Android/issues
Changelog: https://github.com/Micheal-Jiaming/Pomodoro-timer-Android/releases

RepoType: git
Repo: https://github.com/Micheal-Jiaming/Pomodoro-timer-Android.git

Builds:
  - versionName: 1.1.2
    versionCode: 10102
    commit: v1.1.2
    subdir: app
    gradle:
      - yes

AutoUpdateMode: Version v%v
UpdateCheckMode: Tags
CurrentVersion: 1.1.2
CurrentVersionCode: 10102
```

**Two build-recipe frictions to expect at step 3.** The absent Gradle wrapper JAR is fine —
F-Droid supplies its own Gradle matching the version in `gradle-wrapper.properties`. Less
certain is that `app/build.gradle.kts` *computes* `versionCode` from `VERSION` rather than
stating a literal, which is what F-Droid's automatic version detection expects to parse.
Plan on `UpdateCheckMode: Tags`, which suits the existing tagging convention anyway, and be
ready to state `versionCode` explicitly in the metadata.

One caveat worth carrying forward: F-Droid's inclusion policy expects apps to be *actively
maintained* and treats abandonment as grounds for removal. It is far more relaxed about a
small finished utility than Apple is, but the app should not be treated as fire-and-forget.

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

The list above was verified on the **1.0.0** build. Three releases since have changed no
app behaviour:

- **1.1.0** made `VERSION` the single source of the version number. Checked by rebuilding
  and reading the APK back with `aapt2 dump badging` (`versionName='1.1.0'
  versionCode='10100'`), plus a configuration run with `VERSION` deliberately absent to
  confirm a missing file cannot break the build.
- **1.1.1** was a documentation reconciliation only — no code, no build. It nevertheless
  left the version line under *The APK* reading 1.1.0, which 1.1.2 corrected.
- **1.1.2** renamed the application ID (see *The APK*). That rewrote the `package`
  declaration of all nine Kotlin files, moved them to a new directory, changed the internal
  intent action string, and edited `namespace`/`applicationId`. Verified by rebuilding
  (Gradle exit 0) and reading the APK back: `package:
  name='io.github.michealjiaming.pomodoro'`, `versionCode='10102'`, `versionName='1.1.2'`,
  and `launchable-activity` resolving under the new package. The permission set is
  unchanged — the four declared permissions, plus the signature-only
  `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` that AndroidX generates for its own use, which
  renamed along with the package and so confirms the rename propagated everywhere. A
  `/security-audit` pass over the whole tree found nothing.

**1.1.2 was re-run on the device**, as the *release* build rather than the debug one — the
first time any build since 1.0.0 has been exercised on a device, and the first time a
release-signed build has been run at all. On MuMu Player 12 (SM-S9280, Android 12,
`android_id 603f5f9651375de3`), installed with `adb install -r` from
`app-release.apk`:

- installed and launched, with `MainActivity` reaching `topResumedActivity`
- the device reports `versionCode=10102 versionName=1.1.2 minSdk=26 targetSdk=34`
- all three themes cycle and render with their own palettes
- the **Custom** dialog opens over the Black theme with light-on-dark text (the
  `MaterialTheme.colorScheme` fix in `Theme.kt` still holds), validates, and accepts 1
- the ongoing notification posts on the `running` channel with `flags=0x2`
  (`FLAG_ONGOING_EVENT`), importance 2, title *Work session*
- an `RTC_WAKEUP` exact alarm is registered with the system on Start
- the alarm **fired** at its deadline and was consumed, the session counter incremented to
  1, and the end-of-session prompt was waiting on reopen with the documented wording, green
  break buttons and red work buttons
- both notification channels exist: `running` (*Running timer*) and `finished`
  (*Finished sessions*)
- zero entries in the crash buffer across the whole run

One consequence of the rename worth knowing: Android treats 1.1.2 as a *different app* from
any earlier build, because the application ID is the identity. It installs alongside an
older sideloaded APK instead of upgrading it, and starts with empty preferences — no theme,
no session count. This affects only someone who installed a pre-1.1.2 APK; nothing is
published.

### The delivery path, verified end to end

This was the actual purpose of releasing 1.1.2, and it was tested the way a stranger would
experience it rather than by trusting the local build:

1. `PomodoroTimer-1.1.2.apk` was downloaded from the public release URL with `curl` —
   6,069,210 bytes.
2. Its SHA-256 was compared against the published digest and matched exactly
   (`ebb238b7…cc7fe8`), so what GitHub serves is byte-for-byte what was signed here.
3. The app was **uninstalled** from the device and its absence confirmed, so nothing
   carried over.
4. The *downloaded* file — not the local build output — was installed. `Success`.
5. It launched, reached `topResumedActivity`, rendered correctly at a fresh state (15:00,
   Black, zero sessions), with an empty crash buffer.

So build, sign, publish, download, verify, install and run are all confirmed to work. That
is the whole claim this release was meant to establish.

### Still not verified

**Portrait — and now known to be untestable here, not merely untested.** The app is only
ever handed a landscape window on MuMu: `dumpsys window` reports
`mBounds=Rect(0, 0 - 1280, 720)` with `mRotation=ROTATION_90` and a `land` configuration,
even though the panel is physically 720×1280. Setting `user_rotation 0` (with auto-rotate
off) left `mRotation=1`; overriding `wm size` to both `720x1280` and `1280x720` changed
nothing, because MuMu's compositor pins the output. So the tall branch of `Ui.kt` has still
never rendered. It is the simpler of the two branches and has more room to work with on a
phone, but it needs a real device or a different emulator. All settings touched during these
attempts were reset.

**The alarm surviving the process being killed.** Three separate attempts, none conclusive:

- `am force-stop` cancelled the alarm — but that is Android cancelling a force-stopped
  package's alarms by design, not a defect, and no app can prevent it.
- `am kill` left the alarm correctly armed, but did not actually kill the process: the
  ongoing notification keeps the app above the threshold at which `am kill` will act.
- Killing the process directly by PID needs root, and adb is not root on this MuMu instance.

What *was* shown is that the alarm is held by the system (it appears in `dumpsys alarm`
independently of the app) and fires from there. Genuine process death remains unobserved.
The cheapest real test is a phone: start a one-minute session, swipe the app away, lock the
screen, and wait.

**How the tone actually sounds.** Never listened to; the emulator's audio was not captured.
