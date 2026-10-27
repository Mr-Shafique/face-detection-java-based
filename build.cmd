@echo off
setlocal
set "JAVA_HOME=%LOCALAPPDATA%\Tools\jdk21.0.13_0"
set "MAVEN_HOME=%LOCALAPPDATA%\Tools\apache-maven-3.9.16"

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo JDK 21 was not found at:
    echo   %JAVA_HOME%
    echo.
    echo Edit JAVA_HOME in this script to point at your JDK installation.
    pause
    exit /b 1
)

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
    echo Maven was not found at:
    echo   %MAVEN_HOME%
    echo.
    echo Edit MAVEN_HOME in this script to point at your Maven installation.
    pause
    exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%"

if "%~1"=="" (
    call "%MAVEN_HOME%\bin\mvn.cmd" -B -ntp -f "%~dp0pom.xml" clean package
) else (
    call "%MAVEN_HOME%\bin\mvn.cmd" -B -ntp -f "%~dp0pom.xml" %*
)

if errorlevel 1 (
    echo.
    echo Build failed.
    pause
    exit /b 1
)

echo.
echo Build complete: target\face-detector.jar
