@echo off
REM PureWell ERP Launcher
REM This batch file launches the Spring Boot application

setlocal enabledelayedexpansion

REM Find the JAR file
set JAR_PATH=%~dp0demo-0.0.1-SNAPSHOT.jar

REM Check if JAR exists
if not exist "!JAR_PATH!" (
    echo Error: JAR file not found at !JAR_PATH!
    pause
    exit /b 1
)

REM Get available memory
for /f "tokens=2 delims==" %%A in ('wmic OS get TotalVisibleMemorySize /format:list') do set /A TOTAL_MEM=%%A/1024
set /A XMX=!TOTAL_MEM!*3/4

if !XMX! GTR 2048 set XMX=2048

REM Launch the application
java -Xmx!XMX!M -jar "!JAR_PATH!" %*

if errorlevel 1 (
    echo.
    echo Application exited with error code !errorlevel!
    pause
)

endlocal
