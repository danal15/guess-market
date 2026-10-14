@echo off
rem Starts the Guess Market desktop client. The server has to be running first.
rem guess-market-client.jar and engine.jar must sit in the same folder as this file.
setlocal
set JAR=%~dp0guess-market-client.jar

if not exist "%JAR%" (
    echo guess-market-client.jar was not found.
    echo It must be in the same folder as run.bat, together with engine.jar.
    pause
    exit /b 1
)

where javaw >nul 2>nul
if errorlevel 1 (
    echo Java was not found on this machine.
    echo Install Java 25 and make sure it is on the PATH, then run this again.
    pause
    exit /b 1
)

start "" javaw -jar "%JAR%" %*
