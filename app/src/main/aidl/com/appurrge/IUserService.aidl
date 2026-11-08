package com.appurrge;

// Runs inside the privileged (shell) process that Shizuku starts for us.
interface IUserService {

    // Destroy method reserved by Shizuku. The transaction code must be 16777114.
    void destroy() = 16777114;

    void exit() = 1;

    // Runs "pm uninstall ..." for one package. Returns true on success.
    boolean uninstall(String packageName, boolean systemApp) = 2;
}
