@echo off
rem Deploys guess-market.war into the Tomcat under lib\tomcat and starts it.
rem This is for working on the project; the submission itself is just the war,
rem which the checker drops into their own Tomcat.
setlocal
set ROOT=%~dp0
set CATALINA_HOME=%ROOT%lib\tomcat

if not exist "%ROOT%guess-market.war" (
    echo guess-market.war is missing. Run build-ex3.bat first.
    pause
    exit /b 1
)
if not exist "%CATALINA_HOME%\bin\catalina.bat" (
    echo No Tomcat in lib\tomcat.
    echo Unpack a Tomcat 10.1 there, or copy guess-market.war into your own Tomcat's webapps.
    pause
    exit /b 1
)

rem Tomcat insists on a JDK, and "where java" often finds Oracle's little
rem forwarding shim first, whose folder is not one. So each candidate is only
rem accepted once javac has been seen sitting next to it.
call :check "%JAVA_HOME%"
if not errorlevel 1 goto ready
for /f "delims=" %%j in ('where java 2^>nul') do (
    call :fromExe "%%j"
    if not errorlevel 1 goto ready
)
for /d %%d in ("%ProgramFiles%\Java\jdk*") do (
    call :check "%%d"
    if not errorlevel 1 goto ready
)
echo No JDK was found. Install Java 25, or set JAVA_HOME to it, and try again.
pause
exit /b 1

:ready
echo Using Java at %JAVA_HOME%
echo Deploying guess-market.war...
if exist "%CATALINA_HOME%\webapps\guess-market" rmdir /s /q "%CATALINA_HOME%\webapps\guess-market"
if exist "%CATALINA_HOME%\webapps\guess-market.war" del /q "%CATALINA_HOME%\webapps\guess-market.war"
copy /y "%ROOT%guess-market.war" "%CATALINA_HOME%\webapps\" >nul

echo.
echo Starting Tomcat on http://localhost:8080/guess-market
echo Leave this window open. Press Ctrl+C here to stop the server.
echo.
call "%CATALINA_HOME%\bin\catalina.bat" run
exit /b 0

rem Accepts a folder as JAVA_HOME only if it really holds a JDK.
:check
if "%~1"=="" exit /b 1
if not exist "%~1\bin\javac.exe" exit /b 1
set JAVA_HOME=%~1
exit /b 0

rem Turns ...\jdk\bin\java.exe into ...\jdk, then checks it.
:fromExe
for %%d in ("%~dp1..") do call :check "%%~fd"
exit /b %errorlevel%
