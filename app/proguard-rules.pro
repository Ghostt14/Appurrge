# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Android\sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- Shizuku -------------------------------------------------------------------------------
# Shizuku creates UserService reflectively inside its own process, so R8 must not rename it or
# strip its constructors, and the AIDL stub/proxy must keep their transaction methods.
-keep class com.saha.batchuninstaller.shizuku.UserService { *; }
-keep class com.saha.batchuninstaller.shizuku.IUserService { *; }
-keep class com.saha.batchuninstaller.shizuku.IUserService$* { *; }
