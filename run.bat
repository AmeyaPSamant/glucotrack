@echo off
title GlucoTrack Server
echo ===========================================
echo Starting GlucoTrack Server (SQL Backend)...
echo ===========================================

set "JAVA_CMD=java"
where java >nul 2>nul
if %errorlevel% neq 0 (
    if exist "C:\Program Files\BlueJ\jdk\bin\java.exe" (
        set "JAVA_CMD=C:\Program Files\BlueJ\jdk\bin\java.exe"
    )
)

echo Using Java runtime: %JAVA_CMD%
"%JAVA_CMD%" -cp "lib\sqlite-jdbc.jar;." DiabetesTrackerServer

pause
