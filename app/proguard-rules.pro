# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# disable obfuscation
-dontobfuscate

# Keep JNI interface
-keep class org.fcitx.fcitx5.android.core.* { *; }
-keep class org.fcitx.fcitx5.android.data.pinyin.customphrase.PinyinCustomPhrase {
    public <init>(...);
}

# Keep dependency magic
-keep class ** extends org.mechdancer.dependency.Component {
    int hashCode();
    boolean equals(java.lang.Object);
}

# remove kotlin null checks
-processkotlinnullchecks remove

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ONNX Runtime calls Java constructors/fields from native code.
# https://onnxruntime.ai/docs/build/android.html#note-proguard-rules-for-r8-minimization-android-app-builds-to-work
-keep class ai.onnxruntime.** { *; }

