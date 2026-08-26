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

Both report `io.github.michealjiaming.pomodoro` and require Android 8.0 (API 26) or newer.
Their `versionName` and `versionCode` are derived from `VERSION` (see *Version control*), so
a build made now reports whatever that file currently holds — read the file rather than
trusting a number written here.

Two APKs have been **published** to GitHub. **1.1.2** / `versionCode` **10102** was the
first, cut to prove the delivery path end to end. **1.1.11** / `versionCode` **10111**
supersedes it and is the one to install: 1.1.2 carries the alarm-cancel defect described
under *Implementation notes*, which could count a session twice.

`VERSION` may still run ahead of the newest release, because it is bumped for every change
including documentation. To get an exact published artefact, download it from the release
rather than rebuilding — a rebuild reports whatever `VERSION` holds today.

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
   - The Gradle wrapper is committed, so Studio has nothing to recreate; it uses the
     version pinned in `gradle/wrapper/gradle-wrapper.properties`.
4. Start your emulator, then press **Run ▶**. Studio installs and launches the app.

To get an APK you can install anywhere: **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
It lands in `app\build\outputs\apk\debug\app-debug.apk`.

### From the command line, on any machine

The Gradle wrapper is committed, so a fresh clone needs only JDK 17 and an Android SDK with
API 34 — the wrapper fetches Gradle itself on first run:

```bash
./gradlew assembleDebug          # gradlew.bat on Windows
```

Point `ANDROID_HOME` at your SDK, or set `sdk.dir` in `local.properties`. This is the route
a reviewer or a contributor will take, and the reason the wrapper is in git at all: before
it was added, cloning this repository gave you something you could not build.

Verified on 26 Aug 2026 — `gradlew.bat assembleRelease` produced an APK of the same size and
carrying the same certificate (`CN=Micheal-Jiaming`) as the script route below produces. No
byte count is quoted here on purpose: it changes with every code change, and a figure in this
document would be stale by the next commit.

### From the command line, with the toolchain already here

The APK in `dist\` was built this way. A self-contained toolchain — JDK 17, the Android
SDK (platform 34, build-tools 34.0.0, platform-tools), Gradle 8.7, and Gradle's dependency
cache — lives in `D:\claude\android-build`, along with four scripts: `fetch-toolchain.ps1`
and `setup-toolchain.ps1`, which put it there, and `build-apk.ps1` and
`build-release.ps1`, which build with it.

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
  session is still counted; reopen the app and the usual prompt is waiting. Observed in
  1.1.2, both with the app merely backgrounded and with it **closed** — the alarm still
  fired and the device vibrated, which is the point: the deadline lives in the system, not
  in the running app. A user **force-stopping** the app from Android's settings is a
  different case, and a lost cause: Android cancels a force-stopped package's alarms itself,
  and no app can prevent that.
- **The countdown ticks in the notification shade** without anything running in the
  background: the platform's own chronometer does it.

## Layout

```
Pomodoro timer Android\
├── Pomodoro timer Android.md     this file — the authoritative one
├── README.md                     the public GitHub landing page, see Layout note
├── LICENSE                       Apache-2.0 — required by F-Droid, see Publishing
├── VERSION                       current version number — see Version control
├── .gitignore                    keeps dist\, build\, .gradle\, local.properties and
│                                 *.keystore out of git
├── .gitattributes                stores every file byte for byte
├── dist\                         not in git — rebuild instead
│   ├── PomodoroTimer-debug.apk   debug build, 8.4 MB
│   ├── PomodoroTimer-1.1.2.apk   the first published release, 5.8 MB
│   └── PomodoroTimer-1.1.11.apk  the current published release, 5.8 MB
├── fastlane\metadata\android\en-US\   the F-Droid store listing — see Publishing
│   ├── title.txt                 }
│   ├── short_description.txt     } listing text, read by F-Droid from this repo
│   ├── full_description.txt      }
│   ├── changelogs\             one file per versionCode: 10102, 10111
│   └── images\
│       ├── icon.png              512×512, written by make_launcher_icons.py
│       └── phoneScreenshots\     four portrait 720×1280 captures
├── make_launcher_icons.py        redraws the launcher icons and the listing icon
├── settings.gradle.kts           }
├── build.gradle.kts              } Gradle build, versions pinned as a known-good set
├── gradle.properties             }
├── gradlew                       }
├── gradlew.bat                   } Gradle wrapper — a fresh clone builds with no
├── gradle\wrapper\               }  preinstalled Gradle; see Rebuilding it
│   ├── gradle-wrapper.jar        }
│   └── gradle-wrapper.properties pins Gradle 8.7
└── app\
    ├── build.gradle.kts          version derivation, signing config, dependencies
    ├── proguard-rules.pro        named by the release build type
    ├── release.keystore          NOT in git — the signing key, see Rebuilding it
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

**Two Markdown files, deliberately.** The workspace convention is one document per project,
and this file is that document — the authoritative one. `README.md` exists only because the
repository is public and GitHub renders it as the landing page. It is kept deliberately thin:
a shop window plus a pointer back here, rather than a second copy of the facts, so that the
two cannot drift apart. It quotes no version numbers, file sizes or hashes for the same
reason. Do not merge it into this file and do not delete it as a duplicate.

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
- **Cancelling an alarm must not rewrite it — fixed in 1.1.8, do not reintroduce.** The
  guard above had a hole, found by a comment audit. `AlarmScheduler.cancel()` used to build
  its `PendingIntent` with `FLAG_UPDATE_CURRENT` and a placeholder deadline of `0L`. Extras
  take no part in matching a `PendingIntent`, so the placeholder looked harmless — but that
  flag *rewrites* the live intent's extras, so cancelling overwrote the armed deadline with
  0. A broadcast already dispatched and not yet delivered then arrived carrying `deadline =
  0`, and `completeOnce()` skipped its duplicate check whenever the deadline was 0, so the
  session was counted and announced a second time. Two changes close it: the cancel path now
  looks the alarm up with `FLAG_NO_CREATE`, which finds it without modifying it, and
  `completeOnce()` now **refuses** a zero deadline instead of waving it through. Zero is not
  a real session — every in-app caller passes a live deadline and `restore()` only calls in
  from inside `if (deadline > 0L)` — so refusing it is what makes the guard unbypassable.
  If you ever need a placeholder `PendingIntent` again, use `FLAG_NO_CREATE`, never
  `FLAG_UPDATE_CURRENT`.
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

That writes both sets of icons from one drawing: the launcher icons into every density
bucket under `app\src\main\res\mipmap-*\`, and — via `save_listing_icon()` — the 512 px
`fastlane\metadata\android\en-US\images\icon.png` that F-Droid shows on the listing page.
Both are downsampled from the same 1024 px render, which is why the store icon cannot drift
away from the one on the phone.

## Version control

This project is its own Git repository, with two remotes:

| Remote | Points at |
|---|---|
| `origin` | `https://github.com/Micheal-Jiaming/Pomodoro-timer-Android` — **public** |
| `mirror` | `D:\claude\repos\Pomodoro timer Android.git` — local bare copy |

GitHub disallows spaces in repository names, hence `Pomodoro-timer-Android`.
The desktop original is a **separate** repository
(`github.com/Micheal-Jiaming/pomodoro-timer`) and the two version
independently. Authentication is the GitHub CLI acting as git's credential
helper (`gh auth setup-git`), so pushes need no interactive prompt.

Tracked: the Gradle build files, everything under `app\src\`, the launcher-icon
PNGs, `make_launcher_icons.py`, the whole Gradle wrapper (`gradlew`, `gradlew.bat`
and `gradle\wrapper\`), `LICENSE`, `README.md`, the `fastlane\` store listing, and
this document. Ignored: `dist\`, `build\` (which covers `app\build\`), `.gradle\`,
`local.properties`, and `*.keystore`/`*.jks` — the APK is rebuilt from source as
described above, an old debug APK cannot do what newer source does,
`local.properties` is machine-specific, and the keystore is a private credential.
The wrapper JAR is tracked even though it is a binary: it is a bootstrap that
source needs in order to build, not a build output, and without it a clone cannot
be built at all. `.gitattributes` sets `* -text` so every file is stored and checked
out byte for byte; Git for Windows is configured `core.autocrlf=true` system-wide
and would otherwise rewrite these LF files to CRLF.

**Versioning.** `VERSION` holds the current number; every release is tagged
`v<number>`. The baseline was **1.0.0**. This document deliberately does not list
the releases since — `git tag` and `git log VERSION` are the record, and an
enumeration here would be one release stale the moment the next one ships.

**A `v*` tag means "an APK was published" — from 1.1.7 onward.** It did not always:
`v1.1.3` through `v1.1.6` are version bumps for documentation and tooling that never
produced a release, and `v1.1.2` is the only tag of that run with an APK behind it. That
ambiguity had a real cost — an F-Droid recipe following tags would have picked `v1.1.6` and
shipped a build nobody had ever run. So the rule from 1.1.7 on: bump `VERSION` for every
change, but create a `v*` tag only when that version actually publishes an APK. 1.1.7 itself
is the first to follow it, and 1.1.7 through 1.1.9 are deliberately untagged — they were
committed locally while the push was blocked. `v1.1.11` is the first tag created under the
rule, and it carries a real release.

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

Then push both remotes:

```powershell
git -C "D:\claude\Pomodoro timer Android" commit -am "..."
git -C "D:\claude\Pomodoro timer Android" push origin main
git -C "D:\claude\Pomodoro timer Android" push mirror main
```

Tag **only if this version publishes an APK** — see the tag rule above. When it does:

```powershell
git -C "D:\claude\Pomodoro timer Android" tag -a v$(cat VERSION) -m "..."
git -C "D:\claude\Pomodoro timer Android" push origin --tags
git -C "D:\claude\Pomodoro timer Android" push mirror --tags
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
| Gradle wrapper committed, so a fresh clone can be built | **done** (1.1.7) — verified by building the release APK through `gradlew` itself |
| `README.md` landing page for the public repository | **done** (1.1.7) — deliberately thin; see the note under *Layout* |
| Source documented to the workspace comment standard | **done** (1.1.11) — the nine Kotlin files went from 0.096 to 0.68 comment-to-code |
| Second release, `v1.1.11`, carrying the alarm fix | **done** (1.1.11) — supersedes 1.1.2, which shipped the double-count bug |
| IzzyOnDroid inclusion request | outstanding — Codeberg account created; request text drafted below |
| F-Droid merge request | outstanding — needs a GitLab account; recipe drafted below |

The listing screenshots are **portrait**, 720×1280 — three themes plus the Custom dialog.
They were originally landscape, because adb cannot rotate MuMu; they were retaken once the
emulator was switched to portrait through MuMu's own interface (see *Status*). Replacing
them again is just a matter of dropping new files into `phoneScreenshots/`.

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
>   signed APK attached to each tagged release, tags in the form `v1.1.11`. Latest is
>   `v1.1.11`; the APK is signed with my own key, subject `CN=Micheal-Jiaming`.
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
  - versionName: 1.1.11
    versionCode: 10111
    commit: v1.1.11
    subdir: app
    gradle:
      - yes

AutoUpdateMode: None
UpdateCheckMode: None
CurrentVersion: 1.1.11
CurrentVersionCode: 10111
```

**Why auto-update is switched off in that recipe.** `UpdateCheckMode: Tags` would make
F-Droid follow the newest *git tag*, and a tag in this repository does not mean "release":
`v1.1.3` through `v1.1.6` are documentation bumps that were never built into an APK or
installed anywhere. F-Droid would take the newest of them, read `versionCode` 10106 out of
it, and publish a version nobody has ever run — and the listing would show no changelog
either, because `fastlane/.../changelogs/` contains only `10102.txt`. With both modes set to
`None`, F-Droid builds exactly the pinned commit and nothing else.

That also dissolves the `versionCode` friction this section used to warn about.
`app/build.gradle.kts` *computes* `versionCode` arithmetically from `VERSION` rather than
stating a literal, which is what F-Droid's automatic version detection expects to parse —
but nothing needs to parse it while auto-update is off, because the metadata states the
value outright. Keep the computed version: a single source of truth is the right design
here, and the whole cost is one metadata edit per release.

`UpdateCheckMode: Tags` becomes safe again as soon as the newest `v*` tag is a real release,
which the tag rule adopted in 1.1.7 now guarantees (see *Version control*). As of `v1.1.11`
that is already true, so it could be switched on now — the recipe keeps it off for a first
submission anyway, because proving one pinned build works is a smaller thing to ask a
reviewer to check than trusting auto-detection on an app they have never built.

The Gradle wrapper is no longer a concern: `gradlew`, `gradlew.bat` and
`gradle/wrapper/gradle-wrapper.jar` are now committed, so a fresh clone builds without any
preinstalled Gradle. F-Droid discards a checked-in wrapper JAR and substitutes its own
Gradle regardless, which is why this was never a blocker for them — only for humans.

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

The list above was verified on the **1.0.0** build. Of the releases since, only **1.1.8**
changed app behaviour — its own verification is recorded at the end of this section. The
rest were build, packaging or documentation work:

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

### Closed after release: portrait, and the alarm with the app shut

Two behaviours that had never been observed on *any* build were confirmed after 1.1.2 went
out, both of which this document had listed as open since 1.0.0.

**Portrait renders correctly.** The trick none of the adb approaches found is that MuMu can
be switched to portrait through **its own interface** — not through Android. Driven from
adb it is immovable: `user_rotation 0` (with auto-rotate off) leaves `mRotation=1`, and
`wm size` overrides of both `720x1280` and `1280x720` are ignored, because MuMu's
compositor pins the output. Switched in MuMu itself, the emulator reports
`mRotation=ROTATION_0` and `screencap` returns 720×1280. The tall branch of `Ui.kt` then
lays out as designed: ring above the controls, everything above the fold, nothing requiring
a scroll. The listing screenshots were retaken in portrait as a result.

**The alarm fires with the app closed.** Closed using MuMu's own close control, the alarm
still went off at its deadline and the device vibrated. That is the substance of the claim
in *What carried over*: the deadline is held by the system, not by the running app, so
shutting the app does not lose it.

### 1.1.8 — the alarm-cancel fix, verified on the device

Run against the **release** build of 1.1.8 (`versionCode=10108`, signed `CN=Micheal-Jiaming`)
on a cleared install, so the session counter started from a known zero. Four checks, in this
order:

1. **Reset while a countdown is armed** — the path the fix changed, since Reset is what calls
   `AlarmScheduler.cancel()`. `dumpsys alarm` showed one armed `RTC_WAKEUP` for
   `SESSION_FINISHED` before, and none after; Android's own record read
   `reason=alarm_cancelled`. The UI returned to a full ring and `PAUSED`, the counter stayed
   at 0, and the crash buffer stayed empty.
2. **Ordinary completion, app open** — a 1-minute custom session ran to its deadline. The
   prompt appeared and read *"You've completed 1 session so far"*: **exactly one**, which is
   the property the fix exists to protect. The alarm was consumed.
3. **Re-arming after a completion** — this is the regression the fix could itself have
   caused, because `cancel()` now retires the `PendingIntent` with `pending.cancel()`. A
   second session armed a fresh alarm without trouble, so retiring it does not prevent later
   scheduling.
4. **Completion with the app killed** — the app was swiped off recents with an alarm armed;
   its pid went from 4227 to nothing, so the process was genuinely gone, and the alarm stayed
   armed. At the deadline Android recreated the process from scratch (new pid 6938) purely to
   deliver the broadcast, and the notification posted on the `finished` channel reading
   *"You've completed 2 sessions so far"* — **exactly two after two sessions, not three.**
   That last point is the direct evidence there is no double count.

Check 4 also exercises what `FinishReceiver.onReceive` documents: it can run in a process
built from nothing to handle the broadcast, which is why it calls `ensureChannels()` rather
than assuming the activity has ever run.

What this does **not** prove: the race itself. The bug needed a cancel to collide with an
already-dispatched broadcast, which cannot be provoked by hand. What is established is that
the two guards are in place and that ordinary completion, cancellation and re-arming all
still work — the duplicate path is closed by construction rather than by observation.

A leftover worth knowing for future testing: the emulator still has the **pre-rename
package** `com.pomodoro.timer` installed alongside the current one, so two Pomodoro apps
appear in the launcher. Uninstall it before any test where picking the wrong icon would
mislead.

### Still not verified

- **The 1.1.11 alarm re-arm on restore has not been run on a device.** MuMu was closed by
  the time the change was made. It compiles and the release APK builds, but nothing has
  exercised `restore()` since. What to check when an emulator is next available: start a
  session, kill the app from recents, reopen it, and confirm `dumpsys alarm` shows
  **exactly one** armed alarm rather than none or two — one proves the re-arm works and did
  not stack a duplicate. The reboot case it actually fixes is harder to stage and has not
  been attempted at all.
- **A reboot mid-session still loses the alarm until the app is next opened.** The 1.1.11
  re-arm repairs it on launch, but nothing runs between the reboot and that launch: there is
  no `BOOT_COMPLETED` receiver, because adding one needs the `RECEIVE_BOOT_COMPLETED`
  permission, and the store listing currently claims four permissions and no network. That
  is a deliberate open trade, not an oversight.
- **The double-count race itself has never been reproduced.** The 1.1.8 fix is verified in
  the sense that both guards are present and that completion, cancellation and re-arming all
  work (see above), but the collision it prevents — a cancel landing on an
  already-dispatched broadcast — cannot be provoked by hand. The path is closed by
  construction, not by having been observed to fail and then not fail.
- **The 880 Hz tone has still never been heard.** Vibration was felt at the deadline, but
  MuMu's audio is muted, so the synthesised tone in `Alerts` remains unlistened-to. The
  alert it belongs to demonstrably fires; only its sound is unconfirmed.
- **Android's own Force stop** (Settings → Apps → Force stop) is a different case from
  closing the app, and a hopeless one: Android cancels a force-stopped package's alarms
  itself, and no app can prevent that. Not a defect, and not worth testing.
