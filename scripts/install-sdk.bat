@echo off
setlocal
set ROOT=%~dp0..
cd /d %ROOT%
call scripts\yes.bat | .android-sdk\cmdline-tools\latest\bin\sdkmanager.bat --sdk_root=%ROOT%\.android-sdk "platform-tools" "platforms;android-35" "build-tools;35.0.0"
endlocal
