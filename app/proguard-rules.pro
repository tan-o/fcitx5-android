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

# JGit populates translation bundle constructors/fields through reflection.
-keepclassmembers class * extends org.eclipse.jgit.nls.TranslationBundle {
    public <init>();
    public java.lang.String *;
}

# Optional desktop APIs only. SnakeYAML detects Android and uses field access;
# JGit guards ProcessHandle with SystemReader.isAndroid(). JMX stays disabled
# by default; public HTTPS repositories do not use Kerberos authentication.
# Do not suppress missing classes outside these verified desktop integrations.
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.FeatureDescriptor
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
-dontwarn java.lang.ProcessHandle
-dontwarn java.lang.management.ManagementFactory
-dontwarn javax.management.InstanceAlreadyExistsException
-dontwarn javax.management.InstanceNotFoundException
-dontwarn javax.management.JMException
-dontwarn javax.management.MBeanRegistrationException
-dontwarn javax.management.MBeanServer
-dontwarn javax.management.MalformedObjectNameException
-dontwarn javax.management.NotCompliantMBeanException
-dontwarn javax.management.ObjectInstance
-dontwarn javax.management.ObjectName
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid
