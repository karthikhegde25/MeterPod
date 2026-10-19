<p align="center">
  <img src="docs/app_logo.png" width="120" height="120" alt="MeterPod logo" />
</p>

<h1 align="center">MeterPod</h1>
<p align="center"><em>Android, Kotlin</em></p>

<p align="center">
  <a href="https://github.com/karthikhegde25/MeterPod/releases">
    <img src="docs/get_it_on_github.png" alt="Get it on GitHub" height="60" />
  </a>
</p>

# MeterPod (Android, Kotlin)

One App with 30 tools
1. Angle
2. Digital Level
3. Compass
4. Metal Detector
5. Speed
6. Sound Level
7. Time
8. Recorder
9. Protractor
10. Ruler
11. Pedometer
12. Odometer
13. Light Detector
14. Magnetic Field
15. Accelerometer
16. Magnifier
17. Unit Converter
18. Coin Toss
19. Calculator
20. Word & Character Counter
21. Morse Code
22. UUID Generator
23. Screen Info
24. Sensor Tester
25. Network & Wi-Fi Info
26. QR Generator
27. Games
28. File Creator
29. File Converter
30. File Viewer

### Angle
Rest any corner or edge of the phone on the ground and read the acute
angle live, plus the slope (rise/run ratio) that angle corresponds to.

### Digital Level
Lay the phone flat and it reports X tilt (roll), Y tilt (pitch), and a
combined value, plus a bubble-level visual that turns green when level.

### Compass
Shows the current magnetic heading in degrees plus its cardinal
abbreviation (N, NE, E, SE, S, SW, W, NW), with a rotating compass-rose
dial. The dial rotates opposite to the device heading so the "N" label
always points at magnetic north; a fixed blue triangle at the top of the
dial always represents the direction the top of the phone is facing.

### Metal Detector
Reads the magnetometer's raw field **vector** (not just heading, which the
Compass tab uses) and compares it against a calibrated baseline vector.
Tap "Calibrate baseline" somewhere away from metal or magnets, then sweep
the phone near an object — the reading, delta, and intensity bar update
live, with status text stepping through `NO METAL DETECTED` →
`METAL NEARBY` → `STRONG SIGNAL`, plus a short vibration pulse on a
strong signal.

### Speed
GPS-based speedometer for use in a moving vehicle (car, bike, train,
etc.). Android's Location API reports speed directly (`Location.speed`,
in m/s) whenever the GPS provider has a confident fix, which this
converts to km/h and mph on a speedometer-style gauge. Also tracks a
session max speed (with a reset button), similar to a real speedometer's
peak-hold.

### Sound Level
Ambient sound level meter with three live readouts derived from the same
audio capture: a dB figure, a dominant frequency estimate, and a raw
waveform graph.

## File Viewer
Opens about 60 file formats fully offline (text/code, JSON/XML, images,
archives, Office/PDF, SQLite, certificates, 3D, ...) using small JavaScript
parsers bundled in `assets/fileviewer/index.html`, hosted in a WebView by
`FileViewerScreen.kt` (served from `https://appassets.androidplatform.net`;
no INTERNET permission). Other formats get a type label plus Hex/Strings tabs.
Files can also be sent in via **Open with MeterPod** or the Share sheet.

## File Creator
Creates ~45 file types on the device and saves them through the system "Save as"
dialog (no storage permission): text/code formats (TXT, MD, JSON, CSV, XML, HTML, CSS,
JS, KT, JAVA, PY, YAML, TOML, INI, SQL, SH, LOG, DIFF, ...), RTF, PNG/JPEG/WebP/BMP/GIF
images, WAV tones, ZIP/TAR/GZ, Base64 text, SQLite, raw binary (hex), PDF, DOCX and XLSX.
Only built-in APIs are used (`filecreator/FileBuilders.kt` is pure JVM, `AndroidBuilders.kt`
uses Bitmap/PdfDocument/SQLite/Base64). MP3 and MP4 are left out because Android has no
general encoder for them.

## File Converter
Two modes. **Text & Data** converts between JSON, CSV, TSV, XML, YAML, Properties, INI,
Markdown/HTML tables and plain text, and can also write TOML and SQL (`CREATE TABLE` + `INSERT`).
Input can be typed, pasted or opened from a file; the format can be auto-detected.
Everything is parsed into one generic tree and written back out (`fileconverter/DataConverter.kt`,
pure Kotlin, no libraries). **Image** converts between PNG, JPG, WebP, BMP, GIF and one-page PDF
with optional quality and max-size (`ImageConverter.kt`, Android decoders/encoders only).

## Time and Recorder
- **Time** (`TimeScreen.kt`, `timer/TimeEngine.kt`): analog + digital clock, countdown timer (rings via an
  AlarmManager alarm clock, so it works with the screen off) and a stopwatch with laps. State lives in
  `TimeEngine`, so it keeps running if About is opened. The screen stays on while counting.
- **Recorder** (`RecorderScreen.kt`, `record/`): MediaRecorder writes AAC (.m4a) straight to disk with pause,
  resume and stop, a live waveform and a timer. A microphone foreground service keeps long recordings alive
  with the screen off. Save copies the file to a location chosen with the system "Save as" dialog.
- Pressing Back in either tool while something is running or unsaved asks for confirmation first
  (`compose/BackGuard.kt`). New manifest entries: foreground-service, notification and wake-lock permissions,
  `RecorderService`, `TimerReceiver`.

## Games

A grid of eight games (Tools → Games). Back inside a game asks for confirmation only when a game is in progress; Back on the grid leaves the tool.
1. 2048
2. Tic-Tac-Toe
3. Minesweeper
4. Snake
5. Number puzzle
6. Maze Munch
7. Flappy Bird
8. Space Invaders
## Morse Code and UUID Generator
- **Morse Code**: text to Morse and Morse to text; play it as sound, vibration or flashlight (camera torch); three speeds.
- **UUID Generator**: v1, v3, v4, v5, v6, v7, Nil and Max; up to 50,000 at once; uppercase/hyphen options; export all as TXT, PDF or Markdown.



## Branding
- Package name / applicationId: `com.karthikhegde.meterpod`
- App name: `MeterPod`
- Launcher icon: a real Android adaptive icon —
  `res/mipmap-anydpi-v26/ic_launcher.xml` layers a solid black background
  (`@color/ic_launcher_background`) with a colored foreground
  (`res/drawable/ic_launcher_foreground.png`, the blue gauge/pin logo) and
  a white monochrome layer (`res/drawable/ic_launcher_monochrome.png`) for
  Android 13+ themed icons. Both layers were cut out via flood-fill
  background removal (not a color-threshold, so interior highlights on the
  glossy pin survive) and padded to the standard ~66%-of-canvas safe zone
  so OEM icon masks (circle, squircle, rounded-square, etc.) don't clip
  the artwork.
- Legacy fallback for API 24-25 (pre-adaptive-icon): flattened square PNGs
  at `res/mipmap-*dpi/ic_launcher.png`, each resized directly from the
  full-resolution source (not chained through smaller intermediates) to
  stay sharp rather than accumulating resampling blur.

## App structure
The UI is 100% Jetpack Compose (single activity, no XML layouts or Fragments).
- `MainActivity.kt` — `ComponentActivity` that hosts the Compose UI and handles
  "Open with" / Share intents (routed to the File Viewer).
- `compose/` — `Theme.kt` (colours + theme), `Key3D.kt` (the raised 3D buttons,
  calculator keys and round steppers), `Components.kt` (text fields, dropdown,
  cards, lifecycle helper), `AppRoot.kt` (header bar, overflow menu and the
  string-route back stack that replaced the FragmentManager), `HomeScreen.kt`
  (the tool grid).
- `tools/ToolCatalog.kt` — the list of tools shown on the home grid.
- `*Screen.kt` — one composable per tool, with the original sensor/business
  logic unchanged. The Canvas-drawn views in `ui/` are reused through `AndroidView`.

## How to open and run it
1. Open **Android Studio** (Hedgehog/2023.1 or newer works well).
2. **File → Open** and select this `MeterPod` folder.
3. If Android Studio asks to create a Gradle wrapper, let it — it will
   sync automatically using its bundled Gradle. (If you'd rather use
   your own wrapper, run `gradle wrapper` once inside this folder from
   a terminal that has Gradle installed.)
4. Plug in an Android phone (USB debugging enabled) or start an
   emulator with sensor support, then click **Run ▶**.

Minimum SDK: Android 7.0 (API 24). No runtime permissions are required.

## Building a smaller release APK
The `release` build type has R8 shrinking/obfuscation, resource shrinking,
and an English-only `resConfigs` all enabled, plus a trimmed set of
`packagingOptions` exclusions — together these noticeably cut the APK size
versus a debug build. To build one:
```
gradle assembleRelease   # or ./gradlew assembleRelease if you have a wrapper
```

Note: this project doesn't include a Gradle wrapper (`gradlew` +
`gradle-wrapper.jar`), so the workflow installs a pinned Gradle 8.5
directly via `gradle/actions/setup-gradle` and runs `gradle assembleRelease`
instead of `./gradlew assembleRelease`. It also auto-detects whether the
project was committed as a zip or already extracted, and locates
`settings.gradle` wherever it ends up. If you later generate a real
wrapper (open the project in Android Studio and let it sync, or run
`gradle wrapper` locally with Gradle installed), you can simplify the
workflow to the more common `./gradlew assembleRelease` form.

## Credits
The QR Generator tool's content types and code formats are adapted from
[Image Toolbox](https://github.com/T8RIN/ImageToolbox)'s QR code feature.
Thanks to the Image Toolbox project and its contributors.

## License
The app follows GNU General Public License v3.0. Refer to LICENSE in repo.
