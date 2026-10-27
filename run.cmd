@echo off
setlocal
set "JAVA_HOME=%LOCALAPPDATA%\Tools\jdk21.0.13_0"
set "JAR=%~dp0target\face-detector.jar"

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo JDK 21 was not found at:
    echo   %JAVA_HOME%
    echo.
    echo Edit JAVA_HOME in this script to point at your JDK installation.
    pause
    exit /b 1
)

if not exist "%JAR%" (
    echo The app has not been built yet. Building it now...
    call "%~dp0build.cmd"
    if errorlevel 1 exit /b 1
)

if "%~1"=="" (
    start "" "%JAVA_HOME%\bin\javaw.exe" -jar "%JAR%"
) else (
    "%JAVA_HOME%\bin\java.exe" -jar "%JAR%" %*
)
