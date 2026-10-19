# XML -> Jetpack Compose migration notes

* Single `ComponentActivity` + string-route back stack (`compose/AppRoot.kt`); no Fragments, no XML layouts/menus/styles/bg drawables.
* Sensor / audio / location / QR / calculator / converter logic moved over unchanged (same constants, formats, permission flows).
* Canvas-drawn custom views in `ui/` are unchanged and hosted with `AndroidView` (compass, ruler, gauges, glossy tile icons, ...).
* 3D "real key" buttons, tiles, input fields etc. are redrawn in Compose (`compose/Key3D.kt`, `compose/Components.kt`) with the same colours/offsets as the old drawables. The old ripple flash on press was dropped; the face still sinks when pressed.
* `ProvideTextStyle(TextStyle.Default)` in the theme keeps Material3 from adding its own line height / letter spacing, so text metrics match the old TextViews.
* ProGuard: `FileViewerFragment$Bridge` rule is now `FileViewerController$Bridge`.
* Not compiled in the authoring environment (no Android SDK) - first CI build is the compile check.
