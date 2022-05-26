-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-dontwarn org.htmlcleaner.HtmlCleanerForAnt
-dontwarn org.htmlcleaner.JDomSerializer
-verbose
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference

-keepclasseswithmembernames class * {
    native <methods>;
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# Add project specific ProGuard rules here.
-dontobfuscate

# Preserve the line number information for debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# Library specific rules
-dontnote android.net.http.*
-dontnote org.apache.commons.codec.**
-dontnote org.apache.http.**
-dontnote com.squareup.moshi.**
-dontnote com.github.amlcurran.showcaseview.**
-dontnote de.cketti.safecontentresolver.**
-dontnote com.tokenautocomplete.**

-dontwarn okio.**
-dontwarn com.squareup.moshi.**
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**
-dontwarn javax.xml.stream.**
-dontwarn org.apache.tools.ant.**
-dontwarn org.jaxen.**
-dontwarn sun.net.spi.nameservice.**

-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
# -keep http://proguard.sourceforge.net/manual/troubleshooting.html#descriptorclass

# Project specific rules
-dontnote org.atalk.xryptomail.PRNGFixes
-dontnote org.atalk.xryptomail.ui.messageview.**
-dontnote org.atalk.xryptomail.view.**
-keep public class org.openintents.openpgp.**
