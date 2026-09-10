@echo off
if not exist "%~dp0guess-market.jar" (
    echo guess-market.jar is missing. Keep it in the same folder as this file.
    pause
    exit /b 1
)
start "" javaw -jar "%~dp0guess-market.jar"
