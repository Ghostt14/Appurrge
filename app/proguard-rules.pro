# Shizuku starts UserService by name inside its own process, so it must keep its name and constructors.
-keep class com.appurrge.UserService { *; }
-keep class com.appurrge.IUserService { *; }
-keep class com.appurrge.IUserService$* { *; }

# Kotlin's null checks are only useful while debugging, and they add a call to almost every function.
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkNotNull(java.lang.Object);
    static void checkNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullParameter(java.lang.Object, java.lang.String);
    static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
    static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String);
    static void checkFieldIsNotNull(java.lang.Object, java.lang.String);
}

# Shorter class names in the dex.
-repackageclasses ''
