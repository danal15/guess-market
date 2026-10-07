@echo off
rem Starts the Guess Market web client.
rem
rem The Guess Market server has to be running first: guess-market.war deployed
rem in Tomcat, exactly as exercise 3 submits it. Nothing about it changes here.
rem
rem   run-web.bat                              the usual case
rem   run-web.bat http://localhost:9090        Tomcat somewhere else
setlocal
set HERE=%~dp0

where node >nul 2>nul
if errorlevel 1 (
    echo Node.js was not found on this machine.
    echo Install Node.js, make sure it is on the PATH, then run this again.
    pause
    exit /b 1
)

if not exist "%HERE%web\server.js" (
    echo The web folder is missing. Keep run-web.bat beside it.
    pause
    exit /b 1
)

rem An address given here is where Tomcat is. Left out, it is localhost:8080.
if not "%~1"=="" set MARKET=%~1

rem Nothing is installed: the client uses Node by itself and has no packages at
rem all, so there is nothing to fetch and no internet is needed.
pushd "%HERE%web"
node server.js
set STOPPED=%ERRORLEVEL%
popd

rem Without this the window would shut the instant anything went wrong - a port
rem already taken, say - and take the explanation with it.
if not "%STOPPED%"=="0" pause
