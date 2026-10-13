# R8/ProGuard rules for the release build. Keeping these narrow (rather
# than a blanket -dontobfuscate / -dontshrink) is what actually gets the
# size win; the rules below exist only to stop R8 from stripping or
# renaming things it can't see are used, because they're only referenced
# from XML or found by reflection at runtime rather than called directly
# from Kotlin code.

# Every tool screen's custom View (ProtractorView, CompassView, CoinView,
# etc.) is instantiated by the XML layout inflater via reflection using
# its (Context, AttributeSet) constructor - there's no direct Kotlin call
# site for R8 to see, so without this rule a release build would strip or
# rename them and crash with "Error inflating class ..." the moment any
# tool screen opens.
-keep public class com.karthikhegde.meterpod.ui.** extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Every screen is a Fragment swapped in via FragmentTransaction, and
# FragmentManager re-creates fragments from their class name on state
# restore (e.g. after a configuration change or process death) using
# Class.forName + a no-arg constructor. Keep the fragment classes and
# their no-arg constructors so that still works post-shrink/rename.
-keep public class com.karthikhegde.meterpod.** extends androidx.fragment.app.Fragment {
    public <init>();
}

# ZXing (QR Generator) does some internal reflection/service-lookup for
# format detection that R8 can't trace statically; keep it intact rather
# than risk the QR renderer breaking for a saving of a few KB.
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Standard Kotlin reflection metadata used by some AndroidX/Kotlin
# libraries at runtime - safe to keep, tiny footprint.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# File Viewer: methods called from the page's JavaScript via window.AndroidBridge
# are only reachable by name at runtime, so R8 must not strip or rename them.
-keepclassmembers class com.karthikhegde.meterpod.FileViewerFragment$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}
