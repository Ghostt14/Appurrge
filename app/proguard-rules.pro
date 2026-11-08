# Shizuku starts UserService by name inside its own process, so it must keep its name and constructors.
-keep class com.appurrge.UserService { *; }
-keep class com.appurrge.IUserService { *; }
-keep class com.appurrge.IUserService$* { *; }
