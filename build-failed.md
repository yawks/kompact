 *  Executing task: bash -c 'bash ./gradlew app:assembleDebug && /usr/bin/adb uninstall com.kompact 2>/dev/null || true && /usr/bin/adb install -t -r app/build/outputs/apk/debug/app-debug.apk' 

Could not set maximum file descriptor limit: maximum
sed: -e expression #2, char 9: unterminated `s' command

> Task :app:compileDebugKotlin
e: file:///mnt/data/Documents/Naxod/Kompact/app/src/main/java/com/kompact/ui/KompactScreen.kt:1327:76 Unresolved reference: ok

> Task :app:compileDebugKotlin FAILED

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':app:compileDebugKotlin'.
> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
   > Compilation error. See log for more details

* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.

BUILD FAILED in 1m 35s
30 actionable tasks: 2 executed, 28 up-to-date
* daemon not running; starting now at tcp:5037
* daemon started successfully
Performing Streamed Install
adb: failed to install app/build/outputs/apk/debug/app-debug.apk: Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.kompact signatures do not match newer version; ignoring!]

 *  The terminal process "/usr/bin/bash '-c', 'bash -c 'bash ./gradlew app:assembleDebug && /usr/bin/adb uninstall com.kompact 2>/dev/null || true && /usr/bin/adb install -t -r app/build/outputs/apk/debug/app-debug.apk''" terminated with exit code: 1. 
 *  Terminal will be reused by tasks, press any key to close it. 