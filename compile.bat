@echo off
echo ===========================================
echo Compiling GlucoTrack Java Classes...
echo ===========================================

set "JAVAC_CMD=javac"
where javac >nul 2>nul
if %errorlevel% neq 0 (
    if exist "C:\Program Files\BlueJ\jdk\bin\javac.exe" (
        set "JAVAC_CMD=C:\Program Files\BlueJ\jdk\bin\javac.exe"
    )
)

echo Using compiler: %JAVAC_CMD%
"%JAVAC_CMD%" -encoding UTF-8 -cp "lib\sqlite-jdbc.jar;." Database.java DiabetesTrackerServer.java

if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Compilation failed.
    pause
    exit /b %errorlevel%
) else (
    echo.
    echo [SUCCESS] Compilation completed successfully.
)
pause
