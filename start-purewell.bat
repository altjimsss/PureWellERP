@echo off
REM PureWell ERP - Complete Launcher
REM This batch file starts both the Spring Boot backend and the Electron frontend

setlocal enabledelayedexpansion

REM Get the directory of this batch file
set SCRIPT_DIR=%~dp0

REM Check for Java
java -version >nul 2>&1
if errorlevel 1 (
    echo Error: Java is not installed or not in PATH
    echo Please install Java 17 or later
    pause
    exit /b 1
)

echo.
echo Starting PureWell ERP...
echo.

REM Start Spring Boot backend in background
echo [1/2] Starting backend service...
set JAR_PATH=%SCRIPT_DIR%..\target\demo-0.0.1-SNAPSHOT.jar

if not exist "!JAR_PATH!" (
    echo Error: JAR file not found at !JAR_PATH!
    echo Please run: mvn clean package -DskipTests
    pause
    exit /b 1
)

start "" java -Xmx2048M -jar "!JAR_PATH!" -Dserver.port=8080

REM Wait for backend to start
echo Waiting for backend to be ready...
timeout /t 8 /nobreak

REM Start Electron frontend
echo [2/2] Starting frontend application...
set EXE_PATH=%SCRIPT_DIR%spa-react\dist\win-unpacked\PureWell ERP.exe

if exist "!EXE_PATH!" (
    start "" "!EXE_PATH!"
) else (
    echo Warning: Electron EXE not found at !EXE_PATH!
    echo The frontend may need to be built first
    echo Run: cd spa-react ^&^& npm run electron-build
    pause
)

echo.
echo PureWell ERP is starting...
echo The application should open in a new window.
echo.
echo Backend console is running in the background.
echo Close this window or press Ctrl+C to stop the backend.
echo.

endlocal
