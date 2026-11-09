# Consumer rules for :core:netfs, applied to :app's R8 run automatically.

# --- Optional JVM-only deps referenced by the network libraries (absent on Android) ---------------
# smbj: Kerberos/SPNEGO (GSS-API) auth; we only use NTLM.
-dontwarn org.ietf.jgss.**
# mbassador (smbj's event bus): EL expression filters, unused.
-dontwarn javax.el.**

# --- BouncyCastle ------------------------------------------------------------------------------
# The JCA provider registers its algorithms as class-name strings and instantiates them
# reflectively; sshj asks for them by provider name "BC". Keep it whole.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# --- sshj (SFTP) -------------------------------------------------------------------------------
# Algorithm factories, and BC detection via Class.forName in SecurityUtils.
-keep class net.schmizz.sshj.** { *; }
-keep class com.hierynomus.sshj.** { *; }
-dontwarn net.schmizz.sshj.**
-dontwarn com.hierynomus.sshj.**

# --- smbj (SMB2/3) + asn-one + mbassador ------------------------------------------------------
-keep class com.hierynomus.smbj.** { *; }
-keep class com.hierynomus.mssmb2.** { *; }
-keep class com.hierynomus.msdtyp.** { *; }
-keep class com.hierynomus.msfscc.** { *; }
-keep class com.hierynomus.asn1.** { *; }
-keep class com.hierynomus.spnego.** { *; }
-keep class com.hierynomus.ntlm.** { *; }
-keep class com.hierynomus.protocol.** { *; }
# mbassador finds @Handler methods by reflection.
-keep class net.engio.mbassy.** { *; }
-keepclassmembers class * {
    @net.engio.mbassy.listener.Handler <methods>;
}

# --- commons-net (FTP) -------------------------------------------------------------------------
# DefaultFTPFileEntryParserFactory loads LIST parsers by class name.
-keep class org.apache.commons.net.ftp.parser.** { *; }

# --- slf4j: libraries log through slf4j-api; no binding is shipped (NOP logger). ---------------
-dontwarn org.slf4j.**
