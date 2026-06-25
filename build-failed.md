 build
Failed jobs
Status: Failed
fdroid build  e: file:///home/vagrant/build/com.kompact/app/src/main/java/com/kompact/ui/KompactScreen.kt:1301:13 Unresolved reference: AnimatedVisibility
e: file:///home/vagrant/build/com.kompact/app/src/main/java/com/kompact/ui/KompactScreen.kt:1302:17 @Composable invocations can only happen from the context of a @Composable function
> Task :app:compileReleaseKotlin FAILED
FAILURE: Build failed with an exception.
* What went wrong:
Execution failed for task ':app:compileReleaseKotlin'.
> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
   > Compilation error. See log for more details
* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.
BUILD FAILED in 3m 23s
35 actionable tasks: 35 executed
==== detail end ====
2026-06-09 05:04:58,297 DEBUG: Error encountered, stopping by user request.
Uploading artifacts for failed job 00:01
Uploading artifacts...
repo/: found 3 matching artifact files and directories 
unsigned/: found 1 matching artifact files and directories 
tmp/: found 1 matching artifact files and directories 
Uploading artifacts as "archive" to coordinator... 201 Created  correlation_id=4ed7777244044bc5b77d45bf5eb771ba id=14758098668 status=201 token=70_ZzXmyM
Uploading artifacts...
WARNING: codequality.json: no matching files. Ensure that the artifact path is relative to the working directory (/builds/fdroid/fdroiddata) 
ERROR: No files to upload                          
Cleaning up project directory and file based variables 00:01
ERROR: Job failed: exit code 1

