@echo off
rem Starts the Guess Market client. The server has to be running first.
rem
rem This same file is copied into ex3-client as run.bat, where the jar sits
rem beside it. Run from the project folder it looks in ex3-client instead, so
rem it works from either place.
setlocal
set JAR=%~dp0guess-market-client.jar
if not exist "%JAR%" set JAR=%~dp0ex3-client\guess-market-client.jar

if not exist "%JAR%" (
    echo guess-market-client.jar was not found.
    echo Looked beside this file and in ex3-client\ - run build-ex3.bat first.
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
