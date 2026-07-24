# =============================================================================
# OpenVPN Neo R8 / ProGuard rules
# =============================================================================

-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod, Exceptions

# --- JNI ---------------------------------------------------------------------
# Native methods and any class that declares them are called from C/C++.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
# NativeUtils is the primary JNI bridge; the native layer looks it up by name.
-keep class com.mavodev.openvpnneo.core.NativeUtils { *; }

# --- OpenVPN 3 SWIG wrapper --------------------------------------------------
# Generated JNI wrapper classes in net.openvpn.ovpn3 are called from native code.
-keep class net.openvpn.ovpn3.** { *; }

# OpenVPNThreadv3 is instantiated reflectively via Class.forName in OpenVPNService.
-keep class com.mavodev.openvpnneo.core.OpenVPNThreadv3 { *; }

# --- Serialized VPN profiles -------------------------------------------------
# Profiles are persisted with ObjectOutputStream; the embedded class names and
# field names must stay stable so previously saved profiles keep deserializing.
# EVERY class in the serialized object graph (VpnProfile, its inner
# ChangeLogEntry, Connection, and every serialized enum) must keep its original
# name, otherwise readObject() throws ClassNotFoundException and the profile is
# silently dropped.
-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# --- Android boilerplate -----------------------------------------------------
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# --- Third party -------------------------------------------------------------
# BouncyCastle (PEM read/write) references optional/never-present classes.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
