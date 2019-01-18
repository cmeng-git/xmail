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
