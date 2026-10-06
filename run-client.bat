@echo off
rem Starts the Guess Market client. The server has to be running first.
where javaw >nul 2>nul
if errorlevel 1 (
    echo Java was not found on this machine.
    echo Install Java 25 and make sure it is on the PATH, then run this again.
    pause
    exit /b 1
)
if not exist "%~dp0guess-market-client.jar" (
    echo guess-market-client.jar is missing. Keep it in the same folder as this file.
    pause
    exit /b 1
)
start "" javaw -jar "%~dp0guess-market-client.jar" %*
